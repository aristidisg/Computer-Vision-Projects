package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.WearLog
import kotlin.math.roundToInt

/** A week of wearing, for the variety card. */
data class VarietyReport(
    val outfits: Int,
    /** Different clothes worn (shoes and accessories aside) and how many you have. */
    val piecesWorn: Int,
    val pieces: Int,
    /** 0-100: how varied the week was (different pieces, different colours); null with no wears. */
    val score: Int?,
    /** Colour families worn, most worn first (one count per piece per wear). */
    val colors: List<Pair<ColorFamily, Int>>,
    /** Pieces worn [REPEAT]+ times this week. */
    val repeats: List<Pair<Item, Int>>,
    /** Available clothes not worn in [UNWORN_DAYS] days (or since added). */
    val unworn: List<Item>,
)

/** Weekly variety: share of the wardrobe used, colour spread, repeats and forgotten pieces. */
object VarietyStats {
    const val DAYS = 7
    const val REPEAT = 3
    const val UNWORN_DAYS = 30
    /** Colour spread counts up to this many different families as fully varied. */
    private const val COLOR_TARGET = 6

    fun week(logs: List<WearLog>, items: List<Item>, today: Long): VarietyReport {
        val byId = items.associateBy { it.id }
        fun clothes(i: Item?) = i != null && i.primarySlot.isCore && i.primarySlot != io.github.aristidisg.ensemble.model.Slot.SHOES
        val week = logs.filter { it.epochDay in (today - DAYS + 1)..today }
        val wears = week.flatMap { l -> l.pieces.map { it.itemId }.distinct().filter { clothes(byId[it]) } }
        val counts = wears.groupingBy { it }.eachCount()
        val colors = wears.mapNotNull { byId[it]?.primaryColor?.family }.groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<ColorFamily, Int>> { it.second }.thenBy { it.first.name })
        val score = if (wears.isEmpty()) null else {
            val fresh = counts.size.toDouble() / wears.size
            val spread = (colors.size.toDouble() / minOf(COLOR_TARGET, wears.size)).coerceAtMost(1.0)
            (100 * (0.6 * fresh + 0.4 * spread)).roundToInt()
        }
        val last = logs.flatMap { l -> l.pieces.map { it.itemId to l.epochDay } }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
        val owned = items.filter { it.isAvailable && clothes(it) }
        return VarietyReport(
            outfits = week.size,
            piecesWorn = counts.size,
            pieces = owned.size,
            score = score,
            colors = colors,
            repeats = counts.filter { it.value >= REPEAT }.mapNotNull { (id, n) -> byId[id]?.let { it to n } }.sortedByDescending { it.second },
            unworn = owned.filter { today - (last[it.id] ?: it.createdEpochDay) >= UNWORN_DAYS }.sortedBy { last[it.id] ?: it.createdEpochDay },
        )
    }
}
