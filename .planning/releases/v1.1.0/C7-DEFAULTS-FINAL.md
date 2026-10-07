# v1.1.0 shipped defaults of the carried open items (C7): FINAL

These are the 21 open surface-review items carried by the Phase 19 register (`gate2-carry-register.txt` C7, listed in `19-API-REVIEW.md` "Carried open items"). The shipped default of each one freezes at v1.1.0 (status FINAL). Written by plan 20-05 (RT-08), 2026-10-07 (UTC).

Citation rule: every ruling cell is a verbatim quote of at most 25 words with its file and line, taken from an orchestrator ruling on file in this repo or relayed into `relay-log.md`. No ruling text is invented or paraphrased, and every one of the 21 items has a verbatim ruling on file. Shipped defaults are paraphrased in a few words from the phase SURFACE-REVIEW files (the citation, not the default, is the ruling). Relay-log lines are cited as `relay-log.md:N` (`.planning/releases/v1.1.0/evidence/relay-log.md`).

| Item | Shipped default | Status | Ruling citation |
|---|---|---|---|
| P14 OI-1 | `normalize` hook accepted on text and choice slots, rejected at build time on number slots | FINAL | `14-CONTEXT.md:115` (RT-02) "OI-1, OI-3, OI-4, OI-5, OI-7 and OI-8 are ACCEPTED as their shipped defaults." Also `relay-log.md:6` |
| P14 OI-2 | matchedLanguage is the command's own label else null on cross-pack agreement, ruleId null | FINAL | `14-CONTEXT.md:116` (RT-03) "OI-2 and OI-6 are now FINAL: neither CT nor SB objects". Also `relay-log.md:6` |
| P14 OI-3 | ruleId is an opaque string, null on cross-pack agreement, documented as not to be parsed | FINAL | `14-CONTEXT.md:115` (RT-02) "OI-1, OI-3, OI-4, OI-5, OI-7 and OI-8 are ACCEPTED as their shipped defaults." |
| P14 OI-4 | resolver optional only for an all-terminal pack, otherwise a build-time IllegalArgumentException | FINAL | `14-CONTEXT.md:115` (RT-02) "OI-1, OI-3, OI-4, OI-5, OI-7 and OI-8 are ACCEPTED as their shipped defaults." |
| P14 OI-6 | bare a, an, un, una are not numbers (count only inside compounds) | FINAL | `14-CONTEXT.md:116` (RT-03) "OI-2 and OI-6 are now FINAL: neither CT nor SB objects". Also `relay-log.md:6` |
| P14 OI-7 | an interior sentence terminator between words matches nothing (edge punctuation ignored) | FINAL | `14-CONTEXT.md:115` (RT-02) "OI-1, OI-3, OI-4, OI-5, OI-7 and OI-8 are ACCEPTED as their shipped defaults." |
| P15 OI-4 | every plan step shares the planning call's id; step identity is the position ordinal | FINAL | `relay-log.md:16` "Accepted: plan steps share the planning call id; step identity = position ordinal" |
| P15 OI-5 | a first-step fault before any write is replanned once | FINAL | `relay-log.md:17` "Accepted: at most one replan, only for a first-step fault before any write." |
| P15 OI-6 | a PREVIEW, READ or no-action result from a mutating step is a failed step and the run stops | FINAL | `relay-log.md:18` "Accepted: a PREVIEW/READ/no-action result from a mutating step is a failed step; the run stops." |
| P15 OI-7 | the replan digest carries engine codes and a step index only, never app content | FINAL | `relay-log.md:19` "Accepted: the replan digest carries engine codes + step index only, never app content." |
| P16 OI-2 | default ids `start_tier_picker` (Custom) and `start_tier_router` (Router), overridable | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." |
| P16 OI-3 | public names StartTierSelection, StartTierSelected, pickerTimeoutMillis, tierDescriptions, capabilities | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." |
| P16 OI-4 | router output cap is policy.maxTokensPerTurn (no dedicated field) | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." |
| P16 OI-5 | a single router_fallback code, no fallback cause token | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." |
| P16 OI-7 | step-id 64-character cap, parse-only, remainingStepIds not narrowed | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." Also `relay-log.md:9` (P15 OI-1 follow-up, not narrowed) |
| P16 OI-9 | a policy forbidding every provider a picker declares never calls it (JVM-proven, no device proof) | FINAL | `relay-log.md:10` "P16 OI-1..9 are accepted as defaults." |
| P17 OI-1 | JournalStore is a save/delete-only mirror; Undo-all is live-session only; the store stays optional and with none supplied the engine writes nothing to disk (pinned by JournalStoreTest.withNoStoreSuppliedTheEngineWritesNothingToDisk, `evidence/rt-outcomes.txt` P17-OI-1); restore deferred to a future additive release (SB UNDO-PERSIST) | FINAL (SB 178 and CT P75 confirmed, no confirmation owed) | `relay-log.md:27` "Accepted: JournalStore is a save/delete-only mirror with no restore in v1.1.0; Undo-all is live-session only." |
| P17 OI-3 | Undo-all defaults of 50 groups and one hour idle (builder vars) | FINAL | `relay-log.md:20` "Accepted: Undo-all defaults 50 groups / 1 h idle." |
| P17 OI-5 | `:undo` carries one suppression (the file-level TooGenericExceptionCaught in Guard.kt); the second was removed by e1b5653 | FINAL (closed by RT-04) | `relay-log.md:21` "Noted: :undo carries 1 suppression (corrects the earlier 2)." Also `evidence/rt-outcomes.txt` RT-04 |
| P18 (a) | gates 10 and 12 and selftest step 4 get a new-module branch covering both `:undo` and `:voice-adapter` (header-only api.txt seeds, dumps reviewed and committed at the tag) | FINAL (closed by RT-02, wired in plan 20-06) | `20-CONTEXT.md:96` (RT-02) "Phase 20 OWNS the deliberate reds from P17 17-04: release-cut gates 10 and 12 plus selftest step 4" |
| P18 (g) | verify-stt-confinement.sh wired into the release preflight | FINAL (closed by RT-05, wired in plan 20-06) | `20-CONTEXT.md:103` (RT-05) "Wire scripts/verify-stt-confinement.sh into release-cut.sh, next to the gates 10/12 + selftest step 4 new-module branch" |

Notes:

- P14 OI-5 and OI-8, P15 OI-1 to OI-3, P16 OI-1, OI-6 and OI-8, P17 OI-2, OI-4, OI-6 to OI-8 and P18 (b) to (f) are not in the 21: they were closed or carried to other plans before the C7 list was cut (see `19-API-REVIEW.md` "Carried open items").
- The P17 OI-5 ruling text was relayed after the review rows were written; `19-API-REVIEW.md` and `17-09-SUMMARY.md` still say two suppressions and stay as written (closed artifacts, superseded by pointer).
- P18 (a) and (g) cite the phase context directive that assigns the fix. They are closed when plan 20-06 lands the gate branch and the preflight wiring; the evidence of that is plan 20-06's own record.
