package com.universalgameai.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class VisionResult(
    val width: Int,
    val height: Int,
    val averageBrightness: Float,
    val edgeDensity: Float,
    val motionScore: Float,
    val hasVisualContent: Boolean
)

class VisionEngine {

    private var previousBitmap: Bitmap? = null

    /**
     * Analyse one real screen frame.
     *
     * This is intentionally local and lightweight.
     * Later this layer will feed:
     *
     * Bitmap
     *   -> UI/object detection
     *   -> game-state extraction
     *   -> AI decision engine
     */
    fun analyze(
        bitmap: Bitmap
    ): VisionResult {

        require(!bitmap.isRecycled) {
            "Bitmap has already been recycled"
        }

        val width = bitmap.width
        val height = bitmap.height

        if (width <= 0 || height <= 0) {
            return VisionResult(
                width = width,
                height = height,
                averageBrightness = 0f,
                edgeDensity = 0f,
                motionScore = 0f,
                hasVisualContent = false
            )
        }

        /*
         * We analyse a small sample instead of every pixel.
         * This keeps the first local vision stage fast enough
         * for continuous Android operation.
         */
        val sampleWidth = min(width, 160)
        val sampleHeight = min(height, 160)

        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            sampleWidth,
            sampleHeight,
            true
        )

        var brightnessSum = 0.0
        var edgeSum = 0.0
        var pixelCount = 0

        val pixels = IntArray(
            sampleWidth * sampleHeight
        )

        scaled.getPixels(
            pixels,
            0,
            sampleWidth,
            0,
            0,
            sampleWidth,
            sampleHeight
        )

        for (y in 0 until sampleHeight) {

            for (x in 0 until sampleWidth) {

                val index =
                    y * sampleWidth + x

                val color =
                    pixels[index]

                val r =
                    Color.red(color)

                val g =
                    Color.green(color)

                val b =
                    Color.blue(color)

                val brightness =
                    (0.299 * r +
                     0.587 * g +
                     0.114 * b)

                brightnessSum += brightness

                /*
                 * Compare with the pixel immediately to
                 * the left to obtain a lightweight edge
                 * estimate.
                 */
                if (x > 0) {

                    val previousColor =
                        pixels[index - 1]

                    val previousBrightness =
                        (
                            0.299 * Color.red(previousColor) +
                            0.587 * Color.green(previousColor) +
                            0.114 * Color.blue(previousColor)
                        )

                    edgeSum += abs(
                        brightness -
                        previousBrightness
                    )
                }

                pixelCount++
            }
        }

        val averageBrightness =
            if (pixelCount == 0) {
                0f
            } else {
                (brightnessSum / pixelCount)
                    .toFloat()
            }

        val edgeDensity =
            if (pixelCount == 0) {
                0f
            } else {
                min(
                    edgeSum /
                        (pixelCount * 255.0),
                    1.0
                ).toFloat()
            }

        val motionScore =
            calculateMotion(
                scaled
            )

        /*
         * Avoid retaining the scaled bitmap.
         */
        scaled.recycle()

        /*
         * Keep a small previous frame for local motion
         * estimation.
         */
        previousBitmap?.recycle()

        previousBitmap =
            Bitmap.createScaledBitmap(
                bitmap,
                sampleWidth,
                sampleHeight,
                true
            )

        return VisionResult(
            width = width,
            height = height,
            averageBrightness = averageBrightness / 255f,
            edgeDensity = edgeDensity,
            motionScore = motionScore,
            hasVisualContent =
                averageBrightness > 2f
        )
    }

    private fun calculateMotion(
        current: Bitmap
    ): Float {

        val previous =
            previousBitmap
                ?: return 0f

        if (
            previous.width != current.width ||
            previous.height != current.height
        ) {
            return 0f
        }

        val totalPixels =
            current.width *
                current.height

        if (totalPixels <= 0) {
            return 0f
        }

        val currentPixels =
            IntArray(totalPixels)

        val previousPixels =
            IntArray(totalPixels)

        current.getPixels(
            currentPixels,
            0,
            current.width,
            0,
            0,
            current.width,
            current.height
        )

        previous.getPixels(
            previousPixels,
            0,
            previous.width,
            0,
            0,
            previous.width,
            previous.height
        )

        var difference = 0.0

        /*
         * Sample every few pixels instead of comparing
         * the entire image.
         */
        var sampled = 0

        var index = 0

        while (index < totalPixels) {

            val currentColor =
                currentPixels[index]

            val previousColor =
                previousPixels[index]

            val currentBrightness =
                (
                    0.299 * Color.red(currentColor) +
                    0.587 * Color.green(currentColor) +
                    0.114 * Color.blue(currentColor)
                )

            val previousBrightness =
                (
                    0.299 * Color.red(previousColor) +
                    0.587 * Color.green(previousColor) +
                    0.114 * Color.blue(previousColor)
                )

            difference += abs(
                currentBrightness -
                previousBrightness
            )

            sampled++

            /*
             * Sampling stride.
             */
            index += 4
        }

        if (sampled == 0) {
            return 0f
        }

        return min(
            difference /
                (sampled * 255.0),
            1.0
        ).toFloat()
    }

    fun reset() {

        previousBitmap?.recycle()
        previousBitmap = null
    }
}
