package ch.hygro.padelscore.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Relative-orientation heuristic, not a system wrist-down event.
 * A touch establishes the viewing orientation. Rotation away from that pose
 * selects the reduced UI; rotation back restores it. It never changes power mode.
 */
class WristDisplayController(
    context: Context,
    private val onLoweredChanged: (Boolean) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var enabled = false
    private var resumed = false
    private var registered = false
    private var lowered = false
    private var reference: FloatArray? = null
    private var gravity: FloatArray? = null
    private var previousTimestamp = 0L
    private var candidate: Boolean? = null
    private var candidateSince = 0L
    private var interactionUntil = 0L

    fun setEnabled(value: Boolean) {
        enabled = value
        updateRegistration()
        if (!value) {
            reference = null
            publish(false)
        }
    }

    fun onResume() {
        resumed = true
        updateRegistration()
    }

    fun onPause() {
        resumed = false
        updateRegistration()
    }

    fun close() {
        enabled = false
        resumed = false
        updateRegistration()
    }

    fun onInteraction() {
        if (!enabled) return
        reference = gravity?.copyOf()
        interactionUntil = android.os.SystemClock.elapsedRealtimeNanos() + 250_000_000L
        candidate = null
        publish(false)
    }

    private fun updateRegistration() {
        val shouldRegister = enabled && resumed && sensor != null
        if (shouldRegister && !registered) {
            gravity = null
            previousTimestamp = 0L
            candidate = null
            registered = manager.registerListener(this, sensor, 50_000)
            Log.d("PadelWrist", "sensor=${sensor.name} registered=$registered")
        } else if (!shouldRegister && registered) {
            manager.unregisterListener(this)
            registered = false
            gravity = null
            previousTimestamp = 0L
            candidate = null
        }
        if (enabled && resumed && sensor == null) {
            Log.w("PadelWrist", "No gravity or accelerometer sensor; using system ambient only")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled || !resumed || event.values.size < 3) return
        val raw = floatArrayOf(event.values[0], event.values[1], event.values[2])
        val magnitude = length(raw)
        if (!magnitude.isFinite() || magnitude < 1f) return
        // Reject strong linear acceleration on the fallback sensor.
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER &&
            magnitude !in 7f..13f) {
            candidate = null
            previousTimestamp = event.timestamp
            return
        }
        val previous = gravity
        val dt = if (previousTimestamp == 0L) 0.05 else
            ((event.timestamp - previousTimestamp) / 1_000_000_000.0).coerceIn(0.0, 0.5)
        previousTimestamp = event.timestamp
        val alpha = (1.0 - exp(-dt / 0.10)).toFloat()
        val filtered = if (previous == null) raw else FloatArray(3) { i ->
            previous[i] + alpha * (raw[i] - previous[i])
        }
        gravity = filtered
        val baseline = reference
        if (baseline == null) {
            reference = filtered.copyOf()
            return
        }
        if (event.timestamp < interactionUntil) {
            candidate = null
            return
        }
        val denominator = length(baseline) * length(filtered)
        if (denominator < 1f) return
        val cosine = ((baseline[0] * filtered[0] + baseline[1] * filtered[1] +
            baseline[2] * filtered[2]) / denominator).coerceIn(-1f, 1f)
        val degrees = Math.toDegrees(acos(cosine.toDouble()))
        val desired = when {
            !lowered && degrees >= LOWER_ANGLE_DEGREES -> true
            lowered && degrees <= RAISE_ANGLE_DEGREES -> false
            else -> { candidate = null; return }
        }
        if (candidate != desired) {
            candidate = desired
            candidateSince = event.timestamp
            return
        }
        if (event.timestamp - candidateSince >= STABLE_NANOS) {
            Log.d("PadelWrist", "lowered=$desired angle=${degrees.toInt()}")
            publish(desired)
            candidate = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish(value: Boolean) {
        if (lowered == value) return
        lowered = value
        onLoweredChanged(value)
    }

    private fun length(vector: FloatArray): Float = sqrt(
        vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]
    )

    companion object {
        // Initial heuristic values, not device-validated gesture thresholds.
        private const val LOWER_ANGLE_DEGREES = 40.0
        private const val RAISE_ANGLE_DEGREES = 25.0
        private const val STABLE_NANOS = 150_000_000L
    }
}
