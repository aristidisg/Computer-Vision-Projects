package io.github.aristidisg.ensemble.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

/** Laundry basket, plus pieces at their wash threshold with "Move to laundry?". */
@Composable
fun LaundryScreen(nav: NavHostController, vm: PlanViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val basket = s.items.values.filter { it.status == ItemStatus.LAUNDRY }
    val due = s.items.values.filter { it.status == ItemStatus.AVAILABLE && it.laundry.atThreshold }
    Column {
        BackBar(stringResource(R.string.laundry), onBack = { nav.popBackStack() })
        if (basket.isEmpty() && due.isEmpty()) EmptyState(stringResource(R.string.laundry_empty))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (due.isNotEmpty()) item { SectionTitle(stringResource(R.string.wears_since_wash)) }
            items(due, key = { "due" + it.id }) { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemArt(i, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop))
                    Text(stringResource(R.string.move_to_laundry_q, i.name), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.toLaundry(i.id) }) { Text(stringResource(R.string.to_laundry)) }
                }
            }
            if (basket.isNotEmpty()) item { SectionTitle(stringResource(R.string.laundry)) }
            items(basket, key = { it.id }) { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemArt(i, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop))
                    Text(i.name, Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = { vm.washed(i.id) }) { Text(stringResource(R.string.done)) }
                }
            }
        }
        if (basket.isNotEmpty()) Button(onClick = vm::markAllWashed, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.mark_all_washed)) }
    }
}
