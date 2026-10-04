#!/usr/bin/env bash
# Mechanical VER-04 gate over the doc set an integrating agent receives: README.md, INTEGRATION.md, API.md (plus the
# ECOSYSTEM.md hygiene rules). Every checklist item is a grep, every Kotlin snippet is compared byte for byte with a
# region of the compiled, executed DocSnippetsTest, and API.md must name every public top-level type, found at run time.
#   C01 per-module coordinates in README and INTEGRATION     C13 keystore cause codes, re-enter vs transient retry
#   C02 no aggregator coordinate in any doc                   C14 uncached and unsupported combos
#   C03 coordinate lines end in :<version>, version explained C15 ExecutedAction kinds, applied, mutating, held bytes
#   C04 JitPack repository block                              C16 "never log"
#   C05 INTERNET permission                                   C17 OkHttp 4.12 floor, the app keeps its own OkHttp
#   C06 every marked block equals its test region; no unmarked kotlin fence
#   C07 regions are all used; the ten required regions exist; README uses minimal-pipeline
#   C08 every seam in INTEGRATION and API                     C18 own scripted AiProvider; fakes not published
#   C09 both gate modes                                       C19 the :sample pointer; every sample/ path exists
#   C10 open and closed taxonomies, an else branch            C20 API.md names every public top-level type
#   C11 partial rendering                                     C21 domain-free wording
#   C12 clarification and follow-up                           C22 README links INTEGRATION.md and API.md
#   C23 no concrete v1 coordinate while the tag does not exist
#   C24 README names the version to pin once, between the pin-version markers, as vX.Y.Z
#   C25 no private local path (~/..., /home/...) in the public docs
# Usage: scripts/verify-docs-coverage.sh [--only C01,C02,...]
# Prints every failure as "DOC COVERAGE FAIL: <id>: <detail>" (exit 1) or "DOC COVERAGE OK checks=<n> types=<n>".
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

README=README.md
INTEGRATION=INTEGRATION.md
API=API.md
ECOSYSTEM=ECOSYSTEM.md
SNIPPETS=sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
REQUIRED_REGIONS="minimal-pipeline scripted-provider register-providers agentic-tier gate-suspend gate-defer render-outcome clarification-follow-up keystore-wiring telemetry"
COORD_PREFIX='com.github.Ygaray.voice-action-engine:voice-action-engine-'

ONLY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --only) [ $# -ge 2 ] || { echo "DOC COVERAGE FAIL: usage: --only needs a list" >&2; exit 1; }; ONLY=",$2,"; shift 2 ;;
    *) echo "DOC COVERAGE FAIL: usage: unknown argument $1" >&2; exit 1 ;;
  esac
done

failures=()
checks=0
types=0
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { failures+=("DOC COVERAGE FAIL: $1: $2"); }
selected() { [ -z "$ONLY" ] || case "$ONLY" in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
exists() { [ -f "$1" ] || { fail "$CUR" "$1 is missing"; return 1; }; }
# need <file> <needle>...: fixed-string, case-sensitive.
need() { local f="$1"; shift; exists "$f" || return 0; local n; for n in "$@"; do grep -qF -- "$n" "$f" || fail "$CUR" "$f lacks '$n'"; done; }
# needi: as need, ignoring case.
needi() { local f="$1"; shift; exists "$f" || return 0; local n; for n in "$@"; do grep -qiF -- "$n" "$f" || fail "$CUR" "$f lacks '$n' (any case)"; done; }

# region <name>: the lines of the named region in the snippet test, with the common leading whitespace removed.
region() {
  awk -v n="$1" '
    $0 ~ "^[ \t]*// doc-snippet:start " n "[ \t]*$" { on = 1; found = 1; next }
    $0 ~ "^[ \t]*// doc-snippet:end " n "[ \t]*$" { on = 0; next }
    on { lines[++c] = $0 }
    END {
      if (!found) exit 3
      min = -1
      for (i = 1; i <= c; i++) {
        if (lines[i] ~ /^[ \t]*$/) continue
        match(lines[i], /^[ \t]*/)
        if (min < 0 || RLENGTH < min) min = RLENGTH
      }
      if (min < 0) min = 0
      for (i = 1; i <= c; i++) print (lines[i] ~ /^[ \t]*$/ ? "" : substr(lines[i], min + 1))
    }' "$SNIPPETS"
}

# blocks <doc>: split a doc into WORK/<doc>.<n>.name and .body for each marked kotlin fence; WORK/<doc>.bad lists faults.
blocks() {
  local doc="$1"
  [ -f "$doc" ] || return 0
  rm -f "$WORK/$doc".*
  awk -v out="$WORK" -v doc="$doc" '
    /^<!-- doc-snippet: [A-Za-z0-9-]+ -->[ \t]*$/ { name = $3; pending = 1; next }
    pending && /^[ \t]*$/ { next }
    pending {
      if ($0 == "```kotlin") {
        inblock = 1; n++; base = out "/" doc "." n
        print name > (base ".name"); close(base ".name")
        printf "" > (base ".body"); close(base ".body")
      } else {
        print doc ": marker " name " is not followed by a kotlin fence" >> (out "/" doc ".bad")
      }
      pending = 0; next
    }
    inblock { if ($0 == "```") { inblock = 0; next } print >> (base ".body"); next }
    /^```kotlin/ { print doc ": line " NR " is a kotlin fence without a doc-snippet marker" >> (out "/" doc ".bad") }
    END { if (pending) print doc ": a doc-snippet marker at the end has no fence" >> (out "/" doc ".bad") }
  ' "$doc"
}

public_types() {
  grep -rhE '^public ' core/src/main/kotlin providers/src/main/kotlin keystore/src/main/kotlin 2>/dev/null \
    | grep -E '^public +((sealed|abstract|open|data|enum|fun|value|inline|annotation|final) +)*(class|interface|object)\b' \
    | sed -E 's/^public +((sealed|abstract|open|data|enum|fun|value|inline|annotation|final) +)*(class|interface|object) +([A-Za-z0-9_]+).*/\4/' \
    | sort -u
}

run() {
  local id="$1"
  selected "$id" || return 0
  CUR="$id"
  checks=$((checks + 1))
  "check_$id"
}

check_C01() {
  local f a
  for f in "$README" "$INTEGRATION"; do
    for a in core providers keystore; do need "$f" "${COORD_PREFIX}${a}:"; done
  done
}

check_C02() {
  local f
  for f in "$README" "$INTEGRATION" "$API" "$ECOSYSTEM"; do
    [ -f "$f" ] || continue
    if grep -Eq 'com\.github\.Ygaray:voice-action-engine([^-]|$)' "$f"; then
      fail C02 "$f contains the retired aggregator coordinate"
    fi
  done
}

check_C03() {
  local f line
  for f in "$README" "$INTEGRATION"; do
    [ -f "$f" ] || continue
    while IFS= read -r line; do
      case "$line" in
        *"${COORD_PREFIX}"*)
          if ! printf '%s' "$line" | grep -Eq 'voice-action-engine-(core|providers|keystore):<version>'; then
            fail C03 "$f: a coordinate line does not end in :<version>: $line"
          fi ;;
      esac
    done < "$f"
    need "$f" "<version>" "immutable release tag" "commit SHA"
  done
}

check_C04() {
  local f
  for f in "$README" "$INTEGRATION"; do need "$f" 'maven { url = uri("https://jitpack.io") }'; done
}

check_C05() { need "$INTEGRATION" "android.permission.INTERNET"; }

check_C06() {
  local doc bad body name want n=0
  [ -f "$SNIPPETS" ] || { fail C06 "$SNIPPETS is missing"; return; }
  for doc in "$README" "$INTEGRATION" "$API"; do
    blocks "$doc"
    if [ -f "$WORK/$doc.bad" ]; then
      while IFS= read -r bad; do fail C06 "$bad"; done < "$WORK/$doc.bad"
    fi
    for body in "$WORK/$doc".*.body; do
      [ -f "$body" ] || continue
      name="$(cat "${body%.body}.name")"
      n=$((n + 1))
      want="$WORK/want.$name"
      if ! region "$name" > "$want" 2>/dev/null; then
        fail C06 "$doc: no region '$name' in the snippet test"
        continue
      fi
      if ! cmp -s "$body" "$want"; then
        fail C06 "$doc: block '$name' differs from its region ($(diff "$body" "$want" | head -3 | tr '\n' ' '))"
      fi
      if [ ! -s "$body" ]; then fail C06 "$doc: block '$name' is empty"; fi
    done
  done
  [ "$n" -gt 0 ] || fail C06 "no doc-snippet block found in any doc"
}

check_C07() {
  local r doc used all="" used_in
  [ -f "$SNIPPETS" ] || { fail C07 "$SNIPPETS is missing"; return; }
  for r in $REQUIRED_REGIONS; do
    region "$r" >/dev/null 2>&1 || fail C07 "required region '$r' is missing from the snippet test"
  done
  all="$(grep -oE 'doc-snippet:start [A-Za-z0-9-]+' "$SNIPPETS" | sed 's/doc-snippet:start //')"
  for r in $all; do
    used=0
    for doc in "$README" "$INTEGRATION" "$API"; do
      [ -f "$doc" ] && grep -qE "^<!-- doc-snippet: $r -->[[:space:]]*$" "$doc" && used=1
    done
    [ "$used" = 1 ] || fail C07 "region '$r' is not used by any doc"
  done
  used_in=0
  [ -f "$README" ] && grep -qE '^<!-- doc-snippet: minimal-pipeline -->' "$README" && used_in=1
  [ "$used_in" = 1 ] || fail C07 "README does not use the minimal-pipeline snippet"
}

check_C08() {
  local f
  for f in "$INTEGRATION" "$API"; do
    need "$f" ToolSpecProvider ToolExecutor OutcomeResolver UserTurnRenderer PreApplyGate CommitSink \
      ProviderSelectionSource CredentialSource TierPolicySource PipelineEventListener OnDeviceCapability "capabilities("
  done
}

check_C09() { need "$INTEGRATION" AwaitingConfirmGate pending "resolve(" "GateDecision.Hold" "commitHeld(" amended; }

check_C10() {
  local f body found=0
  for f in "$INTEGRATION" "$API"; do
    need "$f" FailureReason EscalationReason CredentialLookup KeyState ActionKind FinishedKind Resolution PipelineEvent \
      TraceCode StopReason ToolChoice AnthropicAttemptKind ChatCompletionsAttemptKind \
      CommandOutcome GateDecision RunTermination
  done
  needi "$INTEGRATION" exhaustive
  for doc in "$README" "$INTEGRATION" "$API"; do
    blocks "$doc"
    for body in "$WORK/$doc".*.body; do
      [ -f "$body" ] || continue
      grep -qF 'else ->' "$body" && found=1
    done
  done
  [ "$found" = 1 ] || fail C10 "no marked snippet contains an 'else ->' branch"
}

check_C11() { local f; for f in "$README" "$INTEGRATION"; do need "$f" partial "couldn't finish"; done; }

check_C12() { need "$INTEGRATION" terminalCall "asClarification()" ClarificationOption parentRunId UserTurnRenderer pressable; }

check_C13() {
  need "$INTEGRATION" key_missing keystore_unavailable decrypt_failed stored_value_malformed storage_unreadable \
    "re-enter" "retry"
}

check_C14() { need "$INTEGRATION" uncached "anthropic/" "gpt-6-astra" ModelUnsupported forced; }

check_C15() {
  local f
  for f in "$INTEGRATION" "$API"; do
    need "$f" "ActionKind.COMMITTED" "ActionKind.HELD" "ActionKind.PREVIEW" "ActionKind.IS_ERROR" applied mutating \
      held_for_confirmation
  done
}

check_C16() { needi "$INTEGRATION" "never log"; }

check_C17() { local f; for f in "$README" "$INTEGRATION"; do need "$f" "4.12" "own OkHttp"; done; }

check_C18() {
  need "$INTEGRATION" scripted AiProvider
  if [ -f "$INTEGRATION" ] && ! grep -E 'FakeAiProvider' "$INTEGRATION" | grep -q 'not published'; then
    fail C18 "$INTEGRATION lacks a statement that FakeAiProvider is not published"
  fi
}

check_C19() {
  local f path
  need "$README" 'sample/'
  need "$INTEGRATION" 'sample/'
  for f in "$README" "$INTEGRATION" "$API"; do
    [ -f "$f" ] || continue
    while IFS= read -r path; do
      path="${path#\`}"; path="${path%\`}"
      [ -e "$path" ] || fail C19 "$f points at $path, which does not exist"
    done < <(grep -oE '`sample/[^` ]*`' "$f" | sort -u)
  done
}

check_C20() {
  local name list
  exists "$API" || return 0
  list="$(public_types)"
  types="$(printf '%s\n' "$list" | grep -c .)"
  [ "$types" -gt 0 ] || fail C20 "no public top-level type found in the main sources (vacuous)"
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    grep -qF -- "\`$name\`" "$API" || fail C20 "$API does not name the public type \`$name\`"
  done <<< "$list"
}

check_C21() {
  local f
  for f in "$README" "$INTEGRATION" "$API"; do
    [ -f "$f" ] || continue
    if grep -Eiq '\b(food|cards?)\b' "$f"; then fail C21 "$f contains domain wording (food or card)"; fi
    if grep -Eq '\b[a-z]+_(notes?|cards?|foods?|meals?)\b' "$f"; then fail C21 "$f names a note, card or food shaped tool"; fi
  done
}

check_C22() { need "$README" "(INTEGRATION.md)" "(API.md)"; }

check_C23() {
  local f tag
  tag="$(git tag --list v1.0.0 2>/dev/null)"
  [ -z "$tag" ] || return 0
  for f in "$README" "$INTEGRATION" "$API" "$ECOSYSTEM"; do
    [ -f "$f" ] || continue
    if grep -Eq ':v1\.' "$f"; then fail C23 "$f contains a :v1. coordinate while the v1.0.0 tag does not exist"; fi
  done
}

# The pinned version lives in one replaceable spot: README, between <!-- pin-version:begin --> and <!-- pin-version:end -->.
check_C24() {
  local spot
  exists "$README" || return 0
  spot="$(sed -n 's/.*<!-- pin-version:begin -->\(.*\)<!-- pin-version:end -->.*/\1/p' "$README")"
  if ! printf '%s' "$spot" | grep -Eq '^`v[0-9]+\.[0-9]+\.[0-9]+`$'; then
    fail C24 "$README does not name the version to pin as \`vX.Y.Z\` between the pin-version markers (found: '$spot')"
  fi
}

check_C25() {
  local f
  for f in "$README" "$INTEGRATION" "$API" "$ECOSYSTEM"; do
    [ -f "$f" ] || continue
    if grep -nE '(^|[^A-Za-z0-9_.])(~/(Projects|\.claude|\.gsd)|/home/[a-z]|/Users/[A-Za-z])' "$f" > "$WORK/paths.txt"; then
      fail C25 "$f contains a private local path: $(head -1 "$WORK/paths.txt" | cut -c1-120)"
    fi
  done
}

for id in C01 C02 C03 C04 C05 C06 C07 C08 C09 C10 C11 C12 C13 C14 C15 C16 C17 C18 C19 C20 C21 C22 C23 C24 C25; do
  run "$id"
done

# types is only counted by C20; report it from a cheap recount when C20 was not selected.
if [ "$types" = 0 ]; then types="$(public_types | grep -c .)"; fi

if [ "${#failures[@]}" -gt 0 ]; then
  printf '%s\n' "${failures[@]}" >&2
  exit 1
fi
echo "DOC COVERAGE OK checks=$checks types=$types"
