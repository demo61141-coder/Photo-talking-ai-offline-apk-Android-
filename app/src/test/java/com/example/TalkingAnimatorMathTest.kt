package com.example

import com.example.engine.AnimationTimelineState
import com.example.engine.AudioDecoderAndAnalyzer
import com.example.model.AudioFrameSample
import com.example.model.ExportSettings
import com.example.model.NormPoint
import com.example.model.OneEuroFilter
import com.example.model.StudioSettings
import com.example.model.VideoAspectRatio
import com.example.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class TalkingAnimatorMathTest {

    @Test
    fun noiseGate_closesMouthCleanlyWhenSpeechStops() {
        val timeline = AnimationTimelineState().apply { reset() }
        val settings = StudioSettings(silenceThreshold = 0.10f, mouthSensitivity = 1.3f)

        // Loud speech frames
        var lastOpen = 0f
        for (i in 0 until 10) {
            val pose = timeline.step(
                timeMs = i * 33L,
                dtSeconds = 1f / 30f,
                sample = AudioFrameSample(i * 33L, rms = 0.85f, lowBand = 0.2f, midBand = 0.6f, highBand = 0.2f, emphasis = 0.4f),
                autoNoiseFloor = 0.05f,
                settings = settings
            )
            lastOpen = pose.mouthOpen
        }
        assertTrue("Mouth should open during speech", lastOpen > 0.3f)

        // Silent frames below noise gate (gate = 0.05 * 0.85 + 0.10 = 0.1425)
        var silentOpen = 1f
        for (i in 10 until 45) {
            val pose = timeline.step(
                timeMs = i * 33L,
                dtSeconds = 1f / 30f,
                sample = AudioFrameSample(i * 33L, rms = 0.06f, lowBand = 0.3f, midBand = 0.4f, highBand = 0.3f, emphasis = 0f),
                autoNoiseFloor = 0.05f,
                settings = settings
            )
            silentOpen = pose.mouthOpen
        }
        assertEquals("Mouth should close to 0 during silence", 0f, silentOpen, 0.001f)
    }

    @Test
    fun frequencyBands_shiftVisemeShapeWeights() {
        val timeline = AnimationTimelineState().apply { reset() }
        val settings = StudioSettings()
        var pose = timeline.step(
            timeMs = 0L,
            dtSeconds = 1f / 30f,
            sample = AudioFrameSample(0L, rms = 0.8f, lowBand = 0.80f, midBand = 0.10f, highBand = 0.10f, emphasis = 0f),
            autoNoiseFloor = 0.04f,
            settings = settings
        )
        for (i in 1..20) {
            pose = timeline.step(
                timeMs = i * 33L,
                dtSeconds = 1f / 30f,
                sample = AudioFrameSample(i * 33L, rms = 0.8f, lowBand = 0.80f, midBand = 0.10f, highBand = 0.10f, emphasis = 0f),
                autoNoiseFloor = 0.04f,
                settings = settings
            )
        }
        assertTrue("Strong low energy should dominate roundWeight", pose.roundWeight > pose.wideOpenWeight)
        assertTrue("Strong low energy should dominate narrowWideWeight", pose.roundWeight > pose.narrowWideWeight)
    }

    @Test
    fun biquadFilters_separateLowAndHighFrequencyTones() {
        val sampleRate = 16000
        val lowFreq = 250f
        val highFreq = 3000f

        val lpOnLow = AudioDecoderAndAnalyzer.BiquadFilter.lowPass(sampleRate, cutoffHz = 480f)
        val hpOnLow = AudioDecoderAndAnalyzer.BiquadFilter.highPass(sampleRate, cutoffHz = 1650f)
        val lpOnHigh = AudioDecoderAndAnalyzer.BiquadFilter.lowPass(sampleRate, cutoffHz = 480f)
        val hpOnHigh = AudioDecoderAndAnalyzer.BiquadFilter.highPass(sampleRate, cutoffHz = 1650f)

        var lpLowSq = 0.0
        var hpLowSq = 0.0
        var lpHighSq = 0.0
        var hpHighSq = 0.0
        val samples = 1600
        for (i in 0 until samples) {
            val sLow = sin(2.0 * PI * lowFreq * i / sampleRate).toFloat()
            val sHigh = sin(2.0 * PI * highFreq * i / sampleRate).toFloat()
            val yLpLow = lpOnLow.process(sLow)
            val yHpLow = hpOnLow.process(sLow)
            val yLpHigh = lpOnHigh.process(sHigh)
            val yHpHigh = hpOnHigh.process(sHigh)
            if (i >= 200) {
                lpLowSq += yLpLow * yLpLow
                hpLowSq += yHpLow * yHpLow
                lpHighSq += yLpHigh * yLpHigh
                hpHighSq += yHpHigh * yHpHigh
            }
        }
        val rmsLpLow = sqrt(lpLowSq / (samples - 200))
        val rmsHpLow = sqrt(hpLowSq / (samples - 200))
        val rmsLpHigh = sqrt(lpHighSq / (samples - 200))
        val rmsHpHigh = sqrt(hpHighSq / (samples - 200))

        assertTrue("Low-pass filter should pass 250 Hz much more strongly than 3000 Hz", rmsLpLow > rmsLpHigh * 5.0)
        assertTrue("High-pass filter should pass 3000 Hz much more strongly than 250 Hz", rmsHpHigh > rmsHpLow * 5.0)
    }

    @Test
    fun highPassAndLowPassBands_mapToDistinctFacialLandmarks() {
        val settings = StudioSettings(mouthSensitivity = 1.2f)

        // Low-pass dominant vowel ("O" / "U") -> strong jawDrop, minimal/negative lipCornerSpread
        val lowTimeline = AnimationTimelineState().apply { reset() }
        var lowPose = lowTimeline.step(0L, 1f / 30f, null, 0.04f, settings)
        for (i in 0 until 15) {
            lowPose = lowTimeline.step(
                timeMs = i * 33L,
                dtSeconds = 1f / 30f,
                sample = AudioFrameSample(
                    timeMs = i * 33L,
                    rms = 0.80f,
                    lowBand = 0.78f,
                    midBand = 0.16f,
                    highBand = 0.06f,
                    emphasis = 0.1f,
                    lowPassAmp = 0.92f,
                    highPassAmp = 0.06f
                ),
                autoNoiseFloor = 0.04f,
                settings = settings
            )
        }

        // High-pass dominant consonant/vowel ("S" / "E") -> strong lipCornerSpread & upperLipLift
        val highTimeline = AnimationTimelineState().apply { reset() }
        var highPose = highTimeline.step(0L, 1f / 30f, null, 0.04f, settings)
        for (i in 0 until 15) {
            highPose = highTimeline.step(
                timeMs = i * 33L,
                dtSeconds = 1f / 30f,
                sample = AudioFrameSample(
                    timeMs = i * 33L,
                    rms = 0.75f,
                    lowBand = 0.08f,
                    midBand = 0.22f,
                    highBand = 0.70f,
                    emphasis = 0.3f,
                    lowPassAmp = 0.10f,
                    highPassAmp = 0.92f
                ),
                autoNoiseFloor = 0.04f,
                settings = settings
            )
        }

        assertTrue("Low-pass energy should drop jaw significantly", lowPose.jawDrop > 0.55f)
        assertTrue("High-pass energy should spread lip corners wider than low-pass", highPose.lipCornerSpread > lowPose.lipCornerSpread + 0.35f)
        assertTrue("High-pass energy should lift upper lip more than low-pass", highPose.upperLipLift > lowPose.upperLipLift + 0.30f)
    }

    @Test
    fun periodicBlinkCycle_triggersAndCompletesWhileTalking() {
        val timeline = AnimationTimelineState().apply { reset(seed = 100L) }
        val settings = StudioSettings(blinkRateSeconds = 2.5f)

        var maxBlinkLeft = 0f
        var maxBlinkRight = 0f
        var sawClosedThenOpen = false
        var wasBlinking = false

        // Simulate 6 seconds at 60 FPS while speaking continuously
        for (i in 0 until 360) {
            val tMs = (i * 1000L) / 60L
            val pose = timeline.step(
                timeMs = tMs,
                dtSeconds = 1f / 60f,
                sample = AudioFrameSample(
                    timeMs = tMs,
                    rms = 0.65f,
                    lowBand = 0.3f,
                    midBand = 0.5f,
                    highBand = 0.2f,
                    emphasis = 0.2f
                ),
                autoNoiseFloor = 0.05f,
                settings = settings
            )
            if (pose.blinkLeft > maxBlinkLeft) maxBlinkLeft = pose.blinkLeft
            if (pose.blinkRight > maxBlinkRight) maxBlinkRight = pose.blinkRight
            if (pose.blinkLeft > 0.8f) wasBlinking = true
            if (wasBlinking && pose.blinkLeft == 0f) sawClosedThenOpen = true
        }

        assertTrue("Left eye should reach near-full blink closure", maxBlinkLeft > 0.85f)
        assertTrue("Right eye should reach near-full blink closure", maxBlinkRight > 0.85f)
        assertTrue("Blink cycle should return cleanly back to open (0f)", sawClosedThenOpen)
    }

    @Test
    fun oneEuroFilter_reducesHighFrequencyJitter() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.5f)
        val jitterInputs = listOf(0.50f, 0.65f, 0.35f, 0.62f, 0.38f, 0.60f, 0.40f)
        val outputs = jitterInputs.map { filter.filter(it, 1f / 60f) }
        val inputRange = jitterInputs.max() - jitterInputs.min()
        val outputRange = outputs.drop(1).max() - outputs.drop(1).min()
        assertTrue("Filter should attenuate jitter amplitude", outputRange < inputRange)
    }

    @Test
    fun exportSettings_alignsDimensionsToMultipleOf16() {
        for (res in VideoResolution.entries) {
            for (ratio in VideoAspectRatio.entries) {
                val (w, h) = ExportSettings(resolution = res, aspectRatio = ratio).outputDimensions()
                assertEquals(0, w % 16)
                assertEquals(0, h % 16)
                assertTrue(w >= 256 && h >= 256)
            }
        }
    }

    @Test
    fun normPoint_clampsWithinSafeBounds() {
        val clamped = NormPoint(-0.5f, 1.4f).clamped()
        assertEquals(0.02f, clamped.x, 0.0001f)
        assertEquals(0.98f, clamped.y, 0.0001f)
    }
}
