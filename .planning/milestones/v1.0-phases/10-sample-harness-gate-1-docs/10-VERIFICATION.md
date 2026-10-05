---
phase: 10-sample-harness-gate-1-docs
verified: 2026-10-01T23:30:00Z
status: passed
goal_met: true
score: 4/4 roadmap success criteria verified (SC1, SC2, SC3, SC4); VER-03 satisfied with an accepted-inconclusive OpenRouter clause (explicitly not a PASS)
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
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 3/4 roadmap success criteria verified (SC4 behavior-unverified)
  gaps_closed:
    - "SC4 / VER-04 / D-07: isolated fresh-agent wiring rerun recorded status: pass on 36c578f464 (WIRING TEST: PASS checks=9)"
    - "C5 / G1-09: OpenRouter EDIT-optional omission accepted by evidence by the orchestrator (INCONCLUSIVE model_filled_optional, not a PASS)"
  gaps_remaining: []
  regressions: []
carried_items:
  - item: "C4 OpenRouter cache-write via router (uat-pending/05 item 3(c), LATER-02)"
    owner: "Phase 11 waiver packet, routed through orchestrator yahir-gsd-control-plane-f2, waiver authority Yahir"
  - item: "C1 low-credit 400 maps to Billing (unit-test-only)"
    owner: "Gate-2 (milestone UAT), Yahir accepts or asks for more"
  - item: "C3 Responses-only 400 marker wording (captured http=400 reason=http_error, body deliberately not captured under LE-7)"
    owner: "Gate-2 (milestone UAT), Yahir accepts or asks for a host-side wording check"
  - item: "3 minor wiring-test stumbles (Credential construction snippet, keystore-wiring imports, JVM-only module need)"
    owner: "Phase 11 OPTIONAL pre-cut doc touch; any doc edit requires an isolated wiring rerun on the new SHA before the tag"
---

# Phase 10: Sample Harness, Gate-1 & Docs Verification Report

**Phase Goal:** The engine is proven on a real device against SB's real prompt and live on all three cloud providers, and it is documented well enough for an AI agent to wire it from the README alone. (The tag is Phase 11.)
**Verified:** 2026-10-01 (HEAD `36c578f464`)
**Status:** passed
**goal_met:** true
**Re-verification:** Yes. Previous status was `human_needed` / `goal_met: false` (SC4 wiring rerun outstanding, C5 INCONCLUSIVE, C4/C1/C3 open).

## What changed since the previous verification, and how I checked it

I did not take the relayed summary on trust. I read each source and checked it against git and the code. No device, adb, network or gradle was used.

1. **Wiring test (SC4, VER-04, D-07).** `10-WIRING-TEST.md` frontmatter is `status: pass`, `rerun_sha: 36c578f464`, `rerun_verdict: "WIRING TEST: PASS checks=9"`.
   - `git diff --stat 18996be HEAD -- README.md INTEGRATION.md API.md ECOSYSTEM.md` is empty, and `git diff --stat 36c578f464 HEAD` over the four docs plus `core providers keystore` is empty. `git diff --name-only 18996be HEAD` outside `.planning` lists only `sample/` and `scripts/` paths, so no `core`, `providers` or `keystore` signature moved. The tested SHA is also `origin/main` HEAD (`git rev-parse origin/main` equals `36c578f464721395f55c84547081e06c2be102b3`). The docs the agent read are the docs at HEAD, and the engine API they describe is API-identical to HEAD.
   - `evidence/wiring-rerun-consulted.txt` lists only workspace files: `docs/README.md`, `docs/INTEGRATION.md`, `docs/API.md`, `docs/ECOSYSTEM.md`, `settings.gradle.kts`, `build.gradle.kts`, `jvmconsumer/build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `app/src/main/AndroidManifest.xml`. No path outside the prepared workspace. That matches the claim in `10-WIRING-TEST.md`.
   - `evidence/wiring-rerun-stumbles.txt` holds exactly three entries (Credential/`CredentialSource` signature, keystore-wiring imports, JVM-only module need), matching the three stumbles the file reports (12 down to 3), all minor, none blocking.
   - The isolation closes the first run's caveat as claimed (throwaway `CLAUDE_CONFIG_DIR`, separate headless process). I cannot re-execute the agent or the judge here (needs network and a fresh agent), so the pass rests on the recorded run and the two evidence copies, which are internally consistent and consistent with git. I judge that sufficient: the claim is checkable on every axis a verifier can reach, and the judge is mechanical (9 checks).
2. **C5 / G1-09 acceptance, spot-checked in code.** The claim "shared `ChatCompletionsProvider`/`ChatVendor` decoder path, host tests prove no default-filling" is true:
   - `providers/.../chat/ChatVendor.kt` defines `OPENAI` and `OPENROUTER` as two instances of one class. `ChatCompletionsProvider.openAi` and `.openRouter` build the same provider over the vendor. There is one decoder (`ChatDecoder.kt`) for both.
   - `providers/src/test/.../chat/ChatAbsentOptionalTest.kt` is `@Parameterized` over `openai` (`gpt-5.4-mini`) and `openrouter` (`openai/gpt-5.4-mini`). It runs a forced EDIT-shaped call through `commandPipeline` and the real `ChatCompletionsProvider` against a MockWebServer, and asserts `modelSent(row.body) == run.received` (the app gets exactly what the model sent, nothing more or less), that the listed optional paths do not resolve (`assertFalse(resolves(...))`), and that for the derived rows the key sets are exactly `card_id`, `items` and `text`. It also asserts `strict` is not sent for the EDIT tool and the schema goes out untouched. The on-disk result XML for that class shows 10 tests, 0 failures, 0 errors, 0 skipped.
   - Live: G1-08 (OpenAI) PASSED `optional_absent=true`. G1-09 (OpenRouter) returned `http=200` with a parsed tool call on both runs but `optional_absent=inconclusive` (`model_filled_optional`).
   - The disposition is recorded in `10-SELF-UAT.md` (frontmatter `g1_09_disposition: "INCONCLUSIVE(model_filled_optional), ACCEPTED BY EVIDENCE by the orchestrator (2026-10-01); not a PASS"`, `gate1_tally: "13/14 pass + 1 accepted-inconclusive (G1-09)"`) and in `.planning/uat-pending/10-sample-harness-gate-1-docs.md` (C5 line). The criterion `result` stays `partial`, so nothing was silently upgraded to a PASS. I treat it as an accepted orchestrator disposition, not a pass.
3. **C4, C1, C3** are not Phase 10 goal gaps. C4 is already in the Phase 11 waiver packet (`11-CONTEXT.md` line 74, with C5 listed next to it). C1 and C3 are listed as Gate-2 items in the uat-pending fragment. Owners are named in the `carried_items` frontmatter and the table below.

Unchanged since the previous verification, still true at HEAD: no tag (`git tag` empty), no tracked `api.txt` or fixture, `.gitignore:48` ignores the fixture, and `scripts/verify-docs-coverage.sh` reproduced `DOC COVERAGE OK checks=23 types=96` in this pass. The prior pass recorded `./gradlew check --offline -q` exit 0, hygiene OK, device-guard OK scenarios=33, API surface OK classes=181 and `:sample` 143 tests with 0 failures. Since that pass, only `.planning` files have changed (the SIGPIPE fix `6ee25b7` predates it). The orchestrator runs `./gradlew check` itself, so I did not.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `:sample` loads the LE-1 fixture (sha256 `ebd3ef4a...af4ed3e`) from a gitignored path, fails loudly at run/debug-task time and never at configuration time when absent, runs a fake `ToolExecutor`, stores the BYO key through `:keystore`, and pins OkHttp 5.2.1. | VERIFIED | Unchanged from the previous pass, no sample app code changed since the Gate-1 build. `FixtureLoader.kt` full-digest compare with typed `Absent`/`ShaMismatch`/`Malformed`; `sample/build.gradle.kts` has no configuration-time reads and pins `okhttp:5.2.1` (line 39); `CannedToolExecutor`, `KeyVault` over `ApiKeyStore`; device G1-01..G1-05 PASS (`evidence/gate1-ver02.txt`). |
| 2 | Gate-1 on the TESTER: Anthropic agentic loop 2+ turns, turn 1 `cache_creation_input_tokens > 0`, turn 2+ `cache_read_input_tokens > 0` near SB's 7,016, canned-admit, prefix size and minimum cacheable length logged. | VERIFIED | `evidence/gate1-ver02.txt`: `cache_creation_input_tokens=7016` on turn 1, `cache_read_input_tokens=7016` on turn 2, `VAE_VERDICT verdict=PASS turn1_write=7016 min_read=7016 calls=2`, `VAE_ENV ... min_cacheable=4096 prefix_chars=21109 est_prefix_tokens=5277`. `SampleEngine.kt` wires `CANNED_ADMIT`. |
| 3 | One live single-shot smoke each to Anthropic, OpenAI and OpenRouter from `:sample` returns a parsed tool call. | VERIFIED (literal SC). The VER-03 OpenRouter optional-omission clause is accepted-inconclusive, NOT a PASS | G1-07 Anthropic and G1-08 OpenAI PASS with `optional_absent=true`. G1-09 OpenRouter `http=200`, parsed tool call `arg_keys=[body,id,tags,title]` on both prompts, `key_charset=ok`, verdict INCONCLUSIVE `model_filled_optional`. Multi-turn G1-10 and G1-11 PASS. The literal criterion (a parsed tool call from each cloud) is met. The extra PROV-12 clause is covered for OpenRouter by the shared decoder plus the parameterized host test above, and the live gap is an explicit orchestrator disposition. |
| 4 | An AI agent can wire the engine into a new app from the README plus integration doc alone (coordinates, minimal pipeline, every seam, both gate modes, `else` branches, `:sample` as the working example). | VERIFIED | Content: README (186 lines), INTEGRATION (595), API (323), ECOSYSTEM (68), `verify-docs-coverage.sh` OK, `DocSnippetsTest` compiles the snippets. Behavior: isolated fresh-agent rerun on the final docs, `WIRING TEST: PASS checks=9`, build green on the first run, stumbles 12 down to 3 (all minor), CONSULTED lists only workspace files. Docs byte-unchanged between the doc-fix commit `18996be` and HEAD. |

**Score:** 4/4 roadmap truths verified. 0 present-but-behavior-unverified (was 1). 0 failed.

### Requirements Coverage

| Requirement | Source Plan(s) | Status | Evidence |
|---|---|---|---|
| VER-01 | 10-01, 10-02, 10-03 | SATISFIED | SC1; G1-01..G1-05 on the device, host tests green |
| VER-02 | 10-04, 10-05 | SATISFIED | SC2; G1-06, `evidence/gate1-ver02.txt` |
| VER-03 | 10-05, 10-07 | SATISFIED, with the OpenRouter optional-omission clause ACCEPTED-INCONCLUSIVE (explicitly not a PASS) | Parsed tool call on all three clouds. EDIT-optional proven live for Anthropic and OpenAI; OpenRouter live run INCONCLUSIVE twice, accepted by evidence by the orchestrator (shared decoder passed live OpenAI, host tests prove no default-filling). Listed in the Phase 11 waiver packet beside C4 |
| VER-04 | 10-08, 10-09 | SATISFIED | All content requirements present; clarification and partial rendering verified on the device (G1-12); isolated wiring rerun passed on the final docs (D-07) |

All four IDs appear in plan frontmatter and ROADMAP Phase 10, and REQUIREMENTS.md maps exactly VER-01..VER-04 to Phase 10. No orphaned requirement. VER-05 is Phase 11's.

**Bookkeeping, not a gap:** `.planning/REQUIREMENTS.md` still shows VER-01..VER-04 as `- [ ]` and `Pending`. I did not edit it. The orchestrator ticks them at close. VER-03 should be ticked with a note that its OpenRouter clause is accepted-inconclusive, not passed.

### Carried items (not Phase 10 goal gaps)

| Item | Why it is not a Phase 10 gap | Owner |
|---|---|---|
| C4 OpenRouter cache-write via router (uat-pending/05 item 3(c)) | Not exercisable in v1.0: the engine sends no explicit OpenRouter breakpoint (LATER-02). `turn2_cache_read=0` only observed. | Phase 11 waiver packet (`11-CONTEXT.md` line 74), routed through orchestrator yahir-gsd-control-plane-f2; waiver authority is Yahir |
| C5 OpenRouter EDIT-optional omission | Accepted by evidence, recorded as accepted and NOT a PASS | Phase 11 waiver packet next to C4; Yahir may still choose a different OpenRouter model for a live check |
| C1 low-credit 400 maps to Billing | Cannot be triggered without draining credit; covered by named unit tests | Gate-2 (milestone UAT), Yahir |
| C3 Responses-only 400 wording | Captured `http=400 reason=http_error`; body text deliberately not captured (LE-7) | Gate-2 (milestone UAT), Yahir accepts or asks for a host-side wording check |
| 3 minor wiring-test stumbles | Did not block the build; fixing them voids the pass | Phase 11 optional pre-cut doc touch; any doc edit needs an isolated wiring rerun (same isolation method) on the new SHA with `WIRING TEST: PASS` before the tag |

Phase 11 still must not cut `v1.0.0` until the waiver packet (C4, C5) is ruled on by Yahir, and, if any doc or public signature changes, until the isolated wiring rerun passes on a SHA that is an ancestor of the tag commit and API-identical to it. These are Phase 11 preconditions, not Phase 10 gaps.

### Required Artifacts, Key Links, Data Flow

Unchanged from the previous verification and spot-confirmed: the sample sources and scripts have not changed since the Gate-1 build except `scripts/run-sample-gate1.sh` (the SIGPIPE fix in `6ee25b7`). All artifacts VERIFIED, all links WIRED, device data FLOWING (`cache_*=7016`, `tools=18`, `optional_absent` from live tool calls, persisted budget `10/33 optional 1/1`).

### Anti-Patterns Found

None blocking. No `TBD`/`FIXME`/`XXX` in the phase's code. The previous `rerun_sha: TBD` placeholder in `10-WIRING-TEST.md` is gone (`rerun_sha: 36c578f464`). Review (CR-01, WR-01..WR-07) resolved 8 of 8; security SECURED, 0 open threats.

### Behavioral Spot-Checks and Probes

| Behavior | Command | Result | Status |
|---|---|---|---|
| Docs coverage | `scripts/verify-docs-coverage.sh` | `DOC COVERAGE OK checks=23 types=96` | PASS |
| Docs and engine API unchanged since the tested SHA | `git diff --stat 18996be HEAD -- README.md INTEGRATION.md API.md ECOSYSTEM.md`; non-`.planning` name-only diff | empty; only `sample/` and `scripts/` | PASS |
| C5 host proof | `ChatAbsentOptionalTest` result XML | 10 tests, 0 failures, 0 errors, 0 skipped, parameterized over openai and openrouter | PASS |
| Whole build | not run here (orchestrator runs `./gradlew check`); previous pass exit 0 | n/a | carried |

Step 7c: SKIPPED (no `probe-*.sh` declared).

### Human Verification Required

None open for Phase 10. The items previously listed (C5, D-07 rerun) are resolved or accepted with orchestrator authority. C4, C1 and C3 are carried to the named Gate-2 and Phase 11 owners above.

### Gaps Summary

No gaps. SC1..SC4 are met on the evidence. The one clause that did not reach a live PASS (OpenRouter omitted-optional) is documented as accepted-inconclusive with a verified code and test basis, and is kept visible in the Phase 11 waiver packet rather than counted as a pass.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
