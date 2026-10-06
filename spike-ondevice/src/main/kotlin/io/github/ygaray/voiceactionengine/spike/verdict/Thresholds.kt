package io.github.ygaray.voiceactionengine.spike.verdict

/**
 * The D-07 bar, one constant per `THRESHOLD key=value` line of 13-THRESHOLDS.md (the key in camelCase). This is the only
 * file that holds a threshold number; `ThresholdsParityTest` fails when a constant and the committed line differ or when
 * either side has an entry the other lacks.
 */
internal object Thresholds {
    const val warmP50Ms = 3000L
    const val warmP95Ms = 5000L
    const val coldMs = 20000L
    const val sustainedP50RatioMax = 1.5
    const val thermalRedAt = "severe"
    const val peakPssMbMax = 2000
    const val processDeathsMax = 0
    const val schemaValidMin = 0.98
    const val schemaValidNMin = 100
    const val semanticLbEnMin = 0.85
    const val semanticNEnMin = 50
    const val semanticLbEsMin = 0.80
    const val semanticNEsMin = 50
    const val falseWritesMax = 0
    const val negativesNMin = 30
    const val wilsonZ = 1.959964
    const val timeboxHours = 4
    const val pssIntervalMs = 250
    const val thermalIntervalS = 10
    const val sustainedMinTrials = 60
    const val sustainedMinSeconds = 120
    const val screenN = 20
    const val forcedSubsetN = 20
    const val pssCrosscheckTolerance = 0.10
}
