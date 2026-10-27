package ch.hygro.padelscore.model

data class MatchHistoryEntry(
    val matchId: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val opponentSets: Int,
    val ourSets: Int,
    val completedSets: List<SetResult>,
    val opponentMatchTieBreakPoints: Int,
    val ourMatchTieBreakPoints: Int,
    val configuration: MatchConfiguration,
    val totalPointsPlayed: Int,
    val pointHistory: List<GamePoint> = emptyList(),
    val totalGamesPlayed: Int,
    val averageHeartRate: Int? = null,
    val maximumHeartRate: Int? = null,
    val steps: Long? = null,
    val stoppedState: MatchState? = null
) {
    val weWon: Boolean get() = ourSets > opponentSets
}
