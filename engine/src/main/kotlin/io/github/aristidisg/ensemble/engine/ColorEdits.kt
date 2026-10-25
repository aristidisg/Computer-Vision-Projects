package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.ColorRole
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemColor

/**
 * Hand edits to a piece's colors. Every color keeps the exact code the user typed; Lab and the
 * family are recomputed from it, so the rules measure exactly that color.
 */
object ColorEdits {
    const val MAX_COLORS = 6
    private const val SECONDARY_SHARE = 0.2

    /** Replaces color [index] with [input] ("#fff", "1F2A44"); null when the code is invalid. */
    fun setHex(item: Item, index: Int, input: String): Item? {
        val hex = ColorScience.normalizeHex(input) ?: return null
        if (index !in item.colors.indices) return null
        return apply(item, item.colors.mapIndexed { i, c -> if (i == index) KMeans.itemColor(hex, c.share, c.role) else c })
    }

    fun setShare(item: Item, index: Int, share: Double): Item =
        apply(item, item.colors.mapIndexed { i, c -> if (i == index) c.copy(share = share.coerceIn(0.01, 1.0)) else c })

    /** Adds a color; null when the code is invalid or the piece already has [MAX_COLORS]. */
    fun add(item: Item, input: String, share: Double = SECONDARY_SHARE): Item? {
        val hex = ColorScience.normalizeHex(input) ?: return null
        if (item.colors.size >= MAX_COLORS) return null
        return apply(item, item.colors + KMeans.itemColor(hex, share, ColorRole.SECONDARY))
    }

    /** Removes a color; the last one can't be removed. */
    fun remove(item: Item, index: Int): Item =
        if (item.colors.size <= 1 || index !in item.colors.indices) item else apply(item, item.colors.filterIndexed { i, _ -> i != index })

    /** The biggest share is primary, others are secondary from 20% and accents below. */
    fun withRoles(colors: List<ItemColor>): List<ItemColor> {
        val primary = colors.indices.maxByOrNull { colors[it].share }
        return colors.mapIndexed { i, c ->
            c.copy(role = when {
                i == primary -> ColorRole.PRIMARY
                c.share >= SECONDARY_SHARE -> ColorRole.SECONDARY
                else -> ColorRole.ACCENT
            })
        }
    }

    /**
     * Applies new colors. The auto name and formality follow them only while the user hasn't
     * changed those by hand.
     */
    fun apply(item: Item, colors: List<ItemColor>): Item {
        val roled = withRoles(colors)
        val next = item.copy(colors = roled)
        val name = if (item.name == ItemFactory.autoName(item.subcategory, item.colors)) ItemFactory.autoName(item.subcategory, roled) else item.name
        val formality = if (item.formality == ItemFactory.formality(item)) ItemFactory.formality(next) else item.formality
        return next.copy(name = name, formality = formality)
    }

    /** "Same as…": every tag from [source], keeping [target]'s identity, colors, photos and history. */
    fun sameAs(target: Item, source: Item): Item {
        val copied = source.copy(
            id = target.id,
            colors = target.colors,
            photos = target.photos,
            status = target.status,
            flags = target.flags,
            laundry = source.laundry.copy(wearsSinceWash = target.laundry.wearsSinceWash),
            meta = source.meta.copy(price = target.meta.price, purchaseEpochDay = target.meta.purchaseEpochDay, notes = target.meta.notes),
            createdEpochDay = target.createdEpochDay,
            updatedAtMillis = target.updatedAtMillis,
        )
        val formality = if (source.formality == ItemFactory.formality(source)) ItemFactory.formality(copied) else source.formality
        return copied.copy(name = ItemFactory.autoName(copied.subcategory, copied.colors), formality = formality)
    }
}
