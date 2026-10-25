package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.engine.planner.Routines
import io.github.aristidisg.ensemble.model.ShoppingEntry
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.hexColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingListViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val list = settings.settings.map { Routines.sortedShoppingList(it.shoppingList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun edit(t: (List<ShoppingEntry>) -> List<ShoppingEntry>) = viewModelScope.launch {
        settings.update { it.copy(shoppingList = t(it.shoppingList)) }
    }

    fun toggle(id: String) = edit { l -> l.map { if (it.id == id) it.copy(done = !it.done) else it } }
    fun remove(id: String) = edit { l -> l.filter { it.id != id } }
    fun clearDone() = edit { l -> l.filter { !it.done } }
}

@Composable
fun ShoppingListScreen(nav: NavHostController, vm: ShoppingListViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.shopping_list), onBack = { nav.popBackStack() }) {
            if (list.any { it.done }) TextButton(onClick = vm::clearDone) { Text(stringResource(R.string.clear_bought)) }
        }
        if (list.isEmpty()) { EmptyState(stringResource(R.string.shopping_empty)); return@Column }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { e -> EntryRow(e, vm) }
        }
    }
}

@Composable
private fun EntryRow(e: ShoppingEntry, vm: ShoppingListViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = e.done, onCheckedChange = { vm.toggle(e.id) })
        Box(Modifier.size(22.dp).clip(CircleShape).background(hexColor(e.hex)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, textDecoration = if (e.done) TextDecoration.LineThrough else null)
            if (e.unlocks > 0) Text(stringResource(R.string.unlocks_n, e.unlocks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.note.isNotEmpty()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.remove(e.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
    }
}
