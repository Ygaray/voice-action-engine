package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.sample.keys.PlaintextScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaintextScanTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Synthetic bytes, deliberately not key-shaped.
    private val needle = "synthetic-needle-0000".toByteArray()

    @Test
    fun findsTheNeedleInANestedFile() {
        val root = tmp.newFolder("root")
        File(root, "a/b").mkdirs()
        File(root, "a/b/store.bin").writeBytes(byteArrayOf(9, 9) + needle + byteArrayOf(7))

        assertTrue(PlaintextScan.contains(root, needle))
    }

    @Test
    fun missesWhenAbsent() {
        val root = tmp.newFolder("root")
        File(root, "store.bin").writeBytes(ByteArray(64) { it.toByte() })
        File(root, "near.bin").writeBytes(needle.copyOf(needle.size - 1))

        assertFalse(PlaintextScan.contains(root, needle))
    }

    @Test
    fun aMissingRootIsFalse() {
        assertFalse(PlaintextScan.contains(File(tmp.root, "does-not-exist"), needle))
    }

    @Test
    fun handlesBinaryFilesAndBoundaries() {
        val root = tmp.newFolder("root")
        // A needle at the very end of a file, a needle that is the whole file, and an empty file.
        File(root, "end.bin").writeBytes(ByteArray(10_000) { (it % 251).toByte() } + needle)
        File(root, "whole.bin").writeBytes(needle)
        File(root, "empty.bin").writeBytes(ByteArray(0))

        assertTrue(PlaintextScan.contains(root, needle))
        assertFalse("an empty needle never matches", PlaintextScan.contains(root, ByteArray(0)))
        assertFalse(PlaintextScan.contains(tmp.newFolder("only-empty").also { File(it, "e").writeBytes(ByteArray(0)) }, needle))
    }

    @Test
    fun neverExposesTheNeedle() {
        // The scanner answers yes or no: its only function that takes a needle returns Boolean, and nothing returns bytes.
        val functions = PlaintextScan::class.java.declaredMethods.filter { !it.isSynthetic }
        assertTrue(functions.none { it.returnType == ByteArray::class.java })
        assertTrue(functions.filter { it.parameterTypes.contains(ByteArray::class.java) }.all { it.returnType == Boolean::class.javaPrimitiveType })
        assertFalse(PlaintextScan.toString().contains("synthetic-needle"))
    }
}
