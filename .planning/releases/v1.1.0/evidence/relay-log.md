
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
- **P17 OI-1 (ruled 2026-10-07, replaces PENDING):** "Accepted: JournalStore is a save/delete-only mirror with no restore in v1.1.0; Undo-all is live-session only. SB 178 (D-05) and CT P75 (UNDO-01/D-05) both confirmed no objection. Condition (SB): JournalStore stays OPTIONAL, and with no store supplied the engine writes nothing to disk. Restore is deferred to a future additive release (SB UNDO-PERSIST)." Plus: "If the no-store/no-disk-write condition isn't already pinned by a test, add one before the tag."

## Relay 1: push main

relayed_by: yahir-gsd-control-plane-3b
time_utc: 2026-10-07T20:33Z (push performed about 20:33-20:34Z)

**Message sent by the master (verbatim):**

`pushing main 97801136ae456ba6b0a8861ee1ea49932cc7bb75`

Context lines sent with it: origin/main was MERGED, not rebased (74a62a7, including 43768ea), so every recorded SHA is intact; the push hold lifts "with v1.1.0" per D-06; no tag is pushed; the pre-push scan is clean (unexplained=0).

**Answer received (resume signal):**

`push ok 97801136ae456ba6b0a8861ee1ea49932cc7bb75 relayed_by=yahir-gsd-control-plane-3b`

**Orchestrator text (verbatim):**

"OK to push main 97801136ae. I verified it myself: local main = 97801136ae, origin/main (43768ea) is an ancestor (fast-forward, 545 commits), no tag points at it, and no sb-gold/sb-fixture/.env/jks files are tracked. Plain `git push origin main` only: no --force, no tags. Then run `release-cut.sh gate pushed` and tell me GATE OK with the remote sha."

**Push performed:** `git push origin main` -> `43768ea..9780113  main -> main`. Immediately after (before this log was committed): `scripts/release-cut.sh gate pushed` printed `GATE OK pushed`. `git ls-remote origin refs/heads/main` = `97801136ae456ba6b0a8861ee1ea49932cc7bb75 refs/heads/main`. Tags on origin: only v1.0.0 and v1.0.1 (plus peeled lines).

**Early asks (all answered; verbatim text is in the "Appended 2026-10-07" section above):**

- Q1: answered. P15 OI-4..7 and P17 OI-3/OI-5 accepted (see the P15 OI-4..OI-7 / P17 OI-3 / OI-5 lines above); P17 OI-1 ruled ("Accepted: JournalStore is a save/delete-only mirror with no restore in v1.1.0 ...", see the "P17 OI-1 (ruled 2026-10-07, replaces PENDING)" line above). P16 OI-1..9 accepted as defaults (earlier block).
- Q2: answered yes (see "C11 (a)" line above): "tag ready" may use a clone-simulated tag run, with a real re-run against the pushed tag on JitPack before the ledger-row relay.
- Q3: answered (see "RT-07 / W03 (b)" line above): JVM-only is enough, no device window.
- Q4: answered (see "Quiet windows 20-07/08/10" line above): same terms (single daemon, pause below 5 GiB, one retry); send "quiet window 20-xx" each time.
