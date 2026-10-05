#!/usr/bin/env bash
# Cross-module negative-compile proof for the key-custody seam (SEAM-07, D-07, T-12-09). Opt-in can only be proven from a
# module other than the one that owns the marker, so each plant is written into :sample (which has no opt-in flag).
#   (a) RED: ApiKeyStore(dataStore, slots, keyAccess) built without @OptIn          -> the constructor is gated
#   (b) RED: a class implementing KeyAccess without @OptIn                          -> the interface is gated
#   (c) GREEN (positive control): (a) with @OptIn added                             -> (a) went red because of the opt-in only
# Every plant is removed on exit (trap), including on Ctrl-C. Never commit a plant. Run: scripts/verify-keyaccess-opt-in.sh
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
PLANT="sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ZzOptInPlant.kt"
LOG="$(mktemp)"
cleanup() { rm -f "$PLANT" "$LOG"; }
# INT and TERM must exit: a trap that only cleans up lets the script carry on and start the next plant. The exit fires
# the EXIT trap, so cleanup runs once.
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
fails=0
# The Kotlin opt-in error prints the marker's own message (not a fixed "needs opt-in" text), so the proof greps for that
# message, reported against the plant file. Keep it in step with DelicateKeyAccess.kt.
MARKER="caller-supplied key access and is meant for tests only"
PLANT_NAME="ZzOptInPlant.kt"

HEADER='package io.github.ygaray.voiceactionengine.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.DelicateKeyAccess
import io.github.ygaray.voiceactionengine.keystore.KeyAccess
import io.github.ygaray.voiceactionengine.keystore.KeySlot
import javax.crypto.SecretKey
'
KEYS_CLASS='@OptIn(DelicateKeyAccess::class)
internal class PlantKeys : KeyAccess {
    override fun existingKey(alias: String): SecretKey? = null
    override fun getOrCreateKey(alias: String): SecretKey = throw IllegalStateException("plant")
}
'

compile() { ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :sample:compileDebugKotlin >"$LOG" 2>&1; }

expect_red() { # <label>
  if compile; then
    echo "FAIL  [$1] stayed GREEN"; fails=$((fails+1))
  elif ! grep -F -- "$PLANT_NAME" "$LOG" | grep -qF -- "$MARKER"; then
    echo "FAIL  [$1] went red for the WRONG reason (no '$MARKER' error on $PLANT_NAME)"; fails=$((fails+1))
  else
    echo "ok    [$1] went red (opt-in error on $PLANT_NAME)"
  fi
}

expect_green() { # <label>
  if compile; then
    echo "ok    [$1] compiled"
  else
    echo "FAIL  [$1] did not compile"; fails=$((fails+1))
  fi
}

CONSTRUCT='(dataStore: DataStore<Preferences>, slots: List<KeySlot>): ApiKeyStore = ApiKeyStore(dataStore, slots, PlantKeys())'

printf '%s\n%s\ninternal fun build%s\n' "$HEADER" "$KEYS_CLASS" "$CONSTRUCT" > "$PLANT"
expect_red "constructor without @OptIn"

printf '%s\ninternal class PlantKeys : KeyAccess {\n    override fun existingKey(alias: String): SecretKey? = null\n    override fun getOrCreateKey(alias: String): SecretKey = throw IllegalStateException("plant")\n}\n' "$HEADER" > "$PLANT"
expect_red "KeyAccess implemented without @OptIn"

printf '%s\n%s\n@OptIn(DelicateKeyAccess::class)\ninternal fun build%s\n' "$HEADER" "$KEYS_CLASS" "$CONSTRUCT" > "$PLANT"
expect_green "positive control: same code with @OptIn"

rm -f "$PLANT"
echo "keyaccess opt-in failures: $fails"; exit $((fails>0))
