package ch.hygro.padelscore.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
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

internal interface WorkoutHealthProvider {
    suspend fun available(): Boolean
    suspend fun insertHeartRate(record: HeartRateRecord): List<String>
    suspend fun insertExerciseSession(record: ExerciseSessionRecord): List<String>
    suspend fun deleteHeartRateByUid(uid: String)
    suspend fun deleteExerciseSessionByUid(uid: String)
}

/** One serialized, idempotent importer shared by DataItems, RPC and durable inbox replay. */
class WorkoutHealthImporter internal constructor(
    context: Context?,
    private val prefsProvider: () -> SharedPreferences,
    private val provider: WorkoutHealthProvider,
    private val consentChecker: ((id: Long, node: String, hash: String) -> Boolean)? = null,
    private val ackSender: (node: String, ackBytes: ByteArray) -> Unit = { node, ack ->
        if (node.isNotBlank() && context != null) {
            Tasks.await(
                Wearable.getMessageClient(context.applicationContext)
                    .sendMessage(node, WorkoutSyncProtocol.ACK, ack),
                10,
                TimeUnit.SECONDS
            )
        }
    },
    private val matchInboxRemover: (id: Long) -> Unit = { id ->
        if (context != null) {
            WorkoutInbox(context.applicationContext).removeMatch(id)
        }
    },
    private val stringResolver: (resId: Int, args: Array<out Any>) -> String = { resId, args ->
        val app = requireNotNull(context).applicationContext
        if (args.isEmpty()) app.getString(resId) else app.getString(resId, *args)
    }
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        prefsProvider = {
            context.applicationContext.getSharedPreferences("health_sync_receipts", Context.MODE_PRIVATE)
        },
        provider = DefaultWorkoutHealthProvider(context.applicationContext)
    )

    private val context: Context? = context?.applicationContext
    private val prefs: SharedPreferences get() = prefsProvider()

    private fun str(resId: Int, vararg args: Any): String = stringResolver(resId, args)
    private fun logI(msg: String) { runCatching { Log.i(TAG, msg) } }
    private fun logD(msg: String) { runCatching { Log.d(TAG, msg) } }
    private fun logW(msg: String, tr: Throwable? = null) {
        runCatching { if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg) }
    }

    companion object {
        private const val TAG = "WorkoutHealthImporter"
        private val importMutex = Mutex()
        val permissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class)
        )
    }

    @Suppress("UseKtx")
    private fun commitPrefs(block: SharedPreferences.Editor.() -> Unit): Boolean {
        val editor = prefs.edit()
        editor.block()
        return editor.commit()
    }

    private class DefaultWorkoutHealthProvider(private val context: Context) : WorkoutHealthProvider {
        override suspend fun available(): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
                HealthConnectClient.getOrCreate(context).permissionController
                    .getGrantedPermissions().containsAll(permissions)

        override suspend fun insertHeartRate(record: HeartRateRecord): List<String> =
            HealthConnectClient.getOrCreate(context).insertRecords(listOf(record)).recordIdsList

        override suspend fun insertExerciseSession(record: ExerciseSessionRecord): List<String> =
            HealthConnectClient.getOrCreate(context).insertRecords(listOf(record)).recordIdsList

        override suspend fun deleteHeartRateByUid(uid: String) {
            HealthConnectClient.getOrCreate(context).deleteRecords(
                HeartRateRecord::class,
                recordIdsList = listOf(uid),
                clientRecordIdsList = emptyList()
            )
        }

        override suspend fun deleteExerciseSessionByUid(uid: String) {
            HealthConnectClient.getOrCreate(context).deleteRecords(
                ExerciseSessionRecord::class,
                recordIdsList = listOf(uid),
                clientRecordIdsList = emptyList()
            )
        }
    }

    internal fun isDeleted(id: Long): Boolean = prefs.getBoolean("deleted_$id", false)

    /** Persist first; block every import even while Health Connect is unavailable. */
    internal suspend fun deleteWorkout(id: Long): Boolean = withContext(Dispatchers.IO) {
        require(id > 0L)
        importMutex.withLock {
            if (!commitPrefs { putBoolean("deleted_$id", true) }) {
                report("Cannot persist workout deletion tombstone for $id")
                return@withLock false
            }
            matchInboxRemover(id)
            if (prefs.getBoolean("delete_done_$id", false)) return@withLock true
            if (!available()) {
                report(str(R.string.status_deletion_pending_permission))
                return@withLock false
            }
            try {
                // Delete by the actual Health Connect record UID returned by insertRecords.
                // A clientRecordId is not a provider-generated record UID.
                val sessionUid = prefs.getString("record_session_$id", null)
                val heartRateUid = prefs.getString("record_hr_$id", null)
                if (prefs.getBoolean("synced_$id", false) && sessionUid.isNullOrBlank()) {
                    report(str(R.string.status_deletion_pending_no_record_id, id.toString()))
                    return@withLock false
                }
                if (!prefs.getBoolean("delete_session_$id", false)) {
                    if (!sessionUid.isNullOrBlank()) {
                        provider.deleteExerciseSessionByUid(sessionUid)
                    }
                    check(commitPrefs { putBoolean("delete_session_$id", true) }) {
                        "Cannot persist session deletion milestone"
                    }
                }
                if (!prefs.getBoolean("delete_hr_$id", false)) {
                    if (!heartRateUid.isNullOrBlank()) {
                        provider.deleteHeartRateByUid(heartRateUid)
                    } else if (prefs.getBoolean("synced_$id", false) &&
                        prefs.getInt("hr_count_$id", -1) != 0) {
                        report(str(R.string.status_deletion_pending_no_hr_id, id.toString()))
                        return@withLock false
                    }
                    check(commitPrefs { putBoolean("delete_hr_$id", true) }) {
                        "Cannot persist heart-rate deletion milestone"
                    }
                }
                check(commitPrefs {
                    putBoolean("delete_done_$id", true)
                    remove("synced_$id")
                    remove("hash_$id")
                    putString("last_status", str(R.string.status_deleted_from_hc, id.toString()))
                }) { "Cannot persist Health Connect deletion completion" }
                true
            } catch (e: Exception) {
                report(str(R.string.status_deletion_pending_error, id.toString(), e.message ?: ""))
                false
            }
        }
    }

    private suspend fun receiveDeletions() {
        val appContext = requireNotNull(context)
        val client = Wearable.getDataClient(appContext)
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
        consentChecker?.let { return it(id, node, hash) }
        val appContext = requireNotNull(context)
        return try {
            val bytes = Tasks.await(Wearable.getMessageClient(appContext).sendRequest(
                node, "${WorkoutSyncProtocol.CONSENT}$id", hash.toByteArray(Charsets.UTF_8)
            ), 10, TimeUnit.SECONDS)
            require(bytes.size <= 4096)
            val response = JSONObject(String(bytes, Charsets.UTF_8))
            require(response.getLong("id") == id && response.getString("sha256") == hash)
            if (response.optBoolean("deleted")) {
                commitPrefs { putBoolean("deleted_$id", true) }
                false
            } else response.optBoolean("allowed") && prefs.getBoolean("sync_enabled", false)
        } catch (e: Exception) {
            report(str(R.string.status_import_paused_consent))
            false
        }
    }

    private suspend fun available(): Boolean = provider.available()

    suspend fun importPending(requestWatch: Boolean = true, onProgress: suspend (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val appContext = requireNotNull(context)
        // Deletions take precedence over inbox replay and all old DataItems.
        receiveDeletions()
        if (!prefs.getBoolean("sync_enabled", false)) {
            onProgress(str(R.string.status_sync_off_phone))
            return@withContext 0
        }
        if (!available()) {
            onProgress(str(R.string.status_hc_unavailable_permissions))
            return@withContext 0
        }
        var imported = 0
        val inbox = WorkoutInbox(appContext)
        for (file in inbox.pending()) {
            try {
                val envelope = inbox.read(file)
                if (importPayload(envelope.getString("payload"), envelope.getString("node"), envelope.getString("sha256"))) {
                    inbox.remove(file)
                    imported++
                }
            } catch (e: Exception) { report("${str(R.string.log_import_deferred)} (Inbox): ${e.message}") }
        }
        val client = Wearable.getDataClient(appContext)
        try {
            val items = Tasks.await(client.dataItems, 15, TimeUnit.SECONDS)
            try {
                logI(str(R.string.log_data_items_visible, items.count))
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
                    } catch (e: Exception) { report("${str(R.string.log_import_deferred)} (DataItem): ${e.message}") }
                }
            } finally { items.release() }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { logW(str(R.string.log_data_item_scan_unavailable), e) }

        // No capability is dynamically added. Discovery of connected nodes is not proof of delivery.
        val nodes = if (requestWatch) Tasks.await(Wearable.getNodeClient(appContext).connectedNodes, 10, TimeUnit.SECONDS) else emptyList()
        var requested = 0
        for (node in if (requestWatch) nodes else emptyList()) {
            try {
                Tasks.await(Wearable.getMessageClient(appContext).sendMessage(
                    node.id, WorkoutSyncProtocol.REQUEST, byteArrayOf()
                ), 10, TimeUnit.SECONDS)
                requested++
            } catch (e: Exception) { logW(str(R.string.log_workout_request_queue_failed), e) }
        }
        val pendingDeletes = prefs.all.keys.count { key ->
            key.startsWith("deleted_") &&
                !prefs.getBoolean("delete_done_${key.removePrefix("deleted_")}", false)
        }
        val confirmed = prefs.all.keys.count { it.startsWith("hash_") }
        onProgress(str(R.string.status_sync_progress, confirmed, imported, requested, pendingDeletes))
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
                val heartRateRecords = records.filterIsInstance<HeartRateRecord>()
                if (heartRateRecords.isEmpty() && previousHash != null && previousHash != hash) {
                    val oldUid = prefs.getString("record_hr_$id", null)
                    val previousHrCount = prefs.getInt("hr_count_$id", -1)
                    if (!oldUid.isNullOrBlank()) {
                        provider.deleteHeartRateByUid(oldUid)
                        check(commitPrefs {
                            remove("record_hr_$id")
                            putInt("hr_count_$id", 0)
                        }) {
                            "Cannot persist heart-rate removal milestone"
                        }
                    } else if (previousHrCount != 0) {
                        report("Workout update deferred: previous heart-rate UID unavailable.")
                        return@withLock false
                    }
                }
                if (heartRateRecords.isNotEmpty()) {
                    val heartRateIds = provider.insertHeartRate(heartRateRecords.single())
                    check(heartRateIds.size == heartRateRecords.size) {
                        "Health Connect did not return all heart-rate record IDs"
                    }
                    val uid = heartRateIds.single()
                    check(uid.isNotBlank() && commitPrefs {
                        putString("record_hr_$id", uid)
                        putInt("hr_count_$id", validSamples.size)
                    }) {
                        "Cannot persist Health Connect heart-rate record UID"
                    }
                    logI(str(R.string.log_hr_write_completed, id.toString(), heartRateIds.size))
                }
                val sessionRecords = records.filterIsInstance<ExerciseSessionRecord>()
                check(sessionRecords.size == 1) { "Expected exactly one exercise session" }
                if (!prefs.getBoolean("sync_enabled", false)) return@withLock false
                val sessionIds = provider.insertExerciseSession(sessionRecords.single())
                check(sessionIds.size == 1) {
                    "Health Connect did not return the exercise session record ID"
                }
                val sessionUid = sessionIds.single()
                check(sessionUid.isNotBlank() && commitPrefs {
                    putString("record_session_$id", sessionUid)
                    if (heartRateRecords.isEmpty()) {
                        putInt("hr_count_$id", 0)
                    }
                }) {
                    "Cannot persist Health Connect exercise record UID"
                }
                logD(str(R.string.log_exercise_session_write_completed))
                check(commitPrefs {
                    putLong("revision_$id", revision)
                    putBoolean("synced_$id", true)
                    putString("hash_$id", hash)
                    putString("last_status", str(R.string.status_hc_import_confirmed, id.toString(), validSamples.size))
                }) { "Cannot persist Health Connect receipt" }
                logI(str(R.string.log_import_confirmed, id.toString(), validSamples.size))
            } else {
                check(commitPrefs { putString("hash_$id", hash) }) {
                    "Cannot persist Health Connect receipt hash"
                }
                logI(str(R.string.log_already_imported, id.toString()))
            }
            try {
                val ack = JSONObject().put("id", id).put("sha256", hash).toString().toByteArray(Charsets.UTF_8)
                ackSender(node, ack)
            } catch (e: Exception) {
                logW(str(R.string.log_import_succeeded_ack_deferred), e)
            }
            true
        } }

    internal fun report(message: String) {
        logW(message)
        prefs.edit { putString("last_status", message) }
    }
}
