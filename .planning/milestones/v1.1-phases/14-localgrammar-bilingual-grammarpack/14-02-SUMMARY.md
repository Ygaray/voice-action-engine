---
phase: 14-localgrammar-bilingual-grammarpack
plan: 02
subsystem: d12-stt-capture-prep
tags: [d-12, tester-window, stt-capture, guard-runner, rae, number-words, host-only]

requires:
  - phase: 13
    provides: window-grant file shape, run-sample-gate1.sh guard pattern, shared TESTER flock
provides:
  - 14-WINDOW-GRANT.md (grant pending) with the Relay text for the orchestrator
  - stt-prompts.tsv, 88 neutral EN/ES prompts, and the grammar fixtures README (provenance, caveats, column contract)
  - scripts/run-stt-capture.sh, the only sanctioned host path to the TESTER for the D-12 capture
  - scripts/verify-stt-capture-guard.sh, offline proof of the runner (54 scenarios, fake adb and gradlew)
  - 14-RAE-CHECK.md, all 11 Spanish number rules confirmed against RAE
affects: [14-03, 14-09]

requirements-completed: []
requirements-contributed: [GRAM-02]

actuals:
  tokens: 13800
  tasks: 3
  commits: 3

key-files:
  created:
    - .planning/phases/14-localgrammar-bilingual-grammarpack/14-RAE-CHECK.md
    - core/src/test/resources/grammar/stt-prompts.tsv
    - core/src/test/resources/grammar/README.md
    - scripts/run-stt-capture.sh
    - scripts/verify-stt-capture-guard.sh
  modified:
    - .planning/phases/14-localgrammar-bilingual-grammarpack/14-WINDOW-GRANT.md

key-decisions:
  - "Grant gate reads only the FIRST block of key lines (^[a-z_]+: ) and requires exactly one grant line equal to grant: open; a later grant: open, a duplicate grant line, trailing space, a missing file and pending/consumed/deferred all refuse (exit 4, reason=no_grant) before the lock and any adb call"
  - "Runner is USB-only: no wireless fallback and no adb connect; the TESTER is seen through the read-only adb devices listing, every other adb call carries -s R5CT10XNKQN"
  - "RAE was read through Wayback snapshots of the same DPD entries because rae.es answers 403 to curl; all 11 rules confirmed, so no human-skim obligation"

coverage:
  - id: D1
    description: "14-WINDOW-GRANT.md is pending with a Relay section; no grant: open is ever written by this plan"
    requirement: "GRAM-02"
    verification:
      - kind: command
        ref: "Task 1 verify (grant: pending, ## Relay, no grant: open)"
        status: pass
    human_judgment: false
  - id: D2
    description: "stt-prompts.tsv has 88 well-formed neutral rows, unique ids, 11 reject rows, every class in both languages"
    requirement: "GRAM-02"
    verification:
      - kind: command
        ref: "Task 1 verify (header, >=80 rows, no duplicate ids, no domain word)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Runner refuses every device subcommand without an open grant, before the lock and before any adb call; build and filter are host-only"
    requirement: "GRAM-02"
    verification:
      - kind: command
        ref: "scripts/verify-stt-capture-guard.sh (STT CAPTURE GUARD OK scenarios=54)"
        status: pass
      - kind: command
        ref: "scripts/run-stt-capture.sh preflight on the pending grant: exit 4, STT_CAPTURE: INFRA sub=preflight reason=no_grant"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every Spanish number rule the lexicon freezes has a recorded RAE status"
    requirement: "GRAM-02"
    verification:
      - kind: command
        ref: "Task 3 verify (11 well-formed RAE rule= lines, no unverified without the skim line)"
        status: pass
    human_judgment: false

duration: 45min
completed: 2026-10-06
status: complete
plan_head_before: 3295025396dedadf871e887cbf9931b04770f536
commits: 3
---

# Phase 14 Plan 02: D-12 capture prep Summary

## Driver action: relay the window request now

Addressed to the orchestrator named in the effort (`yahir-gsd-control-plane-3b`, via the milestone master). The master already relayed a pre-approval on 2026-10-06 (`14-09 PRE-APPROVED: timebox_s=3600, TESTER R5CT10XNKQN only, no radio/airplane changes`), recorded verbatim in `14-WINDOW-GRANT.md`. The grant stays `grant: pending`; nothing in this plan opened it. Still to do on the driver side: confirm the fallback (open item OI-5) and, when 14-09 starts, confirm the TESTER is free and re-dispatch with `grant: open`. Relay text (verbatim from the `## Relay` section of `14-WINDOW-GRANT.md`):

> VAE Phase 14 D-12 requests one TESTER window (R5CT10XNKQN only), at most 2 h (the orchestrator pre-approved 1 h, `timebox_s: 3600`), to be consumed when the phase reaches plan 14-09 (after its JVM gate; the request is sent now so it can be scheduled). No API keys, no spend, full cleanup (uninstall, pushed files removed), no overlap with Phase 19 Gate-1.
> Purpose: record what the platform recognizer emits for synthetic TTS prompts (EN/ES numbers, decimals, accents) through an opt-in :sample instrumentation tool (about 90 prompts). HEAD sha at request: 3295025.
> Fallback if no window arrives before Phase 14 closes: ship the strict golden table (it rejects what it cannot verify, so a late finding only widens acceptance) and record a deferred obligation; please confirm the fallback (open item OI-5).

No device command ran in this plan (no adb, no Gradle).

## Accomplishments

- Task 1 (`f8f21a3`): `14-WINDOW-GRANT.md` extended in place (kept the master's relay log, pending line and handshake text verbatim; added the refusal-rule header, `requested`, `timebox_s: 3600`, `device`, and the `## Relay` section). `stt-prompts.tsv` has 88 rows (EN 41, ES 47: units, teens, tens, hundreds, thousands, fraction, decimal, ES accent, near-misses) with neutral nouns only and 11 `reject` rows. The README states `provenance=synthetic-tts`, the TTS caveats and the `stt-fixtures.tsv` column contract.
- Task 2 (`ec145bb`): `scripts/run-stt-capture.sh` with subcommands `build preflight install push-prompts run pull filter cleanup`. Order for device subcommands is args, exact-line grant, foreign `ANDROID_SERIAL`, `pull` destination guard, flock, TESTER presence, identity (serial, SM-S908U, SDK >= 35). `run` never prints raw instrumentation output; `filter` is a python3 allow-list (prompt id, status ok, drops URL, key shape, control character, bad JSON, duplicates). `scripts/verify-stt-capture-guard.sh` runs 54 scenarios offline and four static scans (every adb call has `-s` or is `devices`, TARGET assigned only from the TESTER constant, no radio or connectivity command, grant check precedes the lock). I ran three negative controls (radio command, bare adb call, grant check removed): each made the verifier fail.
- Task 3 (`493339e`): `14-RAE-CHECK.md`, R01 to R11 all `confirmed`.

## Contract left for plan 14-09

- The runner expects the opt-in tool class `io.github.ygaray.voiceactionengine.sample.SttFormsCaptureTool`, run as `am instrument -w -e class <it> -e captureSttForms true io.github.ygaray.voiceactionengine.sample.test/androidx.test.runner.AndroidJUnitRunner`, and a final `OK (N tests)` line.
- Input dir: `/sdcard/Android/data/io.github.ygaray.voiceactionengine.sample/files/stt-capture/` (the runner pushes `stt-prompts.tsv` there; whether adb shell can write there on this Android 15 handset is unproven until the window).
- Output: `stt-capture/stt-forms.jsonl`, one object per line: `{"id","lang","status","recognized"}`, status `ok | error | tts_unavailable`.
- Runner does not touch volume; the master's relay asks that TTS volume stay moderate and be restored, which the tool or the 14-09 steps must do without any radio change.

## Deviations from Plan

1. **[Rule 3 - Blocking] `14-WINDOW-GRANT.md` already existed (untracked, written by the master).** The plan's Task 1 creates it, but the driver instruction says to stay off master-owned files. Resolved by extending it additively (nothing removed, `grant: pending` untouched, no `grant: open`) and committing it with Task 1, since the plan lists it and its verify needs the `## Relay` section.
2. **`timebox_s: 3600` instead of the plan's 7200.** The master's pre-approval fixed 3600; the Relay text names both numbers.
3. **RAE fetched through Wayback snapshots.** The plan's primary route (rae.es via WebFetch, then curl) gave 403 on all five URLs and WebFetch is not in this executor's toolset; the archived copies of the same DPD entries were used (added `/dpd/medio` for R11, which none of the five pages cover). Source URLs in the file point at the snapshots.
4. **GRAM-02 left Pending.** The plan frontmatter lists it and `requirements.mark-complete` flipped it, but the DSL with number words in both languages is built by plans 14-03, 14-04, 14-05 and 14-08; this plan only prepares D-12 and checks RAE. I reverted the flip in REQUIREMENTS.md so the requirement is not reported complete before its code exists; the plan that lands the DSL should mark it.
5. The verifier derives its "real grant file" scenarios by rewriting the first-block `grant:` line with sed (pending and open), so it stays valid after plan 14-09 changes the real file to `open`, `consumed` or `deferred`.

## Notes for downstream plans

- 14-03: R01 to R11 confirmed, no deviation from the research table. Bare apocopated forms (veintiun, treinta y un) are RAE-correct only before a noun or mil; accepting them bare is an STT alias choice. Accept treintaicinco-style, never treinticinco, trenta or trentaicinco.
- 14-09: `grant_real_layout_opens` proves the real file layout opens with one `grant: open` edit; write `grant: consumed` (or `deferred` plus `deferred_obligation:`) back into the first block.

## Verification

- Task 1, 2 and 3 automated verifies: exit 0.
- `scripts/verify-stt-capture-guard.sh`: `STT CAPTURE GUARD OK scenarios=54`.
- `scripts/run-stt-capture.sh preflight` on the pending grant: exit 4, last line `STT_CAPTURE: INFRA sub=preflight reason=no_grant`.
- `git ls-files -s`: both scripts mode 100755. No file deletions in any plan commit.

## Self-Check: PASSED

- Files exist: 14-WINDOW-GRANT.md, 14-RAE-CHECK.md, stt-prompts.tsv, grammar/README.md, run-stt-capture.sh, verify-stt-capture-guard.sh.
- Commits f8f21a3, ec145bb, 493339e present on gsd/phase-14-localgrammar-bilingual-grammarpack.
