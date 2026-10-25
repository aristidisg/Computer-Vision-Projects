package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.Fixtures.scenario
import io.github.aristidisg.ensemble.engine.Fixtures.weather
import io.github.aristidisg.ensemble.engine.planner.DayContext
import io.github.aristidisg.ensemble.engine.planner.GapFinder
import io.github.aristidisg.ensemble.engine.planner.Recreate
import io.github.aristidisg.ensemble.engine.planner.TripPacker
import io.github.aristidisg.ensemble.engine.planner.TripRequest
import io.github.aristidisg.ensemble.engine.planner.UsageContext
import io.github.aristidisg.ensemble.engine.planner.WeekPlanner
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.DailyPoint
import io.github.aristidisg.ensemble.model.HourlyPoint
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.LookPiece
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.WeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlannerTest {
    private val wardrobe = Fixtures.wardrobe

    @Test fun `a planned week never repeats a top inside its window`() {
        val days = (0 until 7).map { d ->
            DayContext(TODAY + d, scenario(if (d < 5) "university" else "casual"), weather(12.0 + d % 3, 18.0))
        }
        val plan = WeekPlanner().plan(days, Fixtures.ctx("casual"), wardrobe)
        assertEquals(7, plan.days.size)
        assertTrue(plan.days.all { it.outfit != null }, "every day planned")
        val tops = plan.days.map { d -> d.outfit!!.pieces.first { it.slot == Slot.TOP }.itemId }
        assertEquals(tops.size, tops.toSet().size, "tops $tops")
        assertTrue(plan.conflicts.isEmpty(), "${plan.conflicts}")
    }

    @Test fun `a five-day trip packs within its limit`() {
        val days = (0 until 5).map { d -> DayContext(TODAY + d, scenario(if (d == 2) "dinner" else "casual"), weather(14.0, 20.0)) }
        val plan = TripPacker().pack(TripRequest(days, luggageLimit = 12, shoeLimit = 2), Fixtures.ctx("casual"), wardrobe)
        assertTrue(plan.packed.size <= 12, "packed ${plan.packed.size}")
        assertTrue(plan.packed.count { it.slots.first() == Slot.SHOES } <= 2)
        assertTrue(plan.uncoveredDays.isEmpty(), "uncovered ${plan.uncoveredDays}")
        assertTrue(plan.dayOutfits.all { d -> d.outfit!!.itemIds.all { id -> plan.packed.any { it.id == id } } })
    }

    @Test fun `gap finder ranks archetypes that unlock outfits`() {
        val small = wardrobe.filter { it.id in setOf("tee-white", "tee-black", "jeans-dark", "chinos-stone", "white-sneakers", "brown-loafers", "oxford-white") }
        val usage = listOf(UsageContext(scenario("casual"), weather(14.0, 18.0), 1.0, "Casual 12-16 °C"))
        val gaps = GapFinder().findGaps(Fixtures.ctx("casual", items = small), small, usage, limit = 5)
        assertTrue(gaps.isNotEmpty())
        assertTrue(gaps.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test fun `recreate finds the closest pieces and flags missing ones`() {
        val look = InspirationLook("l1", null, listOf(
            LookPiece(Subcategory.OXFORD_SHIRT, "#FAFAFA", ColorFamily.WHITE),
            LookPiece(Subcategory.CHINOS, "#1F2A44", ColorFamily.NAVY),
            LookPiece(Subcategory.LOAFERS, "#7B4B2A", ColorFamily.BROWN),
            LookPiece(Subcategory.NECKLACE, "#D4A017", ColorFamily.MUSTARD),
        ))
        val m = Recreate.recreate(look, wardrobe, Fixtures.ctx("casual"))
        assertEquals("oxford-white", m.pieces[0].item?.id)
        assertEquals("chinos-navy", m.pieces[1].item?.id)
        assertEquals("brown-loafers", m.pieces[2].item?.id)
        assertTrue(m.pieces[3].missing)
        assertNotNull(m.outfit)
    }

    @Test fun `weather window aggregates`() {
        val day = TODAY
        val base = day * 86_400L
        val hourly = (0 until 48).map { h ->
            HourlyPoint(base + h * 3600L, temp = 10.0 + h % 24, feels = 8.0 + h % 24, precipProb = if (h == 12) 70.0 else 10.0,
                precip = if (h == 12) 2.0 else 0.0, wind = 10.0 + h % 5, uv = (h % 24) / 3.0, humidity = 60.0, code = if (h == 12) 61 else 1)
        }
        val snap = WeatherSnapshot("Test", 0.0, 0.0, 0, 0, hourly, listOf(DailyPoint(day, 8.0, 31.0, 7 * 60, 18 * 60 + 30, 61)))
        val w = WeatherWindow.aggregate(snap, day, 8, 19)!!
        assertEquals(16.0, w.feelsMin); assertEquals(26.0, w.feelsMax)
        assertEquals(70.0, w.rainProbMax); assertEquals(2.0, w.rainSum)
        assertTrue(w.dark, "sunset 18:30 is inside 8-19")
        val morning = WeatherWindow.aggregate(snap, day, 8, 12)!!
        assertTrue(!morning.dark)
    }
}
