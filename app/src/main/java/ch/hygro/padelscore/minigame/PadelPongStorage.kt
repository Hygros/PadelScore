package ch.hygro.padelscore.minigame

import android.content.Context
import androidx.core.content.edit

class PadelPongStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("padel_pong", Context.MODE_PRIVATE)
    fun highScore(): Int = prefs.getInt("high_score", 0)
    fun record(score: Int): Int {
        val best = maxOf(highScore(), score)
        if (best > highScore()) prefs.edit { putInt("high_score", best) }
        return best
    }
}