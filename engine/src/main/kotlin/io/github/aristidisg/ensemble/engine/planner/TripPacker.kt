package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.OutfitEngine
import io.github.aristidisg.ensemble.engine.generator.GeneratorConfig
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.withItems
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Slot

data class TripRequest(
    val days: List<DayContext>,
    val luggageLimit: Int = 14,
    val shoeLimit: Int = 2,
    val laundryAvailable: Boolean = false,
    val goodMatch: Double = 0.70,
)

data class PackingPlan(
    val packed: List<Item>,
    val dayOutfits: List<PlannedDay>,
    val travelDay: List<Item>,
    val uncoveredDays: List<Long>,
)

/**
 * Trip packing by greedy maximum coverage (SPEC 8.5): repeatedly add the piece whose gain (new
 * good outfits covering still-uncovered days, divided by its cost) is best, until every day has a
 * distinct outfit or a limit is reached; then assign outfits to days without repeats.
 */
class TripPacker(private val engine: OutfitEngine = OutfitEngine(GeneratorConfig(beamWidth = 60, candidatesPerStep = 20))) {

    private fun cost(item: Item) = if (item.slots.first() == Slot.SHOES || item.slots.first() == Slot.OUTER) 1.5 else 1.0

    fun pack(req: TripRequest, base: EngineContext, wardrobe: List<Item>): PackingPlan {
        val available = wardrobe.filter { it.isAvailable }
        val dayCtx = req.days.associate { d -> d.epochDay to base.copy(scenario = d.scenario, weather = d.weather, epochDay = d.epochDay).withItems(available) }
        // Good outfits per day from the whole wardrobe (core only; accessories are optional extras).
        val good: Map<Long, List<ScoredOutfit>> = req.days.associate { d ->
            d.epochDay to engine.candidates(dayCtx.getValue(d.epochDay), available).filter { it.total >= req.goodMatch }.take(40)
        }
        val byId = available.associateBy { it.id }
        val packed = LinkedHashSet<String>()
        val covered = HashMap<Long, ScoredOutfit>()
        fun shoesPacked() = packed.count { byId[it]?.slots?.first() == Slot.SHOES }
        val maxPieces = if (req.laundryAvailable) minOf(req.luggageLimit, 10) else req.luggageLimit

        while (packed.size < maxPieces && covered.size < req.days.size) {
            val uncovered = req.days.map { it.epochDay }.filter { it !in covered }
            var best: Pair<String, Double>? = null
            val candidates = good.filterKeys { it in uncovered }.values.flatten().flatMap { it.itemIds }.toSet() - packed
            for (id in candidates.sorted()) {
                val item = byId[id] ?: continue
                if (item.slots.first() == Slot.SHOES && shoesPacked() >= req.shoeLimit) continue
                val trial = packed + id
                var gain = 0.0
                for (day in uncovered) {
                    // Progress toward the best outfit this piece belongs to; completing one counts double.
                    gain += good[day].orEmpty().filter { id in it.itemIds }.maxOfOrNull { o ->
                        val f = o.itemIds.count { it in trial }.toDouble() / o.itemIds.size
                        if (f >= 1.0) 2.0 else f
                    } ?: 0.0
                }
                val g = gain / cost(item)
                if (best == null || g > best.second + 1e-9) best = id to g
            }
            val pick = best ?: break
            packed += pick.first
            for (day in uncovered) {
                val used = covered.values.map { it.signature }.toSet()
                good[day].orEmpty().firstOrNull { o -> o.signature !in used && o.itemIds.all { it in packed } }?.let { covered[day] = it }
            }
        }
        // Assign outfits to days, avoiding repeats where possible, from the packed set only.
        val packedItems = packed.mapNotNull { byId[it] }
        val assigned = ArrayList<PlannedDay>()
        val usedSigs = HashSet<String>()
        for (d in req.days.sortedBy { it.epochDay }) {
            val opts = engine.candidates(dayCtx.getValue(d.epochDay).withItems(packedItems), packedItems)
            val pick = opts.firstOrNull { it.signature !in usedSigs } ?: opts.firstOrNull()
            pick?.let { usedSigs += it.signature }
            assigned += PlannedDay(d, pick)
        }
        val travel = packedItems.filter { it.slots.first() == Slot.OUTER || it.slots.first() == Slot.SHOES }
            .sortedByDescending { it.warmthClo + it.layering.bulk * 0.1 }.groupBy { it.slots.first() }.mapNotNull { it.value.firstOrNull() }
        return PackingPlan(packedItems, assigned, travel, assigned.filter { it.outfit == null }.map { it.day.epochDay })
    }
}
