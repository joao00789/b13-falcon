package com.b13.falcon

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class CaptureConsentActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 2001
        private const val REQUEST_NOTIFICATIONS = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
            return
        }
        startCameraPreviewIfEnabled()
        requestCaptureConsent()
    }

    private fun startCameraPreviewIfEnabled() {
        val preferences = getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
        val json = intent.getStringExtra("cameraConfigJson")
            ?: CameraOverlayConfig.loadFromPreferences(preferences)
        val config = CameraOverlayConfig.fromJson(json)
        if (!config.enabled || config.mode != "overlay") return

        val serviceIntent = Intent(this, CameraOverlayService::class.java)
            .setAction(CameraOverlayService.ACTION_UPDATE)
            .putExtra("cameraConfigJson", config.toJson())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (error: Exception) {
            Toast.makeText(
                this,
                error.message ?: "Não foi possível iniciar a prévia da câmera.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun requestCaptureConsent() {
        val captureIntent = getSystemService(MediaProjectionManager::class.java)
            .createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) requestCaptureConsent()
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return

        if (resultCode == RESULT_OK && data != null) {
            val preferences = getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
            val cameraConfigJson = intent.getStringExtra("cameraConfigJson")
                ?: CameraOverlayConfig.loadFromPreferences(preferences)
            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingService.EXTRA_DATA, data)
                putExtra(
                    "title",
                    intent.getStringExtra("title") ?: "B13_FALCON_${System.currentTimeMillis()}",
                )
                putExtra(
                    "resolution",
                    intent.getStringExtra("resolution")
                        ?: preferences.getString("resolution", "1080p"),
                )
                putExtra("fps", intent.getIntExtra("fps", preferences.getInt("fps", 30)))
                putExtra(
                    "bitrateKbps",
                    intent.getIntExtra(
                        "bitrateKbps",
                        preferences.getInt("bitrateKbps", 16000),
                    ),
                )
                putExtra(
                    "audioMode",
                    intent.getStringExtra("audioMode")
                        ?: preferences.getString("audioMode", "both"),
                )
                putExtra(
                    "codec",
                    intent.getStringExtra("codec")
                        ?: preferences.getString("codec", "h264"),
                )
                putExtra(
                    "orientation",
                    intent.getStringExtra("orientation")
                        ?: preferences.getString("orientation", "auto"),
                )
                putExtra(
                    "sourceWidth",
                    intent.getIntExtra("sourceWidth", 0),
                )
                putExtra(
                    "sourceHeight",
                    intent.getIntExtra("sourceHeight", 0),
                )
                putExtra("cameraConfigJson", cameraConfigJson)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this,
                    error.message ?: "Não foi possível iniciar a gravação.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        finish()
    }
}
