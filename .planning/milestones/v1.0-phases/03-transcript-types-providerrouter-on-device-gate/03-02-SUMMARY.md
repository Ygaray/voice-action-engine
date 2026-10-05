---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 02
subsystem: core
status: complete
tags: [kotlin, capabilities, model-table, source-scan, cln-03, cln-04, on-device-gate]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "ProviderId, TierPolicy builder pattern, review-api-surface.sh and ApiShapeTest gates"
provides:
  - "io.github.ygaray.voiceactionengine.core.provider: ModelCapabilities (internal ctor, Builder, UNKNOWN, invoke), CachingMode (open value class), ModelCapabilityTable.lookup(provider, exactModelId)"
  - "NoHardCodedConstantsTest: mechanical CLN-03, CLN-04 and PROV-10 (SC3) source scan over core/src/main with positive controls"
affects: [03-05, 03-06, 03-07, 03-08, 03-09, Phase 4, Phase 5]

plan_head_before: aab474df5aaf1ec18dd8fa8dc6800269be7224e1

actuals:
  tokens: 5700   # chars/4 over the 5 files added (22,836 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Capability facts are data keyed by (ProviderId, exact model id); an app override is a Builder block applied to a Builder seeded from the provider default (patch, not replace)"
    - "Source-scan test: one private matcher per rule, shared by the real scan and its positive controls"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CachingMode.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilityTable.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
  modified: []

key-decisions:
  - "charsPerToken default is 4.0 (private const in ModelCapabilities.kt): larger divisor under-estimates tokens, so the cache diagnostic errs silent; the measured tool-schema JSON ratio is about 3.0, and 03-09 also caps the estimate by the response's own prompt total"
  - "ModelCapabilityTable takes the provider-defaults function and the override map through an internal constructor; 03-07 builds it from registered providers and the capabilities(...) DSL"
  - "On-device rule covers code and import lines only (comment and KDoc lines skipped) so docs can explain v1.0 ships no on-device code"

requirements-completed: [TEL-03, CLN-03, CLN-04, PROV-10]

coverage:
  - id: D1
    description: "An app override for one exact (provider, model id) wins over the provider default, which wins over the unknown-id default, through the public lookup; the override patches only the fields it sets"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt#appOverrideForTheExactPairPatchesTheProviderDefault"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt#overrideKeysAreExactIdsNeverPrefixes"
        status: pass
    human_judgment: false
  - id: D2
    description: "Capability defaults (tools true, caching NONE, minimum null, 4.0 chars per token), validation naming the field, the open CachingMode vocabulary, equality and toString"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt"
        status: pass
      - kind: command
        ref: "scripts/review-api-surface.sh (API SURFACE OK)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Library sources name no model id, hide no limit constant outside its owner, read no files/environment/properties/preferences, and carry no on-device implementation code"
    requirement: "CLN-03, CLN-04, PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 02: Model Capability Table and Source-Scan Guard Summary

**Per-model capability table keyed by (ProviderId, exact model id) with app overrides patched over provider defaults, plus a source-scan test that mechanically bans model ids, hidden limits, settings reads and on-device code in `:core`.**

## Performance

- **Duration:** about 10 min (start time was not captured at the top of the run; approximate)
- **Completed:** 2026-10-01
- **Tasks:** 3 (1 tracer, 2 TDD)
- **Files:** 5 created, 0 modified

## Accomplishments

- `ModelCapabilities` (internal constructor, `Builder`, `UNKNOWN`, `invoke`) with `supportsTools`, `caching`, `minCacheablePrefixTokens`, `charsPerToken`; non-finite or non-positive divisor and a minimum of 0 or below are rejected with `IllegalArgumentException` naming the field.
- `CachingMode` open value class with `EXPLICIT_BREAKPOINTS`, `AUTOMATIC`, `NONE`.
- `ModelCapabilityTable.lookup`: app override for the exact pair seeds a Builder from the provider default and applies only its set fields; a different id, a prefix or another provider never matches.
- `NoHardCodedConstantsTest` (12 tests: 6 real-scan rules, 6 positive controls) scans every `.kt` under `core/src/main/kotlin`.

## Task Commits

1. **Task 1 (tracer): capability table with override over provider default** - `050a1de` (feat)
2. **Task 2: defaults, validation, exact-id keying, caching vocabulary tests** - `c4017a4` (test)
3. **Task 3: NoHardCodedConstantsTest** - `1e4d15f` (test)

## Scan Details (for 03-08 and later phases)

- **Scanned file count:** 52 `.kt` files at this commit (test asserts at least 40 and that `TierPolicy.kt` is among them).
- **Final on-device token rules** (applied to import and code lines; trimmed lines starting with `*`, `/*` or `//` are skipped):
  - `(?i)aicore`
  - `(?i)mlkit`
  - `(?i)(?<![A-Za-z0-9])nano(?![A-Za-z0-9])` (standalone word in any case; this also catches `GEMINI_NANO` because `_` is not alphanumeric, a deliberate superset of the plan's "standalone word")
  - `Nano(?![a-z])` (camel-case segment: uppercase N, `ano`, then a non-lowercase character or end of line)
  - The PipelineBuilder.kt clock line (`System.nanoTime() / NANOS_PER_MILLI`) is read from the real file in the control and asserted not flagged; no file is excluded.
- **Model-id rule:** case-insensitive family prefixes (claude, sonnet, opus, haiku, gpt, gemini, gemma, llama, mistral, deepseek, qwen, grok, not followed by a letter), lowercase o-series ids (`o` plus 1-3 digits), and lowercase vendor-slash ids (`anthropic/x`, `openai/x`, `meta-llama/x`, and others). Company and `ProviderId` wire values on their own do not match.
- **Limit-constant rule:** `const val` named `DEFAULT_*`, `MIN_*`, `MAX_*` or containing `TOKEN`, `ITERATION`, `CEILING`, allowed only in `TierPolicy.kt`, `ModelCapabilities.kt`, `AwaitingConfirmGate.kt`, `ToolSpec.kt`. TierPolicy literals `60_000`/`60000`/`4_096`/`4096` allowed only in `TierPolicy.kt`.
- **Settings rule:** `java.io.File*`, `java.nio.file`, file streams/readers/writers, `System.getenv`, `System.getProperty`, `java.util.prefs`, `java.util.Properties`, `SharedPreferences`, `DataStore`, and any `android.` import or qualified use. Applied to all lines, comments included.
- **Pattern adjustments for false positives:** none. The first run was green with no existing main line flagged. To confirm the real scan is non-vacuous, I temporarily appended a violating comment and a `MAX_FOO` constant to ProviderId.kt: four of the six rule tests failed with `file:line` output (the on-device rule correctly ignored the comment line); the change was reverted before commit.
- **Divisor default:** 4.0, held as `private const val DEFAULT_CHARS_PER_TOKEN` in ModelCapabilities.kt, per-model overridable. Rationale in the KDoc: tool-schema JSON measures about 3 characters per token, so 4.0 under-estimates tokens and keeps the cache diagnostic silent when unsure.

## Deviations from Plan

**1. [Process] Task 2 tests-first not committed as a separate RED commit**
- The Task 1 implementation already satisfied every Task 2 behavior bullet (defaults, validation, exact keys, equality), so the Task 2 tests passed on first run. They were committed as one `test(03-02)` commit rather than a RED/GREEN pair. No production change was needed in Task 2.

**2. [Rule 3 - Blocking] Detekt line-length fixes**
- Found during: Tasks 1 and 3. One KDoc line in ModelCapabilities.kt and five lines in the new test exceeded 120 columns; shortened/wrapped with no behavior change. No `@Suppress`, no baseline, no config change.

**Total deviations:** 2 (1 process note, 1 auto-fixed lint). **Impact:** none on scope or API.

## Issues Encountered

- `main` is classified as protected by `git.base-branch --is-protected`, but this repo uses `branching_strategy: none`, every prior plan committed on `main`, and the orchestrator ran this plan explicitly in sequential mode on `main`. HEAD never drifted; commits went on `main` as in 03-01.

## Verification

- `./gradlew :core:test --tests '*ModelCapabilityTableTest' --tests '*ApiShapeTest'`: pass
- `./gradlew :core:detekt :core:scanBannedConstructs`: pass
- `./gradlew :core:test --tests '*NoHardCodedConstantsTest'`: pass (12 tests)
- `./gradlew :core:check`: pass
- `./gradlew check` (whole repo): pass
- `scripts/review-api-surface.sh`: `API SURFACE OK` (sealed set unchanged, 140 classes)
- Acceptance greps (Builder internal constructor, `DEFAULT_CHARS_PER_TOKEN = 4.0`, `CachingMode internal constructor`, `require(` count of 2): all pass
- No `api.txt` under core/, providers/ or keystore/.

## Next Plan Readiness

Ready for 03-03. 03-05 can default `AiProvider.capabilities(model)` to `ModelCapabilities.UNKNOWN`; 03-07 builds the `ModelCapabilityTable` from provider defaults and the `capabilities(...)` DSL; 03-09 reads `caching`, `minCacheablePrefixTokens` and `charsPerToken`. Later plans that add a `DEFAULT_*`/`MAX_*`/`MIN_*` or token/iteration/ceiling constant outside the four owner files will fail `NoHardCodedConstantsTest` and must declare it in an owner or grow the owner list deliberately.

## Self-Check: PASSED

- Files: all five created files exist on disk.
- Commits: `050a1de`, `c4017a4`, `1e4d15f` present in `git log`.
