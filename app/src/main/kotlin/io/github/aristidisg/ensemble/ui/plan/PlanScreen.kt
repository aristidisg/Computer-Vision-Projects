package io.github.aristidisg.ensemble.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.nav.LaundryRoute
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.ui.nav.TripRoute
import io.github.aristidisg.ensemble.ui.today.weatherIcon
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlanScreen(nav: NavHostController, vm: PlanViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(stringResource(R.string.tab_plan), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (s.planning) CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
            else Button(onClick = vm::autoPlan) { Text(stringResource(R.string.auto_plan)) }
        }
        val scores = s.days.mapNotNull { it.plan?.score }
        if (scores.isNotEmpty()) Text(stringResource(R.string.plan_score, (scores.average() * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
        if (s.replanned) Text(stringResource(R.string.replanned), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { vm.dismissReplanned() })
        s.conflicts.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        s.days.forEach { d -> DayRow(d, s, vm, nav) }

        SectionTitle(stringResource(R.string.trips))
        s.trips.forEach { t ->
            OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { nav.navigate(TripRoute(t.id)) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    Text("${LocalDate.ofEpochDay(t.startEpochDay)} → ${LocalDate.ofEpochDay(t.endEpochDay)} · ${t.packedItemIds.size} pieces", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (s.trips.isEmpty()) Text(stringResource(R.string.no_trips), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { nav.navigate(TripRoute()) }) { Text(stringResource(R.string.new_trip)) }

        SectionTitle(stringResource(R.string.laundry))
        OutlinedCard(Modifier.fillMaxWidth().clickable { nav.navigate(LaundryRoute) }) {
            Text("${s.laundryCount} · ${stringResource(R.string.laundry)}", Modifier.padding(16.dp))
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun DayRow(d: PlanDay, s: PlanState, vm: PlanViewModel, nav: NavHostController) {
    var scenarioMenu by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(d.epochDay)
    val selected = s.swapFrom == d.epochDay
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp)) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + date.dayOfMonth, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(d.weather.code), null, Modifier.width(16.dp))
                    Text(" ${d.weather.feelsMin.roundToInt()}–${d.weather.feelsMax.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    Text(d.scenario.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { scenarioMenu = true }, maxLines = 2)
                    DropdownMenu(scenarioMenu, { scenarioMenu = false }) {
                        s.scenarios.forEach { sc -> DropdownMenuItem(text = { Text(sc.name) }, onClick = { scenarioMenu = false; vm.setScenario(d.epochDay, sc.id) }) }
                    }
                }
            }
            val p = d.plan
            if (p != null && p.pieces.isNotEmpty()) {
                Collage(p.pieces.mapNotNull { x -> s.items[x.itemId]?.let { it to x.slot } },
                    Modifier.weight(1f).height(110.dp).clickable { nav.navigate(OutfitDetailRoute(PiecesArg.encode(p.pieces), p.scenarioId, d.epochDay)) })
            } else Text(stringResource(R.string.unplanned), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Column {
                if (p != null) IconButton(onClick = { vm.toggleLock(d.epochDay) }) {
                    Icon(if (p.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, stringResource(if (p.locked) R.string.unlock_day else R.string.lock_day))
                }
                IconButton(onClick = { vm.swapTap(d.epochDay) }) { Icon(Icons.Outlined.SwapVert, stringResource(R.string.swap_with)) }
            }
        }
    }
}
