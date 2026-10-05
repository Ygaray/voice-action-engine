---
phase: 11-cut-v1-0-0
plan: 03
subsystem: docs
tags: [docs, wiring-stumbles, doc-snippets, keystore, credentials]
requires:
  - phase: 11-02
    provides: "public KeystoreCauseCodes and the reviewed interim signatures"
provides:
  - "final README, INTEGRATION, API and ECOSYSTEM docs for the tagged commit"
  - "compiled fixed-credentials snippet and updated keystore-wiring snippet"
  - "evidence/docs-gate.txt (DOCS GATE: PASS, API identical to 11-02)"
affects: [11-04, 11-05, 11-06, 11-07]
tech-stack:
  added: []
  patterns: ["import lists in text fences cross-checked against the compiled test's real imports"]
key-files:
  created:
    - .planning/phases/11-cut-v1-0-0/evidence/docs-gate.txt
  modified:
    - INTEGRATION.md
    - API.md
    - README.md
    - ECOSYSTEM.md
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
key-decisions:
  - "No tag, no :v1. coordinate: status lines reworded so they stay true before and after the cut"
status: complete
commits: 3
plan_head_before: b8a1081c5d9eb86b01dc8d81ecb3717fc5b483c3
actuals:
  tokens: 6000
  tasks: 3
  commits: 3
---

# Phase 11 Plan 03: Docs final pass Summary

The three wiring stumbles from the 36c578f464 rerun are closed in compiled or exact text, the compiled keystore snippet now uses the public `KeystoreCauseCodes` constants, and the shipped status lines no longer go stale when the tag exists. `commits: 3` counts the task commits; the SUMMARY commit follows it.

## Stumble fixes and where they live

1. **Stumble 1 (credential construction and exact signature).**
   - INTEGRATION.md section 7, "Keys" bullet: exact signature (`fun interface`, one `suspend fun credential(provider: ProviderId): CredentialLookup`) and a new `<!-- doc-snippet: fixed-credentials -->` block building `CredentialLookup.Present(Credential(provider, apiKey))` and `CredentialLookup.Missing()`. The key is a parameter, never a literal.
   - API.md "Credentials and keystore": the same exact signature and the `Present(Credential(provider, apiKey))` form.
   - DocSnippetsTest.kt: region `fixed-credentials` plus test `theFixedCredentialsRegionAnswersPresentOrMissing` (ANTHROPIC gives Present with that provider, OPENAI gives Missing; test key `test-key`). New import `core.Credential`.
2. **Stumble 2 (keystore-wiring imports).** INTEGRATION.md section 7, `:keystore` bullet: a `text` fence before the `keystore-wiring` marker lists `android.content.Context`, the three DataStore imports, and the engine imports (ProviderId, CredentialSource, ApiKeyStore, KeySlot, KeystoreCauseCodes, KeystoreCredentialSource). Every listed line was checked to be a real import of the compiled DocSnippetsTest. The text says DataStore comes with `keystore` as an `api` dependency.
3. **Stumble 3 (which modules).** README "Install (JitPack)" and INTEGRATION step 2 both say `core` alone is enough for a pipeline, every seam and an own scripted `AiProvider` (JVM-only, tests included); `providers` only for HTTP transports (own OkHttp kept, 4.12 wording retained); `keystore` only in an Android app (AAR, minSdk 35).

## Cause-code constants (Runtime Decision)

`keyAdvice` in both the `keystore-wiring` region and INTEGRATION.md branches on `KeystoreCauseCodes.KEY_MISSING`, `DECRYPT_FAILED`, `STORED_VALUE_MALFORMED` (re-enter) and `KEYSTORE_UNAVAILABLE`, `STORAGE_UNREADABLE` (transient, retry). Output strings are unchanged, so the existing assertions were untouched. The causes paragraph names each constant next to its literal code, so the C13 check still sees the literals.

## Status lines true before and after the cut

- README Status: v1.0 releases are immutable git tags listed in the repository's tags; a commit SHA also works. The in-verification wording is gone.
- ECOSYSTEM: Status is "the v1.0 core engine; v1.1 is planned"; the "Current published tag: none" line became "Published tags" pointing at the git tags and the §11 ledger in CROSS-REPO-SCOPE-CONTRACT.md; "Nothing is tagged yet." dropped. All three artifact ids are still named. No `:v1.` coordinate in any doc.

## Docs gate (evidence/docs-gate.txt, HEAD 7e5c314, DOCS GATE: PASS)

- `./gradlew check --offline` -> BUILD SUCCESSFUL
- `scripts/verify-docs-coverage.sh` -> DOC COVERAGE OK checks=23 types=97
- `scripts/agent-wiring-test.sh selftest` -> WIRING SELFTEST OK (reference PASS checks=9; planted bad copy fails W1-W5)
- `scripts/verify-repo-hygiene.sh` -> HYGIENE OK
- isolated three-module dump (core=1698, providers=121, keystore=67 lines) byte-identical to 11-02's reviewed signatures
- `git tag --list` empty; no api.txt present

## Deviations from Plan

None. Plan executed as written. No change under `core/src/main`, `providers/src/main`, `keystore/src/main`, no build file or `jitpack.yml` change.

## Reminder for later plans

This is the last plan that may change README, INTEGRATION, API or ECOSYSTEM or any public signature. Any later change to these docs or a public signature voids the 11-06 wiring SHA (after it, only the three api.txt files, `.planning/`, and §11 ledger rows may change).

## Self-Check: PASSED

- Files found: INTEGRATION.md, API.md, README.md, ECOSYSTEM.md, DocSnippetsTest.kt, evidence/docs-gate.txt
- Commits found: 85c6e5b, 7e5c314, 106cfdb
