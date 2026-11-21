package ch.hygro.padelscore.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessTrackingCommandProcessorTest {
    @Test
    fun discardRemovesLocalWorkoutWithoutSchedulingUpload() = runBlocking {
        val repository = FakeRepository(activeMatchId = 42L)
        val tracker = FakeTracker(isTracking = true)
        var workoutPublishes = 0
        var historyPublishes = 0
        val processor = FitnessTrackingCommandProcessor(
            tracker = tracker,
            repository = repository,
            publishWorkout = { workoutPublishes++ },
            publishHistory = { historyPublishes++ }
        )

        processor.process(WorkoutCommand.Discard(timeMillis = 100L))

        assertEquals(listOf(42L), repository.removedMatches)
        assertEquals(0, workoutPublishes)
        assertEquals(0, historyPublishes)
        assertFalse(tracker.isTracking)
    }

    @Test
    fun repeatedStartForSameMatchDoesNotStartExerciseTwice() = runBlocking {
        val repository = FakeRepository(activeMatchId = 42L)
        val tracker = FakeTracker(isTracking = true)
        val processor = FitnessTrackingCommandProcessor(tracker, repository, {}, {})

        val result = processor.process(WorkoutCommand.Start(matchId = 42L, timeMillis = 100L))

        assertEquals(WorkoutCommandResult.AlreadyRunning, result)
        assertEquals(0, tracker.startCalls)
        assertTrue(repository.begunMatches.isEmpty())
    }

    private class FakeTracker(
        override var isTracking: Boolean
    ) : WorkoutTracker {
        var startCalls = 0

        override suspend fun startWorkout() {
            startCalls++
            isTracking = true
        }

        override suspend fun recoverForStop() {
            isTracking = true
        }

        override suspend fun stopWorkout() {
            isTracking = false
        }
    }

    private class FakeRepository(
        private var activeMatchId: Long
    ) : WorkoutCommandRepository {
        val begunMatches = mutableListOf<Long>()
        val removedMatches = mutableListOf<Long>()

        override fun activeMatchId(): Long = activeMatchId
        override fun isDeleted(matchId: Long): Boolean = false
        override fun lastSampleTime(): Long = activeMatchId
        override fun begin(matchId: Long) {
            begunMatches += matchId
            activeMatchId = matchId
        }
        override fun setEndBoundary(timeMillis: Long) = Unit
        override fun finish(timeMillis: Long) {
            activeMatchId = 0L
        }
        override fun removeMatch(matchId: Long) {
            removedMatches += matchId
            activeMatchId = 0L
        }
    }
}
