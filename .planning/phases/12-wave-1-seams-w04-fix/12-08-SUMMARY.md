---
phase: 12-wave-1-seams-w04-fix
plan: 08
subsystem: verification
tags: [prov-16, live-smoke, gate-1, tester, model-unsupported, w04]
requires: [12-01, 12-05, 12-06, 12-07]
provides:
  - "evidence/gate1-responses_probe.txt: live TESTER evidence that gpt-6-astra under the supportsTools override returns the typed model_unsupported (http 400), never http_error"
affects: [19]
tech-stack:
  added: []
  patterns: ["host Probe C of the exact post-fix wire before any device time, then one bounded device leg"]
key-files:
  created:
    - .planning/phases/12-wave-1-seams-w04-fix/evidence/gate1-responses_probe.txt
  modified:
    - .planning/phases/12-wave-1-seams-w04-fix/12-LIVE-LEG-DECISION.md
key-decisions:
  - "Task 2 (TESTER window) was satisfied by Runtime Decision RT-01 (commit f8d4d73), a GRANT from orchestrator yahir-gsd-control-plane-3b for Task 3 only; no separate window-open prompt was needed"
requirements-completed: [PROV-16]
status: complete
plan_head_before: f9ed2327d1658317fc3d193183c12aeca0725b78
commits: 4
actuals:
  tokens: 6000
  tasks: 3
  commits: 4
---

# Phase 12 Plan 08: PROV-16 live smoke on the TESTER Summary

The live `:sample` `responses_probe` leg on the wired TESTER returned PASS with the typed `model_unsupported` (HTTP 400) for `gpt-6-astra` under the `supportsTools` override, within the request ceiling, with full cleanup and no key left behind.

## Verdict

Exact committed evidence line (`evidence/gate1-responses_probe.txt`):

```
VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui
```

Supporting evidence lines in the same file: `VAE_ATTEMPT ... n=1 kind=initial http=400`, `VAE_OUTCOME ... kind=failed reason=model_unsupported executed=0 committed=0`, `VAE_BUDGET core=0 optional=1 anthropic=0 openai=1 openrouter=0 est_usd=unknown`. The in-app status node read `PASS reason=model_unsupported` (leg latency 1142 ms). The committed file re-passes `scripts/sample-evidence-filter.sh` (`FILTER OK kept=5 dropped=1`).

## Spend

`PROV16 LIVE SPEND: requests=2 (host_probe=1 device=1) est_cost_usd=0.00 ceiling=4`

Both requests drew an HTTP 400 on an unsupported model, which bills nothing (ceiling USD 0.05 not approached). Request count is 2 of the ceiling 4; no re-run was made.

## Task results

| Task | Name | Result | Commit |
|------|------|--------|--------|
| 1 | Host Probe C of the post-fix astra_direct wire | Done earlier: HTTP 400, classified `model_unsupported`; 1 request used | 76f896c (decision record) |
| 2 | TESTER window with the orchestrator | Satisfied by RT-01 grant (see below) | f8d4d73 |
| 3 | Bounded responses_probe leg on the TESTER and evidence commit | PASS, evidence committed | fcec123 |

## TESTER window

- Grant: Runtime Decision RT-01 (12-CONTEXT.md, commit f8d4d73), relayed from orchestrator `yahir-gsd-control-plane-3b`: TESTER R5CT10XNKQN for 12-08 Task 3 only, one responses_probe leg, about 15 min, 3 requests left of the 4 / USD 0.05 ceiling after Probe C, `adb -s R5CT10XNKQN` only, no re-runs past the ceiling, full cleanup.
- Window open: 2026-10-05T16:01:47-06:00 (first `date -Is` of this run; `adb devices` listed R5CT10XNKQN as `device`).
- Window close: 2026-10-05T16:03:14-06:00 (cleanup finished). The whole leg took about 90 seconds of device time.
- The driver (orchestrator) announces "device done tester" using the verdict and spend lines above; this executor did not message the orchestrator directly.

## Runner sequence (each keyed off the `SAMPLE_GATE1:` last line, exit 0 throughout)

1. `preflight`: `SAMPLE_GATE1: OK sub=preflight target=R5CT10XNKQN model=SM-S908U sdk=35 installed=no`
2. `build-install`: `SAMPLE_GATE1: OK sub=build-install target=R5CT10XNKQN apk_md5=a9663b91e450b8befc0343e7748c5099 head=e3de426508 dirty=0 asset_fixture=absent` (foreground Gradle, no memory kill)
3. `push-keys`: `SAMPLE_GATE1: OK sub=push-keys providers=anthropic,openai,openrouter target=R5CT10XNKQN` (passed because the decision line reads `approved`)
4. In-app `import_test_keys`: `import_status` showed `anthropic=Ready deleted=true in_datastore=false; openai=Ready deleted=true in_datastore=false; openrouter=Ready deleted=true in_datastore=false`; `budget_used` showed `requests 0/33 · optional 0/1`
5. `verify-keys-gone`: `SAMPLE_GATE1: OK sub=verify-keys-gone keys_gone=yes`
6. `capture-start`, then one UI press of `run_responses_probe` (`trigger=ui`), then `capture-save responses_probe`: `SAMPLE_GATE1: OK sub=capture-save kept=5 dropped=1 file=.planning/phases/12-wave-1-seams-w04-fix/evidence/gate1-responses_probe.txt`
7. Final `verify-keys-gone`: `SAMPLE_GATE1: OK sub=verify-keys-gone keys_gone=yes`
8. `cleanup` (force-stop, remove test data, uninstall, prove package gone; prints `sample package removed`): `SAMPLE_GATE1: OK sub=cleanup target=R5CT10XNKQN`

## Cleanup confirmation

- TESTER: `:sample` uninstalled (`pm list packages` shows 0 voiceactionengine packages); the on-device key directory was empty at `verify-keys-gone` before and after the leg. The keys were imported into `:keystore` and the plaintext files deleted (`deleted=true`) before the call; uninstall then removed app storage.
- Host: `${XDG_RUNTIME_DIR}/vae-probe-c` removed (probe-c-answer.json and probe-c-body.json gone, directory confirmed missing).
- Repository: `git status --porcelain` shows no probe file or uiautomator dump. UI dumps were kept in the session scratchpad only.
- No key value was read, printed or passed on a command line by this executor. The screen showed key last-4 characters in `key_state_*` nodes during the dump; they are deliberately not recorded anywhere.

## Deviations from Plan

None to code or scope. Two process notes:

1. The TESTER showed the lockscreen (keyguard) on first launch; a wake plus swipe-up unlocked it (the rig has no passcode by design). This changed no state beyond the screen.
2. Task 2 was satisfied by the RT-01 grant rather than the plan's "window open" resume signal, as directed by the dispatch.

`commits: 4` is measured from the plan head (`f9ed232`..HEAD, before this SUMMARY): 76f896c, f8d4d73 and e3de426 are decision/grant records (f8d4d73 written by the orchestrator side), fcec123 is the evidence commit.

## Open Item for the orchestrator: Phase 19 D-13 vs PROV-16

Phase 19's D-13 says the live `gpt-6-astra` smoke "must succeed with no 400 on `reasoning_effort`". On Chat Completions that model takes no function tools, so the only reachable live result is the typed `ModelUnsupported` proved here (`reason=model_unsupported http=400`, post-fix wire, never `http_error`). D-13 should be restated to reuse this evidence line (`.planning/phases/12-wave-1-seams-w04-fix/evidence/gate1-responses_probe.txt`) instead of a second paid call. Not a task in this phase; also recorded in 12-LIVE-LEG-DECISION.md.

## Self-Check: PASSED

- Evidence file exists and contains the PASS line (`grep -F` matches); filter re-scan exit 0.
- Commit fcec123 exists; cleanup and verify-keys-gone ended `OK`; spend line shows requests=2, at or below 4.
