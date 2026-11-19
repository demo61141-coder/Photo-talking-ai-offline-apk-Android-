package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.GalleryExporter
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnCyan
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.VioletContainer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OutputScreen(
    videoPath: String,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onBackToStudio: () -> Unit,
    onBackToHome: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoFile = remember(videoPath) { File(videoPath) }

    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { targetUri ->
        if (targetUri != null) {
            scope.launch {
                GalleryExporter.copyToUri(context, videoFile, targetUri)
                    .onSuccess {
                        isError = false
                        statusMessage = strings.downloadedSuccess
                    }
                    .onFailure {
                        isError = true
                        statusMessage = strings.errorSavingGallery
                    }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                title = strings.outputTitle,
                language = language,
                onToggleLanguage = onToggleLanguage,
                onBack = onBackToStudio
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldContainer)
                        .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.videoReadyBadge, style = MaterialTheme.typography.labelSmall, color = EmeraldAccent)
                }

                // Video Preview Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MidnightSurface)
                        .border(1.dp, MidnightBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoFile.exists()) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.fromFile(videoFile))
                                    setOnPreparedListener { mp: MediaPlayer ->
                                        mp.isLooping = true
                                        start()
                                        isPlaying = true
                                    }
                                    videoViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Button(
                            onClick = {
                                val vv = videoViewRef ?: return@Button
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianBg.copy(alpha = 0.8f),
                                contentColor = ElectricCyan
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .testTag("output_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) strings.pausePreview else strings.playPreview
                            )
                        }
                    } else {
                        Text(strings.errorExportingVideo, color = CoralError)
                    }
                }

                AnimatedVisibility(visible = statusMessage != null) {
                    Surface(
                        color = if (isError) CoralContainer else EmeraldContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) CoralError else EmeraldAccent,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Save to Gallery
                Button(
                    onClick = {
                        scope.launch {
                            GalleryExporter.saveToGallery(context, videoFile, "TalkingPhotoPro")
                                .onSuccess {
                                    isError = false
                                    statusMessage = strings.savedToGallerySuccess
                                }
                                .onFailure {
                                    isError = true
                                    statusMessage = strings.errorSavingGallery
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnCyan
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_to_gallery_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.saveToGallery, style = MaterialTheme.typography.titleSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val shareIntent = GalleryExporter.createShareIntent(
                                context,
                                videoFile,
                                strings.appTitle
                            )
                            context.startActivity(Intent.createChooser(shareIntent, strings.shareVideo))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VioletContainer,
                            contentColor = StudioViolet
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_video_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.shareVideo)
                    }

                    OutlinedButton(
                        onClick = {
                            saveAsLauncher.launch("TalkingPhoto_${System.currentTimeMillis()}.mp4")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("download_copy_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.downloadCopy)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = onBackToStudio,
                        modifier = Modifier.testTag("output_back_studio_button")
                    ) {
                        Text(strings.backToStudio, color = ElectricCyan)
                    }
                    TextButton(
                        onClick = onBackToHome,
                        modifier = Modifier.testTag("output_back_home_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = StudioViolet, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.backToHome, color = StudioViolet)
                    }
                }
            }
        }
    }
}
