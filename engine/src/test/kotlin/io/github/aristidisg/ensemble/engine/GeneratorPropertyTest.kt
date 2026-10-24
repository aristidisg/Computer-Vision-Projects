package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.tables.ScenarioPresets
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.WindowWeather
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Generator invariants checked over many seeded random contexts and wardrobes. */
class GeneratorPropertyTest {
    private val engine = OutfitEngine()

    private fun randomCase(seed: Long): Triple<EngineContext, List<io.github.aristidisg.ensemble.model.Item>, SuggestionRequest> {
        val rng = SeededRandom(seed)
        val wardrobe = if (rng.nextInt(2) == 0) Fixtures.wardrobe else Synthetic.wardrobe(60 + rng.nextInt(80), seed)
        val sc = ScenarioPresets.all[rng.nextInt(ScenarioPresets.all.size)]
        val min = -5.0 + rng.nextDouble() * 35
        val w = WindowWeather(min, min + rng.nextDouble() * 10, rainProbMax = rng.nextDouble() * 100, windMax = rng.nextDouble() * 45, uvMax = rng.nextDouble() * 9)
        val ctx = Fixtures.ctx(sc.id, w, day = TODAY + rng.nextInt(365), items = wardrobe)
        val lockable = wardrobe.filter { it.isAvailable && it.slots.first().isCore && HardConstraints.itemViolation(it, it.slots.first(), ctx) == null }
        val locked = if (lockable.isNotEmpty() && rng.nextInt(3) == 0) lockable[rng.nextInt(lockable.size)].let { listOf(OutfitPiece(it.id, it.slots.first())) } else emptyList()
        return Triple(ctx, wardrobe, SuggestionRequest(deckSize = 8, locked = locked, regenerateCount = rng.nextInt(3)))
    }

    @Test fun `never breaks a hard constraint, keeps locked pieces, deterministic per seed`() {
        var nonEmpty = 0
        for (seed in 1L..25L) {
            val (ctx, wardrobe, req) = randomCase(seed)
            val deck = engine.generate(ctx, wardrobe, req)
            if (deck.cards.isNotEmpty()) nonEmpty++
            val c = ctx.withItems(wardrobe)
            for (card in deck.cards) {
                val look = Look.of(card.pieces, c)!!
                assertNull(HardConstraints.outfitViolation(look, c), "seed $seed outfit ${card.itemIds}")
                for (p in look.placed) {
                    if (req.locked.any { it.itemId == p.item.id }) continue
                    assertNull(HardConstraints.itemViolation(p.item, p.slot, c), "seed $seed item ${p.item.id} in ${p.slot}")
                }
                for (l in req.locked) assertTrue(l.itemId in card.itemIds, "seed $seed locked ${l.itemId}")
            }
            assertEquals(deck.cards.map { it.signature }, engine.generate(ctx, wardrobe, req).cards.map { it.signature }, "seed $seed")
            assertEquals(deck.cards.size, deck.cards.map { it.signature }.toSet().size, "no duplicate cards")
        }
        assertTrue(nonEmpty >= 10, "most random cases produce a deck ($nonEmpty)")
    }

    @Test fun `deck is varied`() {
        val deck = engine.generate(Fixtures.ctx("casual", Fixtures.weather(14.0, 18.0)), Fixtures.wardrobe)
        assertEquals(12, deck.cards.size)
        val tops = deck.cards.mapNotNull { c -> c.pieces.firstOrNull { it.slot == io.github.aristidisg.ensemble.model.Slot.TOP }?.itemId }.toSet()
        assertTrue(tops.size >= 4, "tops $tops")
        assertTrue(deck.cards.any { it.newIdea })
        assertTrue(deck.cards.all { it.why.isNotEmpty() })
    }

    @Test fun `regenerate changes the deck`() {
        val a = engine.generate(Fixtures.ctx("casual"), Fixtures.wardrobe, SuggestionRequest(regenerateCount = 0))
        val b = engine.generate(Fixtures.ctx("casual"), Fixtures.wardrobe, SuggestionRequest(regenerateCount = 1))
        assertTrue(a.cards.map { it.signature } != b.cards.map { it.signature })
    }

    @Test fun `alternatives and complete`() {
        val c = Fixtures.ctx("casual")
        val alts = engine.alternatives(Fixtures.pieces("tee-white", "jeans-dark", "white-sneakers"), io.github.aristidisg.ensemble.model.Slot.SHOES, c, Fixtures.wardrobe)
        assertTrue(alts.isNotEmpty() && alts.none { it.item.id == "white-sneakers" })
        val done = engine.complete(Fixtures.pieces("chinos-navy"), c, Fixtures.wardrobe, 3)
        assertTrue(done.isNotEmpty() && done.all { "chinos-navy" in it.itemIds })
        val rated = engine.score(Fixtures.pieces("tee-white", "chino-shorts", "white-sneakers"), Fixtures.ctx("casual", Fixtures.weather(2.0, 6.0)))!!
        assertTrue(rated.violation != null, "rate-my-outfit reports the broken constraint")
    }
}
