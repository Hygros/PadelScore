package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.Team

object ServeCalculator {

    fun servingTeamForNextTieBreakPoint(
        firstServingTeam: Team,
        pointsPlayed: Int
    ): Team {
        require(pointsPlayed >= 0) {
            "pointsPlayed must not be negative"
        }

        if (pointsPlayed == 0) {
            return firstServingTeam
        }

        val completedTwoPointBlocks = (pointsPlayed - 1) / 2
        val oppositeTeamServes = completedTwoPointBlocks % 2 == 0

        return if (oppositeTeamServes) {
            firstServingTeam.other()
        } else {
            firstServingTeam
        }
    }

    fun servingTeamForNextSetAfterTieBreak(
        firstTieBreakServingTeam: Team
    ): Team {
        return firstTieBreakServingTeam.other()
    }
}
