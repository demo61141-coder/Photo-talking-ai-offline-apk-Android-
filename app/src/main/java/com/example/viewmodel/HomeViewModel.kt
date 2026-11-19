package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.data.SettingsDataStore
import com.example.data.TalkingProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: ProjectRepository,
    private val settingsStore: SettingsDataStore
) : ViewModel() {

    val projects: StateFlow<List<TalkingProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun importPhoto(uri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isImporting = true)
            repository.createProjectFromPhotoUri(uri)
                .onSuccess { proj ->
                    _uiState.value = HomeUiState(isImporting = false)
                    onCreated(proj.id)
                }
                .onFailure {
                    _uiState.value = HomeUiState(
                        isImporting = false,
                        errorMessage = it.message ?: "error"
                    )
                }
        }
    }

    fun deleteProject(project: TalkingProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun toggleLanguage() {
        viewModelScope.launch { settingsStore.toggleLanguage() }
    }

    class Factory(
        private val repository: ProjectRepository,
        private val settingsStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository, settingsStore) as T
    }
}
