package ch.hygro.padelscore.health

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ch.hygro.padelscore.storage.MatchHistoryRepository
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Stores workout data privately on the watch. No phone or cloud transmission. */
class LocalWorkoutRepository internal constructor(
    private val preferences: SharedPreferences,
    // Separate storage: step counts are not added to the existing sync payload.
    private val stepPreferences: SharedPreferences,
    private val deletions: SharedPreferences,
    private var historyPreferences: SharedPreferences? = null
) {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            "local_workout_health",
            Context.MODE_PRIVATE
        ),
        stepPreferences = context.applicationContext.getSharedPreferences(
            "local_workout_steps",
            Context.MODE_PRIVATE
        ),
        deletions = context.applicationContext.getSharedPreferences(
            "workout_deletions",
            Context.MODE_PRIVATE
        ),
        historyPreferences = context.applicationContext.getSharedPreferences(
            MatchHistoryRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    )

    @Synchronized
    internal fun attachHistoryPreferences(historyPreferences: SharedPreferences) {
        if (this.historyPreferences == null) {
            this.historyPreferences = historyPreferences
        }
    }

    @Synchronized
    fun isDeleted(matchId: Long): Boolean {
        if (matchId <= 0L) return false
        reconcilePendingDeletions()
        if (deletions.getBoolean(matchId.toString(), false)) return true
        return !isInFlightDeletion(matchId) &&
            deletions.getBoolean("$PENDING_PREFIX$matchId", false) &&
            isPresentInHistory(matchId) == false
    }

    @Synchronized
    internal fun isDeletionPending(matchId: Long): Boolean {
        if (matchId <= 0L) return false
        reconcilePendingDeletions()
        if (isDeleted(matchId)) return false
        return deletions.getBoolean("$PENDING_PREFIX$matchId", false) &&
            isInFlightDeletion(matchId)
    }

    @Synchronized
    internal fun hasPendingDeletionMarker(matchId: Long): Boolean {
        if (matchId <= 0L) return false
        return deletions.getBoolean("$PENDING_PREFIX$matchId", false)
    }

    @Synchronized
    fun deletedMatchIds(): List<Long> {
        reconcilePendingDeletions()
        val result = LinkedHashSet<Long>()
        for ((key, value) in deletions.all) {
            if (value != true) continue
            val confirmedId = key.toLongOrNull()
            if (confirmedId != null && confirmedId > 0L) {
                result.add(confirmedId)
                continue
            }
            if (key.startsWith(PENDING_PREFIX)) {
                val pendingId = key.removePrefix(PENDING_PREFIX).toLongOrNull() ?: continue
                if (pendingId > 0L &&
                    !isInFlightDeletion(pendingId) &&
                    isPresentInHistory(pendingId) == false
                ) {
                    result.add(pendingId)
                }
            }
        }
        return result.toList()
    }

    fun activeMatchId(): Long = preferences.getLong("active_start", 0L)

    fun recordedSampleAverage(): Int? {
        val samples = JSONArray(preferences.getString("active_samples", "[]") ?: "[]")
        val values = (0 until samples.length()).mapNotNull { i ->
            samples.optJSONObject(i)?.optInt("bpm", 0)?.takeIf { bpm -> bpm in 30..240 }
        }
        return values.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    fun lastSampleTime(): Long {
        val samples = JSONArray(preferences.getString("active_samples", "[]") ?: "[]")
        return (0 until samples.length()).maxOfOrNull {
            samples.optJSONObject(it)?.optLong("timeMillis", 0L) ?: 0L
        } ?: activeMatchId()
    }

    @Synchronized
    fun begin(startTimeMillis: Long) {
        if (startTimeMillis <= 0L || isDeleted(startTimeMillis)) return
        if (preferences.getLong("active_start", 0L) == startTimeMillis) return
        val completed = JSONArray(preferences.getString("completed_workouts", "[]"))
        val previous = (0 until completed.length()).mapNotNull { completed.optJSONObject(it) }
            .firstOrNull { it.optLong("startTimeMillis", 0L) == startTimeMillis }
        if (previous == null) stepPreferences.edit { remove(startTimeMillis.toString()) }
        preferences.edit {
            putLong("active_start", startTimeMillis)
            putString("active_samples", previous?.optJSONArray("samples")?.toString() ?: "[]")
            remove("active_end")
            remove("active_average")
            previous?.optInt("averageHeartRate", 0)?.takeIf { it in 30..240 }
                ?.let { putInt("active_average", it) }
        }
    }

    @Synchronized
    fun addSamples(values: List<Pair<Long, Int>>) {
        if (preferences.getLong("active_start", 0L) <= 0L || values.isEmpty()) return
        val samples = JSONArray(preferences.getString("active_samples", "[]") ?: "[]")
        val seen = HashSet<Long>()
        for (i in 0 until samples.length()) {
            seen.add(samples.optJSONObject(i)?.optLong("timeMillis", 0L) ?: 0L)
        }
        for ((timeMillis, bpm) in values) {
            if (bpm in 30..240 && timeMillis >= activeMatchId() &&
                timeMillis < preferences.getLong("active_end", Long.MAX_VALUE) &&
                samples.length() < 20000 && seen.add(timeMillis)) {
                samples.put(JSONObject().put("timeMillis", timeMillis).put("bpm", bpm))
            }
        }
        preferences.edit { putString("active_samples", samples.toString()) }
    }

    @Synchronized
    fun updateAverage(bpm: Int?) {
        if (bpm != null && bpm in 30..240 && preferences.getLong("active_start", 0L) > 0L) {
            preferences.edit { putInt("active_average", bpm) }
        }
    }

    /** Exercise cumulative total, not a delta and not the daily step counter. */
    @Synchronized
    fun updateSteps(total: Long) {
        val matchId = preferences.getLong("active_start", 0L)
        if (matchId <= 0L || total < 0L || isDeleted(matchId)) return
        val key = matchId.toString()
        val previous = stepPreferences.getLong(key, -1L)
        if (total > previous) {
            stepPreferences.edit { putLong(key, total) }
        }
    }

    @Synchronized
    fun stepsForMatch(matchId: Long): Long? {
        if (matchId <= 0L || isDeleted(matchId)) return null
        return stepPreferences.getLong(matchId.toString(), -1L).takeIf { it >= 0L }
    }

    fun setEndBoundary(endTimeMillis: Long) {
        preferences.edit { putLong("active_end", endTimeMillis) }
    }

    @Synchronized
    fun finish(endTimeMillis: Long = System.currentTimeMillis()) {
        val start = preferences.getLong("active_start", 0L)
        if (start <= 0L) return
        if (isDeleted(start)) {
            removeMatch(start, deleteHealthData = false)
            return
        }
        val expiredStepKeys = stepPreferences.all.keys
            .mapNotNull { key -> key.toLongOrNull()?.let { key to it } }
            .sortedByDescending { it.second }
            .drop(100)
        if (expiredStepKeys.isNotEmpty()) {
            stepPreferences.edit {
                expiredStepKeys.forEach { (key, _) -> remove(key) }
            }
        }
        val cutoff = preferences.getLong("active_end", endTimeMillis).coerceAtLeast(start + 1L)
        val rawSamples = JSONArray(preferences.getString("active_samples", "[]"))
        val samples = JSONArray()
        for (i in 0 until rawSamples.length()) {
            val sample = rawSamples.optJSONObject(i) ?: continue
            if (sample.optLong("timeMillis") in start until cutoff) samples.put(sample)
        }
        // Arithmetic mean of retained, valid samples across all recording segments.
        val average = if (samples.length() > 0) {
            (0 until samples.length()).map { samples.getJSONObject(it).getInt("bpm") }.average().toInt()
        } else preferences.getInt("active_average", 0)
        val maximum = (0 until samples.length()).maxOfOrNull { i -> samples.getJSONObject(i).optInt("bpm", 0) } ?: 0
        val revision = preferences.getLong("revision_$start", 0L) + 1L
        val workout = JSONObject()
            .put("revision", revision)
            .put("startTimeMillis", start)
            .put("endTimeMillis", cutoff)
            .put("averageHeartRate", average)
            .put("maximumHeartRate", maximum)
            .put("sampleCount", samples.length())
            .put("samples", samples)
        val history = JSONArray(preferences.getString("completed_workouts", "[]"))
        val updated = JSONArray()
        updated.put(workout)
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optLong("startTimeMillis", 0L) != start && updated.length() < 50) {
                updated.put(item)
            }
        }
        preferences.edit {
            putLong("revision_$start", revision)
            putString("completed_workouts", updated.toString())
            remove("active_start")
            remove("active_samples")
            remove("active_average")
        }
    }

    @Synchronized
    fun averageHeartRateForMatch(matchId: Long): Int? {
        if (matchId <= 0L || isDeleted(matchId)) return null
        if (preferences.getLong("active_start", 0L) == matchId) {
            return preferences.getInt("active_average", 0).takeIf { it in 30..240 }
        }
        val history = JSONArray(preferences.getString("completed_workouts", "[]"))
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optLong("startTimeMillis", 0L) == matchId) {
                return item.optInt("averageHeartRate", 0).takeIf { it in 30..240 }
            }
        }
        return null
    }

    /** Match ID is the original match start time in milliseconds. */
    @Synchronized
    fun maximumHeartRateForMatch(matchId: Long): Int? {
        if (matchId <= 0L || isDeleted(matchId)) return null
        if (preferences.getLong("active_start", 0L) == matchId) {
            val samples = JSONArray(preferences.getString("active_samples", "[]") ?: "[]")
            return (0 until samples.length())
                .maxOfOrNull { samples.optJSONObject(it)?.optInt("bpm", 0) ?: 0 }
                ?.takeIf { it > 0 }
        }
        val history = JSONArray(preferences.getString("completed_workouts", "[]"))
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optLong("startTimeMillis", 0L) == matchId) {
                return item.optInt("maximumHeartRate", 0).takeIf { it > 0 }
            }
        }
        return null
    }

    internal inline fun <T> withInFlightDeletion(matchId: Long, block: () -> T): T {
        if (matchId <= 0L) return block()
        beginInFlightDeletion(matchId)
        return try {
            block()
        } finally {
            endInFlightDeletion(matchId)
        }
    }

    @Synchronized
    internal fun prepareDeletion(matchId: Long) {
        if (matchId <= 0L) return
        if (!isInFlightDeletion(matchId)) {
            reconcilePendingDeletions()
        }
        if (deletions.getBoolean(matchId.toString(), false)) return
        val persisted = deletions.edit().putBoolean("$PENDING_PREFIX$matchId", true).commit()
        check(persisted) {
            "Cannot persist pending workout deletion"
        }
    }

    @Synchronized
    internal fun clearPendingDeletion(matchId: Long) {
        if (matchId <= 0L) return
        val cleared = deletions.edit().remove("$PENDING_PREFIX$matchId").commit()
        check(cleared) {
            "Cannot clear pending workout deletion"
        }
    }

    @Synchronized
    internal fun confirmDeletion(matchId: Long) {
        if (matchId <= 0L) return
        val persisted = deletions.edit()
            .putBoolean(matchId.toString(), true)
            .remove("$PENDING_PREFIX$matchId")
            .commit()
        check(persisted) {
            "Cannot persist workout deletion"
        }
        purgeWorkoutData(setOf(matchId))
    }

    /**
     * Reconciles any interrupted two-phase deletions against the persisted match history:
     * - If a pending deletion's match was already removed from history, promotes it to confirmed
     *   and purges local workout/step data.
     * - If a pending deletion's match is still in history (and not currently in-flight), rolls back
     *   the pending marker.
     * - Ensures local workout/step data for all confirmed deletions are purged.
     *
     * @return `true` if at least one pending deletion was newly promoted to confirmed.
     */
    @Synchronized
    internal fun reconcilePendingDeletions(): Boolean {
        var newlyConfirmed = false
        val snapshot = deletions.all
        val confirmedIds = LinkedHashSet<Long>()
        val pendingIdsToConfirm = LinkedHashSet<Long>()
        val pendingKeysToClear = LinkedHashSet<String>()

        for ((key, value) in snapshot) {
            val confirmedId = key.toLongOrNull()
            if (confirmedId != null && confirmedId > 0L && value == true) {
                confirmedIds.add(confirmedId)
            }
        }

        for ((key, value) in snapshot) {
            if (!key.startsWith(PENDING_PREFIX)) continue
            val pendingId = key.removePrefix(PENDING_PREFIX).toLongOrNull()
            if (pendingId == null || pendingId <= 0L || value != true) {
                pendingKeysToClear.add(key)
                continue
            }
            if (pendingId in confirmedIds) {
                pendingKeysToClear.add(key)
                continue
            }
            if (isInFlightDeletion(pendingId)) continue
            when (isPresentInHistory(pendingId)) {
                false -> {
                    pendingIdsToConfirm.add(pendingId)
                    confirmedIds.add(pendingId)
                }
                true -> pendingKeysToClear.add(key)
                null -> Unit
            }
        }

        if (pendingIdsToConfirm.isNotEmpty() || pendingKeysToClear.isNotEmpty()) {
            deletions.edit(commit = true) {
                for (id in pendingIdsToConfirm) {
                    putBoolean(id.toString(), true)
                    remove("$PENDING_PREFIX$id")
                }
                for (key in pendingKeysToClear) {
                    remove(key)
                }
            }
            if (pendingIdsToConfirm.isNotEmpty() &&
                pendingIdsToConfirm.all { deletions.getBoolean(it.toString(), false) }
            ) {
                newlyConfirmed = true
            }
        }

        if (confirmedIds.isNotEmpty()) {
            purgeWorkoutData(confirmedIds)
        }
        return newlyConfirmed
    }

    @Synchronized
    internal fun isPresentInHistory(matchId: Long): Boolean? {
        val historyPrefs = historyPreferences ?: return null
        return try {
            if (!historyPrefs.contains(MatchHistoryRepository.KEY_HISTORY_JSON)) {
                return false
            }
            val storedJson = historyPrefs.getString(MatchHistoryRepository.KEY_HISTORY_JSON, null)
                ?: return null
            val array = JSONArray(storedJson)
            var found = false
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getLong("matchId")
                if (id <= 0L) return null
                if (id == matchId) {
                    found = true
                }
            }
            found
        } catch (_: Exception) {
            null
        }
    }

    private fun purgeWorkoutData(matchIds: Set<Long>) {
        if (matchIds.isEmpty()) return
        val stepKeysToRemove = matchIds.map { it.toString() }.filter { stepPreferences.contains(it) }
        if (stepKeysToRemove.isNotEmpty()) {
            stepPreferences.edit(commit = true) {
                for (key in stepKeysToRemove) {
                    remove(key)
                }
            }
        }

        val activeMatches = preferences.getLong("active_start", 0L) in matchIds
        val historyJson = preferences.getString("completed_workouts", null)
        val history = if (historyJson != null) JSONArray(historyJson) else JSONArray()
        var foundInHistory = false
        val updated = JSONArray()
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optLong("startTimeMillis", 0L) in matchIds) {
                foundInHistory = true
            } else {
                updated.put(item)
            }
        }
        if (activeMatches || foundInHistory) {
            preferences.edit(commit = true) {
                if (foundInHistory) {
                    putString("completed_workouts", updated.toString())
                }
                if (activeMatches) {
                    remove("active_start")
                    remove("active_samples")
                    remove("active_average")
                    remove("active_end")
                }
            }
        }
    }

    @Synchronized
    fun removeMatch(matchId: Long, deleteHealthData: Boolean = true) {
        if (matchId <= 0L) return
        if (deleteHealthData) {
            confirmDeletion(matchId)
        } else {
            purgeWorkoutData(setOf(matchId))
        }
    }

    fun completedWorkoutsJson(): String = preferences.getString("completed_workouts", "[]") ?: "[]"

    companion object {
        private const val PENDING_PREFIX = "pending_"
        private val inFlightDeletions = ConcurrentHashMap<Long, Int>()

        internal fun beginInFlightDeletion(matchId: Long) {
            if (matchId <= 0L) return
            inFlightDeletions.compute(matchId) { _, count -> (count ?: 0) + 1 }
        }

        internal fun endInFlightDeletion(matchId: Long) {
            if (matchId <= 0L) return
            inFlightDeletions.computeIfPresent(matchId) { _, count ->
                if (count <= 1) null else count - 1
            }
        }

        internal fun isInFlightDeletion(matchId: Long): Boolean =
            (inFlightDeletions[matchId] ?: 0) > 0
    }
}
