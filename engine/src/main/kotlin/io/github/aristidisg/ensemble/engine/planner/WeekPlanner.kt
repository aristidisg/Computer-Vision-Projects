package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.OutfitEngine
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.generator.DeckBuilder
import io.github.aristidisg.ensemble.engine.generator.DeckPicks
import io.github.aristidisg.ensemble.engine.WearHistory
import io.github.aristidisg.ensemble.engine.generator.GeneratorConfig
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.withItems
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.model.WindowWeather

/** One day's input to the planner. A locked day keeps its pieces. */
data class DayContext(
    val epochDay: Long,
    val scenario: Scenario,
    val weather: WindowWeather,
    val locked: List<OutfitPiece>? = null,
)

data class PlannedDay(val day: DayContext, val outfit: ScoredOutfit?)

data class WeekPlan(val days: List<PlannedDay>, val conflicts: List<String>) {
    val score: Double get() = days.mapNotNull { it.outfit?.total }.average().takeIf { !it.isNaN() } ?: 0.0
}

/**
 * Weekly planner (SPEC 8.3): in date order under rotation, cooldowns, shoe rest days and a
 * laundry projection, each day picked for the week's variety (WeekPicker) from candidates
 * narrowed like the Today deck (accent, Vogue, fresh pairs), then finished with accessories;
 * one improvement pass swaps days whenever the total score rises.
 */
class WeekPlanner(private val engine: OutfitEngine = OutfitEngine(GeneratorConfig(beamWidth = 60, candidatesPerStep = 20))) {

    /**
     * [regenerate] changes the seed (each "Replan" deals a new week); [avoid] holds the core
     * signatures of the plan being replaced, which the new one steers away from.
     */
    fun plan(days: List<DayContext>, base: EngineContext, wardrobe: List<Item>, regenerate: Int = 0, avoid: Set<String> = emptySet()): WeekPlan {
        val history = base.history.logs.toMutableList()
        val wearCounts = HashMap<String, Int>()
        val planned = ArrayList<PlannedDay>()
        val sorted = days.sortedBy { it.epochDay }
        val picker = WeekPicker(SeededRandom.seedOf(sorted.firstOrNull()?.epochDay ?: 0L, "week", regenerate), avoid)
        for (d in sorted) {
            // Laundry projection: pieces that hit their wash threshold during the week drop out.
            val projected = wardrobe.map { item ->
                val extra = wearCounts[item.id] ?: 0
                val l = item.laundry.copy(wearsSinceWash = item.laundry.wearsSinceWash + extra)
                if (extra > 0 && l.atThreshold && item.laundry.wearsBeforeWash > 0) item.copy(laundry = l, status = ItemStatus.LAUNDRY) else item.copy(laundry = l)
            }
            val ctx = contextFor(base, d, history, projected)
            val week = planned.mapNotNull { it.outfit }
            val outfit = d.locked?.let { engine.score(it, ctx) } ?: run {
                val strict = DeckPicks.focus(engine.candidates(ctx.copy(settings = ctx.settings.copy(strictRotation = true)), projected), ctx, 7)
                val pool = strict.ifEmpty { DeckPicks.focus(engine.candidates(ctx, projected), ctx, 7) }
                picker.choose(pool, week, ctx)?.let { DeckBuilder.accessorize(it, ctx, projected) }
            }
            planned += PlannedDay(d, outfit)
            if (outfit != null) {
                history += WearLog("plan-${d.epochDay}", d.epochDay, outfit.signature, outfit.pieces, d.scenario.id, d.weather)
                outfit.itemIds.forEach { wearCounts[it] = (wearCounts[it] ?: 0) + 1 }
            }
        }
        val improved = improve(planned, base, wardrobe)
        return WeekPlan(improved, conflicts(improved, base, wardrobe))
    }

    private fun contextFor(base: EngineContext, d: DayContext, history: List<WearLog>, wardrobe: List<Item>): EngineContext =
        base.copy(scenario = d.scenario, weather = d.weather, epochDay = d.epochDay, history = WearHistory(history.toList()))
            .withItems(wardrobe)

    /** Swap two unlocked days' outfits whenever both stay valid and the total rises. */
    private fun improve(days: List<PlannedDay>, base: EngineContext, wardrobe: List<Item>): List<PlannedDay> {
        val cur = days.toMutableList()
        for (i in cur.indices) for (j in i + 1 until cur.size) {
            val a = cur[i]; val b = cur[j]
            if (a.outfit == null || b.outfit == null || a.day.locked != null || b.day.locked != null) continue
            val sa = rescore(b.outfit.pieces, a.day, base, wardrobe) ?: continue
            val sb = rescore(a.outfit.pieces, b.day, base, wardrobe) ?: continue
            if (sa.total + sb.total > a.outfit.total + b.outfit.total + 0.01) {
                cur[i] = PlannedDay(a.day, sa); cur[j] = PlannedDay(b.day, sb)
            }
        }
        return cur
    }

    private fun rescore(pieces: List<OutfitPiece>, d: DayContext, base: EngineContext, wardrobe: List<Item>): ScoredOutfit? {
        val ctx = contextFor(base, d, base.history.logs, wardrobe)
        val look = Look.of(pieces, ctx) ?: return null
        if (HardConstraints.outfitViolation(look, ctx) != null) return null
        if (look.placed.any { HardConstraints.itemViolation(it.item, it.slot, ctx) != null }) return null
        return Scorer.score(look, ctx)
    }

    /** Conflicts: a top repeated inside its rotation window, or a day with no valid outfit. */
    fun conflicts(days: List<PlannedDay>, base: EngineContext, wardrobe: List<Item>): List<String> {
        val byId = wardrobe.associateBy { it.id }
        val out = mutableListOf<String>()
        val lastTop = HashMap<String, Long>()
        for (d in days.sortedBy { it.day.epochDay }) {
            val o = d.outfit
            if (o == null) { out += "No valid outfit for ${d.day.scenario.name} on day ${d.day.epochDay}"; continue }
            val top = o.pieces.firstOrNull { it.slot == io.github.aristidisg.ensemble.model.Slot.TOP }?.itemId ?: continue
            val window = base.settings.rotationDays["BASE_TOP"] ?: 7
            lastTop[top]?.let { prev ->
                if (d.day.epochDay - prev < window) out += "${byId[top]?.name ?: "A top"} repeats within $window days"
            }
            lastTop[top] = d.day.epochDay
        }
        return out
    }
}
