package ch.hygro.padelscore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMatchHistoryParserTest {

    @Test
    fun parseEntriesDecodesSetsPointHistoryAndMetrics() {
        val json = """
            [
              {
                "matchId": 1700000000000,
                "startedAtEpochMillis": 1700000000000,
                "finishedAtEpochMillis": 1700004500000,
                "opponentSets": 1,
                "ourSets": 2,
                "completedSets": [
                  {"ourGames": 6, "opponentGames": 4, "wasTieBreak": false, "ourTieBreakPoints": 0, "opponentTieBreakPoints": 0},
                  {"ourGames": 6, "opponentGames": 7, "wasTieBreak": true, "ourTieBreakPoints": 5, "opponentTieBreakPoints": 7}
                ],
                "opponentMatchTieBreakPoints": 8,
                "ourMatchTieBreakPoints": 10,
                "totalPointsPlayed": 120,
                "totalGamesPlayed": 23,
                "averageHeartRate": 142,
                "maximumHeartRate": 176,
                "steps": 4850,
                "pointHistory": [
                  {"number": 1, "setNumber": 1, "gameNumber": 1, "scoringTeam": "US", "ourScore": "15", "opponentScore": "0", "ourRallies": 1, "opponentRallies": 0, "numeric": false, "matchTieBreak": false, "gameEnded": false},
                  {"number": 2, "setNumber": 1, "gameNumber": 1, "scoringTeam": "US", "ourScore": "30", "opponentScore": "0", "ourRallies": 2, "opponentRallies": 0, "numeric": false, "matchTieBreak": false, "gameEnded": false},
                  {"number": 3, "setNumber": 1, "gameNumber": 1, "scoringTeam": "US", "ourScore": "40", "opponentScore": "0", "ourRallies": 3, "opponentRallies": 0, "numeric": false, "matchTieBreak": false, "gameEnded": false},
                  {"number": 4, "setNumber": 1, "gameNumber": 1, "scoringTeam": "US", "ourScore": "GAME", "opponentScore": "-", "ourRallies": 4, "opponentRallies": 0, "numeric": false, "matchTieBreak": false, "gameEnded": true}
                ]
              }
            ]
        """.trimIndent()

        val entries = PhoneMatchHistoryParser.parseEntries(json)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertTrue(entry.weWon)
        assertEquals(2, entry.completedSets.size)
        assertTrue(entry.completedSets[1].wasTieBreak)
        assertEquals(10, entry.ourMatchTieBreakPoints)
        assertEquals(142, entry.averageHeartRate)
        assertEquals(176, entry.maximumHeartRate)
        assertEquals(4850L, entry.steps)
        assertEquals(4, entry.pointHistory.size)

        // Losing score on final point of game is recovered from previous contiguous point
        assertEquals("GAME", PhoneMatchHistoryParser.recordedScore(entry.pointHistory, 3, true))
        assertEquals("0", PhoneMatchHistoryParser.recordedScore(entry.pointHistory, 3, false))

        val (hours, minutes) = PhoneMatchHistoryParser.durationHoursAndMinutes(
            entry.startedAtEpochMillis,
            entry.finishedAtEpochMillis
        )
        assertEquals(1L, hours)
        assertEquals(15L, minutes)
    }

    @Test
    fun earlyStopBalanceEvaluatesSetsGamesAndPoints() {
        val json = """
            [
              {
                "matchId": 100,
                "startedAtEpochMillis": 100,
                "finishedAtEpochMillis": 200,
                "opponentSets": 1,
                "ourSets": 1,
                "completedSets": [],
                "opponentMatchTieBreakPoints": 0,
                "ourMatchTieBreakPoints": 0,
                "totalPointsPlayed": 10,
                "totalGamesPlayed": 4,
                "averageHeartRate": -1,
                "maximumHeartRate": -1,
                "steps": -1,
                "stoppedState": {
                  "ourSets": 1,
                  "opponentSets": 1,
                  "ourGames": 3,
                  "opponentGames": 3,
                  "ourPoints": 3,
                  "opponentPoints": 2,
                  "phase": "NORMAL_GAME"
                }
              }
            ]
        """.trimIndent()

        val entry = PhoneMatchHistoryParser.parseEntries(json).single()
        assertNull(entry.averageHeartRate)
        assertNull(entry.maximumHeartRate)
        assertNull(entry.steps)
        assertTrue(PhoneMatchHistoryParser.earlyStopBalance(entry) > 0)
        val stopped = entry.stoppedState!!
        assertEquals("40", PhoneMatchHistoryParser.pointText(stopped, true))
        assertEquals("30", PhoneMatchHistoryParser.pointText(stopped, false))
    }
}
