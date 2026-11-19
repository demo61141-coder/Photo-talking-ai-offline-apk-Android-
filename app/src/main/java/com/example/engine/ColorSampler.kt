package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.NormPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ColorSampler {

    /**
     * Samples the upper eyelid / brow skin/fur/feather tone around each eye center
     * (biasing toward the upper arc to avoid dark pupils/eyelashes and lower-face beards).
     * Returns null if transparent or extremely noisy.
     */
    fun sampleEyelidColor(bitmap: Bitmap, eye: NormPoint, secondEye: NormPoint? = null): Int? {
        val points = listOfNotNull(eye, secondEye)
        val samples = mutableListOf<Int>()
        val radiusPx = (bitmap.width * 0.032f).coerceIn(6f, 40f)

        for (pt in points) {
            val cx = (pt.x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val cy = (pt.y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            // Sample upper semicircle (eyelid/brow skin) + side temples
            for (ring in 1..2) {
                val r = radiusPx * (0.85f + ring * 0.45f)
                for (i in 0 until 12) {
                    // Bias angle toward upper half (-PI..0) plus slight sides
                    val angle = -PI * 1.1 + (PI * 1.2 * i) / 11.0
                    val sx = (cx + cos(angle) * r).toInt().coerceIn(0, bitmap.width - 1)
                    val sy = (cy + sin(angle) * r).toInt().coerceIn(0, bitmap.height - 1)
                    val pixel = bitmap.getPixel(sx, sy)
                    if (Color.alpha(pixel) >= 200) {
                        samples.add(pixel)
                    }
                }
            }
        }

        if (samples.size < 5) return null

        // Keep middle luminance slice (discard dark lashes/brows and bright glare)
        val sortedByLuma = samples.sortedBy { luma(it) }
        val startIdx = (sortedByLuma.size * 0.30f).toInt()
        val endIdx = (sortedByLuma.size * 0.85f).toInt().coerceAtLeast(startIdx + 1)
        val midSlice = sortedByLuma.subList(startIdx, endIdx)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (c in midSlice) {
            sumR += Color.red(c)
            sumG += Color.green(c)
            sumB += Color.blue(c)
        }
        val n = midSlice.size
        val avgR = (sumR / n).toInt()
        val avgG = (sumG / n).toInt()
        val avgB = (sumB / n).toInt()

        var varSum = 0.0
        for (c in midSlice) {
            val dr = Color.red(c) - avgR
            val dg = Color.green(c) - avgG
            val db = Color.blue(c) - avgB
            varSum += dr * dr + dg * dg + db * db
        }
        val stdDev = sqrt(varSum / n)
        if (stdDev > 95.0) return null

        return Color.rgb(avgR, avgG, avgB)
    }

    /**
     * Samples the beak color near the split line and darkens it for realistic inner-beak fill.
     */
    fun sampleBeakInnerColor(bitmap: Bitmap, left: NormPoint, right: NormPoint): Int {
        val samples = mutableListOf<Int>()
        for (i in 1..9) {
            val t = i / 10f
            val nx = left.x + (right.x - left.x) * t
            val ny = left.y + (right.y - left.y) * t
            val px = (nx * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val py = (ny * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val c = bitmap.getPixel(px, py)
            if (Color.alpha(c) >= 180) samples.add(c)
        }
        if (samples.isEmpty()) return Color.rgb(52, 30, 26)
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in samples) {
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
        }
        val n = samples.size
        val darkR = ((r / n) * 0.36f).toInt().coerceIn(14, 110)
        val darkG = ((g / n) * 0.32f).toInt().coerceIn(10, 95)
        val darkB = ((b / n) * 0.32f).toInt().coerceIn(10, 95)
        return Color.rgb(darkR, darkG, darkB)
    }

    private fun luma(c: Int): Float =
        0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
}
