package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GalleryExporter {

    suspend fun saveToGallery(context: Context, videoFile: File, displayTitle: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(videoFile.exists()) { "Video file does not exist" }
                val cleanName = displayTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "TalkingPhoto" }
                val fileName = "${cleanName}_${System.currentTimeMillis()}.mp4"

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/TalkingPhotoPro")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { out ->
                    videoFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Failed to open gallery output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            }
        }

    suspend fun copyToUri(context: Context, videoFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    videoFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Cannot write destination")
                Unit
            }
        }

    fun createShareIntent(context: Context, videoFile: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
