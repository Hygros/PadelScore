package ch.hygro.padelscore.sync

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

class WorkoutDataListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if ((0 until events.count).any {
            events.get(it).type == DataEvent.TYPE_CHANGED &&
                events.get(it).dataItem.uri.path?.startsWith("/padelscore/workout/") == true
        }) importPending(requestWatch = false)
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == WorkoutSyncProtocol.SENDER && info.nodes.isNotEmpty()) importPending()
    }

    /** Each RPC response confirms receipt of a chunk, never merely queue acceptance. */
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path == WorkoutSyncProtocol.STATUS) {
            return try {
                val prefs = getSharedPreferences("health_sync_receipts", MODE_PRIVATE)
                val available = androidx.health.connect.client.HealthConnectClient.getSdkStatus(this) ==
                    androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
                val granted = available && runBlocking {
                    androidx.health.connect.client.HealthConnectClient.getOrCreate(this@WorkoutDataListener)
                        .permissionController.getGrantedPermissions().containsAll(WorkoutHealthImporter.permissions)
                }
                val status = when {
                    !available -> "Health Connect unavailable. Open the phone companion."
                    !granted -> "Health Connect permissions missing. Open the phone companion."
                    !prefs.getBoolean("sync_enabled", false) -> "Phone sync is OFF. Enable it in the companion."
                    else -> "Phone ready. Samsung Health import is not confirmed."
                }
                Tasks.forResult(status.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) { Tasks.forException<ByteArray>(e) }
        }
        if (path.startsWith(WorkoutSyncProtocol.DELETE_RPC)) {
            return try {
                val id = path.removePrefix(WorkoutSyncProtocol.DELETE_RPC).toLong()
                require(id > 0L)
                val done = runBlocking { WorkoutHealthImporter(applicationContext).deleteWorkout(id) }
                Tasks.forResult((if (done) "DELETED:$id" else "DELETE_PENDING:$id")
                    .toByteArray(Charsets.UTF_8))
            } catch (e: Exception) { Tasks.forException<ByteArray>(e) }
        }
        if (!path.startsWith(WorkoutSyncProtocol.CHUNK)) return null
        return try {
            val id = path.removePrefix(WorkoutSyncProtocol.CHUNK).substringBefore('/').toLong()
            if (WorkoutHealthImporter(applicationContext).isDeleted(id)) {
                return Tasks.forResult("DELETED:$id".toByteArray(Charsets.UTF_8))
            }
            val inbox = WorkoutInbox(applicationContext)
            val complete = inbox.accept(nodeId, path, request)
            val response = if (complete == null) "CHUNK" else {
                val envelope = inbox.read(complete)
                val hash = envelope.getString("sha256")
                val payload = envelope.getString("payload")
                val imported = runBlocking {
                    WorkoutHealthImporter(applicationContext).importPayload(payload, nodeId, hash)
                }
                if (imported) {
                    inbox.remove(complete)
                    val id = org.json.JSONObject(payload).getLong("startTimeMillis")
                    if (WorkoutHealthImporter(applicationContext).isDeleted(id)) "DELETED:$id"
                    else "IMPORTED:$id:$hash"
                } else "STORED"
            }
            Tasks.forResult(response.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            WorkoutHealthImporter(applicationContext).report("Transfer/import deferred: ${e.message}")
            Tasks.forException<ByteArray>(e)
        }
    }

    private fun importPending(requestWatch: Boolean = true) {
        try { runBlocking { WorkoutHealthImporter(applicationContext).importPending(requestWatch) } } catch (e: Exception) { Log.w("WorkoutDataListener", "Import deferred; source workouts retained", e) }
    }
}
