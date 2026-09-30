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

// One-way graph: :sample -> {:providers, :keystore} -> :core.
val allowedEdges = mapOf(":core" to emptySet<String>(), ":providers" to setOf(":core"), ":keystore" to setOf(":core"))
val sampleRequiredEdges = setOf(":providers", ":keystore")
val sampleAllowedEdges = setOf(":core", ":providers", ":keystore")
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
                ":sample is missing required project edges $sampleRequiredEdges (graph :sample -> {:providers, :keystore} -> :core)" +
                    " - :sample or its implementation configuration was not found",
            )
        val missing = sampleRequiredEdges - sample
        if (missing.isNotEmpty()) {
            throw GradleException(":sample is missing required project edges $missing (graph :sample -> {:providers, :keystore} -> :core)")
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
