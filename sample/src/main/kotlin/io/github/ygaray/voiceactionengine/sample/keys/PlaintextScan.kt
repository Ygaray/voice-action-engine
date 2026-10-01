package io.github.ygaray.voiceactionengine.sample.keys

import java.io.File
import java.io.IOException

/**
 * Looks for a secret's bytes in files, to prove a plaintext key did not land in storage. It answers yes or no and never
 * returns, prints or keeps file content or the needle.
 */
internal object PlaintextScan {
    /**
     * True when any regular file under [root] contains [needle]. A missing root, an empty needle and unreadable files
     * all count as "not found".
     */
    fun contains(root: File, needle: ByteArray): Boolean {
        if (needle.isEmpty() || !root.exists()) return false
        return root.walkTopDown().filter { it.isFile }.any { fileHolds(it, needle) }
    }

    private fun fileHolds(file: File, needle: ByteArray): Boolean {
        val bytes = try {
            file.readBytes()
        } catch (ignored: IOException) {
            return false
        }
        return indexOf(bytes, needle)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Boolean {
        val last = haystack.size - needle.size
        var start = 0
        while (start <= last) {
            if (matchesAt(haystack, needle, start)) return true
            start++
        }
        return false
    }

    private fun matchesAt(haystack: ByteArray, needle: ByteArray, start: Int): Boolean {
        for (offset in needle.indices) {
            if (haystack[start + offset] != needle[offset]) return false
        }
        return true
    }
}
