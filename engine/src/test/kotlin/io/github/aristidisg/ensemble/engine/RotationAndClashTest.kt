package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.generator.FairShare
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
        // Shown on 6 of the last 36 cards each: several times their fair share.
        val seen = base.copy(recentDeckItems = shown.associateWith { 6 }, recentDeckCount = 36)
        val second = engine.generate(seen, Fixtures.wardrobe).cards
        val firstSigs = first.map { it.signature }.toSet()
        assertTrue(first.take(3).none { c -> second.any { it.signature == c.signature } }, "yesterday's top cards came back")
        assertTrue(second.count { it.signature !in firstSigs } >= second.size / 2, "the deck barely changed")
        val newPieces = coreIds(second).toSet() - coreIds(first).toSet()
        assertTrue(newPieces.isNotEmpty(), "no piece missing from the first deck got a turn")
        // Tops and bottoms (the pieces with real alternatives) move on from what was shown.
        fun shownTopsBottoms(cards: List<io.github.aristidisg.ensemble.engine.rules.ScoredOutfit>) =
            cards.flatMap { c -> c.pieces.filter { it.slot == Slot.TOP || it.slot == Slot.BOTTOM } }.count { it.itemId in shown }
        assertTrue(shownTopsBottoms(second) < shownTopsBottoms(first), "shown tops/bottoms: ${shownTopsBottoms(first)} before, ${shownTopsBottoms(second)} after")
    }

    @Test fun `fair share penalizes reuse and overuse and rewards unused pieces`() {
        val cands = io.github.aristidisg.ensemble.engine.generator.Generator(base.withItems(Fixtures.wardrobe)).candidates(Fixtures.wardrobe)
        val card = cands.first()
        val ids = card.pieces.filter { it.slot.isCore }.map { it.itemId }
        val fresh = FairShare(cands, base, 12)
        assertEquals(0.0, fresh.penalty(card, emptyMap()))
        assertEquals(2 * FairShare.REUSE_PENALTY, fresh.penalty(card, mapOf(ids[0] to 2)), 1e-9)
        // Every piece of this card was on half of the last 40 cards: far past its share.
        val over = FairShare(cands, base.copy(recentDeckItems = ids.associateWith { 20 }, recentDeckCount = 40), 12)
        assertTrue(over.penalty(card, emptyMap()) > 0.2)
        // Pieces never shown while others were get a bonus (negative penalty).
        val under = FairShare(cands, base.copy(recentDeckItems = mapOf("someone-else" to 40), recentDeckCount = 40), 12)
        assertTrue(under.penalty(card, emptyMap()) < 0.0)
    }

    @Test fun `no piece exceeds its in-deck cap when alternatives exist`() {
        val cands = io.github.aristidisg.ensemble.engine.generator.Generator(base.withItems(Fixtures.wardrobe)).candidates(Fixtures.wardrobe)
        val fair = FairShare(cands, base, 12)
        val deck = engine.generate(base, Fixtures.wardrobe).cards.filter { !it.newIdea }
        val uses = deck.flatMap { c -> c.pieces.filter { it.slot.isCore } }.groupBy { it.itemId }
        // A piece worn in two slots (a sweater as top or mid layer) is held to the larger cap.
        for ((id, ps) in uses) {
            val cap = ps.map { it.slot }.distinct().maxOf { fair.cap(it) }
            assertTrue(ps.size <= cap, "$id used ${ps.size} times, cap $cap")
        }
    }

    @Test fun `two weeks of decks spread across the whole wardrobe`() {
        val days = ArrayList<List<List<String>>>()
        val total = HashMap<String, Int>()
        for (d in 0 until 14) {
            val prev = days.takeLast(5)
            val ctx = Fixtures.ctx("casual", Fixtures.weather(13.0, 18.0), day = Fixtures.TODAY + d)
                .copy(recentDeckItems = prev.flatMap { it.flatten() }.groupingBy { it }.eachCount(), recentDeckCount = prev.sumOf { it.size })
            val cards = engine.generate(ctx, Fixtures.wardrobe).cards.map { c -> c.pieces.filter { it.slot.isCore }.map { it.itemId } }
            days += cards
            cards.flatten().forEach { total[it] = (total[it] ?: 0) + 1 }
        }
        val c = Fixtures.ctx("casual", Fixtures.weather(13.0, 18.0))
        val eligible = Fixtures.wardrobe.filter { i -> i.slots.any { s -> s.isCore && HardConstraints.itemViolation(i, s, c) == null } }
        assertTrue(eligible.all { it.id in total }, "never shown: ${eligible.filter { it.id !in total }.map { it.id }}")
        // Per slot, every piece lands within a third and twice its fair share.
        for (slot in listOf(Slot.TOP, Slot.BOTTOM, Slot.SHOES)) {
            val counts = eligible.filter { it.slots.first() == slot }.map { total[it.id] ?: 0 }
            val fair = counts.sum().toDouble() / counts.size
            assertTrue(counts.min() >= fair / 3 && counts.max() <= fair * 2, "$slot spread ${counts.sorted()} (fair ${"%.1f".format(fair)})")
        }
    }
}

class WhyNotSuggestedTest {
    @Test fun `explains blocked pieces and stays quiet for usable ones`() {
        val c = ctx("casual")
        kotlin.test.assertNull(HardConstraints.whyNotSuggested(item("tee-white"), c))
        assertEquals(io.github.aristidisg.ensemble.engine.rules.Violation.UNAVAILABLE,
            HardConstraints.whyNotSuggested(item("tee-white").copy(status = io.github.aristidisg.ensemble.model.ItemStatus.LAUNDRY), c))
        assertEquals(io.github.aristidisg.ensemble.engine.rules.Violation.NEVER_SUGGEST,
            HardConstraints.whyNotSuggested(item("tee-white").copy(flags = io.github.aristidisg.ensemble.model.ItemFlags(neverSuggest = true)), c))
        assertEquals(io.github.aristidisg.ensemble.engine.rules.Violation.WEATHER,
            HardConstraints.whyNotSuggested(item("chino-shorts"), ctx("casual", Fixtures.weather(5.0, 9.0))))
    }
}
