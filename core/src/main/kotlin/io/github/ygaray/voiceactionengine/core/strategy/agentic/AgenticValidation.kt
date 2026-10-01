package io.github.ygaray.voiceactionengine.core.strategy.agentic

import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart

/**
 * True when a tool turn's [calls] can be answered: there is at least one call and no id repeats within the turn. This
 * covers everything the results message needs from its calls, so a turn that passes can never make building that
 * message fail.
 *
 * Only the one turn is looked at. An id that an earlier turn used is fine here, because each results message answers
 * only the calls of its own turn, and some routed upstreams hand out the same id on every turn.
 */
internal fun isAnswerable(calls: List<AssistantPart.ToolCall>): Boolean =
    calls.isNotEmpty() && calls.map { it.id }.toSet().size == calls.size
