package ch.hygro.padelscore.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import ch.hygro.padelscore.model.Team
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recognizes outward wrist-turn gestures (turning the wrist away from the body and back
 * to the viewing pose) while looking at the watch:
 * - 1x turn wrist away & back -> [Team.US]
 * - 2x turn wrist away & back -> [Team.OPPONENT]
 *
 * Uses [Sensor.TYPE_GRAVITY] (with [Sensor.TYPE_ACCELEROMETER] fallback) and optional
 * [Sensor.TYPE_GYROSCOPE]. Requires an outward forearm roll of at least 22 degrees followed
 * by a return to the viewing pose within 950ms, rejecting small wobbles (< 15 degrees) as
 * well as lowering the arm without returning.
 */
class WristGestureController(
    private val context: Context,
    private val onGesturePoint: (Team) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val detector = WristGestureDetector()
    private var enabled = false
    private var resumed = false
    private var registered = false

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        updateRegistration()
        if (!value) {
            detector.reset()
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

    private fun updateRegistration() {
        val shouldRegister = enabled && resumed && gravitySensor != null && sensorManager != null
        if (shouldRegister && !registered) {
            detector.reset()
            registered = sensorManager.registerListener(
                this,
                gravitySensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (registered && gyroSensor != null) {
                sensorManager.registerListener(
                    this,
                    gyroSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
            Log.d(
                "PadelGesture",
                "gravity=${gravitySensor.name} gyro=${gyroSensor?.name ?: "none"} registered=$registered"
            )
        } else if (!shouldRegister && registered) {
            sensorManager?.unregisterListener(this)
            registered = false
            detector.reset()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled || !resumed || event.values.size < 3) return
        val rotation = currentDisplayRotation()
        val (sx, sy, sz) = remapToScreenAxes(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            rotation = rotation
        )
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                detector.onGyroSample(
                    wx = sx,
                    wy = sy,
                    wz = sz,
                    timestampNanos = event.timestamp
                )
            }

            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val isAccelerometer = event.sensor.type == Sensor.TYPE_ACCELEROMETER
                val triggeredTeam = detector.onGravitySample(
                    x = sx,
                    y = sy,
                    z = sz,
                    timestampNanos = event.timestamp,
                    isAccelerometer = isAccelerometer
                )
                if (triggeredTeam != null) {
                    Log.d("PadelGesture", "gesture scored team=$triggeredTeam")
                    onGesturePoint(triggeredTeam)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return try {
            context.display.rotation
        } catch (_: UnsupportedOperationException) {
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    companion object {
        internal fun remapToScreenAxes(
            x: Float,
            y: Float,
            z: Float,
            rotation: Int
        ): Triple<Float, Float, Float> = when (rotation) {
            Surface.ROTATION_90 -> Triple(-y, x, z)
            Surface.ROTATION_180 -> Triple(-x, -y, z)
            Surface.ROTATION_270 -> Triple(y, -x, z)
            else -> Triple(x, y, z)
        }
    }
}

internal class WristGestureDetector {
    private sealed interface State {
        data object Idle : State
        data class Arming(val sinceNanos: Long, val initialRollDeg: Float) : State
        data class Armed(var baselineRollDeg: Float) : State
        data class InFlick(
            val flickNumber: Int,
            val baselineRollDeg: Float,
            val startedAtNanos: Long,
            var minDeltaRollDeg: Float
        ) : State
        data class WaitingForSecondFlick(
            val firstFlickCompletedAtNanos: Long,
            var peakReturnRollDeg: Float,
            var outOfViewSinceNanos: Long
        ) : State
        data class Cooldown(val untilNanos: Long) : State
    }

    private var state: State = State.Idle
    private var filteredGravity: FloatArray? = null
    private var previousTimestampNanos: Long = 0L
    private var previousRollDeg: Float = Float.NaN

    private var latestWx: Float = 0f
    private var latestWy: Float = 0f
    private var latestWz: Float = 0f
    private var latestGyroTimestampNanos: Long = 0L

    fun reset() {
        state = State.Idle
        filteredGravity = null
        previousTimestampNanos = 0L
        previousRollDeg = Float.NaN
        latestWx = 0f
        latestWy = 0f
        latestWz = 0f
        latestGyroTimestampNanos = 0L
    }

    fun onGyroSample(wx: Float, wy: Float, wz: Float, timestampNanos: Long) {
        if (!wx.isFinite() || !wy.isFinite() || !wz.isFinite()) return
        latestWx = wx
        latestWy = wy
        latestWz = wz
        latestGyroTimestampNanos = timestampNanos
    }

    fun onGravitySample(
        x: Float,
        y: Float,
        z: Float,
        timestampNanos: Long,
        isAccelerometer: Boolean = false
    ): Team? {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite() || magnitude < 1.0f) return null

        if (isAccelerometer && magnitude !in MIN_ACCEL_MAGNITUDE..MAX_ACCEL_MAGNITUDE) {
            if (state !is State.Cooldown) {
                state = State.Idle
            }
            previousTimestampNanos = timestampNanos
            return null
        }

        val dtSeconds = if (previousTimestampNanos == 0L) {
            0.02
        } else {
            ((timestampNanos - previousTimestampNanos) / 1_000_000_000.0).coerceIn(0.001, 0.5)
        }
        previousTimestampNanos = timestampNanos

        val gx: Float
        val gy: Float
        val gz: Float
        if (isAccelerometer) {
            val prev = filteredGravity
            val alpha = (1.0 - exp(-dtSeconds / ACCEL_FILTER_TAU_SECONDS)).toFloat()
            if (prev == null) {
                gx = x
                gy = y
                gz = z
                filteredGravity = floatArrayOf(gx, gy, gz)
            } else {
                gx = prev[0] + alpha * (x - prev[0])
                gy = prev[1] + alpha * (y - prev[1])
                gz = prev[2] + alpha * (z - prev[2])
                prev[0] = gx
                prev[1] = gy
                prev[2] = gz
            }
        } else {
            gx = x
            gy = y
            gz = z
        }

        val rollDeg = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
        val derivedRollSpeedDegPerSec = if (previousRollDeg.isNaN()) {
            0f
        } else {
            abs(normalizeAngleDeg(rollDeg - previousRollDeg)) / dtSeconds.toFloat()
        }
        previousRollDeg = rollDeg

        val gyroFresh = latestGyroTimestampNanos > 0L &&
            abs(timestampNanos - latestGyroTimestampNanos) <= GYRO_FRESHNESS_NANOS
        val totalGyroMag = if (gyroFresh) {
            sqrt(latestWx * latestWx + latestWy * latestWy + latestWz * latestWz)
        } else {
            Math.toRadians(derivedRollSpeedDegPerSec.toDouble()).toFloat()
        }

        val isViewing = isViewingPose(gx, gy, gz, rollDeg)
        val isSteady = totalGyroMag <= STEADY_GYRO_MAX_RAD_PER_SEC &&
            derivedRollSpeedDegPerSec <= STEADY_ROLL_SPEED_MAX_DEG_PER_SEC

        when (val current = state) {
            is State.Cooldown -> {
                if (timestampNanos >= current.untilNanos) {
                    state = State.Idle
                }
                return null
            }

            is State.Idle -> {
                if (isViewing && isSteady) {
                    state = State.Arming(sinceNanos = timestampNanos, initialRollDeg = rollDeg)
                }
                return null
            }

            is State.Arming -> {
                if (!isViewing ||
                    !isSteady ||
                    abs(normalizeAngleDeg(rollDeg - current.initialRollDeg)) > MAX_ARMING_DRIFT_DEGREES
                ) {
                    state = State.Idle
                    return null
                }
                if (timestampNanos - current.sinceNanos >= STABLE_ARMING_NANOS) {
                    logDebug("armed at roll=${rollDeg.toInt()}")
                    state = State.Armed(baselineRollDeg = rollDeg)
                }
                return null
            }

            is State.Armed -> {
                if (abs(gx) > MAX_ACTIVE_LATERAL_GRAVITY || !isViewing && normalizeAngleDeg(rollDeg - current.baselineRollDeg) > 15f) {
                    state = State.Idle
                    return null
                }

                val deltaDeg = normalizeAngleDeg(rollDeg - current.baselineRollDeg)
                // Adapt baseline if user slowly adjusts viewing angle or tilts slightly inward
                if (isViewing && isSteady && deltaDeg in -6.0f..15.0f) {
                    current.baselineRollDeg = normalizeAngleDeg(current.baselineRollDeg + 0.15f * deltaDeg)
                    return null
                }

                if (deltaDeg <= -FLICK_START_ANGLE_DEGREES) {
                    logDebug("flick 1 started baseline=${current.baselineRollDeg.toInt()} roll=${rollDeg.toInt()}")
                    state = State.InFlick(
                        flickNumber = 1,
                        baselineRollDeg = current.baselineRollDeg,
                        startedAtNanos = timestampNanos,
                        minDeltaRollDeg = deltaDeg
                    )
                }
                return null
            }

            is State.InFlick -> {
                val elapsed = timestampNanos - current.startedAtNanos
                if (elapsed > MAX_FLICK_DURATION_NANOS || abs(gx) > MAX_ACTIVE_LATERAL_GRAVITY) {
                    logDebug("flick ${current.flickNumber} aborted elapsed=${elapsed / 1_000_000}ms gx=$gx minDelta=${current.minDeltaRollDeg.toInt()}")
                    state = State.Idle
                    return null
                }

                val deltaDeg = normalizeAngleDeg(rollDeg - current.baselineRollDeg)
                current.minDeltaRollDeg = min(current.minDeltaRollDeg, deltaDeg)

                val reachedOutwardPeak = current.minDeltaRollDeg <= -FLICK_OUTWARD_ANGLE_DEGREES
                val returnedToViewingPose = isViewing && deltaDeg >= -FLICK_RETURN_TOLERANCE_DEGREES

                if (reachedOutwardPeak && returnedToViewingPose) {
                    logDebug(
                        "flick ${current.flickNumber} completed minDelta=${current.minDeltaRollDeg.toInt()} returnRoll=${rollDeg.toInt()}"
                    )
                    if (current.flickNumber == 1) {
                        state = State.WaitingForSecondFlick(
                            firstFlickCompletedAtNanos = timestampNanos,
                            peakReturnRollDeg = rollDeg,
                            outOfViewSinceNanos = 0L
                        )
                    } else {
                        state = State.Cooldown(untilNanos = timestampNanos + COOLDOWN_NANOS)
                        return Team.OPPONENT
                    }
                }
                return null
            }

            is State.WaitingForSecondFlick -> {
                if (isViewing && normalizeAngleDeg(rollDeg - current.peakReturnRollDeg) > 0f) {
                    current.peakReturnRollDeg = rollDeg
                }

                val deltaFromReturn = normalizeAngleDeg(rollDeg - current.peakReturnRollDeg)
                if (deltaFromReturn <= -FLICK_START_ANGLE_DEGREES) {
                    logDebug("flick 2 started baseline=${current.peakReturnRollDeg.toInt()} roll=${rollDeg.toInt()}")
                    state = State.InFlick(
                        flickNumber = 2,
                        baselineRollDeg = current.peakReturnRollDeg,
                        startedAtNanos = timestampNanos,
                        minDeltaRollDeg = deltaFromReturn
                    )
                    return null
                }

                if (!isViewing || abs(gx) > MAX_ACTIVE_LATERAL_GRAVITY) {
                    if (current.outOfViewSinceNanos == 0L) {
                        current.outOfViewSinceNanos = timestampNanos
                    } else if (timestampNanos - current.outOfViewSinceNanos >= OUT_OF_VIEW_ABORT_NANOS) {
                        state = State.Idle
                        return null
                    }
                } else {
                    current.outOfViewSinceNanos = 0L
                }

                if (timestampNanos - current.firstFlickCompletedAtNanos >= DOUBLE_FLICK_WINDOW_NANOS) {
                    if (isViewing) {
                        state = State.Cooldown(untilNanos = timestampNanos + COOLDOWN_NANOS)
                        return Team.US
                    } else {
                        state = State.Idle
                    }
                }
                return null
            }
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d("PadelGesture", message) }
    }

    private fun isViewingPose(gx: Float, gy: Float, gz: Float, rollDeg: Float): Boolean {
        val py = max(0f, gy)
        val pz = max(0f, gz)
        val viewMag = sqrt(py * py + pz * pz)
        return gz >= MIN_VIEWING_Z_GRAVITY &&
            gy >= MIN_VIEWING_Y_GRAVITY &&
            viewMag >= MIN_VIEWING_MAGNITUDE &&
            abs(gx) <= MAX_VIEWING_LATERAL_GRAVITY &&
            rollDeg in MIN_VIEWING_ROLL_DEGREES..MAX_VIEWING_ROLL_DEGREES
    }

    private fun normalizeAngleDeg(angleDeg: Float): Float {
        var a = angleDeg % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    companion object {
        private const val ACCEL_FILTER_TAU_SECONDS = 0.06
        private const val MIN_ACCEL_MAGNITUDE = 6.5f
        private const val MAX_ACCEL_MAGNITUDE = 14.5f
        private const val GYRO_FRESHNESS_NANOS = 100_000_000L

        // Viewing pose: forearm horizontal, watch tilted between -25 deg and +80 deg toward face.
        private const val MIN_VIEWING_Z_GRAVITY = 0.8f
        private const val MIN_VIEWING_Y_GRAVITY = -3.0f
        private const val MIN_VIEWING_MAGNITUDE = 5.5f
        private const val MAX_VIEWING_LATERAL_GRAVITY = 6.2f
        private const val MAX_ACTIVE_LATERAL_GRAVITY = 7.8f
        private const val MIN_VIEWING_ROLL_DEGREES = -25.0f
        private const val MAX_VIEWING_ROLL_DEGREES = 80.0f

        // Steadiness criteria before arming (prevents arming during rallies or wobbles).
        private const val STEADY_GYRO_MAX_RAD_PER_SEC = 0.90f
        private const val STEADY_ROLL_SPEED_MAX_DEG_PER_SEC = 55.0f
        private const val MAX_ARMING_DRIFT_DEGREES = 10.0f

        // Outward wrist-turn & return thresholds (degrees).
        private const val FLICK_START_ANGLE_DEGREES = 10.0f
        private const val FLICK_OUTWARD_ANGLE_DEGREES = 22.0f
        private const val FLICK_RETURN_TOLERANCE_DEGREES = 14.0f

        private const val STABLE_ARMING_NANOS = 150_000_000L
        private const val MAX_FLICK_DURATION_NANOS = 950_000_000L
        private const val DOUBLE_FLICK_WINDOW_NANOS = 550_000_000L
        private const val OUT_OF_VIEW_ABORT_NANOS = 150_000_000L
        private const val COOLDOWN_NANOS = 1_000_000_000L
    }
}
