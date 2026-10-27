package ch.hygro.padelscore.storage

import android.content.Context
import ch.hygro.padelscore.health.LocalWorkoutRepository
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.SetResult
import ch.hygro.padelscore.model.Team
import org.json.JSONArray
import org.json.JSONObject

class MatchHistoryRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val workouts = LocalWorkoutRepository(context)

    fun loadEntries(): List<MatchHistoryEntry> {
        val storedJson = preferences.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(storedJson)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching { add(decodeEntry(array.getJSONObject(index))) }
                }
            }.map { entry ->
                entry.copy(
                    averageHeartRate = workouts.averageHeartRateForMatch(entry.matchId)
                        ?: entry.averageHeartRate,
                    maximumHeartRate = workouts.maximumHeartRateForMatch(entry.matchId)
                        ?: entry.maximumHeartRate,
                    steps = workouts.stepsForMatch(entry.matchId) ?: entry.steps
                )
            }.sortedByDescending { it.finishedAtEpochMillis }
        }.getOrDefault(emptyList())
    }

    fun saveCompletedMatch(
        state: MatchState,
        averageHeartRate: Int? = null,
        endedEarly: Boolean = false
    ) {
        val finishedAt = state.finishedAtEpochMillis ?: return
        if (state.startedAtEpochMillis <= 0L || workouts.isDeleted(state.startedAtEpochMillis)) return
        val entry = MatchHistoryEntry(
            matchId = state.startedAtEpochMillis,
            startedAtEpochMillis = state.startedAtEpochMillis,
            finishedAtEpochMillis = finishedAt,
            opponentSets = state.opponentSets,
            ourSets = state.ourSets,
            completedSets = state.completedSets,
            opponentMatchTieBreakPoints = state.opponentMatchTieBreakPoints,
            ourMatchTieBreakPoints = state.ourMatchTieBreakPoints,
            configuration = state.configuration,
            totalPointsPlayed = state.totalPointsPlayed,
            pointHistory = state.pointHistory,
            totalGamesPlayed = state.totalGamesPlayed,
            averageHeartRate = averageHeartRate,
            maximumHeartRate = workouts.maximumHeartRateForMatch(state.startedAtEpochMillis),
            steps = workouts.stepsForMatch(state.startedAtEpochMillis),
            stoppedState = if (endedEarly) state else null
        )
        val updated = loadEntries()
            .filterNot { it.matchId == entry.matchId }
            .plus(entry)
            .sortedByDescending { it.finishedAtEpochMillis }
            .take(MAX_HISTORY_ENTRIES)
        saveEntries(updated)
    }

    fun removeByMatchId(matchId: Long, deleteHealthData: Boolean = true) {
        if (matchId > 0L) {
            // Persist the deletion before removing the visible history entry.
            if (deleteHealthData) workouts.removeMatch(matchId, deleteHealthData = true)
            saveEntries(loadEntries().filterNot { it.matchId == matchId })
            if (deleteHealthData) {
                ch.hygro.padelscore.health.WorkoutDataSender(context).scheduleDeletionSync()
            }
        }
    }

    private fun saveEntries(entries: List<MatchHistoryEntry>) {
        val array = JSONArray().apply { entries.forEach { put(encodeEntry(it)) } }
        check(
            preferences.edit {
                putString(KEY_HISTORY_JSON, array.toString())
            }
        ) { "Could not persist match history" }
    }

    private fun encodeEntry(entry: MatchHistoryEntry) = JSONObject()
        .put("matchId", entry.matchId)
        .put("startedAtEpochMillis", entry.startedAtEpochMillis)
        .put("finishedAtEpochMillis", entry.finishedAtEpochMillis)
        .put("opponentSets", entry.opponentSets)
        .put("ourSets", entry.ourSets)
        .put("completedSets", JSONArray().apply {
            entry.completedSets.forEach { put(encodeSetResult(it)) }
        })
        .put("opponentMatchTieBreakPoints", entry.opponentMatchTieBreakPoints)
        .put("ourMatchTieBreakPoints", entry.ourMatchTieBreakPoints)
        .put("configuration", encodeConfiguration(entry.configuration))
        .put("totalPointsPlayed", entry.totalPointsPlayed)
        .put("pointHistory", PointHistoryCodec.encode(entry.pointHistory))
        .put("totalGamesPlayed", entry.totalGamesPlayed)
        .put("averageHeartRate", entry.averageHeartRate ?: -1)
        .put("maximumHeartRate", entry.maximumHeartRate ?: -1)
        .put("steps", entry.steps ?: -1L)
        .put(
            "stoppedState",
            entry.stoppedState?.let { MatchRepository(context).encodeState(it) }
        )

    private fun decodeEntry(json: JSONObject): MatchHistoryEntry {
        val setsJson = json.optJSONArray("completedSets") ?: JSONArray()
        val sets = buildList {
            for (index in 0 until setsJson.length()) {
                runCatching { add(decodeSetResult(setsJson.getJSONObject(index))) }
            }
        }
        val hr = json.optInt("averageHeartRate", -1)
        return MatchHistoryEntry(
            matchId = json.getLong("matchId"),
            startedAtEpochMillis = json.getLong("startedAtEpochMillis"),
            finishedAtEpochMillis = json.getLong("finishedAtEpochMillis"),
            opponentSets = json.optInt("opponentSets", 0),
            ourSets = json.optInt("ourSets", 0),
            completedSets = sets,
            opponentMatchTieBreakPoints = json.optInt("opponentMatchTieBreakPoints", 0),
            ourMatchTieBreakPoints = json.optInt("ourMatchTieBreakPoints", 0),
            configuration = decodeConfiguration(json.optJSONObject("configuration") ?: JSONObject()),
            totalPointsPlayed = json.optInt("totalPointsPlayed", 0),
            pointHistory = PointHistoryCodec.decode(json.optJSONArray("pointHistory")),
            totalGamesPlayed = json.optInt("totalGamesPlayed", 0),
            averageHeartRate = if (hr > 0) hr else null,
            maximumHeartRate = json.optInt("maximumHeartRate", -1).takeIf { it > 0 },
            steps = json.optLong("steps", -1L).takeIf { it >= 0L },
            stoppedState = json.optJSONObject("stoppedState")?.let {
                MatchRepository(context).decodeState(it)
            }
        )
    }

    private fun encodeConfiguration(configuration: MatchConfiguration) = JSONObject()
        .put("scoringMode", configuration.scoringMode.name)
        .put("matchFormat", configuration.matchFormat.name)
        .put("gamesRequiredToWinSet", configuration.gamesRequiredToWinSet)
        .put("tieBreakEnabled", configuration.tieBreakEnabled)
        .put("tieBreakAtGames", configuration.tieBreakAtGames)
        .put("tieBreakPointsToWin", configuration.tieBreakPointsToWin)
        .put("matchTieBreakPointsToWin", configuration.matchTieBreakPointsToWin)
        .put("firstServingTeam", configuration.firstServingTeam.name)

    private fun decodeConfiguration(json: JSONObject): MatchConfiguration {
        val gamesRequired = json.optInt("gamesRequiredToWinSet", 6)
        return MatchConfiguration(
            scoringMode = enumValueOrDefault(json.optString("scoringMode"), ScoringMode.STAR_POINT),
            matchFormat = enumValueOrDefault(json.optString("matchFormat"), MatchFormat.BEST_OF_THREE),
            gamesRequiredToWinSet = gamesRequired,
            tieBreakEnabled = json.optBoolean("tieBreakEnabled", true),
            tieBreakAtGames = json.optInt("tieBreakAtGames", if (gamesRequired == 4) 4 else 6),
            tieBreakPointsToWin = json.optInt("tieBreakPointsToWin", 7),
            matchTieBreakPointsToWin = json.optInt("matchTieBreakPointsToWin", 10),
            firstServingTeam = enumValueOrDefault(json.optString("firstServingTeam"), Team.US)
        )
    }

    private fun encodeSetResult(result: SetResult) = JSONObject()
        .put("opponentGames", result.opponentGames)
        .put("ourGames", result.ourGames)
        .put("wasTieBreak", result.wasTieBreak)
        .put("opponentTieBreakPoints", result.opponentTieBreakPoints)
        .put("ourTieBreakPoints", result.ourTieBreakPoints)

    private fun decodeSetResult(json: JSONObject) = SetResult(
        opponentGames = json.optInt("opponentGames", 0),
        ourGames = json.optInt("ourGames", 0),
        wasTieBreak = json.optBoolean("wasTieBreak", false),
        opponentTieBreakPoints = json.optInt("opponentTieBreakPoints", 0),
        ourTieBreakPoints = json.optInt("ourTieBreakPoints", 0)
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    companion object {
        private const val PREFERENCES_NAME = "padel_score_history"
        private const val KEY_HISTORY_JSON = "match_history_json"
        private const val MAX_HISTORY_ENTRIES = 100
    }
}
