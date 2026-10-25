package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.Fixtures.TODAY
import io.github.aristidisg.ensemble.engine.GoldenCasesTest.Companion.event
import io.github.aristidisg.ensemble.engine.learning.Features
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.WhyNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vogue stylist swipes train an editorial taste of their own; the everyday deck never sees it. */
class EditorialTasteTest {
    private val ctx = Fixtures.ctx("casual")
    private val feats = Features.extract(Fixtures.look(ctx, "tee-navy", "jeans-black", "white-sneakers"), ctx)
    private fun editorial(i: Int, type: FeedbackType, reasons: List<WhyNot> = emptyList()) =
        event(i, type, feats, "casual", reasons).let { it.copy(context = it.context.copy(editorial = true)) }

    @Test fun `editorial likes reach the vogue deck only`() {
        val stats = (1..6).fold(io.github.aristidisg.ensemble.engine.learning.PreferenceStats.EMPTY) { s, i -> Learning.apply(editorial(i, FeedbackType.SWIPE_YES), s) }
        assertTrue(stats.stats.values.all { it.contextKey == Learning.EDITORIAL })
        assertEquals(0.0, stats.eventWeight)
        assertTrue(stats.editorialWeight > 0)
        val everyday = PreferenceSnapshot.of(stats, "casual", Season.AUTUMN, TODAY)
        val vogue = PreferenceSnapshot.of(stats, "casual", Season.AUTUMN, TODAY, editorial = true)
        assertNull(everyday.value(feats.first()))
        assertTrue((vogue.value(feats.first())?.value ?: 0.0) > 0.0)
        assertTrue(vogue.learnedScore(feats) != null)
    }

    @Test fun `everyday likes still count a little in the vogue deck`() {
        val stats = (1..6).fold(io.github.aristidisg.ensemble.engine.learning.PreferenceStats.EMPTY) { s, i -> Learning.apply(event(i, FeedbackType.SWIPE_YES, feats, "casual"), s) }
        assertTrue((PreferenceSnapshot.of(stats, "casual", Season.AUTUMN, TODAY, editorial = true).value(feats.first())?.value ?: 0.0) > 0.0)
    }

    @Test fun `a vogue 'too cold' or 'too loud' leaves everyday warmth and boldness alone`() {
        val e = Learning.apply(editorial(1, FeedbackType.SWIPE_NO, listOf(WhyNot.TOO_COLD, WhyNot.TOO_LOUD)), io.github.aristidisg.ensemble.engine.learning.PreferenceStats.EMPTY)
        assertEquals(0.0, e.thermalOffset)
        assertEquals(0.0, e.boldnessDelta)
        val n = Learning.apply(event(1, FeedbackType.SWIPE_NO, feats, "casual", listOf(WhyNot.TOO_COLD, WhyNot.TOO_LOUD)), io.github.aristidisg.ensemble.engine.learning.PreferenceStats.EMPTY)
        assertTrue(n.thermalOffset > 0 && n.boldnessDelta < 0)
    }

    @Test fun `rebuild equals incremental with editorial events mixed in`() {
        val events = (1..12).map { i -> if (i % 2 == 0) editorial(i, FeedbackType.SWIPE_YES) else event(i, FeedbackType.SWIPE_NO, feats, "casual") }
        val inc = events.fold(io.github.aristidisg.ensemble.engine.learning.PreferenceStats.EMPTY) { s, e -> Learning.apply(e, s) }
        assertEquals(inc, Learning.rebuild(events))
    }
}
