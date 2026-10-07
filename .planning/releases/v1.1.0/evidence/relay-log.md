
## Orchestrator rulings relayed by the milestone master (verbatim quotes)

Source: cross-session messages from orchestrator `yahir-gsd-control-plane-3b`, recorded by the milestone master, 2026-10-06/07.

- **P14 OI-1..OI-8:** on file at `.planning/phases/14-localgrammar-bilingual-grammarpack/14-CONTEXT.md`, Runtime Decisions RT-02 and RT-03. Verbatim message: "OIs: OI-1, 3, 4, 5, 7 and 8 are accepted as their defaults. OI-2 and OI-6 are provisionally accepted." Followed by: "OI-2 and OI-6 are now final: no objection from CT or SB."
- **P15 OI-1:** "OI-1 ruling (SB objected; adopting SB's rule, which matches the R-v1.1 Q3 binding "pre-hold commits stay")". Full text on file at `15-CONTEXT.md` (BINDING RULING block).
- **P15 OI-2:** "OI-2 is accepted as is."
- **P15 OI-1 follow-up:** "OI-1 follow-up: do NOT narrow it. remainingStepIds stays filled on any non-complete terminal partial (hold or failure), because consumers want "what didn't run" in both cases."
- **P16 OI-1..OI-9:** "P16 OI-1..9 are accepted as defaults. I'll carry "SB 177 maps its picker id in ProviderSelectionSource" to SB at its dispatch."
- **P17 compositeSink policy:** "compositeSink policy is accepted: run all children, then one fixed-text counts-only fault → sink_error with the outcome unchanged. That satisfies SB's "isolate the throwing child sink" (row 8), so I'm not asking SB again."
- **P15 OI-4..OI-7, P17 OI-1/OI-3/OI-5:** NOT YET RELAYED (requested from the orchestrator 2026-10-07). Use FALLBACK until a verbatim ruling is appended below.

### Appended 2026-10-07: verbatim rulings from orchestrator yahir-gsd-control-plane-3b

- **P15 OI-4:** "Accepted: plan steps share the planning call id; step identity = position ordinal (consistent with the R-v1.1 SB Q4 binding: distinct ordinal per step)."
- **P15 OI-5:** "Accepted: at most one replan, only for a first-step fault before any write."
- **P15 OI-6:** "Accepted: a PREVIEW/READ/no-action result from a mutating step is a failed step; the run stops."
- **P15 OI-7:** "Accepted: the replan digest carries engine codes + step index only, never app content."
- **P17 OI-3:** "Accepted: Undo-all defaults 50 groups / 1 h idle."
- **P17 OI-5:** "Noted: :undo carries 1 suppression (corrects the earlier 2)."
- **P17 OI-1:** PENDING. The orchestrator is asking SB and CT and will send verbatim text before 20-05. Until then, use FALLBACK.
- **C11 (a):** "Yes for "tag ready". C11 against a clone-simulated tag counts as pre-tag green, but C11 must re-run for real against the pushed tag on JitPack before you send me the ledger-row relay."
- **RT-07 / W03 (b):** "JVM-only is enough for RT-07 / W03. No device window."
- **Merge:** "OK to MERGE origin/main (43768ea, the YAT v2.5.0 ledger row) into main, never rebase, before "pushing main <sha>"."
- **Quiet windows 20-07/08/10:** "same terms (single daemon, pause below 5 GiB, one retry). Send "quiet window 20-xx" each time and I'll check swap/memory and take the lock."
