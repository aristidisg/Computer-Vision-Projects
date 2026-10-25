package io.github.aristidisg.ensemble.ui.wardrobe

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.ItemFactory
import io.github.aristidisg.ensemble.engine.LabelCheck
import io.github.aristidisg.ensemble.engine.LabelIssue
import io.github.aristidisg.ensemble.engine.LabelSuggestion
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.photo.CategoryHints
import io.github.aristidisg.ensemble.photo.ImageOps
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LabelCheckState(
    val suggestions: List<LabelSuggestion> = emptyList(),
    /** Every top, mid layer and outer layer, for a manual pass. */
    val layers: List<Item> = emptyList(),
    val scanning: Boolean = false,
)

@HiltViewModel
class LabelCheckViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val settings: SettingsRepository,
    private val images: ImageStore,
    private val time: TimeSource,
    @ApplicationContext private val app: Context,
) : ViewModel() {
    private val photoHints = MutableStateFlow<Map<String, Subcategory>>(emptyMap())
    private val scanning = MutableStateFlow(false)
    private val _saved = MutableStateFlow<String?>(null)
    /** "Saved: X is now a Y", shown after every fix. */
    val saved = _saved.asStateFlow()

    val state = combine(wardrobe.items, photoHints, settings.settings, scanning) { items, hints, st, scan ->
        val gone = st.labelsConfirmed
        LabelCheckState(
            LabelCheck.check(items, hints).filter { it.item.id !in gone },
            items.filter { it.subcategory.group == CategoryGroup.OUTERWEAR || it.subcategory.isMidLayer() }
                .sortedWith(compareBy({ it.subcategory.group }, { it.name.lowercase() })),
            scan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabelCheckState())

    init { scanPhotos() }

    /**
     * ML Kit category hints on each layer's photo, only when the user has hints switched on.
     * Results are cached per piece and edit time, so reopening the screen re-runs the model only
     * for pieces that changed (it is the most battery-hungry thing this screen does).
     */
    private fun scanPhotos() = viewModelScope.launch {
        if (!settings.current().categoryHints) return@launch
        val cacheFile = java.io.File(app.filesDir, "label_hints.tsv")
        val cache = withContext(Dispatchers.IO) { readHintCache(cacheFile) }.toMutableMap()
        val layers = wardrobe.all().filter { it.subcategory.group == CategoryGroup.TOPS || it.subcategory.group == CategoryGroup.OUTERWEAR }
        fun hints() = layers.mapNotNull { i -> cache[i.id]?.takeIf { it.first == i.updatedAtMillis }?.second?.let { i.id to it } }.toMap()
        photoHints.value = hints()
        val todo = layers.filter { cache[it.id]?.first != it.updatedAtMillis }
        if (todo.isEmpty()) return@launch
        scanning.value = true
        for (i in todo) {
            val file = images.resolve(i.photos.cutout ?: i.photos.original)
            val hint = if (file == null) null else withContext(Dispatchers.IO) {
                // Decode at reduced size: the labeler only needs ~512 px, and cutouts are larger.
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(file.path, opts)
            }?.let { bmp ->
                CategoryHints.suggest(ImageOps.scaleToFit(bmp, 512)).firstOrNull { it.group == CategoryGroup.TOPS || it.group == CategoryGroup.OUTERWEAR }
            }
            cache[i.id] = i.updatedAtMillis to hint
        }
        withContext(Dispatchers.IO) { writeHintCache(cacheFile, cache.filterKeys { id -> layers.any { it.id == id } }) }
        photoHints.value = hints()
        scanning.value = false
    }

    private fun readHintCache(f: java.io.File): Map<String, Pair<Long, Subcategory?>> = runCatching {
        f.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 2) null else p[0] to ((p[1].toLongOrNull() ?: return@mapNotNull null) to p.getOrNull(2)?.let { n -> Subcategory.entries.firstOrNull { it.name == n } })
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeHintCache(f: java.io.File, m: Map<String, Pair<Long, Subcategory?>>) {
        runCatching { f.writeText(m.entries.joinToString("\n") { (id, v) -> "$id\t${v.first}\t${v.second?.name ?: ""}" }) }
    }

    fun apply(s: LabelSuggestion, sub: Subcategory? = s.suggested) = viewModelScope.launch {
        // Fix the stored piece as it is now, not the copy the list was built from.
        val current = wardrobe.get(s.item.id) ?: return@launch
        LabelCheck.fix(s.copy(item = current), sub)?.let { save(current, it) }
    }

    fun move(item: Item, sub: Subcategory) = viewModelScope.launch {
        val current = wardrobe.get(item.id) ?: return@launch
        if (sub != current.subcategory) save(current, ItemFactory.recategorize(current, sub))
    }

    private suspend fun save(before: Item, after: Item) {
        wardrobe.upsert(after, time.nowMillis())
        _saved.value = app.getString(R.string.label_saved, before.name, after.subcategory.label.lowercase())
    }

    fun clearSaved() { _saved.value = null }

    /** "It's right": never flagged again (kept in settings). */
    fun dismiss(id: String) = viewModelScope.launch { settings.update { it.copy(labelsConfirmed = it.labelsConfirmed + id) } }
}

@Composable
fun LabelCheckScreen(nav: NavHostController, vm: LabelCheckViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Item?>(null) }
    var pickGroup by remember { mutableStateOf<CategoryGroup?>(null) }
    var pickFor by remember { mutableStateOf<LabelSuggestion?>(null) }
    Column {
        BackBar(stringResource(R.string.check_labels), onBack = { nav.popBackStack() })
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Text(stringResource(R.string.check_labels_body), style = MaterialTheme.typography.bodyMedium)
                saved?.let { msg ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✓ $msg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearSaved) { Text(stringResource(R.string.ok)) }
                    }
                }
                if (s.scanning) {
                    Text(stringResource(R.string.checking_photos), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                SectionTitle(stringResource(R.string.likely_mislabeled, s.suggestions.size))
                if (s.suggestions.isEmpty() && !s.scanning) Text(stringResource(R.string.no_mislabels), style = MaterialTheme.typography.bodySmall)
            }
            items(s.suggestions, key = { "s-" + it.item.id }) { sug ->
                SuggestionRow(sug, onFix = { vm.apply(sug) }, onChoose = { pickFor = sug; picking = sug.item; pickGroup = sug.target }, onIgnore = { vm.dismiss(sug.item.id) })
            }
            item { SectionTitle(stringResource(R.string.all_layers)) }
            items(s.layers, key = { "l-" + it.id }) { i ->
                ItemLine(i, stringResource(if (i.subcategory.group == CategoryGroup.OUTERWEAR) R.string.cat_outerwear_short else R.string.cat_mid_short)) {
                    TextButton(onClick = { pickFor = null; picking = i; pickGroup = i.subcategory.group }) { Text(stringResource(R.string.change)) }
                }
            }
        }
    }
    val item = picking
    if (item != null) CategoryDialog(item, pickGroup ?: item.subcategory.group, onGroup = { pickGroup = it }, onDismiss = { picking = null }) { sub ->
        val sug = pickFor
        if (sug != null) vm.apply(sug, sub) else vm.move(item, sub)
        picking = null
    }
}

@Composable
private fun ItemLine(i: Item, sub: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemArt(i, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop).padding(3.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(i.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${i.subcategory.label} · $sub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(s: LabelSuggestion, onFix: () -> Unit, onChoose: () -> Unit, onIgnore: () -> Unit) {
    val reason = when (s.issue) {
        LabelIssue.NAME -> R.string.issue_name
        LabelIssue.MATERIAL -> R.string.issue_material
        LabelIssue.PHOTO -> R.string.issue_photo
        LabelIssue.SLOTS -> R.string.issue_slots
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        ItemLine(s.item, stringResource(reason)) {}
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 62.dp)) {
            val target = s.suggested
            if (target != null) Button(onClick = onFix) {
                Text(if (target == s.item.subcategory) stringResource(R.string.reset_slots) else stringResource(R.string.make_it, target.label))
            }
            OutlinedButton(onClick = onChoose) { Text(stringResource(R.string.choose)) }
            TextButton(onClick = onIgnore) { Text(stringResource(R.string.looks_right)) }
        }
    }
}

@Composable
private fun CategoryDialog(item: Item, group: CategoryGroup, onGroup: (CategoryGroup) -> Unit, onDismiss: () -> Unit, onSave: (Subcategory) -> Unit) {
    var choice by remember(item.id) { mutableStateOf(item.subcategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.now_label, item.subcategory.label, choice.label), style = MaterialTheme.typography.bodyMedium)
                io.github.aristidisg.ensemble.ui.common.ChoiceChips(listOf(CategoryGroup.TOPS, CategoryGroup.OUTERWEAR), setOf(group), { it.label }, onGroup)
                SubcategoryChips(group, choice) { choice = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(choice) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
