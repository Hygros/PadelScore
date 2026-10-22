package ch.hygro.padelscore.sync

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val syncPermissions = WorkoutHealthImporter.permissions
    private lateinit var status: TextView
    private lateinit var syncButton: Button
    private var syncJob: Job? = null
    private val receiptPreferences by lazy {
        getSharedPreferences("health_sync_receipts", MODE_PRIVATE)
    }
    private val receiptListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "last_status") runOnUiThread {
            if (::status.isInitialized) status.text = prefs.getString("last_status", "")
        }
    }

    override fun onStart() {
        super.onStart()
        receiptPreferences.registerOnSharedPreferenceChangeListener(receiptListener)
    }

    override fun onStop() {
        receiptPreferences.unregisterOnSharedPreferenceChangeListener(receiptListener)
        super.onStop()
    }

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(syncPermissions)) {
            status.text = "Permission granted. Ready to sync recorded workouts."
            requestSync()
        } else {
            status.text = "Required permissions were not granted. Check Health Connect permissions."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 60, 36, 36)
        }
        layout.addView(TextView(this).apply {
            text = "PadelScore Sync"
            textSize = 20f
        })
        status = TextView(this).apply {
            text = "Checking Health Connect..."
            textSize = 15f
            setPadding(0, 24, 0, 24)
        }
        layout.addView(status)
        val connectButton = Button(this).apply {
            text = "Connect Health Connect"
            setOnClickListener { requestPermission() }
        }
        layout.addView(connectButton)
        syncButton = Button(this).apply {
            text = "Sync watch workouts now"
            setOnClickListener { requestSync() }
        }
        layout.addView(syncButton)
        layout.addView(TextView(this).apply {
            text = "This companion app is optional. To transfer recorded workouts, " +
                "enable Health Sync on your watch and grant Health Connect permissions here. " +
                "Your watch remains usable without this app. " +
                "Writing to Health Connect does not confirm import into Samsung Health."
        })
        val syncToggle = android.widget.Switch(this).apply {
            text = "Allow workout imports on this phone"
            isChecked = receiptPreferences.getBoolean("sync_enabled", false)
            setOnCheckedChangeListener { _, enabled ->
                receiptPreferences.edit().putBoolean("sync_enabled", enabled).apply()
                if (enabled) requestSync() else status.text = "Health Sync is OFF. Match deletions remain enabled."
            }
        }
        layout.addView(syncToggle, 1)
        layout.addView(Button(this).apply {
            text = "Manage Health Connect access"
            setOnClickListener {
                openExternal(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            }
        })
        layout.addView(Button(this).apply {
            text = "Install or update Health Connect"
            setOnClickListener {
                openExternal(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
            }
        })
        layout.addView(Button(this).apply {
            text = "Privacy and deletion information"
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, PermissionsRationaleActivity::class.java)) }
        })
        layout.addView(TextView(this).apply {
            text = "Match steps stay on the watch; this app does not export step records. " +
                "Deleting a match requests deletion of its PadelScore records from Health Connect. " +
                "Copies in Samsung Health or other apps may need to be deleted there separately. " +
                "An import already in progress may finish when you switch sync off."
        })
        setContentView(android.widget.ScrollView(this).apply { addView(layout) })
        refreshAvailability()
    }

    private fun openExternal(intent: android.content.Intent) {
        try { startActivity(intent) } catch (e: android.content.ActivityNotFoundException) {
            status.text = "No app can open this action. Open Health Connect from phone Settings."
        }
    }

    private fun refreshAvailability() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            status.text = "Health Connect unavailable or needs installation/update (status $sdkStatus)."
            return
        }
        scope.launch {
            try {
                val granted = withContext(Dispatchers.IO) {
                    HealthConnectClient.getOrCreate(this@MainActivity)
                        .permissionController.getGrantedPermissions()
                }
                status.text = if (granted.containsAll(syncPermissions)) "Connected. Ready to sync workouts." else
                    "Health Connect available. Tap Connect to grant workout and heart-rate write access."
            } catch (e: Exception) {
                status.text = "Connection failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    private fun requestPermission() {
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            permissionLauncher.launch(syncPermissions)
        } else {
            refreshAvailability()
        }
    }

    private fun requestSync() {
        if (syncJob?.isActive == true) return
        status.text = "Syncing..."
        syncButton.isEnabled = false
        Log.i("PadelScoreSync", "Manual or automatic sync requested")
        syncJob = scope.launch {
            try {
                syncPending()
            } finally {
                syncButton.isEnabled = true
            }
        }
    }

    private suspend fun syncPending() {
        try {
            WorkoutHealthImporter(applicationContext).importPending { message ->
                withContext(Dispatchers.Main) { status.text = message }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PadelScoreSync", "Sync failed", e)
            status.text = "Sync failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        requestSync()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
