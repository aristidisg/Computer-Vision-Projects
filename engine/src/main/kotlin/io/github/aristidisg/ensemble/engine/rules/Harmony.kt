package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.color.ColorScience
import kotlin.math.min

/** A Matsuda hue template: sectors (center offset, width) on the artist wheel and a prior. */
data class HueTemplate(val name: String, val label: String, val sectors: List<Pair<Double, Double>>, val prior: Double)

data class HarmonyFit(val template: HueTemplate?, val score: Double, val label: String)

/** Rule C1: Matsuda hue templates on the artist wheel, best fit over 1° rotations. */
object Harmony {
    val MONO = HueTemplate("i", "Monochrome", listOf(0.0 to 18.0), 1.0)
    val V = HueTemplate("V", "Analogous", listOf(0.0 to 93.6), 1.0)
    val L = HueTemplate("L", "Split", listOf(0.0 to 18.0, 90.0 to 79.2), 0.95)
    val I = HueTemplate("I", "Complementary", listOf(0.0 to 18.0, 180.0 to 18.0), 0.95)
    val T = HueTemplate("T", "Half wheel", listOf(0.0 to 180.0), 0.75)
    val Y = HueTemplate("Y", "Complementary accent", listOf(0.0 to 93.6, 180.0 to 18.0), 0.9)
    val X = HueTemplate("X", "Double complementary", listOf(0.0 to 93.6, 180.0 to 93.6), 0.8)
    val TRIADIC = HueTemplate("triadic", "Triadic", listOf(0.0 to 40.0, 120.0 to 40.0, 240.0 to 40.0), 0.7)

    fun templates(boldness: Double): List<HueTemplate> =
        if (boldness >= 0.6) listOf(MONO, V, L, I, T, Y, X, TRIADIC) else listOf(MONO, V, L, I, T, Y, X)

    /** Arc distance from [hue] to the nearest sector of [t] rotated by [rot]; 0 inside a sector. */
    fun arcDistance(hue: Double, t: HueTemplate, rot: Double): Double {
        var best = Double.MAX_VALUE
        for ((center, width) in t.sectors) {
            val d = ColorScience.hueDistance(hue, center + rot) - width / 2
            if (d < best) best = d
        }
        return if (best < 0) 0.0 else best
    }

    /** Best template score for colors given as (hue, salience). All-neutral outfits score 1.0 "Neutral". */
    fun fit(colors: List<Pair<Double, Double>>, boldness: Double): HarmonyFit {
        val total = colors.sumOf { it.second }
        if (colors.isEmpty() || total <= 1e-9) return HarmonyFit(null, 1.0, "Neutral")
        var best = HarmonyFit(null, 0.0, "Discordant")
        for (t in templates(boldness)) {
            var bestCost = Double.MAX_VALUE
            var rot = 0.0
            while (rot < 360.0) {
                var cost = 0.0
                for ((h, s) in colors) {
                    cost += s * arcDistance(h, t, rot)
                    if (cost >= bestCost) break
                }
                if (cost < bestCost) bestCost = cost
                rot += 1.0
            }
            val score = (1.0 - min(1.0, bestCost / (total * 45.0))) * t.prior
            if (score > best.score) best = HarmonyFit(t, score, t.label)
        }
        return best
    }
}
