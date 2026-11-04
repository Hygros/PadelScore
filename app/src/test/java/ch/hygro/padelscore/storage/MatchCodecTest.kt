package ch.hygro.padelscore.storage

import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.SetResult
import ch.hygro.padelscore.model.Team
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchCodecTest {

    private val matchRepository: MatchRepository = allocateWithoutConstructor()
    private val historyRepository: MatchHistoryRepository = allocateWithoutConstructor()

    private val configurationFixture = """{
        "scoringMode":"GOLDEN_POINT",
        "matchFormat":"TWO_SETS_AND_MATCH_TIE_BREAK",
        "gamesRequiredToWinSet":4,
        "tieBreakEnabled":false,
        "tieBreakAtGames":4,
        "tieBreakPointsToWin":7,
        "matchTieBreakPointsToWin":10,
        "firstServingTeam":"OPPONENT"
    }"""

    private val expectedConfiguration = MatchConfiguration(
        scoringMode = ScoringMode.GOLDEN_POINT,
        matchFormat = MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK,
        gamesRequiredToWinSet = 4,
        tieBreakEnabled = false,
        tieBreakAtGames = 4,
        tieBreakPointsToWin = 7,
        matchTieBreakPointsToWin = 10,
        firstServingTeam = Team.OPPONENT
    )

    private val setResultFixture = """{
        "opponentGames":6,
        "ourGames":7,
        "wasTieBreak":true,
        "opponentTieBreakPoints":5,
        "ourTieBreakPoints":7
    }"""

    private val expectedSetResult = SetResult(
        opponentGames = 6,
        ourGames = 7,
        wasTieBreak = true,
        opponentTieBreakPoints = 5,
        ourTieBreakPoints = 7
    )

    @Test
    fun configurationFixedExampleDecodesIdenticallyInBothRepositories() {
        val fromMatch = decodeConfigurationViaMatchRepo(JSONObject(configurationFixture))
        val fromHistory = decodeConfigurationViaHistoryRepo(JSONObject(configurationFixture))
        assertEquals(expectedConfiguration, fromMatch)
        assertEquals(expectedConfiguration, fromHistory)
    }

    @Test
    fun configurationEncodingPreservesAllStoredKeysAndValuesInBothRepositories() {
        val expectedJson = JSONObject(configurationFixture)
        val expectedKeys = expectedJson.keys().asSequence().toSet()

        val matchEncoded = encodeConfigurationViaMatchRepo(expectedConfiguration)
        val historyEncoded = encodeConfigurationViaHistoryRepo(expectedConfiguration)

        assertEquals(expectedKeys, matchEncoded.keys().asSequence().toSet())
        assertEquals(expectedKeys, historyEncoded.keys().asSequence().toSet())
        for (key in expectedKeys) {
            assertEquals(key, expectedJson.get(key), matchEncoded.get(key))
            assertEquals(key, expectedJson.get(key), historyEncoded.get(key))
        }
    }

    @Test
    fun configurationRoundTripsAllEnumCombinations() {
        for (scoringMode in ScoringMode.entries) {
            for (matchFormat in MatchFormat.entries) {
                for (firstServing in Team.entries) {
                    val config = MatchConfiguration(
                        scoringMode = scoringMode,
                        matchFormat = matchFormat,
                        gamesRequiredToWinSet = if (scoringMode == ScoringMode.STAR_POINT) 6 else 4,
                        tieBreakEnabled = scoringMode != ScoringMode.CLASSIC_ADVANTAGE,
                        tieBreakAtGames = if (scoringMode == ScoringMode.STAR_POINT) 6 else 4,
                        tieBreakPointsToWin = 7,
                        matchTieBreakPointsToWin = 10,
                        firstServingTeam = firstServing
                    )
                    assertEquals(
                        config,
                        decodeConfigurationViaMatchRepo(encodeConfigurationViaMatchRepo(config))
                    )
                    assertEquals(
                        config,
                        decodeConfigurationViaHistoryRepo(encodeConfigurationViaHistoryRepo(config))
                    )
                }
            }
        }
    }

    @Test
    fun configurationDefaultsAndShortSetTieBreakFallbackArePreserved() {
        val emptyDefaultsMatch = decodeConfigurationViaMatchRepo(JSONObject())
        val emptyDefaultsHistory = decodeConfigurationViaHistoryRepo(JSONObject())
        val expectedDefaults = MatchConfiguration(
            scoringMode = ScoringMode.STAR_POINT,
            matchFormat = MatchFormat.BEST_OF_THREE,
            gamesRequiredToWinSet = 6,
            tieBreakEnabled = true,
            tieBreakAtGames = 6,
            tieBreakPointsToWin = 7,
            matchTieBreakPointsToWin = 10,
            firstServingTeam = Team.US
        )
        assertEquals(expectedDefaults, emptyDefaultsMatch)
        assertEquals(expectedDefaults, emptyDefaultsHistory)

        // When gamesRequiredToWinSet is 4 and tieBreakAtGames is omitted, tieBreakAtGames defaults to 4.
        val shortSetJson = JSONObject().put("gamesRequiredToWinSet", 4)
        assertEquals(4, decodeConfigurationViaMatchRepo(shortSetJson).tieBreakAtGames)
        assertEquals(4, decodeConfigurationViaHistoryRepo(shortSetJson).tieBreakAtGames)
    }

    @Test
    fun configurationUnknownEnumsAndNullValuesFallBackToDefaults() {
        val unknownEnums = JSONObject()
            .put("scoringMode", "UNKNOWN_SCORING")
            .put("matchFormat", "BEST_OF_FIVE")
            .put("firstServingTeam", "REFEREE")
        val nullEnums = JSONObject()
            .put("scoringMode", JSONObject.NULL)
            .put("matchFormat", JSONObject.NULL)
            .put("firstServingTeam", JSONObject.NULL)

        for (json in listOf(unknownEnums, nullEnums)) {
            val decodedMatch = decodeConfigurationViaMatchRepo(json)
            val decodedHistory = decodeConfigurationViaHistoryRepo(json)
            assertEquals(ScoringMode.STAR_POINT, decodedMatch.scoringMode)
            assertEquals(MatchFormat.BEST_OF_THREE, decodedMatch.matchFormat)
            assertEquals(Team.US, decodedMatch.firstServingTeam)
            assertEquals(decodedMatch, decodedHistory)
        }
    }

    @Test
    fun setResultFixedExampleRoundTripAndDefaultsMatchInBothRepositories() {
        val expectedJson = JSONObject(setResultFixture)
        val expectedKeys = expectedJson.keys().asSequence().toSet()

        val matchEncoded = encodeSetResultViaMatchRepo(expectedSetResult)
        val historyEncoded = encodeSetResultViaHistoryRepo(expectedSetResult)
        assertEquals(expectedKeys, matchEncoded.keys().asSequence().toSet())
        assertEquals(expectedKeys, historyEncoded.keys().asSequence().toSet())
        for (key in expectedKeys) {
            assertEquals(key, expectedJson.get(key), matchEncoded.get(key))
            assertEquals(key, expectedJson.get(key), historyEncoded.get(key))
        }

        assertEquals(expectedSetResult, decodeSetResultViaMatchRepo(expectedJson))
        assertEquals(expectedSetResult, decodeSetResultViaHistoryRepo(expectedJson))

        val defaultSet = SetResult(
            opponentGames = 0,
            ourGames = 0,
            wasTieBreak = false,
            opponentTieBreakPoints = 0,
            ourTieBreakPoints = 0
        )
        assertEquals(defaultSet, decodeSetResultViaMatchRepo(JSONObject()))
        assertEquals(defaultSet, decodeSetResultViaHistoryRepo(JSONObject()))
    }

    @Test
    fun matchStateRoundTripAndDefaultFallbacksArePreserved() {
        val point = GamePoint(
            number = 1,
            setNumber = 1,
            gameNumber = 1,
            scoringTeam = Team.US,
            ourScore = "15",
            opponentScore = "0",
            ourRallies = 1,
            opponentRallies = 0,
            numeric = false,
            matchTieBreak = false,
            gameEnded = false
        )
        val state = MatchState(
            opponentPoints = 2,
            ourPoints = 3,
            opponentGames = 4,
            ourGames = 5,
            opponentSets = 1,
            ourSets = 1,
            isTieBreak = false,
            opponentTieBreakPoints = 0,
            ourTieBreakPoints = 0,
            opponentMatchTieBreakPoints = 0,
            ourMatchTieBreakPoints = 0,
            configuration = expectedConfiguration,
            phase = MatchPhase.NORMAL_GAME,
            advantageTeam = Team.US,
            deuceReturnCount = 2,
            isGoldenPointActive = false,
            isStarPointActive = true,
            servingTeam = Team.OPPONENT,
            tieBreakFirstServingTeam = Team.US,
            completedSets = listOf(expectedSetResult),
            pointsInCurrentGame = 5,
            totalPointsPlayed = 42,
            totalGamesPlayed = 9,
            startedAtEpochMillis = 1700000000000L,
            finishedAtEpochMillis = 1700003600000L,
            averageHeartRate = 142,
            pointHistory = listOf(point)
        )

        val encoded = matchRepository.encodeState(state)
        val decoded = matchRepository.decodeState(encoded)
        assertEquals(state, decoded)

        // Verify fallback behaviour on empty/corrupt fields:
        // - servingTeam falls back to configuration.firstServingTeam
        // - finishedAtEpochMillis 0 -> null
        // - averageHeartRate <= 0 -> null
        // - invalid/null advantageTeam & tieBreakFirstServingTeam -> null
        val minimalJson = JSONObject()
            .put("configuration", JSONObject().put("firstServingTeam", "OPPONENT"))
            .put("phase", "INVALID_PHASE")
            .put("advantageTeam", "INVALID_TEAM")
            .put("tieBreakFirstServingTeam", JSONObject.NULL)
            .put("finishedAtEpochMillis", 0L)
            .put("averageHeartRate", 0)
            .put("completedSets", JSONArray().put("not-an-object").put(JSONObject(setResultFixture)))

        val decodedMinimal = matchRepository.decodeState(minimalJson)
        assertEquals(MatchPhase.NORMAL_GAME, decodedMinimal.phase)
        assertEquals(Team.OPPONENT, decodedMinimal.servingTeam)
        assertNull(decodedMinimal.advantageTeam)
        assertNull(decodedMinimal.tieBreakFirstServingTeam)
        assertNull(decodedMinimal.finishedAtEpochMillis)
        assertNull(decodedMinimal.averageHeartRate)
        assertEquals(listOf(expectedSetResult), decodedMinimal.completedSets)
    }

    @Test
    fun historyEntryEncodeAndDecodePreservesFieldsAndOptionalNullability() {
        val entry = MatchHistoryEntry(
            matchId = 1700000000000L,
            startedAtEpochMillis = 1700000000000L,
            finishedAtEpochMillis = 1700003600000L,
            opponentSets = 1,
            ourSets = 2,
            completedSets = listOf(expectedSetResult),
            opponentMatchTieBreakPoints = 8,
            ourMatchTieBreakPoints = 10,
            configuration = expectedConfiguration,
            totalPointsPlayed = 110,
            pointHistory = emptyList(),
            totalGamesPlayed = 19,
            averageHeartRate = null,
            maximumHeartRate = null,
            steps = 0L,
            stoppedState = null
        )

        val encoded = encodeEntryViaHistoryRepo(entry)
        assertEquals(-1, encoded.getInt("averageHeartRate"))
        assertEquals(-1, encoded.getInt("maximumHeartRate"))
        assertEquals(0L, encoded.getLong("steps"))

        val decoded = decodeEntryViaHistoryRepo(encoded)
        assertEquals(entry, decoded)

        // Negative steps decode to null
        val withNegativeSteps = JSONObject(encoded.toString()).put("steps", -1L)
        assertNull(decodeEntryViaHistoryRepo(withNegativeSteps).steps)
    }

    private fun encodeConfigurationViaMatchRepo(config: MatchConfiguration): JSONObject =
        MatchJsonCodec.encodeConfiguration(config)

    private fun decodeConfigurationViaMatchRepo(json: JSONObject): MatchConfiguration =
        MatchJsonCodec.decodeConfiguration(json)

    private fun encodeConfigurationViaHistoryRepo(config: MatchConfiguration): JSONObject =
        MatchJsonCodec.encodeConfiguration(config)

    private fun decodeConfigurationViaHistoryRepo(json: JSONObject): MatchConfiguration =
        MatchJsonCodec.decodeConfiguration(json)

    private fun encodeSetResultViaMatchRepo(result: SetResult): JSONObject =
        MatchJsonCodec.encodeSetResult(result)

    private fun decodeSetResultViaMatchRepo(json: JSONObject): SetResult =
        MatchJsonCodec.decodeSetResult(json)

    private fun encodeSetResultViaHistoryRepo(result: SetResult): JSONObject =
        MatchJsonCodec.encodeSetResult(result)

    private fun decodeSetResultViaHistoryRepo(json: JSONObject): SetResult =
        MatchJsonCodec.decodeSetResult(json)

    private fun encodeEntryViaHistoryRepo(entry: MatchHistoryEntry): JSONObject =
        invokePrivate(historyRepository, "encodeEntry", entry)

    private fun decodeEntryViaHistoryRepo(json: JSONObject): MatchHistoryEntry =
        invokePrivate(historyRepository, "decodeEntry", json)

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokePrivate(target: Any, methodName: String, arg: Any): T {
        val method = target.javaClass.getDeclaredMethod(methodName, arg.javaClass)
        method.isAccessible = true
        return method.invoke(target, arg) as T
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> allocateWithoutConstructor(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, T::class.java) as T
    }
}
