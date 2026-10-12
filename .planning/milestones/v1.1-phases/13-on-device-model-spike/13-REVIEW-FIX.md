---
phase: 13-on-device-model-spike
fixed_at: 2026-10-06T00:00:00Z
review_path: .planning/phases/13-on-device-model-spike/13-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 4
skipped: 3
status: resolved
---

# Phase 13: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/13-on-device-model-spike/13-REVIEW.md
**Iteration:** 1
**Verification location:** main checkout (worked in place on `gsd/phase-13-on-device-model-spike`, no worktree, per the driver's instruction). Host-only checks; no Gradle was run (host memory is tight).

**Summary:**
- Findings in scope: 7 (0 Critical, 7 Warning)
- Fixed: 4 (WR-01..WR-04), plus the Info item IN-05
- Skipped (documented acceptable-skips): 3 (WR-05, WR-06, WR-07)

Status is `resolved`: every finding is fixed or a documented acceptable-skip, and no blocker remains.

## Fixed Issues

### WR-01: private SB fixture filename not ignored or gated

**Files modified:** `.gitignore`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-ml-denial-controls.sh`
**Commit:** 7e29f47
**Applied fix:** added `*sb-fixture*` to `.gitignore`, to the hygiene `forbidden_specs` (section c), and to the section (b) `git check-ignore` probes. Added a `zz-plant-sb-fixture.json` temporary-index plant to Part C of `verify-ml-denial-controls.sh`. Verified host-only: `git check-ignore` matches `sb-fixture.json`, `verify-repo-hygiene.sh` still prints `HYGIENE OK`, and a force-added plant in a temporary index trips `c: forbidden file(s) present`. The new Part C plant itself was not run through the Gradle-backed script (the other plants in that loop use the same mechanism).

### WR-02: leak scan covered only Anthropic/OpenRouter/OpenAI key shapes

**Files modified:** `scripts/spike-evidence-filter.sh`
**Commit:** 3917da2
**Applied fix:** `KEY_RE` now also rejects Hugging Face, Google, GitHub and AWS key shapes (assembled from fragments so no key-shaped literal sits in the file) and any value that is a 40+ character unbroken token. Verified: all four shapes now give `LEAK SCAN FAIL`, a benign value still passes, and all 15 committed evidence files still pass the filter.

### WR-03: digest rule bypassed by uppercase hex and unlisted key names

**Files modified:** `scripts/spike-evidence-filter.sh`
**Commit:** 2cbf673
**Applied fix:** the digest rule now matches either hex case on any key whose name contains `sha`, `digest`, `hash` or `checksum` (9+ hex), plus any value of 32+ hex characters. This is narrower than the review's blanket `=[0-9a-fA-F]{9,}` suggestion on purpose: decimal values such as `bytes=2588147712` and `storage_free_kb=443038292` are valid hex strings and the blanket rule would reject committed evidence. Verified: uppercase `fixture_sha`, `sha256=`, `digest=`, `labels_sha` and an unnamed 64-hex value all fail; 8-hex prefixes and the `head=` field still pass; all 15 evidence files still pass.

### WR-04: filter's parity/test claim was stale

**Files modified:** `scripts/spike-evidence-filter.sh`
**Commit:** 991ab74
**Applied fix:** replaced the false header claim with the real status: the grammar was frozen from `SpikeEvidence.kt@e362fb228b`, its source, `EvidenceGrammarTest` and `verify-spike-evidence-filter.sh` were deleted in plan 13-10, there is no automated test at HEAD, and the old script is recoverable via `git show 1fec77a^:scripts/verify-spike-evidence-filter.sh`. No test was invented or added. `verify-spike-disposition.sh removed` still prints OK.

### IN-05 (Info, requested): 13-DISPOSITION.md recovery SHA

**Files modified:** `.planning/phases/13-on-device-model-spike/13-DISPOSITION.md`
**Commit:** 75950c7
**Applied fix:** per plan 13-10, `harness_sha` is copied from `SPIKE_VERDICT_META` and is legitimately the evidence-producing commit `08ada3366b`, so the frontmatter was left alone. The prose now says the harness is recoverable at `e362fb228b` (the verdict code that `verify-spike-verdict.sh --check` rebuilds) and that `08ada3366b` produced the evidence.

## Skipped Issues

### WR-05: Part 5 appended to a script that cannot go green

**File:** `scripts/verify-negative-controls.sh:141-142`
**Reason:** skipped: out-of-scope, pre-existing Phase 12 issue. The three failing `api.txt missing once released` plants (lines 92-94) predate Phase 13 and were recorded as W-1 in `.planning/phases/12-*/12-VERIFICATION.md` (line 182), tracked outside this phase. Phase 13 only appended the Part 5 call, which is itself correct. Follow-up: make the Part 1 plants back up and delete `$m/api.txt`, run `verifyApiDumpPresent -PvaeAssumeReleased`, then restore.
**Original issue:** the aggregate negative-controls gate exits 1 with 3 known failures, burying the Part 5 signal.

### WR-06: verdict engine does not treat host-synthesised `result=timeout` as incomplete

**File:** `spike-ondevice/.../verdict/VerdictRules.kt@e362fb228b:195-202`
**Reason:** skipped: report-only. The code lives in the deleted spike harness, which `verify-spike-verdict.sh --check` reproduces from the recorded code SHA `e362fb228b`. Editing or resurrecting it would break that pin and change the verdict. The fix text in the review is retained as guidance for any future re-run.
**Original issue:** incomplete-stage detection ignores a `timeout` result with no `trials=`/`planned=`.

### WR-07: sb verdict line carries `peak_pss_mb=525` with no reason

**File:** `spike-ondevice/.../verdict/VerdictRules.kt@e362fb228b:279-302`, `13-VERDICT.md`
**Reason:** skipped: report-only, same reason as WR-06 (deleted harness pinned to `e362fb228b`; the verdict must not change). The sb peak PSS caveat is already documented in `13-VERDICT.md` and listed as a follow-up in `13-DISPOSITION.md`.
**Original issue:** scorePss reads only the winning cell's envelope-tagged samples, so a 525 MB INIT reading is reported while 4267 and 5669 MB were measured elsewhere.

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
