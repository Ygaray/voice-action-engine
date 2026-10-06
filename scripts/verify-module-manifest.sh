#!/usr/bin/env bash
# Module manifest consistency gate. Bash only, never runs Gradle.
#   scripts/verify-module-manifest.sh [--root <dir>] [--selftest]
# scripts/modules.list is the one list of published modules. This gate proves it agrees with:
#   1. the include(...) entries of settings.gradle.kts (minus :sample), in both directions
#   2. each module's maven-publish plugin and artifactId literal
#   3. the single jitpack.yml install line (exactly the manifest set, never :sample)
#   4. allowedEdges in gradle/invariants.gradle.kts, per the dependsOnCore column
#   5. each build file's :core edge (none for dependsOnCore=no, an api edge for yes)
#   6. a tracked <module>/api.txt that starts with the Metalava signature header
#   7. the packaging column against the Gradle plugin (jar = kotlin.jvm, aar = android.library)
#   8. the module's main Kotlin package directory
# Prints every violation as `MODULE MANIFEST FAIL: <check>: <item>: <detail>`, then exits 1, or prints
# `MODULE MANIFEST OK modules=<comma list>`.
# --selftest plants a module row, an include, a jitpack omission, a missing api.txt and a :core edge (and a few more) in an
# isolated copy and requires each to go red naming the item. It never touches the real tree.
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

# shellcheck source=lib/modules.sh
source "$SCRIPT_DIR/lib/modules.sh"

PKG_ROOT="io/github/ygaray/voiceactionengine"
HEADER="// Signature format: 4.0"

gate() {
  local root="$1" violations=() err_file modules m
  violate() { violations+=("MODULE MANIFEST FAIL: $1"); }
  export VAE_MODULES_FILE="$root/scripts/modules.list"
  err_file="$(mktemp)"
  if ! modules="$(vae_modules 2>"$err_file")"; then
    printf 'MODULE MANIFEST FAIL: manifest: %s\n' "$(cat "$err_file")"
    rm -f "$err_file"
    return 1
  fi
  rm -f "$err_file"

  # 1. settings includes
  local included
  included="$(grep -E '^[[:space:]]*include\(' "$root/settings.gradle.kts" | grep -oE '":[A-Za-z0-9_-]+"' \
    | tr -d '":' | grep -vx 'sample' | LC_ALL=C sort -u | tr '\n' ' ' || true)"
  local listed
  listed="$(printf '%s\n' $modules | LC_ALL=C sort -u | tr '\n' ' ')"
  for m in $modules; do
    case " $included" in *" $m "*) ;; *) violate "settings: $m: in modules.list but not included in settings.gradle.kts" ;; esac
  done
  for m in $included; do
    case " $listed" in *" $m "*) ;; *) violate "settings: $m: included in settings.gradle.kts but not in modules.list" ;; esac
  done

  # 3. jitpack install tasks
  local jit tasks
  jit="$(grep -vE '^[[:space:]]*#' "$root/jitpack.yml" || true)"
  tasks="$(printf '%s\n' "$jit" | grep -oE ':[A-Za-z0-9_-]+:publishReleasePublicationToMavenLocal' | cut -d: -f2 || true)"
  for m in $modules; do
    case " $(printf '%s ' $tasks)" in *" $m "*) ;; *) violate "jitpack: $m: jitpack.yml has no :$m:publishReleasePublicationToMavenLocal" ;; esac
  done
  for m in $tasks; do
    case " $listed" in *" $m "*) ;; *) violate "jitpack: $m: jitpack.yml publishes a module that is not in modules.list" ;; esac
  done
  if printf '%s\n' "$jit" | grep -q ':sample'; then violate "jitpack: sample: jitpack.yml names the app module"; fi

  local packaging artifact pkg core build plugin_alias
  for m in $modules; do
    packaging="$(vae_module_field "$m" packaging)"
    artifact="$(vae_module_field "$m" artifactId)"
    pkg="$(vae_module_field "$m" kotlinPackage)"
    core="$(vae_module_field "$m" dependsOnCore)"
    build="$root/$m/build.gradle.kts"

    if [ ! -f "$build" ]; then
      violate "build: $m: $m/build.gradle.kts is missing"
    else
      # 2. publish plugin + artifactId
      grep -q 'maven-publish' "$build" || violate "publish: $m: build file does not apply maven-publish"
      grep -qF "artifactId = \"$artifact\"" "$build" || violate "publish: $m: build file has no artifactId = \"$artifact\""
      # 5. :core edge
      if [ "$core" = "no" ]; then
        if [ "$m" != "core" ] && grep -qE 'project\(":core"\)' "$build"; then
          violate "core-edge: $m: dependsOnCore=no but the build file has a :core project edge"
        fi
      else
        grep -qE 'api\(project\(":core"\)\)' "$build" || violate "core-edge: $m: dependsOnCore=yes but the build file has no api(project(\":core\")) edge"
      fi
      # 7. packaging vs plugin
      if [ "$packaging" = "jar" ]; then plugin_alias='libs\.plugins\.kotlin\.jvm'; else plugin_alias='libs\.plugins\.android\.library'; fi
      grep -qE "$plugin_alias" "$build" || violate "packaging: $m: packaging=$packaging does not match the applied plugin"
    fi

    # 4. allowedEdges
    if [ "$core" = "no" ]; then
      grep -qF "\":$m\" to emptySet<String>()" "$root/gradle/invariants.gradle.kts" \
        || violate "allowedEdges: $m: no \":$m\" to emptySet<String>() entry"
    else
      grep -qF "\":$m\" to setOf(\":core\")" "$root/gradle/invariants.gradle.kts" \
        || violate "allowedEdges: $m: no \":$m\" to setOf(\":core\") entry"
    fi

    # 6. api.txt
    if [ ! -f "$root/$m/api.txt" ]; then
      violate "api.txt: $m: $m/api.txt is missing"
    else
      if ! git -C "$root" ls-files --error-unmatch -- "$m/api.txt" >/dev/null 2>&1; then
        violate "api.txt: $m: $m/api.txt is not tracked"
      fi
      if [ "$(head -n 1 "$root/$m/api.txt")" != "$HEADER" ]; then
        violate "api.txt: $m: $m/api.txt does not start with '$HEADER'"
      fi
    fi

    # 8. source dir
    [ -d "$root/$m/src/main/kotlin/$PKG_ROOT/$pkg" ] || violate "source: $m: $m/src/main/kotlin/$PKG_ROOT/$pkg does not exist"
  done

  if [ "${#violations[@]}" -gt 0 ]; then
    printf '%s\n' "${violations[@]}"
    return 1
  fi
  printf 'MODULE MANIFEST OK modules=%s\n' "${modules// /,}"
}

selftest() {
  local real_before real_after work pristine cases=0 modules m out rc
  real_before="$(git -C "$REPO_ROOT" status --porcelain)"
  work="$(mktemp -d)"
  trap "rm -rf '$work'" EXIT
  pristine="$work/pristine"
  mkdir -p "$pristine"
  modules="$(VAE_MODULES_FILE="$REPO_ROOT/scripts/modules.list" vae_modules)"
  local files=(settings.gradle.kts jitpack.yml gradle/invariants.gradle.kts scripts/modules.list scripts/lib/modules.sh)
  for m in $modules; do files+=("$m/build.gradle.kts" "$m/api.txt" "$m/src/main/kotlin"); done
  (cd "$REPO_ROOT" && tar -cf - "${files[@]}") | tar -C "$pristine" -xf -
  git -C "$pristine" init -q
  git -C "$pristine" add -A

  fail() { echo "MANIFEST SELFTEST FAIL: $1"; exit 1; }
  expect_green() {
    out="$(gate "$1" 2>&1)" || fail "clean copy is not green: $out"
    cases=$((cases + 1))
  }
  # plant <case> <needle>; the plant itself is the function body evaluated in $work/copy.
  run_plant() {
    local name="$1" needle="$2" dir="$work/copy"
    rm -rf "$dir"
    cp -a "$pristine" "$dir"
    "plant_$name" "$dir"
    set +e
    out="$(gate "$dir" 2>&1)"
    rc=$?
    set -e
    [ "$rc" -eq 1 ] || fail "$name: expected exit 1, got $rc"
    printf '%s\n' "$out" | grep -E '^MODULE MANIFEST FAIL:' | grep -qF "$needle" || fail "$name: no FAIL line naming '$needle': $out"
    cases=$((cases + 1))
  }
  plant_zzrow() { printf 'zz jar voice-action-engine-zz zz no\n' >> "$1/scripts/modules.list"; }
  plant_zzinclude() { sed -i 's/":undo", /":undo", ":zz", /' "$1/settings.gradle.kts"; }
  plant_jitpack() { sed -i 's/ :undo:publishReleasePublicationToMavenLocal//' "$1/jitpack.yml"; }
  plant_noapi() { rm -f "$1/undo/api.txt"; }
  plant_badheader() { printf '// Signature format: 3.0\n' > "$1/undo/api.txt"; }
  plant_coreedge() { printf '\ndependencies { api(project(":core")) }\n' >> "$1/undo/build.gradle.kts"; }
  plant_edges() { sed -i '/":undo" to emptySet<String>(),/d' "$1/gradle/invariants.gradle.kts"; }
  plant_fourfields() { printf 'zz jar voice-action-engine-zz zz\n' >> "$1/scripts/modules.list"; }
  plant_badcore() { printf 'zz jar voice-action-engine-zz zz maybe\n' >> "$1/scripts/modules.list"; }
  plant_badpack() { printf 'zz zip voice-action-engine-zz zz no\n' >> "$1/scripts/modules.list"; }

  expect_green "$pristine"
  run_plant zzrow zz
  run_plant zzinclude zz
  run_plant jitpack undo
  run_plant noapi undo
  run_plant badheader undo
  run_plant coreedge undo
  run_plant edges undo
  # Malformed rows must fail with the manifest line number.
  local lines
  lines="$(wc -l < "$pristine/scripts/modules.list")"
  run_plant fourfields "modules.list:$((lines + 1))"
  run_plant badcore "modules.list:$((lines + 1))"
  run_plant badpack "modules.list:$((lines + 1))"

  real_after="$(git -C "$REPO_ROOT" status --porcelain)"
  [ "$real_before" = "$real_after" ] || fail "the real tree changed during the selftest"
  printf 'MANIFEST SELFTEST OK cases=%s\n' "$cases"
}

if [ "$SELFTEST" -eq 1 ]; then
  selftest
else
  gate "$ROOT"
fi
