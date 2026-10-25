package io.github.aristidisg.ensemble.engine.learning

import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.StyleTag
import kotlin.math.tanh

/** One feature's learned value in a context: lift × confidence, plus raw decayed counts. */
data class FeatureValue(val value: Double, val pos: Double, val neg: Double)

/**
 * A read view of [PreferenceStats] for one scenario, season and day: decay applied, contexts
 * blended 0.5 scenario + 0.3 global + 0.2 season (renormalized when one is empty).
 */
class PreferenceSnapshot(
    private val values: Map<String, FeatureValue>,
    val thermalOffset: Double,
    val formalityOffset: Map<String, Double>,
    val boldnessDelta: Double,
    val rotationExtra: Map<String, Int>,
    val hiddenItems: Map<String, Long>,
    val hiddenOutfits: Map<String, Long>,
    val blocked: Set<String>,
    val comfortPenalty: Map<String, Double>,
    /** Learned group weight, ramped from 0.02 to the "trust my taste" ceiling at 60 events. */
    val learnedWeight: Double,
) {
    fun value(feature: String): FeatureValue? = values[feature]

    fun allValues(): Map<String, FeatureValue> = values

    val highLowTaste: Double get() = values["formality:highlow"]?.value ?: 0.0

    fun silhouetteAdjust(top: Silhouette, bottom: Silhouette): Double = 0.2 * (values["sil:${top.name}|${bottom.name}"]?.value ?: 0.0)

    fun styleMixAdjust(a: StyleTag, b: StyleTag): Double {
        val k = if (a.name <= b.name) "${a.name}|${b.name}" else "${b.name}|${a.name}"
        return 0.2 * (values["stylemix:$k"]?.value ?: 0.0)
    }

    fun isItemHidden(itemId: String, scenarioId: String, today: Long): Boolean = (hiddenItems["$itemId|$scenarioId"] ?: Long.MIN_VALUE) >= today

    fun isOutfitHidden(signature: String, today: Long): Boolean =
        signature in blocked || (hiddenOutfits[signature] ?: Long.MIN_VALUE) >= today

    /** Learned score = 0.5 + 0.5 × tanh(1.5 × Σ group weight × mean value), over features with data. */
    fun learnedScore(features: List<String>): Double? {
        val byGroup = features.mapNotNull { f -> values[f]?.let { Features.groupOf(f) to it.value } }.groupBy({ it.first }, { it.second })
        if (byGroup.isEmpty()) return null
        var sum = 0.0
        for ((g, vs) in byGroup) sum += g.weight * vs.average()
        return 0.5 + 0.5 * tanh(1.5 * sum)
    }

    companion object {
        val EMPTY = PreferenceSnapshot(emptyMap(), 0.0, emptyMap(), 0.0, emptyMap(), emptyMap(), emptyMap(), emptySet(), emptyMap(), 0.02)

        fun value(pos: Double, neg: Double): Double {
            val n = pos + neg
            val mean = (pos + 1) / (n + 2)
            val lift = 2 * (mean - 0.5)
            val confidence = n / (n + 3)
            return lift * confidence
        }

        fun ramp(eventWeight: Double, ceiling: Double): Double {
            val c = ceiling.coerceIn(0.10, 0.30)
            return 0.02 + (c - 0.02) * (eventWeight / 60.0).coerceIn(0.0, 1.0)
        }

        fun of(
            stats: PreferenceStats, scenarioId: String, season: Season, today: Long,
            cfg: LearningConfig = LearningConfig(), trustMyTaste: Double = 0.18,
            /** Vogue stylist decks: editorial taste leads, everyday taste fills in. */
            editorial: Boolean = false,
        ): PreferenceSnapshot {
            val scn = "SCN:$scenarioId"; val sea = "SEASON:${season.name}"; val ed = Learning.EDITORIAL
            val perFeature = HashMap<String, HashMap<String, Pair<Double, Double>>>()
            for (st in stats.stats.values) {
                if (st.contextKey != "GLOBAL" && st.contextKey != scn && st.contextKey != sea && !(editorial && st.contextKey == ed)) continue
                val d = Learning.decay((today - st.lastUpdatedEpochDay).toDouble(), cfg.halfLifeDays)
                perFeature.getOrPut(st.featureKey) { HashMap() }[st.contextKey] = st.pos * d to st.neg * d
            }
            val values = perFeature.mapValues { (_, ctxs) ->
                val weights = if (editorial) listOf(ed to 0.6, "GLOBAL" to 0.25, scn to 0.15) else listOf(scn to 0.5, "GLOBAL" to 0.3, sea to 0.2)
                val parts = weights.mapNotNull { (k, w) -> ctxs[k]?.let { Triple(it.first, it.second, w) } }
                val wsum = parts.sumOf { it.third }
                val v = parts.sumOf { value(it.first, it.second) * it.third } / wsum
                FeatureValue(v, parts.sumOf { it.first * it.third } / wsum, parts.sumOf { it.second * it.third } / wsum)
            }
            val everyday = stats.eventWeight * Learning.decay((today - stats.lastEventDay).toDouble(), cfg.halfLifeDays)
            val ev = if (!editorial) everyday else maxOf(everyday, stats.editorialWeight * Learning.decay((today - stats.lastEditorialDay).toDouble(), cfg.halfLifeDays))
            return PreferenceSnapshot(
                values, stats.thermalOffset, stats.formalityOffset, stats.boldnessDelta, stats.rotationExtra,
                stats.hiddenItems, stats.hiddenOutfits, stats.blocked, stats.comfortPenalty, ramp(ev, trustMyTaste),
            )
        }
    }
}
