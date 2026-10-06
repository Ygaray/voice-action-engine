#!/usr/bin/env bash
# Phase 13 spike disposition gate (SPIKE-03, D-08): proves the build that the v1.1.0 tag would carry holds no trace of the
# on-device spike (JitPack configures every included Gradle project, so a leftover include is a leftover risk, L10/T-13-40).
#   removed     (red and green_defer) the spike module, its include, its catalog entries, its jitpack mention and every
#               tracked LiteRT reference outside the allow-list are gone.
#   green_ship  the same rules, except that the extracted :ondevice module is allowed: ondevice/**, README.md,
#               INTEGRATION.md, API.md, ECOSYSTEM.md, the catalog litertlm entries and their use in ondevice/build.gradle.kts,
#               and the jitpack.yml publish line of :ondevice.
# Collects every violation, then prints the result. Last line:
#   SPIKE DISPOSITION OK mode=<m>                       (exit 0)
#   SPIKE DISPOSITION FAIL: <violation>; <violation>... (exit 1)
# Usage: scripts/verify-spike-disposition.sh <removed|green_ship>
set -euo pipefail
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$ROOT"

mode="${1:-}"
case "$mode" in
  removed | green_ship) ;;
  *)
    echo "usage: $0 <removed|green_ship>" >&2
    exit 2
    ;;
esac

violations=()
violate() { violations+=("$1"); }

# Files that may carry the case-insensitive token "litert" in either mode. CROSS-REPO-SCOPE-CONTRACT.md is the
# control-plane-owned contract (changed only by section 10 amendments), whose L10 text names MediaPipe/LiteRT.
allow_removed=(
  gradle/invariants.gradle.kts
  core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
  scripts/verify-ml-denial-controls.sh
  scripts/verify-negative-controls.sh
  scripts/verify-repo-hygiene.sh
  scripts/spike-evidence-filter.sh
  scripts/verify-spike-verdict.sh
  scripts/verify-spike-disposition.sh
  CROSS-REPO-SCOPE-CONTRACT.md
  .gitignore
)
allow_green_extra=(
  README.md
  INTEGRATION.md
  API.md
  ECOSYSTEM.md
  gradle/libs.versions.toml
  ondevice/build.gradle.kts
)

is_allowed() {
  local f="$1" a
  case "$f" in
    .planning/*) return 0 ;;
  esac
  for a in "${allow_removed[@]}"; do [ "$f" = "$a" ] && return 0; done
  if [ "$mode" = green_ship ]; then
    case "$f" in ondevice/*) return 0 ;; esac
    for a in "${allow_green_extra[@]}"; do [ "$f" = "$a" ] && return 0; done
  fi
  return 1
}

# 1. the module directory (on disk, including leftover build output, or tracked)
if [ "$mode" = removed ]; then
  [ -e spike-ondevice ] && violate "spike-ondevice/ is present on disk"
  if [ -n "$(git ls-files -- spike-ondevice)" ]; then violate "spike-ondevice/ has tracked files"; fi
fi

# 2. a spike include in settings.gradle.kts (non-comment lines)
if grep -v '^[[:space:]]*//' settings.gradle.kts | grep -Eqi 'include\(.*spike'; then
  violate "settings.gradle.kts still includes a spike module"
fi

# 3. litertlm catalog keys
if [ "$mode" = removed ]; then
  if grep -Eq '^[[:space:]]*litertlm' gradle/libs.versions.toml; then
    violate "gradle/libs.versions.toml still has a litertlm key"
  fi
fi

# 4. any tracked build script declaring the LiteRT-LM coordinate or catalog alias
while IFS= read -r f; do
  [ -f "$f" ] || continue
  if grep -Eqi 'com\.google\.ai\.edge\.litertlm|libs\.litertlm' "$f"; then
    if [ "$mode" = green_ship ] && [ "$f" = ondevice/build.gradle.kts ]; then continue; fi
    violate "$f declares the LiteRT-LM coordinate"
  fi
done < <(git ls-files -- '*build.gradle.kts')

# 5. jitpack.yml (non-comment lines)
if grep -v '^[[:space:]]*#' jitpack.yml | grep -Eqi 'spike'; then
  violate "jitpack.yml names the spike"
fi

# 6. every tracked file outside the allow-list carrying the token litert
while IFS= read -r f; do
  [ -f "$f" ] || continue
  is_allowed "$f" && continue
  if grep -Eqi 'litert' "$f"; then violate "$f mentions litert outside the allow-list"; fi
done < <(git ls-files)

if [ "${#violations[@]}" -gt 0 ]; then
  out="SPIKE DISPOSITION FAIL: ${violations[0]}"
  for v in "${violations[@]:1}"; do out="$out; $v"; done
  echo "$out"
  exit 1
fi
echo "SPIKE DISPOSITION OK mode=$mode"
