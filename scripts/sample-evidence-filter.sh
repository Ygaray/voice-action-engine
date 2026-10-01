#!/usr/bin/env bash
# Allow-list filter for captured Gate-1 logcat (D-02). stdin -> stdout, kept lines only.
#   Keeps ONLY the lines that fully match the closed evidence grammar of the sample app (the extended-regex form of
#   ALLOW_PATTERN in sample/.../evidence/EvidenceLine.kt; scripts/verify-sample-device-guard.sh proves every golden
#   line passes unchanged). Every other line is counted and dropped, never printed, so a prompt, a reply, a tool
#   argument or a stack trace cannot reach a committed evidence file. The kept lines are then scanned for key shapes;
#   one hit rejects the WHOLE capture: nothing goes to stdout.
# stderr: "FILTER OK kept=<n> dropped=<m>" (exit 0), or "LEAK SCAN FAIL" (exit 1, nothing on stdout).
# Usage: scripts/sample-evidence-filter.sh < captured.txt > kept.txt
set -uo pipefail
export LC_ALL=C

ALLOW_RE='^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)( [a-z0-9_]+=[][A-Za-z0-9_.:/,-]{0,96})+$'
# Key shapes and credential header words, assembled from fragments so no key-shaped literal sits in this file.
KEY_RE="(^|[^A-Za-z0-9])(s""k-(ant|or|proj)-|s""k-[A-Za-z0-9_-]{20})|bearer|x-api-key|authorization"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

tr -d '\r' >"$TMP/in"
total="$(grep -c '' "$TMP/in")"
grep -E "$ALLOW_RE" "$TMP/in" >"$TMP/kept" || true
kept="$(grep -c '' "$TMP/kept")"
dropped=$((total - kept))

if grep -qiE "$KEY_RE" "$TMP/kept"; then
  echo "LEAK SCAN FAIL" >&2
  exit 1
fi

cat "$TMP/kept"
echo "FILTER OK kept=$kept dropped=$dropped" >&2
