package ch.hygro.padelscore.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Local-only fallback. No Health Connect step records are created. */
internal class MatchStepSensor(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val repository = LocalWorkoutRepository(context)
    private var accumulator = StepDeltaAccumulator()
    private var startedNanos = 0L
    private var matchId = 0L
    private var registered = false

    fun start() {
        stop()
        matchId = repository.activeMatchId()
        if (matchId <= 0L || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        accumulator = StepDeltaAccumulator(repository.stepsForMatch(matchId) ?: 0L)
        startedNanos = SystemClock.elapsedRealtimeNanos()
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
    }

    fun stop() {
        if (registered) manager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.timestamp < startedNanos || event.values.isEmpty() ||
            !event.values[0].isFinite() || repository.activeMatchId() != matchId ||
            repository.isDeleted(matchId)) return
        accumulator.accept(event.values[0].toLong())?.let { repository.updateSteps(it) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
