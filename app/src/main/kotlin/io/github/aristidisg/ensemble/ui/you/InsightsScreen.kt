package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.planner.GapFinder
import io.github.aristidisg.ensemble.engine.planner.GapSuggestion
import io.github.aristidisg.ensemble.engine.planner.Orphan
import io.github.aristidisg.ensemble.engine.planner.UsageContext
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.WindowWeather
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.hexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InsightsState(
    val palette: List<Pair<ColorFamily, Int>> = emptyList(),
    val wears: List<Pair<Item, Int>> = emptyList(),
    val unworn60: List<Item> = emptyList(),
    val unworn90: List<Item> = emptyList(),
    val coverage: List<Triple<String, String, Int>> = emptyList(),
    val total: Int? = null,
    val gaps: List<GapSuggestion> = emptyList(),
    val orphans: List<Orphan> = emptyList(),
    val computing: Boolean = false,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val wear: WearRepository,
    private val studio: Studio,
    private val time: TimeSource,
) : ViewModel() {
    private val _s = MutableStateFlow(InsightsState())
    val state = _s.asStateFlow()
    private val bands = listOf("Cold 2-8 °C" to WindowWeather(2.0, 8.0), "Cool 10-16 °C" to WindowWeather(10.0, 16.0), "Mild 17-23 °C" to WindowWeather(17.0, 23.0), "Hot 26-32 °C" to WindowWeather(26.0, 32.0))

    init {
        viewModelScope.launch {
            val items = wardrobe.all(); val logs = wear.all(); val today = time.today()
            val counts = logs.flatMap { l -> l.pieces.map { it.itemId } }.groupingBy { it }.eachCount()
            val last = logs.flatMap { l -> l.pieces.map { it.itemId to l.epochDay } }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            fun unworn(d: Int) = items.filter { it.isAvailable && today - (last[it.id] ?: it.createdEpochDay) >= d }
            _s.update {
                it.copy(
                    palette = items.mapNotNull { i -> i.primaryColor?.family }.groupingBy { f -> f }.eachCount().toList().sortedByDescending { p -> p.second },
                    wears = items.map { i -> i to (counts[i.id] ?: 0) }.sortedByDescending { p -> p.second },
                    unworn60 = unworn(60), unworn90 = unworn(90),
                )
            }
        }
    }

    /** Scenarios you actually use (from the wear log and weekly template), weighted by use. */
    private suspend fun usage(): List<UsageContext> {
        val logs = wear.all()
        val byScenario = logs.groupingBy { it.scenarioId }.eachCount()
        val ids = (byScenario.keys + listOf("casual", "office", "university")).distinct().take(4)
        return ids.flatMap { id ->
            val sc = studio.scenario(id)
            bands.map { (label, w) -> UsageContext(sc, w, (byScenario[id] ?: 1).toDouble(), "${sc.name} · $label") }
        }
    }

    fun compute() = viewModelScope.launch {
        _s.update { it.copy(computing = true) }
        val day = time.today()
        val base = studio.context(day, studio.scenario("casual"))
        val items = base.items.values.toList()
        val use = usage()
        val result = withContext(Dispatchers.Default) {
            val finder = GapFinder()
            val coverage = use.map { u ->
                val ctx = base.copy(scenario = u.scenario, weather = u.weather)
                Triple(u.scenario.name, u.label.substringAfter("· "), studio.engine.candidates(ctx, items).count { it.total >= 0.7 })
            }
            val total = finder.totalOutfits(base.copy(weather = WindowWeather(17.0, 23.0)), items)
            val focus = use.filter { it.label.contains("Cool") || it.label.contains("Mild") }
            Triple(coverage, total, finder.findGaps(base, items, focus, 8) to finder.orphans(base, items, focus.take(3)))
        }
        _s.update { it.copy(coverage = result.first, total = result.second, gaps = result.third.first, orphans = result.third.second, computing = false) }
    }
}

@Composable
fun InsightsScreen(nav: NavHostController, vm: InsightsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.insights), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.palette_title))
            val total = s.palette.sumOf { it.second }.coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                var x = 0f
                s.palette.forEach { (f, n) ->
                    val w = size.width * n / total
                    drawRect(hexColor(if (f == ColorFamily.MULTICOLOR) "#9E9E9E" else ColorTables.family(f).hex), Offset(x, 0f), Size(w, size.height))
                    x += w
                }
            }
            Text(s.palette.joinToString { "${it.first.label} ${it.second}" }, style = MaterialTheme.typography.bodySmall)
            SectionTitle(stringResource(R.string.wears))
            s.wears.take(8).forEach { (i, n) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.name, Modifier.width(180.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Text("$n" + (i.meta.price?.let { p -> " · " + "%.2f".format(p / maxOf(1, n)) } ?: ""), style = MaterialTheme.typography.bodySmall)
            } }
            SectionTitle(stringResource(R.string.unworn_60) + " (${s.unworn60.size})")
            Text(s.unworn60.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
            SectionTitle(stringResource(R.string.unworn_90) + " (${s.unworn90.size})")
            Text(s.unworn90.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)

            Button(onClick = vm::compute, enabled = !s.computing, modifier = Modifier.padding(vertical = 16.dp)) { Text(stringResource(R.string.run_gap_finder)) }
            if (s.computing) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text(stringResource(R.string.computing), Modifier.padding(start = 12.dp)) }
            s.total?.let { SectionTitle(stringResource(R.string.total_outfits)); Text("$it") }
            if (s.coverage.isNotEmpty()) {
                SectionTitle(stringResource(R.string.coverage))
                val max = s.coverage.maxOf { it.third }.coerceAtLeast(1)
                s.coverage.forEach { (sc, band, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("$sc · $band", Modifier.width(200.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        val shade = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.85f * n / max)
                        Canvas(Modifier.width(60.dp).height(16.dp)) { drawRect(shade) }
                        Text(" $n", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (s.gaps.isNotEmpty()) {
                SectionTitle(stringResource(R.string.gap_finder))
                s.gaps.forEach { g ->
                    Text(g.title, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.gap_line, g.unlocks, g.fills.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (s.orphans.isNotEmpty()) {
                SectionTitle(stringResource(R.string.orphans))
                s.orphans.forEach { o -> Text("${o.item.name} → ${o.pairsWith.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
