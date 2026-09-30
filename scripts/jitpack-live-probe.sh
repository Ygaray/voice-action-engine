#!/usr/bin/env bash
# LIVE JitPack proof for ONE ref (a commit SHA in Phase 1; Phase 11 reuses it for the tag). Idempotent: re-running for the
# same ref just re-polls, so it is safe to call again after a tool timeout.
#   Usage: scripts/jitpack-live-probe.sh <commit-sha-or-tag>
#   Env:   EVIDENCE_FILE=<path>   append everything printed to this file
#          TIMEOUT_S=1500 POLL_S=20   build wait budget / poll interval
#          SKIP_CONSUMER=1            skip the clean-cache consumer resolution (used when testing the script on other repos)
#          REPO_OWNER=Ygaray REPO=voice-action-engine SERVED_GROUP=com.github.Ygaray.voice-action-engine
#          EXPECT_MODULES="voice-action-engine-core voice-action-engine-providers voice-action-engine-keystore"
#          FORBID_RE=sample           artifact-name regex that must NOT be published
#          CHECK_CORE_DEP=1           providers/keystore POMs must depend on voice-action-engine-core
#          EXPECT_MODULE_METADATA=1   set 0 when fallback F2 (POM-only) is active: .module files are then not expected
# Exit: 0 PASS | 2 JitPack build error | 3 timeout | 4 assertion failed | 5 consumer resolution failed
set -euo pipefail
REF="${1:?usage: jitpack-live-probe.sh <commit-sha-or-tag>}"
OWNER="${REPO_OWNER:-Ygaray}"; REPO="${REPO:-voice-action-engine}"
SERVED_GROUP="${SERVED_GROUP:-com.github.$OWNER.$REPO}"
MODULES="${EXPECT_MODULES:-voice-action-engine-core voice-action-engine-providers voice-action-engine-keystore}"
FORBID_RE="${FORBID_RE:-sample}"
CHECK_CORE_DEP="${CHECK_CORE_DEP:-1}"; EXPECT_MODULE_METADATA="${EXPECT_MODULE_METADATA:-1}"
TIMEOUT_S="${TIMEOUT_S:-1500}"; POLL_S="${POLL_S:-20}"
GROUP_PATH="com/github/$OWNER/$REPO"
API="https://jitpack.io/api/builds/com.github.$OWNER/$REPO/$REF"
WORK="$(mktemp -d)"; LOG="$WORK/build.log"
say() { if [ -n "${EVIDENCE_FILE:-}" ]; then printf '%s\n' "$*" | tee -a "$EVIDENCE_FILE"; else printf '%s\n' "$*"; fi; }
fail() { say "LIVE PROBE FAIL ($REF): $1"; exit "${2:-4}"; }
first="${MODULES%% *}"

say "== JitPack live probe  ref=$REF  repo=$OWNER/$REPO  $(date -u +%FT%TZ)"
# 1. The first request for a ref triggers its build (404 while building is expected).
curl -s -o /dev/null -m 60 "https://jitpack.io/$GROUP_PATH/$first/$REF/$first-$REF.pom" || true
# 2. Poll the build API: none/building -> keep waiting, ok -> continue, error -> fail.
deadline=$(( $(date +%s) + TIMEOUT_S )); status=""
while :; do
  # A transient curl failure (timeout, reset) must not kill a 25-minute poll: treat it as "unparsed" and keep waiting.
  json="$(curl -s -m 30 "$API" || true)"
  status="$(jq -r '.status // "unparsed"' <<<"$json" 2>/dev/null || echo unparsed)"
  [ -n "$status" ] || status=unparsed
  say "   status=$status"
  case "$status" in
    ok) break ;;
    error) say "   build.log tail:"
           { curl -s -m 60 "https://jitpack.io/$GROUP_PATH/$REF/build.log" || true; } | tail -60 | while IFS= read -r l; do say "   log: $l"; done
           fail "JitPack reports status=error (log tail above; a failed ref stays cached: fix, then push a NEW commit)" 2 ;;
  esac
  [ "$(date +%s)" -lt "$deadline" ] || fail "no 'ok' within ${TIMEOUT_S}s (last status=$status)" 3
  if [ "$status" = "none" ]; then curl -s -o /dev/null -m 60 "https://jitpack.io/$GROUP_PATH/$first/$REF/$first-$REF.pom" || true; fi
  sleep "$POLL_S"
done
say "   api: $(jq -c '{version,status,commit,isTag,modules}' <<<"$json")"
# 3. Published module set must match exactly (and must never include :sample).
# Negative guards match against a captured string, never `producer | grep -q`: under pipefail, grep -q exiting early can
# SIGPIPE the producer (status 141) and make a forbidden-artifact guard read "no match".
mods="$(jq -r '.modules[]?' <<<"$json")"
got="$(sort <<<"$mods" | tr '\n' ' ')"
want="$(tr ' ' '\n' <<<"$MODULES" | sort | tr '\n' ' ')"
[ "$got" = "$want" ] || fail "api modules [$got] != expected [$want]"
if grep -Eiq "$FORBID_RE" <<<"$mods"; then fail "api modules include a forbidden artifact ($FORBID_RE)"; fi
# 4. Build log: install command, discovered artifacts, served coordinates.
curl -s -m 120 "https://jitpack.io/$GROUP_PATH/$REF/build.log" > "$LOG" || fail "could not fetch build.log"
[ -s "$LOG" ] || fail "empty build.log"
# Each listing is captured first: a grep with no match must produce an explicit fail (or be tolerated), not a silent
# pipefail abort outside the documented exit-code set.
install_lines="$(grep -A1 'Running install command' "$LOG" || true)"
[ -n "$install_lines" ] || fail "build.log has no 'Running install command' line"
while IFS= read -r l; do say "   log: $l"; done <<<"$install_lines"
if grep -q ':sample' <<<"$install_lines"; then fail "install command names :sample"; fi
found_lines="$(grep 'Found artifact:' "$LOG" || true)"
while IFS= read -r l; do if [ -n "$l" ]; then say "   log: $l"; fi; done <<<"$found_lines"
artifact_block="$(awk '/Build artifacts:/{f=1;next} /^Files:/{f=0} f' "$LOG")"
for m in $MODULES; do
  grep -Eq "Found artifact: .*:$m:" <<<"$found_lines" || fail "build.log has no 'Found artifact' line for $m"
  grep -q "^$SERVED_GROUP:$m:" <<<"$artifact_block" || fail "build.log 'Build artifacts' block lacks $SERVED_GROUP:$m"
done
if grep -Eiq "$FORBID_RE" <<<"$found_lines"; then fail "build.log found a forbidden artifact ($FORBID_RE)"; fi
say "   served coordinates:"
while IFS= read -r l; do if [ -n "$l" ]; then say "     $l"; fi; done <<<"$artifact_block"
# 5. Served metadata for every module.
for m in $MODULES; do
  base="https://jitpack.io/$GROUP_PATH/$m/$REF/$m-$REF"
  code="$(curl -s -o "$WORK/$m.pom" -m 60 -w '%{http_code}' "$base.pom")" || code=000; [ "$code" = 200 ] || fail "$m.pom -> HTTP $code"
  if [ "$EXPECT_MODULE_METADATA" = 1 ]; then
    code="$(curl -s -o "$WORK/$m.module" -m 60 -w '%{http_code}' "$base.module")" || code=000; [ "$code" = 200 ] || fail "$m.module -> HTTP $code"
  else : > "$WORK/$m.module"; fi
  if grep -Eiq 'test-?fixtures' "$WORK/$m.pom" "$WORK/$m.module"; then fail "$m metadata mentions testFixtures"; fi
  say "   $m: pom 200$( [ "$EXPECT_MODULE_METADATA" = 1 ] && echo ', module 200' ) $(grep -o '<packaging>[a-z]*</packaging>' "$WORK/$m.pom" || echo '<packaging>jar(default)</packaging>')"
  case "$m" in *-providers|*-keystore)
    if [ "$CHECK_CORE_DEP" = 1 ]; then
      grep -q '<artifactId>voice-action-engine-core</artifactId>' "$WORK/$m.pom" || fail "$m.pom does not depend on voice-action-engine-core"
      grep -q "<groupId>$SERVED_GROUP</groupId>" "$WORK/$m.pom" || fail "$m.pom core dependency not under $SERVED_GROUP"
      say "   $m -> core dependency version line: $(grep -A1 '<artifactId>voice-action-engine-core</artifactId>' "$WORK/$m.pom" | grep '<version>' | tr -d ' ')"
    fi;;
  esac
done
# 6. The synthesized aggregator POM lists the modules and never :sample.
agg="https://jitpack.io/$GROUP_PATH/$REF/$REPO-$REF.pom"
code="$(curl -s -o "$WORK/agg.pom" -m 60 -w '%{http_code}' "$agg")" || code=000; [ "$code" = 200 ] || fail "aggregator pom -> HTTP $code ($agg)"
for m in $MODULES; do grep -q "<artifactId>$m</artifactId>" "$WORK/agg.pom" || fail "aggregator pom lacks $m"; done
agg_ids="$(grep '<artifactId>' "$WORK/agg.pom" || true)"
if grep -Eiq "$FORBID_RE" <<<"$agg_ids"; then fail "aggregator pom lists a forbidden artifact ($FORBID_RE)"; fi
say "   aggregator pom: 200, lists all expected modules, no forbidden artifact"
# 7. Clean-cache consumer resolution: jar->jar (:providers -> :core) and AAR->jar (:keystore -> :core).
if [ "${SKIP_CONSUMER:-0}" != 1 ]; then
  HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  say "   running consumer probe (empty Gradle cache)..."
  if ! VERSION="$REF" GROUP="$SERVED_GROUP" REPO_URL="https://jitpack.io" "$HERE/jitpack-consumer-probe.sh" > "$WORK/consumer.out" 2>&1; then
    tail -60 "$WORK/consumer.out" | while IFS= read -r l; do say "$l"; done
    fail "consumer resolution from jitpack.io failed" 5
  fi
  grep -E 'voice-action-engine|PROBE OK' "$WORK/consumer.out" | while IFS= read -r l; do say "   consumer: $l"; done
fi
say "LIVE PROBE PASS ref=$REF  (workdir=$WORK)"
