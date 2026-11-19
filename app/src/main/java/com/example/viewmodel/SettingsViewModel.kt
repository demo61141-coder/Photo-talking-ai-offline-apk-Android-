package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsDataStore
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    fun update(transform: (StudioSettings) -> StudioSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun resetDefaults() {
        viewModelScope.launch { settingsStore.resetDefaults() }
    }

    class Factory(
        private val settingsStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsStore) as T
    }
}
