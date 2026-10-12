---
phase: 12-wave-1-seams-w04-fix
fixed_at: 2026-10-05T23:59:00Z
review_path: .planning/phases/12-wave-1-seams-w04-fix/12-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 5
skipped: 2
status: resolved
---

# Phase 12: Code Review Fix Report

**Fixed at:** 2026-10-05
**Source review:** .planning/phases/12-wave-1-seams-w04-fix/12-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (fix scope: all)
- Fixed: 5
- Skipped: 2 (both documented acceptable-skips; no open critical, warning or blocker)

Locked decisions D-01, D-02, D-05 and D-07 were honored: no behavior of `onFailed` routing changed, no `api.txt` edit, no
default arguments, the deny-list was not widened, and `KeyAccess` stays a plain opt-in interface.

**Verification ran in:** the isolated review-fix worktree (`.claude/worktrees/rf-12-*`), not the main checkout. Gradle
used the shared daemon and build cache, so the numbers are reproducible from the main checkout once the branch is
fast-forwarded.

## Fixed Issues

### WR-01: `onFailed` also receives on-device provider runtime failures

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt`, `API.md`
**Commit:** e180fce
**Applied fix:** Documentation-only resolution (the reviewer's first option). The `onFailed` KDoc and the API.md note now say the hook also receives runtime failures of an on-device provider that was bound successfully, and tell the app to branch on the reason before escalating. The reviewer's alternative (skip the hook for on-device failures) was NOT applied: it would change what `onFailed` receives and needs a design call under D-01. Status: fixed, requires human verification of the wording only.
**Verified:** `:core:detekt`, `scripts/verify-docs-coverage.sh` (OK, 25 checks).

### WR-02: `DelicateKeyAccess` opt-in switched off module-wide in `:keystore`

**Files modified:** `keystore/build.gradle.kts`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/DelicateKeyAccess.kt`
**Commit:** 39d5a8f
**Applied fix:** Removed the module-wide `optIn` from the `kotlin {}` block. The opt-in is now applied only to the unit-test and androidTest Kotlin compile tasks. Main code carries narrow `@OptIn(DelicateKeyAccess::class)` on the internal constructor, the `keyAccess` property, the `reader` property, `save`, `SecretReader` and `AndroidKeyStoreKeyAccess`, so a future public member that exposes `KeyAccess` without the marker now fails to compile. Added a sentence to the `DelicateKeyAccess` KDoc that the gate is Kotlin-only (Java callers and reflection are not checked). The `internal` primary constructor stays public in bytecode (Kotlin cannot change that); this is documented, not removed. No public API or bytecode signature changed.
**Verified:** `:keystore:testDebugUnitTest`, `:keystore:detekt`, `:keystore:assembleDebugAndroidTest` all green in one Gradle run (first attempt failed on two unmarked internal uses, fixed, second attempt green).

### WR-03: `verify-keyaccess-opt-in.sh` keeps running after Ctrl-C

**Files modified:** `scripts/verify-keyaccess-opt-in.sh`
**Commit:** d1007f6
**Applied fix:** `trap cleanup EXIT` plus `trap 'exit 130' INT` and `trap 'exit 143' TERM`, so a signal ends the script and the EXIT trap runs cleanup once.
**Verified:** `bash -n`, and a standalone reproduction of the trap pattern showed cleanup runs once and the script exits 130 without continuing.

### IN-01: Phase directory hard-coded in two scripts

**Files modified:** `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh`
**Commit:** 74c888f
**Applied fix:** The runner reads `PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/12-wave-1-seams-w04-fix}"`. The guard test defines one `PHASE_REL` variable, uses it for the skeleton decision file and the evidence path, and passes it to the runner as `VAE_GATE1_PHASE_DIR`, so a retarget is one edit. The decision file name `12-LIVE-LEG-DECISION.md` stays phase-named. Note: the historical `12-05-PLAN.md` acceptance grep for the literal `PHASE_DIR="..."` line no longer matches the new form; the plan is closed, so it was left as is.
**Verified:** `bash -n` on both, and `scripts/verify-sample-device-guard.sh` (offline fake adb, no device touched): `SAMPLE DEVICE GUARD OK scenarios=33`.

### IN-02: Responses-only deny-list covers only gpt-5/gpt-6 `-pro` and `-codex`

**Files modified:** `INTEGRATION.md`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt`
**Commit:** 4811430
**Applied fix:** Took the reviewer's second option (D-05, the deny-list stays targeted and is not widened). INTEGRATION.md and the `toolsOnChat` KDoc now word the list as the `gpt-5`/`gpt-6` `-pro` and `-codex` ids plus `gpt-6-astra` and `gpt-6.1-sol`, say that any other Responses-only id (for example `o3-pro`) is not on it, and name the `capabilities(...)` override with `supportsTools = false` as the escape hatch. No regex change.
**Verified:** `scripts/verify-docs-coverage.sh` OK, `:providers:detekt` and `:core:detekt` green.

## Skipped Issues

### IN-03: The reasoning-effort classifier reports `ModelUnsupported` for an engine wire-table gap

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt:147-150`
**Reason:** skipped: needs decision. The reviewer's fix adds a new public `TraceCode` constant, which is a public-API addition. D-02 forbids `api.txt` edits in Phase 12, and a new trace code is a contract-surface choice (name, which sink, additive-tag rules) that belongs to a later phase or an amendment. The reviewer rated it a diagnosability note on a deliberate, tested backstop (D-05). The cause is already partly visible without it: `ChatErrorInfo.errorType` carries the endpoint's `unsupported_value` code.
**Original issue:** The fallback fires whenever the endpoint rejects the effort the engine chose, so an engine wire-table gap reads as `model_unsupported` and the trace does not say which cause it was.

### IN-04: `LongParameterList.constructorThreshold` raised for every module to fit one class

**File:** `config/detekt/detekt.yml:11-14`
**Reason:** skipped: acceptable, project-policy conflict. The reviewer offered "or accept the global change knowingly". The project rule is "tune rules in `config/detekt/detekt.yml` with a one-line justification, never bank debt", and the change carries that justification comment. The suggested narrow `@Suppress` would add a new suppression where the project reserves suppressions for the single never-throw helper. Reverting to 8 plus a suppression is a policy call, not a defect fix.
**Original issue:** The threshold went from 8 to 9 so `ModelRequest` (eight parameters) passes, which loosens the gate for every constructor in `:core`, `:providers` and `:keystore`.

---

_Fixed: 2026-10-05_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
