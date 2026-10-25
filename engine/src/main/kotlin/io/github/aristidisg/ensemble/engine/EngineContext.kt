package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.rules.CollectionProfile
import io.github.aristidisg.ensemble.engine.rules.CollectionRules
import io.github.aristidisg.ensemble.engine.tables.WarmthTable
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.model.WindowWeather
import java.time.LocalDate

/** Wear history with precomputed lookups. */
class WearHistory(val logs: List<WearLog>) {
    /** Most recent wear day per item. */
    val lastWornItem: Map<String, Long>
    /** Most recent wear day per outfit signature. */
    val lastWornOutfit: Map<String, Long>
    val wearCount: Map<String, Int>

    init {
        val li = HashMap<String, Long>(); val lo = HashMap<String, Long>(); val wc = HashMap<String, Int>()
        for (log in logs) {
            for (p in log.pieces) {
                li[p.itemId] = maxOf(li[p.itemId] ?: Long.MIN_VALUE, log.epochDay)
                wc[p.itemId] = (wc[p.itemId] ?: 0) + 1
            }
            lo[log.outfitSignature] = maxOf(lo[log.outfitSignature] ?: Long.MIN_VALUE, log.epochDay)
        }
        lastWornItem = li; lastWornOutfit = lo; wearCount = wc
    }

    fun daysSinceItem(id: String, today: Long): Long? = lastWornItem[id]?.let { today - it }

    fun daysSinceOutfit(sig: String, today: Long): Long? = lastWornOutfit[sig]?.let { today - it }

    /** Logs within the last [days] days before [today] (exclusive of today). */
    fun recent(today: Long, days: Int): List<WearLog> = logs.filter { it.epochDay in (today - days) until today }

    companion object {
        val EMPTY = WearHistory(emptyList())
    }
}

/** Everything a rule may look at. Time and randomness are passed in; nothing is read from the system. */
data class EngineContext(
    val scenario: Scenario,
    val weather: WindowWeather,
    val epochDay: Long,
    val settings: Settings = Settings(),
    val items: Map<String, Item> = emptyMap(),
    val prefs: PreferenceSnapshot = PreferenceSnapshot.EMPTY,
    val history: WearHistory = WearHistory.EMPTY,
    val customRules: List<CustomRule> = emptyList(),
    /** Items planned for the next 3 days (R6). */
    val plannedSoon: Set<String> = emptySet(),
    /** Signatures shown in the last 3 decks (R4). */
    val recentDeckSignatures: Set<String> = emptySet(),
    /** Per item: on how many cards it was shown in recent days' decks (fair-share rotation). */
    val recentDeckItems: Map<String, Int> = emptyMap(),
    /** Total cards those decks showed. */
    val recentDeckCount: Int = 0,
    /**
     * Core-piece signatures of outfits shown in recent decks: new decks hold them back so you
     * see new combinations instead of the same outfits again.
     */
    val shownOutfits: Set<String> = emptySet(),
    /** Outfit signatures the user explicitly allowed despite a cooldown ("Use anyway"). */
    val cooldownOverrides: Set<String> = emptySet(),
    /** Per-outfit cooldown overrides keyed by signature. */
    val outfitCooldowns: Map<String, Int> = emptyMap(),
    /** Signatures marked "never show again". */
    val neverAgain: Set<String> = emptySet(),
    /** Saved and liked outfits ("my collection"), used when savedOutfitsInfluence is on. */
    val collection: List<List<OutfitPiece>> = emptyList(),
) {
    /** Precomputed pieces and color-family pairs of the collection. */
    val collectionProfiles: List<CollectionProfile> by lazy { CollectionRules.profiles(this) }

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val weekday: Int get() = date.dayOfWeek.value
    val season: Season get() = seasonOf(epochDay)

    /**
     * Seasons a piece may belong to today: the calendar season, plus summer on a day that feels
     * like summer and winter on one that feels like winter (a 28 °C October afternoon is linen
     * weather).
     */
    val seasonsNow: Set<Season> get() = buildSet {
        add(season)
        if (weather.feelsMid >= 22.0) add(Season.SUMMER)
        if (weather.feelsMid <= 8.0) add(Season.WINTER)
        if (weather.feelsMid in 12.0..22.0) { add(Season.SPRING); add(Season.AUTUMN) }
    }

    /** Boldness after the strictness preset and learned "too boring / too loud" nudges (+ experiment). */
    val boldness: Double get() = (settings.boldness + prefs.boldnessDelta + if (settings.experimentMode) EXPERIMENT_BOLDNESS else 0.0).coerceIn(0.0, 1.0)

    /** Experiment loosens the style rules as the Adventurous preset does. */
    val strictness: io.github.aristidisg.ensemble.model.Strictness get() =
        if (settings.experimentMode) io.github.aristidisg.ensemble.model.Strictness.ADVENTUROUS else settings.strictness

    /** Share of new-idea cards in a deck. */
    val explorationRate: Double get() = if (settings.experimentMode) maxOf(settings.explorationRate, EXPERIMENT_EXPLORATION) else settings.explorationRate

    /** Thermal offset in °C: positive means the user runs cold and wants more warmth. */
    val thermalOffset: Double get() = settings.thermalBias + prefs.thermalOffset

    val formalityOffset: Double get() = prefs.formalityOffset[scenario.id] ?: 0.0

    val tempBand: String get() = WarmthTable.label(weather.feelsMid)

    val indoorTemp: Double get() {
        val m = date.monthValue
        return if (m >= 10 || m <= 4) settings.indoorTempWinter else settings.indoorTempSummer
    }

    fun ruleOn(id: String): Boolean = settings.ruleEnabled[id] ?: true

    fun ruleWeight(id: String, default: Double = 1.0): Double = settings.ruleWeights[id] ?: default

    companion object {
        /** Experiment mode: extra boldness, share of new-idea cards, weight left to learned taste. */
        const val EXPERIMENT_BOLDNESS = 0.35
        const val EXPERIMENT_EXPLORATION = 0.4
        const val EXPERIMENT_LEARNED = 0.4

        /** Northern-hemisphere meteorological seasons. */
        fun seasonOf(epochDay: Long): Season = when (LocalDate.ofEpochDay(epochDay).monthValue) {
            12, 1, 2 -> Season.WINTER
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            else -> Season.AUTUMN
        }
    }
}
