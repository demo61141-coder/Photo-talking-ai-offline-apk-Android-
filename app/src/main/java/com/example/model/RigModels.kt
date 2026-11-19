package com.example.model

import kotlinx.serialization.Serializable

enum class SubjectType {
    HUMAN,
    CARTOON,
    ANIMAL,
    BIRD
}

enum class MouthType {
    JAW,
    CARTOON_LIPS,
    BEAK
}

enum class MouthInteriorStyle {
    REALISTIC,
    CARTOON,
    MINIMAL_DARK
}

@Serializable
data class NormPoint(
    val x: Float,
    val y: Float
) {
    fun clamped(): NormPoint = NormPoint(x.coerceIn(0.02f, 0.98f), y.coerceIn(0.02f, 0.98f))
}

@Serializable
data class NormSize(
    val width: Float,
    val height: Float
)

@Serializable
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun normalized(): NormRect {
        val l = minOf(left, right).coerceIn(0.01f, 0.94f)
        val r = maxOf(left, right).coerceIn(l + 0.05f, 0.99f)
        val t = minOf(top, bottom).coerceIn(0.01f, 0.94f)
        val b = maxOf(top, bottom).coerceIn(t + 0.05f, 0.99f)
        return NormRect(l, t, r, b)
    }
}

@Serializable
data class FaceRig(
    val subjectType: SubjectType = SubjectType.HUMAN,
    val mouthType: MouthType = MouthType.JAW,
    val mouthInteriorStyle: MouthInteriorStyle = MouthInteriorStyle.REALISTIC,
    val mouthLeft: NormPoint = NormPoint(0.41f, 0.56f),
    val mouthRight: NormPoint = NormPoint(0.59f, 0.56f),
    val mouthBottom: NormPoint = NormPoint(0.50f, 0.62f),
    val upperLipCenter: NormPoint? = null,
    val chinBottom: NormPoint? = null,
    val eyeLeft: NormPoint = NormPoint(0.40f, 0.39f),
    val eyeRight: NormPoint = NormPoint(0.60f, 0.39f),
    val leftEyeSize: NormSize? = null,
    val rightEyeSize: NormSize? = null,
    val leftEyebrowPeak: NormPoint? = null,
    val rightEyebrowPeak: NormPoint? = null,
    val useSingleEye: Boolean = false,
    val headBox: NormRect = NormRect(0.24f, 0.14f, 0.76f, 0.70f),
    val neckPivot: NormPoint = NormPoint(0.50f, 0.73f),
    val eyelidColorArgb: Int? = null,
    val beakInnerColorArgb: Int? = null,
    val autoDetected: Boolean = false
)
