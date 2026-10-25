package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot
import kotlin.math.sqrt

data class SuggestionRequest(
    val deckSize: Int = 12,
    val locked: List<OutfitPiece> = emptyList(),
    val regenerateCount: Int = 0,
    val wardrobeVersion: Long = 0,
)

data class SuggestionDeck(val cards: List<ScoredOutfit>, val candidateCount: Int)

/** Deck assembly (SPEC 6 steps 6-10): accessories pass, MMR diversity, exploration, determinism. */
object DeckBuilder {
    private val accessoryOrder = listOf(Slot.BELT, Slot.WATCH, Slot.BAG, Slot.HEADWEAR, Slot.EYEWEAR, Slot.NECK, Slot.JEWELRY, Slot.HANDS, Slot.POCKET)

    fun seed(ctx: EngineContext, req: SuggestionRequest): Long =
        SeededRandom.seedOf(ctx.epochDay, ctx.scenario.id, req.regenerateCount, req.wardrobeVersion, req.locked.joinToString { it.itemId })

    fun generate(ctx: EngineContext, wardrobe: List<Item>, req: SuggestionRequest, cfg: GeneratorConfig = GeneratorConfig()): SuggestionDeck {
        val seed = seed(ctx, req)
        val gen = Generator(ctx, cfg, seed)
        val coreLocked = req.locked.filter { it.slot.isCore }
        val candidates = gen.candidates(wardrobe, coreLocked)
        if (candidates.isEmpty()) return SuggestionDeck(emptyList(), 0)
        val rng = SeededRandom(seed xor 0x5DEECE66DL)
        val byId = wardrobe.associateBy { it.id }
        val nExplore = if (ctx.settings.explorationRate <= 0) 0 else (req.deckSize * ctx.settings.explorationRate).toInt()
        val main = mmr(candidates, req.deckSize - nExplore, ctx, byId, cfg.mmrLambda, rng)
        val explore = explore(candidates, main, nExplore, ctx)
        val deck = interleave(main, explore).map { accessorize(it, ctx, wardrobe, req.locked, gen.pairs) }
        return SuggestionDeck(deck, candidates.size)
    }

    /** Step 6: greedy best-improvement accessories within the scenario's budget, honoring C10/C11/AC rules. */
    fun accessorize(
        o: ScoredOutfit, ctx: EngineContext, wardrobe: List<Item>, locked: List<OutfitPiece> = emptyList(),
        pairs: PairCache = PairCache(ctx), perSlot: Int = 3,
    ): ScoredOutfit {
        if (ctx.settings.hideAccessories) return o
        val byId = wardrobe.associateBy { it.id }
        var pieces = o.pieces.filter { it.slot.isCore } + locked.filter { !it.slot.isCore }
        var best = rescore(pieces, ctx, byId, o.newIdea) ?: return o
        val max = ctx.scenario.accessoryMax
        val min = ctx.scenario.accessoryMin
        // Only the best few per slot by pair score with the core pieces get a full rescore.
        val core = pieces.mapNotNull { p -> byId[p.itemId]?.let { Placed(it, p.slot) } }
        val pools = accessoryOrder.associateWith { slot ->
            wardrobe.filter { HardConstraints.itemViolation(it, slot, ctx) == null }
                .sortedWith(compareByDescending<Item> { i -> core.sumOf { pairs.score(it, Placed(i, slot)) } }.thenBy { it.id })
                .take(perSlot)
        }
        while (pieces.count { !it.slot.isCore } < max) {
            var bestAdd: Pair<List<OutfitPiece>, ScoredOutfit>? = null
            val usedSlots = pieces.map { it.slot }.toSet()
            for (slot in accessoryOrder) {
                if (slot in usedSlots && slot != Slot.JEWELRY) continue
                if (slot == Slot.JEWELRY && pieces.count { it.slot == Slot.JEWELRY } >= 2) continue
                for (item in pools.getValue(slot)) {
                    if (pieces.any { it.itemId == item.id }) continue
                    val trial = pieces + OutfitPiece(item.id, slot)
                    val s = rescore(trial, ctx, byId, o.newIdea) ?: continue
                    if (bestAdd == null || s.total > bestAdd.second.total) bestAdd = trial to s
                }
            }
            val add = bestAdd ?: break
            val needMore = pieces.count { !it.slot.isCore } < min
            if (add.second.total > best.total + 0.001 || needMore) { pieces = add.first; best = add.second } else break
        }
        return best
    }

    private fun rescore(pieces: List<OutfitPiece>, ctx: EngineContext, byId: Map<String, Item>, newIdea: Boolean): ScoredOutfit? {
        val placed = pieces.map { Placed(byId[it.itemId] ?: return null, it.slot) }
        val look = Look(placed, ctx)
        if (HardConstraints.outfitViolation(look, ctx) != null) return null
        return Scorer.score(look, ctx, newIdea)
    }

    /** Similarity = shared item area + 0.2 for the same harmony label. */
    fun similarity(a: ScoredOutfit, b: ScoredOutfit, area: Map<String, Double>): Double {
        val shared = a.itemIds.intersect(b.itemIds.toSet()).sumOf { area[it] ?: 0.0 }
        val sameHarmony = if (a.harmony.substringBefore(" ·") == b.harmony.substringBefore(" ·")) 0.2 else 0.0
        return shared + sameHarmony
    }

    /** Penalty per earlier card in this deck that already uses the same core piece. */
    const val REUSE_PENALTY = 0.05
    /** Penalty for a card whose core pieces were all shown in every recent deck. */
    const val EXPOSURE_PENALTY = 0.25

    /**
     * Rotation across decks and within one: pieces already used in this deck, or shown in most
     * recent decks, make a card less attractive, so forgotten pieces get their turn even when
     * nothing is logged as worn.
     */
    fun rotationPenalty(c: ScoredOutfit, usage: Map<String, Int>, ctx: EngineContext): Double {
        val core = c.pieces.filter { it.slot.isCore }.map { it.itemId }
        if (core.isEmpty()) return 0.0
        val reuse = core.sumOf { usage[it] ?: 0 } * REUSE_PENALTY
        val exposure = core.sumOf { ctx.exposure(it) } / core.size * EXPOSURE_PENALTY
        return reuse + exposure
    }

    /** Step 7: maximal marginal relevance, nudged toward 3+ harmony labels and 4+ different tops. */
    fun mmr(cands: List<ScoredOutfit>, n: Int, ctx: EngineContext, byId: Map<String, Item>, lambda: Double, rng: SeededRandom): List<ScoredOutfit> {
        val jitter = cands.associate { it.signature to rng.nextDouble() * 0.01 }
        val areas = cands.associate { c -> c.signature to (Look.of(c.pieces, ctx)?.pieceArea ?: emptyMap()) }
        val chosen = ArrayList<ScoredOutfit>()
        val usage = HashMap<String, Int>()
        val pool = cands.toMutableList()
        val distinctTops = cands.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }.toSet().size
        while (chosen.size < n && pool.isNotEmpty()) {
            val labels = chosen.map { it.harmony.substringBefore(" ·") }.toSet()
            val tops = chosen.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }
            val next = pool.maxBy { c ->
                val sim = chosen.maxOfOrNull { similarity(c, it, areas.getValue(c.signature)) } ?: 0.0
                val top = c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId
                var div = 0.0
                if (labels.size < 3 && c.harmony.substringBefore(" ·") !in labels) div += 0.05
                if (distinctTops >= 4 && top != null && tops.count { it == top } >= 2) div -= 0.1
                lambda * c.total - (1 - lambda) * sim + div - rotationPenalty(c, usage, ctx) + (jitter[c.signature] ?: 0.0)
            }
            chosen += next
            pool.remove(next)
            next.pieces.filter { it.slot.isCore }.forEach { usage[it.itemId] = (usage[it.itemId] ?: 0) + 1 }
        }
        return chosen
    }

    /** Step 8: "new idea" cards from candidates above the 70th percentile with the least feedback. */
    fun explore(cands: List<ScoredOutfit>, chosen: List<ScoredOutfit>, n: Int, ctx: EngineContext): List<ScoredOutfit> {
        if (n <= 0) return emptyList()
        val sorted = cands.map { it.total }.sorted()
        val p70 = sorted[(sorted.size * 0.7).toInt().coerceAtMost(sorted.lastIndex)]
        val taken = chosen.map { it.signature }.toSet()
        val chosenUsage = chosen.flatMap { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId } }.groupingBy { it }.eachCount()
        return cands.filter { it.total >= p70 && it.signature !in taken }
            .sortedWith(compareByDescending<ScoredOutfit> { c ->
                // Novelty is summed per feature, so the rotation penalty is scaled to match.
                c.features.sumOf { f -> val v = ctx.prefs.value(f); 1.0 / sqrt(1.0 + ((v?.pos ?: 0.0) + (v?.neg ?: 0.0))) } -
                    rotationPenalty(c, chosenUsage, ctx) * c.features.size
            }.thenBy { it.signature })
            .take(n).map { it.copy(newIdea = true) }
    }

    /** About one card in five is an exploration card. */
    private fun interleave(main: List<ScoredOutfit>, explore: List<ScoredOutfit>): List<ScoredOutfit> {
        val out = ArrayList<ScoredOutfit>(); var e = 0
        for ((i, c) in main.withIndex()) {
            out += c
            if ((i + 1) % 4 == 0 && e < explore.size) out += explore[e++]
        }
        while (e < explore.size) out += explore[e++]
        return out
    }
}
