#!/usr/bin/env bash
# Repository hygiene gate (BLD-07, BLD-08 and the Phase 1 prohibitions). Runnable at any time, not part of `check`.
#   a. package root io.github.ygaray.voiceactionengine everywhere; every published module has a main source
#   b. docs list the per-module coordinates and never the retired two-segment aggregator coordinate; ignore rules hold
#   c. no api.txt / A10 fixture / detekt baseline file tracked or untracked-not-ignored
#   d. no git tags
#   e. gradlew committed 100755
#   f. jitpack.yml never names the app module
#   g. toolchain pins intact (Gradle 9.4.1, Kotlin 2.3.20, AGP 9.2.1, OkHttp 4.12.0)
#   h. nothing STAGED under .planning/graphs/ or graphify-out/ (staged set only: unstaged pre-existing edits are not ours)
# Prints every violation, then HYGIENE OK only when there are none. Usage: scripts/verify-repo-hygiene.sh
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
violations=()
violate() { violations+=("$1"); }

# a. BLD-07 package root
PKG_ROOT="io/github/ygaray/voiceactionengine"
PKG_DOT="io.github.ygaray.voiceactionengine"
kt_files="$(find core providers keystore sample -type f -name '*.kt' -path '*/src/*/kotlin/*' -not -path '*/build/*' 2>/dev/null | sort)"
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
for m in core providers keystore; do
  main_count="$(find "$m/src/main/kotlin" -type f -name '*.kt' 2>/dev/null | wc -l)"
  if [ "$main_count" -lt 1 ]; then violate "a: $m has no main Kotlin source"; fi
done

# b. BLD-08 docs and ignore rules
for art in voice-action-engine-core voice-action-engine-providers voice-action-engine-keystore; do
  grep -q "$art" ECOSYSTEM.md || violate "b: ECOSYSTEM.md does not name $art"
done
if grep -Eq 'com\.github\.Ygaray:voice-action-engine([^-]|$)' ECOSYSTEM.md README.md; then
  violate "b: ECOSYSTEM.md or README.md still contains the retired two-segment aggregator coordinate"
fi
for p in sample/src/debug/assets/sb-a10-fixture.json sample/src/debug/assets/sb-a10-fixture.v2.json graphify-out/x/graph.json; do
  git check-ignore -q "$p" || violate "b: $p is not gitignored"
done

# c. forbidden files, tracked or untracked-not-ignored
forbidden="$(git ls-files -co --exclude-standard -- '*api.txt' '*sb-a10-fixture*' '*baseline*.xml')"
if [ -n "$forbidden" ]; then violate "c: forbidden file(s) present: $(echo "$forbidden" | tr '\n' ' ')"; fi

# d. no tags
tags="$(git tag --list)"
if [ -n "$tags" ]; then violate "d: git tags exist: $(echo "$tags" | tr '\n' ' ')"; fi

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
