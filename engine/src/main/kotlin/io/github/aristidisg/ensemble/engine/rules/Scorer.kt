package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.learning.Features
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Strictness
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A fully scored outfit with its explanation. */
@Serializable
data class ScoredOutfit(
    val pieces: List<OutfitPiece>,
    val signature: String,
    val total: Double,
    val groupScores: Map<RuleGroup, Double>,
    val results: List<RuleResult>,
    val why: List<RuleResult>,
    val tradeoffs: List<RuleResult>,
    val harmony: String,
    val weatherLine: RuleResult?,
    val sockHint: RuleResult?,
    val features: List<String>,
    val newIdea: Boolean = false,
    val violation: Violation? = null,
) {
    val matchPct: Int get() = (total * 100).roundToInt()
    val itemIds: List<String> get() = pieces.map { it.itemId }
}

/** Runs every soft rule, combines groups (SPEC 5.11) and picks the explanation lines. */
object Scorer {

    fun allRules(look: Look, ctx: EngineContext): List<RuleResult> {
        val r = mutableListOf<RuleResult?>()
        r += ColorRules.c1Harmony(look, ctx)
        r += ColorRules.c2AccentCount(look, ctx)
        r += ColorRules.c3Proportion(look, ctx)
        r += ColorRules.c4ValueContrast(look, ctx)
        r += ColorRules.c5ChromaBalance(look, ctx)
        r += ColorRules.c6Temperature(look, ctx)
        r += ColorPairRules.c7ClassicPairs(look, ctx)
        r += ColorPairRules.c8DenimOnDenim(look)
        r += ColorPairRules.c9NearMiss(look)
        r += ColorPairRules.c10Leather(look, ctx)
        r += ColorPairRules.c11Metals(look, ctx)
        r += PaletteRules.c12Personal(look, ctx)
        r += PaletteRules.c13ScenarioPalette(look, ctx)
        r += PaletteRules.c14SeasonalNudge(look, ctx)
        r += PaletteRules.c15WeddingWhite(look, ctx)
        r += StyleRules.patterns(look, ctx)
        r += StyleRules.materials(look, ctx)
        r += StyleRules.silhouette(look, ctx)
        r += FormalityRules.f1Coherence(look, ctx)
        r += FormalityRules.f2Target(look, ctx)
        r += FormalityRules.f3Conflicts(look, ctx)
        r += FormalityRules.o1Coherence(look, ctx)
        r += FormalityRules.o2Scenario(look, ctx)
        r += FormalityRules.o3UserStyle(look, ctx)
        r += FormalityRules.o4Signature(look)
        r += ShoeAccessoryRules.shoes(look, ctx)
        r += ShoeAccessoryRules.accessories(look, ctx)
        r += WeatherRules.w1Warmth(look, ctx)
        r += WeatherRules.w2Swing(look, ctx)
        r += WeatherRules.w3Rain(look, ctx)
        r += WeatherRules.w4Wind(look, ctx)
        r += WeatherRules.w5Sun(look, ctx)
        r += WeatherRules.w6Heat(look, ctx)
        r += WeatherRules.w7Snow(look, ctx)
        r += RotationRules.r1ItemRotation(look, ctx)
        r += RotationRules.r2NearDuplicate(look, ctx)
        r += RotationRules.r4Freshness(look, ctx)
        r += RotationRules.r5Forgotten(look, ctx)
        r += RotationRules.r6PlanConflicts(look, ctx)
        r += RotationRules.r7Laundry(look)
        return r.filterNotNull()
            .filter { ctx.ruleOn(it.id) }
            .map { it.copy(weight = it.weight * ctx.ruleWeight(it.id)) }
    }

    fun groupWeight(g: RuleGroup, ctx: EngineContext): Double {
        if (g == RuleGroup.LEARNED) return ctx.prefs.learnedWeight
        var w = ctx.ruleWeight("group:${g.name}", g.defaultWeight)
        val strict = ctx.settings.strictness
        if (g == RuleGroup.PATTERN_TEXTURE || g == RuleGroup.FORMALITY_STYLE) {
            w *= when (strict) { Strictness.CLASSIC -> 1.25; Strictness.ADVENTUROUS -> 0.8; else -> 1.0 }
        }
        return w
    }

    fun score(look: Look, ctx: EngineContext, newIdea: Boolean = false, features: List<String>? = null): ScoredOutfit {
        val results = allRules(look, ctx).toMutableList()
        val feats = features ?: Features.extract(look, ctx)
        ctx.prefs.learnedScore(feats)?.let { s ->
            val like = learnedLikeLine(feats, ctx)
            results += if (like != null && s >= 0.55) like.copy(score = s)
            else res("LEARN", RuleGroup.LEARNED, s, 1.0, if (s < 0.4) Explain.LEARNED_DISLIKE else null)
        }
        val groups = results.filter { it.weight > 0 }.groupBy { it.group }.mapValues { (_, rs) ->
            rs.sumOf { it.score * it.weight } / rs.sumOf { it.weight }
        }
        val weights = groups.keys.associateWith { groupWeight(it, ctx) }
        val wsum = weights.values.sum().takeIf { it > 0 } ?: 1.0
        var total = groups.entries.sumOf { (g, s) -> s * weights.getValue(g) } / wsum
        for ((rule, w) in CustomRules.soft(look, ctx)) {
            total += 0.1 * w
            results += res("CUSTOM", RuleGroup.FORMALITY_STYLE, if (w > 0) 1.0 else 0.0, 0.0, Explain.CUSTOM_RULE, "name" to rule.name)
        }
        CollectionRules.influence(look, ctx)?.let { (bonus, line) -> total += bonus; results += line }
        val comfort = look.items.mapNotNull { ctx.prefs.comfortPenalty[it.id] }.sum()
        total = calibrate(total - comfort)

        fun impact(r: RuleResult) = weights[r.group]?.let { it * r.weight } ?: (r.weight * 0.1)
        val explained = results.filter { it.key != null && it.id != "SH7" }
        val why = explained.filter { it.score > 0.5 && it.key !in negativeKeys }
            .sortedWith(compareByDescending<RuleResult> { impact(it) * (it.score - 0.5) + when (it.id) { "CUSTOM" -> 1.0; "SAVED" -> 0.5; else -> 0.0 } }.thenBy { it.id }).take(3)
        val trade = explained.filter { it.score < 0.5 || it.key in negativeKeys && it.score < 0.8 }
            .sortedWith(compareByDescending<RuleResult> { impact(it) * (0.5 - it.score + 0.5) }.thenBy { it.id }).take(2)
        val weatherLine = results.firstOrNull { it.key == Explain.WEATHER_LAYER } ?: results.firstOrNull { it.key == Explain.RAIN }
            ?: results.firstOrNull { it.id == "W1" }
        return ScoredOutfit(
            pieces = look.placed.map { OutfitPiece(it.item.id, it.slot) },
            signature = look.signature,
            total = total,
            groupScores = groups,
            results = results,
            why = why,
            tradeoffs = trade,
            harmony = ColorRules.harmonyLabel(look, ctx),
            weatherLine = weatherLine,
            sockHint = results.firstOrNull { it.id == "SH7" },
            features = feats,
            newIdea = newIdea,
        )
    }

    /** Group means sit between 0.6 and 1.0 in practice; stretch that range so Match % separates outfits. */
    fun calibrate(raw: Double): Double = ((raw - CALIBRATION_FLOOR) / (1.0 - CALIBRATION_FLOOR)).coerceIn(0.0, 1.0)

    const val CALIBRATION_FLOOR = 0.6

    private val negativeKeys = setOf(
        Explain.HARMONY_WEAK, Explain.TOO_MANY_ACCENTS, Explain.COLOR_FIGHT, Explain.LOW_CONTRAST, Explain.GREY_MUSH,
        Explain.CHROMA_HEAVY, Explain.UNUSUAL_PAIR, Explain.DENIM_CLASH, Explain.NEAR_MISS, Explain.LEATHER_CLASH,
        Explain.METALS_MIXED, Explain.PERSONAL_COLOR_AVOID, Explain.PALETTE_OFF, Explain.WEDDING_WHITE, Explain.PATTERN_CLASH,
        Explain.GRAPHIC_CLASH, Explain.FABRIC_OFF_SEASON, Explain.WEIGHT_MISMATCH, Explain.TECH_WITH_TAILORING,
        Explain.FORMALITY_OFF, Explain.FORMALITY_MIXED, Explain.CLASSIC_CONFLICT, Explain.STYLE_CLASH, Explain.SILHOUETTE_OFF,
        Explain.SHOE_CLASH, Explain.SHOE_WEATHER_BAD, Explain.SHOE_REST, Explain.SHOE_DARKER, Explain.ACCESSORY_COUNT,
        Explain.BELT_MISSING, Explain.BELT_WRONG, Explain.HEADWEAR_OFF, Explain.TOO_COLD, Explain.TOO_WARM,
        Explain.RAIN_UNPROTECTED, Explain.ROTATION, Explain.NEAR_DUPLICATE, Explain.SEEN_RECENTLY, Explain.PLANNED_SOON,
        Explain.LAUNDRY_DUE, Explain.LEARNED_DISLIKE,
    )

    /** "You've liked {a} with {b} {n} times" for the strongest liked color-family pair. */
    private fun learnedLikeLine(features: List<String>, ctx: EngineContext): RuleResult? {
        val best = features.filter { it.startsWith("fam:") }
            .mapNotNull { f -> ctx.prefs.value(f)?.let { f to it } }
            .filter { it.second.value > 0.2 && it.second.pos >= 2 }
            .maxByOrNull { it.second.value } ?: return null
        val (a, b) = best.first.removePrefix("fam:").split('|').map { name ->
            io.github.aristidisg.ensemble.model.ColorFamily.valueOf(name).label.lowercase()
        }
        return res("LEARN", RuleGroup.LEARNED, best.second.value, 1.0, Explain.LEARNED_LIKE, "a" to a, "b" to b, "n" to best.second.pos.roundToInt())
    }
}
