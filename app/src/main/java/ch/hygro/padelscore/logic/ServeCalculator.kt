package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.Team

object ServeCalculator {

    fun isDeuceCourtServe(pointsInCurrentGame: Int): Boolean {
        return pointsInCurrentGame % 2 == 0
    }

    fun isCourtChange(previousState: MatchState, newState: MatchState): Boolean {
        if (newState.phase == MatchPhase.MATCH_FINISHED) return false

        // Check tie-break point total change (change sides every 6 points in tie-break)
        if (newState.isTieBreak || newState.phase == MatchPhase.MATCH_TIE_BREAK) {
            val tbPoints = if (newState.phase == MatchPhase.MATCH_TIE_BREAK) {
                newState.opponentMatchTieBreakPoints + newState.ourMatchTieBreakPoints
            } else {
                newState.opponentTieBreakPoints + newState.ourTieBreakPoints
            }
            val prevTbPoints = if (previousState.phase == MatchPhase.MATCH_TIE_BREAK) {
                previousState.opponentMatchTieBreakPoints + previousState.ourMatchTieBreakPoints
            } else {
                previousState.opponentTieBreakPoints + previousState.ourTieBreakPoints
            }

            if (tbPoints > prevTbPoints && tbPoints > 0 && tbPoints % 6 == 0) {
                return true
            }
        }

        // Check if a game was completed
        if (newState.totalGamesPlayed > previousState.totalGamesPlayed) {
            // Check if a set was completed
            if (newState.completedSets.size > previousState.completedSets.size) {
                val lastSet = newState.completedSets.last()
                val totalGamesInLastSet = lastSet.opponentGames + lastSet.ourGames
                return totalGamesInLastSet % 2 != 0
            } else {
                // Set continues: total games in current set
                val gamesInSet = newState.opponentGames + newState.ourGames
                return gamesInSet % 2 != 0
            }
        }

        return false
    }

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
