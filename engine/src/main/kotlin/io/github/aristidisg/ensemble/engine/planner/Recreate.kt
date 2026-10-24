package io.github.aristidisg.ensemble.engine.planner

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Placed
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.withItems
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.LookPiece

data class PieceMatch(val piece: LookPiece, val item: Item?, val similarity: Double) {
    /** Pieces under 0.5 are "missing" and link to the gap finder. */
    val missing: Boolean get() = item == null || similarity < 0.5
}

data class LookMatch(val pieces: List<PieceMatch>, val outfit: ScoredOutfit?, val overall: Double)

/** Recreate an inspiration look from your wardrobe (SPEC 8.7). */
object Recreate {

    /** 0.45 subcategory + 0.35 color + 0.10 pattern + 0.10 silhouette. */
    fun similarity(p: LookPiece, item: Item): Double {
        val sub = when {
            p.subcategory == item.subcategory -> 1.0
            p.subcategory.group == item.subcategory.group &&
                TaxonomyTable.of(p.subcategory).slots.first() == item.slots.first() -> 0.6
            TaxonomyTable.of(p.subcategory).slots.first() == item.slots.first() -> 0.3
            p.subcategory.group == item.subcategory.group -> 0.3
            else -> 0.0
        }
        val color = item.colors.maxOfOrNull { c ->
            val de = ColorScience.deltaE(ColorScience.hexToLab(p.hex), ColorScience.hexToLab(c.hex))
            ((1 - de / 0.25).coerceIn(0.0, 1.0) + if (c.family == p.family) 0.2 else 0.0).coerceAtMost(1.0) * (0.5 + 0.5 * c.share)
        } ?: 0.0
        val pattern = if (p.pattern == item.pattern.type) 1.0 else 0.0
        val sil = if (p.silhouette == item.silhouette) 1.0 else 0.5
        return 0.45 * sub + 0.35 * color + 0.10 * pattern + 0.10 * sil
    }

    fun recreate(look: InspirationLook, wardrobe: List<Item>, ctx0: EngineContext): LookMatch {
        val ctx = ctx0.withItems(wardrobe)
        val avail = wardrobe.filter { it.isAvailable }
        val options = look.pieces.map { p ->
            p to avail.filter { it.slots.first() == TaxonomyTable.of(p.subcategory).slots.first() }
                .map { it to similarity(p, it) }.sortedByDescending { it.second }.take(3)
        }
        // Beam over the top 3 matches per piece maximizing 0.6 × similarity + 0.4 × outfit score.
        var beam: List<List<Pair<LookPiece, Pair<Item, Double>?>>> = listOf(emptyList())
        for ((p, opts) in options) {
            val choices: List<Pair<Item, Double>?> = opts.ifEmpty { listOf(null) }
            beam = beam.flatMap { partial -> choices.filter { c -> c == null || partial.none { it.second?.first?.id == c.first.id } }.map { partial + (p to it) } }
                .sortedByDescending { b -> b.sumOf { it.second?.second ?: 0.0 } }.take(27)
        }
        var best: LookMatch? = null
        for (cand in beam) {
            val placed = cand.mapNotNull { it.second?.first }.distinctBy { it.id }.map { Placed(it, it.slots.first()) }
                .distinctBy { it.slot.takeIf { s -> s.isCore } ?: it.item.id }
            val outfit = if (placed.isNotEmpty()) Scorer.score(Look(placed, ctx), ctx) else null
            val sim = cand.map { it.second?.second ?: 0.0 }.average()
            val total = 0.6 * sim + 0.4 * (outfit?.total ?: 0.0)
            if (best == null || total > best.overall) {
                best = LookMatch(cand.map { (p, m) -> PieceMatch(p, m?.first, m?.second ?: 0.0) }, outfit, total)
            }
        }
        return best ?: LookMatch(look.pieces.map { PieceMatch(it, null, 0.0) }, null, 0.0)
    }
}
