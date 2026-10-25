package io.github.aristidisg.ensemble.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.domain.FeedbackRecorder
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.model.CheckInPayload
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Thermal
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val wear: WearRepository,
    private val wardrobe: WardrobeRepository,
    private val recorder: FeedbackRecorder,
    private val studio: Studio,
    private val time: TimeSource,
) : ViewModel() {
    private val _log = MutableStateFlow<Pair<WearLog, Map<String, Item>>?>(null)
    val log = _log.asStateFlow()

    fun load(id: String?) = viewModelScope.launch {
        val logs = wear.all()
        val l = (if (id != null) logs.firstOrNull { it.id == id } else null) ?: logs.filter { it.epochDay == time.today() }.maxByOrNull { it.epochDay }
        _log.value = l?.let { it to wardrobe.all().associateBy { i -> i.id } }
    }

    fun submit(p: CheckInPayload, done: () -> Unit) = viewModelScope.launch {
        val l = _log.value?.first ?: return@launch
        val ctx = studio.context(l.epochDay, studio.scenario(l.scenarioId), l.weather)
        recorder.checkIn(l, p, ctx)
        done()
    }
}

/** Evening check-in: comfort, temperature, how it felt, a note. */
@Composable
fun CheckInScreen(logId: String?, nav: NavHostController, vm: CheckInViewModel = hiltViewModel()) {
    androidx.compose.runtime.LaunchedEffect(logId) { vm.load(logId) }
    val data by vm.log.collectAsStateWithLifecycle()
    var comfort by remember { mutableIntStateOf(4) }
    var felt by remember { mutableIntStateOf(4) }
    var thermal by remember { mutableStateOf(Thermal.OK) }
    var note by remember { mutableStateOf("") }
    Column {
        BackBar(stringResource(R.string.checkin_title), onBack = { nav.popBackStack() })
        val d = data
        if (d == null) { EmptyState(stringResource(R.string.no_history)); return@Column }
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Collage(d.first.pieces.mapNotNull { p -> d.second[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().height(260.dp))
            Text(stringResource(R.string.comfort), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Scale(comfort) { comfort = it }
            Text(stringResource(R.string.temperature), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Thermal.TOO_COLD to R.string.too_cold, Thermal.OK to R.string.just_right, Thermal.TOO_WARM to R.string.too_warm).forEach { (t, label) ->
                    FilterChip(selected = thermal == t, onClick = { thermal = t }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.how_it_felt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Scale(felt) { felt = it }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Button(onClick = { vm.submit(CheckInPayload(comfort, thermal, felt, note)) { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun Scale(value: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { v -> FilterChip(selected = value == v, onClick = { onChange(v) }, label = { Text("$v") }) }
    }
}
