package io.github.aristidisg.ensemble

import io.github.aristidisg.ensemble.model.ColorSeason
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.ui.today.MinimumSet
import io.github.aristidisg.ensemble.ui.you.SeasonQuiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogicTest {
    @Test fun piecesRoundTripThroughTheRouteArgument() {
        val p = listOf(OutfitPiece("a-1", Slot.TOP), OutfitPiece("b@x", Slot.BOTTOM), OutfitPiece("c", Slot.SHOES))
        assertEquals(p, PiecesArg.decode(PiecesArg.encode(p)))
        assertTrue(PiecesArg.decode("").isEmpty())
    }

    @Test fun minimumSetNeedsTwoTopsTwoBottomsOneShoe() {
        assertFalse(MinimumSet(1, 2, 1).ok)
        assertTrue(MinimumSet(2, 2, 1).ok)
        assertEquals(1f, MinimumSet(5, 5, 5).progress)
    }

    @Test fun quizPicksTheNearestSeason() {
        val q = SeasonQuiz.questions
        val warmDeep = listOf(q[0].answers[1], q[1].answers[0], q[2].answers[1], q[3].answers[2], q[4].answers[2], q[5].answers[1], q[6].answers[1], q[7].answers[2])
        val (season, _) = SeasonQuiz.result(warmDeep)
        assertTrue(season in setOf(ColorSeason.DEEP_AUTUMN, ColorSeason.WARM_AUTUMN))
        val coolLight = listOf(q[0].answers[0], q[1].answers[1], q[2].answers[0], q[3].answers[0], q[4].answers[0], q[5].answers[0], q[6].answers[0], q[7].answers[1])
        assertTrue(SeasonQuiz.result(coolLight).first in setOf(ColorSeason.LIGHT_SUMMER, ColorSeason.COOL_SUMMER, ColorSeason.SOFT_SUMMER))
    }
}
