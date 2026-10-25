package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.generator.DeckPicks
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.rules.EditorialRules
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.rules.Tip
import io.github.aristidisg.ensemble.model.ItemFlags
import io.github.aristidisg.ensemble.model.Mood
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Styling tips, moods, the accent colour, Vogue stylist mode, signature cards and fresh pairs. */
class EditorialTest {
    private val engine = OutfitEngine()
    private val mild = Fixtures.ctx("casual", Fixtures.weather(16.0, 20.0))
    private fun tips(c: EngineContext, vararg ids: String) = Fixtures.score(c, *ids).tips

    @Test fun `a tee with chinos gets a french tuck, with joggers it doesn't`() {
        assertTrue(Tip.FRENCH_TUCK in tips(mild, "tee-white", "chinos-stone", "white-sneakers"))
        assertFalse(Tip.FRENCH_TUCK in tips(mild, "tee-white", "joggers-grey", "running-shoes"))
    }

    @Test fun `jeans over boots get a cuff, over running shoes they don't`() {
        assertTrue(Tip.CUFF_OVER_BOOTS in tips(mild, "tee-black", "jeans-dark", "suede-chelsea"))
        val run = tips(mild, "tee-black", "jeans-dark", "running-shoes")
        assertFalse(Tip.CUFF_OVER_BOOTS in run || Tip.CUFF_ANKLE in run)
    }

    @Test fun `sleeves roll on a warm day, not a cold one`() {
        val warm = Fixtures.ctx("casual", Fixtures.weather(24.0, 28.0))
        val cold = Fixtures.ctx("casual", Fixtures.weather(6.0, 9.0))
        assertTrue(Tip.ROLL_SLEEVES in tips(warm, "linen-shirt", "jeans-dark", "white-sneakers"))
        assertFalse(Tip.ROLL_SLEEVES in tips(cold, "linen-shirt", "jeans-dark", "white-sneakers"))
    }

    @Test fun `collar out over a knit, and at most two tips`() {
        val t = tips(mild, "oxford-white@TOP", "merino-navy@MID", "chinos-stone", "brown-loafers")
        assertTrue(Tip.COLLAR_OUT in t)
        assertTrue(t.size <= 2)
    }

    @Test fun `italian summer favours linen over a hoodie`() {
        val c = mild.copy(settings = Settings(mood = Mood.ITALIAN_SUMMER))
        val linen = Fixtures.look(c, "linen-shirt", "linen-trousers", "brown-loafers")
        val hoodie = Fixtures.look(c, "tee-black@TOP", "hoodie-grey@MID", "joggers-grey", "running-shoes")
        assertTrue(EditorialRules.moodFit(linen, Mood.ITALIAN_SUMMER) >= EditorialRules.MOOD_LINE)
        assertTrue(EditorialRules.moodFit(hoodie, Mood.ITALIAN_SUMMER) < EditorialRules.MOOD_PIVOT)
        val on = Scorer.score(linen, c)
        assertTrue(on.results.any { it.id == "MOOD" })
        assertTrue(on.total > Fixtures.score(mild, "linen-shirt", "linen-trousers", "brown-loafers").total)
        assertFalse(Fixtures.score(mild, "linen-shirt", "linen-trousers", "brown-loafers").results.any { it.id == "MOOD" })
    }

    @Test fun `an accent colour is built around, and missing it costs`() {
        val rust = Fixtures.item("sweater-rust").primaryColor!!.family
        val c = mild.copy(options = DeckOptions(accent = rust))
        val with = Scorer.score(Fixtures.look(c, "sweater-rust", "jeans-dark", "white-sneakers"), c)
        val without = Scorer.score(Fixtures.look(c, "tee-navy", "jeans-dark", "white-sneakers"), c)
        assertTrue(with.why.any { it.id == "ACCENT" })
        assertTrue(without.why.none { it.id == "ACCENT" })
        assertTrue(without.total < Fixtures.score(mild, "tee-navy", "jeans-dark", "white-sneakers").total)
        val cards = engine.generate(c, Fixtures.wardrobe, SuggestionRequest(deckSize = 6)).cards
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { card -> EditorialRules.accentShare(Fixtures.look(c, *card.pieces.map { "${it.itemId}@${it.slot}" }.toTypedArray()), rust) >= EditorialRules.ACCENT_MIN })
    }

    @Test fun `vogue stylist deals cover looks with a technique and finished accessories`() {
        val c = mild.copy(options = DeckOptions(vogue = true))
        val cards = engine.generate(c, Fixtures.wardrobe, SuggestionRequest(deckSize = 6)).cards
        assertEquals(6, cards.size)
        assertTrue(cards.all { it.technique != null })
        assertTrue(cards.all { card -> card.pieces.count { !it.slot.isCore } >= 2 })
        assertTrue(cards.none { it.newIdea })
        val plain = Fixtures.score(mild, "tee-navy", "wool-trousers-grey", "black-derbies")
        assertTrue(Scorer.score(Fixtures.look(c, "tee-navy", "wool-trousers-grey", "black-derbies"), c).total < plain.total)
    }

    @Test fun `a signature piece gets its own card, but not in a build-around deck`() {
        val items = Fixtures.wardrobe.map { if (it.id == "chinos-green") it.copy(flags = ItemFlags(signature = true)) else it }
        val c = Fixtures.ctx("casual", Fixtures.weather(16.0, 20.0), items = items)
        val cards = engine.generate(c, items, SuggestionRequest(deckSize = 8)).cards
        val sig = cards.filter { it.why.firstOrNull()?.id == "SIGN" }
        assertEquals(1, sig.size)
        assertTrue("chinos-green" in sig.first().itemIds)
        val locked = engine.generate(c, items, SuggestionRequest(deckSize = 8, locked = listOf(OutfitPiece("tee-white", Slot.TOP)))).cards
        assertTrue(locked.none { it.why.firstOrNull()?.id == "SIGN" })
        assertTrue(engine.generate(mild, Fixtures.wardrobe, SuggestionRequest(deckSize = 8)).cards.none { it.why.firstOrNull()?.id == "SIGN" })
    }

    @Test fun `fresh pairs mode skips pieces already shown together`() {
        val first = engine.generate(mild, Fixtures.wardrobe, SuggestionRequest(deckSize = 8)).cards
        val pairs = first.flatMap { card ->
            val ids = DeckPicks.pairIds(card.pieces)
            ids.flatMap { a -> ids.filter { it != a }.map { b -> EngineContext.pairKey(a, b) } }
        }.toSet()
        val fresh = mild.copy(shownPairs = pairs, settings = Settings(freshPairsOnly = true))
        val cards = engine.generate(fresh, Fixtures.wardrobe, SuggestionRequest(deckSize = 8)).cards
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { DeckPicks.seenPairs(it, fresh) == 0 }, cards.map { it.itemIds }.toString())
        assertTrue(cards.all { it.why.first().id == "FRESH" })
        val normal = engine.generate(mild.copy(shownPairs = pairs), Fixtures.wardrobe, SuggestionRequest(deckSize = 8)).cards
        assertTrue(normal.any { DeckPicks.seenPairs(it, fresh) > 0 })
    }
}
