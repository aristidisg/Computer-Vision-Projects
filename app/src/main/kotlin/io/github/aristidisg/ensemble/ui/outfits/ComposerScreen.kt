package io.github.aristidisg.ensemble.ui.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.explain
import io.github.aristidisg.ensemble.ui.nav.ComposerRoute
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

/** The composer: a slot board with a live Match %, warnings with a one-tap fix and "Complete it". */
@Composable
fun ComposerScreen(route: ComposerRoute, nav: NavHostController, vm: OutfitViewModel = hiltViewModel()) {
    LaunchedEffect(route) { vm.load(route.pieces, route.scenarioId, java.time.LocalDate.now().toEpochDay()) }
    val s by vm.state.collectAsStateWithLifecycle()
    val scenarios by produceState(emptyList<Scenario>()) { value = vm.allScenarios() }
    var picking by remember { mutableStateOf<Slot?>(null) }
    LaunchedEffect(s.scored?.signature) { if (s.scored != null) vm.findFix() }

    Column {
        BackBar(stringResource(if (route.rate) R.string.rate_my_outfit else R.string.compose), onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ChoiceChips(scenarios, setOfNotNull(s.scenario), { it.name }, { vm.setScenario(it.id) }, scroll = true)
            Text(stringResource(R.string.composer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bySlot = s.pieces.associateBy { it.slot }
            Slot.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { slot ->
                        val item = bySlot[slot]?.let { s.items[it.itemId] }
                        Card(Modifier.weight(1f).heightIn(min = 96.dp).clickable { picking = slot }) {
                            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (item != null) ItemArt(item, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop))
                                else Box(Modifier.size(64.dp))
                                Text(item?.name ?: slot.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
            s.scored?.let { o ->
                Text(stringResource(R.string.match_pct, o.matchPct), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                o.violation?.let { Text(stringResource(R.string.breaks_rule, it.name.lowercase().replace('_', ' ')), color = MaterialTheme.colorScheme.error) }
                o.tradeoffs.forEach { Text("• " + explain(it), style = MaterialTheme.typography.bodyMedium) }
                s.fix?.let { (alt, slot) ->
                    TextButton(onClick = { vm.swap(slot, alt.item.id) }) {
                        Text(stringResource(R.string.fix_suggestion, alt.item.name) + "  (" + stringResource(R.string.match_pct, alt.outfit.matchPct) + ")")
                    }
                }
                o.why.forEach { Text("• " + explain(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::completeIt, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.complete_it)) }
            Button(onClick = { vm.saveComposed { nav.popBackStack() } }, enabled = s.scored != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
        }
    }
    picking?.let { slot ->
        val options = s.items.values.filter { slot in it.slots }.sortedBy { it.name }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(slot.label) },
            text = {
                LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.height(420.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options, key = { it.id }) { i ->
                        Column(Modifier.clickable { vm.swap(slot, i.id); picking = null }) {
                            ItemArt(i, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop).padding(4.dp))
                            Text(i.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.remove(slot); picking = null }) { Text(stringResource(R.string.none)) } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
