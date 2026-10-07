package com.universalgameai.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import com.universalgameai.ScreenCaptureServiceContract
import com.universalgameai.model.GameState
import com.universalgameai.vision.FrameConverter
import com.universalgameai.vision.VisionEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class ScreenCaptureService : Service() {

    companion object {

        private const val CHANNEL_ID =
            "universal_game_ai_capture"

        private const val CHANNEL_NAME =
            "Game AI Screen Capture"

        private const val NOTIFICATION_ID = 1001

        private const val IMAGE_FORMAT =
            PixelFormat.RGBA_8888

        @Volatile
        var isCapturing = false
            private set

        @Volatile
        var capturedWidth = 0
            private set

        @Volatile
        var capturedHeight = 0
            private set

        @Volatile
        var measuredFps = 0.0
            private set

        @Volatile
        var frameLatencyMs = 0.0
            private set

        @Volatile
        var totalFrames = 0L
            private set

        @Volatile
        var analyzedFrames = 0L
            private set

        @Volatile
        var lastAnalysisTimeMs = 0.0
            private set

        @Volatile
        var latestGameState: GameState? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private val running = AtomicBoolean(false)

    private val visionEngine =
        VisionEngine()

    private var lastFrameTimeNs = 0L

    private var fpsWindowStartNs = 0L
    private var fpsWindowFrames = 0L

    private var projectionCallback:
        MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        captureThread =
            HandlerThread(
                "UGAI-ScreenCapture",
                Process.THREAD_PRIORITY_DISPLAY
            )

        captureThread.start()

        captureHandler =
            Handler(captureThread.looper)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode =
            intent.getIntExtra(
                ScreenCaptureServiceContract.RESULT_CODE,
                -1
            )

        val projectionData =
            getParcelableIntentExtra(
                intent,
                ScreenCaptureServiceContract.DATA_INTENT
            )

        if (
            resultCode < 0 ||
            projectionData == null
        ) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()

        startCapture(
            resultCode,
            projectionData
        )

        return START_NOT_STICKY
    }

    private fun startCapture(
        resultCode: Int,
        projectionData: Intent
    ) {

        if (running.get()) {
            return
        }

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            projectionManager.getMediaProjection(
                resultCode,
                projectionData
            )

        if (mediaProjection == null) {
            stopSelf()
            return
        }

        val metrics =
            resources.displayMetrics

        val width =
            max(metrics.widthPixels, 1)

        val height =
            max(metrics.heightPixels, 1)

        val density =
            max(metrics.densityDpi, 1)

        capturedWidth = width
        capturedHeight = height

        imageReader =
            ImageReader.newInstance(
                width,
                height,
                IMAGE_FORMAT,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatestImage(reader)
            },
            captureHandler
        )

        projectionCallback =
            object : MediaProjection.Callback() {

                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            }

        mediaProjection?.registerCallback(
            projectionCallback!!,
            captureHandler
        )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "UniversalGameAI-Capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                captureHandler
            )

        running.set(true)
        isCapturing = true

        lastFrameTimeNs = 0L
        fpsWindowStartNs = System.nanoTime()
        fpsWindowFrames = 0L

        totalFrames = 0L
        analyzedFrames = 0L

        measuredFps = 0.0
        frameLatencyMs = 0.0
        lastAnalysisTimeMs = 0.0

        latestGameState = null

        visionEngine.reset()
    }

    private fun processLatestImage(
        reader: ImageReader
    ) {

        if (!running.get()) {
            return
        }

        var image: Image? = null
        var bitmap = null as android.graphics.Bitmap?

        try {

            /*
             * Always take the newest frame.
             * Old queued frames are intentionally discarded.
             */
            image =
                reader.acquireLatestImage()

            if (image == null) {
                return
            }

            val nowNs =
                System.nanoTime()

            updateFrameMetrics(
                nowNs,
                image
            )

            /*
             * REAL FRAME CONVERSION
             */
            bitmap =
                FrameConverter.imageToBitmap(
                    image
                )

            if (bitmap == null) {
                return
            }

            /*
             * Resize only for vision processing.
             * The original capture resolution remains
             * available through capturedWidth/Height.
             */
            val visionBitmap =
                FrameConverter.resizeForVision(
                    bitmap,
                    maxWidth = 640,
                    maxHeight = 640
                )

            val analysisStartNs =
                System.nanoTime()

            /*
             * REAL LOCAL VISION ANALYSIS
             */
            val visionResult =
                visionEngine.analyze(
                    visionBitmap
                )

            val analysisEndNs =
                System.nanoTime()

            lastAnalysisTimeMs =
                (
                    analysisEndNs -
                            analysisStartNs
                    ) / 1_000_000.0

            /*
             * REAL GAME STATE CREATION
             */
            latestGameState =
                GameState.fromVision(
                    visionResult
                )

            analyzedFrames++

            /*
             * resizeForVision() can return a copy or a
             * newly-created scaled bitmap. Release it here.
             */
            if (visionBitmap !== bitmap) {
                visionBitmap.recycle()
            }

        } catch (_: Exception) {

            /*
             * Projection shutdown and device display changes
             * can invalidate an Image. We intentionally keep
             * the capture service alive when one frame fails.
             */

        } finally {

            bitmap?.recycle()

            image?.close()
        }
    }

    private fun updateFrameMetrics(
        nowNs: Long,
        image: Image
    ) {

        if (lastFrameTimeNs != 0L) {

            val deltaNs =
                nowNs - lastFrameTimeNs

            if (deltaNs > 0L) {

                val instantFps =
                    1_000_000_000.0 /
                            deltaNs

                measuredFps =
                    if (measuredFps == 0.0) {
                        instantFps
                    } else {
                        measuredFps * 0.85 +
                                instantFps * 0.15
                    }
            }
        }

        lastFrameTimeNs = nowNs

        totalFrames++
        fpsWindowFrames++

        /*
         * Image timestamp is generated by the Android
         * graphics pipeline.
         */
        val imageTimestampNs =
            image.timestamp

        if (
            imageTimestampNs > 0L &&
            nowNs >= imageTimestampNs
        ) {

            frameLatencyMs =
                (
                    nowNs -
                            imageTimestampNs
                    ) / 1_000_000.0
        }

        updateWindowedFps(nowNs)
    }

    private fun updateWindowedFps(
        nowNs: Long
    ) {

        val elapsedNs =
            nowNs - fpsWindowStartNs

        if (
            elapsedNs >=
            1_000_000_000L
        ) {

            val seconds =
                elapsedNs /
                        1_000_000_000.0

            if (seconds > 0.0) {

                measuredFps =
                    fpsWindowFrames /
                            seconds
            }

            fpsWindowStartNs = nowNs
            fpsWindowFrames = 0L
        }
    }

    private fun stopCapture() {

        if (!running.getAndSet(false)) {
            return
        }

        isCapturing = false

        try {
            imageReader?.setOnImageAvailableListener(
                null,
                null
            )
        } catch (_: Exception) {
        }

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }

        imageReader = null

        try {
            projectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (_: Exception) {
        }

        projectionCallback = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null

        visionEngine.reset()

        latestGameState = null
    }

    private fun startForegroundServiceNotification() {

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Universal Game AI"
                )
                .setContentText(
                    "Screen observation is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "Universal Game AI screen observation"

                setShowBadge(false)
            }

        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun getParcelableIntentExtra(
        intent: Intent,
        key: String
    ): Intent? {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            intent.getParcelableExtra(
                key,
                Intent::class.java
            )

        } else {

            intent.getParcelableExtra(key)
        }
    }

    override fun onDestroy() {

        stopCapture()

        if (::captureThread.isInitialized) {
            captureThread.quitSafely()
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
