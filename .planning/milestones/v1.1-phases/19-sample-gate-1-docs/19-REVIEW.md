---
phase: 19-sample-gate-1-docs
reviewed: 2026-10-07T00:00:00Z
depth: standard
files_reviewed: 44
files_reviewed_list:
  - API.md
  - ECOSYSTEM.md
  - INTEGRATION.md
  - README.md
  - sample/src/debug/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/AppGraph.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/MainActivity.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/RequestBudget.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/GrammarLeg.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/PlanLegs.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/TripwireProvider.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/UndoLeg.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/SampleScreen.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemToolExecutor.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/EvidenceLineTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/GrammarLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/PlanLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RequestBudgetTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/RouterLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UndoLegTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
  - sample/src/test/resources/evidence-lines.golden.txt
  - scripts/agent-wiring-test.sh
  - scripts/jitpack-consumer-probe.sh
  - scripts/release-cut.sh
  - scripts/review-api-surface.sh
  - scripts/run-sample-gate1.sh
  - scripts/sample-evidence-filter.sh
  - scripts/verify-docs-coverage.sh
  - scripts/verify-sample-device-guard.sh
  - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt
  - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/AdapterApiShapeTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/DocSnippetAdapterTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMappingTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabelsTest.kt
findings:
  critical: 0
  warning: 4
  info: 8
  total: 12
status: issues_found
---

# Phase 19: Code Review Report

**Reviewed:** 2026-10-07
**Depth:** standard
**Files Reviewed:** 44
**Status:** issues_found

## Summary

Reviewed the Phase 19 delta (`137f3958^..090fd8ec76`, non-`.planning` content): the four root docs, the sample's new
legs (grammar, plan, router, undo-all), the evidence grammar extension (`VAE_TRACE`, `VAE_UNDO`), the voice-adapter API
trim, and the Gate-1 / docs / release scripts. Test files were checked for vacuity rather than style.

The core safety properties hold up under adversarial reading:

- Evidence redaction. Every new line type (`TRACE`, `UNDO`) draws its words from closed sets or typed engine codes, and
  the Kotlin `ALLOW_PATTERN` and the shell `ALLOW_RE` agree character for character. `Item.toString`, `UndoFacts`,
  `TraceFacts`, `CaseCounts`, `GrammarCase` and `TripwireProvider` all keep content out of `toString()`.
- Device guard. Every adb call in `run-sample-gate1.sh` carries `-s <TESTER>` (the one `connect` aside), identity is
  proven by serial, model and SDK, and keys move only by file reference. `verify-keys-gone` is a positive proof (sentinel
  line plus adb exit code), not an absence-of-output proof.
- Budget. The 15/16 ceilings match the approved decision file, and the test reads the real specs so a growing
  reservation fails.
- Doc claims I could check against code (`UndoResult.code` values, `start_tier_router`, `StartTierSelected`, the ASCII
  `\S` reference pattern, the grammar trace codes) are accurate.

No Critical findings. The Warnings are a weakened spend-gate control, a docs gate that no longer verifies what it
claims, a wiring judge that can be satisfied by comments, and a sample executor that rejects a legal optional-argument
shape. The Info items are minor robustness and drift points.

## Warnings

### WR-01: `VAE_GATE1_DECISION_FILE` lets the D-13 spend gate be satisfied by any file

**File:** `scripts/run-sample-gate1.sh:46-49` (consumed at `:213`)
**Issue:** The decision file used to be fixed at `<phase dir>/12-LIVE-LEG-DECISION.md`, so the only way to open the spend
gate was to edit the one tracked planning file that the relay protocol protects ("only plan 19-07, quoting that answer,
may change the decision line"). The new override lets `push-keys` (which moves live API keys to the device) be unlocked
by any path on the host containing a line `decision: approved`, with no tie to the repository, to `.planning/`, or to a
git-tracked file. The header comment says these variables "name PLANNING FILES only", which the code does not enforce.
`grep -qx` also matches that line anywhere in the file (for example inside a quoted block), not just as the recorded
decision.
**Fix:** Resolve the override and require it to live under `$ROOT/.planning/` and be tracked and unmodified, and anchor
the match to the record line.
```bash
DECISION_FILE="${VAE_GATE1_DECISION_FILE:-$PHASE_DIR/${PHASE_BASE%%-*}-LIVE-LEG-DECISION.md}"
# in host_precheck, push-keys:
decision_abs="$(realpath -m "$DECISION_FILE")"
case "$decision_abs" in "$ROOT/.planning/"*) ;; *) finish 2 ERROR "reason=decision_file_outside_planning" ;; esac
git -C "$ROOT" ls-files --error-unmatch -- "$decision_abs" >/dev/null 2>&1 \
  || finish 2 ERROR "reason=decision_file_untracked"
[ "$(sed -n 's/^decision: //p' "$decision_abs" | head -1)" = approved ] || { ...refuse...; }
```

### WR-02: Docs gate no longer verifies the pinned release exists; docs already announce an untagged `v1.1.0`

**File:** `scripts/verify-docs-coverage.sh:354-372`; `README.md` (pin-version marker, `**Status:**`);
`ECOSYSTEM.md` (`**Status:** v1.1.0 is released`, "first published in **v1.1.0**")
**Issue:** `check_C23` guards "no concrete v1 coordinate while the tag does not exist", but it asks git for `v1.0.0`,
which has existed since v1.0.0 shipped, so it now returns early and is permanently vacuous. `check_C24` only checks that
the pinned value has the shape `vX.Y.Z`. Nothing verifies that the tag the README tells integrators to pin is real.
At review time `git tag` holds only `v1.0.0` and `v1.0.1`, yet README pins `v1.1.0`, says "v1.1.0 is the current
release", and ECOSYSTEM says "v1.1.0 is released" and that `undo`/`voice-adapter` are "first published in v1.1.0". Any
integrator or agent who follows the docs before Phase 20 cuts the tag gets an unresolvable JitPack coordinate for every
module (and `undo`/`voice-adapter` never existed at v1.0.x). The wiring test sidesteps this by using a local
publication, so it cannot catch it either.
**Fix:** Make C23/C24 read the pinned tag and require it to exist (or fail the doc gate until Phase 20 tags), and keep the
"released" wording behind that same condition.
```bash
check_C23() {
  local pin; pin="$(sed -n 's/.*<!-- pin-version:begin -->`\(v[0-9.]*\)`<!-- pin-version:end -->.*/\1/p' "$README")"
  [ -n "$pin" ] || return 0   # C24 reports the malformed marker
  git rev-parse -q --verify "refs/tags/$pin" >/dev/null || fail C23 "README pins $pin but that tag does not exist"
}
```
Severity note: the docs-wording half is Info-class and the wiring record is frozen; the gate defect is why this is
raised as a Warning. Phase 20 must tag before (or atomically with) merging these docs to `main`.

### WR-03: Wiring-test judge checks W5, W6 and W10-W13 with bare word greps, so comments or strings satisfy them

**File:** `scripts/agent-wiring-test.sh:151-171`
**Issue:** W5 requires only that some line in `jvmconsumer/src/main` matches `else[[:space:]]*->` and some other line
contains the word `when`; W6 needs the word `partial` anywhere; W10-W13 need `GrammarPack`, `LocalGrammarStrategy`,
`PlanThenExecuteStrategy`, `TierSelector.Router`, `UndoJournal` and `undoAll` anywhere in a `.kt` file. A comment such as
`// TODO: TierSelector.Router, UndoJournal, undoAll, partial, when ... else ->` passes every one of them. The selftest's
planted bad copy only deletes the matching lines, so it proves the checks are not trivially true but not that they
require real use. The judge is the VER-04 release gate ("can a fresh agent wire the engine from the docs alone"), and the
W2 build/test check does not compensate (six tests that never touch these types still pass).
**Fix:** Strip comments before matching and require call/construct shapes, for example
`TierSelector\.Router[[:space:]]*[({]`, `UndoJournal[[:space:]]*[({]`, `\.undoAll\(`, `GrammarPack[[:space:]]*\{`; or add
a reference test the judge runs against the agent's `WireTest` classes. Add a selftest plant that moves the names into a
comment and requires W5/W10-W13 to fail.

### WR-04: `ItemToolExecutor` rejects an explicit `parent_id: null`

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/undo/ItemToolExecutor.kt:108-112`
**Issue:** `parent_id` is declared optional in `createItem`'s schema. `text()` returns null for a `JsonNull`, and
`arguments[ARG_PARENT]` is a non-null Kotlin reference for a `JsonNull` element, so `parent != null && parentId == null`
treats `{"title":"alpha","parent_id":null}` as `invalid_arguments`. Models (and strict-schema modes of OpenAI-compatible
providers) commonly emit `null` for an optional field they have nothing for. In the live `plan_live`/`router_live` legs
that turns the first `create_item` into a finished error step, ends the plan, and fails the leg on a shape the schema
allows, wasting scarce spend-capped requests in the single TESTER window. It also diverges from the "validated, never
echoes" contract the docs teach.
**Fix:** Treat `JsonNull` as absent.
```kotlin
val parent = arguments[ARG_PARENT]?.takeUnless { it is JsonNull }
val parentId = text(parent)
...
parent != null && parentId == null -> failed(ITEM_TOOL_CREATE, INVALID_ARGUMENTS)
```
(import `kotlinx.serialization.json.JsonNull`) and add a unit case for `parent_id: null`.

## Info

### IN-01: Sample's "Undo all (N)" ignores `withheld` and zero, contradicting the documented wiring

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/UndoLeg.kt:378`
**Issue:** `UndoSession.prompt = UndoPrompt(wholeRun.n, wholeRun.pending)` always produces a label. INTEGRATION.md's
`undoLabel` snippet (and its comment "a withheld group means the journal missed an action, so offer nothing") returns
null when `group.withheld || group.count == 0`. `CommandRun.withheld` is read and then dropped, so the README's
reference wiring and the sample disagree. In the sample a withheld group shows a live button that can only refuse.
**Fix:** Have `UndoLeg.start` return a prompt only when `!run.withheld && run.n > 0` (and let the leg fail loudly with a
dedicated reason otherwise), or note in the leg KDoc that it deliberately shows the failing path.

### IN-02: `LegRunner.undoAll` consumes the waiting session before it finishes

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt:427`
**Issue:** `awaitingUndo = null` is assigned before `session.finish(sink)`. If the call is cancelled (view model scope
cleared) or throws mid-way, the counted command can no longer be undone and the label is already gone; the user must
re-run the leg. Also, a second tap on `run_undo_all` while waiting silently replaces the session.
**Fix:** Clear `awaitingUndo` in a `finally` after `finish` returns, or restore it on non-completion.

### IN-03: Real-provider tripwire protection silently depends on a constructor default

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt:135`
**Issue:** `grammar: GrammarLegRig = GrammarLegRig.standalone()` registers no real providers. A production caller that
forgets to pass the rig (only `AppGraph` does today) keeps a green `grammar_offline` leg whose `attempts` count can no
longer detect a leak to a real provider. The proof degrades with no signal.
**Fix:** Make the parameter required in the production constructor and give tests their own helper, or log/flag
`standalone()` in the leg's evidence (for example a `rig=standalone` token).

### IN-04: `make_workspace` runs with `set -e` disabled in the selftest

**File:** `scripts/agent-wiring-test.sh:273`
**Issue:** `make_workspace ... || sf "..."` calls the function in an `||` context, where `set -e` is suspended inside it.
A failing `git show "$rev:$f"` (a doc missing at `HEAD`), `cp -r` of the wrapper, or a missing `$PROMPT` is ignored and
the selftest continues on a partial workspace; only the function's last command status is seen. Also `GROUP_DEFAULT` at
line 34 aborts the whole script silently under `pipefail` if `engineGroup=` ever disappears from `gradle.properties`.
**Fix:** Chain the critical steps with `&&`/explicit `|| return 1` inside `make_workspace`, and give the `GROUP_DEFAULT`
line an explicit message (`|| { echo "engineGroup missing" >&2; exit 2; }`).

### IN-05: `release-cut.sh` hard-codes the v1.1.0 release directory

**File:** `scripts/release-cut.sh:79-85`
**Issue:** `RELEASE_DIR=".planning/releases/v1.1.0"` is a constant, so check 6 (wiring) and the waiver packet read
v1.1.0's records for any later patch tag (the comment says Phase 20 "rewrites" the record). A v1.1.1 cut would validate
against the wrong release unless someone edits the script first.
**Fix:** Derive it from the tag being cut (`RELEASE_DIR=".planning/releases/$TAG"`) once the tag argument is parsed.

### IN-06: `voice-adapter/api.txt` is a header-only stub

**File:** `voice-adapter/api.txt:1`
**Issue:** The committed baseline contains only `// Signature format: 4.0`. The overload removals in this phase
(`toCommandInput(context)`, `commandInputOf(text, label, context)`) are legitimate only because the module is
unpublished, but with an empty baseline the Metalava compatibility check cannot see them or any later change. The
first-release api.txt must be dumped and reviewed (`review-api-surface.sh --module voice-adapter --dump`) before the
tag, or the strictly-additive rule starts from nothing.
**Fix:** Ensure the Phase 20 cut commits the real `voice-adapter/api.txt` dump and its surface review before tagging.

### IN-07: Router reservation does not cover a pick of tier 0 that escalates

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt:34-35`
**Issue:** `ROUTER_RESERVATION = 9` is documented as the router call plus the plan call plus one replan at 3 requests
each. A pick of the single-shot tier that fails and escalates adds a further single-shot call (router 3 + single 3 + plan
3 + replan 3 = 12). `BudgetedProvider` still enforces the 15-request ceiling, so no overspend is possible, but a 12-request
router run followed by the 6-request plan leg would exhaust the pool and refuse the second leg late, mid-window.
**Fix:** Either reserve 12 for the router leg (the budget test already asserts `plan + router <= 15`, so it would need
to run the router first and the plan leg within the remaining headroom) or document the ordering constraint in the
runbook.

### IN-08: Small script hygiene points

**File:** `scripts/verify-docs-coverage.sh:261-267`, `scripts/jitpack-consumer-probe.sh:39-42`
**Issue:** (a) `check_C10` uses `doc` without declaring it `local` (`local f body found=0`), leaking a global that
`check_C06`/`check_C07` happen to also use; harmless today, fragile if a check relies on it being unset. (b) The probe
adds an `exclusiveContent` repository that always fetches the `:stt` AAR from the live `https://jitpack.io`, even when
`REPO_URL` is a `file://` isolated publication, so the "isolated" dry run now needs network access and a live third-party
artifact to pass.
**Fix:** (a) add `doc` to the `local` list. (b) Document the network dependency in the script header and the dry-run
docs, or vendor a stub `:stt` artifact into the isolated repository.

---

_Reviewed: 2026-10-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
