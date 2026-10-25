package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorFamily.*
import io.github.aristidisg.ensemble.model.ColorSeason
import io.github.aristidisg.ensemble.model.ScenarioPalette
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Temperature

/** 12-season data: temperature, lightness and chroma ranges plus display swatches (SPEC 4.5). */
data class SeasonData(
    val season: ColorSeason,
    val temperature: Temperature,
    val l: ClosedFloatingPointRange<Double>,
    val c: ClosedFloatingPointRange<Double>,
    val swatches: List<String>,
)

object PersonalColorData {
    private val W = Temperature.WARM; private val N = Temperature.NEUTRAL; private val C = Temperature.COOL

    val seasons: List<SeasonData> = listOf(
        SeasonData(ColorSeason.LIGHT_SPRING, W, 0.65..0.95, 0.03..0.14, listOf("#F6E3B4", "#F7B89A", "#A8D8C8", "#F4C2C2", "#C9E4A6", "#FFD9A0", "#9FC9E8", "#E8D5B5", "#F2A99B", "#B5D99C")),
        SeasonData(ColorSeason.WARM_SPRING, W, 0.55..0.85, 0.06..0.18, listOf("#F2A65A", "#E8C547", "#7FB069", "#E07A5F", "#C19A6B", "#F4D35E", "#5BA08F", "#D98E4E", "#EFC88B", "#B8D86B")),
        SeasonData(ColorSeason.CLEAR_SPRING, N, 0.50..0.85, 0.10..0.25, listOf("#FF6F59", "#2EC4B6", "#FFD23F", "#3A86FF", "#06D6A0", "#F15BB5", "#FFFFFF", "#1B4965", "#F77F00", "#43AA8B")),
        SeasonData(ColorSeason.LIGHT_SUMMER, C, 0.65..0.92, 0.02..0.10, listOf("#C6D8EF", "#E8C6D9", "#B8D4CF", "#D3C4E3", "#A9C7E8", "#F0D6DC", "#9DB4C0", "#E3E7F1", "#B5C9D6", "#CDB4DB")),
        SeasonData(ColorSeason.COOL_SUMMER, C, 0.45..0.80, 0.03..0.12, listOf("#5B7FA6", "#8E7DBE", "#7AA6A0", "#C27BA0", "#36454F", "#A9C7E8", "#6D8299", "#B07BAC", "#4F6D7A", "#D8A7C2")),
        SeasonData(ColorSeason.SOFT_SUMMER, N, 0.40..0.75, 0.02..0.08, listOf("#8B9DAA", "#A68A9B", "#8EA08A", "#B7A99A", "#6E7F80", "#9C8AA5", "#C4B6A6", "#7D8C8E", "#A3A7A8", "#8B7D6B")),
        SeasonData(ColorSeason.SOFT_AUTUMN, N, 0.40..0.72, 0.03..0.10, listOf("#9CAF88", "#C19A6B", "#A0785A", "#8B8C5A", "#C9A27E", "#7E8F7C", "#B5836B", "#D6C7A8", "#6B6B3A", "#A89F80")),
        SeasonData(ColorSeason.WARM_AUTUMN, W, 0.35..0.70, 0.06..0.16, listOf("#B5532E", "#D4A017", "#6B6B3A", "#7B4B2A", "#C19A6B", "#8A3324", "#556B2F", "#CC7722", "#4A2C1D", "#B8860B")),
        SeasonData(ColorSeason.DEEP_AUTUMN, W, 0.22..0.55, 0.05..0.15, listOf("#4A2C1D", "#6D1A2A", "#1F4D2E", "#7B4B2A", "#8A3324", "#3B3B1F", "#B5532E", "#2F4F4F", "#5C4033", "#8B0000")),
        SeasonData(ColorSeason.DEEP_WINTER, C, 0.15..0.55, 0.05..0.20, listOf("#111111", "#1F2A44", "#6D1A2A", "#1F4D2E", "#5B2A5C", "#FAFAFA", "#2F55D4", "#36454F", "#8B0000", "#004953")),
        SeasonData(ColorSeason.COOL_WINTER, C, 0.25..0.90, 0.05..0.20, listOf("#1F2A44", "#FAFAFA", "#2F55D4", "#C71585", "#36454F", "#4B0082", "#008080", "#DC143C", "#C8C8C8", "#5B2A5C")),
        SeasonData(ColorSeason.CLEAR_WINTER, C, 0.20..0.95, 0.10..0.28, listOf("#111111", "#FAFAFA", "#E0115F", "#2F55D4", "#00A86B", "#7F00FF", "#FF0040", "#1F2A44", "#00CED1", "#FFD700")),
    )

    fun of(s: ColorSeason): SeasonData = seasons.first { it.season == s }

    private val nearFace = setOf(Slot.TOP, Slot.MID, Slot.OUTER, Slot.NECK, Slot.HEADWEAR)

    fun appliesTo(slot: Slot, allSlots: Boolean) = allSlots || slot in nearFace

    /** How well one color sits in a season: ranges plus distance to the nearest swatch. */
    fun fit(info: io.github.aristidisg.ensemble.engine.color.ColorInfo, data: SeasonData): Double {
        val tOk = when {
            info.neutralness >= 0.8 -> 1.0
            data.temperature == Temperature.NEUTRAL || info.temperature == Temperature.NEUTRAL -> 0.8
            info.temperature == data.temperature -> 1.0
            else -> 0.3
        }
        val lOk = if (info.l in data.l) 1.0 else 0.4
        val cOk = if (info.c in data.c || info.neutralness >= 0.9) 1.0 else 0.5
        val nearest = data.swatches.minOf { ColorScience.deltaE(info.lab, ColorScience.hexToLab(it)) }
        val swatch = 1.0 - (nearest / 0.2).coerceIn(0.0, 1.0)
        return 0.6 * (tOk + lOk + cOk) / 3.0 + 0.4 * swatch
    }
}

/** Color rules C12-C15 (SPEC 4.4). */
object PaletteRules {
    private val G = RuleGroup.COLOR

    /** C12: only when the module is on; contributes nothing and never explains when off. */
    fun c12Personal(look: Look, ctx: EngineContext): RuleResult? {
        val pc = ctx.settings.personalColor
        if (!pc.enabled || pc.strength <= 0.0) return null
        val cs = look.colors.filter { PersonalColorData.appliesTo(it.slot, pc.applyAllSlots) }
        if (cs.isEmpty()) return null
        val total = cs.sumOf { it.area }
        var score = pc.season?.let { s -> cs.sumOf { PersonalColorData.fit(it.info, PersonalColorData.of(s)) * it.area } / total } ?: 0.7
        val fav = cs.filter { it.info.family in pc.favorites }.sumOf { it.area } / total
        val avoid = cs.filter { it.info.family in pc.avoid }
        score += 0.2 * fav - 0.5 * avoid.sumOf { it.area } / total
        val s = score.coerceIn(0.0, 1.0)
        val w = 1.5 * pc.strength
        return when {
            avoid.isNotEmpty() -> res("C12", G, s, w, Explain.PERSONAL_COLOR_AVOID, "color" to avoid.first().info.family.label)
            pc.season != null && s >= 0.7 -> res("C12", G, s, w, Explain.PERSONAL_COLOR_FIT,
                "color" to cs.maxBy { it.area }.info.family.label, "season" to pc.season!!.label)
            else -> res("C12", G, s, w)
        }
    }

    fun c13ScenarioPalette(look: Look, ctx: EngineContext): RuleResult? {
        val p = ctx.scenario.palette
        val cs = look.colors.filter { it.slot.isCore }
        if (cs.isEmpty()) return null
        val total = cs.sumOf { it.area }
        val meanL = cs.sumOf { it.info.l * it.area } / total
        val chromaticArea = cs.filter { it.info.neutralness < 0.6 }.sumOf { it.area } / total
        val score = when (p) {
            ScenarioPalette.ANY, ScenarioPalette.NO_DOMINANT_WHITE -> return null
            ScenarioPalette.DARK_MUTED -> cs.sumOf { (if (it.info.l < 0.5 && it.info.c < 0.08) 1.0 else if (it.info.c < 0.05) 0.6 else 0.1) * it.area } / total
            ScenarioPalette.CONSERVATIVE, ScenarioPalette.CONSERVATIVE_LEANING -> {
                val ok = setOf(LIGHT_BLUE, WHITE, NAVY, CHARCOAL, MID_GREY, LIGHT_GREY, BLACK, BROWN, CHOCOLATE, STONE, CAMEL, BURGUNDY, ECRU)
                val conservative = cs.filter { it.info.family in ok || it.info.neutralness >= 0.8 }.sumOf { it.area } / total
                val accents = ColorRules.hueGroups(look, 0.6, 0.02).size
                val base = conservative - 0.2 * (accents - 1).coerceAtLeast(0)
                if (p == ScenarioPalette.CONSERVATIVE_LEANING) 0.5 + 0.5 * base else base
            }
            ScenarioPalette.LIGHT -> (meanL - 0.35) / 0.4
            ScenarioPalette.EVENING -> 1.0 - ((meanL - 0.45) / 0.4).coerceAtLeast(0.0) + 0.1 * chromaticArea
        }.coerceIn(0.0, 1.0)
        val name = ctx.scenario.name
        return if (score >= 0.7) res("C13", G, score, 1.0, Explain.PALETTE_FIT, "scenario" to name)
        else res("C13", G, score, 1.0, Explain.PALETTE_OFF, "scenario" to name)
    }

    private val earth = setOf(BROWN, CHOCOLATE, CAMEL, OLIVE, RUST, MUSTARD, BURGUNDY, KHAKI, TAUPE, FOREST_GREEN)

    fun c14SeasonalNudge(look: Look, ctx: EngineContext): RuleResult? {
        val cs = look.colors.filter { it.slot.isCore }
        if (cs.isEmpty()) return null
        val total = cs.sumOf { it.area }
        val meanL = cs.sumOf { it.info.l * it.area } / total
        val earthy = cs.filter { it.info.family in earth }.sumOf { it.area } / total
        val s = when (ctx.season) {
            Season.AUTUMN, Season.WINTER -> 0.5 + 0.3 * (1 - meanL) + 0.3 * earthy
            Season.SPRING, Season.SUMMER -> 0.4 + 0.6 * meanL
        }.coerceIn(0.0, 1.0)
        return res("C14", G, s, 0.25, if (s > 0.75) Explain.SEASONAL_NUDGE else null)
    }

    fun c15WeddingWhite(look: Look, ctx: EngineContext): RuleResult? {
        if (ctx.scenario.palette != ScenarioPalette.NO_DOMINANT_WHITE) return null
        val white = look.colors.filter { ColorRules.isWhiteish(it.info.family) }.sumOf { it.area }
        return if (white >= 0.5) res("C15", G, 0.0, 3.0, Explain.WEDDING_WHITE) else res("C15", G, 1.0, 0.5)
    }

    internal fun neutralFamily(f: ColorFamily) = ColorTables.isNeutralFamily(f)
}
