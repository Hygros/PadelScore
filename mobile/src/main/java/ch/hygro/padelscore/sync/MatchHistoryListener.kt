package ch.hygro.padelscore.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PhoneMatchHistory {
 private val executor: ExecutorService = Executors.newSingleThreadExecutor()
 const val PATH = "/padelscore/history/snapshot"
 fun preferences(context: Context): SharedPreferences =
  context.getSharedPreferences("match_history_sync", Context.MODE_PRIVATE)

 fun request(context: Context) {
 val app = context.applicationContext
 executor.execute {
 try {
 val client = Wearable.getDataClient(app)
 val items = Tasks.await(client.dataItems, 20, TimeUnit.SECONDS)
 try {
 for (item in items) if (item.uri.path == PATH) receive(app, item)
 } finally { items.release() }
 val nodes = Tasks.await(Wearable.getNodeClient(app).connectedNodes, 15, TimeUnit.SECONDS)
 for (node in nodes) Tasks.await(Wearable.getMessageClient(app).sendMessage(
 node.id, "/padelscore/sync/history/request", byteArrayOf()
 ), 15, TimeUnit.SECONDS)
 } catch (e: Exception) { Log.w("PhoneMatchHistory", "Refresh deferred", e) }
 }
 }

 @Synchronized
 fun receive(context: Context, item: DataItem) {
 if (item.uri.path != PATH) return
 val map = DataMapItem.fromDataItem(item).dataMap
 val prefs = preferences(context)
 val revision = map.getLong("revision")
 val enabled = map.getBoolean("enabled")
 val prevRevision = prefs.getLong("revision", 0)
 if (revision < prevRevision && enabled) return
 if (revision == prevRevision && enabled == prefs.getBoolean("enabled", false)) return
 val json = if (enabled) {
 val asset = map.getAsset("history") ?: return
 val result = Tasks.await(Wearable.getDataClient(context).getFdForAsset(asset), 30, TimeUnit.SECONDS)
 val text = result.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
 val entries = JSONArray(text)
 require(entries.length() <= 100)
 for (i in 0 until entries.length()) require(entries.getJSONObject(i).getLong("matchId") > 0)
 text
 } else "[]"
 prefs.edit(commit = true) {
  putString("entries", json)
  putLong("revision", revision)
  putBoolean("enabled", enabled)
 }
 }
}

class MatchHistoryListener : WearableListenerService() {
 override fun onDataChanged(events: DataEventBuffer) {
 for (event in events) {
 if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PhoneMatchHistory.PATH) {
 try { PhoneMatchHistory.receive(applicationContext, event.dataItem) }
 catch (e: Exception) { Log.w("MatchHistoryListener", "Snapshot retained for retry", e) }
 }
 }
 }
}
