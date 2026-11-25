package ch.hygro.padelscore.presentation

import android.app.Activity
import androidx.core.content.edit
import android.content.ActivityNotFoundException
import android.app.AlertDialog
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.core.net.toUri
import ch.hygro.padelscore.R

/** Owns only the app's rule; never changes the user's global DND policy. */
class MatchQuietModeController(private val activity: Activity) {
    private val manager = activity.getSystemService(NotificationManager::class.java)
    private val preferences = activity.getSharedPreferences("match_quiet_mode", 0)
    private val settings = activity.getSharedPreferences("settings_prefs", 0)
    private fun enabled() = settings.getBoolean("match_quiet_mode_enabled", true)
    private val conditionId = "condition://${activity.packageName}/active-match".toUri()
    private var matchActive = false
    private var resumed = false
    private var errorShown = false
    private var launchPromptShown = false
    private var promptFinished = false
    private var promptFinishedCallback: (() -> Unit)? = null
    private var externalSettingsOpened = false
    private var dialog: AlertDialog? = null
    private var manualDialogPending = false

    init { setRuleActive(false) } // Clear a stale rule after a previous process death.

    fun setMatchActive(active: Boolean) {
        if (matchActive == active) return
        matchActive = active
        if (!active) {
            errorShown = false
            manualDialogPending = false
            dismissPrompt()
        }
        synchronize()
    }

    /**
     * Runs on every resume. [onPromptFinished] is invoked once when no prompt is in the
     * way, so callers can chain the Always-On guidance without stacking dialogs.
     */
    fun onResume(onPromptFinished: () -> Unit = {}) {
        resumed = true
        promptFinished = false
        promptFinishedCallback = onPromptFinished
        externalSettingsOpened = false
        if (dialog?.isShowing == true) {
            // A prompt is still visible (for example after a system overlay); leave it
            // alone and chain the caller when it is dismissed.
            return
        }
        if (manualDialogPending && matchActive) {
            manualDialogPending = false
            showManualModeDialog()
            return
        }
        if (WatchDisplayGuidance.shouldPromptForQuietMode(
                quietModeEnabled = enabled(),
                accessGranted = manager.isNotificationPolicyAccessGranted,
                manualAcknowledged = preferences.getBoolean("manual_mode_acknowledged", false),
                systemDndActive = isSystemDndActive(),
                matchActive = matchActive,
                promptShownThisSession = launchPromptShown
            )
        ) {
            launchPromptShown = true
            showLaunchPrompt()
            return
        }
        synchronize()
        finishPrompt()
    }

    fun onPause() {
        resumed = false
        // Do not disarm the rule here: a dimmed screen or the watch face must not let
        // notifications through in the middle of a match. The rule ends when the match
        // ends or in close().
    }

    fun close() {
        dismissPrompt()
        setRuleActive(false)
    }

    fun requestSetup() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (manager.isNotificationPolicyAccessGranted) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.quiet_mode_title))
                .setMessage(activity.getString(R.string.dnd_already_granted_message))
                .setPositiveButton(activity.getString(R.string.ok), null)
                .show()
        } else {
            showPermissionExplanation()
        }
    }

    fun refreshSettings() {
        synchronize()
    }

    private fun showLaunchPrompt() {
        val supportsAccessScreen = supportsPolicyAccessScreen()
        if (supportsAccessScreen) {
            showQuietPrompt(
                title = activity.getString(R.string.quiet_mode_title),
                message = activity.getString(R.string.dnd_allow_now_message),
                primaryText = activity.getString(R.string.open_settings),
                chainOnDismiss = true
            ) {
                externalSettingsOpened = true
                openNotificationPolicySettings()
            }
        } else {
            showQuietPrompt(
                title = activity.getString(R.string.manual_quiet_mode_title),
                message = activity.getString(R.string.dnd_manual_launch_message),
                primaryText = activity.getString(R.string.open_dnd_settings),
                chainOnDismiss = true
            ) {
                externalSettingsOpened = true
                openDndSettings()
            }
        }
    }

    private fun showPermissionExplanation() {
        if (!supportsPolicyAccessScreen()) {
            showManualModeDialog()
            return
        }
        showQuietPrompt(
            title = activity.getString(R.string.quiet_mode_title),
            message = activity.getString(R.string.dnd_allow_now_message),
            primaryText = activity.getString(R.string.open_settings),
            chainOnDismiss = false
        ) {
            externalSettingsOpened = true
            openNotificationPolicySettings()
        }
    }

    private fun showQuietPrompt(
        title: String,
        message: String,
        primaryText: String,
        chainOnDismiss: Boolean,
        onPrimary: () -> Unit
    ) {
        if (!resumed || activity.isFinishing || activity.isDestroyed) {
            manualDialogPending = true
            return
        }
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        val prompt = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(primaryText) { _, _ -> onPrimary() }
            .setNegativeButton(activity.getString(R.string.not_now), null)
            .setNeutralButton(activity.getString(R.string.dont_ask_again)) { _, _ ->
                acknowledgeManualMode()
            }
            .create()
        prompt.setOnDismissListener {
            if (dialog === prompt) dialog = null
            if (chainOnDismiss && !externalSettingsOpened) finishPrompt()
        }
        dialog = prompt
        prompt.show()
    }

    private fun synchronize() {
        if (!WatchDisplayGuidance.ruleShouldBeActive(matchActive, enabled())) {
            setRuleActive(false)
            return
        }
        if (manager.isNotificationPolicyAccessGranted) {
            setRuleActive(true)
        }
    }

    private fun finishPrompt() {
        if (promptFinished) return
        promptFinished = true
        val callback = promptFinishedCallback
        promptFinishedCallback = null
        callback?.invoke()
    }

    private fun dismissPrompt() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun isSystemDndActive(): Boolean = try {
        Settings.Global.getInt(activity.contentResolver, "zen_mode", 0) > 0
    } catch (exception: Exception) {
        Log.i("MatchQuietMode", "Could not read the system Do Not Disturb state", exception)
        false
    }

    /**
     * On Wear OS the notification-policy screen often resolves to a generic Settings
     * stub, so only treat it as usable when a real handler answers.
     */
    private fun supportsPolicyAccessScreen(): Boolean {
        val intent = Intent(WatchDisplayGuidance.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        val resolved = try {
            activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (exception: Exception) {
            Log.i("MatchQuietMode", "Could not resolve the notification policy screen", exception)
            null
        }
        return WatchDisplayGuidance.isRealSettingsHandler(resolved?.activityInfo?.name)
    }

    private fun openNotificationPolicySettings() {
        if (tryOpenSettings(Intent(WatchDisplayGuidance.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) return
        openDndSettings()
    }

    private fun openDndSettings() {
        for (action in WatchDisplayGuidance.dndSettingsActions()) {
            if (tryOpenSettings(Intent(action))) return
        }
        showManualSettingsHint()
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
        showQuietPrompt(
            title = activity.getString(R.string.manual_quiet_mode_title),
            message = activity.getString(R.string.manual_quiet_mode_message),
            primaryText = activity.getString(R.string.open_dnd_settings),
            chainOnDismiss = false
        ) {
            externalSettingsOpened = true
            openDndSettings()
        }
    }

    private fun showManualSettingsHint() {
        if (!resumed || activity.isFinishing || activity.isDestroyed) return
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.open_settings_manually))
            .setMessage(activity.getString(R.string.open_settings_manually_message))
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                acknowledgeManualMode()
            }
            .show()
    }

    private fun acknowledgeManualMode() {
        // Remember the choice; do not repeatedly interrupt matches on watches
        // that do not expose the policy-access screen. Access is still checked
        // on every resume, so a later grant enables automatic mode.
        preferences.edit { putBoolean("manual_mode_acknowledged", true) }
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
                    activity.getString(R.string.ongoing_match_title), null,
                    ComponentName(activity, MatchQuietModeSettingsActivity::class.java),
                    conditionId, policyBuilder.build(),
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY, true
                )
                id = manager.addAutomaticZenRule(rule)
                check(!id.isNullOrBlank()) { "The system did not create the match rule" }
                // Persist before activation so the next launch can clear a stale rule.
                preferences.edit(commit = true) { putString("rule_id", id) }
            }
            manager.setAutomaticZenRuleState(
                id,
                Condition(conditionId, activity.getString(R.string.ongoing_match_title),
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
