# Reader for scripts/modules.list. Sourced, never executed.
#   vae_modules                      module names, space-separated, in file order
#   vae_module_field <name> <field>  one of packaging|artifactId|kotlinPackage|dependsOnCore
#   vae_artifacts                    artifactIds, space-separated
#   vae_artifacts_sorted             artifactIds sorted, each followed by one space
#   vae_modules_where_core <yes|no>  names filtered by the dependsOnCore column
# VAE_MODULES_FILE overrides the manifest location. Only VAE_* names leak into the caller.

_vae_file() {
  if [ -n "${VAE_MODULES_FILE:-}" ]; then
    printf '%s\n' "$VAE_MODULES_FILE"
  else
    printf '%s/scripts/modules.list\n' "$(git rev-parse --show-toplevel)"
  fi
}

# Prints validated rows (5 whitespace-separated fields) to stdout; a bad row reports on stderr and returns 1.
_vae_rows() {
  local file line n=0 raw name packaging artifact pkg core seen_names=' ' seen_artifacts=' ' noglob=0
  file="$(_vae_file)"
  if [ ! -f "$file" ]; then
    echo "MODULES FAIL: $file: manifest not found" >&2
    return 1
  fi
  while IFS= read -r raw || [ -n "$raw" ]; do
    n=$((n + 1))
    line="${raw%%#*}"
    # Split on whitespace with pathname expansion off, so a '*' in a field stays a literal.
    case "$-" in *f*) noglob=1 ;; esac
    set -f
    # shellcheck disable=SC2086
    set -- $line
    [ "$noglob" -eq 1 ] || set +f
    [ "$#" -eq 0 ] && continue
    if [ "$#" -ne 5 ]; then
      echo "MODULES FAIL: $file:$n: expected 5 fields, got $#" >&2
      return 1
    fi
    name="$1"; packaging="$2"; artifact="$3"; pkg="$4"; core="$5"
    case "$name" in
      *[!a-z0-9-]*) echo "MODULES FAIL: $file:$n: bad module name '$name'" >&2; return 1 ;;
    esac
    case "$packaging" in
      jar | aar) ;;
      *) echo "MODULES FAIL: $file:$n: packaging must be jar or aar, got '$packaging'" >&2; return 1 ;;
    esac
    case "$core" in
      yes | no) ;;
      *) echo "MODULES FAIL: $file:$n: dependsOnCore must be yes or no, got '$core'" >&2; return 1 ;;
    esac
    case "$seen_names" in
      *" $name "*) echo "MODULES FAIL: $file:$n: duplicate module name '$name'" >&2; return 1 ;;
    esac
    case "$seen_artifacts" in
      *" $artifact "*) echo "MODULES FAIL: $file:$n: duplicate artifactId '$artifact'" >&2; return 1 ;;
    esac
    seen_names="$seen_names$name "
    seen_artifacts="$seen_artifacts$artifact "
    printf "%s %s %s %s %s\n" "$name" "$packaging" "$artifact" "$pkg" "$core"
  done < "$file"
}

vae_modules() {
  local rows out="" name rest
  rows="$(_vae_rows)" || return 1
  while read -r name rest; do
    [ -z "$name" ] && continue
    out="${out:+$out }$name"
  done <<< "$rows"
  printf '%s\n' "$out"
}

vae_module_field() {
  local want="$1" field="$2" rows name packaging artifact pkg core
  rows="$(_vae_rows)" || return 1
  while read -r name packaging artifact pkg core; do
    [ "$name" = "$want" ] || continue
    case "$field" in
      packaging) printf '%s\n' "$packaging" ;;
      artifactId) printf '%s\n' "$artifact" ;;
      kotlinPackage) printf '%s\n' "$pkg" ;;
      dependsOnCore) printf '%s\n' "$core" ;;
      *) echo "MODULES FAIL: unknown field '$field'" >&2; return 1 ;;
    esac
    return 0
  done <<< "$rows"
  echo "MODULES FAIL: unknown module '$want'" >&2
  return 1
}

vae_artifacts() {
  local rows out="" name packaging artifact pkg core
  rows="$(_vae_rows)" || return 1
  while read -r name packaging artifact pkg core; do
    [ -z "$name" ] && continue
    out="${out:+$out }$artifact"
  done <<< "$rows"
  printf '%s\n' "$out"
}

vae_artifacts_sorted() {
  local rows name packaging artifact pkg core
  rows="$(_vae_rows)" || return 1
  while read -r name packaging artifact pkg core; do
    [ -z "$name" ] && continue
    printf '%s\n' "$artifact"
  done <<< "$rows" | LC_ALL=C sort | while IFS= read -r artifact; do printf '%s ' "$artifact"; done
}

vae_modules_where_core() {
  local want="$1" rows out="" name packaging artifact pkg core
  case "$want" in
    yes | no) ;;
    *) echo "MODULES FAIL: vae_modules_where_core needs yes or no" >&2; return 1 ;;
  esac
  rows="$(_vae_rows)" || return 1
  while read -r name packaging artifact pkg core; do
    [ -z "$name" ] && continue
    [ "$core" = "$want" ] && out="${out:+$out }$name"
  done <<< "$rows"
  printf '%s\n' "$out"
}
