package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.rules.StylistRules
import io.github.aristidisg.ensemble.engine.rules.Technique
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Silhouette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StylistTest {
    private fun recolor(id: String, newId: String, hex: String) = item(id).copy(id = newId, colors = listOf(KMeans.itemColor(hex)))
    private val extra: List<Item> = listOf(
        recolor("tee-white", "tee-camel", "#C19A6B"), recolor("chinos-stone", "chinos-brown", "#7B4B2A"),
        recolor("merino-grey", "knit-chocolate", "#4A2C1D"), recolor("tee-white", "tee-black2", "#111111"),
        recolor("jeans-black", "trousers-black", "#141414"), recolor("tee-white", "tee-cobalt", "#2F55D4"),
        recolor("chinos-stone", "chinos-orange", "#EF7D22"), recolor("white-sneakers", "sneakers-cobalt", "#2F55D4"),
        recolor("tee-white", "tee-red", "#C62828"),
    )
    private val c = ctx("casual", Fixtures.weather(15.0, 19.0), items = Fixtures.wardrobe + extra)
    private fun tech(vararg ids: String) = StylistRules.technique(look(c, *ids), c)

    @Test fun `tonal browns`() = assertEquals(Technique.TONAL, tech("tee-camel@TOP", "knit-chocolate@MID", "chinos-brown@BOTTOM", "brown-loafers"))
    @Test fun `all black is monochrome`() = assertEquals(Technique.MONOCHROME, tech("tee-black2@TOP", "trousers-black@BOTTOM", "black-derbies"))
    @Test fun `cobalt and orange is a colour block`() = assertEquals(Technique.COLOR_BLOCK, tech("tee-cobalt@TOP", "chinos-orange@BOTTOM", "white-sneakers"))
    @Test fun `shoes echoing the top make a sandwich`() = assertEquals(Technique.SANDWICH, tech("tee-cobalt@TOP", "chinos-stone", "sneakers-cobalt@SHOES"))
    @Test fun `one red tee with neutrals is a statement`() = assertEquals(Technique.STATEMENT, tech("tee-red@TOP", "chinos-stone", "white-sneakers"))
    @Test fun `a blazer over a tee and jeans is high-low`() = assertEquals(Technique.HIGH_LOW, tech("tee-white", "blazer-navy@OUTER", "jeans-black", "white-sneakers"))

    @Test fun `a plain navy-grey outfit uses no technique`() = assertNull(tech("tee-navy", "wool-trousers-grey", "black-derbies"))

    @Test fun `a technique adds to the score and names itself`() {
        val s = Scorer.score(look(c, "tee-cobalt@TOP", "chinos-orange@BOTTOM", "white-sneakers"), c)
        assertEquals(Technique.COLOR_BLOCK, s.technique)
        assertTrue(s.why.any { it.id == "STYLE" && it.args["id"] == "COLOR_BLOCK" })
    }

    @Test fun `a deck mixes techniques and colours`() {
        val deck = OutfitEngine().generate(c, Fixtures.wardrobe + extra).cards
        assertTrue(deck.mapNotNull { it.technique }.toSet().size >= 3, "${deck.map { it.technique }}")
        val uppers = deck.mapNotNull { look(c, *it.pieces.map { p -> "${p.itemId}@${p.slot}" }.toTypedArray()).upperDominant?.family }
        assertTrue(uppers.groupingBy { it }.eachCount().values.max() <= deck.size / 3 + 1, "$uppers")
    }

    @Test fun `soft layers need a relaxed piece and three visible layers`() {
        val relaxed = item("overshirt-olive").copy(id = "relaxed-overshirt", silhouette = Silhouette.OVERSIZED,
            colors = listOf(KMeans.itemColor("#C8C8C8")))
        val cc = c.copy(items = c.items + (relaxed.id to relaxed))
        val t = StylistRules.technique(look(cc, "oxford-white", "relaxed-overshirt@MID", "trench@OUTER", "chinos-stone", "white-sneakers"), cc)
        assertTrue(t != null, "got $t")
    }
}
