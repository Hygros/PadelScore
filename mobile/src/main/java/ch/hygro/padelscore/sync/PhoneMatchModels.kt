package ch.hygro.padelscore.sync

import org.json.JSONArray
import org.json.JSONObject

data class PhoneSetResult(
    val opponentGames: Int,
    val ourGames: Int,
    val wasTieBreak: Boolean = false,
    val opponentTieBreakPoints: Int = 0,
    val ourTieBreakPoints: Int = 0
)

data class PhoneGamePoint(
    val number: Int,
    val setNumber: Int,
    val gameNumber: Int,
    val scoringTeamUs: Boolean,
    val ourScore: String,
    val opponentScore: String,
    val ourRallies: Int,
    val opponentRallies: Int,
    val numeric: Boolean,
    val matchTieBreak: Boolean,
    val gameEnded: Boolean
)

data class PhoneStoppedState(
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
    val isMatchTieBreak: Boolean = false,
    val advantageTeamUs: Boolean? = null,
    val completedSets: List<PhoneSetResult> = emptyList()
)

data class PhoneMatchHistoryEntry(
    val matchId: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val opponentSets: Int,
    val ourSets: Int,
    val completedSets: List<PhoneSetResult>,
    val opponentMatchTieBreakPoints: Int,
    val ourMatchTieBreakPoints: Int,
    val totalPointsPlayed: Int,
    val pointHistory: List<PhoneGamePoint>,
    val totalGamesPlayed: Int,
    val averageHeartRate: Int?,
    val maximumHeartRate: Int?,
    val steps: Long?,
    val stoppedState: PhoneStoppedState?,
    val scoringMode: String = "STAR_POINT",
    val matchFormat: String = "BEST_OF_THREE",
    val gamesRequiredToWinSet: Int = 6
) {
    val weWon: Boolean get() = ourSets > opponentSets
}

object PhoneMatchHistoryParser {

    fun parseEntries(rawJson: String?): List<PhoneMatchHistoryEntry> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                runCatching { decodeEntry(obj) }.getOrNull()?.let { add(it) }
            }
        }.sortedByDescending { it.finishedAtEpochMillis }
    }

    internal fun decodeEntry(json: JSONObject): PhoneMatchHistoryEntry {
        val setsArray = json.optJSONArray("completedSets") ?: JSONArray()
        val sets = decodeSetResults(setsArray)
        val configJson = json.optJSONObject("configuration") ?: JSONObject()
        val avgHr = json.optInt("averageHeartRate", -1).takeIf { it > 0 }
        val maxHr = json.optInt("maximumHeartRate", -1).takeIf { it > 0 }
        val steps = json.optLong("steps", -1L).takeIf { it >= 0L }
        val stoppedJson = json.optJSONObject("stoppedState")

        return PhoneMatchHistoryEntry(
            matchId = json.getLong("matchId"),
            startedAtEpochMillis = json.optLong("startedAtEpochMillis", 0L),
            finishedAtEpochMillis = json.optLong("finishedAtEpochMillis", 0L),
            opponentSets = json.optInt("opponentSets", 0),
            ourSets = json.optInt("ourSets", 0),
            completedSets = sets,
            opponentMatchTieBreakPoints = json.optInt("opponentMatchTieBreakPoints", 0),
            ourMatchTieBreakPoints = json.optInt("ourMatchTieBreakPoints", 0),
            totalPointsPlayed = json.optInt("totalPointsPlayed", 0),
            pointHistory = decodePointHistory(json.optJSONArray("pointHistory")),
            totalGamesPlayed = json.optInt("totalGamesPlayed", 0),
            averageHeartRate = avgHr,
            maximumHeartRate = maxHr,
            steps = steps,
            stoppedState = stoppedJson?.let { decodeStoppedState(it) },
            scoringMode = configJson.optString("scoringMode", "STAR_POINT").ifBlank { "STAR_POINT" },
            matchFormat = configJson.optString("matchFormat", "BEST_OF_THREE").ifBlank { "BEST_OF_THREE" },
            gamesRequiredToWinSet = configJson.optInt("gamesRequiredToWinSet", 6)
        )
    }

    private fun decodeSetResults(array: JSONArray): List<PhoneSetResult> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            add(
                PhoneSetResult(
                    opponentGames = obj.optInt("opponentGames", 0),
                    ourGames = obj.optInt("ourGames", 0),
                    wasTieBreak = obj.optBoolean("wasTieBreak", false),
                    opponentTieBreakPoints = obj.optInt("opponentTieBreakPoints", 0),
                    ourTieBreakPoints = obj.optInt("ourTieBreakPoints", 0)
                )
            )
        }
    }

    private fun decodePointHistory(array: JSONArray?): List<PhoneGamePoint> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            runCatching {
                PhoneGamePoint(
                    number = obj.getInt("number"),
                    setNumber = obj.getInt("setNumber"),
                    gameNumber = obj.getInt("gameNumber"),
                    scoringTeamUs = obj.optString("scoringTeam", "US") == "US",
                    ourScore = obj.getString("ourScore"),
                    opponentScore = obj.getString("opponentScore"),
                    ourRallies = obj.optInt("ourRallies", 0),
                    opponentRallies = obj.optInt("opponentRallies", 0),
                    numeric = obj.optBoolean("numeric", false),
                    matchTieBreak = obj.optBoolean("matchTieBreak", false),
                    gameEnded = obj.optBoolean("gameEnded", false)
                )
            }.getOrNull()?.let { add(it) }
        }
    }

    private fun decodeStoppedState(json: JSONObject): PhoneStoppedState {
        val advRaw = json.optString("advantageTeam", "")
        val advUs = when (advRaw) {
            "US" -> true
            "OPPONENT" -> false
            else -> null
        }
        return PhoneStoppedState(
            opponentPoints = json.optInt("opponentPoints", 0),
            ourPoints = json.optInt("ourPoints", 0),
            opponentGames = json.optInt("opponentGames", 0),
            ourGames = json.optInt("ourGames", 0),
            opponentSets = json.optInt("opponentSets", 0),
            ourSets = json.optInt("ourSets", 0),
            isTieBreak = json.optBoolean("isTieBreak", false),
            opponentTieBreakPoints = json.optInt("opponentTieBreakPoints", 0),
            ourTieBreakPoints = json.optInt("ourTieBreakPoints", 0),
            opponentMatchTieBreakPoints = json.optInt("opponentMatchTieBreakPoints", 0),
            ourMatchTieBreakPoints = json.optInt("ourMatchTieBreakPoints", 0),
            isMatchTieBreak = json.optString("phase", "") == "MATCH_TIE_BREAK",
            advantageTeamUs = advUs,
            completedSets = decodeSetResults(json.optJSONArray("completedSets") ?: JSONArray())
        )
    }

    fun pointText(state: PhoneStoppedState, us: Boolean, advAbbr: String = "AD"): String {
        if (state.isMatchTieBreak) {
            return if (us) state.ourMatchTieBreakPoints.toString() else state.opponentMatchTieBreakPoints.toString()
        }
        if (state.isTieBreak) {
            return if (us) state.ourTieBreakPoints.toString() else state.opponentTieBreakPoints.toString()
        }
        if (state.advantageTeamUs != null && state.advantageTeamUs == us) {
            return advAbbr
        }
        val normalPoints = if (us) state.ourPoints else state.opponentPoints
        return when (normalPoints) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            else -> "40"
        }
    }

    fun earlyStopBalance(entry: PhoneMatchHistoryEntry): Int {
        val state = entry.stoppedState ?: return 0
        val sets = state.ourSets.compareTo(state.opponentSets)
        if (sets != 0) return sets
        if (state.isMatchTieBreak) {
            return state.ourMatchTieBreakPoints.compareTo(state.opponentMatchTieBreakPoints)
        }
        val games = state.ourGames.compareTo(state.opponentGames)
        if (games != 0) return games
        if (state.isTieBreak) {
            return state.ourTieBreakPoints.compareTo(state.opponentTieBreakPoints)
        }
        fun rank(score: String): Int = when (score) {
            "0" -> 0
            "15" -> 1
            "30" -> 2
            "40" -> 3
            "AD" -> 4
            else -> -1
        }
        val us = rank(pointText(state, true))
        val opp = rank(pointText(state, false))
        return if (us >= 0 && opp >= 0) us.compareTo(opp) else 0
    }

    /** Recover the unchanged losing score only from a contiguous record of the same game. */
    fun recordedScore(points: List<PhoneGamePoint>, index: Int, us: Boolean): String? {
        val point = points.getOrNull(index) ?: return null
        val score = if (us) point.ourScore else point.opponentScore
        if (score != "-") return score
        val winnerIsUs = point.scoringTeamUs
        if (!point.gameEnded || us == winnerIsUs || point.numeric) return null
        val previous = points.getOrNull(index - 1) ?: return null
        if (previous.number + 1 != point.number ||
            previous.gameNumber != point.gameNumber ||
            previous.setNumber != point.setNumber
        ) return null
        val previousScore = if (us) previous.ourScore else previous.opponentScore
        return previousScore.takeIf { it in listOf("0", "15", "30", "40", "AD") }
    }

    /** Score categories are equally spaced, not numeric distances between 15/30/40. */
    fun chartScoreLevel(text: String, numeric: Boolean): Int? = if (numeric) {
        text.toIntOrNull()?.takeIf { it >= 0 }
    } else {
        when (text) {
            "0" -> 0
            "15" -> 1
            "30" -> 2
            "40" -> 3
            "AD" -> 4
            "GAME" -> 5
            else -> null
        }
    }

    fun groupPointsByGame(points: List<PhoneGamePoint>): List<List<PhoneGamePoint>> =
        points.groupBy { it.setNumber to it.gameNumber }.values.toList()

    fun durationHoursAndMinutes(startedAtEpochMillis: Long, finishedAtEpochMillis: Long): Pair<Long, Long> {
        val seconds = ((finishedAtEpochMillis - startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return hours to minutes
    }
}
