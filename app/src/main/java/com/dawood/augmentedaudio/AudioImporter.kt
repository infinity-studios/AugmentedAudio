package com.dawood.augmentedaudio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes any audio file Android can read (MP3, M4A, OGG, WAV...) into a
 * 44.1 kHz / 16-bit / stereo WAV that Pure Data's readsf~ can stream.
 */
object AudioImporter {

    private const val OUT_RATE = 44100

    fun decodeToWav(ctx: Context, uri: Uri, outFile: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; format = f; break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track found" }
        extractor.selectTrack(trackIndex)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var srcCh = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false

        val tmp = File(outFile.parentFile, outFile.name + ".tmp")
        val out = BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)
        out.write(ByteArray(44))               // header placeholder
        var dataBytes = 0L

        // streaming linear resampler state
        var pos = 0.0
        var prevL = 0f; var prevR = 0f
        val outBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        fun writeFrame(l: Float, r: Float) {
            outBuf.clear()
            outBuf.putShort(l.toInt().coerceIn(-32768, 32767).toShort())
            outBuf.putShort(r.toInt().coerceIn(-32768, 32767).toShort())
            out.write(outBuf.array(), 0, 4)
            dataBytes += 4
        }

        fun processChunk(buf: ByteBuffer, size: Int) {
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerSample = if (isFloat) 4 else 2
            val frames = size / (bytesPerSample * srcCh)
            if (frames <= 0) return
            val l = FloatArray(frames); val r = FloatArray(frames)
            for (f in 0 until frames) {
                val base = f * srcCh * bytesPerSample
                val a = if (isFloat) buf.getFloat(base) * 32767f else buf.getShort(base).toFloat()
                val b = if (srcCh >= 2) {
                    val o = base + bytesPerSample
                    if (isFloat) buf.getFloat(o) * 32767f else buf.getShort(o).toFloat()
                } else a
                l[f] = a; r[f] = b
            }
            val step = srcRate.toDouble() / OUT_RATE
            // index -1 refers to the last frame of the previous chunk
            while (pos < frames - 1) {
                val i = Math.floor(pos).toInt()
                val frac = (pos - i).toFloat()
                val al = if (i < 0) prevL else l[i]; val ar = if (i < 0) prevR else r[i]
                val bl = l[i + 1]; val br = r[i + 1]
                writeFrame(al + (bl - al) * frac, ar + (br - ar) * frac)
                pos += step
            }
            pos -= frames
            prevL = l[frames - 1]; prevR = r[frames - 1]
        }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(inBuf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIdx >= 0 -> {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)!!
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        processChunk(ob.slice(), info.size)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop(); codec.release(); extractor.release()
        out.flush(); out.close()

        // fill in the WAV header now that we know the size
        RandomAccessFile(tmp, "rw").use { raf ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt((36 + dataBytes).toInt())
            h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
            h.putInt(16); h.putShort(1); h.putShort(2)
            h.putInt(OUT_RATE); h.putInt(OUT_RATE * 4)
            h.putShort(4); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(dataBytes.toInt())
            raf.seek(0); raf.write(h.array())
        }
        // atomic swap so the engine never reads a half-written file
        if (!tmp.renameTo(outFile)) {
            outFile.delete(); tmp.renameTo(outFile)
        }
    }
}
