# Phase 19: Sample Gate-1 & Docs - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 27 new/modified
**Analogs found:** 27 / 27 (all tracked sources; no gitignored mirror paths used)

Paths under `sample/` are abbreviated `S/` = `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample`, `T/` = `sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `S/evidence/EvidenceLine.kt` (LegId + ALLOW_PATTERN + TRACE/UNDO factories) | model/vocabulary | transform | itself (extend `LegId`, `ALLOW_PATTERN`, `outcome()`) | exact |
| `scripts/sample-evidence-filter.sh` (ALLOW_RE) | utility (filter) | transform | itself, line 16 | exact |
| `sample/src/test/resources/evidence-lines.golden.txt` | test fixture | transform | itself | exact |
| `scripts/verify-sample-device-guard.sh` (counts, scenarios) | test (bash) | request-response | itself, `capture_save_happy` + `leg_list_parity` | exact |
| `S/legs/LegCatalog.kt` (new `LegKind`s + `LegSpec`s) | config | CRUD | `smoke()` / `multi()` / `ver02` / `RESPONSES_PROBE` specs | exact |
| `S/legs/LegRunner.kt` (grammar/plan/router/undo kinds, judge) | service | request-response | `runDemo` + `demoTier` + `demoVerdict` (offline), `judge`/`finish` (live) | exact |
| `S/legs/TripwireProvider.kt` (new) | provider (fake) | request-response | `S/legs/DemoProvider.kt` | role-match |
| `S/undo/*` plan/undo tool specs + executor + Hold-Nth gate (new) | service | CRUD | `S/undo/ItemMutations.kt`, `UndoCommitSink.kt` | exact |
| `S/AppGraph.kt` (wire store/journal/legs) | config (composition root) | request-response | itself (`LegRunner(` at line 109) | exact |
| `S/ui/UiTags.kt`, `SampleScreen.kt` (`undo_all`, `undo_label`) | component | event-driven | `UiTags.run/status` + clarify option buttons | role-match |
| `T/GrammarLegTest`, `PlanLegTest`, `RouterLegTest`, `UndoLegTest` (new) | test | request-response | `T/MultiTurnLegTest.kt`, `AgenticLegTest.kt`, `ClarificationFlowTest.kt`, `LegTestSupport.kt` | role-match |
| `T/EvidenceLineTest.kt` | test | transform | itself | exact |
| `scripts/run-sample-gate1.sh` (decision file env, LEGS, dirty list) | utility (host runner) | request-response | itself, lines 41-55, 275 | exact |
| `scripts/verify-docs-coverage.sh` (manifest-driven C01/C20/C07 + C26-C32 + selftest) | utility (gate) | batch | itself (`check_C03` already reads `MODULE_ALT`); `scripts/verify-stt-confinement.sh --selftest` for selftest shape | exact |
| `scripts/agent-wiring-test.sh` + assets under `.planning/releases/v1.1.0/wiring-test/` | utility (test harness) | batch | itself; assets at `.planning/milestones/v1.0-phases/10-sample-harness-gate-1-docs/wiring-test/{AGENT-PROMPT.md,reference/{Wire,WireTest,AppWire}.kt}` | exact |
| `scripts/api-dump-isolated.sh` / `scripts/review-api-surface.sh` (`--module`) | utility | batch | itself | exact |
| `scripts/jitpack-consumer-probe.sh` (`:adapteralone`) | utility | batch | itself, `:undoalone` block lines 24-91 | exact |
| `scripts/jitpack-dry-run.sh`, `jitpack-live-probe.sh` (re-run only) | utility | batch | n/a, run per `.planning/phases/18-voice-adapter/18-QUIET-WINDOW.md` | exact |
| `voice-adapter/.../LanguageLabels.kt`, `FinalSegmentMapping.kt` (RT-04 drop) | utility | transform | the surviving no-arg/full-arg forms in the same files | exact |
| `voice-adapter/.../AdapterApiShapeTest.kt` (+ `FinalSegmentMappingTest`, `LanguageLabelsTest` line fixes) | test | transform | itself (reflection pin) | exact |
| `README.md`, `INTEGRATION.md`, `API.md`, `ECOSYSTEM.md` | docs | n/a | existing sections (INTEGRATION 11/12, "Choosing where the model walk starts") | exact |
| `sample/src/test/kotlin/.../sample/docs/DocSnippetsTest.kt` | test | transform | itself (13 existing regions) | exact |
| `.planning/phases/19-.../19-LIVE-LEG-DECISION.md` | planning artifact | n/a | `12-LIVE-LEG-DECISION.md` | exact |
| `.planning/phases/19-.../19-QUIET-WINDOW.md` | planning artifact | n/a | `18-QUIET-WINDOW.md` (and 17-QUIET-WINDOW.md) | exact |
| `.planning/phases/19-.../evidence/gate1-*.txt` + `gate2-carry-register.txt` | evidence | batch | `12-wave-1-seams-w04-fix/evidence/gate1-responses_probe.txt`; v1.0 carry register under `.planning/milestones/v1.0-phases/10-*/evidence/` | exact |
| `.planning/uat-pending/19-sample-gate-1-docs.md` | planning artifact | n/a | `.planning/uat-pending/18-voice-adapter.md` | exact |
| `.planning/phases/19-.../19-API-REVIEW.md` | planning artifact | batch | `17-SURFACE-REVIEW.md`, `18-SURFACE-REVIEW.md` | role-match |

## Pattern Assignments

### Evidence vocabulary lockstep (EvidenceLine.kt, filter, golden, guard, runner) - closed vocabulary, FIVE places

**Analog:** `S/evidence/EvidenceLine.kt`

LegId enum (lines 18-28): add `GRAMMAR_OFFLINE("grammar_offline")`, `PLAN_LIVE("plan_live")`, `ROUTER_LIVE("router_live")`, `UNDO_ALL("undo_all")`. Keep `fixtureBacked` (line 35) as is; the new legs are synthetic, so do not touch the `leg=ver02` LE-7 backstop.

Allow pattern (lines 56-58), to be widened with `TRACE|UNDO`:
```kotlin
internal const val ALLOW_PATTERN =
    "^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)" +
        "( [a-z0-9_]+=[A-Za-z0-9_.:/,\\[\\]-]{0,96})+$"
```
Lockstep copy in `scripts/sample-evidence-filter.sh:16` (identical alternation, alphabet written `[][A-Za-z0-9_.:/,-]`):
```bash
ALLOW_RE='^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)( [a-z0-9_]+=[][A-Za-z0-9_.:/,-]{0,96})+$'
```
Factory convention: private constructor, typed-value factories (`EvidenceLine.outcome(leg, summary)`, `.verdict(leg, verdict, extras, null, trigger)`); free text renders `invalid_token` through `VALUE_TOKEN` (line 46). Add `trace(...)` and `undo(...)` the same way. Fields are codes/counts/indexes only (tier IDENTITY as `picked_index`, never an id; Pitfall 26, D-08).

Golden file `sample/src/test/resources/evidence-lines.golden.txt` is one line per type (shape: `VAE_OUTCOME leg=ver02 kind=completed partial=false reason=none executed=1 ...`). Append one `VAE_TRACE` and one `VAE_UNDO` line. Adding N golden lines changes the guard's hard-coded counts.

Guard counts to bump (`scripts/verify-sample-device-guard.sh:359,365`): `kept=13 dropped=1` and `[ "$(grep -c '^VAE_' "$ev")" = 13 ]` become `13+N`. `leg_list_parity` (lines 373-378) compares `LEGS="..."` in the runner against the `LegId` wires; add the four new wires to `LEGS` (`run-sample-gate1.sh:55`) in the same commit, or that scenario fails:
```bash
runner_legs="$(sed -nE 's/^LEGS="([^"]*)"$/\1/p' "$RUNNER_SRC" | tr ' ' '\n' | sort | tr '\n' ' ')"
app_legs="$(sed -n '/enum class LegId/,/^}/p' "$EVIDENCE_KT" | grep -oE '\("[a-z0-9_]+"\)' | tr -d '()"' | sort | tr '\n' ' ')"
```
Evidence shape for D-13 reuses the existing line (do not mint a new one): `gate1-responses_probe.txt` of Phase 12:
```
VAE_OUTCOME leg=responses_probe kind=failed partial=false reason=model_unsupported executed=0 committed=0 held=0 reply_len=0 terminal_tool=none
VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui
```

---

### `S/legs/LegCatalog.kt` (config, CRUD)

**Analog:** same file. Spec constants + `LegKind` enum + `LegSpec` (lines 10-78) + builder `private fun smoke(...)`/`multi(...)` (88-140).

Conventions to copy: `private const val X_ITERATIONS`/`X_RESERVATION`; one `LegKind` entry per leg kind with a KDoc line; prompts "weakest first", synthetic, never fixture names; offline legs set `needsKey=false`, `reservation=0` (like DEMO_*). Add `GRAMMAR_OFFLINE`, `PLAN`, `ROUTER`, `UNDO_ALL` kinds. Reuse `HAIKU = "claude-haiku-4-5"` (line 10). Reservation arithmetic is gated by `RequestBudget` (`core + reservation <= 33`, `PER_CALL_WORST_CASE = 3`): research recommends plan reservation 6, router 9. `responses_probe` is already `PROBE_RESERVATION = 1` and `RESPONSES_ONLY_MODEL = "gpt-6-astra"` (line 19): no catalog change for D-13.

---

### `S/legs/LegRunner.kt` (service, request-response)

**Analog (offline legs):** `runDemo` + `demoTier` + `demoVerdict` (lines ~358-420) and the separate demo engine at line 132:
```kotlin
private val demoEngine = SampleEngine(listOf(demo), CredentialSource { CredentialLookup.Missing() }, demoSink, null)
```
Dispatch point (line 162) routes demo kinds before budget/key preconditions:
```kotlin
if (spec.kind == LegKind.DEMO_CLARIFY || spec.kind == LegKind.DEMO_PARTIAL) {
    return runDemo(spec, CommandInput(spec.prompts.first()), trigger, null)
}
```
Copy for `GRAMMAR_OFFLINE` and `UNDO_ALL`: add the new kinds to this branch (a `when` per kind is cleaner), build a pipeline through `SampleEngine.pipeline(tier=..., selection=ProviderSelection(DEMO_PROVIDER, DEMO_MODEL), policy=TierPolicy{...}) { listener = legListener; configure... }`, run `pipeline.execute(input)`, then end with exactly the two emits every leg must make (capture-save requires `VAE_VERDICT leg=<leg> `):
```kotlin
val summary = OutcomeSummary.of(outcome)
sink.emit(EvidenceLine.outcome(spec.id, summary))
sink.emit(EvidenceLine.verdict(spec.id, verdict, emptyMap(), null, trigger))
return LegResult(spec.id, verdict, outcome, summary)
```
Verdict shape: `demoVerdict` returns `Verdict.pass()` on a null failure code and `Verdict.fail(code)` otherwise; copy `partialShape`-style helpers (`when { !outcome.partial -> "not_partial"; ... else -> null }`) for the plan/undo/router shape checks. Grammar leg: `TierPolicy { offlineOnly = true }` (TierPolicy.kt:43) with a `TripwireProvider` as the only provider; PASS needs `tripwire_calls=0`, `provider_turns=0`, near-miss = `Unhandled` with `cappedByPolicy=true`.

**Analog (live legs):** `runLocked` lines 153-262 (`budget.recordRun`, `EvidenceListener(spec.id, sink, ...)`, `engine.pipeline(tier = tierFor(...), selection = ProviderSelection(spec.provider, spec.model), policy = TierPolicy { maxIterations = spec.maxIterations })`) and `judge(facts)` / `finish(facts, trigger)` (lines 263-335) which `when (facts.spec.kind)` on every kind: add the PLAN/ROUTER kinds there so the verdict path emits `VAE_VERDICT`. Router leg: `TierSelector.Router` (core `pipeline/TierSelector.kt:112`, `skipsSingleTier = true`) needs a ladder `[grammar, single, plan]` (>= 2 model tiers); verdict is FAIL `router_fallback` when `sel=router_fallback`, FAIL when `sel=none`, INCONCLUSIVE when `picked_index=0`.

Concurrency/refusal conventions to preserve: single `Mutex` `running.tryLock()`, `refuse(leg, reason, extras, trigger)` with `VerdictKind.REFUSED`, `autorun_before_ui` guard.

---

### `S/legs/TripwireProvider.kt` (provider fake, request-response)

**Analog:** `S/legs/DemoProvider.kt` (provider id `internal val DEMO_PROVIDER = ProviderId("demo")`, `AiProvider` impl with `CopyOnWriteArrayList`/`AtomicInteger` counters, `FollowUpContext.toString` prints lengths only). Copy: counters via `AtomicInteger`, typed `ModelResult` failure return, `toString` with no content. Expose `calls: Int` for the `tripwire_calls=` field.

---

### Stateful store plan/undo legs (`S/undo/*`)

**Analog:** `S/undo/ItemMutations.kt` (`CreateItem` returns the id the plan binds through):
```kotlin
return StepResult("created", false, null, mapOf("id" to item.id))   // line 29
```
Plan binding: step 2 `create_item(title, parent_id="$s1.id")`; tool descriptions must state returned key names (API.md "Strategies and tools"). PASS asserts store's second item `parentId == first.id`. `CannedToolExecutor` is NOT reusable here (no targetIds; D-04). Journal wiring copies the reference bridge `S/undo/UndoCommitSink.kt` (marker region `undo-bridge:start/end`, also the doc snippet):
```kotlin
class UndoCommitSink(private val journal: UndoJournal) : CommitSink {
    ...
    override suspend fun onAction(event: ActionEvent) { ... journal.record(group, parent, entry, action.kind == ActionKind.IS_ERROR, action.context as? UndoTicket) ... }
    override suspend fun onRunClosed(runId: String, termination: RunTermination) { ... journal.runClosed(group, runId, applied) }
}
```
Rules: `commandSink = compositeSink(UndoCommitSink(journal), sink)` journal FIRST; `UndoJournal { adapter(ItemAdapter(store)) }`; mutations pass `journal.newTicket()` as `context`. Hold-Nth gate for the partial case: `PreApplyGate { GateDecision.Hold(...) }` injected via `SampleEngine.pipeline(...) { gate = ... }` (the `configure` lambda runs last; default is `private val CANNED_ADMIT = PreApplyGate { GateDecision.Admit() }`, `SampleEngine.kt:18`). N counts applied actions (COMMITTED + IS_ERROR applied=true); held shown via `pendingHeld`, never counted. Do not use `UndoGroup` internals not in API.md.

---

### `S/AppGraph.kt` + `S/ui/UiTags.kt` / `SampleScreen.kt`

**Analog:** `AppGraph.kt:109` `private val runner = LegRunner(...)`: add store/journal/sink construction beside it; constructor params `demoSink: CommitSink = NoOpCommitSink`, `demo: DemoProvider` already exist, extend with the new collaborators the same way (default args keep tests compiling). UI tag convention (`UiTags.kt`):
```kotlin
fun run(leg: LegId): String = "run_" + leg.wire
fun status(leg: LegId): String = "status_" + leg.wire
```
`run_<wire>`/`status_<wire>` appear automatically for every `LegId`; add only `const val UNDO_ALL = "undo_all"` and `UNDO_LABEL = "undo_label"` (label "Undo all (N)" from `journal.group(key)?.count`, pending apart). The root sets `testTagsAsResourceId`; the runbook addresses tags by these strings.

---

### New sample JVM tests (`T/GrammarLegTest`, `PlanLegTest`, `RouterLegTest`, `UndoLegTest`)

**Analogs:** `T/LegTestSupport.kt` (shared rig), `T/MultiTurnLegTest.kt` / `T/AgenticLegTest.kt` (live-leg verdict tests with fakes), `T/ClarificationFlowTest.kt` (offline demo leg), `T/EvidenceLineTest.kt` (golden/allow-pattern assertions), and `sample/src/test/.../undo/UndoTestRig.kt` (store/journal rig from 17-08). Use `:core` testFixtures (`FakeAiProvider`, `ScriptedGate`, `NoNetworkGuard`, `RecordingCommitSink`, `ScriptedPicker`) already on the classpath (`sample/build.gradle.kts:53`), JUnit 4 + `runTest`. Add a test asserting every emitted line matches `ALLOW_PATTERN` and that no tier id/slot value appears.

---

### `scripts/run-sample-gate1.sh` (host runner)

**Analog:** itself. Replace the hard-coded decision file (lines 40-43):
```bash
PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/12-wave-1-seams-w04-fix}"
DECISION_FILE="$PHASE_DIR/12-LIVE-LEG-DECISION.md"
EVIDENCE_DIR="$PHASE_DIR/evidence"
```
with the env-override shape from RESEARCH Code Examples (`VAE_GATE1_DECISION_FILE`, derived `<NN>-LIVE-LEG-DECISION.md`, `VAE_GATE1_EVIDENCE_DIR`; default phase dir 19). Also keep `verify-sample-device-guard.sh` lines 19 and 127 (`PHASE_REL` / `12-LIVE-LEG-DECISION.md` literals) in step; add scenarios for: override wins, derived name, `decision: consumed` refuses `push-keys` (a `deferred` scenario exists, `consumed` does not). The dirty-path list (line ~275) is hard-coded:
```bash
git -C "$ROOT" status --porcelain -- sample core providers keystore scripts gradle build.gradle.kts settings.gradle.kts
```
Derive module dirs from the manifest (`. scripts/lib/modules.sh; vae_modules`) so `undo`/`voice-adapter` are included. The `push-keys` gate stays `grep -qx 'decision: approved' "$DECISION_FILE"`. Guard scenario mechanics: `run_scenario()` copies the runner + filter into a temp `repo/`, fakes `gradlew`/`adb` and asserts calls (`assert_calls <scenario> "-s R5CT10XNKQN ..."`); copy it for new scenarios. `UNCOUNTED=1` for non-counted scenarios.

---

### `19-LIVE-LEG-DECISION.md`

**Analog:** `.planning/phases/12-wave-1-seams-w04-fix/12-LIVE-LEG-DECISION.md`. Copy its first-line contract: `decision: pending|approved|consumed|deferred` (exact line the runner greps), `relayed_by:`, `date:`, "Approval record (relayed ...)" quoting the orchestrator answer verbatim, "Budget (bounded)" table (Leg / Where / Model / expected-ceiling requests), "Conditions" (TESTER `R5CT10XNKQN` only, announce window open/close, keys by file reference through `push-test-key` only, `verify-keys-gone` and `cleanup` before close, evidence inside closed vocabulary). Never self-author `approved`.

---

### `scripts/verify-docs-coverage.sh` (gate, batch)

**Analog:** itself. Vacuous spots to generalize (verified):
```bash
public_types() {
  grep -rhE '^public ' core/src/main/kotlin providers/src/main/kotlin keystore/src/main/kotlin 2>/dev/null \   # line 110: add undo, voice-adapter via vae_modules
...
check_C01() { ... for a in core providers keystore; do need "$f" "${COORD_PREFIX}${a}:"; done }     # line 127: for a in $(vae_modules)
REQUIRED_REGIONS="minimal-pipeline scripted-provider ... telemetry"                                   # line 41: add new region names (also `undo-bridge`)
```
`check_C03` already uses `MODULE_ALT`; `COORD_PREFIX='com.github.Ygaray.voice-action-engine:voice-action-engine-'` (line 42). Add a top-level-function pass for `:voice-adapter` (`^public fun (Recv\.)?name(`). New checks follow `check_C<NN>() { need "$file" "<token>" ... }`, registered through `run C<NN>`; tokens per RESEARCH "Coverage gate generalization" (C26 grammar, C27 plan, C28 router, C29 undo, C30 adapter, C31 RT-01 `ASCII` + RT-04 negative, C32 P12 seams). Selftest shape: copy `scripts/verify-stt-confinement.sh --selftest` (planted violation in a temp copy, expect red) so the generalization is proven non-vacuous.

---

### `scripts/agent-wiring-test.sh` + wiring assets

**Analog:** itself. Stale paths (lines 19-21):
```bash
PHASE_DIR="$ROOT/.planning/phases/10-sample-harness-gate-1-docs"
PROMPT="$PHASE_DIR/wiring-test/AGENT-PROMPT.md"
REFERENCE="$PHASE_DIR/wiring-test/reference"
```
Assets now live at `.planning/milestones/v1.0-phases/10-sample-harness-gate-1-docs/wiring-test/{AGENT-PROMPT.md,reference/{Wire.kt,WireTest.kt,AppWire.kt}}`. Relocate to `.planning/releases/v1.1.0/wiring-test/` behind `VAE_WIRING_ASSET_DIR`. `VAE_MODULES_FILE="${VAE_MODULES_FILE:-$ROOT/scripts/modules.list}"` and `. "$ROOT/scripts/lib/modules.sh"` (lines 22-24) stay (W3 already accepts every manifest module). Add `prepare-local <m2dir> <version>` reusing `make_workspace <repo_url>` with `file://$m2` (what `selftest` does); the existing `prepare` requires pushed SHA (`merge-base --is-ancestor $sha origin/main`) which fails with main unpushed through P19. Hard-coded `checks=9` (verify prints `WIRING TEST: PASS checks=9`; selftest greps `'^WIRING TEST: PASS checks=9$'`): update both together with new W10-W13 (GrammarPack/LocalGrammarStrategy, PlanThenExecuteStrategy, TierSelector.Router, UndoJournal + undoAll + `voice-action-engine-undo:<version>`). The agent run is dispatched by the master, not an executor (no Agent tool).

---

### `scripts/jitpack-consumer-probe.sh` (`:adapteralone`, RT-03a)

**Analog:** the `:undoalone` block in the same script (lines 24-91): project listed in `include(":app", ":jvmconsumer", ":undoalone")` (line 35), its own `build.gradle.kts` heredoc (line 49), a one-line compile source (line 74), gradle compile target list (line 75), dependency-tree assertions (78-91):
```bash
undo_deps="$(./gradlew --no-daemon -q :undoalone:dependencies --configuration runtimeClasspath)"
for m in voice-action-engine-core kotlinx-coroutines; do
  if grep -q "$m" <<<"$undo_deps"; then echo "PROBE FAIL: $m resolved on :undoalone runtimeClasspath (:undo must stand alone)" >&2; exit 1; fi
done
```
For `:adapteralone` use an Android library/app project (the adapter is an AAR; not `kotlin.jvm`), depend on the adapter coordinate PLUS `:stt` v0.7.0 explicitly, assert the adapter resolves and compiles, and add a negative assertion that a project WITHOUT the adapter does not resolve the `io.github.ygaray`/`com.github.Ygaray.voice-engine-android` group.

---

### RT-04 voice-adapter overload drop

**Analog:** surviving forms in the same files.
`voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt` keeps 2-arg and 4-arg; delete the 3-arg:
```kotlin
public fun commandInputOf(transcript: String, languageLabel: String?, context: Any?): CommandInput =
    commandInputOf(transcript, languageLabel, context, null)
```
`FinalSegmentMapping.kt` keeps `toCommandInput()` and `toCommandInput(context, parentRunId)`; delete `toCommandInput(context: Any?)` (line 24). Rewrite surviving KDoc: drop the "A String passed here is a context object" notes and the `commandInputOf(..., context, parentRunId)` "use the four-argument form" pointer. No-arg form delegates `toCommandInput(null, null)`: keep.

Test update, `AdapterApiShapeTest.kt` (reflection pin; pins THREE overloads today):
```kotlin
assertEquals(listOf("toCommandInput", "toCommandInput", "toCommandInput"), methods.map { it.name })
...
assertEquals(listOf("commandInputOf", "commandInputOf", "commandInputOf", "normalizeSttLanguageLabel"), names)
```
become two each, signature sets `(FinalSegment)` and `(FinalSegment, Any, String)`; labels `(String, String)` and `(String, String, Any, String)`. Add the "no longer resolves" proof as a reflection assertion (a compile failure cannot be asserted): no public static of `labelFacade` has parameter types `(String, String, Object)`, none of `segmentFacade` has `(FinalSegment, Object)`. Also edit `FinalSegmentMappingTest` (~line 61 `toCommandInput(context)`) and `LanguageLabelsTest` (~line 67 `commandInputOf("hello","en",context)`), plus API.md rows ~214-215 ("Three overloads") and INTEGRATION.md section 12 ("two more overloads"). Test class names/facades: `...voiceadapter.FinalSegmentCommandInput`, `...voiceadapter.SttLanguageLabels`. `ActionEvent.toString` KDoc (core `commit/CommitSink.kt:37-45`) is already aligned (commit 149e906); confirm only.

---

### `scripts/api-dump-isolated.sh` / `scripts/review-api-surface.sh` (D-12)

**Analog:** themselves. `review-api-surface.sh` reviews only `:core` (sealed allow-list of seven types; no `copy/componentN`/enum/public static field). Add `--module <name>` with allow-list for `undo` = `undo.UndoResult` (`public sealed class UndoResult`). Run `api-dump-isolated.sh --out <dir>` (all five modules via manifest; never rewrites committed `api.txt`). Output artifact shape: copy `17-SURFACE-REVIEW.md` / `18-SURFACE-REVIEW.md` ("Gate results", open items). Do not create `api.txt` in P19 (P20 owns it).

---

### Docs + `DocSnippetsTest`

**Analog:** `sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt`; regions are `// doc-snippet:start <name>` ... `// doc-snippet:end <name>` (existing: scripted-provider, minimal-pipeline, register-providers, agentic-tier, gate-suspend, gate-defer, keystore-wiring, keystore-fake, fixed-credentials, render-outcome, clarification-follow-up, telemetry, undo-bridge). Rules (C06/C07): doc kotlin fence = byte-equal copy of the region after common-indent removal; each fence preceded by `<!-- doc-snippet: name -->`; imports go in a ```` ```text ```` block; every region used by some doc; C21 bans the words food/card and `*_note(s)/_card(s)/_food(s)/_meal(s)` tool names; C25 bans `~/Projects` and `/home/x` paths. New regions: `grammar-tier`, `plan-tier`, `router-selector`, optional `undo-wiring`, `adapter-input` (only `commandInputOf`; a `FinalSegment` form needs `:stt` on `:sample` testImplementation, recommended `testImplementation(project(":voice-adapter"))` + `testImplementation(libs.stt.engine)` rather than `implementation`). `undo-bridge` is a byte copy of `S/undo/UndoCommitSink.kt` (`UndoBridgeParityTest` covers it).
Doc gaps: README pin inside `pin-version` markers `v1.0.1` -> `v1.1.0`, add `undo` and `voice-adapter` coordinate lines; INTEGRATION step 2 coordinate block + new grammar and plan subsections + router snippet + section 12 (RT-04); API.md RT-01 sentence on ASCII-only `\s` (regex at `PlanBinding.kt:13`) and the `UndoResult.code` doc; ECOSYSTEM status text only (never the tooling-maintained repin matrix). Docs reach final state at the wiring SHA; no doc edit after.

---

### Quiet-window artifact and heavy scripts (RT-02, RT-03)

**Analog:** `.planning/phases/18-voice-adapter/18-QUIET-WINDOW.md` (and 17-QUIET-WINDOW.md). Copy header block verbatim in shape:
```
grant: consumed          # pending|open|deferred|consumed; ONLY the orchestrator relay may change it
requested: / timebox_s: / relayed_by: / date: / opened: / closed:
## Relay        (what is run, no device/keys/spend, HEAD sha at request, fallback)
## Relay log (verbatim)   ("CONFIRMED by orchestrator ... holds the VAE build lock ... MemAvailable 8.7 GiB ... STOP if MemAvailable < 5 GiB; re-run a killed step ONCE; master sends 'quiet done'")
## Pre-checks   (run recipe, MemAvailable/swap readings, pgrep daemon check)
## Results      (### Step N: <script>, started/finished UTC, exit status, ok lines verbatim)
```
Run recipe: `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`, one Gradle process at a time, memory check between steps. Timebox: negative controls alone took ~58 min, so request >= 2.5 h (`timebox_s: 9000`), not 3600. Steps to list: `verify-stt-confinement.sh --selftest` (bash only, any time), `verify-negative-controls.sh`, `verify-api-dump.sh`, `:voice-adapter:check`, `jitpack-dry-run.sh` (with `:adapteralone`, `KEEP_WORK=1` to keep the m2 dir for wiring `prepare-local`), wiring `selftest` + isolated agent run + `verify`, `jitpack-live-probe.sh` (needs a built ref; main is unpushed, so exercise it against tag `v1.0.1` with `EXPECT_MODULES=... SKIP_CONSUMER=1`; the real undo/adapter path is P20's after the push; surface this to the master as an open question).

### Gate-2 fragment and carry register

**Analog:** `.planning/uat-pending/18-voice-adapter.md`: sections `Status` (`pending`), `Milestone`, `Gate 1 self-UAT log` link, `Items covered`, `Owner how-to-verify`, `Note`. Carry register: `evidence/gate2-carry-register.txt` in the v1.0 `C<n>` block shape (source / what the phase does / disposition / evidence pointer) with the deferred `submit_plan` cache-prefix measurement as an explicit row (never silent) and the P18 fragment's `:adapteralone` discharge pointing at `19-QUIET-WINDOW.md`.

## Shared Patterns

### Closed evidence vocabulary and no-content rule
**Source:** `EvidenceLine.kt` (`VALUE_TOKEN`, private constructor) + `scripts/sample-evidence-filter.sh` (`ALLOW_RE`, `KEY_RE`, `fixture_leak`).
**Apply to:** every new `VAE_` line and every new leg. Values are enums, counts, indexes, booleans, stable `[a-z_]` codes (`TraceCode`, `UndoResult.code`, `UndoReason.value`). Never a tier id, transcript, slot value, tool argument or key. The filter rejects the whole capture on a key-shape/credential-word hit.

### TESTER-only device access
**Source:** `scripts/run-sample-gate1.sh` (`adb_t` timeout wrapper with fd 9 closed, lock file `vae-keystore-tester.lock`, `PERSONAL_IP` refusal, identity proof `EXPECTED_MODEL="SM-S908U"`), `~/.claude/context/devices/common.md`.
**Apply to:** all device legs. Every adb call carries `-s R5CT10XNKQN`; one install and one `build-install` for the whole window (uninstall wipes the in-app budget counter); `verify-keys-gone` and `cleanup` at the end; first run of a live leg from UI (autorun refused before first UI run).

### Key custody
**Source:** `~/.claude/context/workflows/test-keys.md`, `PUSH_TEST_KEY` in the runner.
**Apply to:** plan, router, astra legs. `push-test-key openai|anthropic --device R5CT10XNKQN --package io.github.ygaray.voiceactionengine.sample`; missing key = loud stop; decision file must read `decision: approved` from a relayed answer.

### Manifest-driven module lists
**Source:** `scripts/lib/modules.sh` (`vae_modules`, `vae_module_field`, `vae_artifacts`), `scripts/modules.list` (core, providers, keystore, undo, voice-adapter).
**Apply to:** coverage gate C01/C20, runner dirty list, wiring test, probe scripts. Never a copy-pasted module literal (Pitfall 9).

### Serial heavy gates
**Apply to:** every Gradle-running plan: one Gradle process at a time (earlyoom), `MemAvailable >= 5 GiB`, heavy runs only inside an orchestrator-granted quiet window ("quiet window 19-<plan>" handshake, orchestrator holds the build lock, master sends "quiet done").

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `submit_plan` cache-prefix leg | live leg | request-response | deferred by D-03 (needs the private SB-sized fixture and LE-7 filter extension); carry-register row only |
| UI "Undo all (N)" handler emitting `VAE_UNDO phase=undone|refused` | component | event-driven | no existing sample control mutates a store through the journal; closest are clarify-option buttons in `SampleScreen.kt` (planner may fall back to running `undoAll` inside the leg) |
| Wiring-test `prepare-local` | utility | batch | no local-Maven prepare exists; `selftest` already builds a workspace against `file://$m2` and is the pattern to lift |

## Metadata

**Analog search scope:** `sample/src/{main,test}`, `scripts/`, `voice-adapter/`, `.planning/phases/{12,17,18}-*`, `.planning/uat-pending/`, `.planning/milestones/v1.0-phases/10-*/wiring-test`.
**Tracked-source note:** every named script, Kotlin file and phase doc is tracked in git (`git ls-files` confirmed for the scripts; sources are plain repo paths). The v1.0 wiring assets under `.planning/milestones/v1.0-phases/10-*` are archive paths, to be copied to `.planning/releases/v1.1.0/wiring-test/`.
**Pattern extraction date:** 2026-10-06
