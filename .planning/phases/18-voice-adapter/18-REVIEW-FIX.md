---
phase: 18-voice-adapter
fixed_at: 2026-10-06T00:00:00Z
review_path: .planning/phases/18-voice-adapter/18-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 5
skipped: 2
status: resolved
---

# Phase 18: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/18-voice-adapter/18-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (0 critical, 4 warning, 3 info; scope `all`)
- Fixed: 5
- Skipped: 2 (both documented acceptable-skips, see below; no open blocker/critical/high)

`status: resolved` because every finding is either fixed or a documented acceptable-skip with an owner. WR-02 is a
deferral (not a no-op): it carries a forward note below.

Verification ran in an isolated git worktree (`.claude/worktrees/rf-18-*`, since removed), single Gradle process with
`--no-daemon` flags, MemAvailable ~9.9 GB before each run. It is not reproducible from the main checkout's build
directories, but the commits are fast-forwarded onto `gsd/phase-18-voice-adapter` (HEAD `c45d3b9`).

- `./gradlew :voice-adapter:test`: exit 0, 33 tests, 0 failures, 0 skipped (AdapterApiShapeTest 5, FinalSegmentMappingTest 10,
  LanguageLabelsTest 10, RedactionTest 5, SttFreeFacadeTest 3 new).
- `./gradlew :voice-adapter:detekt :core:detekt :voice-adapter:test`: exit 0 (after fixing two MaxLineLength findings and two
  Kotlin nullability warnings in the new test before committing).
- `./gradlew :core:test --tests '*ActionEvent*'`: exit 0, ActionEventTest 7 tests, 0 failures.
- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=107`; `scripts/verify-stt-confinement.sh`:
  `STT CONFINEMENT OK checks=6` (read-only runs, scripts not edited).
- No `api.txt` change was needed (KDoc and one private-body refactor only; `voice-adapter/api.txt` stays header-only).

## Fixed Issues

### WR-01: `ActionEvent.toString` contradicts its own privacy rationale

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt`
**Commit:** 149e906
**Applied fix:** Took the doc-only route (per orchestrator guidance and RT-01, which permits ids). The KDoc no longer claims
"an id may carry user text" while printing two ids; it now states that `runId`/`parentRunId` are opaque identifiers printed
verbatim (keep user text out of ids your run-id seam hands out) and that `heldRunId` shows only as set/null (value is on
the property). Behaviour and `ActionEventTest` unchanged. Status: fixed, requires human verification (a design call:
doc corrected rather than behaviour changed; if you prefer redacting `runId`/`parentRunId`, that is a behaviour change plus
a test change, still additive-safe because v1.1.0 is not cut).

### WR-03: "stt-free facade" has no executable proof

**Files modified:** `voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/SttFreeFacadeTest.kt` (new)
**Commit:** c780190
**Applied fix:** New test with three cases: (1) non-vacuity, the segment facade class bytes DO contain the `sttengine` package
path; (2) the `SttLanguageLabels` class bytes contain no `sttengine` reference; (3) the facade is defined in a classloader that
throws `ClassNotFoundException` for the whole `io.github.ygaray.sttengine` package and then `normalizeSttLanguageLabel` and
`commandInputOf` are invoked reflectively and return the right values. The reviewer's snippet's `ClassLoader.getClassLoadingLock`
is not on the Android compile classpath, so the loader synchronizes on itself instead.

### WR-04: Context-only overloads silently turn a run id into `context`

**Files modified:** `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt`, `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt`
**Commit:** 9bdd7cf
**Applied fix:** KDoc warning on `FinalSegment.toCommandInput(context)` and `commandInputOf(transcript, label, context)`: a
`String` there is a context object, not a run id; use the two-/four-argument form for a parent run. No signature change.
The reviewer's stronger option (dropping the overloads before the tag) was not applied: it changes the pinned overload set
(`AdapterApiShapeTest`, API.md, INTEGRATION.md) and is a contract decision. Note it for the human if wanted before v1.1.0.

### IN-01: README names `voice-adapter:<version>` but the pinned version has no such artifact

**Files modified:** `README.md`
**Commit:** c45d3b9
**Applied fix:** README now says the adapter was "first published in v1.1.0, so pin v1.1.0 or newer for it" (matches
INTEGRATION.md / ECOSYSTEM.md). The `pin-version` markers are untouched.

### IN-03: Segment path and text path duplicate the mapping

**Files modified:** `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt`
**Commit:** 8e27945
**Applied fix:** `FinalSegment.toCommandInput(context, parentRunId)` now delegates to
`commandInputOf(text, language, context, parentRunId)`. Dependency direction is segment facade to label facade only, so the
label facade stays stt-free (now also proven by SttFreeFacadeTest). The existing parity test
(`commandInputOfAgreesWithTheSegmentPathForTheSameTextAndLabel`) and all FinalSegmentMappingTest cases pass.

## Skipped Issues

### WR-02: The `:stt` confinement script is not wired into any automated gate

**File:** `scripts/verify-stt-confinement.sh:1-262`
**Reason:** skipped: deferred. Per orchestrator direction, `scripts/verify-negative-controls.sh`, `scripts/release-cut.sh`
and the hygiene gates were just proven green in a one-hour host quiet window that cannot be repeated, and release-cut gate
wiring is Phase 20 work (18-CONTEXT RT-02). Not edited. The script itself is green when run by hand
(`STT CONFINEMENT OK checks=6`).
**Original issue:** The textual `:stt` confinement checks (single exact-group `exclusiveContent`, immutable pin shape, no
aggregator group, adapter `minSdk`/compileOnly shape, source-import confinement, docs minimum vs catalog pin) and its
`--selftest` have no caller in release-cut, hygiene or negative-controls, so drift passes every automated gate until a
human runs the script.

**Carry-forward (owner: Phase 19 gate run / Phase 20 release-cut wiring):**
1. In `scripts/release-cut.sh`, call `scripts/verify-stt-confinement.sh` inside the existing `hygiene` gate (next to
   `verify-module-manifest.sh`) and require the `STT CONFINEMENT OK` line.
2. In `scripts/verify-negative-controls.sh` Part 6, also run `scripts/verify-stt-confinement.sh --selftest`.
3. Until wired, the Phase 19 gate run must invoke `scripts/verify-stt-confinement.sh` and its `--selftest` by hand and record
   the result. Wiring needs a fresh quiet window to re-prove the gates; do not merge it without one.

### IN-02: Adapter DI/ML gates scan `:stt`'s transitive compile tree

**File:** `gradle/invariants.gradle.kts:316-335, 379-413`, `voice-adapter/build.gradle.kts:37`
**Reason:** skipped: accepted (latent false positive, green today). The gates read `releaseCompileClasspath`, which includes
the `compileOnly` `:stt` tree, so a future `:stt` release adding a DI or ML artifact would fail the adapter's `check` even
though it never reaches a consumer. There is no trivial safe fix: changing the scanned configuration for one module edits
the shared invariant gates that were just proven in the quiet window, and risks weakening them. Note for the owner of the
next `:stt` repin: if that gate trips on the stt tree, narrow the adapter's scan to `releaseRuntimeClasspath` plus the POM
(or exclude the stt component tree with a justification comment) rather than loosening the rule.
**Original issue:** See 18-REVIEW.md IN-02.

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
