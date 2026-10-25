package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.pieces
import io.github.aristidisg.ensemble.engine.Fixtures.score
import io.github.aristidisg.ensemble.engine.Fixtures.wardrobe
import io.github.aristidisg.ensemble.engine.Fixtures.weather
import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.tables.TrouserKind
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.ContextSnapshot
import io.github.aristidisg.ensemble.model.FeedbackEvent
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.WaterResistance
import io.github.aristidisg.ensemble.model.WhyNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The 18 golden cases: proof the engine behaves like a good stylist. */
class GoldenCasesTest {
    private val engine = OutfitEngine()
    private val office = arrayOf("blazer-navy@OUTER", "oxford-white", "wool-trousers-grey", "brown-loafers", "belt-brown")

    @Test fun `01 office classic scores high with neutral harmony and matching leather`() {
        val s = score(ctx("office", weather(15.0, 17.0)), *office)
        assertTrue(s.matchPct >= 85, "match ${s.matchPct}: ${s.results.joinToString { "${it.id}=${"%.2f".format(it.score)}" }}")
        val keys = s.why.map { it.key }
        assertTrue(Explain.HARMONY_NEUTRAL in keys || Explain.NEUTRAL_ACCENT in keys, "why $keys")
        assertTrue(Explain.LEATHER_MATCH in keys, "why $keys")
    }

    @Test fun `02 black derbies with a brown belt fire the leather rule and rank lower`() {
        val c = ctx("office", weather(15.0, 17.0))
        val good = score(c, *office)
        val bad = score(c, "blazer-navy@OUTER", "oxford-white", "wool-trousers-grey", "black-derbies", "belt-brown")
        assertTrue(bad.results.any { it.key == Explain.LEATHER_CLASH })
        assertTrue(bad.total < good.total)
    }

    @Test fun `03 no shorts when it feels 8 degrees or less`() {
        for (sc in listOf("casual", "university", "brunch", "beach", "concert")) {
            val deck = engine.generate(ctx(sc, weather(3.0, 8.0)), wardrobe)
            val shorts = deck.cards.flatMap { it.itemIds }.map { item(it) }.filter { TaxonomyTable.of(it.subcategory).trouserKind == TrouserKind.SHORTS }
            assertTrue(shorts.isEmpty(), "$sc had shorts")
        }
    }

    @Test fun `04 hot and humid - no wool knit or coat, linen boosted`() {
        val c = ctx("casual", weather(28.0, 31.0).copy(humidityMax = 80.0))
        val deck = engine.generate(c, wardrobe)
        val used = deck.cards.flatMap { it.itemIds }.map { item(it) }
        assertTrue(used.none { it.subcategory == Subcategory.CHUNKY_KNIT || it.subcategory == Subcategory.WOOL_OVERCOAT })
        val linen = score(c, "linen-shirt", "chinos-stone", "white-sneakers")
        val oxford = score(c, "oxford-white", "chinos-stone", "white-sneakers")
        assertTrue(linen.total > oxford.total, "linen ${linen.total} vs oxford ${oxford.total}")
    }

    @Test fun `05 bold check plus floral is a pattern penalty`() {
        val c = ctx("casual", weather(16.0, 19.0))
        val clash = score(c, "overshirt-check@MID", "floral-shirt", "jeans-dark", "white-sneakers")
        val solid = score(c, "overshirt-olive@MID", "tee-white", "jeans-dark", "white-sneakers")
        assertTrue(clash.results.any { it.key == Explain.PATTERN_CLASH })
        assertTrue(clash.total < solid.total)
    }

    @Test fun `06 oversized on oversized - penalty for classic, fine for streetwear`() {
        val ids = arrayOf("hoodie-grey@TOP", "cargo-olive", "white-sneakers")
        val classic = score(ctx("casual", weather(18.0, 22.0)), *ids)
        val sil = classic.results.first { it.id == "SIL" }
        assertTrue(sil.score < 0.5, "classic sil ${sil.score}")
        val street = Settings(styleWeights = Settings.DEFAULT_STYLE_WEIGHTS + (StyleTag.STREETWEAR to 1.0))
        val s2 = score(ctx("casual", weather(18.0, 22.0), settings = street), *ids).results.first { it.id == "SIL" }
        assertTrue(s2.score >= 0.5, "streetwear sil ${s2.score}")
    }

    @Test fun `07 heavy rain - suede boots excluded and a water-resistant jacket chosen`() {
        val rainy = Fixtures.scenario("casual").copy(id = "rainy", indoorShare = 0.4)
        val c = ctx("casual", weather(11.0, 14.0, rain = 80.0, rainSum = 6.0)).copy(scenario = rainy)
        assertNotNull(HardConstraints.itemViolation(item("suede-chelsea"), Slot.SHOES, c))
        val deck = engine.generate(c, wardrobe)
        assertTrue(deck.cards.none { "suede-chelsea" in it.itemIds })
        val top = deck.cards.first()
        val outer = top.pieces.firstOrNull { it.slot == Slot.OUTER }?.let { item(it.itemId) }
        assertNotNull(outer, "top card has an outer layer")
        assertTrue(outer.waterResistance != WaterResistance.NONE, "outer ${outer.name}")
    }

    @Test fun `08 an item in the laundry never appears`() {
        val w = wardrobe.map { if (it.id == "jeans-dark") it.copy(status = ItemStatus.LAUNDRY) else it }
        for (sc in listOf("casual", "university", "date")) {
            val deck = engine.generate(ctx(sc, items = w), w)
            assertTrue(deck.cards.none { "jeans-dark" in it.itemIds })
        }
    }

    @Test fun `09 same seed and data give an identical deck`() {
        val a = engine.generate(ctx("university"), wardrobe, SuggestionRequest(regenerateCount = 2))
        val b = engine.generate(ctx("university"), wardrobe, SuggestionRequest(regenerateCount = 2))
        assertEquals(a.cards.map { it.signature }, b.cards.map { it.signature })
        assertEquals(a.cards.map { it.total }, b.cards.map { it.total })
    }

    @Test fun `10 ten colors-dont-work swipes demote navy plus black, items barely move`() {
        val c = ctx("casual", weather(15.0, 19.0))
        val navyBlack = arrayOf("tee-navy", "jeans-black", "white-sneakers")
        val features = Features(c, *navyBlack)
        val events = (1..10).map { i -> event(i, FeedbackType.SWIPE_NO, features, "casual", listOf(WhyNot.COLORS)) }
        val stats = Learning.rebuild(events)
        val snap = PreferenceSnapshot.of(stats, "casual", Season.AUTUMN, TODAY)
        val famKey = features.first { it.startsWith("fam:") && "NAVY" in it && "BLACK" in it }
        assertTrue(snap.value(famKey)!!.value < -0.5, "fam value ${snap.value(famKey)}")
        assertNull(snap.value("item:tee-navy"), "items untouched by a color reason")
        val before = score(c, *navyBlack)
        val after = score(c.copy(prefs = snap), *navyBlack)
        assertTrue(after.total < before.total)
    }

    @Test fun `11 likes in night out leave the office formality offset unchanged`() {
        val c = ctx("night_out", weather(12.0, 14.0))
        val f = Features(c, "tee-black", "jeans-black", "white-sneakers")
        val stats = Learning.rebuild((1..5).map { event(it, FeedbackType.SWIPE_YES, f, "night_out") })
        val office = PreferenceSnapshot.of(stats, "office", Season.AUTUMN, TODAY)
        assertEquals(0.0, office.formalityOffset["office"] ?: 0.0)
        assertTrue(f.filter { it.startsWith("formality:night_out") }.all { k -> office.value(k)?.let { true } ?: true })
        assertNull(office.allValues().keys.firstOrNull { it.startsWith("formality:office") })
    }

    @Test fun `12 feedback 240 days old counts a quarter`() {
        assertEquals(0.25, Learning.decay(240.0, 120.0), 1e-9)
        val e = event(1, FeedbackType.SWIPE_YES, listOf("item:x"), "casual").copy(epochDay = TODAY - 240)
        val stats = Learning.apply(e, io.github.aristidisg.ensemble.engine.learning.PreferenceStats(), LearningConfig(120.0))
        val raw = stats.stats.values.first { it.contextKey == "GLOBAL" }
        assertEquals(0.25, raw.pos * Learning.decay((TODAY - raw.lastUpdatedEpochDay).toDouble(), 120.0), 1e-9)
    }

    @Test fun `13 a 300-item wardrobe gives a 12-card deck in under 1_5 s`() {
        val big = Synthetic.wardrobe(300, seed = 13)
        val c = ctx("casual", weather(12.0, 17.0), items = big)
        engine.generate(c, big) // warm-up
        val t0 = System.nanoTime()
        val deck = engine.generate(c.copy(epochDay = TODAY + 1), big)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("300-item deck: ${deck.cards.size} cards in $ms ms")
        assertEquals(12, deck.cards.size)
        assertTrue(ms < 1500, "took $ms ms")
    }

    @Test fun `14 navy+rust, cobalt+orange, red+green`() {
        val c = ctx("casual", weather(16.0, 19.0))
        val nr = score(c, "sweater-rust@TOP", "chinos-navy", "white-sneakers")
        assertTrue(nr.harmony.startsWith("Neutral + accent"), nr.harmony)
        val co = score(c, "cobalt-tee", "sweater-orange@MID", "chinos-stone", "white-sneakers")
        assertTrue(co.harmony.startsWith("Complementary"), co.harmony)
        val rg = score(c, "red-polo", "chinos-green", "white-sneakers")
        assertTrue(rg.harmony.startsWith("Complementary"), rg.harmony)
    }

    @Test fun `15 wedding guest in a cream suit gets the wedding-white penalty`() {
        val s = score(ctx("wedding", weather(18.0, 22.0)), "suit-jacket-cream@OUTER", "dress-shirt-white", "suit-trousers-cream", "brown-loafers", "belt-brown")
        assertTrue(s.results.any { it.key == Explain.WEDDING_WHITE && it.score == 0.0 })
    }

    @Test fun `16 a locked piece is on every card`() {
        val deck = engine.generate(ctx("casual"), wardrobe, SuggestionRequest(locked = pieces("chinos-stone")))
        assertTrue(deck.cards.isNotEmpty())
        assertTrue(deck.cards.all { "chinos-stone" in it.itemIds })
    }

    @Test fun `17 an outfit worn yesterday is absent for 14 days and says when it returns`() {
        val ids = arrayOf("tee-white", "jeans-dark", "white-sneakers")
        val worn = Fixtures.wear(TODAY - 1, *ids)
        for (d in 0L..12L) {
            val c = ctx("casual", day = TODAY + d, history = listOf(worn))
            val look = Fixtures.look(c, *ids)
            assertNotNull(HardConstraints.outfitViolation(look, c), "day +$d")
            assertEquals(14 - (d + 1), HardConstraints.cooldownRemaining(look.signature, c))
        }
        val back = ctx("casual", day = TODAY + 13, history = listOf(worn))
        assertNull(HardConstraints.outfitViolation(Fixtures.look(back, *ids), back))
    }

    @Test fun `18 leather loafers worn yesterday - rest penalty today, none the day after`() {
        val worn = Fixtures.wear(TODAY - 1, "oxford-white", "chinos-navy", "brown-loafers")
        val ids = arrayOf("oxford-blue", "chinos-stone", "brown-loafers")
        val today = score(ctx("office", history = listOf(worn)), *ids)
        assertTrue(today.results.any { it.key == Explain.SHOE_REST })
        val tomorrow = score(ctx("office", day = TODAY + 1, history = listOf(worn)), *ids)
        assertTrue(tomorrow.results.none { it.key == Explain.SHOE_REST })
    }

    companion object {
        fun Features(c: EngineContext, vararg ids: String): List<String> =
            io.github.aristidisg.ensemble.engine.learning.Features.extract(Fixtures.look(c, *ids), c)

        fun event(i: Int, type: FeedbackType, features: List<String>, scenario: String, reasons: List<WhyNot> = emptyList()) = FeedbackEvent(
            id = "e$i", epochMillis = (TODAY * 86_400_000L) + i, epochDay = TODAY, type = type, outfitSignature = "sig$i",
            pieces = emptyList(), reasons = reasons, features = features,
            context = ContextSnapshot(scenario, Season.AUTUMN, "12-16", 3, 12.0, 16.0),
        )
    }
}
