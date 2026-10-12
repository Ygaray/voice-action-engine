---
phase: "14"
slug: localgrammar-bilingual-grammarpack
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-06"
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: `14-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; hand-written fakes in `core/src/testFixtures` |
| **Config file** | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts`; `scripts/verify-docs-coverage.sh` |
| **Quick run command** | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q :core:test --tests '<Class>'` |
| **Full suite command** | same GRADLE_OPTS, `./gradlew check --offline`, then `scripts/verify-docs-coverage.sh` and `scripts/review-api-surface.sh` |
| **Estimated runtime** | quick ~60-120 s; full ~10+ min (host-memory constrained: one Gradle run at a time) |

---

## Sampling Rate

- **After every task commit:** the single touched test class (quick command)
- **After every plan wave:** `:core:test :core:detekt :core:scanBannedConstructs` in ONE Gradle invocation, plus `:core:metalavaCheckCompatibility` when public API changed
- **Before `/gsd-verify-work`:** full `check` green, docs-coverage and review-api-surface scripts OK
- **Max feedback latency:** ~180 seconds per task

---

## Per-Task Verification Map

Task IDs are bound by the planner; the requirement-to-test map below is authoritative until then.

| Requirement | Behavior | Test Type | Automated Command (after GRADLE_OPTS) | File Exists |
|-------------|----------|-----------|----------------------------------------|-------------|
| GRAM-01 | Matching transcript: zero provider calls, gate->commit->sink, `providerCallId == null` | pipeline | `:core:test --tests '*LocalGrammarPipelineTest*'` | ✅ green |
| GRAM-01 | Held grammar action reported held, never success | pipeline | `:core:test --tests '*LocalGrammarHeldTest*'` | ✅ green |
| GRAM-01 | D-01 StepSubmission move behavior-neutral | unit | `:core:test --tests '*SingleShotResolveTest*' --tests '*SingleShotOutcomeMappingTest*' --tests '*SingleShotPlumbingTest*' --tests '*CommitPathTest*' --tests '*HeldReportingTest*'` | ✅ green |
| GRAM-02 | EN/ES same tool call + same typed slots with spoken numbers | unit | `:core:test --tests '*GrammarBilingualTest*'` | ✅ green |
| GRAM-02 | Template syntax accept/reject; build-time validation | unit | `:core:test --tests '*GrammarTemplateTest*' --tests '*GrammarPackValidationTest*'` | ✅ green |
| GRAM-02 | Number tables, round-trip 0..999,999 EN+ES, digit grouping | unit | `:core:test --tests '*NumberRoundTripTest*' --tests '*NumberGoldenEnTest*' --tests '*NumberGoldenEsTest*' --tests '*DigitGroupingTest*'` | ✅ green |
| GRAM-02 | Text folding rules | unit | `:core:test --tests '*GrammarTextTest*'` | ✅ green |
| GRAM-03 | Near-miss corpus NoMatch; ambiguity NoMatch; label matrix | unit | `:core:test --tests '*GrammarNearMissCorpusTest*' --tests '*GrammarAmbiguityTest*' --tests '*GrammarLanguageLabelTest*' --tests '*GrammarNeverGuessesTest*'` | ✅ green |
| GRAM-03 | Resolver verdicts pass through; NoMatch clears carry | pipeline | `:core:test --tests '*LocalGrammarVerdictsTest*'` | ✅ green |
| GRAM-04 | normalize hook semantics; CLN-02 scan green | unit/gate | `:core:test --tests '*GrammarNormalizeTest*'`; `:core:scanBannedConstructs :core:detekt` | ✅ green |
| GRAM-05 | NO_PROVIDER capability; offline-only; terminal intent offline | pipeline | `:core:test --tests '*LocalGrammarPolicyTest*'` | ✅ green |
| D-05 | `Extraction.matchedLanguage`, API shape | unit | `:core:test --tests '*ApiShapeTest*'` | ✅ green |
| security | Redaction canary, toString non-leak | unit | `:core:test --tests '*RedactionCanaryTest*' --tests '*GrammarRedactionTest*'` | ✅ green |
| D-12 | STT fixture rows parse/match as recorded | unit | `:core:test --tests '*GrammarSttFixturesTest*'` | ✅ green (window consumed, fixtures captured) |
| surface | API.md rows, no sealed/enum/data leak, Metalava compat | gate | `scripts/verify-docs-coverage.sh --only C20,C21`; `scripts/review-api-surface.sh`; `:core:metalavaCheckCompatibility` | ✅ green |

*All rows ✅ green as of 2026-10-06.*

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `core/src/test/.../Grammar*Test.kt`, `Number*Test.kt`, `LocalGrammar*Test.kt` covering GRAM-01..05
- [x] Test-only independent EN/ES speller and a shared neutral example pack in `src/test` (no domain words)
- [x] `TraceTest` rows for new trace codes; `ApiShapeTest` assertion for the internal 4-arg `Extraction`
- [x] `14-WINDOW-GRANT.md` (grant `pending`) and the D-12 prompt list; `GrammarSttFixturesTest` blocked until capture

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real STT number/accent forms captured on the TESTER | D-12 / GRAM-02 | Needs a separately granted TESTER window; never autonomous | Per `14-RESEARCH.md` § D-12: file-feed stimuli via the stt-engine demo harness once `14-WINDOW-GRANT.md` is granted |

---

## Validation Sign-Off

> Plan-time state is a DRAFT. `status: validated`, `nyquist_compliant: true` are finalized only post-execution by the Nyquist finalizer.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency within budget
- [x] `nyquist_compliant: true` set in frontmatter (post-execution only)

**Approval:** validated 2026-10-06. Evidence: latest full run `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` green (907 tests, 0 failures); every test class in the map confirmed present on disk. GRAM-01..05 each map to at least one passing automated class. Zero automatable gaps. Manual-only item (real-speech recognition quality) is deferred to Phase 19 / milestone Gate-2.
