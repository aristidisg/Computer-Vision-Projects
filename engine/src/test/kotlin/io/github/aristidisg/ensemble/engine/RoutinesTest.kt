package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.planner.Routines
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.WearLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutinesTest {
    private val base = Fixtures.wardrobe.take(3)
    private val summer = base[0].copy(id = "s", seasons = setOf(Season.SUMMER))
    private val allYear = base[1].copy(id = "a", seasons = Season.entries.toSet())
    private val storedWinter = base[2].copy(id = "w", seasons = setOf(Season.WINTER), status = ItemStatus.STORED)
    private val storedSummer = base[2].copy(id = "x", seasons = setOf(Season.SUMMER), status = ItemStatus.STORED)
    private val laundrySummer = base[0].copy(id = "l", seasons = setOf(Season.SUMMER), status = ItemStatus.LAUNDRY)
    private val items = listOf(summer, allYear, storedWinter, storedSummer, laundrySummer)

    @Test fun `swap stores off-season pieces and brings back in-season ones`() {
        val plan = Routines.seasonalSwap(items, Season.WINTER)
        assertEquals(listOf("s"), plan.store.map { it.id })
        assertEquals(listOf("w"), plan.restore.map { it.id })
    }

    @Test fun `swap leaves all-season and non-available pieces alone`() {
        val plan = Routines.seasonalSwap(items, Season.SUMMER)
        assertTrue(plan.store.isEmpty())
        assertEquals(listOf("x"), plan.restore.map { it.id })
        assertTrue(Routines.seasonalSwap(listOf(allYear), Season.WINTER).isEmpty)
    }

    private val a = Fixtures.wardrobe[0]; private val b = Fixtures.wardrobe[1]
    private val outfit = Outfit("o", listOf(OutfitPiece(a.id, a.primarySlot), OutfitPiece(b.id, b.primarySlot)), liked = true)
    private val byId = Fixtures.byId
    private fun worn(day: Long) = WearLog("w$day", day, outfit.signature, outfit.pieces, "casual")

    @Test fun `wear again lists liked outfits once the cooldown is over`() {
        val today = Fixtures.TODAY
        val ready = Routines.wearAgain(listOf(outfit), listOf(worn(today - 20), worn(today - 30)), byId, today, 14)
        assertEquals(listOf(20L), ready.map { it.daysSince })
    }

    @Test fun `wear again skips cooling, never worn, unliked and unavailable outfits`() {
        val today = Fixtures.TODAY
        assertTrue(Routines.wearAgain(listOf(outfit), listOf(worn(today - 5)), byId, today, 14).isEmpty())
        assertTrue(Routines.wearAgain(listOf(outfit), emptyList(), byId, today, 14).isEmpty())
        assertTrue(Routines.wearAgain(listOf(outfit.copy(liked = false)), listOf(worn(today - 20)), byId, today, 14).isEmpty())
        val inLaundry = byId + (a.id to a.copy(status = ItemStatus.LAUNDRY))
        assertTrue(Routines.wearAgain(listOf(outfit), listOf(worn(today - 20)), inLaundry, today, 14).isEmpty())
        assertTrue(Routines.wearAgain(listOf(outfit.copy(cooldownDays = 30)), listOf(worn(today - 20)), byId, today, 14).isEmpty())
    }
}

class ShoppingListTest {
    private val gap = io.github.aristidisg.ensemble.engine.planner.GapSuggestion(
        io.github.aristidisg.ensemble.engine.tables.Archetype(io.github.aristidisg.ensemble.model.Subcategory.CHINOS, listOf(io.github.aristidisg.ensemble.model.ColorFamily.OLIVE)),
        io.github.aristidisg.ensemble.model.ColorFamily.OLIVE, unlocks = 12, fills = listOf("Office · Mild"), score = 12.0,
    )

    @Test fun `adding a gap creates one entry with the family color`() {
        val list = Routines.addToShoppingList(emptyList(), gap, "e1", 100)
        assertEquals(1, list.size)
        assertEquals("Olive chinos", list[0].title)
        assertEquals(12, list[0].unlocks)
        assertEquals(io.github.aristidisg.ensemble.engine.color.ColorTables.family(gap.color).hex, list[0].hex)
    }

    @Test fun `adding the same gap again does not duplicate and reopens it`() {
        val once = Routines.addToShoppingList(emptyList(), gap, "e1", 100).map { it.copy(done = true) }
        val twice = Routines.addToShoppingList(once, gap.copy(unlocks = 20), "e2", 101)
        assertEquals(listOf("e1"), twice.map { it.id })
        assertEquals(20, twice[0].unlocks)
        assertEquals(false, twice[0].done)
    }

    @Test fun `open entries sort before done ones, biggest unlock first`() {
        val a = Routines.addToShoppingList(emptyList(), gap, "a", 1)
        val b = Routines.addToShoppingList(a, gap.copy(color = io.github.aristidisg.ensemble.model.ColorFamily.NAVY, unlocks = 30), "b", 2)
        val c = b.map { if (it.id == "b") it.copy(done = true) else it }
        assertEquals(listOf("b", "a"), Routines.sortedShoppingList(b).map { it.id })
        assertEquals(listOf("a", "b"), Routines.sortedShoppingList(c).map { it.id })
    }
}
