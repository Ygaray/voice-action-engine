Draft only. Not the ledger. The orchestrator commits the row to §11 (A14) and writes the registries (LE-5).

| Date | Repo | Tag | Commit | Coordinate(s) | Contents | Evidence | Consumers repinned |
|---|---|---|---|---|---|---|---|
| 2026-10-02 | voice-action-engine | v1.0.0 | efc060f8fe462b71af2e4586b75a97db119ebabd | com.github.Ygaray.voice-action-engine:voice-action-engine-core:v1.0.0, com.github.Ygaray.voice-action-engine:voice-action-engine-providers:v1.0.0, com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:v1.0.0 | Contract §6.2 steps 1-7. :core (pure Kotlin, no HTTP): contract types, CommandPipeline, TierSelector.Linear, TierPolicy defaults 6 / 60000 / 4096, telemetry, PreApplyGate/CommitSink (A6/A17), A19 terminal calls, clarification + parentRunId, ON_DEVICE seam with capability gate (A5). :providers: Anthropic with prompt caching, OpenAI + OpenRouter Chat Completions, OkHttp 4.12 compile floor, green on 4.12.0 / 5.2.1 / 5.5.0 (A1). :keystore: AndroidKeyStore AES/GCM + DataStore BYO keys, SB/CT legacy compat, public KeystoreCauseCodes, minSdk 35. SingleShotStrategy and AgenticLoopStrategy over the neutral transcript with per-dialect mappers. Gate-1 on the TESTER: 13/14 PASS + 1 ACCEPTED BY EVIDENCE (G1-09, NOT a pass); agentic cache write/read 7016. Metalava api.txt baseline is in the tag; isolated docs-only wiring test PASS at be49ea8fc5 (W10). Uncached in v1.0: OpenRouter anthropic/* (LATER-02). Do not pin the aggregator coordinate (E5). | voice-action-engine .planning/phases/11-cut-v1-0-0/11-LEDGER-ROW.md, evidence/11-JITPACK-VERIFY.log (3/3 modules built, POM version v1.0.0, clean-cache resolve), evidence/cut-v1.0.0.txt, evidence/baseline.txt, 11-WIRING-RERUN.md | — |

## Evidence index

- `.planning/phases/11-cut-v1-0-0/evidence/11-JITPACK-VERIFY.log` (LIVE PROBE PASS ref=v1.0.0; api isTag true, status ok, commit = C, modules core/keystore/providers, no sample)
- `.planning/phases/11-cut-v1-0-0/evidence/cut-v1.0.0.txt` (CUT OK, CUT VERIFIED, annotated tag object 343fd3f28676f4bf4bfb76a1b6ad0cb23b59bfaa peeling to C)
- `.planning/phases/11-cut-v1-0-0/evidence/preflight-v1.0.0.txt` (PREFLIGHT OK, 15/15 gates; leak content_check=ran(master))
- `.planning/phases/11-cut-v1-0-0/evidence/baseline.txt`
- `.planning/phases/11-cut-v1-0-0/11-WIRING-RERUN.md`

## message_to_orchestrator

voice-action-engine v1.0.0 is cut and JitPack-verified (3/3 per-module coordinates resolve from a clean cache). §11 row below, for you to commit and broadcast (A14); it is not committed in this repo. Registry and deps-index entries are yours (LE-5). Consumers repin only to this ledger tag and send you their repin rows.

Row args (xrepo ledger-row): repo=voice-action-engine tag=v1.0.0 commit=efc060f8fe462b71af2e4586b75a97db119ebabd (peeled sha of the annotated tag; tag object 343fd3f28676f4bf4bfb76a1b6ad0cb23b59bfaa) coords=com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:v1.0.0. Full row text is the table above; machine-readable args are in evidence/ledger-row.txt.

## Post-tag follow-ups (milestone close)

- (a) `git.create_tag` must stay false through milestone close so no `v1.0` marker tag appears next to `v1.0.0` (D-02, INC-2026-09-30-01).
- (b) Human Gate-2 runs at milestone close after the tag (gsd-verify-milestone drains uat-pending). Carry-to-gate-2: W04 (real OpenAI Responses-only 400, does the body match RESPONSES_ENDPOINT_MARKER; generic http_error fired on device).
- (c) Repin rows come from the consumers through the orchestrator (§11 rule 7).
- (d) `scripts/verify-repo-hygiene.sh` default pre-release mode now fails by design; later gates run `PRE_RELEASE=0`. Making the default tag-aware is a v1.1 tooling candidate.
- (e) Six wiring-rerun doc stumbles (evidence/wiring-rerun-stumbles.txt) become v1.0.x doc-patch follow-ups (docs on main may change after the tag; the tag does not). First: ECOSYSTEM.md header mentions `~/.claude/context/...` private paths an integrator cannot read. Others: INTEGRATION.md section 10 snippet lacks an import list; API.md lacks FailureReason.Other / ModelResult.Success/Failure / ModelResponse / AssistantMessage constructor shapes; INTEGRATION.md section 9 snippet imports and Clarification/FailureReason field descriptions missing from API.md; INTEGRATION.md section 3 should say INTERNET permission is needed only when `providers` is used; README/INTEGRATION lack Kotlin/AGP 9 guidance for the consumer :app.
- (f) Any defect found later means v1.0.1 plus a superseded row, never a moved tag.
