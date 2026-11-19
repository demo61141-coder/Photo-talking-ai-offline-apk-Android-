package com.example.engine

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.model.AudioAnalysisTrack
import com.example.model.AudioFrameSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

object AudioDecoderAndAnalyzer {

    private data class DecodedPcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long
    )

    suspend fun analyzeFile(
        filePath: String,
        displayName: String,
        fps: Int = 30
    ): Result<AudioAnalysisTrack> = withContext(Dispatchers.Default) {
        runCatching {
            val file = File(filePath)
            require(file.exists() && file.length() > 0L) { "Audio file missing" }

            val decoded = if (file.extension.equals("wav", ignoreCase = true)) {
                decodeWavFast(file) ?: decodeWithMediaCodec(file)
            } else {
                decodeWithMediaCodec(file)
            }

            val sr = decoded.sampleRate.coerceAtLeast(8000)
            val pcm = decoded.samples
            val durationMs = decoded.durationMs.coerceAtLeast(
                ((pcm.size.toDouble() / sr) * 1000.0).toLong()
            )
            val totalFrames = max(1, ((durationMs / 1000.0) * fps).toInt())
            val samplesPerFrame = max(1, sr / fps)
            val windowSize = (samplesPerFrame * 2).coerceIn(256, 2048)

            // Continuous time-domain 2nd-order Butterworth Biquad filter bank:
            // - Low-pass (< 480 Hz): jaw drop & rounded vowel articulation
            // - Band-pass (500-1600 Hz): open vowel formants
            // - High-pass (> 1650 Hz): consonant bursts, sibilants, lip-corner spread & upper-lip lift
            val lowPassFilter = BiquadFilter.lowPass(sr, cutoffHz = 480f, q = 0.707f)
            val bandPassFilter = BiquadFilter.bandPass(sr, centerHz = 980f, q = 0.9f)
            val highPassFilter = BiquadFilter.highPass(sr, cutoffHz = 1650f, q = 0.707f)

            val lpSamples = FloatArray(pcm.size)
            val bpSamples = FloatArray(pcm.size)
            val hpSamples = FloatArray(pcm.size)
            for (i in pcm.indices) {
                val x = pcm[i]
                lpSamples[i] = lowPassFilter.process(x)
                bpSamples[i] = bandPassFilter.process(x)
                hpSamples[i] = highPassFilter.process(x)
            }

            val rawFrames = ArrayList<AudioFrameSample>(totalFrames)
            val rmsValues = FloatArray(totalFrames)
            val lpValues = FloatArray(totalFrames)
            val hpValues = FloatArray(totalFrames)

            var prevRms = 0f
            for (frameIdx in 0 until totalFrames) {
                val centerSample = ((frameIdx.toLong() * sr) / fps).toInt()
                val start = (centerSample - windowSize / 2).coerceAtLeast(0)
                val end = (start + windowSize).coerceAtMost(pcm.size)
                val count = (end - start).coerceAtLeast(1)

                var sumSq = 0.0
                var sumLp = 0.0
                var sumBp = 0.0
                var sumHp = 0.0
                for (i in start until end) {
                    val v = pcm[i].toDouble()
                    val lp = lpSamples[i].toDouble()
                    val bp = bpSamples[i].toDouble()
                    val hp = hpSamples[i].toDouble()
                    sumSq += v * v
                    sumLp += lp * lp
                    sumBp += bp * bp
                    sumHp += hp * hp
                }
                val rms = sqrt(sumSq / count).toFloat()
                val lpRms = sqrt(sumLp / count).toFloat()
                val bpRms = sqrt(sumBp / count).toFloat()
                val hpRms = sqrt(sumHp / count).toFloat()

                rmsValues[frameIdx] = rms
                lpValues[frameIdx] = lpRms
                hpValues[frameIdx] = hpRms

                val bandTotal = (lpRms + bpRms + hpRms).coerceAtLeast(1e-6f)
                val normLow = (lpRms / bandTotal).coerceIn(0f, 1f)
                val normMid = (bpRms / bandTotal).coerceIn(0f, 1f)
                val normHigh = (hpRms / bandTotal).coerceIn(0f, 1f)

                val onset = ((rms - prevRms) * 3.5f).coerceIn(0f, 1f)
                prevRms = prevRms * 0.7f + rms * 0.3f

                rawFrames.add(
                    AudioFrameSample(
                        timeMs = (frameIdx * 1000L) / fps,
                        rms = rms,
                        lowBand = normLow,
                        midBand = normMid,
                        highBand = normHigh,
                        emphasis = onset,
                        lowPassAmp = lpRms,
                        highPassAmp = hpRms
                    )
                )
            }

            // Normalize RMS by 90th percentile so conversational speech animates clearly,
            // and auto-calibrate noise floor from the 15th percentile.
            val sortedRms = rmsValues.sorted()
            val p15 = sortedRms[(sortedRms.size * 0.15f).toInt().coerceIn(0, sortedRms.lastIndex)]
            val p90 = sortedRms[(sortedRms.size * 0.90f).toInt().coerceIn(0, sortedRms.lastIndex)]
                .coerceAtLeast(0.015f)
            val lpP90 = lpValues.sorted()[(lpValues.size * 0.90f).toInt().coerceIn(0, lpValues.lastIndex)]
                .coerceAtLeast(p90 * 0.25f)
            val hpP90 = hpValues.sorted()[(hpValues.size * 0.90f).toInt().coerceIn(0, hpValues.lastIndex)]
                .coerceAtLeast(p90 * 0.20f)

            val normalizedFrames = rawFrames.map { f ->
                val normRms = (f.rms / p90).coerceIn(0f, 1.35f)
                val normEmphasis = (f.emphasis / p90).coerceIn(0f, 1f)
                val normLpAmp = (f.lowPassAmp / lpP90).coerceIn(0f, 1.35f)
                val normHpAmp = (f.highPassAmp / hpP90).coerceIn(0f, 1.35f)
                f.copy(
                    rms = normRms,
                    emphasis = normEmphasis,
                    lowPassAmp = normLpAmp,
                    highPassAmp = normHpAmp
                )
            }
            val autoNoiseFloor = (p15 / p90).coerceIn(0.01f, 0.35f)

            // 72-bar waveform preview
            val barCount = 72
            val waveform = List(barCount) { bar ->
                val s = (bar * normalizedFrames.size) / barCount
                val e = (((bar + 1) * normalizedFrames.size) / barCount).coerceAtLeast(s + 1)
                    .coerceAtMost(normalizedFrames.size)
                var peak = 0f
                for (k in s until e) {
                    if (normalizedFrames[k].rms > peak) peak = normalizedFrames[k].rms
                }
                peak.coerceIn(0.03f, 1f)
            }

            AudioAnalysisTrack(
                filePath = filePath,
                displayName = displayName,
                durationMs = durationMs,
                fps = fps,
                autoNoiseFloor = autoNoiseFloor,
                frames = normalizedFrames,
                waveformPreview = waveform
            )
        }
    }

    /**
     * 2nd-order Direct Form II Transposed Biquad IIR filter for real-time & offline band splitting.
     */
    class BiquadFilter(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(input: Float): Float {
            val x = input.toDouble()
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y.toFloat()
        }

        companion object {
            fun lowPass(sampleRate: Int, cutoffHz: Float, q: Float = 0.707f): BiquadFilter {
                val w0 = 2.0 * PI * cutoffHz.coerceIn(20f, sampleRate * 0.45f) / sampleRate
                val alpha = kotlin.math.sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val a0 = 1.0 + alpha
                val b0 = ((1.0 - cosW0) * 0.5) / a0
                val b1 = (1.0 - cosW0) / a0
                val b2 = b0
                val a1 = (-2.0 * cosW0) / a0
                val a2 = (1.0 - alpha) / a0
                return BiquadFilter(b0, b1, b2, a1, a2)
            }

            fun highPass(sampleRate: Int, cutoffHz: Float, q: Float = 0.707f): BiquadFilter {
                val w0 = 2.0 * PI * cutoffHz.coerceIn(20f, sampleRate * 0.45f) / sampleRate
                val alpha = kotlin.math.sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val a0 = 1.0 + alpha
                val b0 = ((1.0 + cosW0) * 0.5) / a0
                val b1 = -(1.0 + cosW0) / a0
                val b2 = b0
                val a1 = (-2.0 * cosW0) / a0
                val a2 = (1.0 - alpha) / a0
                return BiquadFilter(b0, b1, b2, a1, a2)
            }

            fun bandPass(sampleRate: Int, centerHz: Float, q: Float = 0.9f): BiquadFilter {
                val w0 = 2.0 * PI * centerHz.coerceIn(20f, sampleRate * 0.45f) / sampleRate
                val alpha = kotlin.math.sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val a0 = 1.0 + alpha
                val b0 = alpha / a0
                val b1 = 0.0
                val b2 = -alpha / a0
                val a1 = (-2.0 * cosW0) / a0
                val a2 = (1.0 - alpha) / a0
                return BiquadFilter(b0, b1, b2, a1, a2)
            }
        }
    }

    private fun decodeWavFast(file: File): DecodedPcm? {
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            var channels = 1
            var sampleRate = 16000
            var bitsPerSample = 16
            var audioFormat = 1
            var dataOffset = -1
            var dataSize = 0

            while (pos + 8 <= bytes.size) {
                val chunkId = String(bytes, pos, 4)
                val chunkSize = buf.getInt(pos + 4).coerceAtLeast(0)
                val payloadStart = pos + 8
                if (chunkId == "fmt " && payloadStart + 16 <= bytes.size) {
                    audioFormat = buf.getShort(payloadStart).toInt() and 0xFFFF
                    channels = buf.getShort(payloadStart + 2).toInt().coerceIn(1, 2)
                    sampleRate = buf.getInt(payloadStart + 4).coerceAtLeast(8000)
                    bitsPerSample = buf.getShort(payloadStart + 14).toInt() and 0xFFFF
                } else if (chunkId == "data") {
                    dataOffset = payloadStart
                    dataSize = chunkSize.coerceAtMost(bytes.size - payloadStart)
                    break
                }
                val paddedSize = chunkSize + (chunkSize and 1)
                pos = payloadStart + paddedSize
            }

            if (dataOffset < 0 || dataSize <= 0 || audioFormat != 1 || bitsPerSample != 16) return null
            val sampleCount = dataSize / (2 * channels)
            val floats = FloatArray(sampleCount)
            val dataBuf = ByteBuffer.wrap(bytes, dataOffset, sampleCount * 2 * channels)
                .order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += dataBuf.short / 32768f
                }
                floats[i] = sum / channels
            }
            val durationMs = ((sampleCount.toDouble() / sampleRate) * 1000.0).toLong()
            DecodedPcm(floats, sampleRate, durationMs)
        }.getOrNull()
    }

    private fun decodeWithMediaCodec(file: File): DecodedPcm {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track found" }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outChunks = ArrayList<FloatArray>()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val chunk = extractMonoFloats(outBuf, info.size, channels, codec.outputFormat)
                        if (chunk.isNotEmpty()) outChunks.add(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFmt = codec.outputFormat
                    if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val totalSamples = outChunks.sumOf { it.size }
        val merged = FloatArray(totalSamples)
        var offset = 0
        for (c in outChunks) {
            c.copyInto(merged, offset)
            offset += c.size
        }
        val durationMs = if (durationUs > 0) durationUs / 1000L
        else ((totalSamples.toDouble() / sampleRate) * 1000.0).toLong()

        return DecodedPcm(merged, sampleRate, durationMs)
    }

    private fun extractMonoFloats(
        buf: ByteBuffer,
        sizeBytes: Int,
        channels: Int,
        format: MediaFormat
    ): FloatArray {
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val ch = channels.coerceAtLeast(1)
        val ordered = buf.slice().order(ByteOrder.LITTLE_ENDIAN)

        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val frames = sizeBytes / (4 * ch)
            FloatArray(frames) {
                var sum = 0f
                for (c in 0 until ch) sum += ordered.float
                sum / ch
            }
        } else {
            val frames = sizeBytes / (2 * ch)
            FloatArray(frames) {
                var sum = 0f
                for (c in 0 until ch) sum += ordered.short / 32768f
                sum / ch
            }
        }
    }
}
