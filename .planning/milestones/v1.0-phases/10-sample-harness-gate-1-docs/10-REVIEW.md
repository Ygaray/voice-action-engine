---
phase: 10-sample-harness-gate-1-docs
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 65
files_reviewed_list:
  - API.md
  - ECOSYSTEM.md
  - INTEGRATION.md
  - README.md
  - build.gradle.kts
  - gradle/libs.versions.toml
  - sample/build.gradle.kts
  - sample/src/debug/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
  - sample/src/main/AndroidManifest.xml
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/AppGraph.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/MainActivity.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/CostEstimate.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/AndroidFixtureSources.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/FixtureLoader.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/ToolClassifier.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeySlots.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/PlaintextScan.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/DemoProvider.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/net/OkHttpRuntime.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/net/ProviderFactory.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/CannedToolExecutor.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/SyntheticTools.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/OutcomeText.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/SampleScreen.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/CacheVerdict.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/SmokeVerdict.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/verdict/VerdictTypes.kt
  - sample/src/release/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/AgenticLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/CacheVerdictTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/CannedToolExecutorTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/ClarificationFlowTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/FixtureLoaderTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/KeyVaultTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/LegTestSupport.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/MultiTurnLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/OkHttpPinTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/OutcomeTextTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/PlaintextScanTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RequestBudgetTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngineTracerTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeVerdictTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SyntheticToolsTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
  - sample/src/test/resources/evidence-lines.golden.txt
  - sample/src/test/resources/synthetic-fixture.json
  - sample/src/testDebug/kotlin/io/github/ygaray/voiceactionengine/sample/TestKeyImporterTest.kt
  - scripts/agent-wiring-test.sh
  - scripts/run-sample-gate1.sh
  - scripts/sample-evidence-filter.sh
  - scripts/verify-docs-coverage.sh
  - scripts/verify-sample-device-guard.sh
findings:
  critical: 1
  warning: 7
  info: 8
  total: 16
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 65 (production Kotlin, scripts and docs read in full; the unit-test files were read selectively: RequestBudgetTest, TestKeyImporterTest, EvidenceLineTest assertions and the golden file in detail, the rest skimmed for vacuity)
**Status:** issues_found

## Summary

The device guard in `scripts/run-sample-gate1.sh` holds up. Every adb call carries `-s <serial>` except the one documented `adb connect`. The target can only be the TESTER USB serial R5CT10XNKQN or the wireless fallback, and the fallback must prove the same `ro.serialno`, model and SDK. A foreign `ANDROID_SERIAL` is refused. There is no emulator or other fallback. The lock is shared with the keystore runner and fd 9 is closed for children. Keys never touch argv: `push-test-key` gets a provider name, `--device` and `--package`, and the script never reads a key. The 27-scenario fake-adb verifier checks the "-s on every call" property mechanically. In the Kotlin, `KeyVault`, `PlaintextScan`, `ImportReport`, `KeyView`, `UiState`, `FollowUpContext` and `EvidenceLine` print state words or lengths only. The only `Log` call is the evidence sink, and `OkHttpClient` has no interceptor.

The weak spots are in what the evidence pipeline lets through and in the spend guard:

- **Evidence.** The closed grammar is an alphabet filter, not a vocabulary. On the VER-02 run, fixture tool names flow straight into the committed `gate1-ver02.txt`. That breaks the LE-7 rule in the live-leg decision and the runbook.
- **Spend guard.** The 33+1 request ceiling does not hold as a combined invariant. The budget store fails open on an unreadable file. Requests cancelled in flight, or whose store write throws, are never counted.
- **Proof steps.** `verify-keys-gone` can report success on adb timeout or failure.
- **Debug autorun.** The debug autorun intent is unauthenticated and the "first run via the UI" decision (D-01) is not enforced.

## Critical Issues

### CR-01: VER-02 evidence lines carry the fixture's tool names (LE-7 violation, public repo)

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt:150` (also `:338`, `legs/LegRunner.kt:153`)
**Issue:** `EvidenceLine.turn` writes `"tools" to list(record.toolNames)`, and `EvidenceListener` forwards every `ProviderCall` turn. On the `ver02` leg (`LegKind.AGENTIC_FIXTURE`) the tools offered to the model are the private SB A10 fixture's tools. `TurnRecord.toolNames` is the names of the tools the model called (`BoundModel.kt:129`). So the first live run emits `VAE_TURN leg=ver02 ... tools=[<SB tool names>] ...`. These names pass `LIST_ITEM`, the Kotlin `ALLOW_PATTERN` and the shell `ALLOW_RE`. `run-sample-gate1.sh capture-save ver02` then appends them to `.planning/.../evidence/gate1-ver02.txt`, which is committed to a public repo.

The live-leg decision and runbook line 21 say: "evidence contains no fixture content, no fixture tool names and no fixture sha suffix". The only guard is the static scan of `sample/src` in 10-10 (`names_checked=18 hits=0`), which cannot see runtime output. The golden file only shows synthetic names (`tools=[find_items]`), so no test covers this.

The same alphabet-only filter means any model-chosen string (a hallucinated tool name, for instance) of up to 96 allowed characters reaches the file. That is why "closed vocabulary" overstates what `token()`/`list()` enforce (see IN-03).

**Fix:** Redact names whenever the tool set is the fixture's. Pass the fact into the listener and keep only counts.

```kotlin
// EvidenceLine.turn: take names as nullable and emit a count instead when redacted
fun turn(leg: LegId, iteration: Int, record: TurnRecord, prefixChars: Int?, redactTools: Boolean = false) =
    EvidenceLine("TURN", false, listOf(
        "leg" to leg.wire, "iteration" to iteration.toString(),
        "model" to tokenOrNone(record.model), "stop_reason" to tokenOrNone(record.stopReason),
        "tools" to if (redactTools) "redacted" else list(record.toolNames),
        "tool_count" to record.toolNames.size.toString(),
        /* ...rest unchanged... */
    ))

// EvidenceListener(leg, sink, prefixChars, redactTools)
// LegRunner.runLocked:
val legListener = EvidenceListener(spec.id, sink, fixtureForLeg?.prefixChars, redactTools = spec.needsFixture)
```

Add a test that runs the fixture leg with non-synthetic tool names and asserts none appear in any emitted line. Consider having the filter reject `leg=ver02` lines whose `tools=` is not `redacted`.

## Warnings

### WR-01: `verify-keys-gone` reports success when adb fails or times out

**File:** `scripts/run-sample-gate1.sh:357-362`
**Issue:** The result is judged only on output text. `out="$(adbt shell "run-as ... ls files/test-keys" 2>&1 | strip_cr)"` treats an empty string as "dir empty -> keys_gone=yes". Empty output is also what you get when `timeout 30` kills adb, when the device drops mid-call, or when `adb shell` returns nothing. `$?` of the pipeline is discarded. Any other failure text ("error: device offline", "Permission denied") falls through to the count path and is reported as a plaintext-keys FAIL, which is a wrong diagnosis but at least loud. The empty case is the dangerous one, because this step is the proof that the plaintext keys are gone. The later uninstall in `cleanup` is a backstop but not a substitute.
**Fix:** Make the success path positive, using a sentinel emitted by the device shell.

```bash
out="$(adbt shell "run-as $PKG sh -c 'if [ -d files/test-keys ]; then ls files/test-keys; fi; echo __rc=\$?'" 2>&1 | strip_cr)"
case "$out" in
  *"__rc=0") body="${out%__rc=0}" ;;
  *) finish 2 ERROR "reason=check_unproven target=$TARGET" ;;   # adb failed, timed out or run-as refused
esac
# body empty -> keys_gone=yes; otherwise count lines and FAIL
```

Add a verifier scenario where the fake adb prints nothing and exits 124.

### WR-02: The "33+1" ceiling is not a combined invariant; core can spend past 34 after the probe

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt:220-225`
**Issue:** `fits` checks only `state.core + requests <= coreCeiling` for core legs and ignores `state.optional`. The probe is gated by `state.optional == 0 && core + optional + requests <= 34`, but `optional == 0` only limits it to one logical call, and a call can send 2 (chat transport) or 3 (Anthropic) HTTP requests. If the probe runs while core is low, its spend (up to 2 recorded requests) is then added to a core that can still climb to 33. Total reaches 35 or more, against the "hard ceiling 33 core plus 1 optional" in 10-LIVE-LEG-DECISION.md and the test name `theOptionalProbeRunsOnceAndNeverPushesTheTotalPast34`. With the runbook order (probe last, core about 14) this is unlikely to trigger, but the code does not enforce the claim, and no test exercises the core-after-optional order.
**Fix:**

```kotlin
private fun fits(state: BudgetState, requests: Int, optional: Boolean): Boolean =
    if (optional) {
        state.optional == 0 && state.core + state.optional + requests <= totalCeiling
    } else {
        state.core + requests <= coreCeiling &&
            state.core + state.optional + requests <= totalCeiling
    }
```

Add a test with `state(core = 30, optional = 2)` expecting `headroomFor(false)` to be false. Decide separately whether the probe should be capped at 1 request (the decision file says "0-1 / 1") rather than 2.

### WR-03: The budget store fails open on an unreadable or corrupt file, and on a failed write

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt:79-105` (read), `:107-123` (write); `sample/.../net/ProviderFactory.kt:45-52`
**Issue:**
- `read()` returns `BudgetState.EMPTY` when the file is missing, unreadable (`IOException`), or any line fails to parse (`toIntOrNull() ?: 0`). A spend guard that resets to "nothing spent" on corruption defeats the cross-restart guarantee ("a force-stop or a restart cannot reset them").
- A truncated or half-written file is treated as zero.
- `write()` can throw (`IOException` from `writeText` or `Files.move`). The transports call the observer through `notifyQuietly`, which swallows the throw. `AttemptTap.record` also calls `budget.record` first, so the attempt is not added to the leg context or emitted either. The request was sent and billed, but it is not counted and nothing is shown.

**Fix:**
- Distinguish "no file" (genuinely empty) from "file present but unreadable or invalid". For the latter, return a saturated state (core = ceiling) or throw, so `headroomFor` refuses.
- In `AttemptTap.record`, catch store failures and emit a loud line, and add the attempt to the context before touching the store.

```kotlin
// FileBudgetStore.read
if (!file.exists()) return BudgetState.EMPTY
val lines = try { file.readLines(Charsets.UTF_8) } catch (e: IOException) { return BudgetState.EXHAUSTED }
// parse failure of any recognised key -> return BudgetState.EXHAUSTED instead of 0
```

### WR-04: Requests cancelled in flight, or killed with the process, are never counted

**File:** `sample/.../net/ProviderFactory.kt:45-52` with `providers/.../chat/ChatTransport.kt:105-113,176-179`
**Issue:** Counting happens in the attempt observer, which fires only after `attempt()` returns. If the coroutine is cancelled while the request is on the wire (the activity finishing clears `viewModelScope`, or the process is force-stopped), `await()` and `ioFailure`'s `ensureActive()` throw `CancellationException` before `notify`. The request is billed but never recorded. The guard also pre-checks only the next call's headroom. A tester who force-stops mid-leg to retry therefore spends uncounted, and the app tells them the budget is fine.
**Fix:** Record intent before the request leaves, not after: have `BudgetedProvider.complete` add a reservation (`perCallWorstCase`) to the persisted state before delegating and reconcile to actual attempts when the call returns. If that is out of scope for the sample, document the gap in the runbook and subtract a margin from the 33.

### WR-05: Debug autorun intent is unauthenticated and D-01 ("first run via the screen") is not enforced

**File:** `sample/src/main/AndroidManifest.xml:10` (exported), `sample/src/main/kotlin/.../MainActivity.kt:41-54`, `sample/src/debug/kotlin/.../DebugTools.kt:30-33`, `sample/.../SampleViewModel.kt:154-158`
**Issue:** The launcher activity is `exported="true"`. In debug builds, any app on the device can send an intent with `vae_autorun=<leg>` and start a paid live leg with no human present (up to the 33+1 budget). The KDoc says autorun is "a RERUN convenience only: a leg's first verdict never carries trigger=autorun", but nothing checks it: `runLeg` accepts `TRIGGER_AUTORUN` for a leg that has never run.
**Fix:** Refuse an autorun for a leg with no prior UI run, and refuse live (key-needing) legs from an intent altogether if reruns are only needed for offline demos.

```kotlin
fun runLeg(leg: LegId, trigger: String = TRIGGER_UI) {
    if (trigger == TRIGGER_AUTORUN && budget.runsOf(leg.wire) == 0) { /* emit REFUSED reason=autorun_before_ui; return */ }
    ...
}
```

Optionally gate the extra on a debug-only permission or signature check.

### WR-06: The runner prints the fixture SHA suffix, and the runbook makes that line evidence

**File:** `scripts/run-sample-gate1.sh:308` (also `sample/.../HeaderText.kt:29`, `sample/.../fixture/FixtureLoader.kt:18`)
**Issue:** `finish 0 OK "fixture_sha=${EXPECTED_SHA:0:8}...${EXPECTED_SHA: -7} ..."` puts the last 7 hex characters on the `SAMPLE_GATE1:` line. GATE1-RUNBOOK.md:80 says the evidence for push-fixture is "the `SAMPLE_GATE1:` line of `push-fixture`", and the live-leg decision and runbook line 21 forbid any sha suffix in evidence ("quoted by its prefix `ebd3ef4a` only"). The on-screen banner also shows prefix and suffix, and the full 64-hex digest is a source constant in the public repo. The rule and the implementation contradict each other. The first violation needs no unusual step: the tester pastes the line the runbook tells them to.
**Fix:** Print the prefix only in the runner (`fixture_sha=${EXPECTED_SHA:0:8}`) and in the banner. Ask the master whether the committed full digest in `FixtureLoader.kt` is acceptable under LE-7. If it is, state in the runbook that the rule means "no suffix in evidence files", not "the digest is secret".

### WR-07: A non-whitelisted exception during import leaves plaintext key files on disk

**File:** `sample/src/debug/kotlin/.../DebugTools.kt:58-84`; caller `sample/.../SampleViewModel.kt:207-217`
**Issue:** `importOne` reads the key, calls `saveThenRead`, and only afterwards calls `destroy(file)`. `saveThenRead` catches `GeneralSecurityException`, `ProviderException` and `IOException`. Anything else (for example `IllegalStateException` from DataStore, `SecurityException`, `IllegalArgumentException`) propagates out of `importAll`, aborts the `providers.map`, and crashes the app through the unguarded `viewModelScope.launch` in `importTestKeys`. The plaintext file of the failing provider and of every provider after it stays in `files/test-keys`. The documented contract is that the importer "destroys the file".
**Fix:** Guarantee destruction in a `finally`, and let the exception become a `SAVE_FAILED` report.

```kotlin
val saved = try { saveThenRead(provider, text) } finally { /* destroy after the save attempt */ }
val deleted = destroy(file)   // run even if saveThenRead throws (use try/finally around both)
```

Catch `Exception` (not `CancellationException`) in `importTestKeys` so one bad provider does not crash the app.

## Info

### IN-01: Cleanup uninstalls the app, which erases the persistent budget

**File:** `scripts/run-sample-gate1.sh:372-385`; `sample/.../AppGraph.kt:28,99`
**Issue:** The cross-restart budget lives in `filesDir/gate1-budget.txt`. `cleanup` ends with `uninstall`, so a rerun after cleanup starts again at 0/33. The total spend of the Gate-1 effort is therefore not bounded by the app. The ceiling holds per install, not per milestone.
**Fix:** Record the spend line (`VAE_BUDGET`) into the evidence before cleanup and have the runbook require checking it before any reinstall. Or keep a host-side tally.

### IN-02: The evidence header writes the device target into a committed file

**File:** `scripts/run-sample-gate1.sh:349`
**Issue:** `# gate1 leg=... target=$TARGET head=...` records the TESTER serial or the tailnet address `100.118.21.106:1496` in a public repo. Low sensitivity, but the evidence otherwise avoids identifiers.
**Fix:** Write `target=tester` and keep the serial out of the file.

### IN-03: "Closed vocabulary" is an alphabet filter, and the key scan can reject whole captures

**File:** `sample/.../evidence/EvidenceLine.kt:37-38,269-282`; `scripts/sample-evidence-filter.sh:13,15`
**Issue:** `token()` accepts any string of up to 96 characters from `[A-Za-z0-9_.:/,\[\]-]`. Server-supplied values (`model`, `stop_reason`, finish reason) and model-chosen tool names pass through unchanged (see CR-01). The class comment ("a prompt, a reply, a tool argument or a key cannot reach a log line") is true for spaces and quotes but not for secrets or fixture identifiers that fit the alphabet. Separately, the filter's `bearer|x-api-key|authorization` match is case-insensitive anywhere in a kept line, so a benign value such as `authorization_check` rejects the entire capture. The failure is loud, which is acceptable, but it discards a paid run's evidence.
**Fix:** Reword the comment. Where the value space is known (stop reasons, attempt kinds, model ids), whitelist it. Anchor the header-word match on `(^|[^a-z])`.

### IN-04: The device-guard verifier has gaps around the new fail-open paths

**File:** `scripts/verify-sample-device-guard.sh:243-249`
**Issue:** There is a scenario for `verify-keys-gone` with keys present, but none with empty output plus a non-zero exit or timeout (WR-01), none for `push-keys` failing partway (second provider), and none for `install` failing after the identity check. The scenario count (27) reads as stronger coverage than it is for these paths.
**Fix:** Add `keys_gone_adb_timeout`, `push_keys_second_fails` (expect `push_key_failed provider=openai` and exactly two helper calls) and `install_failed` scenarios.

### IN-05: The agent wiring judge's W5/W6 checks are easy to satisfy vacuously

**File:** `scripts/agent-wiring-test.sh:138-143`
**Issue:** W5 passes if any `else ->` and any `when` exist anywhere in the consumer's main source. W6 passes on any occurrence of the word `partial`, including a comment. Neither shows that `CommandOutcome` is matched exhaustively with no `else` or that partial is rendered as "couldn't finish". The verifier's own self-test (planted bad copy fails W4/W5) only proves the checks can fail, not that they detect the property.
**Fix:** Require `is CommandOutcome.Completed`, `is CommandOutcome.Failed` and `is CommandOutcome.Unhandled` in one `when` block without an `else`, and `partial` outside comments.

### IN-06: Per-leg reservations and the USD ceiling in the decision are not enforced

**File:** `sample/.../legs/LegCatalog.kt:20-24`; `sample/.../evidence/CostEstimate.kt`
**Issue:** 10-LIVE-LEG-DECISION.md sets per-leg ceilings (L1 6, L2 to L4 3, L5/L6 6) and "USD 0.20". `reservation` is only a start gate (`canStart`). A 6-iteration VER-02 can legitimately send up to 18 requests and still sit under the global 33. The dollar figure is display only; a leg is never refused for cost.
**Fix:** Either add a per-leg request cap in `BudgetedProvider` or reword the decision to say "global 33+1 only". Treat `est_usd` as informational in the runbook.

### IN-07: Public docs reference local-machine paths and private repo locations

**File:** `ECOSYSTEM.md:5-6,41-42`
**Issue:** The repo is public. ECOSYSTEM.md cites `~/.claude/context/workflows/repin.md` and `~/.claude/context/deps/_index.md` and the consumers' dev checkout paths (`~/Projects/AndroidApps/Personal/SecondBrain`, `.../CalTracker_Android`). These are meaningless to integrators and reveal the private setup. `verify-docs-coverage.sh` (C21 and C19) does not scan ECOSYSTEM.md for them.
**Fix:** Drop the home-directory paths from the committed doc, or move the table to the control plane.

### IN-08: The running cost total resets on process restart while the request counts persist

**File:** `sample/.../AppGraph.kt:63-87`
**Issue:** `CostTallySink` accumulates `est_usd` in memory from `VAE_BUDGET` lines. After a Freecess kill or relaunch, the header shows `est USD 0.00000` next to restored request counts, and the mandated report-back line (`GATE1 LIVE SPEND: ... est_cost_usd=<x>`) can be understated. A single `unknown` leg also makes the total `unknown` for the rest of the process.
**Fix:** Persist the running estimate in `BudgetState`, or have the report take the figure from the sum of the `VAE_BUDGET` lines in the evidence files.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
