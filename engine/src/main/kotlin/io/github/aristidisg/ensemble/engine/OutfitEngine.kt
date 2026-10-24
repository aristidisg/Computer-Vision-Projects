package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.generator.DeckBuilder
import io.github.aristidisg.ensemble.engine.generator.Generator
import io.github.aristidisg.ensemble.engine.generator.GeneratorConfig
import io.github.aristidisg.ensemble.engine.generator.SuggestionDeck
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.rules.Violation
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot

/** A ranked alternative for one slot. */
data class ScoredItem(val item: Item, val outfit: ScoredOutfit)

/** The engine's public API: every mode runs on the same rules and generator. */
class OutfitEngine(private val cfg: GeneratorConfig = GeneratorConfig()) {

    /** Today's deck, build-around (locked pieces) and regenerate. */
    fun generate(ctx: EngineContext, wardrobe: List<Item>, req: SuggestionRequest = SuggestionRequest(deckSize = ctx.settings.deckSize)): SuggestionDeck =
        DeckBuilder.generate(ctx.withItems(wardrobe), wardrobe, req, cfg)

    /** "Rate my outfit": score anything the user assembles, reporting a broken hard constraint instead of hiding it. */
    fun score(pieces: List<OutfitPiece>, ctx: EngineContext): ScoredOutfit? {
        val look = Look.of(pieces, ctx) ?: return null
        val violation = HardConstraints.outfitViolation(look, ctx, ignoreCooldown = true)
            ?: look.placed.firstNotNullOfOrNull { p -> HardConstraints.itemViolation(p.item, p.slot, ctx)?.takeIf { it != Violation.UNAVAILABLE && it != Violation.HIDDEN } }
        return Scorer.score(look, ctx).copy(violation = violation)
    }

    /** "Complete my outfit": the best [n] completions that keep every given piece. */
    fun complete(partial: List<OutfitPiece>, ctx: EngineContext, wardrobe: List<Item>, n: Int = 5): List<ScoredOutfit> =
        generate(ctx, wardrobe, SuggestionRequest(deckSize = n, locked = partial)).cards.take(n)

    /** "Swap one piece": ranked alternatives for [slot], everything else unchanged. */
    fun alternatives(pieces: List<OutfitPiece>, slot: Slot, ctx: EngineContext, wardrobe: List<Item>, n: Int = 8): List<ScoredItem> {
        val c = ctx.withItems(wardrobe)
        val current = pieces.firstOrNull { it.slot == slot }?.itemId
        val rest = pieces.filter { it.slot != slot }
        return wardrobe.asSequence()
            .filter { it.id != current && HardConstraints.itemViolation(it, slot, c) == null }
            .mapNotNull { item ->
                val trial = rest + OutfitPiece(item.id, slot)
                val look = Look.of(trial, c) ?: return@mapNotNull null
                if (HardConstraints.outfitViolation(look, c, ignoreCooldown = true) != null) return@mapNotNull null
                ScoredItem(item, Scorer.score(look, c))
            }
            .sortedWith(compareByDescending<ScoredItem> { it.outfit.total }.thenBy { it.item.id })
            .take(n).toList()
    }

    /** Raw scored candidates (no accessories or diversity), used by planners and insights. */
    fun candidates(ctx: EngineContext, wardrobe: List<Item>, locked: List<OutfitPiece> = emptyList(), config: GeneratorConfig = cfg): List<ScoredOutfit> {
        val c = ctx.withItems(wardrobe)
        return Generator(c, config, DeckBuilder.seed(c, SuggestionRequest(locked = locked))).candidates(wardrobe, locked)
    }
}

/** Ensures the context can resolve every wardrobe item by ID. */
fun EngineContext.withItems(wardrobe: List<Item>): EngineContext =
    if (items.size == wardrobe.size && wardrobe.all { items.containsKey(it.id) }) this else copy(items = wardrobe.associateBy { it.id })
