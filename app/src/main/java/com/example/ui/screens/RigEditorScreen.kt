package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.model.BackgroundFillMode
import com.example.model.MouthInteriorStyle
import com.example.model.MouthType
import com.example.model.StudioSettings
import com.example.model.SubjectType
import com.example.ui.components.RigCanvasOverlay
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TalkingPreviewCanvas
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletContainer
import com.example.viewmodel.AutoDetectBanner
import com.example.viewmodel.RigEditorViewModel
import com.example.viewmodel.RigStep

@Composable
fun RigEditorScreen(
    viewModel: RigEditorViewModel,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onBack: () -> Unit,
    onSavedToStudio: (String) -> Unit
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                title = strings.setupTitle,
                language = language,
                onToggleLanguage = onToggleLanguage,
                onBack = onBack,
                actions = {
                    if (zoomScale > 1.05f) {
                        IconButton(
                            onClick = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = strings.resetZoom,
                                tint = ElectricCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.runAutoDetect() },
                        enabled = !state.isDetecting && state.bitmap != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("retry_auto_detect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = strings.retryAutoDetect,
                            tint = StudioViolet
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else {
                val bmp = state.bitmap!!
                val proj = state.project!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Detection status / guidance banner
                    if (state.isDetecting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VioletContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = StudioViolet
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (state.isTilePass) strings.detectingTilePass else strings.detectingFace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else if (state.banner != AutoDetectBanner.NONE) {
                        val isSuccess = state.banner == AutoDetectBanner.DETECTED
                        Surface(
                            color = if (isSuccess) EmeraldContainer else MidnightCard,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isSuccess) EmeraldAccent.copy(alpha = 0.5f) else StudioViolet.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Text(
                                text = if (isSuccess) strings.autoDetectSuccess else strings.autoDetectFallback,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) EmeraldAccent else StudioViolet,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Canvas Area (Live Test Preview on TEST step, Interactive Rig Handles otherwise)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (state.currentStep == RigStep.TEST) {
                            TalkingPreviewCanvas(
                                bitmap = bmp,
                                rig = state.rig,
                                studioSettings = StudioSettings(),
                                fillMode = BackgroundFillMode.BLURRED_PHOTO,
                                solidFillArgb = 0xFF0A0D14.toInt(),
                                audioTrack = null,
                                isPlayingAudio = false,
                                audioPositionMs = 0L,
                                manualMouthOpen = state.testMouthOpen,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, MidnightBorder, RoundedCornerShape(20.dp))
                            )
                        } else {
                            RigCanvasOverlay(
                                bitmap = bmp,
                                rig = state.rig,
                                step = state.currentStep,
                                zoomScale = zoomScale,
                                panOffset = panOffset,
                                onTransformChange = { s, p ->
                                    zoomScale = s
                                    panOffset = p
                                },
                                onUpdateMouth = viewModel::updateMouthCorners,
                                onUpdateEyes = viewModel::updateEyes,
                                onUpdateHeadAndNeck = viewModel::updateHeadAndNeck
                            )
                        }
                    }

                    // Step selector tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val steps = listOf(
                            RigStep.MOUTH_TYPE to strings.stepMouthType,
                            RigStep.MOUTH_LINE to strings.stepMouthLine,
                            RigStep.EYES to strings.stepEyes,
                            RigStep.HEAD_BOX to strings.stepHeadBox,
                            RigStep.TEST to strings.stepTest
                        )
                        steps.forEach { (step, label) ->
                            val selected = state.currentStep == step
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) CyanContainer else MidnightCard)
                                    .border(
                                        1.dp,
                                        if (selected) ElectricCyan else MidnightBorder,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { viewModel.setStep(step) }
                                    .minimumInteractiveComponentSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("rig_step_${step.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) ElectricCyan else TextSecondary
                                )
                            }
                        }
                    }

                    // Step-specific control deck
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MidnightCard)
                            .border(1.dp, MidnightBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (state.currentStep) {
                            RigStep.MOUTH_TYPE -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        SubjectType.HUMAN to strings.subjectHuman,
                                        SubjectType.CARTOON to strings.subjectCartoon,
                                        SubjectType.ANIMAL to strings.subjectAnimal,
                                        SubjectType.BIRD to strings.subjectBird
                                    ).forEach { (subj, lbl) ->
                                        FilterChip(
                                            selected = state.rig.subjectType == subj,
                                            onClick = { viewModel.setSubjectType(subj) },
                                            label = { Text(lbl) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = VioletContainer,
                                                selectedLabelColor = StudioViolet
                                            ),
                                            modifier = Modifier.testTag("subject_chip_${subj.name.lowercase()}")
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        MouthType.JAW to strings.mouthTypeJaw,
                                        MouthType.CARTOON_LIPS to strings.mouthTypeCartoon,
                                        MouthType.BEAK to strings.mouthTypeBeak
                                    ).forEach { (mt, lbl) ->
                                        FilterChip(
                                            selected = state.rig.mouthType == mt,
                                            onClick = { viewModel.setMouthType(mt) },
                                            label = { Text(lbl) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CyanContainer,
                                                selectedLabelColor = ElectricCyan
                                            ),
                                            modifier = Modifier.testTag("mouth_type_${mt.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            RigStep.MOUTH_LINE -> {
                                Text(
                                    text = if (state.rig.mouthType == MouthType.BEAK) strings.hintBeakLine else strings.hintMouthLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                if (state.rig.mouthType != MouthType.BEAK) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = strings.mouthInteriorLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        listOf(
                                            MouthInteriorStyle.REALISTIC to strings.interiorRealistic,
                                            MouthInteriorStyle.CARTOON to strings.interiorCartoon,
                                            MouthInteriorStyle.MINIMAL_DARK to strings.interiorMinimal
                                        ).forEach { (style, lbl) ->
                                            FilterChip(
                                                selected = state.rig.mouthInteriorStyle == style,
                                                onClick = { viewModel.setMouthInteriorStyle(style) },
                                                label = { Text(lbl) }
                                            )
                                        }
                                    }
                                }
                            }

                            RigStep.EYES -> {
                                Text(
                                    text = strings.hintEyes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !state.rig.useSingleEye,
                                        onClick = { viewModel.setUseSingleEye(false) },
                                        label = { Text(strings.eyeModeTwo) },
                                        modifier = Modifier.testTag("eye_mode_two")
                                    )
                                    FilterChip(
                                        selected = state.rig.useSingleEye,
                                        onClick = { viewModel.setUseSingleEye(true) },
                                        label = { Text(strings.eyeModeOne) },
                                        modifier = Modifier.testTag("eye_mode_one")
                                    )
                                }
                            }

                            RigStep.HEAD_BOX -> {
                                Text(
                                    text = strings.hintHeadBox,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            RigStep.TEST -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.testMouthSlider,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(state.testMouthOpen * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan
                                    )
                                }
                                Slider(
                                    value = state.testMouthOpen,
                                    onValueChange = viewModel::setTestMouthOpen,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricCyan,
                                        activeTrackColor = ElectricCyan,
                                        inactiveTrackColor = MidnightElevated
                                    ),
                                    modifier = Modifier.testTag("test_mouth_slider")
                                )
                                Text(
                                    text = strings.hintTest,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.saveRig { onSavedToStudio(proj.id) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_rig_continue_button")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.saveAndContinue,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
