package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.learning.PreferenceStats
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.fmt1
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasteRow(val key: String, val label: String, val value: Double)

data class TasteState(
    val groups: Map<String, Pair<List<TasteRow>, List<TasteRow>>> = emptyMap(),
    val stats: PreferenceStats = PreferenceStats.EMPTY,
    val settings: Settings = Settings(),
    val events: Int = 0,
)

@HiltViewModel
class TasteViewModel @Inject constructor(
    private val feedback: FeedbackRepository,
    private val settings: SettingsRepository,
    wardrobe: WardrobeRepository,
    time: TimeSource,
) : ViewModel() {
    val state = combine(feedback.stats, settings.settings, wardrobe.items, feedback.eventCount) { st, s, items, n ->
        val names = items.associate { it.id to it.name }
        val today = time.today()
        val global = st.stats.values.filter { it.contextKey == "GLOBAL" }.map { p ->
            val d = Learning.decay((today - p.lastUpdatedEpochDay).toDouble(), s.halfLifeDays)
            TasteRow(p.featureKey, label(p.featureKey, names), PreferenceSnapshot.value(p.pos * d, p.neg * d))
        }
        val groups = linkedMapOf(
            "Color pairs" to "fam:", "Pieces" to "item:", "Styles" to "style:", "Silhouettes" to "sil:", "Patterns" to "pattern:",
        ).mapValues { (_, prefix) ->
            val rows = global.filter { it.key.startsWith(prefix) }
            rows.filter { it.value > 0.05 }.sortedByDescending { it.value }.take(10) to rows.filter { it.value < -0.05 }.sortedBy { it.value }.take(10)
        }
        TasteState(groups, st, s, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasteState())

    private fun label(key: String, names: Map<String, String>): String {
        val body = key.substringAfter(':')
        return when (key.substringBefore(':')) {
            "item" -> names[body] ?: body
            "fam" -> body.split('|').joinToString(" + ") { runCatching { ColorFamily.valueOf(it).label }.getOrDefault(it) }
            else -> body.split('|').joinToString(" / ") { it.lowercase().replace('_', ' ') }
        }
    }

    private suspend fun cfg() = settings.current().let { LearningConfig.of(it) }

    fun forget(key: String) = viewModelScope.launch { feedback.forget(key) }
    fun rebuild() = viewModelScope.launch { feedback.rebuild(settings.current().learningEpoch, cfg()) }
    fun softReset() = viewModelScope.launch { settings.update { it.copy(learningEpoch = it.learningEpoch + 1) }; feedback.softReset() }
    fun hardReset() = viewModelScope.launch { feedback.hardReset() }
    fun setTrust(v: Double) = viewModelScope.launch { settings.update { it.copy(trustMyTaste = v) } }
    fun setHalfLife(v: Double) = viewModelScope.launch { settings.update { it.copy(halfLifeDays = v) }; feedback.rebuild(settings.current().learningEpoch, cfg()) }
}

@Composable
fun TasteScreen(nav: NavHostController, vm: TasteViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.taste_profile), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SliderRow(stringResource(R.string.trust_my_taste), s.settings.trustMyTaste.toFloat(), 0.10f..0.30f, { vm.setTrust(it.toDouble()) }, { "%.2f".format(it) })
            SliderRow(stringResource(R.string.half_life), s.settings.halfLifeDays.toFloat(), 30f..365f, { vm.setHalfLife(it.toDouble().let { d -> (d / 5).toInt() * 5.0 }) }, { "${it.toInt()}" })
            Text(stringResource(R.string.thermal_offset, (s.settings.thermalBias + s.stats.thermalOffset).fmt1()), style = MaterialTheme.typography.bodyLarge)
            if (s.stats.formalityOffset.isNotEmpty()) {
                SectionTitle(stringResource(R.string.formality_offset))
                s.stats.formalityOffset.forEach { (k, v) -> Text("$k: ${if (v > 0) "+" else ""}${v.fmt1()}") }
            }
            if (s.events == 0) EmptyState(stringResource(R.string.no_learning))
            s.groups.forEach { (title, pair) ->
                if (pair.first.isEmpty() && pair.second.isEmpty()) return@forEach
                SectionTitle(title)
                pair.first.forEach { TasteLine(it, vm, true) }
                pair.second.forEach { TasteLine(it, vm, false) }
            }
            SectionTitle(stringResource(R.string.reset_learning))
            OutlinedButton(onClick = vm::rebuild, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rebuild)) }
            OutlinedButton(onClick = vm::softReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_soft)) }
            OutlinedButton(onClick = vm::hardReset, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text(stringResource(R.string.reset_hard)) }
        }
    }
}

@Composable
private fun TasteLine(r: TasteRow, vm: TasteViewModel, liked: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text((if (liked) "▲ " else "▼ ") + r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("%+.2f".format(r.value), style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { vm.forget(r.key) }) { Text(stringResource(R.string.forget)) }
    }
}
