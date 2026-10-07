package com.universalgameai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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

        /*
         * Install crash logger as early as possible.
         */
        CrashLogger.install(this)

        setContentView(
            R.layout.activity_main
        )

        bindViews()
        setupButtons()

        requestNotificationPermissionIfNeeded()

        /*
         * If the previous run crashed, show the actual
         * exception after the app is reopened.
         */
        showPreviousCrashIfAvailable()

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

        try {

            val projectionManager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            val captureIntent =
                projectionManager
                    .createScreenCaptureIntent()

            statusText.text =
                "● WAITING FOR PERMISSION"

            startActivityForResult(
                captureIntent,
                SCREEN_CAPTURE_REQUEST
            )

        } catch (error: Exception) {

            statusText.text =
                "● CAPTURE REQUEST FAILED"

            showError(
                "Screen capture request failed",
                error
            )
        }
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

        try {

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
                "● START FAILED"

            startButton.isEnabled = true
            stopButton.isEnabled = false

            showError(
                "Could not start capture service",
                error
            )
        }
    }

    private fun stopObservation() {

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            )

        stopService(serviceIntent)

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

            startButton.isEnabled = true
            stopButton.isEnabled = false

            if (
                statusText.text
                    .toString()
                    .contains("STARTING")
            ) {
                /*
                 * Leave the starting message briefly.
                 */
            } else if (
                statusText.text
                    .toString()
                    .contains("FAILED")
            ) {
                /*
                 * Keep failure state visible.
                 */
            } else {
                statusText.text =
                    "● READY"
            }
        }

        val width =
            ScreenCaptureService.capturedWidth

        val height =
            ScreenCaptureService.capturedHeight

        resolutionText.text =
            if (
                width > 0 &&
                height > 0
            ) {
                "Resolution: $width × $height"
            } else {
                "Resolution: --"
            }

        val fps =
            ScreenCaptureService.measuredFps

        fpsText.text =
            if (fps > 0.0) {
                "Capture FPS: %.2f"
                    .format(fps)
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

        visionText.text =
            when {
                ScreenCaptureService.analyzedFrames > 0 ->
                    "Vision: ACTIVE"

                capturing ->
                    "Vision: STARTING"

                else ->
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

    private fun showPreviousCrashIfAvailable() {

        val crash =
            CrashLogger.getLastError(this)
                ?: return

        /*
         * Clear it immediately so the same crash isn't shown
         * forever on every launch.
         */
        CrashLogger.clear(this)

        AlertDialog.Builder(this)
            .setTitle(
                "UniversalGameAI Crash Detected"
            )
            .setMessage(crash)
            .setPositiveButton(
                "OK",
                null
            )
            .setNegativeButton(
                "Copy",
                null
            )
            .show()
    }

    private fun showError(
        title: String,
        error: Throwable
    ) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                error.stackTraceToString()
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    override fun onDestroy() {

        metricsHandler.removeCallbacks(
            metricsRunnable
        )

        super.onDestroy()
    }
}
