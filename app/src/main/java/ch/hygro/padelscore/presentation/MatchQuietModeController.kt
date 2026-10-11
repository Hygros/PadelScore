package ch.hygro.padelscore.presentation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.app.AlertDialog
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.core.net.toUri
import androidx.core.content.edit

/** Owns only the app's rule; never changes the user's global DND policy. */
class MatchQuietModeController(private val activity: Activity) {
    private val manager = activity.getSystemService(NotificationManager::class.java)
    private val preferences = activity.getSharedPreferences("match_quiet_mode", 0)
    private val conditionId = "condition://${activity.packageName}/active-match".toUri()
    private var matchActive = false
    private var resumed = false
    private var prompted = false
    private var errorShown = false
    private var dialog: AlertDialog? = null
    private var manualDialogPending = false

    init { setRuleActive(false) } // Clear a stale rule after a previous process death.

    fun setMatchActive(active: Boolean) {
        if (matchActive == active) return
        matchActive = active
        if (!active) {
            prompted = false
            errorShown = false
            dialog?.dismiss()
            dialog = null
        }
        synchronize()
    }

    fun onResume() {
        resumed = true
        if (manualDialogPending && matchActive) {
            manualDialogPending = false
            showManualModeDialog()
        } else {
            synchronize()
        }
    }

    fun onPause() {
        resumed = false
        setRuleActive(false)
    }

    fun close() {
        dialog?.dismiss()
        setRuleActive(false)
    }

    private fun synchronize() {
        if (!matchActive || !resumed) {
            setRuleActive(false)
            return
        }
        if (manager.isNotificationPolicyAccessGranted) {
            setRuleActive(true)
        } else if (!prompted && !activity.isFinishing &&
            !preferences.getBoolean("manual_mode_acknowledged", false)
        ) {
            prompted = true
            dialog = AlertDialog.Builder(activity)
                .setTitle("Match quiet mode")
                .setMessage("Allow Do Not Disturb access to silence notifications and hide pop-ups while a match is open. Calls and messages will be silenced; alarms remain allowed. You can continue without this permission.")
                .setPositiveButton("Open settings") { _, _ -> openSettings() }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    private fun openSettings() {
        // Start directly rather than relying on resolveActivity: package visibility
        // can hide handlers from queries even when startActivity would succeed.
        if (tryOpenSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) return
        showManualModeDialog()
    }

    private fun tryOpenSettings(intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            Log.i("MatchQuietMode", "Settings action unavailable: ${intent.action}", exception)
            false
        } catch (exception: SecurityException) {
            Log.w("MatchQuietMode", "Settings action not allowed: ${intent.action}", exception)
            false
        }
    }

    private fun showManualModeDialog() {
        if (!resumed || activity.isFinishing || activity.isDestroyed) {
            manualDialogPending = true
            return
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity)
            .setTitle("Manual quiet mode")
            .setMessage("Automatic quiet mode is unavailable or has not been allowed on this watch. Turn on Do Not Disturb in your watch settings or quick panel before playing, then turn it off afterward. Opening settings does not grant Padel Score automatic control. Calls and notifications follow your watch's Do Not Disturb exceptions.")
            .setPositiveButton("Watch settings") { _, _ -> openManualSettings() }
            .setNegativeButton("Continue") { _, _ -> acknowledgeManualMode() }
            .show()
    }

    private fun acknowledgeManualMode() {
        // Remember the choice; do not repeatedly interrupt matches on watches
        // that do not expose the policy-access screen. Access is still checked
        // on every resume, so a later grant enables automatic mode.
        preferences.edit { putBoolean("manual_mode_acknowledged", true)}
    }

    private fun openManualSettings() {
        val opened = tryOpenSettings(Intent(Settings.ACTION_SOUND_SETTINGS)) ||
            tryOpenSettings(Intent(Settings.ACTION_SETTINGS))
        if (opened) {
            acknowledgeManualMode()
            return
        }
        if (!resumed || activity.isFinishing || activity.isDestroyed) return
        dialog = AlertDialog.Builder(activity)
            .setTitle("Open settings manually")
            .setMessage("This watch cannot open settings from Padel Score. Open the watch settings or quick panel yourself and enable Do Not Disturb before playing. Turn it off afterward if you no longer need it.")
            .setPositiveButton("OK") { _, _ -> acknowledgeManualMode() }
            .show()
    }

    private fun setRuleActive(active: Boolean) {
        if (!manager.isNotificationPolicyAccessGranted) return
        try {
            var id = preferences.getString("rule_id", null)
            if (id != null && manager.getAutomaticZenRule(id) == null) {
                preferences.edit {remove("rule_id")}
                id = null
            }
            if (id == null) {
                if (!active) return
                val policyBuilder = ZenPolicy.Builder()
                    .disallowAllSounds()
                    .allowAlarms(true)
                    .allowMedia(true)
                    .hideAllVisualEffects()
                if (Build.VERSION.SDK_INT >= 35) policyBuilder.allowPriorityChannels(false)
                val rule = AutomaticZenRule(
                    "Padel Score match", null,
                    ComponentName(activity, MatchQuietModeSettingsActivity::class.java),
                    conditionId, policyBuilder.build(),
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY, true
                )
                id = manager.addAutomaticZenRule(rule)
                check(!id.isNullOrBlank()) { "The system did not create the match rule" }
                // Persist before activation so the next launch can clear a stale rule.
                check(preferences.edit().putString("rule_id", id).commit())
            }
            manager.setAutomaticZenRuleState(
                id,
                Condition(conditionId, "Padel Score match",
                    if (active) Condition.STATE_TRUE else Condition.STATE_FALSE)
            )
        } catch (exception: RuntimeException) {
            Log.w("MatchQuietMode", "Could not change the match rule", exception)
            if (active && !errorShown) {
                errorShown = true
                showManualModeDialog()
            }
        }
    }
}
