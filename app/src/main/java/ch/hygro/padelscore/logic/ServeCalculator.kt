package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.PlayerSlot
import ch.hygro.padelscore.model.ServiceCourt
import ch.hygro.padelscore.model.Team

data class ServiceAssignment(
    val player: PlayerSlot,
    val court: ServiceCourt
)

/**
 * Computes the concrete server, service court and court changes.
 */
object ServeCalculator {

    fun isDeuceCourtServe(pointsInCurrentGame: Int): Boolean {
        require(pointsInCurrentGame >= 0) {
            "pointsInCurrentGame must not be negative"
        }
        return pointsInCurrentGame % 2 == 0
    }

    fun serviceAssignment(state: MatchState): ServiceAssignment {
        require(state.servingPlayer.team == state.servingTeam) {
            "servingPlayer must belong to servingTeam"
        }

        val court = state.selectedDecidingPointCourt ?: when {
            state.isGoldenPointActive || state.isStarPointActive -> {
                ServiceCourt.RECEIVER_CHOICE
            }
            isDeuceCourtServe(state.pointsInCurrentGame) -> {
                ServiceCourt.DEUCE
            }
            else -> {
                ServiceCourt.ADVANTAGE
            }
        }

        return ServiceAssignment(
            player = state.servingPlayer,
            court = court
        )
    }

    fun servingPlayerForNextTieBreakPoint(
        firstServingPlayer: PlayerSlot,
        firstServerForUs: PlayerSlot,
        firstServerForOpponent: PlayerSlot,
        pointsPlayed: Int
    ): PlayerSlot {
        require(pointsPlayed >= 0) {
            "pointsPlayed must not be negative"
        }
        require(firstServerForUs.team == Team.US) {
            "firstServerForUs must belong to Team.US"
        }
        require(firstServerForOpponent.team == Team.OPPONENT) {
            "firstServerForOpponent must belong to Team.OPPONENT"
        }

        if (pointsPlayed == 0) {
            return firstServingPlayer
        }

        val blockIndex = (pointsPlayed - 1) / 2
        val servingTeam = if (blockIndex % 2 == 0) {
            firstServingPlayer.team.other()
        } else {
            firstServingPlayer.team
        }
        val teamServiceTurn = if (servingTeam == firstServingPlayer.team) {
            (blockIndex + 1) / 2
        } else {
            blockIndex / 2
        }
        val firstServer = when (servingTeam) {
            Team.US -> firstServerForUs
            Team.OPPONENT -> firstServerForOpponent
        }

        return if (teamServiceTurn % 2 == 0) {
            firstServer
        } else {
            firstServer.teammate()
        }
    }

    fun isCourtChange(previousState: MatchState, newState: MatchState): Boolean {
        if (newState.phase == MatchPhase.MATCH_FINISHED) return false

        if (newState.isTieBreak || newState.phase == MatchPhase.MATCH_TIE_BREAK) {
            val tieBreakPoints = if (newState.phase == MatchPhase.MATCH_TIE_BREAK) {
                newState.opponentMatchTieBreakPoints + newState.ourMatchTieBreakPoints
            } else {
                newState.opponentTieBreakPoints + newState.ourTieBreakPoints
            }
            val previousTieBreakPoints =
                if (previousState.phase == MatchPhase.MATCH_TIE_BREAK) {
                    previousState.opponentMatchTieBreakPoints +
                        previousState.ourMatchTieBreakPoints
                } else {
                    previousState.opponentTieBreakPoints +
                        previousState.ourTieBreakPoints
                }

            if (
                tieBreakPoints > previousTieBreakPoints &&
                tieBreakPoints > 0 &&
                tieBreakPoints % 6 == 0
            ) {
                return true
            }
        }

        if (newState.totalGamesPlayed > previousState.totalGamesPlayed) {
            if (newState.completedSets.size > previousState.completedSets.size) {
                val lastSet = newState.completedSets.last()
                val totalGamesInLastSet =
                    lastSet.opponentGames + lastSet.ourGames
                return totalGamesInLastSet % 2 != 0
            }

            val gamesInSet = newState.opponentGames + newState.ourGames
            return gamesInSet % 2 != 0
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