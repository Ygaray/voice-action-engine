// Source-scan gate for constructs detekt cannot see syntax-only (method calls, FQ annotations, comment-only ids).
// Applied from each published module; the `project.name == "core"` blocks are repo-wide checks hosted on :core so they run once.
// Applied-script limit: no references to plugin classes here.

data class Rule(val id: String, val regex: Regex)

// Matched against source text with comments and string/char literals blanked out (planning ids: COMMENT text only).
val bannedRules = listOf(
    Rule("runCatching", Regex("""\brunCatching\b""")),
    Rule("println/print", Regex("""(?<![\w.])(println|print)\s*\(""")),
    Rule("System.out/err", Regex("""\bSystem\s*\.\s*(out|err)\b""")),
    Rule("printStackTrace", Regex("""\.\s*printStackTrace\s*\(""")),
    Rule("DI annotation (FQ)", Regex("""@\s*(javax\s*\.\s*inject|jakarta\s*\.\s*inject|dagger|androidx\s*\.\s*hilt)\s*\.""")),
    Rule(
        "forbidden package (FQ or import)",
        Regex(
            """\b(okhttp3\s*\.\s*internal|mockwebserver3|okhttp3\s*\.\s*coroutines|android\s*\.\s*util\s*\.\s*Log""" +
                """|javax\s*\.\s*inject|jakarta\s*\.\s*inject|dagger|androidx\s*\.\s*hilt)\b""",
        ),
    ),
    Rule("app planning id in comment", Regex("""\b(T-\d+-\d+|WR-\d+|Phase\s+\d+\s+D-\d+)\b""")),
)

// Returns (code with literals blanked, comments only), both preserving line structure.
fun splitCodeAndComments(src: String): Pair<String, String> {
    val code = StringBuilder()
    val comments = StringBuilder()
    var i = 0
    val n = src.length
    fun keepNl(c: Char, sb: StringBuilder) {
        sb.append(if (c == '\n') '\n' else ' ')
    }
    fun blank(count: Int) {
        repeat(count) {
            code.append(' ')
            comments.append(' ')
        }
    }
    while (i < n) {
        val c = src[i]
        when {
            src.startsWith("//", i) -> {
                while (i < n && src[i] != '\n') {
                    comments.append(src[i])
                    code.append(' ')
                    i++
                }
            }
            src.startsWith("/*", i) -> {
                var depth = 0
                while (i < n) {
                    if (src.startsWith("/*", i)) {
                        depth++
                        comments.append("  ")
                        code.append("  ")
                        i += 2
                    } else if (src.startsWith("*/", i)) {
                        depth--
                        comments.append("  ")
                        code.append("  ")
                        i += 2
                        if (depth == 0) break
                    } else {
                        comments.append(src[i])
                        keepNl(src[i], code)
                        i++
                    }
                }
            }
            src.startsWith("\"\"\"", i) -> {
                blank(3)
                i += 3
                while (i < n && !src.startsWith("\"\"\"", i)) {
                    keepNl(src[i], code)
                    keepNl(src[i], comments)
                    i++
                }
                if (i < n) {
                    blank(3)
                    i += 3
                }
            }
            c == '"' -> {
                blank(1)
                i++
                while (i < n && src[i] != '"' && src[i] != '\n') {
                    if (src[i] == '\\') {
                        blank(1)
                        i++
                    }
                    blank(1)
                    i++
                }
                if (i < n && src[i] == '"') {
                    blank(1)
                    i++
                }
            }
            c == '\'' && i + 2 < n && (src[i + 2] == '\'' || src[i + 1] == '\\') -> {
                val end = src.indexOf('\'', i + 2).let { if (it < 0) n - 1 else it }
                while (i <= end) {
                    blank(1)
                    i++
                }
            }
            else -> {
                code.append(c)
                keepNl(c, comments)
                i++
            }
        }
    }
    return code.toString() to comments.toString()
}

fun scanText(label: String, text: String): List<String> {
    val (code, comments) = splitCodeAndComments(text)
    val out = mutableListOf<String>()
    for (rule in bannedRules) {
        val target = if (rule.id.startsWith("app planning id")) comments else code
        rule.regex.findAll(target).forEach { m ->
            val line = target.substring(0, m.range.first).count { it == '\n' } + 1
            out += "$label:$line [${rule.id}] '${m.value.trim()}'"
        }
    }
    return out
}

val scanBanned = tasks.register("scanBannedConstructs") {
    group = "verification"
    val files = fileTree("src/main") { include("**/*.kt", "**/*.java") }
    inputs.files(files)
    val moduleDir = projectDir
    val modulePath = project.path
    doLast {
        val violations = files.files.sorted().flatMap { f -> scanText(f.relativeTo(moduleDir).path, f.readText()) }
        if (violations.isNotEmpty()) {
            throw GradleException("Banned constructs in $modulePath:\n" + violations.joinToString("\n") { "  $it" })
        }
    }
}
tasks.named("check") { dependsOn(scanBanned) }

// Repo-wide checks, hosted on :core so they run once.
if (project.name == "core") {
    val verifyNoBaseline = tasks.register("verifyNoDetektBaseline") {
        group = "verification"
        val root = rootProject.projectDir
        doLast {
            val skip = setOf("build", ".gradle", ".git", ".planning", "graphify-out", ".kotlin")
            val hits = mutableListOf<String>()
            root.walkTopDown().onEnter { it.name !in skip }.forEach { f ->
                if (f.isFile && f.name.matches(Regex("(?i).*baseline.*\\.xml"))) {
                    hits += "baseline file: ${f.relativeTo(root)}"
                }
                if (f.isFile && f.name.endsWith(".kts") && Regex("""\bbaseline\s*=""").containsMatchIn(f.readText())) {
                    hits += "baseline wiring: ${f.relativeTo(root)}"
                }
            }
            if (hits.isNotEmpty()) {
                throw GradleException("detekt baseline is forbidden (zero-baseline policy):\n" + hits.joinToString("\n") { "  $it" })
            }
        }
    }
    val verifyScanner = tasks.register("verifyInvariantScannerControls") {
        group = "verification"
        val controls = fileTree(rootProject.file("config/negative-controls")) { include("*.kt.txt") }
        inputs.files(controls)
        doLast {
            val problems = mutableListOf<String>()
            if (controls.files.isEmpty()) problems += "no negative controls found (vacuous)"
            for (f in controls.files.sorted()) {
                val text = f.readText()
                val header = Regex("""^// EXPECT: (.*)$""", RegexOption.MULTILINE).find(text)
                if (header == null) {
                    problems += "${f.name}: missing '// EXPECT:' header"
                    continue
                }
                val expect = header.groupValues[1].trim()
                val found = scanText(f.name, text).map { Regex("""\[(.*?)\]""").find(it)!!.groupValues[1] }.toSet()
                val wanted = if (expect == "(none)") emptySet() else expect.split(';').map { it.trim() }.toSet()
                if (found != wanted) problems += "${f.name}: expected $wanted but scanner found $found"
            }
            if (problems.isNotEmpty()) {
                throw GradleException("Scanner negative controls failed:\n" + problems.joinToString("\n") { "  $it" })
            }
        }
    }
    tasks.named("check") { dependsOn(verifyNoBaseline, verifyScanner) }
}
