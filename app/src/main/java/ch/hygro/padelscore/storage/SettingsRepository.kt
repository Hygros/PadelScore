package ch.hygro.padelscore.storage

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _swipeGesturesEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SWIPE_GESTURES, false)
    )
    val swipeGesturesEnabled: StateFlow<Boolean> = _swipeGesturesEnabled.asStateFlow()

    private val _healthTrackingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HEALTH_TRACKING, false)
    )
    val healthTrackingEnabled: StateFlow<Boolean> = _healthTrackingEnabled.asStateFlow()

    private val _matchQuietModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MATCH_QUIET_MODE, true)
    )
    val matchQuietModeEnabled: StateFlow<Boolean> = _matchQuietModeEnabled.asStateFlow()

    fun setMatchQuietModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MATCH_QUIET_MODE, enabled) }
        _matchQuietModeEnabled.value = enabled
    }

    fun setSwipeGesturesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SWIPE_GESTURES, enabled) }
        _swipeGesturesEnabled.value = enabled
    }

    private val _healthSyncEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HEALTH_SYNC, false)
    )
    val healthSyncEnabled: StateFlow<Boolean> = _healthSyncEnabled.asStateFlow()

    fun setHealthSyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HEALTH_SYNC, enabled) }
        _healthSyncEnabled.value = enabled
    }

    fun setHealthTrackingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HEALTH_TRACKING, enabled) }
        _healthTrackingEnabled.value = enabled
    }

    companion object {
        private const val KEY_SWIPE_GESTURES = "swipe_gestures_enabled"
        private const val KEY_HEALTH_TRACKING = "health_tracking_enabled"
        private const val KEY_HEALTH_SYNC = "health_sync_enabled"
        private const val KEY_MATCH_QUIET_MODE = "match_quiet_mode_enabled"
    }
}
