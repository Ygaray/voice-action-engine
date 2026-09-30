---
phase: 01-scaffold-publishing-proof
reviewed: 2026-09-30T00:00:00Z
depth: standard
files_reviewed: 38
files_reviewed_list:
  - .gitignore
  - ECOSYSTEM.md
  - README.md
  - build.gradle.kts
  - settings.gradle.kts
  - gradle.properties
  - gradle/libs.versions.toml
  - gradle/invariants.gradle.kts
  - jitpack.yml
  - config/detekt/detekt.yml
  - config/negative-controls/clean.kt.txt
  - config/negative-controls/detekt/ForbiddenImports.kt
  - config/negative-controls/di-fq.kt.txt
  - config/negative-controls/packages.kt.txt
  - config/negative-controls/planning-ids.kt.txt
  - config/negative-controls/print.kt.txt
  - config/negative-controls/runCatching.kt.txt
  - core/build.gradle.kts
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CoreModule.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ScriptedHarnessTest.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/NoNetworkGuard.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingSink.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedResponses.kt
  - keystore/build.gradle.kts
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreModule.kt
  - providers/build.gradle.kts
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersModule.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt
  - sample/build.gradle.kts
  - sample/src/main/AndroidManifest.xml
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleModule.kt
  - scripts/jitpack-consumer-probe.sh
  - scripts/jitpack-dry-run.sh
  - scripts/jitpack-live-probe.sh
  - scripts/verify-api-dump.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-repo-hygiene.sh
findings:
  critical: 0
  warning: 9
  info: 7
  total: 16
status: resolved
---

# Phase 1: Code Review Report

**Reviewed:** 2026-09-30
**Depth:** standard
**Files Reviewed:** 38
**Status:** issues_found

## Summary

Phase 1 is build scaffolding, publishing wiring, and verification gates. Almost no production code exists (the
main sources are empty internal marker objects). The real surface is therefore the gates: the source scanner in
`gradle/invariants.gradle.kts`, the bytecode, graph and allowlist checks, and the shell proofs.

No shipping-blocker defects were found. The published artifacts, the module graph, the OkHttp floor handling and
the testFixtures exclusion all look sound on read-through. The defects are in the gates themselves. Several can
pass silently when they should fail (fail-open), and several scripts abort silently or give misleading
diagnostics. Because this repo's value in Phase 1 is "the gates are trustworthy", these are rated WARNING rather
than INFO.

I did not execute anything. All findings come from static reading.

## Warnings

### WR-01: Scanner `println`/`print` rule is bypassed by a fully-qualified call

**File:** `gradle/invariants.gradle.kts:15`
**Issue:** The rule `(?<![\w.])(println|print)\s*\(` refuses to match when the name is preceded by `.`. That
was meant to skip member calls such as `out.print(`, but it also lets `kotlin.io.println("secret")` and
`kotlin.io.print(...)` through. detekt's `ForbiddenMethodCall` is not a fallback here, because it needs type
resolution and the project does not use it. A FQ call is therefore the one way to reach stdout that no gate
sees, and the project rule is that keys, transcripts and tool args never reach a sink. The negative controls
(`print.kt.txt`) do not cover this form, so the hole is invisible to the gate that is meant to prove coverage.
Import aliases (`import kotlin.io.println as p`) are not caught either, because the import line itself is not a
banned construct.
**Fix:** Add explicit rules and a control for each.
```kotlin
Rule("kotlin.io print (FQ)", Regex("""\bkotlin\s*\.\s*io\s*\.\s*(println|print)\b""")),
Rule("print import/alias", Regex("""\bimport\s+kotlin\s*\.\s*io\s*\.\s*(println|print)\b""")),
```
Then add a `print-fq.kt.txt` control with `// EXPECT:` naming the new rules.

### WR-02: Scanner blanks the code inside string templates, so banned constructs hide there

**File:** `gradle/invariants.gradle.kts:88-103` (`splitCodeAndComments`, string branch)
**Issue:** The `"` branch blanks everything up to the closing quote and treats `${...}` as literal text. Real
Kotlin code inside a template expression is invisible to every rule. For example `"${runCatching { x() }}"`
or `"${System.out.println(1)}"` passes `scanBannedConstructs`. The same blanking applies to raw strings
(lines 75-87). Nested quotes inside a template (`"${a["k"]}"`) also desynchronise the lexer. It stays balanced
only by luck, because the quote count is even.
**Fix:** Track template depth: on `${`, switch back to code mode until the matching `}` and only blank the
literal segments. At minimum, treat `${...}` bodies as code and add a control such as
`val s = "${runCatching { 1 }}"` expecting `runCatching`.

### WR-03: `jitpack-live-probe.sh` dies on any transient curl failure despite being documented as re-pollable

**File:** `scripts/jitpack-live-probe.sh:35, 54, 69, 86`
**Issue:** `set -e` is active and `json="$(curl -s -m 30 "$API")"` has no `|| true`. A timeout (exit 28) or a
connection reset during the 25-minute poll terminates the script with a raw curl exit code and no message. The
same happens at `curl ... > "$LOG"` (line 54) and the `code="$(curl ...)"` assignments. The header advertises
an exit-code contract (0/2/3/4/5) and idempotent re-polling, and this path violates both. The initial trigger
calls (lines 31, 44) are guarded with `|| true`, which shows the intent.
**Fix:** Tolerate transient poll failures and map the others to `fail`.
```bash
json="$(curl -s -m 30 "$API" || true)"
...
curl -s -m 120 "https://jitpack.io/$GROUP_PATH/$REF/build.log" > "$LOG" || fail "could not fetch build.log"
code="$(curl -s -o "$WORK/$m.pom" -m 60 -w '%{http_code}' "$base.pom" || echo 000)"
```

### WR-04: On JitPack `error`, the build-log tail is discarded while the message says it is "above"

**File:** `scripts/jitpack-live-probe.sh:40-41`
**Issue:** `... | tail -60 | tee -a "${EVIDENCE_FILE:-/dev/null}" >/dev/null` sends the tee's stdout to
`/dev/null`. When `EVIDENCE_FILE` is unset (the default) the log tail is thrown away. The next line then
reports `fail "JitPack reports status=error (log tail above; ...)"`. A failed JitPack ref is cached
permanently, so this log is the only diagnostic a maintainer gets, and the message claims it was printed.
**Fix:** Print it through `say`, which already handles the `EVIDENCE_FILE` logic.
```bash
curl -s -m 60 "https://jitpack.io/$GROUP_PATH/$REF/build.log" | tail -60 | while IFS= read -r l; do say "   log: $l"; done
```

### WR-05: `set -e`/`pipefail` interactions cause silent aborts and fail-open negative guards in the live probe

**File:** `scripts/jitpack-live-probe.sh:49, 52, 56-58, 63, 86`
**Issue:** Two distinct problems with the same cause.
(a) Silent aborts. Lines 56 and 58 are `grep ... | sed ... | while read`. Under `pipefail`, a `grep` that finds
nothing (no "Running install command" or "Found artifact" line) exits 1 and kills the script with no `fail`
message and exit 1, which is outside the documented exit-code set. The same applies to `jq -r '.modules[]'`
when `.modules` is null (line 49).
(b) Fail-open guards. The "must NOT contain sample/test-fixtures" checks are `producer | grep -Eiq ...` inside
`if` (lines 52, 57, 63, 86). `grep -q` exits at the first match, and the producer may then die of SIGPIPE.
Under `pipefail` the pipeline status becomes 141, the `if` reads "no match", and the forbidden-artifact guard
passes. The output is tiny, so the race is unlikely today, but a guard whose only job is to fail should not
depend on timing.
**Fix:** Capture the text first and match against the variable.
```bash
mods="$(jq -r '.modules[]?' <<<"$json")"
if grep -Eiq "$FORBID_RE" <<<"$mods"; then fail "api modules include a forbidden artifact ($FORBID_RE)"; fi
```
For (a), append `|| true` to the listing pipelines, or test the captured string and `fail` explicitly.

### WR-06: `NoNetworkGuard` overstates what it proves

**File:** `core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/NoNetworkGuard.kt:9-13, 35-42`
**Issue:** The KDoc says the tripwire fails any code asking for a proxy route, "which every JDK connection does
first". That is not true in general. NIO `SocketChannel`, `java.net.http.HttpClient` with its own selector, an
`OkHttpClient` constructed before `during {}` (it captures `ProxySelector.getDefault()` at build time), pooled
keep-alive connections, and direct `InetAddress` DNS lookups never consult the default selector. Later phases
will rely on this fixture to show "zero network", and a provider test could pass while hitting the network.
It is also a process-global mutation with no synchronisation. Parallel test forks or threads can observe or
restore the wrong selector, and the `AssertionError` it throws can be swallowed by any `catch (Throwable)` in
code under test.
**Fix:** Narrow the claim in the KDoc ("catches clients that resolve a proxy at connect time, created inside
the block"). Add a second, stronger layer for the real transport tests, such as an `OkHttpClient` with a
`Dns` that throws. Document that `during` is not safe under parallel execution.

### WR-07: The Metalava compatibility gate is fail-open when `api.txt` is missing

**File:** `build.gradle.kts:41-45`
**Issue:** `onlyIf("api.txt exists")` skips every `metalavaCheckCompatibility*` task when the file is absent.
That is correct before `v1.0.0`. After the tag it means that deleting or forgetting `api.txt`, or a path mistake,
turns the additive-only API guarantee (§11 rule 2) into a silent no-op, and `check` stays green. Nothing
distinguishes "pre-release, intentionally absent" from "post-release, lost". Meanwhile
`verify-repo-hygiene.sh` actively forbids the file, so there is no state in which a missing dump is ever
reported.
**Fix:** Make the skip conditional on the release state rather than on the file. For example, add a
`verifyApiDumpPresent` task in `check` that fails when `api.txt` is absent and a `v*` tag exists, or key the
`onlyIf` off a committed marker that the v1.0.0 cut flips. Record the decision in the v1.0.0 cut checklist.

### WR-08: The no-baseline gate only recognises `baseline =` and XML file names

**File:** `gradle/invariants.gradle.kts:306-311`
**Issue:** The wiring check is `\bbaseline\s*=` over `*.kts`. A baseline wired with
`baseline.set(file("x"))`, `baseline.convention(...)`, or a CLI argument (`-ba`) is not detected. A baseline
stored under a name without "baseline" and an `.xml` suffix is missed too. For a policy the project calls
"zero baseline, never bank debt", the detector is easy to sidestep by accident.
**Fix:** Broaden the wiring regex to `\bbaseline\b\s*(=|\.set\(|\.convention\(|\()` and also flag
`detekt-baseline`, or assert on the resolved configuration instead. For example, in the `detekt` plugin hook,
fail if `baseline.isPresent` (`baseline` is a `RegularFileProperty` in detekt 1.23).

### WR-09: JDK 17 API surface is not restricted for JVM 11 bytecode

**File:** `core/build.gradle.kts:11-21`, `providers/build.gradle.kts:9-19`, `keystore/build.gradle.kts:25`
**Issue:** The modules set `jvmTarget = JVM_11` and Java 11 source/target compatibility, but nothing restricts
the JDK API surface to 11 (no `-Xjdk-release=11`, no `release` option). The build JDK is 17, and Kotlin
resolves against the JDK 17 class library. Code can call JDK 12-17 APIs (for example `Stream.toList()`,
`String.transform`) and still produce class-file major 55. `verifyBytecodeLevel` only inspects the major
version, so it passes. A consumer on an older runtime would then hit `NoSuchMethodError` at runtime, which is
the failure class the JVM 11 constraint exists to prevent.
**Fix:** Add `freeCompilerArgs.add("-Xjdk-release=11")` to `compilerOptions` in all three modules (and
`options.release.set(11)` for any Java), or add a dedicated `verifyJdkApiLevel` check (for example `jdeps`).

## Info

### IN-01: Unused `testFixturesApi(libs.coroutines.core)`

**File:** `core/build.gradle.kts:26`
**Issue:** No testFixtures source uses coroutines. The fixtures (`ScriptedResponses`, `RecordingSink`,
`NoNetworkGuard`) are all blocking and non-suspending. `testFixturesApi` leaks coroutines into every consumer's
test classpath for no reason.
**Fix:** Remove the line.

### IN-02: `jitpack-dry-run.sh` artifact check matches the sandbox path, not just artifact names

**File:** `scripts/jitpack-dry-run.sh:35`
**Issue:** `find "$M2" -ipath '*sample*' -o -iname '*test-fixtures*'` matches against the full path, including
the `mktemp -d` prefix. If `TMPDIR` ever contains "sample", the check reports a false failure. The
`-o`/implicit `-print` precedence is also easy to misread.
**Fix:** Use `find "$M2" -mindepth 1 \( -iname '*sample*' -o -iname '*test-fixtures*' \) -print`.

### IN-03: Root build reads the generic env var `VERSION` without a blank check

**File:** `build.gradle.kts:13-15`
**Issue:** `providers.environmentVariable("VERSION")` wins over the Gradle property even when it is set to an
empty string, which produces a publication with a blank version. `VERSION` is also a name other tools export.
JitPack documents the variable, so the lookup is right, but it is unguarded.
**Fix:** `providers.environmentVariable("VERSION").map { it.trim() }.filter { it.isNotEmpty() }.orElse(...)`.

### IN-04: `verify-repo-hygiene.sh` checks (c) and (d) will hard-fail at the v1.0.0 cut

**File:** `scripts/verify-repo-hygiene.sh:49-55`
**Issue:** The script forbids any `api.txt` and any git tag. That is correct for Phase 1, but the script is
described as runnable "at any time", and the v1.0.0 cut requires both. Whoever cuts the tag will either see it
fail or weaken it ad hoc.
**Fix:** Note in the header that (c)/(d) are pre-release assertions, or gate them on a `PRE_RELEASE=1` switch
that the cut step clears.

### IN-05: `verify-api-dump.sh` final "real tree untouched" check is vacuous

**File:** `scripts/verify-api-dump.sh:53`
**Issue:** The script only ever writes into `$COPY`, so the closing `api.txt` scan of the real tree cannot fail
because of this script. It gives false assurance. Also `$COPY` (a full tree copy plus build outputs) is never
removed.
**Fix:** Delete the check (hygiene script (c) already covers it), or snapshot `git status --porcelain` before
and after. Add `trap 'rm -rf "$(dirname "$COPY")"' EXIT`.

### IN-06: Temp work directories are never cleaned up

**File:** `scripts/jitpack-consumer-probe.sh:15`, `scripts/jitpack-dry-run.sh:12`, `scripts/jitpack-live-probe.sh:71`
**Issue:** Each run leaves a `mktemp -d` directory holding a Gradle home, a full repo copy or downloaded POMs.
The probe runs with an empty dependency cache, so each leaked directory is hundreds of MB.
**Fix:** Add an `EXIT` trap that removes `$WORK`, guarded by a `KEEP_WORK=1` override for debugging. The
success messages already print the path.

### IN-07: Scanner flags declarations named `print`/`println`

**File:** `gradle/invariants.gradle.kts:15`
**Issue:** `(?<![\w.])(println|print)\s*\(` also matches `fun print(` and `fun println(` declarations. A
legitimate domain method called `print(` would fail the gate. The project is domain-free, so this is unlikely
now, but the false positive has no suppression path.
**Fix:** Exclude a preceding `fun\s+` with a lookbehind, or document the limitation in the rule comment.

---

_Reviewed: 2026-09-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
