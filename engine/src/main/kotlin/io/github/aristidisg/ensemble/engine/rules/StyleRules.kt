package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.StyleTables
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.ShoeProfile
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.StyleTag
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Patterns (P1-P4), materials (M1-M5) and silhouette (SPEC 5.2, 5.3, 5.5). */
object StyleRules {
    private val P = RuleGroup.PATTERN_TEXTURE
    private val S = RuleGroup.SILHOUETTE

    private val textured = setOf(Material.TWEED, Material.CORDUROY)

    /** Effective loudness: textures (cable knit, tweed, corduroy) count as low-loudness patterns (P4). */
    fun loudness(item: Item): Int = when {
        item.pattern.isPatterned -> maxOf(1, item.pattern.loudness)
        item.materials.any { it in textured } || item.subcategory.name == "CHUNKY_KNIT" -> 1
        else -> 0
    }

    fun patterns(look: Look, ctx: EngineContext): RuleResult {
        val pieces = look.core.map { it.item }.filter { loudness(it) > 0 }
        val printed = pieces.filter { it.pattern.isPatterned }
        val loud = pieces.count { loudness(it) >= 3 }
        val graphics = look.core.count { it.item.pattern.type == PatternType.GRAPHIC || (it.item.pattern.type == PatternType.LOGO && it.item.pattern.scale == io.github.aristidisg.ensemble.model.PatternScale.LARGE) }
        val bold = ctx.boldness >= 0.7
        if (graphics > 1) return res("P3", P, 0.2, 1.5, Explain.GRAPHIC_CLASH)
        if (!bold && (loud > 1 || printed.size > 2)) return res("P1", P, 0.15, 1.5, Explain.PATTERN_CLASH)
        if (printed.size >= 2) {
            val a = printed[0]; val b = printed[1]
            val differ = a.pattern.scale != b.pattern.scale || a.pattern.type != b.pattern.type
            val shareColor = a.colors.any { ca -> b.colors.any { it.family == ca.family } }
            return if (differ && shareColor && a.pattern.scale != b.pattern.scale) res("P2", P, if (bold) 0.85 else 0.7, 1.0, Explain.PATTERN_MIX_OK)
            else res("P2", P, 0.25, 1.5, Explain.PATTERN_CLASH)
        }
        if (printed.size == 1) return res("P1", P, 1.0, 1.0, Explain.PATTERN_ONE)
        return res("P1", P, 0.9)
    }

    fun materials(look: Look, ctx: EngineContext): List<RuleResult> {
        val out = mutableListOf<RuleResult>()
        val core = look.core.map { it.item }
        // M1 season fit
        val summer = ctx.season == Season.SUMMER || ctx.weather.feelsMax >= 24
        val winter = ctx.season == Season.WINTER || ctx.weather.feelsMax <= 10
        val summerFabric = core.firstOrNull { Material.LINEN in it.materials || Material.SEERSUCKER in it.materials }
        val winterFabric = core.firstOrNull { it.materials.any { m -> m in setOf(Material.FLANNEL, Material.TWEED, Material.CORDUROY) } || (Material.WOOL in it.materials && it.fabricWeight == FabricWeight.HEAVY) }
        when {
            summerFabric != null && winter -> out += res("M1", P, 0.3, 1.0, Explain.FABRIC_OFF_SEASON, "material" to summerFabric.materials.first().label)
            winterFabric != null && summer -> out += res("M1", P, 0.3, 1.0, Explain.FABRIC_OFF_SEASON, "material" to winterFabric.materials.first().label)
            (summerFabric != null && summer) || (winterFabric != null && winter) -> out += res("M1", P, 1.0, 1.0, Explain.FABRIC_SEASON)
        }
        // M2 visual weight balance
        val heavyShoes = look.shoes?.shoe?.profile == ShoeProfile.CHUNKY && look.shoes.subcategory.name.contains("BOOTS")
        val lightBottom = look.bottom?.let { it.fabricWeight == FabricWeight.LIGHT } ?: false
        val spread = core.maxOf { it.fabricWeight.ordinal } - core.minOf { it.fabricWeight.ordinal }
        out += if ((heavyShoes && lightBottom) || spread >= 2) res("M2", P, 0.4, 1.0, Explain.WEIGHT_MISMATCH) else res("M2", P, 1.0, 0.5)
        // M3 sheen consistency in formal outfits
        if (look.meanFormality >= 7) {
            val tech = core.any { Material.NYLON_TECH in it.materials }
            out += res("M3", P, if (tech) 0.4 else 1.0, 0.5)
        }
        // M4 tonal outfits with varied textures
        val fams = core.mapNotNull { it.primaryColor?.family }.toSet()
        if (core.size >= 3 && fams.size <= 2) {
            val mats = core.flatMap { it.materials }.toSet().size
            out += if (mats >= 3) res("M4", P, 1.0, 1.0, Explain.TONAL_TEXTURE) else res("M4", P, 0.75, 0.5)
        }
        // M5 technical fabrics with tailoring
        val tailored = core.any { TaxonomyTable.of(it.subcategory).tailored }
        val tech = core.any { Material.NYLON_TECH in it.materials }
        if (tailored && tech) {
            val techwear = look.items.count { StyleTag.TECHWEAR in it.styleTags } >= 2
            out += res("M5", P, if (techwear) 0.9 else 0.3, 1.0, if (techwear) null else Explain.TECH_WITH_TAILORING)
        }
        return out
    }

    fun silhouette(look: Look, ctx: EngineContext): RuleResult? {
        val upper = listOfNotNull(look.outer?.takeIf { look.outerClosed }, look.mid, look.top).firstOrNull() ?: return null
        val bottom = look.bottom ?: return null
        val streetwear = (ctx.settings.styleWeights[StyleTag.STREETWEAR] ?: 0.0) >= 0.9
        var s = StyleTables.silhouette(upper.silhouette, bottom.silhouette, streetwear)
        s += ctx.prefs.silhouetteAdjust(upper.silhouette, bottom.silhouette)
        // Outer slimmer than the mid layer's bulk
        val mid = look.mid; val outer = look.outer
        if (mid != null && outer != null && outer.silhouette == Silhouette.SLIM && mid.layering.bulk >= 2) s -= 0.3
        // Chunky shoes with slim trousers, or with relaxed/oversized
        if (look.shoes?.shoe?.profile == ShoeProfile.CHUNKY) {
            s += when (bottom.silhouette) { Silhouette.SLIM -> -0.15; Silhouette.RELAXED, Silhouette.OVERSIZED -> 0.1; else -> 0.0 }
        }
        val score = s.coerceIn(0.0, 1.0)
        return when {
            score >= 0.9 && upper.silhouette in setOf(Silhouette.RELAXED, Silhouette.OVERSIZED) && bottom.silhouette <= Silhouette.REGULAR ->
                res("SIL", S, score, 1.0, Explain.SILHOUETTE)
            score < 0.6 -> res("SIL", S, score, 1.0, Explain.SILHOUETTE_OFF)
            else -> res("SIL", S, score)
        }
    }

    /** Area-weighted formality standard deviation and range for F1. */
    fun formalitySpread(look: Look): Pair<Double, Int> {
        val core = look.core.map { it.item to (look.pieceArea[it.item.id] ?: 0.0) }
        val w = core.sumOf { it.second }.takeIf { it > 0 } ?: return 0.0 to 0
        val mean = core.sumOf { it.first.formality * it.second } / w
        val variance = core.sumOf { (it.first.formality - mean).let { d -> d * d } * it.second } / w
        val range = core.maxOf { it.first.formality } - core.minOf { it.first.formality }
        return sqrt(variance) to range
    }

    fun gaussian(d: Double, sigma: Double) = exp(-(d * d) / (2 * sigma * sigma))

    internal fun abs2(x: Double) = abs(x)
}
