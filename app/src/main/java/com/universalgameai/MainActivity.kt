package com.universalgameai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {

    companion object {
        private const val SCREEN_CAPTURE_REQUEST = 1001
        private const val NOTIFICATION_REQUEST = 1002
    }

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST
            )
        }
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(10, 10, 14))
        }

        val title = TextView(this).apply {
            text = "Universal Game AI"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Universal Android Game Agent"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Status: Ready"
            textSize = 18f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 50)
        }

        startButton = Button(this).apply {
            text = "START OBSERVATION"
            setOnClickListener {
                requestScreenCapture()
            }
        }

        stopButton = Button(this).apply {
            text = "STOP OBSERVATION"
            isEnabled = false

            setOnClickListener {
                stopObservation()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            stopButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun requestScreenCapture() {

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        val captureIntent =
            projectionManager.createScreenCaptureIntent()

        startActivityForResult(
            captureIntent,
            SCREEN_CAPTURE_REQUEST
        )

        statusText.text = "Status: Waiting for permission..."
    }

    @Deprecated("Deprecated in Android API")
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

        if (requestCode != SCREEN_CAPTURE_REQUEST) {
            return
        }

        if (resultCode != RESULT_OK || data == null) {

            statusText.text =
                "Status: Screen capture permission denied"

            return
        }

        val serviceIntent =
            Intent(
                this,
                com.universalgameai.capture.ScreenCaptureService::class.java
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        statusText.text =
            "Status: Starting observation..."

        startButton.isEnabled = false
        stopButton.isEnabled = true
    }

    private fun stopObservation() {

        val serviceIntent =
            Intent(
                this,
                com.universalgameai.capture.ScreenCaptureService::class.java
            )

        stopService(serviceIntent)

        statusText.text =
            "Status: Observation stopped"

        startButton.isEnabled = true
        stopButton.isEnabled = false
    }
}
