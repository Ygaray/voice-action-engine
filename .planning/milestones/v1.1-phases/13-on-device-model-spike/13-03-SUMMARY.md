---
phase: 13-on-device-model-spike
plan: 03
subsystem: testing
tags: [verdict-as-code, wilson-lower-bound, evidence-grammar, host-filter, reproducibility, on-device, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: ":spike-ondevice module with the vae.spike.* test properties (13-01) and the locked 13-THRESHOLDS.md THRESHOLD lines"
provides:
  - "evidence/ package: closed VAE_SPIKE_ grammar (SpikeLine, ALLOW_PATTERN), wire enums, TrialRecord"
  - "verdict/ package: Thresholds, Wilson/percentile scoring, SchemaSubset, CellSelector, VerdictRules (pure per-envelope verdict)"
  - "scripts/verify-spike-verdict.sh --print/--check: verdict recomputed from committed evidence, refused on thresholds or harness drift, works after the module is deleted"
  - "scripts/spike-evidence-filter.sh and its offline proof: allow-list plus whole-capture rejection on leak shapes"
affects: [13-04, 13-06, 13-07, 13-08, 13-09, 13-10]

actuals:
  tokens: 52800
  tasks: 3
  commits: 4

plan_head_before: 2f28f0e044ef63369b951052cd2ad9d56b2cfdd9
commits: 4

tech-stack:
  added: []
  patterns:
    - "Verdict as a pure function of closed-grammar evidence lines and constants; absence is red (unmeasured:<metric>), never green or skipped"
    - "Grammar single-sourced in Kotlin and copied byte-for-byte into the bash filter, tied by a parity test"
    - "Thresholds parity by reflection over the Thresholds constants in both directions against the THRESHOLD lines"
    - "Reproduction through a Gradle JVM test driven by -P properties, with a temporary git worktree at the recorded code SHA"

key-files:
  created:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/evidence/SpikeEvidence.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/evidence/Records.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/Thresholds.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/Scoring.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/SchemaSubset.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/CellSelector.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/VerdictRules.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/VerdictReproductionTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/VerdictRulesTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/ScoringTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/SchemaSubsetTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/EvidenceGrammarTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/ThresholdsParityTest.kt
    - spike-ondevice/src/test/resources/evidence/unmeasured/env.txt
    - spike-ondevice/src/test/resources/evidence/green/evidence.txt
    - spike-ondevice/src/test/resources/evidence-golden.txt
    - scripts/verify-spike-verdict.sh
    - scripts/spike-evidence-filter.sh
    - scripts/verify-spike-evidence-filter.sh
  modified: []

key-decisions:
  - "An envelope reads only its own stages plus shared stages; PSS peak = max of MEM peak_pss_mb and INIT pss_mb on the envelope's stages for the winning cell (or cell-less lines), so a losing screen cell over 2000 MB does not decide the envelope"
  - "Process deaths are unmeasured unless the exit_reasons STAGE has result=done; an unknown thermal word ranks above shutdown so it can only turn an envelope red"
  - "A repeated item counts once (first occurrence) in every confirm metric (13-THRESHOLDS (c))"
  - "--check compares the thresholds_sha before recomputing (cheap, and a mismatch makes a recomputed verdict meaningless)"

requirements-completed: []
requirements-advanced: [SPIKE-01, SPIKE-02]

coverage:
  - id: D1
    description: "A closed VAE_SPIKE_ evidence grammar shared by device code and the host filter: the Kotlin ALLOW_PATTERN equals the filter's ALLOW_RE, golden lines round-trip byte-identically, values outside the alphabet render invalid_token"
    requirement: SPIKE-02
    verification:
      - kind: unit
        ref: "EvidenceGrammarTest (9 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The host filter keeps only grammar lines and rejects the whole capture on a key shape, a tool-name field on an sb line, or a digest over 8 hex"
    requirement: SPIKE-02
    verification:
      - kind: other
        ref: "scripts/verify-spike-evidence-filter.sh -> SPIKE FILTER OK cases=12; filter accepts evidence/toolchain.txt"
        status: pass
    human_judgment: false
  - id: D3
    description: "Scoring math: Wilson lower bound (48/50 EN and 46/50 ES boundaries, 100/100 = 0.963), nearest-rank percentiles, and a schema subset validator that fails loudly on any unsupported keyword"
    requirement: SPIKE-01
    verification:
      - kind: unit
        ref: "ScoringTest (10) + SchemaSubsetTest (12)"
        status: pass
    human_judgment: false
  - id: D4
    description: "VerdictRules truth table: every D-07 metric and D-10 row, early exits, incomplete stages, winner recomputation, E2B-only decisions with a 1B control line; unmeasured is red"
    requirement: SPIKE-02
    verification:
      - kind: unit
        ref: "VerdictRulesTest (46 tests, one reason per metric over the committed synthetic green fixture)"
        status: pass
    human_judgment: false
  - id: D5
    description: "The verdict is reproducible: --print recomputes the lines, --check compares with 13-VERDICT.md, refuses a thresholds_sha or dirty-harness mismatch, and still works with spike-ondevice/ deleted (worktree at the recorded code SHA)"
    requirement: SPIKE-02
    verification:
      - kind: other
        ref: "scripts/verify-spike-verdict.sh --print on the unmeasured and green fixtures; --check OK / thresholds_mismatch / verdict_differs / module-deleted worktree fallback exercised in a scratch clone; --check with no verdict file prints FAIL reason=no_verdict_file (exit 1)"
        status: pass
      - kind: unit
        ref: "ThresholdsParityTest (3 tests, both directions against the 24 THRESHOLD lines)"
        status: pass
    human_judgment: false

duration: 17min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 03: Verdict as code and the evidence filter Summary

**The spike verdict is now a pure Kotlin function of closed-grammar evidence and the locked thresholds, recomputable by one script (even after the module is deleted), with a host filter that cannot let a prompt, an SB tool name or a full digest into a committed file; every missing measurement reads red.**

## Performance

- **Duration:** 17 min
- **Started:** 2026-10-06T00:12:31Z
- **Completed:** 2026-10-06T00:29Z
- **Tasks:** 3 (tracer + 2 TDD)
- **Files:** 19 created, 0 modified (about 2,750 added lines, 109 KB of it the synthetic green fixture)

## Accomplishments

- **Task 1 (tracer): end to end first.** `SpikeLine` (private constructor, enum kinds, token sanitizer, `parse` that returns null for comments, free text and spaced values), the wire enums, `TrialRecord`, `Thresholds` and a minimal `VerdictRules` went through `scripts/verify-spike-verdict.sh --print` and the Gradle `VerdictReproductionTest`: the unmeasured directory gives red lines with `unmeasured:warm_p50` first and `unmeasured:kv_reuse` on sb only; the test skips without the property. Tracer gate re-ran the verify after commit: passed, expansion continued.
- **Task 2: the D-07 truth table.** `wilsonLowerBound` (oracle table holds: 48/50 EN reaches 0.85, 47 does not; 46/50 ES reaches 0.80, 45 does not; 100/100 renders 0.963), `percentileNearestRank`, deterministic half-up rounding, `SchemaSubset` (type, required, properties, enum, items, additionalProperties; any other keyword is an `UnknownKeyword` result), `CellSelector` (13-THRESHOLDS (l), E2B and auto shape only), and `VerdictRules` with every metric, the D-10 rows (`rf_enforced`, `rf_enum_nested` worst-of with `native_error < ignored < unproven < enforced`, `gpu_adreno730` last GPU line, `route_ab` as `a:k/n:p50,b:k/n:p50`, `kv_reuse` gating sb only), early exits (shared stages reach both envelopes), `fail:incomplete_stage`, and `inconsistent:winner`. The Gemma 3 1B model appears only on the `SPIKE_CONTROL` line.
- **Task 3: filter, parity, `--check`.** `ALLOW_RE` in the filter equals `ALLOW_PATTERN` byte for byte (`EvidenceGrammarTest`). `ThresholdsParityTest` reflects over the `Thresholds` constants and checks every `THRESHOLD` line has an equal constant and the reverse. `verify-spike-evidence-filter.sh` prints `SPIKE FILTER OK cases=12`. `--check` implements all six steps; I exercised OK, `thresholds_mismatch`, `verdict_differs`, `dirty_harness` and the module-deleted worktree fallback in a throwaway clone, so the real tree never held a fake `13-VERDICT.md`.

## Task Commits

1. **Task 1: evidence grammar and tracer verdict reproduction** - `6621879` (feat)
2. **Task 2 RED: scoring, schema subset and verdict truth-table tests** - `620c547` (test)
3. **Task 2 GREEN: scoring, schema subset, cell selector, full verdict rules** - `05b6ce5` (feat)
4. **Task 3: filter, grammar and thresholds parity, `--check`** - `bec8a31` (feat)

## Decisions Made

See `key-decisions` above. Line shapes the verdict reads (the contract plans 13-04 and 13-07 must emit) are written in the `VerdictRules` KDoc: TRIAL per `TrialRecord`; STAGE `stage result trials planned [winner reason seconds]`; INIT `stage cold init_ms pss_mb result` (confirm stage, `cold=process`); MEM `stage [cell] peak_pss_mb`; THERMAL `stage status`; EXIT `reason count`; SCHEMAPROBE `feature constraint [disposition]` (ON line carries the disposition); GPU `disposition [code]`; KVREUSE `disposition`.

## Deviations from Plan

**1. [Rule 1 - Plan arithmetic] `unmeasured:schema_valid` cannot occur alone.** The plan's behavior block asks for exactly one reason with 99 trials, and 97/100 for `fail:schema_valid`. The schema-valid N is every confirm trial (pos and neg), and the other gates already require 50 EN + 50 ES + 30 negatives = 130, so a 99-trial set always also lacks Spanish items or negatives. The test therefore builds 50 EN + 19 ES + 30 negatives and asserts `[unmeasured:schema_valid, unmeasured:semantic_es]`; the failing case is 126/130 (4 failures) instead of 97/100. The rule itself (n < 100 is unmeasured, point estimate under 0.98 fails) is as specified. Files: `VerdictRulesTest.kt`. Commit `05b6ce5`.

**2. [Rule 3 - Blocking order] Task 1's verify needs a clean harness tree.** The dirty-harness refusal (required by the plan) rejects uncommitted `spike-ondevice/src`, so I ran the same Gradle test directly with the `-P` properties to iterate, committed Task 1, and then ran the plan's verify command, which passed. Same for Task 2's green-fixture check.

**3. [Judgment] `--check` compares `thresholds_sha` before recomputing.** The plan lists the thresholds comparison after the diff. Checking first is cheap and a mismatch makes the recomputed verdict meaningless; only the reason reported when both fail differs.

**4. [Process] Task 3 tests were written together with its scripts, not red first.** Task 2 has a real RED commit (`620c547`, compile-red); for Task 3 the grammar and parity tests and the scripts landed in one commit, so the tests were never seen failing. They do guard the property (the byte-identical `ALLOW_RE`, both parity directions), and the filter script's negative cases were each shown rejecting.

Otherwise as written. Extra types added beyond the artifact list: `Lang`, `ItemKind`, `StagePhase` enums and `CellStats` (shared by the selector and the control line); `CellSelector` cases live in `VerdictRulesTest` (no extra test file).

## Issues Encountered

None. One Gradle invocation at a time, low-memory recipe, no earlyoom kill, no `./gradlew --stop`, no device or adb access.

## Authentication Gates

None.

## Verification

- Plan `<verification>`: `--print` on the unmeasured fixture gives the red/unmeasured lines; on the green fixture exactly two `verdict=green reasons=none` lines; `scripts/verify-spike-evidence-filter.sh` prints `SPIKE FILTER OK cases=12`; `:spike-ondevice:check` exit 0 (lint plus unit tests: 3 + 46 + 9 + 12 + 10 run, `VerdictReproductionTest` skipped without the property).
- Acceptance greps: `VAE_SPIKE_(ENV|TOOLCHAIN` count in `SpikeEvidence.kt` is 1; `import android` count in `verdict/` and `evidence/` is 0; key-shape literal count is `:0` in both scripts; `--check` with no verdict file prints `SPIKE_VERDICT_CHECK: FAIL reason=no_verdict_file` and exits 1; `scripts/spike-evidence-filter.sh` accepts the committed `evidence/toolchain.txt`; `scripts/verify-repo-hygiene.sh` prints `HYGIENE OK`.
- Not run: `scripts/verify-negative-controls.sh` (untouched by this plan).

## Next Phase Readiness

13-04 and 13-07 must emit the line shapes listed under Decisions Made (the verdict reads `status` on THERMAL lines, `cell=` on screen MEM lines, `stage=confirm_<env> cold=process` on the confirm INIT line, `seconds` on the sustained STAGE line, `disposition` on the ON SCHEMAPROBE line). Plan 13-07's own green-fixture acceptance check will catch drift. 13-09 can call `--print` and `--check` as specified; `SPIKE_VERDICT_CODE` names the commit that computed the lines, so commit the harness before running it. The thresholds sha to write into the first ENV line stays `ec4933fb` (first 8 hex of `13-THRESHOLDS.md`).

## Self-Check: PASSED

- All 19 created files present on disk.
- Commits present: 6621879, 620c547, 05b6ce5, bec8a31.
