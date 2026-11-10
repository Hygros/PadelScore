package ch.hygro.padelscore.health

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import ch.hygro.padelscore.storage.MatchHistoryRepository
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Persistent watch-authoritative snapshot independent of Health Connect consent. */
object MatchHistorySync {
    private val executor = Executors.newSingleThreadExecutor()
    internal const val KEY_GENERATION = "match_history_sync_gen"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(
        "settings_prefs", Context.MODE_PRIVATE
    ).getBoolean("match_history_sync_enabled", false)

    internal fun getOrInitGeneration(prefs: android.content.SharedPreferences): Long {
        val existing = prefs.getLong(KEY_GENERATION, 0L)
        if (existing > 0L) return existing
        val created = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
        prefs.edit(commit = true) { putLong(KEY_GENERATION, created) }
        return created
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        executor.execute {
            try {
                val prefs = app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                val allowed = enabled(app)
                val publishedClear = prefs.getBoolean("match_history_sync_published_clear", false)
                if (!allowed && publishedClear) return@execute

                val generation = getOrInitGeneration(prefs)
                val json = if (allowed) {
                    MatchHistoryRepository(app).exportForSync()
                } else null

                val revision = prefs.getLong("match_history_sync_rev", 0L) + 1L
                @Suppress("UseKtx")
                val editor = prefs.edit()
                editor.putLong("match_history_sync_rev", revision)
                check(editor.commit()) {
                    "Cannot persist match history sync revision"
                }

                val request = PutDataMapRequest.create("/padelscore/history/snapshot").apply {
                    dataMap.putBoolean("enabled", allowed)
                    dataMap.putLong("revision", revision)
                    dataMap.putLong("source_generation", generation)
                    if (allowed && json != null) {
                        dataMap.putAsset("history", Asset.createFromBytes(json.toByteArray(Charsets.UTF_8)))
                    }
                }.asPutDataRequest().setUrgent()
                if (allowed && !enabled(app)) return@execute
                Tasks.await(Wearable.getDataClient(app).putDataItem(request), 30, TimeUnit.SECONDS)
                prefs.edit { putBoolean("match_history_sync_published_clear", !allowed) }
            } catch (e: Exception) {
                Log.w("MatchHistorySync", "Transfer deferred; local matches retained", e)
            }
        }
    }
}
