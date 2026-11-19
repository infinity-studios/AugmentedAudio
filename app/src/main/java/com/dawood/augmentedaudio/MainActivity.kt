package com.dawood.augmentedaudio

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.puredata.android.io.PdAudio
import org.puredata.core.PdBase
import org.puredata.core.utils.IoUtils
import java.io.File

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val tag = "AugmentedAudio"
    private val micRequest = 101

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var patchHandle = 0
    private var started = false
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
            // 44.1kHz, 1 mic in, 2 out, small buffer for low latency
            PdAudio.initAudio(44100, 1, 2, 8, true)

            // Extract the bundled patch + rjlib + track to the app's files dir
            val patchDir = File(filesDir, "patch")
            IoUtils.extractZipResource(
                resources.openRawResource(R.raw.patch), patchDir, true
            )
            val patchFile = File(patchDir, "stage3b_4_reactor.pd")
            patchHandle = PdBase.openPatch(patchFile)

            PdAudio.startAudio(this)
            started = true
            status.text = getString(R.string.running)
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (e: Exception) {
            Log.e(tag, "start failed", e)
            status.text = "Error: ${e.message}"
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

    // --- Accelerometer -> Pd. Patch listens on [r accelY] (and accelX/accelZ) ---
    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return
        PdBase.sendFloat("accelX", event.values[0])
        PdBase.sendFloat("accelY", event.values[1])
        PdBase.sendFloat("accelZ", event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        if (started) {
            PdAudio.startAudio(this)
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (started) {
            PdAudio.stopAudio()
            sensorManager.unregisterListener(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (patchHandle != 0) PdBase.closePatch(patchHandle)
        PdAudio.release()
        PdBase.release()
    }
}
