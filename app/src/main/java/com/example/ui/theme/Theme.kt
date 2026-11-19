package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MidnightStudioColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = StudioViolet,
    onSecondary = OnViolet,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = StudioViolet,
    tertiary = EmeraldAccent,
    onTertiary = OnCyan,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldAccent,
    error = CoralError,
    onError = OnViolet,
    errorContainer = CoralContainer,
    onErrorContainer = CoralError,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightCard,
    surfaceContainerHigh = MidnightElevated,
    outline = MidnightBorder,
    outlineVariant = MidnightBorder
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TalkingPhotoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MidnightStudioColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
