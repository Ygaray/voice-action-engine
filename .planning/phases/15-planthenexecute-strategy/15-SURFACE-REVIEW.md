# Phase 15 frozen-surface review (D-01, D-02, D-04, D-12, RT-01; one-way at the v1.1.0 tag)

Reviewed on branch `gsd/phase-15-planthenexecute-strategy` after plans 15-01 .. 15-05, against the real Metalava dump of
`:core` produced by `scripts/review-api-surface.sh` in an isolated copy of the tree (`API SURFACE OK
sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=196`; Phase 14
closed at 193, so the phase adds exactly the three plan classes). The dump lives outside the repository; no `api.txt` was
created or edited. The committed `core/api.txt` is still the v1.0.x snapshot, so every Phase 15 addition reaches it only
at the v1.1.0 cut, and until then the dump diff below is their proof. The compat gate in `check` runs against that
committed snapshot and accepts the additions.

Pitfall 5 question asked of every member: what will a consumer ask next, and can it be added without removing anything?

## What the dump shows

- Package `io.github.ygaray.voiceactionengine.core.strategy.plan` is new and holds exactly `PlanThenExecuteStrategy`, its
  `Builder` and its `Companion`. All three are `final`; the dump shows no sealed type, enum, data shape (`copy` /
  `componentN`), default-argument stub or public static field other than the `Companion` holder. The strategy exposes
  `execute`, `toString`, and the `id` / `capabilities` properties of `CommandStrategy`. The Builder exposes the getter
  and setter pairs of `tooling`, `executor`, `capabilities`, `userTurn`, `clock`, `reasoning`, `maxSteps` and `onFailed`.
  The Companion exposes `invoke(id, block)` (`@KotlinOnly`).
- `TraceCode.Companion` gained three properties: `PLAN_BINDING_UNRESOLVED`, `PLAN_REJECTED`, `PLAN_REPLANNED`.
- `CommandOutcome.Completed` gained `getRemainingStepIds()` and the `remainingStepIds` property and nothing else.
- `StrategyOutcome.Completed` shows exactly its three v1.0 public constructors `(reply)`, `(reply, terminalCall)` and
  `(reply, terminalCall, partial)`; `StrategyOutcome.Escalate` shows exactly its two, `(reason)` and `(reason, carry)`.
  The new internal primary constructors (which carry the never-run ids) are absent from the dump, as intended.
- `RunTermination`, `HeldProposal` and `CommandSession` are unchanged by this phase (no file under `core/commit/` or
  `CommandSession.kt` differs from `21e9547`).
- Internal-only and absent from the dump, as intended: `PlanSchema` (`submitPlanSpec`, `planToolName`), `PlanParse`
  (`PlanStep`, `ParsedPlan`, `PlanVerdict`, `parsePlan`), `PlanBinding` (`bindArguments`, `referencedStepIds`,
  `mergeTargets`), `PlanRun` (`PlanRun`, `RunStop`, `outcomeOf`), `PlanReplan` (`replanRequest`, `rejectionDigest`),
  `PreparedStep` (`prepareGuarded`), `StepSubmission`. The dump holds no `PlanStep`, `ParsedPlan`, `PlanRun` or
  `PlanVerdict` class.

## remainingStepIds is +-only (RT-01 point 4)

Block diff of the three outcome classes, committed `core/api.txt` against the dump (the verify command's own diff; `<`
would be a removed or changed line, `>` an added one):

```
=== CommandOutcome.Completed
2a3
>     method @InaccessibleFromKotlin public java.util.List<java.lang.String> getRemainingStepIds();
6a8
>     property public java.util.List<java.lang.String> remainingStepIds;
=== StrategyOutcome.Completed
(no difference)
=== StrategyOutcome.Escalate
(no difference)
```

Zero removed lines, two added lines (the getter and the property of `remainingStepIds`). Why the form is binary-additive:

- It is a new getter on a class whose constructor was already internal, so no consumer can construct `Completed` and no
  public constructor changed signature.
- `CommandOutcome.Completed` is not a data class: there is no `copy` and no `componentN` to break.
- No default argument is used anywhere, so no synthetic `$default` stub is frozen.
- The ids reach the command outcome through internal constructors on `StrategyOutcome.Completed` and
  `StrategyOutcome.Escalate`, beside their unchanged public ones. This is the 14-SURFACE-REVIEW `Extraction` precedent
  (the longer primary constructor is internal, the public 2- and 3-argument ones keep their signatures).

Shipped mechanics (plan 15-04): the list is the planned steps the tier never handed to the app's executor, in plan order.
The held step is not listed (it is `outcome.held`); a failed step is not listed (it was attempted and is in `executed`);
a step stopped by an unresolved reference is listed (it was never prepared). It is filled on every early stop that ends
the command (a hold in either RT-01 case, and a failure after work that the engine suppresses) and empty when the plan
ran to its end, after `commitHeld`, after a hand-up to the next tier and for every other tier. `toString` prints the
count only. The ids are model-written text, so the KDoc says to show them as data.

## Member-by-member review

| Member | Decision | What a consumer asks next | Addable without removal? | Verdict |
|---|---|---|---|---|
| `PlanThenExecuteStrategy(id) { }` (`Companion.invoke`) | D-01, D-09 | "can I build one from a ready-made request or a tool subset?" | yes: a new `Companion` function or a new builder member | keep |
| `PlanThenExecuteStrategy.id` / `capabilities` / `execute` | D-01 | n/a | the `CommandStrategy` contract; nothing to remove | keep |
| `PlanThenExecuteStrategy.toString()` | D-09 | n/a | prints the id and the step limit only; redaction pinned by tests | keep |
| `Builder.tooling` | D-01 | "different tools per command phase?" | yes: the provider already receives the input; a richer seam is a new member | keep |
| `Builder.executor` | D-01, D-03 | "can the executor see the step id or plan position?" | yes: a new overload on a new seam; `ToolExecutor` is untouched | keep |
| `Builder.capabilities` | D-12 | "an on-device plan tier?" | yes: the type is the existing `StrategyCapabilities` | keep |
| `Builder.userTurn` | D-01 | "a planning-specific renderer?" | yes: a new member; the standard renderer stays the default | keep |
| `Builder.clock` | D-01 | n/a | yes | keep |
| `Builder.reasoning` | D-01 | "reasoning on only for the replan?" | yes: a new member; `OFF` stays the default | keep |
| `Builder.maxSteps` (default 8, at least 1) | D-12 | "a token-based plan budget?" | yes: a new member; the step cap keeps its meaning | keep |
| `Builder.onFailed` | D-09 | n/a | same hook shape as SingleShot; a richer hook is a new member | keep |
| `Companion.invoke` | D-01 | see first row | yes | keep |
| `TraceCode.PLAN_REJECTED` | D-02 | n/a | open set, additive | keep |
| `TraceCode.PLAN_REPLANNED` | D-08 | n/a | open set, additive | keep |
| `TraceCode.PLAN_BINDING_UNRESOLVED` | D-06 | n/a | open set, additive | keep |
| `CommandOutcome.Completed.remainingStepIds` | RT-01 | "which tool and arguments did each never-run step have, and what is the held step's id?" | yes: a new property on `Completed` or on `HeldProposal`, whose constructors are internal | keep |

No row is a "no": every member can grow by adding a member or an overload.

## Frozen model-facing strings

Everything below is written into consumers' planning prompts, tests or traces once v1.1.0 is tagged. A change after the
tag needs a recorded probe failure and a gap plan.

**Tool name.** `submit_plan`. It is the first tool of the request, the tool choice is `Required(submit_plan)`, and the
request is single-tool-call with the cache directive on. A snapshot that already offers a tool of that name fails the
command before any call with `Failed(Other("plan_tool_name_taken"))`.

**Schema** (sent non-strict, not mutating, not terminal; key order is byte-stable so provider prompt caches keep
hitting). For a snapshot with the non-terminal tools `create_item` and `tag_item` and the default `maxSteps` 8, the exact
compact JSON is:

```
{"type":"object","properties":{"steps":{"type":"array","maxItems":8,"items":{"type":"object","properties":{"id":{"type":"string"},"tool":{"type":"string","enum":["create_item","tag_item"]},"arguments":{"type":"object"}},"required":["id","tool","arguments"]}},"needs_lookup":{"type":"boolean"}},"required":["steps"]}
```

The `enum` lists every non-terminal snapshot tool in snapshot order (reads included, terminal tools excluded); `maxItems`
is the Builder's `maxSteps`. `PlanSchemaTest` pins this string.

**Description** (quoted from `PlanSchema.kt`):

> Submit the whole plan for the command as ordered steps, each calling one of the listed tools with its arguments. Give
> every step a short id that starts with a letter and uses only letters, digits, _ or -. To use a value that an earlier
> step's tool returns, write the whole argument value as the string $<stepId>.<key>, with the key names given in that
> tool's description. A reference is the entire value, never part of a longer string, and only names a step listed
> earlier. Do not call any other tool. When the command needs information you do not have, for example something that
> must be looked up first, set needs_lookup to true and leave steps empty.

**D-04 reference grammar** (the mechanics refine, and leave unchanged, the human-locked syntax):

- Step id: `^[A-Za-z][A-Za-z0-9_-]*$`, letter-leading, so a dictated `$5.00` can never name a step.
- A reference is a JSON string whose ENTIRE value is `$<id>.<key>`; the key is one or more non-whitespace characters
  (so it may contain dots; key names belong to the app).
- Literals, never touched: `$5.00`, `$s1`, `$s1.`, a reference inside a longer string, and `$$s1.x`. There is no `$$`
  escape in this version.
- Object keys and non-string values (numbers, booleans, null) are never read or rewritten; arrays and nested objects are
  walked. Binding works on string primitives of the parsed tree, never on serialized text.
- A bound value is always a JSON string (the app owns key names and the target ids a committed step reported).
- A reference-shaped string naming an id not declared by an earlier step (a forward reference, or the step itself) is a
  static rejection (`bad_reference`), not a literal. A reference to a declared step whose result lacks the key is a
  dynamic failure: that step is not prepared and the run stops with `plan_binding_unresolved`. Only a COMMITTED step is
  bindable; a held, previewed or errored step never is, and two actions that disagree on a key drop it.

**Digest vocabulary** (the one replan answers the model's `submit_plan` call with this as its tool result; engine codes
and an index only):

```
{"status":"plan_rejected","reason":"<code>","step_index":<n or null>}
```

`reason` is one of `malformed`, `too_many_steps`, `empty`, `unknown_tool`, `terminal_tool`, `bad_id`, `duplicate_id`,
`bad_reference`, `step_failed`; `step_index` is the engine's 0-based index of the first offending step, `null` when the
whole plan is at fault. Every other call of the same answer gets the fixed notice
`{"status":"error","reason":"ignored_call"}` so no call id is left without a result. The replan is the same
conversation, one turn longer, with the system text, tools, tool choice and settings copied unchanged (prefix
byte-comparison tested).

**Escalation codes** (`EscalationReason.Other(...)`): `plan_needs_lookup` (the plan named a read tool or set
`needs_lookup`; nothing ran, the incoming carry is forwarded), `plan_step_held`, `plan_step_failed`,
`plan_binding_unresolved`. A rejected, truncated or unreadable plan after the one replan escalates as
`MalformedExtraction`; an answer with no tool call as `NoToolCall`.

**Failure codes** (`FailureReason.Other(...)`, before any call): `plan_tool_name_taken`, `plan_no_tools` (no non-terminal
tool offered).

## KDoc gaps found and fixed

None needed. Every public member in `PlanThenExecuteStrategy.kt` has KDoc that states its contract (the order of
requests, the one replan and when it is allowed, the hold behavior, what is never logged), and the
`CommandOutcome.Completed` KDoc covers both `partial` and `remainingStepIds`, including the held, failed and unresolved
cases. The KDoc agrees with the behavior read in the code and uses no domain word. No source file was edited by this
plan, so no signature changed and the committed `api.txt` files are untouched.

## Open items for orchestrator

Non-blocking; each ships with the default stated. Changing one after the v1.1.0 tag is a behavior change for consumers
who already compiled against it, so overturn before the cut if at all. The driver relays OI-3 .. OI-7; OI-1 and OI-2 are
recorded for the record.

- **OI-1 (RESOLVED by RT-01, not pending; orchestrator 3b binding ruling via master relay, 2026-10-06; SB 177 objection adopted).** Refines D-07. No last-step carve-out: a hold with nothing committed escalates `Other(plan_step_held)` and the engine suppresses it into a partial `Completed`; a hold after a commit (last step included) is a terminal partial `Completed` that keeps the commits and `outcome.held` and never escalates. The never-run ids are on `CommandOutcome.Completed.remainingStepIds`, reviewed above as a +-only surface addition. Shipped mechanics: the held step is not listed, an unresolved-reference step is listed, it is filled on every early stop that ends the command (not only holds) and empty after `commitHeld`. Narrowing the fill rule to holds only is a one-line change plus tests, before the tag only. A single-step plan whose step is held is the no-commit case: partial, with a suppressed escalation in the trace.
- **OI-2 (ACCEPTED as is by the orchestrator on 2026-10-06, not pending).** Refines D-04. The grammar detail set above: letter-leading ids `^[A-Za-z][A-Za-z0-9_-]*$`, a non-whitespace key, an undeclared (forward or self) reference is a rejection rather than a literal, no `$$` escape in v1.1. Plan 15-07's live probe still exercises it on the cheap models.
- **OI-3 (pending).** Refines D-04. Live probe status: the probe is approved (`15-LIVE-PROBE.md`, `decision: approved`, relayed by the orchestrator, 2026-10-06) and runs in plan 15-07 (host-only, at most 8 requests / USD 0.05, once). Final status (PASS, FAIL or the deferred obligation) is filled in by plan 15-07, which replaces this line. If it FAILs, the description text and the grammar above are the frozen strings that a gap plan would change, before the tag.
- **OI-4 (pending).** Refines D-03 (SB condition #4). `Extraction.callId` for every plan step is the planning call's id, and the step identity is its position (distinct, rising ordinal per `ExecutedAction`), not the call id. Overturning it would mean a per-step id, which is a new member and a behavior change for undo grouping.
- **OI-5 (pending).** Refines D-08 and D-06. A first-step gate fault or error step before any write (nothing applied or held) is replanned once, under the single predicate (a replan is left, nothing applied or held, token ceiling not reached). Overturning it to "never replan on a step failure" means removing one branch and one test; after the tag it changes how many provider calls a failing command costs.
- **OI-6 (pending).** Refines D-05 (research A2). A PREVIEW, READ or no-action result from a mutating step counts as a failed step: nothing committed, so it is not bindable and the run stops. An app that previews inside a plan would see its plan stop and escalate (or, after earlier work, end partial); documented in the KDoc.
- **OI-7 (pending).** Refines D-08 (research A4). The replan digest carries engine codes and an index only, never app content (no `contentForModel`, no model text, no model id). Replans may be less effective for app-specific validation errors; extending the digest later is additive.

## Source audit

| Source | ID | Item | Plan(s) | Status |
|---|---|---|---|---|
| GOAL | n/a | A command needing several writes runs as one planning call plus gated steps, escalating on anything the plan cannot do | 15-01 .. 15-05 | DONE (JVM-proven; live probe in 15-07) |
| REQ | PLAN-01 .. PLAN-05 | Plan tier, binding, replan, hold, limits and redaction | 15-01 .. 15-05 | DONE on the JVM; 15-06 reviews the surface; 15-07 closes the live probe |
| CONTEXT | D-01 .. D-12 | As recorded in `15-CONTEXT.md` | 15-01 .. 15-06 | DONE (surface reviewed here); D-04 live check pending in 15-07 |
| CONTEXT | RT-01 | Hold semantics and `remainingStepIds` | 15-04, 15-06 | DONE (surface addition shown +-only above) |

## Gate results

Run once, alone, on the phase branch at HEAD `5c26b69` on 2026-10-06 (the review commit; no source file changed in this
plan). Host-safe recipe: `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false
-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`, `--offline -q`. No earlyoom kill, no
retry.

| Command | Exit | Final line | Duration |
|---|---|---|---|
| `scripts/review-api-surface.sh --out <scratch>/vae-15-core-dump.txt` (Task 1) | 0 | `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=196` | under 60 s |
| `./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false check` | 0 | exit 0 (quiet mode prints no task count; the detekt negative-control findings and the sample lint report line in the output are expected output, not failures) | 175 s |
| `scripts/verify-docs-coverage.sh` | 0 | `DOC COVERAGE OK checks=25 types=104` | under 10 s |
| `scripts/verify-repo-hygiene.sh` | 0 | `HYGIENE OK` | under 10 s |

`check` covers detekt (zero baseline on main, test and testFixtures), the banned-construct scanner including CLN-02,
`ApiShapeTest`, `NoHardCodedConstantsTest`, Metalava compatibility against the committed `core/api.txt`, and both OkHttp
matrix legs.

### Phase invariants (git, against `21e9547`)

| Check | Result |
|---|---|
| `core/api.txt`, `providers/api.txt`, `keystore/api.txt` byte-identical | yes (`git diff --quiet 21e9547` exits 0; `git status --porcelain` on the three paths is empty) |
| `strategy/StepSubmission.kt` and every file under `strategy/singleshot/` byte-identical | yes |
| the only file changed under `strategy/agentic/` | `AgenticDispatch.kt` (plan 15-01: its `prepare` now delegates to the shared `prepareGuarded`) |

Stated honestly: `core/api.txt` being byte-identical means only that no dump was committed early. The public surface did
grow (the `core.strategy.plan` package, three `TraceCode`s and, per RT-01 point 4, `CommandOutcome.Completed.remainingStepIds`).
Metalava's compatibility check inside `check` accepts those additions against the committed snapshot, and the block diff
in "remainingStepIds is +-only" is the +-only proof until the v1.1.0 cut writes the new `api.txt`.

### Main files changed since `21e9547` outside `strategy/plan/`, `PreparedStep.kt` and `telemetry/TraceCode.kt`

| File | Plan | Reason |
|---|---|---|
| `core/strategy/agentic/AgenticDispatch.kt` | 15-01 | `prepare` delegates to the shared `prepareGuarded` (the fixed `tool_error` notice moved with it, byte-identical) |
| `core/strategy/StrategyOutcome.kt` | 15-04 | internal primary constructors carrying the never-run ids; public constructors unchanged |
| `core/pipeline/CommandOutcome.kt` | 15-04 | the one public addition, `Completed.remainingStepIds`, plus KDoc and a count-only `toString` |
| `core/pipeline/TierWalk.kt` | 15-04 | passes the ids on when the tier ends the command, drops them on a hand-up |
| `core/pipeline/HeldCommit.kt` | 15-04 | the `commitHeld` outcome passes an empty list |

Nothing else changed in main; no file needs a report to the driver. `15-VALIDATION.md` was not
edited (the Nyquist finalizer owns it).
