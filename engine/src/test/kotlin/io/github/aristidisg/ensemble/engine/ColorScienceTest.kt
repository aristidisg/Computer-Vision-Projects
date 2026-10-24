package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.color.Lab
import io.github.aristidisg.ensemble.engine.color.PatternHint
import io.github.aristidisg.ensemble.engine.color.Sample
import io.github.aristidisg.ensemble.engine.rules.Harmony
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Temperature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorScienceTest {
    private fun near(expected: Double, actual: Double, tol: Double = 1e-3) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected got $actual")

    @Test fun `oklab reference values`() {
        // Reference values from Björn Ottosson's OKLab post.
        val white = ColorScience.hexToLab("#FFFFFF"); near(1.0, white.l); near(0.0, white.a); near(0.0, white.b)
        val black = ColorScience.hexToLab("#000000"); near(0.0, black.l)
        val red = ColorScience.hexToLab("#FF0000"); near(0.62796, red.l); near(0.22486, red.a); near(0.12585, red.b)
        val green = ColorScience.hexToLab("#00FF00"); near(0.86644, green.l); near(-0.23389, green.a); near(0.17950, green.b)
        val blue = ColorScience.hexToLab("#0000FF"); near(0.45201, blue.l); near(-0.03246, blue.a); near(-0.31153, blue.b)
    }

    @Test fun `round trip srgb to oklab and back`() {
        for (hex in listOf("#1F2A44", "#C19A6B", "#B5532E", "#FAFAFA", "#2F55D4", "#123456")) {
            assertEquals(hex, ColorScience.labToHex(ColorScience.hexToLab(hex)))
        }
    }

    @Test fun `artist hue anchors`() {
        near(0.0, ColorScience.artistHueFromHsv(0.0))
        near(60.0, ColorScience.artistHueFromHsv(30.0))
        near(120.0, ColorScience.artistHueFromHsv(60.0))
        near(180.0, ColorScience.artistHueFromHsv(120.0))
        near(240.0, ColorScience.artistHueFromHsv(240.0))
        val purple = ColorScience.artistHueFromHsv(285.0)
        assertTrue(purple in 270.0..300.0)
    }

    @Test fun `classic complements are opposite on the artist wheel`() {
        fun h(hex: String) = ColorScience.artistHue(ColorScience.parseHex(hex))
        assertTrue(ColorScience.hueDistance(h("#FF0000"), h("#00FF00")) >= 170)
        assertTrue(ColorScience.hueDistance(h("#0000FF"), h("#FF8000")) >= 170)
        assertTrue(ColorScience.hueDistance(h("#FFFF00"), h("#8000FF")) >= 150)
        // Navy + camel and navy + rust read as near-complementary.
        assertTrue(ColorScience.hueDistance(h("#1F2A44"), h("#C19A6B")) >= 150)
        assertTrue(ColorScience.hueDistance(h("#1F2A44"), h("#B5532E")) >= 150)
    }

    @Test fun `every family's own hex maps to that family`() {
        for (f in ColorTables.families) assertEquals(f.family, ColorScience.nearestFamily(f.lab), "family ${f.family}")
    }

    @Test fun `neutralness and temperature`() {
        val navy = ColorScience.info("#1F2A44")
        assertEquals(ColorFamily.NAVY, navy.family)
        assertTrue(navy.neutralness >= 0.9)
        assertEquals(Temperature.COOL, navy.temperature)
        val orange = ColorScience.info("#EF7D22")
        assertTrue(orange.neutralness < 0.2)
        assertEquals(Temperature.WARM, orange.temperature)
        val cobalt = ColorScience.info("#2F55D4")
        assertEquals(Temperature.COOL, cobalt.temperature)
    }

    @Test fun `kmeans is deterministic and finds two colors`() {
        val a = ColorScience.hexToLab("#1F2A44"); val b = ColorScience.hexToLab("#F4F4F2")
        val samples = List(300) { Sample(a, 1.0) } + List(100) { Sample(b, 1.0) } +
            List(50) { Sample(Lab(a.l + 0.01, a.a, a.b), 1.0) }
        val c1 = KMeans.cluster(samples, seed = 7); val c2 = KMeans.cluster(samples, seed = 7)
        assertEquals(c1, c2)
        assertEquals(2, c1.size)
        assertEquals(ColorFamily.NAVY, ColorScience.nearestFamily(c1[0].lab))
        near(0.78, c1[0].share, 0.02)
        val colors = KMeans.toItemColors(c1)
        assertEquals(ColorFamily.WHITE, colors[1].family)
    }

    @Test fun `kmeans drops clusters under four percent`() {
        val a = ColorScience.hexToLab("#C62828"); val b = ColorScience.hexToLab("#2F55D4")
        val samples = List(980) { Sample(a, 1.0) } + List(20) { Sample(b, 1.0) }
        assertEquals(1, KMeans.cluster(samples).size)
    }

    @Test fun `pattern hint needs contrast and edges`() {
        val two = KMeans.cluster(List(50) { Sample(ColorScience.hexToLab("#111111"), 1.0) } + List(50) { Sample(ColorScience.hexToLab("#FAFAFA"), 1.0) })
        assertTrue(PatternHint.looksPatterned(two, 0.5))
        assertTrue(!PatternHint.looksPatterned(two, 0.05))
        val stripes = Array(10) { y -> DoubleArray(10) { x -> if ((x / 2) % 2 == 0) 0.0 else 1.0 } }
        val mask = Array(10) { BooleanArray(10) { true } }
        assertTrue(PatternHint.edgeDensity(stripes, mask) > 1.0)
    }

    @Test fun `harmony templates`() {
        val neutral = Harmony.fit(emptyList(), 0.4)
        assertEquals("Neutral", neutral.label); assertEquals(1.0, neutral.score)
        val analog = Harmony.fit(listOf(10.0 to 0.3, 40.0 to 0.3), 0.4)
        assertTrue(analog.score > 0.95)
        val comp = Harmony.fit(listOf(0.0 to 0.3, 180.0 to 0.3), 0.4)
        assertTrue(comp.score >= 0.9 && comp.template == Harmony.I, "complementary fits I: $comp")
        // Triadic is only allowed at boldness >= 0.6.
        assertTrue(Harmony.TRIADIC in Harmony.templates(0.8))
        assertTrue(Harmony.TRIADIC !in Harmony.templates(0.3))
        val tri = Harmony.fit(listOf(0.0 to 0.3, 120.0 to 0.3, 240.0 to 0.3), 0.8)
        assertTrue(tri.score >= 0.7)
        val clash = Harmony.fit(listOf(0.0 to 0.3, 70.0 to 0.3, 150.0 to 0.3, 230.0 to 0.3), 0.3)
        assertTrue(clash.score < analog.score)
    }
}
