package ch.hygro.padelscore.model

data class SetResult(
    val opponentGames: Int,
    val ourGames: Int,
    val wasTieBreak: Boolean = false,
    val opponentTieBreakPoints: Int = 0,
    val ourTieBreakPoints: Int = 0
)

data class MatchState(
    val opponentPoints: Int = 0,
    val ourPoints: Int = 0,

    val opponentGames: Int = 0,
    val ourGames: Int = 0,

    val opponentSets: Int = 0,
    val ourSets: Int = 0,

    val isTieBreak: Boolean = false,
    val opponentTieBreakPoints: Int = 0,
    val ourTieBreakPoints: Int = 0,

    val opponentMatchTieBreakPoints: Int = 0,
    val ourMatchTieBreakPoints: Int = 0,

    val configuration: MatchConfiguration = MatchConfiguration(),
    val phase: MatchPhase = MatchPhase.NORMAL_GAME,

    val advantageTeam: Team? = null,
    val deuceReturnCount: Int = 0,
    val isGoldenPointActive: Boolean = false,
    val isStarPointActive: Boolean = false,

    val servingTeam: Team = configuration.firstServingTeam,
    val tieBreakFirstServingTeam: Team? = null,

    val completedSets: List<SetResult> = emptyList(),

    val totalPointsPlayed: Int = 0,
    val totalGamesPlayed: Int = 0,

    val startedAtEpochMillis: Long = 0L,
    val finishedAtEpochMillis: Long? = null
)
