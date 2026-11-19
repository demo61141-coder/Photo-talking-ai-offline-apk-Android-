package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.data.TalkingProject
import com.example.engine.ColorSampler
import com.example.engine.FaceRigAutoDetector
import com.example.model.FaceRig
import com.example.model.MouthInteriorStyle
import com.example.model.MouthType
import com.example.model.NormPoint
import com.example.model.NormRect
import com.example.model.SubjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RigStep {
    MOUTH_TYPE,
    MOUTH_LINE,
    EYES,
    HEAD_BOX,
    TEST
}

enum class AutoDetectBanner {
    NONE,
    DETECTED,
    FALLBACK_MANUAL
}

data class RigEditorUiState(
    val isLoading: Boolean = true,
    val isDetecting: Boolean = false,
    val isTilePass: Boolean = false,
    val project: TalkingProject? = null,
    val bitmap: Bitmap? = null,
    val rig: FaceRig = FaceRig(),
    val currentStep: RigStep = RigStep.MOUTH_TYPE,
    val testMouthOpen: Float = 0f,
    val banner: AutoDetectBanner = AutoDetectBanner.NONE,
    val errorMessage: String? = null
)

class RigEditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RigEditorUiState())
    val uiState: StateFlow<RigEditorUiState> = _uiState.asStateFlow()

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _uiState.value = RigEditorUiState(isLoading = true)
            val proj = repository.getProject(projectId)
            if (proj == null) {
                _uiState.value = RigEditorUiState(isLoading = false, errorMessage = "Project not found")
                return@launch
            }
            val bmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(proj.imagePath)
            }
            if (bmp == null) {
                _uiState.value = RigEditorUiState(isLoading = false, errorMessage = "Image decode failed")
                return@launch
            }
            _uiState.value = RigEditorUiState(
                isLoading = false,
                project = proj,
                bitmap = bmp,
                rig = proj.rig,
                banner = if (proj.rig.autoDetected) AutoDetectBanner.DETECTED else AutoDetectBanner.NONE
            )

            // Automatically run face detection on brand-new un-rigged projects
            if (!proj.rig.autoDetected && proj.rig.eyelidColorArgb == null) {
                runAutoDetect()
            }
        }
    }

    fun runAutoDetect() {
        val bmp = _uiState.value.bitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetecting = true, isTilePass = false)
            val result = FaceRigAutoDetector.detect(bmp) { tilePass ->
                _uiState.value = _uiState.value.copy(isTilePass = tilePass)
            }
            _uiState.value = _uiState.value.copy(
                isDetecting = false,
                isTilePass = false,
                rig = result.rig,
                banner = if (result.detected) AutoDetectBanner.DETECTED else AutoDetectBanner.FALLBACK_MANUAL,
                currentStep = if (result.detected) RigStep.TEST else RigStep.MOUTH_TYPE
            )
        }
    }

    fun setStep(step: RigStep) {
        _uiState.value = _uiState.value.copy(currentStep = step)
    }

    fun setSubjectType(type: SubjectType) {
        val defaultMouth = when (type) {
            SubjectType.BIRD -> MouthType.BEAK
            SubjectType.CARTOON -> MouthType.CARTOON_LIPS
            else -> MouthType.JAW
        }
        val singleEye = type == SubjectType.BIRD
        updateRig {
            it.copy(
                subjectType = type,
                mouthType = defaultMouth,
                useSingleEye = singleEye
            )
        }
    }

    fun setMouthType(type: MouthType) {
        updateRig { it.copy(mouthType = type) }
    }

    fun setMouthInteriorStyle(style: MouthInteriorStyle) {
        updateRig { it.copy(mouthInteriorStyle = style) }
    }

    fun setUseSingleEye(single: Boolean) {
        updateRig { it.copy(useSingleEye = single) }
    }

    fun updateMouthCorners(left: NormPoint, right: NormPoint) {
        val bmp = _uiState.value.bitmap
        val beakColor = if (bmp != null && _uiState.value.rig.mouthType == MouthType.BEAK) {
            ColorSampler.sampleBeakInnerColor(bmp, left, right)
        } else _uiState.value.rig.beakInnerColorArgb
        updateRig {
            it.copy(
                mouthLeft = left.clamped(),
                mouthRight = right.clamped(),
                mouthBottom = NormPoint(
                    ((left.x + right.x) * 0.5f),
                    ((left.y + right.y) * 0.5f + 0.05f).coerceAtMost(0.98f)
                ),
                beakInnerColorArgb = beakColor
            )
        }
    }

    fun updateEyes(left: NormPoint, right: NormPoint) {
        val bmp = _uiState.value.bitmap
        val sampled = bmp?.let {
            ColorSampler.sampleEyelidColor(it, left, if (_uiState.value.rig.useSingleEye) null else right)
        }
        updateRig {
            it.copy(
                eyeLeft = left.clamped(),
                eyeRight = right.clamped(),
                eyelidColorArgb = sampled
            )
        }
    }

    fun updateHeadAndNeck(headBox: NormRect, neckPivot: NormPoint) {
        updateRig {
            it.copy(
                headBox = headBox.normalized(),
                neckPivot = neckPivot.clamped()
            )
        }
    }

    fun setTestMouthOpen(value: Float) {
        _uiState.value = _uiState.value.copy(testMouthOpen = value.coerceIn(0f, 1f))
    }

    fun saveRig(onSaved: () -> Unit) {
        val proj = _uiState.value.project ?: return
        val rig = _uiState.value.rig
        viewModelScope.launch {
            val bmp = _uiState.value.bitmap
            val refreshedRig = if (bmp != null) {
                val eyeColor = ColorSampler.sampleEyelidColor(
                    bmp,
                    rig.eyeLeft,
                    if (rig.useSingleEye) null else rig.eyeRight
                )
                val beakColor = if (rig.mouthType == MouthType.BEAK) {
                    ColorSampler.sampleBeakInnerColor(bmp, rig.mouthLeft, rig.mouthRight)
                } else rig.beakInnerColorArgb
                rig.copy(eyelidColorArgb = eyeColor, beakInnerColorArgb = beakColor)
            } else rig
            repository.saveProject(proj.copy(rig = refreshedRig))
            onSaved()
        }
    }

    private fun updateRig(transform: (FaceRig) -> FaceRig) {
        val next = transform(_uiState.value.rig)
        _uiState.value = _uiState.value.copy(rig = next)
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.bitmap?.recycle()
    }

    class Factory(
        private val projectId: String,
        private val repository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RigEditorViewModel(projectId, repository) as T
    }
}
