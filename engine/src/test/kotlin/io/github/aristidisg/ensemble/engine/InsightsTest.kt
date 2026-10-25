package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.Fixtures.wear
import io.github.aristidisg.ensemble.engine.planner.GapFinder
import io.github.aristidisg.ensemble.engine.planner.UsageContext
import io.github.aristidisg.ensemble.engine.planner.VarietyStats
import io.github.aristidisg.ensemble.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The weekly variety card and the gap finder's ranking. */
class InsightsTest {
    private val wardrobe = Fixtures.wardrobe

    @Test fun `a varied week scores higher than the same outfit every day`() {
        val varied = listOf(
            wear(TODAY - 1, "tee-white", "jeans-dark", "white-sneakers"),
            wear(TODAY - 2, "oxford-blue", "chinos-stone", "brown-loafers"),
            wear(TODAY - 3, "sweater-rust", "cargo-olive", "canvas-sneakers"),
        )
        val same = (1..3).map { wear(TODAY - it, "tee-white", "jeans-dark", "white-sneakers") }
        val a = VarietyStats.week(varied, wardrobe, TODAY)
        val b = VarietyStats.week(same, wardrobe, TODAY)
        assertTrue((a.score ?: 0) > (b.score ?: 0), "${a.score} vs ${b.score}")
        assertEquals(6, a.piecesWorn)
        assertEquals(3, a.outfits)
        assertTrue(b.repeats.map { it.first.id }.containsAll(listOf("tee-white", "jeans-dark")))
        assertTrue(a.repeats.isEmpty())
    }

    @Test fun `variety ignores older wears and shoes, and is empty without wears`() {
        val old = listOf(wear(TODAY - 20, "tee-white", "jeans-dark", "white-sneakers"))
        val r = VarietyStats.week(old, wardrobe, TODAY)
        assertNull(r.score)
        assertEquals(0, r.outfits)
        val w = VarietyStats.week(listOf(wear(TODAY, "tee-white", "jeans-dark", "white-sneakers")), wardrobe, TODAY)
        assertEquals(2, w.piecesWorn)
    }

    @Test fun `pieces unworn for a month are listed, recently worn ones are not`() {
        val r = VarietyStats.week(listOf(wear(TODAY - 2, "tee-white", "jeans-dark")), wardrobe.map { it.copy(createdEpochDay = TODAY - 60) }, TODAY)
        assertTrue(r.unworn.none { it.id == "tee-white" })
        assertTrue(r.unworn.any { it.id == "tee-black" })
    }

    private val small = wardrobe.filter { it.id in setOf("tee-white", "tee-black", "jeans-dark", "chinos-stone", "white-sneakers", "brown-loafers", "oxford-white") }
    private val usage = listOf(UsageContext(Fixtures.scenario("casual"), Fixtures.weather(14.0, 18.0), 1.0, "Casual 14-18 °C"))
    private val gaps by lazy { GapFinder().findGaps(Fixtures.ctx("casual", items = small), small, usage, limit = 8) }

    @Test fun `the gap finder skips what you own and varies its list`() {
        assertTrue(gaps.isNotEmpty())
        val owned = small.map { it.subcategory to it.primaryColor!!.family }.toSet()
        assertTrue(gaps.none { (it.archetype.sub to it.color) in owned })
        assertEquals(gaps.size, gaps.map { it.archetype.sub }.toSet().size, "one per kind of piece")
        assertTrue(gaps.groupingBy { it.color }.eachCount().values.all { it <= 2 })
    }

    @Test fun `each gap says what it pairs with and what it opens up`() {
        val g = gaps.first()
        assertTrue(g.pairsWith.isNotEmpty() && g.pairsWith.all { id -> small.any { it.id == id } })
        assertTrue(g.unlocks > 0)
        assertTrue(gaps.any { it.techniques.isNotEmpty() })
        assertTrue(gaps.none { it.archetype.sub == Subcategory.T_SHIRT && it.color == small.first { i -> i.id == "tee-white" }.primaryColor!!.family })
    }
}
