package io.github.aristidisg.ensemble.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.domain.WeatherService
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Strictness
import io.github.aristidisg.ensemble.work.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared by the simple settings screens: read the settings, write a transform. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val feedback: FeedbackRepository,
    private val weather: WeatherService,
    private val scheduler: Scheduler,
) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _cities = MutableStateFlow<List<LatLon>>(emptyList())
    val cities = _cities.asStateFlow()

    fun update(t: (Settings) -> Settings) = viewModelScope.launch { repo.update(t) }

    fun setStrictness(s: Strictness) = update { it.copy(strictness = s, boldness = s.boldness) }

    /** Changing the half-life or rejected cooldown replays the log so stats match the new setting. */
    fun updateLearning(t: (Settings) -> Settings) = viewModelScope.launch {
        repo.update(t)
        val s = repo.current()
        feedback.rebuild(s.learningEpoch, LearningConfig.of(s))
    }

    fun updateSchedule(t: (Settings) -> Settings) = viewModelScope.launch { repo.update(t); scheduler.scheduleAll() }

    fun searchCity(q: String) = viewModelScope.launch { _cities.value = weather.search(q).getOrDefault(emptyList()) }

    fun pickCity(c: LatLon) = viewModelScope.launch {
        repo.update { s -> s.copy(savedCity = c, savedCities = (listOf(c) + s.savedCities.filter { it.name != c.name }).take(6)) }
        _cities.value = emptyList()
        weather.refresh(force = true)
    }

    fun refreshWeather() = viewModelScope.launch { weather.refresh(force = true) }
}
