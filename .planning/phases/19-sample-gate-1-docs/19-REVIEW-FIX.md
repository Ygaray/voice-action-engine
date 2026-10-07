---
phase: 19-sample-gate-1-docs
fixed_at: 2026-10-07T00:00:00Z
review_path: .planning/phases/19-sample-gate-1-docs/19-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: resolved
---

# Phase 19: Code Review Fix Report

**Fixed at:** 2026-10-07
**Source review:** .planning/phases/19-sample-gate-1-docs/19-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (WR-01..WR-04; no Critical findings, Info excluded by scope)
- Fixed: 4
- Skipped: 0

Verification ran in the isolated worktree for this fix run (not the main checkout). The shell gates are reproducible from
the main checkout after the fast-forward; the Gradle unit test ran offline in the worktree with in-process Kotlin.
No README.md, INTEGRATION.md, API.md or ECOSYSTEM.md edit was made (the isolated wiring pass stays valid). No published
module API changed.

## Fixed Issues

### WR-01: `VAE_GATE1_DECISION_FILE` lets the D-13 spend gate be satisfied by any file

**Files modified:** `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh`
**Commit:** 2674931
**Status:** fixed: requires human verification (security-gate logic)
**Applied fix:** `push-keys` now resolves the decision file with `realpath -m` and refuses
(`reason=decision_file_outside_planning`, exit 2) unless it sits under `<repo>/.planning/`, so `../` and symlinks cannot
escape. The recorded decision is now the FIRST `decision: ` line of the file and must be exactly `approved`, so a quoted
`decision: approved` further down no longer counts. The guard selftest's override scenarios moved under `.planning/`,
and two new refusal scenarios were added (file outside `.planning/`, `.planning/../` escape). The review's third
suggestion (require the file to be git-tracked and unmodified) was deliberately not applied: the relay protocol edits the
decision line in the working tree during a live window, so a tracked-and-clean requirement would refuse a legitimately
approved file. The path confinement and the anchored line close the reviewed hole without changing that flow.
**Tests:** `bash -n` on both scripts; `scripts/verify-sample-device-guard.sh` -> `SAMPLE DEVICE GUARD OK scenarios=43`, exit 0.

### WR-02: Docs gate no longer verifies the pinned release exists

**Files modified:** `scripts/verify-docs-coverage.sh`
**Commit:** 2b694e8
**Status:** fixed: requires human verification (gate logic); strictness deferred to Phase 20 by design
**Applied fix:** `check_C23` now reads the README pin (between the pin-version markers) and requires
`refs/tags/<pin>` to exist (it was asking for `v1.0.0`, which always exists, so it was vacuous). Because the docs
legitimately announce `v1.1.0` before Phase 20 tags it, a missing tag is a pre-tag allowance: the gate stays green and
prints `DOC COVERAGE NOTE: C23: ... pre-tag allowance ...` to stderr. `VAE_DOCS_REQUIRE_PINNED_TAG=1` makes a missing tag
a hard `FAIL: C23`. The selftest gained three C23 cases in a throwaway git repo (strict without the tag is red, the
allowance is green and says so, strict with the tag is green).
**Tests:** `bash -n`; `scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=32 types=119`, exit 0 (with the
NOTE); `VAE_DOCS_REQUIRE_PINNED_TAG=1 scripts/verify-docs-coverage.sh` -> `FAIL: C23: README.md pins v1.1.0 but that tag
does not exist`, exit 1 (expected until the tag exists); `scripts/verify-docs-coverage.sh --selftest` ->
`DOC COVERAGE SELFTEST OK plants=13`, exit 0.
**Carry to Phase 20:** the release cut must run the docs gate with `VAE_DOCS_REQUIRE_PINNED_TAG=1` (or flip the default
to 1 in the script) once `v1.1.0` is tagged, and the tag must exist before or atomically with merging these docs to
`main`. The docs-wording half of the finding (README and ECOSYSTEM say "released") is frozen by the wiring pass: docs
frozen by wiring pass, carry to Phase 20.

### WR-03: Wiring-test judge checks W5, W6 and W10-W13 with bare word greps

**Files modified:** `scripts/agent-wiring-test.sh`
**Commit:** 9a47c71
**Status:** fixed: requires human verification (judge logic)
**Applied fix:** the seven checks now run in a new `source_checks` function over comment-, string- and import-stripped
Kotlin (perl pass) and need construct or call shapes: `when` followed by `(`/`{` plus `else ->`, `.partial`,
`GrammarPack {`/`(`, `LocalGrammarStrategy(`, `PlanThenExecuteStrategy(`, `TierSelector.Router {`, `UndoJournal(`,
`.undoAll(` plus the undo dependency line. A new `selftest-source` subcommand (no Gradle) proves the committed reference
solution passes and a file with every name moved into a comment and a string fails W5, W6 and W10-W13; the full
`selftest` runs it first. The Gradle-based W1/W2 and the existing planted-copy checks (W4, W5, W12) are unchanged.
**Tests:** `bash -n`; `scripts/agent-wiring-test.sh selftest-source` -> `WIRING SOURCE SELFTEST OK`, exit 0. The heavy
`selftest` (Gradle publication) was not run, per instruction.

### WR-04: `ItemToolExecutor` rejects an explicit `parent_id: null`

**Files modified:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemToolExecutor.kt`,
`sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/PlanLegTest.kt`
**Commit:** d6647a1
**Status:** fixed: requires human verification (argument-validation logic)
**Applied fix:** `create` reads `arguments["parent_id"]` with `takeUnless { it is JsonNull }`, so an explicit null is the
same as an absent parent; a non-null non-string (for example a number) is still `invalid_arguments`. New test
`anExplicitNullParentIsTheSameAsNoParent` in `PlanLegTest` asserts a `ToolStep.Mutation` for `parent_id: null`.
**Tests:** `./gradlew --offline -q :sample:testDebugUnitTest --tests '*PlanLegTest*' --tests '*UndoLegTest*'` -> exit 0
(PlanLegTest tests=9 failures=0). The module has no `:sample:detekt` task (detekt applies to the library modules only).
**Gate-1 build delta:** this changes installed-APK code (`sample/src/main`), so the build is no longer
`gate1_build_head` 1869950dca. It is a source-only delta (one file under `sample/src/main`, plus a unit test). The
orchestrator should account for this in the Gate-1 record and rebuild before any further live window.

## Skipped Issues

None. Info findings IN-01..IN-08 were out of scope (`fix_scope` critical_warning).

---

_Fixed: 2026-10-07_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
