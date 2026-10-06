# Phase 14: LocalGrammar & Bilingual GrammarPack - Research

**Researched:** 2026-10-06
**Domain:** Offline, deterministic, bilingual (EN/ES) template-grammar tier for a domain-free Kotlin/JVM library (`:core`); anchored full-utterance matching; EN/ES number words; accent/punctuation folding; additive-only public API
**Confidence:** HIGH for the in-repo seams, gates and engine integration (read at HEAD `73b5a07`); HIGH for the matching/DSL design (it is a design, not a lookup); MEDIUM for ES morphology (RAE pages returned 403 to fetch, confirmed only through search summaries); LOW-to-MEDIUM for real STT output forms until the D-12 device capture lands

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [submission]:** Full move as commit 1 (`resolutionOutcome` + `submitAll` → `submitSteps(session, steps, providerCallId)` + `prepareGuarded`), so 14 ‖ 15 never edit the same functions and grammar inherits SingleShot's submit semantics exactly. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
- **D-02 [verdicts]:** Pass through unchanged, so SB's shared resolver means the same thing on every tier; SC-3's "rejected slot → NoMatch" holds for a `Resolution.NoMatch` rejection. _(source: ai-auto)_
- **D-03 [dsl]:** Intent-centric: slots declared once per intent (same tool + typed values across languages by construction); template mini-syntax literals/[optional]/(a|b)/{slot}/sub-rules, no regex; internal ctors + builders, no data class/defaulted ctor params; build-time validation throws IllegalArgumentException (duplicate/ambiguous rules, unknown slots, unbounded text). — **Reversibility:** one-way — GrammarPack DSL is public API frozen at the v1.1.0 tag _(source: ai-auto)_
- **D-04 [testability]:** Public pure `match(...)` (frozen surface, redacted toString). CommandSession's ctor is internal, so apps (SB D-04 zero-false-positive corpus) can't unit-test the tier otherwise. _(source: ai-auto)_
- **D-05 [language-out]:** Additive `Extraction.matchedLanguage: String?` (null for model tiers). SB SC-3 / D-06 need it; rewriting `input.language` silently changes its meaning for apps. _(source: ai-auto)_
- **D-06 [terminal]:** Add the per-intent `terminal` flag mirroring SingleShot's terminal tool path: SB G6/G7 search/browse navigation is in its grammar tranche, and adding it after v1.1.0 is a new frozen member anyway. Confirm with SB. _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #2): a zero-commit `terminal` intent → `TerminalCall`; ends the walk as handled carrying intent + slots; NOT NoMatch; MUST work under offline-only.
- **D-07 [slots]:** The four types with min..max bounds only; no date/time/unit types and no SlotParser seam in v1.1 (each is one more frozen fun interface). Example packs live in src/test or :sample (CLN-02 deny-list). _(source: ai-auto)_
- **D-08 [numbers]:** Full golden table with the round-trip test; ambiguous digit grouping → NoMatch. _(source: ai-auto)_
- **D-09 [normalize]:** null = reject (PITFALLS; CT D-02 "a miss returns NoMatch"). Text/list slots only; runs after match, before Extraction; raw = original surface text; language = matched pack (never null); throw → NoMatch + trace code (guardedPlain), not strategy_error. _(source: ai-auto)_
  - **Consumer condition (binding):** CT condition (answer #7): normalize returns canonical English for in-map EN/ES tokens; null → NoMatch → cloud.
- **D-10 [label]:** Labeled pack only by default + opt-in `tryOtherLanguage` flag; null label always tries both and requires agreement; labels other than exactly "en"/"es" → NoMatch + trace code. SB can flip the flag for D-04. _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #1): a null label MUST try both packs by default; SB will set the try-both flag.
- **D-11 [text-norm]:** Fold vowel accents (keep ñ distinct), NFC + Locale.ROOT lowercase, strip punctuation except interior decimal sep, collapse whitespace, split hyphens, token cap → NoMatch; fillers declared per pack/language. Accent-only collisions fail safe as ambiguity → NoMatch. _(source: ai-auto)_
- **D-12 [stt-forms]:** Capture a small real-transcript fixture set on the TESTER as Phase 14's first device step (no device overlap with 12/13/19); verify ES morphology edge cases against RAE before freezing. _(source: ai-auto)_

**Runtime Decisions (authoritative, appended to CONTEXT.md):**

- **D-01 refreshed [submission] (2026-10-06, ai-auto, dependency Phase 12 complete):** Full move as commit 1, refreshed against P12 HEAD 6fa22cc. Move resolutionOutcome (singleshot/SingleShotOutcomes.kt:59) and SingleShotStrategy.submitAll (SingleShotStrategy.kt:154) into strategy/StepSubmission.kt as submitSteps(session, steps, providerCallId: String?). The parameter is NULLABLE: SingleShot passes call.id, grammar passes null, matching P12 CommitCoordinator.submit(step, providerCallId: String?). No function named prepareGuarded exists at HEAD, so the planner moves whatever guarded-prepare logic submitAll calls along with it, under its real name. 14 and 15 never edit the same functions, and grammar inherits SingleShot submit semantics exactly.

### Claude's Discretion

Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GRAM-01 | `LocalGrammarStrategy` resolves a matching transcript to the app's tool call with zero provider calls, then submits it through the session (gate → commit → sink like every tier). | "Tier flow" (execute skeleton), `StepSubmission.kt` move (D-01), zero-call tests with `NoNetworkGuard` + `FakeAiProvider` call counts, `providerCallId == null` |
| GRAM-02 | A bilingual `GrammarPack` DSL lets an app declare EN and ES rules with typed slots, number words in both languages, and per-language phrasing for one intent. | "DSL spec" (EBNF + builder surface), "Number-word golden tables", "Text normalization", intent-centric slot declaration |
| GRAM-03 | A transcript no rule matches, or a slot the app resolver rejects, ends `NoMatch`, so the ladder hands over to the next tier with carry cleared. A grammar tier never guesses. | "Matching algorithm" (whole-utterance, distinct-result ambiguity = NoMatch), label table, near-miss corpus, `TierWalk.startFresh` (carry cleared) |
| GRAM-04 | An optional per-slot `normalize: (raw, language) -> String?` hook lets the app plug in a synonym map without the engine naming any domain. | "normalize semantics" (text + choice slots, null = reject, throw = NoMatch + code, agreement after normalize), CLN-02 constraints |
| GRAM-05 | The grammar tier declares `NO_PROVIDER` capabilities, so it runs offline-only and under any provider policy. | `StrategyCapabilities.NO_PROVIDER`, `PolicyPreCheck.permits`, `Unhandled(cappedByPolicy)` truth table, TierPolicy tests as the pattern |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Directives extracted from `./.claude/CLAUDE.md` (and the global `~/.claude/CLAUDE.md` where it bears on this phase). Treat as locked.

- **Dependency structure:** `:core` depends on no other hub and has no HTTP dependency (L7/A7); enforced by `verifyCoreDependencyAllowlist` (classpath). Phase 14 adds **no dependency** (JDK `java.text.Normalizer` + Kotlin stdlib + kotlinx-serialization-json already on the allowlist).
- **API evolution:** public API grows strictly additively once tagged; contract changes only via §10 amendments through the control plane. Never commit §11 (orchestrator-owned; memory note `vae-bilingual-multi-repo-effort`).
- **Domain-free:** the library names no note/card/food; all app knowledge enters through §5.2 seams. The CLN-02 scan covers `main` and `testFixtures` only; `src/test` and `:sample` may hold example packs.
- **Quality:** detekt zero baseline on library modules (plain `detekt` only, syntax-only; no `detektMain`, no baseline XML); explicit API strict; Kotlin 2.3.20, JVM 11 bytecode, `-Xjdk-release=11`.
- **Secrets:** transcripts, tool args and slot values never reach logs, telemetry, exceptions or `toString()`. A transcript IS user speech: treat it like a secret.
- **Forbidden constructs** (scanner + detekt): `runCatching`, `println`/`print`, `System.out/err`, `printStackTrace`, `android.util.Log`, DI annotations, `okhttp3.internal.*`; no `TODO:`/`FIXME:`; no planning ids (`T-n-n`, `WR-n`, `Phase N D-n`) in comments.
- **Process:** all file changes go through a GSD command; host-memory rule: at most 1-2 Gradle-running plans concurrent, with the recipe `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`, real exit status (never behind `| tail`), never `./gradlew --stop` while another project's daemon is live. [CITED: `~/.claude/projects/-home-yahir-Projects-Reusable-android-voice-action-engine/memory/vae-release-cut-host-oom.md`]
- **Devices:** TESTER (`…-s22-ultra-2`, USB serial `R5CT10XNKQN`) only, never the personal phone; always `adb -s`; no agent device work without a relayed window grant (Phase 13 precedent, `13-WINDOW-GRANT.md`); one device tester at a time. [CITED: `~/.claude/context/devices/common.md`, `test-android.md`]
- **Verification policy:** two-gate UAT; for library phases with no user-visible surface, completion hinges on JVM evidence; Phase 14 adds no `:sample` change (grammar Gate-1 is Phase 19). Do not emit a hand-written self-UAT task or a per-phase blocking human-verify checkpoint except the D-12 device step, which is a window-grant checkpoint, not a UAT. [CITED: `~/.claude/context/workflows/two-gate-uat.md`]

## Summary

Phase 14 adds one new `CommandStrategy` (`LocalGrammarStrategy`), one new public declaration surface (`GrammarPack`), and three tiny additive seams (`Extraction.matchedLanguage`, six `TraceCode`s, a shared `StepSubmission.kt`). Everything runs in pure Kotlin in `:core` with no new dependency. The engine integration is almost free: a zero-call tier that returns `NoMatch`, `Completed`, or a pass-through of the resolver's verdict is already handled by `TierWalk` (carry cleared on `NoMatch`, escalation suppressed after a commit/hold, `Unhandled(cappedByPolicy)` under offline-only) and by `PolicyPreCheck` (`providers.isEmpty()` always passes). The hard part is **never guessing**: the matcher must be an anchored whole-utterance token matcher whose only success criterion is "exactly one distinct result", and the EN/ES number-word parser must consume the whole span or return "not a number".

Four findings change the plan versus the decision map. (1) The D-01 "guarded-prepare logic" clause is vacuous: `submitAll` calls no guarded-prepare code; `guardWrites`/`prepare` live in `AgenticDispatch.kt` and are Agentic-only, so commit 1 moves exactly two functions and must not touch `agentic/` (Phase 15 owns that extraction). (2) The repo's own gates constrain the public shape more than the DSL decision does: no enums, no `data`, **no new public `sealed` type** (`review-api-surface.sh` allow-list), no default-argument constructor stubs, no public static fields (so public constants only via companion getters), and `NoHardCodedConstantsTest` bans `const val` names matching `^(DEFAULT_|MIN_|MAX_)|TOKEN|ITERATION|CEILING` in main. The token cap is therefore **derived from the pack** (longest possible rule match), not a named constant. (3) Adding any `TraceCode` forces an edit to `TraceTest` (it compares every declared code against a hand list) and every new public top-level type needs an `API.md` row (C20) with no `food`/`card` words (C21). (4) D-12 has a proven mechanism: stt-engine already feeds WAV stimuli to the recognizer on this exact TESTER (`EXTRA_AUDIO_SOURCE`, precision/recall 0.994) through a debug demo app with an adb broadcast control surface. TTS-synthesized stimuli exercise the recognizer's number/accent *formatting* faithfully but not human acoustic variability; label the fixtures accordingly.

**Primary recommendation:** Build bottom-up and pure-first: commit 1 = `StepSubmission.kt` move (+ `Extraction.matchedLanguage`, trace codes); then the text tokenizer and EN/ES number parsers with an independent spell-and-parse round-trip over 0..999,999; then template parse/expand/validate + the single "enumerate all parses, abort on a second distinct result" matcher; then `LocalGrammarStrategy` mirroring SingleShot's tail with `NO_PROVIDER`. Treat the D-12 capture as a separately granted, non-autonomous TESTER window requested at phase start and consumed whenever the orchestrator relays it; the lexicon is internal, so late fixture-driven alias changes are patch-safe and never touch the frozen API.

## Architectural Responsibility Map

This is a library phase; "tiers" are the library/app/engine/test layers rather than web tiers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Transcript folding, tokenization, number-word parsing | `:core` internal (pure functions) | — | Deterministic, no I/O, no Android; JVM-testable (`:core` is pure Kotlin, L7/A7) |
| Declaring rules (intents, slots, EN/ES templates, fillers, `tryOtherLanguage`) | App (via `GrammarPack { }` DSL) | `:core` validates at build | The library names no domain (CLN-02); apps own phrasings |
| Matching, ambiguity detection, label handling | `:core` (`GrammarPack.match`, pure, non-suspend) | App unit tests call `match` directly | D-04: `CommandSession`'s ctor is internal, so `match` is the only way apps can corpus-test |
| Slot synonym / canonicalization (`normalize`) | App (supplies lambda) | `:core` runs it under `guardedPlain` | Domain knowledge (e.g. cross-language names) stays in the app; engine only defines null = reject |
| Resolving a match into steps (`OutcomeResolver`) | App | `:core` (`resolutionOutcome` pass-through) | Same seam as SingleShot (D-02) |
| Gate → commit → sink, held handling, `providerCallId = null` | `:core` pipeline (`CommitCoordinator`, unchanged) | App gate/sink | Single write path; grammar only calls `session.submit(step, null)` |
| Policy (offline-only, allowedProviders, maxTier) | `:core` pipeline (`PolicyPreCheck`, unchanged) | App `TierPolicySource` | `NO_PROVIDER` makes the tier always eligible |
| Real STT form capture (D-12) | TESTER device via stt-engine demo (external repo) | Host (adb, python/numpy) | Only the platform recognizer can say what it emits |
| Docs (`API.md` rows) | Repo docs | `verify-docs-coverage.sh` C20/C21 | Gate fails if a new public top-level type is not named |

## Standard Stack

### Core

No new libraries. Everything needed is already on the `:core` classpath allowlist.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib (KGP 2.3.20, JVM 11 bytecode, `-Xjdk-release=11`) | 2.3.20 | All grammar code | [VERIFIED: gradle/libs.versions.toml `kotlin = "2.3.20"`; core/build.gradle.kts `-Xjdk-release=11`] |
| kotlinx-serialization-json | 1.11.0 | `JsonObject`/`JsonPrimitive` for slot values (same types as `Extraction.arguments`, `TerminalCall.arguments`) | [VERIFIED: gradle/libs.versions.toml `serialization = "1.11.0"`; `core/build.gradle.kts` `api(libs.serialization.json)`] |
| `java.text.Normalizer` (JDK 11 API) | JDK | NFC composition of STT text before folding | In the JDK; allowed by `-Xjdk-release=11`; not flagged by `NoHardCodedConstantsTest.settingsAccess` [VERIFIED: that rule list read in NoHardCodedConstantsTest.kt:46-58] |
| `kotlinx-coroutines-core` | 1.11.0 | `suspend` execute only; matching itself is non-suspend | [VERIFIED: libs.versions.toml `coroutines = "1.11.0"`] |

### Supporting (tests)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 4 | 4.13.2 | All tests | Ecosystem standard here [VERIFIED: libs.versions.toml `junit = "4.13.2"`] |
| kotlinx-coroutines-test | 1.11.0 | `runTest` for pipeline tests | Same as existing `TierPolicyTest` |
| `core/src/testFixtures` (`FakeAiProvider`, `NoNetworkGuard`, `ScriptedStrategy`, `ScriptedGate`, `RecordingCommitSink`, `FakeMutation`) | in-repo | Zero-call proof, held path, next-tier carry | Already used by `TierPolicyTest`, `SingleShotTerminalTest` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Own number-word tables | ICU `RuleBasedNumberFormat` | ICU is not on the `:core` allowlist, is not in the JDK, and parses lenient forms (violates "whole-span, strict"). Reject. |
| Regex-compiled templates | Own token matcher | Regex is explicitly out (D-03); backtracking blow-up on dictated paragraphs (PITFALLS 12); ambiguity not observable |
| `fun interface SlotNormalizer` | Plain function type `(String, String) -> String?` | Recommended: the plain function type (the requirement text, D-07's "no more frozen fun interfaces"); both freeze arity-2. See Assumption A4. |

**Installation:** none. **Version verification:** versions read from `gradle/libs.versions.toml` this session (no registry lookup needed; no package is added).

## Package Legitimacy Audit

Not required: Phase 14 installs no external package. Packages removed due to `[SLOP]`: none. Flagged `[SUS]`: none. The D-12 harness reuses a repo that already exists on the host (`~/Projects/Reusable/stt-engine/android`) and host Python with numpy (already installed); nothing is downloaded.

## Verified In-Repo Facts (read this session)

Discrete in-repo values the plan depends on, each opened with `Read`/shell this session at HEAD `73b5a07`. Quoted verbatim.

| Fact | Source | Verbatim |
|------|--------|----------|
| `resolutionOutcome` signature to move | `core/.../strategy/singleshot/SingleShotOutcomes.kt:59` | `internal suspend fun resolutionOutcome(` (params `resolution: Resolution, onSteps: suspend (Resolution.Steps) -> StrategyOutcome`) |
| `UNKNOWN_RESOLUTION_CODE` (private, moves with it or is re-declared) | `SingleShotOutcomes.kt:13` | `private const val UNKNOWN_RESOLUTION_CODE = "unknown_resolution"` |
| `submitAll` body to move | `SingleShotStrategy.kt:154-159` | `private suspend fun submitAll(session: CommandSession, steps: Resolution.Steps, callId: String): StrategyOutcome {` / `steps.steps.filterIsInstance<ToolStep.Finished>().forEach { session.submit(it, callId) }` / `val mutations = steps.steps.filterIsInstance<ToolStep.Mutation>().flatMap { it.mutations }` / `val applied = if (mutations.isEmpty()) null else session.submit(ToolStep.Mutation(mutations), callId)` / `return StrategyOutcome.Completed(if (applied?.isError == true) null else steps.reply)` |
| Internal submit overload (nullable id) | `CommandSession.kt:41` | `internal abstract suspend fun submit(step: ToolStep, providerCallId: String?): DispatchResult` |
| Internal trace-code hook | `CommandSession.kt:60` | `internal abstract suspend fun recordCode(code: TraceCode)` |
| No `prepareGuarded`/`StepSubmission` at HEAD | repo-wide grep this session | no match in any `*.kt`; guarded-prepare code is `guardWrites` (`AgenticDispatch.kt:107`) and `prepare` (`AgenticDispatch.kt:127`), both taking agentic `DispatchContext` |
| `NO_PROVIDER` | `StrategyCapabilities.kt:31` | `public val NO_PROVIDER: StrategyCapabilities = StrategyCapabilities(emptySet())` |
| Policy permits provider-less tier offline | `PolicyPreCheck.kt:83` | `return !policy.offlineOnly \|\| providers.isEmpty() \|\| (capabilities.onDeviceOnly && onDevice)` |
| `Unhandled` creation | `TierWalk.kt:45` | `?: CommandOutcome.Unhandled(effects(), lastReason, ladder.cappedByPolicy)` |
| NoMatch clears carry; suppressed after work | `TierWalk.kt:89-90`, `:103` | `is StrategyOutcome.NoMatch ->` / `if (hasWorked()) suppressed(strategy, null) else startFresh(strategy)` ; `carry = null` |
| `Extraction` today (3-arg primary, 2-arg secondary) | `OutcomeResolver.kt:34-38` | `public class Extraction(` / `public val toolName: String,` / `public val arguments: JsonObject,` / `public val callId: String?,` / `) {` ; v1.0.1 `core/api.txt:1053` has only `ctor public Extraction(String toolName, kotlinx.serialization.json.JsonObject arguments);` |
| Limit-constant name ban (applies to `src/main` only) | `NoHardCodedConstantsTest.kt:35` | `private val limitName = Regex("^(DEFAULT_\|MIN_\|MAX_)\|TOKEN\|ITERATION\|CEILING")` |
| Sealed allow-list (a new public sealed type FAILS the surface review) | `scripts/review-api-surface.sh:17` | `ALLOWED_SEALED="$PKG.strategy.StrategyOutcome $PKG.pipeline.CommandOutcome $PKG.commit.RunTermination $PKG.commit.GateDecision $PKG.commit.ToolStep $PKG.transcript.Message $PKG.transcript.AssistantPart"` |
| Surface rules in `ApiShapeTest` | `ApiShapeTest.kt:59` etc. | `fun noMainClassIsAnEnum()`, `noMainClassIsDataShaped`, `noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion`, `noClassOutsideTheDocumentedExceptionsDeclaresADefaultArgumentConstructorStub`; `Extraction` test at `:142` requires public ctors `(String, JsonObject)` and `(String, JsonObject, String)` and no default stub |
| `TraceTest` compares ALL declared codes to a hand list | `TraceTest.kt:237-262` | `assertEquals(declaredWireValues(), wireValues.toSet())` and a count assertion `assertEquals(TWENTY_EIGHT, all.size)` |
| New public top-level types must be named in `API.md` | `scripts/verify-docs-coverage.sh:100-105,271-281` | `grep -rhE '^public '` (column 0 only: nested types are not required) ; `grep -qF -- "\`$name\`" "$API" \|\| fail C20` |
| Docs ban domain words | `verify-docs-coverage.sh:285-289` | `grep -Eiq '\b(food\|cards?)\b'` → `fail C21` |
| Compat check only runs against the committed `api.txt` (v1.0.1) | `build.gradle.kts:47-51` | `onlyIf("api.txt exists") { project.file("api.txt").isFile }` ; `api.txt` regenerated at the v1.1.0 cut, not in Phase 14 |
| `guardedPlain` (non-suspend app code) | `core/.../internal/Guarded.kt:75` | `internal inline fun <T> guardedPlain(onFault: (EngineFault) -> T, block: () -> T): T = try {` |
| detekt defaults in force (zero baseline, `buildUponDefaultConfig`) | `default-detekt-config.yml` extracted from `detekt-core-1.23.8.jar` | `MagicNumber ignoreNumbers: '-1','0','1','2'`, `ignoreConstantDeclaration: true`; `ReturnCount max: 2 excludeGuardClauses: false`; `CyclomaticComplexMethod threshold: 15`; `LongMethod threshold: 60`; `NestedBlockDepth threshold: 4`; `TooManyFunctions thresholdInFiles: 11` (private counted); `LongParameterList functionThreshold: 6`; `ThrowsCount max: 2`; `UseRequire active` (repo overrides: `thresholdInClasses: 12`, `constructorThreshold: 9`, `ignoreDefaultParameters: true`) |

## Architecture Patterns

### System Architecture Diagram

```
 app: GrammarPack { intent(tool){ slots; en("..."); es("...") ; terminal? } ; fillers ; tryOtherLanguage }
        │  build time: parse templates → expand → typed-slot checks → duplicate/ambiguity self-check → IAE on any fault
        ▼
 command in ──► CommandPipeline ──► PolicyPreCheck (NO_PROVIDER ⇒ always eligible, offline-only too)
                                          │
                                          ▼
                               TierWalk.runTier ─► LocalGrammarStrategy.execute(input, session)
                                          │             (never calls session.model(); never ceilingReached)
                                          ▼
                    GrammarPack.matchDetailed(transcript, input.language)      (pure, non-suspend)
        ┌─────────────────────────────────────────────────────────────────────────────────────┐
        │ 1 NFC → tokenize (offsets kept) → fold; > derived word cap → Rejected(TOO_LONG)      │
        │ 2 label: "en"|"es"|null|other  → candidate packs (other → Rejected(LANGUAGE))        │
        │ 3 per pack: strip fillers → enumerate ALL parses of all rules (slots bound by span)  │
        │      ≥2 DISTINCT results in one pack → Rejected(AMBIGUOUS)                           │
        │ 4 per matching pack: run normalize on text/choice slots (guardedPlain)               │
        │      null/blank → Rejected(SLOT_REJECTED) ; throw → Rejected(NORMALIZE_ERROR)        │
        │ 5 ≥2 matching packs must agree on (tool, terminal, slot JSON) else Rejected(AMBIGUOUS)│
        └─────────────────────────────────────────────────────────────────────────────────────┘
              Rejected(code?) / no match ──► [record code] ──► StrategyOutcome.NoMatch()
                                                                  └► TierWalk.startFresh: carry = null ──► next tier
              Matched(terminal) ──► Completed(null, TerminalCall(tool, args))      (no resolver, no gate)
              Matched ──► resolver.resolve(Extraction(tool, args, callId = null, matchedLanguage), input)
                              ├─ Steps ──► submitSteps(session, steps, providerCallId = null) ──► gate → commit → sink
                              │               Completed(reply?)  (held ⇒ result.held, never "success")
                              ├─ NoMatch ──► [record GRAMMAR_RESOLVER_REJECTED] ──► NoMatch
                              ├─ Escalate ──► Escalate(reason, carry)   (suppressed by TierWalk if work was done)
                              ├─ Failed ──► Failed(reason, details)
                              └─ throw ──► TierWalk guarded ──► strategy_error + Failed(Unexpected)
        no tier handles it, offline-only ──► Unhandled(lastReason = null, cappedByPolicy = true), zero provider calls
```

### Recommended Project Structure

```
core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/
├── StepSubmission.kt                    # NEW (commit 1): internal resolutionOutcome + submitSteps (moved, nullable id)
├── OutcomeResolver.kt                   # EDIT: Extraction gains matchedLanguage (internal 4-arg primary; 2- and 3-arg public secondaries)
└── grammar/                             # NEW package
    ├── GrammarPack.kt                   # public: GrammarPack, nested Builder/IntentBuilder, companion operator invoke
    ├── GrammarMatch.kt                  # public: result of match (redacted toString)
    ├── LocalGrammarStrategy.kt          # public: strategy + Builder (pack, resolver) + companion invoke; capabilities = NO_PROVIDER fixed
    ├── GrammarText.kt                   # internal: NFC, fold, tokenize with offsets, filler strip
    ├── Template*.kt                     # internal: parser (EBNF below), expander, rule compiler
    ├── Slots.kt                         # internal: integer/decimal/choice/text slot candidate generators + JSON value builders
    ├── RuleMatcher.kt                   # internal: enumerate-all-parses, distinct-result ambiguity
    ├── PackValidator.kt                 # internal: build-time checks + example self-check
    └── number/                          # internal EN/ES lexicons and parsers
        ├── DigitForms.kt  NumberLexiconEn.kt  NumberLexiconEs.kt  NumberParserEn.kt  NumberParserEs.kt  Fractions.kt
core/src/main/.../telemetry/TraceCode.kt # EDIT: six new companion vals (additive)
core/src/test/kotlin/.../  Grammar*Test.kt, Number*Test.kt, LocalGrammar*Test.kt   (example packs here; CLN-02 does not scan src/test)
core/src/test/resources/grammar/         # D-12 fixtures (synthetic stimulus text + recognizer output), neutral phrases only
config/detekt/detekt.yml                 # EDIT only if the lexicon needs a MagicNumber exclude (justified, one line)
API.md                                   # EDIT: rows for every new public top-level type (no "food"/"card")
```

### Pattern 1: The shared submit helper (D-01 move)

**What:** Create `core/strategy/StepSubmission.kt` (package `…core.strategy`) holding exactly two internal functions moved verbatim, with `providerCallId: String?`. SingleShot calls them with `call.id`; the grammar calls them with `null`.
**When to use:** commit 1 of the phase, before any grammar code. A pure move: SingleShot's existing tests are the regression net (`SingleShotResolveTest`, `SingleShotOutcomeMappingTest`, `SingleShotPlumbingTest`, `CommitPathTest`, `HeldReportingTest`). No test references the moved names directly [VERIFIED: grep of `resolutionOutcome|submitAll` over `*.kt` this session: only the two source files].
**Do not touch `agentic/`.** `guardWrites`/`prepare` are Agentic-specific (take `DispatchContext`, `ToolSpec`); the grammar has no `ToolExecutor`. Phase 15 extracts what it needs. Leave a note in the plan SUMMARY so 15 knows. [VERIFIED: AgenticDispatch.kt:99-131]

```kotlin
// strategy/StepSubmission.kt  (shape only; bodies are the verbatim moved code)
internal suspend fun resolutionOutcome(resolution: Resolution, onSteps: suspend (Resolution.Steps) -> StrategyOutcome): StrategyOutcome = /* moved */

// Finished steps first, then every mutation folded into one gated step; reply withheld when the apply errored.
internal suspend fun submitSteps(session: CommandSession, steps: Resolution.Steps, providerCallId: String?): StrategyOutcome = /* moved, callId → providerCallId */
```

### Pattern 2: `Extraction.matchedLanguage` without freezing a bad shape (D-05)

Make the 4-arg constructor `internal` and the primary; keep the two public overloads as secondaries (same JVM signatures as today). `ApiShapeTest.extractionKeepsItsTwoArgumentConstructorAlongsideTheCallIdOne` keeps passing (it only requires the two public shapes and no default stub); `toString` still prints only tool name and argument count. [VERIFIED: ApiShapeTest.kt:142-153; OutcomeResolver.kt:34-60]

```kotlin
public class Extraction internal constructor(
    public val toolName: String,
    public val arguments: JsonObject,
    public val callId: String?,
    /** "en" or "es" for a grammar match; null for a model tier and for a language-neutral grammar match (see A5). */
    public val matchedLanguage: String?,
) {
    public constructor(toolName: String, arguments: JsonObject, callId: String?) : this(toolName, arguments, callId, null)
    public constructor(toolName: String, arguments: JsonObject) : this(toolName, arguments, null, null)
    // init { require(toolName.isNotBlank()) ...; require(callId == null || callId.isNotBlank()); require(matchedLanguage == null || matchedLanguage.isNotBlank()) }
}
```

### Pattern 3: The tier is SingleShot's tail with no model

```kotlin
// LocalGrammarStrategy.execute (shape; NOT a full implementation)
override val capabilities: StrategyCapabilities = StrategyCapabilities.NO_PROVIDER   // fixed, not a builder property (GRAM-05)

override suspend fun execute(input: CommandInput, session: CommandSession): StrategyOutcome =
    when (val result = pack.matchDetailed(input.transcript, input.language)) {
        is GrammarResult.Rejected -> { result.code?.let { session.recordCode(it) }; StrategyOutcome.NoMatch() }
        is GrammarResult.Matched -> dispatch(input, session, result.match)
    }
// dispatch: terminal -> Completed(null, TerminalCall(tool, args)); else resolver.resolve(Extraction(tool, args, null, lang), input)
//   -> if (resolution is Resolution.NoMatch) session.recordCode(GRAMMAR_RESOLVER_REJECTED)
//   -> resolutionOutcome(resolution) { submitSteps(session, it, providerCallId = null) }
```

Rules: never call `session.model()` (so zero turns, `TierAttempt.provider == null`); never call `ceilingReached`/`ceilingCrossed` (a zero-call tier must not fail on a 0 token ceiling); no `try/catch` in the strategy: a resolver throw reaches `TierWalk.executeGuarded` → `strategy_error` + `Failed(Unexpected)` exactly as SingleShot (D-02). `GrammarResult` is an **internal** sealed class (not in the public dump, so the sealed allow-list is not involved). [VERIFIED: TierWalk.kt:50-61 `executeGuarded`; SingleShotStrategy.kt:141-146]

### Pattern 4: Public surface = builders with internal constructors, no sealed/enum/data

Follow the `TierPolicy { }` / `SingleShotStrategy(id) { }` idiom: `public class X internal constructor(...)`, nested `public class Builder internal constructor()`, `public companion object { public operator fun invoke(block: Builder.() -> Unit): X }`. Slot kinds are **builder methods** (`integer`, `decimal`, `choice`, `text`), not a public type hierarchy; the internal slot representation can be an internal sealed class. Public constants only via companion getters (a public `const val` is a public static field and fails `ApiShapeTest`). Constants in main are `private const val`. [VERIFIED: ApiShapeTest.kt:59-89]

### Pattern 5: The anchored enumerate-all-parses matcher (see "Matching algorithm")

### Pattern 6: Plan slicing (file ownership, ≤2 Gradle plans per wave)

| Wave | Plan | Gradle? | Owns (hot files) |
|------|------|---------|------------------|
| 1 | A: commit 1 `StepSubmission.kt` move; then `Extraction.matchedLanguage`; six `TraceCode`s + `TraceTest` rows + `ApiShapeTest` ctor-list assertion | yes | `singleshot/*`, `OutcomeResolver.kt`, `TraceCode.kt`, `TraceTest.kt`, `ApiShapeTest.kt` |
| 1 | B: `GrammarText` + `number/*` + round-trip/golden/malformed tests (pure, no dependency on A) | yes | new files only (+ `detekt.yml` if a MagicNumber exclude is chosen) |
| 1 (no Gradle until granted) | C: D-12 window request, prompt list, harness procedure; non-autonomous | no (host python only) | `14-WINDOW-GRANT.md`, `core/src/test/resources/grammar/*`, an optional guarded script |
| 2 | D: template parser/expander, slot kinds, `GrammarPack` builder + validator + self-check | yes | `grammar/*` (needs B) |
| 2 | E: matcher + label handling + normalize + `GrammarMatch` + near-miss corpus (needs B, D) | yes | `RuleMatcher.kt`, `GrammarMatch.kt` |
| 3 | F: `LocalGrammarStrategy` + pipeline tests (zero-call, held, offline-only, terminal, carry) + redaction canary + `API.md` rows | yes | `LocalGrammarStrategy.kt`, tests, `API.md` |
| 4 | G: fold fixtures back (lexicon aliases), frozen-surface review, full `./gradlew check` + docs gate | yes | lexicon files, review doc |

## DSL spec (D-03): exact template mini-syntax

### Surface (proposed; invariants matter more than names)

```kotlin
val pack = GrammarPack {
    tryOtherLanguage = false                       // D-10 opt-in; also honored by pack.match, so app corpus tests reproduce the tier
    fillers(en = listOf("please", "uh"), es = listOf("por favor", "eh"))   // leading/trailing strip only; ENGINE SHIPS NONE
    subRule(en = "polite", "[please]")             // optional: named sub-rule per language, referenced as <polite>
    intent("record_entry") {                       // = the tool name the app's resolver switches on
        integer("count", 1, 99)                    // slots declared ONCE per intent
        text("label", 4)                           // bounded free text: max words REQUIRED (build-time)
        normalize("label") { raw, language -> /* String? ; null = reject */ }
        en("add {count} {label}", "(put|record) {count} {label} [<polite>]")
        es("agrega {count} {label}", "anota {count} {label}")
    }
    intent("open_screen") { terminal(); en("open settings"); es("abre ajustes") }   // → TerminalCall, no resolver, no gate
}
```

Builder surface to freeze (each is a forever member; keep minimal): `GrammarPack { }`, `GrammarPack.match(transcript, language): GrammarMatch?`, `Builder.tryOtherLanguage`, `Builder.fillers`, `Builder.subRule`, `Builder.intent`, `IntentBuilder.integer/decimal/choice/text/normalize/en/es/terminal`, `GrammarMatch.{toolName, arguments, matchedLanguage, ruleId, terminal}`, `LocalGrammarStrategy { pack; resolver }`. Overloads, never defaulted parameters (default-arg synthetic stubs freeze function shapes too). Languages are the explicit methods `en`/`es`, not a free-form language string.

Frozen-surface review (PITFALLS 5): "what will a consumer ask next, and can it be added without removal?" → more languages (new `fr(...)` member), `explain()`, a `SlotParser` seam, intent `priority`, `singleLanguage` lint, rule-id exposure: all addable as new members. The one non-extensible choice is `normalize`'s arity (A4); it is mitigated because the hook is registered per slot (the slot name is known) and already receives the language.

### Template EBNF (no escapes, no regex; ASCII metacharacters only)

```
template  := seq
seq       := item+                       (items separated by ≥0 spaces; whitespace between items is insignificant)
item      := word | optional | group | slot | rule
word      := one or more chars other than whitespace and  [ ] ( ) { } < > |
optional  := '[' alt ']'                 zero or one occurrence of any alternative
group     := '(' alt ')'                 exactly one of the alternatives
alt       := seq ( '|' seq )*            empty alternatives are a build-time IllegalArgumentException
slot      := '{' name '}'                name = [a-z][a-z0-9_]*, MUST be declared on the intent
rule      := '<' name '>'                sub-rule of the same language, non-recursive (cycle = build-time IAE)
```

Words are folded with the same function as transcripts (NFC, lowercase, vowel-accent fold, ñ kept, apostrophes dropped). A word containing a metacharacter cannot be written (unsupported in v1.1, loud error). Hyphenated words in a template are split exactly like transcripts.

### Build-time validation (all `IllegalArgumentException` via `require`, messages carry template text and names only, never transcripts)

1. Unknown `{slot}`/`<rule>`; slot declared but unused in every template; rule cycle.
2. Every template needs ≥1 literal word (after expansion); no empty alternative; no empty template.
3. **Required-slot parity:** every template of an intent, in both languages, must bind the same set of slots outside `[ ]`; slots inside `[ ]` may be absent (key omitted from `arguments`). This is what makes "same tool call, same typed values across languages" true by construction.
4. **Delimiting:** two open-span slots (integer/decimal/text; choice counts as closed) may not be adjacent without a literal word between them; a text slot must have `maxWords ≥ 1`; at most four slots per template (private named limit; relaxing it later is additive). A text slot at the end of a template is fine (bounded by `maxWords` and the end anchor).
5. Duplicate flat sequences (after fold) within a language across the whole pack → IAE, except identical sequences of two intents with the same tool and equal slot bindings. Accent-only collisions (`sí`/`si`) fall out of this because both fold equal.
6. Choice slots: every synonym tokenizes to ≥1 folded token; no synonym (folded) appears under two different option ids.
7. **Ambiguity self-check:** for each flat rule, build a token-level example (integer/decimal slots → digit token of `min` and of `max`; choice → first synonym; text → a placeholder token no literal can equal) and run the real matcher over the whole pack; the example must produce exactly that rule's result and no other distinct result. Any rule that another rule also matches with a different result → IAE naming both rule ids. (Literal-vs-wildcard overlaps such as `remove all` vs `remove {text}` are real ambiguity and fail loudly here.)
8. Expansion bound: a template whose alternatives/optionals expand beyond a private build-time limit (suggest 256 flat sequences per template) → IAE "split it with sub-rules". Name the constant without `DEFAULT_/MIN_/MAX_/TOKEN/ITERATION/CEILING` (see Pitfall 2).
9. `terminal()` intents may declare slots (they become `TerminalCall.arguments`); a pack with any non-terminal intent needs a resolver at strategy build (IAE naming the setting, same wording style as SingleShot).
10. `normalize` only on `text` and `choice` slots (D-09); on integer/decimal → IAE.

### Slot kinds (D-07: exactly four)

| Kind | Builder | JSON in `arguments` | Candidate spans at a position | Notes |
|------|---------|---------------------|-------------------------------|-------|
| integer | `integer(name, min, max)` | `JsonPrimitive(Long)` | every end `e` where `parseInteger(tokens[p,e), pack language)` succeeds and `min ≤ v ≤ max` | digit tokens and words, strict whole-span; decimal point in an integer token = reject even for `.000` |
| decimal | `decimal(name, min, max)` | `JsonPrimitive(Double)` always (so `2` and `2.0` never differ across languages) | same, with `parseDecimal` | parse through integer arithmetic/`BigDecimal`, then `toDouble()`; never `float` sums |
| choice | `choice(name) { option("id") { en("synonym", …); es(…) } }` | `JsonPrimitive(String)` = declared option id (or `normalize` result) | every synonym whose folded tokens equal `tokens[p, p+len)` | closed; two options matching the same span ⇒ distinct results ⇒ ambiguous |
| text | `text(name, maxWords)` | `JsonPrimitive(String)` = original surface substring (NFC, case and accents preserved, interior punctuation preserved) or `normalize` result | every `len` in `1..maxWords` | bounded; delimited by the next literal or the end |

### `normalize` semantics (D-09, refined)

- Signature `(raw: String, language: String) -> String?`; `raw` = original surface substring of the slot span (not folded); `language` = the matched pack's language, never null.
- Runs **after** a unique parse and **before** `Extraction`/`TerminalCall`, once per candidate pack, under `guardedPlain`. `null` or blank result → `NoMatch` + `GRAMMAR_SLOT_REJECTED`; throw → `NoMatch` + `GRAMMAR_NORMALIZE_ERROR` (class name only is recordable); neither is `strategy_error`.
- When two packs both matched (null label, or `tryOtherLanguage`), **all** candidates must normalize successfully and the final `(tool, terminal, slots)` must be equal; otherwise `NoMatch` + `GRAMMAR_AMBIGUOUS`/`SLOT_REJECTED`. Comparing after normalize is what lets CT's map make EN "chicken" and ES "pollo" agree. (A candidate that normalizes to `null` is not silently dropped in favor of the other pack: precision over recall.)
- Applies to `text` and `choice` slots; for `choice` the hook receives the matched synonym's surface text and its result replaces the declared id (the app owns what it returns). Open Question 2 asks CT/SB to confirm that "list slots" in D-09 means the closed-choice kind.
- Cancellation: not suspend, so there is nothing to propagate; the app must not block (document it).

## Matching algorithm (never guesses)

1. **Fold & tokenize** (`GrammarText`): see "Text normalization". Input longer than the pack's **derived** word cap → `Rejected(GRAMMAR_INPUT_TOO_LONG)`. Derived cap = max over rules of (literal words + Σ slot max span), where an integer/decimal slot's max span is the lexicon's longest number phrase (12 tokens EN with "and", 9 ES, plus a bounded fraction tail) and a text slot's is `maxWords`; plus the longest leading and trailing filler phrase. Anything longer cannot match any rule, so this is a performance guard, not a limit knob, and needs no named constant.
2. **Candidate packs** (D-10):

| `language` | `tryOtherLanguage = false` | `tryOtherLanguage = true` |
|-----------|----------------------------|---------------------------|
| `"en"` | EN only | EN, ES |
| `"es"` | ES only | ES, EN |
| `null` | EN and ES (default; SB's binding condition) | same |
| anything else (`"EN"`, `"en-US"`, `""`, `"fr"`) | `Rejected(GRAMMAR_LANGUAGE_UNSUPPORTED)` | same |

3. **Per pack:** strip leading and trailing filler phrases (repeat, longest first; interior fillers must be written as `[…]` in templates). Then for each flat rule run a depth-first walk over its elements: `WORD` must equal the next folded token; a `SLOT` enumerates candidate spans per slot kind (above) and binds; success needs all tokens consumed (anchored at both ends). Collect results as `(tool, terminal, slot map)` and keep a set; **abort as soon as a second distinct result exists** → ambiguous. Identical results from different rules/expansions are one result.
4. **Pack verdicts:** 0 results → no match for that pack; 1 → candidate; ≥2 distinct → `Rejected(GRAMMAR_AMBIGUOUS)` for the whole command (any pack ambiguous = no guess).
5. **Normalize** each candidate (above), then require equal finals across candidates. Result: `Matched(GrammarMatch)` or `Rejected(code?)`.
6. **Complexity:** bounded by tokens ≤ derived cap, ≤4 slots/rule, ≤12 candidate lengths per number slot, ≤`maxWords` per text slot, with literals pruning early. No regex, no backtracking over user-supplied patterns, no recursion on input length beyond slot count. State the bound in the KDoc.
7. `matchedLanguage`: the single matching pack's language; when two packs agree, the labeled language if a label was given, else `null` ("language-neutral match"; A5). `ruleId`: opaque `String`, null when two packs agree (Open Question 3).

Not allowed anywhere in the matcher (warning signs from PITFALLS 12): a score, a threshold, `contains`/`startsWith` on the transcript, "best match", "closest rule", first-match-wins, stemming, edit distance, a global number-word→digit rewrite of the transcript. [CITED: .planning/research/PITFALLS.md Pitfall 12; FEATURES.md anti-features]

## Text normalization (D-11, operational rules)

| Step | Rule | Why / edge |
|------|------|-----------|
| 1 | `Normalizer.normalize(text, NFC)` first | STT may emit `n` + combining tilde; NFC composes it to `ñ` before any folding |
| 2 | Lowercase with `String.lowercase()` (invariant) | Kotlin's `lowercase()` is locale-invariant; never `toLowerCase()`/device locale (warning sign in PITFALLS 13) |
| 3 | Fold vowel accents by explicit map: `áàâä→a`, `éèêë→e`, `íìîï→i`, `óòôö→o`, `úùûü→u`; **`ñ` untouched** | No NFD-and-strip anywhere (turns `año` into `ano`). Folding is applied to transcript tokens AND template literals AND choice synonyms |
| 4 | Drop apostrophes (`'`, `’`) from the folded key; keep in raw | "don't" → key `dont`, same as a template literal `don't` |
| 5 | Split on whitespace; split on `-`/`–`/`—` **only between two letters** | `twenty-one`, `e-mail` split; `5-10`, `-5`, `twenty-1` stay one token and fail number parse (fail-safe; never drop a sign) |
| 6 | Strip edge punctuation from each run: `. , ; : ! ? ¿ ¡ " “ ” ‘ ’ « » ( ) …` ; drop runs that become empty | Interior `.` `,` `/` kept so `2.5`, `1,000`, `1/2` survive; symbols like `%`, `$`, `#`, `&`, `+` are NOT stripped, so `5%` is one non-number token → NoMatch |
| 6a | **Hardening (recommended, refines D-11 toward NoMatch):** a sentence terminator (`. ! ? ;`) between two word tokens (not a decimal/grouping separator between digits) marks a clause boundary → `Rejected` (no code needed; plain no-match) | "add milk. delete everything" must not match a text slot; abbreviations ("Dr. Smith") then go to the cloud tier, which is the cheap miss. A `,` is not a boundary (lists). Needs a near-miss test (A11) |
| 7 | Keep for each token: folded key + `[start,end)` offsets into the NFC string | slot raw text = `nfc.substring(firstStart, lastEnd)` (original case/accents/interior punctuation) |
| 8 | ASCII digits only; leading zeros rejected except a single `0` / `0.x` | `007`, Arabic-Indic digits, full-width digits → not numbers |

Homograph/false-friend policy: folding makes `más`≡`mas`, `sí`≡`si`, `qué`≡`que`; authors who need both write one literal (they fold equal); a pack that declares two rules differing only by accent is a duplicate → IAE; at runtime two parses reachable only through folding give distinct results → NoMatch. Cross-language false friends (`once`, `no`, `sin`, `me`, `a`, `pie`) are handled by the null-label "both packs must agree" rule, not by special cases.

## Number-word golden tables (D-08)

Scope: integers 0..999,999 as digits or words; decimals/fractions in `decimal` slots; **whole-span** parse (any token left over → "not a number"); a number phrase is either all-words or all-digits (mixing inside the integer part is rejected, "twenty 1"), except a digit integer followed by a fraction **word** tail ("2 y medio", "2 and a half"), which is accepted. All parsing is table-driven and strict; the lexicon is internal so fixture-driven aliases are patch-safe.

### English

| Class | Accept (value) | Reject |
|-------|----------------|--------|
| 0..19 | zero … nineteen | `oh five`, `zero five`, `fourty`, `a`/`an` as a bare number (see A3), `one one` |
| tens | twenty … ninety; `twenty one`, `twenty-one` (split by tokenizer) | `twenty and five`, `twenty 1` |
| hundreds | `one hundred`, `a hundred`, `one hundred five`, `one hundred and five` (`and` only directly before a trailing 1..99 group) | `hundred` alone, `one hundred hundred`, `one hundred and` , leading `and` |
| colloquial hundreds | `fifteen hundred` (1500), `twenty five hundred` (2500): `<11..99> hundred`, only when no thousand group is present | `ten hundred`, `fifteen hundred thirty thousand` |
| thousands | `five thousand`, `a thousand`, `two thousand five hundred`, `one thousand and five`, up to `nine hundred ninety nine thousand nine hundred ninety nine` (and the `and` form) | `million`, `thousand` alone, `one thousand thousand`, `dozen` |
| fractions | bare `half`/`a half`/`one half` (0.5), `a quarter`/`one quarter`/`quarter` (0.25), `three quarters` (0.75); after an integer: `<n> and a half`, `<n> and a quarter`, `<n> and three quarters` (`one and a half` = 1.5) | `a half` as an integer, `one point` |
| decimals | `<n> point <digit word>+` (`two point five` = 2.5, `zero point two five` = 0.25), `point five` = 0.5 | `point twenty five` (default; add only if fixtures show it), mixed `two point 5` |
| digit forms | `5`, `1,000` (= 1000, groups of exactly 3), `2.5`, `0.5`, `2 1/2`, vulgar-fraction glyphs (optional, decide from fixtures) | `1,5`, `007`, `5%`, `1,00`, `1.000` for an INTEGER slot (a decimal slot accepts it as 1.0); note `1,000.5` is accepted by a decimal slot as 1000.5 |
| homophones | none accepted: `to/too/for/ate/won` | all (an app that wants them supplies choice synonyms) |

### Spanish (accents folded before lookup: `dieciséis`≡`dieciseis`, `veintiún`≡`veintiun`)

| Class | Accept (value) | Reject |
|-------|----------------|--------|
| 0..15 | cero, uno, dos, tres, cuatro, cinco, seis, siete, ocho, nueve, diez, once, doce, trece, catorce, quince | `un`/`una` as a bare number (see A3) |
| 16..19 | dieciséis, diecisiete, dieciocho, diecinueve; **archaic split `diez y seis` … `diez y nueve`** accepted as an STT alias (D-08) | `diez y` alone, `diez y diez` |
| 20..29 | veinte; veintiuno/**veintiún**/veintiuna, veintidós, veintitrés, veinticuatro, veinticinco, veintiséis, veintisiete, veintiocho, veintinueve; archaic `veinte y uno` … `veinte y nueve` accepted as an alias (same family as `diez y seis`; MEDIUM, confirm with fixtures) | `veinte uno`, `veintiuno mil` |
| 30..99 | treinta, cuarenta, cincuenta, sesenta, setenta, ochenta, noventa; **`y` required** between tens and units: `treinta y uno/un/una`, `cuarenta y cinco`, `noventa y nueve`; one-word `treintaicinco`-style spellings accepted as aliases (RAE-valid per search summary) | `treinta uno`, `treinta y cero`, `treinta y` , `veinte y` |
| hundreds | `cien` **only** as exactly 100 or before `mil` (`cien mil`); `ciento` + 1..99 (`ciento uno`, `ciento veinte`, `ciento treinta y cuatro`); doscientos, trescientos, cuatrocientos, **quinientos**, seiscientos, **setecientos**, **novecientos**, and feminine `-as` forms (`doscientas`, `quinientas`) | `ciento y cinco`, `cien cinco`, `ciento` alone, `cientocinco`, `dos cientos` |
| thousands | `mil` (1000, never `un mil`), `dos mil`, `quince mil`, `cien mil`, `ciento un mil`, `doscientas mil`, `mil quinientos`, `dos mil veinte`; apocope before `mil`: `veintiún mil`, `veintiuna mil`, `treinta y un mil`, `treinta y una mil` (RAE: both genders optional) | `un mil`, `uno mil`, `veintiuno mil`, `millón`/`millones`, `docena` |
| fractions | bare `medio`/`media` (0.5), `un cuarto` (0.25), `tres cuartos` (0.75); after an integer: `<n> y medio/media`, `<n> y cuarto`, `<n> y un cuarto`, `<n> y tres cuartos` (`dos y medio` = 2.5) | `<n> con medio`, `<n> con` (the preposition is NOT a decimal marker) |
| decimals | `<n> coma <frac>` and `<n> punto <frac>`; `<frac>` = 1+ digit words (`dos coma cero cinco` = 2.05) **or** a single cardinal 1..99 read as n/10^(digits) (`cero coma veinticinco` = 0.25, `tres coma cinco` = 3.5) | `coma` alone, `tres coma ciento cinco`, mixed `tres coma 5` |
| digit forms (language-resolved) | `5`; `2,5` (ES decimal comma); `1.5` (decimal point when the `.` is followed by 1-2 or 4+ digits); `2 y medio` (digits + fraction word) | **`1.000` / `12.345` (single `.` + exactly 3 digits) = AMBIGUOUS → not a number**; `1,5` is fine in ES but a reject in EN; any `.`-grouping beyond the 999,999 cap |

### Digit grouping, resolved by the language of the pack being tried

| Token | EN pack | ES pack | null-label outcome (both must agree) |
|-------|---------|---------|------------------------------------|
| `1000` | 1000 | 1000 | agree |
| `1,000` | 1000 (integer or decimal slot) | 1.0 (decimal slot only; integer slot rejects) | disagree → NoMatch |
| `1.000` | 1.0 (decimal slot only) | AMBIGUOUS → rejects | NoMatch |
| `1.5` | 1.5 | 1.5 | agree |
| `1,5` | rejects | 1.5 | ES only → ES match |
| `12,345` | 12345 | 12.345 (decimal) | disagree → NoMatch |

All of the above follow D-08 ("ambiguous digit grouping → NoMatch"); the exact operationalization (the single-dot-plus-3-digits rule) is a design choice, flagged A7. D-12 fixtures decide whether the real recognizer ever emits `.`-grouped ES digits.

### Test strategy for the tables (two independent routes, PITFALLS 13)

1. **Round-trip:** a test-only speller (written independently of the parser's tables, algorithmic, different code path) produces the canonical EN and ES spelling of every n in 0..999,999 (include the feminine/apocope variants as a second generator pass for ES: `veintiún mil`, `doscientas mil`); assert `parse(spell(n)) == n` and `parse(digits(n)) == n` per language. A full sweep is ~1M parses per language and should stay in seconds; if not, sweep 0..99,999 fully and 100,000..999,999 on a stride plus all boundary values.
2. **Hand-written accept/reject table** (the rows above), each with the expected value or "not a number"; plus the digit-grouping matrix; plus a partial-span test (`parse` of a prefix/suffix of a valid phrase must be null).
3. Non-vacuity: assert the generator produced 1,000,000 distinct strings per language and the table has ≥1 reject row per class.

## D-12: capturing real STT forms on the TESTER (non-autonomous checkpoint)

**What decides how much of the table is real:** whether the S22 recognizer emits digits or words for numbers, how it writes `2 y medio`, whether it adds `¿?` and periods, and whether ES decimals use `,` or `.`. The research flagged this as the phase's largest unknown. [CITED: .planning/research/FEATURES.md "Open questions for phase-level research"]

**Mechanism that already works on this exact device (reuse, do not reinvent):** stt-engine's v1.0 probe fed a 130 s WAV to the platform recognizer through `RecognizerIntent.EXTRA_AUDIO_SOURCE` on the SM-S908U (`fedFully: true`, precision 0.994, recall 0.994, 322/324 words), and its debug demo app exposes an adb broadcast control surface (`DemoControlReceiver`: START with a language extra and an audio-source file, DUMP_DIAGNOSTICS, STOP) that writes a JSONL session log. The same repo's `BilingualStimulusGeneratorTest` synthesizes utterances with the on-device `TextToSpeech` (es-US voice was present at that time) one at a time, because `setLanguage` is engine-global. [CITED: `~/Projects/Reusable/stt-engine/.planning/milestones/v1.0-phases/05-android-library/05-RUNG-VERDICT.md` ("AND-02 stimulus - FILE-FED"); `~/Projects/Reusable/stt-engine/android/demo/src/debug/.../DemoControlReceiver.kt`; `…/stt/src/androidTest/.../BilingualStimulusGeneratorTest.kt`]

**Recommended procedure (plan C, non-autonomous):**

1. Host-only prep, no device: write the prompt list (`core/src/test/resources/grammar/stt-prompts.tsv`: language tag, spoken text) covering 0..30, tens, hundreds, thousands compounds, `y medio`/`and a half`, decimals (`coma`/`punto`/`point`), digit-mixed forms, accented/unaccented words, plus 10 neutral near-misses. Use neutral nouns only (no domain words, even though `src/test` is unscanned). Synthetic text, no PII.
2. Request one TESTER window from the orchestrator in the Phase 13 form: a `14-WINDOW-GRANT.md` whose exact line `grant: open` (relayed by the orchestrator, never self-authored) gates every device step, with `timebox_s`, device `R5CT10XNKQN` only, no API keys, no spend, full cleanup. Request it **at phase start** (relay latency is the risk), consume it whenever granted. [CITED: `.planning/phases/13-on-device-model-spike/13-WINDOW-GRANT.md`; `13-08-PLAN.md` "not autonomous"]
3. Under the grant only: synthesize each prompt with on-device TTS (en-US and es-US), build WAVs (16 kHz mono PCM16; host `numpy` is installed for the stt stitcher scripts), push to the demo's app-external dir, run START/DUMP/STOP per prompt over `adb -s R5CT10XNKQN`, pull the JSONL, and extract final-segment text. Reuse the guard pattern of `scripts/run-sample-gate1.sh` (USB serial pin, no other device, shared flock, never airplane/radio toggling). Record `(spoken, tag, recognizer final)` triples with a `synthetic-tts` provenance column; run through an evidence filter like `scripts/spike-evidence-filter.sh` before commit.
4. Fold results back (plan G): promote proven STT forms to lexicon aliases (internal), add each captured transcript as a golden row, and record negative findings (e.g. "recognizer never emits `ciento y`").

**Caveats to state in the fixtures README:** TTS speech exercises the recognizer's text *formatting* (digits vs words, accents, punctuation, decimal marks) and not human acoustic variation, homophone confusions, or accents; label every row `synthetic`. If a window is not granted before the phase must close, ship with the golden table as the conservative default (strict table rejects what it cannot verify, so a late finding only ever widens acceptance) and record the gap as a deferred obligation for Gate-2; this is safe because the lexicon is internal. If the stt demo cannot be built (cross-repo build), fall back to a throwaway debug-only harness outside the published modules, or to the golden-table-only path above. Nothing here touches the personal phone.

**RAE verification (the other half of D-12):** the RAE pages themselves returned HTTP 403 to automated fetch this session; the points below come from search-result summaries of rae.es/dpd/cardinales and related RAE pages and are tagged MEDIUM. `diez y seis`, `diez y nueve`, `veinte y uno` are described as outdated/archaic; 16..29 are one word (`dieciséis`, `veintinueve`); from 30 the conjunction `y` joins tens and units (`treinta y uno`); `treintaicinco`-style one-word spellings are valid; `ciento` apocopates to `cien` before the noun and before `mil` (`cien mil`) but stays `ciento` in `ciento uno`, `mil ciento dieciséis`; `veintiún mil`/`veintiuna mil` and `treinta y un mil`/`treinta y una mil` are both accepted; `un mil` is not used. [CITED: https://www.rae.es/dpd/cardinales via search summary; https://www.rae.es/ortograf%C3%ADa/ortograf%C3%ADa-de-los-numerales-cardinales via search summary] "`ciento y`" is not stated verbatim in the summaries; its rejection rests on the summaries' examples (no `y` after `ciento`) plus FEATURES (A6). A human with RAE access should skim the DPD entries `cardinales`, `cien`, `ciento`, `mil` once before the lexicon is declared frozen.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Second write path for grammar | Own `session.submit` loop | `submitSteps` (moved from SingleShot) | Gate/ledger/sink semantics and "held ≠ success" are subtle and already tested |
| Unicode composition | char-level combining-mark handling | `java.text.Normalizer` NFC + an explicit vowel map | NFD-and-strip corrupts `ñ` |
| Case folding | `toLowerCase()` or locale-dependent code | `String.lowercase()` | Locale-invariant by definition |
| App-hook fault containment | `try/catch (e: Exception)` | `guardedPlain` | The repo's only sanctioned collapse point; detekt `TooGenericExceptionCaught` otherwise |
| Argument validation | `if (!ok) throw IllegalArgumentException(...)` | `require(...)` | detekt `UseRequire` |
| JSON slot values | custom value classes | `JsonPrimitive(Long/Double/String)` in a `JsonObject` | Same representation as `Extraction.arguments`/`TerminalCall.arguments`; map equality gives cross-language agreement for free |
| Fuzzy/regex/edit-distance matching | any of them | the anchored enumerate-all-parses matcher | GRAM-03: a grammar match writes with no model in the loop |
| Zero-network proof | ad-hoc socket mocks | `NoNetworkGuard.during { }` + `FakeAiProvider` call count | Existing, with documented limits |
| Next-tier / carry assertions | custom ladders | `ScriptedStrategy.receivedCarries`, `TierPolicyTest.ladder` helpers | Same patterns as `TierPolicyTest` |
| Number words for 0..999,999 | ICU or a lenient parser | strict own tables + independent speller test | Lenient parsers silently accept `cien cinco` |

**Key insight:** the novelty is not plumbing but refusal. Every convenience that raises hit rate (prefix match, first-rule-wins, lenient numbers, normalize returning a best guess) converts a free cloud round trip into a silent wrong write.

## Runtime State Inventory

Omitted: Phase 14 is additive (new tier, new DSL, one internal function move). No rename, no stored data, no live service config, no OS registration, no secret or env var, no build artifact carries a changed name. The D-12 capture installs the stt demo debug APK on the TESTER; cleanup (uninstall, remove pushed WAVs/logs, undo any battery-optimization whitelist) must leave the device as found.

## Common Pitfalls

### Pitfall 1: The D-01 `prepareGuarded` clause is vacuous; do not "complete" it by touching `agentic/`
**What goes wrong:** An executor reads "move `prepareGuarded` too" and refactors `AgenticDispatch.kt`, colliding with Phase 15 and risking Agentic regressions.
**Why:** `submitAll` calls no guarded-prepare code; `guardWrites`/`prepare` are Agentic's and take `DispatchContext`.
**Avoid:** commit 1 moves exactly `resolutionOutcome` and `submitAll` (→ `submitSteps`). Record the finding in the summary for Phase 15.
**Warning signs:** a diff under `core/.../strategy/agentic/` in a Phase 14 commit.

### Pitfall 2: The repo's mechanical gates reject natural implementations
**What goes wrong:** (a) `private const val MAX_UTTERANCE_TOKENS`/`DEFAULT_*`/`*_CEILING` in main fails `limitConstantsAreDeclaredOnlyByTheirOwners`; (b) a public `sealed` slot hierarchy or an `enum` for slot kinds fails `review-api-surface.sh`/`noMainClassIsAnEnum`; (c) `data class GrammarMatch` fails `noMainClassIsDataShaped`; (d) a public `const val` is a public static field; (e) a public constructor with a defaulted parameter fails the stub rule; (f) a new `TraceCode` without a `TraceTest` row fails `declaredWireValues`.
**Avoid:** derived caps (no limit constant), builder-method slot kinds (internal sealed for representation), final classes with explicit `toString`, `private const`, explicit overloads, and update `TraceTest` in the same commit as the codes. Name any build-time bound without the banned tokens and justify it in a comment.
**Warning signs:** red `:core:test` on `NoHardCodedConstantsTest`/`ApiShapeTest`/`TraceTest` right after a green feature test.

### Pitfall 3: detekt's defaults bite lexicon and matcher code
**What goes wrong:** number tables trip `MagicNumber` (only `-1,0,1,2` ignored; constants and companion properties are exempt); matcher functions trip `ReturnCount` (max 2, guard clauses count), `CyclomaticComplexMethod` (15), `NestedBlockDepth` (4), `LongMethod` (60); files with >11 functions (private included) trip `TooManyFunctions`.
**Avoid:** table-driven lexicons by list index (0..29) plus a few `private const val`s for `HUNDRED`/`THOUSAND`/`TEN`; or, if cleaner, one justified `MagicNumber` exclude for `**/grammar/number/**` in `config/detekt/detekt.yml` ("lexicon tables are data"), which is the sanctioned "tune, don't bank" route and not a baseline. Split by responsibility (the structure above); express branches as `when` expressions and small functions; no baseline XML, ever (`verifyNoDetektBaseline`).
**Warning signs:** `./gradlew :core:detekt` reports on a file you consider "just data".

### Pitfall 4: Silent semantic loss in tokenization
**What goes wrong:** splitting every hyphen turns `-5` into `5` and `5-10` into two numbers; stripping all punctuation turns `5%` into `5`; dropping a standalone `,`/`.` token merges clauses ("add milk. delete everything").
**Avoid:** hyphen split only between letters; strip only the listed edge punctuation; clause terminators between words → no match (6a). Add each as a near-miss test.

### Pitfall 5: Ambiguity hidden by adjacency or by `and`/`y`
**What goes wrong:** a template `{count} and {label}` (ES `{count} y {label}`) legitimately parses "add two and a half apples" two ways; the self-check using digit examples cannot foresee it, so it surfaces at runtime as `NoMatch`.
**Avoid:** document that literals `and`/`y` next to number slots are ambiguous with fraction tails; the runtime rule (≥2 distinct results → NoMatch) is the safety net; add one near-miss row.

### Pitfall 6: `null` label, cross-language words, and normalize rejection
**What goes wrong:** with `language == null` both packs run; a word valid in both (`once`, `no`) can produce two different results; or one pack's `normalize` rejects while the other accepts and the tier returns the survivor (a guess).
**Avoid:** agreement on the final `(tool, terminal, slots)`; any rejection among candidates → NoMatch (precision over recall); test table covering null/en/es/other labels × `tryOtherLanguage`.

### Pitfall 7: Zero-call tier accidentally touching the model or the token ceiling
**What goes wrong:** calling `session.model()` (even to check a refusal) creates a binding attempt and turns, or calling `ceilingReached` fails the tier when the policy ceiling is 0.
**Avoid:** the strategy never references `model()`, `recordTurn`, or the ceiling helpers; assert `TierAttempt.turns.isEmpty()`, `provider == null`, `FakeAiProvider` call count 0.

### Pitfall 8: Redaction regressions
**What goes wrong:** `GrammarMatch.toString`, an `IllegalArgumentException` message, a trace code or a `PipelineEvent` carries transcript text or slot values; `normalize` receives raw text and an exception from it mentions it.
**Avoid:** `toString` prints tool name, language, argument count and rule id only; IAE messages built from authoring data (template text, slot names), never from `match` inputs; hook exceptions recorded by class name only (the `guardedPlain` fault already carries only `errorClass`); extend `RedactionCanaryTest` with a canary utterance through the grammar path (match, normalize-throw, resolver-reject, held) and scan trace, listener events, outcome `toString`, and exception messages. [VERIFIED: existing `RedactionCanaryTest` and `EngineFault` carry class names only, Guarded.kt:15-30]

### Pitfall 9: Number-representation drift between languages
**What goes wrong:** EN yields `JsonPrimitive(2)`, ES yields `JsonPrimitive(2.0)` for the "same" value, so cross-language agreement fails (`JsonLiteral` equality is by content string).
**Avoid:** integer slots always `Long`, decimal slots always `Double` produced by one shared function; test that EN and ES spellings of the same value produce `==` argument objects (SC-1).

### Pitfall 10: The window never arrives
**What goes wrong:** the phase blocks on the TESTER relay.
**Avoid:** request at phase start; make the capture plan non-blocking for every other plan (it only widens aliases); define the explicit fallback (A12) in the plan so the verifier can close the phase with a recorded deferred obligation.

### Pitfall 11: Host memory
**What goes wrong:** two heavy Gradle runs plus other projects' daemons with swap full (2047/2047 MB used at research time, 2.2 GB free) get killed by earlyoom; a kill can also take another project's daemon.
**Avoid:** ≤2 Gradle-running plans per wave with the recipe in "Project Constraints"; run quick targeted tests (`--tests`), reserve full `check` for the phase gate; if killed, stop and report, do not loop.

## Code Examples

Shapes only (new names are proposals, flagged "new"); the in-repo constants referenced are the quoted ones above.

### Tokenizer with offsets (new, internal)

```kotlin
internal class GrammarToken(val key: String, val start: Int, val end: Int)   // key = folded; [start,end) into the NFC text

internal class GrammarTokens(val nfc: String, val tokens: List<GrammarToken>) {
    /** Original surface text of tokens [from, to). Case, accents and interior punctuation preserved. */
    fun surface(from: Int, to: Int): String = nfc.substring(tokens[from].start, tokens[to - 1].end)
}
// fold(): Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(), explicit vowel map, apostrophes dropped, ñ kept.
```

### Zero-call + held proof (test pattern, from `TierPolicyTest`/`SingleShotTerminalTest`)

```kotlin
@Test fun matchingTranscriptCompletesWithZeroProviderCalls() = runTest {
    val fake = FakeAiProvider(ProviderId.ANTHROPIC /* + a scripted result that must never be asked for */)
    val outcome = NoNetworkGuard.during { pipelineOf(listOf(grammarTier), fake, ScriptedGate.admitAll(), sink).execute(CommandInput("add two items", "en")) }
    assertEquals(0, fake.callCount)
    val action = (outcome as CommandOutcome.Completed).executed.single()
    assertNull(action.providerCallId)
}
```
(`FakeAiProvider.callCount: Int` and `.calls: List<ProviderRequest>` are public members. [VERIFIED: core/src/testFixtures/.../FakeAiProvider.kt:51,55: `public val calls: List<ProviderRequest>`, `public val callCount: Int`])

### Offline-only no-match (SC-5; mirrors `TierPolicyTest.offlineOnlyDroppingACloudTierThenNoHandlerIsCapped`)

```kotlin
ladder(grammarTier /* real LocalGrammarStrategy */, tier("cloud", caps(ProviderId.ANTHROPIC)),
       policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }))
    .execute(CommandInput("something no rule matches")).let { assertTrue((it as CommandOutcome.Unhandled).cappedByPolicy) }
```

### Proposed new trace codes (new; wire values lower snake case, additive, `TraceTest` rows required)

`GRAMMAR_AMBIGUOUS` = `grammar_ambiguous`; `GRAMMAR_LANGUAGE_UNSUPPORTED` = `grammar_language_unsupported`; `GRAMMAR_SLOT_REJECTED` = `grammar_slot_rejected` (normalize null/blank); `GRAMMAR_NORMALIZE_ERROR` = `grammar_normalize_error` (hook threw); `GRAMMAR_INPUT_TOO_LONG` = `grammar_input_too_long`; `GRAMMAR_RESOLVER_REJECTED` = `grammar_resolver_rejected` (resolver returned `Resolution.NoMatch`). A plain "no rule matched" needs no code: the tier attempt already reads `no_match`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Regex or fuzzy keyword intent matching | Template grammars: `(a\|b)`, `[optional]`, `{slot}`, `<rule>` compiled once, anchored whole-sentence match (Rhasspy/voice2json JSGF-derived, Home Assistant hassil) | established | Deterministic, offline, testable; matches D-03's syntax. hassil's own docs list exactly these constructs and `{0..100:slot}` ranges [CITED: https://github.com/OHF-Voice/hassil] |
| Global number-word → digit rewrite before matching | Slot-level numeric span parsing | n/a | Rewrite corrupts literals ("un", "a", "the one") and the raw text `normalize` needs |
| `16 = "diez y seis"`, `21 = "veinte y uno"` | one-word `dieciséis`, `veintiuno`; `treinta y uno` from 31 | RAE norm | Keep the archaic forms only as STT aliases [CITED: rae.es via search summary] |

**Deprecated/outdated:** `EncryptedSharedPreferences`-style concerns do not apply. Platform recognizer behavior (digits vs words) is device/version dependent; treat D-12 fixtures as dated evidence.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The TESTER still has en-US and es-US Google TTS voices and the stt-engine demo debug build still installs and runs file-fed WAV (evidence is dated 2026-08, not re-probed; no device command was run this session because no window grant exists) | D-12 | Capture plan needs a different stimulus path or falls back to the golden-table-only path |
| A2 | TTS-synthesized stimuli reproduce the recognizer's number/accent/punctuation *formatting* faithfully enough to freeze aliases | D-12 | Aliases tuned on synthetic output may miss human-speech forms; mitigated by `synthetic` provenance and conservative defaults |
| A3 | A bare `a`/`an` (EN) and `un`/`una` (ES) are NOT numbers (only inside `a hundred`, `a half`, `veintiún`, `treinta y una`, …), a deliberate deviation from the FEATURES "accept one/a/an" row | Number tables | Lower hit rate on "add a milk"/"agrega una …" (cost: one cloud call); the opposite error would be a wrong write. Confirm with SB/CT |
| A4 | `normalize` ships as the plain function type `(String, String) -> String?`, not a named `fun interface` | DSL | Arity frozen at 2 (a `fun interface` freezes it equally); revisit only if the orchestrator wants a named type |
| A5 | `matchedLanguage` is the labeled language (or `null`) when both packs agree on a null/neutral match; `ruleId` is null then | Matching | SB SC-3/D-06 may need a definite language; alternative is to reject cross-language agreement (lower recall) |
| A6 | `ciento y …` is not accepted (rests on RAE summary examples lacking `y` after `ciento` plus FEATURES; RAE pages not fetchable) | Number tables | Real STT may emit it; a pack alias can widen later (lexicon is internal) |
| A7 | ES `.` followed by exactly 3 digits (single dot) is ambiguous and rejected; `,`+3 digits in ES is a decimal | Digit grouping | May reject a real ES thousands token; fixtures decide |
| A8 | `veinte y uno … veinte y nueve` and `treintaicinco`-style tokens accepted as aliases | Number tables | Over-acceptance of an archaic form (harmless; values unambiguous) |
| A9 | `<11..99> hundred` accepted only without a thousand group (`fifteen hundred`) | Number tables | Rare colloquial form; trivially dropped |
| A10 | Build-time expansion bound ~256 flat sequences per template and ≤4 slots per template are adequate | DSL | Packs may need more; relaxing is additive |
| A11 | Rejecting an interior sentence terminator (`. ! ? ;`) between words is acceptable hardening beyond literal D-11 | Text normalization | Slightly lower recall on punctuated STT output; strictly safer |
| A12 | If no TESTER window is relayed in time, closing Phase 14 on the golden table with a recorded deferred obligation is acceptable | D-12 | Verifier/orchestrator may require the capture; confirm the fallback with the orchestrator at plan time |
| A13 | `metalavaCheckCompatibility` against the v1.0.1 `api.txt` stays green (all additions are additive) — not run this session (host memory) | Gates | An accidental signature change on `Extraction` would fail it; run `:core:metalavaCheckCompatibility` per wave |

## Open Questions

1. **Does D-09's "text/list slots" mean closed-choice slots?**
   - Known: D-07 lists integer, decimal, closed list with per-language synonyms, bounded text; D-09 says "Text/list slots only"; CT's use is food-style tokens (text).
   - Unclear: whether the hook should also run on choice slots.
   - Recommendation: run it on `text` and `choice`; reject on number slots at build time. Ask CT in the plan checkpoint only if cheap; otherwise ship and document.
2. **`matchedLanguage`/`ruleId` when both packs agree** (A5)
   - Recommendation as in A5; confirm with SB (SC-3/D-06) through the orchestrator before the freeze review.
3. **`ruleId` shape**
   - Recommendation: opaque string, stable for a given pack declaration order, null on cross-language agreement; documented as opaque so its format can change. Alternative: expose `intent` (the tool name) only and drop `ruleId`.
4. **Resolver requirement when every intent is `terminal`**
   - Recommendation: resolver is required unless the pack has only terminal intents (build-time IAE naming the setting otherwise).
5. **Window grant timing and who requests it** (A12)
   - Recommendation: the driver relays the request to the orchestrator in the Phase 13 five-line form at phase start; record the answer in `14-WINDOW-GRANT.md`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle build | yes | OpenJDK 17.0.19 | none needed |
| Gradle wrapper | all tests | yes (not run this session) | 9.4.1 per CLAUDE.md | recipe with `workers.max=2`, no daemon |
| Host memory | Gradle plans | tight | 32 GB total, ~2.2 GB free, ~11.8 GB available, swap 2047/2047 MB used | ≤2 Gradle plans per wave; ask Yahir for a swap reset before any full `check` if earlyoom fires |
| Python 3 + numpy | D-12 WAV stitching (stt scripts) | yes | 3.12.3 / 1.26.4 | none needed |
| adb | D-12 | yes | 1.0.41 | none |
| TESTER `R5CT10XNKQN` | D-12 | not probed (no window grant; device rules forbid touching it without one) | — | golden-table-only path (A12) |
| Host TTS/audio tools (`espeak`, `ffmpeg`, `sox`, `pico2wave`) | D-12 stimulus | no | — | on-device `TextToSpeech` (as stt-engine does) |
| stt-engine demo harness | D-12 | repo present at `~/Projects/Reusable/stt-engine/android` (builds not tried) | — | throwaway debug harness outside published modules |
| Web access to rae.es | D-12 RAE check | no (403 to fetch) | search summaries only | human skim of DPD entries `cardinales`, `cien`, `ciento`, `mil` |

**Missing with no fallback:** none. **Missing with fallback:** TESTER window (A12), host TTS (on-device TTS), rae.es fetch (summaries + human check).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0; hand-written fakes in `core/src/testFixtures` |
| Config file | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; `gradle/invariants.gradle.kts` (scanner); `scripts/verify-docs-coverage.sh` |
| Quick run command | `./gradlew :core:test --tests '<Class>' --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false` |
| Full suite command | `./gradlew check --offline -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false` (detekt, scanner, Metalava compat, `ApiShapeTest`, OkHttp matrix legs) then `scripts/verify-docs-coverage.sh` and `scripts/review-api-surface.sh` |

(Use the host-safe `GRADLE_OPTS` recipe from "Project Constraints"; capture the real exit status.)

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GRAM-01 | Matching EN transcript completes, zero provider calls (`FakeAiProvider` never asked, `NoNetworkGuard`), `TierAttempt.turns` empty, `provider == null`, write goes through gate→commit→sink, `ExecutedAction.providerCallId == null` | pipeline | `./gradlew :core:test --tests '*LocalGrammarPipelineTest*'` | ❌ Wave 0 |
| GRAM-01 | Held grammar action: `result.held`, `commits` empty, action kind HELD, never success; resolver `Steps` reply kept for held, withheld on errored apply | pipeline | `--tests '*LocalGrammarHeldTest*'` | ❌ Wave 0 |
| GRAM-01 | D-01 move is behavior-neutral: SingleShot suite unchanged | unit | `--tests '*SingleShotResolveTest*' --tests '*SingleShotOutcomeMappingTest*' --tests '*SingleShotPlumbingTest*' --tests '*CommitPathTest*' --tests '*HeldReportingTest*'` | ✅ |
| GRAM-02 | EN and ES phrasing of one intent, each with a spoken number, yield equal `toolName` and equal `arguments` | unit | `--tests '*GrammarBilingualTest*'` | ❌ Wave 0 |
| GRAM-02 | EBNF accept/reject (`[ ]`, `( \| )`, `{slot}`, `<rule>`, empty alt, cycle, unknown slot) | unit | `--tests '*GrammarTemplateTest*'` | ❌ Wave 0 |
| GRAM-02 | Build-time `IllegalArgumentException` table (duplicates, ambiguity self-check, unknown slot, unbounded text, adjacent open slots, required-slot parity, expansion bound, normalize on number slot) | unit | `--tests '*GrammarPackValidationTest*'` | ❌ Wave 0 |
| GRAM-02 | Number tables: round-trip 0..999,999 EN+ES, hand accept/reject, digit-grouping matrix, partial-span null | unit | `--tests '*NumberRoundTripTest*' --tests '*NumberGoldenEnTest*' --tests '*NumberGoldenEsTest*' --tests '*DigitGroupingTest*'` | ❌ Wave 0 |
| GRAM-02 | Text folding: NFC, `ñ` kept, `año`≠`ano`, hyphen rules (`5-10`, `-5`), punctuation, offsets, `Locale`-independence | unit | `--tests '*GrammarTextTest*'` | ❌ Wave 0 |
| GRAM-03 | Near-miss corpus all NoMatch (negation, extra clause, wrong-language word, tilde word, partial number, mixed-language, trailing clause, `5%`, `add milk. delete everything`) + positive corpus all match | unit | `--tests '*GrammarNearMissCorpusTest*'` | ❌ Wave 0 |
| GRAM-03 | Ambiguity: two distinct results → NoMatch + code; identical results from two rules/packs → one match | unit | `--tests '*GrammarAmbiguityTest*'` | ❌ Wave 0 |
| GRAM-03 | Label matrix (en/es/null/other × `tryOtherLanguage`); null label tries both (SB binding) | unit | `--tests '*GrammarLanguageLabelTest*'` | ❌ Wave 0 |
| GRAM-03 | Resolver `NoMatch` → `NoMatch` + `GRAMMAR_RESOLVER_REJECTED`, next tier sees `carry == null` (`ScriptedStrategy.receivedCarries`); resolver `Escalate` keeps carry; `Failed` stops; resolver throw → `strategy_error` + `Failed`; NoMatch after a commit is suppressed | pipeline | `--tests '*LocalGrammarVerdictsTest*'` | ❌ Wave 0 |
| GRAM-03 | Never best-guess: no score/threshold APIs; property: random mutations of valid utterances never match with a different result than the unmutated rule | unit | `--tests '*GrammarNeverGuessesTest*'` | ❌ Wave 0 |
| GRAM-04 | `normalize` sees raw surface text + matched language (never null); null/blank → NoMatch + `GRAMMAR_SLOT_REJECTED`; throw → NoMatch + `GRAMMAR_NORMALIZE_ERROR` (not `strategy_error`); changes what resolves; runs once per candidate pack; cross-pack agreement after normalize | unit | `--tests '*GrammarNormalizeTest*'` | ❌ Wave 0 |
| GRAM-04 | CLN-02 scan stays green; no domain word in main/testFixtures | gate | `./gradlew :core:scanBannedConstructs :core:detekt` | ✅ |
| GRAM-05 | `capabilities == NO_PROVIDER`; runs under `offlineOnly` and `allowedProviders = {X}`; offline-only no-match → `Unhandled(cappedByPolicy = true)` after zero provider calls; terminal intent works offline-only (SB binding) | pipeline | `--tests '*LocalGrammarPolicyTest*'` | ❌ Wave 0 |
| (D-05) | `Extraction.matchedLanguage` populated for grammar, null for SingleShot; 2- and 3-arg ctors still public; `toString` unchanged | unit | `--tests '*ApiShapeTest*' --tests '*SingleShotResolveTest*'` | extend existing |
| (security) | Redaction canary through grammar paths; `GrammarMatch`/pack/strategy `toString` leak nothing | unit | `--tests '*RedactionCanaryTest*' --tests '*GrammarRedactionTest*'` | extend + new |
| (D-12) | STT fixture rows parse/match as recorded | unit | `--tests '*GrammarSttFixturesTest*'` | ❌ after capture |
| (surface) | New public top-level types named in `API.md`; no sealed/enum/data/static leak | gate | `scripts/verify-docs-coverage.sh --only C20,C21`; `scripts/review-api-surface.sh`; `./gradlew :core:test --tests '*ApiShapeTest*'`; `:core:metalavaCheckCompatibility` | ✅ scripts |

### Sampling Rate

- **Per task commit:** the single touched test class (command above), under the memory-safe recipe.
- **Per wave merge:** `./gradlew :core:test :core:detekt :core:scanBannedConstructs` (one Gradle run) plus `:core:metalavaCheckCompatibility` when public API changed.
- **Phase gate:** full `./gradlew check` green (matrix legs included), `scripts/verify-docs-coverage.sh` OK, `scripts/review-api-surface.sh` OK, before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `core/src/test/.../Grammar*Test.kt`, `Number*Test.kt`, `LocalGrammar*Test.kt` (names above) covering GRAM-01..05
- [ ] Test-only independent speller (EN/ES) and a shared neutral example pack in `src/test` (no domain words)
- [ ] `TraceTest` rows for the six new codes; `ApiShapeTest` `Extraction` ctor-list assertion for the internal 4-arg shape
- [ ] `14-WINDOW-GRANT.md` (grant `pending`) and the D-12 prompt list; `GrammarSttFixturesTest` is blocked until capture
- [ ] No framework install needed (existing infrastructure covers everything)

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no (no new privileged seam; `normalize` and the resolver are app code in-process) | — |
| V5 Input Validation | yes (the transcript is untrusted STT/user text that drives a write with no model in the loop) | anchored whole-utterance matcher, derived word cap, bounded slot spans, strict whole-span number parse, IAE at pack build, no regex from input |
| V6 Cryptography | no | — |
| V7 Error Handling and Logging | yes (transcripts/slot values are user speech) | redacted `toString`s, trace codes only, exceptions by class name (`guardedPlain`/`EngineFault`), `RedactionCanaryTest` extension |
| V8/V9 Data protection / comms | D-12 only | synthetic stimuli (no PII), TESTER only, no keys, evidence filter, full device cleanup |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Crafted utterance causes a wrong write (extra clause, negation, prefix match, text slot swallowing words) | Tampering | Whole-utterance anchor; distinct-result ambiguity = NoMatch; bounded text slots; clause-terminator rejection; near-miss corpus; the app gate still decides every write |
| Catastrophic backtracking / long dictated paragraph hangs the caller | Denial of service | No regex; derived word cap; ≤4 slots/rule; ≤12 candidate lengths per number slot |
| Wrong-language label or `null` flips meaning (`once`, `1.000`) | Tampering | Strict label table; both-pack agreement; language-resolved digit grouping with ambiguity rejected |
| Transcript or slot text leaks via `toString`, exception message, trace, event | Information disclosure | Counts/codes/ids only; canary tests |
| App `normalize`/resolver misbehavior (throw, block, wrong value) | Denial of service / tampering | `guardedPlain` for the hook; resolver faults reach `TierWalk`'s guard; null = reject; hook documented non-blocking |
| Device capture leaves artifacts or touches the wrong phone | Information disclosure / tampering | Grant-gated exact-line control, USB serial pin, no other device, cleanup proof, evidence filter |
| Supply chain | — | No new dependency (nothing to audit) |

## Sources

### Primary (HIGH confidence)
- Repository at HEAD `73b5a07` read this session: `core/.../strategy/{CommandSession,CommandStrategy,OutcomeResolver,StrategyCapabilities,StrategyOutcome,TerminalCall,StrategyLimits,ToolSpec}.kt`, `strategy/singleshot/{SingleShotStrategy,SingleShotOutcomes}.kt`, `strategy/agentic/AgenticDispatch.kt`, `pipeline/{TierWalk,PolicyPreCheck,RunSession,CommandOutcome}.kt`, `commit/{CommitCoordinator,ToolStep,ApplyStep,CommitSink}.kt`, `telemetry/{TraceCode,CommandTrace}.kt`, `internal/Guarded.kt`, `CommandInput.kt`, `StrategyId.kt`; `core/api.txt` (v1.0.1); tests `ApiShapeTest`, `NoHardCodedConstantsTest`, `TraceTest`, `TierPolicyTest`, `SingleShotTerminalTest`, `SingleShotTestSupport`; testFixtures `NoNetworkGuard`, `ScriptedStrategy`; `gradle/invariants.gradle.kts`, `config/detekt/detekt.yml`, `core/build.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`; `scripts/{verify-docs-coverage,review-api-surface,verify-api-dump}.sh`.
- `detekt-core-1.23.8.jar` `default-detekt-config.yml` (extracted this session): defaults quoted in "Verified In-Repo Facts".
- `.planning/` : `14-CONTEXT.md`, `REQUIREMENTS.md`, `STATE.md`, `ROADMAP.md`, `v1.1-DECISION-MAP.md`, `cross-repo/R-v1.1-CONSUMER-ANSWERS.md`, `research/{ARCHITECTURE,PITFALLS,FEATURES}.md`, `phases/12-*/{12-RESEARCH,12-PATTERNS,12-VALIDATION,12-LIVE-LEG-DECISION}.md`, `phases/13-*/{13-WINDOW-GRANT,13-07-SUMMARY,13-RESEARCH}.md`.
- stt-engine local evidence: `05-RUNG-VERDICT.md`, `05-PROBE-REPORT.json` (`fileFedAudio`), `android/scripts/{run-and02-uat.sh,build-bilingual-stimulus.py}`, `android/demo/src/debug/.../DemoControlReceiver.kt`, `android/stt/src/androidTest/.../{BilingualStimulus,BilingualStimulusGeneratorTest,RecognizerBehaviourProbeTest}.kt`.
- Host probes this session: `java -version`, `python3`/numpy, `free -m`, `command -v adb espeak-ng espeak ffmpeg sox pico2wave`.
- `~/.claude/context/devices/{common,test-android,linux-host}.md`, `~/.claude/context/workflows/two-gate-uat.md`, memory `vae-release-cut-host-oom.md`.

### Secondary (MEDIUM confidence)
- RAE (via search-result summaries; rae.es returned HTTP 403 to fetch): https://www.rae.es/dpd/cardinales , https://www.rae.es/ortograf%C3%ADa/ortograf%C3%ADa-de-los-numerales-cardinales , https://www.rae.es/buen-uso-espa%C3%B1ol/los-numerales-los-cardinales
- hassil template syntax (fetched): https://github.com/OHF-Voice/hassil
- `.planning/research/FEATURES.md` number-word spec (domain knowledge, MEDIUM by its own label)

### Tertiary (LOW confidence)
- Real S22 recognizer output forms for ES/EN numbers: unmeasured until D-12; the tables' acceptance of `treinta uno`, `ciento y`, `5 mil`, `.`-grouped ES digits is a default-reject pending fixtures.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependency; versions read from the catalog.
- Architecture / engine integration: HIGH — every touched seam read at HEAD, gates enumerated from their source.
- DSL and matching design: HIGH as a design; the exact public names are the planner's to finalize within the invariants.
- ES/EN number tables: MEDIUM — RAE only via summaries; real STT forms pending D-12.
- Pitfalls: HIGH for the repo-gate pitfalls (verified in source), MEDIUM for STT-behavior pitfalls.

**Research date:** 2026-10-06
**Valid until:** 2026-11-05 (30 days; sooner if Phase 15/16 land edits to `TraceCode.kt`, `TraceTest.kt` or `OutcomeResolver.kt`, which share hot files with this phase)
