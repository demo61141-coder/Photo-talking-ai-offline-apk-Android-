package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.model.BackgroundFillMode
import com.example.model.VideoAspectRatio
import com.example.model.VideoResolution
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TalkingPreviewCanvas
import com.example.ui.components.TtsGeneratorSheet
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletContainer
import com.example.viewmodel.StudioViewModel

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onBack: () -> Unit,
    onEditRig: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onVideoExported: (projectId: String, videoPath: String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshProject()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreviewPlayback() }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioUri(uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startMicRecording()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                title = state.project?.title ?: strings.studioTitle,
                language = language,
                onToggleLanguage = onToggleLanguage,
                onBack = onBack,
                actions = {
                    state.project?.let { p ->
                        IconButton(
                            onClick = { onEditRig(p.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_edit_rig_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = strings.editRig,
                                tint = StudioViolet
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = TextSecondary
                        )
                    }
                }
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
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = ElectricCyan,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (state.bitmap == null || state.project == null) {
                Text(
                    text = strings.errorLoadingPhoto,
                    color = CoralError,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else {
                val bmp = state.bitmap!!
                val proj = state.project!!
                val ratioFloat = state.exportSettings.aspectRatio.wRatio.toFloat() /
                    state.exportSettings.aspectRatio.hRatio.toFloat()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Live Animated Preview Stage (exact match to exported video frame)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(316.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MidnightSurface)
                            .border(1.dp, MidnightBorder, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        TalkingPreviewCanvas(
                            bitmap = bmp,
                            rig = proj.rig,
                            studioSettings = state.studioSettings,
                            fillMode = state.exportSettings.fillMode,
                            solidFillArgb = state.exportSettings.solidColorArgb,
                            audioTrack = state.audioTrack,
                            isPlayingAudio = state.isPlayingPreview,
                            audioPositionMs = state.previewTimeMs,
                            modifier = Modifier
                                .padding(10.dp)
                                .aspectRatio(ratioFloat, matchHeightConstraintsFirst = ratioFloat < 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MidnightBorder.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .testTag("studio_preview_canvas")
                        )

                        Text(
                            text = "${state.exportSettings.aspectRatio.label} • ${state.exportSettings.resolution.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ObsidianBg.copy(alpha = 0.78f))
                                .border(1.dp, MidnightBorder, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )

                        if (state.audioTrack != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ObsidianBg.copy(alpha = 0.85f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .clickable { viewModel.togglePreviewPlayback() }
                                    .minimumInteractiveComponentSize()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("preview_play_pause_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (state.isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlayingPreview) strings.pausePreview else strings.playPreview,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.isPlayingPreview) strings.pausePreview else strings.playPreview,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                    }

                    // Audio Import & Mic Recording Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            enabled = !state.isRecordingMic && !state.isAnalyzingAudio,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("upload_audio_button")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.importAudio, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (state.isRecordingMic) {
                                    viewModel.stopMicRecording()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startMicRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !state.isAnalyzingAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isRecordingMic) CoralError else VioletContainer,
                                contentColor = if (state.isRecordingMic) ObsidianBg else StudioViolet
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("record_mic_button")
                        ) {
                            Icon(
                                imageVector = if (state.isRecordingMic) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isRecordingMic) strings.stopRecording else strings.recordVoice,
                                maxLines = 1
                            )
                        }
                    }

                    // Phone Text-to-Speech Generator CTA
                    OutlinedButton(
                        onClick = { viewModel.openTtsSheet() },
                        enabled = !state.isRecordingMic && !state.isAnalyzingAudio,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_tts_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = StudioViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.ttsButtonLabel, color = StudioViolet)
                    }

                    // Live Mic Recording Indicator
                    AnimatedVisibility(visible = state.isRecordingMic) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CoralContainer)
                                .border(1.dp, CoralError.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.micLevel },
                                color = CoralError,
                                trackColor = ObsidianBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = formatMs(state.micElapsedMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = CoralError
                            )
                        }
                    }

                    // Mic Permission Denied Recovery Card
                    AnimatedVisibility(visible = state.micPermissionDenied) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CoralContainer)
                                .border(1.dp, CoralError.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.micPermissionTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = CoralError
                            )
                            Text(
                                text = strings.micPermissionBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    viewModel.clearMicPermissionDenied()
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }) {
                                    Text(strings.grantPermission, color = ElectricCyan)
                                }
                                TextButton(onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                }) {
                                    Text(strings.openAppSettings, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // Error Banner
                    AnimatedVisibility(visible = state.errorMessage != null) {
                        Surface(
                            color = CoralContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.clearError() }
                        ) {
                            Text(
                                text = state.errorMessage ?: strings.errorDecodingAudio,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralError,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Audio Decoding / Waveform / Noise Gate Card
                    if (state.isAnalyzingAudio) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MidnightCard)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(strings.analyzingAudio, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (state.audioTrack == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MidnightCard)
                                .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = strings.noAudioPrompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        val track = state.audioTrack!!
                        val effectiveGate = (track.autoNoiseFloor * 0.85f + state.studioSettings.silenceThreshold).coerceIn(0.01f, 0.75f)
                        val progress = (state.previewTimeMs.toFloat() / track.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MidnightCard)
                                .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${formatMs(track.durationMs)} • ${track.fps}fps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            WaveformVisualizer(
                                waveform = track.waveformPreview,
                                noiseGateThreshold = effectiveGate,
                                progressFraction = progress
                            )

                            Text(
                                text = "${strings.noiseFloorAutoLabel}: ${(track.autoNoiseFloor * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )

                            CompactTuningSlider(
                                label = strings.noiseGateLabel,
                                value = state.studioSettings.silenceThreshold,
                                range = 0f..0.40f,
                                valueText = "${(state.studioSettings.silenceThreshold * 100).toInt()}%",
                                onValueChange = { v -> viewModel.updateStudioTuning { it.copy(silenceThreshold = v) } }
                            )
                            CompactTuningSlider(
                                label = strings.sensitivityLabel,
                                value = state.studioSettings.mouthSensitivity,
                                range = 0.5f..2.5f,
                                valueText = String.format("%.2fx", state.studioSettings.mouthSensitivity),
                                onValueChange = { v -> viewModel.updateStudioTuning { it.copy(mouthSensitivity = v) } }
                            )
                            CompactTuningSlider(
                                label = strings.smoothingLabel,
                                value = state.studioSettings.smoothingAmount,
                                range = 0f..1f,
                                valueText = "${(state.studioSettings.smoothingAmount * 100).toInt()}%",
                                onValueChange = { v -> viewModel.updateStudioTuning { it.copy(smoothingAmount = v) } }
                            )
                        }
                    }

                    // Export Settings Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MidnightCard)
                            .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.exportSectionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Resolution
                        Text(strings.resolutionLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = state.exportSettings.resolution == res,
                                    onClick = { viewModel.updateResolution(res) },
                                    label = { Text(res.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanContainer,
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("export_res_${res.label}")
                                )
                            }
                            listOf(30, 60).forEach { fps ->
                                FilterChip(
                                    selected = state.exportSettings.fps == fps,
                                    onClick = { viewModel.updateFps(fps) },
                                    label = { Text("$fps FPS") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VioletContainer,
                                        selectedLabelColor = StudioViolet
                                    ),
                                    modifier = Modifier.testTag("export_fps_$fps")
                                )
                            }
                        }

                        // Aspect Ratio
                        Text(strings.aspectRatioLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoAspectRatio.entries.forEach { ratio ->
                                FilterChip(
                                    selected = state.exportSettings.aspectRatio == ratio,
                                    onClick = { viewModel.updateAspectRatio(ratio) },
                                    label = { Text(ratio.label) },
                                    modifier = Modifier.testTag("export_ratio_${ratio.name.lowercase()}")
                                )
                            }
                        }

                        // Background Fill Mode
                        Text(strings.backgroundFillLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.exportSettings.fillMode == BackgroundFillMode.BLURRED_PHOTO,
                                onClick = { viewModel.updateFillMode(BackgroundFillMode.BLURRED_PHOTO) },
                                label = { Text(strings.fillBlurred) },
                                modifier = Modifier.testTag("export_fill_blurred")
                            )
                            FilterChip(
                                selected = state.exportSettings.fillMode == BackgroundFillMode.SOLID_COLOR,
                                onClick = { viewModel.updateFillMode(BackgroundFillMode.SOLID_COLOR) },
                                label = { Text(strings.fillSolid) },
                                modifier = Modifier.testTag("export_fill_solid")
                            )
                        }
                    }

                    // Render MP4 CTA
                    Button(
                        onClick = {
                            viewModel.startExport { videoPath ->
                                onVideoExported(proj.id, videoPath)
                            }
                        },
                        enabled = state.audioTrack != null && !state.isExporting && !state.isRecordingMic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("render_export_mp4_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.renderExportMp4, style = MaterialTheme.typography.titleSmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Export Progress Modal Overlay
            if (state.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.88f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .padding(24.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MidnightCard)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                        Text(
                            text = strings.renderingProgress,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { state.exportProgress },
                            color = ElectricCyan,
                            trackColor = MidnightElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${(state.exportProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        TextButton(
                            onClick = { viewModel.cancelExport() },
                            modifier = Modifier.testTag("cancel_export_button")
                        ) {
                            Text(strings.cancelRender, color = CoralError)
                        }
                    }
                }
            }
            // Phone Text-to-Speech Modal Sheet
            if (state.showTtsSheet) {
                TtsGeneratorSheet(
                    availableLocales = state.ttsLocales,
                    isGenerating = state.isGeneratingTts,
                    errorMessage = state.ttsErrorMessage,
                    onDismiss = { viewModel.dismissTtsSheet() },
                    onGenerate = { text, locale, speed, pitch ->
                        viewModel.generateTtsAudio(text, locale, speed, pitch)
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactTuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = MidnightElevated
            )
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format("%d:%02d", m, s)
}
