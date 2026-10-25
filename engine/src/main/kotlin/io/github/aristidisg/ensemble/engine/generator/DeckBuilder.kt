package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.res
import io.github.aristidisg.ensemble.engine.rules.RuleGroup
import io.github.aristidisg.ensemble.engine.rules.Explain
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
        // Turns need history: without past decks there is no "least shown" yet.
        val nTurn = if (req.deckSize >= 8 && coreLocked.isEmpty() && ctx.recentDeckCount > 0) TURN_CARDS else 0
        // Turn cards are picked first, so the caps they use count against the rest of the deck.
        val turns = turns(candidates, emptyList(), nTurn, ctx, req.deckSize)
        val main = mmr(candidates, req.deckSize - nExplore - turns.size, ctx, byId, cfg.mmrLambda, rng, req.deckSize, turns)
        val explore = explore(candidates, main + turns, nExplore, ctx)
        fun acc(o: ScoredOutfit) = accessorize(o, ctx, wardrobe, req.locked, gen.pairs)
        // Accessorizing rescores a card, so the turn line is put back afterwards.
        val turnCards = turns.map { t -> acc(t).let { a -> a.copy(why = t.why.take(1) + a.why.take(2)) } }
        val deck = interleave(withTurns(main.map(::acc), turnCards), explore.map(::acc))
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

    /** Step 7: maximal marginal relevance, nudged toward 3+ harmony labels and 4+ different tops. */
    fun mmr(
        cands: List<ScoredOutfit>, n: Int, ctx: EngineContext, byId: Map<String, Item>, lambda: Double, rng: SeededRandom,
        deckSize: Int = n, already: List<ScoredOutfit> = emptyList(),
    ): List<ScoredOutfit> {
        val jitter = cands.associate { it.signature to rng.nextDouble() * 0.01 }
        val areas = cands.associate { c -> c.signature to (Look.of(c.pieces, ctx)?.pieceArea ?: emptyMap()) }
        val chosen = ArrayList<ScoredOutfit>()
        val usage = HashMap<String, Int>()
        already.flatMap { c -> c.pieces.filter { it.slot.isCore } }.forEach { usage[it.itemId] = (usage[it.itemId] ?: 0) + 1 }
        // Caps are sized for the whole deck, so the turn and new-idea cards fit under them too.
        val fair = FairShare(cands, ctx, deckSize)
        val pool = cands.filter { c -> already.none { it.signature == c.signature } }.toMutableList()
        val slotPools = cands.flatMap { c -> c.pieces.filter { it.slot.isCore } }.groupBy({ it.slot }, { it.itemId }).mapValues { it.value.distinct() }
        val distinctTops = cands.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }.toSet().size
        while (chosen.size < n && pool.isNotEmpty()) {
            val labels = chosen.map { it.harmony.substringBefore(" ·") }.toSet()
            val tops = chosen.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }
            // Fair-share caps first; when every card left breaks one, the card breaking them least.
            val allowed = fair.leastExcess(pool, usage)
            val next = allowed.maxBy { c ->
                val sim = (chosen + already).maxOfOrNull { similarity(c, it, areas.getValue(c.signature)) } ?: 0.0
                val top = c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId
                var div = 0.0
                if (labels.size < 3 && c.harmony.substringBefore(" ·") !in labels) div += 0.05
                if (distinctTops >= 4 && top != null && tops.count { it == top } >= 2) div -= 0.1
                lambda * c.total - (1 - lambda) * sim + div - fair.penalty(c, usage) + (jitter[c.signature] ?: 0.0)
            }
            pool.remove(next)
            // The first card stays the best outfit as it is; later ones are rebalanced.
            val card = if (chosen.isEmpty() && already.isEmpty()) next else rebalance(next, usage, fair, slotPools, ctx, byId).takeIf { r -> (chosen + already).none { it.signature == r.signature } } ?: next
            chosen += card
            card.pieces.filter { it.slot.isCore }.forEach { usage[it.itemId] = (usage[it.itemId] ?: 0) + 1 }
        }
        return chosen
    }

    /** Score a card may lose when an over-used piece is swapped for a fresher one. */
    const val REBALANCE_TOLERANCE = 0.08

    /**
     * When a card would push a piece past its fair-share cap, try the same card with that piece
     * swapped for the least-used piece of its slot that keeps the outfit valid and within
     * [REBALANCE_TOLERANCE] of its score. Fixes "the same trousers every other card" when the
     * candidates are all built around a few strong pieces.
     */
    fun rebalance(
        card: ScoredOutfit, usage: Map<String, Int>, fair: FairShare, slotPools: Map<Slot, List<String>>,
        ctx: EngineContext, byId: Map<String, Item>,
    ): ScoredOutfit {
        var best = card
        for (p in card.pieces.filter { it.slot.isCore }) {
            if ((usage[p.itemId] ?: 0) < fair.cap(p.slot)) continue
            val inCard = best.pieces.map { it.itemId }.toSet()
            val options = slotPools[p.slot].orEmpty()
                .filter { it !in inCard && (usage[it] ?: 0) < fair.cap(p.slot) }
                .sortedWith(compareBy({ usage[it] ?: 0 }, { fair.overuse(it, p.slot) }, { it }))
                .take(6)
            val swapped = options.mapNotNull { id ->
                rescore(best.pieces.map { if (it.itemId == p.itemId && it.slot == p.slot) OutfitPiece(id, p.slot) else it }, ctx, byId, card.newIdea)
            }.filter { it.total >= card.total - REBALANCE_TOLERANCE }.maxByOrNull { it.total }
            if (swapped != null) best = swapped
        }
        return best
    }

    /**
     * Step 8: "new idea" cards from candidates above the 70th percentile with the least feedback.
     * They respect the fair-share caps too: when nothing above the 70th percentile fits, the
     * median is the floor; caps are relaxed only when nothing fits at all.
     */
    fun explore(cands: List<ScoredOutfit>, chosen: List<ScoredOutfit>, n: Int, ctx: EngineContext): List<ScoredOutfit> {
        if (n <= 0) return emptyList()
        val sorted = cands.map { it.total }.sorted()
        val p70 = sorted[(sorted.size * 0.7).toInt().coerceAtMost(sorted.lastIndex)]
        val median = sorted[sorted.size / 2]
        val taken = chosen.map { it.signature }.toMutableSet()
        val usage = chosen.flatMap { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId } }.groupingBy { it }.eachCount().toMutableMap()
        val fair = FairShare(cands, ctx, chosen.size + n)  // whole deck
        // Novelty is summed per feature, so the rotation penalty is scaled to match.
        fun novelty(c: ScoredOutfit) =
            c.features.sumOf { f -> val v = ctx.prefs.value(f); 1.0 / sqrt(1.0 + ((v?.pos ?: 0.0) + (v?.neg ?: 0.0))) } - fair.penalty(c, usage) * c.features.size
        val out = ArrayList<ScoredOutfit>()
        repeat(n) {
            val open = cands.filter { it.signature !in taken }
            val pick = listOf(
                open.filter { it.total >= p70 && !fair.exceedsCap(it, usage) },
                open.filter { it.total >= median && !fair.exceedsCap(it, usage) },
                fair.leastExcess(open.filter { it.total >= median }, usage),
            ).firstOrNull { it.isNotEmpty() }?.sortedWith(compareByDescending<ScoredOutfit> { novelty(it) }.thenBy { it.signature })?.first() ?: return out
            taken += pick.signature
            pick.pieces.filter { it.slot.isCore }.forEach { usage[it.itemId] = (usage[it.itemId] ?: 0) + 1 }
            out += pick.copy(newIdea = true)
        }
        return out
    }

    /** Cards per deck reserved for the least-shown pieces. */
    const val TURN_CARDS = 2

    /**
     * "Give it a turn": the least-shown pieces (against their fair share, then by use in this
     * deck) each get their best decent outfit, so no piece is left out for long even when other
     * pieces score higher. Decent = at least the 30th-percentile candidate.
     */
    fun turns(cands: List<ScoredOutfit>, chosen: List<ScoredOutfit>, n: Int, ctx: EngineContext, deckSize: Int): List<ScoredOutfit> {
        if (n <= 0) return emptyList()
        val fair = FairShare(cands, ctx, deckSize)
        val floor = cands.map { it.total }.sorted()[cands.size * 3 / 10]
        val used = chosen.flatMap { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId } }.groupingBy { it }.eachCount().toMutableMap()
        val taken = chosen.map { it.signature }.toMutableSet()
        val pieces = cands.flatMap { c -> c.pieces.filter { it.slot == Slot.TOP || it.slot == Slot.BOTTOM || it.slot == Slot.MID || it.slot == Slot.OUTER } }
            .distinctBy { it.itemId }
            .sortedWith(compareBy({ fair.overuse(it.itemId, it.slot) + 0.5 * (used[it.itemId] ?: 0) }, { it.itemId }))
        val out = ArrayList<ScoredOutfit>()
        for (p in pieces) {
            if (out.size >= n) break
            if (out.any { o -> o.pieces.any { it.itemId == p.itemId } }) continue
            val best = cands.filter { c -> c.signature !in taken && c.total >= floor && c.pieces.any { it.itemId == p.itemId } && !fair.exceedsCap(c, used) }
                .maxByOrNull { it.total } ?: continue
            best.pieces.filter { it.slot.isCore }.forEach { used[it.itemId] = (used[it.itemId] ?: 0) + 1 }
            val name = ctx.items[p.itemId]?.name?.lowercase() ?: continue
            taken += best.signature
            out += best.copy(why = listOf(res("TURN", RuleGroup.ROTATION, 1.0, 0.0, Explain.GIVE_IT_A_TURN, "item" to name)) + best.why.take(2))
        }
        return out
    }

    /** Turn cards go second and fifth, where they are seen even if you swipe only a few. */
    private fun withTurns(main: List<ScoredOutfit>, turns: List<ScoredOutfit>): List<ScoredOutfit> {
        val out = main.toMutableList()
        turns.getOrNull(0)?.let { out.add(minOf(1, out.size), it) }
        turns.getOrNull(1)?.let { out.add(minOf(4, out.size), it) }
        return out
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
