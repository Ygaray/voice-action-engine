# Phase 18 surface review

Reviewed at HEAD 8ea90f1 (plan 18-07). The real tree's api.txt files were never rewritten; the dump below comes from an
isolated copy.

## Gate results

Memory before the Gradle run (`free -h`):

```
               total        used        free      shared  buff/cache   available
Mem:            31Gi        17Gi       7.6Gi       321Mi       6.7Gi        13Gi
Swap:          2.0Gi       2.0Gi        40Mi
```

Swap was full (as on the host generally) but available memory was 13 GiB, well above the 5 GiB stop line. One Gradle
invocation at a time, single-use daemon, two workers, in-process Kotlin, `--offline`.

| Command | exit | Final output line |
|---|---|---|
| `./gradlew --offline -q --max-workers=2 :voice-adapter:check :core:check` | exit 0 | quiet mode; no FAILED token in the log. The log's tail is the expected detekt negative-control fixture report (`config/negative-controls/detekt/ForbiddenImports.kt` findings), which is the detekt self-test input and not a gate failure |
| `scripts/verify-module-manifest.sh` | exit 0 | `MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter` |
| `scripts/verify-module-manifest.sh --selftest` | exit 0 | `MANIFEST SELFTEST OK cases=11` |
| `scripts/verify-repo-hygiene.sh` | exit 0 | `HYGIENE OK` |
| `scripts/verify-docs-coverage.sh` | exit 0 | `DOC COVERAGE OK checks=25 types=107` |
| `scripts/verify-stt-confinement.sh` | exit 0 | `STT CONFINEMENT OK checks=6` |
| `scripts/verify-stt-confinement.sh --selftest` | exit 0 | `STT CONFINEMENT SELFTEST OK cases=12` |
| `scripts/verify-release-manifest.sh` | exit 0 | `RELEASE MANIFEST PROOF OK cases=8` |

Also: `scripts/verify-api-seed.sh voice-adapter` printed `API SEED OK module=voice-adapter executed=yes removal=red`
(exit 0, task 1), and `scripts/jitpack-dry-run.sh` already contains the `LC_ALL=C sort` locale fix.

## Public surface (verbatim)

Source: `scripts/api-dump-isolated.sh --out /tmp/vae-18-dump` (`API DUMP ISOLATED OK out=/tmp/vae-18-dump core=1956
providers=121 keystore=76 undo=191 voice-adapter=18`), file `voice-adapter.api.sig`. Nothing in the real tree changed:
`voice-adapter/api.txt` is still the header-only seed and `core`, `providers`, `keystore` and `undo` `api.txt` files are
untouched.

```
// Signature format: 4.0
package io.github.ygaray.voiceactionengine.voiceadapter {

  public final class FinalSegmentCommandInput {
    method public static io.github.ygaray.voiceactionengine.core.CommandInput toCommandInput(io.github.ygaray.sttengine.FinalSegment);
    method public static io.github.ygaray.voiceactionengine.core.CommandInput toCommandInput(io.github.ygaray.sttengine.FinalSegment, Object? context);
    method public static io.github.ygaray.voiceactionengine.core.CommandInput toCommandInput(io.github.ygaray.sttengine.FinalSegment, Object? context, String? parentRunId);
  }

  public final class SttLanguageLabels {
    method public static io.github.ygaray.voiceactionengine.core.CommandInput commandInputOf(String transcript, String? languageLabel);
    method public static io.github.ygaray.voiceactionengine.core.CommandInput commandInputOf(String transcript, String? languageLabel, Object? context);
    method public static io.github.ygaray.voiceactionengine.core.CommandInput commandInputOf(String transcript, String? languageLabel, Object? context, String? parentRunId);
    method public static String? normalizeSttLanguageLabel(String? raw);
  }

}
```

Counted from the dump (all asserted by the task verify command): `toCommandInput(` x3, `commandInputOf(` x3,
`normalizeSttLanguageLabel(` x1, `io.github.ygaray.sttengine.FinalSegment` x3 (only in the three `toCommandInput`
signatures), and no `List<`, `Collection` or `Iterable` anywhere. No default-argument synthetic signature leaks: the
overload ladders are explicit, so there is no joiner and no `$default` method (D-05).

## Frozen names

One-way: frozen at the v1.1.0 tag, additive-only afterwards (strict additive rule, section 11).

| Frozen name | Kind | Note |
|---|---|---|
| `FinalSegmentCommandInput` | facade class (`@file:JvmName` facade of `FinalSegmentMapping.kt`) | holder for the three segment overloads |
| `SttLanguageLabels` | facade class (`@file:JvmName` facade of `LanguageLabels.kt`) | holder for the label helpers |
| `toCommandInput(FinalSegment)` | public function | segment only |
| `toCommandInput(FinalSegment, context)` | public function | adds the consumer context |
| `toCommandInput(FinalSegment, context, parentRunId)` | public function | adds the parent run id |
| `commandInputOf(transcript, languageLabel)` | public function | no `:stt` type in the signature |
| `commandInputOf(transcript, languageLabel, context)` | public function | |
| `commandInputOf(transcript, languageLabel, context, parentRunId)` | public function | |
| `normalizeSttLanguageLabel(raw)` | public function | returns `"en"`, `"es"` or null |

Note on `:stt`: the `io.github.ygaray.sttengine.FinalSegment` type is part of the frozen surface through the three
`toCommandInput` overloads. A consumer that calls them must have `:stt` on its own compile classpath. That is the
documented requirement (the adapter publishes no `:stt` dependency, gate of plan 06), not a defect. A consumer that does
not use `:stt` types calls `commandInputOf` and sees no `:stt` type at all.

## Docs versus dump

Grep counts per public function name (occurrences in each document):

| Public function | INTEGRATION.md | API.md | Named in both |
|---|---|---|---|
| `toCommandInput` | 2 | 3 | yes |
| `commandInputOf` | 2 | 2 | yes |
| `normalizeSttLanguageLabel` | 1 | 2 | yes |

Every public function is named in both documents. The two facade class names (`FinalSegmentCommandInput`,
`SttLanguageLabels`) appear in neither document: they are the JVM file-facade holders that Kotlin callers never write, so
the docs name the functions, which is what a consumer imports. Fuller adapter documentation is Phase 19 (DOC-02).

## Carry list

(a) Release-cut: `scripts/release-cut.sh` gates 10 and 12 and the self-test step 4 need a new-module branch for BOTH
`:undo` and `:voice-adapter`. Both modules still carry a header-only `api.txt` seed, and a header-only seed is a
new-module reason at the cut, not a released baseline. Per D-02 and D-03, `voice-adapter/api.txt` stays the header-only
seed until the release cut regenerates it. This plan did not edit `scripts/release-cut.sh`; the release-cut phase owns it
and must handle `:undo` and `:voice-adapter` together.

(b) The clean-cache `:adapteralone` consumer probe (an app that resolves the adapter and adds `:stt` itself) is deferred
to the release-cut phase's dry run, because it needs network access to the external repository for `:stt`. This phase's
proof is the publication gate plus the dry-run artifact set (PD-06).

(c) Phase 19 may revisit wiring `:sample` to the adapter (PD-02). If it does, `sampleRequiredEdges`,
`sampleAllowedEdges`, check c3 of `scripts/verify-stt-confinement.sh`, a repository entry and an `:stt` runtime
dependency in the sample change together.

(d) Fuller adapter documentation, and the hard-coded module lists in `scripts/verify-docs-coverage.sh` (C01, C20) and
`scripts/run-sample-gate1.sh`, belong to Phase 19's DOC-02.

(e) D-07's detected-versus-fallback signal stays with the `:stt` project's v3.2 seed 7b1f660 (consumer answer 9): no new
ask, nothing blocks v1.1.

(f) Consumers may delete their own label-normalising helpers once they adopt the adapter.

(g) Consider adding `scripts/verify-stt-confinement.sh` to the release preflight.

Heavy gates (full negative-control suite with Part 6 and the voice-adapter plants, the API dump proof, the clean-cache
JitPack dry run) are queued in `18-QUIET-WINDOW.md` (`grant: pending`) for plan 08.
