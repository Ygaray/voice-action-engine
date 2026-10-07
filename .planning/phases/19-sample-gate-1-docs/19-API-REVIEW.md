# Phase 19 consolidated frozen-surface review (D-12; one-way at the v1.1.0 tag)

Written by plan 19-08 after RT-04 (plan 19-03) and the TESTER device window (plan 19-07), and before the docs freeze
(plans 09 and 10) and the wiring SHA. Everything v1.1 adds, across all five published modules, in one place. This
supersedes the per-phase reviews' overload counts (see "Frozen names"). No library source, `api.txt` or doc was changed
to produce it.

## Inputs

- Code HEAD at dump time: `673eee00e1c371254d563bdccd67c4616118f52f` (no library-module file differs from HEAD; the only
  uncommitted change at that moment was `scripts/review-api-surface.sh`, committed afterwards as `ce7e457`).
- Dump command: `scripts/api-dump-isolated.sh --out /tmp/vae-19-dump` (isolated copy of the working tree, one Gradle
  invocation, low-memory recipe, single-use daemon). The directory is outside the repository and is not committed.
  Result line: `API DUMP ISOLATED OK out=/tmp/vae-19-dump core=1956 providers=121 keystore=76 undo=191 voice-adapter=16`.
- Line counts: core 1956, providers 121, keystore 76, undo 191, voice-adapter 16.
- Committed baselines: `core/api.txt` 1698 lines, `providers/api.txt` 121, `keystore/api.txt` 67 (all identical to the
  `v1.0.1` tag: `git diff v1.0.1 HEAD -- core/api.txt providers/api.txt keystore/api.txt` is empty). `undo/api.txt` and
  `voice-adapter/api.txt` are the header-only seeds (1 line each).
- The five review lines, verbatim (`scripts/review-api-surface.sh --module <m> --dump /tmp/vae-19-dump/<m>.api.sig`):

```
API SURFACE OK module=core sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=207
API SURFACE OK module=providers sealed=none classes=14
API SURFACE OK module=keystore sealed=none classes=10
API SURFACE OK module=undo sealed=UndoResult classes=19
API SURFACE OK module=voice-adapter sealed=none classes=2
```

- Non-vacuity of the review (run in this plan): a copy of the voice-adapter dump with a planted
  `public enum Planted` exits 1 (`enum declared`); with a planted `public sealed class PlantedSealed` exits 1
  (`outside the voice-adapter allow-list (none)`); the undo dump reviewed as `--module core` exits 1 (`lacks the core
  package`). `git diff --exit-code -- core/api.txt providers/api.txt keystore/api.txt undo/api.txt voice-adapter/api.txt`
  exits 0.

## Per-module results

| Module | Sealed set | Data-shape (copy/componentN) | Enum | Public static fields | Removed vs committed api.txt | Added vs committed api.txt |
|---|---|---|---|---|---|---|
| core | the seven allowed (StrategyOutcome, CommandOutcome, RunTermination, GateDecision, ToolStep, Message, AssistantPart), no other | none | none | only `Companion`/`INSTANCE` | **0** | 258 lines |
| providers | none | none | none | none | **0** | 0 (dump is byte-equal to the baseline) |
| keystore | none | none | none | none | **0** | 9 lines (`DelicateKeyAccess`, `KeyAccess`, the opt-in `ApiKeyStore` constructor) |
| undo | exactly `undo.UndoResult` | none | none | none | n/a (header-only seed, new module) | 190 lines (new module) |
| voice-adapter | none | none | none | none | n/a (header-only seed, new module) | 15 lines (new module) |

Removal counts are `diff <committed api.txt> <dump> | grep -c '^<'`: core 0, providers 0, keystore 0. Every v1.1 change to
the three released modules is additive, so the `--check-compatibility:api:released` gate at Phase 20 has nothing to
refuse. Observation, not a finding: the dump renders the `kotlin.time.Clock` builder property as `ErrorType clock` (seven
occurrences, six already in the committed baseline; the seventh is `PlanThenExecuteStrategy.Builder.clock`). This is
Metalava failing to resolve the type and is the same shape the baseline already freezes; the getter line reads `Clock`.

## Frozen names

These freeze at v1.1.0 and are additive-only afterwards (section 11). Names below are those new since the committed
baselines (v1.0.1). This list supersedes the overload counts in `18-SURFACE-REVIEW.md` (three `toCommandInput` and three
`commandInputOf` before RT-04; the dump now holds two and two).

### :core

- **Grammar tier** (`core.strategy.grammar`): `LocalGrammarStrategy` (+ `Builder` { `pack`, `resolver` }, `Companion.invoke`),
  `GrammarPack` (+ `Builder` { `enRule`, `esRule`, `enFillers`, `esFillers`, `intent`, `tryOtherLanguage` },
  `IntentBuilder` { `en`, `es`, `terminal`, `text`, `integer`, `decimal`, `choice`, `normalize` }, `ChoiceBuilder.option`,
  `OptionBuilder` { `en`, `es` }, `Companion.invoke`, `match`), `GrammarMatch` { `toolName`, `arguments`, `ruleId`,
  `matchedLanguage`, `terminal` }; `Extraction.matchedLanguage`; trace codes `GRAMMAR_AMBIGUOUS`, `GRAMMAR_INPUT_TOO_LONG`,
  `GRAMMAR_LANGUAGE_UNSUPPORTED`, `GRAMMAR_NORMALIZE_ERROR`, `GRAMMAR_RESOLVER_REJECTED`, `GRAMMAR_SLOT_REJECTED`.
- **Plan tier** (`core.strategy.plan`): `PlanThenExecuteStrategy` (+ `Builder` { `capabilities`, `clock`, `executor`,
  `maxSteps`, `onFailed`, `reasoning`, `tooling`, `userTurn` }, `Companion.invoke`); `CommandOutcome.Completed.remainingStepIds`;
  trace codes `PLAN_BINDING_UNRESOLVED`, `PLAN_REJECTED`, `PLAN_REPLANNED`.
- **Router and picker** (`core.pipeline`, `core.telemetry`): `StartTierPicker` (fun interface), `PickContext`,
  `TierSelector.Custom` (+ `Builder`, `Companion.invoke`), `TierSelector.Router` (+ `Builder` { `capabilities`,
  `tierDescriptions` }, `Companion.invoke`), `TierPolicy.pickerTimeoutMillis` (+ builder var), `StartTierSelection`,
  `PipelineEvent.StartTierSelected`, `CommandTrace.selection`; trace code `ROUTER_FALLBACK`.
- **Phase 12 seams**: `onFailed` on the `SingleShotStrategy`, `AgenticLoopStrategy` and `PlanThenExecuteStrategy` builders;
  `ReasoningMode` (value class, `Companion` constants) with `ModelRequest.reasoning` (and its new constructor) and the
  `reasoning` builder var on SingleShot, AgenticLoop and PlanThenExecute; `ExecutedAction.providerCallId`;
  `Extraction.callId` (+ the three-argument constructor); `TierAttempt.carryIn`; `CommandOutcome.Unhandled.cappedByPolicy`.
- **Undo seam**: `compositeSink(vararg CommitSink)` (JVM facade `CompositeSinkKt`), `ActionEvent.heldRunId`.

### :providers

No change since v1.0.1 (dump byte-equal to the baseline).

### :keystore

`KeyAccess` (interface, opt-in), `DelicateKeyAccess` (opt-in marker, `RequiresOptIn` level ERROR, class and constructor
targets), and the `@DelicateKeyAccess` three-argument `ApiKeyStore(dataStore, slots, keyAccess)` constructor.

### :undo (new module, first tag v1.1.0)

`UndoJournal` (+ `Builder` { `adapter`, `compensator`, `maxGroups`, `maxAgeMillis`, `store`, `clock` }, `Companion`),
`UndoTicket`, `UndoGroup`, `UndoResult` (closed sealed set: `Complete`, `Refused`, `Partial`, `AlreadyUndone`),
`UndoReason` (open value class + `Companion`), `Blocker`, `NotRestored`, `EntityAdapter`, `EntityKey`, `EntryRef`,
`Compensator`, `JournalStore`. Full member list and the evolution notes (defaulted-method-only additions on the plain
interfaces) are in `17-SURFACE-REVIEW.md`; the dump is unchanged from that review.

### :voice-adapter (new module, first tag v1.1.0) after RT-04

Two facade classes and exactly the following, counted in the dump with `grep -o`:

| Name | Signatures in the dump |
|---|---|
| `toCommandInput` | 2: `(FinalSegment)` and `(FinalSegment, Object? context, String? parentRunId)` |
| `commandInputOf` | 2: `(String transcript, String? languageLabel)` and `(String, String?, Object? context, String? parentRunId)` |
| `normalizeSttLanguageLabel` | 1: `(String? raw): String?` |
| facades | `FinalSegmentCommandInput` (holds `toCommandInput`), `SttLanguageLabels` (holds `commandInputOf` and `normalizeSttLanguageLabel`) |

The context-only forms are gone, as RT-04 ruled. `io.github.ygaray.sttengine.FinalSegment` is in the frozen surface through
`toCommandInput`: a consumer calling it needs `:stt` on its own compile classpath (documented requirement, not a defect).

## Docs versus dump

Greps over the current API.md / INTEGRATION.md / README.md (the docs are rewritten by plans 09 and 10; this list is their
input, nothing is fixed here).

| New public name | Named today | Input for |
|---|---|---|
| `PipelineEvent.StartTierSelected` | **no** (neither API.md nor INTEGRATION.md; the `StartTierSelection` record it carries is named) | plan 09 (router block) / plan 10 (API.md) |
| `CompositeSinkKt` (JVM facade) | no, by design: Kotlin callers write `compositeSink`, which is named in both docs | none |
| `FinalSegmentCommandInput`, `SttLanguageLabels` (JVM facades) | no, by design (functions are named in both docs) | none; decide in plan 10 whether C20 wants the facade names |
| trace codes `GRAMMAR_*`, `PLAN_*` | named as wire strings (`grammar_ambiguous` ... `plan_replanned`) in API.md only; absent from INTEGRATION.md | plan 09 may cite them in the grammar and plan subsections |
| `ROUTER_FALLBACK` | `router_fallback` in API.md and INTEGRATION.md | none |
| `PickContext` | API.md only (INTEGRATION.md has no mention) | plan 09 router block, if it shows a picker body |
| `GrammarPack`, `GrammarMatch`, `LocalGrammarStrategy`, `PlanThenExecuteStrategy`, `ReasoningMode`, `providerCallId`, `carryIn`, `onFailed`, `remainingStepIds`, `cappedByPolicy`, `matchedLanguage` | API.md yes; INTEGRATION.md no (the grammar and plan subsections do not exist yet) | plan 09 (grammar and plan snippets and prose) |
| `TierSelector.Custom`, `TierSelector.Router`, `StartTierPicker`, `tierDescriptions`, `pickerTimeoutMillis`, `StartTierSelection`, `tiersBypassed`, `selection` | API.md and INTEGRATION.md | none |
| `heldRunId`, `compositeSink`, `UndoJournal`, `UndoResult`, `UndoReason`, `UndoGroup`, `UndoTicket`, `EntityAdapter`, `EntryRef`, `JournalStore` | API.md and INTEGRATION.md | none |
| `EntityKey`, `Compensator`, `Blocker`, `NotRestored` | API.md only | plan 09 undo-wiring region if it uses them |
| `KeyAccess`, `DelicateKeyAccess` | API.md and INTEGRATION.md | none |
| `toCommandInput`, `commandInputOf`, `normalizeSttLanguageLabel` | API.md and INTEGRATION.md (plan 03 already rewrote the two-form wording) | none |
| `:undo` and `:voice-adapter` coordinates | README and ECOSYSTEM are plan 10's C01 work | plan 10 |

## Carried open items

Dispositions: **closed** (with the evidence), **carried** (to the named place, with the reason), **orchestrator** (a
relayed answer is still owed before the cut; the shipped default is what freezes if none arrives). None of them is a
shape finding: every shipped default is additive-safe except where noted, and a changed default after the tag is a
behavior change, which is why they are listed here before the docs freeze.

### Phase 14 (`14-SURFACE-REVIEW.md`)

| Id | Disposition |
|---|---|
| OI-1 normalize on `text` and `choice` slots | orchestrator: relay with the cut; narrowing after the tag would break packs, so the shipped default (accepted on both) is what docs describe |
| OI-2 `matchedLanguage`/`ruleId` on cross-pack agreement | orchestrator: relay with the cut; docs describe the shipped default (command's own label else null) |
| OI-3 `ruleId` is opaque | orchestrator: relay with the cut; docs state "opaque, do not parse" |
| OI-4 resolver optional for all-terminal packs | orchestrator: relay with the cut; build-time error wording is already pinned by tests |
| OI-5 golden-table fallback without a TESTER window | closed: the D-12 window was granted and consumed (`14-WINDOW-GRANT.md` `grant: consumed`); 14-09 committed 88 of 88 recognized forms |
| OI-6 bare `a/an/un/una` are not numbers | orchestrator: relay with the cut; changing it later only raises recall, so this is the safe direction to defer |
| OI-7 interior sentence terminator rejects | orchestrator: relay with the cut; relaxing later is additive |
| OI-8 capture mechanism on the TESTER | closed: recognizer-direct tool ran once under the relayed window, device cleaned, no public API effect |

### Phase 15 (`15-SURFACE-REVIEW.md`)

| Id | Disposition |
|---|---|
| OI-1 hold semantics and `remainingStepIds` | closed: resolved by RT-01 (orchestrator ruling), `remainingStepIds` reviewed above as additive |
| OI-2 reference grammar details | closed: accepted by the orchestrator 2026-10-06; RT-01 of Phase 19 additionally requires the ASCII-only `\s` note in API.md (plan 10) |
| OI-3 `$<stepId>.<key>` grammar and `submit_plan` description | closed: live probe PASS (`15-LIVE-PROBE.md`), and `plan_live` PASS again on the TESTER in 19-07 |
| OI-4 `callId` of every plan step is the planning call's id | carried to Phase 20: relay with the cut; overturning needs a new member plus a behavior change |
| OI-5 first-step failure replans once | carried to Phase 20: relay with the cut; cost is provider-call count only |
| OI-6 PREVIEW/READ result counts as a failed step | carried to Phase 20: relay with the cut; documented in KDoc, plan 09 should repeat it in the plan subsection |
| OI-7 replan digest carries codes only | carried to Phase 20: relay with the cut; extending the digest later is additive |

### Phase 16 (`16-SURFACE-REVIEW.md`)

| Id | Disposition |
|---|---|
| OI-1 Router skips with one eligible model tier | closed: recorded as accepted in the Phase 19 CONTEXT ([router-leg]); the 19-07 router leg used a two-tier ladder (`eligible=2`) as required |
| OI-2 default ids `start_tier_picker` / `start_tier_router` | carried to Phase 20: relay with the cut; the ids are in the frozen strings |
| OI-3 public names (`StartTierSelection`, `StartTierSelected`, `pickerTimeoutMillis`, `tierDescriptions`, `capabilities`) | carried to Phase 20: relay with the cut. This review is the last point where a rename is a one-line change; none is proposed |
| OI-4 router output cap is `policy.maxTokensPerTurn` | carried to Phase 20: relay with the cut; a dedicated field would be additive |
| OI-5 single `router_fallback` code, no cause token | carried to Phase 20: relay with the cut; a cause is additive |
| **OI-6 router wording never run live** | **closed**: 19-07 `evidence/gate1-router_live.txt` has `VAE_VERDICT leg=router_live verdict=PASS eligible=2 picked_index=1 sel_turns=1 router_tokens=901` (trace `sel=picked ... first_model_index=1 bypassed=1`, claude-haiku-4-5, 877 input / 24 output tokens, tool `pick_start_tier`, then `submit_plan` on the picked tier). The MEDIUM-confidence wording is now live-proven on one fixture; the frozen strings stand |
| OI-7 step-id 64-character cap, parse-only | carried to Phase 20: relay with the cut; honored (P15 OI-1 not narrowed) |
| OI-8 consumers must map `start_tier_router` / picker ids | carried to plan 09: INTEGRATION.md must keep the mapping gotcha in the router block |
| OI-9 no on-device picker proof | carried to Phase 20: relay with the cut; defensive guarantee, JVM-proven, no on-device picker exists in v1.1 so no device proof is possible |

### Phase 17 (`17-SURFACE-REVIEW.md`)

| Id | Disposition |
|---|---|
| OI-1 `JournalStore` is a mirror, not a restore | orchestrator: confirm with SB 178 and CT before the cut; `loadAll` stays addable as a defaulted method |
| OI-2 `compositeSink` rethrows one fixed-text exception after all children ran | closed: accepted by the orchestrator in 17-02 |
| OI-3 defaults 50 groups / one hour | orchestrator: relay with the cut; both are builder vars |
| OI-4 N counts no-op applied entries | closed for the code: the 19-07 `undo_all` leg read `Undo all (3)` with `restored=3`, matching the CONTEXT [undo-leg] rule; UI-copy acceptance for consumers still rides the relay |
| OI-5 two suppressions in `:undo` | carried to Phase 20: relay with the cut; removing the second changes no API |
| OI-6 bridge hygiene (maps never pruned, journal first in `compositeSink`) | closed: stated in the sample and in INTEGRATION.md section 11 (`never pruned`) |
| OI-7 heavy gates for `:undo` | carried to plans 19-13 and 19-14 (RT-02): the dry run with `:undoalone` and the live-probe re-run on the final tree |
| OI-8 keystore dump 9 lines ahead of the baseline | closed by this review: the 9 lines are exactly `DelicateKeyAccess`, `KeyAccess` and the opt-in constructor (Phase 12), additive; Phase 20 regenerates the dump |

### Phase 18 (`18-SURFACE-REVIEW.md` Carry list)

| Item | Disposition |
|---|---|
| (a) release-cut handling of `:undo` and `:voice-adapter` as new modules | carried to Phase 20: both `api.txt` are header-only seeds; the dumps in this review are what Phase 20 commits |
| (b) clean-cache `:adapteralone` probe | carried to plan 19-13 (RT-03(a)); the probe script gained the module in plan 19-02 |
| (c) wiring `:sample` to the adapter (PD-02) | closed for this phase: not wired; the adapter snippet is compiled in `:voice-adapter`'s own tests (19-09 Task 4, RT-05(d)), so no `:sample` edge is added |
| (d) fuller adapter docs and hard-coded module lists | carried to plans 09 and 10 (DOC-02, manifest-driven C01/C20) |
| (e) detected-versus-fallback signal stays with the `:stt` project | closed: no ask, nothing blocks v1.1 |
| (f) consumers may delete their own label helpers | closed: informational |
| (g) `verify-stt-confinement.sh` in release preflight | carried to plan 19-12 (RT-03(b), run by hand with `--selftest`) and Phase 20 (preflight wiring) |

## Verdict

**No shape fix needed.**

- All five modules pass the per-module review from one isolated dump, and the review is shown to be non-vacuous
  (planted enum and planted sealed type both go red).
- Removed lines against the committed baselines: core 0, providers 0, keystore 0.
- The sealed sets are exactly the allow-lists: the unchanged seven in core, `UndoResult` in undo, none elsewhere.
- No data-shaped class, enum or leaked public static field in any module.
- The voice-adapter surface is two `toCommandInput`, two `commandInputOf` and one `normalizeSttLanguageLabel`.

Plans 09 and 10 may proceed. The "Docs versus dump" list is their input; the "orchestrator" rows above are non-blocking
for the docs and must be relayed before the Phase 20 cut.
