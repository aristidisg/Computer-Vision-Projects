package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.GarmentColors
import io.github.aristidisg.ensemble.engine.color.Lab
import io.github.aristidisg.ensemble.engine.color.Sample
import io.github.aristidisg.ensemble.model.ColorFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarmentColorsTest {
    private val rng = SeededRandom(7)

    /** Pixels of one fabric: mostly lit, a share in shadow (darker, less chroma) and a little glare. */
    private fun fabric(hex: String, n: Int, shadow: Double = 0.3, glare: Double = 0.08): List<Sample> {
        val base = ColorScience.hexToLab(hex)
        return (0 until n).map { i ->
            val r = i.toDouble() / n
            val (dl, cScale) = when {
                r < shadow -> -0.10 - 0.08 * rng.nextDouble() to 0.6
                r < shadow + glare -> 0.12 + 0.05 * rng.nextDouble() to 0.5
                else -> (rng.nextDouble() - 0.5) * 0.04 to 1.0
            }
            val jitter = { (rng.nextDouble() - 0.5) * 0.01 }
            Sample(Lab((base.l + dl).coerceIn(0.0, 1.0), base.a * cScale + jitter(), base.b * cScale + jitter()), 1.0)
        }
    }

    private fun fam(c: io.github.aristidisg.ensemble.model.ItemColor) = ColorScience.nearestFamily(Lab(c.l, c.a, c.b), denim = false)

    @Test fun `a shaded navy sweater is one navy color, not navy plus black`() {
        val colors = GarmentColors.extract(fabric("#1F2A44", 600))
        assertEquals(1, colors.size, "${colors.map { it.hex to it.share }}")
        assertEquals(ColorFamily.NAVY, fam(colors[0]))
    }

    @Test fun `the robust color is close to the lit fabric, not the darker mean`() {
        val c = GarmentColors.extract(fabric("#7B4B2A", 600))[0]
        assertTrue(ColorScience.deltaE(Lab(c.l, c.a, c.b), ColorScience.hexToLab("#7B4B2A")) < 0.035, "got ${c.hex}")
    }

    @Test fun `a dark brown leather jacket with glare stays chocolate`() {
        assertEquals(ColorFamily.CHOCOLATE, fam(GarmentColors.extract(fabric("#3B2A22", 600, shadow = 0.2, glare = 0.15))[0]))
    }

    @Test fun `two real colors stay two colors`() {
        val colors = GarmentColors.extract(fabric("#FAFAFA", 300, shadow = 0.2, glare = 0.0) + fabric("#1F2A44", 300))
        assertEquals(2, colors.size, "${colors.map { it.hex }}")
        val fams = colors.map(::fam).toSet()
        assertTrue(ColorFamily.NAVY in fams && (ColorFamily.WHITE in fams || ColorFamily.LIGHT_GREY in fams), "$fams")
    }

    @Test fun `black and white are never shades of each other`() {
        assertTrue(!GarmentColors.sameFabric(ColorScience.hexToLab("#111111"), ColorScience.hexToLab("#FAFAFA")))
        assertTrue(GarmentColors.sameFabric(ColorScience.hexToLab("#1F2A44"), ColorScience.hexToLab("#151B2A")))
        assertTrue(!GarmentColors.sameFabric(ColorScience.hexToLab("#1F2A44"), ColorScience.hexToLab("#6D1A2A")))
    }

    @Test fun `tiny specks are dropped`() {
        val cs = GarmentColors.extract(fabric("#C8A27A", 600, shadow = 0.1) + fabric("#C62828", 15, shadow = 0.0, glare = 0.0))
        assertEquals(1, cs.size, "${cs.map { it.hex to it.share }}")
    }
}
