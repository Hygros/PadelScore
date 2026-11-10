package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class ServeCalculatorTest {

    @Test
    fun beforeFirstTieBreakPoint_firstTeamServes() {
        assertEquals(
            Team.US,
            ServeCalculator.servingTeamForNextTieBreakPoint(
                firstServingTeam = Team.US,
                pointsPlayed = 0
            )
        )
    }

    @Test
    fun afterFirstPoint_otherTeamServesNextTwoPoints() {
        assertEquals(Team.OPPONENT, serverAfter(pointsPlayed = 1))
        assertEquals(Team.OPPONENT, serverAfter(pointsPlayed = 2))
    }

    @Test
    fun afterThreePoints_firstTeamServesNextTwoPoints() {
        assertEquals(Team.US, serverAfter(pointsPlayed = 3))
        assertEquals(Team.US, serverAfter(pointsPlayed = 4))
    }

    @Test
    fun afterFivePoints_otherTeamServesNextTwoPoints() {
        assertEquals(Team.OPPONENT, serverAfter(pointsPlayed = 5))
        assertEquals(Team.OPPONENT, serverAfter(pointsPlayed = 6))
    }

    @Test
    fun sequenceAlsoWorksWhenOpponentStartsTieBreak() {
        assertEquals(
            Team.OPPONENT,
            ServeCalculator.servingTeamForNextTieBreakPoint(
                firstServingTeam = Team.OPPONENT,
                pointsPlayed = 0
            )
        )
        assertEquals(
            Team.US,
            ServeCalculator.servingTeamForNextTieBreakPoint(
                firstServingTeam = Team.OPPONENT,
                pointsPlayed = 1
            )
        )
        assertEquals(
            Team.OPPONENT,
            ServeCalculator.servingTeamForNextTieBreakPoint(
                firstServingTeam = Team.OPPONENT,
                pointsPlayed = 3
            )
        )
    }

    @Test
    fun nextSetStartsWithTeamThatDidNotStartTieBreak() {
        assertEquals(
            Team.OPPONENT,
            ServeCalculator.servingTeamForNextSetAfterTieBreak(Team.US)
        )
        assertEquals(
            Team.US,
            ServeCalculator.servingTeamForNextSetAfterTieBreak(Team.OPPONENT)
        )
    }

    private fun serverAfter(pointsPlayed: Int): Team {
        return ServeCalculator.servingTeamForNextTieBreakPoint(
            firstServingTeam = Team.US,
            pointsPlayed = pointsPlayed
        )
    }

    @Test
    fun testDeuceCourtServe() {
        org.junit.Assert.assertTrue(ServeCalculator.isDeuceCourtServe(0))
        org.junit.Assert.assertFalse(ServeCalculator.isDeuceCourtServe(1))
        org.junit.Assert.assertTrue(ServeCalculator.isDeuceCourtServe(2))
        org.junit.Assert.assertFalse(ServeCalculator.isDeuceCourtServe(3))
    }

    @Test
    fun courtChangeTriggeredOnCompletedSevenSixSetTieBreakWhenMatchContinues() {
        val beforePoint = ch.hygro.padelscore.model.MatchState(
            opponentGames = 6,
            ourGames = 6,
            isTieBreak = true,
            phase = ch.hygro.padelscore.model.MatchPhase.TIE_BREAK,
            opponentTieBreakPoints = 4,
            ourTieBreakPoints = 6,
            totalGamesPlayed = 12
        )
        val afterPoint = PadelScoreEngine.awardPoint(beforePoint, Team.US)
        assertEquals(13, afterPoint.totalGamesPlayed)
        org.junit.Assert.assertTrue(ServeCalculator.isCourtChange(beforePoint, afterPoint))
    }

    @Test
    fun courtChangeSuppressedWhenTieBreakFinishesMatch() {
        val beforePoint = ch.hygro.padelscore.model.MatchState(
            ourSets = 1,
            opponentGames = 6,
            ourGames = 6,
            isTieBreak = true,
            phase = ch.hygro.padelscore.model.MatchPhase.TIE_BREAK,
            opponentTieBreakPoints = 4,
            ourTieBreakPoints = 6,
            totalGamesPlayed = 22
        )
        val afterPoint = PadelScoreEngine.awardPoint(beforePoint, Team.US)
        assertEquals(ch.hygro.padelscore.model.MatchPhase.MATCH_FINISHED, afterPoint.phase)
        org.junit.Assert.assertFalse(ServeCalculator.isCourtChange(beforePoint, afterPoint))
    }
}
