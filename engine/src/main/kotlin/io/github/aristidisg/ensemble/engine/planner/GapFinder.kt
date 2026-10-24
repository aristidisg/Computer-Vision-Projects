package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.ItemFactory
import io.github.aristidisg.ensemble.engine.OutfitEngine
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.generator.GeneratorConfig
import io.github.aristidisg.ensemble.engine.generator.PairCache
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.tables.Archetype
import io.github.aristidisg.ensemble.engine.tables.Archetypes
import io.github.aristidisg.ensemble.engine.withItems
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.WindowWeather

/** A context the wardrobe is used in, weighted by how often (scenario × temperature band). */
data class UsageContext(val scenario: Scenario, val weather: WindowWeather, val weight: Double, val label: String)

data class GapSuggestion(val archetype: Archetype, val color: ColorFamily, val unlocks: Int, val fills: List<String>, val score: Double) {
    val title: String get() = "${color.label} ${archetype.sub.label.lowercase()}"
}

data class Orphan(val item: Item, val goodOutfits: Int, val pairsWith: List<Item>)

/**
 * Gap finder (SPEC 8.6): simulate adding each archetype in each typical color, count the new
 * outfits with Match ≥ 75 across the contexts you actually use (weighted by use), and rank.
 */
class GapFinder(private val engine: OutfitEngine = OutfitEngine(GeneratorConfig(beamWidth = 30, candidatesPerStep = 12, keepTop = 60))) {

    private fun goodCount(ctx: EngineContext, wardrobe: List<Item>, threshold: Double, lockedId: String? = null): Int {
        val locked = lockedId?.let { id -> wardrobe.first { it.id == id }.let { listOf(io.github.aristidisg.ensemble.model.OutfitPiece(it.id, it.slots.first())) } }.orEmpty()
        return engine.candidates(ctx.withItems(wardrobe), wardrobe, locked).count { it.total >= threshold }
    }

    fun findGaps(base: EngineContext, wardrobe: List<Item>, usage: List<UsageContext>, limit: Int = 10): List<GapSuggestion> {
        val out = ArrayList<GapSuggestion>()
        for (arch in Archetypes.all) for (color in arch.colors) {
            val item = ItemFactory.create("gap-${arch.sub.name}-${color.name}", arch.sub, listOf(KMeans.itemColor(ColorTables.family(color).hex, family = color)))
            if (!item.slots.first().isCore) continue
            val trial = wardrobe + item
            var unlocks = 0; var weighted = 0.0
            val fills = mutableListOf<String>()
            for (u in usage) {
                val ctx = base.copy(scenario = u.scenario, weather = u.weather)
                val n = goodCount(ctx, trial, 0.75, item.id)
                unlocks += n; weighted += n * u.weight
                if (n > 0 && fills.size < 2) fills += u.label
            }
            if (unlocks > 0) out += GapSuggestion(arch, color, unlocks, fills, weighted)
        }
        return out.sortedWith(compareByDescending<GapSuggestion> { it.score }.thenBy { it.title }).take(limit)
    }

    /** Orphans: pieces in fewer than 3 good outfits, with what would pair with them. */
    fun orphans(base: EngineContext, wardrobe: List<Item>, usage: List<UsageContext>): List<Orphan> {
        val counts = HashMap<String, Int>()
        for (u in usage) {
            val ctx = base.copy(scenario = u.scenario, weather = u.weather).withItems(wardrobe)
            engine.candidates(ctx, wardrobe).filter { it.total >= 0.70 }.forEach { o -> o.itemIds.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        }
        val cache = PairCache(base.withItems(wardrobe))
        return wardrobe.filter { it.isAvailable && it.slots.first().isCore && (counts[it.id] ?: 0) < 3 }.map { item ->
            val partners = wardrobe.filter { it.id != item.id && it.slots.first() != item.slots.first() && it.slots.first().isCore }
                .sortedByDescending { cache.score(Placed(item, item.slots.first()), Placed(it, it.slots.first())) }.take(3)
            Orphan(item, counts[item.id] ?: 0, partners)
        }
    }

    /** Total possible core outfits (top × bottom × shoes) that pass hard constraints in a context. */
    fun totalOutfits(ctx: EngineContext, wardrobe: List<Item>): Int =
        engine.candidates(ctx.withItems(wardrobe), wardrobe, config = GeneratorConfig(beamWidth = 400, candidatesPerStep = 60, keepTop = 2000)).size
}
