package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot

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
        val all = gen.candidates(wardrobe, coreLocked)
        if (all.isEmpty()) return SuggestionDeck(emptyList(), 0)
        val focused = DeckPicks.focus(all, ctx, req.deckSize)
        val candidates = DeckPicks.freshFirst(focused, ctx, req.deckSize)
        val rng = SeededRandom(seed xor 0x5DEECE66DL)
        val byId = wardrobe.associateBy { it.id }
        // Vogue stylist decks are all cover looks: no new-idea or turn cards.
        val vogue = ctx.options.vogue
        val nExplore = if (ctx.explorationRate <= 0 || vogue) 0 else (req.deckSize * ctx.explorationRate).toInt()
        // Turns need history: without past decks there is no "least shown" yet.
        val nTurn = if (req.deckSize >= 8 && coreLocked.isEmpty() && ctx.recentDeckCount > 0 && !vogue) DeckPicks.TURN_CARDS else 0
        // Turn cards are picked first, so the caps they use count against the rest of the deck.
        val turns = DeckPicks.turns(candidates, emptyList(), nTurn, ctx, req.deckSize)
        val signature = DeckPicks.signature(focused, turns, ctx, req)
        // Experiment weighs variety more against score.
        val lambda = if (ctx.settings.experimentMode) cfg.mmrLambda - 0.15 else cfg.mmrLambda
        val main = mmr(candidates, req.deckSize - nExplore - turns.size - signature.size, ctx, byId, lambda, rng, req.deckSize, turns + signature)
        val explore = DeckPicks.explore(candidates, main + turns + signature, nExplore, ctx)
        // Accessorizing rescores a card, so a line saying why the card is here is put back afterwards.
        fun acc(o: ScoredOutfit): ScoredOutfit {
            val a = accessorize(o, ctx, wardrobe, req.locked, gen.pairs)
            val pin = o.why.firstOrNull()?.takeIf { it.id in DeckPicks.PINNED } ?: return a
            return a.copy(why = listOf(pin) + a.why.filter { it.id != pin.id }.take(2))
        }
        val deck = DeckPicks.interleave(DeckPicks.withInserts(main.map(::acc), turns.map(::acc), signature.map(::acc)), explore.map(::acc))
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
        // A cover look is finished: at least two accessories when the occasion allows them.
        val min = if (ctx.options.vogue) maxOf(ctx.scenario.accessoryMin, minOf(2, max)) else ctx.scenario.accessoryMin
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
        val looks = cands.associate { c -> c.signature to Look.of(c.pieces, ctx) }
        val areas = looks.mapValues { (_, l) -> l?.pieceArea ?: emptyMap() }
        // Colour story per card: upper-body and bottom families, for colour variety in the deck.
        val palette = HashMap<String, Pair<ColorFamily?, ColorFamily?>>()
        fun colorsOf(c: ScoredOutfit) = palette.getOrPut(c.signature) {
            val l = looks[c.signature] ?: Look.of(c.pieces, ctx)
            (l?.upperDominant?.family) to (l?.dominant(l.bottom)?.family)
        }
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
            val picked = chosen + already
            val techniques = picked.mapNotNull { it.technique }.groupingBy { it }.eachCount()
            val uppers = picked.mapNotNull { colorsOf(it).first }.groupingBy { it }.eachCount()
            val bottoms = picked.mapNotNull { colorsOf(it).second }.groupingBy { it }.eachCount()
            val tops = chosen.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }
            // Breaking a fair-share cap costs heavily (not a filter), so when every card left breaks
            // some cap, freshness and score still decide among them.
            val next = pool.maxBy { c ->
                val sim = (chosen + already).maxOfOrNull { similarity(c, it, areas.getValue(c.signature)) } ?: 0.0
                val top = c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId
                var div = 0.0
                if (labels.size < 3 && c.harmony.substringBefore(" ·") !in labels) div += 0.05
                if (distinctTops >= 4 && top != null && tops.count { it == top } >= 2) div -= 0.1
                // A stylist's deck: techniques it hasn't shown yet, and no third blue top or
                // third pair of grey trousers in a row.
                if (picked.isNotEmpty()) c.technique?.let { t -> div += if (t !in techniques) TECHNIQUE_VARIETY else -TECHNIQUE_VARIETY * ((techniques[t] ?: 0) - 1).coerceAtLeast(0) }
                val (up, down) = colorsOf(c)
                if (up != null) div -= COLOR_REPEAT * ((uppers[up] ?: 0) - 1).coerceAtLeast(0)
                if (down != null) div -= COLOR_REPEAT * ((bottoms[down] ?: 0) - 1).coerceAtLeast(0)
                lambda * c.total - (1 - lambda) * sim + div - fair.penalty(c, usage) - EXCESS_PENALTY * fair.excess(c, usage) + (jitter[c.signature] ?: 0.0)
            }
            pool.remove(next)
            // The first card stays the best outfit as it is; later ones are rebalanced.
            val card = if (chosen.isEmpty() && already.isEmpty()) next else rebalance(next, usage, fair, slotPools, ctx, byId).takeIf { r -> (chosen + already).none { it.signature == r.signature } } ?: next
            chosen += card
            card.pieces.filter { it.slot.isCore }.forEach { usage[it.itemId] = (usage[it.itemId] ?: 0) + 1 }
        }
        return chosen
    }

    /** Bonus for a technique new to this deck (and cost per repeat after the second). */
    const val TECHNIQUE_VARIETY = 0.06
    /** Cost per card past the first that repeats an upper-body (or bottom) colour family. */
    const val COLOR_REPEAT = 0.05

    /** Cost per use past a piece's fair-share cap in this deck. */
    const val EXCESS_PENALTY = 0.4

    /** Score a card may lose when an over-used piece is swapped for a fresher one. */
    const val REBALANCE_TOLERANCE = 0.12

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
                .take(8)
            val swapped = options.mapNotNull { id ->
                rescore(best.pieces.map { if (it.itemId == p.itemId && it.slot == p.slot) OutfitPiece(id, p.slot) else it }, ctx, byId, card.newIdea)
            }.filter { it.total >= card.total - REBALANCE_TOLERANCE && coreSignature(it) !in ctx.shownOutfits }.maxByOrNull { it.total }
            if (swapped != null) best = swapped
        }
        return best
    }

    /** Signature of an outfit's core pieces (accessories don't make an outfit new). */
    fun coreSignature(c: ScoredOutfit): String = Outfit.signatureOf(c.pieces.filter { it.slot.isCore }.map { it.itemId })

}
