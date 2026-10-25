package io.github.aristidisg.ensemble.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** Null until DataStore has loaded, so the start destination never flickers. */
    val onboarded: StateFlow<Boolean?> = settings.settings.map { it.onboarded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
