package io.github.aristidisg.ensemble.engine.generator

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Slot
import kotlin.math.ceil

/**
 * Wardrobe rotation as fair shares. With 12 cards and 8 usable bottoms, each bottom's fair
 * share is 1.5 cards per deck.
 * - Within a deck, a piece may appear at most its share rounded up (2 for those bottoms)
 *   while other cards are left.
 * - Across days, pieces shown more than their share in recent decks lose points; pieces
 *   shown less gain them.
 * No wear log is needed: what was shown counts.
 */
class FairShare(cands: List<ScoredOutfit>, private val ctx: EngineContext, deckSize: Int) {
    /** Usable pieces per core slot (every eligible piece has a candidate, see Generator). */
    private val pool: Map<Slot, Int> = cands.flatMap { c -> c.pieces.filter { it.slot.isCore } }
        .groupBy { it.slot }.mapValues { (_, ps) -> ps.map { it.itemId }.distinct().size }
    /** How often a slot is in a card at all (mid and outer layers are optional). */
    private val slotRate: Map<Slot, Double> = pool.keys.associateWith { s ->
        cands.count { c -> c.pieces.any { it.slot == s } }.toDouble() / cands.size.coerceAtLeast(1)
    }
    private val caps: Map<Slot, Int> = pool.mapValues { (s, n) -> ceil(deckSize * (slotRate[s] ?: 1.0) / n).toInt().coerceAtLeast(1) }

    fun cap(slot: Slot): Int = caps[slot] ?: Int.MAX_VALUE

    /** Expected share of cards showing one given piece of this slot. */
    private fun fair(slot: Slot): Double = (slotRate[slot] ?: 0.0) / (pool[slot] ?: 1)

    /**
     * How far past its fair share a piece was shown recently: 0 is fair, 1 is twice its share,
     * -1 is never shown. Clamped to -1..3.
     */
    fun overuse(itemId: String, slot: Slot): Double {
        val cards = ctx.recentDeckCount
        val f = fair(slot)
        if (cards <= 0 || f <= 0.0) return 0.0
        val shown = (ctx.recentDeckItems[itemId] ?: 0).toDouble() / cards
        return (shown / f - 1.0).coerceIn(-1.0, 3.0)
    }

    /** How many uses past their caps this card's pieces would go (0: within every cap). */
    fun excess(c: ScoredOutfit, usage: Map<String, Int>): Int =
        c.pieces.filter { it.slot.isCore }.sumOf { maxOf(0, (usage[it.itemId] ?: 0) + 1 - cap(it.slot)) }

    /** True when adding this card would push a piece past its cap for this deck. */
    fun exceedsCap(c: ScoredOutfit, usage: Map<String, Int>): Boolean = excess(c, usage) > 0

    /** The cards that break the caps least (all of them within caps whenever possible). */
    fun leastExcess(cards: List<ScoredOutfit>, usage: Map<String, Int>): List<ScoredOutfit> {
        if (cards.isEmpty()) return cards
        val e = cards.associateWith { excess(it, usage) }
        val min = e.values.min()
        return cards.filter { e.getValue(it) == min }
    }

    /** Score adjustment for a card: reuse inside this deck, and over/under-use across days. */
    fun penalty(c: ScoredOutfit, usage: Map<String, Int>): Double =
        c.pieces.filter { it.slot.isCore }.sumOf { p ->
            (usage[p.itemId] ?: 0) * REUSE_PENALTY + overuse(p.itemId, p.slot) * EXPOSURE_WEIGHT
        }

    companion object {
        /** Per earlier card in this deck that already used the piece. */
        const val REUSE_PENALTY = 0.05
        /** Per piece, per unit of overuse (a piece shown at twice its share costs 0.3). */
        const val EXPOSURE_WEIGHT = 0.3
    }
}
