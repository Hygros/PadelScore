package ch.hygro.padelscore.logic

import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.Team
import org.junit.Assert.*
import org.junit.Test

class PointHistoryTest {
    @Test fun gameRecordsEveryPointAndWinner() {
        var state = MatchState()
        repeat(4) { state = PadelScoreEngine.awardPoint(state, Team.US) }
        assertEquals(4, state.pointHistory.size)
        assertEquals(listOf("15", "30", "40", "GAME"), state.pointHistory.map { it.ourScore })
        assertEquals(4, state.pointHistory.last().ourRallies)
        assertTrue(state.pointHistory.last().gameEnded)
        assertEquals(1, state.ourGames)
        state = PadelScoreEngine.awardPoint(state, Team.OPPONENT)
        assertEquals(2, state.pointHistory.last().gameNumber)
        assertEquals(1, state.pointHistory.last().opponentRallies)
    }
    @Test fun restoredUndoStateDoesNotRetainUndonePoint() {
        val before = PadelScoreEngine.awardPoint(MatchState(), Team.US)
        val after = PadelScoreEngine.awardPoint(before, Team.OPPONENT)
        assertEquals(2, after.pointHistory.size)
        val corrected = PadelScoreEngine.awardPoint(before, Team.US)
        assertEquals(2, corrected.pointHistory.size)
        assertEquals(Team.US, corrected.pointHistory.last().scoringTeam)
        assertEquals(0, corrected.pointHistory.last().opponentRallies)
    }
    @Test fun tieBreakRecordsNumericScoresAndFinalPoint() {
        var state = MatchState(isTieBreak = true, phase = MatchPhase.TIE_BREAK,
            ourGames = 6, opponentGames = 6)
        repeat(7) { state = PadelScoreEngine.awardPoint(state, Team.US) }
        assertEquals("1", state.pointHistory.first().ourScore)
        assertTrue(state.pointHistory.last().gameEnded)
        assertEquals(7, state.pointHistory.last().ourRallies)
        assertEquals(1, state.ourSets)
    }
    @Test fun finishedMatchDoesNotRecordAnotherPoint() {
        val state = MatchState(phase = MatchPhase.MATCH_FINISHED)
        assertEquals(state, PadelScoreEngine.awardPoint(state, Team.US))
    }
    @Test fun legacyMatchStartsAtActualPointNumber() {
        val state = PadelScoreEngine.awardPoint(MatchState(totalPointsPlayed = 20), Team.US)
        assertEquals(21, state.pointHistory.single().number)
    }
}