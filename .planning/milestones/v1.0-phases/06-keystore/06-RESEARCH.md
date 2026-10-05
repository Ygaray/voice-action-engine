# Phase 6: Keystore - Research

**Researched:** 2026-10-01
**Domain:** Android library crypto storage (AndroidKeyStore AES/GCM + app-injected `DataStore<Preferences>`), JitPack-published AAR, Kotlin 2.3.20 / AGP 9.2.1
**Confidence:** HIGH on layout, build mechanics and exception mapping (read from AOSP source and proven by a throwaway spike build); MEDIUM on real-device (One UI 7 / Android 15) behavior, which was not run on the TESTER during research (see "Environment Availability").

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [slot-validation]:** KeySlot(provider, alias, ciphertextKey, ivKey) rows, no defaults, validated at construction — a copy-pasted duplicate alias would otherwise let one provider's save clobber another's key _(source: ai-auto)_
- **D-02 [datastore-scope]:** api scope — the constructor takes DataStore<Preferences>; :keystore never creates a DataStore on the app's file _(source: ai-auto)_
- **D-03 [unmapped]:** Unmapped read = NotConfigured (router never crashes); unmapped write throws loudly; CT's non-provider secrets (MCP token, OA key) stay in CT _(source: ai-auto)_
- **D-04 [fingerprint]:** Ready(last4) with the ≤4-chars-reveals-nothing rule; provider display prefix stays in the app UI _(source: ai-auto)_
- **D-05 [decode]:** java.util.Base64 standard encoder (byte-identical to NO_WRAP), strict decoder with failures → Unreadable; save trims and rejects blank (SB behavior) _(source: ai-auto)_
- **D-06 [compat-test]:** Both legs in Phase 6 so a format regression is caught before SB/CT plan their migrations; the instrumented test uses the literal legacy aliases (app-private keystore, so safe) _(source: ai-auto)_
- **D-07 [unreadable]:** Never auto-clear, reads have no side effects; explicit catch chain (cancellation rethrown; GeneralSecurity/IllegalArgument/Runtime → Unreadable); CT rebuilds its auto-clear banner app-side if wanted _(source: ai-auto)_
- **D-08 [test-seam]:** Key-access seam with shared cipher/Base64/DataStore code so JVM tests exercise the real IV + tag layout with software keys; temp-file PreferenceDataStoreFactory in tests _(source: ai-auto)_
- **D-09 [credential-adapter]:** Typed result so a restored-backup user sees "re-enter your key" (aligns with Phase 3's [credential] recommendation) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_
- **D-10 [ext-keystore]:** Needs external research: SunJCE AES/GCM random 12-byte IV via cipher.iv on JDK 17; Keystore2 (Android 14+/One UI) transient-failure behavior of key lookup (KeyMissing vs Unreadable) and exceptions for missing vs invalidated keys; how api(datastore) appears in the published POM / api.txt _(source: ai-auto)_
- **D-11 [sb-keystore]:** `:keystore` accepts an app-INJECTED `DataStore<Preferences>` (SB hoists `app_preferences` into one Hilt singleton), and a `KeySlot` maps SB's existing alias `secondbrain_anthropic_api_key_v1` plus its ciphertext/IV DataStore keys with no copy/migration; verify ct/IV layout against SB `core/agent/KeystoreCrypto.kt:98` during this phase. SB R1 item via orchestrator. _(source: human — orchestrator)_

Runtime Decisions (bottom of CONTEXT.md):

- **credential-adapter (refreshed vs Phase 3, ai-auto):** CONFIRMED vs Phase 3. The seam exists: public `fun interface CredentialSource { suspend fun credential(provider: ProviderId): CredentialLookup }` in core/provider/CredentialSource.kt, with `CredentialLookup.Present(credential)`, `.Missing` and `.Unreadable(cause)`. ModelRouter already maps these to `FailureReason.NotConfigured` and `CredentialUnreadable`. Phase 6 `KeystoreCredentialSource` implements this interface and adds no parallel type. A lost Keystore key, AEADBadTag or a restored-from-backup ciphertext returns `Unreadable(cause)` with a non-secret cause code, so the user sees "re-enter your key", not "not configured". Missing is only for a key that was never stored.
- **minSdk/compileSdk (orchestrator, 2026-09-30):** `:keystore` minSdk 35 / compileSdk 36.1 CONFIRMED (matches SB + CT).

### Claude's Discretion

Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)

See REQUIREMENTS.md v2 / LATER items.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| KEY-01 | Consumer can store, read and delete a BYO API key per provider, encrypted with AndroidKeyStore AES/GCM and persisted in the app's own `DataStore<Preferences>`. | `ApiKeyStore.save/read/delete/observe`; shared `AesGcm` + `KeyAccess` seam; ct/IV layout section; single-edit atomic pair write; Mutex-serialized writes |
| KEY-02 | Keys map to storage through an app-supplied explicit `KeySlot` table (alias + DataStore key per provider), so SB and CT keep their aliases/keys; engine never derives names by formula and never opens a second DataStore on the app's file. | `KeySlot` + construction-time table validation; legacy value table (aliases, pref keys) read from SB/CT source; `api(datastore-preferences)` POM/module evidence |
| KEY-03 | Reads return typed states `NotConfigured \| Ready \| KeyMissing \| Unreadable`; the decrypt path never creates a key; encryption uses a synchronized get-or-create path and `java.util.Base64` NO_WRAP-compatible encoding. | Keystore2 lookup semantics (`getKey` null vs `UnrecoverableKeyException`), `containsAlias`/`getEntry` hazard, exception mapping table, state reconciliation design |
| KEY-04 | A `KeystoreCredentialSource` adapter plugs `:keystore` into the provider seam (PROV-02); round trip verified by JVM tests via the crypto seam plus one instrumented test on the TESTER. | Adapter mapping onto the existing `CredentialLookup`; pipeline integration test via `commandPipeline { credentials = ... }`; instrumented-test design and run command |
</phase_requirements>

## Summary

`:keystore` is a small generalization of two working ports (SB `KeystoreCrypto`/`AnthropicApiKeyRepository`, CT `KeystoreCrypto`/`ApiKeyRepository`). The on-disk format is identical in both apps: AES-256/GCM, 12-byte random IV read back from `cipher.iv`, ciphertext = `doFinal` output with the 16-byte tag appended, each stored as a standard no-wrap Base64 string in two separate `String` preferences (`anthropic_api_key_ct` / `anthropic_api_key_iv`, etc.), one non-auth-bound AndroidKeyStore key per alias. Only aliases and (for CT's OpenAI/OpenRouter) pref-key names differ per app, which is exactly what the explicit `KeySlot` table carries. Nothing about the byte layout needs to change, so no user's key is stranded as long as the new code writes and reads the same layout under the same alias and names.

The most important finding is a **latent bug in both ports that the new design must not copy**: both ports obtain the key with `KeyStore.getEntry(alias, null)` and generate a fresh key whenever it returns null. On Android's `AndroidKeyStoreSpi`, `getEntry` goes through `isKeyEntry`/`containsAlias`, which swallow *every* `KeyStoreException` (not just KEY_NOT_FOUND) and return "absent". So a transient Keystore2 error looks like "no key", and the port silently overwrites the real key. `engineGetKey` is different: it returns `null` only for KEY_NOT_FOUND and throws `UnrecoverableKeyException` for everything else. The new `KeyAccess` seam must therefore use **`keyStore.getKey(alias, null)`** (never `containsAlias`, never `getEntry`) for both "does it exist" and "load it", and only the encrypt path may generate.

The typed-state tension (KEY-03 says four states, the Runtime Decision says lost key maps to `Unreadable`) resolves cleanly with two layers: `:keystore` exposes the richer `KeyState` (`NotConfigured | Ready(last4) | KeyMissing | Unreadable(cause)`) for the app's Settings UI, and `KeystoreCredentialSource` collapses it onto the existing seam (`NotConfigured -> Missing`, `Ready -> Present`, `KeyMissing` and `Unreadable -> CredentialLookup.Unreadable(stable_cause)`). No new core type is needed.

**Primary recommendation:** Build `ApiKeyStore` (public, app-injected `DataStore<Preferences>` + validated `List<KeySlot>`) over an **internal** `KeyAccess` seam (`existingKey(alias): SecretKey?` never creates; `getOrCreateKey(alias)` JVM-globally synchronized), one shared internal `AesGcm` object used by both the software test keys and AndroidKeyStore, and a thin public `KeystoreCredentialSource`. Switch `implementation(libs.datastore.prefs)` to `api(...)` (proven below to change the published POM scope runtime -> compile and to move it into the API variant of the Gradle module metadata). Prove compatibility in two legs: JVM (independent legacy-writer replica + a fixed golden vector) and one instrumented class on the TESTER (verbatim legacy writers with the literal legacy aliases and real `android.util.Base64.NO_WRAP`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Slot table (provider -> alias + pref keys) and its validation | `:keystore` public API | App (supplies the table) | Names are app data (KEY-02); the library only validates, never derives |
| Which DataStore file holds the ciphertext | App (owns and injects the `DataStore<Preferences>`) | — | The library must never open a second DataStore on the app's file (KEY-02, D-02) |
| AES/GCM seal/open, IV handling, tag layout | `:keystore` internal `AesGcm` | JCA provider (SunJCE on JVM, AndroidKeyStore on device) | One code path on both sides so JVM tests prove the real layout |
| Key material custody, get / create / absent detection | Platform AndroidKeyStore via internal `KeyAccess` | Software `KeyAccess` (tests only) | Hardware-backed, non-exportable; seam lets JVM tests simulate lost keys |
| Base64 encode/decode of the stored pair | `:keystore` (`java.util.Base64`) | — | Plain-JVM testable; byte-identical to `android.util.Base64.NO_WRAP` |
| Typed read state (`KeyState`) for UI | `:keystore` public API | App UI (renders "re-enter your key", adds provider prefix) | D-04: display prefix stays in the app |
| Credential for the provider call | `:keystore` `KeystoreCredentialSource` | `:core` `ModelRouter` (consumes `CredentialLookup`) | Adapter only; failure mapping already lives in `ModelRouter` |
| Backup-exclusion guidance for the key prefs | Docs (Phase 10 README) | App manifest/backup rules | SB has `allowBackup="true"`; ciphertext is restored without its Keystore key, which is exactly the `KeyMissing` case |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.datastore:datastore-preferences` | 1.2.1 | `DataStore<Preferences>` in `:keystore`'s public API | Already in `gradle/libs.versions.toml` as `datastore-prefs`; STACK.md pin; SB, CT and backup-engine use it. Must change from `implementation` to `api` (D-02) |
| Platform `java.security.KeyStore("AndroidKeyStore")`, `javax.crypto.Cipher("AES/GCM/NoPadding")`, `GCMParameterSpec`, `KeyGenerator`, `android.security.keystore.KeyGenParameterSpec` | platform | The crypto | No dependency; identical to both ports; `EncryptedSharedPreferences` is banned |
| `java.util.Base64` | JDK 8+ / Android API 26+ | Stored string encoding | Byte-identical to `android.util.Base64.NO_WRAP`; JVM-testable; minSdk is 35 |
| `kotlinx-coroutines-core` 1.11.0, `kotlinx-serialization-json` | via `api(project(":core"))` | `Mutex`, `Flow`, `withContext` | Already transitively present; do not add `-android` |

### Supporting (test only)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `junit:junit` | 4.13.2 (`libs.junit`) | JVM tests | all unit tests |
| `kotlinx-coroutines-test` | 1.11.0 (`libs.coroutines.test`) | `runTest` | suspend API tests |
| `testFixtures(project(":core"))` | project | `ScriptedCredentialSource`, `FakeAiProvider`, `RecordingCommitSink`, `ScriptedGate`, `ScriptedStrategy` for the pipeline integration test | KEY-04 JVM integration |
| `androidx.test.ext:junit` | 1.3.0 | `AndroidJUnit4` runner for the TESTER test | androidTest only |
| `androidx.test:runner` | 1.7.0 | `AndroidJUnitRunner`, `InstrumentationRegistry` | androidTest only |

No `datastore-preferences-core` test dependency is needed: `datastore-preferences`'s API variant already exposes `datastore` and `datastore-preferences-core`, and `PreferenceDataStoreFactory.create(scope, produceFile)` ran green in a JVM unit test of the Android library (spike, below).

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Raw AndroidKeyStore AES/GCM + DataStore | Tink / `EncryptedSharedPreferences` | Different on-disk format would strand every existing user; `security-crypto` is deprecated and banned (STACK.md) |
| Public `KeyAccess` seam | Internal seam | Recommended internal: tests live in the same module (`internal` is visible to `src/test` and `src/androidTest`, proven in the spike), and making a seam public later is purely additive, while a public seam freezes its methods forever at `v1.0.0` |
| `ApiKeyStore` returns plaintext publicly | Plaintext only through `KeystoreCredentialSource` | Recommended: keep plaintext off the public surface; `internal fun readSecret(...)` serves the adapter |

**Installation (catalog + build file, no new registry dependency beyond androidx.test):**

```kotlin
// gradle/libs.versions.toml  [versions]
androidx-test-runner = "1.7.0"
androidx-test-ext-junit = "1.3.0"
// [libraries]
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidx-test-runner" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext-junit" }

// keystore/build.gradle.kts
android { defaultConfig { minSdk = 35; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" } }
dependencies {
    api(project(":core"))
    api(libs.datastore.prefs)            // DataStore<Preferences> is in the public API
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

**Version verification:** the Gradle `maven` ecosystem is not supported by `gsd-tools query package-legitimacy check` (it prints `Usage: ... --ecosystem <npm|pypi|crates>`), so the audit below is manual. `androidx.test:runner:1.7.0`, `androidx.test.ext:junit:1.3.0` and `androidx.datastore:datastore-preferences:1.2.1` are all present in the local Gradle cache (`~/.gradle/caches/modules-2/files-2.1`) and a spike `assembleDebugAndroidTest --offline` resolved them. [VERIFIED: spike build, this session]

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `androidx.test:runner` 1.7.0 | Google Maven | years (AndroidX) | very high | android.googlesource.com/platform/frameworks/support | not machine-checkable (maven unsupported by seam); first-party Google group, pinned in STACK.md | Approved |
| `androidx.test.ext:junit` 1.3.0 | Google Maven | years (AndroidX) | very high | same | same | Approved |
| `androidx.datastore:datastore-preferences` 1.2.1 | Google Maven | existing dependency since Phase 1 | — | same | already in catalog | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
Both androidx.test coordinates come from STACK.md (which cites Google Maven metadata queried 2026-09-29) and were additionally resolved from the local cache, so no `checkpoint:human-verify` install gate is needed.

## Architecture Patterns

### System Architecture Diagram

```
App (SB / CT / :sample)                                   :keystore                                   Platform
────────────────────────                          ─────────────────────────────                ─────────────────────
DataStore<Preferences>  ──inject──►  ApiKeyStore(dataStore, slots: List<KeySlot>)
slots table (aliases,                    │  init: validate table (D-01)
 pref key names)                         │
                                         ├─ save(provider, key) ─ trim, reject blank ─► Mutex ─► withContext(io)
                                         │        │ unmapped provider → IllegalArgumentException (loud, D-03)
                                         │        ▼
                                         │   KeyAccess.getOrCreateKey(alias)  [JVM-global lock] ──► AndroidKeyStore getKey → null? generate
                                         │        ▼
                                         │   AesGcm.seal(key, utf8)  (no IV passed) → iv=cipher.iv(12) , ct||tag
                                         │        ▼
                                         │   dataStore.edit { ct = b64(ct), iv = b64(iv) }   ◄── ONE edit (no torn pair)
                                         │
                                         ├─ delete(provider) ─► Mutex ─► dataStore.edit { remove ct, iv }   (keystore alias untouched)
                                         │
                                         ├─ read(provider)/observe(provider) ─► KeyState  (no side effects, never creates)
                                         │        dataStore.data.first() ─► pair? ─no→ NotConfigured (also unmapped, torn pair)
                                         │        │yes
                                         │        ▼
                                         │   KeyAccess.existingKey(alias) ─null→ KeyMissing
                                         │        │key                         ─throws→ Unreadable(keystore_unavailable)
                                         │        ▼
                                         │   strict Base64 decode ─IAE→ Unreadable(stored_value_malformed)
                                         │   AesGcm.open(key, iv, ct) ─GSE→ Unreadable(decrypt_failed)
                                         │        ▼                      CancellationException is rethrown, never mapped
                                         │   Ready(last4)   (plaintext stays internal)
                                         ▼
KeystoreCredentialSource(store) : CredentialSource ──► NotConfigured → CredentialLookup.Missing()
        ▲                                             Ready          → CredentialLookup.Present(Credential(provider, plaintext))
        │ credentials = ...                           KeyMissing     → CredentialLookup.Unreadable("key_missing")
:core ModelRouter (existing)                          Unreadable(c)  → CredentialLookup.Unreadable(c)
  Missing → FailureReason.NotConfigured ; Unreadable(c) → FailureReason.CredentialUnreadable(provider, c)
```

### Recommended Project Structure

```
keystore/
├── build.gradle.kts                      # api(datastore), test + androidTest deps, runner (see Installation)
└── src/
    ├── main/kotlin/io/github/ygaray/voiceactionengine/keystore/
    │   ├── KeySlot.kt                    # public: provider, alias, ciphertextKey, ivKey (no defaults)
    │   ├── KeyState.kt                   # public: open abstract class + final leaves (house style, see below)
    │   ├── ApiKeyStore.kt                # public: save/delete/read/observe; internal readSecret
    │   ├── KeystoreCredentialSource.kt   # public adapter
    │   ├── KeyAccess.kt                  # internal seam: existingKey / getOrCreateKey
    │   ├── AesGcm.kt                     # internal: the ONE cipher code path
    │   └── AndroidKeyStoreKeyAccess.kt   # internal: platform impl (only file touching android.security.keystore)
    ├── test/kotlin/io/github/ygaray/voiceactionengine/keystore/
    │   ├── SoftwareKeyAccess.kt          # test double: map alias -> SecretKeySpec, counts creates, can "lose" aliases, can throw
    │   ├── LegacyWriters.kt              # INDEPENDENT replica of SB/CT writers (plain javax.crypto, no AesGcm)
    │   └── *Test.kt
    └── androidTest/kotlin/io/github/ygaray/voiceactionengine/keystore/
        └── KeystoreDeviceTest.kt         # TESTER only (see Instrumented test)
```

`scripts/verify-repo-hygiene.sh` requires every `*.kt` under `*/src/*/kotlin/*` to sit below `io/github/ygaray/voiceactionengine/` and declare a package starting with that root. The androidTest and test sources must follow it (a package like `spike` fails the hygiene gate). Delete the `KeystoreModule` marker only after another main file exists (the hygiene gate needs >= 1 main source).

### Pattern 1: One cipher path, two key sources (answers research ask 1)

**What:** `AesGcm` never takes an IV on encrypt and always reads it back from `cipher.iv`; decrypt always uses `GCMParameterSpec(128, iv)`. The key comes from `KeyAccess`, so SunJCE (tests) and AndroidKeyStore (device) execute the same lines.

**Facts behind it:**
- SunJCE on JDK 17.0.19: `cipher.init(ENCRYPT_MODE, key)` with no params generates a random IV, `cipher.iv.size == 12`, two encrypts give different IVs, `doFinal` output length = plaintext + 16 (tag appended), round trip passes. SunJCE would *accept* a caller IV, so a test double cannot detect an accidental caller-supplied IV. [VERIFIED: `java T.java` run this session on JDK 17.0.19: `iv len=12 ct len=33 (plain 17 +16 tag)`, `iv differs=true`, `reuse key+iv encrypt -> OK 17`, `roundtrip=sk-ant-abcdef1234`]
- AndroidKeyStore GCM rejects a caller-supplied IV when randomized encryption is required (the default): `InvalidAlgorithmParameterException("Caller-provided IV not permitted")`; decrypt requires exactly a 12-byte IV (`"Unsupported IV length: ..."`); the operation is begun lazily so a missing/invalid key can surface from `doFinal` as `IllegalBlockSizeException` with the real cause attached. [CITED: android15-release `KeyStoreCryptoOperationUtils.java:142`, `AndroidKeyStoreAuthenticatedAESCipherSpi.java:106-112`, `AndroidKeyStoreCipherSpiBase.java:598-636` on android.googlesource.com/platform/frameworks/base]
- Therefore the single rule enforced by code review and a unit test: **`AesGcm.seal` has no IV parameter.**

**Example (skeleton; every constant below comes from SB `KeystoreCrypto.kt` lines 70-101 or the verified JVM run):**

```kotlin
// Source: SB core/agent/KeystoreCrypto.kt:46-58, 73-86, 90, 100-101 (read this session); layout verified on JDK 17.
internal class Sealed(val iv: ByteArray, val ciphertext: ByteArray)   // ciphertext = doFinal output, tag appended

internal object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128

    fun seal(key: SecretKey, plaintext: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)              // NO IV spec: provider generates the 12-byte nonce
        val ciphertext = cipher.doFinal(plaintext)
        return Sealed(iv = cipher.iv, ciphertext = ciphertext)
    }

    fun open(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
```

### Pattern 2: Key lookup that cannot clobber (answers research ask 2)

**Findings (AOSP `android15-release`, matches the Android 15 TESTER):**

| Call | Absent key | Permanently invalidated | Other Keystore2 error (e.g. internal/transient) |
|------|-----------|-------------------------|--------------------------------------------------|
| `KeyStore.getKey(alias, null)` (`AndroidKeyStoreSpi.engineGetKey`, lines 120-138) | returns `null` (KEY_NOT_FOUND) | `UnrecoverableKeyException` (wraps `KeyPermanentlyInvalidatedException`) | `UnrecoverableKeyException` with the `android.security.KeyStoreException` as cause |
| `KeyStore.containsAlias(alias)` (via `getKeyMetadata`, lines 161-177) | `false` | `false` | **`false`** (every `KeyStoreException` swallowed, only logged) |
| `KeyStore.getEntry(alias, null)` (Android `KeyStoreSpi.engineGetEntry`: `engineIsKeyEntry` then `engineContainsAlias` before `engineGetKey`) | `null` | `null` | **`null`** |

[CITED: android.googlesource.com `AndroidKeyStoreSpi.java` and libcore `ojluni/.../KeyStoreSpi.java` (android15-release), fetched and read this session; `AndroidKeyStoreProvider.loadAndroidKeyStoreKeyFromKeystore` lines 373-392 shows `KEY_NOT_FOUND -> return null`, `KEY_PERMANENTLY_INVALIDATED -> KeyPermanentlyInvalidatedException`, `default -> UnrecoverableKeyException`]

So with the ports' `getEntry(...) as? SecretKeyEntry ?: generate`, a transient Keystore2 failure on the *encrypt* path regenerates the alias key, orphaning that provider's existing ciphertext, and on the *decrypt* path creates a new key then fails with `AEADBadTagException`. The new seam:

```kotlin
// Source: AndroidKeyStoreSpi.engineGetKey semantics above; spec fields from SB KeystoreCrypto.kt:73-86.
internal interface KeyAccess {
    /** Decrypt path. `null` means the alias is absent. Never creates. Any other failure throws (maps to Unreadable). */
    fun existingKey(alias: String): SecretKey?
    /** Encrypt path. Absent -> generate (non-auth-bound AES-256/GCM/NoPadding); present -> return it. JVM-global lock. */
    fun getOrCreateKey(alias: String): SecretKey
}
```

`AndroidKeyStoreKeyAccess.existingKey` = `KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as SecretKey?` (a non-`SecretKey` result is a bug: throw, which maps to `Unreadable`). `getOrCreateKey` = lock, `existingKey(alias) ?: KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()` with the exact spec the ports use (PURPOSE_ENCRYPT or PURPOSE_DECRYPT, BLOCK_MODE_GCM, ENCRYPTION_PADDING_NONE, key size 256, **no** `setUserAuthenticationRequired`, no StrongBox, so a key the port created and a key this code creates are interchangeable). The lock must be **process-global** (`private val LOCK = Any()` in an `object`/top-level, `synchronized(LOCK)`), not per instance: the ports' `@Synchronized` is per-instance, which only works because Hilt makes it a singleton; two `ApiKeyStore` objects (or the store plus a legacy path during migration) over the same alias would race.

**Exception -> state mapping (decrypt path), with where each is thrown:**

| Thrown | Origin | Maps to | Stable cause code (recommended) |
|--------|--------|---------|---------------------------------|
| `existingKey == null` | alias absent (restored backup, wiped keystore) | `KeyMissing` | adapter emits `key_missing` |
| `UnrecoverableKeyException` / `KeyStoreException` / other `GeneralSecurityException` from lookup | invalidated key, internal Keystore2 error, provider not loadable | `Unreadable` | `keystore_unavailable` |
| `ProviderException` and other `RuntimeException` from lookup or cipher | wedged/evicted keystore; also SunJCE on a short ciphertext (**verified**: `short ct(5) -> java.security.ProviderException [RTE]`) | `Unreadable` | `keystore_unavailable` (lookup) / `decrypt_failed` (cipher) |
| `AEADBadTagException`, `InvalidKeyException` (incl. `KeyPermanentlyInvalidatedException`), `InvalidAlgorithmParameterException` (bad IV length: verified empty IV), `IllegalBlockSizeException` | cipher init / `doFinal` | `Unreadable` | `decrypt_failed` |
| `IllegalArgumentException` | strict Base64 decode of a malformed value (verified: `"not base64!!"` and an embedded newline both throw) | `Unreadable` | `stored_value_malformed` |
| `IOException` / `CorruptionException` from `dataStore.data` | corrupt or unreadable DataStore file | `Unreadable` | `storage_unreadable` |
| `CancellationException` | caller cancelled | **rethrown, never mapped** | — |

All JDK-side outcomes in this table are [VERIFIED: java T.java on JDK 17.0.19]; Keystore2-side behavior is [CITED: AOSP android15-release sources above]. Samsung One UI could in principle deviate from AOSP, and no run on the real device happened in this research; the instrumented test must pin the observable cases (see below) [ASSUMED: One UI 7 matches AOSP for these paths].

**Catch-chain naming matters for detekt:** `TooGenericExceptionCaught` allows catch parameters named `ignored*`/`expected*` and `SwallowedException` tolerates them; the repo already relies on this (`providers/.../http/CallAwait.kt:53`: `} catch (ignored: RuntimeException) {`). Use `catch (cancelled: CancellationException) { throw cancelled }`, then `catch (ignored: GeneralSecurityException)`, `catch (ignored: IllegalArgumentException)`, `catch (ignored: IOException)`, `catch (ignored: RuntimeException)` (CancellationException is an `IllegalStateException`, so it must be first). No `@Suppress` is needed; `core/.../internal/Guarded.kt` is the repo's only suppression site and must stay so.

### Pattern 3: State reconciliation (answers research ask 3)

Recommended design (two layers, no new `:core` type):

```kotlin
// house style (CredentialLookup precedent): open abstract base + internal ctor + final leaves; no sealed, no data class, no enum
public abstract class KeyState internal constructor() {
    public class NotConfigured : KeyState()                       // equals/hashCode/toString like CredentialLookup.Missing
    public class Ready(public val last4: String) : KeyState()     // last4 == "" when key length <= 4 (D-04)
    public class KeyMissing : KeyState()
    public class Unreadable(public val cause: String) : KeyState() // stable lower snake code, same [a-z0-9_]+ rule
}
```

- `Ready.toString()` should print `KeyState.Ready` **without** `last4` (project rule: secrets never reach `toString()`; the UI reads the property). `Unreadable.toString()` prints the cause code only.
- `isStableCode` is `internal` to `:core` (`ReasonSupport.kt:21`), so `:keystore` cannot call it. Keep `KeyState.Unreadable`'s validation by constructing the cause from an internal closed set, or repeat the one-line `Regex("[a-z0-9_]+")` check locally. Do not widen `:core`'s API for this.
- `KeystoreCredentialSource.credential(provider)` maps: `NotConfigured -> CredentialLookup.Missing()`; `Ready -> CredentialLookup.Present(Credential(provider, plaintext))`; `KeyMissing -> CredentialLookup.Unreadable("key_missing")`; `Unreadable(c) -> CredentialLookup.Unreadable(c)`. The pass-through uses the plaintext from `internal fun readSecret(provider)`, never from a public getter. A stored value that decrypts to blank must become `Unreadable("stored_value_malformed")` (`Credential` rejects blank keys with `IllegalArgumentException`; CT's `save` never rejected blanks, so legacy data may contain one). The adapter must never throw: `ModelRouter` treats a throwing source as an engine fault, not as "re-enter your key" (`ModelRouter.kt:176`). [VERIFIED: `core/provider/ModelRouter.kt:175-185` read this session]

### Pattern 4: Slot table and store construction

`KeySlot` is a plain final class with four `public val`s and **no defaults** (D-01), with `equals/hashCode/toString` written by hand (the repo avoids `data class` on public API: `copy`/`componentN` freeze). Validation lives in `ApiKeyStore`'s `init` and throws `IllegalArgumentException` with messages naming the offending alias/pref-key (not secret) but never any value. Reject: empty table (recommended; an empty table is always a wiring bug), blank alias/ciphertextKey/ivKey, `ciphertextKey == ivKey` in a slot, duplicate provider, duplicate alias, and any pref-key name used twice anywhere across all slots (ct or iv, any slot). Copy the list defensively. Public constructor: `ApiKeyStore(dataStore: DataStore<Preferences>, slots: List<KeySlot>, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`; an **internal** constructor adds `keyAccess: KeyAccess` for tests.

Reads: unmapped provider -> `NotConfigured`. Writes (`save`, `delete`): unmapped provider -> `IllegalArgumentException` (loud, D-03). `save` order: `trim()`, `require(isNotEmpty)` (message must not echo the key), `writeMutex.withLock { sealed = withContext(io) { AesGcm.seal(keyAccess.getOrCreateKey(alias), utf8) }; dataStore.edit { it[ctKey] = b64(ct); it[ivKey] = b64(iv) } }` (SB `AnthropicApiKeyRepository.kt:115-129` shape; the single `edit` is the torn-pair guard). `delete` removes both prefs in one `edit` under the same mutex and leaves the Keystore alias alone (parity with both ports; also avoids deleting a key because of a transient error).

### Anti-Patterns to Avoid

- **`getEntry`, `containsAlias` or `getOrCreateKey` on the decrypt path.** Swallows transient errors and/or creates keys (see Pattern 2).
- **Calling `Cipher.init(ENCRYPT_MODE, key, spec)` with an IV.** SunJCE accepts it so JVM tests stay green while the device throws; no IV parameter on `seal`.
- **Per-instance `@Synchronized` for key creation.** Use a process-global lock.
- **Auto-clearing on a bad read** (CT did; D-07 forbids). Reads have no side effects.
- **Copying port comments verbatim.** SB's KDoc contains `T-165-26`, `(CFG-01, D-01, Phase 165)`, `165-RESEARCH`; the scanner rule "app planning id in comment" (`\b(T-\d+-\d+|WR-\d+|Phase\s+\d+\s+D-\d+)\b`) and detekt `ForbiddenComment` fail the build on those. Also drop `@Inject`/`@Singleton`/`javax.inject` imports (banned) and every `Log`/`println`.
- **`runCatching`** (banned token).
- **A public `KeyAccess`/software key class in the published artifact.** A software-key implementation in `src/main` is a footgun; keep it in `src/test`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Authenticated encryption | Custom cipher/tag handling, manual GHASH, AAD schemes | JCA `AES/GCM/NoPadding` | Legacy format has no AAD; any change strands users |
| Atomic two-value persist | Two separate edits or a file | One `dataStore.edit { }` | DataStore serializes edits; one transaction means no ct-without-iv state is ever observable |
| Base64 | Custom codec, lenient decoders | `java.util.Base64.getEncoder()/getDecoder()` | Output equals `android.util.Base64.NO_WRAP`; strict decode turns corruption into a typed state |
| Secure random IV | `SecureRandom` IV generation | Provider-generated IV via `cipher.iv` | AndroidKeyStore forbids caller IVs; SunJCE generates a random one too |
| Temp DataStore for tests | Fake `DataStore` for the happy path | `PreferenceDataStoreFactory.create(scope, produceFile = { tmp.newFile("x.preferences_pb") })` | Exercises the real serializer; verified green in an Android-library unit test |
| Failure collapse to `Unreadable` | A new generic `catch (Exception)` helper | The explicit four-branch chain with `ignored*` names | Keeps the repo's single `@Suppress` site (`Guarded.kt`) unique |
| Test double for Keystore | Robolectric | `SoftwareKeyAccess` in `src/test` | Robolectric has no AndroidKeyStore (STACK.md); the seam keeps the cipher path real |

**Key insight:** the value of `:keystore` is *not changing bytes*. Every piece that touches the stored format (cipher, IV, tag, Base64, pref names, alias) must be shared between production and the tests, and the compat tests must be written with *independent* replica code so they cannot pass tautologically.

## Legacy ciphertext/IV layout (answers research ask 5)

All values below were read from the sources this session.

| App | Provider | Keystore alias (verbatim) | DataStore keys (verbatim) | Base64 | Source |
|-----|----------|---------------------------|---------------------------|--------|--------|
| SB | Anthropic | `secondbrain_anthropic_api_key_v1` | `anthropic_api_key_ct` / `anthropic_api_key_iv` | `java.util.Base64.getEncoder()` (standard, no wrap) | SB `KeystoreCrypto.kt:98` `const val ALIAS_ANTHROPIC_API_KEY = "secondbrain_anthropic_api_key_v1"`; `ThemePreferenceManager.kt:323` `stringPreferencesKey("anthropic_api_key_ct")`, `:329` `stringPreferencesKey("anthropic_api_key_iv")`; `AnthropicApiKeyRepository.kt:124-125` |
| CT | Anthropic | `caltracker_api_key_v1` | `anthropic_api_key_ct` / `anthropic_api_key_iv` | `android.util.Base64.NO_WRAP` | CT `KeystoreCrypto.kt:95` `const val ALIAS_ANTHROPIC_API_KEY = "caltracker_api_key_v1"`; `ApiKeyRepository.kt:199-200` |
| CT | OpenAI | `caltracker_openai_api_key_v1` | `openai_api_key_ct` / `openai_api_key_iv` | same | `KeystoreCrypto.kt:119`; `ApiKeyRepository.kt:205-206` |
| CT | OpenRouter | `caltracker_openrouter_api_key_v1` | `openrouter_api_key_ct` / `openrouter_api_key_iv` | same | `KeystoreCrypto.kt:127`; `ApiKeyRepository.kt:211-212` |

CT also holds `caltracker_mcp_token_v1` and `caltracker_oa_key_v1` (`KeystoreCrypto.kt:103,111`): non-provider secrets that stay in CT (D-03), not slots.

Byte layout (identical in both ports): `iv = cipher.iv` (12 bytes) and `ciphertext = cipher.doFinal(plaintextUtf8)` (plaintext + 16-byte GCM tag appended) are stored as two separate standard-alphabet, padded, no-line-break Base64 strings; key = non-exportable AES-256, GCM, NoPadding, no user authentication. SB trims and rejects blank on save (`AnthropicApiKeyRepository.kt:116-117`); CT does not. SB's read swallows `IOException` from `dataStore.data` to an empty snapshot (`ThemePreferenceManager.kt:249-252`), and treats a torn pair as not configured (`:257`); CT does the same for a torn pair. SB's manifest has `android:allowBackup="true"` (`AndroidManifest.xml:44`) and CT's `"false"` (`:52`), which is why the restored-ciphertext-without-key case is real for SB.

Base64 equivalence: `java.util.Base64` standard encoder yields `+//+` for bytes `FB FF FE`, `+/8=` for `FB FF`, `+w==` for `FB` (padded, `+` and `/` alphabet, no newline). [VERIFIED: java G.java, JDK 17.0.19] The same inputs must be asserted against `android.util.Base64.encodeToString(bytes, NO_WRAP)` in the instrumented leg (it is a stub on the JVM).

**Golden vector for the JVM leg** (deterministic; produced this session with a fixed key and a caller-supplied IV *in the test only*, which SunJCE allows):

```kotlin
// key = bytes 0x00..0x1F (32 bytes), iv = bytes 0xA0..0xAB (12 bytes), plaintext = "sk-ant-api03-LEGACYKEY-wxyz" (27 chars) <!-- secret-scan: allow (fake canary) -->
const val GOLDEN_IV_B64 = "oKGio6Slpqeoqaqr"
const val GOLDEN_CT_B64 = "lXNRTCu/L94SDLfgKjaFmTHvAFvX7m8b5HdcH5L/ps68EN1xW+Y/wyH8kA=="   // 43 bytes = 27 + 16 tag
```
[VERIFIED: java G.java on JDK 17.0.19: `plain len=27 ct len=43`] Stored under `anthropic_api_key_ct`/`_iv`, a `SoftwareKeyAccess` holding that key under a slot alias must read back `Ready(last4 = "wxyz")`.

## Published POM, Gradle module metadata and api.txt (answers research ask 4)

Proven in a throwaway copy of this repo (outside the working tree) with a public `Probe(store: DataStore<Preferences>, p: ProviderId)` in `:keystore`:

| Declaration | Published POM (`pom-default.xml`) | Module metadata API variant (`releaseVariantReleaseApiPublication`) |
|-------------|-----------------------------------|---------------------------------------------------------------------|
| `implementation(libs.datastore.prefs)` (today) | `datastore-preferences` scope **runtime** | not listed (only `voice-action-engine-core`, `kotlin-stdlib`); runtime variant lists it |
| `api(libs.datastore.prefs)` | `datastore-preferences` scope **compile** (next to `voice-action-engine-core` and `kotlin-stdlib` 2.3.20) | listed: `voice-action-engine-core`, `datastore-preferences:1.2.1`, `kotlin-stdlib` |

So `api(...)` is required for a consumer to compile against `DataStore<Preferences>` from `:keystore`'s signatures; with `implementation` it would compile only if the consumer happened to depend on DataStore itself. [VERIFIED: `generatePomFileForReleasePublication` + `generateMetadataFileForReleasePublication` outputs, spike, this session]

`datastore-preferences` 1.2.1's own android API variant depends on `androidx.datastore:datastore`, `datastore-preferences-core`, `kotlin-stdlib` and `kotlinx-coroutines-core`; `datastore-preferences-core-android` exposes `datastore-core`, `datastore-core-okio`, `okio`. [VERIFIED: parsing the cached `.module` files, this session] The coroutines requirement (1.9.0) is lifted to 1.11.0 by `:core`'s `api`.

Metalava (`apiDump`) rendered the type with the full generic and nothing about the dependency scope:

```
ctor @KotlinOnly public Probe(androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> store, io.github.ygaray.voiceactionengine.core.ProviderId p);
```
[VERIFIED: `:keystore:apiDump` in the spike]. Dumps are **not** committed before the `v1.0.0` cut: `scripts/verify-repo-hygiene.sh` forbids any `*api.txt` while `PRE_RELEASE=1`, and `verifyApiDumpPresent` is armed by a tag. Use the isolated-copy approach (`scripts/review-api-surface.sh` / `scripts/verify-api-dump.sh` style, or `KEEP_WORK=1`) for any surface review of `:keystore`. Public functions that take a `ProviderId` value class are name-mangled on the JVM; that is already true of `CredentialSource` in `:core` and is acceptable for Kotlin consumers, but means these functions are not callable from Java.

## Test strategy details

### JVM leg (`./gradlew :keystore:testDebugUnitTest`)

Mechanics proven in the spike (outside the tree): a JUnit 4 test in `keystore/src/test/kotlin` using `TemporaryFolder`, `PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { tmp.newFile("t.preferences_pb") })`, `Base64`, SunJCE AES/GCM ran green under `testDebugUnitTest --offline` with only `libs.junit` + `libs.coroutines.test` on the classpath; `internal` declarations from `src/main` compiled and ran from both `src/test` and `src/androidTest`. Cancel the scope in `@After`.

Test inventory (each maps to a requirement):
- **KEY-01:** save/read/delete round trip per provider; two providers in one store do not clobber each other; second save replaces the pair; delete -> `NotConfigured`; blank/whitespace save rejected and previous state untouched; trim on save; unmapped provider: read `NotConfigured`, save/delete throw `IllegalArgumentException`; IV differs across two saves of the same key (non-reuse); `seal` has no IV parameter (reflection-free: assert distinct IVs and length 12).
- **KEY-02:** every slot-table rule in Pattern 4 (one test per rejected shape, plus a valid SB-shaped and CT-three-provider table); a slot table with SB-style names reads a blob written under those names; the store never touches pref keys outside its slots (write an unrelated key, `save`/`delete`, assert it is intact).
- **KEY-03:** all four states; `KeyMissing` after `SoftwareKeyAccess.lose(alias)` **and zero `getOrCreateKey` calls** across a `read`, an `observe` emission and a `credential()` (assert the counter, not just the state); tampered ciphertext bit-flip -> `Unreadable("decrypt_failed")`; wrong key (swap alias key) -> `decrypt_failed`; malformed Base64 in either half -> `stored_value_malformed`; truncated ciphertext -> `Unreadable` (SunJCE throws `ProviderException`, a `RuntimeException`, for <16 bytes); torn pair (ct only / iv only) -> `NotConfigured` (legacy parity, see Open Questions); lookup throwing `UnrecoverableKeyException` / `ProviderException` -> `keystore_unavailable`; a `DataStore` fake whose `data` throws `IOException` -> `storage_unreadable`; cancellation: a `DataStore` fake whose `data` never emits, cancel the caller, assert `CancellationException`, not `Unreadable`; `Ready(last4)` for lengths 0, 1, 4 (""), 5 (last four), and a long key; reads leave the DataStore bytes unchanged (compare snapshot before/after a failing read).
- **KEY-03 encoding:** stored strings equal `java.util.Base64.getEncoder()` output, contain no newline, and decode strictly; independent legacy-writer replica (plain `javax.crypto` + `java.util.Base64`, **not** calling `AesGcm`) writes SB-style and CT-style blobs that `ApiKeyStore` reads; the reverse (store writes, replica reads) also passes; the golden vector above reads back.
- **KEY-04:** `KeystoreCredentialSource` mapping for each of the four states (exact `CredentialLookup` leaf and cause code); unmapped provider -> `Missing`; wrong-provider credential is never produced (the returned `Credential.provider` equals the asked provider); pipeline integration through `commandPipeline { provider(FakeAiProvider(...)); this.credentials = KeystoreCredentialSource(store); ... }` (pattern from `core/src/test/.../KeyIsolationTest.kt`): saved key reaches the fake provider; lost key yields `FailureReason.CredentialUnreadable(provider, "key_missing")`, never `NotConfigured`; never-stored yields `NotConfigured`.
- **Secrets:** canary test (as in TEL-04 style): save `CANARY-KEY-...`, then assert it appears in no `toString()` of `KeyState`/`KeySlot`/`Credential`/`CredentialLookup`/`ApiKeyStore`/`KeystoreCredentialSource`, no exception message thrown on any failure path, and not in the persisted Preferences as plaintext.

### Instrumented leg (TESTER only)

Package of the test APK: for an AGP library the androidTest APK is self-instrumenting under `<namespace>.test` (here `io.github.ygaray.voiceactionengine.keystore.test`). Its Keystore namespace is its own app uid, so the literal legacy aliases cannot touch SB or CT keys (D-06) [CITED: Keystore2 app-domain keys are per-uid; ASSUMED not re-verified on device]. The class `KeystoreDeviceTest` (under `…/keystore/` in `src/androidTest/kotlin`) should contain:

1. **SB legacy compat:** verbatim copy of SB `KeystoreCrypto.encrypt` (getEntry/KeyGenerator path, alias `secondbrain_anthropic_api_key_v1`) + `java.util.Base64` writes `anthropic_api_key_ct/_iv` into a temp-file `PreferenceDataStoreFactory` store under `InstrumentationRegistry...targetContext.cacheDir`; then `ApiKeyStore` (real `AndroidKeyStoreKeyAccess`) reads `Ready(last4)` and `KeystoreCredentialSource` returns the same plaintext.
2. **CT legacy compat x3:** same with `android.util.Base64.encodeToString(.., NO_WRAP)` and aliases `caltracker_api_key_v1`, `caltracker_openai_api_key_v1`, `caltracker_openrouter_api_key_v1` and their pref names.
3. **Reverse direction:** `ApiKeyStore.save` then the verbatim legacy decrypt reads the plaintext (a consumer must be able to roll back).
4. **Base64 parity:** for byte arrays of length 0..40 plus the `FB FF FE` family, `android.util.Base64.NO_WRAP` equals `java.util.Base64.getEncoder()`.
5. **KeyMissing on a real Keystore:** save, `KeyStore.deleteEntry(alias)`, `read` -> `KeyMissing`, and afterwards `getKey(alias, null)` is still `null` (the read did not create a key).
6. **Concurrent first use:** N threads (barrier-started) save under a fresh alias via separate store instances; afterwards every one reads back `Ready` (a lost-race key would make earlier ciphertexts undecryptable).
7. **Tampered ciphertext on device -> `Unreadable("decrypt_failed")`.**
8. `@After` deletes every alias it created.

**Run command (research ask 6).** The AGP task is `:keystore:connectedDebugAndroidTest` (listed by `:keystore:tasks --all` in the spike; `:keystore:assembleDebugAndroidTest` builds the APK and ran green offline). AGP runs on all attached devices unless restricted, and the TESTER appears twice in `adb devices` (`R5CT10XNKQN` over USB and `100.118.21.106:1496` wireless), so always restrict with `ANDROID_SERIAL`:

```bash
ANDROID_SERIAL=R5CT10XNKQN ./gradlew :keystore:connectedDebugAndroidTest --offline --console=plain
```
`ANDROID_SERIAL` restricts connected checks to one device (and errors if it is absent) [CITED: developer.android.com/studio/test/command-line and community write-ups; MEDIUM]. Wrap it in a script (suggested `scripts/run-keystore-instrumented.sh`) that first refuses to proceed unless `adb -s R5CT10XNKQN get-state` is `device`, `adb -s R5CT10XNKQN shell getprop ro.product.model` is `SM-S908U`, the personal phone `100.126.94.47` is not the target, and then prints the foreground package (informational: someone else's harness may be using the rig). Device availability policy: `~/.claude/context/devices/common.md` (resolve, check, act or report; one agent device-tester per device; `connectedAndroidTest` wipes only the test package's data, which is a throwaway APK).

Who runs it: `execute-phase` runs the blocking agentic Gate-1 (`gsd-verify-work-agentic`); the two-gate policy says plans should not emit a hand-written self-UAT task or a per-phase human checkpoint. Plans should therefore (a) author the test and the guarded script, (b) make `check` compile and lint the instrumented sources (`tasks.named("check") { dependsOn("assembleDebugAndroidTest") }` inside `:keystore`), and (c) record the TESTER run as Gate-1 evidence (`06-SELF-UAT.md`) produced by the agentic gate, with an INFRA (not FAIL) outcome if the device is unavailable. A device-unavailable result leaves SC4's instrumented half open; see Open Questions.

## Repo invariants to honour (answers research ask 6)

- **Gradle tasks that already gate `:keystore`** (from `gradle/invariants.gradle.kts`, applied at the bottom of `keystore/build.gradle.kts`; `:keystore:check --dry-run` lists them): `detekt`, `scanBannedConstructs` (scans `src/main` only), `verifyBytecodeLevel` (AAR classes major 55), `verifyExplicitApiStrict`, `verifyModuleGraph` (`:keystore -> :core` only), `verifyNoDiArtifacts`, `verifyApiDumpPresent`, `lint` (+ unit-test and androidTest lint models), `testDebugUnitTest`. Repo-wide ones hosted on `:core`: `verifyNoDetektBaseline`, `verifyInvariantScannerControls`, `verifyCoreDependencyAllowlist`, `verifyDetektControls`, `verifyNoTestFixturesPublished`. Scripts: `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`, `scripts/review-api-surface.sh` (runs `:core:apiDump` only), `scripts/verify-api-dump.sh`.
- **Banned in `src/main`** (scanner, comments and string literals blanked): `runCatching`, `println`/`print`, `kotlin.io.print*`, `System.out/err`, `.printStackTrace(`, FQ DI annotations, imports of `okhttp3.internal`/`mockwebserver3`/`okhttp3.coroutines`/`android.util.Log`/`javax.inject`/`jakarta.inject`/`dagger`/`androidx.hilt`, and planning ids in comments. `android.util.Base64` is **not** banned but must not be used in `src/main` (JVM stub); it is allowed in the androidTest legacy replica. detekt (`buildUponDefaultConfig`, `maxIssues: 0`, no baseline) also bans `TODO:`/`FIXME:`/`STOPSHIP:` comments.
- **detekt only covers** `src/main/kotlin`, `src/test/kotlin`, `src/testFixtures/kotlin` (`build.gradle.kts` `subprojects { ... source.setFrom(...) }`). `src/androidTest/kotlin` is **not** linted. Recommended: add `"src/androidTest/kotlin"` to that list so the instrumented class meets the same zero-issue bar. `MagicNumber` is active (name 12/16/128/256/4 as `const val`s; tests are excluded by default); also watch `ReturnCount`, `TooManyFunctions` (class threshold 12 in this repo), and keep one top-level class per file named after it (`MatchingDeclarationName`).
- **Explicit API strict:** every `public` declaration needs explicit visibility and return types; leave seam/crypto internals `internal`.
- **Offline verification style used by earlier phases:** `./gradlew :keystore:testDebugUnitTest --tests '*Class' --offline -q`, `./gradlew :keystore:detekt :keystore:scanBannedConstructs --offline -q` after each task, `./gradlew :keystore:check :core:check --offline` per wave, `./gradlew check --offline` plus the four scripts before verify-work (Phase 5 `05-VALIDATION.md`). One Gradle invocation per working tree at a time. `:keystore:check` does **not** run `connectedDebugAndroidTest`.
- **Phase 5 plan style** (for slicing): numbered `NN-MM-PLAN.md` files, one wave per plan when they share a Gradle tree, a tracer task first, `autonomous: false` only for human/orchestrator gates, `must_haves.truths/prohibitions`, `<automated>` verify commands with `fails_when`, commit by explicit path (never `git add -A`), no edits to `CROSS-REPO-SCOPE-CONTRACT.md`, no tags, nothing under `.planning/graphs/` or `graphify-out/` staged.
- **Suggested plan slicing** (planner's call): (1) build wiring + tracer: `api` datastore, catalog/test deps, `KeySlot`+validation, `KeyState`, `AesGcm`, `KeyAccess`, `SoftwareKeyAccess`, first save/read round trip on JVM; (2) `ApiKeyStore` full behavior and the exhaustive state/error tests; (3) legacy-writer replica + golden vector + KEY-02 table tests; (4) `KeystoreCredentialSource` + pipeline integration + canary/redaction tests; (5) `AndroidKeyStoreKeyAccess` + `KeystoreDeviceTest` + guarded script + `check` wiring (compile-only in `check`); (6) device evidence via the Gate-1 run.
- **Docs hand-off (Phase 10):** README guidance to exclude the key prefs from Auto Backup (SB restores ciphertext without keys -> `KeyMissing`), and that `Unreadable`/`KeyMissing` should render "re-enter your key", never a network error. Not part of this phase's code.

## Common Pitfalls

### Pitfall 1: Using `containsAlias`/`getEntry` to decide "key absent"
**What goes wrong:** a transient Keystore2 error reads as "absent": the decrypt path would report `KeyMissing` wrongly and the encrypt path would overwrite a good key.
**Why it happens:** `AndroidKeyStoreSpi.getKeyMetadata` catches every `KeyStoreException` and returns null.
**How to avoid:** `existingKey` = `getKey(alias, null)` only; null is the one and only "absent" signal.
**Warning signs:** a `KeyAccess` implementation that calls `containsAlias` or `getEntry`; a test double that cannot throw from `existingKey`.

### Pitfall 2: Decrypt path creates a key
**What goes wrong:** after a restore the user sees "corrupt key" instead of "re-enter" and a new empty key now sits under the alias.
**How to avoid:** `read`/`observe`/`credential` call only `existingKey`; the JVM and device tests assert zero creates and `getKey == null` after the read.

### Pitfall 3: SunJCE hides the IV-at-encrypt bug
**What goes wrong:** code passes a `GCMParameterSpec` on encrypt; JVM tests pass, the device throws `InvalidAlgorithmParameterException("Caller-provided IV not permitted")`.
**How to avoid:** `seal` takes no IV; the instrumented test saves through the real path.

### Pitfall 4: Comment/format drift from the ports fails the gate
**What goes wrong:** ported KDoc carries `T-165-26`, `Phase 165`, `@Inject`.
**How to avoid:** write fresh comments; run `:keystore:scanBannedConstructs :keystore:detekt` after every task.

### Pitfall 5: Blank or whitespace legacy plaintext
**What goes wrong:** CT never trimmed or rejected blanks, so a stored blank decrypts to `""`; `Credential` throws `IllegalArgumentException`, which in the adapter would escape and become an engine fault.
**How to avoid:** adapter maps a blank plaintext to `Unreadable("stored_value_malformed")`; test it.

### Pitfall 6: Per-instance lock only
**What goes wrong:** two `ApiKeyStore` instances (or store + legacy code during a migration) race `generateKey` on one alias; the loser's ciphertext becomes undecryptable.
**How to avoid:** process-global lock inside `AndroidKeyStoreKeyAccess`, plus the per-store `Mutex` around encrypt+persist; concurrent instrumented test.

### Pitfall 7: Two DataStores on one file
**What goes wrong:** `IllegalStateException: multiple DataStores active for the same file`.
**How to avoid:** the library never calls `preferencesDataStore`/`PreferenceDataStoreFactory` in `src/main`; tests use temp files. Add a test-time assertion by grep (no `PreferenceDataStoreFactory` or `preferencesDataStore` in `src/main`) in the plan's verify step.

### Pitfall 8: Unreadable looks like a crash inside a `Flow`
**What goes wrong:** an exception escaping `dataStore.data.map { decrypt }` terminates the collector.
**How to avoid:** the catch chain wraps the decrypt step inside the `map`, and `IOException` from `data` is mapped with `.catch { }` to `Unreadable("storage_unreadable")`; cancellation is rethrown.

### Pitfall 9: `Dispatchers.IO` hard-wired
**What goes wrong:** tests become slow/flaky.
**How to avoid:** constructor parameter `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`; tests inject `Dispatchers.Unconfined` or the `runTest` dispatcher. AndroidKeyStore calls are blocking binder IPC, so production keeps them off the main thread.

## Code Examples

### Strict Base64 + last4 (D-04, D-05)

```kotlin
// Standard encoder == android.util.Base64.NO_WRAP output; strict decoder throws IllegalArgumentException (verified on JDK 17).
private val encoder = java.util.Base64.getEncoder()
private val decoder = java.util.Base64.getDecoder()
private const val LAST_CHARS = 4

internal fun last4(plaintext: String): String =
    if (plaintext.length <= LAST_CHARS) "" else plaintext.takeLast(LAST_CHARS)   // <= 4 chars reveals nothing
```

### Decrypt path skeleton (shape only)

```kotlin
internal suspend fun readSecret(provider: ProviderId): SecretRead {
    val slot = slotOf[provider] ?: return SecretRead.NotConfigured
    return try {
        val prefs = dataStore.data.first()
        val ct = prefs[slot.ct] ?: return SecretRead.NotConfigured     // torn pair is NotConfigured (legacy parity)
        val iv = prefs[slot.iv] ?: return SecretRead.NotConfigured
        withContext(ioDispatcher) { openSecret(slot, iv, ct) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: IOException) {
        SecretRead.Unreadable("storage_unreadable")
    } // ... GeneralSecurityException / IllegalArgumentException / RuntimeException branches, parameters named ignored*
}
```
(`ApiKeyStore.read` = this result with the plaintext reduced to `Ready(last4(...))`.)

### Pipeline integration (KEY-04)

```kotlin
// Pattern from core/src/test/kotlin/.../KeyIsolationTest.kt (commandPipeline DSL, `credentials = ...`)
val pipeline = commandPipeline {
    tier(strategyThatCallsSessionModel)
    provider(FakeAiProvider(ProviderId.ANTHROPIC, List(1) { { _ -> FakeAiProvider.reply("ok", Usage.ZERO) } }))
    providerSelection = ScriptedSelectionSource /* anthropic */
    this.credentials = KeystoreCredentialSource(store)
    gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `KeyStore.getEntry` + generate-if-null | `KeyStore.getKey` null / throw distinction | this phase (defect in both ports) | Transient failure can no longer overwrite a key or fake `KeyMissing` |
| Per-app KeystoreCrypto copies | One generalized `:keystore` over an app slot table | v1.0 | SB/CT share code; formats frozen by the compat tests |
| `EncryptedSharedPreferences` | AndroidKeyStore AES/GCM + DataStore | already in STACK.md | `security-crypto` deprecated |
| CT auto-clear on decrypt failure | Never clear; app decides | D-07 | Restored-backup user sees "re-enter", not a silent reset |

**Deprecated/outdated:** `android.util.Base64` in testable code (JVM stub); `getOrCreateKey` on the decrypt path.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Samsung One UI 7 / Android 15 on the TESTER behaves like AOSP `android15-release` for `getKey` null vs `UnrecoverableKeyException`, lazy cipher init and "Caller-provided IV not permitted". | Pattern 2 | A device-specific exception class could land in a different branch; mitigated because every branch other than cancellation maps to `Unreadable`, and `KeyMissing` rests only on `getKey == null`. The instrumented test (#5, #7) pins it. |
| A2 | `ANDROID_SERIAL` restricts AGP `connectedDebugAndroidTest` to one device (sources are secondary). | Instrumented run | If ignored, the run could execute twice on the same phone (USB + wireless entries) or, worse, on another attached device. The wrapper script must check `adb devices` and refuse unexpected serials; post-run, confirm via the result XML device name. |
| A3 | Keystore2 app-domain keys are per-uid, so the test APK's literal legacy aliases cannot collide with SB/CT. | Instrumented leg | If wrong, the test could create or delete keys in SB/CT. Mitigated by D-06's reasoning; `@After` deletes only aliases the test created; still prefer a pre-flight that SB/CT packages are different uids (they are different packages). |
| A4 | Cause-code vocabulary (`key_missing`, `decrypt_failed`, `stored_value_malformed`, `keystore_unavailable`, `storage_unreadable`) is a design recommendation, not a locked value. | Pattern 2/3 | Only naming; they pass the `[a-z0-9_]+` rule. Confirm with the orchestrator because they become frozen strings in consumers' UI mappings. |
| A5 | `IOException` from `dataStore.data` should surface as `Unreadable("storage_unreadable")`, not swallowed to `NotConfigured` as SB does. | Mapping table | Behavior differs from SB's `catch -> emptyPreferences()`. Chosen because "not configured" would hide that a key may exist. |
| A6 | A torn pair (only ct or only iv) stays `NotConfigured` (both ports). | Tests, Open Questions | If product wants "re-enter your key" for torn pairs it should be `Unreadable`; atomic single-edit writes make a torn pair impossible from this library. |
| A7 | `Ready.toString()` omits `last4`. | Pattern 3 | Minor; UI reads the property. |
| A8 | Keeping the Keystore alias on `delete` (pair removal only) is acceptable. | Pattern 4 | Orphaned key remains until the next save reuses it; matches both ports and avoids deleting a key on a transient error. |

## Open Questions

1. **Who runs the TESTER leg, and when?**
   - What we know: SC4 requires one instrumented test passing on the TESTER; two-gate policy puts device runs in the agentic Gate-1, not in executor tasks. During this research the TESTER was awake with another project's harness (`io.github.ygaray.yahirandroidtasteharness`) in the foreground and 13 Claude sessions were alive on the host, so contention is real.
   - What's unclear: whether the orchestrator wants the executor to run `scripts/run-keystore-instrumented.sh` itself or defer to Gate-1.
   - Recommendation: plans build + compile + lint the instrumented class and the guarded script; Gate-1 runs it and records `06-SELF-UAT.md`; a device-unavailable result is INFRA and leaves SC4's device half open (ask the orchestrator, per the A13 stop/reconvene rule, before declaring the phase complete without it).

2. **Torn pair and `IOException` semantics (A5, A6).** Defaults above preserve legacy for torn pairs and deliberately deviate for `IOException`. Confirm or override.

3. **Empty slot table allowed?** Recommendation: reject (`require(slots.isNotEmpty())`).

4. **Should `save` self-heal when `existingKey` throws `UnrecoverableKeyException`?** Our keys are non-auth-bound so permanent invalidation should not occur; a wedged alias would make `save` throw until the app deletes the alias itself. Recommendation: no self-heal in v1.0 (never delete a key on a possibly transient error); revisit as an additive `resetKey(provider)` if a real case appears.

5. **Public seam later.** If SB/CT want to unit-test with software keys, an additive public constructor can expose `KeyAccess`; not needed for v1.0.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle, JVM tests, SunJCE facts | ✓ | OpenJDK 17.0.19 | — |
| Android SDK | AGP, androidTest APK | ✓ | `ANDROID_HOME=/home/yahir/Android/Sdk`; platforms android-35/36/36.1/37.0; build-tools 34-37 | — |
| Gradle offline cache | `--offline` plans | ✓ | datastore 1.2.1, androidx.test runner 1.7.0 / ext-junit 1.3.0 present; spike `testDebugUnitTest` + `assembleDebugAndroidTest --offline` green | — |
| `adb` | instrumented run | ✓ | `/usr/bin/adb` | — |
| TESTER device | SC4 instrumented leg | ✓ reachable, ⚠ contended | `R5CT10XNKQN` (USB) and `100.118.21.106:1496` (wireless, same phone) both `device`; SM-S908U, Android 15, SDK 35, screen awake, another project's harness in the foreground | If unavailable: INFRA outcome, SC4 device half stays open; never substitute the personal phone (`100.126.94.47`) |
| `gsd-tools package-legitimacy` for Maven | audit | ✗ (npm/pypi/crates only) | — | manual audit above |

**Not done on purpose:** the real-device spike (Keystore2 `getKey` of an absent alias etc.) was **not** run, because the TESTER's foreground belonged to another project's harness and the device policy says one agent tester at a time and flag state changes. Everything device-side is therefore [CITED] from AOSP source, with the instrumented test as the pin. State change made by this research: none on any device; one throwaway copy of the repo and Java scratch files under the session scratchpad only; no commit; SB and CT repos only read.

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM, `testDebugUnitTest`); AndroidX Test runner 1.7.0 + ext-junit 1.3.0 (device) |
| Config file | `keystore/build.gradle.kts` (Wave 0 adds test + androidTest deps and the runner), `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :keystore:testDebugUnitTest --tests '*<Class>' --offline -q` |
| Full suite command | `./gradlew check --offline` (module-scoped: `./gradlew :keystore:check :core:check --offline`) |
| Device command | `ANDROID_SERIAL=R5CT10XNKQN ./gradlew :keystore:connectedDebugAndroidTest --offline --console=plain` via the guarded script |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| KEY-01 | save/read/delete per provider, replace, isolation, blank rejection, trim, unmapped write throws | unit (temp-file DataStore + `SoftwareKeyAccess`) | `./gradlew :keystore:testDebugUnitTest --tests '*ApiKeyStoreTest' --offline -q` | ❌ Wave 0 |
| KEY-01 | distinct 12-byte IV per save; stored pair is one atomic edit | unit | `... --tests '*AesGcmTest' --tests '*ApiKeyStoreAtomicityTest' ...` | ❌ Wave 0 |
| KEY-02 | slot-table validation (duplicate provider/alias/pref key, ct==iv, blank, empty) | unit | `... --tests '*KeySlotValidationTest' ...` | ❌ Wave 0 |
| KEY-02 | legacy SB/CT layouts read back; unrelated prefs untouched; no DataStore creation in `src/main` | unit + grep | `... --tests '*LegacyCompatJvmTest' ...`; `! grep -rnE 'preferencesDataStore|PreferenceDataStoreFactory' keystore/src/main` | ❌ Wave 0 |
| KEY-02 | real-Keystore legacy compat with literal aliases, SB and CT x3, both directions, `android.util.Base64` parity | instrumented (TESTER) | `ANDROID_SERIAL=R5CT10XNKQN ./gradlew :keystore:connectedDebugAndroidTest ...` | ❌ Wave 0 |
| KEY-03 | four states; zero creates on read paths; tamper/wrong-key/malformed/truncated/lookup-throws/IOException; cancellation rethrown; reads side-effect free | unit | `... --tests '*KeyStateTest' --tests '*ReadNeverCreatesKeyTest' --tests '*UnreadableMappingTest' ...` | ❌ Wave 0 |
| KEY-03 | `KeyMissing` on a real Keystore and `getKey` still null afterwards; concurrent first-use race | instrumented (TESTER) | same device command | ❌ Wave 0 |
| KEY-03 | encoding equals NO_WRAP-compatible standard Base64; golden vector | unit | `... --tests '*GoldenVectorTest' ...` | ❌ Wave 0 |
| KEY-04 | adapter mapping for all four states + unmapped; never throws; wrong-provider impossible | unit | `... --tests '*KeystoreCredentialSourceTest' ...` | ❌ Wave 0 |
| KEY-04 | pipeline round trip through `commandPipeline` (key reaches provider; lost key -> `CredentialUnreadable`) | integration | `... --tests '*KeystorePipelineTest' ...` | ❌ Wave 0 |
| KEY-01..04 | no secret in any `toString`/exception/persisted plaintext (canary) | unit | `... --tests '*KeystoreCanaryTest' ...` | ❌ Wave 0 |
| Gates | detekt zero, banned constructs, explicit API strict, bytecode 55, no DI, module graph, lint incl. androidTest | build | `./gradlew :keystore:check --offline` | ✅ existing gates, new sources must pass |
| Gates | published POM/module put `datastore-preferences` in compile scope | build | `./gradlew :keystore:generatePomFileForReleasePublication :keystore:generateMetadataFileForReleasePublication --offline -q` then inspect `keystore/build/publications/release/pom-default.xml` for `datastore-preferences` + `<scope>compile</scope>` | ❌ (suggest a `verifyDatastoreIsApi` check task) |

### Sampling Rate

- **Per task commit:** the quick command for the touched class plus `./gradlew :keystore:detekt :keystore:scanBannedConstructs --offline -q`
- **Per wave merge:** `./gradlew :keystore:check :core:check --offline`
- **Phase gate:** `./gradlew check --offline` green, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`, `scripts/review-api-surface.sh --expect-sealed-complete` (core only, must stay OK) and the Gate-1 TESTER run recorded
- **Max feedback latency:** ~240 s for the full suite (Phase 5 precedent); the spike's `:keystore:check -x detekt` took 3 s warm

### Wave 0 Gaps

- [ ] `keystore/build.gradle.kts`: `api(libs.datastore.prefs)`, test/androidTest deps, `testInstrumentationRunner`, `check` depends on `assembleDebugAndroidTest`
- [ ] `gradle/libs.versions.toml`: `androidx-test-runner` 1.7.0, `androidx-test-ext-junit` 1.3.0
- [ ] root `build.gradle.kts` detekt `source`: add `src/androidTest/kotlin`
- [ ] `keystore/src/test/kotlin/.../SoftwareKeyAccess.kt`, `LegacyWriters.kt` (independent replica), shared temp-DataStore helper
- [ ] `keystore/src/androidTest/kotlin/.../KeystoreDeviceTest.kt` and `scripts/run-keystore-instrumented.sh`
- [ ] All test classes in the table above
- [ ] Optional hardening task `verifyDatastoreIsApi` in `:keystore` (parses the generated POM) so a regression to `implementation` fails `check`

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no user auth; keys are deliberately non-auth-bound) | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (key isolation per provider/alias) | validated slot table (unique alias/pref keys); `CredentialSource` returns only the asked provider; `ModelRouter` already refuses a mismatching credential |
| V5 Input Validation | yes | trim + reject blank key; strict Base64 decode; slot-table validation; stable-code regex on causes |
| V6 Cryptography | yes | JCA `AES/GCM/NoPadding`, provider-generated 12-byte IV, 128-bit tag, hardware-backed non-exportable key; never hand-rolled |
| V7 Error handling & logging | yes | no logging at all in the library; no secret in `toString`/exception messages; causes are stable codes |
| V8 Data protection | yes | ciphertext only at rest; plaintext only in the returned `Credential`; backup-exclusion guidance (docs) |

### Known Threat Patterns for AndroidKeyStore + DataStore

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Key leak via logs, `toString`, exceptions, telemetry | Information disclosure | redacting `toString` on every type, canary test, no `Log`/`println` (scanner), generic `require` messages |
| Silent key replacement (decrypt-path or transient-failure `generateKey`) orphaning ciphertext | Tampering / DoS | `getKey`-based lookup, create only on encrypt path, process-global lock, zero-create assertions |
| IV reuse | Information disclosure | provider-generated random IV only; no IV parameter on `seal` |
| Cross-provider key confusion / alias collision clobbering | Spoofing / Tampering | table validation at construction; adapter returns only the requested provider's key |
| Ciphertext restored via Auto Backup without its key | DoS (self) | `KeyMissing` state, no key creation on read, README guidance |
| Plaintext lingering in memory / Strings | Information disclosure | accepted for v1.0 (JVM `String`, as in the ports); no caching of plaintext in the store |
| Concurrent first-use race | Tampering | process-global lock + `Mutex` around encrypt+persist |
| Corrupt/malicious stored value crashing the collector | DoS | catch chain maps to `Unreadable`, cancellation preserved |

## Sources

### Primary (HIGH confidence)
- SB `app/src/main/java/com/example/secondbrain/core/agent/KeystoreCrypto.kt` (full), `AnthropicApiKeyRepository.kt` (full), `KeystoreCryptoSeam.kt`, `feature/settings/ThemePreferenceManager.kt:244-332`, `AndroidManifest.xml:44` — read-only, this session
- CT `app/src/main/java/com/caltracker/app/data/security/KeystoreCrypto.kt`, `data/repository/ApiKeyRepository.kt`, `AndroidManifest.xml:52` — read-only, this session
- Repo: `keystore/build.gradle.kts`, `gradle/invariants.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`, `core/provider/CredentialSource.kt`, `core/provider/ModelRouter.kt:171-190`, `core/Credential.kt`, `core/ProviderId.kt`, `core/failure/ReasonSupport.kt`, `core/internal/Guarded.kt`, `core/testFixtures/.../ScriptedSources.kt`, `core/src/test/.../KeyIsolationTest.kt`, `scripts/*.sh`, Phase 5 `05-VALIDATION.md` / `05-12-PLAN.md` / `05-SELF-UAT.md`
- Executed on JDK 17.0.19: `java T.java` (IV/tag/exception behavior), `java G.java` (golden vector, Base64 alphabet)
- Throwaway repo copy (scratchpad, not the working tree): `:keystore:testDebugUnitTest`, `:keystore:assembleDebugAndroidTest`, `:keystore:check -x detekt`, `generatePomFileForReleasePublication`, `generateMetadataFileForReleasePublication`, `:keystore:apiDump`, `:keystore:check --dry-run`, all `--offline`
- Cached Gradle metadata: `androidx.datastore:datastore-preferences(-core)(-android)` 1.2.1 `.module`/`.pom`

### Secondary (MEDIUM confidence)
- [AOSP `AndroidKeyStoreSpi.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/keystore/java/android/security/keystore2/AndroidKeyStoreSpi.java), [`AndroidKeyStoreProvider.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/keystore/java/android/security/keystore2/AndroidKeyStoreProvider.java), [`AndroidKeyStoreCipherSpiBase.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/keystore/java/android/security/keystore2/AndroidKeyStoreCipherSpiBase.java), [`AndroidKeyStoreAuthenticatedAESCipherSpi.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/keystore/java/android/security/keystore2/AndroidKeyStoreAuthenticatedAESCipherSpi.java), [`KeyStoreException.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/keystore/java/android/security/KeyStoreException.java), [libcore `KeyStoreSpi.java`](https://android.googlesource.com/platform/libcore/+/refs/heads/android15-release/ojluni/src/main/java/java/security/KeyStoreSpi.java) (official AOSP source, fetched and read this session; branch matches the TESTER's Android 15 but not Samsung's build)
- [OpenJDK 17u `KeyStoreSpi.java`](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/security/KeyStoreSpi.java) (contrast with Android's `engineGetEntry`)
- [Android: Test from the command line](https://developer.android.com/studio/test/command-line) and community write-ups on `ANDROID_SERIAL` with `connectedAndroidTest`

### Tertiary (LOW confidence)
- None relied upon. Real-device One UI behavior is [ASSUMED] (A1) pending the instrumented test.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions are pinned in the catalog/STACK.md and resolved offline in a spike.
- Architecture: HIGH — a direct generalization of two working ports, with the one defect (`getEntry`/`containsAlias`) found and corrected from platform source.
- Pitfalls: HIGH for build/lint/scanner items (reproduced or read from repo gates); MEDIUM for Keystore2/One UI runtime behavior (AOSP source only, no device run).

**Research date:** 2026-10-01
**Valid until:** 2026-10-31 (stable platform APIs; re-check only if AGP, DataStore or the `:keystore` module layout changes)
