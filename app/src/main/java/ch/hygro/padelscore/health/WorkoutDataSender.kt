package ch.hygro.padelscore.health

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Keeps the persistent DataClient path and adds acknowledged RPC delivery when connected. */
class WorkoutDataSender(context: Context) {
    private val context = context.applicationContext
    private fun enabled() = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        .getBoolean("health_sync_enabled", false)
    private val receipts get() = context.getSharedPreferences("workout_sync_confirmed", Context.MODE_PRIVATE)

    fun publishAll() = publishTo(null)

    fun schedulePublish() {
        deletionExecutor.execute {
            try { publishAll() } catch (e: Exception) { Log.w(TAG, "Sync retained for reconnect retry", e) }
        }
    }

    /** Explicit user deletion is allowed with Health Sync OFF; no workout is sent. */
    fun scheduleDeletionSync() {
        deletionExecutor.execute {
            try { synchronized(sendLock) { publishDeletions(null) } } catch (e: Exception) { Log.w(TAG, "Deletion retained for reconnect retry", e) }
        }
    }

    private fun publishDeletions(requestingNode: String?) {
        val ids = LocalWorkoutRepository(context).deletedMatchIds()
        if (ids.isEmpty()) return
        val dataClient = Wearable.getDataClient(context)
        val targets = if (requestingNode != null) setOf(requestingNode) else try {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS)
                .map { it.id }.toSet()
        } catch (e: Exception) { emptySet() }
        for (id in ids) {
            try {
                // Keep this tombstone for delayed DataItems and phone reinstalls.
                val request = PutDataMapRequest.create("${WorkoutSyncProtocol.DELETE_DATA}$id").apply {
                    dataMap.putLong("id", id)
                }.asPutDataRequest().setUrgent()
                Tasks.await(dataClient.putDataItem(request), 15, TimeUnit.SECONDS)
                val uri = "wear://*/padelscore/workout/$id".toUri()
                Tasks.await(dataClient.deleteDataItems(uri), 15, TimeUnit.SECONDS)
            } catch (e: Exception) { Log.w(TAG, "Deletion DataItem deferred workout=$id", e) }
            for (node in targets) {
                try {
                    val response = String(Tasks.await(Wearable.getMessageClient(context).sendRequest(
                        node, "${WorkoutSyncProtocol.DELETE_RPC}$id", byteArrayOf()
                    ), 35, TimeUnit.SECONDS), Charsets.UTF_8)
                    Log.i(TAG, "Deletion response workout=$id: $response")
                } catch (e: Exception) { Log.w(TAG, "Deletion RPC deferred workout=$id", e) }
            }
        }
    }

    /** A request is served only while the watch's existing opt-in switch is enabled. */
    fun publishTo(requestingNode: String?) {
        synchronized(sendLock) {
            publishDeletions(requestingNode)
            if (!enabled()) return
            val workouts = JSONArray(LocalWorkoutRepository(context).completedWorkoutsJson())
            val targets = if (requestingNode != null) setOf(requestingNode) else try {
                Tasks.await(Wearable.getCapabilityClient(context).getCapability(
                    WorkoutSyncProtocol.RECEIVER, CapabilityClient.FILTER_REACHABLE
                ), 10, TimeUnit.SECONDS).nodes.map { it.id }.toSet()
            } catch (e: Exception) {
                Log.w(TAG, "Receiver discovery unavailable; DataItems remain queued", e)
                emptySet()
            }
            Log.i(TAG, "Stored workouts=${workouts.length()}; receivers=${targets.size}")
            for (index in 0 until workouts.length()) {
                if (!enabled()) return
                val workout = workouts.optJSONObject(index) ?: continue
                val id = workout.optLong("startTimeMillis", 0L)
                if (id <= 0 || workout.optLong("endTimeMillis", 0L) <= id ||
                    LocalWorkoutRepository(context).isDeleted(id) || LocalWorkoutRepository(context).activeMatchId() == id) continue
                val bytes = workout.toString().toByteArray(Charsets.UTF_8)
                val hash = WorkoutSyncProtocol.digest(bytes)
                // The phone is authoritative. A request always replays even acknowledged data,
                // allowing recovery after phone reinstall or a lost phone-side receipt.
                if (requestingNode == null && receipts.getString("hash_$id", null) == hash) continue
                if (bytes.size > WorkoutSyncProtocol.MAX_BYTES) {
                    Log.e(TAG, "Workout $id exceeds transport bound; local workout retained")
                    continue
                }
                try {
                    if (!enabled()) return
                    if (LocalWorkoutRepository(context).isDeleted(id) || LocalWorkoutRepository(context).activeMatchId() == id) continue
                    val request = PutDataMapRequest.create("/padelscore/workout/$id").apply {
                        dataMap.putAsset("workout", Asset.createFromBytes(bytes))
                        dataMap.putString("sha256", hash)
                    }.asPutDataRequest().setUrgent()
                    Tasks.await(Wearable.getDataClient(context).putDataItem(request), 15, TimeUnit.SECONDS)
                    Log.i(TAG, "Queued DataItem workout=$id")
                } catch (e: Exception) {
                    Log.w(TAG, "DataItem queue failed for $id; trying direct RPC", e)
                }
                for (node in targets) {
                    try {
                        val count = (bytes.size + WorkoutSyncProtocol.CHUNK_BYTES - 1) / WorkoutSyncProtocol.CHUNK_BYTES
                        for (part in 0 until count) {
                            if (!enabled()) return
                            if (LocalWorkoutRepository(context).isDeleted(id)) break
                            val start = part * WorkoutSyncProtocol.CHUNK_BYTES
                            val chunk = bytes.copyOfRange(start, minOf(bytes.size, start + WorkoutSyncProtocol.CHUNK_BYTES))
                            val path = "${WorkoutSyncProtocol.CHUNK}$id/$hash/$part/$count"
                            var response: String? = null
                            var lastFailure: Exception? = null
                            for (attempt in 0 until 3) {
                                if (!enabled()) return
                                try {
                                    response = String(Tasks.await(Wearable.getMessageClient(context)
                                        .sendRequest(node, path, chunk), 35, TimeUnit.SECONDS), Charsets.UTF_8)
                                    break
                                } catch (e: Exception) { lastFailure = e }
                            }
                            check(response != null) { "RPC not acknowledged: ${lastFailure?.message}" }
                            if (response == "DELETED:$id") break
                            if (response == "IMPORTED:$id:$hash") {
                                confirmImported(id, hash)
                                break
                            }
                            check(response == "CHUNK" || response == "STORED") { "Receiver: $response" }
                            if (part == count - 1) {
                                Log.i(TAG, "Phone retained workout=$id; Health Connect confirmation pending")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct transfer incomplete for $id; local workout retained for retry", e)
                    }
                }
            }
        }
    }

    /** Never removes a workout. Only a matching payload hash may be acknowledged. */
    fun confirmImported(id: Long, hash: String) {
        if (!enabled() || id <= 0 || LocalWorkoutRepository(context).isDeleted(id)) return
        val workouts = JSONArray(LocalWorkoutRepository(context).completedWorkoutsJson())
        val matches = (0 until workouts.length()).any { index ->
            val workout = workouts.optJSONObject(index)
            workout?.optLong("startTimeMillis", 0L) == id &&
                WorkoutSyncProtocol.digest(workout.toString().toByteArray(Charsets.UTF_8)) == hash
        }
        if (!matches) return
        receipts.edit(commit = true) { putString("hash_$id", hash) }
        Log.i(TAG, "Health Connect import confirmed workout=$id sha256=$hash; local workout retained")
    }

    /** Cancels queued DataItems only. Existing local workout history is never changed. */
    fun clearPending() {
        synchronized(sendLock) {
            try {
                val client = Wearable.getDataClient(context)
                val items = Tasks.await(client.dataItems, 15, TimeUnit.SECONDS)
                try {
                    for (item in items) {
                        if (item.uri.path?.startsWith("/padelscore/workout/") == true &&
                            item.uri.path?.startsWith(WorkoutSyncProtocol.DELETE_DATA) != true) {
                            Tasks.await(client.deleteDataItems(item.uri), 15, TimeUnit.SECONDS)
                        }
                    }
                } finally { items.release() }
            } catch (e: Exception) { Log.w(TAG, "Unable to clear queued DataItems", e) }
        }
    }

    companion object {
        private const val TAG = "WorkoutDataSender"
        private val sendLock = Any()
        private val deletionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
