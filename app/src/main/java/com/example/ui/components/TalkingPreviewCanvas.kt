package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.engine.AnimationTimelineState
import com.example.engine.AnimatorPose
import com.example.engine.TalkingAnimatorRenderer
import com.example.model.AudioAnalysisTrack
import com.example.model.BackgroundFillMode
import com.example.model.FaceRig
import com.example.model.StudioSettings

@Composable
fun TalkingPreviewCanvas(
    bitmap: Bitmap,
    rig: FaceRig,
    studioSettings: StudioSettings,
    fillMode: BackgroundFillMode,
    solidFillArgb: Int,
    audioTrack: AudioAnalysisTrack?,
    isPlayingAudio: Boolean,
    audioPositionMs: Long,
    manualMouthOpen: Float? = null,
    modifier: Modifier = Modifier
) {
    val renderer = remember(bitmap) { TalkingAnimatorRenderer(bitmap) }
    val timeline = remember(bitmap) { AnimationTimelineState().apply { reset() } }

    val currentSettings by rememberUpdatedState(studioSettings)
    val currentTrack by rememberUpdatedState(audioTrack)
    val currentIsPlaying by rememberUpdatedState(isPlayingAudio)
    val currentAudioPosMs by rememberUpdatedState(audioPositionMs)
    val currentManualOpen by rememberUpdatedState(manualMouthOpen)

    var pose by remember(bitmap) {
        mutableStateOf(
            AnimatorPose(
                mouthOpen = 0f,
                wideOpenWeight = 0.34f,
                roundWeight = 0.33f,
                narrowWideWeight = 0.33f,
                blinkAmount = 0f,
                headAngleDeg = 0f,
                headBobY = 0f,
                headSwayX = 0f,
                breathScale = 1f,
                eyeWiden = 0f,
                emphasisZoom = 0f
            )
        )
    }

    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    // Step animation strictly once per vsync frame (prevents Compose recomposition jitter)
    LaunchedEffect(bitmap) {
        var idleClockMs = 0L
        var wasPlaying = false
        var lastFrameMs = withFrameMillis { it }
        while (true) {
            val nowMs = withFrameMillis { it }
            val deltaMs = (nowMs - lastFrameMs).coerceIn(4L, 64L)
            lastFrameMs = nowMs
            val dtSec = deltaMs / 1000f

            val playing = currentIsPlaying
            if (playing && !wasPlaying) {
                timeline.reset()
            }
            wasPlaying = playing

            if (!playing) {
                idleClockMs += deltaMs
            }

            val activeTimeMs = if (playing) currentAudioPosMs else idleClockMs
            val track = currentTrack
            val sample = if (playing) track?.sampleAt(activeTimeMs) else null
            val noiseFloor = track?.autoNoiseFloor ?: 0.05f

            pose = timeline.step(
                timeMs = activeTimeMs,
                dtSeconds = dtSec,
                sample = sample,
                autoNoiseFloor = noiseFloor,
                settings = currentSettings,
                manualOverrideOpen = currentManualOpen
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        drawIntoCanvas { canvas ->
            renderer.renderFrame(
                canvas = canvas.nativeCanvas,
                outW = w,
                outH = h,
                rig = rig,
                pose = pose,
                fillMode = fillMode,
                solidFillArgb = solidFillArgb
            )
        }
    }
}
