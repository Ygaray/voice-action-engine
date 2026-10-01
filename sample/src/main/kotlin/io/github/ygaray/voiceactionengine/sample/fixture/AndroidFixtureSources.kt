package io.github.ygaray.voiceactionengine.sample.fixture

import android.content.Context
import java.io.File
import java.io.FileNotFoundException

/**
 * Path of the fixture inside the app's private files directory. The Gate-1 script pushes the file here
 * (`run-sample-gate1.sh push-fixture`, plan 10-07). Never committed: the host copy lives under the gitignored
 * `sample/src/debug/assets/`.
 */
internal const val FIXTURE_FILES_PATH = "fixture/sb-a10-fixture.json"

/**
 * Name of the fixture as a debug asset, copied by hand into the gitignored `sample/src/debug/assets/` on the host.
 * This file holds the only runtime reference to the fixture name; no build script reads or names it.
 */
internal const val FIXTURE_ASSET_NAME = "sb-a10-fixture.json"

internal const val FILES_SOURCE_LABEL = "files/$FIXTURE_FILES_PATH"
internal const val ASSET_SOURCE_LABEL = "asset/$FIXTURE_ASSET_NAME"

/** The fixture sources in search order: app-private files first, then the debug asset. */
internal fun androidFixtureSources(context: Context): List<NamedFixtureSource> = listOf(
    NamedFixtureSource(FILES_SOURCE_LABEL) {
        val file = File(context.filesDir, FIXTURE_FILES_PATH)
        if (file.isFile) file.readBytes() else null
    },
    NamedFixtureSource(ASSET_SOURCE_LABEL) {
        try {
            context.assets.open(FIXTURE_ASSET_NAME).use { it.readBytes() }
        } catch (missing: FileNotFoundException) {
            null
        }
    },
)
