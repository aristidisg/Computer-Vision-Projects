package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.tables.WarmthTable
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OuterWeight
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot

data class GeneratorConfig(
    val beamWidth: Int = 120,
    val candidatesPerStep: Int = 40,
    val keepTop: Int = 200,
    val mmrLambda: Double = 0.75,
)

/** Slot structure: which optional core layers a candidate has. */
data class Structure(val mid: Boolean, val outer: Boolean)

/**
 * Beam search over the wardrobe (SPEC 6 steps 1-5): pools, structures, pair cache, beam search
 * and full scoring. Accessories, diversification and exploration live in [DeckBuilder].
 */
class Generator(private val ctx: EngineContext, private val cfg: GeneratorConfig = GeneratorConfig(), seed: Long = 0L) {
    private val rng = SeededRandom(seed)
    val pairs = PairCache(ctx)

    /** Step 1: per-slot pools after item-level hard constraints. Locked pieces replace their slot's pool. */
    fun pools(wardrobe: List<Item>, locked: List<OutfitPiece>): Map<Slot, List<Item>> {
        val byId = wardrobe.associateBy { it.id }
        val lockedSlots = locked.groupBy { it.slot }
        return Slot.entries.associateWith { slot ->
            lockedSlots[slot]?.mapNotNull { byId[it.itemId] }
                ?: wardrobe.filter { HardConstraints.itemViolation(it, slot, ctx) == null }
                    .sortedBy { it.id }
        }
    }

    /** Step 2: candidate structures from the feels-like band; the clo model has the final say. */
    fun structures(pools: Map<Slot, List<Item>>, locked: List<OutfitPiece>): List<Structure> {
        val feels = if (ctx.scenario.indoorShare >= 0.95) ctx.indoorTemp else ctx.weather.feelsMin - ctx.thermalOffset
        val opts = when {
            feels >= 24 -> listOf(Structure(false, false))
            feels >= 20 -> listOf(Structure(false, false), Structure(false, true))
            feels >= 16 -> listOf(Structure(true, false), Structure(false, true), Structure(false, false))
            feels >= 12 -> listOf(Structure(true, true), Structure(false, true), Structure(true, false))
            feels >= 8 -> listOf(Structure(true, true), Structure(false, true))
            else -> listOf(Structure(true, true))
        }
        val lockedSlots = locked.map { it.slot }.toSet()
        val forced = opts.map { s ->
            Structure(s.mid || Slot.MID in lockedSlots, s.outer || Slot.OUTER in lockedSlots)
        }.distinct()
        val feasible = forced.filter { s -> (!s.mid || pools[Slot.MID].orEmpty().isNotEmpty()) && (!s.outer || pools[Slot.OUTER].orEmpty().isNotEmpty()) }
        // Fall back to lighter structures when the wardrobe lacks layers.
        return feasible.ifEmpty { listOf(Structure(false, false), Structure(true, false), Structure(false, true)).filter { s ->
            (!s.mid || pools[Slot.MID].orEmpty().isNotEmpty()) && (!s.outer || pools[Slot.OUTER].orEmpty().isNotEmpty())
        } }
    }

    private class Partial(val pieces: List<Placed>, val score: Double)

    /** Step 4: beam search bottom -> top -> shoes -> mid -> outer. */
    fun beam(pools: Map<Slot, List<Item>>, structure: Structure): List<List<Placed>> {
        val order = buildList {
            add(Slot.BOTTOM); add(Slot.TOP)
            if (!ctx.scenario.shoesOptional || pools[Slot.SHOES].orEmpty().isNotEmpty()) add(Slot.SHOES)
            if (structure.mid) add(Slot.MID)
            if (structure.outer) add(Slot.OUTER)
        }
        var beam = listOf(Partial(emptyList(), 1.0))
        for (slot in order) {
            val pool = pools[slot].orEmpty()
            if (pool.isEmpty()) {
                if (slot == Slot.SHOES && ctx.scenario.shoesOptional) continue
                return emptyList()
            }
            val next = ArrayList<Partial>()
            for (p in beam) {
                val used = p.pieces.map { it.item.id }.toSet()
                val cands = pool.asSequence()
                    .filter { it.id !in used && fits(slot, it, p.pieces) }
                    .map { item ->
                        val placed = Placed(item, slot)
                        val s = if (p.pieces.isEmpty()) 0.75 else p.pieces.sumOf { pairs.score(it, placed) } / p.pieces.size
                        placed to s + rng.nextDouble() * 0.04
                    }
                    .sortedByDescending { it.second }
                    .take(cfg.candidatesPerStep)
                for ((placed, s) in cands) {
                    val n = p.pieces.size
                    next += Partial(p.pieces + placed, if (n == 0) s else (p.score * n + s) / (n + 1))
                }
            }
            beam = next.sortedByDescending { it.score }.take(cfg.beamWidth)
            if (beam.isEmpty()) return emptyList()
        }
        return beam.map { it.pieces }
    }

    /** Cheap structural checks during the beam so hard constraints fail early. */
    private fun fits(slot: Slot, item: Item, placed: List<Placed>): Boolean {
        if (slot == Slot.OUTER) {
            val mid = placed.firstOrNull { it.slot == Slot.MID }?.item ?: return true
            val cap = if (TaxonomyTable.of(item.subcategory).outer == OuterWeight.WARM) 3 else 2
            if (mid.layering.bulk > cap) return false
        }
        if (slot == Slot.MID) {
            val top = placed.firstOrNull { it.slot == Slot.TOP }?.item
            if (top != null && top.subcategory == item.subcategory) return false
        }
        return true
    }

    /** Steps 1-5: complete scored candidates, best first, all passing outfit-level hard constraints. */
    fun candidates(wardrobe: List<Item>, locked: List<OutfitPiece> = emptyList()): List<ScoredOutfit> {
        val pools = pools(wardrobe, locked)
        val seen = HashSet<String>()
        val out = ArrayList<ScoredOutfit>()
        for (st in structures(pools, locked)) {
            for (pieces in beam(pools, st)) {
                val look = Look(pieces, ctx)
                if (!seen.add(look.signature)) continue
                if (HardConstraints.outfitViolation(look, ctx) != null) continue
                out += Scorer.score(look, ctx)
            }
        }
        return out.sortedWith(compareByDescending<ScoredOutfit> { it.total }.thenBy { it.signature }).take(cfg.keepTop)
    }

    companion object {
        fun warmthLabel(feels: Double) = WarmthTable.label(feels)
    }
}
