package ch.hygro.padelscore.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDisplayGuidanceTest {

    @Test
    fun alwaysOnStatusPrefersAmbientEnabledSetting() {
        assertEquals(true, WatchDisplayGuidance.alwaysOnStatus(1, 0))
        assertEquals(false, WatchDisplayGuidance.alwaysOnStatus(0, 1))
    }

    @Test
    fun alwaysOnStatusFallsBackToDozeAlwaysOn() {
        assertEquals(true, WatchDisplayGuidance.alwaysOnStatus(-1, 1))
        assertEquals(false, WatchDisplayGuidance.alwaysOnStatus(-1, 0))
    }

    @Test
    fun alwaysOnStatusIsUnknownWhenNeitherSettingExists() {
        assertNull(WatchDisplayGuidance.alwaysOnStatus(-1, -1))
    }

    @Test
    fun quietModeIsPromptedOnlyOnceAndOnlyWhenNeeded() {
        fun prompt(
            enabled: Boolean = true,
            granted: Boolean = false,
            acknowledged: Boolean = false,
            systemDnd: Boolean = false,
            matchActive: Boolean = false,
            shown: Boolean = false
        ) = WatchDisplayGuidance.shouldPromptForQuietMode(
            quietModeEnabled = enabled,
            accessGranted = granted,
            manualAcknowledged = acknowledged,
            systemDndActive = systemDnd,
            matchActive = matchActive,
            promptShownThisSession = shown
        )

        assertTrue(prompt())
        assertFalse("not while quiet mode is disabled", prompt(enabled = false))
        assertFalse("not once access is granted", prompt(granted = true))
        assertFalse("not after the user opted out", prompt(acknowledged = true))
        assertFalse("not when system DND is already active", prompt(systemDnd = true))
        assertFalse("never interrupt an active match", prompt(matchActive = true))
        assertFalse("only once per app start", prompt(shown = true))
    }

    @Test
    fun quietRuleCoversPausedActiveMatches() {
        assertTrue(WatchDisplayGuidance.ruleShouldBeActive(matchActive = true, quietModeEnabled = true))
        assertFalse(WatchDisplayGuidance.ruleShouldBeActive(matchActive = true, quietModeEnabled = false))
        assertFalse(WatchDisplayGuidance.ruleShouldBeActive(matchActive = false, quietModeEnabled = true))
    }

    @Test
    fun alwaysOnPromptRequiresAnExplicitlyDisabledDisplay() {
        fun prompt(
            ambientMode: Boolean = true,
            systemAlwaysOn: Boolean? = false,
            suppressed: Boolean = false,
            shown: Boolean = false
        ) = WatchDisplayGuidance.shouldPromptForAlwaysOn(
            ambientModeEnabled = ambientMode,
            systemAlwaysOn = systemAlwaysOn,
            promptSuppressed = suppressed,
            promptShownThisSession = shown
        )

        assertTrue(prompt())
        assertFalse("ambient mode off keeps the screen awake instead", prompt(ambientMode = false))
        assertFalse("nothing to do when Always-On is enabled", prompt(systemAlwaysOn = true))
        assertFalse("unknown state should not prompt", prompt(systemAlwaysOn = null))
        assertFalse("respect the opt-out", prompt(suppressed = true))
        assertFalse("only once per app start", prompt(shown = true))
    }

    @Test
    fun dndSettingsFallbackCascadeHasMostSpecificActionFirst() {
        val actions = WatchDisplayGuidance.dndSettingsActions()

        assertEquals(WatchDisplayGuidance.ACTION_ZEN_MODE_SETTINGS, actions.first())
        assertEquals(WatchDisplayGuidance.ACTION_SETTINGS, actions.last())
        assertEquals(actions.size, actions.distinct().size)
    }

    @Test
    fun genericSettingsStubsAreNotTreatedAsRealHandlers() {
        assertFalse(WatchDisplayGuidance.isRealSettingsHandler(null))
        assertFalse(WatchDisplayGuidance.isRealSettingsHandler(""))
        assertFalse(WatchDisplayGuidance.isRealSettingsHandler("com.android.settings.Settings"))
        assertFalse(WatchDisplayGuidance.isRealSettingsHandler("com.android.settings.FakeSettingsActivity"))
        assertTrue(
            WatchDisplayGuidance.isRealSettingsHandler(
                "com.android.settings.notification.NotificationAccessSettings"
            )
        )
    }
}
