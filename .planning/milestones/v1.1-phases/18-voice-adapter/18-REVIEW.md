---
phase: 18-voice-adapter
reviewed: 2026-10-06T00:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - API.md
  - ECOSYSTEM.md
  - INTEGRATION.md
  - README.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt
  - gradle/invariants.gradle.kts
  - gradle/libs.versions.toml
  - jitpack.yml
  - scripts/modules.list
  - scripts/verify-api-seed.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-stt-confinement.sh
  - scripts/verify-stt-negative-controls.sh
  - settings.gradle.kts
  - voice-adapter/api.txt
  - voice-adapter/build.gradle.kts
  - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt
  - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/AdapterApiShapeTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMappingTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabelsTest.kt
  - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/RedactionTest.kt
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: resolved
---

# Phase 18: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Reviewed the new `:voice-adapter` module (two small pure mapping files), its tests, the build and invariant gates that
keep `:stt` compile-only, the confinement and negative-control scripts, the docs, and the `CommitSink.kt` string-form
KDoc change with its test. No Gradle was run (read-only analysis).

The adapter logic itself is correct: verbatim transcript, trim+lowercase label normalisation with a closed `en`/`es`
set, no stringification of the segment, total (never throws). The `:stt` compile-only publication gate is well
built (non-vacuity guards, matches on the exact group and artifact rather than the shared owner prefix).
`api.txt` being header-only is correct for an unreleased module: `release-cut.sh` gate `api-dump` compares a fresh
dump to the committed file, so the seed cannot ship unnoticed.

No blockers. The findings are: a self-contradiction in the `toString` privacy contract, an enforcement gap (the textual
`:stt` confinement gate is wired nowhere), a headline guarantee with no executable proof, and a frozen-forever
overload trap that is still fixable because v1.1.0 is not cut.

## Warnings

### WR-01: `ActionEvent.toString` contradicts its own privacy rationale (runId / parentRunId printed verbatim)

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt:37-45`
**Issue:** The new KDoc says "The run-id seam is app code, so an id may carry user text; that is why the held run id's
value is never printed." The implementation then prints `runId=$runId` and `parentRunId=$parentRunId` verbatim. If the
stated premise is true, two of the three ids leak exactly what the third is hidden to protect. The project constraint is
that transcripts never reach `toString()`. `ActionEventTest.everyFieldThatCouldCarryUserTextIsAbsentFromBothStringForms`
pins the inconsistency (it asserts `runId=run-7` and `parentRunId=parent-3` ARE printed), and only sentinel-tests
`heldRunId`, so the test cannot catch a user-text run id. `CommandInput.toString` has the same pattern for
`parentRunId`. Either the premise is false (then the heldRunId special case is unjustified noise) or the code is wrong.
**Fix:** Pick one. If ids may carry user text, print presence/length for all three and update the test:
```kotlin
override fun toString(): String =
    "ActionEvent(runId=${runId.length}, parentRunId=${if (parentRunId == null) "null" else "set"}, " +
        "heldRunId=${if (heldRunId == null) "null" else "set"}, action=$action)"
```
If ids are deemed safe (engine-generated or app-controlled opaque tokens), delete the "may carry user text" sentence
and print `heldRunId` consistently. Add a sentinel-valued `runId`/`parentRunId` case to the all-fields test either way.

### WR-02: The `:stt` confinement script is not wired into any automated gate

**File:** `scripts/verify-stt-confinement.sh:1-262` (and absence of wiring in `scripts/release-cut.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`)
**Issue:** `grep` across `scripts/`, `build.gradle.kts`, `gradle/` and `jitpack.yml` finds no caller of
`verify-stt-confinement.sh` (nor of its `--selftest`). It is not a Gradle `check` dependency, not a release-cut gate,
and Part 6 of `verify-negative-controls.sh` only runs the Gradle-side `verify-stt-negative-controls.sh`. The textual
half is the only thing enforcing: the single exact-group `exclusiveContent` block in `settings.gradle.kts`, the
immutable `vX.Y.Z` pin shape, "no aggregator group", the `minSdk = 35` / compileOnly shape of the adapter build, the
source-import confinement (`LanguageLabels.kt` stays stt-free), and the docs minimum matching the catalog pin. Someone
can raise the catalog pin, widen `includeGroup`, or import `sttengine` into `LanguageLabels.kt` and every automated gate
stays green until a human remembers to run the script. The selftest has the same problem: its planted-violation proof
is never re-run.
**Fix:** The script needs no Gradle, so call it from `scripts/release-cut.sh` in the existing `hygiene` gate (next to
`verify-module-manifest.sh`) and from `verify-negative-controls.sh` Part 6:
```bash
if ! out="$("$REPO/scripts/verify-stt-confinement.sh" 2>&1)" || ! grep -q 'STT CONFINEMENT OK' <<<"$out"; then
  printf '%s\n' "$out" >&2
  gate_fail hygiene "scripts/verify-stt-confinement.sh failed"
fi
# Part 6:
if scripts/verify-stt-confinement.sh --selftest; then echo "ok    [stt confinement selftest]"; else echo "FAIL  [stt confinement selftest]"; fails=$((fails+1)); fi
```

### WR-03: "stt-free facade" has no executable proof (only a textual grep)

**File:** `voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/AdapterApiShapeTest.kt:42-48`, `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt:1-62`
**Issue:** The headline contract (INTEGRATION.md section 12, API.md) is that `commandInputOf` and
`normalizeSttLanguageLabel` work for "a caller with no `:stt` type on its classpath". Nothing runs that. The unit-test
classpath has `:stt` via `testImplementation`, so every test would pass even if `SttLanguageLabels` linked against
`FinalSegment`. `noSignatureInTheLabelFacadeNamesAnSttType` only inspects public signatures (not field types, private
helpers, or constant-pool references), and the script check only greps the source text of `LanguageLabels.kt` for the
package string. An accidental stt reference through a Kotlin-generated bridge, an inlined helper, or a new main file
would not be caught, and the failure would only show as `NoClassDefFoundError` in a consumer app without `:stt`.
**Fix:** Add a test that loads the facade through a classloader that refuses `io.github.ygaray.sttengine.*` and calls
it, or scan the compiled class for the package:
```kotlin
@Test
fun theLabelFacadeLoadsAndRunsWithoutTheSpeechEngineVisible() {
    val hiding = object : ClassLoader(javaClass.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            if (name.startsWith("io.github.ygaray.sttengine")) throw ClassNotFoundException(name)
            else super.loadClass(name, resolve)
    }
    // Child-first load of the facade so its linkage is resolved against the hiding loader.
    val bytes = javaClass.classLoader.getResourceAsStream("io/github/ygaray/voiceactionengine/voiceadapter/SttLanguageLabels.class")!!.readBytes()
    assertFalse(String(bytes, Charsets.ISO_8859_1).contains("io/github/ygaray/sttengine"))
}
```
(The constant-pool substring check is the simplest robust form; keep it for both `SttLanguageLabels.class` and any
other non-mapping class in the module.)

### WR-04: Positional overload trap: a `String` passed third silently becomes `context`, not `parentRunId`

**File:** `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt:41-42`, `FinalSegmentMapping.kt:24`
**Issue:** `commandInputOf(transcript, label, context: Any?)` and `FinalSegment.toCommandInput(context: Any?)` accept
any object, including a `String`. A caller who means "reply to run X" and writes `commandInputOf("yes", "en", "run-42")`
or `segment.toCommandInput("run-42")` compiles and runs, and the id lands in `context`, with `parentRunId = null`. The
run silently loses its parent link (clarification/undo chaining), and there is no error or log. The overload set is
frozen additively once `v1.1.0` is tagged, so this is the last chance to make it hard to misuse.
**Fix:** Cheapest: add a KDoc warning on both one-arg-context overloads ("a String here is a context object, not a run
id; use the two-argument form for a parent run"). Better, before the tag: drop the context-only overloads and keep
only the no-argument and full-argument forms (still additive-safe since nothing is released), so a parent id can never be
mistaken for a context.

## Info

### IN-01: README tells consumers to add `voice-adapter:<version>` but the pinned version has no such artifact

**File:** `README.md:53-54` (pin at line 37: `v1.0.1`)
**Issue:** The README names the `voice-adapter` coordinate with `<version>` and says "use the pinned version for `<version>`",
but `v1.0.1` predates the module. INTEGRATION.md and ECOSYSTEM.md correctly say it first ships in `v1.1.0`; README does not,
so a copy-paste resolves nothing until the repin tooling bumps the pin.
**Fix:** Add "(first published in v1.1.0)" to the README sentence, mirroring ECOSYSTEM.md line 33.

### IN-02: The adapter's DI and ML gates scan `:stt`'s transitive compile tree, which is never published

**File:** `gradle/invariants.gradle.kts:316-335, 379-413` (applied to `:voice-adapter`), `voice-adapter/build.gradle.kts:37`
**Issue:** `verifyNoDiArtifacts` and `verifyNoMlArtifacts` read `releaseCompileClasspath`, which for the adapter includes
the whole transitive tree of the `compileOnly` `:stt` AAR. A future `:stt` release that pulls (for example) a DI or ML
artifact would fail the adapter's `check` for a dependency that never reaches a consumer through this module, and would
nudge someone to loosen the gate. Today it is green, so this is a latent false positive, not a defect.
**Fix:** For `voice-adapter`, scan only `releaseRuntimeClasspath` plus the POM (what is actually published), or exclude the
stt component tree from the compile-classpath scan with a one-line comment.

### IN-03: The segment path and the text path duplicate the mapping instead of sharing it

**File:** `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt:33-39`, `LanguageLabels.kt:51-62`
**Issue:** Both build `CommandInput(transcript, normalizeSttLanguageLabel(label), context, parentRunId)` independently;
`LanguageLabelsTest.commandInputOfAgreesWithTheSegmentPathForTheSameTextAndLabel` exists only to catch drift.
**Fix:** Have the segment path delegate: `= commandInputOf(text, language, context, parentRunId)`. The stt-free facade
stays stt-free (the dependency points segment-facade to label-facade, never the reverse).

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
