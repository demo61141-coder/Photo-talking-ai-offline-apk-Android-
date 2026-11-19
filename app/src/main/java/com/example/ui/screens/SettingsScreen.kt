package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.model.BackgroundFillMode
import com.example.model.VideoAspectRatio
import com.example.model.VideoResolution
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletContainer
import com.example.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                title = strings.settingsTitle,
                language = settings.language,
                onToggleLanguage = {
                    viewModel.update { it.copy(language = it.language.toggled()) }
                },
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
                // Language Card
                SettingsCard {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { viewModel.update { it.copy(language = lang) } },
                                label = { Text(lang.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanContainer,
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            )
                        }
                    }
                }

                // Animation & Audio Sliders Card
                SettingsCard {
                    SettingSlider(
                        label = strings.mouthSensitivitySetting,
                        value = settings.mouthSensitivity,
                        range = 0.5f..2.5f,
                        valueDisplay = String.format("%.2fx", settings.mouthSensitivity),
                        onChange = { v -> viewModel.update { it.copy(mouthSensitivity = v) } }
                    )
                    SettingSlider(
                        label = strings.silenceThresholdSetting,
                        value = settings.silenceThreshold,
                        range = 0f..0.40f,
                        valueDisplay = "${(settings.silenceThreshold * 100).toInt()}%",
                        onChange = { v -> viewModel.update { it.copy(silenceThreshold = v) } }
                    )
                    SettingSlider(
                        label = strings.smoothingSetting,
                        value = settings.smoothingAmount,
                        range = 0f..1f,
                        valueDisplay = "${(settings.smoothingAmount * 100).toInt()}%",
                        onChange = { v -> viewModel.update { it.copy(smoothingAmount = v) } }
                    )
                    SettingSlider(
                        label = strings.blinkRateSetting,
                        value = settings.blinkRateSeconds,
                        range = 2.0f..5.0f,
                        valueDisplay = String.format("%.1fs", settings.blinkRateSeconds),
                        onChange = { v -> viewModel.update { it.copy(blinkRateSeconds = v) } }
                    )
                    SettingSlider(
                        label = strings.headMotionSetting,
                        value = settings.headMotionStrength,
                        range = 0f..1.5f,
                        valueDisplay = "${(settings.headMotionStrength * 100).toInt()}%",
                        onChange = { v -> viewModel.update { it.copy(headMotionStrength = v) } }
                    )
                    SettingSlider(
                        label = strings.bodyBreathingSetting,
                        value = settings.bodyBreathingStrength,
                        range = 0f..1.5f,
                        valueDisplay = "${(settings.bodyBreathingStrength * 100).toInt()}%",
                        onChange = { v -> viewModel.update { it.copy(bodyBreathingStrength = v) } }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.emphasisZoomSetting,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.emphasisZoomEnabled,
                            onCheckedChange = { checked ->
                                viewModel.update { it.copy(emphasisZoomEnabled = checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ElectricCyan,
                                checkedTrackColor = CyanContainer
                            ),
                            modifier = Modifier.testTag("settings_emphasis_zoom_switch")
                        )
                    }
                }

                // Default Export Preset Card
                SettingsCard {
                    Text(strings.defaultExportPreset, style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoResolution.entries.forEach { res ->
                            FilterChip(
                                selected = settings.defaultResolution == res,
                                onClick = { viewModel.update { it.copy(defaultResolution = res) } },
                                label = { Text(res.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoAspectRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = settings.defaultAspectRatio == ratio,
                                onClick = { viewModel.update { it.copy(defaultAspectRatio = ratio) } },
                                label = { Text(ratio.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletContainer,
                                    selectedLabelColor = StudioViolet
                                )
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.defaultFillMode == BackgroundFillMode.BLURRED_PHOTO,
                            onClick = { viewModel.update { it.copy(defaultFillMode = BackgroundFillMode.BLURRED_PHOTO) } },
                            label = { Text(strings.fillBlurred) }
                        )
                        FilterChip(
                            selected = settings.defaultFillMode == BackgroundFillMode.SOLID_COLOR,
                            onClick = { viewModel.update { it.copy(defaultFillMode = BackgroundFillMode.SOLID_COLOR) } },
                            label = { Text(strings.fillSolid) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.resetDefaults() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_reset_button")
                ) {
                    Text(strings.resetDefaults)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MidnightCard)
            .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(valueDisplay, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = MidnightElevated
            )
        )
    }
}
