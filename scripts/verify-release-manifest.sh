#!/usr/bin/env bash
# Cheap proofs that the release script reads the module manifest. Bash, git and python3 only: no Gradle, no network.
#   scripts/verify-release-manifest.sh
# Part 1 (gate 7): a temp clone of HEAD with the working-tree scripts/ overlaid and committed as base B.
#   - a commit changing only undo/api.txt            -> `release-cut.sh gate diff B` exits 0 and prints "GATE OK diff"
#   - a commit changing only a path under .planning/ -> still exits 0
#   - a commit changing a path that merely looks like an api.txt of a module that is not in the manifest -> exits 1
#   - a commit changing undo/build.gradle.kts        -> exits 1 with "RELEASE GATE FAIL diff" and the offending path
# Part 2 (gate 15): a fabricated maven-local for tag v9.9.9 run through scripts/lib/published_versions.py
#   - green: every module carries the tag, providers and keystore depend on core at the tag, undo and core depend on nothing
#   - red: undo's POM names core / keystore's POM lacks core / undo's POM is missing (each must name the module)
# Prints `RELEASE MANIFEST PROOF OK cases=<n>` or `RELEASE MANIFEST PROOF FAIL: <case>` (exit 1). The real tree is never
# written: its `git status --porcelain` is compared before and after.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CASES=0
fail() { echo "RELEASE MANIFEST PROOF FAIL: $1" >&2; exit 1; }
pass() { CASES=$((CASES + 1)); }

STATUS_BEFORE="$(git status --porcelain)"

# ---- Part 1: gate 7 in a temp clone --------------------------------------------------------------------------------------
C="$WORK/clone"
git clone --quiet --no-hardlinks "$ROOT" "$C"
git -C "$C" config user.name "release-manifest-proof"
git -C "$C" config user.email "proof@example.invalid"
git -C "$C" config commit.gpgsign false
rm -rf "$C/scripts"
mkdir -p "$C/scripts"
tar -C "$ROOT" -cf - scripts | tar -C "$C" -xf -
git -C "$C" add -A -- scripts
git -C "$C" commit --quiet --allow-empty -m "proof: overlay working-tree scripts (base B)"
B="$(git -C "$C" rev-parse HEAD)"

commit_change() { # <path> <message>
  mkdir -p "$(dirname "$C/$1")"
  printf '# proof %s\n' "$2" >>"$C/$1"
  git -C "$C" add -- "$1"
  git -C "$C" commit --quiet -m "proof: $2"
}
run_diff() { (cd "$C" && scripts/release-cut.sh gate diff "$B") >"$WORK/out" 2>"$WORK/err"; }

[ -f "$C/undo/api.txt" ] || fail "undo/api.txt is not tracked in HEAD"
[ -f "$C/undo/build.gradle.kts" ] || fail "undo/build.gradle.kts is not tracked in HEAD"

commit_change undo/api.txt "undo/api.txt only"
run_diff || { cat "$WORK/err" >&2; fail "an undo/api.txt change was rejected by gate 7"; }
grep -q '^GATE OK diff$' "$WORK/out" || fail "no 'GATE OK diff' line for an undo/api.txt change"
pass

commit_change .planning/proof-note.md ".planning only"
run_diff || { cat "$WORK/err" >&2; fail "a .planning/ change was rejected by gate 7"; }
pass

commit_change sample/api.txt "api.txt of a module that is not in the manifest"
if run_diff; then fail "an api.txt of a module outside the manifest was accepted by gate 7"; fi
grep -q 'changed after the wiring SHA: sample/api.txt' "$WORK/err" || fail "gate 7 did not name sample/api.txt"
pass
git -C "$C" reset --quiet --hard HEAD~1

commit_change undo/build.gradle.kts "undo/build.gradle.kts"
if run_diff; then fail "an undo/build.gradle.kts change was accepted by gate 7"; fi
grep -q 'RELEASE GATE FAIL diff' "$WORK/err" || fail "no 'RELEASE GATE FAIL diff' line for an undo/build.gradle.kts change"
grep -q 'changed after the wiring SHA: undo/build.gradle.kts' "$WORK/err" || fail "gate 7 did not name undo/build.gradle.kts"
pass

# ---- Part 2: gate 15 on a fabricated maven-local -------------------------------------------------------------------------
GROUP="$(awk -F= '/^engineGroup=/ { print $2; exit }' gradle.properties)"
[ -n "$GROUP" ] || fail "engineGroup is missing from gradle.properties"
TAG="v9.9.9"
MANIFEST="$ROOT/scripts/modules.list"
HELPER="$ROOT/scripts/lib/published_versions.py"

# make_m2 <dir> : writes a green maven-local for every manifest module.
make_m2() {
  local m2="$1" name pkg art core d
  while read -r name _ art pkg core; do
    [ -n "$name" ] || continue
    d="$m2/$(printf '%s' "$GROUP" | tr . /)/$art/$TAG"
    mkdir -p "$d"
    {
      printf '<?xml version="1.0" encoding="UTF-8"?>\n'
      printf '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
      printf '  <groupId>%s</groupId>\n  <artifactId>%s</artifactId>\n  <version>%s</version>\n' "$GROUP" "$art" "$TAG"
      printf '  <dependencies>\n'
      printf '    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.3.20</version></dependency>\n'
      if [ "$core" = yes ]; then
        printf '    <dependency><groupId>%s</groupId><artifactId>voice-action-engine-core</artifactId><version>%s</version></dependency>\n' "$GROUP" "$TAG"
      fi
      printf '  </dependencies>\n</project>\n'
    } >"$d/$art-$TAG.pom"
    if [ "$core" = yes ]; then
      printf '{"formatVersion":"1.1","component":{"group":"%s","module":"%s","version":"%s"},"variants":[{"name":"apiElements","dependencies":[{"group":"%s","module":"voice-action-engine-core","version":{"requires":"%s"}}]}]}\n' \
        "$GROUP" "$art" "$TAG" "$GROUP" "$TAG" >"$d/$art-$TAG.module"
    else
      printf '{"formatVersion":"1.1","component":{"group":"%s","module":"%s","version":"%s"},"variants":[{"name":"apiElements","dependencies":[]}]}\n' \
        "$GROUP" "$art" "$TAG" >"$d/$art-$TAG.module"
    fi
  done <<<"$(awk '{ sub(/#.*/, "") } NF == 5' "$MANIFEST")"
}
pom_of() { printf '%s/%s/%s/%s/%s-%s.pom' "$1" "$(printf '%s' "$GROUP" | tr . /)" "$2" "$TAG" "$2" "$TAG"; }
run_helper() { python3 "$HELPER" "$1" "$GROUP" "$TAG" "$MANIFEST" >"$WORK/hout" 2>"$WORK/herr"; }

M2G="$WORK/m2-green"; make_m2 "$M2G"
run_helper "$M2G" || { cat "$WORK/herr" >&2; fail "the green maven-local was rejected by published_versions.py"; }
pass

M2="$WORK/m2-undo-core"; make_m2 "$M2"
sed -i 's#</dependencies>#<dependency><groupId>'"$GROUP"'</groupId><artifactId>voice-action-engine-core</artifactId><version>'"$TAG"'</version></dependency></dependencies>#' "$(pom_of "$M2" voice-action-engine-undo)"
if run_helper "$M2"; then fail "an undo POM depending on core was accepted"; fi
grep -q 'undo:' "$WORK/herr" || fail "the undo-depends-on-core case did not name undo"
pass

M2="$WORK/m2-keystore-nocore"; make_m2 "$M2"
sed -i 's#<artifactId>voice-action-engine-core</artifactId>#<artifactId>not-core</artifactId>#' "$(pom_of "$M2" voice-action-engine-keystore)"
if run_helper "$M2"; then fail "a keystore POM lacking the core dependency was accepted"; fi
grep -q 'keystore:' "$WORK/herr" || fail "the keystore-without-core case did not name keystore"
pass

M2="$WORK/m2-undo-missing"; make_m2 "$M2"
rm -f "$(pom_of "$M2" voice-action-engine-undo)"
if run_helper "$M2"; then fail "a missing undo POM was accepted"; fi
grep -q 'undo:' "$WORK/herr" || fail "the missing-undo-POM case did not name undo"
pass

# ---- the real tree is untouched ------------------------------------------------------------------------------------------
[ "$(git status --porcelain)" = "$STATUS_BEFORE" ] || fail "the real tree's git status changed during the proof"
echo "RELEASE MANIFEST PROOF OK cases=$CASES"
