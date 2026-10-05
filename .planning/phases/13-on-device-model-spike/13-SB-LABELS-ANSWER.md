# 13 SB envelope pin - sb_labels answer (relayed by master, 2026-10-05)

Source: SB via orchestrator yahir-gsd-control-plane-3b; master re-verified the sha; orchestrator (this executor stage) re-verified sha256 on host.

- Re-pin the spike's SB envelope to SB's CURRENT surface:
  `/home/yahir/Projects/AndroidApps/Personal/SecondBrain/.planning/cross-repo/sb-a10-fixture.json`
- sha256 `8bc739ede3bbde748c0ba35c66522c841158e90296b8995618740014c195dcb6`
- 19 tools including `ask_user_to_choose`; 21938-char prefix; from SB c26502e55 / XR-175-01.
- The old 18-tool pin `ebd3ef4a...` (35,464 B) is byte-identical inside it (the new file is 37,157 B).
- Caveat: SB 179 runs SingleShot-only on device and may cut a narrower subset (e.g. no reminders). The spike's tool count is a REPORTED dimension in the results, never a hard assumption.
- PRIVACY: the fixture stays private. Push only into app-private storage on the device; NEVER commit it (repo is public). Do not copy it into the repo tree.
- Record this sb_labels answer in 13-WINDOW-GRANT.md when the grant lands.
- gemma3_1b: Yahir's call, pending. If unanswered when device plans run, record `skipped_gated`.
- timebox_s=14400 (one TESTER window, at most 4 h; requested at 13-08 start, released when device work is done).
