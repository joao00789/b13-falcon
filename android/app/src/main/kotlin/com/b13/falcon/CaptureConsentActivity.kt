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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val captureIntent = getSystemService(MediaProjectionManager::class.java)
            .createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return

        if (resultCode == RESULT_OK && data != null) {
            val preferences = getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
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
