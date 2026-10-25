package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.tables.StyleTables
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import kotlin.math.abs

/**
 * Pairwise compatibility of two pieces in different slots (generator step 3): color, formality,
 * pattern, style, silhouette, material and the shoe table. Memoized per generate call; computing
 * the full cache for 300 items takes milliseconds, so it is rebuilt rather than persisted.
 */
class PairCache(private val ctx: EngineContext) {
    private val memo = HashMap<String, Double>()

    fun score(a: Placed, b: Placed): Double {
        val k = if (a.item.id < b.item.id) "${a.item.id}/${a.slot}|${b.item.id}/${b.slot}" else "${b.item.id}/${b.slot}|${a.item.id}/${a.slot}"
        return memo.getOrPut(k) { compute(a, b) }
    }

    private fun compute(a: Placed, b: Placed): Double {
        val parts = ArrayList<Double>(7)
        parts += color(a, b)
        parts += 1.0 - abs(a.item.formality - b.item.formality) / 8.0
        val pa = a.item.pattern; val pb = b.item.pattern
        if (pa.isPatterned && pb.isPatterned) parts += if (pa.scale == pb.scale) 0.3 else 0.6
        if (a.item.styleTags.isNotEmpty() && b.item.styleTags.isNotEmpty()) {
            parts += a.item.styleTags.maxOf { x -> b.item.styleTags.maxOf { y -> StyleTables.style(x, y) } }
        }
        val (upper, lower) = when {
            b.slot == Slot.BOTTOM && a.slot in setOf(Slot.TOP, Slot.MID, Slot.OUTER) -> a to b
            a.slot == Slot.BOTTOM && b.slot in setOf(Slot.TOP, Slot.MID, Slot.OUTER) -> b to a
            else -> null to null
        }
        if (upper != null && lower != null) {
            val street = StyleTag.STREETWEAR in upper.item.styleTags && (ctx.settings.styleWeights[StyleTag.STREETWEAR] ?: 0.0) >= 0.9
            parts += StyleTables.silhouette(upper.item.silhouette, lower.item.silhouette, street)
        }
        val shoe = listOf(a, b).firstOrNull { it.slot == Slot.SHOES }
        val bottom = listOf(a, b).firstOrNull { it.slot == Slot.BOTTOM }
        if (shoe != null && bottom != null) {
            val sk = TaxonomyTable.of(shoe.item.subcategory).shoeKind
            val tk = TaxonomyTable.of(bottom.item.subcategory).trouserKind
            if (sk != null && tk != null) parts += StyleTables.shoeTrouser(sk, tk, shoe.item.subcategory.name == "OXFORDS")
        }
        val tech = listOf(a, b).any { Material.NYLON_TECH in it.item.materials }
        val tailored = listOf(a, b).any { TaxonomyTable.of(it.item.subcategory).tailored }
        if (tech && tailored) parts += 0.4
        var s = parts.average()
        val learned = ctx.prefs.value("pair:" + listOf(a.item.id, b.item.id).sorted().joinToString("|"))?.value ?: 0.0
        s += 0.1 * learned
        return s.coerceIn(0.0, 1.0)
    }

    private fun color(a: Placed, b: Placed): Double {
        val ca = a.item.primaryColor ?: return 0.75
        val cb = b.item.primaryColor ?: return 0.75
        ColorTables.affinity(ca.family, cb.family)?.let { return it }
        val ia = Look.colorInfo(ca.hex, ca.family); val ib = Look.colorInfo(cb.hex, cb.family)
        if (ia.neutralness >= 0.8 || ib.neutralness >= 0.8) return 0.85
        if (ca.family == cb.family) {
            val de = ColorScience.deltaE(ia.lab, ib.lab)
            return if (de in 0.03..0.08) 0.4 else 0.85
        }
        val d = ColorScience.hueDistance(ia.hue, ib.hue)
        return when {
            d <= 40 -> 0.85
            d >= 150 -> if (ctx.boldness >= 0.5) 0.8 else 0.65
            else -> 0.55
        }
    }
}
