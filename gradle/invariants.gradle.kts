// Invariant gates, applied from each published module.
// Scanner half: constructs detekt cannot see syntax-only (method calls, FQ annotations, comment-only ids).
// Structural half: bytecode level, explicit API, module graph, dependency allowlists, DI-artifact denial.
// The `project.name == "core"` blocks are repo-wide checks hosted on :core so they run once.
// Applied-script limit: no references to plugin classes here.
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class Rule(val id: String, val regex: Regex)

// Matched against source text with comments and string/char literals blanked out (planning ids: COMMENT text only).
val bannedRules = listOf(
    Rule("runCatching", Regex("""\brunCatching\b""")),
    // A `fun print(` / `fun println(` declaration is not a call, so it is skipped (a later bare call to it is still flagged).
    Rule("println/print", Regex("""(?<![\w.])(?<!\bfun\s{1,16})(println|print)\s*\(""")),
    // The rule above skips member calls (`out.print(`), which also lets `kotlin.io.println(` through; this closes that hole.
    // Matching the name after `kotlin.io.` also catches `import kotlin.io.println as p` (alias import), since detekt's
    // ForbiddenMethodCall needs type resolution and this project runs detekt syntax-only.
    Rule("kotlin.io print (FQ or import)", Regex("""\bkotlin\s*\.\s*io\s*\.\s*(println|print)\b""")),
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

// CLN-02: matched against the UNMODIFIED file text. The rules above run on text with literals and comments blanked,
// but an app-domain name or a tool count hides exactly there (a tool-name string, a KDoc sentence), so these must see it all.
// Deny-list: only names already tracked in planning docs. The list lives here, in a .kts that is never scanned.
// The tool-count rule catches the size of a tools-ish collection set against 17 or 18 with any comparison operator, in
// either order, and as two arguments (`assertEquals(18, tools.size)`). It does not try to be a type checker: a count
// held in another name still gets through. Tests (src/test) are deliberately outside these raw rules; only main and
// testFixtures sources are scanned.
val toolsSizeExpr = """\w*[Tt]ools?\w*\s*\.\s*(?:size|count\s*\(\s*\))"""
val countSeparator = """(?:[=!]=|[<>]=?|,)"""
val rawRules = listOf(
    Rule(
        "app-domain name in source",
        Regex(
            """\b(LogFood\w*|log_food|MutationTier\w*|SYSTEM_PROMPT|TAG_DISAMBIGUATION\w*""" +
                """|find_tags|create_tag|edit_list_card|edit_text_card|SecondBrain|CalTracker)\b""",
        ),
    ),
    Rule(
        "hard-coded tool count",
        Regex(
            """\b(TOOL_COUNT|EXPECTED_TOOL_COUNT|NUM_TOOLS|toolCount)\b|\b1[78]\s+tools?\b""" +
                """|\b$toolsSizeExpr\s*$countSeparator\s*1[78]\b""" +
                """|\b1[78]\s*$countSeparator\s*$toolsSizeExpr""",
        ),
    ),
)

// Splits Kotlin source into (code with literal text blanked, comments only), both preserving line structure.
// Template expressions (`${ ... }`) inside string and raw-string literals are real code, so the lexer switches back
// to code mode for them (recursively: a template can contain strings, which can contain templates) and only the
// literal segments are blanked. Otherwise a banned construct could hide in `"${runCatching { x() }}"`.
class SourceSplitter(private val src: String) {
    private val code = StringBuilder()
    private val comments = StringBuilder()
    private var i = 0
    private val n = src.length

    fun split(): Pair<String, String> {
        lexCode(inTemplate = false)
        return code.toString() to comments.toString()
    }

    private fun keepNl(c: Char, sb: StringBuilder) {
        sb.append(if (c == '\n') '\n' else ' ')
    }

    // Blank `count` chars of the current position's line structure in both outputs.
    private fun blank(count: Int) {
        repeat(count) {
            keepNl(src[i], code)
            keepNl(src[i], comments)
            i++
        }
    }

    private fun atTemplateStart() = src[i] == '$' && i + 1 < n && src[i + 1] == '{'

    // Lexes code until EOF, or (inTemplate) until the `}` that closes the enclosing `${`.
    private fun lexCode(inTemplate: Boolean) {
        var braces = 0
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
                src.startsWith("/*", i) -> lexBlockComment()
                src.startsWith("\"\"\"", i) -> {
                    blank(3)
                    lexString(raw = true)
                }
                c == '"' -> {
                    blank(1)
                    lexString(raw = false)
                }
                c == '\'' && i + 2 < n && (src[i + 2] == '\'' || src[i + 1] == '\\') -> {
                    val end = src.indexOf('\'', i + 2).let { if (it < 0) n - 1 else it }
                    blank(end - i + 1)
                }
                inTemplate && c == '}' && braces == 0 -> {
                    blank(1)
                    return
                }
                else -> {
                    if (c == '{') braces++
                    if (c == '}') braces--
                    code.append(c)
                    keepNl(c, comments)
                    i++
                }
            }
        }
    }

    private fun lexBlockComment() {
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

    // Positioned just after the opening quote(s). Consumes through the closing quote(s).
    private fun lexString(raw: Boolean) {
        while (i < n) {
            when {
                raw && src.startsWith("\"\"\"", i) -> {
                    blank(3)
                    return
                }
                !raw && src[i] == '"' -> {
                    blank(1)
                    return
                }
                !raw && src[i] == '\n' -> return // unterminated: stop at end of line, like the compiler's recovery
                !raw && src[i] == '\\' -> blank(if (i + 1 < n) 2 else 1)
                atTemplateStart() -> {
                    blank(2)
                    lexCode(inTemplate = true)
                }
                else -> blank(1)
            }
        }
    }
}

fun splitCodeAndComments(src: String): Pair<String, String> = SourceSplitter(src).split()

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
    for (rule in rawRules) {
        rule.regex.findAll(text).forEach { m ->
            val line = text.substring(0, m.range.first).count { it == '\n' } + 1
            out += "$label:$line [${rule.id}] '${m.value.trim()}'"
        }
    }
    return out
}

val scanBanned = tasks.register("scanBannedConstructs") {
    group = "verification"
    // src/testFixtures exists only in :core (absent elsewhere, harmless): fixtures ship in no artifact but still must name no app.
    val files = fileTree("src") { include("main/**/*.kt", "main/**/*.java", "testFixtures/**/*.kt", "testFixtures/**/*.java") }
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

// ---- Structural half (all three published modules) ----

// JVM 11 class-file major. Deliberately a constant: no override knob, a knob equal to a wrongly-compiled truth would pass a bad artifact.
val expectedMajor = 55
val verifyBytecode = tasks.register("verifyBytecodeLevel") {
    group = "verification"
    val isAndroid = plugins.hasPlugin("com.android.library")
    val artifact: Provider<File> = if (isAndroid) {
        tasks.named("bundleReleaseAar").flatMap { (it as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile }.map { it.asFile }
    } else {
        tasks.named<Jar>("jar").flatMap { it.archiveFile }.map { it.asFile }
    }
    dependsOn(if (isAndroid) "bundleReleaseAar" else "jar")
    doLast {
        val file = artifact.get()
        val bad = mutableListOf<String>()
        var seen = 0
        fun checkClass(name: String, bytes: ByteArray) {
            if (!name.endsWith(".class") || name.startsWith("META-INF/versions/")) return
            seen++
            val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
            if (major != expectedMajor) bad += "$name major=$major"
        }
        ZipFile(file).use { z ->
            for (e in z.entries()) {
                if (e.isDirectory) continue
                if (e.name == "classes.jar") { // AAR
                    ZipInputStream(z.getInputStream(e)).use { zis ->
                        var ze = zis.nextEntry
                        while (ze != null) {
                            if (!ze.isDirectory) checkClass(ze.name, zis.readBytes())
                            ze = zis.nextEntry
                        }
                    }
                } else {
                    checkClass(e.name, z.getInputStream(e).readBytes())
                }
            }
        }
        if (seen == 0) throw GradleException("No class files found in $file (vacuous check)")
        if (bad.isNotEmpty()) throw GradleException("Non-JVM-11 class files in ${file.name}: $bad")
    }
}

val verifyExplicitApi = tasks.register("verifyExplicitApiStrict") {
    group = "verification"
    val modulePath = project.path
    val mode = provider { // reflection: applied scripts do not see the Kotlin plugin's classes
        val ext = project.extensions.getByName("kotlin")
        (ext.javaClass.getMethod("getExplicitApi").invoke(ext) as Enum<*>?)?.name
    }
    doLast {
        if (mode.orNull != "Strict") throw GradleException("$modulePath: explicitApi is '${mode.orNull}', expected Strict")
    }
}

// One-way graph: :sample -> {:providers, :keystore, :undo} -> :core; :undo depends on nothing; :voice-adapter depends on :core only.
val allowedEdges = mapOf(
    ":core" to emptySet<String>(),
    ":providers" to setOf(":core"),
    ":keystore" to setOf(":core"),
    ":undo" to emptySet<String>(),
    ":voice-adapter" to setOf(":core"),
)
val sampleRequiredEdges = setOf(":providers", ":keystore", ":undo")
val sampleAllowedEdges = setOf(":core", ":providers", ":keystore", ":undo")
val verifyModuleGraph = tasks.register("verifyModuleGraph") {
    group = "verification"
    val modulePath = project.path
    val edges = provider {
        project.configurations
            .flatMap { c -> c.dependencies.filterIsInstance<ProjectDependency>().map { it.path } }
            .filter { it != modulePath }
            .toSet()
    }
    // Resolved at execution time; null means :sample or its implementation configuration is missing (fail, never pass vacuously).
    val sampleEdges = provider {
        rootProject.findProject(":sample")
            ?.configurations?.findByName("implementation")
            ?.dependencies?.filterIsInstance<ProjectDependency>()?.map { it.path }?.toSet()
    }
    doLast {
        val extra = edges.get() - allowedEdges.getValue(modulePath)
        if (extra.isNotEmpty()) throw GradleException("$modulePath has forbidden project dependencies $extra")
        val sample = sampleEdges.orNull
            ?: throw GradleException(
                ":sample is missing required project edges $sampleRequiredEdges (graph :sample -> {:providers, :keystore, :undo} -> :core; :undo -> nothing)" +
                    " - :sample or its implementation configuration was not found",
            )
        val missing = sampleRequiredEdges - sample
        if (missing.isNotEmpty()) {
            throw GradleException(":sample is missing required project edges $missing (graph :sample -> {:providers, :keystore, :undo} -> :core; :undo -> nothing)")
        }
        val sampleExtra = sample - sampleAllowedEdges
        if (sampleExtra.isNotEmpty()) throw GradleException(":sample has forbidden project dependencies $sampleExtra")
    }
}
tasks.named("check") { dependsOn(verifyBytecode, verifyExplicitApi, verifyModuleGraph) }

// CLN-01 at the dependency level: no DI framework artifact may resolve on any published module's compile or runtime classpath.
val deniedDiGroups = setOf("com.google.dagger", "javax.inject", "jakarta.inject", "androidx.hilt", "dagger")
val verifyNoDi = tasks.register("verifyNoDiArtifacts") {
    group = "verification"
    val modulePath = project.path
    val classpathNames = if (plugins.hasPlugin("com.android.library")) {
        listOf("releaseCompileClasspath", "releaseRuntimeClasspath")
    } else {
        listOf("compileClasspath", "runtimeClasspath")
    }
    val classpaths = classpathNames.map { configurations.named(it) }
    doLast {
        val hits = classpaths.flatMap { cp ->
            cp.get().incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { it.group in deniedDiGroups }
                .map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
        }.distinct()
        if (hits.isNotEmpty()) throw GradleException("$modulePath resolves DI artifacts:\n" + hits.joinToString("\n") { "  $it" })
    }
}
tasks.named("check") { dependsOn(verifyNoDi) }

// Only the speech adapter may see the speech engine. No other published module may resolve a component of its group, nor
// request a dependency of it: the engine ships as an AAR, so on a JVM module the declaration cannot resolve and only the
// requested selector reveals it. The adapter's own gate is its publication check. (This script cannot share a value with
// a build file, so the group literal is held here as well as in the adapter's build file.)
if (project.name != "voice-adapter") {
    val sttGroup = "com.github.Ygaray.voice-engine-android"
    val verifySttConfined = tasks.register("verifySttConfined") {
        group = "verification"
        val modulePath = project.path
        val classpathNames = if (plugins.hasPlugin("com.android.library")) {
            listOf("releaseCompileClasspath", "releaseRuntimeClasspath")
        } else {
            listOf("compileClasspath", "runtimeClasspath")
        }
        val classpaths = classpathNames.map { configurations.named(it) }
        doLast {
            val hits = classpaths.flatMap { cp ->
                val result = cp.get().incoming.resolutionResult
                val components = result.allComponents.mapNotNull { it.moduleVersion }
                if (components.isEmpty()) {
                    throw GradleException("$modulePath :stt confinement check is vacuous: ${cp.name} resolved no component")
                }
                val resolved = components
                    .filter { it.group == sttGroup }
                    .map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
                val requested = result.allDependencies
                    .map { it.requested }
                    .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentSelector>()
                    .filter { it.group == sttGroup }
                    .map { "${cp.name}: requested ${it.group}:${it.module}" }
                resolved + requested
            }.distinct()
            if (hits.isNotEmpty()) {
                throw GradleException("$modulePath resolves the :stt group ($sttGroup):\n" + hits.joinToString("\n") { "  $it" })
            }
        }
    }
    tasks.named("check") { dependsOn(verifySttConfined) }
}

// SC4: no ML runtime on a published module's compile or runtime classpath. Scoped by module name (never a shared
// rule) so a dedicated on-device module is not blocked by its own gates.
if (project.name in setOf("core", "providers", "keystore", "undo", "voice-adapter")) {
    val deniedMlGroupPrefixes = listOf("com.google.ai.edge", "com.google.mediapipe", "org.tensorflow", "com.google.mlkit")
    val deniedMlNameTokens = listOf("litert", "tflite")
    val verifyNoMl = tasks.register("verifyNoMlArtifacts") {
        group = "verification"
        val modulePath = project.path
        val classpathNames = if (plugins.hasPlugin("com.android.library")) {
            listOf("releaseCompileClasspath", "releaseRuntimeClasspath")
        } else {
            listOf("compileClasspath", "runtimeClasspath")
        }
        val classpaths = classpathNames.map { configurations.named(it) }
        doLast {
            fun denied(group: String?, name: String?): Boolean =
                (group != null && deniedMlGroupPrefixes.any { group.startsWith(it) }) ||
                    (name != null && deniedMlNameTokens.any { name.contains(it, ignoreCase = true) })
            val hits = classpaths.flatMap { cp ->
                val result = cp.get().incoming.resolutionResult
                // Requested selectors too: an unresolved or offline declaration must still be caught.
                val resolved = result.allComponents
                    .mapNotNull { it.moduleVersion }
                    .filter { denied(it.group, it.name) }
                    .map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
                val requested = result.allDependencies
                    .map { it.requested }
                    .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentSelector>()
                    .filter { denied(it.group, it.module) }
                    .map { "${cp.name}: requested ${it.group}:${it.module}" }
                resolved + requested
            }.distinct()
            if (hits.isNotEmpty()) throw GradleException("$modulePath resolves ML artifacts:\n" + hits.joinToString("\n") { "  $it" })
        }
    }
    tasks.named("check") { dependsOn(verifyNoMl) }
}

// :undo depends on nothing but the Kotlin standard library: no project edge and no other library, main or runtime.
if (project.name == "undo") {
    val undoAllowed = setOf("org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations")
    val verifyUndoZeroDeps = tasks.register("verifyUndoZeroDeps") {
        group = "verification"
        val classpaths = listOf("compileClasspath", "runtimeClasspath").map { configurations.named(it) }
        doLast {
            val offenders = mutableListOf<String>()
            for (cp in classpaths) {
                val result = cp.get().incoming.resolutionResult
                val components = result.allComponents.filter { it != result.root }
                val sawStdlib = components.any { c ->
                    c.moduleVersion?.let { "${it.group}:${it.name}" } == "org.jetbrains.kotlin:kotlin-stdlib"
                }
                if (!sawStdlib) throw GradleException(":undo classpath ${cp.name} resolved no kotlin-stdlib (vacuous check)")
                for (c in components) {
                    val id = c.id
                    if (id is org.gradle.api.artifacts.component.ProjectComponentIdentifier) {
                        offenders += "${cp.name}: project ${id.projectPath}"
                    } else {
                        val mv = c.moduleVersion
                        if (mv != null && "${mv.group}:${mv.name}" !in undoAllowed) {
                            offenders += "${cp.name}: ${mv.group}:${mv.name}:${mv.version}"
                        }
                    }
                }
            }
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    ":undo depends on more than the Kotlin standard library:\n" +
                        offenders.distinct().joinToString("\n") { "  $it" },
                )
            }
        }
    }
    tasks.named("check") { dependsOn(verifyUndoZeroDeps) }
}

// Repo-wide checks, hosted on :core so they run once.
if (project.name == "core") {
    // :core has no HTTP, Android, DI or other-hub dependency by classpath, not by convention (L7/A7).
    // A new :core dependency must extend this allowlist deliberately in the same change.
    val coreAllowed = setOf(
        "org.jetbrains.kotlin:kotlin-stdlib",
        "org.jetbrains:annotations",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm",
        "org.jetbrains.kotlinx:kotlinx-serialization-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm",
        "org.jetbrains.kotlinx:kotlinx-serialization-bom",
    )
    val verifyCoreDeps = tasks.register("verifyCoreDependencyAllowlist") {
        group = "verification"
        val classpaths = listOf("compileClasspath", "runtimeClasspath").map { configurations.named(it) }
        doLast {
            val offenders = classpaths.flatMap { cp ->
                cp.get().incoming.resolutionResult.allComponents
                    .filter { it.id !is org.gradle.api.artifacts.component.ProjectComponentIdentifier } // :core itself
                    .mapNotNull { it.moduleVersion }
                    .filter { "${it.group}:${it.name}" !in coreAllowed }
                    .map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
            }.distinct()
            if (offenders.isNotEmpty()) {
                throw GradleException(":core classpath has non-allow-listed artifacts:\n" + offenders.joinToString("\n") { "  $it" })
            }
        }
    }
    tasks.named("check") { dependsOn(verifyCoreDeps) }

    val verifyNoBaseline = tasks.register("verifyNoDetektBaseline") {
        group = "verification"
        val root = rootProject.projectDir
        doLast {
            val skip = setOf("build", ".gradle", ".git", ".planning", "graphify-out", ".kotlin")
            val hits = mutableListOf<String>()
            // Wiring forms: assignment, property set/convention, a function-style call, a detekt-prefixed baseline name, and
            // the detekt CLI flags. (Described in words so this very file does not match its own patterns.)
            // A baseline XML stored under an arbitrary name is caught by content.
            val wiring = listOf(
                Regex("""\bbaseline\b\s*(=|\.set\(|\.convention\(|\()"""),
                Regex("""detekt[-_.]baseline"""),
                Regex("""["'](-ba|--baseline)\b"""),
            )
            root.walkTopDown().onEnter { it.name !in skip }.forEach { f ->
                if (!f.isFile) return@forEach
                if (f.name.matches(Regex("(?i).*baseline.*\\.xml"))) {
                    hits += "baseline file: ${f.relativeTo(root)}"
                } else if (f.name.endsWith(".xml") && f.length() < 1_000_000 && f.readText().contains("<SmellBaseline")) {
                    hits += "baseline file (by content): ${f.relativeTo(root)}"
                }
                if (f.name.endsWith(".kts") && wiring.any { it.containsMatchIn(f.readText()) }) {
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

if (project.name == "providers") {
    // A1 compile floor: never raise. Consumers pick their own OkHttp; the 5.x pin lives only in :sample.
    val floor = "4.12.0"
    val verifyFloor = tasks.register("verifyOkHttpCompileFloor") {
        group = "verification"
        val classpaths = listOf("compileClasspath", "testCompileClasspath").map { configurations.named(it) }
        doLast {
            val errs = mutableListOf<String>()
            var seen = 0
            classpaths.forEach { cp ->
                cp.get().incoming.resolutionResult.allComponents
                    .mapNotNull { it.moduleVersion }
                    .filter { it.group == "com.squareup.okhttp3" && it.name in setOf("okhttp", "okhttp-jvm", "mockwebserver") }
                    .forEach {
                        seen++
                        if (it.version != floor) errs += "${cp.name}: ${it.group}:${it.name}:${it.version} (must be $floor)"
                    }
            }
            if (seen == 0) errs += "no okhttp found on compile classpaths (vacuous)"
            if (errs.isNotEmpty()) throw GradleException(errs.joinToString("\n"))
        }
    }
    tasks.named("check") { dependsOn(verifyFloor) }
}
