package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.BackgroundFillMode
import com.example.model.FaceRig
import com.example.model.MouthInteriorStyle
import com.example.model.MouthType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unified 2D Mesh-Warp & Layered Facial Rig Renderer.
 *
 * Uses a single 28x28 `drawBitmapMesh` deformation pass over the entire character so the head
 * rotates/bobs around the neck pivot and the lower jaw/beak stretches smoothly WITHOUT creating
 * a duplicate ghosted head over the body. Mouth interior cavity and realistic feathered eyelids
 * are transformed into the exact same head space.
 */
class TalkingAnimatorRenderer(
    private val sourceBitmap: Bitmap
) {
    private val meshCols = 34
    private val meshRows = 34
    private val meshVerts = FloatArray((meshCols + 1) * (meshRows + 1) * 2)

    private var blurredBgCache: Bitmap? = null
    private var cachedBgW = 0
    private var cachedBgH = 0

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun renderFrame(
        canvas: Canvas,
        outW: Int,
        outH: Int,
        rig: FaceRig,
        pose: AnimatorPose,
        fillMode: BackgroundFillMode,
        solidFillArgb: Int
    ) {
        if (outW <= 0 || outH <= 0) return

        // 1. Fit subject rect + optional subtle emphasis zoom around face center
        val fitRect = computeFitRect(
            sourceBitmap.width.toFloat(),
            sourceBitmap.height.toFloat(),
            outW.toFloat(),
            outH.toFloat()
        )

        // Studio Background (Smooth Bokeh Photo or Solid Color) + subtle frame separation behind portrait
        drawBackground(canvas, outW, outH, fitRect, fillMode, solidFillArgb)

        val zoomScale = 1f + pose.emphasisZoom * 0.042f
        val zoomPivotX = fitRect.left + rig.headBox.centerX * fitRect.width()
        val zoomPivotY = fitRect.top + rig.headBox.centerY * fitRect.height()

        canvas.save()
        if (zoomScale > 1.0005f) {
            canvas.scale(zoomScale, zoomScale, zoomPivotX, zoomPivotY)
        }

        // Compute face landmark coordinates in fitRect pixel space
        val headRect = RectF(
            fitRect.left + rig.headBox.left * fitRect.width(),
            fitRect.top + rig.headBox.top * fitRect.height(),
            fitRect.left + rig.headBox.right * fitRect.width(),
            fitRect.top + rig.headBox.bottom * fitRect.height()
        )
        val neckX = fitRect.left + rig.neckPivot.x * fitRect.width()
        val neckY = fitRect.top + rig.neckPivot.y * fitRect.height()

        val lx = fitRect.left + rig.mouthLeft.x * fitRect.width()
        val ly = fitRect.top + rig.mouthLeft.y * fitRect.height()
        val rx = fitRect.left + rig.mouthRight.x * fitRect.width()
        val ry = fitRect.top + rig.mouthRight.y * fitRect.height()
        val mouthCx = (lx + rx) * 0.5f
        val mouthCy = (ly + ry) * 0.5f
        val rawMouthW = hypot((rx - lx).toDouble(), (ry - ly).toDouble()).toFloat()
        val mouthWidth = max(rawMouthW, headRect.width() * 0.25f)
        val mouthAngleRad = atan2((ry - ly).toDouble(), (rx - lx).toDouble()).toFloat()
        val mouthAngleDeg = Math.toDegrees(mouthAngleRad.toDouble()).toFloat()

        val upperLipY = fitRect.top + (rig.upperLipCenter?.y ?: ((rig.mouthLeft.y + rig.mouthRight.y) * 0.5f - rig.headBox.height * 0.04f)) * fitRect.height()
        val mouthBottomY = fitRect.top + rig.mouthBottom.y * fitRect.height()
        val chinY = fitRect.top + (rig.chinBottom?.y ?: rig.headBox.bottom) * fitRect.height()

        val eyeLx = fitRect.left + rig.eyeLeft.x * fitRect.width()
        val eyeLy = fitRect.top + rig.eyeLeft.y * fitRect.height()
        val eyeLRx = ((rig.leftEyeSize?.width?.times(fitRect.width()) ?: (headRect.width() * 0.21f)) * 0.56f).coerceAtLeast(8f)
        val eyeLRy = ((rig.leftEyeSize?.height?.times(fitRect.height()) ?: (eyeLRx * 1.35f)) * 0.65f).coerceAtLeast(5.5f)

        val eyeRx = fitRect.left + rig.eyeRight.x * fitRect.width()
        val eyeRy = fitRect.top + rig.eyeRight.y * fitRect.height()
        val eyeRRx = ((rig.rightEyeSize?.width?.times(fitRect.width()) ?: (headRect.width() * 0.21f)) * 0.56f).coerceAtLeast(8f)
        val eyeRRy = ((rig.rightEyeSize?.height?.times(fitRect.height()) ?: (eyeRRx * 1.35f)) * 0.65f).coerceAtLeast(5.5f)

        val browLx = fitRect.left + (rig.leftEyebrowPeak?.x ?: rig.eyeLeft.x) * fitRect.width()
        val browLy = fitRect.top + (rig.leftEyebrowPeak?.y ?: (rig.eyeLeft.y - rig.headBox.height * 0.11f)) * fitRect.height()
        val browRx = fitRect.left + (rig.rightEyebrowPeak?.x ?: rig.eyeRight.x) * fitRect.width()
        val browRy = fitRect.top + (rig.rightEyebrowPeak?.y ?: (rig.eyeRight.y - rig.headBox.height * 0.11f)) * fitRect.height()

        // 3-shape audio band blend + high-pass lip corner spread & low-pass jaw drop
        val bandWidthFactor = (1.0f * pose.wideOpenWeight + 0.78f * pose.roundWeight + 1.15f * pose.narrowWideWeight)
        val widthFactor = (bandWidthFactor + pose.lipCornerSpread * 0.14f).coerceIn(0.74f, 1.28f)
        val heightFactor = (1.12f * pose.wideOpenWeight + 0.94f * pose.roundWeight + 0.64f * pose.narrowWideWeight)

        val maxDropPx = when (rig.mouthType) {
            MouthType.BEAK -> min(mouthWidth * 0.40f, headRect.height() * 0.105f).coerceAtLeast(10f)
            MouthType.CARTOON_LIPS -> min(mouthWidth * 0.44f, headRect.height() * 0.115f).coerceAtLeast(12f)
            MouthType.JAW -> min(mouthWidth * 0.34f, headRect.height() * 0.088f).coerceAtLeast(10f)
        }
        val combinedOpen = max(pose.mouthOpen * 0.45f + pose.jawDrop * 0.55f, pose.mouthOpen * 0.85f)
        val dropPx = (maxDropPx * combinedOpen * heightFactor).coerceAtLeast(0f)
        val upperLiftPx = (maxDropPx * (if (rig.mouthType == MouthType.CARTOON_LIPS) 0.18f else 0.09f) * pose.upperLipLift)
            .coerceAtLeast(0f)
        val browLiftPx = (headRect.height() * 0.020f * pose.eyebrowLift).coerceAtLeast(0f)

        // 3. Build unified mesh vertices (body breathing + landmark-mapped mouth/eyelid/brow articulation + head rotation)
        val headRad = Math.toRadians(pose.headAngleDeg.toDouble())
        val cosH = cos(headRad).toFloat()
        val sinH = sin(headRad).toFloat()
        val swayDx = pose.headSwayX * fitRect.width()
        val bobDy = pose.headBobY * fitRect.height()

        val headRx = (headRect.width() * 0.58f).coerceAtLeast(24f)
        val headRy = (headRect.height() * 0.58f).coerceAtLeast(24f)
        val jawInfluenceRx = mouthWidth * 0.96f
        val jawInfluenceRy = max(max(chinY - mouthCy, mouthBottomY - mouthCy), mouthWidth * 0.86f)

        val mCos = cos(-mouthAngleRad)
        val mSin = sin(-mouthAngleRad)
        val mCosInv = cos(mouthAngleRad)
        val mSinInv = sin(mouthAngleRad)

        var idx = 0
        for (row in 0..meshRows) {
            val v = row.toFloat() / meshRows
            val baseY = fitRect.top + v * fitRect.height()
            for (col in 0..meshCols) {
                val u = col.toFloat() / meshCols
                val baseX = fitRect.left + u * fitRect.width()

                // Compute smooth head weight (1 inside head ellipse, smoothly 0 outside & below neck)
                val normDx = (baseX - headRect.centerX()) / headRx
                val normDy = (baseY - headRect.centerY()) / headRy
                val ellDist = sqrt(normDx * normDx + normDy * normDy)
                val radialW = 1f - smoothstep(0.55f, 1.15f, ellDist)
                val neckFade = 1f - smoothstep(neckY - headRect.height() * 0.08f, neckY + headRect.height() * 0.18f, baseY)
                val headW = (radialW * neckFade).coerceIn(0f, 1f)

                // Subtle body breathing on non-head vertices
                val bodyW = 1f - headW
                val breathX = fitRect.centerX() + (baseX - fitRect.centerX()) * (1f + (pose.breathScale - 1f) * 0.55f * bodyW)
                val breathY = fitRect.bottom + (baseY - fitRect.bottom) * (1f + (pose.breathScale - 1f) * bodyW)

                var preJawX = breathX
                var preJawY = breathY

                if (headW > 0.05f) {
                    // 3a. Mouth landmarks warp (jawDrop -> mouthBottom/chinBottom, lipCornerSpread -> corners, upperLipLift -> upperLipCenter)
                    if (dropPx > 0.2f || upperLiftPx > 0.15f || kotlin.math.abs(pose.lipCornerSpread) > 0.03f) {
                        val dxM = baseX - mouthCx
                        val dyM = baseY - mouthCy
                        val localX = dxM * mCos - dyM * mSin
                        val localY = dxM * mSin + dyM * mCos

                        val horizFalloff = 1f - smoothstep(mouthWidth * 0.34f, jawInfluenceRx, kotlin.math.abs(localX))
                        if (horizFalloff > 0f) {
                            var jawShiftLocalY = 0f
                            var jawShiftLocalX = 0f
                            if (localY >= -mouthWidth * 0.015f) {
                                // Lower jaw / chin moves down smoothly below lip parting line and tapers cleanly before neck
                                val enter = smoothstep(-mouthWidth * 0.015f, mouthWidth * 0.12f, localY)
                                val exit = 1f - smoothstep(jawInfluenceRy * 0.48f, jawInfluenceRy * 1.12f, localY)
                                val jawW = enter * exit * horizFalloff
                                jawShiftLocalY = dropPx * 0.90f * jawW
                                jawShiftLocalX = localX * ((widthFactor - 1f) * 0.10f + pose.lipCornerSpread * 0.05f) *
                                    max(combinedOpen, 0.25f) * jawW
                            } else if (localY > -mouthWidth * 0.24f) {
                                // Subtle upper-lip articulation anchored at upperLipCenter (never smudges mustache/nose)
                                val upSpan = max(mouthCy - upperLipY, mouthWidth * 0.14f)
                                val upW = (1f - smoothstep(0f, upSpan * 1.35f, -localY)) * horizFalloff
                                jawShiftLocalY = -upperLiftPx * upW
                                jawShiftLocalX = localX * ((widthFactor - 1f) * 0.08f + pose.lipCornerSpread * 0.05f) *
                                    max(combinedOpen, 0.25f) * upW
                            }

                            if (jawShiftLocalY != 0f || jawShiftLocalX != 0f) {
                                preJawX += jawShiftLocalX * mCosInv - jawShiftLocalY * mSinInv
                                preJawY += jawShiftLocalX * mSinInv + jawShiftLocalY * mCosInv
                            }
                        }
                    }

                    // 3b. Subtle upper-eyelid mesh tug during periodic blink
                    if (pose.blinkLeft > 0.05f) {
                        val dEx = (baseX - eyeLx) / (eyeLRx * 1.35f)
                        val dEy = (baseY - (eyeLy - eyeLRy * 0.35f)) / (eyeLRy * 1.15f)
                        val d2 = dEx * dEx + dEy * dEy
                        if (d2 < 1f) {
                            val w = (1f - d2) * pose.blinkLeft
                            preJawY += eyeLRy * 0.42f * w
                        }
                    }
                    if (!rig.useSingleEye && pose.blinkRight > 0.05f) {
                        val dEx = (baseX - eyeRx) / (eyeRRx * 1.35f)
                        val dEy = (baseY - (eyeRy - eyeRRy * 0.35f)) / (eyeRRy * 1.15f)
                        val d2 = dEx * dEx + dEy * dEy
                        if (d2 < 1f) {
                            val w = (1f - d2) * pose.blinkRight
                            preJawY += eyeRRy * 0.42f * w
                        }
                    }

                    // 3c. Eyebrow peak lift on vocal emphasis & crisp high-pass syllables
                    if (browLiftPx > 0.15f) {
                        val bRadX = eyeLRx * 1.5f
                        val bRadY = eyeLRy * 1.35f
                        val dBxL = (baseX - browLx) / bRadX
                        val dByL = (baseY - browLy) / bRadY
                        val d2L = dBxL * dBxL + dByL * dByL
                        if (d2L < 1f) {
                            preJawY -= browLiftPx * (1f - d2L)
                        }
                        if (!rig.useSingleEye) {
                            val dBxR = (baseX - browRx) / (eyeRRx * 1.5f)
                            val dByR = (baseY - browRy) / (eyeRRy * 1.35f)
                            val d2R = dBxR * dBxR + dByR * dByR
                            if (d2R < 1f) {
                                preJawY -= browLiftPx * (1f - d2R)
                            }
                        }
                    }
                }

                // Apply head rotation around neckPivot + sway/bob weighted by headW
                val rxPivot = preJawX - neckX
                val ryPivot = preJawY - neckY
                val rotX = neckX + rxPivot * cosH - ryPivot * sinH + swayDx
                val rotY = neckY + rxPivot * sinH + ryPivot * cosH + bobDy

                val finalX = preJawX * (1f - headW) + rotX * headW
                val finalY = preJawY * (1f - headW) + rotY * headW

                meshVerts[idx++] = finalX
                meshVerts[idx++] = finalY
            }
        }

        // Draw unified deformed character mesh (ZERO ghosting!)
        canvas.drawBitmapMesh(sourceBitmap, meshCols, meshRows, meshVerts, 0, null, 0, bitmapPaint)

        // 4. Overlay animated mouth cavity & eyelids inside the exact head-transformed space
        canvas.save()
        canvas.translate(swayDx, bobDy)
        canvas.rotate(pose.headAngleDeg, neckX, neckY)

        drawEyes(
            canvas = canvas,
            fitRect = fitRect,
            eyeLx = eyeLx, eyeLy = eyeLy, eyeLRx = eyeLRx, eyeLRy = eyeLRy,
            eyeRx = eyeRx, eyeRy = eyeRy, eyeRRx = eyeRRx, eyeRRy = eyeRRy,
            browLx = browLx, browLy = browLy, browRx = browRx, browRy = browRy,
            rig = rig,
            pose = pose
        )
        if (dropPx > 1.0f) {
            drawMouthCavity(
                canvas = canvas,
                mouthCx = mouthCx,
                mouthCy = mouthCy,
                mouthWidth = mouthWidth,
                mouthAngleDeg = mouthAngleDeg,
                dropPx = dropPx,
                upperLiftPx = upperLiftPx,
                widthFactor = widthFactor,
                rig = rig,
                pose = pose
            )
        }

        canvas.restore()
        canvas.restore() // end emphasis zoom
    }

    private fun drawEyes(
        canvas: Canvas,
        fitRect: RectF,
        eyeLx: Float, eyeLy: Float, eyeLRx: Float, eyeLRy: Float,
        eyeRx: Float, eyeRy: Float, eyeRRx: Float, eyeRRy: Float,
        browLx: Float, browLy: Float, browRx: Float, browRy: Float,
        rig: FaceRig,
        pose: AnimatorPose
    ) {
        drawSingleEye(canvas, fitRect, eyeLx, eyeLy, eyeLRx, eyeLRy, browLx, browLy, pose.blinkLeft, rig, pose)
        if (!rig.useSingleEye) {
            drawSingleEye(canvas, fitRect, eyeRx, eyeRy, eyeRRx, eyeRRy, browRx, browRy, pose.blinkRight, rig, pose)
        }
    }

    private fun drawSingleEye(
        canvas: Canvas,
        fitRect: RectF,
        ex: Float,
        ey: Float,
        eyeRx: Float,
        eyeRy: Float,
        browX: Float,
        browY: Float,
        blinkAmount: Float,
        rig: FaceRig,
        pose: AnimatorPose
    ) {
        // Loud-syllable upper brow / lid highlight anchored to eyebrow landmark
        if (pose.eyebrowLift > 0.14f) {
            strokePaint.color = Color.argb((pose.eyebrowLift * 55).toInt().coerceIn(0, 65), 255, 255, 255)
            strokePaint.strokeWidth = (eyeRy * 0.24f).coerceAtLeast(1.5f)
            val arcRect = RectF(
                browX - eyeRx * 1.10f,
                browY - eyeRy * (0.42f + pose.eyebrowLift * 0.30f),
                browX + eyeRx * 1.10f,
                browY + eyeRy * 0.85f
            )
            canvas.drawArc(arcRect, 200f, 140f, false, strokePaint)
        }

        if (blinkAmount > 0.02f) {
            val eyelidColor = rig.eyelidColorArgb
            val lidAlpha = (235 * blinkAmount.coerceIn(0.15f, 1f)).toInt()

            if (eyelidColor != null) {
                val r = Color.red(eyelidColor)
                val g = Color.green(eyelidColor)
                val b = Color.blue(eyelidColor)

                // Upper eyelid descends smoothly, lower eyelid rises slightly to meet it
                val fullHeight = eyeRy * 2.15f
                val upperLidBottom = (ey - eyeRy * 1.05f) + fullHeight * 0.85f * blinkAmount
                val upperLidRect = RectF(
                    ex - eyeRx * 1.15f,
                    ey - eyeRy * 1.25f,
                    ex + eyeRx * 1.15f,
                    upperLidBottom
                )
                solidPaint.shader = RadialGradient(
                    ex,
                    ey,
                    eyeRx * 1.25f,
                    intArrayOf(
                        Color.argb(255, r, g, b),
                        Color.argb(245, r, g, b),
                        Color.argb(0, r, g, b)
                    ),
                    floatArrayOf(0f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawOval(upperLidRect, solidPaint)

                // Subtle lower-lid rise
                val lowerLidTop = (ey + eyeRy * 1.05f) - fullHeight * 0.22f * blinkAmount
                val lowerLidRect = RectF(
                    ex - eyeRx * 1.05f,
                    lowerLidTop,
                    ex + eyeRx * 1.05f,
                    ey + eyeRy * 1.25f
                )
                canvas.drawOval(lowerLidRect, solidPaint)
                solidPaint.shader = null

                // Crisp dark lash closure line near peak blink
                if (blinkAmount > 0.38f) {
                    strokePaint.color = Color.argb(lidAlpha, 24, 16, 18)
                    strokePaint.strokeWidth = (eyeRy * 0.22f).coerceAtLeast(1.8f)
                    val creaseY = (upperLidBottom + lowerLidTop) * 0.5f
                    val creaseRect = RectF(
                        ex - eyeRx * 0.90f,
                        creaseY - eyeRy * 0.30f,
                        ex + eyeRx * 0.90f,
                        creaseY + eyeRy * 0.24f
                    )
                    canvas.drawArc(creaseRect, 15f, 150f, false, strokePaint)
                }
            } else {
                // Fallback vertical squash if eyelid sampling failed
                val squashScale = (1f - blinkAmount * 0.82f).coerceAtLeast(0.14f)
                val eyeBox = RectF(ex - eyeRx, ey - eyeRy, ex + eyeRx, ey + eyeRy)
                canvas.save()
                canvas.clipRect(eyeBox)
                canvas.scale(1f, squashScale, ex, ey)
                canvas.drawBitmap(sourceBitmap, null, fitRect, bitmapPaint)
                canvas.restore()
            }
        }
    }

    private fun drawMouthCavity(
        canvas: Canvas,
        mouthCx: Float,
        mouthCy: Float,
        mouthWidth: Float,
        mouthAngleDeg: Float,
        dropPx: Float,
        upperLiftPx: Float,
        widthFactor: Float,
        rig: FaceRig,
        pose: AnimatorPose
    ) {
        // Pinched anatomical lip-parting path anchored at mouth corners (never paints an oval over mustache/upper lip)
        val cavityHalfW = (mouthWidth * 0.44f * widthFactor).coerceAtLeast(7f)
        val upperPeakOffset = if (rig.mouthType == MouthType.CARTOON_LIPS) {
            dropPx * 0.14f + upperLiftPx * 0.65f
        } else {
            upperLiftPx * 0.32f
        }
        val cavityTop = mouthCy - upperPeakOffset
        val cavityBottom = mouthCy + dropPx * 0.92f

        canvas.save()
        canvas.rotate(mouthAngleDeg, mouthCx, mouthCy)

        val cavityPath = Path().apply {
            val leftX = mouthCx - cavityHalfW
            val rightX = mouthCx + cavityHalfW
            moveTo(leftX, mouthCy)
            if (rig.mouthType == MouthType.BEAK) {
                quadTo(mouthCx, mouthCy - upperLiftPx * 0.35f, rightX, mouthCy)
                quadTo(mouthCx, cavityBottom + dropPx * 0.12f, leftX, mouthCy)
            } else {
                // Upper lip inner arch: pinched smoothly at corners
                cubicTo(
                    mouthCx - cavityHalfW * 0.48f, cavityTop,
                    mouthCx + cavityHalfW * 0.48f, cavityTop,
                    rightX, mouthCy
                )
                // Lower lip inner parabola: deeper at center, smoothly meeting corners
                cubicTo(
                    mouthCx + cavityHalfW * 0.56f, cavityBottom,
                    mouthCx - cavityHalfW * 0.56f, cavityBottom,
                    leftX, mouthCy
                )
            }
            close()
        }

        if (rig.mouthType == MouthType.BEAK) {
            val beakColor = rig.beakInnerColorArgb ?: Color.rgb(55, 32, 28)
            solidPaint.shader = LinearGradient(
                mouthCx, cavityTop, mouthCx, cavityBottom,
                intArrayOf(darkenColor(beakColor, 0.48f), beakColor, darkenColor(beakColor, 0.72f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(cavityPath, solidPaint)
            solidPaint.shader = null
        } else {
            // Deep shaded interior cavity with soft edge falloff
            val midY = (cavityTop + cavityBottom) * 0.5f
            solidPaint.shader = RadialGradient(
                mouthCx,
                midY,
                max(cavityHalfW, dropPx * 0.8f),
                intArrayOf(
                    Color.rgb(16, 5, 8),
                    Color.rgb(26, 8, 13),
                    Color.argb(145, 34, 12, 18)
                ),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(cavityPath, solidPaint)
            solidPaint.shader = null

            if (rig.mouthInteriorStyle != MouthInteriorStyle.MINIMAL_DARK && dropPx > 4.5f) {
                canvas.save()
                canvas.clipPath(cavityPath)

                // Recessed upper teeth band shaded by upper lip shadow (avoids stark white cutout look)
                val teethH = (dropPx * 0.25f).coerceAtMost(mouthWidth * 0.095f)
                val isCartoon = rig.mouthInteriorStyle == MouthInteriorStyle.CARTOON
                val topToothColor = if (isCartoon) Color.argb(240, 245, 245, 250) else Color.argb(165, 208, 198, 188)
                val bottomToothColor = if (isCartoon) Color.argb(225, 228, 228, 238) else Color.argb(120, 168, 152, 144)
                solidPaint.shader = LinearGradient(
                    mouthCx, cavityTop, mouthCx, cavityTop + teethH,
                    intArrayOf(bottomToothColor, topToothColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                val teethRect = RectF(
                    mouthCx - cavityHalfW * 0.68f,
                    cavityTop - teethH * 0.15f,
                    mouthCx + cavityHalfW * 0.68f,
                    cavityTop + teethH
                )
                canvas.drawRoundRect(teethRect, 5f, 5f, solidPaint)
                solidPaint.shader = null

                // Recessed muted tongue inside lower cavity only when opened clearly
                if (dropPx > 7f && (pose.mouthOpen > 0.30f || pose.jawDrop > 0.30f)) {
                    val tongueCenterY = cavityBottom - dropPx * 0.10f
                    solidPaint.shader = RadialGradient(
                        mouthCx,
                        tongueCenterY,
                        cavityHalfW * 0.56f,
                        intArrayOf(
                            if (isCartoon) Color.argb(235, 190, 82, 96) else Color.argb(175, 128, 48, 58),
                            if (isCartoon) Color.argb(180, 150, 56, 70) else Color.argb(0, 80, 28, 36)
                        ),
                        floatArrayOf(0.15f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    val tongueRect = RectF(
                        mouthCx - cavityHalfW * 0.52f,
                        cavityBottom - dropPx * 0.36f,
                        mouthCx + cavityHalfW * 0.52f,
                        cavityBottom + dropPx * 0.16f
                    )
                    canvas.drawOval(tongueRect, solidPaint)
                    solidPaint.shader = null
                }
                canvas.restore()
            }

            // Whisper-thin soft lip crease shadow so edge blends organically
            strokePaint.color = Color.argb(
                if (rig.mouthType == MouthType.CARTOON_LIPS) 130 else 55,
                24,
                10,
                14
            )
            strokePaint.strokeWidth = (mouthWidth * 0.025f).coerceIn(1f, 2.5f)
            canvas.drawPath(cavityPath, strokePaint)
        }

        canvas.restore()
    }

    private fun drawBackground(
        canvas: Canvas,
        outW: Int,
        outH: Int,
        fitRect: RectF,
        fillMode: BackgroundFillMode,
        solidFillArgb: Int
    ) {
        if (fillMode == BackgroundFillMode.SOLID_COLOR) {
            canvas.drawColor(solidFillArgb)
            return
        }
        val bg = ensureBlurredBackground(outW, outH)
        canvas.drawBitmap(bg, 0f, 0f, bitmapPaint)

        // Studio dark vignette so blurred edges never distract from the subject
        solidPaint.shader = RadialGradient(
            outW * 0.5f,
            outH * 0.5f,
            max(outW, outH) * 0.72f,
            intArrayOf(
                Color.argb(80, 10, 13, 20),
                Color.argb(150, 8, 10, 16)
            ),
            floatArrayOf(0.25f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), solidPaint)
        solidPaint.shader = null

        // Subtle soft drop shadow behind the fitted foreground frame
        val pad = min(outW, outH) * 0.015f
        solidPaint.color = Color.argb(90, 4, 6, 12)
        canvas.drawRoundRect(
            fitRect.left - pad,
            fitRect.top - pad,
            fitRect.right + pad,
            fitRect.bottom + pad,
            pad * 1.4f,
            pad * 1.4f,
            solidPaint
        )
    }

    private fun ensureBlurredBackground(outW: Int, outH: Int): Bitmap {
        val existing = blurredBgCache
        if (existing != null && !existing.isRecycled && cachedBgW == outW && cachedBgH == outH) {
            return existing
        }
        // Multi-stage pyramid blur (160 -> 48 -> 18 -> 48 -> outW) yields silky-smooth bokeh with zero pixel blocks
        val s1W = 160
        val s1H = (160f * outH / outW).toInt().coerceIn(90, 280)
        val stage1 = Bitmap.createBitmap(s1W, s1H, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(stage1)
        c1.drawColor(Color.rgb(12, 16, 26))
        val cropRect = computeCenterCropRect(
            sourceBitmap.width.toFloat(),
            sourceBitmap.height.toFloat(),
            s1W.toFloat(),
            s1H.toFloat(),
            zoomMultiplier = 1.32f
        )
        c1.drawBitmap(sourceBitmap, null, cropRect, bitmapPaint)

        val stage2 = Bitmap.createScaledBitmap(stage1, 48, (48f * outH / outW).toInt().coerceIn(27, 86), true)
        stage1.recycle()
        val stage3 = Bitmap.createScaledBitmap(stage2, 18, (18f * outH / outW).toInt().coerceIn(10, 32), true)
        stage2.recycle()
        val stage4 = Bitmap.createScaledBitmap(stage3, 64, (64f * outH / outW).toInt().coerceIn(36, 114), true)
        stage3.recycle()

        val full = Bitmap.createScaledBitmap(stage4, outW, outH, true)
        stage4.recycle()
        existing?.recycle()
        blurredBgCache = full
        cachedBgW = outW
        cachedBgH = outH
        return full
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun computeFitRect(srcW: Float, srcH: Float, dstW: Float, dstH: Float): RectF {
        val scale = min(dstW / srcW, dstH / srcH)
        val w = srcW * scale
        val h = srcH * scale
        val left = (dstW - w) * 0.5f
        val top = (dstH - h) * 0.5f
        return RectF(left, top, left + w, top + h)
    }

    private fun computeCenterCropRect(
        srcW: Float,
        srcH: Float,
        dstW: Float,
        dstH: Float,
        zoomMultiplier: Float = 1f
    ): RectF {
        val scale = max(dstW / srcW, dstH / srcH) * zoomMultiplier
        val w = srcW * scale
        val h = srcH * scale
        val left = (dstW - w) * 0.5f
        val top = (dstH - h) * 0.5f
        return RectF(left, top, left + w, top + h)
    }

    private fun darkenColor(c: Int, factor: Float): Int = Color.rgb(
        (Color.red(c) * factor).toInt().coerceIn(0, 255),
        (Color.green(c) * factor).toInt().coerceIn(0, 255),
        (Color.blue(c) * factor).toInt().coerceIn(0, 255)
    )

    fun release() {
        blurredBgCache?.recycle()
        blurredBgCache = null
    }
}
