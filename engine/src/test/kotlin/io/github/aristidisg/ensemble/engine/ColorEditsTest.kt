package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorRole
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorEditsTest {
    private val jacket = ItemFactory.create("j", Subcategory.LEATHER_JACKET, listOf(KMeans.itemColor("#1B2338")))

    @Test fun `exact hex replaces the color and its family`() {
        val e = ColorEdits.setHex(jacket, 0, "#3b2a22")!!
        assertEquals("#3B2A22", e.colors[0].hex)
        assertEquals(ColorFamily.CHOCOLATE, e.colors[0].family)
        assertTrue(e.name.startsWith("Chocolate"))
    }

    @Test fun `short codes expand and invalid codes are refused`() {
        assertEquals("#FFFFFF", ColorEdits.setHex(jacket, 0, "fff")!!.colors[0].hex)
        assertNull(ColorEdits.setHex(jacket, 0, "#ggg"))
        assertNull(ColorEdits.setHex(jacket, 3, "#fff"))
        assertNull(ColorEdits.add(jacket, "12345"))
    }

    @Test fun `multicolor pieces get roles by share`() {
        val two = ColorEdits.add(jacket, "#C8A27A", share = 0.6)!!
        // The original color keeps share 1.0, so it stays primary; the new one is secondary.
        assertEquals(ColorRole.PRIMARY, two.colors[0].role)
        assertEquals(ColorRole.SECONDARY, two.colors[1].role)
        val swapped = ColorEdits.setShare(two, 0, 0.3)
        assertEquals(ColorRole.SECONDARY, swapped.colors[0].role)
        assertEquals(ColorRole.PRIMARY, swapped.colors[1].role)
        val three = ColorEdits.add(two, "#B22222", share = 0.05)!!
        assertEquals(ColorRole.ACCENT, three.colors[2].role)
        assertEquals(1, three.colors.count { it.role == ColorRole.PRIMARY })
    }

    @Test fun `color count is capped and the last color stays`() {
        var i = jacket
        repeat(10) { i = ColorEdits.add(i, "#808080") ?: i }
        assertEquals(ColorEdits.MAX_COLORS, i.colors.size)
        assertEquals(1, ColorEdits.remove(jacket, 0).colors.size)
        assertEquals(1, ColorEdits.remove(ColorEdits.add(jacket, "#fff")!!, 1).colors.size)
    }

    @Test fun `hand-set name and formality survive color edits`() {
        val named = jacket.copy(name = "My jacket", formality = 9)
        val e = ColorEdits.setHex(named, 0, "#C8A27A")!!
        assertEquals("My jacket", e.name)
        assertEquals(9, e.formality)
    }

    @Test fun `same as copies tags but keeps colors, identity and history`() {
        val source = ItemFactory.create("src", Subcategory.OXFORD_SHIRT, listOf(KMeans.itemColor("#FFFFFF")))
            .copy(seasons = setOf(Season.SUMMER), meta = io.github.aristidisg.ensemble.model.ItemMeta(brand = "Acme", size = "M", notes = "theirs"))
        val target = ItemFactory.create("t", Subcategory.T_SHIRT, listOf(KMeans.itemColor("#1F2A44")), createdEpochDay = 7)
            .copy(laundry = io.github.aristidisg.ensemble.model.Laundry(1, 1))
        val e = ColorEdits.sameAs(target, source)
        assertEquals("t", e.id)
        assertEquals(Subcategory.OXFORD_SHIRT, e.subcategory)
        assertEquals(setOf(Season.SUMMER), e.seasons)
        assertEquals("Acme", e.meta.brand)
        assertEquals("", e.meta.notes)
        assertEquals("#1F2A44", e.colors[0].hex)
        assertEquals(7, e.createdEpochDay)
        assertEquals(1, e.laundry.wearsSinceWash)
        assertEquals(source.laundry.wearsBeforeWash, e.laundry.wearsBeforeWash)
    }
}
