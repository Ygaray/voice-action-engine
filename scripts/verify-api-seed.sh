#!/usr/bin/env bash
# Header-only api.txt seed proof for a published module that has not been released yet.
#   a. <module>/api.txt is exactly the one Signature-format header line, and the module has public API (not vacuous)
#   b. in an ISOLATED COPY of the working tree, the module's Metalava compatibility task exits 0 and actually executes
#      (never SKIPPED / UP-TO-DATE / NO-SOURCE / FROM-CACHE): additions against the empty baseline are accepted.
#      The task follows the packaging column of scripts/modules.list: :<m>:metalavaCheckCompatibility for a jar
#      module, :<m>:metalavaCheckCompatibilityRelease for an aar module
#   c. in the copy, a baseline that declares a class the source lacks makes the same task fail with "Removed"
#   d. the real working tree is unchanged by the run
# Usage: scripts/verify-api-seed.sh <module>
#   VAE_PRINT_TASK=1 scripts/verify-api-seed.sh <module>   prints the chosen Metalava task and exits 0 before any other
#   check and without Gradle (works for any listed module, header-only seed or released)
# Prints "API SEED OK module=<m> executed=yes removal=red", or "API SEED FAIL: <reason>" (exit 1; exit 2 when the
# module's api.txt is not a header-only seed). KEEP_WORK=1 keeps the isolated copy.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
HEADER="// Signature format: 4.0"
fail() { echo "API SEED FAIL: $1" >&2; exit "${2:-1}"; }

[ "$#" -eq 1 ] || { echo "usage: $0 <module>" >&2; exit 64; }
MODULE="$1"
# shellcheck source=lib/modules.sh
source "$ROOT/scripts/lib/modules.sh"
KPKG="$(vae_module_field "$MODULE" kotlinPackage)" || fail "$MODULE is not in scripts/modules.list"
PACKAGING="$(vae_module_field "$MODULE" packaging)" || fail "$MODULE is not in scripts/modules.list"
case "$PACKAGING" in
  aar) TASK=":$MODULE:metalavaCheckCompatibilityRelease" ;;
  jar) TASK=":$MODULE:metalavaCheckCompatibility" ;;
  *) fail "$MODULE has unsupported packaging '$PACKAGING' in scripts/modules.list" ;;
esac
if [ "${VAE_PRINT_TASK:-0}" = 1 ]; then
  echo "$TASK"
  exit 0
fi

# a. header-only seed and non-vacuity, before any Gradle run
if [ ! -f "$MODULE/api.txt" ] || [ "$(cat "$MODULE/api.txt")" != "$HEADER" ]; then
  fail "$MODULE/api.txt is not a header-only seed" 2
fi
if ! grep -rqE '^[[:space:]]*public ' "$MODULE/src/main/kotlin"; then
  fail "no public API (vacuous)"
fi

# Low-memory recipe (host runs earlyoom): single-use daemon, two workers, in-process Kotlin.
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"
GRADLE_FLAGS=(--offline --console=plain --no-build-cache -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false)

WORK="$(mktemp -d)"; COPY="$WORK/repo"; mkdir -p "$COPY"
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
tree_status() { git -C "$ROOT" status --porcelain -- . ':!.planning' ':!graphify-out'; }
before_status="$(tree_status)"

# Copy of every tracked and untracked non-ignored file that exists on disk (same recipe as review-api-surface.sh).
(cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
  | while IFS= read -r -d '' f; do if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\0' "$f"; fi; done >"$WORK/files.list")
expected_files="$(tr -cd '\0' <"$WORK/files.list" | wc -c | tr -d ' ')"
(cd "$ROOT" && tar --null -T "$WORK/files.list" -cf -) | tar -x -C "$COPY" || fail "could not copy the working tree"
copied_files="$(find "$COPY" \( -type f -o -type l \) | wc -l | tr -d ' ')"
[ "$copied_files" = "$expected_files" ] || fail "isolated copy is incomplete: $copied_files of $expected_files files"
if [ -f "$ROOT/local.properties" ]; then cp "$ROOT/local.properties" "$COPY/"; fi

run_check() { # <log>; prints the real exit status, never behind a pipe
  local log="$1" rc=0
  (cd "$COPY" && ./gradlew "${GRADLE_FLAGS[@]}" "$TASK") >"$log" 2>&1 || rc=$?
  echo "$rc"
}

# b. additions against the header-only baseline are accepted, and the check really ran
rc="$(run_check "$WORK/b.log")"
if [ "$rc" -ne 0 ]; then
  tail -30 "$WORK/b.log" >&2
  fail "$TASK exited $rc against the header-only seed (Metalava rejected it?)"
fi
if ! grep -qE "^> Task $TASK$" "$WORK/b.log"; then
  fail "the compatibility check did not execute ($(grep -E "Task $TASK" "$WORK/b.log" | head -1))"
fi

# c. a baseline declaring a class the source lacks must go red with Removed
{
  echo "$HEADER"
  echo "package io.github.ygaray.voiceactionengine.$KPKG {"
  echo
  echo "  public final class ZzSeedProbe {"
  echo "    ctor public ZzSeedProbe();"
  echo "  }"
  echo
  echo "}"
} >"$COPY/$MODULE/api.txt"
rc="$(run_check "$WORK/c.log")"
if [ "$rc" -eq 0 ]; then fail "a removal did not fail the check"; fi
if ! grep -q 'Removed' "$WORK/c.log"; then
  tail -30 "$WORK/c.log" >&2
  fail "a removal did not fail the check (no 'Removed' in the log)"
fi

# d. real-tree guard
[ "$before_status" = "$(tree_status)" ] || fail "the real working tree changed during the run"
[ "$(cat "$MODULE/api.txt")" = "$HEADER" ] || fail "$MODULE/api.txt in the real tree changed"
echo "API SEED OK module=$MODULE executed=yes removal=red"
