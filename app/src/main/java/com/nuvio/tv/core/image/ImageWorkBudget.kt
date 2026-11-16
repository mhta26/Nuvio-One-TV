package com.nuvio.tv.core.image

/** Image work must leave CPU and memory available for focus navigation and playback. */
internal data class ImageWorkBudget(
    val maxRequests: Int,
    val maxRequestsPerHost: Int,
    val decodeParallelism: Int
)

internal fun imageWorkBudgetFor(
    physicalRamBytes: Long,
    lowHeapTier: Boolean,
    lowRamDevice: Boolean
): ImageWorkBudget {
    // A 512 MB largeHeap does not turn a 2 GB TV into a high-memory device.
    // Keep the player's heap budget independent; this only schedules image work.
    val smallPhysicalMemory = physicalRamBytes in 1L..(3L * 1024 * 1024 * 1024)
    return if (smallPhysicalMemory || lowHeapTier || lowRamDevice) {
        ImageWorkBudget(maxRequests = 8, maxRequestsPerHost = 4, decodeParallelism = 2)
    } else {
        ImageWorkBudget(maxRequests = 32, maxRequestsPerHost = 16, decodeParallelism = 4)
    }
}
