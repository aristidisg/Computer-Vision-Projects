package io.github.aristidisg.ensemble.domain

import io.github.aristidisg.ensemble.data.db.DeckLogEntity
import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.repo.DeckCardLog
import io.github.aristidisg.ensemble.data.repo.DeckLog
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.data.repo.RuleRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.data.weather.WeatherRepository
import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.OutfitEngine
import io.github.aristidisg.ensemble.engine.WeatherWindow
import io.github.aristidisg.ensemble.engine.WearHistory
import io.github.aristidisg.ensemble.engine.generator.SuggestionDeck
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.tables.ScenarioPresets
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.WindowWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Weather for a scenario window, with where it came from. */
data class DayWeather(val window: WindowWeather, val fromForecast: Boolean, val fetchedAtMillis: Long?)

/** Recent decks looked back on for rotation: a piece shown in all of them gives way most. */
private const val EXPOSURE_DECKS = 5
/** Deck logs read back; the table keeps the latest 60. */
private const val DECK_LOG_SIZE = 60

/**
 * Builds engine contexts from everything stored on the phone and runs the engine off the main
 * thread. The single place where repositories meet the pure engine.
 */
@Singleton
class Studio @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val wardrobe: WardrobeRepository,
    private val feedback: FeedbackRepository,
    private val wear: WearRepository,
    private val rules: RuleRepository,
    private val outfits: OutfitRepository,
    private val plans: PlanRepository,
    private val weather: WeatherRepository,
    private val db: EnsembleDatabase,
    private val time: TimeSource,
    val engine: OutfitEngine,
) {
    suspend fun scenarios(): List<Scenario> = rules.allScenarios()

    suspend fun scenario(id: String): Scenario =
        scenarios().firstOrNull { it.id == id } ?: ScenarioPresets.byId(id) ?: ScenarioPresets.all.first()

    /** The weekly template's scenario for a day. */
    suspend fun defaultScenarioId(epochDay: Long): String {
        val s = settingsRepo.current()
        val plan = plans.allPlans().firstOrNull { it.epochDay == epochDay }
        return plan?.scenarioId ?: s.weeklyTemplate[time.date(epochDay).dayOfWeek.value] ?: "casual"
    }

    suspend fun dayWeather(epochDay: Long, scenario: Scenario, settings: Settings? = null): DayWeather {
        val s = settings ?: settingsRepo.current()
        s.manualWeather[epochDay.toString()]?.let { return DayWeather(it, false, null) }
        val snap = weather.current()
        if (snap != null) {
            val w = WeatherWindow.aggregate(snap, epochDay, scenario)
            if (w != null) return DayWeather(w, true, snap.fetchedAtMillis)
        }
        return DayWeather(WindowWeather.mild(), false, null)
    }

    suspend fun context(epochDay: Long, scenario: Scenario, weatherOverride: WindowWeather? = null, settingsOverride: Settings? = null): EngineContext =
        withContext(Dispatchers.Default) {
            val s = settingsOverride ?: settingsRepo.current()
            val items = wardrobe.all()
            val stats = feedback.currentStats()
            val cfg = LearningConfig.of(s)
            val prefs = PreferenceSnapshot.of(stats, scenario.id, EngineContext.seasonOf(epochDay), epochDay, cfg, s.trustMyTaste)
            val saved = outfits.all()
            val soon = plans.allPlans().filter { it.epochDay in (epochDay + 1)..(epochDay + 3) }.flatMap { p -> p.pieces.map { it.itemId } }.toSet()
            val logged = db.cache().recentDecks(DECK_LOG_SIZE)
            val recent = logged.filter { it.epochDay < epochDay }.take(3).flatMap { DeckLog.decode(it.json) }.map { it.signature }.toSet()
            // Rotation looks at earlier days only (all decks of a day count as one), so the deck
            // stays the same while you look at it today and changes from one day to the next.
            val decks = logged.filter { it.epochDay < epochDay }.groupBy { it.epochDay }.entries
                .sortedByDescending { it.key }.take(EXPOSURE_DECKS)
                .map { (_, ds) -> ds.flatMap { DeckLog.decode(it.json) } }
            EngineContext(
                scenario = scenario,
                weather = weatherOverride ?: dayWeather(epochDay, scenario, s).window,
                epochDay = epochDay,
                settings = s,
                items = items.associateBy { it.id },
                prefs = prefs,
                history = WearHistory(wear.all()),
                customRules = rules.allRules(),
                plannedSoon = soon,
                recentDeckSignatures = recent,
                recentDeckItems = DeckLog.exposure(decks),
                recentDeckCount = decks.size,
                outfitCooldowns = saved.mapNotNull { o -> o.cooldownDays?.let { o.signature to it } }.toMap(),
                neverAgain = saved.filter { it.neverShow }.map { it.signature }.toSet(),
                collection = saved.filter { it.liked && !it.neverShow }.map { it.pieces },
            )
        }

    suspend fun items(): List<Item> = wardrobe.all()

    /** Generates a deck; [boldnessShift] powers the "Bolder" and "Safer" buttons. */
    suspend fun deck(
        epochDay: Long, scenario: Scenario, regenerate: Int, locked: List<OutfitPiece>,
        boldnessShift: Double = 0.0, weatherOverride: WindowWeather? = null,
    ): Pair<EngineContext, SuggestionDeck> = withContext(Dispatchers.Default) {
        val base = settingsRepo.current()
        val s = base.copy(boldness = (base.boldness + boldnessShift).coerceIn(0.0, 1.0))
        val ctx = context(epochDay, scenario, weatherOverride, s)
        val items = ctx.items.values.toList()
        val version = items.maxOfOrNull { it.updatedAtMillis } ?: 0L
        val deck = engine.generate(ctx, items, SuggestionRequest(s.deckSize, locked, regenerate, version + items.size))
        db.cache().logDeck(DeckLogEntity(epochDay = epochDay, scenarioId = scenario.id,
            json = DeckLog.encode(deck.cards.map { c -> DeckCardLog(c.signature, c.pieces.filter { it.slot.isCore }.map { it.itemId }) })))
        db.cache().trimDecks()
        ctx to deck
    }
}
