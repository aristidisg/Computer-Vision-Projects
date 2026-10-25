package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.generator.SuggestionRequest
import io.github.aristidisg.ensemble.engine.rules.CoverLine
import io.github.aristidisg.ensemble.engine.rules.CoverRules
import io.github.aristidisg.ensemble.engine.tables.ColorFormula
import io.github.aristidisg.ensemble.engine.tables.RuleBreak
import io.github.aristidisg.ensemble.model.Silhouette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vogue stylist: formulas, rule breaks, proportion, texture, third piece and issues. */
class CoverTest {
    private val c = Fixtures.ctx("casual", Fixtures.weather(15.0, 19.0))
    private fun look(vararg ids: String) = Fixtures.look(c, *ids)
    private fun lines(vararg ids: String) = CoverRules.read(look(*ids)).first.notes.map { it.line }

    @Test fun `navy, camel and white is a formula, all black is not`() {
        assertEquals(ColorFormula.NAVY_CAMEL_WHITE, CoverRules.formula(look("tee-white", "chinos-navy", "overcoat-camel@OUTER", "white-sneakers")))
        assertNull(CoverRules.formula(look("tee-black", "jeans-black", "black-derbies")))
        assertTrue(CoverLine.FORMULA in lines("tee-white", "chinos-navy", "overcoat-camel@OUTER", "white-sneakers"))
    }

    @Test fun `rule breaks are recognised, classic outfits break nothing`() {
        assertEquals(RuleBreak.NAVY_WITH_BLACK, CoverRules.ruleBreak(look("tee-navy", "jeans-black", "white-sneakers")))
        assertEquals(RuleBreak.DOUBLE_DENIM, CoverRules.ruleBreak(look("tee-white", "denim-jacket@OUTER", "jeans-dark", "white-sneakers")))
        assertEquals(RuleBreak.BROWN_WITH_BLACK, CoverRules.ruleBreak(look("tee-white", "jeans-black", "brown-loafers")))
        assertEquals(RuleBreak.SNEAKERS_WITH_TAILORING, CoverRules.ruleBreak(look("tee-white", "blazer-navy@OUTER", "chinos-stone", "white-sneakers")))
        assertNull(CoverRules.ruleBreak(look("tee-navy", "chinos-stone", "brown-loafers")))
        assertNull(CoverRules.ruleBreak(look("tee-white", "denim-jacket@OUTER", "chinos-stone", "white-sneakers")))
    }

    @Test fun `volume against a clean line scores, baggy on baggy costs`() {
        assertEquals(CoverLine.VOLUME_TOP to CoverRules.VOLUME_BONUS, CoverRules.proportion(look("hoodie-grey@TOP", "chinos-stone", "white-sneakers")))
        assertEquals(null to -CoverRules.BAGGY_PENALTY, CoverRules.proportion(look("hoodie-grey@TOP", "cargo-olive", "white-sneakers")))
        assertNull(CoverRules.proportion(look("tee-navy", "chinos-stone", "white-sneakers")))
        val items = Fixtures.wardrobe.map { if (it.id == "chinos-navy") it.copy(silhouette = Silhouette.SLIM) else it }
        val slim = Fixtures.ctx("casual", Fixtures.weather(15.0, 19.0), items = items)
        assertEquals(CoverLine.LONG_LINE, CoverRules.proportion(Fixtures.look(slim, "dress-shirt-white", "chinos-navy", "black-derbies"))?.first)
    }

    @Test fun `three textures make a texture line, one doesn't`() {
        assertEquals(3, CoverRules.textures(look("tee-white", "jeans-dark", "brown-loafers")).size)
        assertTrue(CoverLine.TEXTURE in lines("tee-white", "jeans-dark", "brown-loafers"))
        assertTrue(CoverLine.TEXTURE !in lines("tee-white", "chinos-stone"))
    }

    @Test fun `an open layer or a scarf is the third piece, a closed pullover is not`() {
        assertNotNull(CoverRules.thirdPiece(look("tee-white", "overshirt-olive@MID", "chinos-stone", "white-sneakers")))
        assertNotNull(CoverRules.thirdPiece(look("tee-white", "chinos-stone", "white-sneakers", "scarf-camel@NECK")))
        assertNull(CoverRules.thirdPiece(look("tee-white", "merino-navy@MID", "chinos-stone", "white-sneakers")))
    }

    @Test fun `a vogue deck runs one issue with at most one rule break`() {
        val v = c.copy(options = DeckOptions(vogue = true))
        val deck = OutfitEngine().generate(v, Fixtures.wardrobe, SuggestionRequest(deckSize = 6))
        val issue = assertNotNull(deck.issue)
        assertTrue(deck.cards.all { it.cover != null })
        assertTrue(deck.cards.count { it.cover?.ruleBreak != null } <= 1)
        val themed = deck.cards.filter { it.cover?.ruleBreak == null }
        assertTrue(themed.all { CoverRules.matches(issue, it) }, "issue $issue: ${themed.map { it.technique }}")
        assertTrue(OutfitEngine().generate(c, Fixtures.wardrobe, SuggestionRequest(deckSize = 6)).let { d -> d.issue == null && d.cards.all { it.cover == null } })
    }

    @Test fun `the issue changes with More`() {
        val v = c.copy(options = DeckOptions(vogue = true))
        val issues = (0..3).map { OutfitEngine().generate(v, Fixtures.wardrobe, SuggestionRequest(deckSize = 6, regenerateCount = it)).issue }
        assertNotEquals(1, issues.toSet().size, issues.toString())
    }
}
