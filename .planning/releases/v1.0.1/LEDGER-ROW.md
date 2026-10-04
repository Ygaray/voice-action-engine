# v1.0.1: §11 ledger row (messaged to the orchestrator; never committed to §11 here)

repo: voice-action-engine
tag: v1.0.1
commit: b32840e7ebe7066bfe8432b71ddb9e0eaca8e5f3
tag_object: 5d8dde4b0115c8907a457fd2d21d294db270b5f5
date: 2026-10-04
coords: com.github.Ygaray.voice-action-engine:voice-action-engine-core:v1.0.1, com.github.Ygaray.voice-action-engine:voice-action-engine-providers:v1.0.1, com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:v1.0.1
supersedes: v1.0.0 (patch; no public API change, api.txt byte-identical)
contents: XR-171-01 Anthropic stop_sequence maps to END_TURN; XR-171-03 (ruling a) a gate fault is an error, never a hold (is_error action with code gate_error, no HeldProposal, never in commits or COMMITTED; model gets isError=true internal_error and the fault counts as a strike; a real Hold is unchanged; same for SingleShot); doc patch: ECOSYSTEM repin matrix (SB/CT at v1.0.0), private paths removed, KDoc drift, wiring stumbles, version to pin, KeystoreCauseCodes KDoc; release-cut.sh tag-agnostic with fail-closed waiver gate
evidence: .planning/releases/v1.0.1/LEDGER-ROW.md, .planning/releases/v1.0.1/evidence/cut-v1.0.1.txt (PREFLIGHT OK 15/15 + CUT OK), .planning/phases/11-cut-v1-0-0/11-WIRING-RERUN.md (isolated wiring PASS at 5f0a6b03b4), .planning/releases/v1.0.1/NO-WAIVERS.md
jitpack: api/builds v1.0.1 status=ok isTag=true commit=b32840e7eb modules=core,keystore,providers; all three POMs 200; providers -> core v1.0.1
notes: cut needed 6 attempts. Attempts 1-5 were earlyoom-killed (swap full); attempt 6 ran after Yahir's swap reset. Attempt 5's memory pressure also caused earlyoom to kill another project's Gradle daemon (reported to the orchestrator).
