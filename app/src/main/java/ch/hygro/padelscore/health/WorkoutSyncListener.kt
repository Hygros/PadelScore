package ch.hygro.padelscore.health

import android.util.Log
import ch.hygro.padelscore.storage.SettingsRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

/** Runs on WearableListenerService's background callback thread, not the UI thread. */
class WorkoutSyncListener : WearableListenerService() {
    companion object {
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

        internal fun handleConsentRequest(
            path: String,
            request: ByteArray,
            repository: LocalWorkoutRepository,
            healthSyncEnabled: Boolean
        ): ByteArray {
            val id = path.removePrefix(WorkoutSyncProtocol.CONSENT).toLong()
            val hash = String(request, Charsets.UTF_8)
            require(id > 0L && hash.matches(Regex("[0-9a-f]{64}")))
            val deleted = repository.isDeleted(id)
            val pendingDeletion = repository.isDeletionPending(id)
            val workouts = JSONArray(repository.completedWorkoutsJson())
            val exists = (0 until workouts.length()).any {
                val workout = workouts.optJSONObject(it)
                workout != null && workout.optLong("startTimeMillis") == id &&
                    WorkoutSyncProtocol.digest(workout.toString().toByteArray(Charsets.UTF_8)) == hash
            }
            val reply = JSONObject()
                .put("id", id)
                .put("sha256", hash)
                .put("deleted", deleted)
                .put(
                    "allowed",
                    healthSyncEnabled && !deleted && !pendingDeletion && exists &&
                        repository.activeMatchId() != id
                )
            return reply.toString().toByteArray(Charsets.UTF_8)
        }
    }

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (!path.startsWith(WorkoutSyncProtocol.CONSENT)) return null
        return try {
            val reply = handleConsentRequest(
                path = path,
                request = request,
                repository = LocalWorkoutRepository(applicationContext),
                healthSyncEnabled = SettingsRepository(applicationContext).healthSyncEnabled.value
            )
            Tasks.forResult(reply)
        } catch (e: Exception) {
            Tasks.forException(e)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        try {
            when (event.path) {
 "/padelscore/sync/history/request" -> MatchHistorySync.schedule(applicationContext)
                WorkoutSyncProtocol.REQUEST -> {
                    Log.i("WorkoutSyncListener", "Workout request received from ${event.sourceNodeId}")
                    executor.execute {
                        try { WorkoutDataSender(applicationContext).publishTo(event.sourceNodeId) } catch (e: Exception) { Log.w("WorkoutSyncListener", "Transfer deferred", e) }
                    }
                }
                WorkoutSyncProtocol.ACK -> {
                    val receipt = JSONObject(String(event.data, Charsets.UTF_8))
                    executor.execute {
                        try {
                            WorkoutDataSender(applicationContext).confirmImported(
                                receipt.getLong("id"), receipt.getString("sha256")
                            )
                        } catch (e: Exception) { Log.w("WorkoutSyncListener", "Receipt deferred", e) }
                    }
                }
            }
        } catch (e: Exception) { Log.e("WorkoutSyncListener", "Sync deferred; local workouts retained", e) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == WorkoutSyncProtocol.RECEIVER && info.nodes.isNotEmpty()) {
            WorkoutDataSender(applicationContext).schedulePublish()
 MatchHistorySync.schedule(applicationContext)
        }
    }
}
