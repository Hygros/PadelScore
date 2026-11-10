package ch.hygro.padelscore.storage

import android.content.Context
import android.content.SharedPreferences
import ch.hygro.padelscore.health.LocalWorkoutRepository
import ch.hygro.padelscore.health.WorkoutDataSender
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores up to [MAX_HISTORY_ENTRIES] completed or early-stopped matches in local `SharedPreferences`
 * and enriches loaded entries with workout metrics from [LocalWorkoutRepository].
 */
class MatchHistoryRepository internal constructor(
    private val preferences: SharedPreferences,
    private val workouts: LocalWorkoutRepository,
    private val scheduleDeletionSync: () -> Unit,
    private val scheduleHistorySync: () -> Unit = {},
    private val encodeStoppedState: (MatchState) -> JSONObject = {
        error("Stopped-state encoding requires a MatchRepository")
    },
    private val decodeStoppedState: (JSONObject) -> MatchState = {
        error("Stopped-state decoding requires a MatchRepository")
    },
    private val onBeforeHistoryCommit: (() -> Unit)? = null
) {
    init {
        workouts.attachHistoryPreferences(preferences)
    }

    constructor(context: Context) : this(
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        workouts = LocalWorkoutRepository(context),
        scheduleDeletionSync = {
            WorkoutDataSender(context).scheduleDeletionSync()
        },
        scheduleHistorySync = {
            ch.hygro.padelscore.health.MatchHistorySync.schedule(context)
        },
        encodeStoppedState = { MatchRepository(context).encodeState(it) },
        decodeStoppedState = { MatchRepository(context).decodeState(it) }
    )

    @Volatile
    private var cachedHistorySnapshot: Pair<String, List<MatchHistoryEntry>>? = null

    private fun reconcileDeletions() {
        if (workouts.reconcilePendingDeletions()) {
            scheduleDeletionSync()
        }
    }

    fun loadEntries(): List<MatchHistoryEntry> {
        reconcileDeletions()
        val storedJson = preferences.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val snapshot = cachedHistorySnapshot
            val baseEntries = if (snapshot != null && snapshot.first == storedJson) {
                snapshot.second
            } else {
                val array = JSONArray(storedJson)
                buildList {
                    for (index in 0 until array.length()) {
                        runCatching { add(decodeEntry(array.getJSONObject(index))) }
                    }
                }.also { decoded ->
                    cachedHistorySnapshot = storedJson to decoded
                }
            }
            baseEntries.map { entry ->
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
        reconcileDeletions()
        if (state.startedAtEpochMillis <= 0L || workouts.isDeleted(state.startedAtEpochMillis)) return
        val existingEntries = loadEntriesOrThrow()
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
        val updated = existingEntries
            .filterNot { it.matchId == entry.matchId }
            .plus(entry)
            .sortedByDescending { it.finishedAtEpochMillis }
            .take(MAX_HISTORY_ENTRIES)
        saveEntries(updated)
    }

    fun removeByMatchId(matchId: Long, deleteHealthData: Boolean = true) {
        if (matchId > 0L) {
            reconcileDeletions()
            val remaining = loadEntriesOrThrow().filterNot { it.matchId == matchId }
            val existedInHistory = workouts.isPresentInHistory(matchId) == true
            val wasAlreadyDeleted = workouts.isDeleted(matchId)
            val shouldPrepareDeletion = deleteHealthData && !wasAlreadyDeleted && existedInHistory
            try {
                workouts.withInFlightDeletion(matchId) {
                    if (shouldPrepareDeletion) {
                        workouts.prepareDeletion(matchId)
                    }
                    onBeforeHistoryCommit?.invoke()
                    saveEntries(remaining)
                }
            } catch (exception: Exception) {
                if (shouldPrepareDeletion && workouts.hasPendingDeletionMarker(matchId)) {
                    try {
                        workouts.clearPendingDeletion(matchId)
                    } catch (rollbackException: Throwable) {
                        exception.addSuppressed(rollbackException)
                    }
                }
                throw exception
            }
            if (deleteHealthData) {
                workouts.confirmDeletion(matchId)
                scheduleDeletionSync()
            }
        }
    }

    private fun loadEntriesOrThrow(): List<MatchHistoryEntry> {
        if (!preferences.contains(KEY_HISTORY_JSON)) return emptyList()
        val storedJson = preferences.getString(KEY_HISTORY_JSON, null)
            ?: error("Could not read match history")
        val array = JSONArray(storedJson)
        return buildList {
            for (index in 0 until array.length()) {
                add(decodeEntry(array.getJSONObject(index)))
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
    }

    fun exportForSync(): String =
        JSONArray().apply { loadEntriesOrThrow().forEach { put(encodeEntry(it)) } }.toString()

    private fun saveEntries(entries: List<MatchHistoryEntry>) {
        val array = JSONArray().apply { entries.forEach { put(encodeEntry(it)) } }
        val result = preferences.edit().putString(KEY_HISTORY_JSON, array.toString()).commit()
        check(result) { "Could not persist match history" }
        scheduleHistorySync()
    }

    private fun encodeEntry(entry: MatchHistoryEntry) = JSONObject()
        .put("matchId", entry.matchId)
        .put("startedAtEpochMillis", entry.startedAtEpochMillis)
        .put("finishedAtEpochMillis", entry.finishedAtEpochMillis)
        .put("opponentSets", entry.opponentSets)
        .put("ourSets", entry.ourSets)
        .put("completedSets", JSONArray().apply {
            entry.completedSets.forEach { put(MatchJsonCodec.encodeSetResult(it)) }
        })
        .put("opponentMatchTieBreakPoints", entry.opponentMatchTieBreakPoints)
        .put("ourMatchTieBreakPoints", entry.ourMatchTieBreakPoints)
        .put("configuration", MatchJsonCodec.encodeConfiguration(entry.configuration))
        .put("totalPointsPlayed", entry.totalPointsPlayed)
        .put("pointHistory", PointHistoryCodec.encode(entry.pointHistory))
        .put("totalGamesPlayed", entry.totalGamesPlayed)
        .put("averageHeartRate", entry.averageHeartRate ?: -1)
        .put("maximumHeartRate", entry.maximumHeartRate ?: -1)
        .put("steps", entry.steps ?: -1L)
        .put(
            "stoppedState",
            entry.stoppedState?.let(encodeStoppedState)
        )

    private fun decodeEntry(json: JSONObject): MatchHistoryEntry {
        val setsJson = json.optJSONArray("completedSets") ?: JSONArray()
        val sets = buildList {
            for (index in 0 until setsJson.length()) {
                runCatching { add(MatchJsonCodec.decodeSetResult(setsJson.getJSONObject(index))) }
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
            configuration = MatchJsonCodec.decodeConfiguration(
                json.optJSONObject("configuration") ?: JSONObject()
            ),
            totalPointsPlayed = json.optInt("totalPointsPlayed", 0),
            pointHistory = PointHistoryCodec.decode(json.optJSONArray("pointHistory")),
            totalGamesPlayed = json.optInt("totalGamesPlayed", 0),
            averageHeartRate = if (hr > 0) hr else null,
            maximumHeartRate = json.optInt("maximumHeartRate", -1).takeIf { it > 0 },
            steps = json.optLong("steps", -1L).takeIf { it >= 0L },
            stoppedState = json.optJSONObject("stoppedState")?.let(decodeStoppedState)
        )
    }

    companion object {
        internal const val PREFERENCES_NAME = "padel_score_history"
        internal const val KEY_HISTORY_JSON = "match_history_json"
        private const val MAX_HISTORY_ENTRIES = 100
    }
}
