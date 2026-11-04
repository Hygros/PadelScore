package ch.hygro.padelscore.storage

import android.content.SharedPreferences
import ch.hygro.padelscore.health.LocalWorkoutRepository
import ch.hygro.padelscore.health.WorkoutDataSender
import ch.hygro.padelscore.health.WorkoutSyncListener
import ch.hygro.padelscore.health.WorkoutSyncProtocol
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.SetResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPersistenceErrorTest {

    @Test
    fun removeByMatchIdAbortsAllFollowUpStepsWhenDeletionTombstoneCommitFailsAndSucceedsOnRetry() {
        val env = TestEnvironment()
        val matchId = 1700000000000L
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 140, steps = 1500L)

        // Simulate a failure when synchronously persisting the prepared deletion marker.
        env.deletionPrefs.commitShouldFail = true

        assertThrows(IllegalStateException::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }

        // Preparation was not persisted, so subsequent local deletions and sync must not have run.
        assertFalse(env.workouts.isDeleted(matchId))
        assertFalse(env.workouts.isDeletionPending(matchId))
        assertTrue(env.workouts.deletedMatchIds().isEmpty())
        assertEquals(1500L, env.workouts.stepsForMatch(matchId))
        assertEquals(140, env.workouts.averageHeartRateForMatch(matchId))
        assertEquals(1, env.historyRepository.loadEntries().size)
        assertEquals(0, env.syncTriggeredCount)

        // Productive sync and consent readers must not publish a deletion.
        env.sender.publishAll()
        assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
        assertTrue(env.transport.sentDeletionRpcs.isEmpty())
        assertEquals(listOf(matchId), env.transport.putWorkoutDataItems)
        val consentBeforeRetry = env.queryConsent(matchId, hash)
        assertFalse(consentBeforeRetry.getBoolean("deleted"))
        assertTrue(consentBeforeRetry.getBoolean("allowed"))

        // Retry when storage succeeds.
        env.deletionPrefs.commitShouldFail = false
        env.transport.reset()
        env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)

        assertTrue(env.workouts.isDeleted(matchId))
        assertFalse(env.workouts.isDeletionPending(matchId))
        assertEquals(listOf(matchId), env.workouts.deletedMatchIds())
        assertNull(env.workouts.stepsForMatch(matchId))
        assertNull(env.workouts.averageHeartRateForMatch(matchId))
        assertTrue(env.historyRepository.loadEntries().isEmpty())
        assertEquals(1, env.syncTriggeredCount)

        // Productive sync now publishes the confirmed deletion and blocks workout upload/consent.
        env.sender.publishAll()
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
        assertTrue(env.transport.putWorkoutDataItems.isEmpty())
        val consentAfterRetry = env.queryConsent(matchId, hash)
        assertTrue(consentAfterRetry.getBoolean("deleted"))
        assertFalse(consentAfterRetry.getBoolean("allowed"))
    }

    @Test
    fun removeByMatchIdRollsBackTombstoneAndKeepsWorkoutDataWhenHistoryCommitFailsAndSucceedsOnRetry() {
        val env = TestEnvironment()
        val matchId = 1700000500000L
        val hash = env.recordCompletedMatch(
            matchId = matchId,
            samples = listOf(matchId + 1_000L to 135, matchId + 2_000L to 145),
            steps = 2100L
        )

        env.historyPrefs.commitShouldFail = true
        assertThrows(IllegalStateException::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }

        // Intermediate state immediately after history commit failure:
        // Pending marker was rolled back, no confirmed tombstone exists, and workout/steps/history remain intact.
        assertFalse(env.deletionPrefs.contains("pending_$matchId"))
        assertFalse(env.deletionPrefs.contains(matchId.toString()))
        assertFalse(env.workouts.isDeleted(matchId))
        assertFalse(env.workouts.isDeletionPending(matchId))
        assertTrue(env.workouts.deletedMatchIds().isEmpty())
        assertEquals(2100L, env.workouts.stepsForMatch(matchId))
        assertEquals(140, env.workouts.averageHeartRateForMatch(matchId))
        assertEquals(145, env.workouts.maximumHeartRateForMatch(matchId))
        val entryAfterFailure = env.historyRepository.loadEntries().single()
        assertEquals(140, entryAfterFailure.averageHeartRate)
        assertEquals(145, entryAfterFailure.maximumHeartRate)
        assertEquals(2100L, entryAfterFailure.steps)
        assertEquals(0, env.syncTriggeredCount)

        // Productive sync and consent readers must not publish any deletion after failed history commit.
        env.sender.publishTo("phone-1")
        assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
        assertTrue(env.transport.sentDeletionRpcs.isEmpty())
        assertEquals(listOf(matchId), env.transport.putWorkoutDataItems)
        val consentAfterFailure = env.queryConsent(matchId, hash)
        assertFalse(consentAfterFailure.getBoolean("deleted"))
        assertTrue(consentAfterFailure.getBoolean("allowed"))

        // Retry when history storage succeeds.
        env.historyPrefs.commitShouldFail = false
        env.transport.reset()
        env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)

        assertTrue(env.workouts.isDeleted(matchId))
        assertEquals(listOf(matchId), env.workouts.deletedMatchIds())
        assertNull(env.workouts.stepsForMatch(matchId))
        assertNull(env.workouts.averageHeartRateForMatch(matchId))
        assertNull(env.workouts.maximumHeartRateForMatch(matchId))
        assertTrue(env.historyRepository.loadEntries().isEmpty())
        assertEquals(1, env.syncTriggeredCount)

        env.sender.publishTo("phone-1")
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
        assertTrue(env.transport.putWorkoutDataItems.isEmpty())
        val consentAfterRetry = env.queryConsent(matchId, hash)
        assertTrue(consentAfterRetry.getBoolean("deleted"))
        assertFalse(consentAfterRetry.getBoolean("allowed"))
    }

    @Test
    fun syncRunningBetweenPreparationAndHistoryCommitDoesNotPublishPreparedDeletion() {
        var midFlightChecksRan = false
        lateinit var env: TestEnvironment
        val matchId = 1700000600000L
        lateinit var workoutHash: String

        env = TestEnvironment(
            onBeforeHistoryCommit = {
                midFlightChecksRan = true
                // Prepared marker is persisted on disk, but confirmed marker is not.
                assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
                assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))
                assertFalse(env.workouts.isDeleted(matchId))
                assertTrue(env.workouts.isDeletionPending(matchId))
                assertTrue(env.workouts.deletedMatchIds().isEmpty())

                // A sync running mid-flight (even with fresh repository instances) must neither publish
                // a deletion nor upload/confirm the workout while the deletion is in progress,
                // and reconcilePendingDeletions on a separate instance must neither roll back nor confirm it.
                val concurrentWorkouts = env.newWorkoutsInstance()
                assertFalse(concurrentWorkouts.reconcilePendingDeletions())
                assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
                assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))

                val concurrentSender = env.newSenderInstance(concurrentWorkouts)
                concurrentSender.publishAll()
                concurrentSender.publishTo("phone-1")
                concurrentSender.confirmImported(matchId, workoutHash)

                assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
                assertTrue(env.transport.sentDeletionRpcs.isEmpty())
                assertTrue(env.transport.putWorkoutDataItems.isEmpty())
                assertTrue(env.transport.sentWorkoutChunks.isEmpty())
                assertNull(env.receiptsPrefs.getString("hash_$matchId", null))

                val consentDuringPreparation = env.queryConsent(matchId, workoutHash, concurrentWorkouts)
                assertFalse(consentDuringPreparation.getBoolean("deleted"))
                assertFalse(consentDuringPreparation.getBoolean("allowed"))
            }
        )
        workoutHash = env.recordCompletedMatch(matchId = matchId, bpm = 142, steps = 1800L)

        // First run: history commit fails right after the mid-flight sync.
        env.historyPrefs.commitShouldFail = true
        assertThrows(IllegalStateException::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }
        assertTrue(midFlightChecksRan)
        assertFalse(env.workouts.isDeleted(matchId))
        assertFalse(env.workouts.isDeletionPending(matchId))

        // Second run: history commit succeeds after the mid-flight sync, releasing the deletion.
        midFlightChecksRan = false
        env.historyPrefs.commitShouldFail = false
        env.transport.reset()
        env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)

        assertTrue(midFlightChecksRan)
        assertTrue(env.workouts.isDeleted(matchId))
        assertFalse(env.workouts.isDeletionPending(matchId))
        env.sender.publishAll()
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
    }

    @Test
    fun historyCommitFailureAndRollbackFailureReportsBothErrorsAndPreventsUnconfirmedSyncPublication() {
        lateinit var env: TestEnvironment
        val matchId = 1700000700000L
        env = TestEnvironment(
            onBeforeHistoryCommit = {
                // Preparation succeeded; now both history commit and rollback commit will fail.
                env.historyPrefs.commitShouldFail = true
                env.deletionPrefs.commitShouldFail = true
            }
        )
        val hash = env.recordCompletedMatch(
            matchId = matchId,
            samples = listOf(matchId + 1_000L to 136, matchId + 2_000L to 148),
            steps = 2300L
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }
        assertEquals("Could not persist match history", thrown.message)
        // Rollback failure must not be swallowed.
        assertEquals(1, thrown.suppressed.size)
        assertTrue(thrown.suppressed.single() is IllegalStateException)
        assertEquals("Cannot clear pending workout deletion", thrown.suppressed.single().message)

        // Because rollback commit failed, pending_$matchId is still on disk in workout_deletions,
        // while the match is still present in padel_score_history.
        assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
        assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))

        // Even while deletionPrefs commits are still failing, neither in-process nor restarted
        // sync readers may publish the unconfirmed deletion, and history/workout data remain intact.
        val restartedWorkouts = env.newWorkoutsInstance()
        val restartedSender = env.newSenderInstance(restartedWorkouts)
        restartedSender.publishAll()
        restartedSender.publishTo("phone-1")
        assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
        assertTrue(env.transport.sentDeletionRpcs.isEmpty())
        assertFalse(restartedWorkouts.isDeleted(matchId))
        assertTrue(restartedWorkouts.deletedMatchIds().isEmpty())

        val consentWhileRollbackFailed = env.queryConsent(matchId, hash, restartedWorkouts)
        assertFalse(consentWhileRollbackFailed.getBoolean("deleted"))
        assertTrue(consentWhileRollbackFailed.getBoolean("allowed"))

        val entry = env.historyRepository.loadEntries().single()
        assertEquals(142, entry.averageHeartRate)
        assertEquals(148, entry.maximumHeartRate)
        assertEquals(2300L, entry.steps)

        // Once deletionPrefs becomes writable again, reconciliation cleans up the stale pending flag.
        env.deletionPrefs.commitShouldFail = false
        assertFalse(restartedWorkouts.isDeleted(matchId))
        assertFalse(env.deletionPrefs.contains("pending_$matchId"))
    }

    @Test
    fun restartAfterCrashBeforeHistoryCommitRollsBackPendingDeletionAndPreservesMatch() {
        lateinit var env: TestEnvironment
        val matchId = 1700000800000L
        env = TestEnvironment(
            onBeforeHistoryCommit = {
                throw SimulatedProcessCrash("Crash after prepareDeletion before saveEntries")
            }
        )
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 144, steps = 1950L)

        assertThrows(SimulatedProcessCrash::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }

        // Persistent state at crash time: pending marker is set, history still contains matchId.
        assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
        assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))

        // Simulate process restart with brand-new instances on the same persisted preferences.
        val restartedWorkouts = env.newWorkoutsInstance()
        val restartedHistory = env.newHistoryRepositoryInstance(restartedWorkouts)
        val restartedSender = env.newSenderInstance(restartedWorkouts)

        restartedSender.publishAll()
        assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
        assertTrue(env.transport.sentDeletionRpcs.isEmpty())
        assertFalse( env.deletionPrefs.contains("pending_$matchId"))
        assertFalse(restartedWorkouts.isDeleted(matchId))

        val entryAfterRestart = restartedHistory.loadEntries().single()
        assertEquals(matchId, entryAfterRestart.matchId)
        assertEquals(144, entryAfterRestart.averageHeartRate)
        assertEquals(1950L, entryAfterRestart.steps)

        val consentAfterRestart = env.queryConsent(matchId, hash, restartedWorkouts)
        assertFalse(consentAfterRestart.getBoolean("deleted"))
        assertTrue(consentAfterRestart.getBoolean("allowed"))
    }

    @Test
    fun restartAfterCrashBetweenHistoryCommitAndConfirmDeletionCompletesAndPublishesDeletion() {
        val env = TestEnvironment()
        val matchId = 1700000850000L
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 146, steps = 2050L)

        // Crash right when confirmDeletion tries to write to deletionPrefs after history commit succeeded.
        var historyCommitted = false
        env.historyPrefs.onAfterCommit = {
            historyCommitted = true
            env.deletionPrefs.onBeforeCommit = {
                throw SimulatedProcessCrash("Crash after saveEntries before confirmDeletion")
            }
        }

        assertThrows(SimulatedProcessCrash::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }
        assertTrue(historyCommitted)

        // Persistent state at crash time:
        // pending_$matchId is true, confirmed tombstone is not yet written,
        // matchId is already removed from historyPrefs, and workout/steps are still on disk.
        assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
        assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))
        assertEquals("[]", env.historyPrefs.getString(MatchHistoryRepository.KEY_HISTORY_JSON, null))
        assertEquals(0, env.syncTriggeredCount)

        // Clear crash hook and simulate process restart.
        env.historyPrefs.onAfterCommit = null
        env.deletionPrefs.onBeforeCommit = null
        val restartedWorkouts = env.newWorkoutsInstance()
        val restartedHistory = env.newHistoryRepositoryInstance(restartedWorkouts)
        val restartedSender = env.newSenderInstance(restartedWorkouts)

        // Loading history after restart reconciles the committed history deletion, finalizes the
        // tombstone, purges workout/step data, and triggers scheduleDeletionSync.
        assertTrue(restartedHistory.loadEntries().isEmpty())
        assertEquals(1, env.syncTriggeredCount)
        assertFalse(env.deletionPrefs.contains("pending_$matchId"))
        assertTrue(env.deletionPrefs.getBoolean(matchId.toString(), false))
        assertTrue(restartedWorkouts.isDeleted(matchId))
        assertEquals(listOf(matchId), restartedWorkouts.deletedMatchIds())
        assertNull(restartedWorkouts.stepsForMatch(matchId))
        assertNull(restartedWorkouts.averageHeartRateForMatch(matchId))
        assertNull(restartedWorkouts.maximumHeartRateForMatch(matchId))

        // Productive sync publishes the deletion and blocks workout upload and consent.
        restartedSender.publishAll()
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
        assertTrue(env.transport.putWorkoutDataItems.isEmpty())

        val consentAfterRestart = env.queryConsent(matchId, hash, restartedWorkouts)
        assertTrue(consentAfterRestart.getBoolean("deleted"))
        assertFalse(consentAfterRestart.getBoolean("allowed"))
    }

    @Test
    fun restartAfterCrashBetweenConfirmDeletionAndWorkoutPurgeCleansUpAndPublishesDeletion() {
        val env = TestEnvironment()
        val matchId = 1700000900000L
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 150, steps = 2400L)

        // Crash right after confirmDeletion commits the tombstone to deletionPrefs,
        // before stepPrefs and workoutPrefs are purged.
        env.stepPrefs.onBeforeCommit = {
            throw SimulatedProcessCrash("Crash after tombstone commit before step/workout purge")
        }

        assertThrows(SimulatedProcessCrash::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }

        // Confirmed tombstone is on disk, but stepPrefs and workoutPrefs still hold the raw data.
        assertTrue(env.deletionPrefs.getBoolean(matchId.toString(), false))
        assertFalse(env.deletionPrefs.contains("pending_$matchId"))
        assertTrue(env.stepPrefs.contains(matchId.toString()))
        assertEquals(1, JSONArray(env.workoutPrefs.getString("completed_workouts", "[]")).length())

        // Clear crash hook and simulate process restart.
        env.stepPrefs.onBeforeCommit = null
        val restartedWorkouts = env.newWorkoutsInstance()
        val restartedSender = env.newSenderInstance(restartedWorkouts)

        restartedSender.publishAll()
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
        assertTrue(env.transport.putWorkoutDataItems.isEmpty())

        // Reconciliation purged the leftover step and workout entries.
        assertFalse(env.stepPrefs.contains(matchId.toString()))
        assertEquals(0, JSONArray(env.workoutPrefs.getString("completed_workouts", "[]")).length())
        assertNull(restartedWorkouts.stepsForMatch(matchId))
        assertNull(restartedWorkouts.averageHeartRateForMatch(matchId))
        assertNull(restartedWorkouts.maximumHeartRateForMatch(matchId))

        val consentAfterRestart = env.queryConsent(matchId, hash, restartedWorkouts)
        assertTrue(consentAfterRestart.getBoolean("deleted"))
        assertFalse(consentAfterRestart.getBoolean("allowed"))
    }

    @Test
    fun retryAfterInterruptedDeletionIsIdempotentAndPublishesDeletionReliably() {
        val env = TestEnvironment()
        val matchId = 1700000950000L
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 138, steps = 1700L)

        // Simulate an interrupted deletion where saveEntries succeeded, then confirmDeletion failed.
        env.historyPrefs.onAfterCommit = {
            env.deletionPrefs.commitShouldFail = true
        }
        assertThrows(IllegalStateException::class.java) {
            env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        }
        env.historyPrefs.onAfterCommit = null

        // Even before retry, because history removal already committed while pending_$matchId was set,
        // the deletion is recognized as confirmed.
        assertTrue(env.workouts.isDeleted(matchId))
        assertEquals(listOf(matchId), env.workouts.deletedMatchIds())

        // Storage recovers; user or caller retries removeByMatchId multiple times.
        env.deletionPrefs.commitShouldFail = false
        env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)
        env.historyRepository.removeByMatchId(matchId, deleteHealthData = true)

        assertTrue(env.deletionPrefs.getBoolean(matchId.toString(), false))
        assertFalse(env.deletionPrefs.contains("pending_$matchId"))
        assertTrue(env.historyRepository.loadEntries().isEmpty())
        assertNull(env.workouts.stepsForMatch(matchId))
        assertNull(env.workouts.averageHeartRateForMatch(matchId))

        env.sender.publishAll()
        assertEquals(listOf(matchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to matchId), env.transport.sentDeletionRpcs)
        val consent = env.queryConsent(matchId, hash)
        assertTrue(consent.getBoolean("deleted"))
        assertFalse(consent.getBoolean("allowed"))
    }

    @Test
    fun legacyDeletionTombstonesFromPreviousVersionAreRecognizedAndPublished() {
        val env = TestEnvironment()
        val legacyMatchId = 1699999000000L
        val hash = env.recordCompletedMatch(matchId = legacyMatchId, bpm = 139, steps = 1600L)

        // Simulate a legacy tombstone written directly as "<matchId>" -> true without pending state,
        // and remove the history entry as in an upgraded installation.
        env.historyPrefs.edit().putString(MatchHistoryRepository.KEY_HISTORY_JSON, "[]").commit()
        env.deletionPrefs.edit().putBoolean(legacyMatchId.toString(), true).commit()

        val upgradedWorkouts = env.newWorkoutsInstance()
        val upgradedHistory = env.newHistoryRepositoryInstance(upgradedWorkouts)
        val upgradedSender = env.newSenderInstance(upgradedWorkouts)

        assertTrue(upgradedWorkouts.isDeleted(legacyMatchId))
        assertEquals(listOf(legacyMatchId), upgradedWorkouts.deletedMatchIds())
        assertNull(upgradedWorkouts.stepsForMatch(legacyMatchId))
        assertNull(upgradedWorkouts.averageHeartRateForMatch(legacyMatchId))
        assertNull(upgradedWorkouts.maximumHeartRateForMatch(legacyMatchId))

        // Saving a completed match with a legacy-deleted matchId must still be blocked.
        upgradedHistory.saveCompletedMatch(
            finishedMatchState(startedAt = legacyMatchId, finishedAt = legacyMatchId + 60_000L),
            averageHeartRate = 139
        )
        assertTrue(upgradedHistory.loadEntries().isEmpty())

        // Productive sender publishes the legacy tombstone and does not upload the workout.
        upgradedSender.publishAll()
        assertEquals(listOf(legacyMatchId), env.transport.publishedDeletionDataItems)
        assertEquals(listOf("phone-1" to legacyMatchId), env.transport.sentDeletionRpcs)
        assertTrue(env.transport.putWorkoutDataItems.isEmpty())

        val consent = env.queryConsent(legacyMatchId, hash, upgradedWorkouts)
        assertTrue(consent.getBoolean("deleted"))
        assertFalse(consent.getBoolean("allowed"))
    }

    @Test
    fun historyReadOrDecodeErrorNeverReleasesPreparedDeletionOrOverwritesHistory() {
        val env = TestEnvironment()
        val matchId = 1700000980000L
        val hash = env.recordCompletedMatch(matchId = matchId, bpm = 141, steps = 1900L)

        // Persist a prepared deletion marker and corrupt the stored history JSON.
        env.deletionPrefs.edit().putBoolean("pending_$matchId", true).commit()
        env.historyPrefs.edit()
            .putString(MatchHistoryRepository.KEY_HISTORY_JSON, "{corrupt-json")
            .commit()

        val restartedWorkouts = env.newWorkoutsInstance()
        val restartedHistory = env.newHistoryRepositoryInstance(restartedWorkouts)
        val restartedSender = env.newSenderInstance(restartedWorkouts)

        // isPresentInHistory must return null (not false) on decode error, so the prepared
        // deletion must neither be promoted to confirmed nor published externally.
        assertNull(restartedWorkouts.isPresentInHistory(matchId))
        assertFalse(restartedWorkouts.reconcilePendingDeletions())
        assertFalse(restartedWorkouts.isDeleted(matchId))
        assertTrue(restartedWorkouts.deletedMatchIds().isEmpty())
        assertTrue(env.deletionPrefs.getBoolean("pending_$matchId", false))
        assertFalse(env.deletionPrefs.getBoolean(matchId.toString(), false))
        assertEquals(1900L, restartedWorkouts.stepsForMatch(matchId))
        assertEquals(141, restartedWorkouts.averageHeartRateForMatch(matchId))

        restartedSender.publishAll()
        restartedSender.publishTo("phone-1")
        assertTrue(env.transport.publishedDeletionDataItems.isEmpty())
        assertTrue(env.transport.sentDeletionRpcs.isEmpty())

        val consent = env.queryConsent(matchId, hash, restartedWorkouts)
        assertFalse(consent.getBoolean("deleted"))

        // Calling removeByMatchId while history JSON is corrupt must fail before overwriting
        // history with "[]" or confirming the deletion.
        assertThrows(Exception::class.java) {
            restartedHistory.removeByMatchId(matchId, deleteHealthData = true)
        }
        assertFalse(restartedWorkouts.isDeleted(matchId))
        assertTrue(restartedWorkouts.deletedMatchIds().isEmpty())
        assertEquals(
            "{corrupt-json",
            env.historyPrefs.getString(MatchHistoryRepository.KEY_HISTORY_JSON, null)
        )
    }

    @Test
    fun saveCompletedMatchThrowsOnCommitFailureAndRetryDoesNotCreateDuplicateEntries() {
        val env = TestEnvironment()
        val matchId = 1700001000000L
        val completedState = finishedMatchState(startedAt = matchId, finishedAt = matchId + 90_000L)

        // First attempt fails.
        env.historyPrefs.commitShouldFail = true
        assertThrows(IllegalStateException::class.java) {
            env.historyRepository.saveCompletedMatch(completedState, averageHeartRate = 148)
        }
        assertTrue(env.historyRepository.loadEntries().isEmpty())

        // Retry succeeds.
        env.historyPrefs.commitShouldFail = false
        env.historyRepository.saveCompletedMatch(completedState, averageHeartRate = 148)
        assertEquals(1, env.historyRepository.loadEntries().size)

        // Subsequent save with the same matchId updates in place without duplicating.
        env.historyRepository.saveCompletedMatch(
            completedState.copy(averageHeartRate = 150),
            averageHeartRate = 150
        )
        val entries = env.historyRepository.loadEntries()
        assertEquals(1, entries.size)
        assertEquals(matchId, entries.single().matchId)
        assertEquals(150, entries.single().averageHeartRate)
    }

    private class SimulatedProcessCrash(message: String) : Error(message)

    private inner class TestEnvironment(
        onBeforeHistoryCommit: (() -> Unit)? = null
    ) {
        val workoutPrefs = FakeSharedPreferences()
        val stepPrefs = FakeSharedPreferences()
        val deletionPrefs = FakeSharedPreferences()
        val historyPrefs = FakeSharedPreferences()
        val receiptsPrefs = FakeSharedPreferences()
        var healthSyncEnabled = true
        var syncTriggeredCount = 0
        val transport = RecordingTransport()

        val workouts = LocalWorkoutRepository(
            preferences = workoutPrefs,
            stepPreferences = stepPrefs,
            deletions = deletionPrefs,
            historyPreferences = historyPrefs
        )

        val historyRepository = MatchHistoryRepository(
            preferences = historyPrefs,
            workouts = workouts,
            scheduleDeletionSync = { syncTriggeredCount++ },
            onBeforeHistoryCommit = onBeforeHistoryCommit
        )

        val sender = newSenderInstance(workouts)

        fun newWorkoutsInstance(): LocalWorkoutRepository = LocalWorkoutRepository(
            preferences = workoutPrefs,
            stepPreferences = stepPrefs,
            deletions = deletionPrefs,
            historyPreferences = historyPrefs
        )

        fun newHistoryRepositoryInstance(
            workoutRepo: LocalWorkoutRepository = newWorkoutsInstance()
        ): MatchHistoryRepository = MatchHistoryRepository(
            preferences = historyPrefs,
            workouts = workoutRepo,
            scheduleDeletionSync = { syncTriggeredCount++ }
        )

        fun newSenderInstance(
            workoutRepo: LocalWorkoutRepository = workouts
        ): WorkoutDataSender = WorkoutDataSender(
            workoutRepository = { workoutRepo },
            isSyncEnabled = { healthSyncEnabled },
            receiptsProvider = { receiptsPrefs },
            transport = transport
        )

        fun recordCompletedMatch(
            matchId: Long,
            bpm: Int = 140,
            samples: List<Pair<Long, Int>> = listOf(matchId + 1_000L to bpm),
            steps: Long = 1500L
        ): String {
            workouts.begin(matchId)
            workouts.addSamples(samples)
            workouts.updateSteps(steps)
            workouts.finish(matchId + 60_000L)
            val average = workouts.averageHeartRateForMatch(matchId) ?: bpm
            val completedState = finishedMatchState(
                startedAt = matchId,
                finishedAt = matchId + 60_000L,
                averageHeartRate = average
            )
            historyRepository.saveCompletedMatch(completedState, averageHeartRate = average)
            val storedWorkouts = JSONArray(workouts.completedWorkoutsJson())
            val workoutJson = (0 until storedWorkouts.length())
                .map { storedWorkouts.getJSONObject(it) }
                .first { it.getLong("startTimeMillis") == matchId }
                .toString()
            return WorkoutSyncProtocol.digest(workoutJson.toByteArray(Charsets.UTF_8))
        }

        fun queryConsent(
            matchId: Long,
            hash: String,
            workoutRepo: LocalWorkoutRepository = workouts
        ): JSONObject {
            val responseBytes = WorkoutSyncListener.handleConsentRequest(
                path = "${WorkoutSyncProtocol.CONSENT}$matchId",
                request = hash.toByteArray(Charsets.UTF_8),
                repository = workoutRepo,
                healthSyncEnabled = healthSyncEnabled
            )
            return JSONObject(String(responseBytes, Charsets.UTF_8))
        }
    }

    private class RecordingTransport : WorkoutDataSender.Transport {
        val connectedNodes = linkedSetOf("phone-1")
        val reachableNodes = linkedSetOf("phone-1")
        val publishedDeletionDataItems = mutableListOf<Long>()
        val sentDeletionRpcs = mutableListOf<Pair<String, Long>>()
        val putWorkoutDataItems = mutableListOf<Long>()
        val sentWorkoutChunks = mutableListOf<Pair<String, String>>()

        fun reset() {
            publishedDeletionDataItems.clear()
            sentDeletionRpcs.clear()
            putWorkoutDataItems.clear()
            sentWorkoutChunks.clear()
        }

        override fun connectedNodeIds(): Set<String> = connectedNodes

        override fun reachableReceiverNodeIds(): Set<String> = reachableNodes

        override fun publishDeletionDataItem(id: Long) {
            publishedDeletionDataItems.add(id)
        }

        override fun sendDeletionRpc(nodeId: String, id: Long): String {
            sentDeletionRpcs.add(nodeId to id)
            return "DELETED:$id"
        }

        override fun putWorkoutDataItem(id: Long, bytes: ByteArray, hash: String) {
            putWorkoutDataItems.add(id)
        }

        override fun sendWorkoutChunkRpc(nodeId: String, path: String, chunk: ByteArray): String {
            sentWorkoutChunks.add(nodeId to path)
            return "STORED"
        }

        override fun clearPendingWorkoutDataItems() = Unit
    }

    private fun finishedMatchState(
        startedAt: Long,
        finishedAt: Long,
        averageHeartRate: Int = 140
    ): MatchState {
        return MatchState(
            opponentSets = 0,
            ourSets = 2,
            phase = MatchPhase.MATCH_FINISHED,
            completedSets = listOf(SetResult(4, 6), SetResult(3, 6)),
            totalPointsPlayed = 58,
            totalGamesPlayed = 19,
            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt,
            averageHeartRate = averageHeartRate
        )
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()
        var commitShouldFail: Boolean = false
        var onBeforeCommit: (() -> Unit)? = null
        var onAfterCommit: (() -> Unit)? = null

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            if (data.containsKey(key)) data[key] as? String else defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            if (data.containsKey(key)) (data[key] as? Set<String>)?.toMutableSet() else defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private inner class EditorImpl : SharedPreferences.Editor {
            private val staged = LinkedHashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = value
            }

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = values?.toSet()
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = value
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = value
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = value
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
                removals.remove(key)
                staged[key] = value
            }

            override fun remove(key: String): SharedPreferences.Editor = apply {
                staged.remove(key)
                removals.add(key)
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
                staged.clear()
                removals.clear()
            }

            override fun commit(): Boolean {
                onBeforeCommit?.invoke()
                if (commitShouldFail) return false
                applyToBackingMap()
                onAfterCommit?.invoke()
                return true
            }

            override fun apply() {
                onBeforeCommit?.invoke()
                if (!commitShouldFail) {
                    applyToBackingMap()
                    onAfterCommit?.invoke()
                }
            }

            private fun applyToBackingMap() {
                if (clearRequested) data.clear()
                for (key in removals) data.remove(key)
                for ((key, value) in staged) {
                    if (value == null) data.remove(key) else data[key] = value
                }
            }
        }
    }
}
