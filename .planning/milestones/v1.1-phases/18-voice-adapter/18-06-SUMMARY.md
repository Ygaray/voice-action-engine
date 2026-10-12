---
phase: 18-voice-adapter
plan: 06
subsystem: build-gates
tags: [gradle, publication-gate, classpath-gate, negative-controls, stt-confinement]
requires:
  - phase: 18-voice-adapter
    provides: "plan 01 module scaffold (compileOnly stt dependency, publication block); plans 03 and 05 tests in place before the security gates"
provides:
  - ":voice-adapter:verifyAdapterSttCompileOnly (in check): generated POM and module.json carry :core and no :stt; compile classpath resolves :stt, runtime classpath never does"
  - "verifySttConfined (in check) on core, providers, keystore and undo: no resolved component and no requested selector of the :stt group"
  - "scripts/verify-stt-negative-controls.sh (7 controls) and Part 6 hook in scripts/verify-negative-controls.sh"
affects: [18-08]
actuals:
  tokens: 9500
  tasks: 3
  commits: 3
tech-stack:
  added: []
  patterns: ["publication gate reads the generated POM and module.json plus resolved classpaths, with non-vacuity assertions", "requested-selector check so an unresolvable AAR declaration on a JVM module is still caught"]
key-files:
  created:
    - scripts/verify-stt-negative-controls.sh
  modified:
    - voice-adapter/build.gradle.kts
    - gradle/invariants.gradle.kts
    - scripts/verify-negative-controls.sh
key-decisions:
  - "The :stt group literal is a full-string match and is held once in each file (an applied script cannot share a value with a build file); the bare owner name is never matched because the engine's own group shares it"
  - "No baseline, suppression, api.txt change, release-cut or sample edge change"
requirements-completed: [ADPT-01]
status: complete
duration: 12 min
completed: 2026-10-07
plan_head_before: 01f4cd222ed71c405533a6cdddd90ceb83ab2c3d
commits: 3
coverage:
  - id: D1
    description: "The adapter's published POM and module metadata list :core and no :stt; :stt is compile-visible and never runtime-visible"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: ":voice-adapter:verifyAdapterSttCompileOnly :voice-adapter:detekt -> exit 0"
        status: pass
      - kind: command
        ref: "scripts/verify-stt-negative-controls.sh plant 1 (compileOnly flipped) -> red with 'must keep :stt compileOnly'"
        status: pass
    human_judgment: false
  - id: D2
    description: "No other published module resolves or requests the :stt group, and the gate cannot pass on an empty classpath"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: ":core/:providers/:keystore/:undo:verifySttConfined :voice-adapter:verifyModuleGraph :core:verifyCoreDependencyAllowlist -> exit 0"
        status: pass
      - kind: command
        ref: "scripts/verify-stt-negative-controls.sh plants 2 and 3 (:keystore AAR path, :core requested-selector path) -> red with 'resolves the :stt group'"
        status: pass
    human_judgment: false
  - id: D3
    description: "Controls prove each gate red for the right reason and green on the clean tree, run as Part 6, plants restored byte-identically"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: "scripts/verify-stt-negative-controls.sh -> STT NEGATIVE CONTROLS OK plants=7; git diff clean on the four build files"
        status: pass
    human_judgment: false
---

# Phase 18 Plan 06: Stt Compile-Only And Confinement Gates Summary

Two permanent Gradle gates prove the speech engine reaches no consumer through `:voice-adapter`'s publication and no other published module, and seven negative controls prove they are not vacuous.

## Accomplishments

- `verifyAdapterSttCompileOnly` (tracer, in `voice-adapter/build.gradle.kts`, wired into `check`): depends on the POM and module-metadata generation tasks; requires `voice-action-engine-core` in the POM and in a `java-api` variant (non-vacuity); fails on any dependency whose artifact is `voice-engine-android` or starts with `voice-engine-android-`, or whose group is the `:stt` group, in the POM or any module.json variant; requires the compile classpath to resolve `voice-engine-android` and the runtime classpath to resolve something and nothing of the `:stt` group. Violations start with `:voice-adapter must keep :stt compileOnly: `, vacuity messages say `:stt gate is vacuous`.
- `verifySttConfined` (in `gradle/invariants.gradle.kts`, every published module except `voice-adapter`, wired into `check`): resolved components plus requested `ModuleComponentSelector`s of the `:stt` group, so a JVM module whose AAR declaration cannot resolve is still caught; vacuous when a classpath resolved nothing.
- `scripts/verify-stt-negative-controls.sh` (mode 755): three clean-tree controls and four plants (adapter `compileOnly` flipped to `implementation`, `:stt` on `:keystore`, `:stt` on `:core`, `project(":voice-adapter")` on `:providers`), each restored right after its check, trap restore and `cmp` against the backup at the end. Hooked into `scripts/verify-negative-controls.sh` as Part 6.

## Task Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 (tracer) | 5d37f84 | verifyAdapterSttCompileOnly on the real module |
| 2 | 0f2798c | verifySttConfined for every other published module |
| 3 | 24ea489 | `:stt` negative controls and Part 6 hook |

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- Task 1 gate (`verifyAdapterSttCompileOnly`, `:voice-adapter:detekt`): exit 0.
- Task 2 gate (four `verifySttConfined`, adapter gate, `verifyModuleGraph`, `verifyCoreDependencyAllowlist`): exit 0.
- Task 3 gate: `bash -n` on both scripts, then `STT NEGATIVE CONTROLS OK plants=7`; `git diff --exit-code` on the four build files and on every `api.txt`: clean.
- The tracer's verify was exercised again inside Task 2's gate and the Task 3 controls (clean-tree control 1); green.
- All acceptance criteria for the three tasks re-run and pass. The full `verify-negative-controls.sh`, `verify-api-dump.sh` and `jitpack-dry-run.sh` were deliberately not run (reserved for plan 18-08); `bash -n` on the full script passes.
- One Gradle run at a time with the single-use-daemon recipe; one bounded wait for memory before Task 2; no earlyoom kills.

## Threat Model

T-18-50 and T-18-51 mitigated by the two gates and their plants; T-18-52 by the non-vacuity assertions and clean-tree controls; T-18-53 by the trap restore, final `cmp` and a clean `git diff` on the touched build files.

## Next

Ready for 18-07.

## Self-Check: PASSED

`scripts/verify-stt-negative-controls.sh` exists and is executable; commits 5d37f84, 0f2798c and 24ea489 found in `git log`; acceptance criteria re-run green; no plant residue in `git status`.
