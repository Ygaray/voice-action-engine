# Phase 10: Sample Harness, Gate-1 & Docs - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 17 (new/modified)
**Analogs found:** 15 / 17 (all analog paths verified git-tracked)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `sample/build.gradle.kts` (modify: compose plugin, debug assets, deps) | config | n/a | itself (current inert form) + `keystore/build.gradle.kts` | exact |
| `sample/src/main/AndroidManifest.xml` (modify: INTERNET, activity) | config | n/a | itself | exact |
| `sample/src/main/kotlin/.../sample/*` (Activity, ViewModel, engine wiring, tool pair, fixture loader, logcat trace) | component/service | request-response + event-driven | `keystore/.../ApiKeyStore.kt`, `KeystoreCredentialSource.kt`; `core/src/testFixtures/.../ScriptedSources.kt` (seam shapes) | role-match |
| `sample/src/test/kotlin/...` (fixture-sha, trace-format JVM tests) | test | transform | `keystore/src/test/.../KeystoreTestSupport.kt`, `KeystoreCanaryTest.kt` | role-match |
| `scripts/run-sample-gate1.sh` | script (device runner) | request-response | `scripts/run-keystore-instrumented.sh` | exact |
| `scripts/verify-sample-device-guard.sh` | script (fake-adb proof) | request-response | `scripts/verify-keystore-device-guard.sh` | exact |
| `scripts/push-sample-fixture.sh` | script (file I/O to device) | file-I/O | `scripts/run-keystore-instrumented.sh` (resolve/identity/cleanup) | partial |
| `scripts/agent-wiring-test.sh` | script | batch | `scripts/jitpack-consumer-probe.sh` | role-match |
| doc coverage grep script | script | transform | `scripts/review-api-surface.sh`, `scripts/verify-repo-hygiene.sh` | role-match |
| `README.md`, `INTEGRATION.md`, `API.md`, `ECOSYSTEM.md` | docs | n/a | `/home/yahir/Projects/Reusable/android/backup-engine/{README,INTEGRATION,API,ECOSYSTEM}.md` (outside repo; read-only) | exact |
| `GATE1-RUNBOOK.md`, `10-SELF-UAT.md` | docs/evidence | n/a | `.planning/phases/09-.../09-SELF-UAT.md`, `06-07-SELF-UAT.md` | exact |
| `evidence/*.txt` | evidence | n/a | `.planning/phases/09-agentic-loop-strategy/evidence/*.txt` | exact |
| `.gitignore`, `verify-repo-hygiene.sh` (modify) | config | n/a | themselves | exact |

## Pattern Assignments

### `scripts/run-sample-gate1.sh` (device runner)
**Analog:** `scripts/run-keystore-instrumented.sh`. Copy its structure and change only the constants, build, and run step.

**Constants + fixed target** (lines 17-31): `TESTER_USB="R5CT10XNKQN"`, `TESTER_WIFI="100.118.21.106:1496"`, `PERSONAL_IP="100.126.94.47"`, `EXPECTED_MODEL="SM-S908U"`, `MIN_SDK=35`, `LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-...-tester.lock"`, `ADB="${ADB:-adb}"`. Use a new lock name, or the same lock if one device-tester at a time is wanted (recommended: share the keystore lock).

**adb wrapper** (lines 44-46): lock fd 9 closed for adb children.
```bash
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
```

**Guard sequence to keep verbatim** (lines 95-140): (a) no args, or only a fixed whitelist of flags, so nothing can redirect the target; (b) refuse a foreign `ANDROID_SERIAL`, then `unset`; (c) `exec 9>"$LOCK_FILE"; flock -n 9` (exit 3 on busy); (d) USB first, wireless fallback, else "TESTER OFFLINE - not substituting"; (e) refuse a `*$PERSONAL_IP*` target, then require `ro.serialno == TESTER_USB`, model and sdk (exit 4); (f) print the foreground activity (informational).

**Cleanup/trap/finish** (lines 52-82): `cleanup` uninstalls and verifies removal via `pm list packages`; `quiet_uninstall` on `INT TERM HUP EXIT`; `finish <code> <OUTCOME> <details>` prints the single last line. Rename the last line to `SAMPLE_GATE1: <PASS|FAIL|INFRA|ERROR> ...`. Exit codes stay 0/1/2/3/4.

**Build step** (lines 152-163): `./gradlew :sample:assembleDebug --offline -q >"$LOG_DIR/gradle.log" 2>&1 9>&-`. Check there is exactly one APK. Build from the main checkout (D-05).

**Install/run**: replace `am instrument` with `adb install -r`, then `am start` and `logcat -d` filtering by the sample tag. Print counts and fingerprints only (D-02). Parse logcat lines the way step j parses `OK (N tests)`: the PASS rule is in the script and has a minimum count.

**Keys (D-04, D-13)**: never read a key in this script. Per CONTEXT, use `push-test-key <provider> --device <TESTER serial> --package <sample appId>` (see `~/.claude/context/workflows/test-keys.md`). The app imports the key through `:keystore`. Never echo the key or `adb shell` a key literal.

### `scripts/verify-sample-device-guard.sh`
**Analog:** `scripts/verify-keystore-device-guard.sh` (lines 1-40).
- Copy the runner into a `mktemp -d` skeleton whose `gradlew` only records that it was called.
- A fake adb logs `"$*"` to `$CALLS_LOG` and answers per `$SCENARIO`.
- Scenarios: `offline, usb_impostor, wireless_impostor, foreign_android_serial, extra_argument, lock_busy`.
- Assertions per scenario: exit code, message, last line, and no install/am/uninstall call.
- Every logged adb call is `connect 100.118.21.106:1496` or begins with `-s R5CT10XNKQN ` / `-s 100.118.21.106:1496 `.
- Final output is `DEVICE GUARD OK` or `DEVICE GUARD FAIL: <scenario>: <why>` (exit 1).
- Add scenarios for the new flags (for example a flag trying to name another serial).

### `scripts/push-sample-fixture.sh` (file I/O)
Reuse the resolve/identity block (guard a-e) from the runner. Write only into the app's private dir via `adb -s "$TARGET" shell run-as <appId>` (debug build) or `adb push` to a staging path followed by `run-as cp`. Check the sha256 on the host before the push, and again at runtime in the app (D-05). Source file: the gitignored `sb-a10-fixture.json` (the `.gitignore` line 48 pattern `sb-a10-fixture*.json` already covers it). Never reference SB's path inside the build. `verify-repo-hygiene.sh` lines 49-54 already forbid the committed fixture at `sample/src/debug/assets/sb-a10-fixture*.json`. Do not weaken them. The copy lives in the gitignored location only.

### `sample/` Kotlin sources (wiring)
**Current state:** `sample/build.gradle.kts` is "Inert reference app ... reads no files or environment at configuration time" (JitPack configures every included project). **Keep that invariant**: no `File(...)`, `System.getenv`, or fixture checks at configuration time (D-05: runtime sha only). `verify-repo-hygiene.sh` line 79 also requires jitpack.yml to never name `:sample`.

**Build file pattern to extend** (`sample/build.gradle.kts`): `alias(libs.plugins.android.application)` plus, per the stack, `plugin.compose`. Keep `compileSdk { version = release(36) { minorApiLevel = 1 } }`, `minSdk = 35`, JVM 11, `implementation(project(":core"/":providers"/":keystore"))`, and the pinned `okhttp:5.2.1`. Compose BOM 2026.04.01, activity-compose 1.13.0, lifecycle-viewmodel-compose 2.10.0 go through `gradle/libs.versions.toml` (add entries there, not inline).

**Manifest** (current, `sample/src/main/AndroidManifest.xml`): `<application android:label="vae-sample" />`. Add `INTERNET` and a launcher activity. Do not add `usesCleartextTraffic`.

**Key custody pattern:** use `:keystore` `ApiKeyStore` (`keystore/src/main/kotlin/.../keystore/ApiKeyStore.kt`) and `KeystoreCredentialSource.kt` as the `CredentialSource` handed to the providers. The sample proves the key-through-:keystore path (VER-01, D-01). Read both files before wiring, to get the exact constructor and `KeySlot` shape. This pass did not extract them.

**Fakes/seams reference** (`core/src/testFixtures/.../core/testing/`): `FakeAiProvider`, `ScriptedSources`, `ScriptedGate`, `RecordingCommitSink`, `RecordingEventListener`. The sample's synthetic tool pair (D-06) implements the same `ToolSpecProvider` / `ToolExecutor` seams with `Finished | Mutation` steps. It must not depend on testFixtures (an unpublished test artifact). Copy the shapes, not the dependency.

**Logging**: the library has `ForbiddenImport android.util.Log`, but `:sample` is not a library module, so `Log` is allowed there. Emit SB-format per-turn lines plus the extra fields: tool names, counts, fingerprints only. Never prompt text, args, results or keys (D-02; project Secrets constraint).

### `sample/src/test/kotlin/...` (JVM tests)
**Analog:** `keystore/src/test/.../KeystoreTestSupport.kt` (shared test support, JUnit4 + software crypto seam) and `KeystoreCanaryTest.kt` (a canary asserting that secrets never reach sinks). Reuse the canary approach for "no key or prompt text in the trace lines". `sample` needs `testImplementation(libs.junit)` (JUnit 4.13.2). Tests cover the fixture-sha check and the trace-line formatter as pure functions.

### Docs: `README.md`, `INTEGRATION.md`, `API.md`, `ECOSYSTEM.md`
**Analog layout (backup-engine):**
- README (77 lines): one-paragraph purpose, a "Docs for agents & integrators" bullet list linking `INTEGRATION.md` / `API.md` / `CLAUDE.md`, then "Install (JitPack)" with the `settings.gradle.kts` repository block:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven { url = uri("https://jitpack.io") } }
}
```
- INTEGRATION (125 lines): numbered steps `## 1. Add the JitPack repository`, `## 2. Depend on the engine (pin an immutable tag)`, then implement the app seams, bind, call from UI, ending with `## Notes & gotchas`.
- API (79 lines): `## Surface at a glance`, one section per type, `## Extension points`.
- ECOSYSTEM (179 lines): sections 1-8, hub/spokes, consumption, jurisdiction, tiers, litmus, versioning/repin, onboarding a new consumer.

**Adapt for this repo:** per-module coordinates `com.github.Ygaray.voice-action-engine:<artifactId>` (erratum E5). Mention OkHttp compile floor 4.12 and consumers' own version. Cover the §5.2 seams and the tier ladder. Per D-14 document `terminalCall` + `Clarification` rendered as pressable options, and the follow-up as a new command with `parentRunId` and the choice via the user-turn hook. Do not drift from backup-engine's JitPack tag-pin phrasing. Doc names and domain terms must stay domain-free (no note/card/food). Docs must not claim a tag that does not yet exist (the tag is Phase 11).

**Doc coverage grep script:** model on `scripts/review-api-surface.sh` (reads `api.txt`-style public surface and reports `... OK <summary>`). Every public type in the API dump must appear in `API.md`. Output format `DOC COVERAGE OK n=<count>` or fail with the missing names. `api.txt` is not committed before the tag (hygiene rule c), so read the surface from sources or a generated metalava dump to a temp path.

### `scripts/agent-wiring-test.sh`
**Analog:** `scripts/jitpack-consumer-probe.sh` (scratch consumer from Phase 1; D-07). Read it before writing, since this pass did not extract it. Reuse the scratch consumer against the release SHA via JitPack-by-commit. The fresh subagent gets README only. The script records pass/fail and the transcript of the files the subagent consulted.

### Evidence + SELF-UAT + runbook
**Analog:** `.planning/phases/09-agentic-loop-strategy/09-SELF-UAT.md`.
- Frontmatter: `status, result, gate, phase, source, device, apk, run: <ts> @ <sha>`.
- Header bullets: Target, Build identity (HEAD sha plus clean `git status --short` before and after), Pre-flight, per-class counts.
- Per criterion: `### N. SCn: ...`, `result:`, `Rung:`, `Target:`, `Expected:`, `Arranged (seeded):`, then observed output.
- For Gate-1 on the device: device `yahirs-s22-ultra-2`, apk path and sha, cold-start run, D-03 tolerance band around 7,016, warm re-run marked as an infra re-run.
- Evidence files are plain `.txt` under `evidence/` (see `phase-gate.txt`, `mandate-coverage.txt`). Committed evidence holds only tool names, counts and fingerprints. `GATE1-RUNBOOK.md` follows the Phase 6 `06-07-SELF-UAT.md` handling for the TESTER and the two-gate UAT (`~/.claude/context/workflows/two-gate-uat.md`).
- Gate-2 carry items (CONTEXT Runtime Decisions): Billing 400 maps to `FailureReason.Billing`; key character set per provider; `disable_parallel_tool_use` + HTTP 200 on the Anthropic smoke; a bounded multi-turn leg on OpenAI Chat and OpenRouter. State the call count and cost in the plan.

## Shared Patterns

### Single machine-readable last line
**Source:** `run-keystore-instrumented.sh` `finish()` (lines 77-82). **Apply to:** every new script. Last line `<NAME>: <OUTCOME> key=value ...`; the verifiers end with `... OK` / `... FAIL: <why>`.

### Device safety (TESTER only)
**Source:** guard a-e above. **Apply to:** `run-sample-gate1.sh`, `push-sample-fixture.sh`, any key-push helper. No arguments that change the target. Every adb call carries `-s <target>` (except the one `connect`). Cleanup after.

### Secrets and public repo hygiene
**Source:** `.gitignore` line 47-48, `verify-repo-hygiene.sh` (c, f). **Apply to:** all of Phase 10. No key literal, fixture content, prompt text, args or `sb-a10-fixture*` in commits or logs. Run `scripts/verify-repo-hygiene.sh` (expects `HYGIENE OK`) and `scripts/verify-keystore-device-guard.sh` as regression gates. Scripts that print a key or a prompt fail review.

### Build config
**Source:** `keystore/build.gradle.kts` lines 1-25 and `sample/build.gradle.kts`. namespace `io.github.ygaray.voiceactionengine.<module>`, compileSdk 36.1, minSdk 35, JVM 11, AGP built-in Kotlin (no `org.jetbrains.kotlin.android`). Sample is never published (A10) and sets no configuration-time file reads.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| Compose UI (Activity, BYO-key field, run button, trace readout) | component | event-driven | No Compose code exists in the repo; use the STACK.md `:sample` row (BOM 2026.04.01). SB or CT `ui/voice/` are read-only references. |
| Logcat parser for PASS/FAIL against the 7,016 band | script | transform | The closest is the `OK (N tests)` parse in the runner (lines 168-185), but it needs a new grammar. |

## Metadata

**Analog search scope:** `scripts/`, `sample/`, `keystore/`, `core/src/testFixtures`, `.planning/phases/{06,09}-*`, backup-engine docs.
**Files scanned:** about 25
**Pattern extraction date:** 2026-10-01
