package io.github.aristidisg.ensemble.ui.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.explain
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(route: OutfitDetailRoute, nav: NavHostController, vm: OutfitViewModel = hiltViewModel()) {
    LaunchedEffect(route) { vm.load(route.pieces, route.scenarioId, route.epochDay) }
    val s by vm.state.collectAsStateWithLifecycle()
    var swapSlot by remember { mutableStateOf<Slot?>(null) }
    var planPicker by remember { mutableStateOf(false) }
    var debug by remember { mutableStateOf(false) }
    Column {
        BackBar(s.scenario?.name ?: "", onBack = { nav.popBackStack() })
        val o = s.scored ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Collage(o.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().height(320.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.match_pct, o.matchPct), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(o.harmony, style = MaterialTheme.typography.labelLarge)
            }
            s.cooldown?.let { Text(stringResource(R.string.cooldown_badge, it.toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            o.violation?.let { Text(stringResource(R.string.breaks_rule, it.name.lowercase().replace('_', ' ')), color = MaterialTheme.colorScheme.error) }
            o.pieces.forEach { p ->
                val item = s.items[p.itemId] ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemArt(item, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(LocalEnsembleColors.current.backdrop).padding(4.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        Text(p.slot.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { swapSlot = p.slot; vm.loadAlternatives(p.slot) }) { Text(stringResource(R.string.piece_swap)) }
                }
            }
            if (o.why.isNotEmpty()) {
                SectionTitle(stringResource(R.string.why_this_works))
                o.why.forEach { Text("• " + explain(it), style = MaterialTheme.typography.bodyMedium) }
            }
            if (o.tradeoffs.isNotEmpty()) {
                SectionTitle(stringResource(R.string.trade_offs))
                o.tradeoffs.forEach { Text("• " + explain(it), style = MaterialTheme.typography.bodyMedium) }
            }
            o.weatherLine?.let { SectionTitle(stringResource(R.string.weather_line)); Text(explain(it)) }
            o.sockHint?.let { Text(explain(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.wear { nav.popBackStack() } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wear_today)) }
                OutlinedButton(onClick = vm::save, modifier = Modifier.weight(1f)) { Text(stringResource(if (s.saved) R.string.saved else R.string.save_outfit)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { planPicker = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.plan_for)) }
                OutlinedButton(onClick = { vm.neverAgain { nav.popBackStack() } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.never_again)) }
            }
            s.plannedFor?.let { d ->
                Text("✓ " + stringResource(R.string.planned_for, java.time.LocalDate.ofEpochDay(d).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { debug = !debug }) { Text(stringResource(R.string.rule_scores)) }
            if (debug) {
                o.groupScores.forEach { (g, v) -> Text("${g.label}: ${"%.2f".format(v)}", style = MaterialTheme.typography.labelLarge) }
                HorizontalDivider()
                o.results.sortedBy { it.group.ordinal }.forEach { r ->
                    Text("${r.id} ${"%.2f".format(r.score)} ×${"%.2f".format(r.weight)}  ${r.render()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    swapSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { swapSlot = null },
            title = { Text(stringResource(R.string.swap_title, slot.label.lowercase())) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val alts = s.alternatives
                    if (alts == null) CircularProgressIndicator()
                    else if (alts.isEmpty()) Text(stringResource(R.string.no_alternatives))
                    alts.orEmpty().forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { vm.swap(slot, a.item.id); swapSlot = null }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemArt(a.item, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop).padding(4.dp))
                            Text(a.item.name, Modifier.weight(1f).padding(start = 8.dp))
                            Text(stringResource(R.string.match_pct, a.outfit.matchPct), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { swapSlot = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (planPicker) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = (s.epochDay + 1) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { planPicker = false },
            confirmButton = { TextButton(onClick = { dp.selectedDateMillis?.let { vm.planFor(it / 86_400_000L) }; planPicker = false }) { Text(stringResource(R.string.done)) } },
        ) { DatePicker(dp) }
    }
}
