package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.FaceRig
import com.example.model.NormPoint
import com.example.model.NormRect
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.StudioViolet
import com.example.viewmodel.RigStep
import kotlin.math.hypot
import kotlin.math.min

private enum class ActiveHandle {
    NONE,
    MOUTH_LEFT,
    MOUTH_RIGHT,
    EYE_LEFT,
    EYE_RIGHT,
    HEAD_TL,
    HEAD_BR,
    NECK_PIVOT
}

@Composable
fun RigCanvasOverlay(
    bitmap: Bitmap,
    rig: FaceRig,
    step: RigStep,
    zoomScale: Float,
    panOffset: Offset,
    onTransformChange: (scale: Float, pan: Offset) -> Unit,
    onUpdateMouth: (NormPoint, NormPoint) -> Unit,
    onUpdateEyes: (NormPoint, NormPoint) -> Unit,
    onUpdateHeadAndNeck: (NormRect, NormPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val currentRig by rememberUpdatedState(rig)
    val currentStep by rememberUpdatedState(step)
    val density = LocalDensity.current
    var activeHandle by remember { mutableStateOf(ActiveHandle.NONE) }
    var tapEyeToggle by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MidnightSurface)
            .border(1.dp, MidnightBorder, RoundedCornerShape(20.dp))
            .testTag("rig_editor_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }

        // Compute image fit rectangle inside box
        val fitScale = min(boxW / bitmap.width.toFloat(), boxH / bitmap.height.toFloat())
        val imgW = bitmap.width * fitScale
        val imgH = bitmap.height * fitScale
        val imgLeft = (boxW - imgW) * 0.5f
        val imgTop = (boxH - imgH) * 0.5f

        fun normToBox(p: NormPoint): Offset = Offset(
            imgLeft + p.x * imgW,
            imgTop + p.y * imgH
        )

        fun boxToNorm(o: Offset): NormPoint = NormPoint(
            ((o.x - imgLeft) / imgW).coerceIn(0.02f, 0.98f),
            ((o.y - imgTop) / imgH).coerceIn(0.02f, 0.98f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boxW, boxH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Pinch-zoom & pan work whenever user uses 2 fingers (or zoom != 1)
                        if (zoom != 1f || activeHandle == ActiveHandle.NONE) {
                            val nextScale = (zoomScale * zoom).coerceIn(1f, 4.5f)
                            val maxPanX = (boxW * (nextScale - 1f)) * 0.5f
                            val maxPanY = (boxH * (nextScale - 1f)) * 0.5f
                            val nextPan = Offset(
                                (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                            if (zoom != 1f || nextScale > 1.02f) {
                                onTransformChange(nextScale, nextPan)
                            }
                        }
                    }
                }
                .pointerInput(boxW, boxH, zoomScale, panOffset) {
                    detectDragGestures(
                        onDragStart = { startPos ->
                            // Invert graphicsLayer scale+pan around center
                            val unscaled = Offset(
                                (startPos.x - boxW * 0.5f - panOffset.x) / zoomScale + boxW * 0.5f,
                                (startPos.y - boxH * 0.5f - panOffset.y) / zoomScale + boxH * 0.5f
                            )
                            val hitRadius = 38.dp.toPx() / zoomScale
                            val r = currentRig
                            activeHandle = when (currentStep) {
                                RigStep.MOUTH_LINE -> {
                                    val dL = hypot(unscaled.x - normToBox(r.mouthLeft).x, unscaled.y - normToBox(r.mouthLeft).y)
                                    val dR = hypot(unscaled.x - normToBox(r.mouthRight).x, unscaled.y - normToBox(r.mouthRight).y)
                                    when {
                                        dL <= hitRadius && dL <= dR -> ActiveHandle.MOUTH_LEFT
                                        dR <= hitRadius -> ActiveHandle.MOUTH_RIGHT
                                        else -> ActiveHandle.NONE
                                    }
                                }
                                RigStep.EYES -> {
                                    val dL = hypot(unscaled.x - normToBox(r.eyeLeft).x, unscaled.y - normToBox(r.eyeLeft).y)
                                    val dR = if (r.useSingleEye) Float.MAX_VALUE
                                    else hypot(unscaled.x - normToBox(r.eyeRight).x, unscaled.y - normToBox(r.eyeRight).y)
                                    when {
                                        dL <= hitRadius && dL <= dR -> ActiveHandle.EYE_LEFT
                                        dR <= hitRadius -> ActiveHandle.EYE_RIGHT
                                        else -> ActiveHandle.NONE
                                    }
                                }
                                RigStep.HEAD_BOX -> {
                                    val tl = normToBox(NormPoint(r.headBox.left, r.headBox.top))
                                    val br = normToBox(NormPoint(r.headBox.right, r.headBox.bottom))
                                    val nk = normToBox(r.neckPivot)
                                    val dTl = hypot(unscaled.x - tl.x, unscaled.y - tl.y)
                                    val dBr = hypot(unscaled.x - br.x, unscaled.y - br.y)
                                    val dNk = hypot(unscaled.x - nk.x, unscaled.y - nk.y)
                                    val minD = minOf(dTl, dBr, dNk)
                                    when {
                                        minD > hitRadius -> ActiveHandle.NONE
                                        minD == dNk -> ActiveHandle.NECK_PIVOT
                                        minD == dTl -> ActiveHandle.HEAD_TL
                                        else -> ActiveHandle.HEAD_BR
                                    }
                                }
                                else -> ActiveHandle.NONE
                            }
                        },
                        onDragEnd = { activeHandle = ActiveHandle.NONE },
                        onDragCancel = { activeHandle = ActiveHandle.NONE },
                        onDrag = { change, dragAmount ->
                            if (activeHandle == ActiveHandle.NONE) return@detectDragGestures
                            change.consume()
                            val dxNorm = (dragAmount.x / zoomScale) / imgW
                            val dyNorm = (dragAmount.y / zoomScale) / imgH
                            val r = currentRig
                            when (activeHandle) {
                                ActiveHandle.MOUTH_LEFT -> onUpdateMouth(
                                    NormPoint(r.mouthLeft.x + dxNorm, r.mouthLeft.y + dyNorm),
                                    r.mouthRight
                                )
                                ActiveHandle.MOUTH_RIGHT -> onUpdateMouth(
                                    r.mouthLeft,
                                    NormPoint(r.mouthRight.x + dxNorm, r.mouthRight.y + dyNorm)
                                )
                                ActiveHandle.EYE_LEFT -> onUpdateEyes(
                                    NormPoint(r.eyeLeft.x + dxNorm, r.eyeLeft.y + dyNorm),
                                    r.eyeRight
                                )
                                ActiveHandle.EYE_RIGHT -> onUpdateEyes(
                                    r.eyeLeft,
                                    NormPoint(r.eyeRight.x + dxNorm, r.eyeRight.y + dyNorm)
                                )
                                ActiveHandle.HEAD_TL -> onUpdateHeadAndNeck(
                                    NormRect(
                                        (r.headBox.left + dxNorm).coerceAtMost(r.headBox.right - 0.08f),
                                        (r.headBox.top + dyNorm).coerceAtMost(r.headBox.bottom - 0.08f),
                                        r.headBox.right,
                                        r.headBox.bottom
                                    ),
                                    r.neckPivot
                                )
                                ActiveHandle.HEAD_BR -> onUpdateHeadAndNeck(
                                    NormRect(
                                        r.headBox.left,
                                        r.headBox.top,
                                        (r.headBox.right + dxNorm).coerceAtLeast(r.headBox.left + 0.08f),
                                        (r.headBox.bottom + dyNorm).coerceAtLeast(r.headBox.top + 0.08f)
                                    ),
                                    r.neckPivot
                                )
                                ActiveHandle.NECK_PIVOT -> onUpdateHeadAndNeck(
                                    r.headBox,
                                    NormPoint(r.neckPivot.x + dxNorm, r.neckPivot.y + dyNorm)
                                )
                                ActiveHandle.NONE -> Unit
                            }
                        }
                    )
                }
                .pointerInput(boxW, boxH, zoomScale, panOffset) {
                    detectTapGestures { tapPos ->
                        val unscaled = Offset(
                            (tapPos.x - boxW * 0.5f - panOffset.x) / zoomScale + boxW * 0.5f,
                            (tapPos.y - boxH * 0.5f - panOffset.y) / zoomScale + boxH * 0.5f
                        )
                        val normPt = boxToNorm(unscaled)
                        val r = currentRig
                        when (currentStep) {
                            RigStep.EYES -> {
                                if (r.useSingleEye) {
                                    onUpdateEyes(normPt, r.eyeRight)
                                } else {
                                    if (tapEyeToggle) {
                                        onUpdateEyes(normPt, r.eyeRight)
                                    } else {
                                        onUpdateEyes(r.eyeLeft, normPt)
                                    }
                                    tapEyeToggle = !tapEyeToggle
                                }
                            }
                            RigStep.HEAD_BOX -> {
                                // Tap below head box places neck pivot conveniently
                                if (normPt.y > r.headBox.centerY) {
                                    onUpdateHeadAndNeck(r.headBox, normPt)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val invScale = 1f / zoomScale

                // Head Box + Neck Pivot
                val hTL = normToBox(NormPoint(rig.headBox.left, rig.headBox.top))
                val hBR = normToBox(NormPoint(rig.headBox.right, rig.headBox.bottom))
                val headAlpha = if (step == RigStep.HEAD_BOX) 0.95f else 0.40f
                drawRect(
                    color = StudioViolet.copy(alpha = headAlpha),
                    topLeft = hTL,
                    size = Size(hBR.x - hTL.x, hBR.y - hTL.y),
                    style = Stroke(
                        width = 2.dp.toPx() * invScale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * invScale, 8f * invScale))
                    )
                )

                val neck = normToBox(rig.neckPivot)
                drawCircle(
                    color = StudioViolet.copy(alpha = headAlpha),
                    radius = 9.dp.toPx() * invScale,
                    center = neck
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx() * invScale,
                    center = neck
                )

                if (step == RigStep.HEAD_BOX) {
                    listOf(hTL, hBR).forEach { corner ->
                        drawCircle(StudioViolet, 10.dp.toPx() * invScale, corner)
                        drawCircle(Color.White, 4.dp.toPx() * invScale, corner)
                    }
                }

                // Eyes
                val eyeAlpha = if (step == RigStep.EYES) 1f else 0.55f
                val eyeTargets = if (rig.useSingleEye) listOf(rig.eyeLeft) else listOf(rig.eyeLeft, rig.eyeRight)
                eyeTargets.forEach { e ->
                    val ep = normToBox(e)
                    drawCircle(
                        color = EmeraldAccent.copy(alpha = eyeAlpha),
                        radius = 11.dp.toPx() * invScale,
                        center = ep,
                        style = Stroke(width = 2.2.dp.toPx() * invScale)
                    )
                    drawCircle(
                        color = EmeraldAccent.copy(alpha = eyeAlpha),
                        radius = 3.5.dp.toPx() * invScale,
                        center = ep
                    )
                }

                // Mouth / Beak split line + corner handles
                val mL = normToBox(rig.mouthLeft)
                val mR = normToBox(rig.mouthRight)
                val mouthAlpha = if (step == RigStep.MOUTH_LINE) 1f else 0.65f
                drawLine(
                    color = ElectricCyan.copy(alpha = mouthAlpha),
                    start = mL,
                    end = mR,
                    strokeWidth = 2.8.dp.toPx() * invScale
                )

                // Lower jaw / lower beak indicator arc
                val midX = (mL.x + mR.x) * 0.5f
                val midY = (mL.y + mR.y) * 0.5f
                val jawHint = hypot((mR.x - mL.x), (mR.y - mL.y)) * 0.25f
                drawLine(
                    color = AmberWarning.copy(alpha = mouthAlpha * 0.8f),
                    start = Offset(midX, midY),
                    end = Offset(midX, midY + jawHint),
                    strokeWidth = 2.dp.toPx() * invScale
                )

                listOf(mL, mR).forEach { pt ->
                    drawCircle(
                        color = ElectricCyan.copy(alpha = mouthAlpha),
                        radius = (if (step == RigStep.MOUTH_LINE) 10.dp else 6.dp).toPx() * invScale,
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = (if (step == RigStep.MOUTH_LINE) 3.5.dp else 2.dp).toPx() * invScale,
                        center = pt
                    )
                }
            }
        }
    }
}
