# Feature Research

**Domain:** Offline grammar tier, plan-then-execute tier, start-tier router, run-level undo and speech adapter for a domain-free Android/JVM "spoken command to app action" library
**Milestone:** v1.1 (Grammar, Plan, Router, Undo, Spike, Adapter) -> tag `v1.1.0`
**Researched:** 2026-10-05 (replaces the v1.0 file; v1.0 features are Validated in PROJECT.md and are not re-researched)
**Confidence:** HIGH for what the existing engine offers and what stt v0.7.0 exposes (read from source). MEDIUM for how the prior art behaves (Rhasspy/hassil/ReWOO/RouteLLM/saga, partly web-checked, partly domain knowledge). LOW for anything marked LOW (the research seam rates every web source LOW; the load-bearing claims below are either code-verified or cross-checked).

---

## How to read this

Scope is only the new work: Phases 14 (grammar), 15 (plan), 16 (router), 17 (undo), 18 (adapter). Phases 12 (seams/W04) and 13 (spike) are already specified to the last detail in REQUIREMENTS.md and are covered only where they feed these five.

"Table stakes" = a Wave-1 consumer (SB, CT) cannot migrate without it, or the requirement/success criterion demands it, or the feature is unsafe/incorrect without it. "Differentiator" = valued, not required for `v1.1.0`. "Anti-feature" = looks reasonable, would cost correctness, the domain-free rule, or an additive-API commitment.

Complexity: LOW = under a day, MEDIUM = a plan of its own, HIGH = the phase's main risk.

## Existing engine surface the new work attaches to (verified in source, HIGH)

| Existing piece | What it already gives v1.1 | Used by |
|---|---|---|
| `Extraction(toolName, arguments: JsonObject)` + `OutcomeResolver` -> `Resolution.{Steps, NoMatch, Escalate, Failed}` | A grammar tier can emit an `Extraction` and reuse the app resolver and the whole gate -> commit -> sink path. `Resolution.NoMatch` already hands over to the next tier with carry cleared. KDoc on `Extraction` already says "later versions add an intent and slots from a grammar tier" | Grammar |
| `ToolStep.Mutation` / `PendingMutation(toolName, targetIds, context, apply())` | `targetIds` are readable before apply; `context` is documented as "a snapshot taken before the change"; `StepResult.targetIds` is merged over them after apply | Plan binding, Undo |
| `ExecutedAction(position, kind, applied, appOutcomeToken, toolName, targetIds, context)` | `targetIds: Map<String,String>` is the only legal binding source (REQ PLAN-02) and the undo footprint | Plan, Undo |
| `CommitSink.onAction` (awaited, after the action is recorded) / `onRunClosed(runId, RunTermination)` | The journaling hook. It runs after apply, so it cannot capture the before-state by itself | Undo |
| `TierWalk.hasWorked()` -> Escalate/NoMatch after a commit becomes partial `Completed` + `escalation_suppressed` | Plan and grammar inherit "no escalation after commit" for free | Plan, Grammar |
| `StrategyCapabilities.NO_PROVIDER` | Policy already treats a zero-provider tier as runnable offline | Grammar |
| `TierSelector` (abstract, internal ctor, `Linear`, `Fixed`) | Closed today; Phase 16 opens it additively | Router |
| `CommandInput(transcript, language "en"/"es"/null, context, parentRunId)` | The adapter target. `toString()` already redacts the words | Adapter |
| stt `FinalSegment(text, segmentId, language: String?)` | **No confidence field** (the stt contract has none; the engine reads Android's confidence scores but does not consume them). `language` is non-null only for a bilingual `"auto"` session and only from a HIGHLY_CONFIDENT detection; **null is the normal case** | Adapter, Grammar |

---

## 1. LocalGrammar + bilingual GrammarPack (Phase 14)

### How these work in practice

The prior art is template grammars, not ML: Rhasspy/voice2json (JSGF-derived), Home Assistant's hassil, Snips-style intent files, and the cloud assistants' "sample utterances + slot types". They all share one shape: an **intent** owns several **sentence templates**; a template mixes literal words, `[optional]` words, `(alt|alt)` groups, reusable `<rules>`, and typed **slots** (`{n}`, `{0..100:brightness}`, list slots with synonyms). The whole thing is compiled once, matching is an **anchored full-utterance match** (not a substring search), and the result is an intent id plus slot values. Nothing is learned and nothing is scored, which is exactly why it is offline and deterministic. (Rhasspy/hassil docs; MEDIUM.)

For this engine the natural data flow is: transcript + language -> normalize -> match a rule -> typed slot values -> `Extraction(toolName, JsonObject)` -> the app's `OutcomeResolver` -> `Resolution.Steps` -> session submit (gate -> commit -> sink). A rule the app resolver rejects is just `Resolution.NoMatch`, which already means "next tier, carry cleared" (GRAM-03).

### Table stakes

| Feature | Why expected | Complexity | Notes / dependency |
|---|---|---|---|
| Anchored full-utterance match; leftover words = no match | A grammar that matches a prefix will fire a write on "add two apples and also delete everything". "Never guesses" (GRAM-03) means exact | LOW | Pure function `(transcript, language) -> Match?`; expose it non-suspend so apps and tests can golden-test it |
| Template syntax: literals, `[optional]`, `(a\|b)`, `{slot}`, reusable sub-rules | Every prior-art grammar has it; ES/EN pairs are unmaintainable as raw alternatives | MEDIUM | A Kotlin DSL builder that accepts a tiny template string per phrasing. No regex exposed to apps |
| Per-language rule sets inside one pack, one intent -> one `toolName` | GRAM-02/SC-1: EN and ES phrasings of one intent give the same tool call and slot values | MEDIUM | Same `Extraction` shape regardless of language |
| **Language handling that matches stt reality** | `language` is null for every non-auto stt session and for any low-confidence detection. So: `language` given -> that pack only; `language == null` -> try both packs; if both match with different results, **NoMatch** (never pick one) | MEDIUM | This is the most likely day-one bug. Needs a dedicated test table |
| Typed slots: integer, decimal, closed list (with per-language synonyms), bounded free text | Minimum set for CT (food/amount) and SB (title/number) without naming a domain | MEDIUM | JSON types in `Extraction.arguments` (numbers stay numbers); a free-text slot must be delimited by a literal anchor or be the final slot, never a bare wildcard in the middle |
| Number-word slot, EN and ES, digits and words both accepted | Android recognizers emit digits for some utterances ("2 y medio") and words for others. Both must hit the same value | HIGH | See the number-word spec below; this is the phase's main correctness surface |
| STT normalization before matching | Case (use `Locale.ROOT`, not the device locale), Unicode NFC/NFD, diacritic folding, punctuation stripping, whitespace collapse, hyphen splitting ("twenty-one") | LOW | Fold accents for matching but keep the original raw slot text for the `normalize` hook. Treat `n` with tilde as its own letter (`año` vs `ano`) |
| Optional leading/trailing filler stripping (politeness, hesitation) | "please", "por favor", "eh", "pues", "oye" appear in real STT output and would otherwise defeat anchoring | LOW | Declared per pack and per language, not hard-coded domain words |
| Per-slot `normalize: (raw, language) -> String?` hook (GRAM-04) | CT's cross-language synonym map. Domain-free because the app supplies the map | LOW | Decide and document null semantics. Recommend: null = "no opinion, use the raw text"; rejecting a value is the resolver's job (`Resolution.NoMatch`). Meaningful only for list/text slots: a number slot's built-in lexicon decides the span, so a hook cannot turn "to" into a number |
| Build-time pack validation (fail loudly at construction) | Duplicate rules, ambiguous overlapping templates, unknown slot/rule references, slot declared but unused, empty alternatives | MEDIUM | Same "loud, specific failure" principle as the rest of the engine. An ambiguity detected at runtime (two intents match) is NoMatch with a trace code, never first-wins |
| `NO_PROVIDER` capabilities; zero provider calls; same gate/commit/sink path | GRAM-01, GRAM-05, SC-2/5 | LOW | `ExecutedAction.providerCallId` is null (Phase 12 dependency) |
| Held grammar write reported held, never success | SC-2 | LOW | Falls out of the session path; needs a test |
| Trace carries rule id and language, never the transcript or slot values | Secrets constraint | LOW | Rule ids are app-chosen constants; safe |
| No domain words in library code | CLN-02 scan | LOW | Built-in lexicons are numbers/fractions/fillers only |

#### Number-word spec (the Phase 14 research flag; use as the golden table)

| Class | EN | ES | Notes |
|---|---|---|---|
| Units and teens | zero..nineteen | cero..quince, dieciseis..diecinueve | `dieciseis`/`dieciséis` fold to one form. Accept legacy split spelling "diez y seis" (STT emits it) |
| Gender and apocope of 1 | one, a, an | uno, un, una; veintiuno, **veintiun**, veintiuna; treinta y uno / un / una | All forms mean 1 / 21 / 31. "veintiun gramos" (masculine noun) is correct Spanish, so rejecting it would be a bug |
| Accent-dependent | n/a | veintidos/veintitres/veintiseis (STT often drops the accent) | Fold accents when matching |
| 20-29 one word, 31+ spaced with `y` | twenty-one, twenty one | veintiuno; treinta y uno, cuarenta y cinco | `y` joins only tens and units. Be strict here |
| Hundreds | one hundred (and) five, a hundred five | cien (alone or before a noun), ciento uno, ciento veinte, doscientos, **quinientos, setecientos, novecientos** | Irregular ES stems must be in the lexicon. **"ciento y ..." is not grammatical Spanish** (the open item in the roadmap): do not accept it by default. If real STT output shows it, allow it only through an explicit pack-level alias, not silently |
| Hundreds, feminine | n/a | doscientas, quinientas, ... | Accept, same value |
| Thousands | one thousand five hundred, fifteen hundred | mil, mil quinientos, dos mil veinte | Cap the supported range per slot (`min..max`); most slots are under 10,000 |
| Halves/quarters after a number | two and a half, one and three quarters, a quarter | dos **y medio** / dos **y media**, dos y cuarto, dos y tres cuartos | `y medio/media` agrees in gender with the noun, so accept both. This is the "dos y medio" case. Value = 2.5 |
| Bare fractions | half, a half, a quarter, three quarters | medio, media, un cuarto, tres cuartos | |
| Decimal words | two point five, zero point two five, point five | dos coma cinco, dos punto cinco, cero coma veinticinco | Both `coma` and `punto` (country dependent). `con` as a decimal marker is regionally used but ambiguous with the preposition: **off by default** |
| Digit forms from STT | 2.5, 2 1/2, 2.5, a vulgar-fraction glyph | 2,5, 2 y medio, 2 1/2, a vulgar-fraction glyph | Decimal comma (ES) vs point (EN) follows the language of the matching pack |
| Thousands separators | 1,000 | 1.000 | **Ambiguity trap**: ES "1.000" is 1000, but ES "1.5" is 1.5 and EN "1.000" is 1. Resolve by language and digit-group shape, and return NoMatch when it is still ambiguous |
| EN homophones ("to/too", "for", "ate", "won") | | | Do not accept by default (it would let "for" be 4 in "add for apples"). An app that wants it supplies a list-slot synonym via `normalize` |
| Mixed words and digits | "twenty 1", "dos y 1/2" | | Reject. Not worth the ambiguity |

### Differentiators

| Feature | Value | Complexity | Notes |
|---|---|---|---|
| EN/ES parity lint (every intent has both languages, or is explicitly single-language) | Catches the "ES forgot the new phrasing" bug at construction | LOW | Build-time check, off for packs that declare `singleLanguage` |
| Pluggable custom slot type (`SlotParser<T>`) | SB needs reminder times and CT needs units without the engine growing a Time/Date/Unit zoo | MEDIUM | This is the right place to stop the engine's own slot set. Deferring built-in Date/Time/Duration is deliberate |
| Dry-run API `pack.explain(transcript, language)` (which rule, which slots, or why not) | Writing rules against real STT logs is the main authoring loop | LOW | Returns rule ids and slot spans, not the raw transcript, if it is ever logged |
| Optional per-language `fallbackLanguage` (try the other pack when the labeled one does not match) | Code-switching and wrong stt labels ("add dos eggs") | LOW | Off by default; still NoMatch on a cross-pack disagreement |
| Slot-level `requires`/`range` validation before the resolver | Reject "ciento cincuenta" for a 1-10 slot without a resolver round trip | LOW | Rejection = NoMatch, same as resolver rejection |
| Split-spelling and stt-quirk aliases as a shipped, versioned lexicon | Saves every app re-discovering "diez y seis" | LOW | Keep it in the lexicon, not in app code |

### Anti-features

| Anti-feature | Why requested | Why it hurts | Instead |
|---|---|---|---|
| Fuzzy / edit-distance / similarity-threshold matching | "STT is noisy, be forgiving" | It is a best-guess matcher, violates GRAM-03, and a wrong match on a *write* is worse than a cloud round trip | Exact match; NoMatch hands the command to the LLM tier, which is the forgiving tier by design |
| A "confidence" score on the grammar result | Symmetry with ML tiers | Binary by construction; a fake score invites thresholds that re-introduce guessing | Match or NoMatch |
| Global number-word -> digit rewrite of the whole transcript before matching | Simpler pipeline | Corrupts literal words ("the one", "un", "a"), irreversible, breaks the raw text the `normalize` hook sees | Slot-level numeric spans only |
| Built-in domain vocabularies (food names, units, note verbs) | "Ship useful defaults" | Violates domain-free (CLN-02) | App supplies lists and the `normalize` map |
| Learned / downloaded models, dictionaries or network lookups | "Better recognition" | Breaks offline determinism and the zero-dependency `:core` | The ladder escalates to a model tier |
| Exposing raw regex to rule authors | Power | Backtracking blowups, untestable ambiguity, ES/EN divergence | The template DSL |
| Grammar-specific confirmation logic | "Writes from a grammar are riskier" | Duplicates `PreApplyGate` | Apps gate by tool, as for every tier |
| First-match-wins when two intents match | Easy to build | Silent wrong intent | Ambiguity = NoMatch plus a trace code, or an explicit priority declared in the pack |

### Dependencies on the existing engine
Phase 12 (`providerCallId = null` for zero-call tiers; `cappedByPolicy` for the offline no-match end state). Reuses `Extraction`, `OutcomeResolver`, `Resolution`, `CommandSession`. Adds nothing to `:providers`. Feeds Phase 16 (grammar is the router's free pre-pass).

---

## 2. PlanThenExecuteStrategy (Phase 15)

### How these work in practice

Three well-known shapes (LangChain "planning agents" write-up; MEDIUM): **Plan-and-Execute** (planner emits steps, an executor runs each, a replanner revises), **ReWOO** (the planner writes the whole plan up front with variable placeholders like `#E1` so later steps use earlier results without another LLM call), and **LLMCompiler** (a DAG with parallel execution). The cost win of all three is fewer LLM calls than a ReAct loop. This engine's contract §4 narrows it further: Plan is for **lookup-free** commands, so it is closest to ReWOO restricted to *write* outputs: one planning call, N gated executions, at most one replan.

Where it sits in the ladder: SingleShot handles one tool call; Plan handles "two or more writes, one depending on the other's id" (create a note, then tag it); AgenticLoop handles anything needing a read. If a command needs one write, Plan buys nothing over SingleShot.

### Table stakes

| Feature | Why expected | Complexity | Notes / dependency |
|---|---|---|---|
| Plan returned as **one forced tool call** (`submit_plan`, `steps: [{id, tool, args}]`) | Reuses the SingleShot machinery (forced tool choice, capability rows, typed failures) and the same neutral transcript model | MEDIUM | Free-text plans need parsing and are the main source of malformed output |
| Whole plan validated before step 1 runs | A plan that names an unknown tool, references a later or unknown step, or is empty must cost zero side effects | MEDIUM | Failure -> the one replan, else `Escalate` |
| Steps run in order, each mutating step through `PreApplyGate` | PLAN-01, SC-1 | MEDIUM | Gate decision is per step here (SingleShot folds all mutations into one) |
| **Binding syntax**: a step argument may be a whole-value string reference to an earlier **mutating** step's `targetIds` entry; use stable step ids, e.g. `"$a.noteId"` | PLAN-02. Stable ids survive a replan renumbering; `$step[n]` style indexes do not | MEDIUM | Whole-value substitution only; no interpolation inside a larger string (avoids injection and quoting bugs). `targetIds` are `String`, so the bound arg must be a string-typed parameter; a type mismatch fails the step |
| Unresolved binding fails that step (no guessed substitute) | PLAN-02 | LOW | Missing step, missing key, or the referenced step was held / errored / never ran |
| Held step blocks its dependents | A held write has no `targetIds` yet (it is not applied) | MEDIUM | Decide in planning: stop at the first hold (simplest, reports a clean partial) vs continue independent steps. Recommend **stop at the first hold**; `commitHeld` later applies only what was held |
| At most one replan, then `Escalate` | PLAN-03 | MEDIUM | Replan covers only the remaining steps. It must **never re-run a committed step**. The binding environment (step id -> `targetIds`) persists across the replan so the new tail can still reference `$a.noteId` |
| "Needs a lookup" escalates before anything runs | Contract §4, PLAN-03 | MEDIUM | Detect two ways: (1) static: any planned step whose `ToolSpec.mutating` is false means a read is involved, so escalate with zero side effects; (2) an explicit escape-hatch terminal tool (`needs_lookup`) the model may call instead of `submit_plan`. This answers the roadmap's "how is it detected before any step runs" |
| No escalation after the first commit | PLAN-04 | LOW | Already pipeline behavior (`hasWorked()`); needs a Plan-specific test. A mid-plan failure after a commit therefore ends as partial `Completed` with `escalation_suppressed`, so the replan is the only recovery for committed work |
| `onFailed` hook identical to SingleShot's | PLAN-05 | LOW | Provider failures only |
| Trace shows exactly how many model calls ran (1 or 2), steps planned / run / skipped, binding failures | SC-3 | LOW | Counts and codes only; never args or ids |
| Per-step and total ceilings come from `TierPolicy`, not constants | v1.0 rule | LOW | Max steps in a plan is a policy value with a default |
| Budget: plan and replan calls count against the run token ceiling | Existing budget model | LOW | |

### Differentiators

| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Replan prompt carries a compact "what ran" digest (step id, tool, outcome kind, available binding names) but no values | Gives the model enough to finish the tail without re-sending data | MEDIUM | Keep tool results out of logs per the secrets rule |
| Structured replan failure classes (invalid plan, binding unresolved, step errored, step held) | Replan can be told *why*; telemetry can show which class dominates | LOW | Reuses `EscalationReason` taxonomy |
| Forward-only data check against the tool schema (arg declared as string) before running | Catches a type mismatch with zero side effects instead of a mid-plan failure | MEDIUM | Needs `ToolSpec` schema inspection; skip if the schema is opaque |
| Share the AgenticLoop cache prefix | A 7k-token system+tools prefix is the cost driver | MEDIUM | **Caveat**: adding a `submit_plan` tool changes the tools block, which sits first in the cache order, so it will not hit the Agentic prefix's cache. Either accept a separate cache entry, or express the plan as the terminal call of the same tool list. Decide with a real cache-read measurement in Phase 19 |

### Anti-features

| Anti-feature | Why requested | Why it hurts | Instead |
|---|---|---|---|
| Read steps inside a plan (observe, then continue) | "Plan can look something up" | That is ReAct/Agentic; plan correctness depends on unseen data | Escalate to AgenticLoop (contract §4) |
| DAG / parallel steps (LLMCompiler style) | Latency | Parallel tool execution is out of scope, and parallel writes through one gate are ill-defined | Sequential |
| Conditionals, loops, expression language in the plan (`$a.x + 1`, jq paths) | Flexibility | A mini interpreter inside the engine, untestable surface | Whole-value references only |
| Binding to anything but write-output `targetIds` | "Use the reply text" | Unstructured, unstable, leaks content into args | `targetIds` only |
| More than one replan | "Try harder" | Compounds cost on a path that already failed; the next tier exists for this | Cap at one (a policy knob could lower it to zero) |
| Re-running committed steps on replan | Simpler prompt | Double-applies writes | Replan emits the remaining tail only |
| Silent skip of an unresolvable step | Keep going | Hides a half-done command | Fail the step, replan once, else escalate/suppress loudly |

### Dependencies on the existing engine
Phase 12 (`onFailed`, `ReasoningMode`, `ExecutedAction.providerCallId`). Reuses SingleShot request/encoder code, `ToolExecutor`/`ToolStep`, `CommandSession` dispatch, `TierWalk` suppression. Plan's escalation target is normally AgenticLoop, so a `carry` (opaque, typed to the next tier) of "steps already done" is useful but must stay a presence/class, not content.

---

## 3. TierSelector.Custom + TierSelector.Router (Phase 16)

### How these work in practice

Two published patterns (RouteLLM / FrugalGPT write-ups; MEDIUM): **classifier routing** (a cheap model or classifier predicts which tier a query needs, then calls that tier directly) and **cascading** (always start cheap, check the answer, escalate). This engine's ladder *is* already a cascade. The Router adds the classifier half: it lets the walk **skip** rungs. The asymmetry matters for design: a router that picks too low is recoverable (the lower tier escalates, costing one wasted attempt plus the router call); a router that picks too high silently overpays and is unobservable. So bias to the cheap side and abstain when unsure.

A cheap-model start-tier classifier in practice: tiny prompt, a **closed-set answer** (forced tool call with an enum of eligible tier ids, so parsing cannot fail the way free text does), `max_tokens` of a few dozen, temperature 0 where the model allows it, a short timeout, and a hard fallback to the default path on any problem. Tier descriptions come from the app (one line per tier) because the engine names no domain.

### Table stakes

| Feature | Why expected | Complexity | Notes / dependency |
|---|---|---|---|
| `StartTierPicker` seam + `TierSelector.Custom` | ROUT-01; SB's own Router is blocked on it | MEDIUM | Suspend; sees input and eligible LLM tier ids |
| Grammar (any zero-call head tier) always runs first | ROUT-02 | LOW | Picker's eligible list excludes zero-call tiers |
| Picker model calls via `PickContext` count toward budget and trace | ROUT-01 | MEDIUM | The router call is itself subject to `TierPolicy` (allowed providers, ceilings) and honors the per-call provider/model/key seam |
| null / ineligible id / throw -> Linear + `router_fallback`; cancellation propagates | ROUT-03 | LOW | Same never-throw collapse helper as everywhere |
| No eligible LLM tier -> picker never called, no router call | ROUT-04, SB condition | LOW | Also covers offline-only |
| **Skip the engine Router when only one LLM tier is eligible** | A classifier choosing among one option is a pure cost | LOW | Recommend for `Router`; `Custom` is still called (the app may depend on it). Not in the REQ text; add as a planning decision |
| `TierSelector.Router` off by default | ROUT-05 | LOW | Absent = v1.0 Linear walk, wire bytes unchanged |
| Router model from policy/config, never hard-coded | v1.0 rule | LOW | Default is "unset -> router disabled", not a baked-in model id |
| Router classifier prompt: closed enum answer, transcript + language + tier one-liners, no tool schemas | Keeps the call small and sub-second; the tool schemas are the expensive part | MEDIUM | Prefix is too small to cache; do not try |
| Telemetry: router start vs Linear start | ROUT-05 | MEDIUM | See the "tiers saved" definition below |

#### "Tiers saved" has to be defined carefully (Phase 16 research flag)
Linear would start at the lowest eligible LLM tier; the Router starts at tier k. The tiers Linear *would have attempted* but the Router skipped = k. Whether skipping them was a **saving** depends on a counterfactual the engine cannot observe (tier 1 might have handled it). So report facts, not a claim:

| Metric | Source | Meaning |
|---|---|---|
| `tiersSkipped` = router start index - Linear start index | trace | Upper bound on attempts avoided |
| `routerCalls`, router tokens in/out, router latency | trace | The price paid |
| Outcome at pick: handled at the picked tier / escalated from it | trace | Misroute-up (picked too low) is observable |
| Misroute-down (picked too high) | not observable | Only a shadow run can estimate it; do not pretend |
| Net estimate = `tiersSkipped` minus router cost, labeled as an estimate | derived | Name the field `tiersSkipped`, not `tiersSaved`, so the API does not over-claim |

### Differentiators

| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Router may abstain (returns null -> Linear) | Uncertainty costs one cheap rung, not a wrong jump | LOW | Make abstention the prompt's documented default for unclear commands |
| `maxSkip` guard (never skip more than N rungs on a router's word) | Bounds the downside of a bad classifier | LOW | Policy knob, default unlimited or 1 |
| Language-aware prompt (EN and ES examples in the app's tier descriptions) | Bilingual commands | LOW | App-supplied text |
| Opt-in shadow mode (run the Router, record its pick, walk Linear) | The only way to measure misroute-down and tune | MEDIUM | Costs one extra call per command; for dev/eval builds only |
| Router result in `PickContext` trace as tier id only | Debuggability without content | LOW | |

### Anti-features

| Anti-feature | Why requested | Why it hurts | Instead |
|---|---|---|---|
| Running the Router in parallel with the grammar tier "to hide latency" | Speed | Pays a model call on every command the grammar would have handled free | Grammar first, router only on hand-over |
| Router decides *which provider/model*, not just which tier | "Smart routing" | Collides with the app-owned per-call provider seam and the "never substitute another provider's key" rule | Tier selection only |
| Router enabled by default, or a built-in default model id | "Works out of the box" | Hidden spend, violates the policy-owned-model rule and "default off" | Opt-in with an explicit model |
| Heuristic/regex router inside the engine (length, verb count) | No model call | Domain guessing; the `Custom` picker already lets an app do this | `TierSelector.Custom` |
| Failing the command when the router errors | Strictness | The router is an optimizer; failing on it turns a latency feature into an availability risk | Always fall back to Linear |
| A `CapRefused`-style new outcome type | | Breaks exhaustive `when` (already ruled out) | `cappedByPolicy` flag |

### Dependencies on the existing engine
Phase 12 (trace codes, `cappedByPolicy`), Phase 14 (real-grammar pre-pass proof only). New: `PickContext` (budget + trace wiring) and the `router_fallback` trace code. `TierSelector.startIndex` stays internal and synchronous today; the suspend pick must run in the walk before tier selection.

---

## 4. Run-level undo, `:undo` module (Phase 17)

### How these work in practice

Four classic patterns, and a real undo uses two of them (Wikipedia/saga write-ups; MEDIUM):
- **Command with inverse**: each action knows its opposite. Cheap, but wrong for lossy writes (delete, overwrite).
- **Memento / before-image**: snapshot the entity before the change; undo writes the snapshot back. Right for database rows.
- **Journal**: an append-only log of what happened, keyed by a group id (here `runId`), so a *group* can be reversed in **reverse order**.
- **Saga compensation**: for side effects outside the database (an alarm, a notification, a file) you cannot snapshot, so you register an explicit, **idempotent** compensating action and run it in the reverse sequence.

Plus **optimistic concurrency**: before restoring, verify the entity is still in the state the command left it (a version, `updatedAt`, or content fingerprint). If something else changed it since, overwriting loses the user's later edit, so refuse.

The shape for this engine: `onAction` (after apply) records `(runId, position, toolName, targetIds, context)`; the app captures the **before-image** when it builds the `PendingMutation` (that is what `context` is documented for), and the after-fingerprint is read immediately in the sink (it runs awaited, right after apply). Undo-all walks the run's entries in reverse, and for each entity: check unchanged-since-commit, then restore through the entity adapter, or run the compensator.

### Table stakes

| Feature | Why expected | Complexity | Notes / dependency |
|---|---|---|---|
| Zero dependencies (not even `:core`), usable by a non-voice JVM app | UNDO-01, SC-1 | LOW | Module-graph gate proves it |
| One adapter per entity type: `read`, `write back`, `re-insert if deleted` | UNDO-02. Matches SB `PreMutationSnapshot` / `VoiceUndoOperations` | MEDIUM | Deleted rows must come back with their **original primary key**; cascade-deleted children are part of the footprint or they are lost |
| Reverse-order restore within a run | A later action in the same run may depend on an earlier one | LOW | |
| **Unchanged-since-commit check before every restore; changed -> refuse for that entity, never overwrite** | UNDO-03 | MEDIUM | Compare the current state to the *after-state of the last action in this run that touched the entity*. That handles intra-run chains and cross-run edits uniformly. Give the adapter an atomic `restoreIf(expectedCurrent, before)` so check and write are one DB transaction (avoids check-then-write races) |
| Explicit compensators for out-of-DB effects (alarm, notification, file) | UNDO-02. SB `ReminderArmer` becomes one | MEDIUM | Registered by key; must be idempotent and retryable |
| Result is complete or an exact list of what was not restored, never a silent partial | UNDO-03, SC-3 | MEDIUM | Per item: Restored, RefusedChangedSince, Failed(error), AlreadyUndone, NotReversible, BlockedByLater |
| Idempotent re-run (undo twice is safe) | Double-tap on "Undo all" | LOW | Mark entries undone; a retry only touches Refused/Failed items |
| Footprint = set of (entity type, id) per action; overlap = entangled | UNDO-04, A18 | MEDIUM | Entangled actions undo together; isolated ones can be undone alone |
| Group by `runId`; `commitHeld` child runs (`parentRunId`) fold into the parent's "Undo all (N)" | UNDO-04 | MEDIUM | Open item from the roadmap; decide the count semantics (N = reversible actions) |
| Failed/errored actions (`IS_ERROR`, `applied=true`) are journaled too | An errored apply "may have written" per `ApplyStep` | LOW | Undo must treat them as possibly-written, restore if the before-image differs |
| Bounded retention (count and age) | Snapshots can hold user content | LOW | In-memory store by default; the persistence store is an app seam |
| Snapshots and results never logged; redacted `toString()` | Secrets constraint | LOW | |

### Differentiators

| Feature | Value | Complexity | Notes |
|---|---|---|---|
| **Dry-run / preview**: "what would Undo all restore, refuse, or skip" before doing it | A button labeled "Undo all (3)" can say honestly "2 of 3 can be undone" | MEDIUM | Same check path, no writes |
| `BlockedByLater` reporting: if a later action on entity X was refused, earlier actions on X cannot be restored | Explains partial results instead of looking like a bug | LOW | Falls out of the chain model |
| Optional all-or-nothing mode via an app-supplied transaction runner (e.g. a Room transaction) | Atomic undo when all adapters share one database | MEDIUM | Best-effort remains the default because compensators cannot join a DB transaction |
| Persisted journal store seam (survives process death) | A command, then app kill, then "Undo" | MEDIUM | Interface only; app implements |
| Per-entity conflict callback (`Refuse` default, app may choose `Overwrite` / `Merge`) | Some entities are last-write-wins by nature | MEDIUM | Defer. If added, default must stay Refuse and the override explicit |
| Undo of a single action inside a run when its footprint is isolated | Keeps SB's current per-item undo | MEDIUM | Needed for SB 178 parity; covered by the footprint model |
| Voice phrase "undo that" / "deshacer todo" | Natural voice UX | LOW | Not engine code: a grammar rule mapping to the app's undo tool |

### Anti-features

| Anti-feature | Why requested | Why it hurts | Instead |
|---|---|---|---|
| Force-overwrite flag on undo | "Just make it work" | The exact data-loss the refuse check exists to prevent | Refuse loudly; app-level explicit conflict callback if ever needed |
| Redo / undo stack across runs / history UI | Familiar editor UX | Scope explosion; cross-run undo needs conflict semantics for every pair | Undo is per run only |
| Reflection or schema-scan entity capture | Less app code | Fragile on R8, cannot know cascade/external effects | Explicit adapters |
| Undoing irreversible external effects (a sent message) | Completeness | Impossible; pretending is worse | Mark `NotReversible` and say so |
| Hiding partial results behind a boolean | Simple API | Violates the "loud, specific failure" principle | The per-item report |
| Capturing the before-state in `CommitSink.onAction` | Seems like the natural hook | It runs **after** apply, so the before-state is gone | Capture when the `PendingMutation` is built (`context`), or wrap `apply` |
| `:undo` importing `:core` types | Pipeline convenience | Breaks UNDO-01 | Keep the bridge outside `:undo` |

### Open design question for Phase 17 (flag, not resolved here)
`:undo` cannot depend on `:core`, but "the pipeline journals each command's commits" needs a `CommitSink` bridge, which needs `:core` types. The bridge must live somewhere that sees both: (a) in `:core` (a `:core` -> `:undo` dependency, which touches the `:core` classpath-allowlist gate and A7 reasoning), (b) in app glue documented in INTEGRATION.md (a few lines), or (c) a tiny extra artifact. Recommend deciding this before planning; (b) is cheapest and keeps both gates untouched, (a) is the most convenient for consumers.

### Dependencies on the existing engine
Phase 12 (`ExecutedAction.providerCallId` is *not* the journal key: `position` is, per its KDoc, "never a provider's tool-use id"). Uses `runId`/`parentRunId` (already on every sink call), `PendingMutation.context`/`targetIds`, `StepResult.targetIds`, `RunTermination.commits`. A grammar tier's writes are journaled identically (no provider call id).

---

## 5. Speech-to-command adapter, `:voice-adapter` (Phase 18)

### What it should map

| stt v0.7.0 | `CommandInput` | Rule |
|---|---|---|
| `FinalSegment.text` | `transcript` | Pass through; trim only. No casing, punctuation, or number rewriting (that is the grammar's job) |
| `FinalSegment.language` (`String?`) | `language` | Lowercase and take the primary subtag (`es-MX` -> `es`); `en`/`es` pass; anything else, and null, -> `null`. **Never guess**, including from the text |
| `FinalSegment.segmentId` | not a field | Optional: offered through the `context` overload so an app can dedupe |
| (no confidence in stt v0.7.0) | none | **There is nothing to map.** Do not invent a field |

Real-world facts that shape the adapter: stt segments by pauses, so a spoken command can arrive in two finals; `language` is null for every non-auto session, so "null" is the common case, not an edge case (this is why the grammar must try both packs on null); the segment is already final and immutable.

### Table stakes

| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| `FinalSegment -> CommandInput` in one call (an extension function) | ADPT-01 | LOW | Stateless; no lifecycle |
| Language normalization to `en` / `es` / null, defensively (stt already normalizes, the adapter must not trust it) | ADPT-01, SC-1 | LOW | |
| Blank / whitespace-only text yields no command | A command of nothing should not walk the ladder | LOW | Return nullable or a sealed result; do not throw |
| `context` and `parentRunId` pass-through parameters | `CommandInput` has both; clarification replies need `parentRunId` | LOW | Explicit overloads (no reliance on Kotlin defaults alone), `@JvmOverloads` or hand-written |
| Module graph: only `:voice-adapter` depends on `:stt`; `:core` untouched | SC-2, L7 | LOW | Gate must pass; the module may need to be an Android library if stt's artifact is an AAR (roadmap open item) |
| Transcript never in `toString()`, exceptions or logs | Secrets constraint | LOW | `CommandInput.toString()` already redacts |

### Differentiators

| Feature | Value | Complexity | Notes |
|---|---|---|---|
| App-declared `sessionLanguage` fallback used only when the segment language is null | A fixed-language session is a fact the app knows, not a guess | LOW | Off by default; keep `null` otherwise so the grammar tries both |
| `Flow<FinalSegment>.toCommandInputs()` convenience | One-line wiring in a ViewModel | LOW | Optional; needs only coroutines-core. Defer unless an app asks |
| Consecutive-segment joiner with an idle window | Fixes commands split across pauses | MEDIUM | Needs timers, so it is lifecycle. Better as app code or a later minor |

### Anti-features

| Anti-feature | Why requested | Why it hurts | Instead |
|---|---|---|---|
| A `confidence` field on `CommandInput`, or confidence gating in the adapter | Common in speech SDKs | stt does not expose it; a field that is always null misleads, and gating would hide commands | Add it only if stt adds it, as an additive field |
| Owning capture start/stop, partial/interim results, permissions | "Complete voice solution" | Duplicates stt, adds Android lifecycle to a one-call mapper | Apps call stt directly |
| Running the pipeline from the adapter | Convenience | Couples adapter to run policy, scope, cancellation | App calls `pipeline.run(input)` |
| Text cleanup (punctuation, number words, casing) | "Help the grammar" | Destroys the raw text the grammar/`normalize` hook needs | Leave to the grammar |
| Guessing language from the text | A wrong label breaks a bilingual pack | Violates "never a guess" | Null |

### Dependencies on the existing engine
`CommandInput` only (it already has `language` and `parentRunId`). `:stt` v0.7.0 (already in section 11). No dependency on the other new tiers; feeds the grammar's null-language behavior.

---

## Cross-feature dependency graph

```
Phase 12 seams (providerCallId, cappedByPolicy, carryIn, onFailed, ReasoningMode)
    |-- providerCallId = null for zero-call --> Grammar (14), Undo journal (17)
    |-- cappedByPolicy ------------------------> Grammar offline no-match (14), Router no-eligible (16)
    |-- onFailed, ReasoningMode ---------------> Plan (15)

Adapter (18): language null is common --> Grammar must try both packs (14)
Grammar (14) --free pre-pass--> Router/Custom (16) --picks among--> SingleShot / Plan (15) / Agentic
Plan (15): step committed --> TierWalk.hasWorked() --> escalation suppressed (existing)
Plan targetIds --binding--> later step      Undo footprint <-- same targetIds
Grammar writes + Plan writes + Agentic writes --all journal identically--> Undo (17)
Undo (17) is standalone; its CommitSink bridge location is the open design question
```

## MVP recommendation (what each phase must contain to meet its success criteria)

1. **Grammar**: anchored matcher + template DSL + the number-word table above for EN and ES + `language == null` try-both + ambiguity = NoMatch + build-time validation + `normalize` hook + resolver-rejection = NoMatch. Defer custom slot parsers, explain/dry-run, parity lint to differentiators (take parity lint and `explain` only if cheap).
2. **Plan**: forced `submit_plan`, up-front validation, stable-id whole-value bindings, static lookup detection plus `needs_lookup` escape, one replan, per-step gating, stop at first hold, `onFailed`, trace counts. Defer cache-prefix sharing and schema type pre-check.
3. **Router**: `Custom` + `PickContext` + fallback + skip-when-none-eligible first (all table stakes, JVM-testable with a fake `NO_PROVIDER` head tier), then `Router` with a closed-enum prompt and `tiersSkipped` telemetry. Defer shadow mode and `maxSkip`.
4. **Undo**: adapters + reverse restore + atomic `restoreIf` + compensators + per-item report + footprint grouping + runId journaling. Defer transactional mode, persisted store, conflict callback. Take dry-run preview if the check path makes it nearly free.
5. **Adapter**: the extension function and language normalization only.

Defer beyond v1.1: built-in Date/Time/Duration slots, joiner for split segments, shadow routing, conflict-resolution callback, Flow convenience.

## Open questions for phase-level research

| Phase | Question | Why it matters |
|---|---|---|
| 14 | Do real S22 `SpeechRecognizer` outputs for ES emit digits, words, or "dos y medio" with digits? | Decides how much of the number table is exercised vs. theoretical. Capture a handful of real transcripts on the TESTER |
| 14 | Null semantics of `normalize` and whether it also runs on list-slot matches | Affects CT's synonym map design |
| 15 | Hold handling: stop at first hold vs continue independent steps | Interacts with `commitHeld` |
| 15 | Does a separate `submit_plan` tool defeat prompt-cache reuse in practice? | Real cost on SB's 7k-token prefix |
| 16 | Skip the engine Router when exactly one LLM tier is eligible? | Free saving; not in the REQ text |
| 16 | Name the metric `tiersSkipped` instead of "saved"? | API honesty; additive-only means the name is permanent |
| 17 | Where does the `CommitSink` bridge live? | Gate constraints on `:core` vs `:undo` |
| 17 | Cascade-delete children and autoincrement ids in re-insert | Most likely source of "undo restored half a thing" |
| 18 | Is stt's artifact an AAR, forcing `:voice-adapter` to be an Android library? | Module plumbing and JitPack form |

## Sources

- Source code, verified (HIGH): `core/.../strategy/{OutcomeResolver,StrategyOutcome,ToolExecutor,StrategyCapabilities}.kt`, `core/.../commit/{CommitSink,ToolStep,ApplyStep,RunTermination}.kt`, `core/.../pipeline/TierSelector.kt`, `core/.../CommandInput.kt`; `stt-engine/android/stt/.../FinalSegment.kt` and `SttEngineImpl.kt` (language label from HIGHLY_CONFIDENT detection only; confidence scores read but not consumed).
- Project docs (HIGH): `.planning/PROJECT.md`, `ROADMAP.md`, `REQUIREMENTS.md`, `cross-repo/RECONVENE-BRIEF-R-v1.1.md`.
- Rhasspy / voice2json / hassil template grammars (template syntax, number ranges, lists, rules; MEDIUM, web-fetched): https://github.com/home-assistant/hassil , https://rhasspy.readthedocs.io/en/latest/training/ , https://voice2json.readthedocs.io/en/latest/sentences/
- LangChain planning agents (Plan-and-Execute vs ReWOO `#E1` vs LLMCompiler; MEDIUM, web-fetched): https://www.langchain.com/blog/planning-agents
- LLM routing and cascades (RouteLLM, FrugalGPT; MEDIUM, search summary only): https://lmsys.org/blog/2024-07-01-routellm/ , https://www.resumelens.org/blog/ai/llm-routing-cascades
- Saga / compensating transactions (reverse order, idempotent compensation; MEDIUM, search summary): https://en.wikipedia.org/wiki/Compensating_transaction , https://quality.arc42.org/approaches/saga-pattern
- Spanish number forms (veintiuno / veintiun / veintiuna, one-word up to 30): search results only (LOW). The `y medio` / decimal `coma` vs `punto` / "ciento y is not grammatical" / thousands-separator ambiguity rows are domain knowledge (MEDIUM) and must be confirmed against real recognizer output on the TESTER in Phase 14.
