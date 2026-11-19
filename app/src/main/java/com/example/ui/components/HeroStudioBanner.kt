package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalStrings
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

@Composable
fun HeroStudioBanner(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(MidnightCard, Color(0xFF1A1738), MidnightSurface)
                )
            )
            .border(1.dp, MidnightBorder, RoundedCornerShape(24.dp))
    ) {
        // Decorative Studio Soundwave Canvas Backdrop
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ElectricCyan.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.84f, h * 0.30f),
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = Offset(w * 0.84f, h * 0.30f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(StudioViolet.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.16f, h * 0.82f),
                    radius = w * 0.40f
                ),
                radius = w * 0.40f,
                center = Offset(w * 0.16f, h * 0.82f)
            )

            // Twin acoustic sine waves
            val wave1 = Path()
            val wave2 = Path()
            val steps = 48
            for (i in 0..steps) {
                val x = (i / steps.toFloat()) * w
                val t = i * 0.28f
                val y1 = h * 0.68f + sin(t) * 14.dp.toPx() * (0.4f + i / steps.toFloat() * 0.6f)
                val y2 = h * 0.72f + sin(t + 1.4f) * 10.dp.toPx()
                if (i == 0) {
                    wave1.moveTo(x, y1)
                    wave2.moveTo(x, y2)
                } else {
                    wave1.lineTo(x, y1)
                    wave2.lineTo(x, y2)
                }
            }
            drawPath(
                wave1,
                brush = Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.15f), ElectricCyan.copy(alpha = 0.5f))),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                wave2,
                brush = Brush.horizontalGradient(listOf(StudioViolet.copy(alpha = 0.12f), StudioViolet.copy(alpha = 0.4f))),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CyanContainer.copy(alpha = 0.8f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.offlineBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.heroSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
