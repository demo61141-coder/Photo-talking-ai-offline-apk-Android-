package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.TtsLocaleOption
import com.example.i18n.LocalStrings
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsGeneratorSheet(
    availableLocales: List<TtsLocaleOption>,
    isGenerating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onGenerate: (text: String, locale: Locale, speed: Float, pitch: Float) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf(strings.ttsSampleEnglish) }
    val defaultLocale = remember(availableLocales) {
        availableLocales.firstOrNull { it.isAvailable }?.locale ?: Locale.US
    }
    var selectedLocale by remember(defaultLocale) { mutableStateOf(defaultLocale) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ElectricCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.ttsDialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Quick Sample Chips (EN / BN)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        text = strings.ttsSampleEnglish
                        availableLocales.firstOrNull { it.locale.language == "en" && it.isAvailable }
                            ?.let { selectedLocale = it.locale }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("tts_sample_en")
                ) {
                    Text("English Sample", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        text = strings.ttsSampleBengali
                        availableLocales.firstOrNull { it.locale.language == "bn" }
                            ?.let { selectedLocale = it.locale }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("tts_sample_bn")
                ) {
                    Text("বাংলা নমুনা", style = MaterialTheme.typography.labelSmall)
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(strings.ttsTextPlaceholder) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tts_text_input")
            )

            // Voice Language Selector
            Text(
                text = strings.ttsLanguageLabel,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableLocales.forEach { opt ->
                    val selected = selectedLocale.toLanguageTag() == opt.locale.toLanguageTag()
                    FilterChip(
                        selected = selected,
                        onClick = { selectedLocale = opt.locale },
                        label = {
                            Text(
                                text = if (opt.isAvailable) opt.label else "${opt.label} (!)",
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (opt.isAvailable) CyanContainer else VioletContainer,
                            selectedLabelColor = if (opt.isAvailable) ElectricCyan else StudioViolet
                        ),
                        modifier = Modifier.testTag("tts_lang_${opt.locale.toLanguageTag()}")
                    )
                }
            }

            // Speed & Pitch Sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightCard)
                    .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.ttsSpeedLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(String.format("%.2fx", speed), style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = MidnightElevated
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.ttsPitchLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(String.format("%.2fx", pitch), style = MaterialTheme.typography.labelSmall, color = StudioViolet)
                }
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioViolet,
                        activeTrackColor = StudioViolet,
                        inactiveTrackColor = MidnightElevated
                    )
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.ttsLanguageMissingHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        }
                    }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioViolet)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.ttsOpenDeviceSettings, style = MaterialTheme.typography.labelSmall, color = StudioViolet)
                }
            }

            Button(
                onClick = { onGenerate(text, selectedLocale, speed, pitch) },
                enabled = text.isNotBlank() && !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OnCyan
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("tts_generate_attach_button")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnCyan, strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.ttsGenerating)
                } else {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.ttsGenerateAttach, style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
