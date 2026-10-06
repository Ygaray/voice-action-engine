package io.github.ygaray.voiceactionengine.spike.envelope

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope

/**
 * One measurement envelope: the system text and the tool set a trial offers the model. It is a snapshot (D-05), so the
 * small envelope and the SB-sized envelope stay separate values that are never merged or substituted for each other.
 * [toString] shows the envelope and the tool count only, never the system text or a tool name.
 */
internal class EnvelopeSnapshot(val env: Envelope, val system: String, val tools: List<ToolSpec>) {
    /** The engine's tooling snapshot, with [singleShotTool] as the tool a forced single-shot tier calls (or null). */
    fun toTooling(singleShotTool: String?): ToolingSnapshot = ToolingSnapshot(system, tools, singleShotTool)

    override fun toString(): String = "EnvelopeSnapshot(env=${env.wire}, tools=${tools.size})"
}
