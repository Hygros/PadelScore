package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadelScoreEngineTest {

    @Test
    fun normalPointSequence_reachesFortyWithoutWinningGame() {
        var state = MatchState()
        repeat(3) { state = PadelScoreEngine.awardPoint(state, Team.US) }
        assertEquals(3, state.ourPoints)
        assertEquals(0, state.ourGames)
        assertEquals(3, state.totalPointsPlayed)
    }

    @Test
    fun fourthStraightPoint_winsGameForOurTeam() {
        var state = MatchState()
        repeat(4) { state = PadelScoreEngine.awardPoint(state, Team.US) }
        assertEquals(0, state.ourPoints)
        assertEquals(1, state.ourGames)
        assertEquals(4, state.totalPointsPlayed)
        assertEquals(1, state.totalGamesPlayed)
    }

    @Test
    fun fourthStraightPoint_winsGameForOpponent() {
        var state = MatchState()
        repeat(4) { state = PadelScoreEngine.awardPoint(state, Team.OPPONENT) }
        assertEquals(0, state.opponentPoints)
        assertEquals(1, state.opponentGames)
        assertEquals(4, state.totalPointsPlayed)
    }

    @Test
    fun starPoint_firstDeuce_usesNormalAdvantage() {
        val result = PadelScoreEngine.awardPoint(
            starState(opponentPoints = 3, ourPoints = 3),
            Team.US
        )

        assertEquals(Team.US, result.advantageTeam)
        assertEquals(0, result.deuceReturnCount)
        assertFalse(result.isStarPointActive)
        assertEquals(0, result.ourGames)
    }

    @Test
    fun starPoint_firstReturnToDeuce_increasesCounter() {
        var state = starState(opponentPoints = 3, ourPoints = 3)

        state = PadelScoreEngine.awardPoint(state, Team.US)
        state = PadelScoreEngine.awardPoint(state, Team.OPPONENT)

        assertNull(state.advantageTeam)
        assertEquals(1, state.deuceReturnCount)
        assertFalse(state.isStarPointActive)
        assertEquals(3, state.opponentPoints)
        assertEquals(3, state.ourPoints)
    }

    @Test
    fun starPoint_secondDeuce_usesNormalAdvantage() {
        val state = starState(
            opponentPoints = 3,
            ourPoints = 3,
            deuceReturnCount = 1
        )

        val result = PadelScoreEngine.awardPoint(state, Team.OPPONENT)

        assertEquals(Team.OPPONENT, result.advantageTeam)
        assertEquals(1, result.deuceReturnCount)
        assertFalse(result.isStarPointActive)
    }

    @Test
    fun starPoint_secondReturnToDeuce_activatesStarPoint() {
        var state = starState(
            opponentPoints = 3,
            ourPoints = 3,
            deuceReturnCount = 1
        )

        state = PadelScoreEngine.awardPoint(state, Team.OPPONENT)
        state = PadelScoreEngine.awardPoint(state, Team.US)

        assertNull(state.advantageTeam)
        assertEquals(2, state.deuceReturnCount)
        assertTrue(state.isStarPointActive)
        assertEquals(0, state.opponentGames)
        assertEquals(0, state.ourGames)
    }

    @Test
    fun starPoint_activeNextPoint_winsGame() {
        val state = starState(
            opponentPoints = 3,
            ourPoints = 3,
            deuceReturnCount = 2,
            isStarPointActive = true
        )

        val result = PadelScoreEngine.awardPoint(state, Team.US)

        assertEquals(1, result.ourGames)
        assertEquals(0, result.opponentGames)
        assertEquals(0, result.opponentPoints)
        assertEquals(0, result.ourPoints)
        assertEquals(0, result.deuceReturnCount)
        assertNull(result.advantageTeam)
        assertFalse(result.isStarPointActive)
    }

    @Test
    fun starPoint_advantageWinnerBeforeThirdDeuce_winsGameNormally() {
        var state = starState(
            opponentPoints = 3,
            ourPoints = 3,
            deuceReturnCount = 1
        )

        state = PadelScoreEngine.awardPoint(state, Team.US)
        state = PadelScoreEngine.awardPoint(state, Team.US)

        assertEquals(1, state.ourGames)
        assertEquals(0, state.deuceReturnCount)
        assertFalse(state.isStarPointActive)
    }

    @Test
    fun classicAdvantage_deuceThenAdvantageThenGame() {
        var state = classicState(opponentPoints = 3, ourPoints = 3)

        state = PadelScoreEngine.awardPoint(state, Team.US)
        assertEquals(Team.US, state.advantageTeam)
        assertEquals(3, state.opponentPoints)
        assertEquals(3, state.ourPoints)
        assertEquals(0, state.ourGames)

        state = PadelScoreEngine.awardPoint(state, Team.US)
        assertEquals(1, state.ourGames)
        assertEquals(0, state.opponentGames)
        assertNull(state.advantageTeam)
        assertEquals(0, state.opponentPoints)
        assertEquals(0, state.ourPoints)
    }

    @Test
    fun classicAdvantage_opponentPointAgainstAdvantage_returnsToDeuce() {
        var state = classicState(opponentPoints = 3, ourPoints = 3)

        state = PadelScoreEngine.awardPoint(state, Team.US)
        assertEquals(Team.US, state.advantageTeam)

        state = PadelScoreEngine.awardPoint(state, Team.OPPONENT)
        assertNull(state.advantageTeam)
        assertEquals(3, state.opponentPoints)
        assertEquals(3, state.ourPoints)
        assertEquals(0, state.opponentGames)
        assertEquals(0, state.ourGames)
    }

    @Test
    fun classicAdvantage_pointFromFortyThirty_winsGame() {
        val result = PadelScoreEngine.awardPoint(
            classicState(opponentPoints = 2, ourPoints = 3),
            Team.US
        )
        assertEquals(1, result.ourGames)
        assertEquals(0, result.ourPoints)
    }

    @Test
    fun goldenPoint_reachingFortyAll_activatesGoldenPoint() {
        val result = PadelScoreEngine.awardPoint(
            goldenState(opponentPoints = 2, ourPoints = 3),
            Team.OPPONENT
        )

        assertEquals(3, result.opponentPoints)
        assertEquals(3, result.ourPoints)
        assertTrue(result.isGoldenPointActive)
        assertNull(result.advantageTeam)
        assertEquals(0, result.opponentGames)
        assertEquals(0, result.ourGames)
    }

    @Test
    fun goldenPoint_atFortyAll_nextPointWinsGameForOurTeam() {
        val result = PadelScoreEngine.awardPoint(
            goldenState(
                opponentPoints = 3,
                ourPoints = 3,
                isGoldenPointActive = true
            ),
            Team.US
        )

        assertEquals(1, result.ourGames)
        assertEquals(0, result.opponentGames)
        assertEquals(0, result.opponentPoints)
        assertEquals(0, result.ourPoints)
        assertFalse(result.isGoldenPointActive)
        assertEquals(1, result.totalPointsPlayed)
    }

    @Test
    fun goldenPoint_atFortyAll_nextPointWinsGameForOpponent() {
        val result = PadelScoreEngine.awardPoint(
            goldenState(
                opponentPoints = 3,
                ourPoints = 3,
                isGoldenPointActive = true
            ),
            Team.OPPONENT
        )

        assertEquals(1, result.opponentGames)
        assertEquals(0, result.ourGames)
        assertEquals(0, result.opponentPoints)
        assertEquals(0, result.ourPoints)
        assertFalse(result.isGoldenPointActive)
    }

    @Test
    fun gameWin_resetsPreparedDeuceAndStarPointFields() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                opponentPoints = 3,
                ourPoints = 3,
                advantageTeam = Team.US,
                deuceReturnCount = 2,
                isGoldenPointActive = true,
                isStarPointActive = true
            ),
            Team.US
        )
        assertNull(result.advantageTeam)
        assertEquals(0, result.deuceReturnCount)
        assertFalse(result.isGoldenPointActive)
        assertFalse(result.isStarPointActive)
    }

    @Test
    fun gameWin_changesServingTeam() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(ourPoints = 3, servingTeam = Team.US),
            Team.US
        )
        assertEquals(Team.OPPONENT, result.servingTeam)
        assertEquals(1, result.ourGames)
    }

    @Test
    fun setIsWonAtSixFour() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(ourPoints = 3, opponentGames = 4, ourGames = 5),
            Team.US
        )
        assertEquals(1, result.ourSets)
        assertEquals(0, result.ourGames)
        assertEquals(1, result.completedSets.size)
        assertEquals(4, result.completedSets.first().opponentGames)
        assertEquals(6, result.completedSets.first().ourGames)
    }

    @Test
    fun setIsNotWonAtSixFive() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(ourPoints = 3, opponentGames = 5, ourGames = 5),
            Team.US
        )
        assertEquals(5, result.opponentGames)
        assertEquals(6, result.ourGames)
        assertEquals(0, result.ourSets)
        assertFalse(result.isTieBreak)
    }

    @Test
    fun tieBreakStartsAtSixSix() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(opponentPoints = 3, opponentGames = 5, ourGames = 6),
            Team.OPPONENT
        )
        assertEquals(6, result.opponentGames)
        assertEquals(6, result.ourGames)
        assertTrue(result.isTieBreak)
        assertEquals(MatchPhase.TIE_BREAK, result.phase)
    }

    @Test
    fun tieBreakAtSevenThree_isWon() {
        val result = PadelScoreEngine.awardPoint(
            tieBreakState(opponentPoints = 6, ourPoints = 3),
            Team.OPPONENT
        )
        assertEquals(1, result.opponentSets)
        assertFalse(result.isTieBreak)
        assertEquals(7, result.completedSets.first().opponentTieBreakPoints)
        assertEquals(3, result.completedSets.first().ourTieBreakPoints)
    }

    @Test
    fun tieBreakAtSevenSix_continues() {
        val result = PadelScoreEngine.awardPoint(
            tieBreakState(opponentPoints = 6, ourPoints = 6),
            Team.OPPONENT
        )
        assertEquals(7, result.opponentTieBreakPoints)
        assertEquals(6, result.ourTieBreakPoints)
        assertTrue(result.isTieBreak)
        assertEquals(0, result.opponentSets)
    }

    @Test
    fun tieBreakAtEightSix_isWon() {
        val result = PadelScoreEngine.awardPoint(
            tieBreakState(opponentPoints = 7, ourPoints = 6),
            Team.OPPONENT
        )
        assertEquals(1, result.opponentSets)
        assertFalse(result.isTieBreak)
        assertEquals(8, result.completedSets.first().opponentTieBreakPoints)
    }

    @Test
    fun secondSetWin_finishesMatch() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 4,
                ourGames = 5,
                ourSets = 1
            ),
            Team.US
        )
        assertEquals(2, result.ourSets)
        assertEquals(MatchPhase.MATCH_FINISHED, result.phase)
        assertTrue(PadelScoreEngine.isMatchFinished(result))
        assertTrue(result.finishedAtEpochMillis != null)
    }

    @Test
    fun pointAfterMatchFinished_doesNotChangeState() {
        val state = MatchState(
            ourSets = 2,
            phase = MatchPhase.MATCH_FINISHED,
            finishedAtEpochMillis = 123456789L
        )
        assertEquals(state, PadelScoreEngine.awardPoint(state, Team.OPPONENT))
    }

    @Test
    fun newMatch_isNotFinished() {
        assertFalse(PadelScoreEngine.isMatchFinished(MatchState()))
    }

    @Test
    fun oneSetFormat_firstSetWinFinishesMatch() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 4,
                ourGames = 5,
                configuration = MatchConfiguration(
                    matchFormat = MatchFormat.ONE_SET
                )
            ),
            Team.US
        )

        assertEquals(1, result.ourSets)
        assertEquals(MatchPhase.MATCH_FINISHED, result.phase)
        assertTrue(PadelScoreEngine.isMatchFinished(result))
    }

    @Test
    fun bestOfThree_firstSetWinContinuesMatch() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 4,
                ourGames = 5,
                configuration = MatchConfiguration(
                    matchFormat = MatchFormat.BEST_OF_THREE
                )
            ),
            Team.US
        )

        assertEquals(1, result.ourSets)
        assertEquals(MatchPhase.NORMAL_GAME, result.phase)
        assertFalse(PadelScoreEngine.isMatchFinished(result))
    }

    @Test
    fun shortSet_isWonAtFourTwo() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 2,
                ourGames = 3,
                configuration = MatchConfiguration.shortSet()
            ),
            Team.US
        )

        assertEquals(1, result.ourSets)
        assertEquals(2, result.completedSets.first().opponentGames)
        assertEquals(4, result.completedSets.first().ourGames)
    }

    @Test
    fun shortSet_isNotWonAtFourThree() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 3,
                ourGames = 3,
                configuration = MatchConfiguration.shortSet()
            ),
            Team.US
        )

        assertEquals(3, result.opponentGames)
        assertEquals(4, result.ourGames)
        assertEquals(0, result.ourSets)
        assertEquals(MatchPhase.NORMAL_GAME, result.phase)
    }

    @Test
    fun shortSet_tieBreakStartsAtFourAll() {
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                opponentPoints = 3,
                opponentGames = 3,
                ourGames = 4,
                configuration = MatchConfiguration.shortSet()
            ),
            Team.OPPONENT
        )

        assertEquals(4, result.opponentGames)
        assertEquals(4, result.ourGames)
        assertTrue(result.isTieBreak)
        assertEquals(MatchPhase.TIE_BREAK, result.phase)
    }

    @Test
    fun disabledTieBreak_atSixAllContinuesNormalGames() {
        val configuration = MatchConfiguration(
            tieBreakEnabled = false
        )
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                opponentPoints = 3,
                opponentGames = 5,
                ourGames = 6,
                configuration = configuration
            ),
            Team.OPPONENT
        )

        assertEquals(6, result.opponentGames)
        assertEquals(6, result.ourGames)
        assertFalse(result.isTieBreak)
        assertEquals(MatchPhase.NORMAL_GAME, result.phase)
    }

    @Test
    fun twoSetsAndMatchTieBreak_atOneAllStartsMatchTieBreak() {
        val configuration = MatchConfiguration(
            matchFormat = MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK
        )
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 4,
                ourGames = 5,
                opponentSets = 1,
                configuration = configuration
            ),
            Team.US
        )

        assertEquals(1, result.opponentSets)
        assertEquals(1, result.ourSets)
        assertEquals(MatchPhase.MATCH_TIE_BREAK, result.phase)
        assertFalse(PadelScoreEngine.isMatchFinished(result))
    }

    @Test
    fun twoSetsAndMatchTieBreak_straightSetsFinishMatch() {
        val configuration = MatchConfiguration(
            matchFormat = MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK
        )
        val result = PadelScoreEngine.awardPoint(
            MatchState(
                ourPoints = 3,
                opponentGames = 4,
                ourGames = 5,
                ourSets = 1,
                configuration = configuration
            ),
            Team.US
        )

        assertEquals(2, result.ourSets)
        assertEquals(MatchPhase.MATCH_FINISHED, result.phase)
        assertTrue(PadelScoreEngine.isMatchFinished(result))
    }

    @Test
    fun matchTieBreak_tenEightFinishesMatch() {
        val state = matchTieBreakState(opponentPoints = 8, ourPoints = 9)
        val result = PadelScoreEngine.awardPoint(state, Team.US)

        assertEquals(8, result.opponentMatchTieBreakPoints)
        assertEquals(10, result.ourMatchTieBreakPoints)
        assertEquals(2, result.ourSets)
        assertEquals(MatchPhase.MATCH_FINISHED, result.phase)
    }

    @Test
    fun matchTieBreak_tenNineContinues() {
        val state = matchTieBreakState(opponentPoints = 9, ourPoints = 9)
        val result = PadelScoreEngine.awardPoint(state, Team.US)

        assertEquals(9, result.opponentMatchTieBreakPoints)
        assertEquals(10, result.ourMatchTieBreakPoints)
        assertEquals(MatchPhase.MATCH_TIE_BREAK, result.phase)
        assertFalse(PadelScoreEngine.isMatchFinished(result))
    }

    @Test
    fun matchTieBreak_elevenNineFinishesMatch() {
        val state = matchTieBreakState(opponentPoints = 9, ourPoints = 10)
        val result = PadelScoreEngine.awardPoint(state, Team.US)

        assertEquals(9, result.opponentMatchTieBreakPoints)
        assertEquals(11, result.ourMatchTieBreakPoints)
        assertEquals(2, result.ourSets)
        assertEquals(MatchPhase.MATCH_FINISHED, result.phase)
    }

    private fun matchTieBreakState(
        opponentPoints: Int,
        ourPoints: Int
    ): MatchState {
        return MatchState(
            opponentSets = 1,
            ourSets = 1,
            opponentMatchTieBreakPoints = opponentPoints,
            ourMatchTieBreakPoints = ourPoints,
            configuration = MatchConfiguration(
                matchFormat = MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK
            ),
            phase = MatchPhase.MATCH_TIE_BREAK
        )
    }

    private fun classicState(
        opponentPoints: Int = 0,
        ourPoints: Int = 0
    ): MatchState {
        return MatchState(
            opponentPoints = opponentPoints,
            ourPoints = ourPoints,
            configuration = MatchConfiguration(
                scoringMode = ScoringMode.CLASSIC_ADVANTAGE
            )
        )
    }

    private fun starState(
        opponentPoints: Int = 0,
        ourPoints: Int = 0,
        deuceReturnCount: Int = 0,
        isStarPointActive: Boolean = false
    ): MatchState {
        return MatchState(
            opponentPoints = opponentPoints,
            ourPoints = ourPoints,
            configuration = MatchConfiguration(
                scoringMode = ScoringMode.STAR_POINT
            ),
            deuceReturnCount = deuceReturnCount,
            isStarPointActive = isStarPointActive
        )
    }

    private fun goldenState(
        opponentPoints: Int = 0,
        ourPoints: Int = 0,
        isGoldenPointActive: Boolean = false
    ): MatchState {
        return MatchState(
            opponentPoints = opponentPoints,
            ourPoints = ourPoints,
            configuration = MatchConfiguration(
                scoringMode = ScoringMode.GOLDEN_POINT
            ),
            isGoldenPointActive = isGoldenPointActive
        )
    }

    private fun tieBreakState(
        opponentPoints: Int,
        ourPoints: Int
    ): MatchState {
        return MatchState(
            opponentGames = 6,
            ourGames = 6,
            isTieBreak = true,
            opponentTieBreakPoints = opponentPoints,
            ourTieBreakPoints = ourPoints,
            phase = MatchPhase.TIE_BREAK
        )
    }
}
