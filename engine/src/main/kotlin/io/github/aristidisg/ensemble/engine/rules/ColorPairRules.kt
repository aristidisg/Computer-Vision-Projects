package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import kotlin.math.abs

/** Color rules C7-C11 (SPEC 4.4). */
object ColorPairRules {
    private val G = RuleGroup.COLOR

    /** Adjacent core slot pairs used by C7 and by learned pair features. */
    fun adjacentPairs(look: Look): List<Pair<Item, Item>> = listOfNotNull(
        pair(look.top ?: look.mid, look.bottom),
        pair(look.outer, look.bottom),
        pair(look.bottom, look.shoes),
        pair(look.outer, look.top ?: look.mid),
        pair(look.outer, look.mid).takeIf { look.top != null },
        pair(look.mid, look.top),
    )

    /** C7 weight when a pair is a known clash: enough to sink an outfit the hue math likes. */
    private const val CLASH_WEIGHT = 4.0

    private fun pair(a: Item?, b: Item?) = if (a != null && b != null) a to b else null

    fun c7ClassicPairs(look: Look, ctx: EngineContext): RuleResult? {
        val scored = adjacentPairs(look).mapNotNull { (a, b) ->
            val fa = a.primaryColor?.family ?: return@mapNotNull null
            val fb = b.primaryColor?.family ?: return@mapNotNull null
            ColorTables.affinity(fa, fb)?.let { Triple(fa, fb, it) }
        }
        if (scored.isEmpty()) return null
        val mean = scored.sumOf { it.third } / scored.size
        val best = scored.maxBy { it.third }
        val worst = scored.minBy { it.third }
        return when {
            ColorTables.clashes.containsKey(setOf(worst.first, worst.second)) ->
                res("C7", G, worst.third, CLASH_WEIGHT, Explain.UNUSUAL_PAIR, "a" to worst.first.label, "b" to worst.second.label.lowercase())
            worst.third <= 0.5 -> res("C7", G, mean, 1.0, Explain.UNUSUAL_PAIR, "a" to worst.first.label, "b" to worst.second.label.lowercase())
            best.third >= 0.9 -> res("C7", G, mean, 1.0, Explain.CLASSIC_PAIR, "a" to best.first.label, "b" to best.second.label.lowercase())
            else -> res("C7", G, mean)
        }
    }

    fun c8DenimOnDenim(look: Look): RuleResult? {
        val denims = look.core.filter { it.slot != Slot.SHOES && (Material.DENIM in it.item.materials || it.item.primaryColor?.family?.isDenim == true) }
        if (denims.size < 2) return null
        val a = look.dominant(denims[0].item) ?: return null
        val b = look.dominant(denims[1].item) ?: return null
        val ok = abs(a.l - b.l) >= 0.15 || ColorScience.deltaE(a.lab, b.lab) <= 0.03
        return if (ok) res("C8", G, 1.0, 1.0, Explain.DENIM_OK) else res("C8", G, 0.2, 1.5, Explain.DENIM_CLASH)
    }

    fun c9NearMiss(look: Look): RuleResult? {
        val pairs = listOfNotNull(pair(look.top, look.bottom), pair(look.outer, look.bottom))
        for ((x, y) in pairs) {
            val a = look.dominant(x) ?: continue; val b = look.dominant(y) ?: continue
            val de = ColorScience.deltaE(a.lab, b.lab)
            if (a.family == b.family && de in 0.03..0.08) {
                return res("C9", G, 0.25, 1.5, Explain.NEAR_MISS, "a" to a.family.label)
            }
        }
        return null
    }

    private fun isBlackLeather(f: ColorFamily) = f == ColorFamily.BLACK || f == ColorFamily.CHARCOAL

    /** Leather color of a piece, when it is leather (shoes, belt, bag, watch strap). */
    fun leatherColor(item: Item, slot: Slot): ColorFamily? {
        item.accessory?.leatherColor?.let { return it }
        return when (slot) {
            Slot.SHOES -> item.shoe?.takeIf { it.upper == ShoeUpper.LEATHER || it.upper == ShoeUpper.SUEDE }?.let { item.primaryColor?.family }
            Slot.BELT -> item.takeIf { it.subcategory == Subcategory.LEATHER_BELT }?.primaryColor?.family
            Slot.BAG -> item.takeIf { Material.LEATHER in it.materials }?.primaryColor?.family
            else -> null
        }
    }

    /** C10: shoes, belt, bag and watch strap in the same leather family. Also serves SH4. */
    fun c10Leather(look: Look, ctx: EngineContext, id: String = "C10", group: RuleGroup = G): RuleResult? {
        val leathers = look.placed.mapNotNull { p -> leatherColor(p.item, p.slot)?.let { p to it } }
        if (leathers.size < 2) return null
        val shoes = look.shoes
        val relaxed = look.meanFormality <= 4 || shoes == null ||
            shoes.shoe?.upper in setOf(ShoeUpper.SUEDE, ShoeUpper.CANVAS, ShoeUpper.KNIT, ShoeUpper.SYNTHETIC) ||
            shoes.subcategory.name.contains("SNEAKERS") || shoes.subcategory == Subcategory.RUNNING_SHOES
        val strict = ctx.scenario.formalityTarget >= 6 || look.meanFormality >= 6
        val labs = leathers.map { (p, f) -> Triple(p, f, Look.colorInfo(ColorTables.family(f).hex, f)) }
        val clash = labs.indices.any { i -> (i + 1 until labs.size).any { j ->
            val a = labs[i]; val b = labs[j]
            isBlackLeather(a.second) != isBlackLeather(b.second) || abs(a.third.l - b.third.l) > 0.12
        } }
        if (!clash) {
            return res(id, group, 1.0, 1.0, Explain.LEATHER_MATCH, "color" to leathers.first().second.label.lowercase())
        }
        val belt = leathers.firstOrNull { it.first.slot == Slot.BELT }?.second
        val shoe = leathers.firstOrNull { it.first.slot == Slot.SHOES }?.second
        val score = when { strict && !relaxed -> 0.05; strict -> 0.4; relaxed -> 0.7; else -> 0.4 }
        return res(id, group, score, if (strict) 2.0 else 1.0, Explain.LEATHER_CLASH,
            "belt" to (belt ?: leathers[0].second).label, "shoes" to (shoe ?: leathers[1].second).label.lowercase())
    }

    fun c11Metals(look: Look, ctx: EngineContext): RuleResult? {
        val metals = look.items.mapNotNull { it.accessory?.metalTone }.toSet()
        if (metals.isEmpty()) return null
        if (metals.size == 1) return res("C11", G, 1.0, 0.5, Explain.METALS_MATCH, "metal" to metals.first().label.lowercase())
        return res("C11", G, if (ctx.settings.allowMixedMetals) 0.9 else 0.6, 0.5, Explain.METALS_MIXED)
    }
}
