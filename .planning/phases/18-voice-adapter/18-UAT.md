---
status: complete
phase: 18-voice-adapter
source: [18-01-SUMMARY.md, 18-02-SUMMARY.md, 18-03-SUMMARY.md, 18-04-SUMMARY.md, 18-05-SUMMARY.md, 18-06-SUMMARY.md, 18-07-SUMMARY.md, 18-08-SUMMARY.md]
started: 2026-10-12T00:40:35Z
updated: 2026-10-12T00:40:35Z
---

## Current Test

[testing complete]

## Tests

### 1. :voice-adapter publishes and maps one :stt final segment to CommandInput (SC1)
expected: normalizeSttLanguageLabel trims and lowercases, maps exact en/es and gives null for anything else (no default). Text is verbatim. voice-adapter/api.txt is the frozen v1.1.0 surface. :stt is pinned v0.7.0. 33 adapter tests, 0 failures.
result: pass

### 2. :core stays hub-free; only :voice-adapter touches :stt (SC2)
expected: verify-stt-confinement.sh prints "STT CONFINEMENT OK checks=6" and its selftest "OK cases=12" (both re-run at Gate-2). :stt is compileOnly, behind a POM gate that also fails if it would be vacuous.
result: pass

### 3. Deferred :adapteralone clean-cache consumer probe
expected: scripts/jitpack-consumer-probe.sh includes :adapteralone (compiles both entry points; hard-fails if :stt is on its runtime classpath), and it ran green on the v1.1.0 release tree (20-QUIET-WINDOW-03).
result: pass

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]

## Notes

- Reviewed via evidence read-through with the operator. The STT confinement script and its selftest were re-run at Gate-2.
- The literal "adapteralone" grep in 20-QUIET-WINDOW-03 can't match the probe output (already recorded there). The probe's own exit status carries the proof.
