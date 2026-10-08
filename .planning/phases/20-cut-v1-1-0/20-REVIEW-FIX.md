---
phase: 20-cut-v1-1-0
fixed_at: 2026-10-07T00:00:00Z
review_path: .planning/phases/20-cut-v1-1-0/20-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 1
skipped: 7
status: resolved
---

# Phase 20: Code Review Fix Report

**Fixed at:** 2026-10-07
**Source review:** .planning/phases/20-cut-v1-1-0/20-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (WR-01..WR-04, IN-01..IN-04; 0 critical)
- Fixed: 1
- Skipped: 7 (all documented acceptable-skips; no open blocker/critical/high)

**Constraints honoured:** no Gradle or build was run. Tag v1.1.0 and `.planning/releases/v1.1.0/**` were not touched. Nothing was pushed. No published artifact or api.txt changed. Verification ran in an isolated worktree (fast-forwarded into `main` afterwards), using only `bash -n`, the script's own selftest and manual bash/JDK checks. shellcheck is not installed.

## Fixed Issues

### IN-02: verify-binary-diff.sh dead locals and ambiguous failure exit codes

**Files modified:** `scripts/verify-binary-diff.sh`
**Commit:** a8a6022
**Applied fix:**
- Dropped the unused locals `classes`, `present` and `added` from `run_diff`.
- Removed the lowercase `work` alias and used `$WORK` throughout (`run_diff` and `selftest`).
- A `javap` failure in `member_lines` (old or new artifact) now prints `BINARY DIFF ERROR: javap failed on the {old,new} artifact` and exits 2. A failing `--out` write prints `BINARY DIFF ERROR: cannot write <path>` and exits 2. Before, both ended the script with a bare status that could be 1, which is indistinguishable from "removed members".

**Verification (bash/JDK only):**
- `bash -n` passes.
- `--selftest` still ends `BINARY DIFF SELFTEST OK cases=13`. The case count is unchanged on purpose: `.planning/releases/v1.1.0/evidence/tooling-proof.txt` records `cases=13`.
- With no arguments the script exits 2.
- A corrupt `.class` in the new jar gives the javap ERROR message and exit 2.
- An unwritable `--out` path gives the ERROR message and exit 2.
- The happy path still prints `BINARY DIFF OK ...` and exits 0.

The diff changes tooling only. No documented output line or the exit-code contract for removed members changed.

## Skipped Issues

All skips are acceptable-skips. For the unverifiable-without-Gradle ones the reason is: "unverifiable without Gradle in this window; tooling/sample hardening, does not affect the released tag; carried to a later patch".

### WR-01: verify-binary-diff.sh never compares class headers

**File:** `scripts/verify-binary-diff.sh:380-390` (the cited lines no longer exist: the file is 330 lines, and the awk is at lines 80-103)
**Reason:** The change is bash-verifiable, but it alters the helper's documented verdict semantics. Emitting a header pseudo-member changes the `added=` counts and can turn a previously OK result on the same artifacts into FAIL. That makes the recorded v1.1.0 binary-diff evidence not reproducible at HEAD. It also needs a design decision that a naive pseudo-member cannot answer: adding an `implements` or a supertype is binary-compatible and additive, but would be reported as REMOVED. A correct check must only flag removed or narrowed supertypes and modifier changes. That needs validating against the real v1.0.1 and v1.1.0 artifacts, which means a build. Carried to the v1.2 tooling work together with WR-02.
**Suggested backlog entry:** "verify-binary-diff.sh: compare class headers (class modifiers, superclass, interfaces) as a subset check (old supertypes must be a subset of new; additions OK), with selftest cases for a dropped `implements` and a class made `final`; validate against the v1.0.1 to v1.1.0 artifacts."

### WR-02: D-02 binary diff not enforced by any release gate

**File:** `scripts/release-cut.sh`
**Reason:** Changing release-cut.sh gate wiring; its selftest needs Gradle. Unverifiable without Gradle in this window; tooling hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "release-cut.sh: add a fail-closed `binary-diff <tag>` gate (run the helper per released module; require exit 0 or a committed `binary-diff-waiver.txt` whose removed count matches) plus controls."

### WR-03: gate 12 does not detect a module dropped since the previous release

**File:** `scripts/release-cut.sh:528-551`
**Reason:** Change to release-cut.sh gate wiring; the control needs the Gradle-backed selftest. Unverifiable without Gradle in this window; tooling hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "release-cut.sh api_baseline_check: fail when a module with an api.txt in the previous tag is absent from scripts/modules.list at HEAD; add a control beside `retag_prior_without`."

### WR-04: RT-07 fingerprint-never-echoed rule has no mechanical enforcement; `UiTags.neverEchoed` is dead code

**File:** `sample/.../ui/UiTags.kt:37-41`, `sample/.../keys/KeyVault.kt:338-341`
**Reason:** Sample-module Kotlin plus evidence-filter changes; verification needs Gradle. Unverifiable without Gradle in this window; tooling/sample hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "sample: make RT-07 executable. Have sample-evidence-filter.sh reject or redact `key_state_*` lines and `fp [0-9a-f]{6}` tokens, with a selftest that plants one; otherwise delete UiTags.neverEchoed."

### IN-01: ApiKeyStoreVault.fingerprint silently degrades to a green "Ready"

**File:** `sample/.../SampleViewModel.kt:294-296`, `sample/.../keys/KeyVault.kt:38-43`
**Reason:** Sample Kotlin behaviour change; needs Gradle unit tests to verify. Unverifiable without Gradle in this window; sample hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "sample: derive the fingerprint from the same vault read as the key state (single readSecret). Show `Ready - fingerprint unavailable` with Tone.BAD if it is null."

### IN-03: JournalStoreTest no-disk-write test is environment-sensitive

**File:** `undo/src/test/kotlin/.../JournalStoreTest.kt:527-572`
**Reason:** Test change; verifiable only by running Gradle tests. Unverifiable without Gradle in this window; test hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "undo JournalStoreTest: keep the constant-pool scan; restrict the tree comparison to a fresh temp `user.dir` or `scratch`; reduce the needles to java/io/File, java/nio/file/ and java/io/RandomAccessFile."

### IN-04: KeyUx.fingerprint uses an inline fully-qualified `java.security.MessageDigest`

**File:** `sample/.../keys/KeyVault.kt:339`
**Reason:** Style-only change in sample Kotlin; compile check needs Gradle. Unverifiable without Gradle in this window; sample hardening, does not affect the released tag; carried to a later patch.
**Suggested backlog entry:** "sample KeyVault.kt: import java.security.MessageDigest instead of the inline fully-qualified name."

---

_Fixed: 2026-10-07_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
