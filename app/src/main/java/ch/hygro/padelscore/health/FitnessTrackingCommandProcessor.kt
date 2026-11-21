package ch.hygro.padelscore.health

internal interface WorkoutTracker {
    val isTracking: Boolean
    suspend fun startWorkout()
    suspend fun recoverForStop()
    suspend fun stopWorkout()
}

internal interface WorkoutCommandRepository {
    fun activeMatchId(): Long
    fun isDeleted(matchId: Long): Boolean
    fun lastSampleTime(): Long
    fun begin(matchId: Long)
    fun setEndBoundary(timeMillis: Long)
    fun finish(timeMillis: Long)
    fun removeMatch(matchId: Long)
}

internal sealed interface WorkoutCommand {
    val timeMillis: Long

    data class Start(
        val matchId: Long,
        override val timeMillis: Long
    ) : WorkoutCommand

    data class Stop(override val timeMillis: Long) : WorkoutCommand

    data class Discard(override val timeMillis: Long) : WorkoutCommand
}

internal enum class WorkoutCommandResult {
    Completed,
    AlreadyRunning,
    InvalidMatch,
    StartFailed
}

internal class FitnessTrackingCommandProcessor(
    private val tracker: WorkoutTracker,
    private val repository: WorkoutCommandRepository,
    private val publishWorkout: () -> Unit,
    private val publishHistory: () -> Unit
) {
    suspend fun process(command: WorkoutCommand): WorkoutCommandResult = when (command) {
        is WorkoutCommand.Start -> start(command)
        is WorkoutCommand.Stop -> stop(command.timeMillis)
        is WorkoutCommand.Discard -> discard(command.timeMillis)
    }

    private suspend fun start(command: WorkoutCommand.Start): WorkoutCommandResult {
        if (command.matchId <= 0L || repository.isDeleted(command.matchId)) {
            return WorkoutCommandResult.InvalidMatch
        }
        val activeMatchId = repository.activeMatchId()
        if (tracker.isTracking && activeMatchId == command.matchId) {
            return WorkoutCommandResult.AlreadyRunning
        }
        if (activeMatchId > 0L && activeMatchId != command.matchId) {
            val lastMeasurement = repository.lastSampleTime()
            if (!tracker.isTracking) tracker.recoverForStop()
            tracker.stopWorkout()
            repository.finish(lastMeasurement + 1L)
        }
        repository.begin(command.matchId)
        tracker.startWorkout()
        return if (tracker.isTracking) {
            WorkoutCommandResult.Completed
        } else {
            WorkoutCommandResult.StartFailed
        }
    }

    private suspend fun stop(timeMillis: Long): WorkoutCommandResult {
        val matchId = repository.activeMatchId()
        repository.setEndBoundary(timeMillis)
        if (matchId > 0L && !tracker.isTracking) tracker.recoverForStop()
        tracker.stopWorkout()
        repository.finish(timeMillis)
        publishWorkout()
        publishHistory()
        return WorkoutCommandResult.Completed
    }

    private suspend fun discard(timeMillis: Long): WorkoutCommandResult {
        val matchId = repository.activeMatchId()
        repository.setEndBoundary(timeMillis)
        if (matchId > 0L && !tracker.isTracking) tracker.recoverForStop()
        tracker.stopWorkout()
        if (matchId > 0L) repository.removeMatch(matchId)
        return WorkoutCommandResult.Completed
    }
}
