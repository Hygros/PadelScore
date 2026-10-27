package ch.hygro.padelscore.sync

import java.security.MessageDigest

/** Versioned, bounded RPC transport. Both modules intentionally use the same wire format. */
internal object WorkoutSyncProtocol {
    const val CONSENT = "/padelscore/sync/consent/"
    const val STATUS = "/padelscore/sync/chunk/status"
    const val REQUEST = "/padelscore/sync/request"
    const val ACK = "/padelscore/sync/imported"
    const val CHUNK = "/padelscore/sync/chunk/"
    // Within the existing manifest path filters; no new exported component.
    const val DELETE_RPC = CHUNK + "delete/"
    const val DELETE_DATA = "/padelscore/workout/deleted/"
    const val SENDER = "padelscore_sync_sender"
    const val CHUNK_BYTES = 48 * 1024
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CHUNKS = (MAX_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES
    fun readBounded(stream: java.io.InputStream): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = stream.read(buffer)
            if (size < 0) break
            if (size == 0) continue
            require(result.size() + size <= MAX_BYTES) { "Workout exceeds transport limit" }
            result.write(buffer, 0, size)
        }
        return result.toByteArray()
    }

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
