package ch.hygro.padelscore.sync

import android.os.Bundle
import androidx.core.content.edit
import androidx.core.net.toUri
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
import kotlin.time.Duration.Companion.milliseconds

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
            status.text = getString(R.string.status_permission_granted)
            requestSync()
        } else {
            status.text = getString(R.string.status_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(android.graphics.Color.rgb(246, 247, 249))
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.app_title)
            textSize = 28f
            setTextColor(android.graphics.Color.rgb(24, 32, 40))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 15f
            setTextColor(android.graphics.Color.rgb(91, 101, 111))
            setPadding(0, dp(4), 0, dp(20))
        })

        val statusCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        statusCard.addView(TextView(this).apply {
            text = getString(R.string.current_status)
            textSize = 12f
            setTextColor(android.graphics.Color.rgb(1, 40, 83))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = getString(R.string.checking_health_connect)
            textSize = 17f
            setTextColor(android.graphics.Color.rgb(24, 32, 40))
            setPadding(0, dp(6), 0, 0)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        statusCard.addView(status)
        page.addView(statusCard, spacedParams(dp(14)))

        page.addView(accordionSection(
            title = getString(R.string.health_connect),
            summary = getString(R.string.health_connect_summary),
            initiallyExpanded = true
        ) { content ->
            val syncToggle = android.widget.Switch(this).apply {
                text = getString(R.string.allow_workout_imports)
                textSize = 16f
                isChecked = receiptPreferences.getBoolean("sync_enabled", false)
                setPadding(0, dp(4), 0, dp(10))
                setOnCheckedChangeListener { _, enabled ->
                    receiptPreferences.edit { putBoolean("sync_enabled", enabled) }
                    if (enabled) requestSync()
                    else status.text = getString(R.string.status_sync_off)
                }
            }
            content.addView(syncToggle)

            content.addView(primaryButton(getString(R.string.connect_health_connect)) { requestPermission() })

            syncButton = secondaryButton(getString(R.string.sync_watch_workouts_now)) { requestSync() }
            content.addView(syncButton, spacedParams(dp(10)))

            content.addView(secondaryButton(getString(R.string.manage_health_connect_access)) {
                openExternal(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            }, spacedParams(dp(10)))

            content.addView(secondaryButton(getString(R.string.install_or_update_health_connect)) {
                openExternal(android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()
                ))
            }, spacedParams(dp(10)))

            content.addView(bodyText(getString(R.string.health_sync_explanation)), spacedParams(dp(12)))
        }, spacedParams(dp(14)))

        page.addView(accordionSection(
            title = getString(R.string.watch_app),
            summary = getString(R.string.watch_app_summary),
            initiallyExpanded = false
        ) { content ->
            watchInstallButton = primaryButton(getString(R.string.install_or_update_on_watch)) {
                chooseWatchForInstallation()
            }
            content.addView(watchInstallButton)

            watchInstallStatus = bodyText(getString(R.string.watch_install_explanation))
            content.addView(watchInstallStatus, spacedParams(dp(12)))
        }, spacedParams(dp(14)))

        page.addView(accordionSection(
            title = getString(R.string.privacy_and_help),
            summary = getString(R.string.privacy_summary),
            initiallyExpanded = false
        ) { content ->
            content.addView(secondaryButton(getString(R.string.privacy_and_deletion_info)) {
                startActivity(android.content.Intent(
                    this@MainActivity,
                    PermissionsRationaleActivity::class.java
                ))
            })

            content.addView(bodyText(getString(R.string.privacy_explanation)), spacedParams(dp(12)))

            val phoneManufacturer = android.os.Build.MANUFACTURER
                .lowercase(java.util.Locale.ROOT)
            val phoneBrand = android.os.Build.BRAND
                .lowercase(java.util.Locale.ROOT)
            val xiaomiBrands = setOf("xiaomi", "redmi", "poco")
            if (phoneManufacturer in xiaomiBrands || phoneBrand in xiaomiBrands) {
                content.addView(bodyText(getString(R.string.xiaomi_workaround)), spacedParams(dp(12)))
            }
        }, spacedParams(dp(14)))

        page.addView(accordionSection(
            title = getString(R.string.debug_info),
            summary = getString(R.string.debug_summary),
            initiallyExpanded = false
        ) { content ->
            content.addView(secondaryButton(getString(R.string.show_local_data)) {
                requestSync()
            })
        }, spacedParams(dp(14)))

        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        })
        refreshAvailability()
    }

    private fun accordionSection(
        title: String,
        summary: String,
        initiallyExpanded: Boolean,
        populate: (LinearLayout) -> Unit
    ): LinearLayout {
        val section = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(14), dp(16))
            isClickable = true
            isFocusable = true
            foreground = android.util.TypedValue().let { value ->
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                getDrawable(value.resourceId)
            }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(android.graphics.Color.rgb(24, 32, 40))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = summary
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val indicator = TextView(this).apply {
            text = if (initiallyExpanded) "-" else "+"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(1, 40, 83))
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(indicator, LinearLayout.LayoutParams(dp(40), dp(40)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(18))
            visibility = if (initiallyExpanded) android.view.View.VISIBLE else android.view.View.GONE
        }
        populate(content)

        fun updateAccessibility(expanded: Boolean) {
            val stateText = if (expanded) getString(R.string.expanded) else getString(R.string.collapsed)
            header.contentDescription = "$title, $stateText"
        }
        updateAccessibility(initiallyExpanded)
        header.setOnClickListener {
            val expand = content.visibility != android.view.View.VISIBLE
            content.visibility = if (expand) android.view.View.VISIBLE else android.view.View.GONE
            indicator.text = if (expand) "-" else "+"
            updateAccessibility(expand)
        }

        section.addView(header)
        section.addView(content)
        return section
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(2).toFloat()
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(android.graphics.Color.WHITE)
            setStroke(dp(1), android.graphics.Color.rgb(226, 230, 234))
        }
    }

    private fun primaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        minHeight = dp(52)
        setTextColor(android.graphics.Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.rgb(1, 40, 83)
        )
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        minHeight = dp(52)
        setTextColor(android.graphics.Color.rgb(1, 40, 83))
        backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.rgb(229, 237, 245)
        )
        setOnClickListener { action() }
    }

    private fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(android.graphics.Color.rgb(76, 86, 96))
        setLineSpacing(0f, 1.15f)
    }

    private fun spacedParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
    private fun chooseWatchForInstallation() {
        if (watchInstallJob?.isActive == true || watchSelectionDialog?.isShowing == true) return
        watchInstallButton.isEnabled = false
        watchInstallStatus.text = getString(R.string.status_finding_watches)
        watchInstallJob = scope.launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    com.google.android.gms.tasks.Tasks.await(
                        com.google.android.gms.wearable.Wearable.getNodeClient(applicationContext)
                            .connectedNodes, 10, java.util.concurrent.TimeUnit.SECONDS
                    ).sortedByDescending { it.isNearby }
                }
                when (nodes.size) {
                    0 -> watchInstallStatus.text = getString(R.string.status_no_watch)
                    1 -> watchInstallStatus.text = openWatchStore(nodes.single().id)
                    else -> {
                        watchInstallStatus.text = getString(R.string.status_choose_watch)
                        watchSelectionDialog = android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.choose_watch_title))
                            .setItems(nodes.map { it.displayName }.toTypedArray()) { _, index ->
                                installOnSelectedWatch(nodes[index].id)
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .create()
                        watchSelectionDialog?.show()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                watchInstallStatus.text = getString(R.string.status_watch_discovery_failed, (e.cause ?: e).javaClass.simpleName)
            } finally {
                watchInstallButton.isEnabled = true
            }
        }
    }

    private fun installOnSelectedWatch(nodeId: String) {
        if (watchInstallJob?.isActive == true) return
        watchInstallButton.isEnabled = false
        watchInstallStatus.text = getString(R.string.status_opening_play_store)
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
            applicationContext
        ) { it.run() }
        val links = listOf(
            "market://details?id=$packageName",
            "https://play.google.com/store/apps/details?id=$packageName"
        )
        var failure = "Timeout"
        for (link in links) {
            try {
                val sent = kotlinx.coroutines.withTimeoutOrNull(15_000L.milliseconds) {
                    helper.startRemoteActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, link.toUri())
                            .addCategory(android.content.Intent.CATEGORY_BROWSABLE), nodeId
                    ).await()
                    true
                } ?: false
                if (sent) return getString(R.string.status_store_request_sent)
                failure = "Timeout"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = (e.cause ?: e).javaClass.simpleName
            }
        }
        return getString(R.string.status_store_request_failed, failure)
    }

    private fun openExternal(intent: android.content.Intent) {
        try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            status.text = getString(R.string.status_no_app_for_action)
        }
    }

    private fun refreshAvailability() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            status.text = getString(R.string.status_hc_unavailable, sdkStatus.toString())
            return
        }
        scope.launch {
            try {
                val granted = withContext(Dispatchers.IO) {
                    HealthConnectClient.getOrCreate(this@MainActivity)
                        .permissionController.getGrantedPermissions()
                }
                status.text = if (granted.containsAll(syncPermissions)) getString(R.string.status_connected_ready) else
                    getString(R.string.status_hc_available_connect)
            } catch (e: Exception) {
                status.text = getString(R.string.status_connection_failed, e.javaClass.simpleName, e.message ?: "")
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
        status.text = getString(R.string.status_syncing)
        syncButton.isEnabled = false
        Log.i("PadelScoreSync", getString(R.string.log_sync_requested))
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
            Log.e("PadelScoreSync", getString(R.string.log_sync_failed), e)
            status.text = getString(R.string.status_sync_failed, e.javaClass.simpleName, e.message ?: "")
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
