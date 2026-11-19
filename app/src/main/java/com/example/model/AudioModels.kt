package com.example.model

data class AudioFrameSample(
    val timeMs: Long,
    val rms: Float,          // 0..1+ normalized overall amplitude envelope
    val lowBand: Float,      // normalized low-band ratio 0..1 (round/O energy)
    val midBand: Float,      // normalized mid-band ratio 0..1 (wide-open vowel energy)
    val highBand: Float,     // normalized high-band ratio 0..1 (narrow-wide E/I/sibilant energy)
    val emphasis: Float,     // transient loudness spike 0..1
    val lowPassAmp: Float = rms * lowBand * 1.4f,   // Biquad low-pass (< 480 Hz) amplitude -> jaw drop / lip pucker
    val highPassAmp: Float = rms * highBand * 1.4f  // Biquad high-pass (> 1650 Hz) amplitude -> lip corner spread / upper lip lift
)

data class AudioAnalysisTrack(
    val filePath: String,
    val displayName: String,
    val durationMs: Long,
    val fps: Int,
    val autoNoiseFloor: Float,
    val frames: List<AudioFrameSample>,
    val waveformPreview: List<Float>
) {
    fun sampleAt(timeMs: Long): AudioFrameSample {
        if (frames.isEmpty()) return AudioFrameSample(timeMs, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val idx = ((timeMs.toFloat() / 1000f) * fps).toInt().coerceIn(0, frames.lastIndex)
        return frames[idx]
    }
}
