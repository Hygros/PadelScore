package ch.hygro.padelscore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSyncProtocolTest {
    @Test fun boundedReadAcceptsLimitAndRejectsOversize() {
        val bytes = ByteArray(WorkoutSyncProtocol.MAX_BYTES)
        assertEquals(bytes.size, WorkoutSyncProtocol.readBounded(bytes.inputStream()).size)
        try {
            WorkoutSyncProtocol.readBounded(ByteArray(bytes.size + 1).inputStream())
            throw AssertionError("Oversize input accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun sha256UsesCanonicalLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            WorkoutSyncProtocol.digest("abc".toByteArray(Charsets.UTF_8)))
    }

    @Test fun transportBoundFitsMaximumChunkCount() {
        val sizes = listOf(1, WorkoutSyncProtocol.CHUNK_BYTES - 1,
            WorkoutSyncProtocol.CHUNK_BYTES, WorkoutSyncProtocol.CHUNK_BYTES + 1,
            WorkoutSyncProtocol.MAX_BYTES)
        for (size in sizes) {
            val count = (size + WorkoutSyncProtocol.CHUNK_BYTES - 1) / WorkoutSyncProtocol.CHUNK_BYTES
            assertTrue(count in 1..WorkoutSyncProtocol.MAX_CHUNKS)
            val lastSize = size - (count - 1) * WorkoutSyncProtocol.CHUNK_BYTES
            assertTrue(lastSize in 1..WorkoutSyncProtocol.CHUNK_BYTES)
        }
    }

    @Test fun distinctPayloadsHaveDistinctReceipts() {
        assertTrue(WorkoutSyncProtocol.digest(byteArrayOf(1)) !=
            WorkoutSyncProtocol.digest(byteArrayOf(2)))
    }
}
