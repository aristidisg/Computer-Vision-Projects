package io.github.aristidisg.ensemble.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.domain.DayWeather
import io.github.aristidisg.ensemble.domain.FeedbackRecorder
import io.github.aristidisg.ensemble.domain.Recorded
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.domain.WeatherService
import io.github.aristidisg.ensemble.engine.DemoWardrobe
import io.github.aristidisg.ensemble.engine.DeckOptions
import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Mood
import io.github.aristidisg.ensemble.engine.tables.Issue
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.WhyNot
import io.github.aristidisg.ensemble.model.WindowWeather
import io.github.aristidisg.ensemble.widget.WidgetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Swipe { LIKE, NOPE, WEAR }

data class MinimumSet(val tops: Int, val bottoms: Int, val shoes: Int) {
    val ok: Boolean get() = tops >= 2 && bottoms >= 2 && shoes >= 1
    val progress: Float get() = (minOf(tops, 2) + minOf(bottoms, 2) + minOf(shoes, 1)) / 5f
}

data class TodayState(
    val epochDay: Long = 0,
    val today: Long = 0,
    val scenarios: List<Scenario> = emptyList(),
    val scenario: Scenario? = null,
    val weather: DayWeather? = null,
    val place: String? = null,
    val cards: List<ScoredOutfit> = emptyList(),
    val items: Map<String, Item> = emptyMap(),
    val loading: Boolean = true,
    val minimum: MinimumSet = MinimumSet(0, 0, 0),
    val locked: List<OutfitPiece> = emptyList(),
    val whyNotFor: ScoredOutfit? = null,
    val undoCount: Int = 0,
    val message: String? = null,
    val weatherError: String? = null,
    /** Experiment mode: bolder, more creative suggestions. */
    val experiment: Boolean = false,
    /** Outerwear switched off: no jackets or coats in suggestions. */
    val noOuter: Boolean = false,
    /** Mid layers are switched off (Settings.midLayersOff). */
    val noMid: Boolean = false,
    val mood: Mood? = null,
    /** Only pairings never shown or worn together. */
    val freshPairs: Boolean = false,
    /** Today's colour to build around (not saved) and the families in the wardrobe to choose from. */
    val accent: ColorFamily? = null,
    val accentChoices: List<ColorFamily> = emptyList(),
    /** Vogue stylist: a short deck of cover looks, all on one [issue] (theme). */
    val vogue: Boolean = false,
    val issue: Issue? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val studio: Studio,
    private val recorder: FeedbackRecorder,
    private val weatherService: WeatherService,
    private val wardrobe: WardrobeRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val _state = MutableStateFlow(TodayState(epochDay = time.today(), today = time.today()))
    val state: StateFlow<TodayState> = _state.asStateFlow()

    private var ctx: EngineContext? = null
    private var regenerate = 0
    private var boldShift = 0.0
    private val undo = ArrayDeque<Pair<ScoredOutfit, Recorded>>()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            // Deck first, from the cached forecast: no waiting for location and network.
            val day = time.today()
            val before = weatherService.cachedFetchMillis()
            selectScenario(studio.defaultScenarioId(day), day)
            val r = weatherService.refresh()
            _state.update { it.copy(place = r.getOrNull()?.locationName ?: it.place, weatherError = r.exceptionOrNull()?.message) }
            // Rebuild only when a newer forecast actually arrived.
            if (r.getOrNull()?.fetchedAtMillis?.let { it != before } == true) generate()
        }
        viewModelScope.launch { settings.settings.collect { s -> _state.update { it.copy(experiment = s.experimentMode, noOuter = s.outerwearOff, noMid = s.midLayersOff, mood = s.mood, freshPairs = s.freshPairsOnly) } } }
        viewModelScope.launch {
            var ids: Set<String>? = null
            wardrobe.items.collect { items ->
                _state.update { it.copy(items = items.associateBy { i -> i.id }, minimum = minimum(items)) }
                // Pieces added or deleted elsewhere: rebuild the deck so it never shows a deleted
                // piece and new pieces get a chance. Swipes and wears keep the set, so no reshuffle.
                val now = items.map { it.id }.toSet()
                // Also when a card no longer fits the pieces: a layer unticked ("not a jacket"),
                // a piece sent to the laundry or to storage.
                val byId = items.associateBy { it.id }
                val stale = _state.value.cards.any { c -> c.pieces.any { p -> byId[p.itemId]?.let { i -> p.slot !in i.slots || !i.isAvailable } ?: true } }
                if (ids != null && (now != ids || stale) && _state.value.scenario != null && !_state.value.loading) generate()
                ids = now
            }
        }
    }

    private fun minimum(items: List<Item>): MinimumSet {
        val a = items.filter { it.isAvailable }
        return MinimumSet(a.count { Slot.TOP in it.slots }, a.count { Slot.BOTTOM in it.slots }, a.count { Slot.SHOES in it.slots })
    }

    fun selectDay(epochDay: Long) = viewModelScope.launch { selectScenario(studio.defaultScenarioId(epochDay), epochDay) }

    fun selectScenario(id: String, day: Long = _state.value.epochDay) {
        viewModelScope.launch {
            val scenarios = studio.scenarios()
            val sc = scenarios.firstOrNull { it.id == id } ?: studio.scenario(id)
            regenerate = 0; boldShift = 0.0
            _state.update { it.copy(epochDay = day, scenarios = scenarios, scenario = sc) }
            generate()
        }
    }

    fun more() { regenerate++; generate() }

    /** Outerwear on/off: a new deck straight away. */
    fun setNoOuter(on: Boolean) = viewModelScope.launch {
        settings.update { it.copy(outerwearOff = on) }
        regenerate++
        generate()
    }

    /** Mood, fresh pairs (saved), accent and Vogue (this deck only): each deals a new deck. */
    fun setMood(m: Mood?) = viewModelScope.launch { settings.update { it.copy(mood = m) }; regenerate++; generate() }

    fun setFreshPairs(on: Boolean) = viewModelScope.launch { settings.update { it.copy(freshPairsOnly = on) }; regenerate++; generate() }

    fun setAccent(f: ColorFamily?) { _state.update { it.copy(accent = f) }; regenerate++; generate() }

    fun setVogue(on: Boolean) { _state.update { it.copy(vogue = on) }; regenerate++; generate() }

    /** Mid layers on/off: a new deck straight away. */
    fun setNoMid(on: Boolean) = viewModelScope.launch {
        settings.update { it.copy(midLayersOff = on) }
        regenerate++
        generate()
    }

    /** Experiment on/off: a new deck straight away in the new mode. */
    fun setExperiment(on: Boolean) = viewModelScope.launch {
        settings.update { it.copy(experimentMode = on) }
        regenerate++
        generate()
    }
    fun bolder() { regenerate++; boldShift = (boldShift + 0.2).coerceAtMost(0.6); generate() }
    fun safer() { regenerate++; boldShift = (boldShift - 0.2).coerceAtLeast(-0.4); generate() }

    fun lock(piece: OutfitPiece) {
        _state.update { s -> s.copy(locked = (s.locked.filter { it.slot != piece.slot } + piece)) }
        regenerate++; generate()
    }

    fun buildAround(pieces: List<OutfitPiece>) { _state.update { it.copy(locked = pieces) }; regenerate++; generate() }

    fun clearLocks() { _state.update { it.copy(locked = emptyList()) }; generate() }

    fun setManualWeather(min: Double, max: Double) = viewModelScope.launch {
        val day = _state.value.epochDay
        settings.update { s -> s.copy(manualWeather = s.manualWeather + (day.toString() to WindowWeather(min, max, manual = true))) }
        generate()
    }

    fun clearManualWeather() = viewModelScope.launch {
        val day = _state.value.epochDay
        settings.update { s -> s.copy(manualWeather = s.manualWeather - day.toString()) }
        generate()
    }

    fun refreshWeather() = viewModelScope.launch {
        val r = weatherService.refresh(force = true)
        _state.update { it.copy(place = r.getOrNull()?.locationName ?: it.place, weatherError = r.exceptionOrNull()?.message) }
        generate()
    }

    fun generate() {
        val s = _state.value
        val sc = s.scenario ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val w = studio.dayWeather(s.epochDay, sc)
            val (c, deck) = studio.deck(s.epochDay, sc, regenerate, s.locked, boldShift, w.window, DeckOptions(s.accent, s.vogue))
            ctx = c
            // Colours you could build the day around: those of your available clothes, most common first.
            val choices = c.items.values.filter { it.isAvailable && it.primarySlot.isCore }.mapNotNull { it.primaryColor?.family }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            _state.update { it.copy(weather = w, cards = deck.cards, loading = false, whyNotFor = null, accentChoices = choices, issue = deck.issue) }
            if (s.epochDay == time.today() && regenerate == 0) deck.cards.firstOrNull()?.let { top ->
                WidgetState.save(app, top, c.items, w.window, sc.name)
            }
        }
    }

    /** A committed swipe on the top card. */
    fun swipe(card: ScoredOutfit, dir: Swipe) {
        val c = ctx ?: return
        _state.update { s -> s.copy(cards = s.cards.drop(1), whyNotFor = if (dir == Swipe.NOPE) card else null) }
        viewModelScope.launch {
            val rec = when (dir) {
                Swipe.LIKE -> recorder.like(card.pieces, c)
                Swipe.WEAR -> recorder.wear(card.pieces, c).also { _state.update { s -> s.copy(message = "wear") } }
                Swipe.NOPE -> recorder.nope(card.pieces, c)
            }
            push(card, rec)
        }
    }

    /** "Why not?" chip after a No: replaces the plain No with a reasoned one. */
    fun whyNot(reason: WhyNot, target: String? = null) {
        val c = ctx ?: return
        val card = _state.value.whyNotFor ?: return
        _state.update { it.copy(whyNotFor = null) }
        viewModelScope.launch {
            undo.lastOrNull()?.takeIf { it.first.signature == card.signature }?.let { (_, r) -> undo.removeLast(); recorder.undo(r) }
            push(card, recorder.nope(card.pieces, c, listOf(reason), target))
        }
    }

    fun dismissWhyNot() = _state.update { it.copy(whyNotFor = null) }

    fun pieceThumb(card: ScoredOutfit, itemId: String, up: Boolean) {
        val c = ctx ?: return
        viewModelScope.launch { recorder.pieceThumb(itemId, up, card.pieces, c) }
    }

    fun save(card: ScoredOutfit) {
        val c = ctx ?: return
        viewModelScope.launch { push(card, recorder.save(card.pieces, c)) }
    }

    private fun push(card: ScoredOutfit, r: Recorded) {
        undo.addLast(card to r)
        while (undo.size > 5) undo.removeFirst()
        _state.update { it.copy(undoCount = undo.size) }
    }

    /** Undo the last of up to 5 actions and put the card back on top. */
    fun undo() {
        val (card, rec) = undo.removeLastOrNull() ?: return
        _state.update { s -> s.copy(cards = listOf(card) + s.cards.filter { it.signature != card.signature }, undoCount = undo.size, whyNotFor = null) }
        viewModelScope.launch { recorder.undo(rec) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun seedDemo() = viewModelScope.launch {
        val text = app.assets.open("demo_wardrobe.json").bufferedReader().use { it.readText() }
        wardrobe.upsertAll(DemoWardrobe.parse(text, createdEpochDay = time.today() - 30), time.nowMillis())
        generate()
    }

    fun context(): EngineContext? = ctx
}
