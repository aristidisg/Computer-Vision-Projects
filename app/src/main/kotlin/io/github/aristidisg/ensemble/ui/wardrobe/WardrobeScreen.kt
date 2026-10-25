package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import io.github.aristidisg.ensemble.ui.nav.InsightsRoute
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.nav.ItemDetailRoute
import io.github.aristidisg.ensemble.ui.nav.ItemEditorRoute
import io.github.aristidisg.ensemble.ui.nav.LabelCheckRoute
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import java.io.File
import java.util.UUID

fun newCameraUri(context: Context): Uri {
    val f = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WardrobeScreen(nav: NavHostController, vm: WardrobeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var addMenu by remember { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingCamera
        if (ok && u != null) nav.navigate(ItemEditorRoute(uris = u))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) nav.navigate(ItemEditorRoute(uris = uris.joinToString("|")))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_wardrobe), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(InsightsRoute) }) { Icon(Icons.Outlined.Insights, stringResource(R.string.insights)) }
                Box {
                    var more by remember { mutableStateOf(false) }
                    IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                    DropdownMenu(more, { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.seasonal_swap)) }, onClick = { more = false; vm.previewSwap() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.check_labels)) }, onClick = { more = false; nav.navigate(LabelCheckRoute) })
                    }
                }
            }
            OutlinedTextField(
                s.filter.query, { vm.setFilter(s.filter.copy(query = it)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("slot" to R.string.filter_slot, "color" to R.string.filter_color, "season" to R.string.filter_season,
                    "status" to R.string.filter_status, "style" to R.string.filter_style).forEach { (k, label) ->
                    FilterChip(selected = filterOpen == k, onClick = { filterOpen = if (filterOpen == k) null else k }, label = { Text(stringResource(label)) })
                }
            }
            val f = s.filter
            when (filterOpen) {
                "slot" -> ChoiceChips(Slot.entries, f.slots, { it.label }, { t -> vm.setFilter(f.copy(slots = f.slots.toggle(t))) }, scroll = true)
                "color" -> ChoiceChips(ColorFamily.entries, f.families, { it.label }, { t -> vm.setFilter(f.copy(families = f.families.toggle(t))) }, scroll = true)
                "season" -> ChoiceChips(Season.entries, f.seasons, { it.label }, { t -> vm.setFilter(f.copy(seasons = f.seasons.toggle(t))) }, scroll = true)
                "status" -> ChoiceChips(ItemStatus.entries, f.statuses, { it.label }, { t -> vm.setFilter(f.copy(statuses = f.statuses.toggle(t))) }, scroll = true)
                "style" -> ChoiceChips(StyleTag.entries, f.styles, { it.label }, { t -> vm.setFilter(f.copy(styles = f.styles.toggle(t))) }, scroll = true)
            }
            if (s.allTags.isNotEmpty()) ChoiceChips(s.allTags, f.tags, { "#$it" }, { t -> vm.setFilter(f.copy(tags = f.tags.toggle(t))) }, scroll = true)
            val sorts = listOf(WardrobeSort.RECENT to R.string.sort_recent, WardrobeSort.MOST_WORN to R.string.sort_most_worn,
                WardrobeSort.LEAST_WORN to R.string.sort_least_worn, WardrobeSort.FORMALITY to R.string.sort_formality, WardrobeSort.HUE to R.string.sort_hue)
            ChoiceChips(sorts.map { it.first }, setOf(f.sort), { srt -> ctx.getString(sorts.first { it.first == srt }.second) }, { vm.setFilter(f.copy(sort = it)) }, scroll = true)
            if (s.selected.isNotEmpty()) BulkBar(s.selected.size, vm)
            if (s.total == 0) EmptyState(stringResource(R.string.wardrobe_empty))
            LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.items, key = { it.id }) { item ->
                    val sel = item.id in s.selected
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { if (s.selected.isNotEmpty()) vm.toggleSelect(item.id) else nav.navigate(ItemDetailRoute(item.id)) },
                                onLongClick = { vm.toggleSelect(item.id) },
                            ),
                    ) {
                        Box {
                            ItemArt(item, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(LocalEnsembleColors.current.backdrop).padding(6.dp)
                                .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier))
                            if (sel) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                        Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                        if (item.status != ItemStatus.AVAILABLE) Text(item.status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
        SwapDialog(vm)
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add)) })
            DropdownMenu(addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.add_camera)) }, onClick = {
                    addMenu = false
                    val u = newCameraUri(ctx); pendingCamera = u.toString(); camera.launch(u)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_gallery)) }, onClick = {
                    addMenu = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_manual)) }, onClick = { addMenu = false; nav.navigate(ItemEditorRoute()) })
                Text(stringResource(R.string.photo_tips), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp).fillMaxWidth(0.8f))
            }
        }
    }
}

@Composable
private fun SwapDialog(vm: WardrobeViewModel) {
    val pending by vm.swap.collectAsStateWithLifecycle()
    val (season, plan) = pending ?: return
    AlertDialog(
        onDismissRequest = vm::dismissSwap,
        title = { Text(stringResource(R.string.seasonal_swap_title, season.label)) },
        text = {
            Text(
                if (plan.isEmpty) stringResource(R.string.seasonal_swap_nothing)
                else stringResource(R.string.seasonal_swap_body, plan.store.size, plan.restore.size) +
                    plan.store.take(6).joinToString("") { "\n− ${it.name}" } + plan.restore.take(6).joinToString("") { "\n+ ${it.name}" },
            )
        },
        confirmButton = { if (!plan.isEmpty) TextButton(onClick = vm::applySwap) { Text(stringResource(R.string.seasonal_swap_apply)) } },
        dismissButton = { TextButton(onClick = vm::dismissSwap) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun <T> Set<T>.toggle(t: T): Set<T> = if (t in this) this - t else this + t

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkBar(n: Int, vm: WardrobeViewModel) {
    var status by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Wraps onto a second line on narrow screens instead of squeezing the label to one letter per line.
        FlowRow(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.selected_n, n), modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp), maxLines = 1)
            TextButton(onClick = { vm.bulkStatus(ItemStatus.LAUNDRY) }) { Text(stringResource(R.string.to_laundry)) }
            Box {
                TextButton(onClick = { status = true }) { Text(stringResource(R.string.set_status)) }
                DropdownMenu(status, { status = false }) {
                    ItemStatus.entries.forEach { st -> DropdownMenuItem(text = { Text(st.label) }, onClick = { status = false; vm.bulkStatus(st) }) }
                }
            }
            Box {
                TextButton(onClick = { season = true }) { Text(stringResource(R.string.set_season)) }
                DropdownMenu(season, { season = false }) {
                    Season.entries.forEach { se -> DropdownMenuItem(text = { Text(se.label) }, onClick = { season = false; vm.bulkSeasons(setOf(se)) }) }
                    DropdownMenuItem(text = { Text(Season.entries.joinToString(" · ") { it.label.take(3) }) }, onClick = { season = false; vm.bulkSeasons(Season.entries.toSet()) })
                }
            }
            val busy by vm.busy.collectAsStateWithLifecycle()
            TextButton(onClick = vm::bulkRereadColors, enabled = !busy) { Text(stringResource(if (busy) R.string.rereading_colors else R.string.reread_colors)) }
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.cancel)) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_selected_q, n)) },
        text = { Text(stringResource(R.string.delete_selected_body)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.bulkDelete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
