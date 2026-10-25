package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.generator.DeckBuilder
import io.github.aristidisg.ensemble.engine.generator.Generator
import io.github.aristidisg.ensemble.engine.rules.ColorPairRules
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClashTest {
    private fun colored(id: String, newId: String, hex: String) = item(id).copy(id = newId, colors = listOf(KMeans.itemColor(hex)))
    private val roseShirt = colored("oxford-white", "rose-shirt", "#C9848C")
    private val oliveChinos = colored("chinos-stone", "olive-chinos", "#6B6B3A")
    private val c = ctx("casual", items = Fixtures.wardrobe + roseShirt + oliveChinos)

    @Test fun `rose shades are pink families`() {
        for (hex in listOf("#C9848C", "#D8A7A0", "#E8B4B8", "#C08081")) {
            val f = ColorScience.nearestFamily(ColorScience.hexToLab(hex))
            assertTrue(f == ColorFamily.DUSTY_PINK || f == ColorFamily.PINK, "$hex -> $f")
        }
    }

    @Test fun `rose with olive is a heavy C7 clash`() {
        val r = ColorPairRules.c7ClassicPairs(look(c, "rose-shirt@TOP", "olive-chinos@BOTTOM", "white-sneakers"), c)!!
        assertEquals(Explain.UNUSUAL_PAIR, r.key)
        assertTrue(r.score <= 0.2 && r.weight >= 4.0)
    }

    @Test fun `rose with navy or stone is not a clash`() {
        val navy = ColorPairRules.c7ClassicPairs(look(c, "rose-shirt@TOP", "chinos-navy", "white-sneakers"), c)
        assertTrue(navy == null || navy.weight < 4.0)
        val olive = ColorPairRules.c7ClassicPairs(look(c, "oxford-white", "olive-chinos@BOTTOM", "white-sneakers"), c)!!
        assertTrue(olive.weight < 4.0)
    }

    @Test fun `the clash costs the outfit its match`() {
        val clash = Scorer.score(look(c, "rose-shirt@TOP", "olive-chinos@BOTTOM", "white-sneakers"), c).total
        val fine = Scorer.score(look(c, "rose-shirt@TOP", "chinos-navy", "white-sneakers"), c).total
        assertTrue(clash < fine - 0.1, "clash $clash vs fine $fine")
    }
}

class RotationTest {
    private val engine = OutfitEngine()
    private val base = ctx("casual", Fixtures.weather(15.0, 19.0))

    private fun coreIds(cards: List<io.github.aristidisg.ensemble.engine.rules.ScoredOutfit>) =
        cards.flatMap { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId } }

    @Test fun `every eligible top and bottom reaches the candidates`() {
        val gen = Generator(base.withItems(Fixtures.wardrobe))
        val cands = gen.candidates(Fixtures.wardrobe)
        val used = coreIds(cands).toSet()
        for (slot in listOf(Slot.TOP, Slot.BOTTOM)) {
            val eligible = Fixtures.wardrobe.filter { it.slots.first() == slot && HardConstraints.itemViolation(it, slot, base) == null }
            val missing = eligible.filter { it.id !in used }.map { it.id }
            assertTrue(missing.isEmpty(), "$slot never suggested: $missing")
        }
    }

    @Test fun `one deck spreads across pieces`() {
        val deck = engine.generate(base, Fixtures.wardrobe).cards
        val counts = coreIds(deck).groupingBy { it }.eachCount()
        assertTrue(counts.values.max() <= deck.size / 2, "a piece is in ${counts.values.max()} of ${deck.size} cards: $counts")
    }

    @Test fun `pieces shown in recent decks give way to others`() {
        val first = engine.generate(base, Fixtures.wardrobe).cards
        // The top three cards' pieces were in every one of the last three decks.
        val shown = coreIds(first.take(3)).toSet()
        val seen = base.copy(recentDeckItems = shown.associateWith { 3 }, recentDeckCount = 3)
        val second = engine.generate(seen, Fixtures.wardrobe).cards
        val firstSigs = first.map { it.signature }.toSet()
        assertTrue(first.take(3).none { c -> second.any { it.signature == c.signature } }, "yesterday's top cards came back")
        assertTrue(second.count { it.signature !in firstSigs } >= second.size / 2, "the deck barely changed")
        val newPieces = coreIds(second).toSet() - coreIds(first).toSet()
        assertTrue(newPieces.isNotEmpty(), "no piece missing from the first deck got a turn")
        val topsBefore = first.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }.count { it in shown }
        val topsAfter = second.mapNotNull { c -> c.pieces.firstOrNull { it.slot == Slot.TOP }?.itemId }.count { it in shown }
        assertTrue(topsAfter < topsBefore, "shown tops: $topsBefore before, $topsAfter after")
    }

    @Test fun `rotation penalty grows with reuse and exposure, zero for fresh pieces`() {
        val card = engine.generate(base, Fixtures.wardrobe).cards.first()
        assertEquals(0.0, DeckBuilder.rotationPenalty(card, emptyMap(), base))
        val ids = card.pieces.filter { it.slot.isCore }.map { it.itemId }
        val reused = DeckBuilder.rotationPenalty(card, mapOf(ids[0] to 2), base)
        assertEquals(2 * DeckBuilder.REUSE_PENALTY, reused, 1e-9)
        val exposed = base.copy(recentDeckItems = ids.associateWith { 5 }, recentDeckCount = 5)
        assertEquals(DeckBuilder.EXPOSURE_PENALTY, DeckBuilder.rotationPenalty(card, emptyMap(), exposed), 1e-9)
    }
}
