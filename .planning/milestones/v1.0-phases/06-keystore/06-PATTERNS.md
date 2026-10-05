# Phase 6: Keystore - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 14 (new/modified)
**Analogs found:** 14 / 14 (7 in-repo, 4 SB/CT read-only port sources, 3 partial)

All in-repo analogs are git-tracked (checked with `git ls-files`). SB/CT paths are external read-only port sources (outside this repo), named for reading only; never edit them.

SB = `/home/yahir/Projects/AndroidApps/Personal/SecondBrain/app/src/main/java/com/example/secondbrain/core/agent/`

## File Classification

All new main files live under `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/`.

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `keystore/build.gradle.kts` (modify: `api` datastore, test deps, runner, `check` dependsOn androidTest assemble) | config | n/a | itself (current) + `core/build.gradle.kts` (testFixtures wiring) | exact |
| `gradle/libs.versions.toml` (modify: add androidx-test runner/ext-junit) | config | n/a | existing `datastore-prefs` entries | exact |
| `build.gradle.kts` (root; modify detekt `source.setFrom` to add `src/androidTest/kotlin`) | config | n/a | root lines ~33-38 | exact |
| `KeySlot.kt` | model | transform (validated value) | `core/.../core/Credential.kt` | role-match |
| `KeyState.kt` | model | request-response | `core/.../provider/CredentialSource.kt` (`CredentialLookup`) | exact |
| `ApiKeyStore.kt` | service | CRUD (DataStore edit, encrypt/decrypt) | SB `AnthropicApiKeyRepository.kt` (lines 100-135) | exact (port) |
| `AesGcm.kt` | utility | transform | SB `KeystoreCrypto.kt` encrypt/decrypt (lines 46-58) | exact (port) |
| `KeyAccess.kt` | seam (interface) | request-response | SB `KeystoreCryptoSeam` + `core/.../provider/CredentialSource.kt` | role-match |
| `AndroidKeyStoreKeyAccess.kt` | service | key custody | SB `KeystoreCrypto.getOrCreateKey` (lines 70-101) | exact (port, with getKey fix) |
| `KeystoreCredentialSource.kt` | service/adapter | request-response | `core/.../provider/CredentialSource.kt`; `ScriptedCredentialSource` (testFixtures) | role-match |
| `KeystoreModule.kt` (delete once another main file exists) | marker | n/a | itself | exact |
| `src/test/.../SoftwareKeyAccess.kt`, `LegacyWriters.kt`, `*Test.kt` | test | CRUD/transform | `core/src/test/.../KeyIsolationTest.kt` | role-match |
| `src/androidTest/.../KeystoreDeviceTest.kt` | test (instrumented) | CRUD | none in-repo | no analog |
| `scripts/run-keystore-instrumented.sh` | script | n/a | `scripts/verify-*.sh` (hygiene style) | partial |

## Pattern Assignments

### `KeyState.kt` (model, open abstract base + final leaves)

**Analog:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CredentialSource.kt` lines 22-60 (house style: open abstract class with `internal constructor`, final nested leaves, hand-written equals/hashCode/toString, no `sealed`, `data class`, or `enum`).

```kotlin
public abstract class CredentialLookup internal constructor() {
    public class Missing : CredentialLookup() {
        override fun equals(other: Any?): Boolean = other is Missing
        override fun hashCode(): Int = Missing::class.java.name.hashCode()
        override fun toString(): String = "CredentialLookup.Missing"
    }
    public class Unreadable(public val cause: String) : CredentialLookup() {
        init { require(isStableCode(cause)) { "Unreadable cause must be a stable code" } }
        override fun equals(other: Any?): Boolean = other is Unreadable && cause == other.cause
        override fun hashCode(): Int = mixHash(Unreadable::class.java.name.hashCode(), cause.hashCode())
        override fun toString(): String = "CredentialLookup.Unreadable(cause=$cause)"
    }
}
```

Caveat: `isStableCode` and `mixHash` are `internal` to `:core` (`failure/ReasonSupport.kt:12,21`). Do not widen `:core`. In `:keystore` repeat a local `Regex("[a-z0-9_]+")` check and a local hash mix. `Ready.toString()` must omit `last4` (secrets rule). Apply the same "print provider/cause only" rule as `Credential.toString()`.

### `KeySlot.kt` (model)

**Analog:** `core/.../core/Credential.kt` (init `require`, hand toString, no data class):

```kotlin
public class Credential(public val provider: ProviderId, public val apiKey: String) {
    init { require(apiKey.isNotBlank()) { "Credential apiKey must not be blank" } }
    override fun toString(): String = "Credential(provider=$provider)"
}
```

Four `public val`s, no defaults (D-01), hand-written equals/hashCode/toString. Table-level validation (duplicate provider/alias/pref-key, blank, ct == iv, empty table) lives in `ApiKeyStore.init`, messages name alias/pref-key only.

### `AesGcm.kt` (utility, transform) and `AndroidKeyStoreKeyAccess.kt` (key custody)

**Analog:** SB `KeystoreCrypto.kt` lines 46-101 (read this session). Copy the layout exactly; drop `@Singleton`/`@Inject`, port comments (`T-165-26`, `165-RESEARCH`, `CFG-01`), and replace `getEntry` with `getKey`.

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, key)          // NO IV spec
val ciphertext = cipher.doFinal(plaintext)     // tag appended
Encrypted(iv = cipher.iv, ciphertext = ciphertext)
// decrypt:
cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
cipher.doFinal(ciphertext)
// key generation spec (copy verbatim; keep non-auth-bound):
KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM).setEncryptionPaddings(ENCRYPTION_PADDING_NONE).setKeySize(256).build()
KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
```

Deviations required by research: `existingKey` uses `keyStore.getKey(alias, null)` (never `getEntry`/`containsAlias`); `getOrCreateKey` uses a process-global `synchronized(LOCK)` (not per-instance `@Synchronized`); `seal` has no IV parameter; name MagicNumbers (`12/16/128/256/4`) as `const val`s (detekt `MagicNumber`).

### `ApiKeyStore.kt` (service, CRUD)

**Analog:** SB `AnthropicApiKeyRepository.kt` lines 100-135.

```kotlin
suspend fun saveApiKey(rawKey: String) {
    val trimmed = rawKey.trim()
    require(trimmed.isNotEmpty()) { "Anthropic API key must not be blank" }   // do NOT echo the key
    writeMutex.withLock {
        val encrypted = withContext(ioDispatcher) { crypto.encrypt(trimmed.toByteArray(Charsets.UTF_8), ALIAS) }
        preferences.setAnthropicApiKeySecret(StoredSecret(
            ciphertextB64 = Base64.getEncoder().encodeToString(encrypted.ciphertext),
            ivB64 = Base64.getEncoder().encodeToString(encrypted.iv)))      // one DataStore edit
    }
}
suspend fun removeApiKey() { writeMutex.withLock { preferences.clearAnthropicApiKeySecret() } }
```

Generalize: `dataStore.edit { it[stringPreferencesKey(slot.ciphertextKey)] = ...; it[stringPreferencesKey(slot.ivKey)] = ... }` in ONE edit. Reads: `dataStore.data.first()`, torn pair = `NotConfigured`, `existingKey(alias) == null` -> `KeyMissing`. Catch chain naming (to avoid `@Suppress`), copied from `providers/.../http/CallAwait.kt:53`:

```kotlin
} catch (ignored: RuntimeException) { ... }
```

Order: `catch (cancelled: CancellationException) { throw cancelled }` first, then `ignored: GeneralSecurityException`, `ignored: IllegalArgumentException`, `ignored: IOException`, `ignored: RuntimeException`. Public ctor `ApiKeyStore(dataStore, slots, ioDispatcher = Dispatchers.IO)`; internal ctor adds `keyAccess`. Plaintext only via `internal fun readSecret`. No `runCatching`, no `Log`, no `android.util.Base64` in `src/main`.

### `KeystoreCredentialSource.kt` (adapter)

**Analog:** the `CredentialSource` fun interface (`core/.../provider/CredentialSource.kt:15-18`) and `ScriptedCredentialSource.credential` (`core/src/testFixtures/.../testing/ScriptedSources.kt:25-28`):

```kotlin
override suspend fun credential(provider: ProviderId): CredentialLookup {
    ...
    return answers[provider] ?: CredentialLookup.Missing()
}
```

Mapping per RESEARCH Pattern 3: `NotConfigured -> Missing()`, `Ready -> Present(Credential(provider, plaintext))`, `KeyMissing -> Unreadable("key_missing")`, `Unreadable(c) -> Unreadable(c)`; blank decrypted value -> `Unreadable("stored_value_malformed")` (`Credential` throws on blank). Must never throw (`ModelRouter.kt:175-185` treats a throwing source as an engine fault); rethrow `CancellationException` only.

### Build wiring: `keystore/build.gradle.kts`

**Analog:** current file plus `core/build.gradle.kts:33` (`testImplementation(testFixtures(project(":core")))`). Change:

```kotlin
defaultConfig { minSdk = 35; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
dependencies {
    api(project(":core"))
    api(libs.datastore.prefs)     // was implementation(...)
    testImplementation(libs.junit); testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.androidx.test.ext.junit); androidTestImplementation(libs.androidx.test.runner)
}
tasks.named("check") { dependsOn("assembleDebugAndroidTest") }
```

Keep `apply(from = rootProject.file("gradle/invariants.gradle.kts"))` last. Root `build.gradle.kts` (~lines 33-38) lists detekt sources: add `"src/androidTest/kotlin"`.

### Tests (`src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/*`)

**Analog:** `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/KeyIsolationTest.kt` (lines 1-110): JUnit4 + `runTest`, `commandPipeline { tier(...); provider(fake); providerSelection = ...; this.credentials = credentials; gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink() }`, `NoNetworkGuard.during { }`, `FakeAiProvider.reply(...)`, assert `call.credential?.apiKey`. Swap `ScriptedCredentialSource.keys(...)` for `KeystoreCredentialSource(store)` over a temp-file `PreferenceDataStoreFactory.create(scope, produceFile = { tmp.newFile("x.preferences_pb") })` with `SoftwareKeyAccess`; cancel scope in `@After`. `LegacyWriters.kt` must be an independent replica (plain `javax.crypto` + `java.util.Base64`, not `AesGcm`). Golden vector is in RESEARCH lines 345-352. Package must start with `io.github.ygaray.voiceactionengine` (hygiene script).

## Shared Patterns

### Secrets never reach sinks
**Source:** `core/.../Credential.kt` toString; `CredentialLookup.Present.toString()`. **Apply to:** `KeyState`, `KeySlot`, `ApiKeyStore`, `KeystoreCredentialSource`, all exception messages. Print provider/cause code only; canary test asserts no leak.

### Catch chain without `@Suppress`
**Source:** `providers/.../http/CallAwait.kt:53` (`catch (ignored: RuntimeException)`). **Apply to:** every Keystore/cipher/DataStore read path. `core/.../internal/Guarded.kt` remains the repo's only suppression.

### Banned constructs in `src/main`
`runCatching`, `println`, `System.out/err`, `printStackTrace`, `android.util.Log`, `javax.inject`/dagger imports, planning ids (`T-\d+-\d+`, `WR-\d+`, `Phase N D-N`) in comments, `TODO:`/`FIXME:`. Explicit API strict: every public declaration needs visibility and return type; seam/crypto types `internal`.

### Test fixtures
`testFixtures(project(":core"))` provides `ScriptedCredentialSource`, `FakeAiProvider`, `RecordingCommitSink`, `ScriptedGate`, `ScriptedStrategy`, `NoNetworkGuard`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/androidTest/.../KeystoreDeviceTest.kt` | instrumented test | CRUD on real AndroidKeyStore | No androidTest sources exist in repo; use RESEARCH "Instrumented leg" list (8 cases) and SB `KeystoreCrypto` verbatim as the legacy-writer replica (android.util.Base64 allowed here only). Run via `ANDROID_SERIAL=R5CT10XNKQN ./gradlew :keystore:connectedDebugAndroidTest --offline --console=plain`. |
| `scripts/run-keystore-instrumented.sh` | guard script | n/a | Only `scripts/verify-*.sh` siblings exist (style reference); gating on `adb -s R5CT10XNKQN get-state` and model `SM-S908U` per RESEARCH. |

## Metadata

**Analog search scope:** `keystore/`, `core/src/main/.../provider`, `core/src/testFixtures`, `core/src/test`, `providers/.../http`, SB `core/agent/` (read-only)
**CT port sources** (`caltracker KeystoreCrypto.kt`, `ApiKeyRepository.kt`) were not re-read; layout and alias values come from RESEARCH's legacy table, which cites them.
**Pattern extraction date:** 2026-10-01
