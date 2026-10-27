package ch.hygro.padelscore.health

import android.content.Context
import org.json.JSONArray
import androidx.core.content.edit

import org.json.JSONObject

/** Stores workout data privately on the watch. No phone or cloud transmission. */
class LocalWorkoutRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("local_workout_health", Context.MODE_PRIVATE)

    // Separate storage: step counts are not added to the existing sync payload.
    private val stepPreferences = context.applicationContext.getSharedPreferences(
        "local_workout_steps", Context.MODE_PRIVATE
    )

    private val deletions = context.applicationContext.getSharedPreferences(
        "workout_deletions", Context.MODE_PRIVATE
    )

    fun isDeleted(matchId: Long): Boolean = deletions.getBoolean(matchId.toString(), false)

    fun deletedMatchIds(): List<Long> = deletions.all.keys.mapNotNull { it.toLongOrNull() }

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
        if (matchId <= 0L) return null
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
        if (matchId <= 0L) return null
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

    @Synchronized
    fun removeMatch(matchId: Long, deleteHealthData: Boolean = true) {
        if (matchId <= 0L) return
        if (deleteHealthData) {
            check(deletions.edit { putBoolean(matchId.toString(), true) }.let { true }) {
                "Cannot persist workout deletion"
            }
        }
        stepPreferences.edit { remove(matchId.toString()) }
        val history = JSONArray(preferences.getString("completed_workouts", "[]"))
        val updated = JSONArray()
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optLong("startTimeMillis", 0L) != matchId) updated.put(item)
        }
        preferences.edit {
            putString("completed_workouts", updated.toString())
            if (preferences.getLong("active_start", 0L) == matchId) {
                remove("active_start")
                remove("active_samples")
                remove("active_average")
            }
        }
    }

    fun completedWorkoutsJson(): String = preferences.getString("completed_workouts", "[]") ?: "[]"
}
