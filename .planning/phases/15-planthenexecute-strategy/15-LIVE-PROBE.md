# Phase 15 live-probe request (D-04, binding syntax)

decision: consumed
relayed_by: orchestrator yahir-gsd-control-plane-3b via the milestone master
date: 2026-10-06
relayed_answer: "GO on 15-07: the host-only D-04 binding probe (S1 + S2) on claude-haiku-4-5 + gpt-5.4-mini. Hard ceiling 8 requests / USD 0.05, run once, no retries, keys only via with-test-keys --only anthropic,openai, output PLAN_PROBE lines only. This falls under Yahir's live-smoke approval (relayed by 3b)."

run_date: 2026-10-06
consumed: 2026-10-06, 4 requests (limit 8), one run, exit 0, no retries

requested: 2026-10-06
requested_by: executor of plan 15-03, for the orchestrator relay that plan 15-07 waits on

`livePlanProbe` makes no request until the line above reads exactly `decision: approved`. Only plan 15-07, quoting a
relayed orchestrator answer, may change it. Plan 15-03 made no live request and spent nothing: it only built the harness
(`PlanBindingLiveProbeTest`, the `livePlanProbe` task in `providers/build.gradle.kts`) and this request.

## What the probe checks

D-04 froze the reference syntax a plan step uses to name an earlier step's result: a JSON string whose whole content is
`$<stepId>.<key>`. The probe asks two cheap real models, through the real `PlanThenExecuteStrategy` and the real
transports, whether they write that syntax correctly and whether they leave dictated dollar amounts alone.

## Budget (bounded)

| Model | Scenario | Transcript idea | Expected / ceiling requests |
|-------|----------|-----------------|-----------------------------|
| claude-haiku-4-5 (Anthropic) | S1 | create an item and tag it: needs a whole-value reference | 1 / 4 |
| claude-haiku-4-5 (Anthropic) | S2 | two items named with dictated amounts (`$5.00`, `$3.50`): must not be read as references | 1 / 4 |
| gpt-5.4-mini (OpenAI) | S1 | as above | 1 / 4 |
| gpt-5.4-mini (OpenAI) | S2 | as above | 1 / 4 |

Totals: expected 4 requests, hard ceiling 8 requests (counted through each provider's `attemptObserver`, checked before
each scenario as used + 4 at most 8, never retried), ceiling USD 0.05. Each request is a short single-turn call with a
small plan answer; the expected cost is a few cents at most.

## Conditions

- Host-only Gradle task. No device, no TESTER window, no emulator.
- Keys travel only through `with-test-keys --only anthropic,openai`; the spend-capped test keys. Never read, cat or print
  the key files; the test reads the two environment variables and passes them to the transports only.
- Output is limited to `PLAN_PROBE` lines: model id, scenario, verdict, a reason code, call and replan counts, and two
  booleans. Never a body, an argument, an item id, a transcript or a key. The final line is
  `PLAN_PROBE requests=<n> limit=8`.
- One run, once. Not part of `check`, not part of any OkHttp matrix leg (the class name contains `Live`, so `:providers:test`
  and both legs exclude it).

## Pass criteria

- S1 PASS: the outcome is Completed (not partial), the executor saw `create_item` then `tag_item`, and `tag_item`'s
  `item_id` argument equals the id the create step published (`ref_bound=true`: the model wrote a whole-value reference and
  the engine resolved it). At most one replan.
- S2 PASS: the outcome is Completed, the executor saw exactly two `create_item` calls, and the trace holds no
  `plan_rejected` and no `plan_binding_unresolved` (the engine never treated the dictated amounts as references).
  `literal_kept=true` when each received name still holds a `$` followed by a digit. When it is false the line's reason is
  `literal_not_exercised`: the model rewrote the text, so the literal path was not tested. That is recorded, not an
  engine failure, and the scenario still counts as PASS.
- A scenario that would break the ceiling is `NOT_RUN` with reason `budget` and is never retried.
- The probe passes when every scenario that ran PASSed and the request total is at most 8.

## The command plan 15-07 runs

```
with-test-keys --only anthropic,openai -- env VAE_LIVE_PLAN=1 GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline --console=plain -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :providers:livePlanProbe
```

Run it in the foreground, once, and record only the `PLAN_PROBE` lines.

## What a FAIL means

A FAIL on `ref_not_bound` or `plan_rejected` for S1 is a wording problem first: reword the `submit_plan` description in a
gap plan, under a new bounded approval. Only a systematic inability of both models to emit whole-value references reopens
D-04, and that goes through the orchestrator, never as a syntax change inside this phase. A FAIL on S2 with
`plan_rejected` or `binding_unresolved` means the engine treated a dictated amount as a reference: that is an engine bug
and is fixed in a gap plan.

## Relay

Plan 15-07's executor records the answer as follows: replace the `decision: pending` line with the relayed value (exactly
`decision: approved` to allow the live call), and add `relayed_by:` and `date:` lines quoting the relay. Any value other
than `approved` means no live call is made.

After the run, replace the decision line with `decision: consumed`, add a `consumed:` line with the date and the request
count, and paste the `PLAN_PROBE` lines under a "Result" heading with an overall PASS or FAIL.

## Deferral path

If the orchestrator defers the probe, or a key or the network is missing, record

```
decision: deferred
deferred_obligation: D-04 live binding probe owed; owner: Phase 19 Gate-1 plan leg; must run before the v1.1.0 cut
```

in place of the decision line, with `relayed_by:` and `date:` lines, and make no live call.

## Result

overall: PASS

Run once on 2026-10-06 under the relayed GO above, via `with-test-keys --only anthropic,openai` (usage log: one run, exit=0, 24 s).
Raw output kept in the session scratchpad only. Every `PLAN_PROBE` line, verbatim:

```
PLAN_PROBE model=claude-haiku-4-5 scenario=S1 verdict=PASS reason=ok calls=1 replans=0 ref_bound=true literal_kept=false
PLAN_PROBE model=claude-haiku-4-5 scenario=S2 verdict=PASS reason=ok calls=1 replans=0 ref_bound=false literal_kept=true
PLAN_PROBE model=gpt-5.4-mini scenario=S1 verdict=PASS reason=ok calls=1 replans=0 ref_bound=true literal_kept=false
PLAN_PROBE model=gpt-5.4-mini scenario=S2 verdict=PASS reason=ok calls=1 replans=0 ref_bound=false literal_kept=true
PLAN_PROBE requests=4 limit=8
```

Reading: both models wrote a whole-value `$<stepId>.<key>` reference for S1 and the engine resolved it (`ref_bound=true`,
no replan); both left the dictated `$5.00` / `$3.50` literal in S2 (`literal_kept=true`, no `plan_rejected` /
`plan_binding_unresolved`). 4 requests of the 8 ceiling. The D-04 syntax and the `submit_plan` description stand as frozen.
