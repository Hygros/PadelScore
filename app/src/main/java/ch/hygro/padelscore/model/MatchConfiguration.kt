package ch.hygro.padelscore.model

data class MatchConfiguration(
    val scoringMode: ScoringMode = ScoringMode.STAR_POINT,
    val matchFormat: MatchFormat = MatchFormat.BEST_OF_THREE,
    val gamesRequiredToWinSet: Int = 6,
    val tieBreakEnabled: Boolean = true,
    val tieBreakAtGames: Int = 6,
    val tieBreakPointsToWin: Int = 7,
    val matchTieBreakPointsToWin: Int = 10,
    val firstServingTeam: Team = Team.US
) {
    init {
        require(gamesRequiredToWinSet >= 1) {
            "gamesRequiredToWinSet must be at least 1"
        }
        require(tieBreakAtGames >= gamesRequiredToWinSet) {
            "tieBreakAtGames must be at least gamesRequiredToWinSet"
        }
        require(tieBreakPointsToWin >= 2) {
            "tieBreakPointsToWin must be at least 2"
        }
        require(matchTieBreakPointsToWin >= 2) {
            "matchTieBreakPointsToWin must be at least 2"
        }
    }

    companion object {
        fun normalSet(
            scoringMode: ScoringMode = ScoringMode.STAR_POINT,
            matchFormat: MatchFormat = MatchFormat.BEST_OF_THREE,
            firstServingTeam: Team = Team.US
        ): MatchConfiguration {
            return MatchConfiguration(
                scoringMode = scoringMode,
                matchFormat = matchFormat,
                gamesRequiredToWinSet = 6,
                tieBreakEnabled = true,
                tieBreakAtGames = 6,
                tieBreakPointsToWin = 7,
                matchTieBreakPointsToWin = 10,
                firstServingTeam = firstServingTeam
            )
        }

        fun shortSet(
            scoringMode: ScoringMode = ScoringMode.STAR_POINT,
            matchFormat: MatchFormat = MatchFormat.BEST_OF_THREE,
            firstServingTeam: Team = Team.US
        ): MatchConfiguration {
            return MatchConfiguration(
                scoringMode = scoringMode,
                matchFormat = matchFormat,
                gamesRequiredToWinSet = 4,
                tieBreakEnabled = true,
                tieBreakAtGames = 4,
                tieBreakPointsToWin = 7,
                matchTieBreakPointsToWin = 10,
                firstServingTeam = firstServingTeam
            )
        }
    }
}
