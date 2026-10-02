#!/usr/bin/env bash
# release-cut.sh - the gated release script for the v1.0.0 tag (Phase 11, D-01). Modelled on stt-engine's
# release-android.sh: every check that can run before the tag runs before the tag, and the irreversible act (pushing the
# tag) is a separate mode that re-runs every check first.
#
# WHY THE GATES EXIST: A TAG IS IMMUTABLE. JitPack resolves a coordinate to a git tag once and caches the built artifact
# for good, and the tag string is used literally as the version segment (CROSS-REPO-SCOPE-CONTRACT.md section 11 rule 6).
# A defect found after the tag can only be fixed by a new patch tag plus a ledger row marking the old one superseded.
# So everything checkable before the tag is checked before the tag.
#
# MODES
#   preflight <tag> <wiringSHA>                 run EVERY gate in order and stop; prints PREFLIGHT OK ... gates=<list>
#   cut <tag> <wiringSHA> <approvedCommit>      re-run the full preflight, re-check HEAD and the tag state, create the
#                                               ANNOTATED tag on HEAD, push ONLY refs/tags/<tag>, verify the remote
#   gate <name> [args]                          run one gate as a diagnostic; never tags, never pushes
#   selftest happy                              prove the happy path in a sandbox (temp clone + LOCAL bare remote)
# Exit codes: 0 ok, 1 a gate failed (RELEASE GATE FAIL <gate>: <why>), 2 usage (RELEASE USAGE: ...), 3 the push failed.
#
# NO GATE MAY BE SKIPPED. There is no bypass flag and no environment lever, on purpose: the failures these gates catch
# are exactly the ones that cannot be undone once the tag exists. `preflight` is not a bypass either: it runs every gate
# and stops before the irreversible step; `cut` runs the same gates again itself.
#
# GATES, in the order preflight runs them. Entry guards first (cheap, fail fast), then the six ROADMAP SC1 release gates.
#   1  tag-format   the tag is exactly RELEASE_TAG in strict vMAJOR.MINOR.PATCH form; the wiring SHA resolves to a commit
#   2  tags-absent  at v1.0.0 the repository holds no tag at all, locally or on origin (D-02)
#   3  create-tag   .planning/config.json git.create_tag is exactly false (D-02: GSD's milestone close must not tag)
#   4  clean        nothing staged; no uncommitted or untracked file outside the orchestrator bookkeeping paths
#   5  pushed       HEAD equals origin/main exactly
#   6  wiring       the wiring SHA is an ancestor of HEAD and 11-WIRING-RERUN.md (read from HEAD) passes for exactly it
#   7  diff         since the wiring SHA only the three api.txt files, paths under .planning/ and (ledger rows only) the
#                   contract's section 11 table changed
#   8  waiver       the waiver packet in HEAD is accepted with no pending row and every pre-freeze (category C) row
#                   answered ok, accept or waive (Yahir's answers are a hard tag precondition)
#   -- the six ROADMAP SC1 release gates, all on the content of HEAD --
#   9  check        ./gradlew check green in a clean archive of HEAD
#   10 api-dump     a fresh apiDump of HEAD equals the three api.txt committed in HEAD, byte for byte
#   11 hygiene      PRE_RELEASE=0 repository hygiene (api.txt tracked, no fixture, no baseline)
#   12 api-check    ./gradlew apiCheck green AND every module's compatibility task actually executed
#   13 dry-run      clean-clone JitPack dry run from jitpack.yml's install list (never :sample), VERSION=<tag>
#   14 leak         tracked-content scan for the A10 fixture name and key-shaped strings
#   15 version      the published coordinates carry the tag version (POM, .module, providers/keystore -> core)
# That is 15 gates; the PREFLIGHT OK line lists the gates it ran, in order. One more gate exists only as a DIAGNOSTIC and is
# not part of preflight (waiver already covers it): `prefreeze`, run before the api.txt baseline is dumped (plan 11-07).
# It requires every category C (pre-freeze API confirmation) row of the waiver packet to be answered ok, accept or waive,
# whatever the state of the other rows.
#
# GATE ARGUMENTS (pinned; a wrong count or an unknown gate is a usage error, exit 2, never a gate verdict):
#   gate tag-format <tag> [<wiringSHA>]   gate tags-absent <tag>        gate wiring <wiringSHA>   gate diff <wiringSHA>
#   gate dry-run <tag>                    gate version <tag>            every other gate takes no argument
#
# The script only READS the repository's .planning/config.json and CROSS-REPO-SCOPE-CONTRACT.md. The helper scripts it
# calls are taken from the repository under test ($REPO/scripts/), so the tree being released supplies its own tooling.
set -euo pipefail
export LC_ALL=C GIT_TERMINAL_PROMPT=0

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "RELEASE USAGE: run inside the repository" >&2; exit 2; }
cd "$REPO"

# The only tag this milestone cuts. The next release edits it in a reviewed commit.
RELEASE_TAG="v1.0.0"
# The record 11-06 writes after the isolated wiring rerun: frontmatter status, tested_sha, consulted_only_workspace.
WIRING_RECORD=".planning/phases/11-cut-v1-0-0/11-WIRING-RERUN.md"
# The waiver packet and its answer block (grammar from 11-01).
WAIVER_PACKET=".planning/phases/11-cut-v1-0-0/11-WAIVER-PACKET.md"
# The three Metalava dumps committed with the tag.
MODULES="core providers keystore"
CONTRACT="CROSS-REPO-SCOPE-CONTRACT.md"
# Paths excluded from the clean gate. Each is orchestrator bookkeeping that never reaches an artifact, because every
# gate below builds from the content of HEAD, never from the working tree:
#   .planning          planning state, run logs and per-phase stage markers the orchestrator rewrites continuously
#   graphify-out       generated knowledge-graph output (also ignored by the dump and probe scripts)
#   .gsd               GSD runtime state
#   .claude/worktrees  parallel-executor worktrees of this milestone run
CLEAN_EXCLUDES=(':!.planning' ':!graphify-out' ':!.gsd' ':!.claude/worktrees')

# The header row of the section 11 ledger table in the contract (the table the orchestrator appends dated rows to).
LEDGER_HEADER='| Date | Repo | Tag | Commit | Coordinate(s) | Contents | Evidence | Consumers repinned |'

GATE_ORDER=(tag-format tags-absent create-tag clean pushed wiring diff waiver check api-dump hygiene api-check dry-run leak version)

TMPROOT=""
DRY_M2=""
mk_tmp() { if [ -z "$TMPROOT" ]; then TMPROOT="$(mktemp -d)"; fi; }
# One temp root holds all work. Gradle homes created by the probe may symlink wrapper to the real ~/.gradle/wrapper, so the
# symlinks are unlinked before the recursive remove can ever reach through them. KEEP_WORK=1 keeps the root for debugging.
cleanup() {
  if [ "${KEEP_WORK:-0}" = 1 ]; then return 0; fi
  if [ -n "$TMPROOT" ] && [ -d "$TMPROOT" ]; then
    find "$TMPROOT" -type l -name wrapper -delete 2>/dev/null || true
    rm -rf "$TMPROOT"
  fi
}
trap cleanup EXIT

usage() {
  {
    echo "RELEASE USAGE: release-cut.sh preflight <tag> <wiringSHA> | cut <tag> <wiringSHA> <approvedCommit> | gate <name> [args] | selftest happy"
    echo "  gate tag-format <tag> [<wiringSHA>]"
    echo "  gate tags-absent <tag>"
    echo "  gate wiring <wiringSHA>"
    echo "  gate diff <wiringSHA>"
    echo "  gate dry-run <tag>"
    echo "  gate version <tag>"
    echo "  gate create-tag | clean | pushed | waiver | prefreeze | check | api-dump | hygiene | api-check | leak   (no argument)"
  } >&2
  exit 2
}

gate_fail() { printf 'RELEASE GATE FAIL %s: %s\n' "$1" "$2" >&2; exit 1; }
gate_ok() { printf 'GATE OK %s\n' "$1"; }

# Full 40-hex commit id of a revision, empty and non-zero when it does not resolve to a commit.
full_sha() { git rev-parse --verify --quiet "$1^{commit}"; }

# Value of a frontmatter key from stdin (first line must be ---), surrounding quotes stripped. Prints nothing when absent.
fm_get() {
  awk -v k="$1" '
    NR == 1 { if ($0 != "---") exit; infm = 1; next }
    infm && $0 == "---" { exit }
    infm {
      n = index($0, ":")
      if (n > 0 && substr($0, 1, n - 1) == k) {
        v = substr($0, n + 1); sub(/^[ \t]+/, "", v); sub(/[ \t\r]+$/, "", v)
        if (v ~ /^".*"$/ || v ~ /^\047.*\047$/) v = substr(v, 2, length(v) - 2)
        print v; exit
      }
    }'
}

# A clean archive of the content of HEAD (what JitPack would check out), plus the host's local.properties when present.
archive_head() {
  mkdir -p "$1"
  git archive HEAD | tar -x -C "$1"
  if [ -f "$REPO/local.properties" ]; then cp "$REPO/local.properties" "$1/"; fi
}

# ---------------------------------------------------------------------------------------------------------------------
# Entry guards
# ---------------------------------------------------------------------------------------------------------------------
gate_tag_format() {
  local tag="$1" sha="${2:-}"
  [ "$tag" = "$RELEASE_TAG" ] || gate_fail tag-format "tag '$tag' is not the release tag $RELEASE_TAG"
  [[ "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || gate_fail tag-format "tag '$tag' is not in strict vMAJOR.MINOR.PATCH form"
  if [ -n "$sha" ]; then
    full_sha "$sha" >/dev/null || gate_fail tag-format "wiring SHA '$sha' does not resolve to a commit"
  fi
  gate_ok tag-format
}

gate_tags_absent() {
  local tag="$1" local_tags remote_tags
  local_tags="$(git tag --list)"
  [ -z "$local_tags" ] || gate_fail tags-absent "local tag(s) already exist ($(echo "$local_tags" | tr '\n' ' ')); v1.0.0 must be the first tag (D-02)"
  if ! remote_tags="$(git ls-remote --tags origin 2>/dev/null)"; then
    gate_fail tags-absent "cannot list the tags on origin (network or remote problem)"
  fi
  [ -z "$remote_tags" ] || gate_fail tags-absent "origin already has tag ref(s) (first: $(echo "$remote_tags" | head -1 | cut -f2)); refusing to cut $tag"
  gate_ok tags-absent
}

# D-02 entry guard (INC-2026-09-30-01): GSD's milestone close would create its own v1.0 marker tag if git.create_tag were
# true. This reads the WORKING-TREE value (the one milestone close would use) and only reads: it never edits config.json.
gate_create_tag() {
  local val cfg="$REPO/.planning/config.json"
  [ -f "$cfg" ] || gate_fail create-tag "git.create_tag is missing (no .planning/config.json), must be false (D-02); this script never edits config.json"
  if command -v jq >/dev/null; then
    val="$(jq -r '.git.create_tag' "$cfg" 2>/dev/null)" || val="unreadable"
  else
    val="$(cd "$REPO" && node "${CLAUDE_CONFIG_DIR:-$HOME/.claude}/gsd-core/bin/gsd-tools.cjs" query config-get git.create_tag --raw 2>/dev/null)" || val="unreadable"
  fi
  [ "$val" = "false" ] || gate_fail create-tag "git.create_tag is $val, must be false (D-02); this script never edits config.json"
  gate_ok create-tag
}

gate_clean() {
  local st
  git diff --cached --quiet -- . "${CLEAN_EXCLUDES[@]}" || gate_fail clean "files are staged"
  st="$(git status --porcelain --untracked-files=all -- . "${CLEAN_EXCLUDES[@]}")"
  [ -z "$st" ] || gate_fail clean "the working tree is not clean: $(echo "$st" | head -5 | tr '\n' ';')"
  gate_ok clean
}

gate_pushed() {
  local head remote
  git fetch --quiet origin main || gate_fail pushed "git fetch origin main failed"
  head="$(git rev-parse HEAD)"
  remote="$(git rev-parse --verify --quiet refs/remotes/origin/main)" || gate_fail pushed "origin/main is unknown"
  [ "$head" = "$remote" ] || gate_fail pushed "HEAD ${head:0:10} is not origin/main ${remote:0:10}: push first, or pull"
  gate_ok pushed
}

gate_wiring() {
  local full rec st tested only
  full="$(full_sha "$1")" || gate_fail wiring "wiring SHA '$1' does not resolve to a commit"
  git merge-base --is-ancestor "$full" HEAD || gate_fail wiring "wiring SHA ${full:0:10} is not an ancestor of HEAD"
  rec="$(git show "HEAD:$WIRING_RECORD" 2>/dev/null)" || gate_fail wiring "$WIRING_RECORD is not in HEAD"
  st="$(fm_get status <<<"$rec")"
  tested="$(fm_get tested_sha <<<"$rec")"
  only="$(fm_get consulted_only_workspace <<<"$rec")"
  [ "$st" = "pass" ] || gate_fail wiring "the wiring record status is '${st:-missing}', must be pass"
  [ "$tested" = "$full" ] || gate_fail wiring "the wiring record tested_sha is '${tested:-missing}', must be exactly $full"
  [ "$only" = "true" ] || gate_fail wiring "consulted_only_workspace is '${only:-missing}', must be true"
  grep -q 'WIRING TEST: PASS' <<<"$rec" || gate_fail wiring "the wiring record has no 'WIRING TEST: PASS' line"
  gate_ok wiring
}

# "<separator line> <last row line>" of the section 11 ledger table in a revision of the contract. Fails unless exactly one
# ledger header line follows the '## 11.' heading, directly followed by its separator line. The table block is the run of
# lines starting with '|' after the separator.
ledger_range() {
  git show "$1:$CONTRACT" 2>/dev/null | awk -v hdr="$LEDGER_HEADER" '
    { L[NR] = $0 }
    /^## 11\./ { in11 = 1; next }
    in11 && $0 == hdr { nh++; h = NR }
    END {
      if (nh != 1 || L[h + 1] !~ /^\|[-| :]+\|$/) exit 1
      last = h + 1
      while ((last + 1) in L && L[last + 1] ~ /^\|/) last++
      print h + 1, last
    }'
}

# True only when every change to the contract between the wiring SHA and HEAD is a dated ledger row inside the section 11
# table. Why this allowance exists: the orchestrator is the sole ledger writer (A14) and commits section 11 rows into this
# repository (aa65368, 70cfe9b). A ledger row is not part of the docs or the API the wiring test proved, so it cannot void
# the wiring pass; any other contract change (a section 10 amendment, an erratum, body text) can. The pre-ledger placeholder
# row was removed at aa65368, before any wiring SHA, so every later ledger commit adds or edits '| 20...' rows only.
#   (a) both revisions have one well-formed ledger table;
#   (b) every changed line (a +/- line, ignoring the file headers and the no-newline marker) is a '| 20YY-MM-DD |' row;
#   (c) every hunk lies inside the table rows of both revisions (a zero-length side anchors between the separator and the
#       last row).
contract_change_is_ledger_only() {
  local w="$1" oldr newr osep olast nsep nlast d rows commits
  oldr="$(ledger_range "$w")" || return 1
  newr="$(ledger_range HEAD)" || return 1
  read -r osep olast <<<"$oldr"
  read -r nsep nlast <<<"$newr"
  d="$(git diff -U0 --no-color --no-ext-diff "$w" HEAD -- "$CONTRACT")"
  [ -n "$d" ] || return 1
  rows="$(awk -v osep="$osep" -v olast="$olast" -v nsep="$nsep" -v nlast="$nlast" '
    BEGIN { ok = 1; hunks = 0; rows = 0; inh = 0 }
    /^@@ / {
      inh = 1; hunks++
      s = $0; sub(/^@@ -/, "", s)
      split(s, parts, " ")
      o = parts[1]; n = parts[2]; sub(/^\+/, "", n)
      ob = 1; nb = 1
      if (index(o, ",")) { split(o, oo, ","); oa = oo[1]; ob = oo[2] } else { oa = o }
      if (index(n, ",")) { split(n, nn, ","); na = nn[1]; nb = nn[2] } else { na = n }
      if (ob + 0 > 0) { if (oa + 0 < osep + 1 || oa + ob - 1 > olast) ok = 0 } else { if (oa + 0 < osep || oa + 0 > olast) ok = 0 }
      if (nb + 0 > 0) { if (na + 0 < nsep + 1 || na + nb - 1 > nlast) ok = 0 } else { if (na + 0 < nsep || na + 0 > nlast) ok = 0 }
      next
    }
    !inh { next }
    /^\\/ { next }
    /^[+-]/ { if ($0 ~ /^[+-]\| 20[0-9][0-9]-[0-9][0-9]-[0-9][0-9] \|/) rows++; else ok = 0; next }
    { ok = 0 }
    END { if (ok && hunks > 0 && rows > 0) print rows; else exit 1 }' <<<"$d")" || return 1
  commits="$(git log --format=%h "$w..HEAD" -- "$CONTRACT" | paste -sd, -)"
  echo "DIFF NOTE: ledger-only contract change ($rows row line(s) in section 11; commits: $commits)"
  return 0
}

gate_diff() {
  local full f names offenders=()
  full="$(full_sha "$1")" || gate_fail diff "wiring SHA '$1' does not resolve to a commit"
  names="$(git diff --no-renames --name-only -z "$full" HEAD | tr '\0' '\n')"
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    case "$f" in
      core/api.txt | providers/api.txt | keystore/api.txt | .planning/*) ;;
      "$CONTRACT") if ! contract_change_is_ledger_only "$full"; then offenders+=("$f"); fi ;;
      *) offenders+=("$f") ;;
    esac
  done <<<"$names"
  if [ "${#offenders[@]}" -gt 0 ]; then
    for f in "${offenders[@]}"; do echo "  changed after the wiring SHA: $f" >&2; done
    for f in "${offenders[@]}"; do
      if [ "$f" = "$CONTRACT" ]; then
        echo "  a peer or orchestrator commit after the wiring SHA changed the contract outside the section 11 ledger rows: relay to yahir-gsd-control-plane-f2; resolution is an isolated wiring rerun on a SHA that contains it" >&2
      fi
    done
    gate_fail diff "${#offenders[@]} path(s) changed since the wiring SHA ${full:0:10} outside core|providers|keystore/api.txt and .planning/ (the wiring pass would be void)"
  fi
  gate_ok diff
}

# Loads HEAD's waiver packet into PACKET and its '## Answer block' section into BLOCK (fails the named gate otherwise).
PACKET=""
BLOCK=""
load_packet() {
  PACKET="$(git show "HEAD:$WAIVER_PACKET" 2>/dev/null)" || gate_fail "$1" "$WAIVER_PACKET is not in HEAD"
  [ "$(grep -c '^## Answer block$' <<<"$PACKET" || true)" = 1 ] || gate_fail "$1" "the packet must hold exactly one '## Answer block' section"
  BLOCK="$(awk '$0 == "## Answer block" { f = 1; next } f && /^#/ { exit } f { print }' <<<"$PACKET")"
}

# A scalar field ("packet_status", "answered_by", "answered_at") of the answer block.
block_field() {
  awk -v k="$1" 'index($0, k ":") == 1 { sub("^" k ":[ \t]*", ""); sub(/[ \t\r]+$/, ""); print; exit }' <<<"$BLOCK"
}

# The answer recorded for a row id (empty when the id has no answer line).
answer_of() {
  awk -v id="$1" '$1 == "-" && $2 == id ":" { a = $3; sub(/\r$/, "", a); print a; exit }' <<<"$BLOCK"
}

# Ids of the packet table rows of category C (the pre-freeze API confirmations), in table order.
category_c_ids() {
  awk -F'|' '/^\| W[0-9][0-9] \|/ { id = $2; c = $3; gsub(/ /, "", id); gsub(/ /, "", c); if (c == "C") print id }' <<<"$PACKET"
}

# Diagnostic only (not in the preflight list: waiver already covers it). Plan 11-07 runs it before the api.txt baseline is
# dumped: every category C row must be answered ok, accept or waive, whatever the state of the other rows.
gate_prefreeze() {
  local status ids id ans
  load_packet prefreeze
  status="$(block_field packet_status)"
  ids="$(category_c_ids)"
  [ -n "$ids" ] || gate_fail prefreeze "no category C rows found"
  for id in $ids; do
    ans="$(answer_of "$id")"
    case "$ans" in
      ok | accept | waive) ;;
      needs-fix) gate_fail prefreeze "$id needs-fix: W is void; loop: API change in 11-02 scope, 11-03 docs gate, new W and isolated rerun in 11-06, then 11-07" ;;
      pending | "") gate_fail prefreeze "$id pending: the api.txt baseline waits for this pre-freeze answer" ;;
      carry-to-gate-2) gate_fail prefreeze "$id carry-to-gate-2: not valid for a pre-freeze row (the API freezes at the baseline)" ;;
      *) gate_fail prefreeze "$id answered '$ans': not valid for a pre-freeze row (use ok, accept or waive)" ;;
    esac
  done
  [ "$status" != "rejected" ] || gate_fail prefreeze "packet rejected (non-pre-freeze row): stop for the orchestrator"
  gate_ok prefreeze
}

gate_waiver() {
  local status by at ids_ans ids_tab id ans
  load_packet waiver
  status="$(block_field packet_status)"
  by="$(block_field answered_by)"
  at="$(block_field answered_at)"
  [ "$status" = "accepted" ] || gate_fail waiver "packet_status is '${status:-missing}', must be accepted: Yahir's answers to the waiver packet are a hard tag precondition"
  { [ -n "$by" ] && [ "$by" != "-" ]; } || gate_fail waiver "answered_by is '${by:--}'"
  { [ -n "$at" ] && [ "$at" != "-" ]; } || gate_fail waiver "answered_at is '${at:--}'"
  ids_ans="$(awk '/^- W[0-9][0-9]:/ { id = $2; sub(/:$/, "", id); print id }' <<<"$BLOCK" | sort)"
  ids_tab="$(awk -F'|' '/^\| W[0-9][0-9] \|/ { id = $2; gsub(/ /, "", id); print id }' <<<"$PACKET" | sort)"
  [ -n "$ids_tab" ] || gate_fail waiver "no item rows found in the packet table"
  [ "$ids_ans" = "$ids_tab" ] || gate_fail waiver "the answered ids ($(echo "$ids_ans" | tr '\n' ' ')) differ from the table ids ($(echo "$ids_tab" | tr '\n' ' '))"
  while read -r id ans; do
    case "$ans" in
      waive | accept | carry-to-gate-2 | ok) ;;
      *) gate_fail waiver "$id is answered '$ans'; every row must be waive, accept, carry-to-gate-2 or ok" ;;
    esac
  done < <(awk '/^- W[0-9][0-9]:/ { id = $2; sub(/:$/, "", id); a = $3; sub(/\r$/, "", a); print id, a }' <<<"$BLOCK")
  # A pre-freeze row cannot be carried past the freeze: the public API is frozen into api.txt before Gate-2 runs.
  for id in $(category_c_ids); do
    ans="$(answer_of "$id")"
    case "$ans" in
      ok | accept | waive) ;;
      *) gate_fail waiver "$id is a pre-freeze (category C) row answered '$ans'; only ok, accept or waive let the API freeze" ;;
    esac
  done
  gate_ok waiver
}

# ---------------------------------------------------------------------------------------------------------------------
# Release gates (ROADMAP SC1 order), all on the content of HEAD
# ---------------------------------------------------------------------------------------------------------------------
gate_check() {
  local d
  mk_tmp
  d="$TMPROOT/check-src"
  archive_head "$d"
  if ! (cd "$d" && ./gradlew check --no-build-cache --console=plain) >"$TMPROOT/check.log" 2>&1; then
    tail -25 "$TMPROOT/check.log" >&2
    gate_fail check "./gradlew check failed in a clean archive of HEAD (log tail above)"
  fi
  grep -q 'BUILD SUCCESSFUL' "$TMPROOT/check.log" || gate_fail check "no BUILD SUCCESSFUL in the ./gradlew check output"
  rm -rf "$d"
  gate_ok check
}

gate_api_dump() {
  local out m
  mk_tmp
  out="$TMPROOT/apidump"
  if ! "$REPO/scripts/api-dump-isolated.sh" --head --out "$out" >"$TMPROOT/apidump.log" 2>&1; then
    tail -15 "$TMPROOT/apidump.log" >&2
    gate_fail api-dump "scripts/api-dump-isolated.sh --head failed"
  fi
  for m in $MODULES; do
    git cat-file -e "HEAD:$m/api.txt" 2>/dev/null || gate_fail api-dump "$m/api.txt is not tracked in HEAD"
    git show "HEAD:$m/api.txt" >"$TMPROOT/committed-$m.api.txt"
    [ -f "$out/$m.api.sig" ] || gate_fail api-dump "$m: the fresh dump is missing"
    cmp -s "$out/$m.api.sig" "$TMPROOT/committed-$m.api.txt" \
      || gate_fail api-dump "$m: a fresh apiDump of HEAD differs from the api.txt committed in HEAD"
  done
  gate_ok api-dump
}

gate_hygiene() {
  local out
  if ! out="$(PRE_RELEASE=0 "$REPO/scripts/verify-repo-hygiene.sh" 2>&1)"; then
    echo "$out" >&2
    gate_fail hygiene "PRE_RELEASE=0 scripts/verify-repo-hygiene.sh failed"
  fi
  grep -q '^HYGIENE OK$' <<<"$out" || gate_fail hygiene "no HYGIENE OK line"
  gate_ok hygiene
}

gate_api_check() {
  local d m log
  mk_tmp
  d="$TMPROOT/apicheck-src"
  log="$TMPROOT/apicheck.log"
  archive_head "$d"
  if ! (cd "$d" && ./gradlew apiCheck --no-build-cache --console=plain) >"$log" 2>&1; then
    tail -25 "$log" >&2
    gate_fail api-check "./gradlew apiCheck failed in a clean archive of HEAD (log tail above)"
  fi
  grep -q 'BUILD SUCCESSFUL' "$log" || gate_fail api-check "no BUILD SUCCESSFUL in the ./gradlew apiCheck output"
  # A task line with a status suffix (SKIPPED, UP-TO-DATE, NO-SOURCE, FROM-CACHE) did not run the check: a vacuous pass.
  for m in $MODULES; do
    grep -Eq "^> Task :$m:metalavaCheckCompatibility[A-Za-z]*$" "$log" \
      || gate_fail api-check "$m: metalavaCheckCompatibility did not execute (skipped, up to date or absent), so the compatibility check was vacuous"
  done
  rm -rf "$d"
  gate_ok api-check
}

gate_dry_run() {
  local tag="$1" dd log
  mk_tmp
  dd="$TMPROOT/dry"
  log="$TMPROOT/dryrun.log"
  mkdir -p "$dd"
  if ! TMPDIR="$dd" DRYRUN_VERSION="$tag" KEEP_WORK=1 "$REPO/scripts/jitpack-dry-run.sh" >"$log" 2>&1; then
    tail -25 "$log" >&2
    gate_fail dry-run "scripts/jitpack-dry-run.sh failed with VERSION=$tag"
  fi
  grep -q -- "DRY RUN OK version=$tag " "$log" || gate_fail dry-run "the dry run did not print 'DRY RUN OK version=$tag'"
  DRY_M2="$(grep -oE 'm2=[^ ]+ \(kept\)' "$log" | tail -1 | sed -E 's/^m2=//; s/ \(kept\)$//')"
  [ -d "$DRY_M2" ] || gate_fail dry-run "could not locate the dry run's isolated maven-local"
  gate_ok dry-run
}

# Key-shaped strings, the A10 fixture name and (when the host has the fixture) its system prompt, over the TRACKED content
# of HEAD. Every needle and regex is assembled from fragments so this file can never match itself. Findings print
# path:line and at most a 10-character prefix, never the match.
gate_leak() {
  local out rc=0
  out="$(python3 - <<'PY'
import json, os, re, subprocess, sys

FIXTURE = "sb-a10" + "-fixture"
KEY_RES = [
    ("provider-key", re.compile(rb"(?<![A-Za-z0-9_\-])s" + rb"k-(?:(?:ant|or|proj|svcacct|admin)-[A-Za-z0-9_\-]{12,}|[A-Za-z0-9]{20,})")),
    ("google-key", re.compile(rb"AI" + rb"za[0-9A-Za-z_\-]{35}")),
    ("bearer-token", re.compile(rb"Bea" + rb"rer[ \t]+[A-Za-z0-9._~+/=\-]{20,}")),
]
ALLOW_MARKER = b"secret-scan: " + b"allow"
# Paths (exact) that may name the fixture, each with its reason. Anything under .planning/ also may: planning prose and
# filtered evidence name the path.
FIXTURE_NAME_PATHS = {
    ".gitignore": "the ignore rule that keeps the fixture out of the tree",
    "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/AndroidFixtureSources.kt":
        "debug-only loader that names the file it reads; never published (A10)",
    "scripts/run-sample-gate1.sh": "pushes the host-local fixture to the TESTER device; names the path only",
    "scripts/verify-repo-hygiene.sh": "guard: asserts the fixture is ignored and untracked",
    "scripts/verify-sample-device-guard.sh": "guard: asserts the sample never reads or commits the fixture",
}
# Exact path plus fixed substring for key-shaped hits that are legitimate. Each entry states why.
KEY_ALLOW = {
    # A made-up Bearer value the transport test asserts is sent for a deliberately invalid credential.
    "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCaptureRunTest.kt":
        ("Bea" + "rer invalid-credential-for-capture",),   # assembled so this script never matches itself
}


def run(*args):
    return subprocess.run(args, capture_output=True, check=True).stdout


paths = [p for p in run("git", "ls-tree", "-r", "-z", "--name-only", "HEAD").split(b"\0") if p]
cat = subprocess.Popen(["git", "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)


def blob(path):
    cat.stdin.write(b"HEAD:" + path + b"\n")
    cat.stdin.flush()
    head = cat.stdout.readline().split()
    if len(head) != 3 or head[1] != b"blob":
        return None
    data = cat.stdout.read(int(head[2]))
    cat.stdout.read(1)
    return data


def placeholder(body):
    body = re.sub(rb"^(api\d+|v\d+)-", b"", body)
    return len(set(body.lower())) < 4


findings = []
needle = None
content_check = "skipped(no local fixture)"
for cand in ("sample/src/debug/assets/" + FIXTURE + ".json", "sample/src/debug/assets/" + FIXTURE + ".v2.json"):
    if os.path.isfile(cand):
        try:
            doc = json.load(open(cand, encoding="utf-8"))
            system = doc.get("system") if isinstance(doc, dict) else None
        except (OSError, ValueError):
            system = None
        if not isinstance(system, str) or len(system) < 16:
            findings.append("fixture present on the host but its system prompt cannot be read: refusing to skip the content check")
        else:
            needle = system[:64]
            content_check = "ran"
        break
raw_needles = []
if needle is not None:
    raw_needles = [needle.encode("utf-8"), json.dumps(needle)[1:-1].encode("utf-8")]

fx = FIXTURE.encode()
for path in paths:
    p = path.decode("utf-8", "replace")
    if FIXTURE in p:
        findings.append("%s: a tracked path contains the fixture name" % p)
    data = blob(path)
    if data is None or b"\0" in data[:8192]:
        continue
    for needle_bytes in raw_needles:
        if needle_bytes in data:
            findings.append("%s: tracked content contains the fixture's system prompt" % p)
    for n, line in enumerate(data.split(b"\n"), 1):
        if fx in line and not (p in FIXTURE_NAME_PATHS or p.startswith(".planning/")):
            findings.append("%s:%d: names the fixture outside the allow-list" % (p, n))
        if ALLOW_MARKER in line:
            continue
        for kind, rx in KEY_RES:
            for m in rx.finditer(line):
                text = m.group(0)
                body = text.split(b"-", 2)[-1] if kind == "provider-key" else text[4:]
                if placeholder(body):
                    continue
                if any(sub.encode() in line for sub in KEY_ALLOW.get(p, ())):
                    continue
                findings.append("%s:%d: %s shape (%s...)" % (p, n, kind, text[:10].decode("latin-1")))

cat.stdin.close()
cat.wait()
print("content_check=" + content_check)
for f in findings:
    sys.stderr.write("  LEAK " + f + "\n")
sys.exit(1 if findings else 0)
PY
)" || rc=$?
  if [ "$rc" -ne 0 ]; then
    gate_fail leak "the tracked content of HEAD carries a fixture reference or a key-shaped string (findings above)"
  fi
  echo "$out"
  gate_ok leak
}

# The published coordinates carry the tag. JitPack exports VERSION=<tag> for a tag build (it exported the commit SHA for the
# Phase 1 SHA builds), the root build reads VERSION into engineVersion, and the dry run exports VERSION=<tag> the same way.
# So when every POM, .module and the providers/keystore -> core dependency in the dry run's maven-local carry <tag>, the
# coordinates JitPack serves for the tag build carry exactly that string. engineVersion's 0.0.0-local default is a local
# placeholder that never reaches a publication.
gate_version() {
  local tag="$1" group
  [ -n "$DRY_M2" ] && [ -d "$DRY_M2" ] || gate_fail version "no dry-run maven-local to inspect (run the dry-run gate first)"
  git show HEAD:build.gradle.kts | grep -q 'providers.environmentVariable("VERSION")' \
    || gate_fail version "the root build.gradle.kts does not read the VERSION environment variable for engineVersion"
  if git show HEAD:jitpack.yml | grep -Ev '^[[:space:]]*#' | grep -q 'VERSION'; then
    gate_fail version "jitpack.yml sets VERSION itself; JitPack must supply it"
  fi
  group="$(git show HEAD:gradle.properties | awk -F= '/^engineGroup=/ { print $2; exit }')"
  [ -n "$group" ] || gate_fail version "engineGroup is missing from gradle.properties"
  python3 - "$DRY_M2" "$group" "$tag" <<'PY' || gate_fail version "a published artifact does not carry version $tag (reason above)"
import json, os, sys
import xml.etree.ElementTree as ET

m2, group, tag = sys.argv[1:4]
core = "voice-action-engine-core"
bad = []
for m in ("core", "providers", "keystore"):
    art = "voice-action-engine-" + m
    d = os.path.join(m2, *group.split("."), art, tag)
    pom = os.path.join(d, "%s-%s.pom" % (art, tag))
    mod = os.path.join(d, "%s-%s.module" % (art, tag))
    if not os.path.isfile(pom):
        bad.append("%s: POM %s-%s.pom is missing" % (m, art, tag))
        continue
    root = ET.parse(pom).getroot()
    ns = root.tag[: root.tag.index("}") + 1] if root.tag.startswith("{") else ""
    ver = root.find(ns + "version")
    if ver is None or (ver.text or "").strip() != tag:
        bad.append("%s: the POM project version is not %s" % (m, tag))
    if m != "core":
        deps = [x for x in root.iter(ns + "dependency") if (x.findtext(ns + "artifactId") or "").strip() == core]
        if not deps:
            bad.append("%s: the POM does not depend on %s" % (m, core))
        for x in deps:
            if (x.findtext(ns + "version") or "").strip() != tag:
                bad.append("%s: the POM depends on %s at a version other than %s" % (m, core, tag))
    if not os.path.isfile(mod):
        bad.append("%s: %s-%s.module is missing" % (m, art, tag))
        continue
    doc = json.load(open(mod, encoding="utf-8"))
    if doc.get("component", {}).get("version") != tag:
        bad.append("%s: the .module component version is not %s" % (m, tag))
    for variant in doc.get("variants", []):
        for dep in variant.get("dependencies", []):
            if dep.get("module") == core and dep.get("version", {}).get("requires") != tag:
                bad.append("%s: a .module dependency on %s is not at %s" % (m, core, tag))
for b in bad:
    sys.stderr.write("  " + b + "\n")
sys.exit(1 if bad else 0)
PY
  gate_ok version
}

# ---------------------------------------------------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------------------------------------------------
call_gate() {
  local g="$1" tag="$2" wiring="$3"
  case "$g" in
    tag-format) gate_tag_format "$tag" "$wiring" ;;
    tags-absent) gate_tags_absent "$tag" ;;
    create-tag) gate_create_tag ;;
    prefreeze) gate_prefreeze ;;
    clean) gate_clean ;;
    pushed) gate_pushed ;;
    wiring) gate_wiring "$wiring" ;;
    diff) gate_diff "$wiring" ;;
    waiver) gate_waiver ;;
    check) gate_check ;;
    api-dump) gate_api_dump ;;
    hygiene) gate_hygiene ;;
    api-check) gate_api_check ;;
    dry-run) gate_dry_run "$tag" ;;
    leak) gate_leak ;;
    version) gate_version "$tag" ;;
    *) usage ;;
  esac
}

run_gate() {
  local name="$1"
  shift
  case "$name" in
    tag-format) { [ $# -eq 1 ] || [ $# -eq 2 ]; } || usage; gate_tag_format "$@" ;;
    tags-absent) [ $# -eq 1 ] || usage; gate_tags_absent "$1" ;;
    wiring) [ $# -eq 1 ] || usage; gate_wiring "$1" ;;
    diff) [ $# -eq 1 ] || usage; gate_diff "$1" ;;
    dry-run) [ $# -eq 1 ] || usage; gate_dry_run "$1" ;;
    version) [ $# -eq 1 ] || usage; gate_dry_run "$1"; gate_version "$1" ;;
    create-tag | clean | pushed | waiver | prefreeze | check | api-dump | hygiene | api-check | leak)
      [ $# -eq 0 ] || usage
      call_gate "$name" "" ""
      ;;
    *) usage ;;
  esac
}

run_preflight() {
  local tag="$1" wiring="$2" full g
  full="$(full_sha "$wiring")" || gate_fail tag-format "wiring SHA '$wiring' does not resolve to a commit"
  for g in "${GATE_ORDER[@]}"; do call_gate "$g" "$tag" "$full"; done
  echo "PREFLIGHT OK tag=$tag commit=$(git rev-parse HEAD) wiring=$full gates=$(IFS=,; echo "${GATE_ORDER[*]}")"
}

cut_fail() { printf 'RELEASE CUT FAIL: %s\n' "$1" >&2; exit 1; }

run_cut() {
  local tag="$1" wiring="$2" approved="$3" full head group msg tagobj peeled remote expect
  [[ "$approved" =~ ^[0-9a-f]{10,40}$ ]] || cut_fail "approvedCommit must be a full SHA or a unique prefix of at least 10 hex digits"
  full="$(full_sha "$approved")" || cut_fail "approvedCommit '$approved' does not resolve to a single commit"
  head="$(git rev-parse HEAD)"
  [ "$full" = "$head" ] || cut_fail "approvedCommit ${full:0:10} is not HEAD ${head:0:10}"
  run_preflight "$tag" "$wiring"
  # Time of check: HEAD and the tag state must not have moved while the (long) preflight ran.
  [ "$(git rev-parse HEAD)" = "$full" ] || cut_fail "HEAD moved during the preflight"
  gate_tags_absent "$tag"
  group="$(awk -F= '/^engineGroup=/ { print $2; exit }' gradle.properties)"
  msg="voice-action-engine $tag

Coordinates (JitPack, per module):
  $group:voice-action-engine-core:$tag
  $group:voice-action-engine-providers:$tag
  $group:voice-action-engine-keystore:$tag
Wiring-tested SHA: $(full_sha "$wiring")
Contract: CROSS-REPO-SCOPE-CONTRACT.md section 6.2 steps 1-7.
Section 11 ledger row: messaged to the orchestrator, the sole ledger writer (A14)."
  git tag -a "$tag" -m "$msg" "$full"
  [ "$(git cat-file -t "refs/tags/$tag")" = "tag" ] || cut_fail "refs/tags/$tag is not an annotated tag object"
  peeled="$(git rev-parse "refs/tags/$tag^{commit}")"
  [ "$peeled" = "$full" ] || cut_fail "the tag peels to ${peeled:0:10}, not the approved commit ${full:0:10}"
  tagobj="$(git rev-parse "refs/tags/$tag")"
  if ! git push origin "refs/tags/$tag"; then
    echo "CUT PUSH FAILED: local annotated tag $tag exists at $full; it was not deleted or moved; retry exactly: git push origin refs/tags/$tag" >&2
    exit 3
  fi
  remote="$(git ls-remote --tags origin | sort)"
  expect="$(printf '%s\trefs/tags/%s\n%s\trefs/tags/%s^{}\n' "$tagobj" "$tag" "$full" "$tag" | sort)"
  [ "$remote" = "$expect" ] || cut_fail "origin does not list exactly the tag and its peeled commit after the push (got: $(echo "$remote" | tr '\n' ' '))"
  echo "CUT OK tag=$tag commit=$full tag_object=$tagobj pushed=refs/tags/$tag"
}

# ---------------------------------------------------------------------------------------------------------------------
# selftest happy: a temp clone and a LOCAL bare remote; the real repository is only read
# ---------------------------------------------------------------------------------------------------------------------
snapshot_real() {
  local remote
  printf 'tags: %s\n' "$(git -C "$REPO" tag --list | tr '\n' ' ')"
  remote="$(git -C "$REPO" ls-remote --tags origin)" || { echo "snapshot: cannot list origin tags" >&2; return 1; }
  printf 'remote tags: %s\n' "$(echo "$remote" | tr '\n' ' ')"
  printf 'status: %s\n' "$(git -C "$REPO" status --porcelain --untracked-files=all -- . "${CLEAN_EXCLUDES[@]}" | tr '\n' ';')"
  printf 'config.json: %s\n' "$(sha256sum "$REPO/.planning/config.json" 2>/dev/null | cut -d' ' -f1)"
  printf 'contract: %s\n' "$(sha256sum "$REPO/$CONTRACT" 2>/dev/null | cut -d' ' -f1)"
}

selftest_happy() {
  local sb bare clone w head before after out fetch_url push_url n rec_tag
  sf() { echo "RELEASE SELFTEST FAIL: $*" >&2; exit 1; }
  mk_tmp
  sb="$TMPROOT/sandbox"
  bare="$sb/origin.git"
  clone="$sb/work"
  mkdir -p "$sb"
  before="$(snapshot_real)" || sf "cannot snapshot the real repository"

  # 1. A bare repository holding the real HEAD commit as main, and a working clone of it. The push target is a local path.
  git init --quiet --bare --initial-branch=main "$bare"
  git -C "$REPO" push --quiet "$bare" HEAD:refs/heads/main
  git clone --quiet "$bare" "$clone"
  git -C "$clone" config user.name "release-cut selftest"
  git -C "$clone" config user.email "selftest@invalid"
  git -C "$clone" config core.hooksPath /dev/null
  git -C "$clone" config commit.gpgsign false
  git -C "$clone" config tag.gpgsign false

  # 2. Overlay the real working-tree scripts/ and the phase directory, so uncommitted script edits are what gets tested.
  tar -C "$REPO" --exclude='*.done.json' -cf - scripts .planning/phases/11-cut-v1-0-0 | tar -C "$clone" -xf -
  git -C "$clone" add -- scripts .planning/phases/11-cut-v1-0-0
  if ! git -C "$clone" diff --cached --quiet; then git -C "$clone" commit --quiet -m "selftest: overlay working-tree scripts and phase directory"; fi
  w="$(git -C "$clone" rev-parse HEAD)"

  # The clone has the committed config.json; pin the value the create-tag guard reads (uncommitted, under .planning/).
  mkdir -p "$clone/.planning"
  printf '{"git":{"create_tag":false}}\n' >"$clone/.planning/config.json"

  # 3. A synthetic wiring record (status pass for W) and an accepted packet with every row ok.
  cat >"$clone/$WIRING_RECORD" <<EOF
---
status: pass
tested_sha: $w
consulted_only_workspace: true
---
SANDBOX SYNTHETIC wiring record.
WIRING TEST: PASS checks=9
EOF
  sed -i -e 's/^packet_status: .*/packet_status: accepted/' \
    -e 's/^answered_by: .*/answered_by: SANDBOX SYNTHETIC/' \
    -e 's/^answered_at: .*/answered_at: 2000-01-01/' \
    -e 's/^- \(W[0-9][0-9]\): .*/- \1: ok/' "$clone/$WAIVER_PACKET"
  git -C "$clone" add -- "$WIRING_RECORD" "$WAIVER_PACKET"
  git -C "$clone" commit --quiet -m "selftest: synthetic wiring record and accepted packet"

  # 4. The three api.txt dumps (the freeze), committed and pushed to the sandbox remote.
  if [ -f "$REPO/local.properties" ]; then cp "$REPO/local.properties" "$clone/"; fi
  (cd "$clone" && ./gradlew apiDump --console=plain) >"$sb/apidump.log" 2>&1 || { tail -20 "$sb/apidump.log" >&2; sf "apiDump failed in the sandbox clone"; }
  git -C "$clone" add -- core/api.txt providers/api.txt keystore/api.txt
  git -C "$clone" commit --quiet -m "selftest: commit the api.txt baseline"

  # 5. Never push or cut before the clone's remote is proven to be the local bare path.
  fetch_url="$(git -C "$clone" remote get-url origin)"
  push_url="$(git -C "$clone" remote get-url --push origin)"
  [ "$fetch_url" = "$bare" ] && [ "$push_url" = "$bare" ] || sf "the sandbox origin is not the local bare path ($fetch_url, $push_url)"
  case "$fetch_url$push_url" in *github.com* | *://* | *@*) sf "the sandbox origin looks like a network remote" ;; esac
  case "$(cd "$clone" && git rev-parse --show-toplevel)" in "$TMPROOT"/*) ;; *) sf "the sandbox clone is not under the temp root" ;; esac
  git -C "$clone" push --quiet origin main
  head="$(git -C "$clone" rev-parse HEAD)"

  # 6. preflight, then cut, run by absolute path with the clone as the working directory.
  echo "--- sandbox preflight (W=$w)"
  (cd "$clone" && "$SELF" preflight "$RELEASE_TAG" "$w") >"$sb/preflight.log" 2>&1 || { tail -30 "$sb/preflight.log" >&2; sf "sandbox preflight failed"; }
  cat "$sb/preflight.log"
  grep -q "^PREFLIGHT OK tag=$RELEASE_TAG commit=$head wiring=$w gates=" "$sb/preflight.log" || sf "no PREFLIGHT OK line for the sandbox HEAD"
  echo "--- sandbox cut"
  (cd "$clone" && "$SELF" cut "$RELEASE_TAG" "$w" "$head") >"$sb/cut.log" 2>&1 || { tail -30 "$sb/cut.log" >&2; sf "sandbox cut failed"; }
  cat "$sb/cut.log"
  grep -q "^CUT OK tag=$RELEASE_TAG commit=$head " "$sb/cut.log" || sf "no CUT OK line"

  # 7. The bare repository holds exactly one tag, annotated, peeling to the clone's HEAD.
  n="$(git -C "$bare" tag --list | wc -l | tr -d ' ')"
  [ "$n" = 1 ] || sf "the sandbox remote holds $n tags, expected exactly one"
  rec_tag="$(git -C "$bare" tag --list)"
  [ "$rec_tag" = "$RELEASE_TAG" ] || sf "the sandbox remote tag is '$rec_tag'"
  [ "$(git -C "$bare" cat-file -t "refs/tags/$RELEASE_TAG")" = tag ] || sf "the sandbox tag is not annotated"
  [ "$(git -C "$bare" rev-parse "refs/tags/$RELEASE_TAG^{commit}")" = "$head" ] || sf "the sandbox tag does not peel to the clone's HEAD"
  echo "sandbox remote: exactly one annotated tag $RELEASE_TAG peeling to ${head:0:10}"

  # 8. The real repository is unchanged.
  after="$(snapshot_real)" || sf "cannot snapshot the real repository afterwards"
  [ "$before" = "$after" ] || { echo "$before" >&2; echo "$after" >&2; sf "the real repository changed during the selftest"; }
  echo "real-repo guard: unchanged (tags, remote tags, status, config.json, contract)"
  echo "RELEASE SELFTEST OK happy=1 negatives=0 positives=0"
}

case "${1:-}" in
  preflight) [ $# -eq 3 ] || usage; run_preflight "$2" "$3" ;;
  cut) [ $# -eq 4 ] || usage; run_cut "$2" "$3" "$4" ;;
  gate) [ $# -ge 2 ] || usage; shift; run_gate "$@" ;;
  selftest) { [ $# -eq 2 ] && [ "$2" = happy ]; } || usage; selftest_happy ;;
  *) usage ;;
esac
