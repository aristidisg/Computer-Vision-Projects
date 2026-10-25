package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorNamingTest {
    private fun fam(hex: String, denim: Boolean = false) = ColorScience.nearestFamily(ColorScience.hexToLab(hex), denim)

    @Test fun `deep blues are navy or blue, never black`() {
        for (h in listOf("#101828", "#0E1626", "#0A0F1F", "#1A2238", "#003366", "#1D3557", "#14213D")) {
            val f = fam(h)
            assertTrue(f == ColorFamily.NAVY || f == ColorFamily.BLUE, "$h -> $f")
        }
        assertEquals(ColorFamily.BLUE, fam("#3A5A9B"))
    }

    @Test fun `true blacks and greys stay achromatic`() {
        assertEquals(ColorFamily.BLACK, fam("#111111"))
        assertEquals(ColorFamily.BLACK, fam("#16171A"))
        assertEquals(ColorFamily.MID_GREY, fam("#7F8082"))
    }

    @Test fun `denim names only for denim pieces`() {
        assertEquals(ColorFamily.DARK_DENIM, fam("#2E3F5C", denim = true))
        assertTrue(!fam("#2E3F5C").isDenim)
        assertTrue(!fam("#5B7FA6").isDenim)
        val jeans = ItemFactory.create("j", Subcategory.JEANS, listOf(KMeans.itemColor("#2E3F5C")))
        assertEquals(ColorFamily.DARK_DENIM, jeans.primaryColor!!.family)
        val zip = ItemFactory.create("q", Subcategory.QUARTER_ZIP, listOf(KMeans.itemColor("#101828")))
        assertTrue(zip.primaryColor!!.family == ColorFamily.NAVY, "${zip.primaryColor}")
        assertEquals("Navy quarter-zip", zip.name)
    }

    @Test fun `normalizing fixes old wrong names but keeps typed names`() {
        val zip = ItemFactory.create("q", Subcategory.QUARTER_ZIP, listOf(KMeans.itemColor("#101828")))
        val wrong = zip.copy(colors = zip.colors.map { it.copy(family = ColorFamily.BLACK) }, name = "Black quarter-zip")
        assertEquals("Navy quarter-zip", ItemFactory.normalizeColors(wrong).name)
        val typed = wrong.copy(name = "Work zip")
        assertEquals("Work zip", ItemFactory.normalizeColors(typed).name)
        assertEquals(ColorFamily.NAVY, ItemFactory.normalizeColors(typed).primaryColor!!.family)
        assertEquals(zip, ItemFactory.normalizeColors(zip))
    }
}

class RefreshDerivedTest {
    private val tee = ItemFactory.create("t", Subcategory.T_SHIRT, listOf(KMeans.itemColor("#FFFFFF")))

    @Test fun `hand-set seasons and formality survive a material change`() {
        val custom = tee.copy(seasons = setOf(io.github.aristidisg.ensemble.model.Season.WINTER), formality = 9)
        val edited = ItemFactory.refreshDerived(custom.copy(materials = listOf(io.github.aristidisg.ensemble.model.Material.LINEN)), custom)
        assertEquals(setOf(io.github.aristidisg.ensemble.model.Season.WINTER), edited.seasons)
        assertEquals(9, edited.formality)
    }

    @Test fun `automatic seasons follow a material change`() {
        val edited = ItemFactory.refreshDerived(tee.copy(materials = listOf(io.github.aristidisg.ensemble.model.Material.LINEN)), tee)
        assertEquals(ItemFactory.seasons(edited), edited.seasons)
    }
}
