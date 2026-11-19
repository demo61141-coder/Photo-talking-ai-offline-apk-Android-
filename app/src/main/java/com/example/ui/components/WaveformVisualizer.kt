package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextMuted

@Composable
fun WaveformVisualizer(
    waveform: List<Float>,
    noiseGateThreshold: Float,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MidnightSurface)
            .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp))
    ) {
        if (waveform.isEmpty()) return@Canvas
        val padH = 10.dp.toPx()
        val padV = 10.dp.toPx()
        val usableW = (size.width - padH * 2).coerceAtLeast(1f)
        val usableH = (size.height - padV * 2).coerceAtLeast(1f)
        val barSlot = usableW / waveform.size
        val barW = (barSlot * 0.62f).coerceAtLeast(2f)

        waveform.forEachIndexed { idx, amp ->
            val frac = idx.toFloat() / waveform.size.coerceAtLeast(1)
            val isPlayed = frac <= progressFraction
            val aboveGate = amp > noiseGateThreshold
            val barH = (amp * usableH).coerceAtLeast(4f)
            val x = padH + idx * barSlot + (barSlot - barW) * 0.5f
            val y = size.height - padV - barH

            val brush = when {
                isPlayed && aboveGate -> Brush.verticalGradient(listOf(ElectricCyan, StudioViolet))
                aboveGate -> Brush.verticalGradient(
                    listOf(ElectricCyan.copy(alpha = 0.52f), StudioViolet.copy(alpha = 0.35f))
                )
                else -> Brush.verticalGradient(listOf(TextMuted.copy(alpha = 0.45f), TextMuted.copy(alpha = 0.25f)))
            }
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
            )
        }

        // Noise gate horizontal threshold line
        val gateY = size.height - padV - (noiseGateThreshold.coerceIn(0f, 1f) * usableH)
        drawLine(
            color = CoralError.copy(alpha = 0.8f),
            start = Offset(padH, gateY),
            end = Offset(size.width - padH, gateY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        // Playhead indicator
        if (progressFraction > 0f) {
            val headX = padH + progressFraction.coerceIn(0f, 1f) * usableW
            drawLine(
                color = ElectricCyan,
                start = Offset(headX, 4.dp.toPx()),
                end = Offset(headX, size.height - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
