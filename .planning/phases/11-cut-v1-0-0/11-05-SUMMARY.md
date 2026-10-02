---
phase: 11-cut-v1-0-0
plan: 05
subsystem: release-tooling
tags: [release, negative-controls, sandbox-selftest, tag-gate, ledger-allowance, waiver, d-01, d-02]
requires:
  - phase: 11-04
    provides: "scripts/release-cut.sh with 15 preflight gates, the diagnostic prefreeze gate, cut mode and selftest happy"
provides:
  - "scripts/release-cut.sh: selftest negative (33 controls) and selftest all (happy, then negative) with a right-reason matcher"
  - "evidence/release-cut-selftest.txt: the full selftest all output"
affects: [11-06, 11-07, 11-08]
tech-stack:
  added: []
  patterns:
    - "one throwaway clone (and its own local bare remote) per control, cloned from a pristine green bare repository"
    - "expect-red / expect-green matcher: exit code, the 'RELEASE GATE FAIL <gate>:' line and a reason marker all have to match"
    - "every planted write goes through an under-the-temp-root assertion; every push or cut first asserts the remote is a local path"
key-files:
  created:
    - .planning/phases/11-cut-v1-0-0/evidence/release-cut-selftest.txt
  modified:
    - scripts/release-cut.sh
key-decisions:
  - "SELFTEST_ONLY=\"label ...\" is a development aid for the selftest only; a filtered run ends in RELEASE SELFTEST PARTIAL and can never print the OK line"
  - "`selftest all` builds the green sandbox once and clones a pristine copy of its bare repository before the happy cut, so the controls never see the cut's tag"
  - "gate version runs its build-file half before the dry run (fix, see Deviations)"
status: complete
commits: 3
plan_head_before: f7ce8970ba9e81940fcb74c55bfb252789a730b5
actuals:
  tokens: 10800
  tasks: 2
  commits: 3
requirements-completed: [VER-05]
---

# Phase 11 Plan 05: Release-gate negative controls Summary

Every gate of `scripts/release-cut.sh` is now shown non-vacuous: a planted violation in a throwaway sandbox clone turns the named gate red for the right reason (gate line plus reason marker), the two allowances (ledger-only contract rows, pre-freeze-only answers) are proven narrow, `cut` refuses without leaving a tag, and the real repository is untouched. `selftest all` ended `RELEASE SELFTEST OK happy=1 negatives=31 positives=2` in about 9.6 minutes. `commits: 3` are the task commits `cb4758d`, `45e9d2a` and `51fa895`; this SUMMARY commit follows them.

## Controls (label, plant, gate, matched marker)

W is the sandbox overlay commit. Output line shape: `ok    [label] went red (gate: matched 'marker')` or `... stayed green ...`.

| # | Label | Plant | Gate | Marker |
|---|-------|-------|------|--------|
| 1 | tag-format-args | tag arguments v1.0.1, 1.0.0, v1.0 (3 sub-runs) | tag-format | `is not the release tag` |
| 2 | tag-local-lightweight | lightweight v1.0.0 in the clone | tags-absent | `local tag(s) already exist` |
| 3 | tag-remote-only | tag only on the control's bare remote | tags-absent | `origin already has tag ref(s)` |
| 4 | tag-unrelated-v0.9.0 | unrelated v0.9.0 tag | tags-absent | `v0.9.0` |
| 5 | create-tag-not-false | working-tree config.json `create_tag` true, then key absent (2 sub-runs) | create-tag | `must be false` |
| 6 | clean-tracked-modified | tracked file modified | clean | `working tree is not clean` |
| 7 | clean-staged | staged change | clean | `files are staged` |
| 8 | pushed-ahead | one unpushed local commit | pushed | `is not origin/main` |
| 9 | wiring-status-fail | record status fail | wiring | `must be pass` |
| 10 | wiring-sha-mismatch | record tested_sha not W | wiring | `tested_sha` |
| 11 | wiring-not-ancestor | wiring SHA on a side branch | wiring | `is not an ancestor of HEAD` |
| 12 | diff-readme | README.md edited, committed, pushed after W | diff | `README.md` |
| 13 | diff-core-main | core/src/main file changed after W | diff | `core/src/main` |
| 14 | diff-jitpack-yml | jitpack.yml changed after W | diff | `jitpack.yml` |
| 15 | contract-non-ledger | one section 10 line edited | diff | `yahir-gsd-control-plane-f2` |
| 16 | contract-mixed | ledger row appended, then a section 10 line edited (2 commits) | diff | `yahir-gsd-control-plane-f2` |
| 17 | contract-row-outside-11 | dated row-shaped line right after the `## 10.` heading | diff | `yahir-gsd-control-plane-f2` |
| 18 | contract-ledger-only (POSITIVE) | appended synthetic row, then an existing row's last cell dash to SANDBOX (2 commits) | diff, green | `DIFF NOTE: ledger-only contract change` |
| 19 | waiver-not-accepted | packet pending, then partial (2 sub-runs) | waiver | `must be accepted` |
| 20 | waiver-needs-fix | accepted with a non-C row needs-fix; accepted with a C row carry-to-gate-2 | waiver | `needs-fix`; `carry-to-gate-2` |
| 21 | waiver-id-missing | answer id W07 removed | waiver | `differ from the table ids` |
| 22 | prefreeze-c-row-open | C row pending / needs-fix / carry-to-gate-2 (3 sub-runs) | prefreeze | `pending`; `W is void`; `carry-to-gate-2` |
| 23 | prefreeze-c-rows-answered (POSITIVE) | partial, C rows ok, other rows pending | prefreeze, green | `GATE OK prefreeze` |
| 24 | cut-dirty-tree | cut with a modified tracked file | clean | `working tree is not clean`; no tag in clone or bare |
| 25 | cut-approved-not-head | cut with approvedCommit = W, not HEAD | cut | `is not HEAD`; no GATE OK line; no tag in clone or bare |
| 26 | hygiene-api-txt-removed | keystore/api.txt removed in a commit | hygiene | `keystore/api.txt` |
| 27 | api-dump-core-line-deleted | one signature line deleted from core/api.txt | api-dump | `core: a fresh apiDump of HEAD differs` |
| 28 | leak-key-shape | key-shaped string (fragment-assembled) in a tracked file | leak | `provider-key shape` |
| 29 | leak-fixture-filename | tracked file whose name contains the fixture name (fragment-assembled) | leak | `a tracked path contains the fixture name` |
| 30 | leak-fixture-mention | fixture name mentioned in a tracked file outside the allow-list | leak | `names the fixture outside the allow-list` |
| 31 | dry-run-sample-install | jitpack.yml install line naming :sample | dry-run | `names :sample` |
| 32 | version-not-read | root build reads `VERSION_SANDBOX_UNSET` instead of VERSION | version | `0.0.0-local` |
| 33 | check-print-call | a print call planted in core main source | check | `Banned constructs` |

That is 31 red controls and 2 positive controls (18 and 23). The matched wiring tests, the ledger allowance both ways (18 green; 15, 16, 17 red with the relay diagnostic) and the pre-freeze rule both ways (22 red, 23 green) all behaved as the plan requires on the first complete run.

## Result

- Final line: `RELEASE SELFTEST OK happy=1 negatives=31 positives=2`; no `FAIL` line.
- The happy half of `selftest all` printed the 15-gate PREFLIGHT OK, CUT OK and the single annotated tag in the sandbox bare remote (all in the evidence file).
- Real-repository guard: `real-repo guard: unchanged (tags, remote tags, status, config.json, contract)`.
- After the plan, `git tag --list` and `git ls-remote --tags origin` in the real repository are both empty; no diff against `plan_head_before` in core, providers, keystore, sample, the four docs, jitpack.yml, `*.gradle.kts`, the contract or `.planning/config.json`.
- `./gradlew check --offline` is BUILD SUCCESSFUL in the worktree.
- Task 1 and Task 2 verify commands both exited 0 (each includes a full re-run of `selftest negative`).

## Deviations from Plan

1. **[Rule 1 - Bug] gate version failed at the dry-run gate for the version-not-read plant (wrong gate).** Control 32 as planned (root build no longer reading VERSION) cannot reach the version gate: `gate version` ran the dry run first, and a build that ignores VERSION publishes `0.0.0-local`, so the dry run fails on missing `-v1.0.0` file names and the verdict would be `dry-run`, with a reason that never names the version actually published. Fix in the gate, not the control: the build-file half of the version gate is now `version_static_checks`, run before the dry run in `gate version` (and still inside `gate_version`), and its reason names the version found: `the root build.gradle.kts does not read the VERSION environment variable, so the published coordinates would carry '0.0.0-local' (the local engineVersion default) instead of v1.0.0`. No check was removed; the preflight order is unchanged (dry-run still runs before version there). The first complete run after the fix was green for this control.
2. The harness blocks shell text that names git inside heredocs or python, so a few helper launch scripts live in the scratchpad (not in the repository). No repository effect.

No other gate or control needed a fix: every other control went red (or green) for the right reason on its first run.

## Notes for the next plans

- 11-06: any later edit to `scripts/release-cut.sh` is a non-.planning change after the wiring SHA and forces another wiring rerun, so this plan is the last one allowed to touch it before the wiring SHA.
- 11-07: `gate prefreeze` is proven both ways (open C row red with the loop diagnostic, answered C rows green on a partial packet).
- 11-08: the gate argument table is unchanged; `gate version <tag>` now fails fast on its build-file half before the dry run.
- A debugging aid exists: `SELFTEST_ONLY="label label" scripts/release-cut.sh selftest negative` runs chosen controls and ends in `RELEASE SELFTEST PARTIAL`, never the OK line.

## Self-Check: PASSED

- `scripts/release-cut.sh` and `.planning/phases/11-cut-v1-0-0/evidence/release-cut-selftest.txt` exist.
- Task commits `cb4758d`, `45e9d2a`, `51fa895` exist; measured count `commits: 3` from `plan_head_before`.
- Evidence holds 31 `went red` lines, 2 `stayed green` lines, the 15-gate PREFLIGHT OK line, the guard line, and no `FAIL` line.
