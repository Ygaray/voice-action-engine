#!/usr/bin/env bash
# Public-surface review over the REAL Metalava dump, produced in an ISOLATED COPY of the working tree so the real tree
# never receives an api.txt (dumps are committed only at the v1.0.0 cut; a stray dump would arm the compat gate early).
#   fails when core's dump:
#     a. is missing, lacks the "Signature format" header, or lacks the core package (non-vacuity)
#     b. has a sealed type outside {StrategyOutcome, CommandOutcome, RunTermination, GateDecision, ToolStep, Message,
#        AssistantPart}, matched by fully qualified name (with --expect-sealed-complete the sealed set must equal all
#        seven)
#     c. declares a copy( or componentN( method (data-shaped class)
#     d. declares an enum
#     e. has a public static field other than INSTANCE or Companion
# Usage: scripts/review-api-surface.sh [--expect-sealed-complete] [--out <path>]
# Prints "API SURFACE OK sealed=<list|none> classes=<n>" or "API SURFACE FAIL: <reason>" (exit 1).
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
PKG="io.github.ygaray.voiceactionengine.core"
ALLOWED_SEALED="$PKG.strategy.StrategyOutcome $PKG.pipeline.CommandOutcome $PKG.commit.RunTermination $PKG.commit.GateDecision $PKG.commit.ToolStep $PKG.transcript.Message $PKG.transcript.AssistantPart"
EXPECT_COMPLETE=0
OUT=""
fail() { echo "API SURFACE FAIL: $1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --expect-sealed-complete) EXPECT_COMPLETE=1; shift ;;
    --out) [ $# -ge 2 ] || fail "--out needs a path"; OUT="$2"; shift 2 ;;
    *) fail "unknown argument $1" ;;
  esac
done

if [ -n "$OUT" ]; then
  OUT_ABS="$(realpath -m "$OUT")"
  case "$(basename "$OUT_ABS")" in *api.txt) fail "--out must not end in api.txt (the hygiene gate forbids api.txt before the cut)" ;; esac
  for m in core providers keystore; do
    case "$OUT_ABS" in "$ROOT/$m"/*|"$ROOT/$m") fail "--out must not lie under $m/" ;; esac
  done
fi

WORK="$(mktemp -d)"; COPY="$WORK/repo"; mkdir -p "$COPY"
# The copy is every tracked and untracked non-ignored file (git ls-files -co --exclude-standard) that exists on disk, so
# ignored build outputs are not copied. It is removed on exit unless KEEP_WORK=1 (debugging).
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
# Orchestrator/graph bookkeeping under .planning/ and graphify-out/ changes independently of this script: not compared.
tree_status() { git -C "$ROOT" status --porcelain -- . ':!.planning' ':!graphify-out'; }
before_status="$(tree_status)"
# Files that are tracked but deleted in the working tree cannot be copied; every other listed file must be, so a file the
# tar cannot read fails the run instead of silently producing a dump from an incomplete tree.
(cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
  | while IFS= read -r -d '' f; do if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\0' "$f"; fi; done >"$WORK/files.list")
expected_files="$(tr -cd '\0' <"$WORK/files.list" | wc -c | tr -d ' ')"
(cd "$ROOT" && tar --null -T "$WORK/files.list" -cf -) | tar -x -C "$COPY" || fail "could not copy the working tree"
copied_files="$(find "$COPY" \( -type f -o -type l \) | wc -l | tr -d ' ')"
[ "$copied_files" = "$expected_files" ] || fail "isolated copy is incomplete: $copied_files of $expected_files files"
if [ -f "$ROOT/local.properties" ]; then cp "$ROOT/local.properties" "$COPY/"; fi
cd "$COPY"
./gradlew -q :core:apiDump >"$WORK/dump.out" 2>&1 || { tail -20 "$WORK/dump.out" >&2; fail ":core:apiDump failed"; }
DUMP="$COPY/core/api.txt"

[ -f "$DUMP" ] || fail "core/api.txt was not produced"
head -1 "$DUMP" | grep -q 'Signature format' || fail "core/api.txt lacks a Signature format header"
grep -q '^package io\.github\.ygaray\.voiceactionengine\.core' "$DUMP" || fail "core/api.txt lacks the core package (vacuous dump)"

# A declaration line is indentation, optional annotations, then a visibility word, then class|interface|enum. Member
# lines (method/field/ctor/property) start with their own keyword and never match.
decl_lines() { grep -E '^[[:space:]]*(@[^[:space:]]+[[:space:]]+)*(public|protected)[[:space:]].*\b(class|interface|enum)[[:space:]]+[A-Za-z_]' "$DUMP" || true; }

# Fully qualified: the package of each declaration comes from the enclosing "package ... {" line of the dump.
sealed_found="$(awk '
  /^package [A-Za-z0-9_.]+ \{/ { pkg = $2; next }
  /^[[:space:]]*(@[^[:space:]]+[[:space:]]+)*(public|protected)[[:space:]].*[[:space:]](class|interface)[[:space:]]+[A-Za-z_]/ && /[[:space:]]sealed[[:space:]]/ {
    line = $0
    sub(/^.*[[:space:]](class|interface)[[:space:]]+/, "", line)
    sub(/[^A-Za-z0-9_.].*$/, "", line)
    print pkg "." line
  }' "$DUMP" | sort -u || true)"
for t in $sealed_found; do
  case " $ALLOWED_SEALED " in *" $t "*) ;; *) fail "sealed type $t is outside the allow-list ($ALLOWED_SEALED)" ;; esac
done
if [ "$EXPECT_COMPLETE" = 1 ]; then
  for t in $ALLOWED_SEALED; do
    echo "$sealed_found" | grep -qxF "$t" || fail "expected sealed type $t is missing from the dump"
  done
fi

if grep -nE '^[[:space:]]*method .*\b(copy|component[0-9]+)\(' "$DUMP" >"$WORK/data.out"; then
  fail "data-shaped class (copy/componentN): $(head -3 "$WORK/data.out" | tr '\n' ' ')"
fi
if decl_lines | grep -E '\benum[[:space:]]+[A-Za-z_]' >"$WORK/enum.out"; then
  fail "enum declared: $(head -3 "$WORK/enum.out" | tr '\n' ' ')"
fi
bad_fields="$(grep -E '^[[:space:]]*field .*\bstatic\b' "$DUMP" \
  | sed -E 's/[[:space:]]*=.*$//; s/;[[:space:]]*$//; s/.*[[:space:]]([A-Za-z0-9_$]+)$/\1/' \
  | grep -Ev '^(INSTANCE|Companion)$' || true)"
[ -z "$bad_fields" ] || fail "public static field(s) leaked: $(echo "$bad_fields" | tr '\n' ' ')(a public const val shows up here too: use a private const and an internal function)"

classes="$(decl_lines | wc -l | tr -d ' ')"
cd "$ROOT"
# Real-tree guard: the working-tree status must be byte-identical to the snapshot taken before the run.
[ "$(tree_status)" = "$before_status" ] || fail "the real working tree changed during the run"
# Written after the guard so an --out inside the repo cannot trip it.
if [ -n "$OUT" ]; then mkdir -p "$(dirname "$OUT_ABS")"; cp "$DUMP" "$OUT_ABS"; fi
sealed_list="$(echo "$sealed_found" | sed -E 's/.*\.//' | sort -u | paste -sd, -)"
echo "API SURFACE OK sealed=${sealed_list:-none} classes=$classes"
