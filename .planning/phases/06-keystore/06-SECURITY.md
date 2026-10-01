---
phase: "6"
slug: keystore
status: secured
threats_open: 0
asvs_level: 1
audited_head: 7fed22dd9d0c25dbc3e9f6569fe724a42c7e779f
created: "2026-10-01"
---

# Phase 6 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app -> keystore | The app injects its own DataStore and calls save, delete, read, observe | BYO API key plaintext (save only), provider ids |
| keystore -> AndroidKeyStore | Hardware-backed AES key custody, getKey lookup, first-use creation | key handle, ciphertext, IV |
| keystore -> app DataStore file | Base64 ciphertext and IV pairs under the apps' existing pref names | ciphertext, IV (no plaintext on disk) |
| keystore -> provider seam | KeystoreCredentialSource feeds the engine CredentialSource | Credential (plaintext key inside the engine only) |
| library -> app | KeyState, exceptions, toString, cause codes | state, five stable cause codes (no last4, no free text) |
| test resources and evidence -> repository | Committed fixtures and device-run record | synthetic keys, test names |
| host -> TESTER device | Guarded instrumented runner | test APK, adb commands (TESTER only) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-06-01 | Info disclosure | KeyState, KeySlot, ApiKeyStore toString and save message | high | mitigate | Ready omits last4, names and lengths only; KeystoreCanaryTest, KeyStateTest | closed |
| T-06-02 | Info disclosure | IV reuse | high | mitigate | AesGcm.seal has no IV parameter; AesGcmTest distinct IVs | closed |
| T-06-03 | DoS | second DataStore on the app file | high | mitigate | constructor takes the app DataStore; verifyNoDataStoreCreation in check plus negative control | closed |
| T-06-04 | Tampering | DataStore types not exported | medium | mitigate | api scope plus verifyDatastoreIsApi and negative control | closed |
| T-06-05 | Info disclosure | plaintext on disk | high | mitigate | only Base64 ciphertext and IV written; ApiKeyStoreTest, KeystoreCanaryTest file-bytes sweep | closed |
| T-06-06 | Tampering | duplicated slot alias or pref key | high | mitigate | construction-time table validation; KeySlotValidationTest | closed |
| T-06-07 | Spoofing | key under the wrong provider | high | mitigate | slots keyed by provider; three-provider isolation tests; adapter stamps asked provider | closed |
| T-06-08 | DoS | torn ct/iv pair | medium | mitigate | one edit per save and delete; ApiKeyStoreAtomicityTest | closed |
| T-06-09 | Tampering | concurrent saves | medium | mitigate | per-store Mutex plus process-wide creation lock; 32-way concurrency test | closed |
| T-06-10 | Info disclosure | IV reuse (cipher path) | high | mitigate | provider-generated IV; AesGcmTest | closed |
| T-06-11 | Info disclosure | key echoed in a message | high | mitigate | messages carry names only; atomicity and canary tests | closed |
| T-06-12 | Tampering | decrypt path creating a key after restore | high | mitigate | SecretReader only calls existingKey; ReadNeverCreatesKeyTest, device test | closed |
| T-06-13 | DoS | transient lookup failure read as KeyMissing | medium | mitigate | only a null lookup is KeyMissing; thrown failure is keystore_unavailable | closed |
| T-06-14 | DoS | corrupt stored value crashing the caller | high | mitigate | per-step catch chains to Unreadable; observe survives corruption | closed |
| T-06-15 | DoS | cancellation swallowed | medium | mitigate | CancellationException branch first everywhere; cancellation tests | closed |
| T-06-16 | Repudiation | auto-clearing a key on a bad read | medium | mitigate | no write on any read path; update counter assertions | closed |
| T-06-17 | Info disclosure | last4 or free text in a cause | high | mitigate | cause restricted to [a-z0-9_]+ and the five KeystoreCauses values | closed |
| T-06-18 | DoS | byte or Base64 format change stranding keys | high | mitigate | independent replica both directions plus golden vector; device compat cases PASS | closed |
| T-06-19 | Tampering | tautological compat tests | medium | mitigate | replica references no library internals (grep) | closed |
| T-06-20 | Info disclosure | real key in a test constant | medium | mitigate | synthetic strings with secret-scan allow marker | closed |
| T-06-21 | Tampering | store touching non-provider secrets | medium | mitigate | provider rows only; MCP-shaped pair untouched test | closed |
| T-06-22 | Spoofing | Credential for the wrong provider | high | mitigate | adapter builds Credential from the asked id; ModelRouter mismatch refusal | closed |
| T-06-23 | DoS | throwing adapter becoming an engine fault | high | mitigate | total state mapping; never-throws matrix; credential_source_error absent (see O-1) | closed |
| T-06-24 | Info disclosure | key in toString, exception or file | high | mitigate | KeystoreCanaryTest sweep; scanBannedConstructs in check | closed |
| T-06-25 | Info disclosure | plaintext getter | high | mitigate | readSecret and SecretRead internal; adapter is the only public path | closed |
| T-06-26 | Tampering | accidental data class or default args freezing API | medium | mitigate | KeystoreApiShapeTest with self-proving synthetic classes | closed |
| T-06-27 | Tampering | transient Keystore2 error read as absent key | high | mitigate | getKey-only lookup that propagates errors; code-line grep ban; tests | closed |
| T-06-28 | Tampering | racing first-use key generation | high | mitigate | process-global creation lock; eight-thread device test PASS on TESTER (see O-2) | closed |
| T-06-29 | DoS | key spec differing from the ports | high | mitigate | spec copied from the ports; no auth binding or StrongBox; device compat PASS | closed |
| T-06-30 | Elevation | instrumented test touching real app keys | medium | mitigate | separate test package namespace; cleanup deletes only listed aliases | closed |
| T-06-31 | Info disclosure | real key in a device test | low | mitigate | synthetic keys only | closed |
| T-06-32 | Elevation | running on the personal phone or another device | high | mitigate | fixed TESTER serials, adb -s, identity check, foreign serial and personal IP refused; DEVICE GUARD OK | closed |
| T-06-33 | Repudiation | device outage reported as a pass | high | mitigate | INFRA exit codes, final line, PASS needs OK (N>=7 tests) | closed |
| T-06-34 | DoS | colliding with another device tester | medium | mitigate | host flock lock; foreground activity printed; no retry loop | closed |
| T-06-35 | Tampering | api.txt leaking before the cut | medium | mitigate | dump in isolated copy only; hygiene check; no api.txt in tree | closed |
| T-06-36 | Info disclosure | key-shaped string in device evidence | low | mitigate | evidence holds test names and counts only | closed |
| T-06-SC | Tampering | new androidx.test coordinates | low | accept | AR-06-01 | closed |

*Status: open · closed · open - below high threshold (non-blocking)*
*Severity: critical > high > medium > low - only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-06-01 | T-06-SC | androidx.test runner 1.7.0 and ext-junit 1.3.0 are first-party Google Maven coordinates, pinned in STACK.md, androidTestImplementation only (on no published classpath), resolved offline from the local cache. RESEARCH Package Legitimacy Audit: Approved, no SUS/SLOP. | plan 06-01 threat model | 2026-10-01 |

---

## Observations (non-blocking)

- O-1 (T-06-23 edge): KeyStore.load(null) declares IOException; if it ever threw, read and the credential source would surface it as an engine-wrapped failure (credential_source_error) and observe would label it storage_unreadable. A lost key never becomes an engine fault, so the threat as registered stays closed.
- O-2 (T-06-28 scope): the creation lock covers only this library's stores in one process. The old SecondBrain and CalTracker code uses its own monitor, so the Wave-1 migrations must keep one writer per alias (06-REVIEW-FIX WR-05). Carry this into the migration plans.
- O-3: the TESTER serial and tailnet addresses are committed in the runner and guard scripts (06-REVIEW IN-03, skipped as the deliberate identity guard). Not a keystore secret.
- O-4: the device-run evidence predates the post-review error-classification changes (WR-01..03); the key-custody code it proves is unchanged since (KDoc only). Gate-1 re-runs the instrumented class on the TESTER.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 37 | 37 | 0 | gsd-security-auditor (verdict SECURED, ASVS 1, block_on high) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-01
