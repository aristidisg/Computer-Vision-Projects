package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.rules.ColorPairRules
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Strictness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentModeTest {
    private val normal = ctx("casual")
    private val bold = normal.copy(settings = Settings(experimentMode = true))

    @Test fun `experiment raises boldness, loosens strictness and adds new ideas`() {
        assertTrue(bold.boldness > normal.boldness)
        assertEquals(Strictness.ADVENTUROUS, bold.strictness)
        assertTrue(bold.explorationRate > normal.explorationRate)
        val deck = OutfitEngine().generate(bold, Fixtures.wardrobe).cards
        assertTrue(deck.count { it.newIdea } > OutfitEngine().generate(normal, Fixtures.wardrobe).cards.count { it.newIdea })
    }

    @Test fun `experiment gives a different deck`() {
        val a = OutfitEngine().generate(normal, Fixtures.wardrobe).cards.map { it.signature }
        val b = OutfitEngine().generate(bold, Fixtures.wardrobe).cards.map { it.signature }
        assertTrue(b.count { it !in a } >= b.size / 3)
    }

    @Test fun `unusual pairs are welcome but clashes still are not`() {
        // Navy with black is "unusual" (contested), not a clash.
        val unusual = ColorPairRules.c7ClassicPairs(look(bold, "tee-navy", "jeans-black", "black-derbies"), bold)!!
        assertTrue(unusual.score >= 0.7 && unusual.key != Explain.UNUSUAL_PAIR)
        assertEquals(Explain.UNUSUAL_PAIR, ColorPairRules.c7ClassicPairs(look(normal, "tee-navy", "jeans-black", "black-derbies"), normal)!!.key)
        val rose = Fixtures.item("oxford-white").copy(id = "rose", colors = listOf(io.github.aristidisg.ensemble.engine.color.KMeans.itemColor("#C9848C")))
        val olive = Fixtures.item("chinos-stone").copy(id = "olive", colors = listOf(io.github.aristidisg.ensemble.engine.color.KMeans.itemColor("#6B6B3A")))
        val c = bold.copy(items = bold.items + (rose.id to rose) + (olive.id to olive))
        val clash = ColorPairRules.c7ClassicPairs(look(c, "rose@TOP", "olive@BOTTOM", "white-sneakers"), c)!!
        assertTrue(clash.score <= 0.2 && clash.weight >= 4.0)
    }
}

class SeasonTagTest {
    @Test fun `pieces not tagged for the season lose match and say so`() {
        val c = ctx("casual")  // October: autumn
        val base = Fixtures.score(c, "tee-white", "chinos-navy", "white-sneakers")
        val summerTee = Fixtures.item("tee-white").copy(id = "summer-tee", seasons = setOf(io.github.aristidisg.ensemble.model.Season.SUMMER))
        val c2 = c.copy(items = c.items + (summerTee.id to summerTee))
        val off = io.github.aristidisg.ensemble.engine.rules.Scorer.score(look(c2, "summer-tee@TOP", "chinos-navy", "white-sneakers"), c2)
        assertTrue(off.total < base.total - 0.05, "${off.total} vs ${base.total}")
        assertTrue(off.results.any { it.key == Explain.OUT_OF_SEASON })
        assertTrue(base.results.none { it.key == Explain.OUT_OF_SEASON })
    }

    @Test fun `a summer piece is in season on a summer-like day in autumn`() {
        val hot = ctx("casual", Fixtures.weather(24.0, 29.0))
        assertTrue(io.github.aristidisg.ensemble.model.Season.SUMMER in hot.seasonsNow)
        val summerTee = Fixtures.item("tee-white").copy(id = "summer-tee", seasons = setOf(io.github.aristidisg.ensemble.model.Season.SUMMER))
        val c = hot.copy(items = hot.items + (summerTee.id to summerTee))
        val s = io.github.aristidisg.ensemble.engine.rules.Scorer.score(look(c, "summer-tee@TOP", "chinos-navy", "white-sneakers"), c)
        assertTrue(s.results.none { it.key == Explain.OUT_OF_SEASON })
    }
}

class OuterwearOffTest {
    @Test fun `no outer layers when outerwear is off, even in the cold`() {
        val cold = ctx("casual", Fixtures.weather(4.0, 8.0))
        assertTrue(OutfitEngine().generate(cold, Fixtures.wardrobe).cards.any { c -> c.pieces.any { it.slot == io.github.aristidisg.ensemble.model.Slot.OUTER } })
        val off = cold.copy(settings = Settings(outerwearOff = true))
        val deck = OutfitEngine().generate(off, Fixtures.wardrobe).cards
        assertTrue(deck.isNotEmpty())
        assertTrue(deck.none { c -> c.pieces.any { it.slot == io.github.aristidisg.ensemble.model.Slot.OUTER } })
    }

    @Test fun `a scenario that needs a jacket still works with outerwear off`() {
        val formal = ctx("business_formal", Fixtures.weather(14.0, 18.0)).copy(settings = Settings(outerwearOff = true))
        val deck = OutfitEngine().generate(formal, Fixtures.wardrobe).cards
        assertTrue(deck.none { c -> c.pieces.any { it.slot == io.github.aristidisg.ensemble.model.Slot.OUTER } })
    }
}

class LayeringTest {
    @Test fun `a tee under a hoodie barely shows, under an open overshirt it does`() {
        val c = ctx("casual", Fixtures.weather(14.0, 18.0))
        val hoodie = look(c, "tee-white", "hoodie-grey@MID", "jeans-dark", "white-sneakers")
        val open = look(c, "tee-white", "overshirt-olive@MID", "jeans-dark", "white-sneakers")
        assertTrue(hoodie.midClosed && hoodie.topHidden)
        assertTrue(!open.midClosed && !open.topHidden)
        assertTrue(hoodie.pieceArea.getValue("tee-white") < open.pieceArea.getValue("tee-white") / 1.5)
    }

    @Test fun `no mid layers when they are switched off, even in the cold`() {
        val cold = ctx("casual", Fixtures.weather(4.0, 8.0)).copy(settings = Settings(midLayersOff = true))
        val deck = OutfitEngine().generate(cold, Fixtures.wardrobe).cards
        assertTrue(deck.isNotEmpty())
        assertTrue(deck.none { c -> c.pieces.any { it.slot == io.github.aristidisg.ensemble.model.Slot.MID } })
    }
}
