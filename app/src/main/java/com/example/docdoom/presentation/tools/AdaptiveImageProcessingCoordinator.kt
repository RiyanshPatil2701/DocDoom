package com.example.docdoom.presentation.tools

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Connects metadata inspection with the device-aware resource planner.
 * This class does not decode pixels or start compression.
 * Call it from a background dispatcher, not from the main thread.
 */
object AdaptiveImageProcessingCoordinator {

    data class InspectionResult(
        val metadata: ImageMetadataInspector.ImageMetadata,
        val plan: AdaptiveImageResourcePlanner.ProcessingPlan
    )

    @Throws(IOException::class)
    fun inspectAndPlan(
        context: Context,
        imageFile: File
    ): InspectionResult {
        val metadata = ImageMetadataInspector.inspect(imageFile)

        val plan = AdaptiveImageResourcePlanner.createPlan(
            context = context.applicationContext,
            width = metadata.width,
            height = metadata.height,
            bytesPerPixel = BYTES_PER_PIXEL
        )

        return InspectionResult(
            metadata = metadata,
            plan = plan
        )
    }

    private const val BYTES_PER_PIXEL = 4
}
