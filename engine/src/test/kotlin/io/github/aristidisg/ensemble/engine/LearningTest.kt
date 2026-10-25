package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.GoldenCasesTest.Companion.event
import io.github.aristidisg.ensemble.engine.learning.Features
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.learning.PreferenceStats
import io.github.aristidisg.ensemble.model.CheckInPayload
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Thermal
import io.github.aristidisg.ensemble.model.WhyNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningTest {
    private val ctx = Fixtures.ctx("casual")
    private val feats = Features.extract(Fixtures.look(ctx, "tee-navy", "chinos-stone", "brown-loafers"), ctx)

    @Test fun `feature keys follow the spec formats`() {
        assertTrue(feats.any { it == "item:tee-navy" })
        assertTrue(feats.any { it.startsWith("pair:") && "chinos-stone" in it })
        assertTrue(feats.any { it.startsWith("fam:") })
        assertTrue(feats.any { it.startsWith("harmony:") })
        assertTrue(feats.any { it.startsWith("contrast:") })
        assertTrue(feats.any { it.startsWith("style:") })
        assertTrue(feats.any { it.startsWith("sil:") })
        assertTrue(feats.any { it == "shoe:LOAFERS|CHINOS" })
        assertTrue(feats.any { it.startsWith("formality:casual:") })
        assertTrue(feats.any { it.startsWith("layers:") })
        assertTrue(feats.any { it.startsWith("material:") })
    }

    @Test fun `identical event lists give identical stats`() {
        val events = (1..20).map { event(it, if (it % 3 == 0) FeedbackType.SWIPE_NO else FeedbackType.SWIPE_YES, feats, "casual") }
        assertEquals(Learning.rebuild(events), Learning.rebuild(events))
    }

    @Test fun `rebuild equals incremental`() {
        val events = (1..15).map { i ->
            event(i, listOf(FeedbackType.SWIPE_YES, FeedbackType.SAVE, FeedbackType.SWIPE_NO)[i % 3], feats, if (i % 2 == 0) "casual" else "office",
                if (i % 5 == 0) listOf(WhyNot.TOO_FORMAL) else emptyList()).copy(epochDay = TODAY + i, epochMillis = (TODAY + i) * 86_400_000L)
        }
        val incremental = events.fold(PreferenceStats.EMPTY) { s, e -> Learning.apply(e, s) }
        assertEquals(incremental, Learning.rebuild(events.shuffled(java.util.Random(3))))
    }

    @Test fun `decay halves a weight at the half-life`() {
        assertEquals(0.5, Learning.decay(120.0, 120.0), 1e-12)
        assertEquals(1.0, Learning.decay(0.0, 120.0), 1e-12)
    }

    @Test fun `a reason touches only its features`() {
        val s = Learning.rebuild(listOf(event(1, FeedbackType.SWIPE_NO, feats, "casual", listOf(WhyNot.FIT))))
        val touched = s.stats.values.map { it.featureKey }.toSet()
        assertTrue(touched.isNotEmpty())
        assertTrue(touched.all { it.startsWith("sil:") || it.startsWith("shoe:") }, "$touched")
    }

    @Test fun `no reason splits toward combinations`() {
        val s = Learning.rebuild(listOf(event(1, FeedbackType.SWIPE_NO, feats, "casual")))
        val g = s.stats.values.filter { it.contextKey == "GLOBAL" }.associate { it.featureKey to it.neg }
        assertEquals(0.15, g.getValue("item:tee-navy"), 1e-9)
        assertEquals(0.6, g.entries.first { it.key.startsWith("pair:") }.value, 1e-9)
        assertEquals(0.3, g.entries.first { it.key.startsWith("harmony:") }.value, 1e-9)
    }

    @Test fun `scenarios stay isolated`() {
        val s = Learning.rebuild((1..6).map { event(it, FeedbackType.SWIPE_NO, feats, "night_out", listOf(WhyNot.TOO_CASUAL)) })
        assertTrue((s.formalityOffset["night_out"] ?: 0.0) > 0)
        assertNull(s.formalityOffset["office"])
    }

    @Test fun `the ramp follows its formula`() {
        assertEquals(0.02, PreferenceSnapshot.ramp(0.0, 0.18), 1e-12)
        assertEquals(0.10, PreferenceSnapshot.ramp(30.0, 0.18), 1e-12)
        assertEquals(0.18, PreferenceSnapshot.ramp(60.0, 0.18), 1e-12)
        assertEquals(0.30, PreferenceSnapshot.ramp(600.0, 0.30), 1e-12)
    }

    @Test fun `value is lift times confidence`() {
        assertEquals(0.0, PreferenceSnapshot.value(0.0, 0.0), 1e-12)
        val v = PreferenceSnapshot.value(3.0, 0.0)
        assertEquals(2 * (4.0 / 5 - 0.5) * (3.0 / 6), v, 1e-12)
    }

    @Test fun `check-in moves the thermal offset and is bounded`() {
        val cold = (1..20).map { event(it, FeedbackType.CHECK_IN, feats, "casual").copy(checkIn = CheckInPayload(4, Thermal.TOO_COLD, 4)) }
        assertEquals(5.0, Learning.rebuild(cold).thermalOffset)
    }

    @Test fun `not today hides for a day without learning`() {
        val s = Learning.rebuild(listOf(event(1, FeedbackType.SWIPE_NO, feats, "casual", listOf(WhyNot.NOT_TODAY))))
        assertTrue(s.stats.isEmpty())
        val snap = PreferenceSnapshot.of(s, "casual", Season.AUTUMN, TODAY)
        assertTrue(snap.isOutfitHidden("sig1", TODAY))
        assertTrue(!snap.isOutfitHidden("sig1", TODAY + 2))
    }

    @Test fun `not this piece hides the item for three days in that scenario`() {
        val e = event(1, FeedbackType.SWIPE_NO, feats, "casual", listOf(WhyNot.NOT_THIS_PIECE)).copy(targetItemId = "tee-navy")
        val snap = PreferenceSnapshot.of(Learning.rebuild(listOf(e)), "casual", Season.AUTUMN, TODAY)
        assertTrue(snap.isItemHidden("tee-navy", "casual", TODAY + 3))
        assertTrue(!snap.isItemHidden("tee-navy", "casual", TODAY + 4))
        assertTrue(!snap.isItemHidden("tee-navy", "office", TODAY))
    }

    @Test fun `piece thumbs stay in the scenario`() {
        val pairs = Features.pairsOf("tee-navy", feats)
        val e = event(1, FeedbackType.PIECE_UP, feats, "casual").copy(targetItemId = "tee-navy", targetFeatures = pairs)
        val s = Learning.rebuild(listOf(e))
        assertTrue(s.stats.values.all { it.contextKey == "SCN:casual" })
        assertEquals(1.5, s.stats.values.first { it.featureKey == "item:tee-navy" }.pos, 1e-9)
    }
}
