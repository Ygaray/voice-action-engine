# API Coverage: multi-turn tool conversations (Anthropic Messages, Chat Completions)

> Full coverage by default. Every opt-out is an explicit decision with a reason.
> Scope: §6.2 step 6a (XCR-01..03). This phase adds no endpoint, service, SDK or credential. It hardens and proves how the
> Phase 4 and 5 transports carry a multi-turn tool conversation: replay, tool results, empty arguments, caching across
> iterations. Live multi-turn capture is opt-in and outside `check` (08-09). Plans: 08-01..08-09.

## Anthropic Messages (POST /v1/messages), multi-turn

| capability | decision | reason |
|---|---|---|
| assistant turn replay: the raw `content` array sent back verbatim | INTEGRATE | the decoder stores it; the encoder replays it only for the stamped provider and model (08-01); byte-identical across the whole history (08-05) |
| replay to the same provider and model only (requested id, frozen per command) | INTEGRATE | a stamp mismatch fails loudly as `replay_mismatch` before any request (08-01) |
| carry a transcript to another provider or model | OPT-OUT | rejected by design (XCR-02, D-03): `carry` is semantic only; a different provider starts a new transcript |
| `thinking` blocks with `signature`, replayed unmodified | INTEGRATE | kept inside the raw array, never re-shaped (08-05 derived; 08-09 real capture on a default-thinking model) |
| `redacted_thinking` blocks | INTEGRATE | kept inside the raw array; never filtered by type (08-05 derived fixture) |
| thinking interleaved between `tool_use` blocks | INTEGRATE | order preserved by raw replay (08-05 derived fixture) |
| dropping or pruning earlier-turn thinking on the client | OPT-OUT | the API filters per model; removing a middle block breaks prefix binding; the engine never prunes |
| `thinking` request parameter (adaptive/enabled, effort, budget) | OPT-OUT | no neutral `ModelRequest` field; adding one is a public change outside XCR; the capture uses a model that thinks by default |
| `thinking.block_binding.prefix_mismatch_behavior` | OPT-OUT | append-only encoding satisfies prefix binding by construction (08-07) |
| parallel `tool_use` blocks in one turn | INTEGRATE | decoded in order; answered by one batched results message (08-02, 08-05) |
| `tool_use.input` `{}` (zero-argument tool) | INTEGRATE | round-tripped (08-05) |
| `tool_use.input` absent or `null` | INTEGRATE | decoded as `{}`; the raw keeps what arrived (08-03, D-06) |
| `tool_result` blocks for a turn in one user message | INTEGRATE | existing batching, now in the calls' emission order (08-02) |
| `tool_result.is_error: true` | INTEGRATE | sent only on error results (08-02) |
| `tool_result` with empty text | INTEGRATE | `content` omitted; the API documents it as optional (08-02) |
| `tool_result.content` as a block array (text, image, document) | OPT-OUT | the neutral `ToolResult.content` is one string |
| text after the tool results in the same user message | INTEGRATE | only the reshape instruction line, which is single-turn only (pinned in 08-07) |
| `cache_control`: one ephemeral breakpoint on the last system block (last tool when no system) | INTEGRATE | existing; proven on every iteration of a conversation (08-07) |
| moving message-tail breakpoint (`CacheDirective.conversationTail`) | OPT-OUT | ignored in v1.0 (a later version's work); the static prefix still caches |
| `tool_choice` auto in a loop | INTEGRATE | the only append-only choice (08-07) |
| `tool_choice` forced plus reshape in a loop | OPT-OUT | reshape moves the instruction line every iteration, so an earlier turn is rewritten; single-turn only (08-07) |
| `disable_parallel_tool_use` | INTEGRATE | unchanged from Phase 7; never part of the cached prefix |
| server tools (`server_tool_use`, web search, code execution results), citations, search results | OPT-OUT | the engine sends no server tools; such blocks would still replay inside the raw array |
| context management edits (`clear_tool_uses` and similar) | OPT-OUT | they rewrite earlier turns, which breaks append-only and thinking binding |

## Chat Completions (OpenAI, OpenRouter), multi-turn

| capability | decision | reason |
|---|---|---|
| assistant replay: allowlist projection (`role`, `content`, `tool_calls`, `refusal`, `reasoning_details`) in stored key order | INTEGRATE | the projection of the stored response message (08-01 guard, 08-06 proof) |
| response-only fields on echo (`annotations`, `audio`, `reasoning` text, `function_call`, unknown keys) | OPT-OUT | dropped by the allowlist; the documented input schema has none of them; the 08-09 echo probe records whether OpenAI would accept them |
| `tool_calls` entries copied entry-for-entry (OpenRouter `index`, provider extras) | INTEGRATE | needed for extras that strict endpoints require back; OpenRouter `index` acceptance is checked live (08-09 R1); if it is refused, that comes back as a gap |
| repair of an invalid echo (missing `role`; empty-arguments form becomes `"{}"`) | INTEGRATE | repair only: it never touches a valid replay and is deterministic, so the prefix stays stable (08-03) |
| `reasoning_details` echoed whole and in order (OpenRouter) | INTEGRATE | the router requires the sequence unmodified (08-06 derived; 08-09 R2/R3 live) |
| OpenRouter `reasoning` request parameter / effort object | OPT-OUT | not part of the neutral request; reasoning shows up only when the upstream reasons by default |
| one `role:tool` message per call id | INTEGRATE | existing, now in the calls' emission order (08-02) |
| error result as `{"error": <text>}` content | INTEGRATE | built with a JSON builder, never concatenated (08-02, D-05) |
| non-error content (including a held-for-confirmation JSON string) | INTEGRATE | passed through byte-for-byte (08-02, D-05) |
| empty `role:tool` content | INTEGRATE | sent as `""`; no placeholder invented (plan-level decision on research A3); a live 400 would come back as a gap |
| `role:tool` content as a content-part array; `name` on tool messages | OPT-OUT | the neutral result is one string; `name` is not required by the current API |
| parallel `tool_calls` in one turn | INTEGRATE | decoded in order, answered per id (08-06); the existing `parallel_tool_calls` flag rules are unchanged |
| empty-arguments forms (`""`, blank, `"null"`, JSON `null`, absent) | INTEGRATE | decoded as `{}` (08-03, D-06); ids never invented |
| tool-call ids blank, or duplicated across turns | OPT-OUT here | blank ids stay `MalformedResponse` at decode; duplicates within one turn fail the pre-flight; duplicates across turns are left to Phase 9 whole-turn validation (D-06) |
| automatic prompt caching (no directive) | INTEGRATE | passive; byte-stable prefix proven on every iteration (08-07) |
| OpenRouter `cache_control` for `anthropic/*` routes | OPT-OUT | D-10: Chat sends no cache directive, on any route |
| `prompt_cache_key`, OpenRouter `session_id` (sticky upstream) | OPT-OUT | deferred (REQUIREMENTS later list); noted for Phase 9/10 |
| legacy `function` role and `function_call` | OPT-OUT | superseded by tools |

## Engine-side invariants (no wire field, listed for completeness)

| capability | decision | reason |
|---|---|---|
| pre-flight conversation check (stamp, coverage, orphan and duplicate results) | INTEGRATE | typed `FailureReason.Other(code)` with zero requests; codes carry the kind only, never an id (08-01; plan-level decision on research A9) |
| one conformance suite over both mappers (three dialect bindings) | INTEGRATE | 08-05..08-07 (D-09) |
| conversation goldens: derived, then captured, sanitized with one id map per conversation | INTEGRATE | 08-04..08-06 derived; 08-08 recorder; 08-09 capture (D-07, D-08, D-12) |
