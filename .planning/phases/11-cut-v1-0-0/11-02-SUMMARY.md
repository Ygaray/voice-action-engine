---
phase: 11-cut-v1-0-0
plan: 02
subsystem: public-api
tags: [api-freeze, keystore, toolspec, metalava, review]
requires:
  - phase: 11-01
    provides: "CAUSE MAPPING: MATCH, suppressions audit, waiver packet (W05 = 4b value-class row)"
provides:
  - "public KeystoreCauseCodes (five frozen cause codes with UX KDoc)"
  - "ToolSpec frozen as one public constructor plus the default-argument stub"
  - "scripts/api-dump-isolated.sh (reused by 11-03, 11-04, 11-06)"
  - "reviewed interim signatures for core, providers, keystore and 11-API-REVIEW.md (INTENDED)"
affects: [11-03, 11-04, 11-06, 11-07]
tech-stack:
  added: []
  patterns: ["getter-only, no-backing-field public values (no const, no JvmField, no MayBeConst)"]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauseCodes.kt
    - scripts/api-dump-isolated.sh
    - .planning/phases/11-cut-v1-0-0/11-API-REVIEW.md
    - .planning/phases/11-cut-v1-0-0/evidence/interim-api/core.api.sig
    - .planning/phases/11-cut-v1-0-0/evidence/interim-api/providers.api.sig
    - .planning/phases/11-cut-v1-0-0/evidence/interim-api/keystore.api.sig
  modified:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt
    - API.md
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
key-decisions:
  - "KeystoreCauseCodes: getter-only String properties with no initializer, so no backing field and no static field; detekt clean with no @Suppress and no detekt.yml change"
  - "ToolSpec: keep Kotlin defaults, drop @JvmOverloads; frozen JVM surface is one constructor plus the default stub"
  - "Value-class kinds kept (4b, W05); decided by agents under A12"
status: complete
commits: 3
plan_head_before: ded96478dfb5e76730c8db8ffeb06f41379ad76b
actuals:
  tokens: 30000
  tasks: 3
  commits: 3
duration: ~35min
completed: 2026-10-01
---

# Phase 11 Plan 02: Final public API and interim surface review Summary

**Cause codes are now public with their UX documented, ToolSpec freezes as a single constructor, and the whole three-module surface is dumped in isolation and reviewed in writing (INTERIM API REVIEW: INTENDED).**

`commits: 3` is measured with `git rev-list --count ded9647..HEAD` before this SUMMARY commit; the SUMMARY commit makes four on the branch.

## Tasks

| Task | Commit | Result |
|---|---|---|
| 1 (tracer) KeystoreCauseCodes | a9a4c34 | RED seen (unresolved reference), then GREEN; detekt zero; docs coverage OK |
| 2 ToolSpec one constructor | 11fdb0f | RED seen (1 failed of 10), then GREEN; isolated dump shows 1 `ctor public ToolSpec(` |
| 3 dump script, check, review | 0977144 | `./gradlew check --offline` BUILD SUCCESSFUL; review ends INTENDED |

## Decisions

### KeystoreCauseCodes shape (Task 1)
The CAUSE MAPPING line in 11-01's preconditions audit was MATCH, so the KDoc was written. The object has five getter-only
properties written as `public val KEY_MISSING: String get() = "key_missing"`, one line each, no initializer. Reasons: a
`const val` or `@JvmField` in an object compiles to a public static field (rejected by `KeystoreApiShapeTest` and
`review-api-surface.sh`); a plain `val` with a literal initializer trips detekt `style>MayBeConst`; a getter with no
initializer has no backing field, so the compiled object has only `INSTANCE` plus five getters. `KeystoreCauses`
(internal) now spells no code string itself. Tests pin: the five values, vocabulary equals the public set and the five are
distinct, the four `KeyState.Unreadable` values and the missing-key lookup carry the matching public values, exactly five
public String getters, and `INSTANCE` is the only declared field. `API.md` names the object (packages line, surface
table row, credentials section) with the literal code strings kept.

### ToolSpec final shape (Task 2)
Grounds re-confirmed before editing: `git ls-files '*.java'` is empty; `getConstructor` is used for ToolSpec only in
`ApiShapeTest` (the other hits in `TranscriptTypesTest` are `ModelRequest`). The Phase 9 dump had four `ctor public
ToolSpec(` lines; the new isolated dump has one. `@JvmOverloads` is removed; parameter list, defaults, `init`,
`toString` and companion are unchanged. The KDoc keeps the growth rule and gains the sentence that Java callers pass all
six arguments and no shorter overloads are generated. `toolSpecKeepsItsFiveArgumentConstructorAndAddsTheOtherShapes`
was replaced by `toolSpecDeclaresExactlyOnePublicConstructor` (one public non-synthetic constructor with the six
parameter types, the 3-, 4- and 5-argument lookups throw `NoSuchMethodException`, the default-argument stub exists).
STUB_EXCEPTIONS is unchanged because the stub remains.

## Result lines

- `./gradlew check --offline` -> BUILD SUCCESSFUL (all modules, matrix legs, sample, detekt zero)
- `scripts/api-dump-isolated.sh --out …/evidence/interim-api` -> `API DUMP ISOLATED OK … core=1698 providers=121 keystore=67`
- `scripts/review-api-surface.sh --expect-sealed-complete` -> `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=181`
- `scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=23 types=97`
- `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`; no `api.txt` in the real tree; no tags

## Deltas (all explained, none flagged)

- core vs Phase 9: only the three dropped `ToolSpec` ctor lines (Task 2).
- keystore vs Phase 6: only the new `KeystoreCauseCodes` (five getters, five properties, `INSTANCE`, no other field).
- providers: first full review; 14 declarations (8 top-level, 6 nested); no enum, no `copy`/`componentN`, no sealed type,
  only `Companion` static fields. Intended as is.

## Deviations from Plan

None - plan executed as written. Two small mechanical points: a `MaxLineLength` detekt finding in the new test was fixed
by splitting a line (caught by `:core:detekt` before the Task 2 commit), and the now-unused private helper `isTypeOf` was
removed from `ApiShapeTest`.

## Notes for downstream plans

- `api-dump-isolated.sh` takes `--out <dir>` and optional `--head` (dumps `git archive HEAD`, for the 11-04 release gate
  and 11-06 pre-push API identity). It does not pass `--offline` to its inner Gradle, matching `review-api-surface.sh`.
- If W05 (4b) is answered needs-fix, this plan's scope reopens before 11-07's baseline.
- Environment note: the worktree tooling refused some compound shell forms mentioning git; commands were split. No
  functional impact.

## Self-Check: PASSED

Files found: KeystoreCauseCodes.kt, scripts/api-dump-isolated.sh (mode 100755), 11-API-REVIEW.md and the three
`.api.sig` files. Commits a9a4c34, 11fdb0f and 0977144 exist on `worktree-agent-af8baaeed2d81590b`.
