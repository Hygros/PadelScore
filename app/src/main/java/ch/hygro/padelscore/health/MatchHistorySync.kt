package ch.hygro.padelscore.health

import android.content.Context
import android.util.Log
import ch.hygro.padelscore.storage.MatchHistoryRepository
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Persistent watch-authoritative snapshot independent of Health Connect consent. */
object MatchHistorySync {
 private val executor = Executors.newSingleThreadExecutor()

 fun enabled(context: Context): Boolean = context.getSharedPreferences(
 "settings_prefs", Context.MODE_PRIVATE
 ).getBoolean("match_history_sync_enabled", false)

 fun schedule(context: Context) {
 val app = context.applicationContext
 executor.execute {
 try {
 val prefs = app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
 val allowed = enabled(app)
 val publishedClear = prefs.getBoolean("match_history_sync_published_clear", false)
 if (!allowed && publishedClear) return@execute

 val revision = prefs.getLong("match_history_sync_rev", 0) + 1
 prefs.edit().putLong("match_history_sync_rev", revision).apply()

 val request = PutDataMapRequest.create("/padelscore/history/snapshot").apply {
 dataMap.putBoolean("enabled", allowed)
 dataMap.putLong("revision", revision)
 if (allowed) {
 val json = MatchHistoryRepository(app).exportForSync()
 dataMap.putAsset("history", Asset.createFromBytes(json.toByteArray(Charsets.UTF_8)))
 }
 }.asPutDataRequest().setUrgent()
 if (allowed && !enabled(app)) return@execute
 Tasks.await(Wearable.getDataClient(app).putDataItem(request), 30, TimeUnit.SECONDS)
 prefs.edit().putBoolean("match_history_sync_published_clear", !allowed).apply()
 } catch (e: Exception) {
 Log.w("MatchHistorySync", "Transfer deferred; local matches retained", e)
 }
 }
 }
}
