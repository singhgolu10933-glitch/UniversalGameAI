package com.universalgameai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.universalgameai.capture.ScreenCaptureService

class MainActivity : Activity() {

    companion object {
        private const val SCREEN_CAPTURE_REQUEST = 1001
        private const val NOTIFICATION_REQUEST = 1002

        private const val METRICS_INTERVAL_MS = 500L
    }

    private lateinit var statusText: TextView
    private lateinit var resolutionText: TextView
    private lateinit var fpsText: TextView
    private lateinit var latencyText: TextView
    private lateinit var frameText: TextView
    private lateinit var analyzedFrameText: TextView
    private lateinit var visionText: TextView
    private lateinit var gameStateText: TextView
    private lateinit var analysisTimeText: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val metricsHandler =
        Handler(Looper.getMainLooper())

    private val metricsRunnable =
        object : Runnable {

            override fun run() {

                updateMetrics()

                metricsHandler.postDelayed(
                    this,
                    METRICS_INTERVAL_MS
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        bindViews()

        setupButtons()

        requestNotificationPermissionIfNeeded()

        metricsHandler.post(
            metricsRunnable
        )
    }

    private fun bindViews() {

        statusText =
            findViewById(R.id.statusText)

        resolutionText =
            findViewById(R.id.resolutionText)

        fpsText =
            findViewById(R.id.fpsText)

        latencyText =
            findViewById(R.id.latencyText)

        frameText =
            findViewById(R.id.frameText)

        analyzedFrameText =
            findViewById(R.id.analyzedFrameText)

        visionText =
            findViewById(R.id.visionText)

        gameStateText =
            findViewById(R.id.gameStateText)

        analysisTimeText =
            findViewById(R.id.analysisTimeText)

        startButton =
            findViewById(R.id.startButton)

        stopButton =
            findViewById(R.id.stopButton)
    }

    private fun setupButtons() {

        startButton.setOnClickListener {
            requestScreenCapture()
        }

        stopButton.setOnClickListener {
            stopObservation()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    NOTIFICATION_REQUEST
                )
            }
        }
    }

    private fun requestScreenCapture() {

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val captureIntent =
            projectionManager.createScreenCaptureIntent()

        statusText.text =
            "● WAITING FOR PERMISSION"

        startActivityForResult(
            captureIntent,
            SCREEN_CAPTURE_REQUEST
        )
    }

    @Deprecated("Uses legacy activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            SCREEN_CAPTURE_REQUEST
        ) {
            return
        }

        if (
            resultCode != RESULT_OK ||
            data == null
        ) {

            statusText.text =
                "● CAPTURE PERMISSION DENIED"

            startButton.isEnabled = true
            stopButton.isEnabled = false

            return
        }

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {

                putExtra(
                    ScreenCaptureServiceContract.RESULT_CODE,
                    resultCode
                )

                putExtra(
                    ScreenCaptureServiceContract.DATA_INTENT,
                    data
                )
            }

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                startForegroundService(
                    serviceIntent
                )

            } else {

                startService(
                    serviceIntent
                )
            }

            statusText.text =
                "● STARTING OBSERVATION"

            startButton.isEnabled = false
            stopButton.isEnabled = true

        } catch (error: Exception) {

            statusText.text =
                "● START FAILED: ${error.javaClass.simpleName}"

            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun stopObservation() {

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            )

        stopService(
            serviceIntent
        )

        statusText.text =
            "● OBSERVATION STOPPED"

        startButton.isEnabled = true
        stopButton.isEnabled = false

        updateMetrics()
    }

    private fun updateMetrics() {

        val capturing =
            ScreenCaptureService.isCapturing

        if (capturing) {

            statusText.text =
                "● OBSERVATION ACTIVE"

            startButton.isEnabled = false
            stopButton.isEnabled = true

        } else {

            /*
             * Don't overwrite a temporary starting/permission
             * message too aggressively.
             */
            if (
                !statusText.text
                    .toString()
                    .contains("PERMISSION") &&
                !statusText.text
                    .toString()
                    .contains("FAILED")
            ) {

                statusText.text =
                    "● READY"
            }

            startButton.isEnabled = true
            stopButton.isEnabled = false
        }

        val width =
            ScreenCaptureService.capturedWidth

        val height =
            ScreenCaptureService.capturedHeight

        resolutionText.text =
            if (width > 0 && height > 0) {

                "Resolution: ${width} × ${height}"

            } else {

                "Resolution: --"
            }

        val fps =
            ScreenCaptureService.measuredFps

        fpsText.text =
            if (fps > 0.0) {

                "Capture FPS: %.2f".format(fps)

            } else {

                "Capture FPS: --"
            }

        val latency =
            ScreenCaptureService.frameLatencyMs

        latencyText.text =
            if (latency > 0.0) {

                "Frame latency: %.2f ms"
                    .format(latency)

            } else {

                "Frame latency: --"
            }

        frameText.text =
            "Captured frames: " +
                    ScreenCaptureService.totalFrames

        analyzedFrameText.text =
            "Analyzed frames: " +
                    ScreenCaptureService.analyzedFrames

        val analyzed =
            ScreenCaptureService.analyzedFrames > 0

        visionText.text =
            if (analyzed) {

                "Vision: ACTIVE"

            } else if (capturing) {

                "Vision: STARTING"

            } else {

                "Vision: INACTIVE"
            }

        val state =
            ScreenCaptureService.latestGameState

        gameStateText.text =
            if (state != null) {

                "Game state: ${state.phase} " +
                        "(confidence %.2f)"
                    .format(
                        state.stateConfidence
                    )

            } else {

                "Game state: UNKNOWN"
            }

        val analysisTime =
            ScreenCaptureService.lastAnalysisTimeMs

        analysisTimeText.text =
            if (analysisTime > 0.0) {

                "Vision processing: %.2f ms"
                    .format(analysisTime)

            } else {

                "Vision processing: --"
            }
    }

    override fun onDestroy() {

        metricsHandler.removeCallbacks(
            metricsRunnable
        )

        super.onDestroy()
    }
}
