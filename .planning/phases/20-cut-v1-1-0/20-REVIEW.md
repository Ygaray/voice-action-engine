---
phase: 20-cut-v1-1-0
reviewed: 2026-10-07T00:00:00Z
depth: standard
files_reviewed: 19
files_reviewed_list:
  - scripts/release-cut.sh
  - scripts/verify-binary-diff.sh
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanSchemaTest.kt
  - undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStoreTest.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/KeyVaultTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
  - API.md
  - INTEGRATION.md
  - CROSS-REPO-SCOPE-CONTRACT.md
  - core/api.txt
  - keystore/api.txt
  - undo/api.txt
  - voice-adapter/api.txt
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-10-07
**Depth:** standard
**Files Reviewed:** 19
**Status:** issues_found

## Summary

Scope was the non-.planning diff `d261da4..HEAD`. v1.1.0 is already tagged at 2e677a604f, so no finding below asks for a tag
change. Every finding concerns tooling or follow-up work.

Nothing found would make the released tag wrong:

- Docs were checked against source and match. This covers the `submit_plan` shape (`PlanParse.kt`/`PlanSchema.kt`), the
  `pick_start_tier` router contract (`RouterPicker.kt` `decodePick`), the read-tool `Extraction` and `Resolution.Escalate` claim
  (`SingleShotStrategy.kt`), and the `heldRunId` KDoc claim.
- The `heldRunId` claim is true: `HeldCommit.open` sets both `parentRunId` and `heldRunId` to `held.runId`, so printing it adds no join information.
- The `undo-bridge` import list matches the snippet.
- `core`, `providers` and `keystore` api.txt diffs against v1.0.1 contain only `+` lines.
- The new api.txt files parse cleanly (every declared class is recognised by `api_classes`).
- Key leakage: no key, transcript or tool argument reaches a log, `toString` or exception in the diff. The `ActionEvent.toString` change is KDoc only.

The release-cut.sh changes (new-module branch of gate 12, the `api-baseline` diagnostic gate, the :stt confinement call in gate 11,
the ledger-row placement fix and its controls) are fail-closed in the cases they exercise. The weaknesses are in what the gates do
not cover.

The core binary-diff red (removed=12) is waived by RT-12 and is not repeated here.

## Warnings

### WR-01: verify-binary-diff.sh never compares class headers (supertypes, class modifiers)

**File:** `scripts/verify-binary-diff.sh:380-390`
**Issue:** In the `member_lines` awk, the class-header line (`public class X extends Y implements Z {`) is parsed only to extract the class name, and then discarded (`mem = ""; next`). A class that stops extending a superclass or implementing an interface, becomes `final`, `abstract` or non-`public`, or changes its generic supertype bounds is a binary-breaking change for compiled consumers. The helper reports `BINARY DIFF OK removed=0` for all of those. The script header claims it compares "what a consumer's compiled code links against", so this is a false-negative class in a gate-evidence tool. api.txt (Metalava) covers supertypes at source level only.
**Fix:** Emit the normalised header as a pseudo-member, so the existing removed/added accounting catches it:
```awk
/^[^ ].*\{$/ {
  hdr = $0; sub(/ *\{$/, "", hdr)
  # ... existing cls extraction ...
  printf "%s\t%s\t%s\n", cls, "<class> " hdr, "header"
  mem = ""; next
}
```
Add selftest cases: drop an `implements`, and make a class `final`. Both must exit 1.

### WR-02: the D-02 binary diff is not enforced by any release gate

**File:** `scripts/release-cut.sh` (no reference to `verify-binary-diff.sh`); `scripts/verify-binary-diff.sh`
**Issue:** `release-cut.sh preflight`/`cut` never invokes the binary-diff helper. A grep of the repo outside `.planning` finds no caller. D-02 is satisfied by an operator running it by hand and pasting the output into `evidence/binary-diff.txt`. The cut therefore passes whether or not the diff was run, was run against the right artifacts, or was green. The project requires the strictly-additive guarantee, and every other §11 guarantee has a fail-closed gate. The v1.1.0 cut waived the red result by hand; a gate would have forced that ruling to be recorded in a machine-checkable place. Note that the false-positive on internal constructors is already tracked in ROADMAP (v1.2). This finding is about enforcement, not that false positive.
**Fix:** In the v1.2 tooling work, add a gate `binary-diff <tag>` that:
- builds or downloads the previous release's artifacts and the HEAD artifacts;
- runs the helper per released module;
- requires either exit 0 or a committed `releases/<tag>/evidence/binary-diff-waiver.txt` whose removed count equals the helper's output.
Add it to the preflight list and to the controls.

### WR-03: gate 12 only checks modules in HEAD's manifest, so a module dropped since the previous release passes

**File:** `scripts/release-cut.sh:528-551` (`api_baseline_check`, `for m in $MODULES`)
**Issue:** The loop iterates HEAD's `scripts/modules.list` only. A module that exists in the previous release tag but is removed from HEAD (its directory deleted, or its row removed from the manifest) is never compared. Its published artifact and coordinates disappear in a minor release, which is a breaking change for any consumer that pinned it, and gate 12 stays green. The new-module branch added in this phase makes the asymmetry more visible: "absent from the previous tag" is handled, "absent from HEAD" is not.
**Fix:** Before the loop, list the previous release's published modules and require each to be in HEAD's `MODULES`. If the previous tag has no manifest, derive the set from `git ls-tree -d --name-only "$prior"` entries that contain an `api.txt`:
```bash
for pm in $(git ls-tree -r --name-only "$prior" | sed -n 's#^\([^/]*\)/api\.txt$#\1#p'); do
  case " $MODULES " in *" $pm "*) ;; *) gate_fail api-check "$pm was released in $prior but is no longer in the manifest" ;; esac
done
```
Add a control in the same style as `retag_prior_without`.

### WR-04: the "Gate-1 never echoes the fingerprint" rule (RT-07) has no mechanical enforcement; `UiTags.neverEchoed` is dead code in main

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt:37-41`, `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt:338-341`
**Issue:** The sample now renders `Ready - fp <6 hex of SHA-256(key)>` in the `key_state_<p>` node. That text is exposed to uiautomator via `testTagsAsResourceId`. `UiTags.neverEchoed()` is introduced as "the tag contract", but its only consumer is `UiTagsTest`. No script (`run-sample-gate1.sh`, `sample-evidence-filter.sh`) or production code reads it. The evidence filter's closed vocabulary also admits hex fingerprints (see the 10-04 plan), so a tester or helper that copies the `key_state_*` text into evidence would pass the filter. The only guard is a prose rule that an agent tester is asked to follow. The fingerprint is an unsalted 24-bit hash of the plaintext key. That is not reversible for real high-entropy keys, but it is derived key material, and the project rule is that keys never reach a sink.
**Fix:** Make the contract executable:
- have `sample-evidence-filter.sh` (and the UI helper) reject or redact any line from a `key_state_*` node, or any token matching `fp [0-9a-f]{6}`;
- add a filter selftest that plants such a line;
- if no consumer is added, delete `neverEchoed` so there is no false sense of enforcement.

## Info

### IN-01: ApiKeyStoreVault.fingerprint silently degrades to a plain, green "Ready" when the key cannot be re-read

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt:294-296`, `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt:38-43`
**Issue:** `refreshKeys` reads the state, then separately decrypts the key again for the fingerprint. If the second read is not `Present` (the key was deleted or became unreadable between the two awaits, or the lookup is `Unreadable`), `fingerprint` is null and the row shows plain `Ready` with `Tone.GOOD`. The project values loud failure, and a missing fingerprint on a `Ready` row is the only signal. The second decrypt also puts the plaintext key into an immutable `String` on every refresh (after every save, delete and run).
**Fix:** Derive the fingerprint from the same read that produced the state, or treat a null fingerprint on a `Ready` state as `KeyView(provider, "Ready - fingerprint unavailable", Tone.BAD)`. Prefer a single `readSecret` in the vault.

### IN-02: verify-binary-diff.sh dead locals and ambiguous failure exit codes

**File:** `scripts/verify-binary-diff.sh:401-402, 380, 451`
**Issue:**
- `classes`, `present` and `added` are declared `local` in `run_diff` and never used.
- `work` is an unexplained lowercase global alias of `WORK`.
- `member_lines` runs `javap | awk | sort` under `pipefail`. If `javap` fails (for example a class it cannot load), `set -e` ends the script with javap's status (1 or 2) and no `BINARY DIFF ERROR` message. Status 1 is indistinguishable from "removed members".
- A failing `--out` write has the same effect.
**Fix:** Drop the unused locals and use `$WORK` throughout. Wrap the calls so a failure prints `BINARY DIFF ERROR: javap failed for <jar>` and exits 2:
```bash
member_lines "$work/old.jar" "$work/present.classes" >"$work/old.members" || err "javap failed on the old artifact"
```

### IN-03: JournalStoreTest no-disk-write test is environment-sensitive and has redundant needles

**File:** `undo/src/test/kotlin/io/github/ygaray/voiceactionengine/undo/JournalStoreTest.kt:527-572`
**Issue:**
- `treeOf(System.getProperty("user.dir"))` compares size and mtime of every file in the module directory (everything except `build/` and `.gradle/`). Any concurrent writer into the module directory (IDE index files, another Gradle fork writing under the module, a `.kotlin/` session dir) fails the test spuriously. The needle `java/io/File` already subsumes `java/io/FileOutputStream`.
- A hit in a class that merely names `java/io/File` in an error string would also trip it.
**Fix:** Keep the constant-pool scan, which is the deterministic half. Restrict the tree comparison to a freshly created temp directory used as `user.dir` for the `Rig`, or to `scratch` only. Reduce the needles to `java/io/File`, `java/nio/file/` and `java/io/RandomAccessFile`.

### IN-04: KeyUx.fingerprint uses an inline fully-qualified `java.security.MessageDigest`

**File:** `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt:339`
**Issue:** Style only: the rest of the file imports its types, and the digest is built on every call. This causes no behavioural problem. `"%02x".format(Byte)` is correct for negative bytes (unsigned two-digit hex), and the unit test pins `ba7816` for "abc".
**Fix:** `import java.security.MessageDigest`.

---

_Reviewed: 2026-10-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
