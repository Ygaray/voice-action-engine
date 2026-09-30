#!/usr/bin/env bash
# Metalava wiring proof (BLD-05, D-07) in an ISOLATED COPY of the working tree: the real tree never receives an api.txt
# (dumps are committed only at the v1.0.0 cut; a stray dump would arm the compat gate early and freeze an interim API).
#   a. apiDump on the real (possibly empty) public surface: report, do not require (see the contingency note below)
#   b. plant a public class per module -> apiDump -> every module's api.txt names it -> apiCheck green
#   c. add a second public class (additive change) -> apiCheck still green
#   d. remove the first class (breaking change) -> apiCheck must go RED and say "Removed"
# Usage: scripts/verify-api-dump.sh      (a few minutes; not part of `check`)
# Contingency: if step (a) fails because Metalava cannot dump an empty public surface, the script prints
# "EMPTY-SURFACE DUMP FAILED" and continues; steps b-d still prove the wiring, and the SUMMARY records the finding.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)"; COPY="$WORK/repo"; mkdir -p "$COPY"
# The copy is a full tree plus build outputs: remove it on exit unless KEEP_WORK=1 (debugging).
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
# Orchestrator/graph bookkeeping under .planning/ and graphify-out/ changes independently of this script: not compared.
tree_status() { git -C "$ROOT" status --porcelain -- . ':!.planning' ':!graphify-out'; }
before_status="$(tree_status)"
(cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
  | tar --null --ignore-failed-read -T - -cf -) | tar -x -C "$COPY"
if [ -f "$ROOT/local.properties" ]; then cp "$ROOT/local.properties" "$COPY/"; fi
cd "$COPY"
MODULES="core providers keystore"
pkgdir() { echo "$1/src/main/kotlin/io/github/ygaray/voiceactionengine/$1"; }
plant() { # <module> <ClassName>
  printf 'package io.github.ygaray.voiceactionengine.%s\n\npublic class %s\n' "$1" "$2" > "$(pkgdir "$1")/$2.kt"
}
fail() { echo "API DUMP PROOF FAIL: $1" >&2; exit 1; }

echo "== a. empty-surface dump"
if ./gradlew -q apiDump >/dev/null 2>"$COPY/a.err"; then
  for m in $MODULES; do
    [ -f "$m/api.txt" ] || fail "a: $m/api.txt not produced"
    head -1 "$m/api.txt" | grep -q 'Signature format' || fail "a: $m/api.txt lacks a Signature format header"
    echo "   $m/api.txt: $(wc -l < "$m/api.txt") lines"
  done
else
  echo "EMPTY-SURFACE DUMP FAILED: $(tail -5 "$COPY/a.err" | tr '\n' ' ')"
fi
rm -f core/api.txt providers/api.txt keystore/api.txt

echo "== b. planted public class -> dump -> check"
for m in $MODULES; do plant "$m" ZzApiProbe; done
./gradlew -q apiDump
for m in $MODULES; do grep -q 'ZzApiProbe' "$m/api.txt" || fail "b: $m/api.txt does not name ZzApiProbe"; done
./gradlew -q apiCheck || fail "b: apiCheck failed right after a fresh dump"

echo "== c. additive change stays green"
for m in $MODULES; do plant "$m" ZzApiAdded; done
./gradlew -q apiCheck || fail "c: an additive public class made apiCheck fail"

echo "== d. removal goes red"
for m in $MODULES; do rm -f "$(pkgdir "$m")/ZzApiProbe.kt"; done
if ./gradlew -q apiCheck >"$COPY/d.out" 2>&1; then fail "d: removing a public class did not fail apiCheck"; fi
grep -q 'Removed' "$COPY/d.out" || fail "d: apiCheck failed but did not report 'Removed' (wrong reason)"

cd "$ROOT"
# Real-tree guard: the working-tree status must be byte-identical to the snapshot taken before the run.
[ "$(tree_status)" = "$before_status" ] || fail "the real working tree changed during the run"
if [ "${KEEP_WORK:-0}" = 1 ]; then note="copy kept at $COPY"; else note="copy removed on exit"; fi
echo "API DUMP PROOF OK (real tree untouched; $note)"
