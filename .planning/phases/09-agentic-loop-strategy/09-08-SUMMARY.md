---
phase: 09-agentic-loop-strategy
plan: 08
subsystem: build-invariants
tags: [cln-02, scanner, negative-controls, detekt-adjacent, keystore-kdoc]
requires:
  - phase: 09-07
    provides: both agentic ports landed (nothing left that should name an app domain)
provides:
  - raw-text CLN-02 rule class (app-domain name in source, hard-coded tool count) in scanBannedConstructs
  - scan coverage of core src/testFixtures in addition to src/main of all three published modules
  - self-checking negative controls app-domain.kt.txt and tool-count.kt.txt, extended clean.kt.txt
  - CLN-02 plants in scripts/verify-negative-controls.sh
affects: [09-09]
tech-stack:
  added: []
  patterns: ["raw-text scanner rules matched against unmodified text so literals and comments are covered"]
key-files:
  created:
    - config/negative-controls/app-domain.kt.txt
    - config/negative-controls/tool-count.kt.txt
  modified:
    - gradle/invariants.gradle.kts
    - config/negative-controls/clean.kt.txt
    - scripts/verify-negative-controls.sh
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcm.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt
key-decisions:
  - "Deny-list kept only in gradle/invariants.gradle.kts (never scanned); includes edit_text_card per sign-off CARRY"
  - "Tool-count rule also catches a tools-named list's size or count() compared to 17/18, not only identifiers and the 'N tools' phrase"
requirements-completed: [CLN-02]
status: complete
plan_head_before: 135c08ce6770c1c0684e9e8ba40100975e1c2b2b
commits: 2
actuals:
  tokens: 4000
  tasks: 2
  commits: 2
duration: ~20min
completed: 2026-10-01
---

# Phase 9 Plan 08: CLN-02 raw-text scanner Summary

Library code is now mechanically barred from naming an app domain or hard-coding a tool count, with every rule self-checked by a negative control and a plant that turns each module red.

## What was built

- `gradle/invariants.gradle.kts`: `rawRules` (matched against unmodified text) with ids `app-domain name in source` (LogFood*, log_food, MutationTier*, SYSTEM_PROMPT, TAG_DISAMBIGUATION*, find_tags, create_tag, edit_list_card, edit_text_card, SecondBrain, CalTracker) and `hard-coded tool count` (TOOL_COUNT, EXPECTED_TOOL_COUNT, NUM_TOOLS, toolCount, "17/18 tool(s)", tools size/count() == 17/18). `scanText` runs them; `scanBannedConstructs` now scans `src/main` plus `src/testFixtures`.
- Controls: `app-domain.kt.txt`, `tool-count.kt.txt`, and `clean.kt.txt` extended with near-misses (catalog_food, SYSTEM_PROMPTS_SEEN, createTagged, edit_text_cards, edit_text_card_x, "118 tools", an unrelated size compare). All still yield exactly their EXPECT set.
- `scripts/verify-negative-controls.sh`: per-module app-domain and tool-count plants plus a core testFixtures plant.
- Three `:keystore` KDoc sentences neutralized (KDoc lines only).

## RED then GREEN (keystore scan)

After adding SecondBrain/CalTracker to the rule, before rewording, `:keystore:scanBannedConstructs` failed:

```
AesGcm.kt:15 [app-domain name in source] 'SecondBrain'
AesGcm.kt:15 [app-domain name in source] 'CalTracker'
AndroidKeyStoreKeyAccess.kt:25 [app-domain name in source] 'SecondBrain'
AndroidKeyStoreKeyAccess.kt:25 [app-domain name in source] 'CalTracker'
ApiKeyStore.kt:31 [app-domain name in source] 'SecondBrain'
```

After the KDoc rewording the core, providers and keystore scans and `:core:verifyInvariantScannerControls` passed; `:keystore:testDebugUnitTest :keystore:detekt` passed.

## Negative-controls run (`scripts/verify-negative-controls.sh`, exit 0)

```
ok    [app-domain name (core)] went red (Banned constructs)
ok    [hard-coded tool count (core)] went red (Banned constructs)
ok    [app-domain name (providers)] went red (Banned constructs)
ok    [hard-coded tool count (providers)] went red (Banned constructs)
ok    [app-domain name (keystore)] went red (Banned constructs)
ok    [hard-coded tool count (keystore)] went red (Banned constructs)
ok    [app-domain name (core testFixtures)] went red (Banned constructs)
negative-control failures: 0
```

No `FAIL` lines in the whole run. `git status --porcelain -- core/src providers/src keystore/src` was empty afterwards (no ZzPlant.kt left). `./gradlew check --offline` was green on the final tree (all legs incl. detekt and the OkHttp matrix).

## Task commits

1. Task 1 (tracer): e9983e2 - raw-text CLN-02 rules, controls, plants
2. Task 2: 9c821e4 - app names enforced, keystore KDoc neutralized

## Deviations from Plan

None. The pre-existing tree already contained no deny-listed name outside the three keystore KDoc lines, so Task 1's tracer passed on the real tree without source changes. Note the sandbox's git-command filter rejected compound commands, so verifications were run as separate plain commands; no effect on outcomes.

## Verification against plan criteria

- `grep -rIl 'SecondBrain\|CalTracker' core/src/main providers/src/main keystore/src/main core/src/testFixtures` prints nothing.
- `git diff -U0 <base> -- keystore/src/main` shows only ` * ` KDoc lines changed.
- `git log <base>..HEAD -- core/src providers/src` is empty; no evidence-directory file written; no api.txt; no tag; STATE.md and ROADMAP.md untouched.

## Self-Check: PASSED

Created files exist (app-domain.kt.txt, tool-count.kt.txt); commits e9983e2 and 9c821e4 exist on the worktree branch.
