package io.github.ygaray.voiceactionengine.providers.conformance

import io.github.ygaray.voiceactionengine.providers.chat.ChatVendor

/** The conformance suite bound to OpenAI Chat Completions; no assertion differs from the Anthropic binding's. */
internal class OpenAiMultiTurnConformanceTest : MultiTurnConformanceSuite() {

    override val dialect: WireDialect = ChatWire(ChatVendor.OPENAI, "openai")
}
