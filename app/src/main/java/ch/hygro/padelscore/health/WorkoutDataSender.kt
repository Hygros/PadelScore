package ch.hygro.padelscore.health

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import org.json.JSONArray

/** Keeps the persistent DataClient path and adds acknowledged RPC delivery when connected. */
class WorkoutDataSender internal constructor(
    private val workoutRepository: () -> LocalWorkoutRepository,
    private val isSyncEnabled: () -> Boolean,
    private val receiptsProvider: () -> SharedPreferences,
    private val transport: Transport
) {
    constructor(context: Context) : this(
        workoutRepository = { LocalWorkoutRepository(context.applicationContext) },
        isSyncEnabled = {
            context.applicationContext
                .getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                .getBoolean("health_sync_enabled", false)
        },
        receiptsProvider = {
            context.applicationContext
                .getSharedPreferences("workout_sync_confirmed", Context.MODE_PRIVATE)
        },
        transport = WearableTransport(context.applicationContext)
    )

    internal interface Transport {
        fun connectedNodeIds(): Set<String>
        fun reachableReceiverNodeIds(): Set<String>
        fun publishDeletionDataItem(id: Long)
        fun sendDeletionRpc(nodeId: String, id: Long): String
        fun putWorkoutDataItem(id: Long, bytes: ByteArray, hash: String)
        fun sendWorkoutChunkRpc(nodeId: String, path: String, chunk: ByteArray): String
        fun clearPendingWorkoutDataItems()
    }

    private fun enabled(): Boolean = isSyncEnabled()
    private val receipts: SharedPreferences get() = receiptsProvider()

    fun publishAll() = publishTo(null)

    fun schedulePublish() {
        deletionExecutor.execute {
            try {
                publishAll()
            } catch (e: Exception) {
                logWarn("Sync retained for reconnect retry", e)
            }
        }
    }

    /** Explicit user deletion is allowed with Health Sync OFF; no workout is sent. */
    fun scheduleDeletionSync() {
        deletionExecutor.execute {
            try {
                synchronized(sendLock) { publishDeletions(null) }
            } catch (e: Exception) {
                logWarn("Deletion retained for reconnect retry", e)
            }
        }
    }

    internal fun publishDeletions(requestingNode: String?) {
        val ids = workoutRepository().deletedMatchIds()
        if (ids.isEmpty()) return
        val targets = if (requestingNode != null) {
            setOf(requestingNode)
        } else {
            try {
                transport.connectedNodeIds()
            } catch (e: Exception) {
                emptySet()
            }
        }
        for (id in ids) {
            try {
                transport.publishDeletionDataItem(id)
            } catch (e: Exception) {
                logWarn("Deletion DataItem deferred workout=$id", e)
            }
            for (node in targets) {
                try {
                    val response = transport.sendDeletionRpc(node, id)
                    logInfo("Deletion response workout=$id: $response")
                } catch (e: Exception) {
                    logWarn("Deletion RPC deferred workout=$id", e)
                }
            }
        }
    }

    /** A request is served only while the watch's existing opt-in switch is enabled. */
    fun publishTo(requestingNode: String?) {
        synchronized(sendLock) {
            publishDeletions(requestingNode)
            if (!enabled()) return
            val repository = workoutRepository()
            val workouts = JSONArray(repository.completedWorkoutsJson())
            val targets = if (requestingNode != null) {
                setOf(requestingNode)
            } else {
                try {
                    transport.reachableReceiverNodeIds()
                } catch (e: Exception) {
                    logWarn("Receiver discovery unavailable; DataItems remain queued", e)
                    emptySet()
                }
            }
            logInfo("Stored workouts=${workouts.length()}; receivers=${targets.size}")
            for (index in 0 until workouts.length()) {
                if (!enabled()) return
                val workout = workouts.optJSONObject(index) ?: continue
                val id = workout.optLong("startTimeMillis", 0L)
                val currentRepo = workoutRepository()
                if (id <= 0 || workout.optLong("endTimeMillis", 0L) <= id ||
                    currentRepo.isDeleted(id) || currentRepo.isDeletionPending(id) ||
                    currentRepo.activeMatchId() == id
                ) {
                    continue
                }
                val bytes = workout.toString().toByteArray(Charsets.UTF_8)
                val hash = WorkoutSyncProtocol.digest(bytes)
                // The phone is authoritative. A request always replays even acknowledged data,
                // allowing recovery after phone reinstall or a lost phone-side receipt.
                if (requestingNode == null && receipts.getString("hash_$id", null) == hash) continue
                if (bytes.size > WorkoutSyncProtocol.MAX_BYTES) {
                    logError("Workout $id exceeds transport bound; local workout retained")
                    continue
                }
                try {
                    if (!enabled()) return
                    val repoBeforeQueue = workoutRepository()
                    if (repoBeforeQueue.isDeleted(id) || repoBeforeQueue.isDeletionPending(id) ||
                        repoBeforeQueue.activeMatchId() == id
                    ) {
                        continue
                    }
                    transport.putWorkoutDataItem(id, bytes, hash)
                    logInfo("Queued DataItem workout=$id")
                } catch (e: Exception) {
                    logWarn("DataItem queue failed for $id; trying direct RPC", e)
                }
                for (node in targets) {
                    try {
                        val count = (bytes.size + WorkoutSyncProtocol.CHUNK_BYTES - 1) / WorkoutSyncProtocol.CHUNK_BYTES
                        for (part in 0 until count) {
                            if (!enabled()) return
                            val repoBeforeChunk = workoutRepository()
                            if (repoBeforeChunk.isDeleted(id) || repoBeforeChunk.isDeletionPending(id)) break
                            val start = part * WorkoutSyncProtocol.CHUNK_BYTES
                            val chunk = bytes.copyOfRange(start, minOf(bytes.size, start + WorkoutSyncProtocol.CHUNK_BYTES))
                            val path = "${WorkoutSyncProtocol.CHUNK}$id/$hash/$part/$count"
                            var response: String? = null
                            var lastFailure: Exception? = null
                            for (attempt in 0 until 3) {
                                if (!enabled()) return
                                try {
                                    response = transport.sendWorkoutChunkRpc(node, path, chunk)
                                    break
                                } catch (e: Exception) {
                                    lastFailure = e
                                }
                            }
                            check(response != null) { "RPC not acknowledged: ${lastFailure?.message}" }
                            if (response == "DELETED:$id") break
                            if (response == "IMPORTED:$id:$hash") {
                                confirmImported(id, hash)
                                break
                            }
                            check(response == "CHUNK" || response == "STORED") { "Receiver: $response" }
                            if (part == count - 1) {
                                logInfo("Phone retained workout=$id; Health Connect confirmation pending")
                            }
                        }
                    } catch (e: Exception) {
                        logWarn("Direct transfer incomplete for $id; local workout retained for retry", e)
                    }
                }
            }
        }
    }

    /** Never removes a workout. Only a matching payload hash may be acknowledged. */
    fun confirmImported(id: Long, hash: String) {
        val repository = workoutRepository()
        if (!enabled() || id <= 0 || repository.isDeleted(id) || repository.isDeletionPending(id)) return
        val workouts = JSONArray(repository.completedWorkoutsJson())
        val matches = (0 until workouts.length()).any { index ->
            val workout = workouts.optJSONObject(index)
            workout?.optLong("startTimeMillis", 0L) == id &&
                WorkoutSyncProtocol.digest(workout.toString().toByteArray(Charsets.UTF_8)) == hash
        }
        if (!matches) return
        receipts.edit(commit = true) { putString("hash_$id", hash) }
        logInfo("Health Connect import confirmed workout=$id sha256=$hash; local workout retained")
    }

    /** Cancels queued DataItems only. Existing local workout history is never changed. */
    fun clearPending() {
        synchronized(sendLock) {
            try {
                transport.clearPendingWorkoutDataItems()
            } catch (e: Exception) {
                logWarn("Unable to clear queued DataItems", e)
            }
        }
    }

    private class WearableTransport(private val context: Context) : Transport {
        override fun connectedNodeIds(): Set<String> =
            Tasks.await(Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS)
                .map { it.id }
                .toSet()

        override fun reachableReceiverNodeIds(): Set<String> =
            Tasks.await(
                Wearable.getCapabilityClient(context).getCapability(
                    WorkoutSyncProtocol.RECEIVER,
                    CapabilityClient.FILTER_REACHABLE
                ),
                10,
                TimeUnit.SECONDS
            ).nodes.map { it.id }.toSet()

        override fun publishDeletionDataItem(id: Long) {
            val dataClient = Wearable.getDataClient(context)
            // Keep this tombstone for delayed DataItems and phone reinstalls.
            val request = PutDataMapRequest.create("${WorkoutSyncProtocol.DELETE_DATA}$id").apply {
                dataMap.putLong("id", id)
            }.asPutDataRequest().setUrgent()
            Tasks.await(dataClient.putDataItem(request), 15, TimeUnit.SECONDS)
            val uri = "wear://*/padelscore/workout/$id".toUri()
            Tasks.await(dataClient.deleteDataItems(uri), 15, TimeUnit.SECONDS)
        }

        override fun sendDeletionRpc(nodeId: String, id: Long): String =
            String(
                Tasks.await(
                    Wearable.getMessageClient(context).sendRequest(
                        nodeId,
                        "${WorkoutSyncProtocol.DELETE_RPC}$id",
                        byteArrayOf()
                    ),
                    35,
                    TimeUnit.SECONDS
                ),
                Charsets.UTF_8
            )

        override fun putWorkoutDataItem(id: Long, bytes: ByteArray, hash: String) {
            val request = PutDataMapRequest.create("/padelscore/workout/$id").apply {
                dataMap.putAsset("workout", Asset.createFromBytes(bytes))
                dataMap.putString("sha256", hash)
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(context).putDataItem(request), 15, TimeUnit.SECONDS)
        }

        override fun sendWorkoutChunkRpc(nodeId: String, path: String, chunk: ByteArray): String =
            String(
                Tasks.await(
                    Wearable.getMessageClient(context).sendRequest(nodeId, path, chunk),
                    35,
                    TimeUnit.SECONDS
                ),
                Charsets.UTF_8
            )

        override fun clearPendingWorkoutDataItems() {
            val client = Wearable.getDataClient(context)
            val items = Tasks.await(client.dataItems, 15, TimeUnit.SECONDS)
            try {
                for (item in items) {
                    if (item.uri.path?.startsWith("/padelscore/workout/") == true &&
                        item.uri.path?.startsWith(WorkoutSyncProtocol.DELETE_DATA) != true
                    ) {
                        Tasks.await(client.deleteDataItems(item.uri), 15, TimeUnit.SECONDS)
                    }
                }
            } finally {
                items.release()
            }
        }
    }

    companion object {
        private const val TAG = "WorkoutDataSender"
        private val sendLock = Any()
        private val deletionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        private fun logInfo(message: String) {
            runCatching { Log.i(TAG, message) }
        }

        private fun logWarn(message: String, error: Throwable) {
            runCatching { Log.w(TAG, message, error) }
        }

        private fun logError(message: String) {
            runCatching { Log.e(TAG, message) }
        }
    }
}
