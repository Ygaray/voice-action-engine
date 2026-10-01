# API Coverage — OpenAI and OpenRouter Chat Completions

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
> Scope: the v1.0 contract (§6.2 step 3b) for an STT-agnostic voice-command engine whose input is a text transcript and whose action surface is the app's own tools. One Chat Completions transport serves both vendors (D-01). Plans: 05-01..05-12.

| capability | decision | reason |
|---|---|---|
| chat completions create, OpenAI (POST /v1/chat/completions, non-streaming) | INTEGRATE | the single round trip every strategy uses on OpenAI (05-06) |
| chat completions create, OpenRouter (POST /api/v1/chat/completions, non-streaming) | INTEGRATE | same transport, second vendor config (05-02, 05-06) |
| Authorization: Bearer key header (both vendors) | INTEGRATE | key read only from the per-provider Credential, vetted for header safety, sent once per request (05-03, 05-06) |
| model id (app-selected, any id; OpenRouter vendor/model ids) | INTEGRATE | ids arrive through the selection seam; OpenRouter ids are normalized for capability lookup only, never rewritten on the wire (05-02) |
| model capability table (tools, forced tools, caching) per vendor | INTEGRATE | public facts through AiProvider.capabilities and the pipeline's capabilityTable; GPT-6 Astra / 6.1 Sol tools-unsupported on OpenAI (05-02, 05-06) |
| messages: system role | INTEGRATE | non-blank system prompt as the first message (05-04) |
| messages: developer role | OPT-OUT | the system role is accepted on the models in scope (Assumption A11, checked by capture C1); switching roles is a one-line change if a model rejects it |
| messages: user text content | INTEGRATE | transcript, language and date live here only (05-04) |
| messages: assistant turns rebuilt from text and tool calls | INTEGRATE | used when no native replay matches; arguments re-encoded as a compact JSON string (05-04) |
| messages: assistant native replay | INTEGRATE | stored response message replayed with an allow-list (role, content, tool_calls, refusal, reasoning_details) (05-04, 05-05) |
| messages: tool role results (tool_call_id, content) | INTEGRATE | one tool message per result, in order (05-04) |
| messages: image, audio and file content parts | OPT-OUT | input is a voice transcript, text only |
| tools: function tools in the nested {type, function{name, description, parameters, strict}} shape | INTEGRATE | the shape Chat Completions requires (CalTracker's flat shape was rejected live); sorted by name (05-04) |
| tools: custom (free-form) tools | OPT-OUT | the neutral ToolSpec is JSON-schema based; a custom-type call in an answer is skipped, not crashed on (05-05) |
| function strict mode (strict: true) | INTEGRATE | engine-decided: only schemas without optional properties, closed objects and inside the strict subset, OpenAI-routed models only; app can only opt out (05-01, 05-04) |
| strict-mode keyword strip on the wire copy | INTEGRATE | data-driven drop list and format allow list, applied only to the strict copy (05-01, 05-04) |
| tool_choice auto | INTEGRATE | default choice (05-04) |
| tool_choice named function (forced) | INTEGRATE | per-model capability, with auto plus an instruction when the model rejects forcing (05-02, 05-04) |
| tool_choice "required" (any tool) | OPT-OUT | the neutral ToolChoice has only Auto and Required(name); no contract need |
| tool_choice "none" | OPT-OUT | an app that wants no tool call sends no tools |
| tool_choice allowed_tools | OPT-OUT | the app already chooses which tools to send per request |
| parallel_tool_calls false (OpenAI) | INTEGRATE | sent on forced or strict calls, so single-shot gets zero or one call (05-04; SHOT-02 consumes it in Phase 7) |
| parallel_tool_calls on OpenRouter | OPT-OUT | require_parameters hard-filters endpoints on every parameter and the model pages checked do not list it; capture R5 tests it and any flip is a returned gap (05-12) |
| reasoning_effort (top-level) | INTEGRATE | none with tools on gpt-5.4+ and GPT-6 Sol/Luna, low for Astra/6.1 Sol via OpenRouter, never sent to models that reject it (05-02, 05-04) |
| OpenRouter reasoning {effort} object | OPT-OUT | OpenRouter documents the top-level reasoning_effort as the equivalent shorthand; one code path for both vendors |
| max_completion_tokens | INTEGRATE | reasoning models and current OpenAI ids (05-02, 05-04) |
| max_tokens | INTEGRATE | legacy gpt-4* / gpt-3.5* and OpenRouter non-OpenAI routes, floor 16 on OpenRouter (05-02, 05-04) |
| response: choices[0].message content, tool_calls, refusal | INTEGRATE | decoded with the D-10 precedence; tool turns decided by tool_calls presence (05-05) |
| response: tool call arguments JSON string | INTEGRATE | "" is {}, a JSON object is used, anything else is MalformedToolArgs (05-05) |
| response: finish_reason (stop, tool_calls, length, content_filter, error, unknown) | INTEGRATE | mapped to neutral stop reasons or typed failures; disagreement reported through the attempt observer (05-05, 05-06) |
| response: OpenRouter native_finish_reason | INTEGRATE | typed hint on finish_reason error (05-05) |
| response: OpenRouter reasoning_details | INTEGRATE | kept inside the native replay and sent back as OpenRouter asks (05-04, 05-05) |
| response: reasoning text, annotations, audio, logprobs | OPT-OUT | not part of the neutral transcript; never parsed or surfaced (reasoning text is a canary-tested secret carrier) |
| usage: prompt, completion, cached and cache-write tokens | INTEGRATE | normalized to {inputUncached, cacheRead, cacheWrite, output}; parity with Anthropic proven (05-05, 05-09) |
| usage: completion_tokens_details (reasoning tokens), OpenRouter cost fields | OPT-OUT | reasoning tokens are already inside completion_tokens; cost is not part of the engine's usage model |
| request id: OpenAI x-request-id header | INTEGRATE | validated before it reaches FailureDetails or ModelResponse (05-05, 05-06) |
| request id: OpenRouter body gen- id | INTEGRATE | validated the same way (05-03, 05-05) |
| error envelope on non-2xx (error.type, error.code) and status mapping | INTEGRATE | status-first table with in-memory refinements, body discarded (05-03) |
| error envelope inside HTTP 200 (OpenRouter and OpenAI-shaped) | INTEGRATE | checked on every 2xx before choices; transient codes retried once (05-03, 05-05, 05-06) |
| insufficient_quota 429 and OpenRouter 402 | INTEGRATE | Billing, never retried (05-03) |
| context_length_exceeded | INTEGRATE | ContextWindowExceeded (05-03) |
| Responses-only tool errors (/v1/responses) and OpenRouter No endpoints found | INTEGRATE | reactive ModelUnsupported, no new failure leaf (05-03) |
| OpenRouter moderation 403 (metadata.reasons) | INTEGRATE | Refusal (05-03) |
| retry-after header | INTEGRATE | seconds, capped; one transient retry per call (05-06, 05-08) |
| x-ratelimit-* headers | OPT-OUT | the engine needs only retry-after; no quota display in the contract |
| automatic prompt caching (OpenAI) | INTEGRATE | passive: tools sorted and system prompt stable for a byte-identical prefix; cached tokens read from usage; caching AUTOMATIC with a 1 024-token minimum feeds the cache diagnostic (05-02, 05-04) |
| prompt_cache_key / prompt cache retention | OPT-OUT | deferred (REQUIREMENTS later list); automatic caching needs no key |
| OpenRouter cache_control for anthropic/* routes | OPT-OUT | deferred; routed Anthropic models are uncached in v1.0 (caching NONE) |
| OpenRouter provider.require_parameters | INTEGRATE | sent only on forced named calls (05-04) |
| OpenRouter provider routing (order, only, ignore, sort, data_collection, quantizations) | OPT-OUT | the app picks the model; routing preferences are not part of the neutral request |
| OpenRouter models fallback array and route | OPT-OUT | fallback is the engine's tier ladder, not the router's |
| OpenRouter session_id | OPT-OUT | deferred (REQUIREMENTS later list) |
| OpenRouter attribution headers (HTTP-Referer, X-Title) | OPT-OUT | the engine sends no app identity (CalTracker decision kept) |
| OpenRouter plugins and transforms (web search, file parser, middle-out) | OPT-OUT | the app's own tools are the action surface |
| OpenRouter :variant model suffixes (:free, :nitro, :online) | INTEGRATE | stripped for capability lookup only (05-02) |
| streaming (stream: true, SSE) | OPT-OUT | voice commands use single complete responses; streaming is outside the v1.0 contract |
| Responses API dialect (/v1/responses) | OPT-OUT | LATER-03 (orchestrator ruling Option A); Responses-only tool models are refused typed before any request |
| response_format / structured outputs | OPT-OUT | structured results come from strict tool calls |
| temperature, top_p, seed, stop, n, presence and frequency penalties, logit_bias, logprobs | OPT-OUT | provider defaults; not part of the neutral ModelRequest |
| verbosity | OPT-OUT | not part of the neutral ModelRequest |
| store and metadata | OPT-OUT | the engine stores nothing provider-side |
| user / safety_identifier | OPT-OUT | the engine sends no user identity (privacy) |
| service_tier | OPT-OUT | default tier only |
| audio output, modalities, prediction, web_search_options | OPT-OUT | not a voice-command capability of this engine (STT/TTS are app concerns) |
| legacy functions / function_call parameters | OPT-OUT | superseded by tools and tool_choice |
| Models list endpoints (OpenAI /v1/models, OpenRouter /models) | OPT-OUT | the model catalog is app-owned and enters through the selection seam |
| OpenRouter generation stats, credits and key endpoints | OPT-OUT | account administration is not a client-library concern |
| Embeddings, moderation, files, batch, assistants, realtime, images and audio APIs | OPT-OUT | outside the voice-command contract |
