package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.generator.DeckPicks
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A piece you build around (or lock) stays visible: no white tee hidden under a hoodie. */
class LockedVisibilityTest {
    private val engine = OutfitEngine()

    private fun deck(min: Double, vararg locked: OutfitPiece) = Fixtures.ctx("casual", Fixtures.weather(min, min + 4)).let { c ->
        c to engine.generate(c, Fixtures.wardrobe, SuggestionRequest(deckSize = 8, locked = locked.toList())).cards
    }

    @Test fun `a locked tee shows in every card on a layering day`() {
        val (c, cards) = deck(11.0, OutfitPiece("tee-white", Slot.TOP))
        assertTrue(cards.isNotEmpty())
        for (card in cards) {
            assertTrue("tee-white" in card.itemIds)
            assertTrue(Look.of(card.pieces, c)!!.isVisible(Slot.TOP), "tee hidden in ${card.itemIds}")
        }
    }

    @Test fun `building around a tee completes it with open layers only`() {
        val c = Fixtures.ctx("casual", Fixtures.weather(11.0, 15.0))
        val outs = engine.complete(listOf(OutfitPiece("tee-white", Slot.TOP)), c, Fixtures.wardrobe, 4)
        assertTrue(outs.isNotEmpty())
        val mids = outs.mapNotNull { o -> o.pieces.firstOrNull { it.slot == Slot.MID }?.itemId }
        assertTrue(mids.none { it == "hoodie-grey" || it.startsWith("merino") || it.startsWith("sweater") }, "pullover over the tee: $mids")
    }

    @Test fun `on a freezing day the locked tee is still in every card`() {
        val (_, cards) = deck(-2.0, OutfitPiece("tee-white", Slot.TOP))
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { "tee-white" in it.itemIds })
    }

    @Test fun `a locked mid layer stays even when no coat can be worn open`() {
        val (_, cards) = deck(3.0, OutfitPiece("chunky-knit", Slot.MID))
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { "chunky-knit" in it.itemIds }, cards.map { it.itemIds }.toString())
    }

    @Test fun `visible is false under a pullover and true under an open layer`() {
        val c = Fixtures.ctx("casual", Fixtures.weather(14.0, 18.0))
        val hidden = Scorer.score(Fixtures.look(c, "tee-white@TOP", "hoodie-grey@MID", "jeans-dark", "white-sneakers"), c)
        val open = Scorer.score(Fixtures.look(c, "tee-white@TOP", "overshirt-olive@MID", "jeans-dark", "white-sneakers"), c)
        assertFalse(DeckPicks.visible(hidden, "tee-white", c))
        assertTrue(DeckPicks.visible(open, "tee-white", c))
    }
}
