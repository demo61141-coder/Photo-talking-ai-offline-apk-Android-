package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.model.AudioAnalysisTrack
import com.example.model.ExportSettings
import com.example.model.FaceRig
import com.example.model.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max

object Mp4VideoExporter {

    private data class PcmData(
        val shorts: ShortArray,
        val sampleRate: Int,
        val channels: Int
    )

    private val supportedAacRates = intArrayOf(16000, 22050, 24000, 44100, 48000)

    suspend fun exportMp4(
        context: Context,
        projectId: String,
        sourceBitmap: Bitmap,
        rig: FaceRig,
        audioTrack: AudioAnalysisTrack,
        exportSettings: ExportSettings,
        studioSettings: StudioSettings,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val outFile = File(exportsDir, "talking_${projectId}_${System.currentTimeMillis()}.mp4")
            if (outFile.exists()) outFile.delete()

            val (outW, outH) = exportSettings.outputDimensions()
            val fps = exportSettings.fps.coerceIn(24, 60)

            // Decode audio into PCM shorts so any input (mp3/wav/m4a/ogg) encodes cleanly to AAC in MP4
            val rawPcm = decodeToPcm16(File(audioTrack.filePath))
            val pcm = normalizePcmForAac(rawPcm)
            val pcmDurationMs = if (pcm.shorts.isNotEmpty() && pcm.sampleRate > 0) {
                ((pcm.shorts.size.toDouble() / pcm.channels) * 1000.0 / pcm.sampleRate).toLong()
            } else {
                audioTrack.durationMs
            }
            val totalDurationMs = max(audioTrack.durationMs, pcmDurationMs).coerceAtLeast(500L)
            val totalFrames = max(1, ((totalDurationMs / 1000.0) * fps).toInt())

            // Configure H.264 Video Encoder with Surface input
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    when (exportSettings.resolution.shortEdge) {
                        1080 -> 6_500_000
                        720 -> 3_800_000
                        else -> 2_000_000
                    }
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // Configure AAC Audio Encoder
            val aacSampleRate = pcm.sampleRate
            val aacChannels = pcm.channels.coerceIn(1, 2)
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                aacSampleRate,
                aacChannels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIdx = -1
            var audioTrackIdx = -1
            var muxerStarted = false

            val renderer = TalkingAnimatorRenderer(sourceBitmap)
            val frameBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)
            val eglRenderer = EglSurfaceRenderer(inputSurface, outW, outH)
            val timeline = AnimationTimelineState().apply { reset() }
            val videoInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()

            val pendingVideoBuffers = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
            val pendingAudioBuffers = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()

            fun tryStartMuxerAndFlush() {
                if (!muxerStarted && videoTrackIdx >= 0 && audioTrackIdx >= 0) {
                    muxer.start()
                    muxerStarted = true
                    for ((bytes, info) in pendingAudioBuffers) {
                        muxer.writeSampleData(audioTrackIdx, ByteBuffer.wrap(bytes), info)
                    }
                    pendingAudioBuffers.clear()
                    for ((bytes, info) in pendingVideoBuffers) {
                        muxer.writeSampleData(videoTrackIdx, ByteBuffer.wrap(bytes), info)
                    }
                    pendingVideoBuffers.clear()
                }
            }

            fun drainVideo(endOfStream: Boolean) {
                if (endOfStream) videoEncoder.signalEndOfInputStream()
                var tryAgainCount = 0
                while (true) {
                    val outIdx = videoEncoder.dequeueOutputBuffer(videoInfo, if (endOfStream) 10_000 else 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                            tryAgainCount++
                            if (tryAgainCount > 250) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            tryAgainCount = 0
                            if (videoTrackIdx < 0) {
                                videoTrackIdx = muxer.addTrack(videoEncoder.outputFormat)
                                tryStartMuxerAndFlush()
                            }
                        }
                        outIdx >= 0 -> {
                            tryAgainCount = 0
                            val buf = videoEncoder.getOutputBuffer(outIdx)
                            if (buf != null && (videoInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoInfo.size > 0) {
                                buf.position(videoInfo.offset)
                                buf.limit(videoInfo.offset + videoInfo.size)
                                if (muxerStarted) {
                                    muxer.writeSampleData(videoTrackIdx, buf, videoInfo)
                                } else {
                                    val copy = ByteArray(videoInfo.size)
                                    buf.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, videoInfo.presentationTimeUs, videoInfo.flags)
                                    }
                                    pendingVideoBuffers.add(copy to infoCopy)
                                }
                            }
                            videoEncoder.releaseOutputBuffer(outIdx, false)
                            if ((videoInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            fun drainAudio(): Boolean {
                var reachedEos = false
                while (true) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioInfo, 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIdx < 0) {
                                audioTrackIdx = muxer.addTrack(audioEncoder.outputFormat)
                                tryStartMuxerAndFlush()
                            }
                        }
                        outIdx >= 0 -> {
                            val buf = audioEncoder.getOutputBuffer(outIdx)
                            if (buf != null && (audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioInfo.size > 0) {
                                buf.position(audioInfo.offset)
                                buf.limit(audioInfo.offset + audioInfo.size)
                                if (muxerStarted) {
                                    muxer.writeSampleData(audioTrackIdx, buf, audioInfo)
                                } else {
                                    val copy = ByteArray(audioInfo.size)
                                    buf.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, audioInfo.presentationTimeUs, audioInfo.flags)
                                    }
                                    pendingAudioBuffers.add(copy to infoCopy)
                                }
                            }
                            val eos = (audioInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioEncoder.releaseOutputBuffer(outIdx, false)
                            if (eos) {
                                reachedEos = true
                                break
                            }
                        }
                    }
                }
                return reachedEos
            }

            try {
                // Step A: Encode AAC audio stream
                val totalShorts = pcm.shorts.size
                val shortsPerChunk = 1024 * aacChannels
                var shortCursor = 0
                var audioInputDone = false
                var audioOutputDone = false

                while (!audioOutputDone) {
                    if (!coroutineContext.isActive) error("Export cancelled")
                    if (!audioInputDone) {
                        val inIdx = audioEncoder.dequeueInputBuffer(5_000)
                        if (inIdx >= 0) {
                            val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                            inBuf.clear()
                            if (shortCursor >= totalShorts) {
                                audioEncoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    (totalShorts.toLong() / aacChannels) * 1_000_000L / aacSampleRate,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                audioInputDone = true
                            } else {
                                val maxShorts = inBuf.remaining() / 2
                                val count = minOf(shortsPerChunk, totalShorts - shortCursor, maxShorts)
                                val ptsUs = (shortCursor.toLong() / aacChannels) * 1_000_000L / aacSampleRate
                                val ordered = inBuf.order(ByteOrder.LITTLE_ENDIAN)
                                for (i in 0 until count) {
                                    ordered.putShort(pcm.shorts[shortCursor + i])
                                }
                                audioEncoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                                shortCursor += count
                            }
                        }
                    }
                    audioOutputDone = drainAudio()
                }

                // Step B: Render & encode Video frames via EGL14 with exact monotonic presentationTimeNs
                val dt = 1f / fps
                for (frameIdx in 0 until totalFrames) {
                    if (!coroutineContext.isActive) error("Export cancelled")
                    val timeMs = (frameIdx * 1000L) / fps
                    val ptsNs = (frameIdx.toLong() * 1_000_000_000L) / fps
                    val sample = audioTrack.sampleAt(timeMs)
                    val pose = timeline.step(
                        timeMs = timeMs,
                        dtSeconds = dt,
                        sample = sample,
                        autoNoiseFloor = audioTrack.autoNoiseFloor,
                        settings = studioSettings
                    )

                    frameCanvas.drawColor(Color.BLACK)
                    renderer.renderFrame(
                        canvas = frameCanvas,
                        outW = outW,
                        outH = outH,
                        rig = rig,
                        pose = pose,
                        fillMode = exportSettings.fillMode,
                        solidFillArgb = exportSettings.solidColorArgb
                    )
                    eglRenderer.drawBitmapAndSwap(frameBitmap, ptsNs)

                    drainVideo(endOfStream = false)
                    onProgress(((frameIdx + 1).toFloat() / totalFrames).coerceIn(0.02f, 0.99f))
                }
                drainVideo(endOfStream = true)
                onProgress(1f)
            } finally {
                renderer.release()
                eglRenderer.release()
                if (!frameBitmap.isRecycled) frameBitmap.recycle()
                runCatching { inputSurface.release() }
                runCatching { videoEncoder.stop() }
                runCatching { videoEncoder.release() }
                runCatching { audioEncoder.stop() }
                runCatching { audioEncoder.release() }
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }

            outFile
        }
    }

    private fun normalizePcmForAac(pcm: PcmData): PcmData {
        val ch = pcm.channels.coerceIn(1, 2)
        val inRate = pcm.sampleRate.coerceAtLeast(8000)
        if (inRate in supportedAacRates && ch == pcm.channels) return pcm

        val targetRate = if (inRate in supportedAacRates) inRate
        else if (inRate < 16000) 16000
        else 44100

        if (targetRate == inRate && ch == pcm.channels) return pcm
        val inFrames = pcm.shorts.size / pcm.channels.coerceAtLeast(1)
        if (inFrames <= 1) return PcmData(pcm.shorts, targetRate, ch)

        val outFrames = max(1, ((inFrames.toLong() * targetRate) / inRate).toInt())
        val outShorts = ShortArray(outFrames * ch)
        val ratio = (inFrames - 1).toDouble() / max(1, outFrames - 1)
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, inFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, inFrames - 1)
            val frac = (srcPos - idx0).toFloat()
            for (c in 0 until ch) {
                val srcC = c.coerceAtMost(pcm.channels - 1)
                val s0 = pcm.shorts[idx0 * pcm.channels + srcC].toFloat()
                val s1 = pcm.shorts[idx1 * pcm.channels + srcC].toFloat()
                val interp = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
                outShorts[i * ch + c] = interp.toShort()
            }
        }
        return PcmData(outShorts, targetRate, ch)
    }

    private fun decodeToPcm16(file: File): PcmData {
        if (file.extension.equals("wav", ignoreCase = true)) {
            val wav = readWavShorts(file)
            if (wav != null) return wav
        }
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track" }
        extractor.selectTrack(trackIndex)

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
        } else 1
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val chunks = ArrayList<ShortArray>()
        val info = MediaCodec.BufferInfo()
        var inEos = false
        var outEos = false
        try {
            while (!outEos) {
                if (!inEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(inBuf, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
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
                        val enc = if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT

                        val slice = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN)
                        val arr = if (enc == AudioFormat.ENCODING_PCM_FLOAT) {
                            ShortArray(info.size / 4) { (slice.float * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
                        } else {
                            ShortArray(info.size / 2) { slice.short }
                        }
                        if (arr.isNotEmpty()) chunks.add(arr)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val fmt = codec.outputFormat
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }
        val total = chunks.sumOf { it.size }
        val merged = ShortArray(total)
        var off = 0
        for (c in chunks) {
            c.copyInto(merged, off)
            off += c.size
        }
        return PcmData(merged, sampleRate, channels)
    }

    private fun readWavShorts(file: File): PcmData? = runCatching {
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
        val shortCount = dataSize / 2
        val dataBuf = ByteBuffer.wrap(bytes, dataOffset, shortCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(shortCount) { dataBuf.short }
        PcmData(shorts, sampleRate, channels)
    }.getOrNull()
}
