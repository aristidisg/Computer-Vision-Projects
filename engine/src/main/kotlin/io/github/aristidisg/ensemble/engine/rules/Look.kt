package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorInfo
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.Lab
import io.github.aristidisg.ensemble.engine.tables.StyleTables.Area
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OuterWeight
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot

/** One color of one piece, placed in the outfit with its area and chromatic salience. */
data class LookColor(
    val item: Item,
    val slot: Slot,
    val info: ColorInfo,
    val area: Double,
) {
    /** σ = A × (1 − neutralness) × clamp(C / 0.12, 0.3, 1.2) */
    val salience: Double get() = area * (1 - info.neutralness) * (info.c / 0.12).coerceIn(0.3, 1.2)
    val chromatic: Boolean get() = info.neutralness < 0.8
}

data class Placed(val item: Item, val slot: Slot)

/**
 * A resolved outfit: items in slots, whether the outer is worn open, and the outfit color model
 * (SPEC 4.3). Built once per candidate and shared by every rule.
 */
class Look(val placed: List<Placed>, ctx: EngineContext) {
    val bySlot: Map<Slot, List<Item>> = placed.groupBy({ it.slot }, { it.item })
    val top: Item? = bySlot[Slot.TOP]?.firstOrNull()
    val mid: Item? = bySlot[Slot.MID]?.firstOrNull()
    val outer: Item? = bySlot[Slot.OUTER]?.firstOrNull()
    val bottom: Item? = bySlot[Slot.BOTTOM]?.firstOrNull()
    val shoes: Item? = bySlot[Slot.SHOES]?.firstOrNull()
    val items: List<Item> = placed.map { it.item }
    val core: List<Placed> = placed.filter { it.slot.isCore }
    val accessories: List<Placed> = placed.filterNot { it.slot.isCore }
    val itemIds: List<String> = items.map { it.id }
    val signature: String = io.github.aristidisg.ensemble.model.Outfit.signatureOf(itemIds)

    /** Outer is closed when it is a warm coat, can't be worn open, or it is cold. */
    val outerClosed: Boolean = outer?.let { !wornOpen(it, ctx) } ?: false

    /**
     * A pullover mid layer (hoodie, crewneck, sweatshirt) covers the top except collar and hem;
     * only pieces that open (cardigan, overshirt, zip) let it show.
     */
    val midClosed: Boolean = mid?.let { !it.layering.canWearOpen } ?: false

    /** True when the top barely shows (a tee under a hoodie or a closed coat). */
    val topHidden: Boolean get() = top != null && (midClosed || (outer != null && outerClosed))

    fun areaWeight(slot: Slot): Double = when (slot) {
        Slot.OUTER -> if (outerClosed) Area.OUTER_CLOSED else Area.OUTER_OPEN
        Slot.TOP -> when {
            outer == null && mid == null -> Area.TOP_ALONE
            midClosed && outer != null && outerClosed -> Area.TOP_FULLY_HIDDEN
            midClosed || (outer != null && outerClosed) -> Area.TOP_UNDER_CLOSED
            else -> Area.TOP_UNDER_OPEN
        }
        Slot.MID -> if (outer != null && outerClosed) Area.MID_UNDER_CLOSED else Area.MID
        Slot.BOTTOM -> Area.BOTTOM
        Slot.SHOES -> Area.SHOES
        else -> Area.accessory(slot)
    }

    /** True when a piece in [slot] reads in the outfit (not just a collar under a closed layer). */
    fun isVisible(slot: Slot): Boolean = !slot.isCore || slot == Slot.SHOES || areaWeight(slot) >= Area.VISIBLE

    /** Raw slot weights normalized to 1 for the outfit. */
    val pieceArea: Map<String, Double> = run {
        val raw = placed.associate { it.item.id to areaWeight(it.slot) }
        val total = raw.values.sum().takeIf { it > 0 } ?: 1.0
        raw.mapValues { it.value / total }
    }

    val colors: List<LookColor> = placed.flatMap { p ->
        val a = pieceArea.getValue(p.item.id)
        val shares = p.item.colors.sumOf { it.share }.takeIf { it > 0 } ?: 1.0
        p.item.colors.map { c ->
            LookColor(p.item, p.slot, colorInfo(c.hex, c.family), a * c.share / shares)
        }
    }

    val chromaticColors: List<LookColor> = colors.filter { it.chromatic }

    /** The most visible color of an item. */
    fun dominant(item: Item?): ColorInfo? = item?.primaryColor?.let { colorInfo(it.hex, it.family) }

    /** Upper-body dominant color: closed outer, else the largest visible of outer/mid/top. */
    val upperDominant: ColorInfo? get() {
        if (outer != null && outerClosed) return dominant(outer)
        val ups = listOfNotNull(outer, mid, top)
        return dominant(ups.maxByOrNull { pieceArea[it.id] ?: 0.0 })
    }

    /** Area share per color family. */
    val familyArea: Map<ColorFamily, Double> = colors.groupBy { it.info.family }.mapValues { e -> e.value.sumOf { it.area } }

    /** Area-weighted mean formality of the core pieces. */
    val meanFormality: Double = run {
        val c = core.map { it.item to (pieceArea[it.item.id] ?: 0.0) }
        val w = c.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
        c.sumOf { it.first.formality * it.second } / w
    }

    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, ColorInfo>()

        fun colorInfo(hex: String, family: ColorFamily): ColorInfo =
            cache.getOrPut("$hex|$family") { ColorScience.info(ColorScience.hexToLab(hex), ColorScience.parseHex(hex), family = family) }

        /** An outer layer is worn open unless it is a warm coat, can't open, or it is cold. */
        fun wornOpen(outer: Item, ctx: EngineContext): Boolean =
            TaxonomyTable.of(outer.subcategory).outer != OuterWeight.WARM && outer.layering.canWearOpen && ctx.weather.feelsMin >= 12.0

        fun of(pieces: List<OutfitPiece>, ctx: EngineContext): Look? {
            val placed = pieces.map { p -> Placed(ctx.items[p.itemId] ?: return null, p.slot) }
            return Look(placed, ctx)
        }
    }
}

fun Lab.deltaE(other: Lab): Double = ColorScience.deltaE(this, other)
