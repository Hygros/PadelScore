package ch.hygro.padelscore.sync

import android.os.Bundle
import androidx.concurrent.futures.await
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
    private var watchInstallJob: Job? = null
    private lateinit var watchInstallButton: Button
    private lateinit var watchInstallStatus: TextView
    private var watchSelectionDialog: android.app.AlertDialog? = null
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
        val phoneManufacturer = android.os.Build.MANUFACTURER
            .lowercase(java.util.Locale.ROOT)
        val phoneBrand = android.os.Build.BRAND
            .lowercase(java.util.Locale.ROOT)
        val xiaomiBrands = setOf("xiaomi", "redmi", "poco")
        if (phoneManufacturer in xiaomiBrands || phoneBrand in xiaomiBrands) {
            layout.addView(TextView(this).apply {
                text = "Watch links not opening on this phone?\n\n" +
                    "Some Xiaomi, Redmi and POCO phones block links opened by the watch plugin. " +
                    "In phone Settings > Apps, select your watch plugin, then Other permissions. " +
                    "If available, allow 'Display pop-up windows while running in the background'. " +
                    "For Samsung watches, select the Galaxy Watch plugin, not PadelScore. " +
                    "Menu names vary. Only change this setting if watch links do not open. " +
                    "PadelScore cannot check or grant this setting."
                setPadding(0, 12, 0, 24)
            })
        }

        watchInstallButton = Button(this).apply {
            text = "Install / update watch app"
            setOnClickListener { chooseWatchForInstallation() }
        }
        layout.addView(watchInstallButton)
        watchInstallStatus = TextView(this).apply {
            text = "Opens the Play Store on your connected Wear OS watch. " +
                "Confirm installation on the watch. Test-track access is still required."
            setPadding(0, 12, 0, 24)
        }
        layout.addView(watchInstallStatus)
        setContentView(android.widget.ScrollView(this).apply { addView(layout) })
        refreshAvailability()
    }

    private fun chooseWatchForInstallation() {
        if (watchInstallJob?.isActive == true || watchSelectionDialog?.isShowing == true) return
        watchInstallButton.isEnabled = false
        watchInstallStatus.text = "Finding connected watches..."
        watchInstallJob = scope.launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    com.google.android.gms.tasks.Tasks.await(
                        com.google.android.gms.wearable.Wearable.getNodeClient(applicationContext)
                            .connectedNodes, 10, java.util.concurrent.TimeUnit.SECONDS
                    ).sortedByDescending { it.isNearby }
                }
                when (nodes.size) {
                    0 -> watchInstallStatus.text =
                        "No connected Wear OS watch. Check pairing and Google Play services."
                    1 -> watchInstallStatus.text = openWatchStore(nodes.single().id)
                    else -> {
                        watchInstallStatus.text = "Choose the watch to install or update."
                        watchSelectionDialog = android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Choose watch")
                            .setItems(nodes.map { it.displayName }.toTypedArray()) { _, index ->
                                installOnSelectedWatch(nodes[index].id)
                            }
                            .setNegativeButton("Cancel", null)
                            .create()
                        watchSelectionDialog?.show()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                watchInstallStatus.text = "Watch discovery failed (${(e.cause ?: e).javaClass.simpleName}). " +
                    "Check pairing and Google Play services."
            } finally {
                watchInstallButton.isEnabled = true
            }
        }
    }

    private fun installOnSelectedWatch(nodeId: String) {
        if (watchInstallJob?.isActive == true) return
        watchInstallButton.isEnabled = false
        watchInstallStatus.text = "Opening Play Store on the watch..."
        watchInstallJob = scope.launch {
            try {
                watchInstallStatus.text = openWatchStore(nodeId)
            } finally {
                watchInstallButton.isEnabled = true
            }
        }
    }

    private suspend fun openWatchStore(nodeId: String): String {
        val helper = androidx.wear.remote.interactions.RemoteActivityHelper(
            applicationContext,
            java.util.concurrent.Executor { command -> command.run() }
        )
        val links = listOf(
            "market://details?id=$packageName",
            "https://play.google.com/store/apps/details?id=$packageName"
        )
        var failure = "Timeout"
        for (link in links) {
            try {
                val sent = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    helper.startRemoteActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                            .addCategory(android.content.Intent.CATEGORY_BROWSABLE), nodeId
                    ).await()
                    true
                } ?: false
                if (sent) return "Store request sent. Continue on your watch. " +
                    "Installation and test-track access are not confirmed by this request."
                failure = "Timeout"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = (e.cause ?: e).javaClass.simpleName
            }
        }
        return "Watch store request failed ($failure). Unlock the watch and check its connection. " +
            "Alternatively, open Play Store on the watch and search for Padel Score."
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
        watchSelectionDialog?.dismiss()
        watchSelectionDialog = null
        scope.cancel()
        super.onDestroy()
    }
}
