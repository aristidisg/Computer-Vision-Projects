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
import io.github.aristidisg.ensemble.engine.rules.Technique
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.WindowWeather

/** A context the wardrobe is used in, weighted by how often (scenario × temperature band). */
data class UsageContext(val scenario: Scenario, val weather: WindowWeather, val weight: Double, val label: String)

data class GapSuggestion(
    val archetype: Archetype,
    val color: ColorFamily,
    val unlocks: Int,
    val fills: List<String>,
    val score: Double,
    /** Your pieces it pairs with most often in the new outfits (IDs). */
    val pairsWith: List<String> = emptyList(),
    /** Pieces in fewer than 3 good outfits today that it would bring into play. */
    val rescued: Int = 0,
    /** Styling techniques the new outfits open up. */
    val techniques: Set<Technique> = emptySet(),
) {
    val title: String get() = "${color.label} ${archetype.sub.label.lowercase()}"
}

data class Orphan(val item: Item, val goodOutfits: Int, val pairsWith: List<Item>)

/**
 * Gap finder (SPEC 8.6): simulate adding each archetype in each typical color, count the new
 * outfits with Match ≥ 75 across the contexts you actually use (weighted by use), and rank.
 */
class GapFinder(private val engine: OutfitEngine = OutfitEngine(GeneratorConfig(beamWidth = 30, candidatesPerStep = 12, keepTop = 60))) {

    /**
     * Ranked by weighted new outfits, plus the pieces it rescues (worn in few good outfits now)
     * and the styling techniques it opens up. Pieces you already own in that colour are skipped,
     * and the list is diverse: one entry per kind of piece, at most two per colour.
     */
    fun findGaps(base: EngineContext, wardrobe: List<Item>, usage: List<UsageContext>, limit: Int = 10): List<GapSuggestion> {
        val owned = wardrobe.filter { it.status != ItemStatus.RETIRED }.mapNotNull { i -> i.primaryColor?.let { i.subcategory to it.family } }.toSet()
        val before = goodPerItem(base, wardrobe, usage)
        val meanWeight = usage.map { it.weight }.average().takeIf { !it.isNaN() } ?: 1.0
        val out = ArrayList<GapSuggestion>()
        for (arch in Archetypes.all) for (color in arch.colors) {
            if ((arch.sub to color) in owned) continue
            val item = ItemFactory.create("gap-${arch.sub.name}-${color.name}", arch.sub, listOf(KMeans.itemColor(ColorTables.family(color).hex, family = color)))
            if (!item.slots.first().isCore) continue
            val trial = wardrobe + item
            val locked = listOf(OutfitPiece(item.id, item.slots.first()))
            var unlocks = 0; var weighted = 0.0
            val fills = mutableListOf<String>()
            val partners = HashMap<String, Int>()
            val techniques = HashSet<Technique>()
            for (u in usage) {
                val ctx = base.copy(scenario = u.scenario, weather = u.weather)
                val good = engine.candidates(ctx.withItems(trial), trial, locked).filter { it.total >= GOOD }
                unlocks += good.size; weighted += good.size * u.weight
                if (good.isNotEmpty() && fills.size < 2) fills += u.label
                for (o in good) {
                    o.technique?.let { techniques += it }
                    o.pieces.filter { it.slot.isCore && it.itemId != item.id }.forEach { partners[it.itemId] = (partners[it.itemId] ?: 0) + 1 }
                }
            }
            if (unlocks == 0) continue
            val rescued = partners.keys.count { (before[it] ?: 0) < 3 }
            val score = weighted + RESCUE_WEIGHT * rescued * meanWeight + TECHNIQUE_WEIGHT * techniques.size * meanWeight
            val top = partners.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key }
            out += GapSuggestion(arch, color, unlocks, fills, score, top, rescued, techniques)
        }
        val ranked = out.sortedWith(compareByDescending<GapSuggestion> { it.score }.thenBy { it.title })
        val perSub = HashSet<Subcategory>(); val perColor = HashMap<ColorFamily, Int>()
        val diverse = ranked.filter { g ->
            val ok = g.archetype.sub !in perSub && (perColor[g.color] ?: 0) < 2
            if (ok) { perSub += g.archetype.sub; perColor[g.color] = (perColor[g.color] ?: 0) + 1 }
            ok
        }
        return diverse.take(limit)
    }

    /** Good outfits (Match ≥ [GOOD]) each piece is in today, across the contexts. */
    private fun goodPerItem(base: EngineContext, wardrobe: List<Item>, usage: List<UsageContext>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (u in usage) {
            val ctx = base.copy(scenario = u.scenario, weather = u.weather).withItems(wardrobe)
            engine.candidates(ctx, wardrobe).filter { it.total >= GOOD }.forEach { o -> o.itemIds.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        }
        return counts
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

    companion object {
        /** Match that counts as a good outfit. */
        const val GOOD = 0.75
        /** Score per rescued piece and per technique, in units of one (average-weight) new outfit. */
        const val RESCUE_WEIGHT = 3.0
        const val TECHNIQUE_WEIGHT = 2.0
    }

    /** Total possible core outfits (top × bottom × shoes) that pass hard constraints in a context. */
    fun totalOutfits(ctx: EngineContext, wardrobe: List<Item>): Int =
        engine.candidates(ctx.withItems(wardrobe), wardrobe, config = GeneratorConfig(beamWidth = 400, candidatesPerStep = 60, keepTop = 2000)).size
}
