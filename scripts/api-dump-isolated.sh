#!/usr/bin/env bash
# Metalava dump of every published module (scripts/modules.list) in an ISOLATED COPY, so the api.txt files committed in
# the real tree (the released baselines) are never rewritten. Each module's dump is written as <out>/<module>.api.sig,
# never as api.txt.
#   --out <dir>   required; must not end in api.txt and must not lie under any module directory
#   --head        copy `git archive HEAD` instead of the working tree (the release gate dumps exactly what is committed)
# The default copy is every tracked and untracked non-ignored file of the working tree (minus graphify-out/ and
# .planning/graphs/), with a completeness count. Any api.txt inside the copy is deleted before dumping, so a stale file
# can never compare equal to itself. KEEP_WORK=1 keeps the copy for debugging.
# Usage: scripts/api-dump-isolated.sh --out <dir> [--head]
# Prints "API DUMP ISOLATED OK out=<dir> <module>=<n> ..." (line counts, one per module in manifest order) or
# "API DUMP ISOLATED FAIL: <reason>" (exit 1).
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
PKG_ROOT="io.github.ygaray.voiceactionengine"
# The manifest is read from the real tree: the isolated copy is not a git repository, so the reader cannot discover it there.
export VAE_MODULES_FILE="${VAE_MODULES_FILE:-$ROOT/scripts/modules.list}"
# shellcheck source=lib/modules.sh
. "$ROOT/scripts/lib/modules.sh" || { echo "API DUMP ISOLATED FAIL: cannot load scripts/lib/modules.sh" >&2; exit 1; }
MODULES="$(vae_modules)" || { echo "API DUMP ISOLATED FAIL: module manifest unreadable" >&2; exit 1; }
OUT=""
HEAD_MODE=0
fail() { echo "API DUMP ISOLATED FAIL: $1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --out) [ $# -ge 2 ] || fail "--out needs a directory"; OUT="$2"; shift 2 ;;
    --head) HEAD_MODE=1; shift ;;
    *) fail "unknown argument $1" ;;
  esac
done
[ -n "$OUT" ] || fail "--out <dir> is required"

OUT_ABS="$(realpath -m "$OUT")"
case "$(basename "$OUT_ABS")" in *api.txt) fail "--out must not end in api.txt (it would shadow a module's committed baseline)" ;; esac
for m in $MODULES; do
  case "$OUT_ABS" in "$ROOT/$m"/*|"$ROOT/$m") fail "--out must not lie under $m/" ;; esac
done

WORK="$(mktemp -d)"; COPY="$WORK/repo"; mkdir -p "$COPY"
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
# Orchestrator/graph bookkeeping under .planning/ and graphify-out/ changes independently of this script: not compared.
tree_status() { git -C "$ROOT" status --porcelain -- . ':!.planning' ':!graphify-out'; }
before_status="$(tree_status)"

if [ "$HEAD_MODE" = 1 ]; then
  (cd "$ROOT" && git archive HEAD) | tar -x -C "$COPY" || fail "could not archive HEAD"
else
  # Files that are tracked but deleted in the working tree cannot be copied; every other listed file must be, so a file
  # the tar cannot read fails the run instead of silently producing a dump from an incomplete tree.
  (cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
    | while IFS= read -r -d '' f; do if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\0' "$f"; fi; done >"$WORK/files.list")
  expected_files="$(tr -cd '\0' <"$WORK/files.list" | wc -c | tr -d ' ')"
  (cd "$ROOT" && tar --null -T "$WORK/files.list" -cf -) | tar -x -C "$COPY" || fail "could not copy the working tree"
  copied_files="$(find "$COPY" \( -type f -o -type l \) | wc -l | tr -d ' ')"
  [ "$copied_files" = "$expected_files" ] || fail "isolated copy is incomplete: $copied_files of $expected_files files"
fi
if [ -f "$ROOT/local.properties" ]; then cp "$ROOT/local.properties" "$COPY/"; fi

cd "$COPY"
for m in $MODULES; do rm -f "$m/api.txt"; done
./gradlew -q apiDump >"$WORK/dump.out" 2>&1 || { tail -20 "$WORK/dump.out" >&2; fail "apiDump failed"; }

for m in $MODULES; do
  dump="$COPY/$m/api.txt"
  [ -f "$dump" ] || fail "$m/api.txt was not produced"
  head -1 "$dump" | grep -q 'Signature format' || fail "$m/api.txt lacks a Signature format header"
  pkg="$(vae_module_field "$m" kotlinPackage)" || fail "no kotlinPackage for $m"
  grep -q "^package $PKG_ROOT\\.$pkg" "$dump" || fail "$m/api.txt lacks the $m package (vacuous dump)"
done

cd "$ROOT"
# Real-tree guard: the working-tree status must be byte-identical to the snapshot taken before the run.
[ "$(tree_status)" = "$before_status" ] || fail "the real working tree changed during the run"
# Written after the guard so an --out inside the repo cannot trip it.
mkdir -p "$OUT_ABS"
counts=""
for m in $MODULES; do
  cp "$COPY/$m/api.txt" "$OUT_ABS/$m.api.sig"
  counts="$counts $m=$(wc -l <"$OUT_ABS/$m.api.sig" | tr -d ' ')"
done
echo "API DUMP ISOLATED OK out=$OUT_ABS${counts}"
