package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.generator.DeckBuilder
import io.github.aristidisg.ensemble.engine.planner.DayContext
import io.github.aristidisg.ensemble.engine.planner.WeekPlanner
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.model.ItemFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The week plan: varied like a deck, finished with accessories, and new on every replan. */
class WeekPlanTest {
    private val wardrobe = Fixtures.wardrobe
    private val days = (0 until 7).map { d -> DayContext(TODAY + d, Fixtures.scenario("casual"), Fixtures.weather(14.0 + d % 3, 19.0)) }
    private val base = Fixtures.ctx("casual")
    private fun core(o: io.github.aristidisg.ensemble.engine.rules.ScoredOutfit) = DeckBuilder.coreSignature(o)

    @Test fun `a week mixes colours and techniques`() {
        val plan = WeekPlanner().plan(days, base, wardrobe)
        val outfits = plan.days.mapNotNull { it.outfit }
        assertEquals(7, outfits.size)
        val uppers = outfits.mapNotNull { o -> Look.of(o.pieces, base)?.upperDominant?.family }
        assertTrue(uppers.toSet().size >= 4, "upper colours $uppers")
        assertTrue(uppers.groupingBy { it }.eachCount().values.all { it <= 2 }, "upper colours $uppers")
        assertTrue(outfits.mapNotNull { it.technique }.toSet().size >= 3, "${outfits.map { it.technique }}")
    }

    @Test fun `days are finished with accessories`() {
        val plan = WeekPlanner().plan(days, base, wardrobe)
        assertTrue(plan.days.count { d -> d.outfit?.pieces?.any { !it.slot.isCore } == true } >= 4)
    }

    @Test fun `replan deals a new week, the same inputs the same week`() {
        val first = WeekPlanner().plan(days, base, wardrobe)
        assertEquals(first.days.map { it.outfit?.signature }, WeekPlanner().plan(days, base, wardrobe).days.map { it.outfit?.signature })
        val avoid = first.days.mapNotNull { it.outfit?.let(::core) }.toSet()
        val again = WeekPlanner().plan(days, base, wardrobe, regenerate = 1, avoid = avoid)
        val repeated = again.days.count { d -> d.outfit?.let(::core) in avoid }
        assertTrue(repeated <= 1, "repeated $repeated of 7")
    }

    @Test fun `a signature piece gets a day in the week`() {
        val items = wardrobe.map { if (it.id == "chinos-green") it.copy(flags = ItemFlags(signature = true)) else it }
        val plan = WeekPlanner().plan(days, Fixtures.ctx("casual", items = items), items)
        assertTrue(plan.days.any { d -> d.outfit?.itemIds?.contains("chinos-green") == true })
    }

    @Test fun `a vogue week is all cover looks`() {
        val plan = WeekPlanner().plan(days, base.copy(options = DeckOptions(vogue = true)), wardrobe)
        assertTrue(plan.days.all { it.outfit?.technique != null && it.outfit?.cover != null })
    }
}
