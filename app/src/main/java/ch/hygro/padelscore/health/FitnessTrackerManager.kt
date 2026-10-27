package ch.hygro.padelscore.health

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class FitnessTrackerManager(private val context: Context) {
    private val healthClient = HealthServices.getClient(context)
    private val exerciseClient = healthClient.exerciseClient
    private val workoutRepository = LocalWorkoutRepository(context)
    private val stepSensor = MatchStepSensor(context)
    private var acceptUpdates = false
    private var resumedSegment = false
    private var endingRequested = false
    private val recoveryState = context.getSharedPreferences("workout_tracking_recovery", Context.MODE_PRIVATE)
    private var healthStepsOffset = 0L

    internal fun releaseCallback() {
        acceptUpdates = false
        stepSensor.stop()
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback) }
    }

    @Volatile
    var averageHeartRate: Int? = null
        private set

    @Volatile
    private var tracking = false

    val isTracking: Boolean get() = tracking

    private val lifecycleMutex = Mutex()
    @Volatile
    private var finalUpdate = CompletableDeferred<Unit>()


    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            if (!acceptUpdates) return
            averageHeartRate = update.latestMetrics.getData(DataType.HEART_RATE_BPM_STATS)?.average?.toInt() ?: averageHeartRate
            FitnessTrackingService.publishAverage(averageHeartRate)
            workoutRepository.updateAverage(averageHeartRate)
            update.latestMetrics.getData(DataType.STEPS_TOTAL)?.let { steps ->
                stepSensor.stop()
                workoutRepository.updateSteps(healthStepsOffset + steps.total)
            }
            val samples = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            val bootEpochMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            workoutRepository.addSamples(samples.map {
                (bootEpochMillis + it.timeDurationFromBoot.toMillis()) to it.value.toInt()
            })
            if (resumedSegment) {
                averageHeartRate = workoutRepository.recordedSampleAverage() ?: averageHeartRate
                workoutRepository.updateAverage(averageHeartRate)
                FitnessTrackingService.publishAverage(averageHeartRate)
            }
            // Signal only after all measurements in the terminal update were stored.
            if (update.exerciseStateInfo.state.isEnded) {
                finalUpdate.complete(Unit)
                if (!endingRequested) FitnessTrackingService.stop(context)
            }
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e("FitnessTracker", "Exercise callback registration failed", throwable)
        }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    suspend fun startWorkout() = lifecycleMutex.withLock {
        if (tracking) return@withLock
        finalUpdate = CompletableDeferred()
        endingRequested = false
        resumedSegment = false
        healthStepsOffset = if (recoveryState.getLong("match_id", 0L) == workoutRepository.activeMatchId()) {
            recoveryState.getLong("steps_offset", 0L)
        } else 0L
        averageHeartRate = workoutRepository.averageHeartRateForMatch(workoutRepository.activeMatchId())
        FitnessTrackingService.publishAverage(averageHeartRate)
        try {
            // The tracked-status constants are library-restricted in Health Services 1.0.0.
            // Let the public startExerciseAsync API decide whether a new exercise can start.
            resumedSegment = workoutRepository.recordedSampleAverage() != null
            healthStepsOffset = workoutRepository.stepsForMatch(workoutRepository.activeMatchId()) ?: 0L
            recoveryState.edit {
                putLong("match_id", workoutRepository.activeMatchId())
                putLong("steps_offset", healthStepsOffset)
            }
            val capabilities = withTimeoutOrNull(10_000L.milliseconds) { exerciseClient.getCapabilitiesAsync().await() }
                ?: error("Health Services capabilities unavailable")
            val supportedTypes = capabilities.supportedExerciseTypes
            
            val exerciseType = when {
                ExerciseType.TENNIS in supportedTypes -> ExerciseType.TENNIS
                ExerciseType.WORKOUT in supportedTypes -> ExerciseType.WORKOUT
                else -> return@withLock
            }

            val supportedDataTypes = capabilities.getExerciseTypeCapabilities(exerciseType).supportedDataTypes
            val requestedDataTypes = setOf(
                DataType.HEART_RATE_BPM, DataType.HEART_RATE_BPM_STATS, DataType.CALORIES_TOTAL,
                DataType.STEPS, DataType.STEPS_TOTAL
            ).filterTo(mutableSetOf()) { it in supportedDataTypes }
            val config = ExerciseConfig(
                exerciseType = exerciseType,
                dataTypes = requestedDataTypes,
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false
            )
            acceptUpdates = true
            exerciseClient.setUpdateCallback(ContextCompat.getMainExecutor(context), callback)
            withTimeoutOrNull(10_000L.milliseconds) { exerciseClient.startExerciseAsync(config).await(); true }
                ?: error("Health Services start timed out")
            tracking = true
            stepSensor.start()
            Log.d("FitnessTracker", "Workout started: $exerciseType")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FitnessTracker", "Failed to start workout", e)
            tracking = false
            acceptUpdates = false
            stepSensor.stop()
            runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
        }
    }

    suspend fun recoverForStop() = lifecycleMutex.withLock {
        if (tracking) return@withLock
        // No public ownership check is available in this library version.
        // Never end an exercise that might belong to another application.
        Log.w("FitnessTracker", "Cannot safely reattach after process recreation")
    }

    suspend fun stopWorkout() = lifecycleMutex.withLock {
        if (!tracking) return@withLock
        endingRequested = true
        val completion = finalUpdate
        try {
            if (!completion.isCompleted) {
                withTimeoutOrNull(10_000L.milliseconds) { exerciseClient.endExerciseAsync().await(); true }
                    ?: Log.w("FitnessTracker", "End request timed out; keeping last measurements")
            }
            // Device/API fault fallback, not a fixed sleep: return immediately on ENDED.
            val received = withTimeoutOrNull(5_000L.milliseconds) {
                completion.await()
                true
            } ?: false
            if (received) {
                Log.d("FitnessTracker", "Workout stopped; final measurements stored")
            } else {
                Log.w("FitnessTracker", "Final workout update missing; retaining last received measurements")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FitnessTracker", "Failed to stop workout", e)
        } finally {
            tracking = false
            acceptUpdates = false
            stepSensor.stop()
            withContext(NonCancellable) {
                runCatching { kotlinx.coroutines.withTimeout(5_000L.milliseconds) { exerciseClient.clearUpdateCallbackAsync(callback).await() } }
                    .onFailure { Log.w("FitnessTracker", "Callback cleanup failed", it) }
            }
        }
    }
}
