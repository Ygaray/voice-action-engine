# R-v1.1 consumer answers (input to /gsd-discuss-milestone)

Relayed by the orchestrator (yahir-gsd-control-plane-6e), 2026-10-05. These are peer answers to VAE's questions in the
research-complete message (decision map 2e9e349). They are inputs for Yahir's discuss decisions, not decisions themselves.

| # | Topic | Decision-map area | Answer | Condition for VAE |
|---|---|---|---|---|
| 1 | Grammar with a label present | P14 [label] | SB OK with "labeled pack only + opt-in try-both"; SB will set try-both | **A null label must try both packs by default.** |
| 2 | Terminal/navigation intents | P14 [terminal] | SB needs it (D-05 G6/G7) | A zero-commit `terminal` intent → `TerminalCall`; ends the walk as handled, carrying intent + slots; **not** NoMatch; **works under offline-only**. |
| 3 | Plan hold semantics | P15 [hold] | SB OK: stop at first hold | Steps committed before the hold stay committed (escalation suppressed); the held step surfaces for the needs-confirmation sheet. |
| 4 | Plan steps' providerCallId | P15 [call-id] | SB OK: the `submit_plan` call id | **Each step's ExecutedAction keeps a distinct ordinal/step index** (SB keys undo by runId + ordinal, not providerCallId). |
| 5 | heldRunId + clarification grouping | P17 [grouping] | SB OK | Keep `parentRunId` on the clarification-reply group so a combined undo stays possible later. |
| 6 | Picker StrategyId mapping | P16 [pickcontext] | SB OK | SB maps its picker StrategyId → `claude-haiku-4-5`. |
| 7 | `normalize` null semantics | P14 [normalize] | CT OK: null = reject | `normalize` returns canonical English for in-map EN/ES tokens; null → NoMatch → cloud. |
| 8 | :undo bridge placement (A18) | P17 [bridge] | **Final** (orchestrator + SB + CT): app glue + sample + doc snippet + additive `compositeSink`; no 6th module (E7's five modules) | **SB: `compositeSink` isolates a throwing child sink from its siblings and from the pipeline.** A18 is met if the engine exposes the `compositeSink` seam and the sample proves the bridge end to end. |
| 9 | stt labels that can be fallbacks | P18 [stt-semantics] | pending (stt answers separately) | — |

Also accepted by the orchestrator: brief corrections (a) KeyAccess is a plain `interface`, still opt-in `@DelicateKeyAccess`;
(b) providerCallId via internal `submit()` plumbing; (c) W04 has two internal causes.
