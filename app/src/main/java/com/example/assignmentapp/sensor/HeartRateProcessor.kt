package com.example.assignmentapp.sensor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

interface HeartRateProcessor {
    suspend fun calculate(videoUri: Uri): Int
}

class HeartRateProcessingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class VideoHeartRateProcessor(context: Context) : HeartRateProcessor {
    private val applicationContext = context.applicationContext

    override suspend fun calculate(videoUri: Uri): Int = withContext(Dispatchers.IO) {
        if (videoUri.toString().isBlank()) {
            throw HeartRateProcessingException("No heart-rate video was provided.")
        }

        val retriever = MediaMetadataRetriever()
        try {
            try {
                retriever.setDataSource(applicationContext, videoUri)
            } catch (error: SecurityException) {
                throw HeartRateProcessingException(
                    "The heart-rate video cannot be read.",
                    error
                )
            } catch (error: RuntimeException) {
                throw HeartRateProcessingException(
                    "The heart-rate video could not be opened.",
                    error
                )
            }

            val frameCount = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toIntOrNull()?.takeIf { it > 0 }
                ?: throw HeartRateProcessingException("The heart-rate video is malformed.")
            val colorTotals = mutableListOf<Long>()
            val frameLimit = min(frameCount, 425)
            var frameIndex = 10

            while (frameIndex < frameLimit) {
                retriever.getFrameAtIndex(frameIndex)?.let { frame ->
                    try {
                        if (frame.width < 450 || frame.height < 450) {
                            throw HeartRateProcessingException(
                                "The heart-rate video is malformed."
                            )
                        }
                        colorTotals += frame.centerColorTotal()
                    } finally {
                        frame.recycle()
                    }
                }
                frameIndex += 15
            }

            calculateBeatsPerMinute(colorTotals)
        } catch (error: HeartRateProcessingException) {
            throw error
        } catch (error: RuntimeException) {
            throw HeartRateProcessingException(
                "The heart-rate video is malformed.",
                error
            )
        } finally {
            retriever.release()
        }
    }
}

private fun Bitmap.centerColorTotal(): Long {
    var total = 0L
    for (y in 350 until 450) {
        for (x in 350 until 450) {
            val color = getPixel(x, y)
            total += Color.red(color) + Color.green(color) + Color.blue(color)
        }
    }
    return total
}

internal fun calculateBeatsPerMinute(colorTotals: List<Long>): Int {
    if (colorTotals.size < 9) {
        throw HeartRateProcessingException(
            "The heart-rate video does not contain enough usable frames."
        )
    }

    val smoothedTotals = mutableListOf<Long>()
    for (index in 0 until colorTotals.lastIndex - 5) {
        val total = colorTotals[index] +
            colorTotals[index + 1] +
            colorTotals[index + 2] +
            colorTotals[index + 3] +
            colorTotals[index + 4]
        smoothedTotals += total / 4
    }

    var previous = smoothedTotals.first()
    var pulseCount = 0
    for (index in 1 until smoothedTotals.lastIndex) {
        val current = smoothedTotals[index]
        if (current - previous > 3_500) {
            pulseCount++
        }
        previous = current
    }

    return pulseCount * 60 / 4
}
