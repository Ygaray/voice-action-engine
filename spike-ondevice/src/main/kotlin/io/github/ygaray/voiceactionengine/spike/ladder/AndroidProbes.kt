package io.github.ygaray.voiceactionengine.spike.ladder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.delay
import java.io.File

private const val BYTES_PER_MB = 1024L * 1024L
private const val KB_PER_MB = 1024
private const val NANOS_PER_MILLI = 1_000_000L
private const val STATUS_UNKNOWN = "unknown"
private const val REASON_OTHER = "other"
private const val NO_BATTERY = -1
private const val DEFAULT_PAGE_SIZE = 4096L

// The system's OpenCL library, where the GPU backend loads it from (the manifest declares it optional).
private val OPENCL_PATHS = listOf(
    "/vendor/lib64/libOpenCL.so",
    "/system/vendor/lib64/libOpenCL.so",
    "/system/lib64/libOpenCL.so",
    "/vendor/lib64/egl/libGLES_mali.so",
)

/**
 * The Android [Probes]: in-process PSS (`Debug.getMemoryInfo` `totalPss`), `PowerManager.getCurrentThermalStatus`,
 * `ActivityManager.getHistoricalProcessExitReasons`, `StatFs`, `Build` and the monotonic clock. It reads numbers and closed
 * words only. Framework-bound, so it is exercised on the TESTER (plan 13-08), not on the JVM.
 */
internal class AndroidProbes(private val context: Context, private val storageDir: File) : Probes {
    private val activityManager: ActivityManager = context.getSystemService(ActivityManager::class.java)
    private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java)

    override fun pssMb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / KB_PER_MB
    }

    override fun thermalStatus(): String = when (powerManager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> STATUS_UNKNOWN
    }

    override fun exitReasons(sinceEpochMs: Long): List<String> =
        activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 0)
            .filter { it.timestamp >= sinceEpochMs }
            .map { reasonWord(it.reason) }

    private fun reasonWord(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        else -> REASON_OTHER
    }

    override fun deviceFacts(): DeviceFacts {
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val battery = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: NO_BATTERY
        return DeviceFacts(
            memTotalMb = memory.totalMem / BYTES_PER_MB,
            memAvailMb = memory.availMem / BYTES_PER_MB,
            storageFreeMb = freeStorageMb(),
            abis = Build.SUPPORTED_ABIS.toList(),
            pageSize = pageSize(),
            openClPresent = OPENCL_PATHS.any { File(it).exists() },
            batteryPct = battery,
        )
    }

    // The external files directory may be absent on a device with no shared storage; its own parent is the next best volume.
    private fun freeStorageMb(): Long {
        val dir = generateSequence(storageDir) { it.parentFile }.firstOrNull { it.exists() } ?: return 0L
        return StatFs(dir.absolutePath).availableBytes / BYTES_PER_MB
    }

    private fun pageSize(): Long = try {
        Os.sysconf(OsConstants._SC_PAGESIZE)
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
        DEFAULT_PAGE_SIZE
    }

    override fun monotonicMs(): Long = System.nanoTime() / NANOS_PER_MILLI

    override fun epochMs(): Long = System.currentTimeMillis()

    override suspend fun pause(ms: Long) {
        delay(ms)
    }

    override fun toString(): String = "AndroidProbes"
}
