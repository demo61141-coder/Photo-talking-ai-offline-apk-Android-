package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.data.SettingsDataStore
import com.example.data.TalkingProject
import com.example.engine.AudioDecoderAndAnalyzer
import com.example.engine.MicVoiceRecorder
import com.example.engine.Mp4VideoExporter
import com.example.engine.PhoneTtsGenerator
import com.example.engine.TtsLocaleOption
import com.example.model.AudioAnalysisTrack
import com.example.model.BackgroundFillMode
import com.example.model.ExportSettings
import com.example.model.StudioSettings
import com.example.model.VideoAspectRatio
import com.example.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

data class StudioUiState(
    val isLoading: Boolean = true,
    val project: TalkingProject? = null,
    val bitmap: Bitmap? = null,
    val audioTrack: AudioAnalysisTrack? = null,
    val isAnalyzingAudio: Boolean = false,
    val isRecordingMic: Boolean = false,
    val micLevel: Float = 0f,
    val micElapsedMs: Long = 0L,
    val micPermissionDenied: Boolean = false,
    val isPlayingPreview: Boolean = false,
    val previewTimeMs: Long = 0L,
    val exportSettings: ExportSettings = ExportSettings(),
    val studioSettings: StudioSettings = StudioSettings(),
    val showTtsSheet: Boolean = false,
    val ttsLocales: List<TtsLocaleOption> = emptyList(),
    val isGeneratingTts: Boolean = false,
    val ttsErrorMessage: String? = null,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val errorMessage: String? = null
)

class StudioViewModel(
    private val appContext: Context,
    private val projectId: String,
    private val repository: ProjectRepository,
    private val settingsStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val micRecorder = MicVoiceRecorder()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val initialSettings = settingsStore.settingsFlow.first()
            _uiState.value = _uiState.value.copy(
                studioSettings = initialSettings,
                exportSettings = ExportSettings(
                    resolution = initialSettings.defaultResolution,
                    aspectRatio = initialSettings.defaultAspectRatio,
                    fps = initialSettings.defaultFps,
                    fillMode = initialSettings.defaultFillMode
                )
            )
            loadProjectAndAudio()
        }

        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                _uiState.value = _uiState.value.copy(studioSettings = s)
            }
        }

        viewModelScope.launch {
            micRecorder.liveLevel.collect { lvl ->
                _uiState.value = _uiState.value.copy(micLevel = lvl)
            }
        }
        viewModelScope.launch {
            micRecorder.elapsedMs.collect { ms ->
                _uiState.value = _uiState.value.copy(micElapsedMs = ms)
            }
        }
    }

    fun refreshProject() {
        viewModelScope.launch {
            val proj = repository.getProject(projectId) ?: return@launch
            _uiState.value = _uiState.value.copy(project = proj)
        }
    }

    private suspend fun loadProjectAndAudio() {
        val proj = repository.getProject(projectId)
        if (proj == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Project missing")
            return
        }
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(proj.imagePath) }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            project = proj,
            bitmap = bmp
        )
        val audioPath = proj.audioPath
        if (audioPath != null && File(audioPath).exists()) {
            analyzeAudioPath(audioPath, proj.audioDisplayName ?: "Voice Track")
        }
    }

    fun importAudioUri(uri: Uri) {
        val proj = _uiState.value.project ?: return
        stopPreviewPlayback()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzingAudio = true, errorMessage = null)
            repository.importAudioForProject(proj.id, uri)
                .onSuccess { (path, name) ->
                    val updated = proj.copy(audioPath = path, audioDisplayName = name)
                    repository.saveProject(updated)
                    _uiState.value = _uiState.value.copy(project = updated)
                    analyzeAudioPath(path, name)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzingAudio = false,
                        errorMessage = it.message ?: "Audio import failed"
                    )
                }
        }
    }

    private suspend fun analyzeAudioPath(path: String, displayName: String) {
        _uiState.value = _uiState.value.copy(isAnalyzingAudio = true, errorMessage = null)
        val fps = _uiState.value.exportSettings.fps
        AudioDecoderAndAnalyzer.analyzeFile(path, displayName, fps)
            .onSuccess { track ->
                _uiState.value = _uiState.value.copy(
                    isAnalyzingAudio = false,
                    audioTrack = track,
                    previewTimeMs = 0L
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingAudio = false,
                    errorMessage = it.message ?: "Audio analysis failed"
                )
            }
    }

    fun onMicPermissionDenied() {
        _uiState.value = _uiState.value.copy(micPermissionDenied = true)
    }

    fun clearMicPermissionDenied() {
        _uiState.value = _uiState.value.copy(micPermissionDenied = false)
    }

    fun startMicRecording() {
        val proj = _uiState.value.project ?: return
        stopPreviewPlayback()
        val dest = repository.newRecordedAudioFile(proj.id)
        _uiState.value = _uiState.value.copy(isRecordingMic = true, micPermissionDenied = false, errorMessage = null)
        micRecorder.start(viewModelScope, dest) { err ->
            _uiState.value = _uiState.value.copy(
                isRecordingMic = false,
                errorMessage = err.message ?: "Mic error"
            )
        }
    }

    fun stopMicRecording() {
        val proj = _uiState.value.project ?: return
        viewModelScope.launch {
            val file = micRecorder.stop()
            _uiState.value = _uiState.value.copy(isRecordingMic = false)
            if (file != null) {
                val updated = proj.copy(
                    audioPath = file.absolutePath,
                    audioDisplayName = "Mic Recording (${(_uiState.value.micElapsedMs / 1000).coerceAtLeast(1)}s)"
                )
                repository.saveProject(updated)
                _uiState.value = _uiState.value.copy(project = updated)
                analyzeAudioPath(file.absolutePath, updated.audioDisplayName ?: "Voice")
            }
        }
    }

    fun togglePreviewPlayback() {
        if (_uiState.value.isPlayingPreview) {
            stopPreviewPlayback()
        } else {
            startPreviewPlayback()
        }
    }

    private fun startPreviewPlayback() {
        val track = _uiState.value.audioTrack ?: return
        stopPreviewPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(track.filePath)
                prepare()
                setOnCompletionListener {
                    stopPreviewPlayback()
                }
                start()
            }
            mediaPlayer = mp
            _uiState.value = _uiState.value.copy(isPlayingPreview = true, previewTimeMs = 0L)
            playbackJob = viewModelScope.launch {
                var smoothPosMs = 0L
                var lastWallMs = SystemClock.elapsedRealtime()
                while (isActive && _uiState.value.isPlayingPreview) {
                    val nowWall = SystemClock.elapsedRealtime()
                    val dt = (nowWall - lastWallMs).coerceIn(0L, 64L)
                    lastWallMs = nowWall
                    smoothPosMs += dt

                    val mpPos = runCatching { mp.currentPosition.toLong() }.getOrDefault(smoothPosMs)
                    if (abs(mpPos - smoothPosMs) > 90L) {
                        smoothPosMs = mpPos
                    }
                    _uiState.value = _uiState.value.copy(previewTimeMs = smoothPosMs)
                    delay(16L)
                }
            }
        } catch (t: Throwable) {
            _uiState.value = _uiState.value.copy(errorMessage = t.message)
        }
    }

    fun openTtsSheet() {
        stopPreviewPlayback()
        _uiState.value = _uiState.value.copy(showTtsSheet = true, ttsErrorMessage = null)
        if (_uiState.value.ttsLocales.isEmpty()) {
            viewModelScope.launch {
                val locales = PhoneTtsGenerator.loadAvailableLocales(appContext)
                _uiState.value = _uiState.value.copy(ttsLocales = locales)
            }
        }
    }

    fun dismissTtsSheet() {
        if (!_uiState.value.isGeneratingTts) {
            _uiState.value = _uiState.value.copy(showTtsSheet = false, ttsErrorMessage = null)
        }
    }

    fun generateTtsAudio(text: String, locale: Locale, speed: Float, pitch: Float) {
        val proj = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingTts = true, ttsErrorMessage = null)
            val dest = repository.newRecordedAudioFile(proj.id)
            PhoneTtsGenerator.synthesizeToWav(
                context = appContext,
                text = text,
                locale = locale,
                speechRate = speed,
                pitch = pitch,
                destFile = dest
            ).onSuccess { wavFile ->
                val label = "TTS (${locale.language.uppercase()}): ${text.trim().take(18)}"
                val updated = proj.copy(audioPath = wavFile.absolutePath, audioDisplayName = label)
                repository.saveProject(updated)
                _uiState.value = _uiState.value.copy(
                    project = updated,
                    isGeneratingTts = false,
                    showTtsSheet = false
                )
                analyzeAudioPath(wavFile.absolutePath, label)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingTts = false,
                    ttsErrorMessage = err.message ?: "TTS generation failed"
                )
            }
        }
    }

    fun stopPreviewPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _uiState.value = _uiState.value.copy(isPlayingPreview = false, previewTimeMs = 0L)
    }

    fun updateStudioTuning(transform: (StudioSettings) -> StudioSettings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    fun updateResolution(res: VideoResolution) {
        _uiState.value = _uiState.value.copy(exportSettings = _uiState.value.exportSettings.copy(resolution = res))
    }

    fun updateAspectRatio(ratio: VideoAspectRatio) {
        _uiState.value = _uiState.value.copy(exportSettings = _uiState.value.exportSettings.copy(aspectRatio = ratio))
    }

    fun updateFillMode(mode: BackgroundFillMode) {
        _uiState.value = _uiState.value.copy(exportSettings = _uiState.value.exportSettings.copy(fillMode = mode))
    }

    fun updateFps(fps: Int) {
        val prevFps = _uiState.value.exportSettings.fps
        if (prevFps == fps) return
        _uiState.value = _uiState.value.copy(exportSettings = _uiState.value.exportSettings.copy(fps = fps))
        val track = _uiState.value.audioTrack
        if (track != null) {
            viewModelScope.launch { analyzeAudioPath(track.filePath, track.displayName) }
        }
    }

    fun startExport(onExported: (String) -> Unit) {
        val state = _uiState.value
        val proj = state.project ?: return
        val bmp = state.bitmap ?: return
        val track = state.audioTrack ?: return
        if (state.isExporting) return

        stopPreviewPlayback()
        _uiState.value = state.copy(isExporting = true, exportProgress = 0.02f, errorMessage = null)
        exportJob = viewModelScope.launch {
            Mp4VideoExporter.exportMp4(
                context = appContext,
                projectId = proj.id,
                sourceBitmap = bmp,
                rig = proj.rig,
                audioTrack = track,
                exportSettings = state.exportSettings,
                studioSettings = state.studioSettings,
                onProgress = { p ->
                    _uiState.value = _uiState.value.copy(exportProgress = p)
                }
            ).onSuccess { outFile ->
                repository.saveProject(proj.copy(lastExportPath = outFile.absolutePath))
                _uiState.value = _uiState.value.copy(isExporting = false, exportProgress = 1f)
                onExported(outFile.absolutePath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportProgress = 0f,
                    errorMessage = err.message ?: "Export failed"
                )
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.value = _uiState.value.copy(isExporting = false, exportProgress = 0f)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopPreviewPlayback()
        exportJob?.cancel()
        _uiState.value.bitmap?.recycle()
    }

    class Factory(
        private val appContext: Context,
        private val projectId: String,
        private val repository: ProjectRepository,
        private val settingsStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudioViewModel(appContext, projectId, repository, settingsStore) as T
    }
}
