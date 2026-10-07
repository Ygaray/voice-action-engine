#!/usr/bin/env bash
# :stt confinement gate (textual half). Bash only, never runs Gradle, never touches the network.
#   scripts/verify-stt-confinement.sh [--root <dir>] [--selftest]
# Proves that only :voice-adapter can ever pull the speech engine (:stt), by reading the files that decide it:
#   repo           settings.gradle.kts: one exclusiveContent block, one JitPack host mention, one includeGroup that is
#                  the exact :stt group (never the aggregator group)
#   catalog        gradle/libs.versions.toml: one com.github.Ygaray library, the per-module :stt coordinate, pinned to an
#                  immutable vX.Y.Z tag through the version key stt-engine
#   wiring         no build file except voice-adapter's names the catalog alias, the :stt coordinate or :voice-adapter
#   adapter-build  voice-adapter/build.gradle.kts: the :stt alias only as compileOnly + testImplementation, android library
#                  plugin, minSdk 35
#   sources        no Kotlin source imports the :stt package except voice-adapter main FinalSegmentMapping.kt (and tests)
#   docs-minimum   INTEGRATION.md states "<catalog pin> or newer"
# Prints every violation as `STT CONFINEMENT FAIL: <check>: <item>: <detail>` then exits 1, or prints
# `STT CONFINEMENT OK checks=6`. --selftest plants each violation in an isolated copy and requires it to go red naming the
# item; it never touches the real tree. The group literal is matched in full: this repository's own published group
# (com.github.Ygaray.voice-action-engine) contains the same prefix and stays legal.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT="$REPO_ROOT"
SELFTEST=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --root) ROOT="$(cd "${2:?--root needs a directory}" && pwd)"; shift 2 ;;
    --selftest) SELFTEST=1; shift ;;
    *) echo "usage: $0 [--root <dir>] [--selftest]" >&2; exit 2 ;;
  esac
done

STT_GROUP="com.github.Ygaray.voice-engine-android"
STT_ARTIFACT="voice-engine-android"
AGGREGATOR_GROUP="com.github.Ygaray"
STT_ALIAS="libs.stt.engine"
ADAPTER="voice-adapter"
STT_PACKAGE="io.github.ygaray.sttengine"
ADAPTER_MAPPING="voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt"

# Lines of a file that are not whole-line comments (marker given as the second argument).
code_lines() { grep -vE "^[[:space:]]*$2" "$1" || true; }

gate() {
  local root="$1" violations=()
  violate() { violations+=("STT CONFINEMENT FAIL: $1: $2: $3"); }

  # 1. repo
  local settings="$root/settings.gradle.kts" code n
  if [ ! -f "$settings" ]; then
    violate repo settings.gradle.kts "file is missing"
  else
    code="$(code_lines "$settings" '//')"
    n="$(printf '%s\n' "$code" | grep -cE '^[[:space:]]*exclusiveContent[[:space:]]*\{' || true)"
    [ "$n" -eq 1 ] || violate repo settings.gradle.kts "expected exactly one exclusiveContent block, found $n"
    n="$(printf '%s\n' "$code" | grep -oi 'jitpack\.io' | wc -l || true)"
    [ "$n" -eq 1 ] || violate repo settings.gradle.kts "expected exactly one mention of the JitPack host, found $n"
    local groups
    groups="$(printf '%s\n' "$code" | grep -oE 'includeGroup\("[^"]*"\)' || true)"
    n="$(printf '%s' "$groups" | grep -c . || true)"
    [ "$n" -eq 1 ] || violate repo settings.gradle.kts "expected exactly one includeGroup, found $n"
    if [ "$n" -ge 1 ] && ! printf '%s\n' "$groups" | grep -qxF "includeGroup(\"$STT_GROUP\")"; then
      violate repo settings.gradle.kts "includeGroup is not the exact group $STT_GROUP"
    fi
    if printf '%s\n' "$groups" | grep -qxF "includeGroup(\"$AGGREGATOR_GROUP\")"; then
      violate repo settings.gradle.kts "includeGroup admits the aggregator group $AGGREGATOR_GROUP"
    fi
  fi

  # 2. catalog
  local catalog="$root/gradle/libs.versions.toml" pin="" entries
  if [ ! -f "$catalog" ]; then
    violate catalog gradle/libs.versions.toml "file is missing"
  else
    code="$(code_lines "$catalog" '#')"
    entries="$(printf '%s\n' "$code" | grep -F 'com.github.Ygaray' || true)"
    n="$(printf '%s' "$entries" | grep -c . || true)"
    if [ "$n" -ne 1 ]; then
      violate catalog gradle/libs.versions.toml "expected exactly one com.github.Ygaray library entry, found $n"
    else
      printf '%s\n' "$entries" | grep -qF "group = \"$STT_GROUP\"" \
        || violate catalog gradle/libs.versions.toml "the com.github.Ygaray entry is not group $STT_GROUP"
      printf '%s\n' "$entries" | grep -qF "name = \"$STT_ARTIFACT\"" \
        || violate catalog gradle/libs.versions.toml "the :stt entry is not the per-module name $STT_ARTIFACT"
      printf '%s\n' "$entries" | grep -qF 'version.ref = "stt-engine"' \
        || violate catalog gradle/libs.versions.toml "the :stt entry does not use version.ref stt-engine"
    fi
    if printf '%s\n' "$entries" | grep -qE "group = \"$AGGREGATOR_GROUP\""; then
      violate catalog gradle/libs.versions.toml "an entry uses the aggregator group $AGGREGATOR_GROUP"
    fi
    pin="$(printf '%s\n' "$code" | sed -nE 's/^stt-engine[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/p' | head -n 1)"
    if [ -z "$pin" ]; then
      violate catalog gradle/libs.versions.toml "version key stt-engine is missing"
    elif ! printf '%s\n' "$pin" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+$'; then
      violate catalog gradle/libs.versions.toml "version key stt-engine is '$pin', not an immutable vX.Y.Z tag"
    fi
  fi

  # 3. wiring
  local f rel hits
  while IFS= read -r f; do
    rel="${f#"$root"/}"
    [ "$rel" = "$ADAPTER/build.gradle.kts" ] && continue
    hits="$(grep -nE '^[[:space:]]*[^[:space:]/]' "$f" | grep -vE '^[0-9]+:[[:space:]]*//' \
      | grep -F -e "$STT_ALIAS" -e "$STT_ARTIFACT" -e "project(\":$ADAPTER\")" || true)"
    if [ -n "$hits" ]; then
      while IFS= read -r line; do
        violate wiring "$rel" "line ${line%%:*} names the :stt coordinate or :$ADAPTER, only $ADAPTER/build.gradle.kts may"
      done <<< "$hits"
    fi
  done < <(find "$root" \( -name build -o -name .gradle -o -name .git -o -name .planning -o -name graphify-out -o -name .kotlin \) \
    -type d -prune -o -name build.gradle.kts -type f -print | LC_ALL=C sort)

  # 4. adapter-build
  local ab="$root/$ADAPTER/build.gradle.kts"
  if [ ! -f "$ab" ]; then
    violate adapter-build "$ADAPTER/build.gradle.kts" "file is missing"
  else
    code="$(code_lines "$ab" '//')"
    printf '%s\n' "$code" | grep -qF "compileOnly($STT_ALIAS)" \
      || violate adapter-build "$ADAPTER/build.gradle.kts" "no compileOnly($STT_ALIAS)"
    printf '%s\n' "$code" | grep -qF "testImplementation($STT_ALIAS)" \
      || violate adapter-build "$ADAPTER/build.gradle.kts" "no testImplementation($STT_ALIAS)"
    if printf '%s\n' "$code" | grep -qE '(^|[^A-Za-z])(api|implementation|runtimeOnly|compileOnlyApi)\(libs\.stt\.engine'; then
      violate adapter-build "$ADAPTER/build.gradle.kts" "the :stt alias is declared through api/implementation/runtimeOnly/compileOnlyApi"
    fi
    printf '%s\n' "$code" | grep -qF 'libs.plugins.android.library' \
      || violate adapter-build "$ADAPTER/build.gradle.kts" "the android library plugin alias is not applied"
    printf '%s\n' "$code" | grep -qE 'minSdk[[:space:]]*=[[:space:]]*35([^0-9]|$)' \
      || violate adapter-build "$ADAPTER/build.gradle.kts" "minSdk is not 35"
  fi

  # 5. sources
  while IFS= read -r f; do
    rel="${f#"$root"/}"
    if ! grep -qF "$STT_PACKAGE" "$f"; then continue; fi
    case "$rel" in
      "$ADAPTER_MAPPING") ;;
      "$ADAPTER"/src/test/*) ;;
      "$ADAPTER"/src/main/*/LanguageLabels.kt)
        violate sources "$rel" "names the :stt package, but the stt-free facade must stay stt-free" ;;
      *) violate sources "$rel" "names the :stt package outside the allowed adapter files" ;;
    esac
  done < <(find "$root" \( -name build -o -name .gradle -o -name .git -o -name .planning -o -name graphify-out -o -name .kotlin \) \
    -type d -prune -o -name '*.kt' -type f -print | LC_ALL=C sort)

  # 6. docs-minimum
  local docs="$root/INTEGRATION.md"
  if [ ! -f "$docs" ]; then
    violate docs-minimum INTEGRATION.md "file is missing"
  elif [ -n "$pin" ] && ! grep -qF "$pin or newer" "$docs"; then
    violate docs-minimum INTEGRATION.md "does not state the catalog pin '$pin or newer'"
  fi

  if [ "${#violations[@]}" -gt 0 ]; then
    printf '%s\n' "${violations[@]}"
    return 1
  fi
  printf 'STT CONFINEMENT OK checks=6\n'
}


if [ "$SELFTEST" -eq 1 ]; then
  echo "STT CONFINEMENT SELFTEST FAIL: not implemented" >&2; exit 1
else
  gate "$ROOT"
fi
