package io.github.ygaray.voiceactionengine.core.testing

/**
 * Ordered scripted replies for hand-written fakes. Each [next] call hands out the following value; once the script
 * runs dry the call fails loudly, because a fake that silently defaults would hide a pipeline taking an unplanned step.
 */
public class ScriptedResponses<T>(values: List<T>) {
    private val script: List<T> = values.toList()
    private var index: Int = 0
    private val lock = Any()

    /** Replies not yet handed out. */
    public val remaining: Int
        get() = synchronized(lock) { script.size - index }

    /** Returns the next scripted reply, or fails with "script exhausted" when none is left. */
    public fun next(): T = synchronized(lock) {
        check(index < script.size) { "script exhausted: all ${script.size} scripted replies were already used" }
        script[index++]
    }
}
