package ch.hygro.padelscore.health

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import ch.hygro.padelscore.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns the ExerciseClient for the entire workout, independent of Compose screens. */
class FitnessTrackingService : Service() {
    companion object {
        private const val CHANNEL_ID = "padel_workout_tracking"
        private const val NOTIFICATION_ID = 3307
        private const val ACTION_START = "ch.hygro.padelscore.health.START"
        private const val ACTION_DISCARD = "ch.hygro.padelscore.health.DISCARD"
        private const val ACTION_STOP = "ch.hygro.padelscore.health.STOP"
        private const val EXTRA_MATCH_START = "match_start_epoch_millis"

        @Volatile private var lastAverage: Int? = null
        val averageHeartRate: Int? get() = lastAverage

        internal fun publishAverage(value: Int?) { lastAverage = value }

        fun start(context: Context, matchStartTimeMillis: Long) {
            val intent = Intent(context, FitnessTrackingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_MATCH_START, matchStartTimeMillis)
            ContextCompat.startForegroundService(context, intent)
        }

        fun discard(context: Context) {
            context.startService(Intent(context, FitnessTrackingService::class.java).setAction(ACTION_DISCARD))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FitnessTrackingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: FitnessTrackerManager
    private lateinit var workoutRepository: LocalWorkoutRepository
    private data class Command(val action: String, val matchId: Long, val startId: Int, val time: Long)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var trackerIsRunning = false

    override fun onCreate() {
        super.onCreate()
        tracker = FitnessTrackerManager(applicationContext)
        workoutRepository = LocalWorkoutRepository(applicationContext)
        val channel = NotificationChannel(CHANNEL_ID, "Padel workout", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        scope.launch {
            for (command in commands) {
                try { process(command) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                    Log.e("FitnessTrackingService", "Workout command failed", e)
                    if (!tracker.isTracking) stopSelf(command.startId)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY.also { stopSelf(startId) }
        if (action == ACTION_START) {
            val heartPermission = if (Build.VERSION.SDK_INT >= 36) {
                "android.permission.health.READ_HEART_RATE"
            } else Manifest.permission.BODY_SENSORS
            if (!ch.hygro.padelscore.storage.SettingsRepository(this).healthTrackingEnabled.value ||
                ContextCompat.checkSelfPermission(this, heartPermission) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                if (!tracker.isTracking) stopSelf(startId)
                return START_NOT_STICKY
            }
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                } else startForeground(NOTIFICATION_ID, buildNotification())
            } catch (e: Exception) {
                Log.e("FitnessTrackingService", "Cannot start foreground tracking", e)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        commands.trySend(Command(action, intent.getLongExtra(EXTRA_MATCH_START, 0L),
            startId, System.currentTimeMillis()))
        return START_NOT_STICKY
    }

    private suspend fun process(command: Command) {
        when (command.action) {
            ACTION_START -> {
                if (command.matchId <= 0L || workoutRepository.isDeleted(command.matchId)) {
                    stopSelf(command.startId)
                    return
                }
                if (trackerIsRunning && workoutRepository.activeMatchId() == command.matchId) return
                if (workoutRepository.activeMatchId() > 0L &&
                    workoutRepository.activeMatchId() != command.matchId) {
                    val lastMeasurement = workoutRepository.lastSampleTime()
                    if (!tracker.isTracking) tracker.recoverForStop()
                    tracker.stopWorkout()
                    // Never stretch an orphaned recording to the next match's start.
                    workoutRepository.finish(lastMeasurement + 1L)
                }
                workoutRepository.begin(command.matchId)
                tracker.startWorkout()
                trackerIsRunning = tracker.isTracking
                if (!trackerIsRunning) {
                    android.widget.Toast.makeText(this,
                        "Health recording could not start. Check permissions and other workout apps.",
                        android.widget.Toast.LENGTH_LONG).show()
                    stopSelf(command.startId)
                }
            }
            ACTION_STOP, ACTION_DISCARD -> {
                val matchId = workoutRepository.activeMatchId()
                workoutRepository.setEndBoundary(command.time)
                if (matchId > 0L && !tracker.isTracking) tracker.recoverForStop()
                tracker.stopWorkout()
                trackerIsRunning = false
                if (command.action == ACTION_DISCARD && matchId > 0L) {
                    workoutRepository.removeMatch(matchId)
                } else workoutRepository.finish(command.time)
                WorkoutDataSender(applicationContext).schedulePublish()
                // stopSelfResult protects newer queued starts; foreground is removed only
                // when this was the latest service request.
                if (stopSelfResult(command.startId)) stopForeground(STOP_FOREGROUND_REMOVE)
                publishAverage(null)
            }
            else -> stopSelf(command.startId)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("PadelScore workout")
            .setContentText("Recording match heart rate")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        // Do not end a recoverable exercise from an unscoped coroutine.
        tracker.releaseCallback()
        commands.close()
        publishAverage(null)
        scope.cancel()
        super.onDestroy()
    }
}
