package ch.hygro.padelscore.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.core.content.edit

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One serialized, idempotent importer shared by DataItems, RPC and durable inbox replay. */
class WorkoutHealthImporter(context: Context) {
    private val context = context.applicationContext
    private val prefs get() = context.getSharedPreferences("health_sync_receipts", Context.MODE_PRIVATE)
    companion object {
        private const val TAG = "WorkoutHealthImporter"
        private val importMutex = Mutex()
        val permissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class)
        )
    }

    internal fun isDeleted(id: Long): Boolean = prefs.getBoolean("deleted_$id", false)

    /** Persist first; block every import even while Health Connect is unavailable. */
    internal suspend fun deleteWorkout(id: Long): Boolean = withContext(Dispatchers.IO) {
        require(id > 0L)
        importMutex.withLock {
            prefs.edit(commit = true) { putBoolean("deleted_$id", true) }
            WorkoutInbox(context).removeMatch(id)
            if (prefs.getBoolean("delete_done_$id", false)) return@withLock true
            if (!available()) {
                report(context.getString(R.string.status_deletion_pending_permission))
                return@withLock false
            }
            try {
                val client = HealthConnectClient.getOrCreate(context)
                // Delete by the actual Health Connect record UID returned by insertRecords.
                // A clientRecordId is not a provider-generated record UID.
                val sessionUid = prefs.getString("record_session_$id", null)
                val heartRateUid = prefs.getString("record_hr_$id", null)
                if (prefs.getBoolean("synced_$id", false) && sessionUid.isNullOrBlank()) {
                    report(context.getString(R.string.status_deletion_pending_no_record_id, id.toString()))
                    return@withLock false
                }
                if (!prefs.getBoolean("delete_session_$id", false)) {
                    if (!sessionUid.isNullOrBlank()) {
                        client.deleteRecords(ExerciseSessionRecord::class,
                            recordIdsList = listOf(sessionUid), clientRecordIdsList = emptyList())
                    }
                    prefs.edit(commit = true) { putBoolean("delete_session_$id", true) }
                }
                if (!prefs.getBoolean("delete_hr_$id", false)) {
                    if (!heartRateUid.isNullOrBlank()) {
                        client.deleteRecords(HeartRateRecord::class,
                            recordIdsList = listOf(heartRateUid), clientRecordIdsList = emptyList())
                    } else if (prefs.getBoolean("synced_$id", false) &&
                        prefs.getInt("hr_count_$id", -1) != 0) {
                        report(context.getString(R.string.status_deletion_pending_no_hr_id, id.toString()))
                        return@withLock false
                    }
                    prefs.edit(commit = true) { putBoolean("delete_hr_$id", true) }
                }
                check(prefs.edit {
                putBoolean("delete_done_$id", true)
                remove("synced_$id")
                remove("hash_$id")
                putString("last_status", context.getString(R.string.status_deleted_from_hc, id.toString()))
            }.let { true })
                true
            } catch (e: Exception) {
                report(context.getString(R.string.status_deletion_pending_error, id.toString(), e.message))
                false
            }
        }
    }

    private suspend fun receiveDeletions() {
        val client = Wearable.getDataClient(context)
        try {
            val items = Tasks.await(client.dataItems, 15, TimeUnit.SECONDS)
            try {
                for (item in items) {
                    val path = item.uri.path ?: continue
                    if (!path.startsWith(WorkoutSyncProtocol.DELETE_DATA)) continue
                    val id = path.removePrefix(WorkoutSyncProtocol.DELETE_DATA).toLongOrNull() ?: continue
                    if (id > 0L) deleteWorkout(id)
                }
            } finally { items.release() }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { report("Deletion scan deferred: ${e.message}") }
        for (key in prefs.all.keys.filter { it.startsWith("deleted_") }) {
            val id = key.removePrefix("deleted_").toLongOrNull() ?: continue
            if (!prefs.getBoolean("delete_done_$id", false)) deleteWorkout(id)
        }
    }

    private fun hasLiveConsent(id: Long, node: String, hash: String): Boolean {
        return try {
            val bytes = Tasks.await(Wearable.getMessageClient(context).sendRequest(
                node, "${WorkoutSyncProtocol.CONSENT}$id", hash.toByteArray(Charsets.UTF_8)
            ), 10, TimeUnit.SECONDS)
            require(bytes.size <= 4096)
            val response = JSONObject(String(bytes, Charsets.UTF_8))
            require(response.getLong("id") == id && response.getString("sha256") == hash)
            if (response.optBoolean("deleted")) {
                prefs.edit(commit = true) { putBoolean("deleted_$id", true) }
                false
            } else response.optBoolean("allowed") && prefs.getBoolean("sync_enabled", false)
        } catch (e: Exception) {
            report(context.getString(R.string.status_import_paused_consent))
            false
        }
    }

    private suspend fun available(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
            HealthConnectClient.getOrCreate(context).permissionController
                .getGrantedPermissions().containsAll(permissions)

    suspend fun importPending(requestWatch: Boolean = true, onProgress: suspend (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        // Deletions take precedence over inbox replay and all old DataItems.
        receiveDeletions()
        if (!prefs.getBoolean("sync_enabled", false)) {
            onProgress(context.getString(R.string.status_sync_off_phone))
            return@withContext 0
        }
        if (!available()) {
            onProgress(context.getString(R.string.status_hc_unavailable_permissions))
            return@withContext 0
        }
        var imported = 0
        val inbox = WorkoutInbox(context)
        for (file in inbox.pending()) {
            try {
                val envelope = inbox.read(file)
                if (importPayload(envelope.getString("payload"), envelope.getString("node"), envelope.getString("sha256"))) {
                    inbox.remove(file)
                    imported++
                }
            } catch (e: Exception) { report("${context.getString(R.string.log_import_deferred)} (Inbox): ${e.message}") }
        }
        val client = Wearable.getDataClient(context)
        try {
            val items = Tasks.await(client.dataItems, 15, TimeUnit.SECONDS)
            try {
                Log.i(TAG, context.getString(R.string.log_data_items_visible, items.count))
                for (item in items) {
                    val path = item.uri.path ?: continue
                    if (!path.startsWith("/padelscore/workout/")) continue
                    if (path.startsWith(WorkoutSyncProtocol.DELETE_DATA)) continue
                    val itemId = path.substringAfterLast('/').toLongOrNull()
                    if (itemId != null && isDeleted(itemId)) {
                        Tasks.await(client.deleteDataItems(item.uri), 15, TimeUnit.SECONDS)
                        continue
                    }
                    try {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val asset = map.getAsset("workout") ?: continue
                        val stream = Tasks.await(client.getFdForAsset(asset), 15, TimeUnit.SECONDS).inputStream
                        val bytes = stream.use { WorkoutSyncProtocol.readBounded(it) }
                        require(bytes.size <= WorkoutSyncProtocol.MAX_BYTES)
                        val payload = String(bytes, Charsets.UTF_8)
                        require(JSONObject(payload).getLong("startTimeMillis").toString() == path.substringAfterLast('/'))
                        val hash = WorkoutSyncProtocol.digest(bytes)
                        val declaredHash = map.getString("sha256")
                        require(declaredHash == null || declaredHash == hash)
                        if (importPayload(payload, item.uri.host ?: "", hash)) {
                            // Only the transport copy is removed after successful import.
                            Tasks.await(client.deleteDataItems(item.uri), 15, TimeUnit.SECONDS)
                            imported++
                        }
                    } catch (e: Exception) { report("${context.getString(R.string.log_import_deferred)} (DataItem): ${e.message}") }
                }
            } finally { items.release() }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, context.getString(R.string.log_data_item_scan_unavailable), e) }

        // No capability is dynamically added. Discovery of connected nodes is not proof of delivery.
        val nodes = if (requestWatch) Tasks.await(Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS) else emptyList()
        var requested = 0
        for (node in if (requestWatch) nodes else emptyList()) {
            try {
                Tasks.await(Wearable.getMessageClient(context).sendMessage(
                    node.id, WorkoutSyncProtocol.REQUEST, byteArrayOf()
                ), 10, TimeUnit.SECONDS)
                requested++
            } catch (e: Exception) { Log.w(TAG, context.getString(R.string.log_workout_request_queue_failed), e) }
        }
        val pendingDeletes = prefs.all.keys.count { key ->
            key.startsWith("deleted_") &&
                !prefs.getBoolean("delete_done_${key.removePrefix("deleted_")}", false)
        }
        val confirmed = prefs.all.keys.count { it.startsWith("hash_") }
        onProgress(context.getString(R.string.status_sync_progress, confirmed, imported, requested, pendingDeletes))
        imported
    }

    /** True means imported, already imported, or permanently discarded by a deletion tombstone. */
    internal suspend fun importPayload(payload: String, node: String, hash: String): Boolean =
        withContext(Dispatchers.IO) { importMutex.withLock {
            require(WorkoutSyncProtocol.digest(payload.toByteArray(Charsets.UTF_8)) == hash)
            val json = JSONObject(payload)
            val id = json.getLong("startTimeMillis")
            // A tombstone wins over receipts, payload replay and delayed chunks.
            if (isDeleted(id)) return@withLock true
            val end = json.getLong("endTimeMillis")
            require(id > 0 && end in (id + 1)..(System.currentTimeMillis() + 60000L))
            if (!available()) return@withLock false
            val previousHash = prefs.getString("hash_$id", null)
            val revision = json.optLong("revision", 0L)
            val previousRevision = prefs.getLong("revision_$id", 0L)
            require(revision >= 0L)
            if (previousHash != null && previousHash != hash && revision <= previousRevision) {
                report("Stale workout revision ignored; source retained.")
                return@withLock false
            }
            if (!prefs.getBoolean("synced_$id", false) || previousHash != hash) {
                if (!hasLiveConsent(id, node, hash)) return@withLock false
                val records = ArrayList<Record>()
                records.add(ExerciseSessionRecord(
                    startTime = Instant.ofEpochMilli(id), startZoneOffset = null,
                    endTime = Instant.ofEpochMilli(end), endZoneOffset = null,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
                    title = "PadelScore", notes = "Padel match recorded on Wear OS",
                    metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_WATCH),
                        clientRecordId = "padelscore-$id", clientRecordVersion = revision)
                ))
                val samples = ArrayList<HeartRateRecord.Sample>()
                val samplesJson = json.optJSONArray("samples")
                if (samplesJson != null) {
                    for (i in 0 until samplesJson.length()) {
                        val sample = samplesJson.optJSONObject(i) ?: continue
                        val timestamp = sample.optLong("timeMillis", 0L)
                        val bpm = sample.optLong("bpm", 0L)
                        if (timestamp in id..<end && bpm in 30L..240L) {
                            samples.add(HeartRateRecord.Sample(Instant.ofEpochMilli(timestamp), bpm))
                        }
                    }
                }
                val validSamples = samples.distinctBy { it.time }.sortedBy { it.time }
                if (validSamples.isNotEmpty()) {
                    records.add(HeartRateRecord(
                        startTime = Instant.ofEpochMilli(id), startZoneOffset = null,
                        endTime = Instant.ofEpochMilli(end), endZoneOffset = null,
                        samples = validSamples,
                        metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_WATCH),
                            clientRecordId = "padelscore-hr-$id", clientRecordVersion = revision)
                    ))
                }
                // Publish supporting measurements first. Publish the real session last in a
                // separate call, matching the test path's session-only insertion shape.
                // This is deliberately not atomic: a failed session write leaves HR available
                // for retry. Stable clientRecordIds are retained; no receipt or ACK is written
                // until every required insert has completed successfully.
                if (!prefs.getBoolean("sync_enabled", false)) return@withLock false
                val healthClient = HealthConnectClient.getOrCreate(context)
                val heartRateRecords = records.filterIsInstance<HeartRateRecord>()
                if (heartRateRecords.isEmpty() && previousHash != null && previousHash != hash) {
                    val oldUid = prefs.getString("record_hr_$id", null)
                    if (oldUid.isNullOrBlank()) {
                        report("Workout update deferred: previous heart-rate UID unavailable.")
                        return@withLock false
                    }
                    healthClient.deleteRecords(HeartRateRecord::class,
                        recordIdsList = listOf(oldUid), clientRecordIdsList = emptyList())
                    prefs.edit(commit = true) {
                    remove("record_hr_$id")
                    putInt("hr_count_$id", 0)
                }
                }
                if (heartRateRecords.isNotEmpty()) {
                    val heartRateResponse = healthClient.insertRecords(heartRateRecords)
                    check(heartRateResponse.recordIdsList.size == heartRateRecords.size) {
                        "Health Connect did not return all heart-rate record IDs"
                    }
                    val uid = heartRateResponse.recordIdsList.single()
                    check(uid.isNotBlank() && prefs.edit {
                        putString("record_hr_$id", uid)
                        putInt("hr_count_$id", validSamples.size)
                    }.let { true }) {
                        "Cannot persist Health Connect heart-rate record UID"
                    }
                    Log.i(TAG, context.getString(R.string.log_hr_write_completed, id.toString(), heartRateResponse.recordIdsList.size))
                }
                val sessionRecords = records.filterIsInstance<ExerciseSessionRecord>()
                check(sessionRecords.size == 1) { "Expected exactly one exercise session" }
                if (!prefs.getBoolean("sync_enabled", false)) return@withLock false
                val sessionResponse = healthClient.insertRecords(sessionRecords)
                check(sessionResponse.recordIdsList.size == 1) {
                    "Health Connect did not return the exercise session record ID"
                }
                val sessionUid = sessionResponse.recordIdsList.single()
                check(sessionUid.isNotBlank() && prefs.edit {
                    putString("record_session_$id", sessionUid)
                }.let { true }) {
                    "Cannot persist Health Connect exercise record UID"
                }
                if (heartRateRecords.isEmpty()) {
                    prefs.edit(commit = true) { putInt("hr_count_$id", 0) }
                }
                Log.d(TAG, context.getString(R.string.log_exercise_session_write_completed))
                check(prefs.edit {
                    putLong("revision_$id", revision)
                    putBoolean("synced_$id", true)
                    putString("hash_$id", hash)
                    putString("last_status", context.getString(R.string.status_hc_import_confirmed, id.toString(), validSamples.size))
                }.let { true }) { "Cannot persist Health Connect receipt" }
                Log.i(TAG, context.getString(R.string.log_import_confirmed, id.toString(), validSamples.size))
            } else {
                prefs.edit(commit = true) { putString("hash_$id", hash) }
                Log.i(TAG, context.getString(R.string.log_already_imported, id.toString()))
            }
            try {
                val ack = JSONObject().put("id", id).put("sha256", hash).toString().toByteArray(Charsets.UTF_8)
                if (node.isNotBlank()) Tasks.await(Wearable.getMessageClient(context)
                    .sendMessage(node, WorkoutSyncProtocol.ACK, ack), 10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, context.getString(R.string.log_import_succeeded_ack_deferred), e)
            }
            true
        } }

    internal fun report(message: String) {
        Log.w(TAG, message)
        prefs.edit { putString("last_status", message) }
    }
}
