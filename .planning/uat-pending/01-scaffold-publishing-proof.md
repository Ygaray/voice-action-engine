### Phase 1 — scaffold-publishing-proof (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/01-scaffold-publishing-proof/01-SELF-UAT.md`](phases/01-scaffold-publishing-proof/01-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless CLI/build + live JitPack, no device; artifacts core.jar md5 `2080ab4a048088a3a2b89b4080463aba` @ `96c9c62`, live ref `a40f8319ca`, 2026-09-30). Live consumer resolution from an empty Gradle cache, green `check`, 69 negative controls, api-dump wiring proof, OkHttp matrix legs with runtime guard.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 — Per-module JitPack coordinates.** core, providers (pulls core), keystore resolve by SHA from an empty cache; `:sample` absent from the install command and module list; ECOSYSTEM.md lists the coordinates.
  - **SC2 — check green, JVM 11, clean :core classpath.** 137-task `check`, class-file major 55 in all three artifacts, no HTTP/Android/DI on `:core` runtime classpath.
  - **SC3 — Banned constructs fail check.** Script (69 plants) plus hand-planted planning-id comment, detekt maxIssues 0, no baseline.
  - **SC4 — explicitApi + Metalava.** Undeclared visibility rejected in all three modules; api.txt dumped in an isolated copy only; none in the real tree.
  - **SC5 — Harnesses.** OKHTTP_RUNTIME 4.12.0 / 5.2.1 / 5.5.0 legs green; `:core` harness test under NoNetworkGuard; graphify-out and A10 fixture gitignored.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `scripts/jitpack-live-probe.sh <sha>` for the milestone HEAD and `./gradlew check`.
  3. Confirm Phase 3 delivers the `FakeAiProvider` owed by the accepted SC5 scope-reading (Phase 1 shipped the generic harness primitives only).
- **Note:** No physical or device-hardware step; nothing deferred. Registered for ledger completeness, so the owner can sign off without action. Live proof ref is a SHA; the tag-level proof belongs to Phase 11.
