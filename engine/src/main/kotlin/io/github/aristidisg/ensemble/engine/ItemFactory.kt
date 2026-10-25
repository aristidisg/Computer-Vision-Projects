package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.Lab
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.AccessoryExtras
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemColor
import io.github.aristidisg.ensemble.model.Laundry
import io.github.aristidisg.ensemble.model.Layering
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Pattern
import io.github.aristidisg.ensemble.model.PatternScale
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory

/** Smart defaults (SPEC 3 step 8): the chosen subcategory pre-fills everything else. */
object ItemFactory {

    fun create(
        id: String,
        sub: Subcategory,
        colors: List<ItemColor>,
        pattern: Pattern = Pattern(),
        materials: List<Material>? = null,
        createdEpochDay: Long = 0,
    ): Item {
        val d = TaxonomyTable.of(sub)
        val mats = materials ?: d.materials
        val base = Item(
            id = id,
            name = autoName(sub, colors),
            subcategory = sub,
            slots = d.slots,
            colors = colors,
            pattern = if (d.graphic && pattern.type == PatternType.SOLID) Pattern(PatternType.GRAPHIC, PatternScale.LARGE, 2) else pattern,
            materials = mats,
            fabricWeight = d.weight,
            warmthClo = clo(d.clo, mats),
            waterResistance = d.water,
            formality = d.formality,
            styleTags = d.tags,
            silhouette = io.github.aristidisg.ensemble.model.Silhouette.REGULAR,
            layering = Layering(d.canWearOpen, d.presentableAlone, d.bulk),
            shoe = d.shoe,
            accessory = accessoryExtras(sub, colors),
            laundry = Laundry(wearsBeforeWash = d.washAfter),
            createdEpochDay = createdEpochDay,
        )
        val named = normalizeColors(base)
        return named.copy(formality = formality(named), seasons = seasons(named))
    }

    /**
     * Re-applies the defaults that depend on colors/pattern/materials after an edit. With
     * [before], a formality or season set the user chose by hand is kept; only values that were
     * still the automatic ones follow the edit.
     */
    fun refreshDerived(item: Item, before: Item? = null): Item = normalizeColors(item).let {
        val autoFormality = before == null || before.formality == formality(before)
        val autoSeasons = before == null || before.seasons == seasons(before)
        it.copy(
            formality = if (autoFormality) formality(it) else it.formality,
            seasons = if (autoSeasons) seasons(it) else it.seasons,
        )
    }

    /** Bumped whenever family matching changes, so stored pieces are renamed once on update. */
    const val COLOR_NAMES_VERSION = 1

    /** Denim color names belong to denim pieces; a dark-blue sweater is navy or blue, not "dark denim". */
    fun isDenim(item: Item): Boolean = Material.DENIM in item.materials

    /**
     * Color families recomputed from each exact color for this piece (denim names only for
     * denim), with the automatic name following when the user hasn't typed one.
     */
    fun normalizeColors(item: Item): Item {
        val denim = isDenim(item)
        val colors = item.colors.map { c ->
            val f = ColorScience.nearestFamily(Lab(c.l, c.a, c.b), denim)
            if (f == c.family) c else c.copy(family = f)
        }
        if (colors == item.colors) return item
        val auto = item.name == autoName(item.subcategory, item.colors)
        return item.copy(colors = colors, name = if (auto) autoName(item.subcategory, colors) else item.name)
    }

    /**
     * Moves a piece to another subcategory: its defaults (slots, warmth, materials, formality,
     * layering...) are re-applied while its identity, colors, pattern, photos, notes, status,
     * flags and wear count stay. A name the user typed is kept.
     */
    fun recategorize(item: Item, sub: Subcategory): Item {
        val base = create(item.id, sub, item.colors, item.pattern, createdEpochDay = item.createdEpochDay)
        val customName = item.name != autoName(item.subcategory, item.colors)
        return base.copy(
            name = if (customName) item.name else base.name,
            photos = item.photos, meta = item.meta, flags = item.flags, status = item.status,
            laundry = base.laundry.copy(wearsSinceWash = item.laundry.wearsSinceWash),
            silhouette = item.silhouette, updatedAtMillis = item.updatedAtMillis,
        )
    }

    /** Slots back to the subcategory's defaults. */
    fun resetSlots(item: Item): Item = item.copy(slots = TaxonomyTable.of(item.subcategory).slots)

    fun autoName(sub: Subcategory, colors: List<ItemColor>): String {
        val color = colors.maxByOrNull { it.share }?.family?.label
        val noun = sub.label.replaceFirstChar { it.lowercase() }
        return if (color == null) sub.label else "$color $noun".replaceFirstChar { it.uppercase() }
    }

    /** Default formality from the subcategory; logos -1, dark solid tailoring +1, dark/raw jeans +1. */
    fun formality(item: Item): Int {
        val d = TaxonomyTable.of(item.subcategory)
        var f = d.formality
        val primary = item.primaryColor
        val dark = primary != null && primary.l < 0.4
        if (item.pattern.type == PatternType.LOGO || item.pattern.type == PatternType.GRAPHIC) f -= 1
        if (d.tailored && dark && !item.pattern.isPatterned) f += 1
        if (item.subcategory == Subcategory.JEANS && primary != null &&
            (primary.family == ColorFamily.DARK_DENIM || primary.family == ColorFamily.NAVY || primary.family == ColorFamily.BLACK)
        ) f += 1
        return f.coerceIn(1, 10)
    }

    /** Seasons derived from warmth and material; editable afterwards. */
    fun seasons(item: Item): Set<Season> {
        TaxonomyTable.of(item.subcategory).seasons?.let { return it }
        val m = item.materials
        return when {
            Material.LINEN in m || Material.SEERSUCKER in m -> setOf(Season.SPRING, Season.SUMMER)
            Material.TWEED in m || Material.FLANNEL in m || Material.CORDUROY in m ||
                item.fabricWeight == FabricWeight.HEAVY || item.warmthClo >= 0.5 -> setOf(Season.AUTUMN, Season.WINTER)
            else -> Season.entries.toSet()
        }
    }

    private fun clo(base: Double, materials: List<Material>): Double = when {
        Material.LINEN in materials || Material.SEERSUCKER in materials -> base * 0.85
        Material.FLANNEL in materials || Material.TWEED in materials || Material.CASHMERE in materials -> base * 1.1
        else -> base
    }

    private fun accessoryExtras(sub: Subcategory, colors: List<ItemColor>): AccessoryExtras? {
        val d = TaxonomyTable.of(sub)
        val leather = Material.LEATHER in d.materials || Material.SUEDE in d.materials
        val slot = d.slots.first()
        val wantsLeather = leather && (slot == Slot.BELT || slot == Slot.SHOES || slot == Slot.BAG)
        if (!wantsLeather && slot !in setOf(Slot.WATCH, Slot.JEWELRY, Slot.EYEWEAR, Slot.BELT)) return null
        return AccessoryExtras(leatherColor = if (wantsLeather) colors.maxByOrNull { it.share }?.family else null)
    }
}
