// Signature format: 4.0
package io.github.ygaray.voiceactionengine.providers.anthropic {

  public final class AnthropicAttempt {
    method public boolean equals(Object? other);
    method @InaccessibleFromKotlin public Integer? getHttpStatus();
    method @InaccessibleFromKotlin public int getNumber();
    method public int hashCode();
    method public String toString();
    property public Integer? httpStatus;
    property public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind kind;
    property public int number;
  }

  @kotlin.jvm.JvmInline public final value class AnthropicAttemptKind {
    method @InaccessibleFromKotlin public String getValue();
    method public String toString();
    property public String value;
    field public static final io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind.Companion Companion;
  }

  public static final class AnthropicAttemptKind.Companion {
    property public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind FORCED_TOOL_RESHAPE;
    property public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind INITIAL;
    property public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind TRANSIENT_RETRY;
  }

  public fun interface AnthropicAttemptObserver {
    method public void onAttempt(io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttempt attempt);
  }

  public final class AnthropicProvider implements io.github.ygaray.voiceactionengine.core.provider.AiProvider {
    method public suspend Object? complete(io.github.ygaray.voiceactionengine.core.provider.ProviderRequest call, kotlin.coroutines.Continuation<? super io.github.ygaray.voiceactionengine.core.provider.ModelResult>);
    method public String toString();
    property public io.github.ygaray.voiceactionengine.core.ProviderId id;
    field public static final io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider.Companion Companion;
  }

  public static final class AnthropicProvider.Builder {
    method @InaccessibleFromKotlin public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver? getAttemptObserver();
    method @InaccessibleFromKotlin public long getCallTimeoutMillis();
    method @InaccessibleFromKotlin public okhttp3.OkHttpClient? getHttpClient();
    method @InaccessibleFromKotlin public long getReadTimeoutMillis();
    method @InaccessibleFromKotlin public void setAttemptObserver(io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver?);
    method @InaccessibleFromKotlin public void setCallTimeoutMillis(long);
    method @InaccessibleFromKotlin public void setHttpClient(okhttp3.OkHttpClient?);
    method @InaccessibleFromKotlin public void setReadTimeoutMillis(long);
    property public io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver? attemptObserver;
    property public long callTimeoutMillis;
    property public okhttp3.OkHttpClient? httpClient;
    property public long readTimeoutMillis;
  }

  public static final class AnthropicProvider.Companion {
    method public operator io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider invoke(kotlin.jvm.functions.Function1<? super io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider.Builder,kotlin.Unit> block);
  }

}

package io.github.ygaray.voiceactionengine.providers.chat {

  public final class ChatCompletionsAttempt {
    method public boolean equals(Object? other);
    method @InaccessibleFromKotlin public String? getFinishReason();
    method @InaccessibleFromKotlin public Integer? getHttpStatus();
    method @InaccessibleFromKotlin public int getNumber();
    method @InaccessibleFromKotlin public int getToolCalls();
    method public int hashCode();
    method public String toString();
    property public String? finishReason;
    property public Integer? httpStatus;
    property public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptKind kind;
    property public int number;
    property public int toolCalls;
  }

  @kotlin.jvm.JvmInline public final value class ChatCompletionsAttemptKind {
    method @InaccessibleFromKotlin public String getValue();
    method public String toString();
    property public String value;
    field public static final io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptKind.Companion Companion;
  }

  public static final class ChatCompletionsAttemptKind.Companion {
    property public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptKind INITIAL;
    property public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptKind TRANSIENT_RETRY;
  }

  public fun interface ChatCompletionsAttemptObserver {
    method public void onAttempt(io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttempt attempt);
  }

  public final class ChatCompletionsProvider implements io.github.ygaray.voiceactionengine.core.provider.AiProvider {
    method public suspend Object? complete(io.github.ygaray.voiceactionengine.core.provider.ProviderRequest call, kotlin.coroutines.Continuation<? super io.github.ygaray.voiceactionengine.core.provider.ModelResult>);
    method public String toString();
    property public io.github.ygaray.voiceactionengine.core.ProviderId id;
    field public static final io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider.Companion Companion;
  }

  public static final class ChatCompletionsProvider.Builder {
    method @InaccessibleFromKotlin public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver? getAttemptObserver();
    method @InaccessibleFromKotlin public long getCallTimeoutMillis();
    method @InaccessibleFromKotlin public okhttp3.OkHttpClient? getHttpClient();
    method @InaccessibleFromKotlin public long getReadTimeoutMillis();
    method @InaccessibleFromKotlin public void setAttemptObserver(io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver?);
    method @InaccessibleFromKotlin public void setCallTimeoutMillis(long);
    method @InaccessibleFromKotlin public void setHttpClient(okhttp3.OkHttpClient?);
    method @InaccessibleFromKotlin public void setReadTimeoutMillis(long);
    property public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver? attemptObserver;
    property public long callTimeoutMillis;
    property public okhttp3.OkHttpClient? httpClient;
    property public long readTimeoutMillis;
  }

  public static final class ChatCompletionsProvider.Companion {
    method public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider openAi(kotlin.jvm.functions.Function1<? super io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider.Builder,kotlin.Unit> block);
    method public io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider openRouter(kotlin.jvm.functions.Function1<? super io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider.Builder,kotlin.Unit> block);
  }

}

