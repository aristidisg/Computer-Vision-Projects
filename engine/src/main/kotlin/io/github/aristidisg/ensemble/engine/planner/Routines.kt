package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.ShoppingEntry
import io.github.aristidisg.ensemble.model.WearLog

/** Pieces to put away and to bring back for [season]. */
data class SwapPlan(val store: List<Item>, val restore: List<Item>) {
    val isEmpty: Boolean get() = store.isEmpty() && restore.isEmpty()
}

/** A saved outfit whose cooldown is over, worn [daysSince] days ago. */
data class WearAgain(val outfit: Outfit, val daysSince: Long)

object Routines {

    /**
     * Seasonal swap: available pieces not tagged for [season] go to storage; stored pieces
     * tagged for it come back. Other statuses (laundry, tailor, lent, retired) are left alone.
     */
    fun seasonalSwap(items: List<Item>, season: Season): SwapPlan = SwapPlan(
        store = items.filter { it.status == ItemStatus.AVAILABLE && season !in it.seasons },
        restore = items.filter { it.status == ItemStatus.STORED && season in it.seasons },
    )

    /**
     * "Wear it again": liked outfits worn before whose cooldown has ended and whose pieces are all
     * available, longest-unworn first.
     */
    fun wearAgain(outfits: List<Outfit>, logs: List<WearLog>, items: Map<String, Item>, today: Long, defaultCooldown: Int): List<WearAgain> {
        val lastWorn = logs.groupBy { it.outfitSignature }.mapValues { (_, l) -> l.maxOf { it.epochDay } }
        return outfits.asSequence()
            .filter { it.liked && !it.neverShow }
            .mapNotNull { o -> lastWorn[o.signature]?.let { WearAgain(o, today - it) } }
            .filter { it.daysSince >= (it.outfit.cooldownDays ?: defaultCooldown) }
            .filter { w -> w.outfit.itemIds.all { items[it]?.isAvailable == true } }
            .distinctBy { it.outfit.signature }
            .sortedByDescending { it.daysSince }
            .toList()
    }

    /**
     * Adds a gap-finder suggestion to the shopping list. The same piece in the same color is
     * listed once: adding it again refreshes its unlock count and brings it back if ticked off.
     */
    fun addToShoppingList(list: List<ShoppingEntry>, gap: GapSuggestion, id: String, today: Long): List<ShoppingEntry> {
        val existing = list.firstOrNull { it.subcategory == gap.archetype.sub && it.family == gap.color }
        if (existing != null) return list.map { if (it.id == existing.id) it.copy(unlocks = gap.unlocks, done = false) else it }
        val note = gap.fills.joinToString()
        return list + ShoppingEntry(id, gap.archetype.sub, gap.color, ColorTables.family(gap.color).hex, gap.unlocks, note, addedEpochDay = today)
    }

    /** Open entries first (most outfits unlocked first), then the ticked-off ones. */
    fun sortedShoppingList(list: List<ShoppingEntry>): List<ShoppingEntry> =
        list.sortedWith(compareBy<ShoppingEntry> { it.done }.thenByDescending { it.unlocks }.thenBy { it.addedEpochDay })
}
