# Phase 5: OpenAI & OpenRouter Transports - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can point the same commands at OpenAI or OpenRouter through one Chat Completions transport. It speaks each provider's real wire shape and maps each provider's quirks to the engine's typed outcomes.

</domain>

<decisions>
## Implementation Decisions

### vendor
- **D-01 [vendor]:** Two public factories over an internal config (base URL, ProviderId, require_parameters on forced calls, request-id source: OpenAI x-request-id header vs OpenRouter body gen- id); Bearer auth for both _(source: ai-auto)_

### or-normalize
- **D-02 [or-normalize]:** Normalize for lookup (CT's OpenRouterModelCatalog rule) so openai/gpt-5.4-mini inherits reasoning flags and anthropic/claude-sonnet-5.5 inherits forced-unsupported; caching mode still keyed by ProviderId (OpenRouter anthropic/* uncached in v1.0) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_

### reasoning-flags
- **D-03 [reasoning-flags]:** Capability flags (requiresReasoningNoneWithTools, maxTokensParamName) resolved by family rules for unknown ids, never sent to models that reject them (e.g. gpt-4o-mini) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_

### model-unsupported
- **D-04 [model-unsupported]:** Add a ModelUnsupported leaf to the open FailureReason taxonomy (Phase 2); scope the block to the OpenAI provider unless research shows OpenRouter also can't route these tools _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### optional-detect
- **D-05 [optional-detect]:** Recursive detection at every depth; required+nullable stays strict-eligible (keeps CT's log_food strict, sends SB's edit tools non-strict — edit_list_card's nested item_id/completed_at would otherwise be wiped) _(source: ai-auto)_

### strict-authority
- **D-06 [strict-authority]:** Engine computes effective strict; ToolSpec.strict may only opt out; any object missing additionalProperties:false → non-strict (no silent rewrite) _(source: ai-auto)_

### strip
- **D-07 [strip]:** Strip only on strict calls from the wire copy; non-strict calls keep minLength/pattern/format/default as hints; the strip list is data refreshed from current OpenAI docs _(source: ai-auto)_

### shared-detector
- **D-08 [shared-detector]:** One shared pure function in :providers built in Phase 4; decoders and the local validator never fill schema defaults, so an omitted optional stays absent _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 4)_

### tool-turn
- **D-09 [tool-turn]:** Presence of tool_calls decides (OpenAI has returned finish_reason "stop" for forced named functions); don't port CT's gate; add a trace note when finish_reason disagrees _(source: ai-auto)_

### decode-order
- **D-10 [decode-order]:** The ordered precedence above; arguments "" decodes to {}; other decode failures → MalformedToolArguments _(source: ai-auto)_

### http200-errors
- **D-11 [http200-errors]:** Both vendors, every 200: top-level error object → mapped via the same status → FailureReason table; finish_reason "error" → failure with native_finish_reason as a typed hint; never retain error.message or metadata.raw (TEL-04) _(source: ai-auto)_

### goldens
- **D-12 [goldens]:** A key-gated capture task (outside ./gradlew check) with gpt-5.4-mini / openai/gpt-5.4-mini and synthetic log_food-shaped + EDIT-with-optionals tools (never SB's fixture); sanitized (ids, system_fingerprint, all headers stripped) and committed; needs Yahir's OpenAI + OpenRouter keys at Phase 5 time Capture tasks are GATED on Yahir's key approval (pending, relayed by the orchestrator). _(source: ai-auto)_

### golden-derivatives
- **D-13 [golden-derivatives]:** Real envelopes for reproducible cases + documented minimal derivatives for the rest _(source: ai-auto)_

### absent-proof
- **D-14 [absent-proof]:** Per-vendor JVM test on the golden request + a recorded real response; live on-device EDIT in Phase 10 (VER-03) _(source: ai-auto)_

### ext-openai-or
- **D-15 [ext-openai-or]:** Needs external research: finish_reason under forced named tool on gpt-5.4-mini (and routed upstreams); current OpenAI strict keyword subset; OpenRouter reasoning_effort vs reasoning{effort}, max_completion_tokens acceptance, require_parameters hard-filtering; the exact 200-error envelope; GPT-6 Astra/6.1 Sol tools via OpenRouter; OpenRouter cached-token usage fields by default; strict on non-OpenAI upstreams _(source: ai-auto)_



### r1-verdict
- **D-16 [r1-verdict]:** Golden-capture tasks are KEY-GATED until the orchestrator relays Yahir's OK; structure plans so everything else in the phase executes without them. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 5 (source of these decisions)
- `.planning/research/SUMMARY.md` (+ STACK/FEATURES/ARCHITECTURE/PITFALLS.md)
- `.planning/cross-repo/HANDOFF.md` — cross-repo rules, orchestrator, devices

</canonical_refs>

<code_context>
## Existing Code Insights

Greenfield repo; port sources are read-only in SecondBrain (`app/src/main/java/com/example/secondbrain/core/agent/`) and CalTracker (`app/src/main/java/com/caltracker/app/ai/`, `mcp/`, `ui/voice/`). File:line evidence per decision is in the decision map's analyzer sources; concrete reuse is surfaced at plan time.

</code_context>

<specifics>
## Specific Ideas

None beyond the decisions above.

</specifics>

<deferred>
## Deferred Ideas

See REQUIREMENTS.md v2 / LATER items.

</deferred>

## Runtime Decisions

- **TEL-01 parity test owner (milestone master, 2026-09-30):** Phase 2's verifier found that no Phase 4/5 requirement line owns TEL-01's cross-provider parity test. Phase 5 owns it, because it's the first phase where both Anthropic (`input_tokens` excludes cached) and OpenAI/OpenRouter (`prompt_tokens` includes cached) normalization exist. Ship a JVM test proving that the same work normalizes to identical `{inputUncached, cacheRead, cacheWrite, output}` and counts identically against the CORE-04 token ceiling (SB sum semantics) on every provider.

- **or-normalize (refreshed vs Phase 3, ai-auto):** CONFIRMED vs Phase 3. Lookup goes through the existing public `ModelCapabilityTable.lookup(provider, model)` and the internal ModelRouter (core/provider). Phase 5 adds OpenRouter id normalization (CT OpenRouterModelCatalog rule: strip the vendor prefix) as a lookup-key step, so `openai/gpt-5.4-mini` inherits the OpenAI flags and `anthropic/claude-sonnet-5.5` inherits forced-tool-choice-unsupported (the existing `supportsForcedToolChoice=false`). `CachingMode` stays keyed by ProviderId, so OpenRouter anthropic/* is uncached in v1.0.
- **reasoning-flags (refreshed vs Phase 3/4, ai-auto):** REFINED vs Phase 3/4. ModelCapabilities (public, builder, fields additive) has no wire-param flags today, and Phase 4 kept Anthropic wire specifics in `:providers` (`anthropic/AnthropicModels.kt`). Put `requiresReasoningNoneWithTools` and `maxTokensParamName` in an internal OpenAI model-rules table inside `:providers`, mirroring AnthropicModels.kt and resolved by family rules for unknown ids. Do NOT add them to public `:core` ModelCapabilities: they are wire-format details, and every public field is frozen at the v1.0.0 API dump. Never send them to models that reject them (e.g. gpt-4o-mini).
- **model-unsupported (refreshed vs Phase 2, ai-auto):** ALREADY SATISFIED by Phase 2: `FailureReason.ModelUnsupported` already exists (core/failure/FailureReason.kt), so add no new leaf. Phase 5 only maps to it. Scope the block to the OpenAI provider unless research shows OpenRouter also cannot route these tools.
- **shared-detector (refreshed vs Phase 4, ai-auto):** ALREADY SATISFIED by Phase 4: the shared pure function is `internal fun hasOptionalProperties(schema: JsonObject)` in providers/schema/OptionalProperties.kt. The OpenAI/OpenRouter strict path reuses it and makes no copy. Decoders and the local validator never fill schema defaults, so an omitted optional stays absent.
- **PROV-12 (from Phase 4):** left Pending by Phase 4 and mapped to Phase 5, so cover it here.
- **Phase 10 Gate-2 carry (from Phase 4):** the live smoke must confirm that a low-credit HTTP 400 maps to Billing and that the accepted API-key character set holds. Apply the same checks to OpenAI/OpenRouter where applicable.
