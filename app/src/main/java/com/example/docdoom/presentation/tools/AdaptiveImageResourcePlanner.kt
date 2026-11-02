package com.example.docdoom.presentation.tools

import android.app.ActivityManager
import android.content.Context
import kotlin.math.min

/**
 * Chooses an image-processing path from the current device state and the
 * decoded image dimensions. It deliberately does not use compressed file size
 * as a proxy for memory use.
 *
 * This class only plans the processing path. The STREAMING_ENGINE path must be
 * connected to a native streaming backend (for example libvips) before it can
 * process oversized images without a full Android Bitmap.
 */
object AdaptiveImageResourcePlanner {

    enum class ProcessingPath {
        ANDROID_BITMAP,
        STREAMING_ENGINE
    }

    data class DeviceSnapshot(
        val appHeapLimitBytes: Long,
        val appHeapUsedBytes: Long,
        val appHeapAvailableBytes: Long,
        val systemAvailableBytes: Long,
        val systemLowMemoryThresholdBytes: Long,
        val memoryClassBytes: Long,
        val isLowRamDevice: Boolean,
        val isSystemLowMemory: Boolean
    )

    data class ProcessingPlan(
        val path: ProcessingPath,
        val width: Int,
        val height: Int,
        val estimatedDecodedBytes: Long,
        val estimatedPeakBytes: Long,
        val safeWorkingBudgetBytes: Long,
        val reason: String
    ) {
        val requiresStreamingEngine: Boolean
            get() = path == ProcessingPath.STREAMING_ENGINE
    }

    /** Read current memory conditions. Call shortly before starting a job. */
    fun readDeviceSnapshot(context: Context): DeviceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val heapLimit = runtime.maxMemory().coerceAtLeast(1L)
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val heapAvailable = (heapLimit - heapUsed).coerceAtLeast(0L)
        val memoryClass = activityManager.memoryClass.toLong() * MEBIBYTE

        return DeviceSnapshot(
            appHeapLimitBytes = heapLimit,
            appHeapUsedBytes = heapUsed,
            appHeapAvailableBytes = heapAvailable,
            systemAvailableBytes = memoryInfo.availMem.coerceAtLeast(0L),
            systemLowMemoryThresholdBytes = memoryInfo.threshold.coerceAtLeast(0L),
            memoryClassBytes = memoryClass.coerceAtLeast(1L),
            isLowRamDevice = activityManager.isLowRamDevice,
            isSystemLowMemory = memoryInfo.lowMemory
        )
    }

    /**
     * Estimate peak memory for a full-resolution bitmap operation.
     * The multiplier accounts for decoder/encoder working buffers and temporary
     * allocations; it is intentionally conservative and can be tuned using
     * measurements from real devices.
     */
    fun createPlan(
        context: Context,
        width: Int,
        height: Int,
        bytesPerPixel: Int = 4
    ): ProcessingPlan {
        require(width > 0) { "Image width must be positive." }
        require(height > 0) { "Image height must be positive." }
        require(bytesPerPixel > 0) { "Bytes per pixel must be positive." }

        val snapshot = readDeviceSnapshot(context)
        val pixels = safeMultiply(width.toLong(), height.toLong())
        val decodedBytes = safeMultiply(pixels, bytesPerPixel.toLong())
        val peakBytes = safeMultiply(decodedBytes, PEAK_MEMORY_MULTIPLIER)

        // Do not spend all available memory on one image operation. The budget
        // adapts to both the app heap and current system memory pressure.
        val appBudget = (snapshot.appHeapAvailableBytes * APP_HEAP_BUDGET_FRACTION).toLong()
        val classBudget = (snapshot.memoryClassBytes * MEMORY_CLASS_BUDGET_FRACTION).toLong()
        val systemHeadroom = (snapshot.systemAvailableBytes - snapshot.systemLowMemoryThresholdBytes)
            .coerceAtLeast(0L)
        val systemBudget = (systemHeadroom * SYSTEM_HEADROOM_BUDGET_FRACTION).toLong()

        var budget = min(appBudget, min(classBudget, systemBudget)).coerceAtLeast(0L)

        // On low-RAM devices or while Android reports memory pressure, reduce
        // the amount of memory this feature is allowed to consume further.
        if (snapshot.isLowRamDevice) budget = (budget * LOW_RAM_REDUCTION).toLong()
        if (snapshot.isSystemLowMemory) budget = (budget * MEMORY_PRESSURE_REDUCTION).toLong()

        val useBitmap = !snapshot.isSystemLowMemory && peakBytes <= budget
        val path = if (useBitmap) ProcessingPath.ANDROID_BITMAP else ProcessingPath.STREAMING_ENGINE
        val reason = when {
            snapshot.isSystemLowMemory -> "The device is currently under memory pressure."
            useBitmap -> "The image fits the current safe bitmap-processing budget."
            else -> "A streaming image-processing path is recommended for this image and device state."
        }

        return ProcessingPlan(
            path = path,
            width = width,
            height = height,
            estimatedDecodedBytes = decodedBytes,
            estimatedPeakBytes = peakBytes,
            safeWorkingBudgetBytes = budget,
            reason = reason
        )
    }

    private fun safeMultiply(first: Long, second: Long): Long {
        if (first == 0L || second == 0L) return 0L
        if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE
        return first * second
    }

    private const val MEBIBYTE = 1024L * 1024L
    private const val PEAK_MEMORY_MULTIPLIER = 3L
    private const val APP_HEAP_BUDGET_FRACTION = 0.30
    private const val MEMORY_CLASS_BUDGET_FRACTION = 0.20
    private const val SYSTEM_HEADROOM_BUDGET_FRACTION = 0.08
    private const val LOW_RAM_REDUCTION = 0.65
    private const val MEMORY_PRESSURE_REDUCTION = 0.50
}
