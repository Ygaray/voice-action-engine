### Phase 6 — keystore (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/06-keystore/06-07-SELF-UAT.md`](phases/06-keystore/06-07-SELF-UAT.md) — Verdict: **ALL 4 criteria PASS** (device SM-S908U TESTER R5CT10XNKQN, androidTest APK md5 `6ba7a5a27c1bb3edc7b274197095390e` @ `8d9382d`, 2026-10-01). Guarded runner `scripts/run-keystore-instrumented.sh` -> `OK (7 tests)` on the real AndroidKeyStore with real AES/GCM and file DataStores, on the post-code-review HEAD; JVM `:keystore` suite 96 tests, 0 failures.
- **Items covered (4 ROADMAP success criteria):**
  - **SC1 — Store/read/delete per provider.** Real AES/GCM ciphertext persisted in a caller-owned DataStore (decrypted by the verbatim legacy code on-device; a flipped byte reads `decrypt_failed`). Delete leg is JVM-proven (`ApiKeyStoreTest`), not on device.
  - **SC2 — SB/CT legacy compat both ways.** SB (1 alias) and CT (3 aliases) legacy-written blobs read back unchanged; engine-written blobs decrypt with the legacy code; framework Base64 NO_WRAP equals `java.util.Base64`.
  - **SC3 — Read states / KeyMissing / one key.** Deleted device key reads `KeyMissing` and `getKey` stays null after both read paths; 8-way concurrent first use leaves every store reading its own key.
  - **SC4 — Provider seam.** `KeystoreCredentialSource` yields `Present` with the exact key on hardware and `Unreadable("key_missing")` when the key is gone; JVM round trip through the crypto seam passes.
- **Owner how-to-verify (run at milestone completion; no physical step):**
  1. Read the Gate-1 log above for per-criterion evidence and `evidence/keystore-instrumented-run-gate1.txt` for the verbatim runner output.
  2. Optionally re-run `bash scripts/run-keystore-instrumented.sh` at milestone HEAD with the TESTER idle; expect the last line `KEYSTORE_INSTRUMENTED: PASS tests=7 ...` and `test package removed`.
  3. Phase 10 live obligation (not Phase 6): a real saved BYO key reaching a live provider call from `:sample`.
- **Note:** Library phase, no UI and no physical step. One observed hardening opportunity, not a defect: no single device test does `ApiKeyStore.save` immediately followed by `KeystoreCredentialSource.credential` == Present (both halves are hardware-proven separately; the composite is JVM-proven). `api.txt` is still created at the v1.0.0 cut in Phase 11.
