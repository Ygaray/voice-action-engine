#!/usr/bin/env bash
# Repository hygiene gate (BLD-07, BLD-08 and the Phase 1 prohibitions). Runnable at any time, not part of `check`.
#   a. package root io.github.ygaray.voiceactionengine everywhere; every published module listed in scripts/modules.list has a main source
#   b. docs list the per-module coordinates and never the retired two-segment aggregator coordinate; ignore rules hold
#   c. every published module listed in scripts/modules.list tracks its api.txt (release mode); no A10 fixture / detekt baseline / on-device model weight / private gold-label / spike fixture file tracked or untracked-not-ignored
#   d. no git tags
#   e. gradlew committed 100755
#   f. jitpack.yml never names the app module
#   g. toolchain pins intact (Gradle 9.4.1, Kotlin 2.3.20, AGP 9.2.1, OkHttp 4.12.0)
#   h. nothing STAGED under .planning/graphs/ or graphify-out/ (staged set only: unstaged pre-existing edits are not ours)
# Prints every violation, then HYGIENE OK only when there are none. Usage: scripts/verify-repo-hygiene.sh
#
# (c) api.txt and (d) tags are PRE-RELEASE assertions, and PRE_RELEASE=1 selects them. The first release (v1.0.0) needed
# both inverted, and the repository has been past it since 2026-10-02, so the default is now PRE_RELEASE=0: every
# published module must track its api.txt, and tags are no longer forbidden. The release cut runs it with PRE_RELEASE=0
# explicitly. The fixture/baseline prohibitions in (c) hold in both modes.
set -euo pipefail
PRE_RELEASE="${PRE_RELEASE:-0}"
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
# shellcheck source=lib/modules.sh
. "$ROOT/scripts/lib/modules.sh" || { echo "HYGIENE FAIL: cannot load scripts/lib/modules.sh" >&2; exit 1; }
MODULES="$(vae_modules)" || { echo "HYGIENE FAIL: module manifest unreadable" >&2; exit 1; }
ARTIFACTS="$(vae_artifacts)" || { echo "HYGIENE FAIL: module manifest unreadable" >&2; exit 1; }
violations=()
violate() { violations+=("$1"); }

# a. BLD-07 package root
PKG_ROOT="io/github/ygaray/voiceactionengine"
PKG_DOT="io.github.ygaray.voiceactionengine"
# shellcheck disable=SC2086
kt_files="$(find $(printf '%s\n' $MODULES sample spike-ondevice ondevice | sort -u) -type f -name '*.kt' -path '*/src/*/kotlin/*' -not -path '*/build/*' 2>/dev/null | sort || true)"
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    */"$PKG_ROOT"/*) ;;
    *) violate "a: $f is not below $PKG_ROOT/" ;;
  esac
  if ! grep -Eq "^package ${PKG_DOT//./\\.}(\\.|\$)" "$f"; then
    violate "a: $f does not declare a package starting $PKG_DOT"
  fi
done <<< "$kt_files"
for m in $MODULES; do
  main_count="$({ find "$m/src/main/kotlin" -type f -name '*.kt' 2>/dev/null || true; } | wc -l)"
  if [ "$main_count" -lt 1 ]; then violate "a: $m has no main Kotlin source"; fi
done

# b. BLD-08 docs and ignore rules
for art in $ARTIFACTS; do
  grep -q "$art" ECOSYSTEM.md || violate "b: ECOSYSTEM.md does not name $art"
done
if grep -Eq 'com\.github\.Ygaray:voice-action-engine([^-]|$)' ECOSYSTEM.md README.md; then
  violate "b: ECOSYSTEM.md or README.md still contains the retired two-segment aggregator coordinate"
fi
for p in sample/src/debug/assets/sb-a10-fixture.json sample/src/debug/assets/sb-a10-fixture.v2.json graphify-out/x/graph.json \
  spike-ondevice/src/main/assets/x.litertlm spike-ondevice/src/main/assets/x.task spike-ondevice/src/main/assets/x.tflite \
  spike-ondevice/src/main/assets/x.bin spike-ondevice/src/main/assets/zz-sb-gold.json \
  spike-ondevice/src/main/assets/zz-sb-fixture.json zz-sb-fixture.json; do
  git check-ignore -q "$p" || violate "b: $p is not gitignored"
done

# c. forbidden files, tracked or untracked-not-ignored
forbidden_specs=('*sb-a10-fixture*' '*baseline*.xml' '*.litertlm' '*.task' '*.tflite' '*.bin' '*sb-gold*' '*sb-fixture*')
if [ "$PRE_RELEASE" = 1 ]; then forbidden_specs+=('*api.txt'); fi
forbidden="$(git ls-files -co --exclude-standard -- "${forbidden_specs[@]}")"
if [ -n "$forbidden" ]; then violate "c: forbidden file(s) present: $(echo "$forbidden" | tr '\n' ' ')"; fi
if [ "$PRE_RELEASE" != 1 ]; then
  for m in $MODULES; do
    git ls-files --error-unmatch -- "$m/api.txt" >/dev/null 2>&1 || violate "c: release mode: $m/api.txt is not tracked"
  done
fi

# d. no tags (pre-release only)
if [ "$PRE_RELEASE" = 1 ]; then
  tags="$(git tag --list)"
  if [ -n "$tags" ]; then violate "d: git tags exist: $(echo "$tags" | tr '\n' ' ')"; fi
fi

# e. gradlew mode
gradlew_entry="$(git ls-files -s gradlew)"
case "$gradlew_entry" in
  100755*) ;;
  *) violate "e: gradlew is not committed with mode 100755 (got: ${gradlew_entry:-not tracked})" ;;
esac

# f. jitpack.yml never names the app module (comment lines are ignored)
jitpack_cmds="$(grep -Ev '^[[:space:]]*#' jitpack.yml || true)"
if echo "$jitpack_cmds" | grep -Eq '(^|[^[:alnum:]_-]):?sample([^[:alnum:]_-]|$)'; then
  violate "f: jitpack.yml names the sample module in a command"
fi
if echo "$jitpack_cmds" | grep -Eq '(^|[^[:alnum:]_-]):?spike-ondevice([^[:alnum:]_-]|$)'; then
  violate "f: jitpack.yml names the spike module in a command"
fi

# g. toolchain pins
grep -q 'gradle-9\.4\.1-' gradle/wrapper/gradle-wrapper.properties || violate "g: wrapper is not pinned to Gradle 9.4.1"
grep -Eq '^kotlin = "2\.3\.20"' gradle/libs.versions.toml || violate "g: catalog kotlin is not 2.3.20"
grep -Eq '^agp = "9\.2\.1"' gradle/libs.versions.toml || violate "g: catalog agp is not 9.2.1"
grep -Eq '^okhttp = "4\.12\.0"' gradle/libs.versions.toml || violate "g: catalog okhttp is not 4.12.0"

# h. staged set only
staged="$(git diff --cached --name-only)"
if echo "$staged" | grep -Eq '^(\.planning/graphs/|graphify-out/)'; then
  violate "h: staged paths under .planning/graphs/ or graphify-out/"
fi

if [ "${#violations[@]}" -gt 0 ]; then
  echo "HYGIENE FAIL (${#violations[@]} violation(s)):" >&2
  for v in "${violations[@]}"; do echo "  - $v" >&2; done
  exit 1
fi
echo "HYGIENE OK"
