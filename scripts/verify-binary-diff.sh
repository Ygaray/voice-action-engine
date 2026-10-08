#!/usr/bin/env bash
# D-02 binary-compatibility descriptor diff. Bash plus the JDK's javap/unzip only: no Gradle, no network.
#   scripts/verify-binary-diff.sh --old <jar|aar> --new <jar|aar> --api <old api.txt> [--out <file>]
#   scripts/verify-binary-diff.sh --selftest
# Metalava's api.txt shows the source-level surface; it does not show synthetic members (constructors taking a
# DefaultConstructorMarker, `$default` methods, bridge methods). Those are part of what a consumer's compiled code links
# against, so this helper compares what javap prints.
#   classes   exactly the classes declared in the OLD release's api.txt (the public contract; Kotlin `internal` classes are
#             public in bytecode and are not part of it), mapped to binary names (package path, Outer.Inner -> Outer$Inner)
#   members   `javap -protected -s` of the old and the new artifact, reduced to sorted lines of class, member signature and
#             descriptor, so public and protected members including synthetic ones are compared. An .aar is reduced to its
#             classes.jar first.
#   result    a member of the old set that is not in the new set is REMOVED and fails the run, and so does a class that is
#             wholly absent from the new artifact. Exception: members carrying Kotlin's internal-visibility suffix (a dollar
#             sign and the module name, such as `$core`, `$providers`, `$keystore_release`) are counted as internal_removed
#             and reported without failing. Additions are counted, never failing.
#   note      a modifier or generic-signature change of a member shows as REMOVED plus an addition: it is reported for a
#             human to judge, never silently accepted.
# The last line is `BINARY DIFF OK classes=<n> removed=0 internal_removed=<k> added=<m>` or `BINARY DIFF FAIL: removed=<n>`
# (every removed line is printed before it, and written to --out when given). Exit codes: 0 ok, 1 removed members, 2 usage or
# input error (including an api.txt that names a class the old artifact does not hold). --selftest builds jars with javac
# and ends `BINARY DIFF SELFTEST OK cases=<n>`; it writes only under its own temp directory.
set -euo pipefail
export LC_ALL=C

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
WORK=""
trap 'if [ -n "$WORK" ]; then rm -rf "$WORK"; fi' EXIT

usage() {
  echo "usage: $0 --old <jar|aar> --new <jar|aar> --api <old api.txt> [--out <file>] | --selftest" >&2
  exit 2
}
err() {
  echo "BINARY DIFF ERROR: $*" >&2
  exit 2
}

# Internal-visibility suffix of a Kotlin member name: $<module>, optionally followed by a variant such as _release.
INTERNAL_RE='\$(core|providers|keystore|undo|voice_adapter)(_[a-z]+)?$'

# Reduces a .jar or .aar to a plain jar at $2.
to_jar() { # <file> <dest>
  [ -f "$1" ] || err "$1 does not exist"
  case "$1" in
    *.aar)
      unzip -p "$1" classes.jar >"$2" 2>/dev/null || err "$1 holds no classes.jar"
      [ -s "$2" ] || err "$1 holds an empty classes.jar"
      ;;
    *.jar) cp -- "$1" "$2" ;;
    *) err "$1 is neither a .jar nor an .aar" ;;
  esac
}

# Binary class names (a/b/Outer$Inner) of every class declared in an api.txt, one per line.
api_classes() { # <api.txt>
  awk '
    /^package [^ ]+ \{$/ { pkg = $2; gsub(/\./, "/", pkg); next }
    /^  [^ ].*\{$/ && pkg != "" {
      n = split($0, t, " ")
      k = 0
      for (i = 1; i <= n; i++) {
        if (t[i] == "class" || t[i] == "interface" || t[i] == "@interface") { k = i; break }
        if (t[i] == "enum" && t[i + 1] != "class") { k = i; break }
      }
      if (k == 0) next
      name = t[k + 1]
      sub(/<.*/, "", name)
      gsub(/\./, "$", name)
      print pkg "/" name
    }' "$1" | sort -u
}

# Binary class names inside a jar, one per line.
jar_classes() { # <jar>
  unzip -Z1 "$1" | sed -n 's/\.class$//p' | sort -u
}

# javap -protected -s of the given classes, reduced to `class<TAB>member<TAB>descriptor` lines, sorted and deduplicated.
member_lines() { # <jar> <class-file> (one binary name per line)
  local -a names
  mapfile -t names <"$2"
  [ "${#names[@]}" -gt 0 ] || return 0
  names=("${names[@]//\//.}")
  javap -protected -s -cp "$1" "${names[@]}" | awk '
    /^[^ ].*\{$/ {
      n = split($0, t, " ")
      cls = ""
      for (i = 1; i <= n; i++) {
        if (t[i] == "class" || t[i] == "interface" || t[i] == "enum" || t[i] == "@interface") { cls = t[i + 1]; break }
      }
      sub(/<.*/, "", cls)
      mem = ""
      next
    }
    /^  [^ ].*;$/ { mem = $0; sub(/^  /, "", mem); next }
    /^    descriptor: / {
      d = $0
      sub(/^    descriptor: /, "", d)
      if (mem != "" && mem != "static {};") printf "%s\t%s\t%s\n", cls, mem, d
      mem = ""
    }' | sort -u
}

run_diff() { # <old> <new> <api> <out or empty>
  local old="$1" new="$2" api="$3" out="$4" report="" line n
  local missing gone removed internal=0 hard=0 added_n
  WORK="$(mktemp -d)"
  to_jar "$old" "$WORK/old.jar"
  to_jar "$new" "$WORK/new.jar"
  [ -f "$api" ] || err "$api does not exist"
  api_classes "$api" >"$WORK/api.classes"
  [ -s "$WORK/api.classes" ] || err "$api declares no class"
  jar_classes "$WORK/old.jar" >"$WORK/old.classes"
  jar_classes "$WORK/new.jar" >"$WORK/new.classes"
  missing="$(comm -23 "$WORK/api.classes" "$WORK/old.classes")"
  if [ -n "$missing" ]; then
    echo "BINARY DIFF ERROR: $api names class(es) the old artifact does not hold (api file and artifact disagree): $(printf '%s' "$missing" | tr '\n' ' ')" >&2
    exit 2
  fi
  gone="$(comm -23 "$WORK/api.classes" "$WORK/new.classes")"
  comm -12 "$WORK/api.classes" "$WORK/new.classes" >"$WORK/present.classes"
  member_lines "$WORK/old.jar" "$WORK/present.classes" >"$WORK/old.members" || err "javap failed on the old artifact"
  member_lines "$WORK/new.jar" "$WORK/present.classes" >"$WORK/new.members" || err "javap failed on the new artifact"
  removed="$(comm -23 "$WORK/old.members" "$WORK/new.members")"
  added_n="$(comm -13 "$WORK/old.members" "$WORK/new.members" | wc -l)"
  n="$(wc -l <"$WORK/api.classes")"

  if [ -n "$gone" ]; then
    while IFS= read -r line; do
      report+="REMOVED class ${line//\//.} (declared in the api file, absent from the new artifact)"$'\n'
      hard=$((hard + 1))
    done <<<"$gone"
  fi
  if [ -n "$removed" ]; then
    while IFS=$'\t' read -r cls mem desc; do
      local sig="${mem%;}" name
      sig="${sig%%(*}"
      name="${sig##* }"
      if [[ "$name" =~ $INTERNAL_RE ]]; then
        report+="INTERNAL-REMOVED $cls: $mem descriptor: $desc"$'\n'
        internal=$((internal + 1))
      else
        report+="REMOVED $cls: $mem descriptor: $desc"$'\n'
        hard=$((hard + 1))
      fi
    done <<<"$removed"
  fi
  if [ "$hard" -gt 0 ]; then
    report+="BINARY DIFF FAIL: removed=$hard"$'\n'
  else
    report+="BINARY DIFF OK classes=$n removed=0 internal_removed=$internal added=$added_n"$'\n'
  fi
  printf '%s' "$report"
  if [ -n "$out" ]; then printf '%s' "$report" >"$out" || err "cannot write $out"; fi
  [ "$hard" -eq 0 ]
}

# ---------------------------------------------------------------------------------------------------------------------
selftest() {
  local cases=0 rc out
  WORK="$(mktemp -d)"
  command -v javac >/dev/null && command -v jar >/dev/null && command -v javap >/dev/null || { echo "BINARY DIFF SELFTEST FAIL: javac, jar and javap are required" >&2; exit 1; }
  sfail() { echo "BINARY DIFF SELFTEST FAIL: $*" >&2; exit 1; }

  # The old tree: a class with constructors, a protected and a static method, a $default-shaped synthetic name, an internal-
  # mangled name and a nested class; a second listed class; and a public class the api file does not list.
  local old="$WORK/old-src"
  mkdir -p "$old/t"
  cat >"$old/t/A.java" <<'EOF'
package t;
public class A {
  public A(int x) {}
  public A() {}
  public void m() {}
  public void m$default(int a) {}
  protected void p() {}
  public static void s() {}
  public void foo$core() {}
  public static class N { public void n() {} }
}
EOF
  cat >"$old/t/B.java" <<'EOF'
package t;
public class B { public void b() {} }
EOF
  cat >"$old/t/Hidden.java" <<'EOF'
package t;
public class Hidden { public void h() {} }
EOF
  cat >"$WORK/api.txt" <<'EOF'
// Signature format: 4.0
package t {

  public class A {
    ctor public A(int);
    ctor public A();
    method public void m();
  }

  public static class A.N {
    ctor public A.N();
  }

  public class B {
    ctor public B();
  }

}
EOF
  cat >"$WORK/api-missing.txt" <<'EOF'
// Signature format: 4.0
package t {

  public class Missing {
  }

}
EOF

  # build <name> : compiles the tree $WORK/<name>-src into $WORK/<name>.jar and $WORK/<name>.aar
  build() {
    local n="$1"
    mkdir -p "$WORK/$n-classes" "$WORK/$n-aar"
    javac -d "$WORK/$n-classes" $(find "$WORK/$n-src" -name '*.java') || sfail "javac failed for $n"
    jar cf "$WORK/$n.jar" -C "$WORK/$n-classes" . || sfail "jar failed for $n"
    cp "$WORK/$n.jar" "$WORK/$n-aar/classes.jar"
    (cd "$WORK/$n-aar" && jar cf "$WORK/$n.aar" classes.jar) || sfail "aar packaging failed for $n"
  }
  # variant <name> : a copy of the old tree for the caller to edit
  variant() {
    rm -rf "$WORK/$1-src"
    cp -a "$old" "$WORK/$1-src"
  }
  # expect <label> <want-rc> <new-artifact> <regex or empty> [<api file>] [<old artifact>]
  expect() {
    local label="$1" want="$2" new="$3" pat="$4" api="${5:-$WORK/api.txt}" oldart="${6:-$WORK/old.jar}"
    rc=0
    out="$("$SELF" --old "$oldart" --new "$new" --api "$api" 2>&1)" || rc=$?
    [ "$rc" -eq "$want" ] || sfail "[$label] exit $rc, wanted $want: $out"
    if [ -n "$pat" ]; then
      grep -Eq -- "$pat" <<<"$out" || sfail "[$label] output lacks /$pat/: $out"
    fi
    cases=$((cases + 1))
  }

  build old

  # 1. identical artifacts (jar against jar, and aar against aar)
  expect identical-jar 0 "$WORK/old.jar" '^BINARY DIFF OK classes=3 removed=0 internal_removed=0 added=0$'
  expect identical-aar 0 "$WORK/old.aar" '^BINARY DIFF OK classes=3 removed=0 internal_removed=0 added=0$' "$WORK/api.txt" "$WORK/old.aar"
  # 2. an added method is counted, never failing
  variant add
  sed -i 's/public void m() {}/public void m() {}\n  public void extra() {}/' "$WORK/add-src/t/A.java"
  build add
  expect added-method 0 "$WORK/add.jar" '^BINARY DIFF OK classes=3 removed=0 internal_removed=0 added=1$'
  # 3. a removed public constructor fails and is named
  variant noctor
  sed -i '/public A(int x) {}/d' "$WORK/noctor-src/t/A.java"
  build noctor
  expect removed-constructor 1 "$WORK/noctor.jar" 't\.A\(int\)'
  grep -q 'BINARY DIFF FAIL: removed=1' <<<"$out" || sfail "[removed-constructor] no FAIL line with removed=1: $out"
  # 4. a removed class that the api file lists fails
  variant nob
  rm -f "$WORK/nob-src/t/B.java"
  build nob
  expect removed-listed-class 1 "$WORK/nob.jar" 'REMOVED class t\.B '
  # 5. a removed class that the api file does not list (internal) stays ok
  variant nohidden
  rm -f "$WORK/nohidden-src/t/Hidden.java"
  build nohidden
  expect removed-unlisted-class 0 "$WORK/nohidden.jar" '^BINARY DIFF OK classes=3 removed=0 '
  # 6. an api file that names a class the old artifact lacks is an error (exit 2)
  expect api-names-missing-class 2 "$WORK/old.jar" 'names class\(es\) the old artifact does not hold.*t/Missing' "$WORK/api-missing.txt"
  # 7. a removed Kotlin-internal mangled member is reported, not failing
  variant nointernal
  sed -i '/foo\$core/d' "$WORK/nointernal-src/t/A.java"
  build nointernal
  expect removed-internal-member 0 "$WORK/nointernal.jar" '^BINARY DIFF OK classes=3 removed=0 internal_removed=1 added=0$'
  grep -q 'INTERNAL-REMOVED' <<<"$out" || sfail "[removed-internal-member] the internal removal was not reported: $out"
  # 8. a removed $default-shaped synthetic member is a real removal
  variant nodefault
  sed -i '/m\$default/d' "$WORK/nodefault-src/t/A.java"
  build nodefault
  expect removed-default-member 1 "$WORK/nodefault.jar" 'm\$default'
  # 9. a removed nested class that the api file lists fails
  variant non
  sed -i '/public static class N/d' "$WORK/non-src/t/A.java"
  build non
  expect removed-nested-class 1 "$WORK/non.jar" 'REMOVED class t\.A\$N '
  # 10. the report also goes to --out
  rc=0
  "$SELF" --old "$WORK/old.jar" --new "$WORK/noctor.jar" --api "$WORK/api.txt" --out "$WORK/report.txt" >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 1 ] && grep -q 'BINARY DIFF FAIL: removed=1' "$WORK/report.txt" || sfail "[out-file] the report file lacks the FAIL line"
  cases=$((cases + 1))
  # 11. usage errors exit 2
  rc=0
  "$SELF" >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 2 ] || sfail "[usage] no arguments exited $rc, wanted 2"
  cases=$((cases + 1))
  rc=0
  "$SELF" --old "$WORK/old.jar" --new "$WORK/old.txt" --api "$WORK/api.txt" >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 2 ] || sfail "[usage] a missing artifact exited $rc, wanted 2"
  cases=$((cases + 1))

  echo "BINARY DIFF SELFTEST OK cases=$cases"
}

# ---------------------------------------------------------------------------------------------------------------------
OLD="" NEW="" API="" OUT="" DO_SELFTEST=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --old) [ "$#" -ge 2 ] || usage; OLD="$2"; shift 2 ;;
    --new) [ "$#" -ge 2 ] || usage; NEW="$2"; shift 2 ;;
    --api) [ "$#" -ge 2 ] || usage; API="$2"; shift 2 ;;
    --out) [ "$#" -ge 2 ] || usage; OUT="$2"; shift 2 ;;
    --selftest) DO_SELFTEST=1; shift ;;
    *) usage ;;
  esac
done

if [ "$DO_SELFTEST" -eq 1 ]; then
  { [ -z "$OLD$NEW$API$OUT" ]; } || usage
  selftest
  exit 0
fi
{ [ -n "$OLD" ] && [ -n "$NEW" ] && [ -n "$API" ]; } || usage
run_diff "$OLD" "$NEW" "$API" "$OUT"
