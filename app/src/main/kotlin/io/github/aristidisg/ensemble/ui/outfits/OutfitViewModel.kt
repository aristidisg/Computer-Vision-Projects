package io.github.aristidisg.ensemble.ui.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.domain.FeedbackRecorder
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.ScoredItem
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.DayPlan
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.OutfitSource
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OutfitState(
    val pieces: List<OutfitPiece> = emptyList(),
    val scenario: Scenario? = null,
    val epochDay: Long = 0,
    val scored: ScoredOutfit? = null,
    val items: Map<String, Item> = emptyMap(),
    val alternatives: List<ScoredItem> = emptyList(),
    val cooldown: Long? = null,
    val saved: Boolean = false,
    /** Composer: the best single swap and how much it adds. */
    val fix: Pair<ScoredItem, Slot>? = null,
)

/** Shared by outfit detail and the composer: scores any set of pieces live. */
@HiltViewModel
class OutfitViewModel @Inject constructor(
    private val studio: Studio,
    private val recorder: FeedbackRecorder,
    private val outfits: OutfitRepository,
    private val plans: PlanRepository,
) : ViewModel() {
    private val _s = MutableStateFlow(OutfitState())
    val state = _s.asStateFlow()
    private var ctx: EngineContext? = null

    fun load(pieces: String, scenarioId: String, epochDay: Long) = viewModelScope.launch {
        val sc = studio.scenario(scenarioId.ifEmpty { studio.defaultScenarioId(epochDay) })
        val c = studio.context(epochDay, sc)
        ctx = c
        _s.update { it.copy(scenario = sc, epochDay = epochDay, items = c.items) }
        rescore(PiecesArg.decode(pieces))
    }

    suspend fun allScenarios(): List<Scenario> = studio.scenarios()

    fun setScenario(id: String) = viewModelScope.launch {
        val s = _s.value
        val sc = studio.scenario(id)
        ctx = studio.context(s.epochDay, sc)
        _s.update { it.copy(scenario = sc) }
        rescore(s.pieces)
    }

    fun rescore(pieces: List<OutfitPiece>) = viewModelScope.launch {
        val c = ctx ?: return@launch
        val scored = withContext(Dispatchers.Default) { if (pieces.isEmpty()) null else studio.engine.score(pieces, c) }
        val saved = scored?.let { outfits.bySignature(it.signature)?.liked } ?: false
        val cooldown = scored?.let { HardConstraints.cooldownRemaining(it.signature, c) }
        _s.update { it.copy(pieces = pieces, scored = scored, saved = saved, cooldown = cooldown, fix = null) }
    }

    fun loadAlternatives(slot: Slot) = viewModelScope.launch {
        val c = ctx ?: return@launch
        val alts = withContext(Dispatchers.Default) { studio.engine.alternatives(_s.value.pieces, slot, c, c.items.values.toList(), 12) }
        _s.update { it.copy(alternatives = alts) }
    }

    fun swap(slot: Slot, itemId: String) = rescore(_s.value.pieces.filter { it.slot != slot } + OutfitPiece(itemId, slot))

    fun remove(slot: Slot) = rescore(_s.value.pieces.filter { it.slot != slot })

    /** Composer: find the single swap that most improves the score ("Brown belt clashes… swap to the black belt?"). */
    fun findFix() = viewModelScope.launch {
        val c = ctx ?: return@launch
        val s = _s.value
        val base = s.scored?.total ?: return@launch
        val best = withContext(Dispatchers.Default) {
            s.pieces.map { it.slot }.distinct().mapNotNull { slot ->
                studio.engine.alternatives(s.pieces, slot, c, c.items.values.toList(), 1).firstOrNull()?.let { it to slot }
            }.maxByOrNull { it.first.outfit.total }
        }
        _s.update { it.copy(fix = best?.takeIf { b -> b.first.outfit.total > base + 0.02 }) }
    }

    fun completeIt() = viewModelScope.launch {
        val c = ctx ?: return@launch
        val done = withContext(Dispatchers.Default) { studio.engine.complete(_s.value.pieces, c, c.items.values.toList(), 1).firstOrNull() }
        done?.let { rescore(it.pieces) }
    }

    fun wear(done: () -> Unit) = viewModelScope.launch { val c = ctx ?: return@launch; recorder.wear(_s.value.pieces, c); done() }

    fun save() = viewModelScope.launch {
        val c = ctx ?: return@launch
        recorder.save(_s.value.pieces, c)
        _s.update { it.copy(saved = true) }
    }

    fun saveComposed(done: () -> Unit) = viewModelScope.launch {
        val c = ctx ?: return@launch
        recorder.save(_s.value.pieces, c, OutfitSource.COMPOSED)
        done()
    }

    fun neverAgain(done: () -> Unit) = viewModelScope.launch { val c = ctx ?: return@launch; recorder.neverAgain(_s.value.pieces, c); done() }

    fun planFor(epochDay: Long) = viewModelScope.launch {
        val s = _s.value
        plans.savePlan(DayPlan(epochDay, s.scenario?.id ?: "casual", s.pieces, locked = true))
    }

    fun setCooldown(days: Int?) = viewModelScope.launch {
        val sig = _s.value.scored?.signature ?: return@launch
        val o = outfits.bySignature(sig) ?: Outfit(java.util.UUID.randomUUID().toString(), _s.value.pieces)
        outfits.save(o.copy(cooldownDays = days))
    }
}
