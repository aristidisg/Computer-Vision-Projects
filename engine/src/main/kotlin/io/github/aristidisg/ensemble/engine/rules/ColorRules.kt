package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.PersonalContrast
import io.github.aristidisg.ensemble.model.Temperature
import kotlin.math.abs
import kotlin.math.roundToInt

/** Color rules C1-C6 (SPEC 4.4). */
object ColorRules {
    private val G = RuleGroup.COLOR

    /** Groups chromatic colors whose hues lie within 25° of each other. */
    fun hueGroups(look: Look, maxNeutralness: Double = 0.6, minArea: Double = 0.0): List<List<LookColor>> {
        val cs = look.colors.filter { it.info.neutralness < maxNeutralness }.sortedByDescending { it.area }
        val groups = mutableListOf<MutableList<LookColor>>()
        for (c in cs) {
            val g = groups.firstOrNull { ColorScience.hueDistance(it.first().info.hue, c.info.hue) <= 25.0 }
            if (g != null) g += c else groups += mutableListOf(c)
        }
        return groups.filter { g -> g.sumOf { it.area } >= minArea }
    }

    fun c1Harmony(look: Look, ctx: EngineContext): RuleResult {
        val chroma = look.chromaticColors
        val fit = Harmony.fit(chroma.map { it.info.hue to it.salience }, ctx.boldness)
        val groups = hueGroups(look, 0.8)
        return when {
            fit.template == null -> res("C1", G, 1.0, 1.2, Explain.HARMONY_NEUTRAL)
            groups.size <= 1 -> {
                val accent = groups.firstOrNull()?.maxBy { it.area }
                val pct = ((groups.firstOrNull()?.sumOf { it.area } ?: 0.0) * 100).roundToInt()
                res("C1", G, fit.score, 1.2, Explain.NEUTRAL_ACCENT, "accent" to (accent?.info?.family?.label?.lowercase() ?: "color"), "pct" to pct)
            }
            fit.template == Harmony.I || fit.template == Harmony.Y || fit.template == Harmony.X -> {
                val (a, b) = groups[0].first().info.family.label to groups[1].first().info.family.label
                res("C1", G, fit.score, 1.2, Explain.HARMONY_COMPLEMENTARY, "a" to a, "b" to b)
            }
            fit.template == Harmony.TRIADIC -> res("C1", G, fit.score, 1.2, Explain.HARMONY_TRIADIC)
            fit.score >= 0.6 -> {
                val (a, b) = groups[0].first().info.family.label to groups[1].first().info.family.label.lowercase()
                res("C1", G, fit.score, 1.2, Explain.HARMONY_ANALOGOUS, "a" to a, "b" to b)
            }
            else -> res("C1", G, fit.score, 1.2, Explain.HARMONY_WEAK)
        }
    }

    /** Harmony label for the deck chip, e.g. "Analogous · earth". */
    fun harmonyLabel(look: Look, ctx: EngineContext): String {
        val fit = Harmony.fit(look.chromaticColors.map { it.info.hue to it.salience }, ctx.boldness)
        val groups = hueGroups(look, 0.8)
        val base = when {
            fit.template == null -> "Neutral"
            groups.size <= 1 -> "Neutral + accent"
            else -> fit.label
        }
        val warm = look.colors.filter { it.info.temperature == Temperature.WARM }.sumOf { it.area }
        val cool = look.colors.filter { it.info.temperature == Temperature.COOL }.sumOf { it.area }
        val mood = when {
            look.colors.isNotEmpty() && look.colors.all { it.info.l < 0.45 } -> "dark"
            warm > cool * 1.5 -> "earth"
            cool > warm * 1.5 -> "cool"
            else -> "balanced"
        }
        return "$base · $mood"
    }

    fun c2AccentCount(look: Look, ctx: EngineContext): RuleResult {
        val n = hueGroups(look, 0.6, 0.03).size
        val ideal = if (ctx.boldness > 0.7) 3 else 2
        val extra = (n - ideal).coerceAtLeast(0)
        return if (extra == 0) res("C2", G, 1.0) else res("C2", G, 1.0 - 0.3 * extra, 1.0, Explain.TOO_MANY_ACCENTS, "n" to n)
    }

    fun c3Proportion(look: Look, ctx: EngineContext): RuleResult {
        // Dominant group: the largest family block (neutrals included) at 45-70 % of area.
        val dominant = look.familyArea.values.maxOrNull() ?: 0.0
        var score = when {
            dominant in 0.45..0.70 -> 1.0
            dominant > 0.70 -> 0.85
            else -> 0.7 + (dominant / 0.45) * 0.3
        }
        val vivid = hueGroups(look, 0.5).map { g -> g.sumOf { it.area } to g.first().info.hue }
        val accents = vivid.filter { it.first <= 0.15 }
        if (vivid.size == 1 && accents.size == 1) score = minOf(1.0, score + 0.1)
        val big = vivid.filter { it.first >= 0.30 }
        if (big.size >= 2) {
            val comp = ColorScience.hueDistance(big[0].second, big[1].second) >= 150.0
            if (!(comp && ctx.boldness >= 0.6)) return res("C3", G, 0.25, key = Explain.COLOR_FIGHT)
        }
        return res("C3", G, score)
    }

    fun c4ValueContrast(look: Look, ctx: EngineContext): RuleResult? {
        val up = look.upperDominant ?: return null
        val down = look.dominant(look.bottom) ?: return null
        val pc = ctx.settings.personalColor
        val band = when (if (pc.enabled) pc.contrast else PersonalContrast.MEDIUM) {
            PersonalContrast.LOW -> 0.05..0.20
            PersonalContrast.MEDIUM -> 0.15..0.35
            PersonalContrast.HIGH -> 0.30..0.60
        }
        val dl = abs(up.l - down.l)
        var s = when {
            dl in band -> 1.0
            dl < band.start -> 1.0 - (band.start - dl) / 0.2
            // Above the band is a milder fault than below it: crisp contrast still reads well.
            else -> 1.0 - (dl - band.endInclusive) / 0.8
        }.coerceIn(0.4, 1.0)
        val bottomDarker = down.l < up.l
        if (bottomDarker) s = minOf(1.0, s + 0.05)
        val mush = look.colors.isNotEmpty() && look.colors.filter { it.slot.isCore }.all { it.info.l in 0.45..0.65 && it.info.c < 0.05 }
        if (mush) return res("C4", G, 0.3, key = Explain.GREY_MUSH)
        return when {
            s >= 0.8 && bottomDarker -> res("C4", G, s, key = Explain.VALUE_CONTRAST)
            s < 0.6 -> res("C4", G, s, key = Explain.LOW_CONTRAST)
            else -> res("C4", G, s)
        }
    }

    fun c5ChromaBalance(look: Look, ctx: EngineContext): RuleResult {
        val vividBig = hueGroups(look, 1.01).count { g -> g.first().info.c >= 0.15 && g.sumOf { it.area } > 0.25 }
        val neonBig = look.colors.filter { it.info.c >= 0.22 }.sumOf { it.area } > 0.10
        var s = 1.0
        if (vividBig > 1 && ctx.boldness < 0.7) s -= 0.5
        if (neonBig) s -= 0.4
        return if (s < 1.0) res("C5", G, s, key = Explain.CHROMA_HEAVY) else res("C5", G, 1.0)
    }

    fun c6Temperature(look: Look, ctx: EngineContext): RuleResult? {
        val chroma = look.chromaticColors.filter { it.info.neutralness < 0.6 }
        if (chroma.size < 2) return null
        val temps = chroma.map { it.info.temperature }.filter { it != Temperature.NEUTRAL }.toSet()
        if (temps.size <= 1) {
            val t = temps.firstOrNull()?.name?.lowercase() ?: "neutral"
            return res("C6", G, 1.0, 0.5, Explain.TEMPERATURE_MATCH, "temp" to t)
        }
        val vividWarm = chroma.any { it.info.temperature == Temperature.WARM && it.info.c >= 0.12 }
        val vividCool = chroma.any { it.info.temperature == Temperature.COOL && it.info.c >= 0.12 }
        val fit = Harmony.fit(look.chromaticColors.map { it.info.hue to it.salience }, ctx.boldness).template
        val complementary = fit == Harmony.I || fit == Harmony.Y
        return res("C6", G, if (vividWarm && vividCool && !complementary) 0.6 else 0.85, 0.5)
    }

    internal fun familyLabel(f: ColorFamily) = f.label.lowercase()

    internal fun isWhiteish(f: ColorFamily) = f == ColorFamily.WHITE || f == ColorFamily.ECRU

    internal fun neutral(f: ColorFamily) = ColorTables.isNeutralFamily(f)
}
