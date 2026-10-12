---
phase: 19-sample-gate-1-docs
plan: 10
subsystem: docs
tags: [doc-02, verify-docs-coverage, readme, api-md, ecosystem-md, rt-01, selftest]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 09 (INTEGRATION.md content, the second snippet file), plan 08 (docs-versus-dump list)
provides:
  - verify-docs-coverage.sh generalized to every module in scripts/modules.list (C01, C20 types plus a top-level function pass, per-module vacuity)
  - checks C26-C32 (grammar, plan, router, undo, adapter, RT-01/RT-04, Phase 12 seams) and five more required regions
  - verify-docs-coverage.sh --selftest with ten planted violations
  - README final for v1.1.0 (pin, all five coordinates, "What v1.1 adds"), API.md RT-01 note and UndoResult.code, ECOSYSTEM.md v1.1.0 status
affects: [19-11, 19-12, 19-13, phase-20]

status: complete
actuals:
  tokens: 6800
  tasks: 3
  commits: 3
plan_head_before: cadeae5d3d29b8cf6f35126b3c7eedd479607481
commits: 3

tech-stack:
  added: []
  patterns:
    - "A coverage gate reads the module manifest, and each module must yield a public symbol or the gate fails as vacuous"
    - "A gate's --selftest copies only the files it reads into a temp dir and plants one violation per fresh copy"

key-files:
  created: []
  modified:
    - scripts/verify-docs-coverage.sh
    - README.md
    - API.md
    - ECOSYSTEM.md

key-decisions:
  - "API.md documents the real behaviour of the ASCII-only reference whitespace (a no-break space becomes part of the key and fails closed as plan_binding_unresolved; a non-matching near-reference stays literal), not the plan's one-sided wording"
  - "C31 rejects any toCommandInput( with exactly one argument in INTEGRATION.md and API.md (regex toCommandInput\\([^,()]+\\)), which covers the named and unnamed context-only form"
  - "Functions are required in backticks as the bare name (commandPipeline, compositeSink); the two API.md sentences were reworded to carry the bare name rather than loosening the check"

patterns-established:
  - "make_copy / apply_plant / run_copy selftest structure in verify-docs-coverage.sh"

coverage:
  - id: D1
    description: "C01 and C20 are manifest-driven over all five modules; C20 also requires every public top-level function; a module with no public symbol is a vacuous failure"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/verify-docs-coverage.sh (DOC COVERAGE OK checks=32 types=119, was 107)"
        status: pass
      - kind: other
        ref: "scripts/verify-docs-coverage.sh --selftest plants undo-coordinate, api-type, api-function, empty-module"
        status: pass
    human_judgment: false
  - id: D2
    description: "C26-C32 content checks and REQUIRED_REGIONS with grammar-tier, plan-tier, router-selector, undo-wiring, undo-bridge"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/verify-docs-coverage.sh (checks=32)"
        status: pass
    human_judgment: false
  - id: D3
    description: "--selftest: ten plants red naming their check, unplanted copy green, real tree untouched; covers the adapter block and the missing DocSnippetAdapterTest.kt"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/verify-docs-coverage.sh --selftest (DOC COVERAGE SELFTEST OK plants=10; git status identical before and after)"
        status: pass
    human_judgment: false
  - id: D4
    description: "README pins v1.1.0, names all five coordinates and links the INTEGRATION.md sections; ECOSYSTEM.md status describes v1.1.0 with the repin matrix byte-identical; API.md carries the RT-01 note and UndoResult.code"
    requirement: DOC-02
    verification:
      - kind: other
        ref: "scripts/verify-docs-coverage.sh (C01, C03, C24, C29, C31); repin-matrix block compared with HEAD; verify-stt-confinement.sh; verify-repo-hygiene.sh"
        status: pass
    human_judgment: false
  - id: D5
    description: "Whether the final docs are sufficient for a cold integrating agent (the isolated wiring test on the final SHA is the judge)"
    verification: []
    human_judgment: true
    rationale: "Only the wiring test in plans 19-11 and 19-13 can show it; DOC-02 stays pending until then"

duration: 25min
completed: 2026-10-07
---

# Phase 19 Plan 10: Manifest-driven doc coverage gate and final README/API/ECOSYSTEM Summary

**verify-docs-coverage.sh now requires every public type and top-level function of all five modules in API.md, runs 32 checks including a check per new tier, and proves itself with a ten-plant `--selftest`; README, API.md and ECOSYSTEM.md are in their final v1.1.0 state.**

## Performance

- **Duration:** about 25 min
- **Completed:** 2026-10-07
- **Tasks:** 3 (one tracer)
- **Files modified:** 4 (no file created)

## Accomplishments

- Task 1 (tracer): `check_C01` loops over `vae_modules` and builds each coordinate from the manifest's artifactId, for README and INTEGRATION. README pins `v1.1.0`, lists the undo and voice-adapter coordinates in the install block with a note for each, has a "What v1.1 adds" list whose items link INTEGRATION.md anchors, and a Status line true at the tag.
- Task 2: `public_types` is now per module (`public_types_of`) plus `public_funs_of` (column-0 public functions, extension receivers allowed, `fun interface` excluded). `check_C20` fails per module when it yields neither (vacuous). Types rose from 107 to 119. C26-C32 added, `REQUIRED_REGIONS` gained `grammar-tier plan-tier router-selector undo-wiring undo-bridge`. API.md gained the RT-01 note, `UndoResult.code` (`complete`, `refused`, `partial`, `already_undone`), the `start_tier_router` id and the bare names `commandPipeline` and `compositeSink`. ECOSYSTEM.md says undo and voice-adapter are first published in v1.1.0, no on-device module, status v1.1.0; the repin-matrix block is byte-identical to HEAD.
- Task 3: `--selftest` (see Verification) with `make_copy`, `apply_plant`, `run_copy`.

## Task Commits

1. **Task 1 (tracer): manifest-driven C01 and final README** - `452671a` (docs)
2. **Task 2: C20 over all modules, C26-C32, API and ECOSYSTEM text** - `a3b6d65` (docs)
3. **Task 3: --selftest** - `c1d0fc0` (docs)

**Plan metadata:** the commit that adds this SUMMARY, STATE.md and ROADMAP.md.

## Files Created/Modified

- `scripts/verify-docs-coverage.sh` - manifest-driven C01/C20, function pass, C26-C32, five more required regions, `--selftest`
- `README.md` - pin v1.1.0, five coordinates, "What v1.1 adds", status
- `API.md` - RT-01 note, `UndoResult.code`, `start_tier_router`, function names
- `ECOSYSTEM.md` - v1.1.0 status and unpublished-modules sentence, snippet-file sentence (adapter block lives in `DocSnippetAdapterTest.kt`)

## Verification

- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=32 types=119`.
- `scripts/verify-docs-coverage.sh --selftest`: `DOC COVERAGE SELFTEST OK plants=10`, `git status --porcelain` identical before and after, temp directory removed on exit. Plants (each red with the exact expected line): undo coordinate removed from README (C01); `UndoGroup` name removed (C20 type); `toCommandInput` name removed (C20 function); "Three overloads" added (C31); lone-context `toCommandInput(context)` added to INTEGRATION (C31); the ASCII note removed (C31); a changed identifier in the INTEGRATION adapter-wiring block (C06 differs from its region); `DocSnippetAdapterTest.kt` removed (C06 no region); a manifest with no module row (the gate's own failure); a manifest row for a module with an empty main source tree (C20 vacuous). The unplanted copy is green.
- `scripts/verify-stt-confinement.sh`: `STT CONFINEMENT OK checks=6`; `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`; `scripts/verify-module-manifest.sh`: OK.
- Repin-matrix block compared with `git show HEAD:ECOSYSTEM.md`: identical. `grep -c 'not yet published'` and `grep -c 'is planned'` in ECOSYSTEM.md print 0. No Gradle was run (Bash and docs only), no device touched, no api.txt edited.
- INTEGRATION.md was not changed by this plan: the new checks found no gap in it, so the wiring-SHA freeze from 19-09 is intact.

## Decisions Made

- The RT-01 sentence documents what the regex really does (see Deviations 1).
- C31's one-argument test is a regex over both INTEGRATION.md and API.md rather than a fixed phrase, so `toCommandInput(context)` and `toCommandInput(context = x)` both go red.
- The selftest leaves C23 out of the copy run (it asks git for a tag) and copies the `sample/` paths the docs point at so C19 stays meaningful in the copy.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Inaccurate doc text] The RT-01 sentence in the plan says a no-break-space near-reference is "not a reference and reaches the executor as a literal string"**
- **Found during:** Task 2
- **Issue:** The regex is `[$](id)\.(\S+)` matched with `matchEntire`, and Java's default `\S` is ASCII-only, so a no-break space is NOT whitespace to it. I confirmed with jshell that `$a.b<NBSP>c` and `$a.b<NBSP>` both match, with the no-break space inside the key. That fails closed as `plan_binding_unresolved` before the step runs (Phase 15 security note and review IN-04 say the same). What really stays literal is a string the pattern does not match, such as one with a trailing line feed or a space inside.
- **Fix:** API.md says the whitespace is ASCII-only, that a Unicode space becomes part of the key and stops the plan with `plan_binding_unresolved` (loud, never a wrong write), and that a non-matching near-reference is delivered literally (the safe direction). The word ASCII is present, so C31 holds. The plan's truth text and the CONTEXT RT-01 line should be read as satisfied by this more precise wording.
- **Files modified:** API.md
- **Committed in:** `a3b6d65`

**2. [Rule 3 - Blocking] The new function pass required the bare backticked name, which API.md did not carry for `commandPipeline` and `compositeSink`**
- **Fix:** reworded the one sentence that introduces the two public functions to name each in backticks; the check was not loosened.
- **Committed in:** `a3b6d65`

**3. [Rule 1 - Test defect found by the selftest] First `adapter-block` plant did nothing**
- **Issue:** I planted by editing an `import` line, but the INTEGRATION adapter block holds no import lines (they are in a separate text block), so the copy stayed green and the selftest said so.
- **Fix:** the plant changes an identifier (`segment`) inside the block. This is the selftest doing its job.
- **Committed in:** `c1d0fc0`

---

**Total deviations:** 3 (1 doc accuracy, 1 blocking doc gap, 1 selftest plant). **Impact on plan:** none on scope.

## Issues Encountered

- None blocking. The selftest takes about 11 s (copies are small; sys time dominated by `cp -r scripts`).

## Findings for later plans

- The plan's `<verification>` and Task 1 mention a `checks=32` count and 6+ plants; both met (32, 10).
- INTEGRATION.md's plan subsection points at API.md for "whitespace"; the API.md sentence now makes that reference true.
- Docs are final for the wiring SHA: any later change to README, API.md, ECOSYSTEM.md or INTEGRATION.md voids the wiring pass (19-11..19-13).
- DOC-02 stays pending (needs the wiring test on the final SHA).

## Next Phase Readiness

Ready for 19-11. The doc set is frozen from here until the wiring test; the coverage gate (`scripts/verify-docs-coverage.sh`, and `--selftest`) is the mechanical half of DOC-02.

## Self-Check: PASSED

Modified files exist, the three task commits are in history (`452671a`, `a3b6d65`, `c1d0fc0`), and the plan-level verification commands were re-run green (see Verification).

---
*Phase: 19-sample-gate-1-docs*
*Completed: 2026-10-07*
