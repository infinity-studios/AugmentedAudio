package com.dawood.augmentedaudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.puredata.android.io.PdAudio
import org.puredata.core.PdBase
import org.puredata.core.utils.IoUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private val tag = "AugmentedAudio"
    private val micRequest = 101

    private var patchHandle = 0
    private var started = false
    private lateinit var status: TextView
    private lateinit var micButton: Button
    private lateinit var musicButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        micButton = findViewById(R.id.micButton)
        musicButton = findViewById(R.id.musicButton)

        micButton.setOnClickListener { selectSource(0) }
        musicButton.setOnClickListener { selectSource(1) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micRequest
            )
        } else {
            startEngine()
        }
    }

    private fun startEngine() {
        try {
            // 44.1kHz, 1 mic in, 2 out, small buffer for near-live latency
            PdAudio.initAudio(44100, 1, 2, 4, true)

            // Extract the bundled patch + rjlib + track to the app's files dir
            val patchDir = File(filesDir, "patch")
            IoUtils.extractZipResource(
                resources.openRawResource(R.raw.patch), patchDir, true
            )
            val patchFile = File(patchDir, "stage3b_4_reactor.pd")
            patchHandle = PdBase.openPatch(patchFile)

            PdAudio.startAudio(this)
            started = true
            selectSource(1)   // default to music mode (song + live room woven in)
        } catch (e: Exception) {
            Log.e(tag, "start failed", e)
            status.text = "Error: ${e.message}"
        }
    }

    // 0 = microphone (ambient), 1 = local music file. Patch listens on [r source].
    private fun selectSource(mode: Int) {
        if (started) PdBase.sendFloat("source", mode.toFloat())
        if (mode == 0) {
            status.text = getString(R.string.mode_mic)
            micButton.isEnabled = false
            musicButton.isEnabled = true
        } else {
            status.text = getString(R.string.mode_music)
            micButton.isEnabled = true
            musicButton.isEnabled = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micRequest &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startEngine()
        } else {
            status.text = getString(R.string.no_mic)
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) PdAudio.startAudio(this)
    }

    override fun onPause() {
        super.onPause()
        if (started) PdAudio.stopAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (patchHandle != 0) PdBase.closePatch(patchHandle)
        PdAudio.release()
        PdBase.release()
    }
}
