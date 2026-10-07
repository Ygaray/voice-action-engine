#!/usr/bin/env bash
# Public-surface review over a REAL Metalava dump. Without --dump it produces core's dump in an ISOLATED COPY of the
# working tree so the api.txt baselines committed in the real tree are never rewritten. With --dump <file> it reviews a
# dump that scripts/api-dump-isolated.sh already produced (one isolated dump of every manifest module, reviewed module
# by module), and runs no Gradle at all.
#   fails when the module's dump:
#     a. is missing, lacks the "Signature format" header, or lacks the module's package (non-vacuity)
#     b. has a sealed type outside the module's allow-list, matched by fully qualified name. core allows
#        {StrategyOutcome, CommandOutcome, RunTermination, GateDecision, ToolStep, Message, AssistantPart}; undo allows
#        exactly undo.UndoResult; every other module allows none. (--expect-sealed-complete: the sealed set must equal
#        the whole allow-list)
#     c. declares a copy( or componentN( method (data-shaped class)
#     d. declares an enum
#     e. has a public static field other than INSTANCE or Companion
# Usage: scripts/review-api-surface.sh [--module <name>] [--dump <file>] [--expect-sealed-complete] [--out <path>]
#   --module <name>  a manifest module (scripts/modules.list); default core
#   --dump <file>    review this dump instead of dumping (cannot be combined with --out)
# Prints "API SURFACE OK module=<name> sealed=<list|none> classes=<n>" or "API SURFACE FAIL: <reason>" (exit 1).
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
PKG_ROOT="io.github.ygaray.voiceactionengine"
CORE_PKG="$PKG_ROOT.core"
EXPECT_COMPLETE=0
OUT=""
MODULE="core"
DUMP_IN=""
fail() { echo "API SURFACE FAIL: $1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --expect-sealed-complete) EXPECT_COMPLETE=1; shift ;;
    --out) [ $# -ge 2 ] || fail "--out needs a path"; OUT="$2"; shift 2 ;;
    --module) [ $# -ge 2 ] || fail "--module needs a name"; MODULE="$2"; shift 2 ;;
    --dump) [ $# -ge 2 ] || fail "--dump needs a file"; DUMP_IN="$2"; shift 2 ;;
    *) fail "unknown argument $1" ;;
  esac
done
[ -z "$DUMP_IN" ] || [ -z "$OUT" ] || fail "--dump and --out cannot be combined (nothing is dumped to copy out)"

# shellcheck source=lib/modules.sh
. "$ROOT/scripts/lib/modules.sh" || fail "cannot load scripts/lib/modules.sh"
REVIEW_MODULES="$(vae_modules)" || fail "module manifest unreadable"
case " $REVIEW_MODULES " in *" $MODULE "*) ;; *) fail "unknown module '$MODULE' (manifest: $REVIEW_MODULES)" ;; esac
MODULE_SUBPKG="$(vae_module_field "$MODULE" kotlinPackage)" || fail "no kotlinPackage for $MODULE"
MODULE_PKG="$PKG_ROOT.$MODULE_SUBPKG"

case "$MODULE" in
  core) ALLOWED_SEALED="$CORE_PKG.strategy.StrategyOutcome $CORE_PKG.pipeline.CommandOutcome $CORE_PKG.commit.RunTermination $CORE_PKG.commit.GateDecision $CORE_PKG.commit.ToolStep $CORE_PKG.transcript.Message $CORE_PKG.transcript.AssistantPart" ;;
  undo) ALLOWED_SEALED="$PKG_ROOT.undo.UndoResult" ;;
  *) ALLOWED_SEALED="" ;;
esac

if [ -n "$OUT" ]; then
  OUT_ABS="$(realpath -m "$OUT")"
  case "$(basename "$OUT_ABS")" in *api.txt) fail "--out must not end in api.txt (it would shadow a module's committed baseline)" ;; esac
  for m in $REVIEW_MODULES; do
    case "$OUT_ABS" in "$ROOT/$m"/*|"$ROOT/$m") fail "--out must not lie under $m/" ;; esac
  done
fi

WORK="$(mktemp -d)"
# The copy is removed on exit unless KEEP_WORK=1 (debugging).
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
# Orchestrator/graph bookkeeping under .planning/ and graphify-out/ changes independently of this script: not compared.
tree_status() { git -C "$ROOT" status --porcelain -- . ':!.planning' ':!graphify-out'; }
before_status="$(tree_status)"

if [ -n "$DUMP_IN" ]; then
  DUMP="$(realpath -m "$DUMP_IN")"
  [ "$(basename "$DUMP")" != "api.txt" ] || fail "--dump must not be a committed api.txt (review an isolated dump)"
  [ -f "$DUMP" ] || fail "dump $DUMP not found"
else
  COPY="$WORK/repo"; mkdir -p "$COPY"
  # The copy is every tracked and untracked non-ignored file (git ls-files -co --exclude-standard) that exists on disk,
  # so ignored build outputs are not copied.
  # Files that are tracked but deleted in the working tree cannot be copied; every other listed file must be, so a file
  # the tar cannot read fails the run instead of silently producing a dump from an incomplete tree.
  (cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
    | while IFS= read -r -d '' f; do if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\0' "$f"; fi; done >"$WORK/files.list")
  expected_files="$(tr -cd '\0' <"$WORK/files.list" | wc -c | tr -d ' ')"
  (cd "$ROOT" && tar --null -T "$WORK/files.list" -cf -) | tar -x -C "$COPY" || fail "could not copy the working tree"
  copied_files="$(find "$COPY" \( -type f -o -type l \) | wc -l | tr -d ' ')"
  [ "$copied_files" = "$expected_files" ] || fail "isolated copy is incomplete: $copied_files of $expected_files files"
  if [ -f "$ROOT/local.properties" ]; then cp "$ROOT/local.properties" "$COPY/"; fi
  cd "$COPY"
  ./gradlew -q ":$MODULE:apiDump" >"$WORK/dump.out" 2>&1 || { tail -20 "$WORK/dump.out" >&2; fail ":$MODULE:apiDump failed"; }
  DUMP="$COPY/$MODULE/api.txt"
fi

[ -f "$DUMP" ] || fail "$MODULE dump was not produced"
head -1 "$DUMP" | grep -q 'Signature format' || fail "$MODULE dump lacks a Signature format header"
grep -q "^package ${MODULE_PKG//./\\.}" "$DUMP" || fail "$MODULE dump lacks the $MODULE_PKG package (vacuous dump)"

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
  case " $ALLOWED_SEALED " in *" $t "*) ;; *) fail "sealed type $t is outside the $MODULE allow-list (${ALLOWED_SEALED:-none})" ;; esac
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
echo "API SURFACE OK module=$MODULE sealed=${sealed_list:-none} classes=$classes"
