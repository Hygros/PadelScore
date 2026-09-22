package ch.hygro.padelscore.vibration

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View

class HapticFeedbackManager(
    private val view: View
) {
    private val handler = Handler(Looper.getMainLooper())

    fun point() {
        perform(HapticFeedbackConstants.CONFIRM)
    }

    fun gameWon() {
        perform(HapticFeedbackConstants.LONG_PRESS)
        performDelayed(
            feedbackConstant = HapticFeedbackConstants.CONFIRM,
            delayMillis = 120L
        )
    }

    fun setWon() {
        perform(HapticFeedbackConstants.LONG_PRESS)
        performDelayed(
            feedbackConstant = HapticFeedbackConstants.LONG_PRESS,
            delayMillis = 180L
        )
    }

    fun matchFinished() {
        perform(HapticFeedbackConstants.LONG_PRESS)
        performDelayed(
            feedbackConstant = HapticFeedbackConstants.CONFIRM,
            delayMillis = 170L
        )
        performDelayed(
            feedbackConstant = HapticFeedbackConstants.LONG_PRESS,
            delayMillis = 340L
        )
    }

    fun undo() {
        perform(HapticFeedbackConstants.CONFIRM)
    }

    private fun perform(feedbackConstant: Int) {
        view.performHapticFeedback(feedbackConstant)
    }

    private fun performDelayed(
        feedbackConstant: Int,
        delayMillis: Long
    ) {
        handler.postDelayed(
            { perform(feedbackConstant) },
            delayMillis
        )
    }
}
