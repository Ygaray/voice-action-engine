package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.undo.Compensator
import io.github.ygaray.voiceactionengine.undo.EntityAdapter
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Wraps an adapter and logs every restore call as `restore:<id>`, so a test can see the order of the writes. */
internal class LoggingAdapter(private val inner: EntityAdapter, private val log: RecordingSink<String>) : EntityAdapter {
    override val entityType: String = inner.entityType

    override suspend fun read(id: String): Any? = inner.read(id)

    override suspend fun fingerprint(id: String): String? = inner.fingerprint(id)

    override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean {
        log.record("restore:$id")
        return inner.restoreIf(id, expectedFingerprint, snapshot)
    }
}

/** Forwards everything to [inner] except the action at [dropPosition], which it swallows (a bridge that missed one). */
internal class DroppingSink(private val inner: CommitSink, private val dropPosition: Int) : CommitSink {
    override suspend fun onAction(event: ActionEvent) {
        if (event.action.position != dropPosition) inner.onAction(event)
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = inner.onRunClosed(runId, termination)
}

/** An effect outside the database: a set of armed alarm ids, with an idempotent disarm. */
internal class AlarmBoard {
    private val armed = ConcurrentHashMap.newKeySet<String>()

    /** Ids whose next disarm throws once. */
    val failOnce: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val ids: Set<String>
        get() = armed.toSet()

    fun arm(id: String) {
        armed.add(id)
    }

    /** The compensator for the `alarm` kind; it logs `disarm:<id>` for every call, including a failing one. */
    fun compensator(log: RecordingSink<String>): Compensator = Compensator { payload ->
        log.record("disarm:$payload")
        check(!failOnce.remove(payload)) { "disarm failed" }
        armed.remove(payload)
    }
}

/** One store, one journal behind the bridge, and a pipeline builder, wired the way an app would wire them. */
internal class UndoRig(val store: ItemStore = ItemStore().also { it.seed("a", "v0") }) {
    val log = RecordingSink<String>()
    val board = AlarmBoard()
    val journal: UndoJournal = UndoJournal {
        adapter(LoggingAdapter(ItemAdapter(this@UndoRig.store), log))
        compensator("alarm", board.compensator(log))
    }
    val bridge = UndoCommitSink(journal)
    val recording = RecordingCommitSink()

    /** The store as it was seeded. */
    val seeded: Map<String, Item> = store.snapshot()

    private val ids = AtomicInteger()

    fun create(title: String, parentId: String? = null): PendingMutation =
        CreateItem(store, journal.newTicket(), title, parentId)

    fun rename(id: String, title: String): PendingMutation = RenameItem(store, journal.newTicket(), id, title)

    /** An action that arms an alarm outside the database, with a compensator declared on its ticket. */
    fun alarm(id: String): FakeMutation {
        val ticket = journal.newTicket()
        return FakeMutation("arm_alarm", {
            board.arm(id)
            ticket.compensate("alarm", id)
            StepResult("armed")
        }, context = ticket)
    }

    /** One tier execution that submits every mutation in order and completes. */
    fun submitting(vararg mutations: PendingMutation): StrategyStep = { _, session ->
        mutations.forEach { session.submit(ToolStep.Mutation(it)) }
        StrategyOutcome.Completed("done")
    }

    /**
     * A pipeline with one tier that plays [steps] (one per execution), run ids `run-1`, `run-2` and so on unless
     * [runIds] says otherwise, and [sink] (the journal bridge beside the recording sink unless given).
     */
    fun pipeline(
        gate: ScriptedGate,
        vararg steps: StrategyStep,
        sink: CommitSink = compositeSink(bridge, recording),
        runIds: () -> String = { "run-${ids.incrementAndGet()}" },
    ): CommandPipeline = commandPipeline {
        tier(ScriptedStrategy(StrategyId("tier"), *steps))
        this.gate = gate
        commitSink = sink
        this.runIds = runIds
    }
}
