package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.SetResult
import ch.hygro.padelscore.model.Team

object PadelScoreEngine {

    fun awardPoint(state: MatchState, team: Team): MatchState {
        if (isMatchFinished(state)) return state

        if (state.phase == MatchPhase.MATCH_TIE_BREAK) {
            return awardMatchTieBreakPoint(state, team)
        }

        if (state.isTieBreak) {
            return awardTieBreakPoint(state, team)
        }

        return when (state.configuration.scoringMode) {
            ScoringMode.CLASSIC_ADVANTAGE -> awardClassicAdvantagePoint(state, team)
            ScoringMode.GOLDEN_POINT -> awardGoldenPoint(state, team)
            ScoringMode.STAR_POINT -> awardStarPoint(state, team)
        }
    }

    fun isMatchFinished(state: MatchState): Boolean {
        return state.phase == MatchPhase.MATCH_FINISHED
    }

    private fun awardClassicAdvantagePoint(state: MatchState, team: Team): MatchState {
        val countedState = state.copy(totalPointsPlayed = state.totalPointsPlayed + 1)
        val isDeuce = state.opponentPoints == 3 && state.ourPoints == 3

        if (isDeuce) {
            return when {
                state.advantageTeam == null -> countedState.copy(
                    advantageTeam = team,
                    isGoldenPointActive = false,
                    isStarPointActive = false
                )
                state.advantageTeam == team -> awardGame(countedState, team)
                else -> countedState.copy(
                    advantageTeam = null,
                    isGoldenPointActive = false,
                    isStarPointActive = false
                )
            }
        }

        return awardRegularPointBeforeDeuce(countedState, state, team)
    }

    private fun awardGoldenPoint(state: MatchState, team: Team): MatchState {
        val countedState = state.copy(totalPointsPlayed = state.totalPointsPlayed + 1)
        val isGoldenPoint = state.opponentPoints == 3 && state.ourPoints == 3

        if (isGoldenPoint) return awardGame(countedState, team)

        val newOpponentPoints = state.opponentPoints + if (team == Team.OPPONENT) 1 else 0
        val newOurPoints = state.ourPoints + if (team == Team.US) 1 else 0

        val winner = regularGameWinner(newOpponentPoints, newOurPoints)
        if (winner != null) return awardGame(countedState, winner)

        return countedState.copy(
            opponentPoints = newOpponentPoints,
            ourPoints = newOurPoints,
            advantageTeam = null,
            isGoldenPointActive = newOpponentPoints == 3 && newOurPoints == 3,
            isStarPointActive = false
        )
    }

    private fun awardStarPoint(state: MatchState, team: Team): MatchState {
        val countedState = state.copy(totalPointsPlayed = state.totalPointsPlayed + 1)
        val isDeuce = state.opponentPoints == 3 && state.ourPoints == 3

        if (isDeuce) {
            if (state.isStarPointActive || state.deuceReturnCount >= 2) {
                return awardGame(countedState, team)
            }

            return when {
                state.advantageTeam == null -> countedState.copy(
                    advantageTeam = team,
                    isGoldenPointActive = false,
                    isStarPointActive = false
                )
                state.advantageTeam == team -> awardGame(countedState, team)
                else -> {
                    val returns = state.deuceReturnCount + 1
                    countedState.copy(
                        advantageTeam = null,
                        deuceReturnCount = returns,
                        isGoldenPointActive = false,
                        isStarPointActive = returns >= 2
                    )
                }
            }
        }

        return awardRegularPointBeforeDeuce(countedState, state, team)
    }

    private fun awardRegularPointBeforeDeuce(
        countedState: MatchState,
        originalState: MatchState,
        team: Team
    ): MatchState {
        val newOpponentPoints = originalState.opponentPoints +
                if (team == Team.OPPONENT) 1 else 0
        val newOurPoints = originalState.ourPoints +
                if (team == Team.US) 1 else 0

        val winner = regularGameWinner(newOpponentPoints, newOurPoints)
        if (winner != null) return awardGame(countedState, winner)

        return countedState.copy(
            opponentPoints = newOpponentPoints.coerceAtMost(3),
            ourPoints = newOurPoints.coerceAtMost(3),
            advantageTeam = null,
            isGoldenPointActive = false,
            isStarPointActive = false
        )
    }

    private fun regularGameWinner(opponentPoints: Int, ourPoints: Int): Team? {
        return when {
            opponentPoints >= 4 && opponentPoints - ourPoints >= 2 -> Team.OPPONENT
            ourPoints >= 4 && ourPoints - opponentPoints >= 2 -> Team.US
            else -> null
        }
    }

    private fun awardGame(state: MatchState, winner: Team): MatchState {
        val opponentGames = state.opponentGames + if (winner == Team.OPPONENT) 1 else 0
        val ourGames = state.ourGames + if (winner == Team.US) 1 else 0
        val nextServer = state.servingTeam.other()
        val totalGames = state.totalGamesPlayed + 1
        val configuration = state.configuration

        if (
            configuration.tieBreakEnabled &&
            opponentGames == configuration.tieBreakAtGames &&
            ourGames == configuration.tieBreakAtGames
        ) {
            return state.copy(
                opponentPoints = 0,
                ourPoints = 0,
                opponentGames = opponentGames,
                ourGames = ourGames,
                isTieBreak = true,
                opponentTieBreakPoints = 0,
                ourTieBreakPoints = 0,
                phase = MatchPhase.TIE_BREAK,
                advantageTeam = null,
                deuceReturnCount = 0,
                isGoldenPointActive = false,
                isStarPointActive = false,
                servingTeam = nextServer,
                tieBreakFirstServingTeam = nextServer,
                totalGamesPlayed = totalGames
            )
        }

        val opponentWonSet =
            opponentGames >= configuration.gamesRequiredToWinSet &&
                    opponentGames - ourGames >= 2
        val weWonSet =
            ourGames >= configuration.gamesRequiredToWinSet &&
                    ourGames - opponentGames >= 2

        if (opponentWonSet || weWonSet) {
            return concludeSet(
                state = state,
                winner = if (opponentWonSet) Team.OPPONENT else Team.US,
                result = SetResult(opponentGames, ourGames),
                nextServingTeam = nextServer,
                totalGamesPlayed = totalGames
            )
        }

        return state.copy(
            opponentPoints = 0,
            ourPoints = 0,
            opponentGames = opponentGames,
            ourGames = ourGames,
            phase = MatchPhase.NORMAL_GAME,
            advantageTeam = null,
            deuceReturnCount = 0,
            isGoldenPointActive = false,
            isStarPointActive = false,
            servingTeam = nextServer,
            totalGamesPlayed = totalGames
        )
    }

    private fun awardTieBreakPoint(state: MatchState, team: Team): MatchState {
        val opponentPoints = state.opponentTieBreakPoints +
                if (team == Team.OPPONENT) 1 else 0
        val ourPoints = state.ourTieBreakPoints + if (team == Team.US) 1 else 0
        val target = state.configuration.tieBreakPointsToWin
        val winner = numericTieBreakWinner(opponentPoints, ourPoints, target)
        val totalPoints = state.totalPointsPlayed + 1

        if (winner == null) {
            val firstServer = state.tieBreakFirstServingTeam ?: state.servingTeam
            val pointsPlayed = opponentPoints + ourPoints

            return state.copy(
                opponentTieBreakPoints = opponentPoints,
                ourTieBreakPoints = ourPoints,
                servingTeam = ServeCalculator.servingTeamForNextTieBreakPoint(
                    firstServingTeam = firstServer,
                    pointsPlayed = pointsPlayed
                ),
                tieBreakFirstServingTeam = firstServer,
                totalPointsPlayed = totalPoints
            )
        }

        val result = SetResult(
            opponentGames = if (winner == Team.OPPONENT) {
                state.configuration.tieBreakAtGames + 1
            } else {
                state.configuration.tieBreakAtGames
            },
            ourGames = if (winner == Team.US) {
                state.configuration.tieBreakAtGames + 1
            } else {
                state.configuration.tieBreakAtGames
            },
            wasTieBreak = true,
            opponentTieBreakPoints = opponentPoints,
            ourTieBreakPoints = ourPoints
        )

        return concludeSet(
            state = state.copy(totalPointsPlayed = totalPoints),
            winner = winner,
            result = result,
            nextServingTeam = ServeCalculator.servingTeamForNextSetAfterTieBreak(
                state.tieBreakFirstServingTeam ?: state.servingTeam
            ),
            totalGamesPlayed = state.totalGamesPlayed
        )
    }

    private fun concludeSet(
        state: MatchState,
        winner: Team,
        result: SetResult,
        nextServingTeam: Team,
        totalGamesPlayed: Int
    ): MatchState {
        val opponentSets = state.opponentSets + if (winner == Team.OPPONENT) 1 else 0
        val ourSets = state.ourSets + if (winner == Team.US) 1 else 0
        val nextPhase = phaseAfterSet(state.configuration.matchFormat, opponentSets, ourSets)
        val finished = nextPhase == MatchPhase.MATCH_FINISHED

        return state.copy(
            opponentPoints = 0,
            ourPoints = 0,
            opponentGames = 0,
            ourGames = 0,
            opponentSets = opponentSets,
            ourSets = ourSets,
            isTieBreak = false,
            opponentTieBreakPoints = 0,
            ourTieBreakPoints = 0,
            opponentMatchTieBreakPoints = 0,
            ourMatchTieBreakPoints = 0,
            phase = nextPhase,
            advantageTeam = null,
            deuceReturnCount = 0,
            isGoldenPointActive = false,
            isStarPointActive = false,
            servingTeam = nextServingTeam,
            tieBreakFirstServingTeam = if (nextPhase == MatchPhase.MATCH_TIE_BREAK) {
                nextServingTeam
            } else {
                null
            },
            completedSets = state.completedSets + result,
            totalGamesPlayed = totalGamesPlayed,
            finishedAtEpochMillis = if (finished) System.currentTimeMillis() else null
        )
    }

    private fun phaseAfterSet(
        format: MatchFormat,
        opponentSets: Int,
        ourSets: Int
    ): MatchPhase {
        return when (format) {
            MatchFormat.ONE_SET -> MatchPhase.MATCH_FINISHED
            MatchFormat.BEST_OF_THREE -> {
                if (opponentSets >= 2 || ourSets >= 2) {
                    MatchPhase.MATCH_FINISHED
                } else {
                    MatchPhase.NORMAL_GAME
                }
            }
            MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK -> when {
                opponentSets >= 2 || ourSets >= 2 -> MatchPhase.MATCH_FINISHED
                opponentSets == 1 && ourSets == 1 -> MatchPhase.MATCH_TIE_BREAK
                else -> MatchPhase.NORMAL_GAME
            }
        }
    }

    private fun awardMatchTieBreakPoint(state: MatchState, team: Team): MatchState {
        val opponentPoints = state.opponentMatchTieBreakPoints +
                if (team == Team.OPPONENT) 1 else 0
        val ourPoints = state.ourMatchTieBreakPoints + if (team == Team.US) 1 else 0
        val target = state.configuration.matchTieBreakPointsToWin
        val winner = numericTieBreakWinner(opponentPoints, ourPoints, target)
        val totalPoints = state.totalPointsPlayed + 1

        if (winner == null) {
            val firstServer = state.tieBreakFirstServingTeam ?: state.servingTeam
            val pointsPlayed = opponentPoints + ourPoints

            return state.copy(
                opponentMatchTieBreakPoints = opponentPoints,
                ourMatchTieBreakPoints = ourPoints,
                servingTeam = ServeCalculator.servingTeamForNextTieBreakPoint(
                    firstServingTeam = firstServer,
                    pointsPlayed = pointsPlayed
                ),
                tieBreakFirstServingTeam = firstServer,
                totalPointsPlayed = totalPoints
            )
        }

        return state.copy(
            opponentSets = state.opponentSets + if (winner == Team.OPPONENT) 1 else 0,
            ourSets = state.ourSets + if (winner == Team.US) 1 else 0,
            opponentMatchTieBreakPoints = opponentPoints,
            ourMatchTieBreakPoints = ourPoints,
            phase = MatchPhase.MATCH_FINISHED,
            totalPointsPlayed = totalPoints,
            finishedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun numericTieBreakWinner(
        opponentPoints: Int,
        ourPoints: Int,
        target: Int
    ): Team? {
        return when {
            opponentPoints >= target && opponentPoints - ourPoints >= 2 -> Team.OPPONENT
            ourPoints >= target && ourPoints - opponentPoints >= 2 -> Team.US
            else -> null
        }
    }
}
