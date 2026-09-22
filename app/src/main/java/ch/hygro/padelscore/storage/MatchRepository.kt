package ch.hygro.padelscore.storage

import android.content.Context
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.SetResult
import ch.hygro.padelscore.model.Team
import org.json.JSONArray
import org.json.JSONObject

class MatchRepository(context: Context) {

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun loadSnapshot(): MatchSnapshot {
        val snapshotJson = preferences.getString(KEY_SNAPSHOT_JSON, null)

        if (snapshotJson != null) {
            runCatching {
                return decodeSnapshot(JSONObject(snapshotJson))
            }
        }

        val legacyState = loadLegacyState()
        val snapshot = MatchSnapshot(
            currentState = legacyState,
            undoHistory = emptyList()
        )

        saveSnapshot(snapshot.currentState, snapshot.undoHistory)
        return snapshot
    }

    fun saveSnapshot(
        currentState: MatchState,
        undoHistory: List<MatchState>
    ) {
        val limitedHistory = undoHistory.takeLast(MAX_PERSISTED_HISTORY)
        val snapshot = JSONObject()
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_CURRENT_STATE, encodeState(currentState))
            .put(
                KEY_UNDO_HISTORY,
                JSONArray().apply {
                    limitedHistory.forEach { put(encodeState(it)) }
                }
            )

        preferences.edit()
            .putString(KEY_SNAPSHOT_JSON, snapshot.toString())
            .apply()
    }

    private fun decodeSnapshot(json: JSONObject): MatchSnapshot {
        val currentState = decodeState(json.getJSONObject(KEY_CURRENT_STATE))
        val historyJson = json.optJSONArray(KEY_UNDO_HISTORY) ?: JSONArray()
        val history = buildList {
            for (index in 0 until historyJson.length()) {
                runCatching {
                    add(decodeState(historyJson.getJSONObject(index)))
                }
            }
        }

        return MatchSnapshot(
            currentState = currentState,
            undoHistory = history.takeLast(MAX_PERSISTED_HISTORY)
        )
    }

    private fun encodeState(state: MatchState): JSONObject {
        return JSONObject()
            .put("opponentPoints", state.opponentPoints)
            .put("ourPoints", state.ourPoints)
            .put("opponentGames", state.opponentGames)
            .put("ourGames", state.ourGames)
            .put("opponentSets", state.opponentSets)
            .put("ourSets", state.ourSets)
            .put("isTieBreak", state.isTieBreak)
            .put("opponentTieBreakPoints", state.opponentTieBreakPoints)
            .put("ourTieBreakPoints", state.ourTieBreakPoints)
            .put(
                "opponentMatchTieBreakPoints",
                state.opponentMatchTieBreakPoints
            )
            .put("ourMatchTieBreakPoints", state.ourMatchTieBreakPoints)
            .put("configuration", encodeConfiguration(state.configuration))
            .put("phase", state.phase.name)
            .put("advantageTeam", state.advantageTeam?.name)
            .put("deuceReturnCount", state.deuceReturnCount)
            .put("isGoldenPointActive", state.isGoldenPointActive)
            .put("isStarPointActive", state.isStarPointActive)
            .put("servingTeam", state.servingTeam.name)
            .put(
                "tieBreakFirstServingTeam",
                state.tieBreakFirstServingTeam?.name
            )
            .put(
                "completedSets",
                JSONArray().apply {
                    state.completedSets.forEach { put(encodeSetResult(it)) }
                }
            )
            .put("totalPointsPlayed", state.totalPointsPlayed)
            .put("totalGamesPlayed", state.totalGamesPlayed)
            .put("startedAtEpochMillis", state.startedAtEpochMillis)
            .put("finishedAtEpochMillis", state.finishedAtEpochMillis)
    }

    private fun decodeState(json: JSONObject): MatchState {
        val configuration = decodeConfiguration(
            json.optJSONObject("configuration") ?: JSONObject()
        )
        val completedSetsJson = json.optJSONArray("completedSets") ?: JSONArray()
        val completedSets = buildList {
            for (index in 0 until completedSetsJson.length()) {
                runCatching {
                    add(decodeSetResult(completedSetsJson.getJSONObject(index)))
                }
            }
        }

        return MatchState(
            opponentPoints = json.optInt("opponentPoints", 0),
            ourPoints = json.optInt("ourPoints", 0),
            opponentGames = json.optInt("opponentGames", 0),
            ourGames = json.optInt("ourGames", 0),
            opponentSets = json.optInt("opponentSets", 0),
            ourSets = json.optInt("ourSets", 0),
            isTieBreak = json.optBoolean("isTieBreak", false),
            opponentTieBreakPoints = json.optInt("opponentTieBreakPoints", 0),
            ourTieBreakPoints = json.optInt("ourTieBreakPoints", 0),
            opponentMatchTieBreakPoints = json.optInt(
                "opponentMatchTieBreakPoints",
                0
            ),
            ourMatchTieBreakPoints = json.optInt("ourMatchTieBreakPoints", 0),
            configuration = configuration,
            phase = enumValueOrDefault(
                json.optString("phase"),
                MatchPhase.NORMAL_GAME
            ),
            advantageTeam = nullableEnumValue<Team>(
                json.optString("advantageTeam")
            ),
            deuceReturnCount = json.optInt("deuceReturnCount", 0),
            isGoldenPointActive = json.optBoolean(
                "isGoldenPointActive",
                false
            ),
            isStarPointActive = json.optBoolean("isStarPointActive", false),
            servingTeam = enumValueOrDefault(
                json.optString("servingTeam"),
                configuration.firstServingTeam
            ),
            tieBreakFirstServingTeam = nullableEnumValue<Team>(
                json.optString("tieBreakFirstServingTeam")
            ),
            completedSets = completedSets,
            totalPointsPlayed = json.optInt("totalPointsPlayed", 0),
            totalGamesPlayed = json.optInt("totalGamesPlayed", 0),
            startedAtEpochMillis = json.optLong("startedAtEpochMillis", 0L),
            finishedAtEpochMillis = json.optLong("finishedAtEpochMillis", 0L)
                .takeIf { it != 0L }
        )
    }

    private fun encodeConfiguration(
        configuration: MatchConfiguration
    ): JSONObject {
        return JSONObject()
            .put("scoringMode", configuration.scoringMode.name)
            .put("matchFormat", configuration.matchFormat.name)
            .put("gamesRequiredToWinSet", configuration.gamesRequiredToWinSet)
            .put("tieBreakEnabled", configuration.tieBreakEnabled)
            .put("tieBreakAtGames", configuration.tieBreakAtGames)
            .put("tieBreakPointsToWin", configuration.tieBreakPointsToWin)
            .put(
                "matchTieBreakPointsToWin",
                configuration.matchTieBreakPointsToWin
            )
            .put("firstServingTeam", configuration.firstServingTeam.name)
    }

    private fun decodeConfiguration(json: JSONObject): MatchConfiguration {
        val gamesRequired = json.optInt("gamesRequiredToWinSet", 6)
        val defaultTieBreakAt = if (gamesRequired == 4) 4 else 6

        return MatchConfiguration(
            scoringMode = enumValueOrDefault(
                json.optString("scoringMode"),
                ScoringMode.STAR_POINT
            ),
            matchFormat = enumValueOrDefault(
                json.optString("matchFormat"),
                MatchFormat.BEST_OF_THREE
            ),
            gamesRequiredToWinSet = gamesRequired,
            tieBreakEnabled = json.optBoolean("tieBreakEnabled", true),
            tieBreakAtGames = json.optInt(
                "tieBreakAtGames",
                defaultTieBreakAt
            ),
            tieBreakPointsToWin = json.optInt("tieBreakPointsToWin", 7),
            matchTieBreakPointsToWin = json.optInt(
                "matchTieBreakPointsToWin",
                10
            ),
            firstServingTeam = enumValueOrDefault(
                json.optString("firstServingTeam"),
                Team.US
            )
        )
    }

    private fun encodeSetResult(result: SetResult): JSONObject {
        return JSONObject()
            .put("opponentGames", result.opponentGames)
            .put("ourGames", result.ourGames)
            .put("wasTieBreak", result.wasTieBreak)
            .put("opponentTieBreakPoints", result.opponentTieBreakPoints)
            .put("ourTieBreakPoints", result.ourTieBreakPoints)
    }

    private fun decodeSetResult(json: JSONObject): SetResult {
        return SetResult(
            opponentGames = json.optInt("opponentGames", 0),
            ourGames = json.optInt("ourGames", 0),
            wasTieBreak = json.optBoolean("wasTieBreak", false),
            opponentTieBreakPoints = json.optInt("opponentTieBreakPoints", 0),
            ourTieBreakPoints = json.optInt("ourTieBreakPoints", 0)
        )
    }

    private fun loadLegacyState(): MatchState {
        val opponentSets = preferences.getInt("opponent_sets", 0)
        val ourSets = preferences.getInt("our_sets", 0)
        val tieBreak = preferences.getBoolean("is_tie_break", false)
        val phase = when {
            opponentSets >= 2 || ourSets >= 2 -> MatchPhase.MATCH_FINISHED
            tieBreak -> MatchPhase.TIE_BREAK
            else -> MatchPhase.NORMAL_GAME
        }

        return MatchState(
            opponentPoints = preferences.getInt("opponent_points", 0),
            ourPoints = preferences.getInt("our_points", 0),
            opponentGames = preferences.getInt("opponent_games", 0),
            ourGames = preferences.getInt("our_games", 0),
            opponentSets = opponentSets,
            ourSets = ourSets,
            isTieBreak = tieBreak,
            opponentTieBreakPoints = preferences.getInt(
                "opponent_tie_break_points",
                0
            ),
            ourTieBreakPoints = preferences.getInt("our_tie_break_points", 0),
            phase = phase
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T
    ): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }

    private inline fun <reified T : Enum<T>> nullableEnumValue(
        value: String
    ): T? {
        if (value.isBlank() || value == "null") return null
        return runCatching { enumValueOf<T>(value) }.getOrNull()
    }

    companion object {
        private const val PREFERENCES_NAME = "padel_score_match"
        private const val KEY_SNAPSHOT_JSON = "match_snapshot_json"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_CURRENT_STATE = "currentState"
        private const val KEY_UNDO_HISTORY = "undoHistory"
        private const val SCHEMA_VERSION = 1
        private const val MAX_PERSISTED_HISTORY = 100
    }
}

data class MatchSnapshot(
    val currentState: MatchState,
    val undoHistory: List<MatchState>
)
