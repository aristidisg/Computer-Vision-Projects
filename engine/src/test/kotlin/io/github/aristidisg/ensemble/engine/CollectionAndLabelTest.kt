package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.ctx
import io.github.aristidisg.ensemble.engine.Fixtures.item
import io.github.aristidisg.ensemble.engine.Fixtures.look
import io.github.aristidisg.ensemble.engine.Fixtures.pieces
import io.github.aristidisg.ensemble.engine.GoldenCasesTest.Companion.event
import io.github.aristidisg.ensemble.engine.learning.Features
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.learning.PreferenceStats
import io.github.aristidisg.ensemble.engine.rules.CollectionRules
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionTest {
    private val saved = pieces("oxford-white", "chinos-navy", "brown-loafers")
    private val on = ctx("casual").copy(collection = listOf(saved))
    private val off = on.copy(settings = Settings(savedOutfitsInfluence = false))

    @Test fun `outfits like a saved one get a boost`() {
        // Same look (white shirt, navy trousers, brown shoes) with a different shirt.
        val similar = look(on, "dress-shirt-white", "chinos-navy", "brown-loafers")
        val unrelated = look(on, "graphic-tee", "joggers-grey", "running-shoes")
        val s = CollectionRules.similarity(similar, on)
        assertTrue(s > CollectionRules.similarity(unrelated, on))
        assertTrue(s >= 0.5)
        val boosted = Scorer.score(similar, on)
        assertTrue(boosted.total > Scorer.score(similar, ctx("casual")).total)
        assertTrue(boosted.results.any { it.key == Explain.LIKE_SAVED })
    }

    @Test fun `the saved outfit itself is not boosted`() {
        assertEquals(0.0, CollectionRules.similarity(look(on, "oxford-white", "chinos-navy", "brown-loafers"), on))
    }

    @Test fun `switching influence off removes the boost`() {
        val l = look(off, "dress-shirt-white", "chinos-navy", "brown-loafers")
        assertNull(CollectionRules.influence(l, off))
        assertEquals(Scorer.score(l, ctx("casual")).total, Scorer.score(l, off).total, 1e-12)
    }

    @Test fun `saves stop teaching taste when influence is off`() {
        val c = ctx("casual")
        val feats = Features.extract(look(c, "tee-navy", "chinos-stone", "brown-loafers"), c)
        val events = listOf(event(1, FeedbackType.SAVE, feats, "casual"), event(2, FeedbackType.COMPOSED, feats, "casual"))
        assertTrue(Learning.rebuild(events).stats.isNotEmpty())
        val offCfg = LearningConfig.of(Settings(savedOutfitsInfluence = false))
        assertEquals(PreferenceStats.EMPTY.stats, Learning.rebuild(events, offCfg).stats)
        // Swipes still teach.
        assertTrue(Learning.rebuild(listOf(event(3, FeedbackType.SWIPE_YES, feats, "casual")), offCfg).stats.isNotEmpty())
    }
}

class LabelCheckTest {
    @Test fun `the demo wardrobe is labeled correctly`() {
        assertEquals(emptyList(), LabelCheck.check(Fixtures.wardrobe).map { it.item.id })
    }

    @Test fun `a typed name that says jacket on a mid layer is flagged`() {
        val i = item("merino-navy").copy(name = "Navy wool jacket")
        val s = LabelCheck.suggestion(i)!!
        assertEquals(LabelIssue.NAME, s.issue)
        assertEquals(CategoryGroup.OUTERWEAR, s.target)
        val bomber = LabelCheck.suggestion(item("merino-navy").copy(name = "Green bomber"))!!
        assertEquals(Subcategory.BOMBER, bomber.suggested)
    }

    @Test fun `greek names are understood, with or without accents`() {
        assertEquals(Subcategory.CARDIGAN, LabelCheck.suggestion(item("denim-jacket").copy(name = "Μπλε Ζακέτα"))!!.suggested)
        assertEquals(Subcategory.WOOL_OVERCOAT, LabelCheck.suggestion(item("merino-navy").copy(name = "μαύρο παλτο"))!!.suggested)
        assertEquals(CategoryGroup.OUTERWEAR, LabelCheck.suggestion(item("merino-navy").copy(name = "Μπουφάν"))!!.target)
        assertNull(LabelCheck.hintFromName("ζακετάκι"))
    }

    @Test fun `a cardigan filed as outerwear is flagged`() {
        val i = item("denim-jacket").copy(name = "Grey cardigan")
        assertEquals(Subcategory.CARDIGAN, LabelCheck.suggestion(i)!!.suggested)
        val knit = ItemFactory.recategorize(item("merino-navy"), Subcategory.CHORE_JACKET).copy(materials = listOf(Material.KNIT, Material.MERINO))
        assertEquals(LabelIssue.MATERIAL, LabelCheck.suggestion(knit)!!.issue)
        assertEquals(CategoryGroup.TOPS, LabelCheck.suggestion(knit)!!.target)
    }

    @Test fun `a leather mid layer, a photo hint and edited slots are flagged`() {
        val leather = item("chunky-knit").copy(materials = listOf(Material.LEATHER))
        assertEquals(CategoryGroup.OUTERWEAR, LabelCheck.suggestion(leather)!!.target)
        val photo = LabelCheck.suggestion(item("merino-grey"), Subcategory.PARKA)!!
        assertEquals(LabelIssue.PHOTO, photo.issue)
        // Untick "outer layer" on an overshirt: a choice, not a mistake.
        assertNull(LabelCheck.suggestion(item("overshirt-olive").copy(slots = listOf(Slot.MID))))
        val slots = LabelCheck.suggestion(item("trench").copy(slots = listOf(Slot.MID)))!!
        assertEquals(LabelIssue.SLOTS, slots.issue)
        assertEquals(item("trench").slots, LabelCheck.fix(slots)!!.slots)
    }

    @Test fun `auto names and same-group hints are not flagged`() {
        assertNull(LabelCheck.suggestion(item("merino-grey"), Subcategory.CARDIGAN))
        assertNull(LabelCheck.suggestion(item("overshirt-olive"), Subcategory.BOMBER))
        assertNull(LabelCheck.suggestion(item("overcoat-camel").copy(name = "My winter coat")))
    }

    @Test fun `recategorize keeps identity and switches defaults`() {
        val old = item("merino-navy").copy(name = "Navy wool jacket", status = io.github.aristidisg.ensemble.model.ItemStatus.LAUNDRY)
        val moved = LabelCheck.fix(LabelCheck.suggestion(old)!!, Subcategory.HARRINGTON)!!
        assertEquals(old.id, moved.id)
        assertEquals(old.colors, moved.colors)
        assertEquals(old.photos, moved.photos)
        assertEquals("Navy wool jacket", moved.name)
        assertEquals(old.status, moved.status)
        assertEquals(Subcategory.HARRINGTON, moved.subcategory)
        assertTrue(Slot.OUTER in moved.slots && Slot.MID !in moved.slots)
    }
}
