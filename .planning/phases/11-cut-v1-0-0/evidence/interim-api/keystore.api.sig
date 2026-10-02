// Signature format: 4.0
package io.github.ygaray.voiceactionengine.keystore {

  public final class ApiKeyStore {
    ctor public ApiKeyStore(androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> dataStore, java.util.List<io.github.ygaray.voiceactionengine.keystore.KeySlot> slots);
    ctor public ApiKeyStore(androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> dataStore, java.util.List<io.github.ygaray.voiceactionengine.keystore.KeySlot> slots, kotlinx.coroutines.CoroutineDispatcher ioDispatcher);
    method @KotlinOnly public suspend Object? delete(io.github.ygaray.voiceactionengine.core.ProviderId provider, kotlin.coroutines.Continuation<? super java.lang.Void>);
    method @KotlinOnly public kotlinx.coroutines.flow.Flow<io.github.ygaray.voiceactionengine.keystore.KeyState> observe(io.github.ygaray.voiceactionengine.core.ProviderId provider);
    method @KotlinOnly public suspend Object? read(io.github.ygaray.voiceactionengine.core.ProviderId provider, kotlin.coroutines.Continuation<? super io.github.ygaray.voiceactionengine.keystore.KeyState>);
    method @KotlinOnly public suspend Object? save(io.github.ygaray.voiceactionengine.core.ProviderId provider, String apiKey, kotlin.coroutines.Continuation<? super java.lang.Void>);
  }

  public final class KeySlot {
    ctor @KotlinOnly public KeySlot(io.github.ygaray.voiceactionengine.core.ProviderId provider, String alias, String ciphertextKey, String ivKey);
    method @InaccessibleFromKotlin public String getAlias();
    method @InaccessibleFromKotlin public String getCiphertextKey();
    method @InaccessibleFromKotlin public String getIvKey();
    property public String alias;
    property public String ciphertextKey;
    property public String ivKey;
    property public io.github.ygaray.voiceactionengine.core.ProviderId provider;
  }

  public abstract class KeyState {
  }

  public static final class KeyState.KeyMissing extends io.github.ygaray.voiceactionengine.keystore.KeyState {
    ctor public KeyState.KeyMissing();
  }

  public static final class KeyState.NotConfigured extends io.github.ygaray.voiceactionengine.keystore.KeyState {
    ctor public KeyState.NotConfigured();
  }

  public static final class KeyState.Ready extends io.github.ygaray.voiceactionengine.keystore.KeyState {
    ctor public KeyState.Ready(String last4);
    method @InaccessibleFromKotlin public String getLast4();
    property public String last4;
  }

  public static final class KeyState.Unreadable extends io.github.ygaray.voiceactionengine.keystore.KeyState {
    ctor public KeyState.Unreadable(String cause);
    method @InaccessibleFromKotlin public String getCause();
    property public String cause;
  }

  public final class KeystoreCauseCodes {
    method @InaccessibleFromKotlin public String getDECRYPT_FAILED();
    method @InaccessibleFromKotlin public String getKEYSTORE_UNAVAILABLE();
    method @InaccessibleFromKotlin public String getKEY_MISSING();
    method @InaccessibleFromKotlin public String getSTORAGE_UNREADABLE();
    method @InaccessibleFromKotlin public String getSTORED_VALUE_MALFORMED();
    property public String DECRYPT_FAILED;
    property public String KEYSTORE_UNAVAILABLE;
    property public String KEY_MISSING;
    property public String STORAGE_UNREADABLE;
    property public String STORED_VALUE_MALFORMED;
    field public static final io.github.ygaray.voiceactionengine.keystore.KeystoreCauseCodes INSTANCE;
  }

  public final class KeystoreCredentialSource implements io.github.ygaray.voiceactionengine.core.provider.CredentialSource {
    ctor public KeystoreCredentialSource(io.github.ygaray.voiceactionengine.keystore.ApiKeyStore store);
    method @KotlinOnly public suspend Object? credential(io.github.ygaray.voiceactionengine.core.ProviderId provider, kotlin.coroutines.Continuation<? super io.github.ygaray.voiceactionengine.core.provider.CredentialLookup>);
  }

}

