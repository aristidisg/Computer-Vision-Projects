@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.aristidisg.ensemble.engine.generator.PairCache
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Violation
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.ColorDot
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.nav.ItemDetailRoute
import io.github.aristidisg.ensemble.ui.nav.ItemEditorRoute
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ItemDetail(
    val item: Item,
    val wears: Int,
    val lastWornDaysAgo: Long?,
    val pairs: List<Item>,
    val outfits: List<ScoredOutfit>,
    val items: Map<String, Item>,
    val scenarioId: String,
    val day: Long,
    /** Why today's deck can't use this piece at all (null: it can). */
    val blocked: Violation? = null,
    val scenarioName: String = "",
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val wear: WearRepository,
    private val studio: Studio,
    private val time: TimeSource,
) : ViewModel() {
    private val _d = MutableStateFlow<ItemDetail?>(null)
    val detail = _d.asStateFlow()
    private val _gone = MutableStateFlow(false)
    /** True once the piece no longer exists (deleted from the editor): the screen closes. */
    val gone = _gone.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        val item = wardrobe.get(id)
        if (item == null) { _gone.value = true; return@launch }
        val logs = wear.all().filter { l -> l.pieces.any { it.itemId == id } }
        val day = time.today()
        val scenario = studio.scenario(studio.defaultScenarioId(day))
        val ctx = studio.context(day, scenario)
        val all = ctx.items.values.toList()
        val (pairs, outfits) = withContext(Dispatchers.Default) {
            val cache = PairCache(ctx)
            val me = Placed(item, item.slots.first())
            val p = all.filter { it.id != id && it.slots.first().isCore && it.slots.first() != item.slots.first() && it.isAvailable }
                .sortedByDescending { cache.score(me, Placed(it, it.slots.first())) }.take(6)
            p to if (item.slots.first().isCore) studio.engine.complete(listOf(OutfitPiece(id, item.slots.first())), ctx, all, 4) else emptyList()
        }
        _d.value = ItemDetail(item, logs.size, logs.maxOfOrNull { it.epochDay }?.let { day - it }, pairs, outfits, ctx.items, scenario.id, day,
            HardConstraints.whyNotSuggested(item, ctx), scenario.name)
    }

    fun setStatus(s: ItemStatus) = viewModelScope.launch {
        val d = _d.value ?: return@launch
        val l = if (s == ItemStatus.AVAILABLE && d.item.status == ItemStatus.LAUNDRY) d.item.laundry.copy(wearsSinceWash = 0) else d.item.laundry
        val updated = d.item.copy(status = s, laundry = l)
        wardrobe.upsert(updated, time.nowMillis())
        _d.value = d.copy(item = updated)
    }
}

@Composable
fun ItemDetailScreen(id: String, nav: NavHostController, vm: ItemDetailViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val gone by vm.gone.collectAsStateWithLifecycle()
    LaunchedEffect(gone) { if (gone) nav.popBackStack() }
    Column {
        BackBar(d?.item?.name ?: "", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate(ItemEditorRoute(itemId = id)) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) }
        }
        val x = d ?: return@Column
        val i = x.item
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            ItemArt(i, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)).background(LocalEnsembleColors.current.backdrop).padding(12.dp), thumb = false)
            Text("${i.subcategory.label} · ${i.silhouette.label} · ${stringResource(R.string.formality)} ${i.formality}/10", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                i.colors.forEach { ColorDot(it.hex, "${it.family.label} ${it.hex}") }
            }
            Text(listOfNotNull(i.pattern.type.label, i.materials.joinToString { it.label }.ifEmpty { null }, i.styleTags.joinToString { it.label }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            x.blocked?.let { v ->
                Text(stringResource(R.string.not_suggested_today, x.scenarioName) + " " + stringResource(violationText(v)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            SectionTitle(stringResource(R.string.wears))
            Text("${x.wears} · " + (x.lastWornDaysAgo?.let { stringResource(R.string.days_ago, it.toInt()) } ?: stringResource(R.string.never_worn)))
            i.meta.price?.let { p ->
                Text("${stringResource(R.string.cost_per_wear)}: " + "%.2f".format(p / maxOf(1, x.wears)), style = MaterialTheme.typography.bodyMedium)
            }
            SectionTitle(stringResource(R.string.status))
            ChoiceChips(ItemStatus.entries, setOf(i.status), { it.label }, vm::setStatus, scroll = true)
            if (x.pairs.isNotEmpty()) {
                SectionTitle(stringResource(R.string.pairs_best_with))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    x.pairs.forEach { p ->
                        ItemArt(p, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(LocalEnsembleColors.current.backdrop).clickable { nav.navigate(ItemDetailRoute(p.id)) }.padding(6.dp))
                    }
                }
            }
            if (x.outfits.isNotEmpty()) {
                SectionTitle(stringResource(R.string.outfits_with_this))
                x.outfits.forEach { o ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { nav.navigate(OutfitDetailRoute(PiecesArg.encode(o.pieces), x.scenarioId, x.day)) }) {
                        Collage(o.pieces.mapNotNull { p -> x.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().height(180.dp))
                        Text(stringResource(R.string.match_pct, o.matchPct), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Plain-language reason for a hard block. */
private fun violationText(v: Violation): Int = when (v) {
    Violation.UNAVAILABLE -> R.string.why_unavailable
    Violation.NEVER_SUGGEST -> R.string.why_never_suggest
    Violation.HIDDEN -> R.string.why_hidden
    Violation.WEATHER -> R.string.why_weather
    Violation.SCENARIO -> R.string.why_scenario
    Violation.PERSONAL_NEVER -> R.string.why_personal
    Violation.ROTATION -> R.string.why_rotation
    else -> R.string.why_other
}
