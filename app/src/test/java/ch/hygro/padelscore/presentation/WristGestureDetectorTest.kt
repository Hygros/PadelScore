package ch.hygro.padelscore.presentation

import android.view.Surface
import ch.hygro.padelscore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WristGestureDetectorTest {

    private val gravity = 9.81f
    private val stepNanos = 20_000_000L // 20ms (50Hz)
    private val baseRollDeg = 45f // Realistic viewing angle tilted 45 deg toward user

    private fun feedRollPose(
        detector: WristGestureDetector,
        rollDeg: Float,
        durationMs: Int,
        startTimestampNanos: Long,
        wxRadPerSec: Float = 0f,
        gx: Float = 0f
    ): Pair<Long, List<Team>> {
        val rad = Math.toRadians(rollDeg.toDouble())
        val gy = (gravity * sin(rad)).toFloat()
        val gz = (gravity * cos(rad)).toFloat()
        val steps = (durationMs * 1_000_000L / stepNanos).toInt().coerceAtLeast(1)
        var t = startTimestampNanos
        val triggered = mutableListOf<Team>()
        repeat(steps) {
            t += stepNanos
            detector.onGyroSample(wxRadPerSec, 0f, 0f, t)
            val team = detector.onGravitySample(
                x = gx,
                y = gy,
                z = gz,
                timestampNanos = t,
                isAccelerometer = false
            )
            if (team != null) {
                triggered.add(team)
            }
        }
        return t to triggered
    }

    private fun performOutwardTurnAndReturn(
        detector: WristGestureDetector,
        startTimestampNanos: Long,
        outwardTargetRollDeg: Float = -60f, // Screen facing away (gz < 0) during deep wrist turn!
        returnRollDeg: Float = baseRollDeg
    ): Pair<Long, List<Team>> {
        var t = startTimestampNanos
        val triggered = mutableListOf<Team>()

        // Smoothly interpolate from baseRollDeg to outwardTargetRollDeg over 240ms
        val outSteps = 12
        for (i in 1..outSteps) {
            val fraction = i.toFloat() / outSteps
            val currentRoll = baseRollDeg + (outwardTargetRollDeg - baseRollDeg) * fraction
            val (tNext, trig) = feedRollPose(detector, currentRoll, durationMs = 20, startTimestampNanos = t, wxRadPerSec = -1.6f)
            t = tNext
            triggered.addAll(trig)
        }

        // Smoothly interpolate back to returnRollDeg over 240ms
        val backSteps = 12
        for (i in 1..backSteps) {
            val fraction = i.toFloat() / backSteps
            val currentRoll = outwardTargetRollDeg + (returnRollDeg - outwardTargetRollDeg) * fraction
            val (tNext, trig) = feedRollPose(detector, currentRoll, durationMs = 20, startTimestampNanos = t, wxRadPerSec = 1.6f)
            t = tNext
            triggered.addAll(trig)
        }

        return t to triggered
    }

    @Test
    fun singleOutwardTurnIncludingNegativeGz_scoresPointForUs() {
        val detector = WristGestureDetector()
        var t = 1_000_000_000L

        // 1. Hold watch steady in viewing pose for 200ms
        val (tArm, armTriggers) = feedRollPose(detector, baseRollDeg, durationMs = 200, startTimestampNanos = t)
        t = tArm
        assertEquals(emptyList<Team>(), armTriggers)

        // 2. Turn wrist away (all the way to -110 deg where gz < 0!) and back to viewing pose (+52 deg with slight overshoot)
        val (tTurn, turnTriggers) = performOutwardTurnAndReturn(
            detector = detector,
            startTimestampNanos = t,
            outwardTargetRollDeg = -110f,
            returnRollDeg = 52f
        )
        t = tTurn
        assertEquals(emptyList<Team>(), turnTriggers)

        // 3. Keep looking at watch for 600ms -> scores US
        val (_, waitTriggers) = feedRollPose(detector, 52f, durationMs = 600, startTimestampNanos = t)
        assertEquals(listOf(Team.US), waitTriggers)
    }

    @Test
    fun doubleOutwardTurn_scoresPointForOpponentImmediately() {
        val detector = WristGestureDetector()
        var t = 1_000_000_000L

        // 1. Steady viewing pose for 200ms
        t = feedRollPose(detector, baseRollDeg, durationMs = 200, startTimestampNanos = t).first

        // 2. First outward turn & return
        t = performOutwardTurnAndReturn(detector, t, outwardTargetRollDeg = 10f, returnRollDeg = 48f).first

        // 3. Brief pause (120ms)
        t = feedRollPose(detector, 48f, durationMs = 120, startTimestampNanos = t).first

        // 4. Second outward turn & return -> immediately scores OPPONENT
        val (tSecond, secondTriggers) = performOutwardTurnAndReturn(detector, t, outwardTargetRollDeg = 10f, returnRollDeg = 45f)
        t = tSecond
        assertEquals(listOf(Team.OPPONENT), secondTriggers)

        // 5. Waiting further does not score again
        val (_, cooldownTriggers) = feedRollPose(detector, baseRollDeg, durationMs = 700, startTimestampNanos = t)
        assertEquals(emptyList<Team>(), cooldownTriggers)
    }

    @Test
    fun watchWobbleUpTo15Degrees_doesNotScore() {
        val detector = WristGestureDetector()
        var t = 1_000_000_000L

        // 1. Steady viewing pose
        t = feedRollPose(detector, baseRollDeg, durationMs = 200, startTimestampNanos = t).first

        // 2. Wobble of 15 degrees back and forth
        t = feedRollPose(detector, baseRollDeg - 15f, durationMs = 120, startTimestampNanos = t, wxRadPerSec = -1.2f).first
        t = feedRollPose(detector, baseRollDeg + 10f, durationMs = 120, startTimestampNanos = t, wxRadPerSec = 1.2f).first
        val (_, triggers) = feedRollPose(detector, baseRollDeg, durationMs = 700, startTimestampNanos = t, wxRadPerSec = 0f)

        assertEquals(emptyList<Team>(), triggers)
    }

    @Test
    fun loweringArmAfterCheckingScore_doesNotScore() {
        val detector = WristGestureDetector()
        var t = 1_000_000_000L

        // 1. Steady viewing pose
        t = feedRollPose(detector, baseRollDeg, durationMs = 200, startTimestampNanos = t).first

        // 2. Lower arm outward (-50 deg) and drop arm to side (gx = 9.0f)
        t = feedRollPose(detector, baseRollDeg - 50f, durationMs = 160, startTimestampNanos = t, wxRadPerSec = -2.5f).first
        val (_, triggers) = feedRollPose(
            detector,
            rollDeg = -70f,
            durationMs = 800,
            startTimestampNanos = t,
            wxRadPerSec = 0f,
            gx = 9.0f
        )
        assertEquals(emptyList<Team>(), triggers)
    }

    @Test
    fun unsteadyInwardCounterMovementBeforeOutwardFlick_stillScoresPointForUs() {
        val detector = WristGestureDetector()
        var t = 1_000_000_000L

        // 1. Steady viewing pose at baseRollDeg (45 deg) for 200ms -> Armed
        t = feedRollPose(detector, baseRollDeg, durationMs = 200, startTimestampNanos = t).first

        // 2. Brief unsteady inward roll to +60 deg (+15 deg above baseline, high gyro rate so !isSteady)
        t = feedRollPose(detector, baseRollDeg + 15f, durationMs = 40, startTimestampNanos = t, wxRadPerSec = 2.0f).first

        // 3. Outward flick to 15 deg and return to baseRollDeg (45 deg).
        //    If baselineRollDeg had ratcheted to 60 deg, returning to 45 deg would leave deltaDeg = -15 deg
        //    (< -14 deg return tolerance) and fail to complete the flick.
        val (tTurn, turnTriggers) = performOutwardTurnAndReturn(
            detector = detector,
            startTimestampNanos = t,
            outwardTargetRollDeg = 15f,
            returnRollDeg = baseRollDeg
        )
        t = tTurn
        assertEquals(emptyList<Team>(), turnTriggers)

        // 4. Wait in steady viewing pose at baseRollDeg (45 deg) for 600ms -> scores US
        val (_, waitTriggers) = feedRollPose(detector, baseRollDeg, durationMs = 600, startTimestampNanos = t)
        assertEquals(listOf(Team.US), waitTriggers)
    }

    @Test
    fun remapToScreenAxes_rotates180DegreesForOppositeWrist() {
        val (sx, sy, sz) = WristGestureController.remapToScreenAxes(
            x = 1.5f,
            y = -4.0f,
            z = 8.5f,
            rotation = Surface.ROTATION_180
        )
        assertEquals(-1.5f, sx, 0.001f)
        assertEquals(4.0f, sy, 0.001f)
        assertEquals(8.5f, sz, 0.001f)
        assertNull(
            WristGestureDetector().onGravitySample(sx, sy, sz, 1_000_000L)
        )
    }
}
