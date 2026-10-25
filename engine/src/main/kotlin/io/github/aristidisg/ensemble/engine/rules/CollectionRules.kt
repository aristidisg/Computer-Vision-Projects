package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext

/** One saved outfit reduced to what similarity compares: its core pieces and color pairs. */
data class CollectionProfile(val signature: String, val pieces: Set<String>, val familyPairs: Set<String>)

/**
 * "My collection": outfits you saved or composed pull similar suggestions up. Similarity mixes
 * shared color-family pairs (the look) with shared pieces (the clothes). The saved outfit itself
 * gets no boost, so the deck finds new outfits in the same spirit instead of repeating them.
 */
object CollectionRules {
    /** Raw score added at similarity 1; about 0.2 on the Match % scale after calibration. */
    const val MAX_BONUS = 0.08
    const val PAIR_SHARE = 0.6

    fun familyPairs(look: Look): Set<String> = ColorPairRules.adjacentPairs(look).mapNotNull { (a, b) ->
        val fa = a.primaryColor?.family ?: return@mapNotNull null
        val fb = b.primaryColor?.family ?: return@mapNotNull null
        listOf(fa.name, fb.name).sorted().joinToString("|")
    }.toSet()

    fun profiles(ctx: EngineContext): List<CollectionProfile> = ctx.collection.mapNotNull { pieces ->
        val look = Look.of(pieces, ctx) ?: return@mapNotNull null
        CollectionProfile(look.signature, look.core.map { it.item.id }.toSet(), familyPairs(look))
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() && b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

    /** 0..1 similarity to the closest saved outfit other than this exact one. */
    fun similarity(look: Look, ctx: EngineContext): Double {
        val profiles = ctx.collectionProfiles
        if (profiles.isEmpty()) return 0.0
        val pieces = look.core.map { it.item.id }.toSet()
        val pairs = familyPairs(look)
        return profiles.filter { it.signature != look.signature }.maxOfOrNull { p ->
            PAIR_SHARE * jaccard(pairs, p.familyPairs) + (1 - PAIR_SHARE) * jaccard(pieces, p.pieces)
        } ?: 0.0
    }

    /** The bonus and its explanation line, or null when the influence is off or nothing is close. */
    fun influence(look: Look, ctx: EngineContext): Pair<Double, RuleResult>? {
        if (!ctx.settings.savedOutfitsInfluence || ctx.collection.isEmpty()) return null
        val sim = similarity(look, ctx)
        if (sim <= 0.0) return null
        val line = res("SAVED", RuleGroup.LEARNED, sim, 0.0, if (sim >= 0.5) Explain.LIKE_SAVED else null)
        return MAX_BONUS * sim to line
    }
}
