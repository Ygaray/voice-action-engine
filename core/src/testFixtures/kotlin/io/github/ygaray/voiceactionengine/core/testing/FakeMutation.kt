package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pending change a test controls. It counts how often it was applied and logs `apply:<toolName>` when apply starts.
 *
 * @param toolName the tool's name.
 * @param behavior what apply does; it may suspend or throw.
 * @param targetIds the ids reported on the action.
 * @param context the opaque object reported on the action.
 * @param log an optional shared log.
 */
public class FakeMutation(
    override val toolName: String,
    private val behavior: suspend () -> StepResult,
    override val targetIds: Map<String, String> = emptyMap(),
    override val context: Any? = null,
    private val log: RecordingSink<String>? = null,
) : PendingMutation {
    /** A change whose apply returns [result]. */
    public constructor(
        toolName: String,
        result: StepResult,
        log: RecordingSink<String>? = null,
    ) : this(toolName, { result }, log = log)

    private val applies = AtomicInteger()

    /** How many times apply was called. */
    public val applyCount: Int
        get() = applies.get()

    override suspend fun apply(): StepResult {
        applies.incrementAndGet()
        log?.record("apply:$toolName")
        return behavior()
    }
}
