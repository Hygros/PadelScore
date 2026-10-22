package ch.hygro.padelscore.sync

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream

/** Private durable inbox. Incomplete transfers survive process death and are safe to replay. */
internal class WorkoutInbox(context: Context) {
    private val root = File(context.filesDir, "workout_sync_inbox").apply { mkdirs() }
    fun accept(node: String, path: String, bytes: ByteArray): File? = synchronized(lock) {
        require(path.startsWith(WorkoutSyncProtocol.CHUNK))
        val fields = path.removePrefix(WorkoutSyncProtocol.CHUNK).split('/')
        require(fields.size == 4)
        val id = fields[0].toLong()
        val hash = fields[1]
        val index = fields[2].toInt()
        val count = fields[3].toInt()
        require(id > 0 && hash.matches(Regex("[0-9a-f]{64}")))
        require(count in 1..WorkoutSyncProtocol.MAX_CHUNKS && index in 0 until count)
        require(bytes.isNotEmpty() && bytes.size <= WorkoutSyncProtocol.CHUNK_BYTES)
        require(index == count - 1 || bytes.size == WorkoutSyncProtocol.CHUNK_BYTES)
        val key = WorkoutSyncProtocol.digest(node.toByteArray(Charsets.UTF_8)) + "_$id" + "_$hash"
        val complete = File(root, "$key.json")
        if (complete.exists()) return@synchronized complete
        val parts = File(root, "$key.parts").apply { mkdirs() }
        atomicWrite(File(parts, "$index"), bytes)
        if (!(0 until count).all { File(parts, "$it").exists() }) return@synchronized null
        val payload = ByteArrayOutputStream()
        for (part in 0 until count) {
            val data = AtomicFile(File(parts, "$part")).openRead().use { it.readBytes() }
            require(payload.size() + data.size <= WorkoutSyncProtocol.MAX_BYTES)
            payload.write(data)
        }
        val assembled = payload.toByteArray()
        require(WorkoutSyncProtocol.digest(assembled) == hash) { "Workout checksum mismatch" }
        val workout = JSONObject(String(assembled, Charsets.UTF_8))
        require(workout.getLong("startTimeMillis") == id)
        val envelope = JSONObject().put("node", node).put("sha256", hash)
            .put("payload", String(assembled, Charsets.UTF_8))
        atomicWrite(complete, envelope.toString().toByteArray(Charsets.UTF_8))
        // Only temporary transport fragments are removed, never source workouts.
        parts.listFiles()?.forEach { it.delete() }
        parts.delete()
        complete
    }
    fun pending(): List<File> = synchronized(lock) {
        root.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
    }
    fun read(file: File): JSONObject = synchronized(lock) {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }
    fun remove(file: File) = synchronized(lock) { AtomicFile(file).delete() }
    fun removeMatch(id: Long) = synchronized(lock) {
        require(id > 0L)
        root.listFiles()?.filter { it.name.split('_').getOrNull(1) == id.toString() }
            ?.forEach { file ->
                if (file.isDirectory) {
                    file.listFiles()?.forEach { part -> check(part.delete() || !part.exists()) }
                    check(file.delete() || !file.exists())
                } else AtomicFile(file).delete()
            }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    companion object { private val lock = Any() }
}
