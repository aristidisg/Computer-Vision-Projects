package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.engine.generator.DeckBuilder
import io.github.aristidisg.ensemble.engine.generator.DeckPicks
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.ColorFamily

/**
 * Chooses one day's outfit for a week the way the Today deck mixes a deck: score, minus what the
 * week already has (same pieces, same top or trouser colour, same technique), plus a fresh
 * technique, an unused signature piece, and a seeded nudge so "Replan" deals a new week.
 */
class WeekPicker(seed: Long, private val avoid: Set<String>) {
    private val rng = SeededRandom(seed)
    private val looks = HashMap<String, Pair<ColorFamily?, ColorFamily?>>()

    private fun colors(c: ScoredOutfit, ctx: EngineContext) = looks.getOrPut(c.signature) {
        val l = Look.of(c.pieces, ctx)
        l?.upperDominant?.family to l?.let { it.dominant(it.bottom)?.family }
    }

    fun choose(pool: List<ScoredOutfit>, week: List<ScoredOutfit>, ctx: EngineContext): ScoredOutfit? {
        val cands = pool.take(POOL)
        if (cands.isEmpty()) return null
        val weekCore = week.map { w -> w.pieces.filter { it.slot.isCore }.map { it.itemId }.toSet() }
        val uppers = week.mapNotNull { colors(it, ctx).first }.groupingBy { it }.eachCount()
        val bottoms = week.mapNotNull { colors(it, ctx).second }.groupingBy { it }.eachCount()
        val techniques = week.mapNotNull { it.technique }.groupingBy { it }.eachCount()
        val usedIds = week.flatMap { it.itemIds }.toSet()
        val signature = ctx.items.values.filter { it.flags.signature && it.isAvailable && it.id !in usedIds }.map { it.id }.toSet()
        return cands.maxBy { c ->
            val core = c.pieces.filter { it.slot.isCore }.map { it.itemId }
            val shared = weekCore.maxOfOrNull { w -> core.count { it in w }.toDouble() / core.size.coerceAtLeast(1) } ?: 0.0
            val (up, down) = colors(c, ctx)
            var v = c.total - SHARED * shared
            if (up != null) v -= COLOR_REPEAT * (uppers[up] ?: 0)
            if (down != null) v -= COLOR_REPEAT * (bottoms[down] ?: 0)
            c.technique?.let { t -> v += if (t !in techniques) TECHNIQUE_NEW else -TECHNIQUE_REPEAT * (techniques[t] ?: 0) }
            val sig = DeckBuilder.coreSignature(c)
            if (sig in avoid) v -= AVOID
            if (sig in ctx.shownOutfits) v -= SHOWN
            if (core.any { it in signature && DeckPicks.visible(c, it, ctx) }) v += SIGNATURE
            v + rng.nextDouble() * JITTER
        }
    }

    companion object {
        /** Best candidates per day the week chooses from. */
        const val POOL = 80
        /** Cost of sharing all core pieces with a day already in the week (scaled by share). */
        const val SHARED = 0.15
        /** Cost per day that already has the same top (or trouser) colour family. */
        const val COLOR_REPEAT = 0.04
        const val TECHNIQUE_NEW = 0.04
        const val TECHNIQUE_REPEAT = 0.03
        /** Outfits of the plan being replaced: "Replan" means new outfits. */
        const val AVOID = 0.3
        /** Outfits the Today deck showed recently. */
        const val SHOWN = 0.05
        /** A signature piece the week hasn't worn yet. */
        const val SIGNATURE = 0.06
        /** Seeded nudge so equal-ish outfits rotate between replans. */
        const val JITTER = 0.03
    }
}
