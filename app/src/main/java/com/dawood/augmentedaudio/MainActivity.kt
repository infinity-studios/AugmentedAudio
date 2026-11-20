package com.dawood.augmentedaudio

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var drownBar: SeekBar
    private lateinit var songBar: SeekBar
    private lateinit var roomBar: SeekBar
    private lateinit var importButton: Button
    private var importing = false

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSong(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        micButton = findViewById(R.id.micButton)
        musicButton = findViewById(R.id.musicButton)
        drownBar = findViewById(R.id.drownBar)
        songBar = findViewById(R.id.songBar)
        roomBar = findViewById(R.id.roomBar)
        importButton = findViewById(R.id.importButton)
        importButton.setOnClickListener { if (!importing) pickAudio.launch(arrayOf("audio/*")) }

        musicButton.setOnClickListener { selectSource(1) }
        micButton.setOnClickListener { selectSource(0) }

        bindSlider(drownBar, "drown")
        bindSlider(songBar, "songlvl")
        bindSlider(roomBar, "roomlvl")

        startOrb(findViewById(R.id.orb))

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
            PdAudio.initAudio(44100, 1, 2, 8, true)
            val patchDir = File(filesDir, "patch")
            IoUtils.extractZipResource(
                resources.openRawResource(R.raw.patch), patchDir, true
            )
            patchHandle = PdBase.openPatch(File(patchDir, "stage3b_4_reactor.pd"))
            PdAudio.startAudio(this)
            started = true
            selectSource(1)
            // resume the last imported song, if there is one
            if (File(patchDir, "imported.wav").exists()) {
                PdBase.sendFloat("useimport", 1f)
                PdBase.sendBang("reopen")
                status.text = "Music\n" + getPreferences(MODE_PRIVATE).getString("lastName", "your song")
            }
            // push current slider positions into the engine
            PdBase.sendFloat("drown", drownBar.progress / 100f)
            PdBase.sendFloat("songlvl", songBar.progress / 100f)
            PdBase.sendFloat("roomlvl", roomBar.progress / 100f)
        } catch (e: Exception) {
            Log.e(tag, "start failed", e)
            status.text = "Error: ${e.message}"
        }
    }

    private fun importSong(uri: Uri) {
        importing = true
        val name = displayName(uri)
        status.text = "Importing…\n$name"
        importButton.isEnabled = false
        Thread {
            try {
                val out = File(File(filesDir, "patch"), "imported.wav")
                AudioImporter.decodeToWav(this, uri, out)
                getPreferences(MODE_PRIVATE).edit().putString("lastName", name).apply()
                runOnUiThread {
                    if (started) {
                        PdBase.sendFloat("useimport", 1f)
                        PdBase.sendBang("reopen")
                    }
                    selectSource(1)
                    status.text = "Music\n$name"
                }
            } catch (e: Exception) {
                Log.e(tag, "import failed", e)
                runOnUiThread { status.text = "Couldn't import that file\n${e.message ?: ""}" }
            } finally {
                runOnUiThread { importing = false; importButton.isEnabled = true }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
        }
        return "your song"
    }

    private fun bindSlider(bar: SeekBar, receiver: String) {
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (started) PdBase.sendFloat(receiver, p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    // 1 = music (room flows into the song), 0 = ambient (room only)
    private fun selectSource(mode: Int) {
        if (started) PdBase.sendFloat("source", mode.toFloat())
        val music = mode == 1
        status.text = getString(if (music) R.string.mode_music else R.string.mode_mic)
        musicButton.setBackgroundResource(if (music) R.drawable.pill_selected else R.drawable.pill_idle)
        micButton.setBackgroundResource(if (music) R.drawable.pill_idle else R.drawable.pill_selected)
        musicButton.setTextColor(if (music) Color.parseColor("#0A0F1F") else Color.WHITE)
        micButton.setTextColor(if (music) Color.WHITE else Color.parseColor("#0A0F1F"))
    }

    // slow "breathing" glow so the screen feels alive
    private fun startOrb(orb: View) {
        ObjectAnimator.ofPropertyValuesHolder(
            orb,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f, 1.06f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.65f, 1f)
        ).apply {
            duration = 4200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micRequest && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startEngine() else status.text = getString(R.string.no_mic)
    }

    override fun onResume() { super.onResume(); if (started) PdAudio.startAudio(this) }
    override fun onPause() { super.onPause(); if (started) PdAudio.stopAudio() }

    override fun onDestroy() {
        super.onDestroy()
        if (patchHandle != 0) PdBase.closePatch(patchHandle)
        PdAudio.release()
        PdBase.release()
    }
}
