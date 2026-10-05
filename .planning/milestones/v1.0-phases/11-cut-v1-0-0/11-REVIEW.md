---
phase: 11-cut-v1-0-0
reviewed: 2026-10-02T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - scripts/release-cut.sh
  - scripts/api-dump-isolated.sh
  - scripts/jitpack-dry-run.sh
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauseCodes.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
  - API.md
  - ECOSYSTEM.md
  - INTEGRATION.md
  - README.md
findings:
  critical: 0
  warning: 4
  info: 8
  total: 12
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-10-02
**Depth:** standard
**Files Reviewed:** 13 (core/providers/keystore `api.txt` read for sanity only)
**Status:** issues_found

## Summary

The tag v1.0.0 is already cut and immutable, so everything below is v1.0.x follow-up. Nothing found would have changed what was tagged or pushed.

The irreversible path in `release-cut.sh` holds up:
- `cut` requires an exact approved SHA equal to HEAD.
- It re-runs all 15 gates, then re-checks HEAD and the tag state.
- It creates an annotated tag and checks that it peels to the approved commit.
- It pushes only `refs/tags/<tag>`. An explicit refspec on the command line ignores any configured `remote.*.push`.
- It verifies that origin lists exactly the tag and its peeled commit.
- The selftest sandbox uses local bare remotes only, asserts that, and snapshots the real repo before and after.

No path was found that pushes more than `refs/tags/v1.0.0` or tags a commit other than HEAD.

The defects are:
- one fail-open spot in the waiver gate;
- the script's single-use design, which blocks the very v1.0.x follow-ups it is meant to serve;
- inaccuracies in the frozen `KeystoreCauseCodes` KDoc and the docs;
- docs that never name the version consumers should pin.

## Warnings

### WR-01: waiver gate silently skips the pre-freeze (category C) enforcement if the packet table format drifts

**File:** `scripts/release-cut.sh:357-363` (with `category_c_ids`, 316-318)
**Issue:** `gate_waiver` loops over `$(category_c_ids)` to ensure no pre-freeze row is carried past the freeze. Unlike `gate_prefreeze` (line 322, which fails when the id list is empty), `gate_waiver` has no non-empty check. If the packet's table columns change (for example the category column moves or the `W<nn>` id format changes), `category_c_ids` returns nothing. The loop body never runs and the gate passes without checking a hard tag precondition. Because that gate is meant to be a fail-closed irreversible-tag guard, this is a fail-open path.
**Fix:**
```bash
  local c_ids
  c_ids="$(category_c_ids)"
  [ -n "$c_ids" ] || gate_fail waiver "no category C rows found in the packet table (format drift?)"
  for id in $c_ids; do
```

### WR-02: release-cut.sh is single-use by construction, so it cannot cut the v1.0.x patch tags this review feeds

**File:** `scripts/release-cut.sh:62` (`RELEASE_TAG`), `153-162` (`gate_tags_absent`), `scripts/verify-repo-hygiene.sh` default mode
**Issue:** `tag-format` requires `tag == v1.0.0`. `tags-absent` requires the repository to hold no tag at all, locally or on origin. `v1.0.0` now exists, so `preflight` and `cut` fail at gate 1 or 2 for any follow-up tag, including v1.0.1. The comment says "the next release edits it in a reviewed commit". Editing the gate constants and the tag-absence rule right before an immutable push is the riskiest moment to loosen them. The tool-level fix has no design yet: the first-tag rule and a "tag must be strictly newer than the latest tag, and not already present" rule are different gates. The hygiene script's default mode also fails by design now (follow-up (d) in the ledger-row note).
**Fix:** Before v1.0.1, make the release tag an input validated against a strict semver ordering. For example, require `tag > max(existing tags)` and that neither the local repo nor origin already has `tag`. Make "no tags at all" apply only when `tag == v1.0.0`. Keep the selftest controls for the new rule in the same commit.

### WR-03: no consumer-facing doc names the version to pin; the ECOSYSTEM status lost its content

**File:** `README.md` (Status line), `ECOSYSTEM.md` (Status and "Published tags" lines), `INTEGRATION.md` (dependency block)
**Issue:** The 11-03 edit made the docs "true across the cut" by removing every concrete statement. The README now says releases are "listed in the repository's tags". ECOSYSTEM says "the v1.0 core engine; v1.1 is planned" and "see the repository's git tags". All dependency snippets keep the `<version>` placeholder. A consumer or integrating agent reading the doc set (the stated audience) is never told that `v1.0.0` exists or that it is the version to use. It is also told "a commit SHA also works", which is the wrong advice now that an immutable tag exists. The ECOSYSTEM "Phase 1 proof" paragraph lost its closing sentence and now reads as an unfinished fragment. The doc set's own wiring-rerun stumbles (ledger-row follow-up (e)) are already queued for v1.0.x, so this fits there.
**Fix:** In the v1.0.x doc patch, state "Latest release: v1.0.0" in README and INTEGRATION (a one-line constant is fine, since tags are immutable and docs on main may change after the tag). Replace `<version>` in at least one copy-paste snippet with `v1.0.0`. Put a concrete status in ECOSYSTEM.

### WR-04: KeystoreCauseCodes KDoc and docs say `KeyState.Unreadable` carries `key_missing`, but the store never emits it that way

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauseCodes.kt:4-9`, `KeystoreCauses.kt:6-8`; `INTEGRATION.md` ("An unreadable key reaches you as ... (and `KeyState.Unreadable` from the store). The causes ... `key_missing` ..."); `API.md` (Credentials and keystore)
**Issue:** `KEY_MISSING` is produced only for the `CredentialLookup`: `KeystoreCredentialSource.kt:29` maps `KeyState.KeyMissing` to `KeystoreCauses.keyMissingLookup`. The store's own state for a gone device key is the separate leaf `KeyState.KeyMissing`, never `KeyState.Unreadable(cause = "key_missing")`. The public KDoc, which freezes in the v1.0.0 tag, says the codes are carried by "a `CredentialLookup.Unreadable` or a [KeyState.Unreadable]", and INTEGRATION lumps all five codes under that statement. A consumer who switches only on `KeyState.Unreadable.cause` (as the KDoc invites) never sees `KEY_MISSING`. Their re-enter-the-key path for a restored backup then falls to whatever they do for an unmatched state. This is the exact user-visible scenario the KDoc cites ("for example after a backup restore"). API.md also calls the values "constants", but they are getter-only `val`s, not `const`. That is intentional, and it matters because they cannot be used in annotations.
**Fix:** Docs-only, since the API is frozen. In the v1.0.x patch, state that `key_missing` arrives only via `CredentialLookup.Unreadable` and that the store reports the same situation as the `KeyState.KeyMissing` leaf. In the `KeystoreCauseCodes` KDoc, change "or a [KeyState.Unreadable]" to "or a [KeyState.Unreadable] for the other four". Drop the word "constants" in API.md.

## Info

### IN-01: bearer-token placeholder filter is dead because of a wrong slice

**File:** `scripts/release-cut.sh:544`
**Issue:** For the non-provider-key kinds, `body = text[4:]`. That is right for the 4-character `AIza` prefix. For `Bearer <token>` it leaves `rer <token>`, so the "fewer than 4 distinct characters means placeholder" test (`placeholder`) almost never applies to bearer hits. This yields false positives only, which is why `KEY_ALLOW` needed an entry for a made-up Bearer value in `ChatCaptureRunTest.kt`. It is not a missed leak.
**Fix:** Use a per-kind prefix length (`text[7:].lstrip()` for bearer), or run `placeholder` on the token after the whitespace.

### IN-02: latent `pipefail` + `grep -q` false RED in the version static checks

**File:** `scripts/release-cut.sh:576`, `580`; `scripts/verify-repo-hygiene.sh` (`echo "$jitpack_cmds" | grep -Eq`)
**Issue:** Under `set -o pipefail`, `producer | grep -q` can return 141 (SIGPIPE) when `grep -q` exits on its first match before the producer finishes writing. For these small inputs it is currently harmless. If a file grows past the pipe buffer, the first form (`! git show ... | grep -q`) would flip into a spurious "VERSION not read" failure. It fails closed, so the effect is a bogus gate failure, not a missed one.
**Fix:** Capture first, then test: `src="$(git show HEAD:build.gradle.kts)"; grep -q '...' <<<"$src"`.

### IN-03: `create-tag` guard accepts the string "false" and treats a missing key and a JSON `null` alike

**File:** `scripts/release-cut.sh:~182-190` (`gate_create_tag`)
**Issue:** `jq -r '.git.create_tag'` prints `false` for both boolean `false` and the string `"false"`. The header documents "exactly false". The `node` fallback is a different code path with its own output format, which is not checked for equivalence.
**Fix:** `jq -e '.git.create_tag == false' "$cfg" >/dev/null`.

### IN-04: `jitpack-dry-run.sh` copies no `local.properties` and appends `-Dmaven.repo.local` to every install command

**File:** `scripts/jitpack-dry-run.sh:23-34`
**Issue:** `release-cut.sh` (`archive_head`) and `api-dump-isolated.sh` both copy the host's `local.properties` into the clean copy. The dry run does not, so it passes only when the Android SDK is found through the environment. The `:keystore` AAR build needs the SDK. The unquoted `$M2` and the suffix on every command are fine for today's single-line `jitpack.yml`, but a second non-gradle install line would break.
**Fix:** Copy `local.properties` as the other two scripts do, and append the flag only to `./gradlew` lines.

### IN-05: `cut` re-checks HEAD and tags after the long preflight, but not `pushed` or `clean`

**File:** `scripts/release-cut.sh:~690-700` (`run_cut`)
**Issue:** The time-of-check recheck covers HEAD movement and tag absence only. Gates 4 and 5 (`clean`, `pushed`) run minutes before the push. The orchestrator commits ledger rows to this repo during runs, so origin/main can advance. The tag would then land on a commit that is behind main. This is still valid and reachable, and nothing is lost. It does mean the recorded "tag == origin/main tip" invariant is not what the final act verifies.
**Fix:** Call `gate_pushed` again right before `git tag`, or document that a trailing ledger commit is expected.

### IN-06: `KeystoreCauses.vocabulary` is never read by main code, and two tests overlap

**File:** `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt:20-26`; `UnreadableMappingTest.kt:255-289`
**Issue:** `vocabulary` is used only by tests. `theCauseVocabularyIsExactlyTheFiveStableCodes` and `theInternalVocabularyIsExactlyThePublicSetAndEveryCodeIsDistinct` assert largely the same thing. This is harmless dead weight in the AAR.
**Fix:** Move `vocabulary` to the test source set, or keep it with a one-line "tests only" comment, and merge the two tests.

### IN-07: ToolSpec's growth rule collides with ApiShapeTest, and its field-name constants are duplicated

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt:10-18, 32-35`; `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt` (`toolSpecDeclaresExactlyOnePublicConstructor`)
**Issue:** The KDoc promises later attributes arrive as `with...` members. That needs a wider constructor, and an `internal` Kotlin constructor is public in JVM bytecode. `toolSpecDeclaresExactlyOnePublicConstructor` counts non-synthetic public JVM constructors, so it would fail, and Java would see the extra constructor. The wider constructor therefore has to be `private`, and the rule should say so. The `question/options/id/label` constants are repeated in `ToolSpec.kt` and `TerminalCall.kt`, kept in step only by a round-trip test.
**Fix:** State in the KDoc and the test message that the wider constructor must be `private`. Share the field names through one `internal` constants file.

### IN-08: `api-dump-isolated.sh` runs apiDump with the build cache on, unlike the `check` and `api-check` gates

**File:** `scripts/api-dump-isolated.sh:58` versus `scripts/release-cut.sh:~385`, `~430` (`--no-build-cache`)
**Issue:** `check` and `apiCheck` pass `--no-build-cache` in the clean archive, but the dump gate that must be byte-equal to the committed `api.txt` does not. The cache is keyed on inputs, so a stale hit is unlikely. The gates are still inconsistent on the one point meant to prove "fresh".
**Fix:** `./gradlew -q apiDump --no-build-cache`.

---

_Reviewed: 2026-10-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
