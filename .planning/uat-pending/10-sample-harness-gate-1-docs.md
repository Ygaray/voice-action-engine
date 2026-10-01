### Phase 10 - sample-harness-gate-1-docs (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md`](phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md) - Verdict: **13 of 14 criteria PASS + 1 ACCEPTED BY EVIDENCE (G1-09 INCONCLUSIVE `model_filled_optional` after one rerun, accepted by the orchestrator 2026-10-01, NOT a PASS)** (device yahirs-s22-ultra-2 SM-S908U, APK md5 `4c6fc98c64485ce878e11e60fdf92559` @ `4a586ed7b8`, 2026-10-01). Live legs L1-L6 and the optional L7 ran on the TESTER through the `:sample` UI; spend: 11 requests (anthropic=3 openai=4 openrouter=4), est USD 0.014.
- **Items covered (4 ROADMAP success criteria, 14 runbook criteria):**
  - **VER-01 - on-device base (G1-01..G1-05).** Fixture absent is loud and refuses with no spend; fixture present and verified (prefix `ebd3ef4a`, tools=18 equals host); OkHttp 5.2.1 pin runs on the device; bring-your-own key save, relaunch and delete through `:keystore`; the three test keys imported through `:keystore` with the plaintext files deleted.
  - **VER-02 - Anthropic agentic cold run (G1-06).** PASS: 2 turns, write 7016, read 7016, both 200, `min_cacheable=4096`, claude-haiku-4-5.
  - **VER-03 - single-shot smokes and multi-turn (G1-07, G1-08, G1-09, G1-10, G1-11).** Anthropic and OpenAI smokes PASS; OpenAI Chat and OpenRouter multi-turn PASS; OpenRouter smoke INCONCLUSIVE (`model_filled_optional`) on both prompts.
  - **VER-04 - clarification and partial on device (G1-12).** Offline demos PASS: question and two options shown, follow-up completes after choosing, partial readout "Did 1 action(s), couldn't finish".
  - **Spend and cleanup (G1-13, G1-14).** 10/33 core and 1/1 optional; keys directory empty; sample package removed; no fixture left on host.
- **Carry dispositions (register C1-C7):**
  - **C1 Billing mapping:** NOT EXERCISED (cannot be triggered without draining credit); covered by the named unit tests. Stays a Gate-2 item.
  - **C2 key charset:** proven per provider by `key_charset=ok` after a 200 (Anthropic, OpenAI, OpenRouter). Closed.
  - **C3 Responses-only 400:** L7 CAPTURED http=400 reason=http_error. Captured per the register; the exact marker wording is not visible under LE-7, so Yahir may accept or ask for a host-side wording check.
  - **C4 OpenRouter cache-write accounting (uat-pending/05 item 3(c)):** not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet. NOT closed. `turn2_cache_read=0` observed in L6.
  - **C5 OpenRouter EDIT-shaped call omitting optionals:** INCONCLUSIVE(`model_filled_optional`) after one rerun (G1-09), **ACCEPTED BY EVIDENCE by the orchestrator (2026-10-01), not a PASS**. Evidence: the shared `ChatCompletionsProvider`/`ChatVendor` decoder path passed the live OpenAI omitted-optional check, and host tests prove no default-filling. Listed in the Phase 11 waiver packet next to C4 so Yahir sees both open items together.
  - **C6 `disable_parallel_tool_use`:** host golden plus live 200 on the first attempt (L2), accepted by the orchestrator. Closed.
  - **C7 deferred live legs:** not applicable (decision approved).
- **Owner how-to-verify (run at milestone completion):**
  1. Read the Gate-1 log above and the nine `evidence/gate1-*.txt` files.
  2. Decide the C4 waiver (cache-write via router, LATER-02) and confirm the C5 acceptance (already accepted by the orchestrator; Yahir may instead choose a different OpenRouter model for the optional-omission check).
  3. Optionally re-run L4 from the `:sample` screen on the TESTER with `scripts/run-sample-gate1.sh` (needs `push-keys` and a fresh install) to see whether a different model omits the optionals.
  4. `10-WIRING-TEST.md` is `status: pass` (isolated rerun on `36c578f464`, `WIRING TEST: PASS checks=9`). If Phase 11 edits README, INTEGRATION, API or ECOSYSTEM (the optional fix for the 3 minor stumbles), the isolated wiring test must be rerun on the new SHA before the tag.
- **Note:** `scripts/run-sample-gate1.sh` `package_installed` returns 141 under `pipefail` (grep -q SIGPIPE), so `build-install` reports `install_failed` after a good install and `preflight` reports `installed=no`; routed to gap-closure as a tooling defect. The runbook G1-01 step order should be capture-start, then a cold relaunch. The TESTER is cleaned (no package, no keys).
