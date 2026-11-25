package ch.hygro.padelscore.presentation

/**
 * Pure decisions behind the Do Not Disturb and Always-On guidance shown at app start.
 *
 * Keeping the rules free of Android types makes them unit-testable and keeps both
 * controllers behaving consistently on watches with and without the relevant screens.
 */
internal object WatchDisplayGuidance {

    const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
    const val ACTION_ZEN_MODE_PRIORITY_SETTINGS = "android.settings.ZEN_MODE_PRIORITY_SETTINGS"
    const val ACTION_SOUND_SETTINGS = "android.settings.SOUND_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"
    const val ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS =
        "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"
    const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"

    /**
     * Handler names the platform and some OEMs use as generic Settings stubs. Opening
     * those does not lead to the intended screen, so they count as unsupported.
     */
    private val genericSettingsHandlers = setOf(
        "Settings",
        "FakeSettingsActivity"
    )

    /** Ordered fallbacks for reaching Do Not Disturb settings on a watch. */
    fun dndSettingsActions(): List<String> = listOf(
        ACTION_ZEN_MODE_SETTINGS,
        ACTION_ZEN_MODE_PRIORITY_SETTINGS,
        ACTION_SOUND_SETTINGS,
        ACTION_SETTINGS
    )

    /**
     * Returns false when the resolved handler is only a generic Settings stub, which is
     * common on Wear OS where a dedicated per-app policy screen does not exist.
     */
    fun isRealSettingsHandler(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className.substringAfterLast('.') !in genericSettingsHandlers
    }

    /** The app-owned quiet rule must cover the whole active match, including paused screens. */
    fun ruleShouldBeActive(matchActive: Boolean, quietModeEnabled: Boolean): Boolean =
        matchActive && quietModeEnabled

    /**
     * Ask for quiet-mode setup at app start only when nothing is configured yet and no
     * system Do Not Disturb is already active, so the prompt never interrupts play.
     */
    fun shouldPromptForQuietMode(
        quietModeEnabled: Boolean,
        accessGranted: Boolean,
        manualAcknowledged: Boolean,
        systemDndActive: Boolean,
        matchActive: Boolean,
        promptShownThisSession: Boolean
    ): Boolean = quietModeEnabled &&
        !accessGranted &&
        !manualAcknowledged &&
        !systemDndActive &&
        !matchActive &&
        !promptShownThisSession

    /** Prompt only when the app relies on the system Always-On display and it is known to be off. */
    fun shouldPromptForAlwaysOn(
        ambientModeEnabled: Boolean,
        systemAlwaysOn: Boolean?,
        promptSuppressed: Boolean,
        promptShownThisSession: Boolean
    ): Boolean = ambientModeEnabled &&
        systemAlwaysOn == false &&
        !promptSuppressed &&
        !promptShownThisSession

    /** Reads the platform values without loading any Settings class, so tests stay on the JVM. */
    fun alwaysOnStatus(ambientEnabled: Int, dozeAlwaysOn: Int): Boolean? = when {
        ambientEnabled >= 0 -> ambientEnabled != 0
        dozeAlwaysOn >= 0 -> dozeAlwaysOn != 0
        else -> null
    }
}
