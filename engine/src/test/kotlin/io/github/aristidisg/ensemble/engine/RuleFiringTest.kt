package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.Fixtures.score
import io.github.aristidisg.ensemble.engine.Fixtures.weather
import io.github.aristidisg.ensemble.engine.rules.ColorPairRules
import io.github.aristidisg.ensemble.engine.rules.ColorRules
import io.github.aristidisg.ensemble.engine.rules.CustomRules
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.FormalityRules
import io.github.aristidisg.ensemble.engine.rules.HardConstraints
import io.github.aristidisg.ensemble.engine.rules.PaletteRules
import io.github.aristidisg.ensemble.engine.rules.RotationRules
import io.github.aristidisg.ensemble.engine.rules.ShoeAccessoryRules
import io.github.aristidisg.ensemble.engine.rules.StyleRules
import io.github.aristidisg.ensemble.engine.rules.Violation
import io.github.aristidisg.ensemble.engine.rules.WeatherRules
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorSeason
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.ItemField
import io.github.aristidisg.ensemble.model.ItemPredicate
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.PersonalColor
import io.github.aristidisg.ensemble.model.RuleAction
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every rule fires and doesn't fire. */
class RuleFiringTest {
    private val mild = ctx("casual", weather(15.0, 19.0))

    @Test fun `C2 accent count`() {
        val calm = ColorRules.c2AccentCount(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild)
        assertEquals(1.0, calm.score)
        val busy = ColorRules.c2AccentCount(look(mild, "floral-shirt", "chinos-green", "sweater-orange@MID", "white-sneakers"), mild)
        assertTrue(busy.score < 1.0 && busy.key == Explain.TOO_MANY_ACCENTS)
    }

    @Test fun `C3 colour fight`() {
        val fight = ColorRules.c3Proportion(look(mild, "red-polo", "chinos-green", "white-sneakers"), mild)
        assertEquals(Explain.COLOR_FIGHT, fight.key)
        val ok = ColorRules.c3Proportion(look(mild, "tee-white", "chinos-navy", "white-sneakers"), mild)
        assertTrue(ok.key != Explain.COLOR_FIGHT)
    }

    @Test fun `C4 value contrast and grey mush`() {
        val good = ColorRules.c4ValueContrast(look(mild, "merino-grey@TOP", "jeans-dark", "white-sneakers"), mild)!!
        assertTrue(good.score >= 0.8)
        val mush = ColorRules.c4ValueContrast(look(mild, "tee-grey", "joggers-grey", "running-shoes"), mild)!!
        assertEquals(Explain.GREY_MUSH, mush.key)
    }

    @Test fun `C5 chroma balance`() {
        assertTrue(ColorRules.c5ChromaBalance(look(mild, "red-polo", "chinos-green", "white-sneakers"), mild).score < 1.0)
        assertEquals(1.0, ColorRules.c5ChromaBalance(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild).score)
    }

    @Test fun `C6 temperature`() {
        assertNull(ColorRules.c6Temperature(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild))
        assertNotNull(ColorRules.c6Temperature(look(mild, "cobalt-tee", "sweater-orange@MID", "chinos-stone", "white-sneakers"), mild))
    }

    @Test fun `C7 classic pairing table`() {
        val classic = ColorPairRules.c7ClassicPairs(look(mild, "oxford-white", "chinos-navy", "brown-loafers"), mild)!!
        assertTrue(classic.score >= 0.9)
        val contested = ColorPairRules.c7ClassicPairs(look(mild, "tee-navy", "jeans-black", "black-derbies"), mild)!!
        assertEquals(Explain.UNUSUAL_PAIR, contested.key)
    }

    @Test fun `C8 denim on denim`() {
        assertNull(ColorPairRules.c8DenimOnDenim(look(mild, "tee-white", "jeans-dark", "white-sneakers")))
        val dd = ColorPairRules.c8DenimOnDenim(look(mild, "tee-white", "denim-jacket", "jeans-dark", "white-sneakers"))!!
        assertTrue(dd.key == Explain.DENIM_OK || dd.key == Explain.DENIM_CLASH)
    }

    @Test fun `C9 near miss`() {
        assertNull(ColorPairRules.c9NearMiss(look(mild, "blazer-navy@OUTER", "oxford-white", "chinos-navy", "brown-loafers")))
        val nearNavy = item("chinos-navy").copy(id = "near-navy", colors = listOf(io.github.aristidisg.ensemble.engine.color.KMeans.itemColor("#28344F", family = ColorFamily.NAVY)))
        val c = ctx("casual", items = Fixtures.wardrobe + nearNavy)
        assertNotNull(ColorPairRules.c9NearMiss(look(c, "blazer-navy@OUTER", "oxford-white", "near-navy@BOTTOM", "brown-loafers")))
    }

    @Test fun `C10 leather and C11 metals`() {
        val office = ctx("office")
        assertEquals(Explain.LEATHER_MATCH, ColorPairRules.c10Leather(look(office, "oxford-white", "wool-trousers-grey", "brown-loafers", "belt-brown"), office)!!.key)
        assertEquals(Explain.LEATHER_CLASH, ColorPairRules.c10Leather(look(office, "oxford-white", "wool-trousers-grey", "black-derbies", "belt-brown"), office)!!.key)
        assertNull(ColorPairRules.c11Metals(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild))
        assertEquals(Explain.METALS_MIXED, ColorPairRules.c11Metals(look(mild, "tee-white", "jeans-dark", "white-sneakers", "watch-silver", "sunglasses"), mild)!!.key)
    }

    @Test fun `C12 personal colour only when switched on`() {
        val ids = arrayOf("sweater-orange@TOP", "jeans-dark", "white-sneakers")
        assertNull(PaletteRules.c12Personal(look(mild, *ids), mild))
        val on = ctx(settings = Settings(personalColor = PersonalColor(enabled = true, strength = 1.0, season = ColorSeason.COOL_SUMMER, avoid = setOf(ColorFamily.ORANGE))))
        val r = PaletteRules.c12Personal(look(on, *ids), on)!!
        assertEquals(Explain.PERSONAL_COLOR_AVOID, r.key)
        val full = score(on, *ids)
        assertTrue(full.results.any { it.id == "C12" })
        assertTrue(score(mild, *ids).results.none { it.id == "C12" })
    }

    @Test fun `C13 scenario palette and C15 wedding white`() {
        val funeral = ctx("funeral")
        val dark = PaletteRules.c13ScenarioPalette(look(funeral, "blazer-navy@OUTER", "dress-shirt-white", "wool-trousers-grey", "black-derbies"), funeral)!!
        val loud = PaletteRules.c13ScenarioPalette(look(funeral, "red-polo", "chinos-green", "black-derbies"), funeral)!!
        assertTrue(dark.score > loud.score)
        assertNull(PaletteRules.c13ScenarioPalette(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild))
        val wedding = ctx("wedding")
        assertEquals(1.0, PaletteRules.c15WeddingWhite(look(wedding, "blazer-navy@OUTER", "dress-shirt-white", "wool-trousers-grey", "brown-loafers"), wedding)!!.score)
    }

    @Test fun `C14 seasonal nudge`() {
        assertNotNull(PaletteRules.c14SeasonalNudge(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild))
    }

    @Test fun `P1-P4 patterns`() {
        assertEquals(Explain.PATTERN_ONE, StyleRules.patterns(look(mild, "floral-shirt", "chinos-stone", "white-sneakers"), mild).key)
        assertEquals(0.9, StyleRules.patterns(look(mild, "tee-white", "chinos-stone", "white-sneakers"), mild).score)
        assertEquals(1, StyleRules.loudness(item("chunky-knit")))
    }

    @Test fun `M1-M5 materials`() {
        val winter = ctx("casual", weather(2.0, 6.0), day = TODAY + 60)
        val m = StyleRules.materials(look(winter, "linen-shirt", "linen-trousers", "white-sneakers"), winter)
        assertTrue(m.any { it.key == Explain.FABRIC_OFF_SEASON })
        val ok = StyleRules.materials(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild)
        assertTrue(ok.none { it.key == Explain.FABRIC_OFF_SEASON })
    }

    @Test fun `F1-F3 formality`() {
        val office = ctx("office")
        assertEquals(Explain.FORMALITY_FIT, FormalityRules.f2Target(look(office, "oxford-white", "wool-trousers-grey", "brown-loafers"), office).key)
        assertEquals(Explain.FORMALITY_OFF, FormalityRules.f2Target(look(office, "graphic-tee", "joggers-grey", "running-shoes"), office).key)
        assertNull(FormalityRules.f3Conflicts(look(office, "oxford-white", "wool-trousers-grey", "brown-loafers"), office))
        assertNotNull(FormalityRules.f3Conflicts(look(office, "suit-jacket-cream@OUTER", "oxford-white", "wool-trousers-grey", "brown-loafers"), office))
        val hl = FormalityRules.f1Coherence(look(mild, "hoodie-grey@TOP", "suit-trousers-cream", "black-derbies"), mild)
        assertEquals(Explain.FORMALITY_MIXED, hl.key)
    }

    @Test fun `O1-O4 style`() {
        assertTrue(FormalityRules.o1Coherence(look(mild, "oxford-white", "chinos-stone", "brown-loafers"), mild)!!.score >= 0.85)
        assertNull(FormalityRules.o4Signature(look(mild, "tee-white", "jeans-dark", "white-sneakers")))
        val sig = item("tee-white").copy(flags = item("tee-white").flags.copy(signature = true))
        val c = ctx(items = Fixtures.wardrobe.map { if (it.id == sig.id) sig else it })
        assertNotNull(FormalityRules.o4Signature(look(c, "tee-white", "jeans-dark", "white-sneakers")))
        val uni = ctx("university")
        assertEquals(1.0, FormalityRules.o2Scenario(look(uni, "tee-white", "jeans-dark", "white-sneakers", "backpack-black"), uni)!!.score)
    }

    @Test fun `SH rules`() {
        val office = ctx("office")
        val r = ShoeAccessoryRules.shoes(look(office, "oxford-white", "wool-trousers-grey", "brown-loafers"), office)
        assertTrue(r.first { it.id == "SH2" }.score == 1.0)
        val bad = ShoeAccessoryRules.shoes(look(mild, "tee-white", "jeans-dark", "black-derbies"), mild)
        assertTrue(bad.first { it.id == "SH2" }.score < 1.0)
        val rain = ctx("casual", weather(12.0, 14.0, rain = 80.0))
        assertEquals(Explain.SHOE_WEATHER_BAD, ShoeAccessoryRules.shoes(look(rain, "tee-white", "jeans-dark", "canvas-sneakers"), rain).first { it.id == "SH3" }.key)
        assertTrue(ShoeAccessoryRules.shoes(look(mild, "tee-white", "chino-shorts", "white-sneakers"), mild).any { it.key == Explain.SOCKS_NO_SHOW || it.key == Explain.SOCKS_MATCH })
        val dark = ShoeAccessoryRules.shoes(look(office, "oxford-white", "chinos-navy", "brown-loafers"), office).firstOrNull { it.id == "SH6" }
        assertEquals(Explain.SHOE_DARKER, dark?.key)
    }

    @Test fun `AC rules`() {
        val office = ctx("office")
        val noBelt = ShoeAccessoryRules.accessories(look(office, "oxford-white", "wool-trousers-grey", "brown-loafers"), office)
        assertTrue(noBelt.any { it.key == Explain.BELT_MISSING })
        val joggers = ShoeAccessoryRules.accessories(look(mild, "tee-white", "joggers-grey", "running-shoes", "belt-black"), mild)
        assertTrue(joggers.any { it.key == Explain.BELT_WRONG })
        val cold = ctx("casual", weather(2.0, 6.0))
        assertEquals(1.0, ShoeAccessoryRules.accessories(look(cold, "tee-white", "jeans-dark", "white-sneakers", "beanie-grey", "scarf-camel"), cold).first { it.id == "AC5" }.score)
        val warm = ctx("casual", weather(22.0, 26.0))
        assertTrue(ShoeAccessoryRules.accessories(look(warm, "tee-white", "jeans-dark", "white-sneakers", "scarf-camel"), warm).first { it.id == "AC7" }.score < 0.5)
    }

    @Test fun `W rules`() {
        val cold = ctx("casual", weather(2.0, 5.0))
        assertEquals(Explain.TOO_COLD, WeatherRules.w1Warmth(look(cold, "tee-white", "jeans-dark", "white-sneakers"), cold).key)
        val warm = ctx("casual", weather(20.0, 23.0))
        assertEquals(Explain.WARMTH_OK, WeatherRules.w1Warmth(look(warm, "oxford-white", "jeans-dark", "white-sneakers"), warm).key)
        val swing = ctx("casual", weather(9.0, 19.0))
        assertEquals(Explain.WEATHER_LAYER, WeatherRules.w2Swing(look(swing, "tee-white", "overshirt-olive@MID", "jeans-dark", "white-sneakers"), swing)!!.key)
        assertNull(WeatherRules.w2Swing(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild))
        val rain = ctx("casual", weather(12.0, 14.0, rain = 80.0))
        assertEquals(Explain.RAIN, WeatherRules.w3Rain(look(rain, "tee-white", "rain-shell", "jeans-dark", "white-sneakers"), rain)!!.key)
        assertEquals(Explain.RAIN_UNPROTECTED, WeatherRules.w3Rain(look(rain, "tee-white", "jeans-dark", "white-sneakers"), rain)!!.key)
        val windy = ctx("casual", weather(12.0, 14.0, wind = 40.0))
        assertEquals(1.0, WeatherRules.w4Wind(look(windy, "tee-white", "rain-shell", "jeans-dark", "white-sneakers"), windy)!!.score)
        val sunny = ctx("casual", weather(22.0, 26.0, uv = 8.0))
        assertNotNull(WeatherRules.w5Sun(look(sunny, "tee-white", "chinos-stone", "white-sneakers", "sunglasses"), sunny))
        val hot = ctx("casual", weather(29.0, 32.0))
        assertTrue(WeatherRules.w6Heat(look(hot, "linen-shirt", "linen-trousers", "white-sneakers"), hot)!!.score >
            WeatherRules.w6Heat(look(hot, "tee-black", "jeans-black", "white-sneakers"), hot)!!.score)
        val snow = ctx("casual", weather(-3.0, 0.0).copy(snow = true))
        assertEquals(0.2, WeatherRules.w7Snow(look(snow, "tee-white", "jeans-dark", "white-sneakers"), snow)!!.score)
        assertEquals(1.03 * 0.835 + 0.161, WeatherRules.ensembleClo(listOf(item("tee-white").copy(warmthClo = 1.03))), 1e-9)
    }

    @Test fun `R rules`() {
        val worn = Fixtures.wear(TODAY - 2, "tee-white", "chinos-navy", "brown-loafers")
        val c = ctx(history = listOf(worn))
        assertEquals(Explain.ROTATION, RotationRules.r1ItemRotation(look(c, "tee-white", "jeans-dark", "white-sneakers"), c)!!.key)
        assertTrue(RotationRules.r1ItemRotation(look(mild, "tee-white", "jeans-dark", "white-sneakers"), mild)!!.score == 1.0)
        val nearDup = ctx(history = listOf(Fixtures.wear(TODAY - 2, "tee-white", "jeans-dark", "white-sneakers", "overshirt-olive@MID")))
        assertNotNull(RotationRules.r2NearDuplicate(look(nearDup, "tee-white", "jeans-dark", "white-sneakers"), nearDup))
        val laundry = item("tee-white").copy(laundry = item("tee-white").laundry.copy(wearsSinceWash = 1))
        val lc = ctx(items = Fixtures.wardrobe.map { if (it.id == laundry.id) laundry else it })
        assertNotNull(RotationRules.r7Laundry(look(lc, "tee-white", "jeans-dark", "white-sneakers")))
        assertNull(RotationRules.r7Laundry(look(mild, "tee-white", "jeans-dark", "white-sneakers")))
        val planned = mild.copy(plannedSoon = setOf("jeans-dark"))
        assertNotNull(RotationRules.r6PlanConflicts(look(planned, "tee-white", "jeans-dark", "white-sneakers"), planned))
        val fresh = mild.copy(recentDeckSignatures = setOf(look(mild, "tee-white", "jeans-dark", "white-sneakers").signature))
        assertNotNull(RotationRules.r4Freshness(look(fresh, "tee-white", "jeans-dark", "white-sneakers"), fresh))
        assertNotNull(RotationRules.r5Forgotten(look(ctx(day = TODAY + 40), "tee-white", "jeans-dark", "white-sneakers"), ctx(day = TODAY + 40)))
    }

    @Test fun `hard constraints`() {
        val c = ctx("office")
        assertEquals(Violation.SCENARIO, HardConstraints.itemViolation(item("graphic-tee"), Slot.TOP, c))
        assertNull(HardConstraints.itemViolation(item("oxford-white"), Slot.TOP, c))
        val l = item("jeans-dark").copy(status = ItemStatus.LAUNDRY)
        assertEquals(Violation.UNAVAILABLE, HardConstraints.itemViolation(l, Slot.BOTTOM, mild))
        assertEquals(Violation.SCENARIO, HardConstraints.itemViolation(item("polo-navy"), Slot.TOP, ctx("gym")))
        assertEquals(Violation.STRUCTURE, HardConstraints.outfitViolation(look(mild, "tee-white", "white-sneakers"), mild))
        assertEquals(Violation.LAYERING, HardConstraints.outfitViolation(look(mild, "tee-white", "chunky-knit@MID", "blazer-navy@OUTER", "jeans-dark", "white-sneakers"), mild))
        val home = ctx("home")
        assertNull(HardConstraints.outfitViolation(look(home, "tee-grey", "joggers-grey"), home))
    }

    @Test fun `custom rules`() {
        val never = CustomRule("r1", name = "Never brown shoes with black trousers",
            a = listOf(ItemPredicate(ItemField.SLOT, "SHOES"), ItemPredicate(ItemField.COLOR_FAMILY, "BROWN")),
            b = listOf(ItemPredicate(ItemField.SLOT, "BOTTOM"), ItemPredicate(ItemField.COLOR_FAMILY, "BLACK")),
            action = RuleAction.NEVER_TOGETHER)
        val c = mild.copy(customRules = listOf(never))
        assertTrue(CustomRules.hardViolation(look(c, "tee-white", "jeans-black", "brown-loafers"), c))
        assertTrue(!CustomRules.hardViolation(look(c, "tee-white", "jeans-dark", "brown-loafers"), c))
        val prefer = CustomRule("r2", name = "Prefer white sneakers above 18", a = listOf(ItemPredicate(ItemField.ITEM, "white-sneakers")),
            action = RuleAction.PREFER, weight = 0.5, scope = io.github.aristidisg.ensemble.model.RuleScope(minTemp = 18.0))
        val warm = ctx("casual", weather(18.0, 24.0)).copy(customRules = listOf(prefer))
        val with = score(warm, "tee-white", "jeans-dark", "white-sneakers")
        val without = score(warm.copy(customRules = emptyList()), "tee-white", "jeans-dark", "white-sneakers")
        assertTrue(with.total > without.total)
        val coldCtx = ctx("casual", weather(5.0, 9.0)).copy(customRules = listOf(prefer))
        assertTrue(CustomRules.soft(look(coldCtx, "tee-white", "jeans-dark", "white-sneakers"), coldCtx).isEmpty())
    }

    @Test fun `rule toggles remove a rule`() {
        val off = ctx("office", settings = Settings(ruleEnabled = mapOf("C10" to false)))
        val s = score(off, "oxford-white", "wool-trousers-grey", "black-derbies", "belt-brown")
        assertTrue(s.results.none { it.id == "C10" })
    }
}
