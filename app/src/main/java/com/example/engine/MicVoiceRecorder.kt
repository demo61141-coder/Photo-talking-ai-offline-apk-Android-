package com.example.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class MicVoiceRecorder {
    private val sampleRate = 44100
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveLevel = MutableStateFlow(0f)
    val liveLevel: StateFlow<Float> = _liveLevel.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, destFile: File, onError: (Throwable) -> Unit) {
        if (_isRecording.value) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (t: Throwable) {
            onError(t)
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onError(IllegalStateException("AudioRecord init failed"))
            return
        }

        outputFile = destFile
        audioRecord = rec
        _isRecording.value = true
        _elapsedMs.value = 0L

        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(minBuf / 2)
            var totalBytesWritten = 0L
            val startTime = System.currentTimeMillis()
            try {
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { fos ->
                    // Placeholder 44-byte WAV header
                    fos.write(ByteArray(44))
                    rec.startRecording()
                    val byteBuf = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    while (isActive && _isRecording.value) {
                        val read = rec.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            byteBuf.clear()
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i]
                                byteBuf.putShort(s)
                                val norm = s / 32768.0
                                sumSq += norm * norm
                            }
                            fos.write(byteBuf.array(), 0, read * 2)
                            totalBytesWritten += read * 2L
                            val rms = sqrt(sumSq / read).toFloat()
                            _liveLevel.value = (rms * 4.5f).coerceIn(0f, 1f)
                            _elapsedMs.value = System.currentTimeMillis() - startTime
                        }
                    }
                }
                writeWavHeader(destFile, totalBytesWritten, sampleRate, 1, 16)
            } catch (t: Throwable) {
                onError(t)
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                audioRecord = null
                _liveLevel.value = 0f
                _isRecording.value = false
            }
        }
    }

    suspend fun stop(): File? {
        _isRecording.value = false
        recordJob?.join()
        recordJob = null
        return withContext(Dispatchers.IO) {
            outputFile?.takeIf { it.exists() && it.length() > 44L }
        }
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate.toLong() * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate.toInt())
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(totalAudioLen.toInt())
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}
