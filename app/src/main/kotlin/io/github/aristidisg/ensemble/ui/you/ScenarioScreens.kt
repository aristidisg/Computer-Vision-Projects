@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.aristidisg.ensemble.data.repo.RuleRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.engine.tables.ScenarioPresets
import io.github.aristidisg.ensemble.model.Activity
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.ScenarioPalette
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.NavRow
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.fmt1
import io.github.aristidisg.ensemble.ui.nav.ScenarioEditorRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ScenariosViewModel @Inject constructor(private val repo: RuleRepository, private val settings: SettingsRepository) : ViewModel() {
    val scenarios = repo.scenarios.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScenarioPresets.all)
    val template = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(s: Scenario) = viewModelScope.launch { repo.saveScenario(s) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteScenario(id) }
    fun setWeekday(day: Int, id: String) = viewModelScope.launch { settings.update { it.copy(weeklyTemplate = it.weeklyTemplate + (day to id)) } }
}

@Composable
fun ScenariosScreen(nav: NavHostController, vm: ScenariosViewModel = hiltViewModel()) {
    val list by vm.scenarios.collectAsStateWithLifecycle()
    val settings by vm.template.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.scenarios), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.weekly_template))
            (1..7).forEach { d ->
                Text(DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(list, setOfNotNull(list.firstOrNull { it.id == settings?.weeklyTemplate?.get(d) }), { it.name }, { vm.setWeekday(d, it.id) }, scroll = true)
            }
            SectionTitle(stringResource(R.string.scenarios))
            list.forEach { s ->
                NavRow(s.name, stringResource(R.string.formality_band, s.formalityMin.fmt1(), s.formalityMax.fmt1(), s.formalityTarget.fmt1()) + " · " +
                    stringResource(R.string.window_hours, s.windowStartHour, s.windowEndHour % 24)) { nav.navigate(ScenarioEditorRoute(s.id)) }
            }
            Button(onClick = { nav.navigate(ScenarioEditorRoute()) }, modifier = Modifier.padding(vertical = 12.dp)) { Text(stringResource(R.string.new_scenario)) }
        }
    }
}

@Composable
fun ScenarioEditorScreen(id: String?, nav: NavHostController, vm: ScenariosViewModel = hiltViewModel()) {
    val list by vm.scenarios.collectAsStateWithLifecycle()
    var s by remember { mutableStateOf<Scenario?>(null) }
    LaunchedEffect(list, id) {
        if (s == null) s = list.firstOrNull { it.id == id }
            ?: if (id == null) Scenario(UUID.randomUUID().toString(), "", formalityMin = 2.0, formalityTarget = 4.0, formalityMax = 6.0,
                windowStartHour = 9, windowEndHour = 18, indoorShare = 0.5, activity = Activity.LIGHT, builtIn = false) else null
    }
    val sc = s ?: return
    Column {
        BackBar(sc.name.ifEmpty { stringResource(R.string.new_scenario) }, onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(sc.name, { s = sc.copy(name = it) }, label = { Text(stringResource(R.string.scenario_name)) }, modifier = Modifier.fillMaxWidth())
            SliderRow("Min", sc.formalityMin.toFloat(), 1f..10f, { s = sc.copy(formalityMin = (it * 2).roundToInt() / 2.0) }, { it.toDouble().fmt1() })
            SliderRow("Target", sc.formalityTarget.toFloat(), 1f..10f, { s = sc.copy(formalityTarget = (it * 2).roundToInt() / 2.0) }, { it.toDouble().fmt1() })
            SliderRow("Max", sc.formalityMax.toFloat(), 1f..10f, { s = sc.copy(formalityMax = (it * 2).roundToInt() / 2.0) }, { it.toDouble().fmt1() })
            SliderRow(stringResource(R.string.window_hours, sc.windowStartHour, sc.windowEndHour % 24), sc.windowStartHour.toFloat(), 0f..23f, { s = sc.copy(windowStartHour = it.roundToInt(), windowEndHour = maxOf(sc.windowEndHour, it.roundToInt() + 1)) }, { "${it.roundToInt()}:00" })
            SliderRow("→", sc.windowEndHour.toFloat(), 1f..30f, { s = sc.copy(windowEndHour = maxOf(it.roundToInt(), sc.windowStartHour + 1)) }, { "${it.roundToInt() % 24}:00" })
            SliderRow(stringResource(R.string.indoor_share, (sc.indoorShare * 100).roundToInt()), sc.indoorShare.toFloat(), 0f..1f, { s = sc.copy(indoorShare = (it * 20).roundToInt() / 20.0) }, { "${(it * 100).roundToInt()}%" })
            SectionTitle(stringResource(R.string.activity))
            ChoiceChips(Activity.entries, setOf(sc.activity), { it.name.lowercase() }, { s = sc.copy(activity = it) })
            SectionTitle(stringResource(R.string.palette))
            ChoiceChips(ScenarioPalette.entries, setOf(sc.palette), { it.name.lowercase().replace('_', ' ') }, { s = sc.copy(palette = it) }, scroll = true)
            SliderRow(stringResource(R.string.accessories_range, sc.accessoryMin, sc.accessoryMax), sc.accessoryMax.toFloat(), 0f..5f, { s = sc.copy(accessoryMax = it.roundToInt(), accessoryMin = minOf(sc.accessoryMin, it.roundToInt())) }, { "${it.roundToInt()}" }, steps = 4)
            FlowRow(Modifier.padding(vertical = 16.dp)) {
                OutlinedButton(onClick = { vm.delete(sc.id); nav.popBackStack() }) {
                    Text(stringResource(if (ScenarioPresets.byId(sc.id) != null) R.string.restore_default else R.string.delete))
                }
                Button(onClick = { vm.save(sc.copy(name = sc.name.ifBlank { "Custom" })); nav.popBackStack() }, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
