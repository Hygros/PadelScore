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
}
