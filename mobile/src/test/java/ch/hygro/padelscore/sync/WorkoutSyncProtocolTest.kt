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

    @Test fun staleDisabledSnapshotDoesNotLowerRevisionHighWaterMark() {
        // Initial accepted state: disabled at revision 20
        val afterStaleDisable = PhoneMatchHistory.evaluateRevision(
            prevRevision = 20L,
            prevEnabled = false,
            prevGeneration = 100L,
            incomingRevision = 10L,
            incomingEnabled = false,
            incomingGeneration = 100L
        )
        org.junit.Assert.assertFalse(afterStaleDisable.accept)
        assertEquals(20L, afterStaleDisable.nextRevision)

        // Even if previously enabled at revision 20 and an older disable (revision 10) arrives,
        // the high-water mark stays 20 so a stale enabled revision 15 is rejected.
        val afterDisableFromEnabled = PhoneMatchHistory.evaluateRevision(
            prevRevision = 20L,
            prevEnabled = true,
            prevGeneration = 100L,
            incomingRevision = 10L,
            incomingEnabled = false,
            incomingGeneration = 100L
        )
        assertTrue(afterDisableFromEnabled.accept)
        assertEquals(20L, afterDisableFromEnabled.nextRevision)

        val staleEnableAfterClear = PhoneMatchHistory.evaluateRevision(
            prevRevision = afterDisableFromEnabled.nextRevision,
            prevEnabled = false,
            prevGeneration = afterDisableFromEnabled.nextGeneration,
            incomingRevision = 15L,
            incomingEnabled = true,
            incomingGeneration = 100L
        )
        org.junit.Assert.assertFalse(staleEnableAfterClear.accept)
        assertEquals(20L, staleEnableAfterClear.nextRevision)
    }

    @Test fun newWatchGenerationAfterResetIsAcceptedWithLowerRevision() {
        val afterWatchReset = PhoneMatchHistory.evaluateRevision(
            prevRevision = 100L,
            prevEnabled = true,
            prevGeneration = 111L,
            incomingRevision = 1L,
            incomingEnabled = true,
            incomingGeneration = 222L
        )
        assertTrue(afterWatchReset.accept)
        assertEquals(1L, afterWatchReset.nextRevision)
        assertEquals(222L, afterWatchReset.nextGeneration)
    }

    @Test fun sessionOnlyHigherRevisionUpdatesWithoutStallingOnMissingHeartRateUid() = kotlinx.coroutines.runBlocking {
        val prefs = InMemorySharedPreferences().apply {
            edit().putBoolean("sync_enabled", true).commit()
        }
        val provider = FakeWorkoutHealthProvider()
        val acks = mutableListOf<String>()
        val importer = WorkoutHealthImporter(
            context = null,
            prefsProvider = { prefs },
            provider = provider,
            consentChecker = { _, _, _ -> true },
            ackSender = { _, bytes -> acks.add(String(bytes, Charsets.UTF_8)) },
            matchInboxRemover = {},
            stringResolver = { resId, _ -> "str:$resId" }
        )

        val payloadRev1 = """{"revision":1,"startTimeMillis":1000,"endTimeMillis":2000,"samples":[]}"""
        val hashRev1 = WorkoutSyncProtocol.digest(payloadRev1.toByteArray(Charsets.UTF_8))
        assertTrue(importer.importPayload(payloadRev1, "node-1", hashRev1))
        assertEquals(0, prefs.getInt("hr_count_1000", -1))
        assertEquals(1L, prefs.getLong("revision_1000", 0L))
        assertEquals(1, acks.size)

        val payloadRev2 = """{"revision":2,"startTimeMillis":1000,"endTimeMillis":2500,"samples":[]}"""
        val hashRev2 = WorkoutSyncProtocol.digest(payloadRev2.toByteArray(Charsets.UTF_8))
        assertTrue(importer.importPayload(payloadRev2, "node-1", hashRev2))
        assertEquals(0, prefs.getInt("hr_count_1000", -1))
        assertEquals(2L, prefs.getLong("revision_1000", 0L))
        assertEquals(hashRev2, prefs.getString("hash_1000", null))
        assertEquals(2, acks.size)
    }

    @Test fun heartRateToSessionOnlyRevisionDeletesOldHeartRateAndAllowsRetry() = kotlinx.coroutines.runBlocking {
        val prefs = InMemorySharedPreferences().apply {
            edit().putBoolean("sync_enabled", true).commit()
        }
        val provider = FakeWorkoutHealthProvider()
        val acks = mutableListOf<String>()
        val importer = WorkoutHealthImporter(
            context = null,
            prefsProvider = { prefs },
            provider = provider,
            consentChecker = { _, _, _ -> true },
            ackSender = { _, bytes -> acks.add(String(bytes, Charsets.UTF_8)) },
            matchInboxRemover = {},
            stringResolver = { resId, _ -> "str:$resId" }
        )

        val payloadRev1 = """{"revision":1,"startTimeMillis":1000,"endTimeMillis":2000,"samples":[{"timeMillis":1500,"bpm":140}]}"""
        val hashRev1 = WorkoutSyncProtocol.digest(payloadRev1.toByteArray(Charsets.UTF_8))
        assertTrue(importer.importPayload(payloadRev1, "node-1", hashRev1))
        assertEquals(1, prefs.getInt("hr_count_1000", -1))
        assertEquals("hr-1", prefs.getString("record_hr_1000", null))

        // Revision 2 has 0 HR samples; simulate session write failure after HR deletion succeeds
        val payloadRev2 = """{"revision":2,"startTimeMillis":1000,"endTimeMillis":2500,"samples":[]}"""
        val hashRev2 = WorkoutSyncProtocol.digest(payloadRev2.toByteArray(Charsets.UTF_8))
        provider.failNextSessionInsert = true
        try {
            importer.importPayload(payloadRev2, "node-1", hashRev2)
            throw AssertionError("Expected session insert failure")
        } catch (_: RuntimeException) { }

        assertEquals(listOf("hr-1"), provider.deletedHeartRateUids)
        assertEquals(null, prefs.getString("record_hr_1000", null))
        assertEquals(0, prefs.getInt("hr_count_1000", -1))
        assertEquals(1, acks.size)

        // Retry of revision 2 must not stall even though record_hr_1000 is now null
        assertTrue(importer.importPayload(payloadRev2, "node-1", hashRev2))
        assertEquals(2L, prefs.getLong("revision_1000", 0L))
        assertEquals(hashRev2, prefs.getString("hash_1000", null))
        assertEquals(2, acks.size)
    }

    @Test fun commitFailureAbortsImportBeforeSendingAck() = kotlinx.coroutines.runBlocking {
        val prefs = InMemorySharedPreferences().apply {
            edit().putBoolean("sync_enabled", true).commit()
            failCommits = true
        }
        val provider = FakeWorkoutHealthProvider()
        val acks = mutableListOf<String>()
        val importer = WorkoutHealthImporter(
            context = null,
            prefsProvider = { prefs },
            provider = provider,
            consentChecker = { _, _, _ -> true },
            ackSender = { _, bytes -> acks.add(String(bytes, Charsets.UTF_8)) },
            matchInboxRemover = {},
            stringResolver = { resId, _ -> "str:$resId" }
        )

        val payload = """{"revision":1,"startTimeMillis":1000,"endTimeMillis":2000,"samples":[]}"""
        val hash = WorkoutSyncProtocol.digest(payload.toByteArray(Charsets.UTF_8))
        try {
            importer.importPayload(payload, "node-1", hash)
            throw AssertionError("Expected IllegalStateException when commit() returns false")
        } catch (_: IllegalStateException) { }
        assertTrue(acks.isEmpty())
    }

    private class FakeWorkoutHealthProvider : WorkoutHealthProvider {
        var hrCounter = 0
        var sessionCounter = 0
        var failNextSessionInsert = false
        val deletedHeartRateUids = mutableListOf<String>()
        val deletedSessionUids = mutableListOf<String>()

        override suspend fun available(): Boolean = true

        override suspend fun insertHeartRate(
            record: androidx.health.connect.client.records.HeartRateRecord
        ): List<String> {
            hrCounter++
            return listOf("hr-$hrCounter")
        }

        override suspend fun insertExerciseSession(
            record: androidx.health.connect.client.records.ExerciseSessionRecord
        ): List<String> {
            if (failNextSessionInsert) {
                failNextSessionInsert = false
                throw RuntimeException("Simulated session insert failure")
            }
            sessionCounter++
            return listOf("session-$sessionCounter")
        }

        override suspend fun deleteHeartRateByUid(uid: String) {
            deletedHeartRateUids.add(uid)
        }

        override suspend fun deleteExerciseSessionByUid(uid: String) {
            deletedSessionUids.add(uid)
        }
    }

    private class InMemorySharedPreferences : android.content.SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()
        var failCommits = false

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            private val staged = LinkedHashMap<String, Any?>()
            private val removals = LinkedHashSet<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor = apply { staged[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor = apply { staged[key] = values?.toSet() }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor = apply { staged[key] = value }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor = apply { staged[key] = value }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor = apply { staged[key] = value }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor = apply { staged[key] = value }
            override fun remove(key: String): android.content.SharedPreferences.Editor = apply { removals.add(key); staged.remove(key) }
            override fun clear(): android.content.SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                if (failCommits) return false
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                for ((k, v) in staged) {
                    if (v == null) data.remove(k) else data[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
