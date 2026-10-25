package io.github.aristidisg.ensemble.ui.outfits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.LookRepository
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.planner.Routines
import io.github.aristidisg.ensemble.engine.planner.WearAgain
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitSource
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.SwitchRow
import io.github.aristidisg.ensemble.ui.nav.ComposerRoute
import io.github.aristidisg.ensemble.ui.nav.LookEditorRoute
import io.github.aristidisg.ensemble.ui.nav.LookMatchRoute
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class OutfitsState(
    val saved: List<Outfit> = emptyList(),
    val composed: List<Outfit> = emptyList(),
    val looks: List<InspirationLook> = emptyList(),
    val history: List<WearLog> = emptyList(),
    val items: Map<String, Item> = emptyMap(),
    val today: Long = 0,
    val cooldown: Int = 14,
    /** Liked outfits worn before whose cooldown is over ("Wear it again"). */
    val ready: List<WearAgain> = emptyList(),
    /** Saved outfits shape suggestions (boost and learning). */
    val influence: Boolean = true,
)

@HiltViewModel
class OutfitsViewModel @Inject constructor(
    outfits: OutfitRepository, looks: LookRepository, wear: WearRepository, wardrobe: WardrobeRepository,
    private val settings: SettingsRepository, time: TimeSource, private val feedback: FeedbackRepository,
) : ViewModel() {
    /** On/off for the collection's influence; past saves are replayed so they stop (or start) counting. */
    fun setInfluence(on: Boolean) = viewModelScope.launch {
        settings.update { it.copy(savedOutfitsInfluence = on) }
        val s = settings.current()
        feedback.rebuild(s.learningEpoch, LearningConfig.of(s))
    }

    val state = combine(outfits.outfits, looks.looks, wear.logs, wardrobe.items, settings.settings) { o, l, w, i, s ->
        val visible = o.filter { !it.neverShow }
        val byId = i.associateBy { it.id }
        val today = time.today()
        OutfitsState(
            visible.filter { it.source != OutfitSource.COMPOSED && it.liked }, visible.filter { it.source == OutfitSource.COMPOSED },
            l, w, byId, today, s.outfitCooldownDays, Routines.wearAgain(visible, w, byId, today, s.outfitCooldownDays), s.savedOutfitsInfluence,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OutfitsState())
}

@Composable
fun OutfitsScreen(nav: NavHostController, vm: OutfitsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(R.string.seg_saved, R.string.seg_composed, R.string.seg_inspiration, R.string.seg_history)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.tab_outfits), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(stringResource(l), maxLines = 1) }
                }
            }
            when (tab) {
                0 -> {
                    SwitchRow(stringResource(R.string.saved_influence), s.influence, vm::setInfluence, stringResource(R.string.saved_influence_body))
                    WearAgainRow(s, nav)
                    OutfitGrid(s.saved, s, nav, stringResource(R.string.nothing_saved))
                }
                1 -> OutfitGrid(s.composed, s, nav, stringResource(R.string.composer_hint))
                2 -> Looks(s.looks, nav)
                else -> History(s.history, s, nav)
            }
        }
        val fab = when (tab) { 1 -> R.string.compose; 2 -> R.string.add_look; else -> null }
        if (fab != null) ExtendedFloatingActionButton(
            onClick = { if (tab == 1) nav.navigate(ComposerRoute()) else nav.navigate(LookEditorRoute()) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(fab)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun OutfitGrid(list: List<Outfit>, s: OutfitsState, nav: NavHostController, empty: String) {
    if (list.isEmpty()) { EmptyState(empty); return }
    LazyVerticalGrid(GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        items(list, key = { it.id }) { o ->
            val lastWorn = s.history.filter { it.outfitSignature == o.signature }.maxOfOrNull { it.epochDay }
            val back = lastWorn?.let { (o.cooldownDays ?: s.cooldown) - (s.today - it) }?.takeIf { it > 0 }
            Column(Modifier.clickable { nav.navigate(OutfitDetailRoute(PiecesArg.encode(o.pieces), o.scenarioId ?: "", s.today)) }) {
                Collage(o.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().aspectRatio(0.8f))
                Text(o.name.ifEmpty { o.pieces.mapNotNull { s.items[it.itemId]?.name }.take(2).joinToString(" + ") }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (back != null) Text(stringResource(R.string.back_in_days, back.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Wear it again": favourites that are off cooldown, longest-unworn first. Tap to wear today. */
@Composable
private fun WearAgainRow(s: OutfitsState, nav: NavHostController) {
    if (s.ready.isEmpty()) return
    Text(stringResource(R.string.wear_again), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.ready, key = { it.outfit.id }) { r ->
            val o = r.outfit
            Column(Modifier.width(120.dp).clickable { nav.navigate(OutfitDetailRoute(PiecesArg.encode(o.pieces), o.scenarioId ?: "", s.today)) }) {
                Collage(o.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().aspectRatio(0.8f))
                Text(o.name.ifEmpty { o.pieces.mapNotNull { s.items[it.itemId]?.name }.take(2).joinToString(" + ") }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.last_worn_days, r.daysSince.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Looks(looks: List<InspirationLook>, nav: NavHostController) {
    if (looks.isEmpty()) { EmptyState(stringResource(R.string.no_looks)); return }
    val ctx = LocalContext.current
    LazyVerticalGrid(GridCells.Adaptive(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        items(looks, key = { it.id }) { l ->
            Column(Modifier.clickable { nav.navigate(LookMatchRoute(l.id)) }) {
                AsyncImage(l.photoPath?.let { File(ctx.filesDir, it) }, null, Modifier.fillMaxWidth().aspectRatio(0.75f), contentScale = ContentScale.Crop)
                Text(l.pieces.joinToString { it.subcategory.label }, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun History(logs: List<WearLog>, s: OutfitsState, nav: NavHostController) {
    if (logs.isEmpty()) { EmptyState(stringResource(R.string.no_history)); return }
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        items(logs, key = { it.id }) { log ->
            Row(Modifier.fillMaxWidth().clickable { nav.navigate(OutfitDetailRoute(PiecesArg.encode(log.pieces), log.scenarioId, s.today)) }, verticalAlignment = Alignment.CenterVertically) {
                Collage(log.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.height(110.dp).aspectRatio(0.9f))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(LocalDate.ofEpochDay(log.epochDay).format(fmt), style = MaterialTheme.typography.titleSmall)
                    log.weather?.let { Text(stringResource(R.string.feels_range, it.feelsMin.toInt(), it.feelsMax.toInt()), style = MaterialTheme.typography.bodySmall) }
                    Text(log.scenarioId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
