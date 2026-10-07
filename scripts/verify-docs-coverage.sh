#!/usr/bin/env bash
# Mechanical VER-04 gate over the doc set an integrating agent receives: README.md, INTEGRATION.md, API.md (plus the
# ECOSYSTEM.md hygiene rules). Every checklist item is a grep, every Kotlin snippet is compared byte for byte with a
# region of a compiled, executed snippet test (the sample's DocSnippetsTest, and the voice adapter's own
# DocSnippetAdapterTest for the adapter snippet), and API.md must name every public top-level type, found at run time.
#   C01 every manifest module's coordinate in README+INTEGRATION C13 keystore cause codes, re-enter vs transient retry
#   C02 no aggregator coordinate in any doc                   C14 uncached and unsupported combos
#   C03 coordinate lines end in :<version>, version explained C15 ExecutedAction kinds, applied, mutating, held bytes
#   C04 JitPack repository block                              C16 "never log"
#   C05 INTERNET permission                                   C17 OkHttp 4.12 floor, the app keeps its own OkHttp
#   C06 every marked block equals its test region; no unmarked kotlin fence
#   C07 regions are all used; the required regions exist; README uses minimal-pipeline
#   C08 every seam in INTEGRATION and API                     C18 own scripted AiProvider; fakes not published
#   C09 both gate modes                                       C19 the :sample pointer; every sample/ path exists
#   C10 open and closed taxonomies, an else branch            C20 API.md names every public type and function
#   C11 partial rendering                                     C21 domain-free wording
#   C12 clarification and follow-up                           C22 README links INTEGRATION.md and API.md
#   C23 no concrete v1 coordinate while the tag does not exist
#   C24 README names the version to pin once, between the pin-version markers, as vX.Y.Z
#   C25 no private local path (~/..., /home/...) in the public docs
#   C26 grammar tier   C27 plan tier   C28 router and start-tier picker   C29 undo   C30 voice adapter
#   C31 RT-01 (API.md says the plan reference pattern is ASCII-only) and RT-04 (no old overload count, no one-argument
#       context form)   C32 the Phase 12 seams
# C20 reads the main sources of EVERY module in scripts/modules.list: each public top-level type and each public
# top-level function must be named in backticks in API.md, and a module that yields neither fails as vacuous.
# C03 module names come from scripts/modules.list (the one manifest of published modules).
# Usage: scripts/verify-docs-coverage.sh [--only C01,C02,...] [--selftest]
# Prints every failure as "DOC COVERAGE FAIL: <id>: <detail>" (exit 1) or "DOC COVERAGE OK checks=<n> types=<n>".
# --selftest plants violations in isolated copies of the doc set and requires each to go red naming its check; it prints
# "DOC COVERAGE SELFTEST OK plants=<n>" and never touches the real tree.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

# The module manifest. VAE_MODULES_FILE is set explicitly so the script also works in a copy that is not a git checkout.
VAE_MODULES_FILE="${VAE_MODULES_FILE:-$ROOT/scripts/modules.list}"
export VAE_MODULES_FILE
# shellcheck source=lib/modules.sh
. "$ROOT/scripts/lib/modules.sh"
MODULE_ALT="$(vae_modules | tr ' ' '|')"
[ -n "$MODULE_ALT" ] || { echo "DOC COVERAGE FAIL: scripts/modules.list lists no module" >&2; exit 1; }

README=README.md
INTEGRATION=INTEGRATION.md
API=API.md
ECOSYSTEM=ECOSYSTEM.md
SNIPPETS=sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
# Every file that holds doc-snippet regions, derived once: the sample's test, then the voice adapter's own snippet test
# (the adapter snippet is compiled where the adapter lives, so :sample needs no edge to :voice-adapter or :stt).
SNIPPET_FILES=("$SNIPPETS")
while IFS= read -r extra_snippets; do
  [ -n "$extra_snippets" ] && SNIPPET_FILES+=("$extra_snippets")
done < <(find voice-adapter/src/test -name DocSnippetAdapterTest.kt 2>/dev/null | sort)
REQUIRED_REGIONS="minimal-pipeline scripted-provider register-providers agentic-tier gate-suspend gate-defer render-outcome clarification-follow-up keystore-wiring keystore-fake telemetry grammar-tier plan-tier router-selector undo-wiring undo-bridge"
COORD_PREFIX='com.github.Ygaray.voice-action-engine:voice-action-engine-'
COORD_GROUP='com.github.Ygaray.voice-action-engine'

ONLY=""
SELFTEST=0
while [ $# -gt 0 ]; do
  case "$1" in
    --selftest) SELFTEST=1; shift ;;
    --only) [ $# -ge 2 ] || { echo "DOC COVERAGE FAIL: usage: --only needs a list" >&2; exit 1; }; ONLY=",$2,"; shift 2 ;;
    *) echo "DOC COVERAGE FAIL: usage: unknown argument $1" >&2; exit 1 ;;
  esac
done

failures=()
checks=0
types=0
WORK="$(mktemp -d)"
SELF=""
trap 'rm -rf "$WORK"; [ -z "$SELF" ] || rm -rf "$SELF"' EXIT

fail() { failures+=("DOC COVERAGE FAIL: $1: $2"); }
selected() { [ -z "$ONLY" ] || case "$ONLY" in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
exists() { [ -f "$1" ] || { fail "$CUR" "$1 is missing"; return 1; }; }
# need <file> <needle>...: fixed-string, case-sensitive.
need() { local f="$1"; shift; exists "$f" || return 0; local n; for n in "$@"; do grep -qF -- "$n" "$f" || fail "$CUR" "$f lacks '$n'"; done; }
# needi: as need, ignoring case.
needi() { local f="$1"; shift; exists "$f" || return 0; local n; for n in "$@"; do grep -qiF -- "$n" "$f" || fail "$CUR" "$f lacks '$n' (any case)"; done; }

# region <name>: the lines of the named region in the snippet tests, with the common leading whitespace removed.
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
    }' "${SNIPPET_FILES[@]}"
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

# MAIN_SOURCES <module>: the main Kotlin sources of a manifest module (the directory may not exist).
main_sources() { printf '%s/src/main/kotlin\n' "$1"; }

# public_types_of <module>: names of public top-level types (a public fun interface is a type).
public_types_of() {
  grep -rhE '^public ' "$(main_sources "$1")" 2>/dev/null \
    | grep -E '^public +((sealed|abstract|open|data|enum|fun|value|inline|annotation|final) +)*(class|interface|object)\b' \
    | sed -E 's/^public +((sealed|abstract|open|data|enum|fun|value|inline|annotation|final) +)*(class|interface|object) +([A-Za-z0-9_]+).*/\4/' \
    | sort -u
}

# public_funs_of <module>: names of public top-level functions at column 0, extension receivers allowed.
# A public fun interface is a type, not a function.
public_funs_of() {
  grep -rhE '^public +((inline|suspend|tailrec|operator|infix|external) +)*fun +' "$(main_sources "$1")" 2>/dev/null \
    | grep -vE '^public +fun +interface\b' \
    | sed -E 's/^public +((inline|suspend|tailrec|operator|infix|external) +)*fun +(<[^>]*> *)?//' \
    | sed -E 's/\(.*$//' \
    | sed -E 's/^.*\.//' \
    | grep -E '^[A-Za-z0-9_]+$' \
    | sort -u
}

public_types() {
  local m
  for m in $(vae_modules); do public_types_of "$m"; done | sort -u
}

run() {
  local id="$1"
  selected "$id" || return 0
  CUR="$id"
  checks=$((checks + 1))
  "check_$id"
}

# Every module in scripts/modules.list needs a per-module coordinate in both docs, so a new module cannot go unnamed.
check_C01() {
  local f a
  for f in "$README" "$INTEGRATION"; do
    for a in $(vae_modules); do need "$f" "${COORD_GROUP}:$(vae_module_field "$a" artifactId):"; done
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
          if ! printf '%s' "$line" | grep -Eq "voice-action-engine-(${MODULE_ALT}):<version>"; then
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
  all="$(grep -ohE 'doc-snippet:start [A-Za-z0-9-]+' "${SNIPPET_FILES[@]}" | sed 's/doc-snippet:start //')"
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
  local name list m tlist flist
  exists "$API" || return 0
  list="$(public_types)"
  types="$(printf '%s\n' "$list" | grep -c .)"
  for m in $(vae_modules); do
    tlist="$(public_types_of "$m")"
    flist="$(public_funs_of "$m")"
    if [ -z "$tlist" ] && [ -z "$flist" ]; then
      fail C20 "module $m has no public top-level type or function in its main sources (vacuous)"
      continue
    fi
    while IFS= read -r name; do
      [ -n "$name" ] || continue
      grep -qF -- "\`$name\`" "$API" || fail C20 "$API does not name the public type \`$name\` ($m)"
    done <<< "$tlist"
    while IFS= read -r name; do
      [ -n "$name" ] || continue
      grep -qF -- "\`$name\`" "$API" || fail C20 "$API does not name the public function \`$name\` ($m)"
    done <<< "$flist"
  done
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

# C23: the tag the README tells integrators to pin must exist. The docs legitimately announce a release before its tag is cut
# (Phase 19 ships the docs for v1.1.0, Phase 20 tags it), so until then a missing tag is a loud NOTE, not a failure: the
# PRE-TAG ALLOWANCE. VAE_DOCS_REQUIRE_PINNED_TAG=1 turns the allowance off and a missing tag fails C23; Phase 20's release cut
# runs the gate with it set, and the allowance is dropped (default flipped to 1) once v1.1.0 is tagged.
REQUIRE_PINNED_TAG="${VAE_DOCS_REQUIRE_PINNED_TAG:-0}"
check_C23() {
  local pin
  pin="$(sed -n 's/.*<!-- pin-version:begin -->`\(v[0-9][0-9.]*\)`<!-- pin-version:end -->.*/\1/p' "$README" 2>/dev/null | head -1)"
  [ -n "$pin" ] || return 0   # C24 reports a missing or malformed marker
  git rev-parse -q --verify "refs/tags/$pin" >/dev/null 2>&1 && return 0
  if [ "$REQUIRE_PINNED_TAG" = 1 ]; then
    fail C23 "$README pins $pin but that tag does not exist"
  else
    echo "DOC COVERAGE NOTE: C23: $README pins $pin but that tag does not exist yet (pre-tag allowance; VAE_DOCS_REQUIRE_PINNED_TAG=1 makes this a failure)" >&2
  fi
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

check_C26() {
  need "$INTEGRATION" GrammarPack LocalGrammarStrategy matchedLanguage cappedByPolicy
  need "$API" GrammarPack LocalGrammarStrategy matchedLanguage cappedByPolicy grammar_ambiguous
}

check_C27() {
  need "$INTEGRATION" PlanThenExecuteStrategy submit_plan needs_lookup remainingStepIds
  need "$API" PlanThenExecuteStrategy submit_plan needs_lookup remainingStepIds plan_binding_unresolved targetIds
}

check_C28() {
  need "$INTEGRATION" TierSelector.Router tierDescriptions start_tier_router router_fallback
  need "$API" TierSelector.Router tierDescriptions start_tier_router router_fallback PickContext StartTierPicker
}

check_C29() {
  need "$INTEGRATION" UndoJournal undoAll EntityAdapter UndoResult compositeSink "Undo all (N)"
  need "$API" UndoJournal undoAll EntityAdapter UndoResult compositeSink "Undo all (N)" UndoReason "UndoResult.code"
}

check_C30() {
  need "$INTEGRATION" toCommandInput commandInputOf normalizeSttLanguageLabel "or newer" "compile-only"
  need "$API" toCommandInput commandInputOf normalizeSttLanguageLabel
}

# RT-01: the plan reference pattern's whitespace is ASCII-only, and the docs must say so.
# RT-04: the context-only overloads were dropped, so the docs may not count three overloads or show a lone context argument.
check_C31() {
  local f
  need "$API" ASCII
  [ -f "$API" ] && grep -qi 'Three overloads' "$API" && fail C31 "$API still carries the old overload count (Three overloads)"
  for f in "$INTEGRATION" "$API"; do
    [ -f "$f" ] || continue
    if grep -Eq 'toCommandInput\([^,()]+\)' "$f"; then
      fail C31 "$f shows toCommandInput with a lone argument (the one-argument context form was dropped)"
    fi
  done
  return 0
}

check_C32() { need "$API" onFailed ReasoningMode providerCallId carryIn; }

# --selftest: copy only what the gate reads (the four docs, scripts/, every snippet test file, the main sources of every
# manifest module, and the sample paths the docs point at) into a temp directory, require the copied gate to be green
# there, then plant one violation per fresh copy and require a failure line naming the expected check. The planning files
# are never copied, and git is not needed in the copy (C23 asks git for a tag, so it is left out of the copy runs and exercised by selftest_c23 in a throwaway git repo).
SELFTEST_CHECKS=""
for n in $(seq 1 32); do
  [ "$n" -eq 23 ] || SELFTEST_CHECKS="${SELFTEST_CHECKS:+$SELFTEST_CHECKS,}$(printf 'C%02d' "$n")"
done

# make_copy <dir>: populate an isolated copy of the files the gate reads.
make_copy() {
  local dir="$1" f m path
  mkdir -p "$dir"
  cp "$README" "$INTEGRATION" "$API" "$ECOSYSTEM" "$dir/"
  cp -r scripts "$dir/scripts"
  for f in "${SNIPPET_FILES[@]}"; do cp --parents "$f" "$dir/"; done
  for m in $(vae_modules); do
    [ -d "$(main_sources "$m")" ] && cp -r --parents "$(main_sources "$m")" "$dir/"
  done
  while IFS= read -r path; do
    path="${path#\`}"; path="${path%\`}"
    [ -e "$path" ] && cp -r --parents "$path" "$dir/"
  done < <(grep -ohE '`sample/[^` ]*`' "$README" "$INTEGRATION" "$API" 2>/dev/null | sort -u)
  return 0
}

# run_copy <dir>: the copied gate's combined output; VAE_MODULES_FILE must not leak in from this run.
run_copy() {
  (cd "$1" && env -u VAE_MODULES_FILE -u BASH_ENV bash scripts/verify-docs-coverage.sh --only "$SELFTEST_CHECKS" 2>&1) || true
}

# apply_plant <name> <dir>: the one violation of this plant.
apply_plant() {
  local name="$1" d="$2"
  case "$name" in
    undo-coordinate) sed -i '/voice-action-engine-undo:/d' "$d/README.md" ;;
    api-type) sed -i 's/`UndoGroup`/UndoGroup/g' "$d/API.md" ;;
    api-function) sed -i 's/`toCommandInput`/toCommandInput/g' "$d/API.md" ;;
    old-overloads) printf '\nThree overloads exist for the mapping.\n' >> "$d/API.md" ;;
    lone-context) printf '\nCall segment.toCommandInput(context) for the run context.\n' >> "$d/INTEGRATION.md" ;;
    api-ascii-note) sed -i 's/ASCII/plain/g' "$d/API.md" ;;
    adapter-block) sed -i '/^<!-- doc-snippet: adapter-wiring -->/,/^```$/ s/segment/segmnt/' "$d/INTEGRATION.md" ;;
    adapter-test-missing) find "$d/voice-adapter/src/test" -name DocSnippetAdapterTest.kt -delete ;;
    empty-manifest) printf '# no module rows\n' > "$d/scripts/modules.list" ;;
    empty-module)
      printf 'ghost jar voice-action-engine-ghost ghost no\n' >> "$d/scripts/modules.list"
      mkdir -p "$d/ghost/src/main/kotlin" ;;
    *) echo "unknown plant $name" >&2; return 1 ;;
  esac
}

# selftest_c23: C23 needs git, so it runs in its own throwaway repository: strict + no tag is red, the pre-tag allowance is
# green but says so, strict + the tag is green.
selftest_c23() {
  local d="$SELF/c23" out pin
  make_copy "$d"
  pin="$(sed -n 's/.*<!-- pin-version:begin -->`\(v[0-9][0-9.]*\)`<!-- pin-version:end -->.*/\1/p' "$d/README.md" | head -1)"
  [ -n "$pin" ] || { echo "DOC COVERAGE SELFTEST FAIL: c23: no pin in the copy"; exit 1; }
  git -C "$d" init -q >/dev/null 2>&1 && git -C "$d" add -A >/dev/null 2>&1 \
    && git -C "$d" -c user.name=selftest -c user.email=selftest@example.invalid -c commit.gpgsign=false commit -q -m copy >/dev/null 2>&1 \
    || { echo "DOC COVERAGE SELFTEST FAIL: c23: could not build the throwaway repository"; exit 1; }
  out="$(cd "$d" && env -u VAE_MODULES_FILE -u BASH_ENV VAE_DOCS_REQUIRE_PINNED_TAG=1 bash scripts/verify-docs-coverage.sh --only C23 2>&1)" && {
    echo "DOC COVERAGE SELFTEST FAIL: c23-strict-no-tag: stayed green (the check is vacuous)"; exit 1; }
  printf '%s\n' "$out" | grep -qF -- "FAIL: C23: README.md pins $pin but that tag does not exist" \
    || { echo "DOC COVERAGE SELFTEST FAIL: c23-strict-no-tag: red without the expected line (got: $(printf '%s' "$out" | head -2 | tr '\n' ' '))"; exit 1; }
  out="$(cd "$d" && env -u VAE_MODULES_FILE -u BASH_ENV -u VAE_DOCS_REQUIRE_PINNED_TAG bash scripts/verify-docs-coverage.sh --only C23 2>&1)" \
    || { echo "DOC COVERAGE SELFTEST FAIL: c23-allowance: the pre-tag allowance went red"; exit 1; }
  printf '%s\n' "$out" | grep -qF -- "pre-tag allowance" \
    || { echo "DOC COVERAGE SELFTEST FAIL: c23-allowance: green but silent about the missing tag"; exit 1; }
  git -C "$d" -c user.name=selftest -c user.email=selftest@example.invalid tag "$pin" >/dev/null 2>&1
  out="$(cd "$d" && env -u VAE_MODULES_FILE -u BASH_ENV VAE_DOCS_REQUIRE_PINNED_TAG=1 bash scripts/verify-docs-coverage.sh --only C23 2>&1)" \
    || { echo "DOC COVERAGE SELFTEST FAIL: c23-strict-tagged: red although the tag exists"; exit 1; }
  plants=$((plants + 3))
}

selftest() {
  local before after out plant want why plants=0 bt='`'
  local table=(
    "undo-coordinate|FAIL: C01: README.md lacks 'com.github.Ygaray.voice-action-engine:voice-action-engine-undo:'"
    "api-type|FAIL: C20: API.md does not name the public type ${bt}UndoGroup${bt}"
    "api-function|FAIL: C20: API.md does not name the public function ${bt}toCommandInput${bt}"
    "old-overloads|FAIL: C31: API.md still carries the old overload count"
    "lone-context|FAIL: C31: INTEGRATION.md shows toCommandInput with a lone argument"
    "api-ascii-note|FAIL: C31: API.md lacks 'ASCII'"
    "adapter-block|FAIL: C06: INTEGRATION.md: block 'adapter-wiring' differs from its region"
    "adapter-test-missing|FAIL: C06: INTEGRATION.md: no region 'adapter-wiring'"
    "empty-manifest|lists no module"
    "empty-module|FAIL: C20: module ghost has no public top-level type or function"
  )
  SELF="$(mktemp -d)"
  before="$(git status --porcelain 2>/dev/null || true)"

  make_copy "$SELF/base"
  out="$(run_copy "$SELF/base")"
  case "$out" in
    "DOC COVERAGE OK checks="*) ;;
    *) echo "DOC COVERAGE SELFTEST FAIL: baseline: the unplanted copy is not green: $(printf '%s' "$out" | head -3 | tr '\n' ' ')"; exit 1 ;;
  esac

  for entry in "${table[@]}"; do
    plant="${entry%%|*}"; want="${entry#*|}"
    make_copy "$SELF/$plant"
    if ! apply_plant "$plant" "$SELF/$plant"; then
      echo "DOC COVERAGE SELFTEST FAIL: $plant: the plant could not be applied"; exit 1
    fi
    out="$(run_copy "$SELF/$plant")"
    case "$out" in
      "DOC COVERAGE OK"*) why="the planted copy stayed green (the check is vacuous)" ;;
      *) if printf '%s\n' "$out" | grep -qF -- "$want"; then why=""; else
           why="red, but without the expected line '$want' (got: $(printf '%s' "$out" | head -2 | tr '\n' ' '))"; fi ;;
    esac
    if [ -n "$why" ]; then echo "DOC COVERAGE SELFTEST FAIL: $plant: $why"; exit 1; fi
    plants=$((plants + 1))
  done

  selftest_c23

  after="$(git status --porcelain 2>/dev/null || true)"
  if [ "$before" != "$after" ]; then
    echo "DOC COVERAGE SELFTEST FAIL: real tree: git status changed during the selftest"; exit 1
  fi
  echo "DOC COVERAGE SELFTEST OK plants=$plants"
}

if [ "$SELFTEST" = 1 ]; then
  selftest
  exit 0
fi

for id in C01 C02 C03 C04 C05 C06 C07 C08 C09 C10 C11 C12 C13 C14 C15 C16 C17 C18 C19 C20 C21 C22 C23 C24 C25 C26 C27 C28 C29 C30 C31 C32; do
  run "$id"
done

# types is only counted by C20; report it from a cheap recount when C20 was not selected.
if [ "$types" = 0 ]; then types="$(public_types | grep -c .)"; fi

if [ "${#failures[@]}" -gt 0 ]; then
  printf '%s\n' "${failures[@]}" >&2
  exit 1
fi
echo "DOC COVERAGE OK checks=$checks types=$types"
