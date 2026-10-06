#!/usr/bin/env bash
# Allow-list filter for captured spike evidence (Phase 13). stdin -> stdout, kept lines only.
#   The evidence vocabulary has no free text: every value is a token with no space, item ids are opaque, and SB tool names
#   never appear. This filter keeps ONLY the lines that fully match the closed grammar (ALLOW_RE below is byte-identical to
#   ALLOW_PATTERN in spike-ondevice/.../evidence/SpikeEvidence.kt; EvidenceGrammarTest proves it). Every other line is
#   counted and dropped, never printed, so a prompt, a model output, a tool argument or a stack trace cannot reach a
#   committed evidence file. The kept lines are then scanned, and one hit rejects the WHOLE capture (nothing on stdout):
#     - a key shape or a credential header word,
#     - on a line of the sb envelope (env=sb), a tool, tools, tool_name or arg_keys field (the SB tool names are private),
#     - a value of 9 or more hex characters (either case) on any key whose name contains sha, digest, hash or checksum, or any
#       value of 32 or more hex characters (digests stay at the 8-hex prefix, so a full fixture or gold-label digest cannot
#       leave the device).
# stderr: "FILTER OK kept=<n> dropped=<m>" (exit 0), or "LEAK SCAN FAIL" (exit 1, nothing on stdout).
# Usage: scripts/spike-evidence-filter.sh < captured.txt > kept.txt
set -uo pipefail
export LC_ALL=C

ALLOW_RE='^VAE_SPIKE_(ENV|TOOLCHAIN|PREFLIGHT|MODEL|INIT|PREFILL|KVREUSE|SCHEMAPROBE|GPU|TRIAL|MEM|THERMAL|EXIT|STAGE)( [a-z0-9_]+=[A-Za-z0-9_.:/,-]{0,96})+$'
# Key shapes and credential header words, assembled from fragments so no key-shaped literal sits in this file.
# Also covers Hugging Face (gated model downloads), Google, GitHub and AWS key shapes, and any 40+ character unbroken token.
KEY_RE="(^|[^A-Za-z0-9])(s""k-(ant|or|proj)-|s""k-[A-Za-z0-9_-]{20}|h""f_[A-Za-z0-9]{20,}|A""Iza[0-9A-Za-z_-]{30,}|gh""p_[A-Za-z0-9]{30,}|AK""IA[0-9A-Z]{16})|bearer|x-api-key|authorization|=[A-Za-z0-9_-]{40,}( |$)"

# True (0) when a kept line would expose SB content or a full digest. Command substitutions, not `grep -q` in a pipe: under
# pipefail an early-exiting grep -q can turn a hit into a miss.
spike_leak() {
  local f="$1" hits
  hits="$(grep -E '(^| )env=sb( |$)' "$f" | grep -E ' (tool|tools|tool_name|arg_keys)=' || true)"
  [ -z "$hits" ] || return 0
  # Any key whose name mentions sha/digest/hash/checksum, either hex case, plus any value that is 32+ hex characters.
  hits="$(grep -E ' [a-z0-9_]*(sha|digest|hash|checksum)[a-z0-9_]*=[0-9a-fA-F]{9,}|=[0-9a-fA-F]{32,}( |$)' "$f" || true)"
  [ -z "$hits" ] || return 0
  return 1
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

tr -d '\r' >"$TMP/in"
total="$(grep -c '' "$TMP/in")"
grep -E "$ALLOW_RE" "$TMP/in" >"$TMP/kept" || true
kept="$(grep -c '' "$TMP/kept")"
dropped=$((total - kept))

if grep -qiE "$KEY_RE" "$TMP/kept" || spike_leak "$TMP/kept"; then
  echo "LEAK SCAN FAIL" >&2
  exit 1
fi

cat "$TMP/kept"
echo "FILTER OK kept=$kept dropped=$dropped" >&2
