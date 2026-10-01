package io.github.ygaray.voiceactionengine.core.commit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val DEFAULT_CONFIRM_TIMEOUT_MILLIS = 120_000L

/**
 * Decides whether a proposal needs the user's confirmation.
 */
public fun interface ConfirmationPolicy {
    /**
     * Returns null when [proposal] needs no confirmation (it is admitted at once), or an app-typed subject that the
     * confirmation UI renders. The engine never inspects the subject.
     */
    public suspend fun subjectFor(proposal: CommitProposal): Any?
}

/**
 * Runs after the user confirms and before the changes are applied, so the app can refresh what it is about to write.
 */
public fun interface ConfirmAmendHook {
    /**
     * Returns the changes to apply instead of the proposed ones (for example with a fresh pre-change snapshot and
     * merge authorization in each change's context), or null to apply the proposal unchanged.
     */
    public suspend fun afterConfirm(proposal: CommitProposal, subject: Any?): List<PendingMutation>?
}

/**
 * A confirmation the user has been asked for and not yet answered.
 *
 * @property id identifies this confirmation; pass it to [AwaitingConfirmGate.resolve].
 * @property subject the app's own subject for the confirmation UI.
 * @property proposal the changes waiting for the answer.
 */
public class PendingConfirmation internal constructor(
    public val id: Long,
    public val subject: Any?,
    public val proposal: CommitProposal,
) {
    /** Prints the id and the subject's class name only, so the subject's content can never reach a log. */
    override fun toString(): String = "PendingConfirmation(id=$id, subject=${subject?.let { it::class.simpleName }})"
}

/**
 * A [PreApplyGate] that waits inside [admit] for the user's answer (suspend mode).
 *
 * Only an explicit [resolve] with `confirmed = true` admits. A decline, a timeout, or an error from the policy or the
 * amend hook never admits. A decline or a timeout answers with a hold whose reason is the app's own subject; a
 * policy or hook error propagates, and the engine fails closed on it.
 *
 * Observe [pending] from the UI to show the confirmation, and call [resolve] with its id when the user answers.
 *
 * Hand-off notes for apps:
 * - Dispatch is sequential: the gate holds a lock for the whole confirmation window, so a second proposal waits until
 *   the first is answered or times out.
 * - The engine adds no timeout around [admit] beyond this gate's own window. An app that sets
 *   `TierPolicy.commandTimeoutMillis` shorter than the window will have that deadline end the wait as
 *   `Failed(Timeout)` with nothing recorded.
 * - Cancelling the caller while a confirmation is pending clears it and is never turned into a decision.
 *
 * @param policy decides which proposals need confirmation, and the subject to show.
 * @param timeoutMillis how long to wait for an answer before holding; positive.
 * @param amendHook runs after a confirm and may replace the changes to apply, or null for none.
 * @param heldOutcomeToken the app's own outcome string for changes held by a decline or timeout, or null.
 */
public class AwaitingConfirmGate(
    private val policy: ConfirmationPolicy,
    private val timeoutMillis: Long,
    private val amendHook: ConfirmAmendHook?,
    private val heldOutcomeToken: String?,
) : PreApplyGate {
    /** A gate with the default 120 second window, no amend hook and no outcome token. */
    public constructor(policy: ConfirmationPolicy) : this(policy, DEFAULT_CONFIRM_TIMEOUT_MILLIS, null, null)

    /** A gate with a [timeoutMillis] window, no amend hook and no outcome token. */
    public constructor(policy: ConfirmationPolicy, timeoutMillis: Long) : this(policy, timeoutMillis, null, null)

    init {
        require(timeoutMillis > 0) { "timeoutMillis must be positive but was $timeoutMillis" }
    }

    private val awaitMutex = Mutex()
    private val nextId = AtomicLong()
    private val active = AtomicReference<Pair<Long, CompletableDeferred<Boolean>>?>(null)
    private val pendingState = MutableStateFlow<PendingConfirmation?>(null)

    /** The confirmation waiting for an answer right now, or null. For the app's UI; the engine never reads it. */
    public val pending: StateFlow<PendingConfirmation?> = pendingState.asStateFlow()

    /**
     * Delivers the user's answer to the confirmation with [confirmationId].
     *
     * Returns true only when this call answered the active confirmation. A stale id, an unknown id, or a second answer
     * to the same confirmation returns false and changes nothing.
     */
    public fun resolve(confirmationId: Long, confirmed: Boolean): Boolean {
        val current = active.get() ?: return false
        return current.first == confirmationId && current.second.complete(confirmed)
    }

    override suspend fun admit(proposal: CommitProposal): GateDecision {
        val subject = policy.subjectFor(proposal) ?: return GateDecision.Admit()
        return awaitMutex.withLock {
            if (awaitAnswer(subject, proposal)) {
                GateDecision.Admit(amendHook?.afterConfirm(proposal, subject))
            } else {
                GateDecision.Hold(subject, heldOutcomeToken)
            }
        }
    }

    /**
     * Publishes the confirmation, waits for the answer within the window, and always clears the state afterwards.
     *
     * The deferred is the single source of truth: once the wait ends without an answer (a timeout, or the caller being
     * cancelled) the deferred is settled as declined, so a racing [resolve] either won before that and is honoured, or
     * lost and returns false. [resolve] never reports an answer that is then discarded.
     */
    private suspend fun awaitAnswer(subject: Any, proposal: CommitProposal): Boolean {
        val id = nextId.incrementAndGet()
        val deferred = CompletableDeferred<Boolean>()
        active.set(id to deferred)
        pendingState.value = PendingConfirmation(id, subject, proposal)
        return try {
            val answer = withTimeoutOrNull(timeoutMillis) { deferred.await() }
            if (answer == null) deferred.complete(false)
            deferred.await()
        } finally {
            deferred.complete(false)
            active.set(null)
            pendingState.value = null
        }
    }

    /** Prints the window and the pending id only. */
    override fun toString(): String =
        "AwaitingConfirmGate(timeoutMillis=$timeoutMillis, pending=${pendingState.value?.id ?: "none"})"
}
