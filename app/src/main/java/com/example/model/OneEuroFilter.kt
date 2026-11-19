package com.example.model

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter (Casiez et al.) for smooth, low-latency animation without jitter.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.2f,
    private val beta: Float = 0.6f,
    private val dCutoff: Float = 1.0f
) {
    private var initialized = false
    private var xPrev = 0f
    private var dxPrev = 0f

    fun reset(initialValue: Float = 0f) {
        initialized = true
        xPrev = initialValue
        dxPrev = 0f
    }

    fun filter(value: Float, dtSeconds: Float): Float {
        val dt = dtSeconds.coerceAtLeast(1e-4f)
        if (!initialized) {
            initialized = true
            xPrev = value
            dxPrev = 0f
            return value
        }
        val dx = (value - xPrev) / dt
        val alphaD = alpha(dt, dCutoff)
        val dxHat = alphaD * dx + (1f - alphaD) * dxPrev
        val cutoff = minCutoff + beta * abs(dxHat)
        val alphaX = alpha(dt, cutoff)
        val xHat = alphaX * value + (1f - alphaX) * xPrev
        xPrev = xHat
        dxPrev = dxHat
        return xHat
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(0.01f))
        return 1f / (1f + tau / dt)
    }
}

/**
 * Asymmetric attack/release envelope follower so mouth opens snappily on syllables
 * and closes smoothly without chattering.
 */
class AttackReleaseEnvelope(
    private val attackSec: Float = 0.035f,
    private val releaseSec: Float = 0.11f
) {
    private var current = 0f

    fun reset(value: Float = 0f) {
        current = value
    }

    fun process(target: Float, dtSeconds: Float): Float {
        val dt = dtSeconds.coerceAtLeast(1e-4f)
        val tau = if (target > current) attackSec else releaseSec
        val coeff = 1f - kotlin.math.exp((-dt / tau.coerceAtLeast(1e-3f)).toDouble()).toFloat()
        current += (target - current) * coeff
        if (target == 0f && current < 0.006f) current = 0f
        return current.coerceIn(0f, 1.35f)
    }
}
