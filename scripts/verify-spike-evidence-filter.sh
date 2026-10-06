#!/usr/bin/env bash
# Offline proof of scripts/spike-evidence-filter.sh: the golden evidence file passes through unchanged, free text is dropped,
# and each leak shape (a key shape, a credential word, a tool-name field on an sb line, a digest of 9 or more hex characters)
# rejects the WHOLE capture with nothing on stdout. Negative samples are built from fragments at runtime so no key-shaped
# literal is committed (the git secret hook scans staged files).
# Prints "SPIKE FILTER OK cases=<n>", or "SPIKE FILTER FAIL: <case>" (exit 1).
# Usage: scripts/verify-spike-evidence-filter.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILTER="$HERE/spike-evidence-filter.sh"
GOLDEN="$HERE/../spike-ondevice/src/test/resources/evidence-golden.txt"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CASES=0

die() { echo "SPIKE FILTER FAIL: $1" >&2; exit 1; }
[ -x "$FILTER" ] || die "setup: $FILTER is missing or not executable"
[ -f "$GOLDEN" ] || die "setup: $GOLDEN is missing"

grep -vE '^(#|$)' "$GOLDEN" >"$WORK/golden.lines"
golden_n="$(grep -c '' "$WORK/golden.lines")"
comment_n="$(( $(grep -c '' "$GOLDEN") - golden_n ))"
[ "$golden_n" -ge 14 ] || die "setup: the golden file has only $golden_n lines"

# run_filter <input file>: sets OUT, ERR and RC.
run_filter() {
  "$FILTER" <"$1" >"$WORK/out" 2>"$WORK/err"
  RC=$?
  OUT="$(cat "$WORK/out")"
  ERR="$(cat "$WORK/err")"
}

# expect_kept <name> <input file> <expected kept count>
expect_kept() {
  run_filter "$2"
  [ "$RC" -eq 0 ] || die "$1: exit $RC, expected 0 ($ERR)"
  case "$ERR" in
    "FILTER OK kept=$3 "*) ;;
    *) die "$1: expected kept=$3, got: $ERR" ;;
  esac
  CASES=$((CASES + 1))
}

# expect_leak <name> <line>: the line is appended to the golden lines; the whole capture must be rejected.
expect_leak() {
  { cat "$WORK/golden.lines"; printf '%s\n' "$2"; } >"$WORK/leak.in"
  run_filter "$WORK/leak.in"
  [ "$RC" -eq 1 ] || die "$1: exit $RC, expected 1"
  [ -z "$OUT" ] || die "$1: stdout was not empty (a leak must reject the whole capture)"
  case "$ERR" in
    *"LEAK SCAN FAIL"*) ;;
    *) die "$1: expected LEAK SCAN FAIL, got: $ERR" ;;
  esac
  CASES=$((CASES + 1))
}

# 1. The golden file (comments dropped) is kept entirely and unchanged.
run_filter "$GOLDEN"
[ "$RC" -eq 0 ] || die "golden: exit $RC ($ERR)"
[ "$OUT" = "$(cat "$WORK/golden.lines")" ] || die "golden: kept lines differ from the golden lines"
case "$ERR" in
  "FILTER OK kept=$golden_n dropped=$comment_n") ;;
  *) die "golden: expected kept=$golden_n dropped=$comment_n, got: $ERR" ;;
esac
CASES=$((CASES + 1))

# 2. Free text and a line with a space in a value are dropped; the kept count is unchanged.
{
  cat "$WORK/golden.lines"
  echo "VAE_SPIKE_ENV note=a value with spaces"
  echo "turn on the lights in the kitchen please"
  echo "VAE_SPIKE_UNKNOWN kind=notallowed"
  echo "VAE_SPIKE_ENV note=\"quoted\""
} >"$WORK/dropped.in"
expect_kept "free text dropped" "$WORK/dropped.in" "$golden_n"

# 3. Carriage returns (adb logcat on some hosts) do not change what is kept.
sed 's/$/\r/' "$WORK/golden.lines" >"$WORK/crlf.in"
expect_kept "CRLF tolerated" "$WORK/crlf.in" "$golden_n"

# 4. A key shape in a value that matches the grammar rejects the whole capture.
KEY_VALUE="s""k-ant-0123456789abcdefghijklmnop"
expect_leak "key shape" "VAE_SPIKE_ENV note=$KEY_VALUE"
expect_leak "credential word" "VAE_SPIKE_ENV note=bearer"

# 5. A tool-name field on an sb line rejects the whole capture (SB tool names are private).
expect_leak "sb tool field" "VAE_SPIKE_TRIAL env=sb stage=confirm_sb tool=some_private_name"
expect_leak "sb tools field" "VAE_SPIKE_TRIAL env=sb stage=confirm_sb tools=a,b"
expect_leak "sb tool_name field" "VAE_SPIKE_TRIAL env=sb stage=confirm_sb tool_name=x"
expect_leak "sb arg_keys field" "VAE_SPIKE_TRIAL env=sb stage=confirm_sb arg_keys=a,b"

# 6. A digest longer than the 8-hex prefix rejects the whole capture.
expect_leak "sha of 9 hex" "VAE_SPIKE_MODEL model=e2b sha=0123456789a"
expect_leak "thresholds_sha of 9 hex" "VAE_SPIKE_ENV thresholds_sha=ec4933fb1"
expect_leak "gold_sha of 64 hex" "VAE_SPIKE_ENV gold_sha=$(printf '0123456789abcdef%.0s' 1 2 3 4)"

echo "SPIKE FILTER OK cases=$CASES"
