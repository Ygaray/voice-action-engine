#!/usr/bin/env bash
# Local emulation of what JitPack does (BLD-03 pre-check, no network push needed):
#   clean copy of the repo -> run every jitpack.yml `install:` command -> publish into an ISOLATED maven-local
#   -> assert exactly the three engine artifacts exist (and nothing for :sample or testFixtures)
#   -> resolve them from an EMPTY Gradle cache with scripts/jitpack-consumer-probe.sh (jar->jar and AAR->jar).
# Usage:  scripts/jitpack-dry-run.sh              committed HEAD content (exactly what JitPack would check out)
#         WORKTREE=1 scripts/jitpack-dry-run.sh   tracked + untracked-but-not-ignored working tree (pre-commit checks)
#         DRYRUN_VERSION=v1.0.0 scripts/jitpack-dry-run.sh
#                                                 export VERSION=<value> exactly as JitPack does for a tag build (the release
#                                                 gate proves the published coordinates carry the tag). Default, when unset:
#                                                 dryrun-<sha10>, as before.
# The install commands are read from jitpack.yml and run verbatim (plus -Dmaven.repo.local), so a drift between
# jitpack.yml and the module publish tasks fails here, not on JitPack.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)"; CLONE="$WORK/clone"; M2="$WORK/m2/repository"; mkdir -p "$CLONE" "$M2"
# Remove the clone, build outputs and isolated maven-local on exit unless KEEP_WORK=1 (debugging).
trap '[ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"' EXIT
if [ "${WORKTREE:-0}" = "1" ]; then
  (cd "$ROOT" && git ls-files -co --exclude-standard -z | grep -zv -e '^graphify-out/' -e '^\.planning/graphs/' \
    | tar --null --ignore-failed-read -T - -cf -) | tar -x -C "$CLONE"
else
  git -C "$ROOT" archive HEAD | tar -x -C "$CLONE"
fi
export VERSION="${DRYRUN_VERSION:-dryrun-$(git -C "$ROOT" rev-parse --short=10 HEAD)}"   # JitPack exports VERSION to the build (fallback F3)
cd "$CLONE"
[ -x ./gradlew ] || { echo "DRY RUN FAIL: gradlew is not executable in the tree JitPack would see" >&2; exit 1; }
[ -f jitpack.yml ] || { echo "DRY RUN FAIL: no jitpack.yml" >&2; exit 1; }
mapfile -t CMDS < <(awk '/^install:/{f=1;next} f&&/^[^[:space:]#-]/{f=0} f&&/^[[:space:]]*-[[:space:]]/{sub(/^[[:space:]]*-[[:space:]]*/,"");print}' jitpack.yml)
[ "${#CMDS[@]}" -gt 0 ] || { echo "DRY RUN FAIL: no install commands found in jitpack.yml" >&2; exit 1; }
for c in "${CMDS[@]}"; do
  case "$c" in *":sample"*) echo "DRY RUN FAIL: jitpack.yml install list names :sample: $c" >&2; exit 1;; esac
  echo ">>> $c"
  bash -c "$c -Dmaven.repo.local=$M2"
done
G="$(grep -E '^engineGroup=' gradle.properties | cut -d= -f2)"
[ -n "$G" ] || { echo "DRY RUN FAIL: engineGroup missing from gradle.properties" >&2; exit 1; }
found="$(find "$M2" -type d -name 'voice-action-engine-*' -prune | sed 's#.*/##' | sort | tr '\n' ' ')"
[ "$found" = "voice-action-engine-core voice-action-engine-keystore voice-action-engine-providers " ] \
  || { echo "DRY RUN FAIL: published artifact set is [$found], expected core/keystore/providers only" >&2; exit 1; }
# Match artifact names only (-iname), never the full path: the mktemp prefix must not be able to cause a false failure.
if [ -n "$(find "$M2" -mindepth 1 \( -iname '*sample*' -o -iname '*test-fixtures*' \) -print)" ]; then
  echo "DRY RUN FAIL: a sample or test-fixtures artifact was published" >&2; exit 1
fi
for spec in core:jar providers:jar keystore:aar; do
  m="${spec%%:*}"; ext="${spec##*:}"
  f="$(find "$M2" -name "voice-action-engine-$m-$VERSION.$ext")"
  [ -n "$f" ] || { echo "DRY RUN FAIL: voice-action-engine-$m-$VERSION.$ext missing" >&2; exit 1; }
  echo "artifact: ${f#$M2/}"
  find "$M2" -name "voice-action-engine-$m-$VERSION.module" | sed "s#$M2/#metadata: #"
done
cd "$ROOT"
REPO_URL="file://$M2" VERSION="$VERSION" GROUP="$G" "$ROOT/scripts/jitpack-consumer-probe.sh"
if [ "${KEEP_WORK:-0}" = 1 ]; then where="m2=$M2 (kept)"; else where="workdir removed on exit"; fi
echo "DRY RUN OK version=$VERSION group=$G $where"
