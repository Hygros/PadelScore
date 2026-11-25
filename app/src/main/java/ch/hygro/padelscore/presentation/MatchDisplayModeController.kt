package ch.hygro.padelscore.presentation

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.provider.Settings
import android.util.Log
import ch.hygro.padelscore.R
import ch.hygro.padelscore.storage.SettingsRepository

/**
 * Guidance for the watch's system Always-On display.
 *
 * Play Store apps cannot change the global watch display setting. This controller only
 * reads the current state, points the user to the right settings screen, and offers the
 * in-app alternative of keeping the match screen awake.
 */
class MatchDisplayModeController(
    private val activity: Activity,
    private val settings: SettingsRepository
) {
    private var promptShown = false
    private var dialog: AlertDialog? = null

    /** `true` = Always-On enabled, `false` = disabled, `null` = not readable. */
    fun readSystemAlwaysOn(): Boolean? = readSystemAlwaysOn(activity.contentResolver)

    fun onResume() {
        if (!WatchDisplayGuidance.shouldPromptForAlwaysOn(
                ambientModeEnabled = settings.ambientModeEnabled.value,
                systemAlwaysOn = readSystemAlwaysOn(),
                promptSuppressed = settings.aodPromptSuppressed.value,
                promptShownThisSession = promptShown
            )
        ) {
            return
        }
        promptShown = true
        showPrompt()
    }

    fun openDisplaySettings(): Boolean {
        val opened = tryOpenSettings(Intent(WatchDisplayGuidance.ACTION_DISPLAY_SETTINGS)) ||
            tryOpenSettings(Intent(WatchDisplayGuidance.ACTION_SETTINGS))
        if (!opened) showManualHint()
        return opened
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
    }

    private fun showPrompt() {
        if (activity.isFinishing || activity.isDestroyed) return
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.aod_prompt_title))
            .setMessage(activity.getString(R.string.aod_prompt_message))
            .setPositiveButton(activity.getString(R.string.display_settings)) { _, _ ->
                openDisplaySettings()
            }
            .setNegativeButton(activity.getString(R.string.keep_screen_on_in_match)) { _, _ ->
                // Keep the match screen on instead of changing the watch setting.
                settings.setAmbientModeEnabled(false)
            }
            .setNeutralButton(activity.getString(R.string.dont_ask_again)) { _, _ ->
                settings.setAodPromptSuppressed(true)
            }
            .show()
    }

    private fun showManualHint() {
        if (activity.isFinishing || activity.isDestroyed) return
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.aod_settings_blocked_title))
            .setMessage(activity.getString(R.string.aod_settings_blocked_message))
            .setPositiveButton(activity.getString(R.string.ok), null)
            .show()
    }

    private fun tryOpenSettings(intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            Log.i("MatchDisplayMode", "Settings action unavailable: ${intent.action}", exception)
            false
        } catch (exception: SecurityException) {
            Log.w("MatchDisplayMode", "Settings action not allowed: ${intent.action}", exception)
            false
        }
    }

    companion object {
        fun readSystemAlwaysOn(resolver: ContentResolver): Boolean? {
            val ambientEnabled = readSetting {
                Settings.Global.getInt(resolver, "ambient_enabled", -1)
            }
            val dozeAlwaysOn = readSetting {
                Settings.Secure.getInt(resolver, "doze_always_on", -1)
            }
            return WatchDisplayGuidance.alwaysOnStatus(ambientEnabled, dozeAlwaysOn)
        }

        private fun readSetting(read: () -> Int): Int = try {
            read()
        } catch (exception: Exception) {
            Log.i("MatchDisplayMode", "Could not read the Always-On state", exception)
            -1
        }
    }
}
