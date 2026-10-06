---
phase: "14"
slug: localgrammar-bilingual-grammarpack
status: verified
threats_open: 0
asvs_level: 1
audited_head: 6dec07a4fdce7c9ebb605c756fced686edd53c72
created: "2026-10-06"
---

# Phase 14 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail. Register authored at plan time (PLAN threat_model blocks of 14-01..14-10). Audit depth: ASVS L1, block_on high. Verified by gsd-security-auditor against the post-review-fix HEAD.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Transcript -> GrammarPack | Untrusted spoken text enters the matcher | Transcripts (never logged or printed) |
| Grammar tier -> write path | A match becomes a tool call through the shared gate, commit and sink path | Typed slot values |
| App hook -> library | App-supplied `normalize` and resolver code runs inside the tier | Raw slot text, language |
| Host -> TESTER over adb | Device steps can hit the wrong phone or leave state behind | adb commands, synthetic audio |
| Recognizer output -> repository | Device text enters committed fixtures | Filtered synthetic-tts rows |
| :sample build -> consumers | :sample is never published; androidTest only | none |

---

## Threat Register

Status is closed for every row. Evidence: file and test references are in the auditor verdict summarized in the Audit Trail; one-line mitigation per row below.

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-14-01 | Tampering | GrammarPack.matchDetailed | high | mitigate | Anchored whole-transcript match, exact word keys, label table, cross-pack agreement | closed |
| T-14-02 | Tampering | D-01 submit move | medium | mitigate | Pure move in cc5f4c9, agentic tier untouched | closed |
| T-14-03 | Info disclosure | toString / messages | medium | mitigate | Ids and counts only; GrammarRedactionTest | closed |
| T-14-04 | Elevation of privilege | LocalGrammarStrategy writes | high | mitigate | Only `submitSteps(session, steps, null)` through the gate | closed |
| T-14-05 | DoS | resolver throw | low | accept | TierWalk records STRATEGY_ERROR | closed (accepted) |
| T-14-SC (14-01) | Tampering | dependencies | low | accept | No new dependency | closed (accepted) |
| T-14-06 | Tampering | runner target selection | high | mitigate | USB serial pinned, `adb -s` on every call, foreign ANDROID_SERIAL refused | closed |
| T-14-07 | Elevation of privilege | device steps without consent | high | mitigate | Exact `grant: open` line check before lock and adb | closed |
| T-14-08 | DoS | radio-state change | high | mitigate | No radio command in the runner; guard static scan | closed |
| T-14-09 | Info disclosure | filter to stt-fixtures.tsv | medium | mitigate | Allow-list filter | closed |
| T-14-10 | Tampering | RAE excerpts | low | accept | Paraphrases with source URLs | closed (accepted) |
| T-14-11 | Tampering | lenient number parse | high | mitigate | Strict single-token spans, ambiguous groupings return null | closed |
| T-14-12 | Tampering | EN/ES float drift | medium | mitigate | Exact BigDecimal, one conversion | closed |
| T-14-13 | DoS | pathological token lists | low | mitigate | Window capped at longest number phrase | closed |
| T-14-14 | Info disclosure | parser exceptions | low | mitigate | Parsers never throw | closed |
| T-14-15 | Tampering | extra clause or prefix | high | mitigate | Clause break refusal, anchored match | closed |
| T-14-16 | Tampering | two readings by order | high | mitigate | Second distinct reading aborts to Ambiguous | closed |
| T-14-17 | DoS | expansion or backtracking | medium | mitigate | EXPANSION_LIMIT 256, single-pass parser | closed |
| T-14-18 | Tampering | filler strips a required word | medium | mitigate | Build-time edge-filler refusal (residual R1, fails closed) | closed |
| T-14-19 | Info disclosure | build-time messages | low | mitigate | Authoring data only | closed |
| T-14-20 | Tampering | text slot swallows words | high | mitigate | maxWords 1..64, no adjacent open spans | closed |
| T-14-21 | Tampering | mis-grouped number | high | mitigate | Range check, language-resolved grouping | closed |
| T-14-22 | Tampering | EN/ES value drift | medium | mitigate | Single Long and Double conversion path | closed |
| T-14-23 | Info disclosure | slot values in output | medium | mitigate | argumentCount only | closed |
| T-14-24 | Tampering | cross-language false friend | high | mitigate | Equal tool, terminal flag and arguments or ambiguous | closed |
| T-14-25 | Tampering | overlapping rules | high | mitigate | Build-time overlap validation through the real matcher | closed |
| T-14-26 | DoS | huge transcript | medium | mitigate | Derived input cap before the walk | closed |
| T-14-27 | Tampering | malformed label | medium | mitigate | Only null, en, es; others unsupported | closed |
| T-14-28 | Tampering | normalize best-guess | high | mitigate | Null or blank rejects, whole command refused | closed |
| T-14-29 | DoS | throwing hook | medium | mitigate | Contained as GRAMMAR_NORMALIZE_ERROR | closed |
| T-14-30 | Info disclosure | hook message in trace | high | mitigate | Fault ignored, codes only | closed |
| T-14-31 | Elevation of privilege | terminal intent bypasses gate | medium | accept | Terminal ends with TerminalCall, writes nothing | closed (accepted) |
| T-14-32 | Tampering | grammar under restrictive policy | low | accept | NO_PROVIDER, maxTier still applies | closed (accepted) |
| T-14-33 | Tampering | non-additive API change | high | mitigate | Internal 4-arg ctor, metalava compat, api.txt unchanged | closed |
| T-14-34 | Repudiation | open decisions shipped silently | medium | mitigate | Surface review OI-1..OI-8 | closed |
| T-14-35 | Info disclosure | docs name domain or private path | low | mitigate | Doc coverage checks C21 and C25 | closed |
| T-14-36 | Elevation of privilege | device steps without a window | high | mitigate | Grant pending, open (7e66d93, relayed), consumed (2ee4735) | closed |
| T-14-37 | Tampering | wrong device | high | mitigate | Serial pin, static guard checks | closed |
| T-14-38 | DoS | radio toggling | high | mitigate | static_no_radio_writes | closed |
| T-14-39 | Info disclosure | raw audio, JSONL, logcat committed | medium | mitigate | Filter, pull_dest_guard, nothing tracked | closed |
| T-14-40 | Tampering | :sample behavior change | low | mitigate | androidTest only, main diff empty | closed |
| T-14-SC (14-09) | Tampering | dependencies | low | accept | Existing catalog aliases only | closed (accepted) |
| T-14-41 | Tampering | alias widens to wrong value | high | mitigate | Zero promotions, lexicon untouched | closed |
| T-14-42 | Repudiation | skipped capture closes silently | medium | mitigate | Capture path taken, status line recorded | closed |
| T-14-43 | Info disclosure | fixture contents | low | accept | 88 of 88 rows synthetic-tts, neutral nouns | closed (accepted) |

*Severity: critical > high > medium > low. Only open threats at or above high count toward threats_open.*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-14-01 | T-14-05 | A resolver throw ends the run Failed with a STRATEGY_ERROR code and no message | plan-time disposition | 2026-10-06 |
| AR-14-02 | T-14-SC (14-01) | No new dependency | plan-time disposition | 2026-10-06 |
| AR-14-03 | T-14-10 | RAE paraphrases carry source URLs | plan-time disposition | 2026-10-06 |
| AR-14-04 | T-14-31 | Terminal intents write nothing, so skipping the gate is safe | plan-time disposition | 2026-10-06 |
| AR-14-05 | T-14-32 | NO_PROVIDER tier runs under any provider policy; maxTier still applies | plan-time disposition | 2026-10-06 |
| AR-14-06 | T-14-SC (14-09) | Only existing androidx.test catalog aliases | plan-time disposition | 2026-10-06 |
| AR-14-07 | T-14-43 | Synthetic TTS fixtures contain no PII-shaped text | plan-time disposition | 2026-10-06 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-06 | 45 | 45 | 0 | gsd-security-auditor (verdict SECURED, L1, block_on high) |

Residual non-blocking notes: R1 review IN-01 (a filler longer than the leading literal run is not caught at build time; fails closed). R2 three anchored linear Regex patterns on single tokens in DigitForms. R3 weak tests from review IN-06. R4 no .gitignore rule for wav or jsonl; the standing control is pull_dest_guard. R5 `ADB` binary override is a documented test lever and stays behind `-s`.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-06
