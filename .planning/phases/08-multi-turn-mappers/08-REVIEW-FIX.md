---
phase: 08-multi-turn-mappers
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/08-multi-turn-mappers/08-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 8: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/08-multi-turn-mappers/08-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (WR-01..WR-04, IN-01..IN-04)
- Fixed: 8
- Skipped: 0

Verification ran in the main checkout (no worktree; the fixer was the only writer). After the last commit,
`./gradlew check --offline -q --console=plain` exited 0 (all OkHttp matrix legs, detekt zero baseline, scanners).
`:providers:detekt` and `:providers:test` were also run after every finding. No public API changed, so no `api.txt`
change; no golden file was edited; no tag, push or ledger edit.

## Fixed Issues

### WR-01: Chat replay can carry tool calls the neutral turn does not

**Files modified:** `providers/.../chat/ChatDecoder.kt`, `providers/.../chat/ChatReplay.kt` (new),
`providers/.../chat/ChatDecoderTest.kt`
**Commit:** ab933bd
**Status:** fixed: requires human verification (logic change)
**Applied fix:** The decoder now stores a Chat turn with only the tool calls it decoded (a length stop, refusal,
content filter or non-function call no longer leaves undecoded `tool_calls` in the stored turn), so the pre-flight
and the encoder see the same calls. Chose the "store a trimmed replay" option from the review, not the pre-flight
variant. Tests: continuation after a length-stopped, refused and filtered turn passes the pre-flight; a skipped
non-function call is dropped while the kept call stays byte-identical; a fully decoded turn is stored as received.

### WR-02: Object-form tool arguments replayed as an object

**Files modified:** `providers/.../chat/ChatMessageEncoder.kt`, `providers/.../chat/ChatReplay.kt`,
`providers/.../chat/ChatMessageEncoderTest.kt`, `providers/.../conformance/WireDialect.kt`
**Commit:** c7664d2
**Applied fix:** The replay repair now writes an object-valued `arguments` as its compact text in the key order
received (including `{}`); the repair code moved to `ChatReplay.kt` to stay under the detekt function-count limit. The
conformance dialect's independent `repairedReplayWire` applies the same rule. New encoder test covers nested objects,
`{}` and determinism; all goldens still replay.

### WR-03: Anthropic tool_use without an object input replayed unrepaired

**Files modified:** `providers/.../anthropic/AnthropicMessageEncoder.kt`, `providers/.../anthropic/AnthropicEncoderTest.kt`
**Commit:** 94187e6
**Applied fix:** A stamped `tool_use` block whose `input` is absent or not an object gets `"input":{}` on replay;
nothing else in the stored content is touched and the result is deterministic. Test pins the repaired bytes next to a
thinking block and a valid block.

### WR-04: Golden hygiene leaves raw ids in exempt keys

**Files modified:** `providers/.../conformance/ConversationGolden.kt`, `ConversationSanitizer.kt`,
`ConversationGoldenTest.kt`, `ConversationSanitizerTest.kt`
**Commit:** dc8f098
**Applied fix:** `reasoning_details` is now a container, not an exempt value. Exempt are the string values of
`thinking`, `signature`, `data`, `reasoning`, plus `text`, `summary` and `encrypted_content` inside a
`reasoning_details` array; an entry's `id` is scrubbed (and flagged by hygiene). A structured value under an exempt key
is cleaned like any other. The id prefix list gained `resp_`, `rs_`, `fc_`. The two tests the review named were
flipped/extended and new tests cover scrub and hygiene. The committed goldens were verified clean under the stricter
rules and needed no edit.
**Not changed (by choice):** the review's parenthetical about `DROPPED_KEYS` silently dropping `cost`/`user_id`
arguments. The scripted tools have no such arguments, and dropping those keys is the intended pricing/identity rule.
A string argument named like an exempt key stays exempt (key name is all the sanitizer sees); this is documented in the
sanitizer KDoc.

### IN-01: Two separate predicates for "replay usable"

**Files modified:** `providers/.../anthropic/AnthropicMessageEncoder.kt`, `AnthropicTransport.kt`,
`providers/.../chat/ChatReplay.kt`, `ChatMessageEncoder.kt`, `ChatTransport.kt`, `AnthropicEncoderTest.kt`,
`ChatMessageEncoderTest.kt`
**Commit:** 916584e
**Applied fix:** One internal function per dialect (`anthropicReplayContent`, `chatReplayMessage`) returns the typed
replay or null; the transport pre-flight and the encoder backstop both use it. Tests assert that the shapes the
pre-flight accepts are exactly the ones the encoder encodes.

### IN-02: Empty Anthropic assistant turn sent as `content: []`

**Files modified:** `providers/.../anthropic/AnthropicMessageEncoder.kt`, `AnthropicEncoderTest.kt`
**Commit:** 2c36709
**Status:** fixed: requires human verification (behavior choice)
**Applied fix:** Took the "skip empty assistant turns in `encodeMessages`" option: a turn whose content is empty
(no parts, only empty text, or a stamped `[]`) is omitted and the user messages around it go out in sequence. A
thinking-only stamped turn is kept. Chosen over a new pre-flight code because an empty turn holds nothing to lose.

### IN-03: Sanitizer and hygiene details

**Files modified:** `providers/.../chat/ChatGoldenSanitizer.kt`, `conformance/ConversationSanitizer.kt`,
`ConversationCapture.kt`, `ConversationGoldenTest.kt`, `ConversationSanitizerTest.kt`, `ConversationCaptureRunTest.kt`
**Commit:** 561c6c1
**Applied fix:** All four sub-items: `BEARER_IN_TEXT` is case-insensitive; `sanitize` also refuses an `error` nested in
a `choices` entry; `ConversationPlan.requiresThinking` replaces the hard-coded `"A2"`; a new test requires every
`*.json` under `golden/conversations/` to have a manifest row.

### IN-04: Cross-provider or cross-model history is a hard refusal

**Files modified:** `core/.../transcript/Message.kt` (KDoc only), `providers/.../transcript/ConversationCheck.kt` (KDoc only)
**Commit:** 336353d
**Applied fix:** Behavior unchanged, per locked decision D-03. The `AssistantMessage.nativeReplay` KDoc and the
`conversationViolation` KDoc now state that a mismatched stamp is refused with `replay_mismatch` and that a ladder
escalating mid-conversation must pass the history with the replays dropped (turns rebuilt without thinking blocks).
The optional additive helper was not added, to keep the public API unchanged.

## Skipped Issues

None.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
