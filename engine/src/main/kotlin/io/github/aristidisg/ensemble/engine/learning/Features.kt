package io.github.aristidisg.ensemble.engine.learning

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.rules.ColorPairRules
import io.github.aristidisg.ensemble.engine.rules.ColorRules
import io.github.aristidisg.ensemble.engine.rules.FormalityRules
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.StyleRules
import io.github.aristidisg.ensemble.engine.tables.WarmthTable
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.WhyNot
import kotlin.math.abs
import kotlin.math.roundToInt

/** Feature groups for the learned score (SPEC 7, "Math"). */
enum class FeatureGroup(val weight: Double) {
    ITEMS(0.20), PAIRS(0.25), COLORS(0.20), STYLE(0.15), SIL_SHOES(0.10), OTHER(0.10)
}

/** Extracts named feature keys from an outfit. Pure and deterministic. */
object Features {

    fun groupOf(key: String): FeatureGroup = when (key.substringBefore(':')) {
        "item" -> FeatureGroup.ITEMS
        "pair" -> FeatureGroup.PAIRS
        "fam", "harmony", "accent", "contrast" -> FeatureGroup.COLORS
        "style", "stylemix" -> FeatureGroup.STYLE
        "sil", "shoe" -> FeatureGroup.SIL_SHOES
        else -> FeatureGroup.OTHER
    }

    /** "A rejection usually means this combination, not this item." */
    fun isCombination(key: String): Boolean = key.substringBefore(':') in setOf("pair", "fam", "stylemix", "patternmix", "sil", "shoe")

    /** Features a "why not?" reason names; empty for reasons with side effects only. */
    fun namedBy(reason: WhyNot, key: String): Boolean {
        val k = key.substringBefore(':')
        return when (reason) {
            WhyNot.COLORS -> k in setOf("fam", "harmony", "accent", "contrast")
            WhyNot.TOO_FORMAL, WhyNot.TOO_CASUAL -> k == "formality"
            WhyNot.FIT -> k == "sil" || k == "shoe"
            WhyNot.TOO_LOUD -> k == "patternmix" || k == "pattern"
            else -> false
        }
    }

    private fun sorted(a: String, b: String) = if (a <= b) "$a|$b" else "$b|$a"

    fun formalityBucket(f: Double): String = when {
        f < 3 -> "casual"
        f < 5 -> "relaxed"
        f < 7 -> "smart"
        else -> "formal"
    }

    fun extract(look: Look, ctx: EngineContext): List<String> {
        val out = LinkedHashSet<String>()
        for (p in look.core) out += "item:${p.item.id}"
        val pairs = ColorPairRules.adjacentPairs(look)
        for ((a, b) in pairs) {
            out += "pair:${sorted(a.id, b.id)}"
            val fa = a.primaryColor?.family; val fb = b.primaryColor?.family
            if (fa != null && fb != null && fa != fb) out += "fam:${sorted(fa.name, fb.name)}"
        }
        out += "harmony:${ColorRules.harmonyLabel(look, ctx).substringBefore(" ·")}"
        ColorRules.hueGroups(look, 0.6, 0.02).forEach { g -> out += "accent:${g.maxBy { it.area }.info.family.name}" }
        val up = look.upperDominant; val down = look.dominant(look.bottom)
        if (up != null && down != null) {
            val dl = abs(up.l - down.l)
            out += "contrast:" + when { dl < 0.15 -> "low"; dl < 0.35 -> "med"; else -> "high" }
        }
        val styles = FormalityRules.topStyles(look)
        styles.forEach { out += "style:${it.name}" }
        if (styles.size == 2) out += "stylemix:${sorted(styles[0].name, styles[1].name)}"
        val upper = look.outer?.takeIf { look.outerClosed } ?: look.mid ?: look.top
        if (upper != null && look.bottom != null) out += "sil:${upper.silhouette.name}|${look.bottom.silhouette.name}"
        if (look.shoes != null && look.bottom != null) out += "shoe:${look.shoes.subcategory.name}|${look.bottom.subcategory.name}"
        val patterned = look.core.map { it.item }.filter { StyleRules.loudness(it) > 0 }
        patterned.forEach { out += "pattern:${it.pattern.type.name}" }
        if (patterned.size >= 2) out += "patternmix:${sorted(patterned[0].pattern.type.name, patterned[1].pattern.type.name)}"
        out += "formality:${ctx.scenario.id}:${formalityBucket(look.meanFormality)}"
        val (_, range) = StyleRules.formalitySpread(look)
        if (range > 4) out += "formality:highlow"
        out += "layers:${look.core.count { it.slot != io.github.aristidisg.ensemble.model.Slot.SHOES && it.slot != io.github.aristidisg.ensemble.model.Slot.BOTTOM }}|${WarmthTable.label(ctx.weather.feelsMid)}"
        look.core.flatMap { it.item.materials }.toSet().forEach { out += "material:${it.name}|${ctx.season.name}" }
        look.accessories.forEach { out += "acc:${it.item.subcategory.name}|${ctx.scenario.id}" }
        return out.toList()
    }

    /** Pair features that involve one item (for piece thumbs). */
    fun pairsOf(itemId: String, features: List<String>): List<String> =
        features.filter { it.startsWith("pair:") && it.removePrefix("pair:").split('|').contains(itemId) }

    /** Abstract features of an inspiration look: families, harmony, silhouette, style, pattern, shoe type. */
    fun extract(look: InspirationLook): List<String> {
        val out = LinkedHashSet<String>()
        val fams = look.pieces.map { it.family.name }.distinct()
        for (i in fams.indices) for (j in i + 1 until fams.size) out += "fam:${sorted(fams[i], fams[j])}"
        look.styleTags.forEach { out += "style:${it.name}" }
        look.pieces.filter { it.pattern.name != "SOLID" }.forEach { out += "pattern:${it.pattern.name}" }
        val top = look.pieces.firstOrNull { it.subcategory.group.name == "TOPS" || it.subcategory.group.name == "OUTERWEAR" }
        val bottom = look.pieces.firstOrNull { it.subcategory.group.name == "BOTTOMS" }
        val shoes = look.pieces.firstOrNull { it.subcategory.group.name == "SHOES" }
        if (top != null && bottom != null) out += "sil:${top.silhouette.name}|${bottom.silhouette.name}"
        if (shoes != null && bottom != null) out += "shoe:${shoes.subcategory.name}|${bottom.subcategory.name}"
        return out.toList()
    }

    internal fun pct(x: Double) = (x * 100).roundToInt()
}
