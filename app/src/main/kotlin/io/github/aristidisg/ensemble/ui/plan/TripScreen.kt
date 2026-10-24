package io.github.aristidisg.ensemble.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.data.weather.WeatherRepository
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.WeatherWindow
import io.github.aristidisg.ensemble.engine.planner.DayContext
import io.github.aristidisg.ensemble.engine.planner.TripPacker
import io.github.aristidisg.ensemble.engine.planner.TripRequest
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.Trip
import io.github.aristidisg.ensemble.model.TripDay
import io.github.aristidisg.ensemble.model.WindowWeather
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class TripUi(val trip: Trip, val items: Map<String, Item> = emptyMap(), val results: List<LatLon> = emptyList(), val packing: Boolean = false)

@HiltViewModel
class TripViewModel @Inject constructor(
    private val plans: PlanRepository,
    private val weather: WeatherRepository,
    private val studio: Studio,
    private val time: TimeSource,
) : ViewModel() {
    private val _s = MutableStateFlow(TripUi(Trip(UUID.randomUUID().toString(), "", null, time.today() + 7, time.today() + 11, emptyList())))
    val state = _s.asStateFlow()

    fun load(id: String?) = viewModelScope.launch {
        val items = studio.items().associateBy { it.id }
        val t = id?.let { tid -> plans.allTrips().firstOrNull { it.id == tid } }
        _s.update { it.copy(trip = t ?: it.trip, items = items) }
    }

    fun edit(f: (Trip) -> Trip) = _s.update { it.copy(trip = f(it.trip)) }

    fun search(q: String) = viewModelScope.launch { _s.update { it.copy(results = weather.search(q).getOrDefault(emptyList())) } }

    fun pick(p: LatLon) = _s.update { it.copy(trip = it.trip.copy(destination = p, name = it.trip.name.ifEmpty { p.name.substringBefore(',') }), results = emptyList()) }

    fun pack() = viewModelScope.launch {
        _s.update { it.copy(packing = true) }
        val t = _s.value.trip
        val snap = t.destination?.let { weather.forecastFor(it, time.nowMillis()).getOrNull() }
        val days = (t.startEpochDay..t.endEpochDay).map { d ->
            val scId = t.days.firstOrNull { it.epochDay == d }?.scenarioId ?: if (d == t.startEpochDay || d == t.endEpochDay) "travel" else "casual"
            val sc = studio.scenario(scId)
            val w = snap?.let { WeatherWindow.aggregate(it, d, sc) }
                ?: WindowWeather(t.expectedMin ?: 14.0, t.expectedMax ?: 22.0, manual = true)
            DayContext(d, sc, w)
        }
        val base = studio.context(time.today(), days.first().scenario)
        val plan = withContext(Dispatchers.Default) {
            TripPacker().pack(TripRequest(days, t.luggageLimit, t.shoeLimit, t.laundryAvailable), base, base.items.values.toList())
        }
        val packed = t.copy(
            packedItemIds = plan.packed.map { it.id },
            days = plan.dayOutfits.map { TripDay(it.day.epochDay, it.day.scenario.id, it.outfit?.pieces ?: emptyList()) },
            travelDayItemIds = plan.travel.map { it.id },
        )
        plans.saveTrip(packed)
        _s.update { it.copy(trip = packed, packing = false) }
    }

    fun toggleChecked(id: String) = viewModelScope.launch {
        val t = _s.value.trip
        val updated = t.copy(checkedItemIds = if (id in t.checkedItemIds) t.checkedItemIds - id else t.checkedItemIds + id)
        plans.saveTrip(updated); _s.update { it.copy(trip = updated) }
    }

    fun delete(done: () -> Unit) = viewModelScope.launch { plans.deleteTrip(_s.value.trip.id); done() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(id: String?, nav: NavHostController, vm: TripViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val t = s.trip
    var query by remember { mutableStateOf("") }
    var dates by remember { mutableStateOf(false) }
    Column {
        BackBar(t.name.ifEmpty { stringResource(R.string.new_trip) }, onBack = { nav.popBackStack() }) {
            if (id != null) TextButton(onClick = { vm.delete { nav.popBackStack() } }) { Text(stringResource(R.string.delete)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(t.name, { v -> vm.edit { it.copy(name = v) } }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.destination)) }, modifier = Modifier.weight(1f), singleLine = true)
                TextButton(onClick = { vm.search(query) }) { Text(stringResource(R.string.search)) }
            }
            t.destination?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
            s.results.forEach { r -> TextButton(onClick = { vm.pick(r) }) { Text(r.name) } }
            OutlinedButton(onClick = { dates = true }) { Text("${stringResource(R.string.dates)}: ${LocalDate.ofEpochDay(t.startEpochDay)} → ${LocalDate.ofEpochDay(t.endEpochDay)}") }
            SliderRow(stringResource(R.string.luggage_limit), t.luggageLimit.toFloat(), 5f..30f, { v -> vm.edit { it.copy(luggageLimit = v.toInt()) } }, { "${it.toInt()}" })
            SliderRow(stringResource(R.string.shoe_limit), t.shoeLimit.toFloat(), 1f..5f, { v -> vm.edit { it.copy(shoeLimit = v.toInt()) } }, { "${it.toInt()}" }, steps = 3)
            SwitchRow(stringResource(R.string.laundry_available), t.laundryAvailable, { v -> vm.edit { it.copy(laundryAvailable = v) } })
            Text(stringResource(R.string.expected_temps), style = MaterialTheme.typography.bodySmall)
            SliderRow(stringResource(R.string.min_c), (t.expectedMin ?: 14.0).toFloat(), -10f..35f, { v -> vm.edit { it.copy(expectedMin = v.toDouble()) } }, { "${it.toInt()}" })
            SliderRow(stringResource(R.string.max_c), (t.expectedMax ?: 22.0).toFloat(), -5f..42f, { v -> vm.edit { it.copy(expectedMax = v.toDouble()) } }, { "${it.toInt()}" })
            Button(onClick = vm::pack, enabled = !s.packing, modifier = Modifier.fillMaxWidth()) {
                if (s.packing) CircularProgressIndicator(Modifier.height(18.dp)) else Text(stringResource(R.string.pack))
            }
            if (t.packedItemIds.isNotEmpty()) {
                SectionTitle(stringResource(R.string.packing_list) + " (${t.packedItemIds.size})")
                t.packedItemIds.forEach { pid ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pid in t.checkedItemIds, onCheckedChange = { vm.toggleChecked(pid) })
                        Text(s.items[pid]?.name ?: pid)
                    }
                }
                if (t.travelDayItemIds.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.wear_on_travel_day))
                    Text(t.travelDayItemIds.mapNotNull { s.items[it]?.name }.joinToString(), style = MaterialTheme.typography.bodyMedium)
                }
                SectionTitle(stringResource(R.string.day_plan))
                t.days.forEach { d ->
                    Text(LocalDate.ofEpochDay(d.epochDay).toString() + " · " + d.scenarioId, style = MaterialTheme.typography.labelLarge)
                    if (d.pieces.isNotEmpty()) Collage(d.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().height(160.dp).padding(vertical = 4.dp))
                }
            }
            Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.Center) {}
        }
    }
    if (dates) {
        val st = rememberDateRangePickerState(t.startEpochDay * 86_400_000L, t.endEpochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { dates = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis
                    if (a != null && b != null) vm.edit { it.copy(startEpochDay = a / 86_400_000L, endEpochDay = b / 86_400_000L, days = emptyList()) }
                    dates = false
                }) { Text(stringResource(R.string.done)) }
            },
        ) { DateRangePicker(st, Modifier.height(480.dp)) }
    }
}
