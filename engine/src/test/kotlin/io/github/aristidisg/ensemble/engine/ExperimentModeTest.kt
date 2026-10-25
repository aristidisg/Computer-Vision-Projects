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
