package com.example.engine

import com.example.model.AttackReleaseEnvelope
import com.example.model.AudioFrameSample
import com.example.model.OneEuroFilter
import com.example.model.StudioSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class AnimatorPose(
    val mouthOpen: Float,        // 0..1.25 overall mouth openness
    val wideOpenWeight: Float,   // 0..1 (band-pass vowel energy)
    val roundWeight: Float,      // 0..1 (low-pass O/U energy)
    val narrowWideWeight: Float, // 0..1 (high-pass E/I/sibilant energy)
    val blinkAmount: Float,      // 0..1 max of left/right blink
    val headAngleDeg: Float,     // rotation around neck pivot
    val headBobY: Float,         // normalized vertical bob
    val headSwayX: Float,        // normalized horizontal sway
    val breathScale: Float,      // subtle body breathing
    val eyeWiden: Float,         // 0..1 loud syllable eyebrow/eye widen
    val emphasisZoom: Float,     // 0..1 subtle camera zoom on emphasis
    val blinkLeft: Float = blinkAmount,    // 0..1 left eye closure
    val blinkRight: Float = blinkAmount,   // 0..1 right eye closure
    val jawDrop: Float = mouthOpen,        // 0..1.35 low-pass + vowel jaw drop
    val lipCornerSpread: Float = 0f,       // -0.45..1.0 high-pass widen vs low-pass pucker
    val upperLipLift: Float = 0f,          // 0..1.0 high-pass + mid upper lip articulation
    val eyebrowLift: Float = eyeWiden      // 0..1.0 emphasis + high-pass eyebrow raise
)

class AnimationTimelineState {
    private val mouthEnv = AttackReleaseEnvelope(attackSec = 0.022f, releaseSec = 0.085f)
    private val lowPassEnv = AttackReleaseEnvelope(attackSec = 0.024f, releaseSec = 0.095f)
    private val highPassEnv = AttackReleaseEnvelope(attackSec = 0.016f, releaseSec = 0.068f)
    private val mouthFilter = OneEuroFilter(minCutoff = 2.1f, beta = 0.95f)
    private val jawFilter = OneEuroFilter(minCutoff = 2.0f, beta = 0.85f)
    private val highPassFilter = OneEuroFilter(minCutoff = 2.6f, beta = 1.05f)
    private val lowFilter = OneEuroFilter(minCutoff = 1.6f, beta = 0.45f)
    private val midFilter = OneEuroFilter(minCutoff = 1.6f, beta = 0.45f)
    private val highFilter = OneEuroFilter(minCutoff = 1.6f, beta = 0.45f)
    private val headAngleFilter = OneEuroFilter(minCutoff = 0.85f, beta = 0.25f)
    private val headNodFilter = OneEuroFilter(minCutoff = 1.15f, beta = 0.35f)
    private val emphasisEnv = AttackReleaseEnvelope(attackSec = 0.035f, releaseSec = 0.24f)

    private var nextBlinkTimeMs = 1800L
    private var blinkStartMs = -1L
    private var doubleBlinkPending = false
    private var lastBlinkCompletedMs = -2000L
    private var wasTalking = false
    private val rng = Random(42)

    fun reset(seed: Long = 42L) {
        mouthEnv.reset(0f)
        lowPassEnv.reset(0f)
        highPassEnv.reset(0f)
        mouthFilter.reset(0f)
        jawFilter.reset(0f)
        highPassFilter.reset(0f)
        lowFilter.reset(0.33f)
        midFilter.reset(0.34f)
        highFilter.reset(0.33f)
        headAngleFilter.reset(0f)
        headNodFilter.reset(0f)
        emphasisEnv.reset(0f)
        blinkStartMs = -1L
        doubleBlinkPending = false
        lastBlinkCompletedMs = -2000L
        wasTalking = false
        nextBlinkTimeMs = 1200L + (seed.coerceAtLeast(0L) % 1000L)
    }

    fun step(
        timeMs: Long,
        dtSeconds: Float,
        sample: AudioFrameSample?,
        autoNoiseFloor: Float,
        settings: StudioSettings,
        manualOverrideOpen: Float? = null
    ): AnimatorPose {
        val dt = dtSeconds.coerceIn(1f / 120f, 0.1f)
        val tSec = timeMs / 1000f
        val smoothFactor = settings.smoothingAmount.coerceIn(0f, 1f)
        val filterDtScale = (1.35f - smoothFactor * 0.6f).coerceAtLeast(0.45f)

        val rawMouthTarget: Float
        val rawJawTarget: Float
        val rawHighPassTarget: Float
        val targetLow: Float
        val targetMid: Float
        val targetHigh: Float
        val rawEmphasis: Float
        var isTalking = false

        if (manualOverrideOpen != null) {
            rawMouthTarget = manualOverrideOpen.coerceIn(0f, 1f)
            rawJawTarget = rawMouthTarget
            rawHighPassTarget = rawMouthTarget * 0.35f
            targetLow = 0.25f
            targetMid = 0.55f
            targetHigh = 0.20f
            rawEmphasis = 0f
            isTalking = rawMouthTarget > 0.05f
        } else if (sample != null) {
            val gate = (autoNoiseFloor * 0.85f + settings.silenceThreshold).coerceIn(0.01f, 0.75f)
            if (sample.rms <= gate) {
                rawMouthTarget = 0f
                rawJawTarget = 0f
                rawHighPassTarget = 0f
                rawEmphasis = 0f
            } else {
                isTalking = true
                val norm = ((sample.rms - gate) / (1f - gate).coerceAtLeast(0.15f)).coerceIn(0f, 1.5f)
                // Soft-knee curve boosts conversational syllables while keeping silence strictly 0
                rawMouthTarget = (norm.pow(0.76f) * settings.mouthSensitivity).coerceIn(0f, 1.25f)

                // Low-pass band drives jaw drop / chin excursion; high-pass band drives lip corner spread & upper lip lift
                val lpNorm = ((sample.lowPassAmp - gate * 0.45f) / (1f - gate * 0.45f).coerceAtLeast(0.15f))
                    .coerceIn(0f, 1.45f)
                val hpNorm = ((sample.highPassAmp - gate * 0.35f) / (1f - gate * 0.35f).coerceAtLeast(0.15f))
                    .coerceIn(0f, 1.45f)

                rawJawTarget = ((lpNorm.pow(0.80f) * 0.68f + norm.pow(0.76f) * 0.32f) * settings.mouthSensitivity)
                    .coerceIn(0f, 1.35f)
                rawHighPassTarget = (hpNorm.pow(0.74f) * settings.mouthSensitivity).coerceIn(0f, 1.25f)
                rawEmphasis = sample.emphasis
            }
            targetLow = sample.lowBand
            targetMid = sample.midBand
            targetHigh = sample.highBand
        } else {
            rawMouthTarget = 0f
            rawJawTarget = 0f
            rawHighPassTarget = 0f
            targetLow = 0.33f
            targetMid = 0.34f
            targetHigh = 0.33f
            rawEmphasis = 0f
        }

        val envMouth = mouthEnv.process(rawMouthTarget, dt)
        val filteredMouth = mouthFilter.filter(envMouth, dt * filterDtScale)
            .coerceIn(0f, 1.25f)

        val envJaw = lowPassEnv.process(rawJawTarget, dt)
        val filteredJaw = jawFilter.filter(envJaw, dt * filterDtScale)
            .coerceIn(0f, 1.35f)

        val envHp = highPassEnv.process(rawHighPassTarget, dt)
        val filteredHp = highPassFilter.filter(envHp, dt * filterDtScale)
            .coerceIn(0f, 1.25f)

        val wLow = lowFilter.filter(targetLow, dt).coerceAtLeast(0.01f)
        val wMid = midFilter.filter(targetMid, dt).coerceAtLeast(0.01f)
        val wHigh = highFilter.filter(targetHigh, dt).coerceAtLeast(0.01f)
        val wSum = (wLow + wMid + wHigh).coerceAtLeast(0.01f)

        val emph = emphasisEnv.process(rawEmphasis, dt).coerceIn(0f, 1f)

        val normLowW = wLow / wSum
        val normMidW = wMid / wSum
        val normHighW = wHigh / wSum

        // Map filtered low-pass & high-pass bands to lip landmark articulation:
        // - High-pass energy spreads mouth corners outward & lifts upper lip
        // - Low-pass energy drops jaw & slightly narrows/rounds corners
        val finalMouthOpen = if (rawMouthTarget == 0f && filteredMouth < 0.012f) 0f else filteredMouth
        val finalJawDrop = if (rawJawTarget == 0f && filteredJaw < 0.012f) 0f else filteredJaw
        val rawCornerSpread = if (finalMouthOpen <= 0.012f && filteredHp < 0.02f) {
            0f
        } else {
            (filteredHp * 0.78f - finalJawDrop * 0.28f * normLowW).coerceIn(-0.45f, 1.0f)
        }
        val finalUpperLipLift = if (finalMouthOpen <= 0.012f && filteredHp < 0.02f) {
            0f
        } else {
            (filteredHp * 0.72f + finalMouthOpen * normMidW * 0.30f).coerceIn(0f, 1.0f)
        }

        // Head idle + speech-driven motion (kept subtle and organic so mesh warp never exaggerates)
        val strength = settings.headMotionStrength.coerceIn(0f, 1.5f)
        val idleSway = sin(tSec * 1.15f) * 1.1f + sin(tSec * 0.47f) * 0.6f
        val speechTilt = sin(tSec * 2.9f) * finalMouthOpen * 2.4f
        val targetAngle = (idleSway + speechTilt) * strength
        val smoothAngle = headAngleFilter.filter(targetAngle, dt)

        val idleBob = cos(tSec * 1.45f) * 0.002f
        val nod = emph * 0.010f + finalMouthOpen * 0.004f * sin(tSec * 4.8f)
        val smoothBob = headNodFilter.filter((idleBob + nod) * strength, dt)
        val swayX = sin(tSec * 0.82f) * 0.0028f * strength

        // Body breathing
        val breathScale = 1f + sin(tSec * 1.45f) * 0.011f * settings.bodyBreathingStrength

        // Natural periodic blink cycle (2 - 5 seconds + phrase-pause trigger + occasional double blink)
        val speechJustPaused = wasTalking && !isTalking
        wasTalking = isTalking
        val (blinkLeft, blinkRight) = computeBlinkPair(
            timeMs = timeMs,
            avgIntervalSec = settings.blinkRateSeconds,
            speechJustPaused = speechJustPaused
        )
        val blinkMax = maxOf(blinkLeft, blinkRight)

        // Optional emphasis zoom
        val zoom = if (settings.emphasisZoomEnabled) emph else 0f
        val eyebrowLift = (emph * 0.72f + filteredHp * 0.26f).coerceIn(0f, 1f)

        return AnimatorPose(
            mouthOpen = finalMouthOpen,
            wideOpenWeight = normMidW,
            roundWeight = normLowW,
            narrowWideWeight = normHighW,
            blinkAmount = blinkMax,
            headAngleDeg = smoothAngle,
            headBobY = smoothBob,
            headSwayX = swayX,
            breathScale = breathScale,
            eyeWiden = (emph * 0.85f).coerceIn(0f, 1f),
            emphasisZoom = zoom,
            blinkLeft = blinkLeft,
            blinkRight = blinkRight,
            jawDrop = finalJawDrop,
            lipCornerSpread = rawCornerSpread,
            upperLipLift = finalUpperLipLift,
            eyebrowLift = eyebrowLift
        )
    }

    private fun computeBlinkPair(
        timeMs: Long,
        avgIntervalSec: Float,
        speechJustPaused: Boolean
    ): Pair<Float, Float> {
        val blinkDurationMs = 195L
        val leadLagMs = 12L

        // Phrase-pause bias: humans naturally blink at sentence pauses if enough time elapsed
        if (blinkStartMs < 0L) {
            val minGapAfterBlink = 1350L
            val pauseTriggered = speechJustPaused &&
                (timeMs - lastBlinkCompletedMs) >= minGapAfterBlink &&
                rng.nextFloat() < 0.35f

            if (timeMs >= nextBlinkTimeMs || pauseTriggered) {
                blinkStartMs = timeMs
                doubleBlinkPending = rng.nextFloat() < 0.24f
            }
        }

        if (blinkStartMs >= 0L) {
            val elapsed = timeMs - blinkStartMs
            if (elapsed <= blinkDurationMs + leadLagMs) {
                val left = evaluateAsymmetricBlinkCurve(elapsed, blinkDurationMs)
                val right = evaluateAsymmetricBlinkCurve(elapsed - leadLagMs, blinkDurationMs)
                return left to right
            } else if (doubleBlinkPending) {
                doubleBlinkPending = false
                blinkStartMs = timeMs + 70L
            } else {
                blinkStartMs = -1L
                lastBlinkCompletedMs = timeMs
                val clampedInterval = avgIntervalSec.coerceIn(1.5f, 6.5f)
                val minMs = (clampedInterval * 0.62f * 1000f).toLong().coerceAtLeast(1400L)
                val maxMs = (clampedInterval * 1.38f * 1000f).toLong().coerceAtLeast(minMs + 500L)
                nextBlinkTimeMs = timeMs + rng.nextLong(minMs, maxMs)
            }
        }
        return 0f to 0f
    }

    /**
     * Asymmetric eyelid kinematics:
     * - 0..28% (~55ms): brisk lid close
     * - 28..42% (~27ms): full closure hold
     * - 42..100% (~113ms): smooth ease-out open
     */
    private fun evaluateAsymmetricBlinkCurve(elapsedMs: Long, durationMs: Long): Float {
        if (elapsedMs <= 0L || elapsedMs >= durationMs) return 0f
        val p = elapsedMs.toFloat() / durationMs.toFloat()
        return when {
            p < 0.28f -> {
                val t = p / 0.28f
                sin(t * (PI.toFloat() * 0.5f)).coerceIn(0f, 1f)
            }
            p < 0.42f -> 1f
            else -> {
                val t = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
                cos(t * (PI.toFloat() * 0.5f)).pow(1.35f).coerceIn(0f, 1f)
            }
        }
    }
}
