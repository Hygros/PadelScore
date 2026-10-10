package ch.hygro.padelscore.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchState

/** Keeps a user-started match discoverable without taking over navigation. */
class OngoingMatchController(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private var active = false
    private var state: MatchState? = null
    private var errorShown = false

    init {
        val channel = NotificationChannel(
            CHANNEL_ID, "Active match", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps your current Padel Score match available."
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        return permissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance !=
                NotificationManager.IMPORTANCE_NONE
    }

    fun update(active: Boolean, state: MatchState) {
        this.active = active
        this.state = state
        if (!active) errorShown = false
        refresh()
    }

    fun refresh() {
        if (!active) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val match = state ?: return
        if (!canPostNotifications()) return
        val returnIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_RESUME_MATCH, true)
        }
        val touchIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = "Games ${match.opponentGames}:${match.ourGames} | Sets ${match.opponentSets}:${match.ourSets}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing_match)
            .setContentTitle("Padel Score match")
            .setContentText(summary)
            .setContentIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        val status = Status.Builder()
            .addTemplate("#score#")
            .addPart("score", Status.TextPart(summary))
            .build()
        try {
            OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_ongoing_match)
                .setTouchIntent(touchIntent)
                .setStatus(status)
                .build()
                .apply(context)
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (exception: RuntimeException) {
            Log.w("OngoingMatch", "Could not post ongoing match", exception)
            if (!errorShown) {
                errorShown = true
                Toast.makeText(context, "The ongoing match notification is unavailable. Check notification settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_RESUME_MATCH = "ch.hygro.padelscore.RESUME_ONGOING_MATCH"
        private const val CHANNEL_ID = "padel_active_match"
        private const val NOTIFICATION_ID = 2101
    }
}
