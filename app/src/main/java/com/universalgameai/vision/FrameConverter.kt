package com.universalgameai.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.max

object FrameConverter {

    /**
     * Converts an ImageReader RGBA_8888 Image into a Bitmap.
     *
     * Handles rowStride/pixelStride correctly instead of
     * assuming that each row contains exactly width pixels.
     */
    fun imageToBitmap(
        image: Image
    ): Bitmap? {

        if (image.format != android.graphics.PixelFormat.RGBA_8888) {
            return null
        }

        val width = image.width
        val height = image.height

        if (width <= 0 || height <= 0) {
            return null
        }

        val plane = image.planes.firstOrNull()
            ?: return null

        val buffer: ByteBuffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        if (pixelStride <= 0 || rowStride <= 0) {
            return null
        }

        /*
         * Some Android devices add padding at the end
         * of every row. We therefore calculate the actual
         * row width from rowStride instead of assuming:
         *
         * width * 4
         */
        val rowPadding =
            max(
                0,
                rowStride - pixelStride * width
            )

        val paddedWidth =
            width + rowPadding / pixelStride

        val bitmapWithPadding =
            try {
                Bitmap.createBitmap(
                    paddedWidth,
                    height,
                    Bitmap.Config.ARGB_8888
                )
            } catch (_: OutOfMemoryError) {
                return null
            }

        return try {

            buffer.rewind()

            bitmapWithPadding.copyPixelsFromBuffer(
                buffer
            )

            /*
             * Remove any row padding so the returned bitmap
             * has exactly the captured screen dimensions.
             */
            if (paddedWidth == width) {

                bitmapWithPadding

            } else {

                val bitmap =
                    try {
                        Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                    } catch (_: OutOfMemoryError) {

                        bitmapWithPadding.recycle()
                        return null
                    }

                val canvas =
                    Canvas(bitmap)

                val paint =
                    Paint(Paint.FILTER_BITMAP_FLAG)

                canvas.drawBitmap(
                    bitmapWithPadding,
                    0f,
                    0f,
                    paint
                )

                bitmapWithPadding.recycle()

                bitmap
            }

        } catch (_: Exception) {

            bitmapWithPadding.recycle()
            null
        }
    }

    /**
     * Creates a smaller copy suitable for lightweight
     * local vision processing.
     */
    fun resizeForVision(
        bitmap: Bitmap,
        maxWidth: Int = 640,
        maxHeight: Int = 640
    ): Bitmap {

        require(!bitmap.isRecycled) {
            "Input bitmap has been recycled"
        }

        if (
            bitmap.width <= maxWidth &&
            bitmap.height <= maxHeight
        ) {
            return bitmap.copy(
                Bitmap.Config.ARGB_8888,
                false
            )
        }

        val widthRatio =
            maxWidth.toFloat() /
                    bitmap.width.toFloat()

        val heightRatio =
            maxHeight.toFloat() /
                    bitmap.height.toFloat()

        val scale =
            minOf(
                widthRatio,
                heightRatio
            )

        val newWidth =
            max(
                1,
                (bitmap.width * scale).toInt()
            )

        val newHeight =
            max(
                1,
                (bitmap.height * scale).toInt()
            )

        return Bitmap.createScaledBitmap(
            bitmap,
            newWidth,
            newHeight,
            true
        )
    }
}
