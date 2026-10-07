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

<!-- gsd:write-continue -->
