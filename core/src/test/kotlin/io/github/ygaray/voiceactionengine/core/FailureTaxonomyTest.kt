package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureTaxonomyTest {

    private val allFailures: List<FailureReason> = listOf(
        FailureReason.Auth(), FailureReason.Billing(), FailureReason.RateLimited(), FailureReason.Overloaded(),
        FailureReason.Timeout(), FailureReason.Network(), FailureReason.MalformedResponse(),
        FailureReason.MalformedToolArgs(), FailureReason.Refusal(), FailureReason.MaxTokens(),
        FailureReason.NoToolCall(), FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), FailureReason.ToolFailure(),
        FailureReason.NotConfigured(ProviderId.OPENAI), FailureReason.ProviderUnavailable(ProviderId.ANTHROPIC, "x"),
        FailureReason.ModelUnsupported(), FailureReason.ModelNotFound(), FailureReason.PauseTurn(),
        FailureReason.ContextWindowExceeded(), FailureReason.UnknownStop(), FailureReason.HttpError(),
        FailureReason.NoEligibleTier(), FailureReason.PolicyUnavailable(), FailureReason.Unexpected("IllegalState"),
        FailureReason.Other("custom"),
    )

    private val allEscalations: List<EscalationReason> = listOf(
        EscalationReason.NoToolCall(), EscalationReason.ModelDeclined(), EscalationReason.MalformedExtraction(),
        EscalationReason.ResolverAmbiguous(), EscalationReason.ProviderUnavailable(ProviderId.ON_DEVICE),
        EscalationReason.Other("custom"),
    )

    @Test
    fun everyFailureLeafHasAUniqueCode() {
        assertEquals(TWENTY_FIVE, allFailures.size)
        assertEquals(allFailures.size, allFailures.map { it.code }.toSet().size)
    }

    @Test
    fun theRequiredCodesArePresent() {
        val required = setOf(
            "auth", "billing", "rate_limited", "overloaded", "timeout", "network", "malformed_response",
            "malformed_tool_args", "refusal", "max_tokens", "no_tool_call", "budget_exceeded", "tool_failure",
            "not_configured", "provider_unavailable", "model_unsupported", "model_not_found", "pause_turn",
            "context_window_exceeded", "unknown_stop", "http_error", "no_eligible_tier", "policy_unavailable",
            "unexpected",
        )
        assertTrue(allFailures.map { it.code }.containsAll(required))
    }

    @Test
    fun timeoutAndNetworkAreDistinct() {
        assertEquals("timeout", FailureReason.Timeout().code)
        assertEquals("network", FailureReason.Network().code)
        assertNotEquals(FailureReason.Timeout() as FailureReason, FailureReason.Network())
    }

    @Test
    fun leavesCompareByClassAndFields() {
        assertEquals(FailureReason.Timeout(), FailureReason.Timeout())
        assertEquals(FailureReason.Other("x"), FailureReason.Other("x"))
        assertNotEquals(FailureReason.Other("x"), FailureReason.Other("y"))
        assertNotEquals(
            FailureReason.BudgetExceeded(BudgetBound.ITERATIONS),
            FailureReason.BudgetExceeded(BudgetBound.TOKENS),
        )
        assertEquals(
            FailureReason.ProviderUnavailable(ProviderId.OPENAI, "c").hashCode(),
            FailureReason.ProviderUnavailable(ProviderId.OPENAI, "c").hashCode(),
        )
        assertNotEquals(FailureReason.NotConfigured(null), FailureReason.NotConfigured(ProviderId.OPENAI))
    }

    @Test
    fun blankOtherIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { FailureReason.Other("") }
        assertThrows(IllegalArgumentException::class.java) { FailureReason.Other("  ") }
        assertThrows(IllegalArgumentException::class.java) { EscalationReason.Other("") }
    }

    @Test
    fun unexpectedKeepsItsErrorClass() {
        assertEquals("NoSuchMethodError", FailureReason.Unexpected("NoSuchMethodError").errorClass)
    }

    @Test
    fun consumersMustKeepAnElseBranch() {
        val reason: FailureReason = FailureReason.Other("future")
        val label = when (reason) {
            is FailureReason.Timeout -> "timeout"
            is FailureReason.Network -> "network"
            else -> "other:${reason.code}"
        }
        assertEquals("other:future", label)
    }

    @Test
    fun everyEscalationLeafHasAUniqueCode() {
        assertEquals(allEscalations.size, allEscalations.map { it.code }.toSet().size)
        assertEquals("model_declined", EscalationReason.ModelDeclined().code)
        assertEquals("malformed_extraction", EscalationReason.MalformedExtraction().code)
        assertEquals("resolver_ambiguous", EscalationReason.ResolverAmbiguous().code)
        assertEquals("no_tool_call", EscalationReason.NoToolCall().code)
        assertEquals(
            EscalationReason.ProviderUnavailable(ProviderId.OPENAI),
            EscalationReason.ProviderUnavailable(ProviderId.OPENAI),
        )
        assertNotEquals(EscalationReason.Other("a"), EscalationReason.Other("b"))
    }

    @Test
    fun budgetBoundsHaveStableValues() {
        assertEquals("iterations", BudgetBound.ITERATIONS.toString())
        assertEquals("tokens", BudgetBound.TOKENS.value)
    }

    @Test
    fun noLeafToStringContainsACanarySecret() {
        val canary = "sk-CANARY-SECRET-123"
        Credential(ProviderId.ANTHROPIC, canary)
        val rendered = (allFailures + allEscalations + FailureDetails(HTTP_OK, "t", "req")).map { it.toString() }
        assertTrue(rendered.none { it.contains(canary) })
        assertFalse(rendered.any { it.isBlank() })
    }

    private companion object {
        const val TWENTY_FIVE = 25
        const val HTTP_OK = 200
    }
}
