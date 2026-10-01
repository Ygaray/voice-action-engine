package io.github.ygaray.voiceactionengine.sample.ui

/** How a line should be coloured: green, amber, red, or plain. */
internal enum class Tone {
    /** The thing worked. */
    GOOD,

    /** Something needs a look: not a pass and not a failure. */
    WARN,

    /** A failure or a refusal. */
    BAD,

    /** Nothing to judge. */
    NEUTRAL,
}
