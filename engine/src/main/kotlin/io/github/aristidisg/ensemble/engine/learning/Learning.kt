package io.github.aristidisg.ensemble.engine.learning

import io.github.aristidisg.ensemble.model.FeedbackEvent
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.PreferenceStat
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Thermal
import io.github.aristidisg.ensemble.model.WhyNot
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class LearningConfig(
    val halfLifeDays: Double = 120.0,
    val rejectedCooldownDays: Int = 21,
    /** Saving or composing an outfit teaches taste; off with "saved outfits shape suggestions". */
    val learnFromSaved: Boolean = true,
) {
    companion object {
        fun of(s: Settings) = LearningConfig(s.halfLifeDays, s.rejectedCooldownDays, s.savedOutfitsInfluence)
    }
}

/** Learned state. Every field can be rebuilt by replaying the event log. */
@Serializable
data class PreferenceStats(
    /** Keyed by "context␟feature"; counts are decayed up to lastUpdatedEpochDay. */
    val stats: Map<String, PreferenceStat> = emptyMap(),
    val thermalOffset: Double = 0.0,
    val formalityOffset: Map<String, Double> = emptyMap(),
    val boldnessDelta: Double = 0.0,
    /** Extra rotation days per rotation group ("wore it recently"). */
    val rotationExtra: Map<String, Int> = emptyMap(),
    /** Hidden items: "itemId|scenarioId" -> hidden until this epoch day (inclusive). */
    val hiddenItems: Map<String, Long> = emptyMap(),
    /** Hidden outfit signatures -> hidden until this epoch day (inclusive). */
    val hiddenOutfits: Map<String, Long> = emptyMap(),
    val blocked: Set<String> = emptySet(),
    val comfortPenalty: Map<String, Double> = emptyMap(),
    /** Learning events (decay applied when read) used for the learned-weight ramp. */
    val eventWeight: Double = 0.0,
    val lastEventDay: Long = 0,
    /** The same ramp for editorial (Vogue stylist) feedback, kept apart from the everyday one. */
    val editorialWeight: Double = 0.0,
    val lastEditorialDay: Long = 0,
) {
    companion object {
        const val SEP = "␟"
        val EMPTY = PreferenceStats()
    }
}

/** Taste learned by counting (SPEC 7): no models, fully explainable and replayable. */
object Learning {

    fun decay(ageDays: Double, halfLife: Double): Double = if (ageDays <= 0) 1.0 else 0.5.pow(ageDays / halfLife)

    /** Context key of editorial (Vogue stylist) feedback: read only by Vogue decks. */
    const val EDITORIAL = "EDITORIAL"

    private fun contexts(e: FeedbackEvent): List<Pair<String, Double>> =
        if (e.context.editorial) listOf(EDITORIAL to 1.0)
        else listOf("GLOBAL" to 1.0, "SCN:${e.context.scenarioId}" to 1.0, "SEASON:${e.context.season.name}" to 0.5)

    private fun add(
        stats: MutableMap<String, PreferenceStat>, feature: String, ctxKey: String, weight: Double, day: Long, cfg: LearningConfig,
    ) {
        if (weight == 0.0) return
        val key = ctxKey + PreferenceStats.SEP + feature
        val old = stats[key]
        val d = old?.let { decay((day - it.lastUpdatedEpochDay).toDouble(), cfg.halfLifeDays) } ?: 1.0
        val pos = (old?.pos ?: 0.0) * d + if (weight > 0) weight else 0.0
        val neg = (old?.neg ?: 0.0) * d + if (weight < 0) -weight else 0.0
        stats[key] = PreferenceStat(feature, ctxKey, pos, neg, maxOf(day, old?.lastUpdatedEpochDay ?: day))
    }

    private fun addAll(stats: MutableMap<String, PreferenceStat>, e: FeedbackEvent, features: Map<String, Double>, cfg: LearningConfig) {
        for ((ctxKey, mult) in contexts(e)) for ((f, w) in features) add(stats, f, ctxKey, w * mult, e.epochDay, cfg)
    }

    /** Applies one event. Pure: same stats and event always give the same result. */
    fun apply(event: FeedbackEvent, stats: PreferenceStats, cfg: LearningConfig = LearningConfig()): PreferenceStats {
        val s = stats.stats.toMutableMap()
        var out = stats
        val f = event.features
        val day = event.epochDay
        var learned = true
        fun all(w: Double) = f.associateWith { w }
        when (event.type) {
            FeedbackType.SWIPE_YES -> addAll(s, event, all(1.0), cfg)
            FeedbackType.SAVE -> if (cfg.learnFromSaved) addAll(s, event, all(2.0), cfg) else learned = false
            FeedbackType.COMPOSED -> if (cfg.learnFromSaved) addAll(s, event, all(3.0), cfg) else learned = false
            FeedbackType.SWIPE_UP_WEAR -> addAll(s, event, all(2.5), cfg)
            FeedbackType.INSPIRATION_ADDED -> addAll(s, event, all(2.0), cfg)
            FeedbackType.SWIPE_NO -> {
                val sig = event.outfitSignature
                if (WhyNot.NOT_TODAY in event.reasons) {
                    learned = false
                    if (sig != null) out = out.copy(hiddenOutfits = out.hiddenOutfits + (sig to day + 1))
                } else {
                    if (sig != null) out = out.copy(hiddenOutfits = out.hiddenOutfits + (sig to day + cfg.rejectedCooldownDays))
                    if (event.reasons.isEmpty()) {
                        addAll(s, event, f.associateWith { k ->
                            when { Features.isCombination(k) -> -0.6; k.startsWith("item:") -> -0.15; else -> -0.3 }
                        }, cfg)
                    } else {
                        val named = f.filter { k -> event.reasons.any { Features.namedBy(it, k) } }
                        addAll(s, event, named.associateWith { -1.0 }, cfg)
                        // Warmth, formality and boldness nudges belong to the everyday deck only.
                        if (!event.context.editorial) out = applyReasonSideEffects(out, event)
                    }
                }
            }
            FeedbackType.PIECE_UP, FeedbackType.PIECE_DOWN -> {
                val sign = if (event.type == FeedbackType.PIECE_UP) 1.0 else -1.0
                val scn = if (event.context.editorial) EDITORIAL else "SCN:${event.context.scenarioId}"
                event.targetItemId?.let { add(s, "item:$it", scn, 1.5 * sign, day, cfg) }
                for (p in event.targetFeatures) add(s, p, scn, 0.3 * sign, day, cfg)
            }
            FeedbackType.CHECK_IN -> {
                val c = event.checkIn
                if (c != null) {
                    addAll(s, event, all((c.felt - 3).toDouble()), cfg)
                    if (c.comfort <= 2) {
                        val pen = out.comfortPenalty.toMutableMap()
                        event.pieces.forEach { pen[it.itemId] = ((pen[it.itemId] ?: 0.0) + 0.05).coerceAtMost(0.3) }
                        out = out.copy(comfortPenalty = pen)
                    }
                    val dt = when (c.thermal) { Thermal.TOO_COLD -> 0.5; Thermal.TOO_WARM -> -0.5; Thermal.OK -> 0.0 }
                    out = out.copy(thermalOffset = (out.thermalOffset + dt).coerceIn(-5.0, 5.0))
                }
            }
            FeedbackType.NEVER_AGAIN -> {
                event.outfitSignature?.let { out = out.copy(blocked = out.blocked + it) }
                addAll(s, event, (event.targetFeatures.ifEmpty { f.filter { it.startsWith("pair:") } }).associateWith { -2.0 }, cfg)
            }
        }
        if (learned && event.context.editorial) {
            val ramp = out.editorialWeight * decay((day - out.lastEditorialDay).toDouble(), cfg.halfLifeDays) + 1.0
            return out.copy(stats = s, editorialWeight = ramp, lastEditorialDay = maxOf(day, out.lastEditorialDay))
        }
        val ramp = if (learned) out.eventWeight * decay((day - out.lastEventDay).toDouble(), cfg.halfLifeDays) + 1.0 else out.eventWeight
        return out.copy(stats = s, eventWeight = ramp, lastEventDay = if (learned) maxOf(day, out.lastEventDay) else out.lastEventDay)
    }

    private fun applyReasonSideEffects(stats: PreferenceStats, e: FeedbackEvent): PreferenceStats {
        var out = stats
        val scn = e.context.scenarioId
        for (r in e.reasons) out = when (r) {
            WhyNot.TOO_FORMAL, WhyNot.TOO_CASUAL -> {
                val d = if (r == WhyNot.TOO_FORMAL) -0.25 else 0.25
                val v = ((out.formalityOffset[scn] ?: 0.0) + d).coerceIn(-2.0, 2.0)
                out.copy(formalityOffset = out.formalityOffset + (scn to v))
            }
            WhyNot.TOO_WARM -> out.copy(thermalOffset = (out.thermalOffset - 0.5).coerceIn(-5.0, 5.0))
            WhyNot.TOO_COLD -> out.copy(thermalOffset = (out.thermalOffset + 0.5).coerceIn(-5.0, 5.0))
            WhyNot.TOO_BORING -> out.copy(boldnessDelta = (out.boldnessDelta + 0.03).coerceIn(-0.5, 0.5))
            WhyNot.TOO_LOUD -> out.copy(boldnessDelta = (out.boldnessDelta - 0.03).coerceIn(-0.5, 0.5))
            WhyNot.NOT_THIS_PIECE -> e.targetItemId?.let { id ->
                val m = out.stats.toMutableMap()
                add(m, "item:$id", "SCN:$scn", -1.0, e.epochDay, LearningConfig())
                out.copy(stats = m, hiddenItems = out.hiddenItems + ("$id|$scn" to e.epochDay + 3))
            } ?: out
            WhyNot.WORE_RECENTLY -> {
                val group = e.targetItemId?.let { id -> e.pieces.firstOrNull { it.itemId == id }?.slot?.name } ?: "BASE_TOP"
                val key = if (group == "TOP") "BASE_TOP" else group
                out.copy(rotationExtra = out.rotationExtra + (key to ((out.rotationExtra[key] ?: 0) + 1).coerceAtMost(7)))
            }
            else -> out
        }
        return out
    }

    /** Deterministic replay of the log; equals applying the events one by one. */
    fun rebuild(events: List<FeedbackEvent>, cfg: LearningConfig = LearningConfig()): PreferenceStats =
        events.sortedWith(compareBy<FeedbackEvent> { it.epochMillis }.thenBy { it.id })
            .fold(PreferenceStats.EMPTY) { acc, e -> apply(e, acc, cfg) }
}
