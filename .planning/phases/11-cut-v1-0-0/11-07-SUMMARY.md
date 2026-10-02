---
phase: 11-cut-v1-0-0
plan: 07
subsystem: release
tags: [api-baseline, metalava, jitpack, ver-05, d-01]
requires:
  - phase: 11-06
    provides: "wiring pass on W, packet accepted"
status: complete
plan_head_before: a875352da2ab1496169a4185cc73e0897728aad4
W: be49ea8fc5036f0cb3c8203a0d3accb19146480a
baseline_commit: c43c65ef437d50c4e9bbefe236a552aa027d2ca3
requirements-completed: [VER-05]
---

# Phase 11 Plan 07: v1.0.0 API baseline

- Pre-freeze answers were already on record from 11-06 (W05=ok); `GATE OK prefreeze`, `GATE OK wiring`, `GATE OK diff` (no DIFF NOTE) before the dump.
- Baseline commit `c43c65ef437d50c4e9bbefe236a552aa027d2ca3` adds exactly core/api.txt, providers/api.txt, keystore/api.txt.
- sha256: core dd2e3b573475d85eeafe59bd270b47977fd5e4e6b3c00ba4d13f09895735a780; providers 9e45e6873659bcfe974b518fb906d44a488add0848f28101b5268cafe1a78875; keystore 113e7c2ff8aa958d9d3386ee03dcfe83f646eaad265acdf13b2575e4fd2b597f.
- cmp: all three byte-identical to evidence/interim-api/*.api.sig. apiCheck and check green with the compat tasks executed; `HYGIENE OK` (PRE_RELEASE=0); `GATE OK api-dump`, `GATE OK hygiene`, `GATE OK leak`, `GATE OK diff`.
- JitPack: `LIVE PROBE PASS ref=c43c65ef43` (evidence/baseline-jitpack.txt). No tag exists.
