package com.example.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.model.FaceRig
import com.example.model.MouthInteriorStyle
import com.example.model.MouthType
import com.example.model.NormPoint
import com.example.model.NormRect
import com.example.model.NormSize
import com.example.model.SubjectType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class AutoDetectResult(
    val detected: Boolean,
    val usedTiledCrop: Boolean,
    val rig: FaceRig
)

object FaceRigAutoDetector {

    private data class TileSpec(
        val leftFrac: Float,
        val topFrac: Float,
        val rightFrac: Float,
        val bottomFrac: Float,
        val upscale: Float
    )

    private val upperBodyTiles = listOf(
        TileSpec(0.0f, 0.0f, 1.0f, 0.55f, 1.8f),
        TileSpec(0.24f, 0.0f, 0.76f, 0.45f, 2.4f),
        TileSpec(0.0f, 0.0f, 0.52f, 0.48f, 2.2f),
        TileSpec(0.48f, 0.0f, 1.0f, 0.48f, 2.2f),
        TileSpec(0.20f, 0.12f, 0.80f, 0.65f, 2.0f)
    )

    suspend fun detect(
        bitmap: Bitmap,
        onStageUpdate: (isTilePass: Boolean) -> Unit = {}
    ): AutoDetectResult = withContext(Dispatchers.Default) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build()

        val detector = FaceDetection.getClient(options)

        try {
            // Pass 1: Full image
            onStageUpdate(false)
            val fullFaces = detectOnBitmap(detector, bitmap)
            val bestFull = pickBestFace(fullFaces)
            if (bestFull != null) {
                val rig = buildRigFromFace(
                    face = bestFull,
                    cropLeftPx = 0f,
                    cropTopPx = 0f,
                    scaleBackX = 1f,
                    scaleBackY = 1f,
                    fullW = bitmap.width.toFloat(),
                    fullH = bitmap.height.toFloat(),
                    originalBitmap = bitmap
                )
                return@withContext AutoDetectResult(detected = true, usedTiledCrop = false, rig = rig)
            }

            // Pass 2: Zoomed upper-region tiles for full-body or distant portraits
            onStageUpdate(true)
            for (tile in upperBodyTiles) {
                val cropRect = Rect(
                    (tile.leftFrac * bitmap.width).toInt().coerceIn(0, bitmap.width - 2),
                    (tile.topFrac * bitmap.height).toInt().coerceIn(0, bitmap.height - 2),
                    (tile.rightFrac * bitmap.width).toInt().coerceIn(2, bitmap.width),
                    (tile.bottomFrac * bitmap.height).toInt().coerceIn(2, bitmap.height)
                )
                if (cropRect.width() < 40 || cropRect.height() < 40) continue

                val cropped = Bitmap.createBitmap(
                    bitmap,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height()
                )
                val targetW = (cropped.width * tile.upscale).toInt().coerceAtMost(1440)
                val targetH = (cropped.height * tile.upscale).toInt().coerceAtMost(1440)
                val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                if (scaled !== cropped) cropped.recycle()

                val tileFaces = detectOnBitmap(detector, scaled)
                val bestTile = pickBestFace(tileFaces)
                val scaleBackX = cropRect.width().toFloat() / scaled.width.toFloat()
                val scaleBackY = cropRect.height().toFloat() / scaled.height.toFloat()
                scaled.recycle()

                if (bestTile != null) {
                    val rig = buildRigFromFace(
                        face = bestTile,
                        cropLeftPx = cropRect.left.toFloat(),
                        cropTopPx = cropRect.top.toFloat(),
                        scaleBackX = scaleBackX,
                        scaleBackY = scaleBackY,
                        fullW = bitmap.width.toFloat(),
                        fullH = bitmap.height.toFloat(),
                        originalBitmap = bitmap
                    )
                    return@withContext AutoDetectResult(detected = true, usedTiledCrop = true, rig = rig)
                }
            }

            // Fallback default rig centered for manual adjustment
            val defaultRig = FaceRig(
                subjectType = SubjectType.ANIMAL,
                mouthType = MouthType.JAW,
                autoDetected = false
            )
            val sampledEye = ColorSampler.sampleEyelidColor(bitmap, defaultRig.eyeLeft, defaultRig.eyeRight)
            AutoDetectResult(
                detected = false,
                usedTiledCrop = true,
                rig = defaultRig.copy(eyelidColorArgb = sampledEye)
            )
        } finally {
            runCatching { detector.close() }
        }
    }

    private suspend fun detectOnBitmap(
        detector: com.google.mlkit.vision.face.FaceDetector,
        bitmap: Bitmap
    ): List<Face> = suspendCancellableCoroutine { cont ->
        val input = InputImage.fromBitmap(bitmap, 0)
        detector.process(input)
            .addOnSuccessListener { faces ->
                if (cont.isActive) cont.resume(faces ?: emptyList())
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    private fun pickBestFace(faces: List<Face>): Face? {
        if (faces.isEmpty()) return null
        return faces.maxByOrNull { f ->
            val area = f.boundingBox.width() * f.boundingBox.height()
            val hasMouth = if (f.getLandmark(FaceLandmark.MOUTH_LEFT) != null &&
                f.getLandmark(FaceLandmark.MOUTH_RIGHT) != null
            ) 2 else 1
            area * hasMouth
        }
    }

    private fun buildRigFromFace(
        face: Face,
        cropLeftPx: Float,
        cropTopPx: Float,
        scaleBackX: Float,
        scaleBackY: Float,
        fullW: Float,
        fullH: Float,
        originalBitmap: Bitmap
    ): FaceRig {
        fun mapPt(px: Float, py: Float): NormPoint = NormPoint(
            x = (cropLeftPx + px * scaleBackX) / fullW,
            y = (cropTopPx + py * scaleBackY) / fullH
        ).clamped()

        val box = face.boundingBox
        val boxTopLeft = mapPt(box.left.toFloat(), box.top.toFloat())
        val boxBottomRight = mapPt(box.right.toFloat(), box.bottom.toFloat())
        val bw = (boxBottomRight.x - boxTopLeft.x).coerceAtLeast(0.06f)
        val bh = (boxBottomRight.y - boxTopLeft.y).coerceAtLeast(0.06f)

        val headBox = NormRect(
            left = boxTopLeft.x - bw * 0.18f,
            top = boxTopLeft.y - bh * 0.25f,
            right = boxBottomRight.x + bw * 0.18f,
            bottom = boxBottomRight.y + bh * 0.14f
        ).normalized()

        val leftEyeLm = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyeLm = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val mouthLeftLm = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRightLm = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottomLm = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

        // Note: ML Kit LEFT_EYE is subject's left eye (viewer's right), swap so eyeLeft is viewer-left
        val eyeA = leftEyeLm?.let { mapPt(it.x, it.y) }
            ?: NormPoint(headBox.left + headBox.width * 0.65f, headBox.top + headBox.height * 0.42f)
        val eyeB = rightEyeLm?.let { mapPt(it.x, it.y) }
            ?: NormPoint(headBox.left + headBox.width * 0.35f, headBox.top + headBox.height * 0.42f)

        val viewerLeftEye = if (eyeB.x <= eyeA.x) eyeB else eyeA
        val viewerRightEye = if (eyeB.x <= eyeA.x) eyeA else eyeB

        fun contourSize(contourType: Int): NormSize? {
            val pts = face.getContour(contourType)?.points
            if (pts.isNullOrEmpty()) return null
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in pts) {
                val mp = mapPt(p.x, p.y)
                if (mp.x < minX) minX = mp.x
                if (mp.x > maxX) maxX = mp.x
                if (mp.y < minY) minY = mp.y
                if (mp.y > maxY) maxY = mp.y
            }
            val w = (maxX - minX).coerceAtLeast(0.015f)
            val h = (maxY - minY).coerceAtLeast(0.008f)
            return NormSize(w, h)
        }

        fun contourCenter(contourType: Int): NormPoint? {
            val pts = face.getContour(contourType)?.points
            if (pts.isNullOrEmpty()) return null
            var sx = 0f
            var sy = 0f
            for (p in pts) {
                val mp = mapPt(p.x, p.y)
                sx += mp.x
                sy += mp.y
            }
            return NormPoint(sx / pts.size, sy / pts.size).clamped()
        }

        // Swap ML Kit LEFT/RIGHT contours to match viewer-left / viewer-right eyes & eyebrows
        val mlLeftEyeSize = contourSize(FaceContour.LEFT_EYE)
        val mlRightEyeSize = contourSize(FaceContour.RIGHT_EYE)
        val leftEyeContourSize = if (eyeB.x <= eyeA.x) mlRightEyeSize else mlLeftEyeSize
        val rightEyeContourSize = if (eyeB.x <= eyeA.x) mlLeftEyeSize else mlRightEyeSize

        val mlLeftBrow = contourCenter(FaceContour.LEFT_EYEBROW_TOP)
        val mlRightBrow = contourCenter(FaceContour.RIGHT_EYEBROW_TOP)
        val viewerLeftBrow = if (eyeB.x <= eyeA.x) mlRightBrow else mlLeftBrow
        val viewerRightBrow = if (eyeB.x <= eyeA.x) mlLeftBrow else mlRightBrow

        val mA = mouthLeftLm?.let { mapPt(it.x, it.y) }
            ?: NormPoint(headBox.left + headBox.width * 0.36f, headBox.top + headBox.height * 0.72f)
        val mB = mouthRightLm?.let { mapPt(it.x, it.y) }
            ?: NormPoint(headBox.left + headBox.width * 0.64f, headBox.top + headBox.height * 0.72f)

        val viewerMouthLeft = if (mA.x <= mB.x) mA else mB
        val viewerMouthRight = if (mA.x <= mB.x) mB else mA
        val mouthBottom = contourCenter(FaceContour.LOWER_LIP_BOTTOM)
            ?: mouthBottomLm?.let { mapPt(it.x, it.y) }
            ?: NormPoint(
                x = (viewerMouthLeft.x + viewerMouthRight.x) * 0.5f,
                y = ((viewerMouthLeft.y + viewerMouthRight.y) * 0.5f + bh * 0.10f).coerceAtMost(0.97f)
            )
        val upperLipCenter = contourCenter(FaceContour.UPPER_LIP_TOP)
            ?: NormPoint(
                x = (viewerMouthLeft.x + viewerMouthRight.x) * 0.5f,
                y = ((viewerMouthLeft.y + viewerMouthRight.y) * 0.5f - bh * 0.04f).coerceAtLeast(0.02f)
            )
        val chinBottom = face.getContour(FaceContour.FACE)?.points
            ?.maxByOrNull { it.y }
            ?.let { mapPt(it.x, it.y) }

        val neckPivot = NormPoint(
            x = headBox.centerX,
            y = (headBox.bottom + bh * 0.08f).coerceAtMost(0.97f)
        )

        val eyelidColor = ColorSampler.sampleEyelidColor(originalBitmap, viewerLeftEye, viewerRightEye)

        return FaceRig(
            subjectType = SubjectType.HUMAN,
            mouthType = MouthType.JAW,
            mouthInteriorStyle = MouthInteriorStyle.REALISTIC,
            mouthLeft = viewerMouthLeft,
            mouthRight = viewerMouthRight,
            mouthBottom = mouthBottom,
            upperLipCenter = upperLipCenter,
            chinBottom = chinBottom,
            eyeLeft = viewerLeftEye,
            eyeRight = viewerRightEye,
            leftEyeSize = leftEyeContourSize,
            rightEyeSize = rightEyeContourSize,
            leftEyebrowPeak = viewerLeftBrow,
            rightEyebrowPeak = viewerRightBrow,
            useSingleEye = false,
            headBox = headBox,
            neckPivot = neckPivot,
            eyelidColorArgb = eyelidColor,
            autoDetected = true
        )
    }
}
