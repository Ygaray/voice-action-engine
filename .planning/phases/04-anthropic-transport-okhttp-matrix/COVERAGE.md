# API Coverage — Anthropic Messages API

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
> Scope: the v1.0 contract (§6.2 step 3a-ii) for an STT-agnostic voice-command engine whose input is a text transcript and whose action surface is the app's own tools. Plans: 04-01..04-08.

| capability | decision | reason |
|---|---|---|
| messages create (POST /v1/messages, non-streaming) | INTEGRATE | the single round trip every strategy uses (04-03) |
| x-api-key authentication header | INTEGRATE | key read only from the per-provider Credential, sent once per request (04-03) |
| anthropic-version header 2023-06-01 | INTEGRATE | PROV-04 (04-03) |
| model id (app-selected, any id) | INTEGRATE | ids arrive through the selection seam; verified table in :providers (04-01) |
| max_tokens | INTEGRATE | from ModelRequest.maxTokens (04-03) |
| system prompt as text blocks | INTEGRATE | one text block, part of the cached prefix (04-03) |
| prompt caching: cache_control ephemeral on the static prefix | INTEGRATE | exactly one breakpoint on the last system block, A10 parity (04-03) |
| prompt caching: moving conversation-tail breakpoint | OPT-OUT | v1.0 caches the static prefix only; the tail breakpoint is a v1.x item (REQUIREMENTS later list) |
| prompt caching: 1-hour TTL | OPT-OUT | the default 5-minute TTL fits voice-command cadence; no contract requirement |
| user messages (text content) | INTEGRATE | transcript, language and date live here only (04-03) |
| assistant messages rebuilt from text and tool_use parts | INTEGRATE | used when no native replay matches (04-03) |
| assistant native replay sent verbatim | INTEGRATE | raw content array kept by reference for same provider and model (04-03) |
| custom client tools (name, description, input_schema) | INTEGRATE | sorted by name, schema bytes untouched (04-03) |
| tool_choice auto | INTEGRATE | default choice (04-03) |
| tool_choice tool (forced named tool) | INTEGRATE | per-model capability with reshape to auto plus instruction when unsupported (04-03, 04-06) |
| tool_choice any | OPT-OUT | the neutral ToolChoice has only Auto and Required(name); no contract need |
| tool_choice none | OPT-OUT | an app that wants no tool call sends no tools |
| disable_parallel_tool_use | OPT-OUT | parallel calls arrive as one batch the engine already handles; no contract need |
| strict tool use (strict: true) | INTEGRATE | engine decides only when the schema has no optional properties; explicit app value honored (04-06) |
| tool_use response blocks to neutral tool calls | INTEGRATE | input passed through untouched, non-object input is MalformedToolArgs (04-03) |
| tool_result blocks with is_error | INTEGRATE | one user message of tool_result blocks, is_error only when true (04-03) |
| usage: input, output, cache read, cache creation tokens | INTEGRATE | mapped to Usage{inputUncached, cacheRead, cacheWrite, output} (04-03) |
| stop_reason mapping (end_turn, tool_use, max_tokens, refusal, pause_turn, model_context_window_exceeded, stop_sequence) | INTEGRATE | neutral StopReason; unknown values map to OTHER (04-03) |
| request-id response header and body request_id | INTEGRATE | validated before it reaches FailureDetails or ModelResponse (04-03, 04-04) |
| error envelope (error.type) and HTTP status mapping | INTEGRATE | status-first table to existing FailureReason leaves, body discarded (04-04) |
| spend-limit signals (tier spend cap 429, user spend limit 400) | INTEGRATE | mapped to Billing and never retried (04-04, 04-05) |
| retry-after header | INTEGRATE | seconds, capped; one transient retry per call (04-05) |
| anthropic-ratelimit-* quota headers | OPT-OUT | the engine needs only retry-after; no quota display in the contract |
| streaming (stream: true, SSE events) | OPT-OUT | voice commands use single complete responses; streaming is outside the v1.0 contract |
| Message Batches API | OPT-OUT | interactive commands need a synchronous answer |
| Files API | OPT-OUT | voice commands carry no files |
| Models API (list and get) | OPT-OUT | the model catalog is app-owned and enters through the selection seam |
| token counting endpoint | OPT-OUT | the cache diagnostic estimates prefix size locally; no contract need |
| extended thinking request parameter | OPT-OUT | strategies never enable manual extended thinking (PROV-07); the encoder sends no thinking field |
| thinking blocks returned by the model | INTEGRATE | preserved verbatim inside NativeReplay, never parsed into parts (04-03) |
| image content blocks (vision) | OPT-OUT | input is a voice transcript, text only |
| PDF and document content blocks | OPT-OUT | input is a voice transcript, text only |
| citations | OPT-OUT | no documents are sent |
| search result content blocks | OPT-OUT | no retrieval inputs in the contract |
| server tools (web search, web fetch, code execution) | OPT-OUT | the app's own tools are the action surface; server tools are not in the contract |
| MCP connector (mcp_servers) | OPT-OUT | apps wire their own MCP clients; not an engine concern |
| computer use, text editor and bash tools | OPT-OUT | not a voice-command capability |
| structured outputs (JSON schema output format) | OPT-OUT | structured results come from strict tool calls |
| temperature, top_p, top_k | OPT-OUT | provider defaults; not part of the neutral ModelRequest |
| stop_sequences | OPT-OUT | not part of the neutral ModelRequest |
| metadata.user_id | OPT-OUT | the engine sends no user identity (privacy) |
| service_tier | OPT-OUT | default tier only |
| context management and memory tool | OPT-OUT | conversation length is bounded by tier policy, not provider-side management |
| Agent Skills and containers | OPT-OUT | not a voice-command capability |
| beta headers (anthropic-beta) | OPT-OUT | v1.0 uses generally available features only |
| Admin API (keys, workspaces, usage reports) | OPT-OUT | organization administration is not a client-library concern |
