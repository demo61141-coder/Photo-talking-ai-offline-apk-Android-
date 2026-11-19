package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.FaceRig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val dao: TalkingProjectDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val audioDir: File
        get() = File(context.filesDir, "audio").apply { mkdirs() }

    val allProjects: Flow<List<TalkingProject>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getProject(id: String): TalkingProject? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun saveProject(project: TalkingProject) = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        dao.upsert(updated.toEntity())
    }

    suspend fun deleteProject(project: TalkingProject) = withContext(Dispatchers.IO) {
        dao.deleteById(project.id)
        runCatching { File(project.imagePath).delete() }
        project.audioPath?.let { path ->
            if (path.startsWith(context.filesDir.absolutePath)) {
                runCatching { File(path).delete() }
            }
        }
    }

    /**
     * Imports a gallery photo (JPG/PNG/WebP), corrects EXIF orientation, scales gently to max 1600px
     * while preserving alpha transparency (PNG), and stores it in app internal filesDir.
     */
    suspend fun createProjectFromPhotoUri(uri: Uri): Result<TalkingProject> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val id = UUID.randomUUID().toString()
            val displayName = queryDisplayName(uri) ?: "Portrait ${id.take(4)}"
            val cleanTitle = displayName.substringBeforeLast('.').ifBlank { "Talking Photo" }

            // Read EXIF orientation if available
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            // Decode bounds first
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: error("Failed to decode image")

            val oriented = applyExifOrientation(rawBitmap, orientation)
            if (oriented !== rawBitmap) rawBitmap.recycle()

            val finalBitmap = scaleDownIfNeeded(oriented, 1440)
            if (finalBitmap !== oriented) oriented.recycle()

            val outFile = File(projectsDir, "$id.png")
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            finalBitmap.recycle()

            val project = TalkingProject(
                id = id,
                title = cleanTitle,
                imagePath = outFile.absolutePath,
                rig = FaceRig(),
                updatedAt = System.currentTimeMillis()
            )
            dao.upsert(project.toEntity())
            project
        }
    }

    suspend fun importAudioForProject(projectId: String, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val rawName = queryDisplayName(uri) ?: "voice_${System.currentTimeMillis()}.mp3"
                val ext = rawName.substringAfterLast('.', "mp3").lowercase()
                val dest = File(audioDir, "${projectId}_${System.currentTimeMillis()}.$ext")
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Cannot read audio file")
                dest.absolutePath to rawName
            }
        }

    fun newRecordedAudioFile(projectId: String): File =
        File(audioDir, "rec_${projectId}_${System.currentTimeMillis()}.wav")

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val maxCur = maxOf(bitmap.width, bitmap.height)
        if (maxCur <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / maxCur.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun TalkingProjectEntity.toDomain(): TalkingProject {
        val parsedRig = runCatching { json.decodeFromString<FaceRig>(rigJson) }.getOrDefault(FaceRig())
        return TalkingProject(
            id = id,
            title = title,
            imagePath = imagePath,
            audioPath = audioPath,
            audioDisplayName = audioDisplayName,
            lastExportPath = lastExportPath,
            rig = parsedRig,
            updatedAt = updatedAt
        )
    }

    private fun TalkingProject.toEntity(): TalkingProjectEntity = TalkingProjectEntity(
        id = id,
        title = title,
        imagePath = imagePath,
        audioPath = audioPath,
        audioDisplayName = audioDisplayName,
        lastExportPath = lastExportPath,
        rigJson = json.encodeToString(rig),
        updatedAt = updatedAt
    )
}
