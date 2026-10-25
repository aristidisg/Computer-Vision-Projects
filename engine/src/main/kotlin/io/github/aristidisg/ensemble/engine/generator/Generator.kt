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
    /** Candidates kept per core piece on top of the best [keepTop]. */
    val perPiece: Int = 4,
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
        // Outerwear switched off: no outer layer unless one is locked in by hand.
        val allowOuter = !ctx.settings.outerwearOff || Slot.OUTER in lockedSlots
        val forced = opts.map { s -> if (allowOuter) s else s.copy(outer = false) }.distinct().map { s ->
            Structure(s.mid || Slot.MID in lockedSlots, s.outer || Slot.OUTER in lockedSlots)
        }.distinct()
        val feasible = forced.filter { s -> (!s.mid || pools[Slot.MID].orEmpty().isNotEmpty()) && (!s.outer || pools[Slot.OUTER].orEmpty().isNotEmpty()) }
        // Fall back to lighter structures when the wardrobe lacks layers.
        return feasible.ifEmpty { listOf(Structure(false, false), Structure(true, false), Structure(false, true)).filter { s ->
            (!s.mid || pools[Slot.MID].orEmpty().isNotEmpty()) && (!s.outer || (allowOuter && pools[Slot.OUTER].orEmpty().isNotEmpty()))
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
            fun extend(p: Partial, item: Item): Partial {
                val placed = Placed(item, slot)
                val s = (if (p.pieces.isEmpty()) 0.75 else p.pieces.sumOf { pairs.score(it, placed) } / p.pieces.size) + rng.nextDouble() * 0.04
                val n = p.pieces.size
                return Partial(p.pieces + placed, if (n == 0) s else (p.score * n + s) / (n + 1))
            }
            for (p in beam) {
                val used = p.pieces.map { it.item.id }.toSet()
                next += pool.asSequence()
                    .filter { it.id !in used && fits(slot, it, p.pieces) }
                    .map { extend(p, it) }
                    .sortedByDescending { it.score }
                    .take(cfg.candidatesPerStep)
            }
            // Coverage: every piece in the pool gets at least one partial, built on the best
            // partial it fits, so pieces with modest pair scores are never silently dropped.
            val placedIds = next.map { it.pieces.last().item.id }.toHashSet()
            for (item in pool) {
                if (item.id in placedIds) continue
                val host = beam.firstOrNull { p -> p.pieces.none { it.item.id == item.id } && fits(slot, item, p.pieces) } ?: continue
                next += extend(host, item)
            }
            val ranked = next.sortedByDescending { it.score }
            // The best partial holding each piece survives later steps too, not just this one.
            val bestPerItem = ranked.flatMap { p -> p.pieces.map { it.item.id to p } }.distinctBy { it.first }.map { it.second }
            beam = (ranked.take(cfg.beamWidth) + bestPerItem).distinct()
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
        val ranked = out.sortedWith(compareByDescending<ScoredOutfit> { it.total }.thenBy { it.signature })
        // Keep the best few outfits for every core piece too, so the deck can rotate the whole
        // wardrobe: one candidate per piece is used up after a single appearance.
        val bestPerItem = ranked.flatMap { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId to c } }
            .groupBy({ it.first }, { it.second }).values.flatMap { it.take(cfg.perPiece) }
        val top = ranked.take(cfg.keepTop)
        val keep = (top + bestPerItem).distinctBy { it.signature }
        return keep.sortedWith(compareByDescending<ScoredOutfit> { it.total }.thenBy { it.signature })
    }

    companion object {
        fun warmthLabel(feels: Double) = WarmthTable.label(feels)
    }
}
