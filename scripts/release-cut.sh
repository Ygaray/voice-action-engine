#!/usr/bin/env bash
# release-cut.sh - the gated release script for any vMAJOR.MINOR.PATCH release tag, passed as an argument (Phase 11, D-01;
# made tag-agnostic for the v1.0.x patch line, WR-02). Modelled on stt-engine's release-android.sh: every check that can run before the tag runs before the tag, and the irreversible act (pushing the
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
#   selftest happy|negative|all                 prove the happy path (happy) and/or that every gate goes red on a planted violation
#                                               (negative), in a sandbox (temp clones + LOCAL bare remotes); all = happy, then negative
# Exit codes: 0 ok, 1 a gate failed (RELEASE GATE FAIL <gate>: <why>), 2 usage (RELEASE USAGE: ...), 3 the push failed.
#
# NO GATE MAY BE SKIPPED. There is no bypass flag and no environment lever, on purpose: the failures these gates catch
# are exactly the ones that cannot be undone once the tag exists. `preflight` is not a bypass either: it runs every gate
# and stops before the irreversible step; `cut` runs the same gates again itself.
#
# GATES, in the order preflight runs them. Entry guards first (cheap, fail fast), then the six ROADMAP SC1 release gates.
#   1  tag-format   the tag is in strict vMAJOR.MINOR.PATCH form; the wiring SHA resolves to a commit
#   2  tags-absent  THAT tag exists neither locally nor on origin, and it is strictly newer than every existing release tag
#                   (a release tag is a vMAJOR.MINOR.PATCH tag; other tags are ignored)
#   3  create-tag   .planning/config.json git.create_tag is exactly false (D-02: GSD's milestone close must not tag)
#   4  clean        nothing staged; no uncommitted or untracked file outside the orchestrator bookkeeping paths
#   5  pushed       HEAD equals origin/main exactly
#   6  wiring       the wiring SHA is an ancestor of HEAD and the release's WIRING-RERUN.md (read from HEAD) passes for exactly it
#   7  diff         since the wiring SHA only the module api.txt files (scripts/modules.list), paths under .planning/ and
#                   (ledger rows only) the contract's section 11 table changed
#   8  waiver       a PATCH release may carry, in HEAD, a no-waiver statement .planning/releases/<tag>/NO-WAIVERS.md (the
#                   lines "tag: <tag>" and "no waivers: patch release"): then there is nothing to answer. Otherwise the
#                   waiver packet in HEAD is accepted with no pending row, at least one pre-freeze (category C) row exists
#                   and every one is answered ok, accept or waive (Yahir's answers are a hard tag precondition)
#   -- the six ROADMAP SC1 release gates, all on the content of HEAD --
#   9  check        ./gradlew check green in a clean archive of HEAD
#   10 api-dump     a fresh apiDump of HEAD (made in an isolated copy, so no tracked file is ever rewritten) equals the
#                   module api.txt files committed in HEAD, byte for byte
#   11 hygiene      PRE_RELEASE=0 repository hygiene (api.txt tracked, no fixture, no baseline), the module manifest, and the
#                   :stt confinement (scripts/verify-stt-confinement.sh: only :voice-adapter may pull the speech engine)
#   12 api-check    the committed api.txt is checked against the one released in the previous release tag (a patch release
#                   must be byte-identical to it, a minor or major release may only add lines; a module whose directory is
#                   absent from the previous tag is new in this release and needs a real committed dump instead), then
#                   ./gradlew apiCheck is green AND every module's compatibility task actually executed
#   13 dry-run      clean-clone JitPack dry run from jitpack.yml's install list (never :sample), VERSION=<tag>
#   14 leak         tracked-content scan for the A10 fixture name and key-shaped strings
#   15 version      the published coordinates carry the tag version (POM, .module; every dependsOnCore=yes module depends on
#                   core at the tag, every other non-core module does not depend on core)
# That is 15 gates; the PREFLIGHT OK line lists the gates it ran, in order. One more gate exists only as a DIAGNOSTIC and is
# not part of preflight (waiver already covers it): `prefreeze`, run before the api.txt baseline is dumped (plan 11-07).
# It requires every category C (pre-freeze API confirmation) row of the waiver packet to be answered ok, accept or waive,
# whatever the state of the other rows.
#
# GATE ARGUMENTS (pinned; a wrong count or an unknown gate is a usage error, exit 2, never a gate verdict):
#   gate tag-format <tag> [<wiringSHA>]   gate tags-absent <tag>        gate wiring <wiringSHA>   gate diff <wiringSHA>
#   gate dry-run <tag>                    gate version <tag>            gate waiver <tag>         gate api-check <tag>
#   gate api-baseline <tag>               (diagnostic, outside preflight: only the api.txt baseline half of api-check, no Gradle)
#   every other gate takes no argument
#
# The script only READS the repository's .planning/config.json and CROSS-REPO-SCOPE-CONTRACT.md. The helper scripts it
# calls are taken from the repository under test ($REPO/scripts/), so the tree being released supplies its own tooling.
set -euo pipefail
export LC_ALL=C GIT_TERMINAL_PROMPT=0

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "RELEASE USAGE: run inside the repository" >&2; exit 2; }
cd "$REPO"

# A release tag: strict vMAJOR.MINOR.PATCH, no leading zeros, no suffix.
SEMVER_RE='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
# The selftest sandbox cuts SELFTEST_TAG on top of a synthetic previous release SELFTEST_PRIOR_TAG (a patch release).
SELFTEST_PRIOR_TAG="v1.0.0"
SELFTEST_TAG="v1.0.1"
# A patch release with no waivers states it here, in HEAD, one directory per tag.
NO_WAIVER_DIR=".planning/releases"
NO_WAIVER_LINE="no waivers: patch release"
# The stable per-release directory for this release's wiring record and waiver packet. It survives the milestone archive
# (a phase directory does not), which is why the v1.0 locations under the archived Phase 11 directory are gone.
RELEASE_DIR=".planning/releases/v1.1.0"
# The record written after the isolated wiring rerun: frontmatter status, tested_sha, consulted_only_workspace. Phase 19
# plan 14 writes it for Phase 19's wiring SHA; Phase 20 rewrites it for its own final SHA (the previous release's record
# stays in git history at that release's tag).
WIRING_RECORD="$RELEASE_DIR/WIRING-RERUN.md"
# The waiver packet and its answer block (grammar from 11-01), written by Phase 20. Used when a release has no no-waiver
# statement.
WAIVER_PACKET="$RELEASE_DIR/WAIVER-PACKET.md"
# The api.txt dumps of every published module (HEAD's scripts/modules.list), committed with the first release that carries
# each module and kept from then on as the released-API baseline.
HEAD_MANIFEST="$(git show HEAD:scripts/modules.list 2>/dev/null)" || { echo "RELEASE USAGE: HEAD has no scripts/modules.list" >&2; exit 2; }
# Module names (first column) of the non-comment 5-field rows of a manifest read on stdin, in file order.
manifest_names() { awk '{ sub(/#.*/, "") } NF == 5 { printf "%s%s", (n++ ? " " : ""), $1 } END { print "" }'; }
MODULES="$(manifest_names <<<"$HEAD_MANIFEST")"
# The artifactId (third column) of module $1 in HEAD's manifest.
vae_artifact_of() { awk -v m="$1" '{ sub(/#.*/, "") } NF == 5 && $1 == m { print $3; exit }' <<<"$HEAD_MANIFEST"; }
# The kotlinPackage (fourth column) of module $1 in HEAD's manifest: the last segment of its package under io.github.ygaray.voiceactionengine.
vae_pkg_of() { awk -v m="$1" '{ sub(/#.*/, "") } NF == 5 && $1 == m { print $4; exit }' <<<"$HEAD_MANIFEST"; }
[ -n "$MODULES" ] || { echo "RELEASE USAGE: HEAD's scripts/modules.list lists no module" >&2; exit 2; }
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
# Writes HEAD's manifest into the temp root (for the python helper) and prints its path.
manifest_head_file() { mk_tmp; printf '%s\n' "$HEAD_MANIFEST" >"$TMPROOT/modules.list"; printf '%s\n' "$TMPROOT/modules.list"; }
# True when $1 is the api.txt of a manifest module (exact paths, no glob).
is_module_api() { local m; for m in $MODULES; do [ "$1" = "$m/api.txt" ] && return 0; done; return 1; }
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
    echo "RELEASE USAGE: release-cut.sh preflight <tag> <wiringSHA> | cut <tag> <wiringSHA> <approvedCommit> | gate <name> [args] | selftest happy|negative|all"
    echo "  gate tag-format <tag> [<wiringSHA>]"
    echo "  gate tags-absent <tag>"
    echo "  gate wiring <wiringSHA>"
    echo "  gate diff <wiringSHA>"
    echo "  gate dry-run <tag>"
    echo "  gate version <tag>"
    echo "  gate waiver <tag>"
    echo "  gate api-check <tag>"
    echo "  gate api-baseline <tag>   (diagnostic: only the api.txt baseline half of api-check, no Gradle)"
    echo "  gate create-tag | clean | pushed | prefreeze | check | api-dump | hygiene | leak   (no argument)"
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
# Release tags (vMAJOR.MINOR.PATCH only) known locally or on origin, one per line, unsorted and deduplicated. Fails when
# origin cannot be listed, so a network problem is never read as "no tags".
release_tags() {
  local remote
  remote="$(git ls-remote --tags origin 2>/dev/null)" || return 1
  { git tag --list; printf '%s\n' "$remote" | awk 'NF == 2 { print $2 }' | sed -e 's|^refs/tags/||' -e 's|\^{}$||'; } \
    | grep -E "$SEMVER_RE" | sort -u || true
}

# Stdin: release tags. Prints the greatest one that is strictly older than $1 (version order), or nothing.
greatest_below() {
  local tag="$1" t best=""
  while IFS= read -r t; do
    [ -n "$t" ] && [ "$t" != "$tag" ] || continue
    [ "$(printf '%s\n%s\n' "$t" "$tag" | sort -V | head -1)" = "$t" ] || continue
    if [ -z "$best" ] || [ "$(printf '%s\n%s\n' "$best" "$t" | sort -V | tail -1)" = "$t" ]; then best="$t"; fi
  done
  printf '%s' "$best"
}

gate_tag_format() {
  local tag="$1" sha="${2:-}"
  [[ "$tag" =~ $SEMVER_RE ]] || gate_fail tag-format "tag '$tag' is not in strict vMAJOR.MINOR.PATCH form"
  if [ -n "$sha" ]; then
    full_sha "$sha" >/dev/null || gate_fail tag-format "wiring SHA '$sha' does not resolve to a commit"
  fi
  gate_ok tag-format
}

# The tag being cut must not exist yet, here or on origin (a tag is immutable: JitPack caches the build for good), and it
# must be strictly newer than every release tag that does exist, so a later number can never be cut before an earlier one.
gate_tags_absent() {
  local tag="$1" remote_tags known newest
  if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
    gate_fail tags-absent "tag $tag already exists locally; a tag is immutable, so the fix is a new patch tag"
  fi
  if ! remote_tags="$(git ls-remote --tags origin "refs/tags/$tag" 2>/dev/null)"; then
    gate_fail tags-absent "cannot list the tags on origin (network or remote problem)"
  fi
  [ -z "$remote_tags" ] || gate_fail tags-absent "origin already has tag ref(s) (first: $(echo "$remote_tags" | head -1 | cut -f2)); refusing to cut $tag"
  known="$(release_tags)" || gate_fail tags-absent "cannot list the tags on origin (network or remote problem)"
  newest="$(printf '%s\n%s\n' "$known" "$tag" | grep -E "$SEMVER_RE" | sort -V | tail -1)"
  [ "$newest" = "$tag" ] \
    || gate_fail tags-absent "$tag is not newer than the existing release tag $newest; tags only move forward"
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
      .planning/*) ;;
      "$CONTRACT") if ! contract_change_is_ledger_only "$full"; then offenders+=("$f"); fi ;;
      *) is_module_api "$f" || offenders+=("$f") ;;
    esac
  done <<<"$names"
  if [ "${#offenders[@]}" -gt 0 ]; then
    for f in "${offenders[@]}"; do echo "  changed after the wiring SHA: $f" >&2; done
    for f in "${offenders[@]}"; do
      if [ "$f" = "$CONTRACT" ]; then
        echo "  a peer or orchestrator commit after the wiring SHA changed the contract outside the section 11 ledger rows: relay to yahir-gsd-control-plane-f2; resolution is an isolated wiring rerun on a SHA that contains it" >&2
      fi
    done
    gate_fail diff "${#offenders[@]} path(s) changed since the wiring SHA ${full:0:10} outside the module api.txt files (scripts/modules.list) and .planning/ (the wiring pass would be void)"
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

# A patch release (PATCH > 0) changes no pre-freeze API decision and needs no waiver packet, but "no waivers" must be a
# reviewed statement in HEAD, never an omission: .planning/releases/<tag>/NO-WAIVERS.md carries the lines "tag: <tag>" and
# "no waivers: patch release". A major or minor release (PATCH is 0) never qualifies. Returns 0 only when the statement
# exists and is valid; returns 1 when there is no statement (the caller falls back to the packet).
waiver_statement() {
  local tag="$1" stmt body patch
  stmt="$NO_WAIVER_DIR/$tag/NO-WAIVERS.md"
  git cat-file -e "HEAD:$stmt" 2>/dev/null || return 1
  [[ "$tag" =~ $SEMVER_RE ]] || gate_fail waiver "tag '$tag' is not a vMAJOR.MINOR.PATCH release tag"
  patch="${BASH_REMATCH[3]}"
  [ "$patch" != 0 ] \
    || gate_fail waiver "$stmt exists but $tag is a major or minor release (PATCH is 0): a no-waiver statement is valid only for a patch release, which needs no new decision; this release needs a waiver packet"
  body="$(git show "HEAD:$stmt")"
  grep -qxF "tag: $tag" <<<"$body" || gate_fail waiver "$stmt does not carry the line 'tag: $tag'"
  grep -qxF "$NO_WAIVER_LINE" <<<"$body" || gate_fail waiver "$stmt does not carry the line '$NO_WAIVER_LINE'"
  echo "WAIVER NOTE: no-waiver statement for patch release $tag ($stmt); the waiver packet is not consulted"
  return 0
}

gate_waiver() {
  local tag="$1" status by at ids_ans ids_tab id ans c_ids
  if waiver_statement "$tag"; then gate_ok waiver; return 0; fi
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
  # A pre-freeze row cannot be carried past the freeze: the public API is frozen into api.txt before Gate-2 runs. An empty
  # id list means the packet table format drifted (WR-01), which would silently skip this check, so it fails closed.
  c_ids="$(category_c_ids)"
  [ -n "$c_ids" ] || gate_fail waiver "no category C (pre-freeze) rows found in the packet table: the table format changed, so the pre-freeze check cannot run"
  for id in $c_ids; do
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
  if ! out="$("$REPO/scripts/verify-module-manifest.sh" 2>&1)" || ! grep -q 'MODULE MANIFEST OK' <<<"$out"; then
    echo "$out" >&2
    gate_fail hygiene "scripts/verify-module-manifest.sh failed"
  fi
  # RT-05: only :voice-adapter may pull the speech engine (:stt); the confinement check is part of this gate, not a gate of its own.
  if ! out="$("$REPO/scripts/verify-stt-confinement.sh" 2>&1)" || ! grep -q '^STT CONFINEMENT OK' <<<"$out"; then
    echo "$out" >&2
    gate_fail hygiene "scripts/verify-stt-confinement.sh failed"
  fi
  gate_ok hygiene
}

# The committed api.txt files are the released-API baseline, so they are compared with the ones the previous release tag
# carries before any build runs. Identical is the pass for a patch release (same MAJOR.MINOR as the previous tag); a minor
# or major release may only ADD lines (no line of the previous baseline disappears). Without any previous release tag there
# is no baseline to compare with (the first release dumps it).
# A module whose DIRECTORY is absent from the previous release tag is NEW IN THIS RELEASE (D-01, RT-02): it has no baseline to
# compare with, so the check is that HEAD tracks a real dump of it (more than the one header line, with its own package line).
# Newness is keyed on the directory, never on api.txt, so a released module whose baseline file was deleted still fails; a
# patch release can never introduce a module.
api_baseline_check() {
  local tag="$1" known prior pmm tmm m pkg newmods="" lines
  known="$(release_tags)" || gate_fail api-check "cannot list the tags on origin (network or remote problem)"
  prior="$(greatest_below "$tag" <<<"$known")"
  if [ -z "$prior" ]; then
    echo "API NOTE: no previous release tag, so there is no released api.txt to compare with"
    return 0
  fi
  git rev-parse --quiet --verify "refs/tags/$prior^{commit}" >/dev/null \
    || gate_fail api-check "the previous release tag $prior is not in this clone: run git fetch --tags origin"
  pmm="${prior%.*}"
  tmm="${tag%.*}"
  for m in $MODULES; do
    if ! git cat-file -e "$prior:$m" 2>/dev/null; then
      [ "$pmm" != "$tmm" ] \
        || gate_fail api-check "$m is not in the previous release $prior and $tag is a patch release: a patch release introduces no module"
      git cat-file -e "HEAD:$m/api.txt" 2>/dev/null || gate_fail api-check "$m/api.txt is not tracked in HEAD"
      pkg="$(vae_pkg_of "$m")"
      lines="$(git show "HEAD:$m/api.txt" | wc -l)"
      if [ "$lines" -le 1 ] \
        || ! git show "HEAD:$m/api.txt" | awk -v p="io.github.ygaray.voiceactionengine.$pkg" '$1 == "package" && $2 == p { f = 1 } END { exit !f }'; then
        gate_fail api-check "$m is new in this release but its committed api.txt is a header-only seed or lacks the package io.github.ygaray.voiceactionengine.$pkg: dump it before the cut"
      fi
      newmods="${newmods:+$newmods }$m"
      continue
    fi
    git cat-file -e "$prior:$m/api.txt" 2>/dev/null || gate_fail api-check "$m/api.txt is not in the previous release $prior, so there is no baseline"
    git cat-file -e "HEAD:$m/api.txt" 2>/dev/null || gate_fail api-check "$m/api.txt is not tracked in HEAD"
    if [ "$pmm" = "$tmm" ]; then
      [ "$(git rev-parse "$prior:$m/api.txt")" = "$(git rev-parse "HEAD:$m/api.txt")" ] \
        || gate_fail api-check "$m/api.txt differs from the baseline released in $prior: a patch release ($tag) changes no public API"
    else
      if [ -n "$(comm -23 <(git show "$prior:$m/api.txt" | sort -u) <(git show "HEAD:$m/api.txt" | sort -u))" ]; then
        gate_fail api-check "$m/api.txt removes or changes a line the released baseline in $prior has: public API grows only by addition"
      fi
    fi
  done
  if [ -n "$newmods" ]; then echo "API NOTE: new in this release (no baseline): $newmods"; fi
  echo "API NOTE: api.txt compared with the baseline released in $prior ($([ "$pmm" = "$tmm" ] && echo identical || echo additive only))"
}

# Diagnostic only (outside the preflight gate list, no Gradle): the api.txt baseline half of api-check on its own.
gate_api_baseline() {
  api_baseline_check "$1"
  gate_ok api-baseline
}

gate_api_check() {
  local tag="$1" d m log
  api_baseline_check "$tag"
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
# So when every POM, .module and the core dependency of every dependsOnCore=yes module in the dry run's maven-local carry <tag>, the
# coordinates JitPack serves for the tag build carry exactly that string. engineVersion's 0.0.0-local default is a local
# placeholder that never reaches a publication.
# The static half of the version gate: HEAD's build files must let JitPack's VERSION decide the coordinates. It needs no build
# output, so `gate version` runs it BEFORE the (long) dry run: a build that ignores VERSION would otherwise fail the dry run
# first, on missing <tag> file names, and the reason would not name the version it publishes instead.
version_static_checks() {
  local tag="$1" local_default
  if ! git show HEAD:build.gradle.kts | grep -q 'providers.environmentVariable("VERSION")'; then
    local_default="$(git show HEAD:gradle.properties | awk -F= '/^engineVersion=/ { print $2; exit }')"
    gate_fail version "the root build.gradle.kts does not read the VERSION environment variable, so the published coordinates would carry '${local_default:-unknown}' (the local engineVersion default) instead of $tag"
  fi
  if git show HEAD:jitpack.yml | grep -Ev '^[[:space:]]*#' | grep -q 'VERSION'; then
    gate_fail version "jitpack.yml sets VERSION itself; JitPack must supply it"
  fi
}

gate_version() {
  local tag="$1" group
  [ -n "$DRY_M2" ] && [ -d "$DRY_M2" ] || gate_fail version "no dry-run maven-local to inspect (run the dry-run gate first)"
  version_static_checks "$tag"
  group="$(git show HEAD:gradle.properties | awk -F= '/^engineGroup=/ { print $2; exit }')"
  [ -n "$group" ] || gate_fail version "engineGroup is missing from gradle.properties"
  python3 "$REPO/scripts/lib/published_versions.py" "$DRY_M2" "$group" "$tag" "$(manifest_head_file)" \
    || gate_fail version "a published artifact does not carry version $tag (reason above)"
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
    waiver) gate_waiver "$tag" ;;
    check) gate_check ;;
    api-dump) gate_api_dump ;;
    hygiene) gate_hygiene ;;
    api-check) gate_api_check "$tag" ;;
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
    version) [ $# -eq 1 ] || usage; version_static_checks "$1"; gate_dry_run "$1"; gate_version "$1" ;;
    waiver) [ $# -eq 1 ] || usage; gate_waiver "$1" ;;
    api-check) [ $# -eq 1 ] || usage; gate_api_check "$1" ;;
    api-baseline) [ $# -eq 1 ] || usage; gate_api_baseline "$1" ;;
    create-tag | clean | pushed | prefreeze | check | api-dump | hygiene | leak)
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
  local tag="$1" wiring="$2" approved="$3" full head group msg tagobj peeled remote expect before_remote coords m
  [[ "$approved" =~ ^[0-9a-f]{10,40}$ ]] || cut_fail "approvedCommit must be a full SHA or a unique prefix of at least 10 hex digits"
  full="$(full_sha "$approved")" || cut_fail "approvedCommit '$approved' does not resolve to a single commit"
  head="$(git rev-parse HEAD)"
  [ "$full" = "$head" ] || cut_fail "approvedCommit ${full:0:10} is not HEAD ${head:0:10}"
  run_preflight "$tag" "$wiring"
  # Time of check: HEAD and the tag state must not have moved while the (long) preflight ran.
  [ "$(git rev-parse HEAD)" = "$full" ] || cut_fail "HEAD moved during the preflight"
  gate_tags_absent "$tag"
  group="$(awk -F= '/^engineGroup=/ { print $2; exit }' gradle.properties)"
  coords=""
  for m in $MODULES; do coords="${coords:+$coords
}  $group:$(vae_artifact_of "$m"):$tag"; done
  msg="voice-action-engine $tag

Coordinates (JitPack, per module):
$coords
Wiring-tested SHA: $(full_sha "$wiring")
Contract: CROSS-REPO-SCOPE-CONTRACT.md (the section 11 ledger row for $tag lists what this release contains).
Section 11 ledger row: messaged to the orchestrator, the sole ledger writer (A14)."
  git tag -a "$tag" -m "$msg" "$full"
  [ "$(git cat-file -t "refs/tags/$tag")" = "tag" ] || cut_fail "refs/tags/$tag is not an annotated tag object"
  peeled="$(git rev-parse "refs/tags/$tag^{commit}")"
  [ "$peeled" = "$full" ] || cut_fail "the tag peels to ${peeled:0:10}, not the approved commit ${full:0:10}"
  tagobj="$(git rev-parse "refs/tags/$tag")"
  before_remote="$(git ls-remote --tags origin | sort)"
  if ! git push origin "refs/tags/$tag"; then
    echo "CUT PUSH FAILED: local annotated tag $tag exists at $full; it was not deleted or moved; retry exactly: git push origin refs/tags/$tag" >&2
    exit 3
  fi
  remote="$(git ls-remote --tags origin | sort)"
  # Origin must list exactly the tag refs it listed before, plus this tag and its peeled commit, and nothing else.
  expect="$({ printf '%s\n' "$before_remote"; printf '%s\trefs/tags/%s\n%s\trefs/tags/%s^{}\n' "$tagobj" "$tag" "$full" "$tag"; } | sed '/^$/d' | sort)"
  [ "$remote" = "$expect" ] || cut_fail "origin does not list exactly its earlier tags plus the new tag and its peeled commit after the push (got: $(echo "$remote" | tr '\n' ' '))"
  echo "CUT OK tag=$tag commit=$full tag_object=$tagobj pushed=refs/tags/$tag"
}

# ---------------------------------------------------------------------------------------------------------------------
# selftest: a temp clone and a LOCAL bare remote; the real repository is only read
#   happy      green sandbox (a synthetic previous release tag, then patch work) -> preflight -> cut -> the sandbox remote
#              holds the previous tag plus exactly one new annotated tag
#   negative   one planted violation per control, each in its own throwaway clone of the green bare repository; a control
#              passes only when its gate went red (or stayed green, for a positive control) for the RIGHT reason
#   all        happy, then negative, on one green sandbox
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

sf() { echo "RELEASE SELFTEST FAIL: $*" >&2; exit 1; }

# Every write the selftest makes goes through this assertion: the path must resolve under the temp root.
under_root() {
  local p root
  p="$(realpath -m -- "$1")"
  root="$(realpath -m -- "$TMPROOT")"
  case "$p" in "$root"/*) ;; *) sf "refusing to write outside the temp root: $p" ;; esac
}

# The remote of a sandbox clone is the local bare path and nothing else; checked before any push or cut.
assert_local_remote() { # <clone> <bare>
  local f p
  f="$(git -C "$1" remote get-url origin)"
  p="$(git -C "$1" remote get-url --push origin)"
  { [ "$f" = "$2" ] && [ "$p" = "$2" ]; } || sf "the sandbox origin is not the local bare path ($f, $p)"
  case "$f$p" in *github.com* | *://* | *@*) sf "the sandbox origin looks like a network remote" ;; esac
  under_root "$f"
  under_root "$(cd "$1" && git rev-parse --show-toplevel)/."
}

sandbox_git_config() { # <clone>
  git -C "$1" config user.name "release-cut selftest"
  git -C "$1" config user.email "selftest@invalid"
  git -C "$1" config core.hooksPath /dev/null
  git -C "$1" config commit.gpgsign false
  git -C "$1" config tag.gpgsign false
}

# Steps 1-5: the green state every control starts from. Sets SB_BARE, SB_CLONE, GREEN_W (the overlay commit W) and GREEN_HEAD.
build_green_sandbox() { # <dir>
  local sb="$1"
  under_root "$sb"
  mkdir -p "$sb"
  SB_BARE="$sb/origin.git"
  SB_CLONE="$sb/work"

  # 1. A bare repository holding the real HEAD commit as main, and a working clone of it. The push target is a local path.
  git init --quiet --bare --initial-branch=main "$SB_BARE"
  git -C "$REPO" push --quiet "$SB_BARE" HEAD:refs/heads/main
  git clone --quiet "$SB_BARE" "$SB_CLONE"
  sandbox_git_config "$SB_CLONE"

  # 2. Overlay the real working-tree scripts/ and the release directory, so uncommitted script edits are what gets tested.
  #    The real release statements (.planning/releases) are dropped: each control plants the statement it needs. The
  #    current release directory (wiring record and waiver packet) is overlaid AFTER that removal, so it is not dropped again.
  tar -C "$REPO" --exclude='*.done.json' -cf - scripts | tar -C "$SB_CLONE" -xf -
  git -C "$SB_CLONE" add -- scripts
  git -C "$SB_CLONE" rm --quiet -r --ignore-unmatch -- "$NO_WAIVER_DIR"
  if [ -d "$REPO/$RELEASE_DIR" ]; then
    tar -C "$REPO" --exclude='*.done.json' -cf - "$RELEASE_DIR" | tar -C "$SB_CLONE" -xf -
    git -C "$SB_CLONE" add -- "$RELEASE_DIR"
  fi
  if ! git -C "$SB_CLONE" diff --cached --quiet; then git -C "$SB_CLONE" commit --quiet -m "selftest: overlay working-tree scripts and release directory"; fi
  GREEN_W="$(git -C "$SB_CLONE" rev-parse HEAD)"

  # The clone has the committed config.json; pin the value the create-tag guard reads (uncommitted, under .planning/).
  mkdir -p "$SB_CLONE/.planning"
  printf '{"git":{"create_tag":false}}\n' >"$SB_CLONE/.planning/config.json"

  # 3. A synthetic wiring record (status pass for W) and an accepted packet with every row ok.
  mkdir -p "$SB_CLONE/$RELEASE_DIR"
  cat >"$SB_CLONE/$WIRING_RECORD" <<EOF
---
status: pass
tested_sha: $GREEN_W
consulted_only_workspace: true
---
SANDBOX SYNTHETIC wiring record.
WIRING TEST: PASS checks=9
EOF
  sed -i -e 's/^packet_status: .*/packet_status: accepted/' \
    -e 's/^answered_by: .*/answered_by: SANDBOX SYNTHETIC/' \
    -e 's/^answered_at: .*/answered_at: 2000-01-01/' \
    -e 's/^- \(W[0-9][0-9]\): .*/- \1: ok/' "$SB_CLONE/$WAIVER_PACKET"
  git -C "$SB_CLONE" add -- "$WIRING_RECORD" "$WAIVER_PACKET"
  git -C "$SB_CLONE" commit --quiet -m "selftest: synthetic wiring record and accepted packet"

  # 4. The module api.txt dumps. Once a release exists they are tracked in HEAD and a fresh dump must not change them; a
  #    HEAD that predates the first release has none yet, and the dump becomes the freeze (committed here).
  if [ -f "$REPO/local.properties" ]; then cp "$REPO/local.properties" "$SB_CLONE/"; fi
  (cd "$SB_CLONE" && ./gradlew apiDump --console=plain) >"$sb/apidump.log" 2>&1 || { tail -20 "$sb/apidump.log" >&2; sf "apiDump failed in the sandbox clone"; }
  # The module list is the sandbox's own manifest (overlaid above), so a module added to it is dumped and committed here too.
  SB_APIS=()
  for m in $(manifest_names <"$SB_CLONE/scripts/modules.list"); do SB_APIS+=("$m/api.txt"); done
  if git -C "$SB_CLONE" ls-files --error-unmatch -- "${SB_APIS[@]}" >/dev/null 2>&1; then
    git -C "$SB_CLONE" diff --quiet -- "${SB_APIS[@]}" \
      || sf "apiDump changed an api.txt that is already tracked: the released baseline must not move"
  else
    git -C "$SB_CLONE" add -- "${SB_APIS[@]}"
    git -C "$SB_CLONE" commit --quiet -m "selftest: commit the api.txt baseline"
  fi

  # 5. Never push or cut before the clone's remote is proven to be the local bare path. The previous release is a synthetic
  #    annotated tag on this commit (it holds the released api.txt baseline), pushed with main; one commit of patch work
  #    follows it, and that commit is HEAD.
  assert_local_remote "$SB_CLONE" "$SB_BARE"
  git -C "$SB_CLONE" tag -a "$SELFTEST_PRIOR_TAG" -m "selftest: the previous release" HEAD
  git -C "$SB_CLONE" commit --quiet --allow-empty -m "selftest: patch work after the previous release"
  git -C "$SB_CLONE" push --quiet origin main "refs/tags/$SELFTEST_PRIOR_TAG"
  GREEN_HEAD="$(git -C "$SB_CLONE" rev-parse HEAD)"
}

# Steps 6-7 of the happy path: preflight, then cut, then the bare repository holds exactly one annotated tag.
sandbox_preflight_and_cut() {
  local sb tags
  sb="$(dirname "$SB_BARE")"
  echo "--- sandbox preflight (W=$GREEN_W)"
  (cd "$SB_CLONE" && "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W") >"$sb/preflight.log" 2>&1 || { tail -30 "$sb/preflight.log" >&2; sf "sandbox preflight failed"; }
  cat "$sb/preflight.log"
  grep -q "^PREFLIGHT OK tag=$SELFTEST_TAG commit=$GREEN_HEAD wiring=$GREEN_W gates=" "$sb/preflight.log" || sf "no PREFLIGHT OK line for the sandbox HEAD"
  echo "--- sandbox cut"
  (cd "$SB_CLONE" && "$SELF" cut "$SELFTEST_TAG" "$GREEN_W" "$GREEN_HEAD") >"$sb/cut.log" 2>&1 || { tail -30 "$sb/cut.log" >&2; sf "sandbox cut failed"; }
  cat "$sb/cut.log"
  grep -q "^CUT OK tag=$SELFTEST_TAG commit=$GREEN_HEAD " "$sb/cut.log" || sf "no CUT OK line"

  tags="$(git -C "$SB_BARE" tag --list | sort | tr '\n' ' ')"
  [ "$tags" = "$SELFTEST_PRIOR_TAG $SELFTEST_TAG " ] || sf "the sandbox remote holds '$tags', expected exactly '$SELFTEST_PRIOR_TAG $SELFTEST_TAG '"
  [ "$(git -C "$SB_BARE" cat-file -t "refs/tags/$SELFTEST_TAG")" = tag ] || sf "the sandbox tag is not annotated"
  [ "$(git -C "$SB_BARE" rev-parse "refs/tags/$SELFTEST_TAG^{commit}")" = "$GREEN_HEAD" ] || sf "the sandbox tag does not peel to the clone's HEAD"
  echo "sandbox remote: the previous tag $SELFTEST_PRIOR_TAG plus exactly one new annotated tag $SELFTEST_TAG peeling to ${GREEN_HEAD:0:10}"
}

# ---------------------------------------------------------------------------------------------------------------------
# Negative-control harness
# ---------------------------------------------------------------------------------------------------------------------
NEG_RED=0
NEG_GREEN=0
NEG_FAIL=0
CTL_ROOT=""
GREEN_BARE=""
CTL_LABEL=""
CTL_MARKS=()
CTL_BAD=()
CTL_NOTE=""
CASE_OUT=""
C=""
CB=""
PP=""
DASH=$'\xe2\x80\x94'

# A throwaway clone of the green bare repository for one control (or one sub-run of it), with its OWN bare remote.
ctl_clone() { # <name>   sets C (working clone) and CB (its bare remote), both under the temp root
  local d="$CTL_ROOT/$1"
  under_root "$d"
  mkdir -p "$d"
  CB="$d/origin.git"
  C="$d/work"
  git clone --quiet --bare "$GREEN_BARE" "$CB"
  git clone --quiet "$CB" "$C"
  sandbox_git_config "$C"
  assert_local_remote "$C" "$CB"
  under_root "$C/.planning/config.json"
  mkdir -p "$C/.planning"
  printf '{"git":{"create_tag":false}}\n' >"$C/.planning/config.json"
  if [ -f "$REPO/local.properties" ]; then cp "$REPO/local.properties" "$C/"; fi
}

# Resolves a path inside the current control clone into PP, after asserting it lies under the temp root.
plant_path() { # <relative path>
  PP="$C/$1"
  under_root "$PP"
}

# Fails the selftest when a planted write changed nothing (a vacuous plant would make a control meaningless).
assert_planted() { # <relative path>
  git -C "$C" diff --quiet -- "$1" && sf "[$CTL_LABEL] the plant did not change $1"
  return 0
}

ccommit() { # <message> <relative paths...>
  local msg="$1"
  shift
  git -C "$C" add -- "$@"
  git -C "$C" commit --quiet -m "$msg"
}

cpush() {
  assert_local_remote "$C" "$CB"
  git -C "$C" push --quiet origin main
}

# One run of a command in the current clone (cwd = the clone). Records a matched marker or a failure description; prints nothing.
#   red    exit non-zero, a 'RELEASE GATE FAIL <gate>:' line (or 'RELEASE CUT FAIL:' for gate 'cut'), and the marker
#   green  exit 0, a 'GATE OK <gate>' line, and the marker
run_case() { # <red|green> <gate> <marker> -- <command...>
  local mode="$1" gate="$2" marker="$3" rc=0 seen
  shift 4
  CASE_OUT="$CTL_ROOT/case.out"
  under_root "$CASE_OUT"
  (cd "$C" && "$@") >"$CASE_OUT" 2>&1 || rc=$?
  if [ "$mode" = red ]; then
    if [ "$rc" -eq 0 ]; then
      CTL_BAD+=("stayed GREEN (exit 0) where $gate should have failed [$marker]")
      return 0
    fi
    if [ "$gate" = cut ]; then
      grep -q '^RELEASE CUT FAIL:' "$CASE_OUT" && seen=ok || seen=""
    else
      grep -q "^RELEASE GATE FAIL $gate:" "$CASE_OUT" && seen=ok || seen=""
    fi
    if [ -z "$seen" ]; then
      CTL_BAD+=("went red for the WRONG gate: expected $gate, saw '$(grep -m1 -E '^RELEASE (GATE FAIL|CUT FAIL|USAGE)' "$CASE_OUT" | cut -c1-160 || true)' (exit $rc)")
      return 0
    fi
  else
    if [ "$rc" -ne 0 ]; then
      CTL_BAD+=("expected GREEN but exited $rc: '$(grep -m1 -E '^RELEASE ' "$CASE_OUT" | cut -c1-160 || true)'")
      return 0
    fi
    grep -q "^GATE OK $gate\$" "$CASE_OUT" || { CTL_BAD+=("exited 0 without 'GATE OK $gate'"); return 0; }
  fi
  if ! grep -qF -- "$marker" "$CASE_OUT"; then
    CTL_BAD+=("right gate ($gate), wrong reason: marker '$marker' not found; saw '$(grep -m1 -E '^RELEASE ' "$CASE_OUT" | cut -c1-200 || true)'")
    return 0
  fi
  local m dup=0
  for m in "${CTL_MARKS[@]:-}"; do
    if [ "$m" = "'$marker'" ]; then dup=1; fi
  done
  if [ "$dup" -eq 0 ]; then CTL_MARKS+=("'$marker'"); fi
}

# Prints the one result line of the current control and updates the counters.
ctl_done() { # <red|green> <gate>
  local kind="$1" gate="$2" marks=""
  if [ "${#CTL_MARKS[@]}" -gt 0 ]; then
    marks="$(printf '%s; ' "${CTL_MARKS[@]}")"
    marks="${marks%; }"
  else
    CTL_BAD+=("no run recorded a result")
  fi
  if [ "${#CTL_BAD[@]}" -gt 0 ]; then
    printf 'FAIL  [%s] %s\n' "$CTL_LABEL" "${CTL_BAD[*]}"
    NEG_FAIL=$((NEG_FAIL + 1))
  elif [ "$kind" = red ]; then
    printf 'ok    [%s] went red (%s: matched %s)\n' "$CTL_LABEL" "$gate" "$marks"
    NEG_RED=$((NEG_RED + 1))
    if [ -n "$CTL_NOTE" ]; then printf '      [%s] %s\n' "$CTL_LABEL" "$CTL_NOTE"; fi
  else
    printf 'ok    [%s] stayed green (%s: matched %s)\n' "$CTL_LABEL" "$gate" "$marks"
    NEG_GREEN=$((NEG_GREEN + 1))
  fi
}

# After a refused cut: the tag that was to be cut exists neither in the clone nor in its bare remote, and the previous
# release tag is still the only one.
assert_no_tag() {
  if [ "$(git -C "$C" tag --list | tr '\n' ' ')" != "$SELFTEST_PRIOR_TAG " ] \
    || [ "$(git -C "$CB" tag --list | tr '\n' ' ')" != "$SELFTEST_PRIOR_TAG " ]; then
    CTL_BAD+=("a tag was created by the refused cut (clone: '$(git -C "$C" tag --list | tr '\n' ' ')', bare: '$(git -C "$CB" tag --list | tr '\n' ' ')')")
  else
    CTL_NOTE="no new tag exists afterwards in the clone or in its bare remote (only $SELFTEST_PRIOR_TAG)"
  fi
}

# Rewrites the answer block of the clone's packet: status, answered_by, answered_at, the answer for every category C row and
# for every other row, then ID=answer overrides.
packet_set() { # <status> <by> <at> <c_answer> <other_answer> [ID=answer...]
  plant_path "$WAIVER_PACKET"
  python3 - "$PP" "$@" <<'PY'
import re, sys

path, status, by, at, c_ans, other_ans = sys.argv[1:7]
over = dict(a.split("=", 1) for a in sys.argv[7:])
text = open(path, encoding="utf-8").read()
cats = {m.group(1): m.group(2) for m in re.finditer(r"^\| (W\d\d) \| (\w+) \|", text, re.M)}
out = []
for line in text.split("\n"):
    if line.startswith("packet_status:"):
        line = "packet_status: " + status
    elif line.startswith("answered_by:"):
        line = "answered_by: " + by
    elif line.startswith("answered_at:"):
        line = "answered_at: " + at
    else:
        m = re.match(r"^- (W\d\d): ", line)
        if m:
            i = m.group(1)
            line = "- %s: %s" % (i, over.get(i, c_ans if cats.get(i) == "C" else other_ans))
    out.append(line)
open(path, "w", encoding="utf-8").write("\n".join(out))
PY
  assert_planted "$WAIVER_PACKET"
}

# Id of the first category C row of the clone's packet.
first_c_id() {
  awk -F'|' '/^\| W[0-9][0-9] \|/ { id = $2; c = $3; gsub(/ /, "", id); gsub(/ /, "", c); if (c == "C") { print id; exit } }' "$C/$WAIVER_PACKET"
}

# A synthetic dated row appended at the end of the section 11 table (the last line of the contract).
contract_append_row() {
  plant_path "$CONTRACT"
  printf '| 2026-10-02 | sandbox-peer | v0.0.1 | 0000000000000000000000000000000000000000 | sandbox:none:v0.0.1 | SANDBOX SYNTHETIC ledger row | none | %s |\n' "$DASH" >>"$PP"
  assert_planted "$CONTRACT"
}

# A one-word edit of a non-table line under the section 10 heading.
contract_edit_section10() {
  plant_path "$CONTRACT"
  sed -i '/^\*\*A1 /s/$/ SANDBOX-EDIT/' "$PP"
  assert_planted "$CONTRACT"
}

# ---------------------------------------------------------------------------------------------------------------------
# Controls (label = function name suffix). W = GREEN_W, the sandbox overlay commit.
# ---------------------------------------------------------------------------------------------------------------------
ctl_tag-format-args() {
  local t
  ctl_clone "$CTL_LABEL"
  for t in 1.0.1 v1.0 v1.0.1-rc1 v01.0.1 V1.0.1; do
    run_case red tag-format "is not in strict vMAJOR.MINOR.PATCH form" -- "$SELF" preflight "$t" "$GREEN_W"
  done
  ctl_done red tag-format
}

ctl_tag-local-lightweight() {
  ctl_clone "$CTL_LABEL"
  git -C "$C" tag "$SELFTEST_TAG"
  run_case red tags-absent "already exists locally" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red tags-absent
}

ctl_tag-remote-only() {
  ctl_clone "$CTL_LABEL"
  git -C "$CB" tag "$SELFTEST_TAG" refs/heads/main
  run_case red tags-absent "origin already has tag ref(s)" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red tags-absent
}

# The previous release tag itself, and an older number, can never be cut again.
ctl_tag-not-newer() {
  ctl_clone "$CTL_LABEL"
  run_case red tags-absent "already exists locally" -- "$SELF" preflight "$SELFTEST_PRIOR_TAG" "$GREEN_W"
  run_case red tags-absent "is not newer than the existing release tag $SELFTEST_PRIOR_TAG" -- "$SELF" preflight v0.9.0 "$GREEN_W"
  git -C "$C" tag v1.0.2
  run_case red tags-absent "is not newer than the existing release tag v1.0.2" -- "$SELF" gate tags-absent "$SELFTEST_TAG"
  ctl_done red tags-absent
}

# The tag being cut is checked on its own: an older unrelated tag, or a non-release tag, does not block it.
ctl_tag-patch-after-older-tags() {
  ctl_clone "$CTL_LABEL"
  git -C "$C" tag v0.9.0
  git -C "$C" tag not-a-release-tag
  run_case green tags-absent "GATE OK tags-absent" -- "$SELF" gate tags-absent "$SELFTEST_TAG"
  run_case green tags-absent "GATE OK tags-absent" -- "$SELF" gate tags-absent v2.0.0
  ctl_done green tags-absent
}

ctl_create-tag-not-false() {
  ctl_clone "$CTL_LABEL-true"
  plant_path .planning/config.json
  printf '{"git":{"create_tag":true}}\n' >"$PP"
  run_case red create-tag "must be false" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_clone "$CTL_LABEL-absent"
  plant_path .planning/config.json
  printf '{"git":{"branching_strategy":"none"}}\n' >"$PP"
  run_case red create-tag "must be false" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red create-tag
}

ctl_clean-tracked-modified() {
  ctl_clone "$CTL_LABEL"
  plant_path README.md
  printf '\nSANDBOX uncommitted plant\n' >>"$PP"
  assert_planted README.md
  run_case red clean "working tree is not clean" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red clean
}

ctl_clean-staged() {
  ctl_clone "$CTL_LABEL"
  plant_path README.md
  printf '\nSANDBOX staged plant\n' >>"$PP"
  git -C "$C" add -- README.md
  run_case red clean "files are staged" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red clean
}

ctl_pushed-ahead() {
  ctl_clone "$CTL_LABEL"
  git -C "$C" commit --quiet --allow-empty -m "sandbox: local commit that was never pushed"
  run_case red pushed "is not origin/main" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red pushed
}

ctl_wiring-status-fail() {
  ctl_clone "$CTL_LABEL"
  plant_path "$WIRING_RECORD"
  sed -i 's/^status: .*/status: fail/' "$PP"
  assert_planted "$WIRING_RECORD"
  ccommit "sandbox: wiring record status fail" "$WIRING_RECORD"
  run_case red wiring "must be pass" -- "$SELF" gate wiring "$GREEN_W"
  ctl_done red wiring
}

ctl_wiring-sha-mismatch() {
  ctl_clone "$CTL_LABEL"
  plant_path "$WIRING_RECORD"
  sed -i 's/^tested_sha: .*/tested_sha: 0000000000000000000000000000000000000000/' "$PP"
  assert_planted "$WIRING_RECORD"
  ccommit "sandbox: wiring record names another SHA" "$WIRING_RECORD"
  run_case red wiring "tested_sha" -- "$SELF" gate wiring "$GREEN_W"
  ctl_done red wiring
}

ctl_wiring-not-ancestor() {
  local side
  ctl_clone "$CTL_LABEL"
  git -C "$C" checkout --quiet -b sandbox-side "${GREEN_W}^"
  git -C "$C" commit --quiet --allow-empty -m "sandbox: a commit on a side branch"
  side="$(git -C "$C" rev-parse HEAD)"
  git -C "$C" checkout --quiet main
  run_case red wiring "is not an ancestor of HEAD" -- "$SELF" gate wiring "$side"
  ctl_done red wiring
}

ctl_diff-readme() {
  ctl_clone "$CTL_LABEL"
  plant_path README.md
  printf '\nSANDBOX committed plant\n' >>"$PP"
  assert_planted README.md
  ccommit "sandbox: README edit after W" README.md
  cpush
  run_case red diff "README.md" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red diff
}

ctl_diff-core-main() {
  local f
  ctl_clone "$CTL_LABEL"
  f="$(git -C "$C" ls-files -- 'core/src/main/*.kt' | sed -n 1p)"
  [ -n "$f" ] || sf "[$CTL_LABEL] no core main source file found"
  plant_path "$f"
  printf '// SANDBOX committed plant\n' >>"$PP"
  assert_planted "$f"
  ccommit "sandbox: core main edit after W" "$f"
  run_case red diff "core/src/main" -- "$SELF" gate diff "$GREEN_W"
  ctl_done red diff
}

ctl_diff-jitpack-yml() {
  ctl_clone "$CTL_LABEL"
  plant_path jitpack.yml
  printf '# SANDBOX committed plant\n' >>"$PP"
  assert_planted jitpack.yml
  ccommit "sandbox: jitpack.yml edit after W" jitpack.yml
  run_case red diff "jitpack.yml" -- "$SELF" gate diff "$GREEN_W"
  ctl_done red diff
}

ctl_contract-non-ledger() {
  ctl_clone "$CTL_LABEL"
  contract_edit_section10
  ccommit "sandbox: section 10 line edited after W" "$CONTRACT"
  run_case red diff "yahir-gsd-control-plane-f2" -- "$SELF" gate diff "$GREEN_W"
  ctl_done red diff
}

ctl_contract-mixed() {
  ctl_clone "$CTL_LABEL"
  contract_append_row
  ccommit "sandbox: ledger row appended" "$CONTRACT"
  contract_edit_section10
  ccommit "sandbox: section 10 line edited" "$CONTRACT"
  run_case red diff "yahir-gsd-control-plane-f2" -- "$SELF" gate diff "$GREEN_W"
  ctl_done red diff
}

ctl_contract-row-outside-11() {
  ctl_clone "$CTL_LABEL"
  plant_path "$CONTRACT"
  sed -i '/^## 10\./a | 2026-10-02 | sandbox-peer | v0.0.1 | 0000000000000000000000000000000000000000 | sandbox:none:v0.0.1 | SANDBOX row-shaped line outside section 11 | none | - |' "$PP"
  assert_planted "$CONTRACT"
  ccommit "sandbox: row-shaped line under section 10" "$CONTRACT"
  run_case red diff "yahir-gsd-control-plane-f2" -- "$SELF" gate diff "$GREEN_W"
  ctl_done red diff
}

ctl_contract-ledger-only() {
  ctl_clone "$CTL_LABEL"
  contract_append_row
  ccommit "sandbox: ledger row appended" "$CONTRACT"
  plant_path "$CONTRACT"
  sed -i "0,/^| 20[0-9][0-9]-[0-9][0-9]-[0-9][0-9] |.*| ${DASH} |\$/s/| ${DASH} |\$/| SANDBOX |/" "$PP"
  git -C "$C" diff --quiet -- "$CONTRACT" && sf "[$CTL_LABEL] no existing dated row ended in a dash"
  ccommit "sandbox: last cell of an existing ledger row edited" "$CONTRACT"
  run_case green diff "DIFF NOTE: ledger-only contract change" -- "$SELF" gate diff "$GREEN_W"
  ctl_done green diff
}

ctl_waiver-not-accepted() {
  ctl_clone "$CTL_LABEL-pending"
  packet_set pending - - ok ok
  ccommit "sandbox: packet pending" "$WAIVER_PACKET"
  cpush
  run_case red waiver "must be accepted" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_clone "$CTL_LABEL-partial"
  packet_set partial SANDBOX 2000-01-01 ok pending W03=waive
  ccommit "sandbox: packet partial" "$WAIVER_PACKET"
  cpush
  run_case red waiver "must be accepted" -- "$SELF" preflight "$SELFTEST_TAG" "$GREEN_W"
  ctl_done red waiver
}

ctl_waiver-needs-fix() {
  local cid
  ctl_clone "$CTL_LABEL-other"
  packet_set accepted SANDBOX 2000-01-01 ok ok W01=needs-fix
  ccommit "sandbox: a non-C row needs-fix" "$WAIVER_PACKET"
  run_case red waiver "needs-fix" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_clone "$CTL_LABEL-carry"
  cid="$(first_c_id)"
  [ -n "$cid" ] || sf "[$CTL_LABEL] no category C row in the packet"
  packet_set accepted SANDBOX 2000-01-01 ok ok "$cid=carry-to-gate-2"
  ccommit "sandbox: a category C row carried to Gate-2" "$WAIVER_PACKET"
  run_case red waiver "carry-to-gate-2" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_done red waiver
}

ctl_waiver-id-missing() {
  ctl_clone "$CTL_LABEL"
  plant_path "$WAIVER_PACKET"
  sed -i '/^- W07:/d' "$PP"
  assert_planted "$WAIVER_PACKET"
  ccommit "sandbox: answer W07 removed" "$WAIVER_PACKET"
  run_case red waiver "differ from the table ids" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_done red waiver
}

# WR-01: a packet table whose category column no longer says C must fail closed, never skip the pre-freeze check.
ctl_waiver-c-rows-vanished() {
  ctl_clone "$CTL_LABEL"
  plant_path "$WAIVER_PACKET"
  sed -i -E 's/^(\| W[0-9][0-9] \| )C( \|)/\1Z\2/' "$PP"
  assert_planted "$WAIVER_PACKET"
  ccommit "sandbox: the category column no longer says C" "$WAIVER_PACKET"
  run_case red waiver "no category C (pre-freeze) rows found" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_done red waiver
}

# A patch release with no waivers says so in a statement in HEAD; the packet is then not consulted (here it is pending).
ctl_waiver-no-waiver-statement() {
  ctl_clone "$CTL_LABEL"
  packet_set pending - - pending pending
  ccommit "sandbox: packet pending" "$WAIVER_PACKET"
  run_case red waiver "must be accepted" -- "$SELF" gate waiver "$SELFTEST_TAG"
  plant_path "$NO_WAIVER_DIR/$SELFTEST_TAG/NO-WAIVERS.md"
  mkdir -p "$(dirname "$PP")"
  printf 'tag: %s\n%s\n' "$SELFTEST_TAG" "$NO_WAIVER_LINE" >"$PP"
  ccommit "sandbox: no-waiver statement for the patch release" "$NO_WAIVER_DIR/$SELFTEST_TAG/NO-WAIVERS.md"
  run_case green waiver "WAIVER NOTE: no-waiver statement" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_done green waiver
}

ctl_waiver-statement-invalid() {
  local f="$NO_WAIVER_DIR/$SELFTEST_TAG/NO-WAIVERS.md"
  ctl_clone "$CTL_LABEL-wrong-tag-line"
  plant_path "$f"
  mkdir -p "$(dirname "$PP")"
  printf 'tag: v9.9.9\n%s\n' "$NO_WAIVER_LINE" >"$PP"
  ccommit "sandbox: statement names another tag" "$f"
  run_case red waiver "does not carry the line 'tag: $SELFTEST_TAG'" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_clone "$CTL_LABEL-no-statement-line"
  plant_path "$f"
  mkdir -p "$(dirname "$PP")"
  printf 'tag: %s\nwaivers: none, trust me\n' "$SELFTEST_TAG" >"$PP"
  ccommit "sandbox: statement lacks the fixed sentence" "$f"
  run_case red waiver "does not carry the line '$NO_WAIVER_LINE'" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_clone "$CTL_LABEL-minor-release"
  plant_path "$NO_WAIVER_DIR/v1.1.0/NO-WAIVERS.md"
  mkdir -p "$(dirname "$PP")"
  printf 'tag: v1.1.0\n%s\n' "$NO_WAIVER_LINE" >"$PP"
  ccommit "sandbox: statement for a minor release" "$NO_WAIVER_DIR/v1.1.0/NO-WAIVERS.md"
  run_case red waiver "major or minor release" -- "$SELF" gate waiver v1.1.0
  ctl_done red waiver
}

# The statement is per tag: a statement for another tag never covers this one.
ctl_waiver-statement-other-tag() {
  ctl_clone "$CTL_LABEL"
  packet_set pending - - pending pending
  plant_path "$NO_WAIVER_DIR/v1.0.9/NO-WAIVERS.md"
  mkdir -p "$(dirname "$PP")"
  printf 'tag: v1.0.9\n%s\n' "$NO_WAIVER_LINE" >"$PP"
  ccommit "sandbox: packet pending, statement for another tag" "$WAIVER_PACKET" "$NO_WAIVER_DIR/v1.0.9/NO-WAIVERS.md"
  run_case red waiver "must be accepted" -- "$SELF" gate waiver "$SELFTEST_TAG"
  ctl_done red waiver
}

ctl_prefreeze-c-row-open() {
  local cid
  ctl_clone "$CTL_LABEL-pending"
  cid="$(first_c_id)"
  [ -n "$cid" ] || sf "[$CTL_LABEL] no category C row in the packet"
  packet_set partial SANDBOX 2000-01-01 ok ok "$cid=pending"
  ccommit "sandbox: category C row pending" "$WAIVER_PACKET"
  run_case red prefreeze "pending" -- "$SELF" gate prefreeze
  ctl_clone "$CTL_LABEL-needs-fix"
  packet_set rejected SANDBOX 2000-01-01 ok ok "$cid=needs-fix"
  ccommit "sandbox: category C row needs-fix" "$WAIVER_PACKET"
  run_case red prefreeze "W is void" -- "$SELF" gate prefreeze
  ctl_clone "$CTL_LABEL-carry"
  packet_set partial SANDBOX 2000-01-01 ok ok "$cid=carry-to-gate-2"
  ccommit "sandbox: category C row carried" "$WAIVER_PACKET"
  run_case red prefreeze "carry-to-gate-2" -- "$SELF" gate prefreeze
  ctl_done red prefreeze
}

ctl_prefreeze-c-rows-answered() {
  ctl_clone "$CTL_LABEL"
  packet_set partial SANDBOX 2000-01-01 ok pending
  ccommit "sandbox: partial packet, category C rows answered" "$WAIVER_PACKET"
  run_case green prefreeze "GATE OK prefreeze" -- "$SELF" gate prefreeze
  ctl_done green prefreeze
}

ctl_cut-dirty-tree() {
  ctl_clone "$CTL_LABEL"
  plant_path README.md
  printf '\nSANDBOX uncommitted plant\n' >>"$PP"
  assert_planted README.md
  run_case red clean "working tree is not clean" -- "$SELF" cut "$SELFTEST_TAG" "$GREEN_W" "$(git -C "$C" rev-parse HEAD)"
  assert_no_tag
  ctl_done red clean
}

ctl_cut-approved-not-head() {
  ctl_clone "$CTL_LABEL"
  run_case red cut "is not HEAD" -- "$SELF" cut "$SELFTEST_TAG" "$GREEN_W" "$GREEN_W"
  if grep -q '^GATE OK' "$CASE_OUT"; then CTL_BAD+=("a gate ran before the approvedCommit check (GATE OK line present)"); fi
  assert_no_tag
  ctl_done red cut
}

# --- the six ROADMAP SC1 release gates (hygiene, api-dump, leak, dry-run, version, check) ---
ctl_hygiene-api-txt-removed() {
  ctl_clone "$CTL_LABEL"
  git -C "$C" rm --quiet -- keystore/api.txt
  git -C "$C" commit --quiet -m "sandbox: keystore/api.txt removed"
  run_case red hygiene "keystore/api.txt" -- "$SELF" gate hygiene
  ctl_done red hygiene
}

# RT-05: a build file other than :voice-adapter's that names the :stt catalog alias must turn gate 11 red, and the marker must
# be the confinement script's own line (so the plant is not caught earlier by the other two hygiene checks).
ctl_hygiene-stt-confinement() {
  ctl_clone "$CTL_LABEL"
  plant_path core/build.gradle.kts
  printf '\ndependencies {\n    compileOnly(libs.stt.engine)\n}\n' >>"$PP"
  assert_planted core/build.gradle.kts
  ccommit "sandbox: core names the :stt catalog alias" core/build.gradle.kts
  run_case red hygiene "STT CONFINEMENT FAIL: wiring: core/build.gradle.kts" -- "$SELF" gate hygiene
  ctl_done red hygiene
}

# The committed api.txt is the released baseline: a patch release must carry the previous release's api.txt unchanged.
ctl_api-check-patch-changed() {
  ctl_clone "$CTL_LABEL"
  plant_path core/api.txt
  printf '  public final class SandboxAddition {\n  }\n' >>"$PP"
  assert_planted core/api.txt
  ccommit "sandbox: an addition in core/api.txt for a patch release" core/api.txt
  run_case red api-check "core/api.txt differs from the baseline released in $SELFTEST_PRIOR_TAG" -- "$SELF" gate api-check "$SELFTEST_TAG"
  ctl_done red api-check
}

# A minor release may add lines, never remove one of the released baseline.
ctl_api-check-minor-removed() {
  ctl_clone "$CTL_LABEL"
  plant_path core/api.txt
  # A line that appears exactly once in the dump, so the removal cannot hide behind an identical line elsewhere.
  sed -i '/pipeline\.TierPolicy getDEFAULT();/d' "$PP"
  assert_planted core/api.txt
  ccommit "sandbox: a released signature line deleted" core/api.txt
  run_case red api-check "removes or changes a line the released baseline in $SELFTEST_PRIOR_TAG has" -- "$SELF" gate api-check v1.1.0
  ctl_done red api-check
}

# Re-tags the previous release of the control clone (and of its OWN bare remote) on a commit that lacks the given paths, so
# a module looks new in the release (its directories removed) or looks like a released module whose baseline was deleted
# (only its api.txt removed). The clone ends on main again, with the tag moved and the side branch gone.
retag_prior_without() { # <relative paths...>
  assert_local_remote "$C" "$CB"
  git -C "$C" checkout --quiet -b sandbox-prior "refs/tags/$SELFTEST_PRIOR_TAG^{commit}"
  git -C "$C" rm -r --quiet -- "$@"
  git -C "$C" commit --quiet -m "sandbox: the previous release without $*"
  git -C "$C" tag -f -a "$SELFTEST_PRIOR_TAG" -m "selftest: the previous release, without $*" HEAD >/dev/null 2>&1
  assert_local_remote "$C" "$CB"
  git -C "$C" push --quiet --force origin "refs/tags/$SELFTEST_PRIOR_TAG"
  git -C "$C" checkout --quiet main
  git -C "$C" branch --quiet -D sandbox-prior
  if git -C "$C" cat-file -e "$SELFTEST_PRIOR_TAG:$1" 2>/dev/null; then sf "[$CTL_LABEL] the re-tagged previous release still holds $1"; fi
}

# D-01 / RT-02: a module absent from the previous release whose committed api.txt is only the one header line is not a baseline.
ctl_api-check-new-module-seed() {
  ctl_clone "$CTL_LABEL"
  retag_prior_without undo voice-adapter
  plant_path undo/api.txt
  printf '// Signature format: 4.0\n' >"$PP"
  assert_planted undo/api.txt
  ccommit "sandbox: undo/api.txt is the header-only seed" undo/api.txt
  run_case red api-check "header-only seed" -- "$SELF" gate api-baseline v1.1.0
  ctl_done red api-check
}

# D-01 / RT-02: both new modules with real committed dumps are named as new and the gate is green (positive control).
ctl_api-check-new-module-populated() {
  ctl_clone "$CTL_LABEL"
  retag_prior_without undo voice-adapter
  run_case green api-baseline "new in this release (no baseline): undo voice-adapter" -- "$SELF" gate api-baseline v1.1.0
  ctl_done green api-baseline
}

# Newness is keyed on the module DIRECTORY: a module that was released, whose api.txt then vanished from the release, still has no baseline.
ctl_api-check-baseline-deleted() {
  ctl_clone "$CTL_LABEL"
  retag_prior_without undo/api.txt
  run_case red api-check "undo/api.txt is not in the previous release $SELFTEST_PRIOR_TAG, so there is no baseline" -- "$SELF" gate api-baseline v1.1.0
  ctl_done red api-check
}

# A patch release (same MAJOR.MINOR as the previous tag) can never introduce a module.
ctl_api-check-new-module-patch() {
  ctl_clone "$CTL_LABEL"
  retag_prior_without undo voice-adapter
  run_case red api-check "is a patch release" -- "$SELF" gate api-baseline "$SELFTEST_TAG"
  ctl_done red api-check
}

ctl_api-dump-core-line-deleted() {
  ctl_clone "$CTL_LABEL"
  plant_path core/api.txt
  sed -i '0,/^ *method /{/^ *method /d}' "$PP"
  assert_planted core/api.txt
  ccommit "sandbox: one signature line deleted from core/api.txt" core/api.txt
  run_case red api-dump "core: a fresh apiDump of HEAD differs" -- "$SELF" gate api-dump
  ctl_done red api-dump
}

# A key-shaped string, assembled at run time from fragments, committed in a tracked file.
ctl_leak-key-shape() {
  local k
  k="s""k-ant-""Qm7Zt2Lw9Rb4Vn8Hc3Xk"
  ctl_clone "$CTL_LABEL"
  plant_path plant-notes.txt
  printf 'credential = %s\n' "$k" >"$PP"
  git -C "$C" add -f -- plant-notes.txt
  git -C "$C" commit --quiet -m "sandbox: tracked file with a key-shaped string"
  run_case red leak "provider-key shape" -- "$SELF" gate leak
  ctl_done red leak
}

ctl_leak-fixture-filename() {
  local fx
  fx="sb-a10""-fixture"
  ctl_clone "$CTL_LABEL"
  plant_path "plant-$fx.txt"
  printf 'SANDBOX plant\n' >"$PP"
  git -C "$C" add -f -- "plant-$fx.txt"
  git -C "$C" commit --quiet -m "sandbox: tracked file named after the fixture"
  run_case red leak "a tracked path contains the fixture name" -- "$SELF" gate leak
  ctl_done red leak
}

ctl_leak-fixture-mention() {
  local fx
  fx="sb-a10""-fixture"
  ctl_clone "$CTL_LABEL"
  plant_path plant-notes.md
  printf 'This note mentions %s outside the allow-list.\n' "$fx" >"$PP"
  git -C "$C" add -f -- plant-notes.md
  git -C "$C" commit --quiet -m "sandbox: tracked file that names the fixture"
  run_case red leak "names the fixture outside the allow-list" -- "$SELF" gate leak
  ctl_done red leak
}

ctl_dry-run-sample-install() {
  ctl_clone "$CTL_LABEL"
  plant_path jitpack.yml
  sed -i 's|^\(  - \./gradlew .*\)$|\1 :sample:assembleDebug|' "$PP"
  assert_planted jitpack.yml
  ccommit "sandbox: jitpack.yml install line names :sample" jitpack.yml
  run_case red dry-run "names :sample" -- "$SELF" gate dry-run "$SELFTEST_TAG"
  ctl_done red dry-run
}

# The root build reads a variable that is never set, so engineVersion falls back to the local default.
ctl_version-not-read() {
  ctl_clone "$CTL_LABEL"
  plant_path build.gradle.kts
  sed -i 's/environmentVariable("VERSION")/environmentVariable("VERSION_SANDBOX_UNSET")/' "$PP"
  assert_planted build.gradle.kts
  ccommit "sandbox: the root build no longer reads VERSION" build.gradle.kts
  run_case red version "0.0.0-local" -- "$SELF" gate version "$SELFTEST_TAG"
  ctl_done red version
}

ctl_check-print-call() {
  ctl_clone "$CTL_LABEL"
  plant_path core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ZzPlant.kt
  printf 'package io.github.ygaray.voiceactionengine.core\n\ninternal fun plant() {\n    println("x")\n}\n' >"$PP"
  [ -s "$PP" ] || sf "[$CTL_LABEL] the plant file is empty"
  ccommit "sandbox: a print call in core main source" core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ZzPlant.kt
  run_case red check "Banned constructs" -- "$SELF" gate check
  ctl_done red check
}

CONTROL_ORDER=(
  tag-format-args tag-local-lightweight tag-remote-only tag-not-newer tag-patch-after-older-tags create-tag-not-false
  clean-tracked-modified clean-staged pushed-ahead
  wiring-status-fail wiring-sha-mismatch wiring-not-ancestor
  diff-readme diff-core-main diff-jitpack-yml
  contract-non-ledger contract-mixed contract-row-outside-11 contract-ledger-only
  waiver-not-accepted waiver-needs-fix waiver-id-missing waiver-c-rows-vanished waiver-no-waiver-statement
  waiver-statement-invalid waiver-statement-other-tag
  prefreeze-c-row-open prefreeze-c-rows-answered
  cut-dirty-tree cut-approved-not-head
  hygiene-api-txt-removed hygiene-stt-confinement api-check-patch-changed api-check-minor-removed
  api-check-new-module-seed api-check-new-module-populated api-check-baseline-deleted api-check-new-module-patch
  api-dump-core-line-deleted
  leak-key-shape leak-fixture-filename leak-fixture-mention
  dry-run-sample-install version-not-read check-print-call
)

# SELFTEST_ONLY="label label" runs just those controls while developing the selftest; the run then ends in
# RELEASE SELFTEST PARTIAL, never in the OK line, so a filtered run can never pass for the evidence.
run_controls() {
  local l
  for l in "${CONTROL_ORDER[@]}"; do
    if [ -n "${SELFTEST_ONLY:-}" ] && [[ " $SELFTEST_ONLY " != *" $l "* ]]; then continue; fi
    CTL_LABEL="$l"
    CTL_MARKS=()
    CTL_BAD=()
    CTL_NOTE=""
    "ctl_$l"
  done
}

selftest_negative_run() { # <green bare repository holding no tag>
  GREEN_BARE="$1"
  CTL_ROOT="$TMPROOT/controls"
  under_root "$CTL_ROOT"
  mkdir -p "$CTL_ROOT"
  echo "--- negative controls (one throwaway clone per control; sandbox remotes are local paths)"
  run_controls
}

# Runs one selftest mode. The real repository is snapshotted before and compared after.
selftest() { # <happy|negative|all>
  local mode="$1" before after happy=0 pristine
  set -E
  trap 'echo "RELEASE SELFTEST FAIL: internal error at line $LINENO (control: ${CTL_LABEL:-none})" >&2' ERR
  mk_tmp
  before="$(snapshot_real)" || sf "cannot snapshot the real repository"
  build_green_sandbox "$TMPROOT/sandbox"
  case "$mode" in
    happy)
      sandbox_preflight_and_cut
      happy=1
      ;;
    negative)
      selftest_negative_run "$SB_BARE"
      ;;
    all)
      pristine="$TMPROOT/green-pristine.git"
      git clone --quiet --bare "$SB_BARE" "$pristine"
      sandbox_preflight_and_cut
      happy=1
      selftest_negative_run "$pristine"
      ;;
  esac
  after="$(snapshot_real)" || sf "cannot snapshot the real repository afterwards"
  [ "$before" = "$after" ] || { echo "$before" >&2; echo "$after" >&2; sf "the real repository changed during the selftest"; }
  echo "real-repo guard: unchanged (tags, remote tags, status, config.json, contract)"
  if [ "$NEG_FAIL" -gt 0 ]; then
    echo "RELEASE SELFTEST FAIL: $NEG_FAIL control(s) failed (see the FAIL lines above)" >&2
    exit 1
  fi
  if [ -n "${SELFTEST_ONLY:-}" ]; then
    echo "RELEASE SELFTEST PARTIAL (SELFTEST_ONLY set) happy=$happy negatives=$NEG_RED positives=$NEG_GREEN"
    return 0
  fi
  echo "RELEASE SELFTEST OK happy=$happy negatives=$NEG_RED positives=$NEG_GREEN"
}

case "${1:-}" in
  preflight) [ $# -eq 3 ] || usage; run_preflight "$2" "$3" ;;
  cut) [ $# -eq 4 ] || usage; run_cut "$2" "$3" "$4" ;;
  gate) [ $# -ge 2 ] || usage; shift; run_gate "$@" ;;
  selftest) { [ $# -eq 2 ] && { [ "$2" = happy ] || [ "$2" = negative ] || [ "$2" = all ]; }; } || usage; selftest "$2" ;;
  *) usage ;;
esac
