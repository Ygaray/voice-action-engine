---
phase: 13-on-device-model-spike
reviewed: 2026-10-06T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - gradle/invariants.gradle.kts
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
  - scripts/verify-ml-denial-controls.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-repo-hygiene.sh
  - scripts/verify-spike-verdict.sh
  - scripts/spike-evidence-filter.sh
  - scripts/verify-spike-disposition.sh
  - .gitignore
  - settings.gradle.kts
  - gradle/libs.versions.toml
  - .planning/phases/13-on-device-model-spike/evidence/ (15 committed evidence files, leak scan)
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../verdict/VerdictRules.kt"
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../verdict/Scoring.kt"
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../verdict/CellSelector.kt"
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../verdict/Thresholds.kt"
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../gold/GoldMatcher.kt"
  - "deleted harness at e362fb228b (light pass): spike-ondevice/.../evidence/SpikeEvidence.kt and Records.kt"
findings:
  critical: 0
  warning: 7
  info: 8
  total: 15
status: fixed
---

# Phase 13: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** standard
**Files Reviewed:** 18 (live deliverables plus a light pass over the deleted harness)
**Status:** issues_found

## Summary

I reviewed the live Phase 13 deliverables: the ML-denial gate in `gradle/invariants.gradle.kts`, the `:core` on-device scan
test, the four shell gates, the evidence filter, the disposition gate and the `.gitignore` additions. I also did a lighter
pass over the deleted harness's verdict engine and gold matcher at `e362fb228b`.

I executed `scripts/verify-repo-hygiene.sh` (`HYGIENE OK`) and `scripts/verify-spike-disposition.sh removed`
(`SPIKE DISPOSITION OK mode=removed`). `13-THRESHOLDS.md` still hashes to the recorded `ec4933fb...`.

I did NOT execute `verify-spike-verdict.sh --check`, `verify-ml-denial-controls.sh` or `verify-negative-controls.sh`. They need a
Gradle build, and the host swap was 2047/2047 MB used, which is the earlyoom-kill condition from the release-cut memory. Those
gates are unverified by this review.

No secrets, transcripts or SB fixture content were found in the committed evidence or in the tracked tree. All 396 evidence
lines are either closed-grammar `VAE_SPIKE_*` lines or `# spike ...` header comments. No key shapes, home paths or tailnet
IPs appear in the evidence, and no `*sb-gold*`, `*a10-fixture*`, `.litertlm` or `.bin` file is tracked. There are no Critical
defects. The weaknesses are in the gates' coverage and in the verdict engine's treatment of an incomplete envelope. The
verdict is red regardless, because small is a measured fail by a wide margin and sb is unmeasured.

## Warnings

### WR-01: The spike's own private SB fixture filename is not covered by .gitignore, hygiene or any negative control

**File:** `.gitignore:47-55`, `scripts/verify-repo-hygiene.sh:57`, `scripts/verify-ml-denial-controls.sh:89`
**Issue:** The spike runner (deleted, `scripts/run-spike-ondevice.sh` at `e362fb228b` lines 359-360) reads the private inputs
from `$VAE_SPIKE_PRIVATE_DIR/sb-fixture.json` and `sb-gold.json`. The new rules cover only `*sb-gold*`. For the fixture, the
repo still has only the older `sb-a10-fixture*.json` ignore pattern and the matching hygiene spec `*sb-a10-fixture*`. A file
named `sb-fixture.json` (the name the spike itself used for SB's 19-tool schema) is not ignored. `git add .` would stage it,
and `verify-repo-hygiene.sh` section (c) would not flag it. The repo is public, and the SB tool schema and system prompt are
SB-private. `VAE_SPIKE_PRIVATE_DIR` is overridable and was only guarded for the raw-answers directory. The negative control
plants only `zz-plant-sb-gold.json`, so the gap is also untested.
**Fix:** Add `sb-fixture*.json` (or `*sb-fixture*`) to `.gitignore`, add `'*sb-fixture*'` to `forbidden_specs`, add a matching
`git check-ignore` path in section (b), and add a `zz-plant-sb-fixture.json` plant in Part C of `verify-ml-denial-controls.sh`.

### WR-02: Evidence leak scan covers only Anthropic/OpenRouter/OpenAI key shapes

**File:** `scripts/spike-evidence-filter.sh:17-19`
**Issue:** `ALLOW_RE` admits any value of up to 96 characters from `[A-Za-z0-9_.:/,-]`, which is long enough to carry a token.
`KEY_RE` rejects only `sk-ant-`, `sk-or-`, `sk-proj-`, `sk-` plus 20 characters, `bearer`, `x-api-key` and `authorization`.
This spike involved gated Gemma downloads. The plan records a Hugging Face terms question, and `fetch-model` existed. A
Hugging Face token (`hf_` plus 30+ alphanumerics), a Google key (`AIza...`), a GitHub token (`ghp_...`) or an AWS key
(`AKIA...`) in a value would pass the filter and be committed. The grammar is closed on the device side, so this is
defense in depth, but the file's own comment says the filter exists so a key "cannot reach a committed evidence file".
**Fix:** Extend `KEY_RE` with fragment-assembled shapes, for example:
```bash
KEY_RE="...|(^|[^A-Za-z0-9])(h""f_[A-Za-z0-9]{20,}|A""Iza[0-9A-Za-z_-]{30,}|gh""p_[A-Za-z0-9]{30,}|AK""IA[0-9A-Z]{16})"
```
Optionally reject any value of 40 or more characters that matches `[A-Za-z0-9_-]{40,}`.

### WR-03: The digest rule is bypassed by uppercase hex and by any key name not on the fixed list

**File:** `scripts/spike-evidence-filter.sh:27`
**Issue:** The full-digest check is `' (sha|thresholds_sha|model_sha|fixture_sha|gold_sha)=[0-9a-f]{9,}'`. It matches only
lowercase hex and only those exact key names. The value alphabet allows uppercase, so `fixture_sha=8BC739ED...` (64 chars)
passes. A key such as `sha256=`, `digest=`, `labels_sha=` or `file_sha=` also passes at full length. The stated goal is
that "a full fixture or gold-label digest cannot leave the device". A full digest of the private fixture lets anyone
confirm SB's tool surface against a guess, and it is committed in a public repo.
**Fix:** Drop the key list and the case restriction:
```bash
hits="$(grep -E '=[0-9a-fA-F]{9,}( |$)' "$f" || true)"
```
This also catches other long hex values (build hashes), so allow-list the known short ones (`head=` is 10 hex) by length 9..12
only if needed.

### WR-04: The filter's parity proof and its test were deleted, but the live script still claims them

**File:** `scripts/spike-evidence-filter.sh:3-5`, `scripts/verify-spike-disposition.sh:37`
**Issue:** The header says `ALLOW_RE` is "byte-identical to ALLOW_PATTERN in spike-ondevice/.../evidence/SpikeEvidence.kt;
EvidenceGrammarTest proves it". Plan 13-10 deleted `SpikeEvidence.kt`, `EvidenceGrammarTest` and
`scripts/verify-spike-evidence-filter.sh`. The retained filter (a security control) now has no test, no leak-scan negative
controls and no source of truth for its grammar. The claim in the header is false at HEAD. The disposition gate allow-lists
the file but nothing exercises it.
**Fix:** Either delete the filter (red branch: nothing ships), or keep it and replace the stale comment with the real status
("grammar frozen from SpikeEvidence.kt@e362fb228b, untested at HEAD"). Preferably retain the 97-line
`verify-spike-evidence-filter.sh` from `git show 1fec77a^:scripts/verify-spike-evidence-filter.sh` and fix it to not need the
deleted Kotlin file.

### WR-05: Part 5 was appended to a script that cannot go green, and the full script was never run

**File:** `scripts/verify-negative-controls.sh:141-142`
**Issue:** `12-VERIFICATION.md` W-1 records that `verify-negative-controls.sh` exits 1 with `negative-control failures: 3`.
The three `api.txt missing once released` plants (lines 92-94) are stale because they never remove `api.txt`. Phase 13 added
Part 5 behind it. The aggregate gate named in `13-VALIDATION.md` therefore cannot pass, and the Part 5 signal is buried in a
count of 3 known failures. `13-02-SUMMARY.md` says the full script was "Not run".
**Fix:** Fix the stale Part 1 plants (back up `$m/api.txt`, delete it, run `verifyApiDumpPresent -PvaeAssumeReleased`, restore),
or reference `verify-ml-denial-controls.sh` directly as its own gate in the validation commands, and run the full script once
so its green status is evidenced.

### WR-06: Verdict engine does not treat a host-synthesised `result=timeout` stage as incomplete (deleted harness, report-only)

**File:** `spike-ondevice/.../verdict/VerdictRules.kt@e362fb228b:195-202` (`stageReasons`)
**Issue:** `incomplete` is true only when `result == "error"` or `trials < planned` with both present. The committed
`screen_sb.host.txt` is `VAE_SPIKE_STAGE stage=screen_sb result=timeout source=host`, with no `trials=` or `planned=`. It
raises no `fail:incomplete_stage` and no `early_exit:` reason. The committed sb verdict line has only `unmeasured:*` reasons,
so the timeout is invisible to a machine reader of `SPIKE_VERDICT`. Because a timed-out stage that happens to have enough
trials already on file would raise nothing, a truncated run could in principle grade green on N alone. The mirror
condition for the exit-reasons stage (`result != done`) is handled correctly in `scoreDeaths`.
**Fix (for any future re-run):** Treat any stage result other than `done`, `skipped` and `early_exit` as incomplete:
```kotlin
result !in setOf("done", "skipped", "early_exit") ||
    (trials != null && planned != null && trials < planned)
```
Findings here can only be reported: `13-VERDICT.md` is pinned to this code by `SPIKE_VERDICT_CODE sha=e362fb228b`.

### WR-07: The sb verdict line carries `peak_pss_mb=525` with no reason, while 4267 and 5669 MB were measured (deleted harness, report-only)

**File:** `spike-ondevice/.../verdict/VerdictRules.kt@e362fb228b:279-302` (`scorePss`), `13-VERDICT.md:25,62,72-76`
**Issue:** `scorePss` keeps only lines whose `envelopeOf()` equals the envelope. A shared stage has a null envelope, so
`kv_reuse` MEM (5669 MB with an sb preface) and `prefill` MEM (5459 MB) are excluded, and only lines on the winning cell
count. The sb winner is provisional (8 of 20 screen trials), and its only sample was an INIT reading of 525 MB. The 4267 MB
peak of the other sb screen cell is not the winner's. The engine raises neither `unmeasured:peak_pss` nor `fail:peak_pss`.
`13-VERDICT.md` documents the caveat in prose and `13-DISPOSITION.md` lists it as a follow-up, but the machine line a
consumer such as SB 179 parses still reads 525 MB against a 2000 MB bar.
**Fix (future re-run):** Fail closed. Take the max over every MEM/INIT line on the envelope's stages and the shared `kv_reuse`
stage. If the winner has no MEM sample (INIT only), raise `unmeasured:peak_pss`. In the meantime, add the caveat to
`13-VERDICT-MESSAGE.md`'s machine block as a comment line.

## Info

### IN-01: `*.bin` is ignored and forbidden repo-wide

**File:** `.gitignore:52`, `scripts/verify-repo-hygiene.sh:57`
**Issue:** The model-weight patterns are not scoped to a directory. Any legitimate `.bin` test resource in `core`,
`providers` or `keystore` would be silently ignored, and a tracked one would fail hygiene section (c). Weights in other
common formats (`*.gguf`, `*.onnx`, `*.safetensors`, `*.pte`) are not listed.
**Fix:** Scope `*.bin` to `**/src/**/assets/**` or drop it, and add the other weight extensions.

### IN-02: `grep -v ... | grep -q` under `pipefail` is a latent false-negative

**File:** `scripts/verify-spike-disposition.sh:72,93`
**Issue:** `scripts/spike-evidence-filter.sh` documents that an early-exiting `grep -q` in a pipe can turn a hit into a miss
under `pipefail`. The disposition gate uses exactly that shape for the `settings.gradle.kts` and `jitpack.yml` checks. Both
files are small enough that the writer finishes in one write today, so there is no live failure.
**Fix:** Capture into a variable first (`lines="$(grep -v ... || true)"; echo "$lines" | grep -Eqi ...`), as `verify-repo-hygiene.sh` does for `jitpack_cmds`.

### IN-03: Committed evidence includes the TESTER serial and `#` header lines the filter would drop

**File:** `.planning/phases/13-on-device-model-spike/evidence/*.txt` (line 1 of 10 files), `env.txt:1`
**Issue:** Each stage file begins with `# spike stage=... target=R5CT10XNKQN head=...`, and `env.txt` has `target=R5CT10XNKQN`.
The `# spike` lines are not output of `spike-evidence-filter.sh` (it keeps only `VAE_SPIKE_*` grammar lines), so the committed
evidence is not purely filtered output, contrary to the filter's framing. A hardware serial is committed in a repo described
as public. The serial is also committed in other planning docs and runner scripts, so this follows the project's existing
convention.
**Fix:** Accept as convention, or drop `target=` from the header and ENV line in any future capture.

### IN-04: An SB tool name appears in committed planning docs

**File:** `13-CONTEXT.md:102`, `13-SB-LABELS-ANSWER.md:8`, `13-THRESHOLDS.md:15`, `13-WINDOW-GRANT.md:24`
**Issue:** The filter and the design state that SB tool names "never appear" because they are private. One SB tool name
(`ask_user_to_choose`) is committed in four phase docs, alongside the fixture digest prefix and tool count. Low sensitivity,
but it contradicts the stated invariant.
**Fix:** Say "a 19-tool surface including its ask-the-user tool", or decide that this tool name is public and adjust the
filter's rationale.

### IN-05: `13-DISPOSITION.md` points at the wrong recovery SHA

**File:** `.planning/phases/13-on-device-model-spike/13-DISPOSITION.md:39` ("The harness is recoverable at `08ada3366b`")
**Issue:** The verdict is computed by `e362fb228b`. That commit added the RT-03 grading tolerance and the host-side sb
re-score, and `verify-spike-verdict.sh --check` builds that SHA. Restoring `08ada3366b`, which is the evidence-producing
`harness_sha`, gives a different `GoldMatcher` and a different sb re-score path.
**Fix:** Say "recoverable at `e362fb228b` (verdict code); evidence was produced by `08ada3366b`".

### IN-06: Stale references to the deleted module in live gates

**File:** `scripts/verify-repo-hygiene.sh:27,50-52,85-87`, `scripts/verify-ml-denial-controls.sh:99-100`,
`scripts/verify-spike-verdict.sh:82`
**Issue:** The hygiene gate still scans, ignores-checks and jitpack-checks `spike-ondevice` paths. The ML-denial jitpack
plant names `:spike-ondevice:assembleDebug`. The `.gitignore` `check-ignore` probes under `spike-ondevice/src/main/assets/`
pass only because the patterns are repo-wide, not because of anything about that directory. Harmless, but dead coupling.
**Fix:** Keep as a deliberate regression guard but say so in a comment, or generalise the names (`ondevice`, any `*spike*`).

### IN-07: ML deny-list is narrow, and the `:core` scan's comment heuristic can be bypassed

**File:** `gradle/invariants.gradle.kts:334-335`, `NoHardCodedConstantsTest.kt:80-84`
**Issue:** `deniedMlNameTokens` is only `litert` and `tflite`, and the group prefixes are Google/TensorFlow only. Other
on-device runtimes (`onnxruntime`, `executorch`, `llama`, `pytorch`) pass. In the test, `onDeviceHit` skips any line whose
trimmed text starts with `*`, `/*` or `//`, so `/* x */ val e = LiteRtEngine()` or a code line beginning with `*` is not
flagged. These are contrived, and the dependency gate is the real defense, but the scan claims to prove SC4.
**Fix:** Add the extra runtime tokens. Strip comments properly, as `SourceSplitter` in `invariants.gradle.kts` does, instead
of testing line prefixes.

### IN-08: Script hygiene nits

**File:** `scripts/verify-negative-controls.sh:12-18`, `scripts/verify-ml-denial-controls.sh:25`
**Issue:** `verify-negative-controls.sh` creates `BAK="$(mktemp -d)"` and never removes it (the ML script does), so a temp
directory is left behind on every run. The ML script sets `GRADLE_OPTS` by assignment rather than append, silently dropping
any caller-set options such as a proxy.
**Fix:** Add `rm -rf "$BAK"` to `cleanup` in the former, and use `GRADLE_OPTS="${GRADLE_OPTS:-} ..."` in the latter.

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
