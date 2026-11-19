package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextSecondary

@Composable
fun GuideScreen(
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                title = strings.guideTitle,
                language = language,
                onToggleLanguage = onToggleLanguage,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = strings.guideIntro,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                // Visual Rigging Blueprint Diagram
                RiggingGuideDiagram()

                GuideTipCard(
                    icon = Icons.Default.Face,
                    accent = ElectricCyan,
                    title = strings.guideFrontSideTitle,
                    body = strings.guideFrontSideBody
                )
                GuideTipCard(
                    icon = Icons.Default.HighQuality,
                    accent = EmeraldAccent,
                    title = strings.guideResolutionTitle,
                    body = strings.guideResolutionBody
                )
                GuideTipCard(
                    icon = Icons.Default.Layers,
                    accent = StudioViolet,
                    title = strings.guideCartoonTitle,
                    body = strings.guideCartoonBody
                )
                GuideTipCard(
                    icon = Icons.Default.Pets,
                    accent = AmberWarning,
                    title = strings.guideAnimalBirdTitle,
                    body = strings.guideAnimalBirdBody
                )
                GuideTipCard(
                    icon = Icons.Default.AccessibilityNew,
                    accent = ElectricCyan,
                    title = strings.guideFullBodyTitle,
                    body = strings.guideFullBodyBody
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RiggingGuideDiagram() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MidnightSurface)
            .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
    ) {
        val w = size.width
        val h = size.height

        // 1. Human / Animal Jaw Diagram (left)
        val c1 = Offset(w * 0.25f, h * 0.48f)
        drawRect(
            color = StudioViolet.copy(alpha = 0.85f),
            topLeft = Offset(c1.x - 42.dp.toPx(), c1.y - 44.dp.toPx()),
            size = Size(84.dp.toPx(), 88.dp.toPx()),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
        drawCircle(EmeraldAccent, 4.dp.toPx(), Offset(c1.x - 16.dp.toPx(), c1.y - 12.dp.toPx()))
        drawCircle(EmeraldAccent, 4.dp.toPx(), Offset(c1.x + 16.dp.toPx(), c1.y - 12.dp.toPx()))
        drawLine(
            color = ElectricCyan,
            start = Offset(c1.x - 18.dp.toPx(), c1.y + 16.dp.toPx()),
            end = Offset(c1.x + 18.dp.toPx(), c1.y + 16.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(StudioViolet, 6.dp.toPx(), Offset(c1.x, c1.y + 48.dp.toPx()))

        // Divider
        drawLine(
            color = MidnightBorder,
            start = Offset(w * 0.5f, 16.dp.toPx()),
            end = Offset(w * 0.5f, h - 16.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Side-View Bird Beak Diagram (right)
        val c2 = Offset(w * 0.75f, h * 0.48f)
        drawRect(
            color = StudioViolet.copy(alpha = 0.85f),
            topLeft = Offset(c2.x - 42.dp.toPx(), c2.y - 40.dp.toPx()),
            size = Size(84.dp.toPx(), 80.dp.toPx()),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
        // Single side-view eye
        drawCircle(EmeraldAccent, 5.dp.toPx(), Offset(c2.x - 6.dp.toPx(), c2.y - 10.dp.toPx()))
        // Beak split line angled forward
        drawLine(
            color = ElectricCyan,
            start = Offset(c2.x + 8.dp.toPx(), c2.y + 4.dp.toPx()),
            end = Offset(c2.x + 48.dp.toPx(), c2.y + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(AmberWarning, 4.dp.toPx(), Offset(c2.x + 48.dp.toPx(), c2.y + 10.dp.toPx()))
        drawCircle(StudioViolet, 6.dp.toPx(), Offset(c2.x - 10.dp.toPx(), c2.y + 44.dp.toPx()))
    }
}

@Composable
private fun GuideTipCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MidnightCard)
            .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CyanContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
