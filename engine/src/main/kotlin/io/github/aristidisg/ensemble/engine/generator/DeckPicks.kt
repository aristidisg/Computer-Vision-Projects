package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.rules.EditorialRules
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.RuleGroup
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.res
import io.github.aristidisg.ensemble.model.Slot
import kotlin.math.sqrt

/** The special cards of a deck: turns, the signature card, new ideas, and what Today narrows to. */
object DeckPicks {
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
        // Novelty is the mean over the card's features (0..1: how little feedback they have), so the
        // freshness of its pieces weighs as much as in the main cards.
        fun novelty(c: ScoredOutfit) =
            c.features.map { f -> val v = ctx.prefs.value(f); 1.0 / sqrt(1.0 + ((v?.pos ?: 0.0) + (v?.neg ?: 0.0))) }.average().takeIf { !it.isNaN() }.let { it ?: 0.0 } -
                fair.penalty(c, usage)
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

    /**
     * Outfits shown in recent decks are held back: only fresh ones are used while there are
     * enough of them (twice the deck size, so diversity still has a choice); otherwise the
     * shown ones fill in at the end.
     */
    fun freshFirst(cands: List<ScoredOutfit>, ctx: EngineContext, deckSize: Int): List<ScoredOutfit> {
        if (ctx.shownOutfits.isEmpty()) return cands
        val (fresh, shown) = cands.partition { DeckBuilder.coreSignature(it) !in ctx.shownOutfits }
        return if (fresh.size >= deckSize * 2) fresh else fresh + shown.take(deckSize * 2 - fresh.size)
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
            // The rest of the outfit should be fresh too, not yesterday's pieces around a new one.
            val best = cands.filter { c -> c.signature !in taken && c.total >= floor && c.pieces.any { it.itemId == p.itemId } && visible(c, p.itemId, ctx) && !fair.exceedsCap(c, used) }
                .maxByOrNull { it.total - fair.penalty(it, used) } ?: continue
            best.pieces.filter { it.slot.isCore }.forEach { used[it.itemId] = (used[it.itemId] ?: 0) + 1 }
            val name = ctx.items[p.itemId]?.name?.lowercase() ?: continue
            taken += best.signature
            out += best.copy(why = listOf(res("TURN", RuleGroup.ROTATION, 1.0, 0.0, Explain.GIVE_IT_A_TURN, "item" to name)) + best.why.take(2))
        }
        return out
    }

    /** Turn cards go second and fifth, the signature card third: seen even if you swipe only a few. */
    fun withInserts(main: List<ScoredOutfit>, turns: List<ScoredOutfit>, signature: List<ScoredOutfit>): List<ScoredOutfit> {
        val out = main.toMutableList()
        turns.getOrNull(0)?.let { out.add(minOf(1, out.size), it) }
        signature.firstOrNull()?.let { out.add(minOf(2, out.size), it) }
        turns.getOrNull(1)?.let { out.add(minOf(4, out.size), it) }
        return out
    }

    /** About one card in five is an exploration card. */
    fun interleave(main: List<ScoredOutfit>, explore: List<ScoredOutfit>): List<ScoredOutfit> {
        val out = ArrayList<ScoredOutfit>(); var e = 0
        for ((i, c) in main.withIndex()) {
            out += c
            if ((i + 1) % 4 == 0 && e < explore.size) out += explore[e++]
        }
        while (e < explore.size) out += explore[e++]
        return out
    }

    /** Lines that say why a card is in the deck; they survive the accessories rescore. */
    val PINNED = setOf("TURN", "SIGN", "FRESH")

    /** True when the piece reads in the outfit (a piece you're meant to notice isn't a collar under a hoodie). */
    fun visible(c: ScoredOutfit, itemId: String, ctx: EngineContext): Boolean {
        val p = c.pieces.firstOrNull { it.itemId == itemId } ?: return false
        return Look.of(c.pieces, ctx)?.isVisible(p.slot) ?: false
    }

    /** How many pairs of the outfit's clothes (shoes aside) were shown or worn together before. */
    fun seenPairs(c: ScoredOutfit, ctx: EngineContext): Int {
        val ids = pairIds(c.pieces)
        var n = 0
        for (i in ids.indices) for (j in i + 1 until ids.size) if (EngineContext.pairKey(ids[i], ids[j]) in ctx.shownPairs) n++
        return n
    }

    /** The pieces fresh-pairs mode compares: clothes, not shoes (one pair of sneakers goes with everything). */
    fun pairIds(pieces: List<io.github.aristidisg.ensemble.model.OutfitPiece>): List<String> =
        pieces.filter { it.slot.isCore && it.slot != Slot.SHOES }.map { it.itemId }

    /** Pair keys for pieces shown or worn together, as [EngineContext.shownPairs] stores them. */
    fun pairKeys(ids: List<String>, items: Map<String, io.github.aristidisg.ensemble.model.Item>): List<String> {
        val clothes = ids.distinct().filter { id -> items[id]?.primarySlot?.let { it.isCore && it != Slot.SHOES } == true }
        return clothes.flatMapIndexed { i, a -> clothes.drop(i + 1).map { b -> EngineContext.pairKey(a, b) } }
    }

    /** Cards in a Vogue stylist deck: a handful of cover looks. */
    const val VOGUE_DECK = 6

    /**
     * Narrow the candidates to what Today asked for: the accent colour visible in every card;
     * Vogue looks that use a technique; in fresh-pairs mode, outfits whose pieces were never
     * shown together (or, when fewer than half a deck, the fewest repeated pairs).
     */
    fun focus(cands: List<ScoredOutfit>, ctx: EngineContext, deckSize: Int): List<ScoredOutfit> {
        var out = cands
        ctx.options.accent?.let { a ->
            val with = out.filter { c -> Look.of(c.pieces, ctx)?.let { EditorialRules.accentShare(it, a) >= EditorialRules.ACCENT_MIN } == true }
            if (with.isNotEmpty()) out = with
        }
        if (ctx.options.vogue) out.filter { it.technique != null }.takeIf { it.size >= 3 }?.let { out = it }
        if (ctx.settings.freshPairsOnly) {
            val seen = out.associate { it.signature to seenPairs(it, ctx) }
            val fresh = out.filter { seen[it.signature] == 0 }
            val line = res("FRESH", RuleGroup.ROTATION, 1.0, 0.0, Explain.FRESH_PAIR)
            // Half a deck of new pairings beats a full deck of repeats; below that, the fewest repeats fill in.
            out = if (fresh.size * 2 >= deckSize) fresh.map { it.copy(why = listOf(line) + it.why.take(2)) }
            else fresh.map { it.copy(why = listOf(line) + it.why.take(2)) } +
                out.filter { seen[it.signature] != 0 }.sortedWith(compareBy<ScoredOutfit> { seen[it.signature] }.thenByDescending { it.total }).take(deckSize * 3)
        }
        return out
    }

    /**
     * One card per deck built around a signature piece, a different one each day, always with
     * the piece visible and preferably in an outfit you haven't seen.
     */
    fun signature(cands: List<ScoredOutfit>, chosen: List<ScoredOutfit>, ctx: EngineContext, req: SuggestionRequest): List<ScoredOutfit> {
        if (req.deckSize < 6 || req.locked.isNotEmpty()) return emptyList()
        val sig = ctx.items.values.filter { it.flags.signature && it.isAvailable && it.primarySlot.isCore }.sortedBy { it.id }
        if (sig.isEmpty()) return emptyList()
        val taken = chosen.map { it.signature }.toSet()
        val start = Math.floorMod(ctx.epochDay + req.regenerateCount, sig.size.toLong()).toInt()
        for (k in sig.indices) {
            val piece = sig[(start + k) % sig.size]
            val best = cands.filter { it.signature !in taken && visible(it, piece.id, ctx) }
                .maxByOrNull { it.total - if (DeckBuilder.coreSignature(it) in ctx.shownOutfits) SHOWN_COST else 0.0 } ?: continue
            val line = res("SIGN", RuleGroup.FORMALITY_STYLE, 1.0, 0.0, Explain.SIGNATURE_CARD, "item" to piece.name.lowercase())
            return listOf(best.copy(why = listOf(line) + best.why.filter { it.id != "O4" }.take(2)))
        }
        return emptyList()
    }

    /** Score a signature card gives up to avoid an outfit already shown. */
    const val SHOWN_COST = 0.2
}
