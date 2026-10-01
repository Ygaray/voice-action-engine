---
phase: 10-sample-harness-gate-1-docs
verified: 2026-10-01T23:00:00Z
status: human_needed
goal_met: false
score: 3/4 roadmap success criteria verified (SC1, SC2, SC3); SC4 present and compiled but its behavioral proof (fresh-agent wiring test) is not rerun on the final docs
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-01-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-01-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-02-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-02-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-03-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-03-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-04-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-04-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-05-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-05-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-06-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-06-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-07-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-07-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-08-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-08-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-09-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-09-SUMMARY.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-10-PLAN.md"
  - ".planning/phases/10-sample-harness-gate-1-docs/10-10-SUMMARY.md"
  - "API.md"
  - "ECOSYSTEM.md"
  - "INTEGRATION.md"
  - "README.md"
  - "sample/build.gradle.kts"
  - "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt"
  - "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/FixtureLoader.kt"
  - "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt"
  - "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt"
  - "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/tools/CannedToolExecutor.kt"
  - "scripts/run-sample-gate1.sh"
covered_digest: "v1:sha256:0b6ad7e29cb253d4fc9f8bc6742017402e33de6790af2866a58376b75730d12f"
behavior_unverified: 1
overrides_applied: 0
behavior_unverified_items:
  - truth: "SC4 / VER-04: an AI agent can wire the engine into a new app from the README plus integration doc alone"
    test: "Rerun the fresh-agent wiring test on the final docs SHA (scripts/agent-wiring-test.sh prepare <sha>, a fresh subagent from outside this repository, then verify), and record status: pass in 10-WIRING-TEST.md"
    expected: "WIRING TEST: PASS for a SHA that is an ancestor of the v1.0.0 tag commit and API-identical to it"
    why_human: "The only run (338d85ffa3) passed mechanically but logged 12 doc stumbles; the docs were rewritten afterwards and never re-tested. Doc coverage greps and compiled snippets prove the content exists, not that an agent can follow it. It needs network and a fresh isolated agent."
human_verification:
  - test: "C5 / VER-03 PROV-12: OpenRouter EDIT-shaped smoke proves an omitted optional arrives absent"
    expected: "optional_absent=true on L4 with a model that omits the optionals, or an explicit Yahir acceptance of the INCONCLUSIVE result"
    why_human: "G1-09 ran twice (two prompt variants) on openai/gpt-5.4-mini via OpenRouter: http=200, tool call parsed, but the model filled the optional fields both times (INCONCLUSIVE model_filled_optional). This is model behavior, not host-fixable."
  - test: "C4 OpenRouter cache-write accounting (uat-pending/05 item 3(c))"
    expected: "A recorded Yahir waiver (the engine sends no explicit OpenRouter breakpoint in v1.0, LATER-02), carried in the Phase 11 waiver packet"
    why_human: "Not exercisable in v1.0. turn2_cache_read=0 was only observed. Waiver authority is Yahir's."
  - test: "D-07 wiring test rerun on the final docs (same as the behavior_unverified item above)"
    expected: "10-WIRING-TEST.md status: pass with rerun_sha recorded"
    why_human: "Phase 11 precondition. Needs network and a fresh isolated agent; the first run also loaded this repository's CLAUDE.md into the subagent's context (isolation caveat)."
  - test: "C1 low-credit 400 maps to Billing; C3 Responses-only 400 wording"
    expected: "C1 stays a unit-test-only item; C3 was captured as http=400 reason=http_error but the marker wording is not visible under LE-7. Yahir accepts it or asks for a host-side wording check."
    why_human: "C1 cannot be triggered without draining credit. C3's body text is deliberately not captured."
---

# Phase 10: Sample Harness, Gate-1 & Docs Verification Report

**Phase Goal:** The engine is proven on a real device against SB's real prompt and live on all three cloud providers, and it is documented well enough for an AI agent to wire it from the README alone. (The tag is Phase 11.)
**Verified:** 2026-10-01
**Status:** human_needed
**goal_met:** false. There is no code or documentation defect. The flag is false because two parts of the goal are not yet demonstrated: SC4's "an agent can wire it" has no passing run on the final docs, and VER-03's OpenRouter optional-omission clause is INCONCLUSIVE. Both are carried to named human/Phase 11 steps rather than passed silently.
**Re-verification:** No, initial verification

I did not rely on the SUMMARY files. I read the sample sources, the build file, the docs, the Gate-1 log and the evidence files. At HEAD 0341f9d I ran, with no device, adb or network:

- `./gradlew check --offline -q`: exit 0 (all modules, OkHttp matrix legs, detekt zero baseline, scanners, `:sample` unit tests and lint).
- `scripts/verify-repo-hygiene.sh`: HYGIENE OK. `scripts/verify-docs-coverage.sh`: DOC COVERAGE OK checks=23 types=96. `scripts/verify-sample-device-guard.sh`: SAMPLE DEVICE GUARD OK scenarios=33. `scripts/review-api-surface.sh --expect-sealed-complete`: API SURFACE OK classes=181. `scripts/agent-wiring-test.sh selftest`: WIRING SELFTEST OK (its inner `WIRING TEST: FAIL W5` line is the planted negative control).
- `:sample` JUnit XML: 143 tests, 0 failures, 0 errors, 0 skipped (19 `*Test.kt` files under `sample/src/test`, per the validation audit 20 classes including `DocSnippetsTest`). The XML on disk dates from the review-fix run, and `check` just now reported it up to date.
- `git tag`: empty. `git ls-files '*api.txt' '*sb-a10-fixture*'`: empty. The fixture is gitignored (`.gitignore:48`).

**Build identity of the device run.** The Gate-1 APK is `4a586ed7b8` (md5 `4c6fc98c64485ce878e11e60fdf92559`). The only non-`.planning` change since is `scripts/run-sample-gate1.sh` (5 lines, the SIGPIPE fix in `6ee25b7`), so the app code the device exercised is the code at HEAD. The Gate-1 build already includes the review fixes (CR-01, WR-01..WR-07).

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `:sample` loads the LE-1 fixture (sha256 `ebd3ef4a...af4ed3e`) from a gitignored path, fails loudly at run/debug-task time and never at configuration time when it is absent, runs a fake `ToolExecutor`, stores the BYO key through `:keystore`, and pins OkHttp 5.2.1. | VERIFIED | **Fixture:** `FixtureLoader.kt` holds the full 64-hex `FIXTURE_SHA256` (prefix `ebd3ef4a`, suffix `af4ed3e`), compares the full digest and never falls through to a later source on a mismatch. It returns the typed `Absent`, `ShaMismatch` and `Malformed` states and prints at most an 8-hex prefix. `.gitignore:48` ignores `sb-a10-fixture*.json`, and no fixture file is tracked. **Not at configuration time:** `sample/build.gradle.kts` contains none of `getenv`, `environmentVariable`, `fileTree(`, `readText`, `File(` or `exec`. **Fake executor:** `CannedToolExecutor : ToolExecutor` (read tools finish, mutating tools return a canned `Mutation`), used by `LegRunner`. **Keys:** `KeyVault.kt` delegates one to one to `:keystore`'s `ApiKeyStore`. **Pin:** `implementation("com.squareup.okhttp3:okhttp:5.2.1")` at `sample/build.gradle.kts:39` (the only 5.x coordinate outside the test-leg overrides). **Tests (re-run green):** `FixtureLoaderTest`, `CannedToolExecutorTest`, `KeyVaultTest`, `PlaintextScanTest`, `OkHttpPinTest`. **On the TESTER (SELF-UAT):** G1-01 `FIXTURE ABSENT` in red, `REFUSED reason=fixture_absent`, no spend; G1-02 `Fixture OK sha=ebd3ef4a tools=18`; G1-03 `okhttp 5.2.1` on screen and `VAE_ENV okhttp=5.2.1`; G1-04 save, relaunch and delete a dummy key; G1-05 three keys imported through `:keystore` with the plaintext deleted. Evidence: `evidence/gate1-ver02.txt` first two blocks. |
| 2 | Gate-1 on the TESTER: the Anthropic agentic loop runs 2 or more turns, turn 1 has `cache_creation_input_tokens > 0`, turn 2 and later have `cache_read_input_tokens > 0`, near SB's 7,016, the gate runs in canned-admit mode, and the prefix size and minimum cacheable length are logged. | VERIFIED | `evidence/gate1-ver02.txt` third block, captured on the device at `4a586ed7b8`: `VAE_TURN iteration=1 ... cache_creation_input_tokens=7016 cache_read_input_tokens=0`, `iteration=2 ... cache_creation_input_tokens=0 cache_read_input_tokens=7016`, both attempts `http=200`, `VAE_VERDICT verdict=PASS turn1_write=7016 min_read=7016 calls=2`. 7,016 equals SB's number exactly. `VAE_ENV ... min_cacheable=4096 prefix_chars=21109 est_prefix_tokens=5277` logs both required sizes. The run was cold (cold stamp `cold=yes`, G1-06). Canned-admit: `SampleEngine.kt:18-19` defines `CANNED_ADMIT = PreApplyGate { GateDecision.Admit() }` and wires it as the gate. The fixture leg's evidence redacts the fixture's tool names (`tools=redacted tool_count=n`, CR-01 fix), and I found no key-shaped string in the evidence directory. Note: the VER-02 run executed no mutating step (`executed=0 committed=0`), so canned-admit is the configured gate but this leg did not push a mutation through it. SC2 does not require one. The mutation path is covered by G1-12 (`committed=1`) and the Phase 9 tests. |
| 3 | One live single-shot smoke call each to Anthropic, OpenAI and OpenRouter from `:sample` (Yahir's real keys) returns a parsed tool call. | VERIFIED (literal SC); the VER-03 EDIT-optional clause is open for OpenRouter | G1-07 Anthropic `http=200`, `tool=edit_item`, `optional_absent=true`, PASS, one attempt, no retry. G1-08 OpenAI `gpt-5.4-mini`, `http=200`, `optional_absent=true`, PASS. G1-09 OpenRouter `openai/gpt-5.4-mini`, `http=200`, a parsed tool call (`arg_keys=[body,id,tags,title]`) on both runs, so the wire shape works end to end and `key_charset=ok`. But the verdict is INCONCLUSIVE `model_filled_optional` on both prompts, so the PROV-12 clause "each provider's smoke proves an omitted optional arrives absent" is shown for Anthropic and OpenAI only. Extended multi-turn legs G1-10 (OpenAI Chat) and G1-11 (OpenRouter) PASS with tool-result replay and `end_turn`. Spend 11 requests, about USD 0.014, inside the 33+1 ceiling (G1-13). Carry C5 stays open (see Human Verification). |
| 4 | An AI agent can wire the engine into a new app from the README plus the integration doc alone. The docs cover the per-module coordinates, a minimal pipeline, every seam, both gate modes, `else` branches on open taxonomies, and point to `:sample` as the working example. | PRESENT_BEHAVIOR_UNVERIFIED | **Content present:** `README.md` (186 lines), `INTEGRATION.md` (595 lines), `API.md` (323 lines) and `ECOSYSTEM.md` (68 lines). They hold per-module JitPack coordinates, a minimal pipeline with a copy-paste import block, a numbered adoption checklist, the suspend (`AwaitingConfirmGate`) and non-suspend gate modes (INTEGRATION lines 224-305), `else` branches on open taxonomies (INTEGRATION 190, 280, 359, 397, 453-470, and a note at 579), `Completed(partial = true)` rendered as "did X, couldn't finish", never as full success (README 180-181, INTEGRATION 424, 450), pressable options for `Clarification` (INTEGRATION 427, 451), the uncached combos (OpenRouter `anthropic/<id>` uncached in v1.0; Responses-only OpenAI ids such as `gpt-6-astra` fail on Chat Completions, INTEGRATION 571-573), and `:sample` named as the working example with file pointers (README 14-19). `scripts/verify-docs-coverage.sh` reproduced OK (23 checks, 96 types) and `DocSnippetsTest` compiles the snippets. **Behavior not proven on the final docs:** the one fresh-agent wiring run (338d85ffa3) passed mechanically but logged 12 stumbles. All 12 were fixed in the docs afterwards (`18996be`), and `10-WIRING-TEST.md` is `status: pending-rerun` with `rerun_sha: TBD`. Its own isolation caveat says that run also auto-loaded this repository's CLAUDE.md. Whether an agent can wire the engine from the fixed docs has never been shown, so the criterion is present but behavior-unverified. |

**Score:** 3/4 roadmap truths verified. 1 present but behavior-unverified (SC4). 0 failed.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| VER-01 | 10-01, 10-02, 10-03 | Fixture from a gitignored path, loud run-time failure, fake executor, BYO key via `:keystore`, OkHttp 5.2.1 pin | SATISFIED | SC1; G1-01..G1-05 on the device, host tests green |
| VER-02 | 10-04, 10-05 | Anthropic agentic Gate-1: 2 or more turns, write then read near 7,016, canned-admit, sizes logged | SATISFIED | SC2; G1-06, `evidence/gate1-ver02.txt` |
| VER-03 | 10-05, 10-07 | One live single-shot smoke per cloud returning a parsed tool call, plus an EDIT-shaped call proving an omitted optional is absent (PROV-12) | PARTIAL | Parsed tool call on all three clouds. The EDIT-optional proof holds for Anthropic and OpenAI. OpenRouter is INCONCLUSIVE (C5), carried to Gate-2 |
| VER-04 | 10-08, 10-09 | Docs good enough for an agent to wire from them; `terminalCall`/`Clarification` as pressable options; partial wording; uncached combos; `:sample` referenced | NEEDS HUMAN | All content requirements present and checked in the docs. The "agent can wire from it alone" proof awaits the rerun on the final docs (D-07). Clarification and partial rendering verified on the device (G1-12) |

All four IDs appear in the plan frontmatter and in ROADMAP Phase 10, and REQUIREMENTS.md maps exactly VER-01..VER-04 to Phase 10. VER-05 is Phase 11's. No orphaned requirement.

**Bookkeeping, not a gap:** `.planning/REQUIREMENTS.md` still shows VER-01..VER-04 as `- [ ]` and `Pending`. The orchestrator ticks them at close. Because VER-03 and VER-04 are partial, tick VER-01 and VER-02 only, and leave VER-03 and VER-04 open until the carries resolve or are waived.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `sample/build.gradle.kts` | Compose app, OkHttp 5.2.1, no configuration-time reads | VERIFIED | Pin at line 39; grep for configuration-time read patterns is empty |
| `sample/.../fixture/FixtureLoader.kt` | Full-sha loader with typed failures | VERIFIED | Substantive, used by `LegRunner` and the UI header, covered by `FixtureLoaderTest` |
| `sample/.../tools/CannedToolExecutor.kt` | Fake `ToolExecutor` | VERIFIED | Wired in `LegRunner` (lines 76, 251, 256) |
| `sample/.../keys/KeyVault.kt` | Keys through `:keystore` | VERIFIED | Imports and delegates to `ApiKeyStore` and `KeyState` |
| `sample/.../SampleEngine.kt` | Composition root, canned-admit gate | VERIFIED | `CANNED_ADMIT` wired as the pipeline gate |
| `sample/.../legs/LegRunner.kt` + `LegCatalog.kt` | Every Gate-1 leg | VERIFIED | The legs ran on the device (G1-06..G1-12) |
| `scripts/run-sample-gate1.sh` | Guarded TESTER-only runner | VERIFIED | 33 fake-adb scenarios OK. The SIGPIPE false negative found in the live run is fixed in `6ee25b7` (the 4-line change is in the tree) |
| `README.md`, `INTEGRATION.md`, `API.md`, `ECOSYSTEM.md` | Agent-wireable docs | VERIFIED (content) | Behavior proof pending, see SC4 |
| `evidence/gate1-*.txt` (9 files), `gate2-carry-register.txt`, `phase-gate.txt` | Committed Gate-1 evidence | VERIFIED | Closed-vocabulary lines, no key shapes, no fixture tool names |
| `.planning/uat-pending/10-sample-harness-gate-1-docs.md` | Gate-2 fragment | VERIFIED | Lists C1..C7 with dispositions and the owner how-to-verify steps |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| UI press | `LegRunner` | `SampleViewModel` -> `LegRunner` | WIRED | Each leg ran from a stable resource id on the device |
| `LegRunner` | engine pipeline | `SampleEngine` + `CannedToolExecutor` + `BudgetedProvider` | WIRED | `AgenticLegTest`, `SmokeLegTest`, `MultiTurnLegTest` and the device runs |
| `KeyVault` | `:keystore` | `ApiKeyStore` | WIRED | G1-04 and G1-05 on the device |
| fixture | `ToolSpec` list | `FixtureLoader.load` -> `FixtureState.Loaded` | WIRED | G1-02 `tools=18` equals the host count |
| evidence | committed files | `EvidenceLine` -> allow-list filter -> `capture-save` | WIRED | Filter rejects planted leaks (guard scenarios); committed files clean |
| `sample` | OkHttp 5.x | `implementation(okhttp:5.2.1)` | WIRED | `OkHttpPinTest`; `VAE_ENV okhttp=5.2.1` on the device |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| ver02 verdict | cache tokens | the live Anthropic response usage on the device (`cache_creation_input_tokens=7016`, `cache_read_input_tokens=7016`) | Yes | FLOWING |
| `fixture_state` header | sha, tools | `FixtureLoader` over the pushed file | Yes (`tools=18`, `sha=ebd3ef4a`) | FLOWING |
| smoke verdicts | `arg_keys`, `optional_absent` | the live tool call | Yes | FLOWING |
| budget chip | requests used | persisted `RequestBudget` | Yes (`10/33 optional 1/1`) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Whole build and `:sample` unit tests | `./gradlew check --offline -q` | exit 0 | PASS |
| Repo hygiene | `scripts/verify-repo-hygiene.sh` | HYGIENE OK | PASS |
| Docs coverage | `scripts/verify-docs-coverage.sh` | OK checks=23 types=96 | PASS |
| Device-guard fake-adb scenarios | `scripts/verify-sample-device-guard.sh` | OK scenarios=33 | PASS |
| API surface sealed and complete | `scripts/review-api-surface.sh --expect-sealed-complete` | OK classes=181 | PASS |
| Wiring-test harness self-check | `scripts/agent-wiring-test.sh selftest` | WIRING SELFTEST OK | PASS |
| No tag, no api.txt, no fixture tracked | `git tag`, `git ls-files` | empty | PASS |
| Device legs | not re-run (no device, per the brief) | taken from `10-SELF-UAT.md` and the evidence files, which agree with each other | n/a (device-verified per SELF-UAT) |

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh`. The host verifiers above were run directly.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| phase files (sample, scripts, docs) | - | `TBD`/`FIXME`/`XXX` grep | none outside `10-WIRING-TEST.md` | `rerun_sha: TBD` in the wiring-test frontmatter is a deliberate placeholder tied to the named Phase 11 precondition, not code debt |
| `scripts/run-sample-gate1.sh` | - | live-run SIGPIPE defect | resolved | Fixed in `6ee25b7`. It did not change any criterion verdict (the install succeeded) |

Code review (`10-REVIEW.md`: CR-01 plus WR-01..WR-07) is resolved 8 of 8 in `10-REVIEW-FIX.md`, and the fixes are in the Gate-1 build. Security (`10-SECURITY.md`): SECURED, 0 open threats of 50. The remaining flagged items are info-level.

### Human Verification Required

1. **C5 OpenRouter EDIT-optional omission (VER-03, PROV-12).** Test: re-run L4 with a model that omits the optionals, or decide. Expected: `optional_absent=true`, or Yahir records acceptance of the INCONCLUSIVE result. Why human: model behavior, INCONCLUSIVE twice.
2. **C4 OpenRouter cache-write via router.** Test: Yahir decides the waiver (LATER-02). Expected: waiver recorded in the Phase 11 packet. Why human: not exercisable in v1.0 and waiver authority is Yahir's.
3. **D-07 wiring rerun on the final docs (VER-04, SC4).** Test: run the rerun procedure in `10-WIRING-TEST.md`, with the subagent dispatched from outside this repository. Expected: `status: pass` with `rerun_sha`, an ancestor of the tag commit and API-identical to it. Why human: needs network and a fresh isolated agent. This is a hard Phase 11 precondition.
4. **C1 and C3.** C1 is covered by named unit tests only. C3 was captured as http=400 `http_error` without wording. Yahir accepts both or asks for more.

### Gaps Summary

No code or documentation gaps. All of VER-01 and VER-02 are device-verified and re-confirmed on the host. VER-03 returns a parsed tool call from all three clouds, but its OpenRouter EDIT-optional clause is INCONCLUSIVE after a rerun. VER-04's content is complete and compiled, but no agent has yet been shown to wire the engine from the fixed docs. These are carried as human or Phase 11 items (C5, C4, the wiring rerun, C1, C3). Nothing has been silently passed. Phase 11 owns the tag, and it must not cut `v1.0.0` until the wiring rerun records `status: pass` and Yahir has ruled on C4 and C5.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
