package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.domain.WardrobeCleaner
import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.planner.Routines
import io.github.aristidisg.ensemble.engine.planner.SwapPlan
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WardrobeSort { RECENT, MOST_WORN, LEAST_WORN, FORMALITY, HUE }

data class WardrobeFilter(
    val query: String = "",
    val slots: Set<Slot> = emptySet(),
    val families: Set<ColorFamily> = emptySet(),
    val seasons: Set<Season> = emptySet(),
    val statuses: Set<ItemStatus> = emptySet(),
    val styles: Set<StyleTag> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sort: WardrobeSort = WardrobeSort.RECENT,
)

data class WardrobeState(
    val items: List<Item> = emptyList(),
    val total: Int = 0,
    val filter: WardrobeFilter = WardrobeFilter(),
    val selected: Set<String> = emptySet(),
    val allTags: List<String> = emptyList(),
)

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    private val wardrobe: WardrobeRepository,
    wear: WearRepository,
    private val time: TimeSource,
    private val cleaner: WardrobeCleaner,
) : ViewModel() {
    private val filter = MutableStateFlow(WardrobeFilter())
    private val selected = MutableStateFlow(emptySet<String>())

    val state: StateFlow<WardrobeState> = combine(wardrobe.items, wear.logs, filter, selected) { items, logs, f, sel ->
        val wears = logs.flatMap { l -> l.pieces.map { it.itemId } }.groupingBy { it }.eachCount()
        val q = f.query.trim().lowercase()
        val shown = items.filter { i ->
            (q.isEmpty() || i.name.lowercase().contains(q) || i.subcategory.label.lowercase().contains(q) || i.meta.brand.lowercase().contains(q)) &&
                (f.slots.isEmpty() || i.slots.any { it in f.slots }) &&
                (f.families.isEmpty() || i.colors.any { it.family in f.families }) &&
                (f.seasons.isEmpty() || i.seasons.any { it in f.seasons }) &&
                (f.statuses.isEmpty() || i.status in f.statuses) &&
                (f.styles.isEmpty() || i.styleTags.any { it in f.styles }) &&
                (f.tags.isEmpty() || i.meta.customTags.any { it in f.tags })
        }.let { list ->
            when (f.sort) {
                WardrobeSort.RECENT -> list.sortedByDescending { it.createdEpochDay * 1_000_000_000L + it.updatedAtMillis / 1000 }
                WardrobeSort.MOST_WORN -> list.sortedByDescending { wears[it.id] ?: 0 }
                WardrobeSort.LEAST_WORN -> list.sortedBy { wears[it.id] ?: 0 }
                WardrobeSort.FORMALITY -> list.sortedBy { it.formality }
                WardrobeSort.HUE -> list.sortedWith(compareBy({ hueKey(it).first }, { hueKey(it).second }))
            }
        }
        WardrobeState(shown, items.size, f, sel, items.flatMap { it.meta.customTags }.distinct().sorted())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WardrobeState())

    /** Rainbow closet: neutrals first by lightness, then chromatic colors by artist hue. */
    private fun hueKey(i: Item): Pair<Double, Double> {
        val c = i.primaryColor ?: return 999.0 to 0.0
        val info = ColorScience.info(c.hex)
        return if (info.neutralness >= 0.8) -1.0 to -info.l else info.hue to -info.l
    }

    fun setFilter(f: WardrobeFilter) = filter.update { f }
    fun toggleSelect(id: String) = selected.update { if (id in it) it - id else it + id }
    fun clearSelection() = selected.update { emptySet() }

    fun bulkStatus(status: ItemStatus) = viewModelScope.launch {
        val ids = selected.value
        wardrobe.all().filter { it.id in ids }.forEach { i ->
            val l = if (status == ItemStatus.AVAILABLE && i.status == ItemStatus.LAUNDRY) i.laundry.copy(wearsSinceWash = 0) else i.laundry
            wardrobe.upsert(i.copy(status = status, laundry = l), time.nowMillis())
        }
        clearSelection()
    }

    /** Deletes the selected pieces with their photos and the outfits and plans that use them. */
    fun bulkDelete() = viewModelScope.launch {
        cleaner.delete(selected.value)
        clearSelection()
    }

    fun bulkSeasons(seasons: Set<Season>) = viewModelScope.launch {
        val ids = selected.value
        wardrobe.all().filter { it.id in ids }.forEach { wardrobe.upsert(it.copy(seasons = seasons), time.nowMillis()) }
        clearSelection()
    }

    private val _swap = MutableStateFlow<Pair<Season, SwapPlan>?>(null)
    /** The pending seasonal swap, shown for confirmation before anything changes. */
    val swap = _swap.asStateFlow()

    fun previewSwap() = viewModelScope.launch {
        val season = EngineContext.seasonOf(time.today())
        _swap.value = season to Routines.seasonalSwap(wardrobe.all(), season)
    }

    fun dismissSwap() { _swap.value = null }

    /** Off-season pieces go to storage (never suggested), in-season stored pieces come back. */
    fun applySwap() = viewModelScope.launch {
        val (_, plan) = _swap.value ?: return@launch
        _swap.value = null
        val now = time.nowMillis()
        // Re-read each piece so an edit made while the dialog was open isn't overwritten.
        val target = plan.store.associate { it.id to ItemStatus.STORED } + plan.restore.associate { it.id to ItemStatus.AVAILABLE }
        wardrobe.all().filter { it.id in target }.forEach { wardrobe.upsert(it.copy(status = target.getValue(it.id)), now) }
    }
}
