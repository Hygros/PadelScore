package ch.hygro.padelscore.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import ch.hygro.padelscore.billing.SupporterBillingManager
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

class MainActivity : ComponentActivity() {

    private val syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private lateinit var supporterBillingManager: SupporterBillingManager
    private val supporterBillingState = mutableStateOf(SupporterBillingState())

    private lateinit var matchQuietMode: MatchQuietModeController
    private lateinit var ongoingMatch: OngoingMatchController
    private lateinit var wristDisplayController: WristDisplayController
    val wristLowered = mutableStateOf(false)

    fun setWristDetectionEnabled(enabled: Boolean) {
        wristDisplayController.setEnabled(enabled)
    }

    private val resumeFromOngoing = mutableStateOf(false)
    private val permissionPreferences by lazy {
        getSharedPreferences("permission_onboarding", MODE_PRIVATE)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (::ongoingMatch.isInitialized) ongoingMatch.refresh()
        if (!granted) android.widget.Toast.makeText(
            this,
            "Allow notifications in app settings to keep the match available on your watch.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    fun updateOngoingMatch(active: Boolean, state: MatchState) {
        ongoingMatch.update(active, state)
    }

    fun requestNotificationsOnFirstStartScreen() {
        if (Build.VERSION.SDK_INT < 33) return
        if (permissionPreferences.getBoolean("notifications_prompted", false)) return
        // Persist the first visit so declining does not interrupt later matches.
        permissionPreferences.edit {
            putBoolean("notifications_prompted", true)
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            ::wristDisplayController.isInitialized
        ) {
            wristDisplayController.onInteraction()
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d("PadelInput", "touch=${event.actionMasked} x=${event.x.toInt()} y=${event.y.toInt()}")
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OngoingMatchController.EXTRA_RESUME_MATCH, false)) {
            resumeFromOngoing.value = true
        }
    }

    fun configureMatchQuietMode() {
        if (::matchQuietMode.isInitialized) matchQuietMode.requestSetup()
    }

    fun refreshMatchQuietModeSettings() {
        if (::matchQuietMode.isInitialized) matchQuietMode.refreshSettings()
    }

    fun setMatchQuietMode(active: Boolean) {
        if (::matchQuietMode.isInitialized) matchQuietMode.setMatchActive(active)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        matchQuietMode = MatchQuietModeController(this)
        ongoingMatch = OngoingMatchController(this)
        wristDisplayController = WristDisplayController(this) { lowered ->
            wristLowered.value = lowered
        }
        resumeFromOngoing.value = intent.getBooleanExtra(
            OngoingMatchController.EXTRA_RESUME_MATCH, false
        )

        supporterBillingManager = SupporterBillingManager(
            context = applicationContext,
            onStateChanged = { updatedState ->
                runOnUiThread {
                    supporterBillingState.value = updatedState
                }
            }
        )

        setContent {
            PadelScoreTheme {
                PadelScoreApp(
                    supporterBillingState = supporterBillingState.value,
                    resumeFromOngoing = resumeFromOngoing.value,
                    onResumeFromOngoingHandled = { resumeFromOngoing.value = false },
                    onRefreshSupporterPurchases = {
                        supporterBillingManager.refresh()
                    },
                    onLaunchSupporterPurchase = { activity ->
                        supporterBillingManager.launchPurchase(activity)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        supporterBillingManager.start()
    }

    override fun onResume() {
        super.onResume()
        supporterBillingManager.refresh()
        matchQuietMode.onResume()
        ongoingMatch.refresh()
        wristDisplayController.onResume()
        syncExecutor.execute {
            try { ch.hygro.padelscore.health.WorkoutDataSender(applicationContext).publishAll() }
            catch (e: Exception) { Log.w("PadelScoreSync", "Pending sync deferred", e) }
        }
    }

    override fun onPause() {
        wristDisplayController.onPause()
        matchQuietMode.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        wristDisplayController.close()
        matchQuietMode.close()
        supporterBillingManager.close()
        syncExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun PadelScoreApp(
    supporterBillingState: SupporterBillingState,
    resumeFromOngoing: Boolean,
    onResumeFromOngoingHandled: () -> Unit,
    onRefreshSupporterPurchases: () -> Unit,
    onLaunchSupporterPurchase: (Activity) -> Unit
) {
    val ambientManager = rememberAmbientModeManager()
    CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
        PadelScoreContent(
            supporterBillingState = supporterBillingState,
            resumeFromOngoing = resumeFromOngoing,
            onResumeFromOngoingHandled = onResumeFromOngoingHandled,
            onRefreshSupporterPurchases = onRefreshSupporterPurchases,
            onLaunchSupporterPurchase = onLaunchSupporterPurchase
        )
    }
}
