package com.example.model

import com.example.i18n.AppLanguage

enum class VideoResolution(val label: String, val shortEdge: Int) {
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080)
}

enum class VideoAspectRatio(val label: String, val wRatio: Int, val hRatio: Int) {
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_16_9("16:9", 16, 9)
}

enum class BackgroundFillMode {
    BLURRED_PHOTO,
    SOLID_COLOR
}

data class ExportSettings(
    val resolution: VideoResolution = VideoResolution.P720,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_9_16,
    val fps: Int = 30,
    val fillMode: BackgroundFillMode = BackgroundFillMode.BLURRED_PHOTO,
    val solidColorArgb: Int = 0xFF0A0D14.toInt()
) {
    fun outputDimensions(): Pair<Int, Int> {
        val base = resolution.shortEdge
        val (rawW, rawH) = when (aspectRatio) {
            VideoAspectRatio.RATIO_9_16 -> base to (base * 16 / 9)
            VideoAspectRatio.RATIO_1_1 -> base to base
            VideoAspectRatio.RATIO_16_9 -> (base * 16 / 9) to base
        }
        // MediaCodec H.264 requires dimensions divisible by 16 (or at least 2; 16 is safest across Android encoders)
        val w = (rawW / 16) * 16
        val h = (rawH / 16) * 16
        return w.coerceAtLeast(256) to h.coerceAtLeast(256)
    }
}

data class StudioSettings(
    val language: AppLanguage = AppLanguage.EN,
    val mouthSensitivity: Float = 1.25f,     // 0.5 .. 2.5
    val silenceThreshold: Float = 0.08f,     // 0.0 .. 0.40 (added onto noise floor)
    val smoothingAmount: Float = 0.45f,      // 0.0 .. 1.0
    val blinkRateSeconds: Float = 3.4f,      // 2.0 .. 5.0 avg interval
    val headMotionStrength: Float = 0.70f,   // 0.0 .. 1.5
    val bodyBreathingStrength: Float = 0.65f,// 0.0 .. 1.5
    val emphasisZoomEnabled: Boolean = true,
    val defaultResolution: VideoResolution = VideoResolution.P720,
    val defaultAspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_9_16,
    val defaultFillMode: BackgroundFillMode = BackgroundFillMode.BLURRED_PHOTO,
    val defaultFps: Int = 30
)
