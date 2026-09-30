---
phase: 01-scaffold-publishing-proof
fixed_at: 2026-09-30T22:00:00Z
review_path: .planning/phases/01-scaffold-publishing-proof/01-REVIEW.md
iteration: 1
findings_in_scope: 16
fixed: 16
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-09-30
**Source review:** .planning/phases/01-scaffold-publishing-proof/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 16 (9 Warning, 7 Info; fix_scope = all)
- Fixed: 16
- Skipped: 0

One commit per finding, in severity order (`fix(01): <ID> ...`). No push, no tag, and none of the pre-existing dirty
files (`.planning/graphs/`, `.planning/config.json`, `.planning/v1.0-MILESTONE-RUN.md`, `.planning/state.json`,
`.planning/milestone.lock`, `.gsd-stage-plan.done.json`) were staged.

**Verification location:** the MAIN checkout (`/home/yahir/Projects/Reusable/android/voice-action-engine`, branch `main`),
not an isolated worktree. The orchestrator's repo rules required committing on `main` with explicit paths, and a
hand-rolled worktree has no `local.properties` (Android SDK path), so the `:keystore`/`:sample` gates could not run there.
After the last fix:
- `./gradlew check` : BUILD SUCCESSFUL
- `scripts/verify-negative-controls.sh` : 69 ok, `negative-control failures: 0` (includes all new controls below)
- `scripts/verify-repo-hygiene.sh` : HYGIENE OK
- `scripts/verify-api-dump.sh` : API DUMP PROOF OK (run after IN-05)
- `scripts/jitpack-dry-run.sh` : DRY RUN OK, no leaked temp dir (run after IN-06)
- `scripts/jitpack-live-probe.sh 5fc723786b` (SKIP_CONSUMER=1, read-only GETs against the already-built ref) : PASS;
  with `FORBID_RE=keystore` it exits 4 "forbidden artifact", so the reworked guard is still non-vacuous (run after WR-05)

Items marked "requires human verification" changed gate logic. They passed their negative controls, but a human should
confirm the semantics are what is wanted.

## Fixed Issues

### WR-01: Scanner `println`/`print` rule is bypassed by a fully-qualified call

**Files modified:** `gradle/invariants.gradle.kts`, `config/negative-controls/print-fq.kt.txt` (new), `scripts/verify-negative-controls.sh`
**Commit:** b8dfa2e
**Status:** fixed: requires human verification
**Applied fix:** One rule `kotlin.io print (FQ or import)` matching `kotlin.io.print|println` anywhere. It also covers the alias import
(`import kotlin.io.println as p`) because the import line contains the same text, so the separate import rule from the
suggestion was folded in rather than emitting two findings per line. New scanner control `print-fq.kt.txt` and a
per-module source plant in `verify-negative-controls.sh`.

### WR-02: Scanner blanks the code inside string templates, so banned constructs hide there

**Files modified:** `gradle/invariants.gradle.kts`, `config/negative-controls/string-template.kt.txt` (new), `config/negative-controls/clean.kt.txt`, `scripts/verify-negative-controls.sh`
**Commit:** 7926cdc
**Status:** fixed: requires human verification
**Applied fix:** Replaced the flat `splitCodeAndComments` function with a small recursive lexer class (`SourceSplitter`).
On `${` inside a string or raw string it switches back to code mode (tracking `{}` depth) until the matching `}`, and only the
literal segments are blanked. Nested quotes inside a template no longer desynchronise it. New control
`string-template.kt.txt` (banned constructs inside templates, including a raw-string template and a nested-quote template)
and a nested-template case in `clean.kt.txt` proving text after a template is still blanked. Per-module plant added.

### WR-03: `jitpack-live-probe.sh` dies on any transient curl failure

**Files modified:** `scripts/jitpack-live-probe.sh`
**Commit:** 4d426d3
**Applied fix:** The poll `curl` is `|| true` (an empty body parses to status `unparsed`, so polling continues). `build.log`
fetch now `|| fail`, and the three `http_code` assignments fall back to `000` so they report a normal assertion failure
(exit 4) instead of a raw curl exit code.

### WR-04: On JitPack `error`, the build-log tail is discarded

**Files modified:** `scripts/jitpack-live-probe.sh`
**Commit:** 4e7c0b2
**Applied fix:** The tail is now printed through `say` (which honours `EVIDENCE_FILE`), so "log tail above" is true.

### WR-05: `set -e`/`pipefail` interactions in the live probe

**Files modified:** `scripts/jitpack-live-probe.sh`
**Commit:** 1851899
**Applied fix:** `jq '.modules[]?'`; every negative guard (api modules, install command, Found artifact, aggregator POM,
testFixtures in POM/module) now matches against a captured string or file arguments instead of `producer | grep -q`. The
"Running install command" listing is captured and an empty result is an explicit `fail` instead of a silent pipefail
abort. The `awk | grep -q` positive checks got the same treatment (they could spuriously fail on SIGPIPE).

### WR-06: `NoNetworkGuard` overstates what it proves

**Files modified:** `core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/NoNetworkGuard.kt`
**Commit:** bbe43ac
**Applied fix:** KDoc rewritten: it is a tripwire on the default `ProxySelector`, with the un-seen paths (NIO, own-selector
clients, direct DNS, pooled connections, pre-built `OkHttpClient`), the non-thread-safe `during`, and the swallowable
`AssertionError` spelled out. The review's second suggestion (a stronger layer such as an `OkHttpClient` with a throwing `Dns`)
is documented as belonging next to the transport tests rather than implemented: `:core` has no HTTP dependency by design
(L7/A7), so that layer can only live in `:providers` tests in the later provider phases.

### WR-07: The Metalava compatibility gate is fail-open when `api.txt` is missing

**Files modified:** `build.gradle.kts`, `scripts/verify-negative-controls.sh`
**Commit:** 87230d6
**Status:** fixed: requires human verification
**Applied fix:** New per-module `verifyApiDumpPresent` task wired into `check`: if any `v*` git tag exists and the module's
`api.txt` is missing it fails loudly. The pre-release skip is unchanged (no tag yet, so no behaviour change today). If git is
unavailable the task stays in the pre-release (pass) state. `-PvaeAssumeReleased` is a stricter-only lever used by three new
negative controls (one per module). The v1.0.0 cut should also run `PRE_RELEASE=0 scripts/verify-repo-hygiene.sh` (see IN-04).

### WR-08: The no-baseline gate only recognises `baseline =` and XML file names

**Files modified:** `gradle/invariants.gradle.kts`, `scripts/verify-negative-controls.sh`
**Commit:** 20692f2
**Status:** fixed: requires human verification
**Applied fix:** Wiring detection now covers assignment, `.set(`/`.convention(`/call forms, a `detekt[-_.]baseline` name, and
the `-ba`/`--baseline` CLI flags in `*.kts`; an XML file containing `<SmellBaseline` is flagged regardless of its name. The
comment describing this was worded so the file does not match its own patterns. Two new controls: baseline XML under an
arbitrary name, and `baseline.set(...)` wiring planted in a build file (backup + restore).

### WR-09: JDK 17 API surface is not restricted for JVM 11 bytecode

**Files modified:** `core/build.gradle.kts`, `providers/build.gradle.kts`, `keystore/build.gradle.kts`, `scripts/verify-negative-controls.sh`
**Commit:** f2921a9
**Applied fix:** `-Xjdk-release=11` added to `compilerOptions` in all three modules. Verified empirically that a JDK 16 call
(`Stream.toList()`) now fails to compile in `:core` and `:providers` ("Unresolved reference"); new source plants prove it.
`:keystore` compiles against `android.jar`, which legitimately carries newer JDK APIs at minSdk 35, so the flag is applied there
for consistency but has no observable effect and no plant. Side effect handled: Kotlin rejects `-Xjdk-release=11` combined with
`-jvm-target 17`, which made the existing "core compiled at JVM 17" control go red for the wrong reason, so that control's
`sed` now flips `jdk-release` too and it again reaches `verifyBytecodeLevel` ("Non-JVM-11 class files"). `expect_red` gained an
optional `MARKER` override for the new plants.

### IN-01: Unused `testFixturesApi(libs.coroutines.core)`

**Files modified:** `core/build.gradle.kts`
**Commit:** 1718193
**Applied fix:** Line removed. Test fixtures, `:core` tests and `:providers` tests still compile.

### IN-02: `jitpack-dry-run.sh` artifact check matches the sandbox path

**Files modified:** `scripts/jitpack-dry-run.sh`
**Commit:** 6e3196e
**Applied fix:** `find "$M2" -mindepth 1 \( -iname '*sample*' -o -iname '*test-fixtures*' \) -print`.

### IN-03: Root build reads `VERSION` without a blank check

**Files modified:** `build.gradle.kts`
**Commit:** c1a43cd
**Applied fix:** `.map { it.trim() }.filter { it.isNotEmpty() }` before falling back to `engineVersion`. Checked: empty and
whitespace-only `VERSION` give `0.0.0-local`, `VERSION=abc` still wins.

### IN-04: `verify-repo-hygiene.sh` checks (c) and (d) will hard-fail at the v1.0.0 cut

**Files modified:** `scripts/verify-repo-hygiene.sh`
**Commit:** 1c1b3f5
**Applied fix:** `PRE_RELEASE` switch (default 1, current behaviour). With `PRE_RELEASE=0` the api.txt and tag prohibitions are
lifted and replaced by a requirement that each published module tracks its `api.txt`; fixture and baseline prohibitions hold in
both modes. Header documents this. Checked both modes on the current tree.

### IN-05: `verify-api-dump.sh` final "real tree untouched" check is vacuous

**Files modified:** `scripts/verify-api-dump.sh`
**Commit:** 34c4e8e
**Applied fix:** The closing check now compares `git status --porcelain` (excluding `.planning/` and `graphify-out/`, which change
independently) before and after; an `EXIT` trap removes the copy unless `KEEP_WORK=1`.

### IN-06: Temp work directories are never cleaned up

**Files modified:** `scripts/jitpack-consumer-probe.sh`, `scripts/jitpack-dry-run.sh`, `scripts/jitpack-live-probe.sh`
**Commit:** 319bd8c
**Applied fix:** `EXIT` traps remove `$WORK` unless `KEEP_WORK=1`; success messages no longer print a path that is about to be
deleted. In the consumer probe the `wrapper` symlink into `~/.gradle` is unlinked before the recursive remove so it can never
reach the real distribution directory. Confirmed no temp dir is left after a full dry run.

### IN-07: Scanner flags declarations named `print`/`println`

**Files modified:** `gradle/invariants.gradle.kts`, `config/negative-controls/clean.kt.txt`
**Commit:** 883e8ef
**Applied fix:** Added `(?<!\bfun\s{1,16})` to the println/print rule; `fun print(x: Int)` is now in the clean control. A later
bare call to such a function is still flagged, and the FQ rule from WR-01 still catches a forwarding `kotlin.io.println`.

## Skipped Issues

None.

## Notes for the orchestrator

- About 127 `/tmp/tmp.*` directories from earlier runs of the old (non-cleaning) scripts remain on the host, some holding full
  Gradle homes. They are not from this fix run and were left alone; they can be deleted by hand.
- The v1.0.0 cut checklist should now include: commit the three `api.txt` dumps, run `PRE_RELEASE=0 scripts/verify-repo-hygiene.sh`,
  and expect `verifyApiDumpPresent` to become active the moment the first `v*` tag exists.

---

_Fixed: 2026-09-30_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
