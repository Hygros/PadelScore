package ch.hygro.padelscore.sync

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.concurrent.futures.await
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val syncPermissions = WorkoutHealthImporter.permissions
    private lateinit var rootScrollView: ScrollView
    private lateinit var status: TextView
    private lateinit var syncButton: Button
    private var syncJob: Job? = null
    private var watchInstallJob: Job? = null
    private lateinit var watchInstallButton: Button
    private lateinit var watchInstallStatus: TextView
    private lateinit var matchesTabContainer: LinearLayout
    private lateinit var healthTabContainer: LinearLayout
    private lateinit var watchHelpTabContainer: LinearLayout
    private val tabButtons = mutableListOf<TextView>()
    private var activeTabIndex = 0
    private var selectedMatchId: Long? = null
    private var selectedGameIndex: Int = 0
    private var watchSelectionDialog: AlertDialog? = null

    private val backFromDetailCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (selectedMatchId != null) {
                selectedMatchId = null
                selectedGameIndex = 0
                isEnabled = false
                renderMatchesSection()
                rootScrollView.post { rootScrollView.smoothScrollTo(0, 0) }
            }
        }
    }

    private val receiptPreferences by lazy {
        getSharedPreferences("health_sync_receipts", MODE_PRIVATE)
    }
    private val receiptListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "last_status") runOnUiThread {
            if (::status.isInitialized) status.text = prefs.getString("last_status", "")
        }
    }

    private val historyPreferences by lazy { PhoneMatchHistory.preferences(this) }
    private val historyListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "entries" || key == "enabled" || key == null) {
            runOnUiThread { renderMatchesSection() }
        }
    }

    override fun onStart() {
        super.onStart()
        receiptPreferences.registerOnSharedPreferenceChangeListener(receiptListener)
        historyPreferences.registerOnSharedPreferenceChangeListener(historyListener)
    }

    override fun onStop() {
        historyPreferences.unregisterOnSharedPreferenceChangeListener(historyListener)
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
        onBackPressedDispatcher.addCallback(this, backFromDetailCallback)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(32))
            setBackgroundColor(Color.rgb(244, 246, 249))
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.app_title)
            textSize = 27f
            setTextColor(Color.rgb(18, 28, 38))
            setTypeface(typeface, Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 14f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, dp(16))
        })

        // Top Register / Tab Bar
        page.addView(createTabBar())

        // Tab 0: Matches, Extended Statistics & Charts (Default)
        matchesTabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(matchesTabContainer, spacedParams(dp(14)))

        // Tab 1: Health Connect & Workout Sync
        healthTabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        populateHealthTab(healthTabContainer)
        page.addView(healthTabContainer, spacedParams(dp(14)))

        // Tab 2: Watch App & Privacy / Help
        watchHelpTabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        populateWatchAndHelpTab(watchHelpTabContainer)
        page.addView(watchHelpTabContainer, spacedParams(dp(14)))

        rootScrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        setContentView(rootScrollView)

        if (savedInstanceState != null) {
            activeTabIndex = savedInstanceState.getInt("activeTabIndex", 0)
            selectedGameIndex = savedInstanceState.getInt("selectedGameIndex", 0)
            selectedMatchId = if (savedInstanceState.containsKey("selectedMatchId")) savedInstanceState.getLong("selectedMatchId") else null
        }

        selectTab(activeTabIndex)
        renderMatchesSection()
        refreshAvailability()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("activeTabIndex", activeTabIndex)
        outState.putInt("selectedGameIndex", selectedGameIndex)
        selectedMatchId?.let { outState.putLong("selectedMatchId", it) }
    }

    private fun createTabBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(225, 232, 240))
            }
        }
        val titles = listOf(
            getString(R.string.tab_matches_stats),
            getString(R.string.tab_health_sync),
            getString(R.string.tab_watch_help)
        )
        tabButtons.clear()
        titles.forEachIndexed { index, title ->
            val tabView = TextView(this).apply {
                text = title
                textSize = 13.5f
                gravity = Gravity.CENTER
                maxLines = 2
                // Long translations shrink (and wrap) instead of being cut off.
                setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTab(index) }
            }
            tabButtons.add(tabView)
            bar.addView(
                tabView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        return bar
    }

    private fun selectTab(index: Int) {
        activeTabIndex = index
        matchesTabContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        healthTabContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        watchHelpTabContainer.visibility = if (index == 2) View.VISIBLE else View.GONE

        tabButtons.forEachIndexed { i, btn ->
            val selected = i == index
            btn.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            btn.setTextColor(if (selected) Color.WHITE else Color.rgb(32, 48, 64))
            btn.background = if (selected) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(11).toFloat()
                    setColor(Color.rgb(1, 40, 83))
                }
            } else {
                null
            }
        }
        backFromDetailCallback.isEnabled = (index == 0 && selectedMatchId != null)
    }

    private fun populateHealthTab(container: LinearLayout) {
        val statusCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        statusCard.addView(TextView(this).apply {
            text = getString(R.string.current_status)
            textSize = 12f
            setTextColor(Color.rgb(1, 40, 83))
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = getString(R.string.checking_health_connect)
            textSize = 16f
            setTextColor(Color.rgb(24, 32, 40))
            setPadding(0, dp(6), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        statusCard.addView(status)
        container.addView(statusCard)

        val syncCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        syncCard.addView(TextView(this).apply {
            text = getString(R.string.health_connect)
            textSize = 18f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })
        syncCard.addView(TextView(this).apply {
            text = getString(R.string.health_connect_summary)
            textSize = 14f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, dp(12))
        })

        val syncToggle = Switch(this).apply {
            text = getString(R.string.allow_workout_imports)
            textSize = 15.5f
            isChecked = receiptPreferences.getBoolean("sync_enabled", false)
            setPadding(0, dp(4), 0, dp(12))
            setOnCheckedChangeListener { _, enabled ->
                receiptPreferences.edit { putBoolean("sync_enabled", enabled) }
                if (enabled) requestSync()
                else status.text = getString(R.string.status_sync_off)
            }
        }
        syncCard.addView(syncToggle)

        syncCard.addView(primaryButton(getString(R.string.connect_health_connect)) { requestPermission() })

        syncButton = secondaryButton(getString(R.string.sync_watch_workouts_now)) { requestSync() }
        syncCard.addView(syncButton, spacedParams(dp(10)))

        syncCard.addView(bodyText(getString(R.string.health_sync_explanation)), spacedParams(dp(12)))
        container.addView(syncCard, spacedParams(dp(14)))

        container.addView(accordionSection(
            title = getString(R.string.health_connect_advanced),
            summary = getString(R.string.health_connect_advanced_summary)
        ) { content ->
            content.addView(secondaryButton(getString(R.string.manage_health_connect_access)) {
                openExternal(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            })

            content.addView(secondaryButton(getString(R.string.install_or_update_health_connect)) {
                openExternal(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()
                    )
                )
            }, spacedParams(dp(10)))
        }, spacedParams(dp(14)))
    }

    private fun populateWatchAndHelpTab(container: LinearLayout) {
        val watchCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        watchCard.addView(TextView(this).apply {
            text = getString(R.string.watch_app)
            textSize = 18f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })
        watchCard.addView(TextView(this).apply {
            text = getString(R.string.watch_app_summary)
            textSize = 14f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, dp(12))
        })
        watchInstallButton = primaryButton(getString(R.string.install_or_update_on_watch)) {
            chooseWatchForInstallation()
        }
        watchCard.addView(watchInstallButton)
        watchInstallStatus = bodyText(getString(R.string.watch_install_explanation))
        watchCard.addView(watchInstallStatus, spacedParams(dp(12)))
        container.addView(watchCard)

        val privacyCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        privacyCard.addView(TextView(this).apply {
            text = getString(R.string.privacy_and_help)
            textSize = 18f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })
        privacyCard.addView(TextView(this).apply {
            text = getString(R.string.privacy_summary)
            textSize = 14f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, dp(12))
        })
        privacyCard.addView(secondaryButton(getString(R.string.privacy_and_deletion_info)) {
            startActivity(
                Intent(
                    this@MainActivity,
                    PermissionsRationaleActivity::class.java
                )
            )
        })
        privacyCard.addView(bodyText(getString(R.string.privacy_explanation)), spacedParams(dp(12)))

        val phoneManufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val phoneBrand = Build.BRAND.lowercase(Locale.ROOT)
        val xiaomiBrands = setOf("xiaomi", "redmi", "poco")
        if (phoneManufacturer in xiaomiBrands || phoneBrand in xiaomiBrands) {
            privacyCard.addView(bodyText(getString(R.string.xiaomi_workaround)), spacedParams(dp(12)))
        }
        container.addView(privacyCard, spacedParams(dp(14)))
    }

    private fun renderMatchesSection() {
        if (!::matchesTabContainer.isInitialized) return
        matchesTabContainer.removeAllViews()

        val prefs = historyPreferences
        if (!prefs.getBoolean("enabled", false)) {
            val disabledCard = card().apply {
                setPadding(dp(18), dp(16), dp(18), dp(18))
            }
            disabledCard.addView(TextView(this).apply {
                text = getString(R.string.match_history)
                textSize = 18f
                setTextColor(Color.rgb(24, 32, 40))
                setTypeface(typeface, Typeface.BOLD)
            })
            disabledCard.addView(
                bodyText(getString(R.string.match_history_disabled)),
                spacedParams(dp(8))
            )
            disabledCard.addView(secondaryButton(getString(R.string.refresh_match_history)) {
                PhoneMatchHistory.request(applicationContext)
            }, spacedParams(dp(12)))
            matchesTabContainer.addView(disabledCard)
            return
        }

        val entries = PhoneMatchHistoryParser.parseEntries(prefs.getString("entries", "[]"))
        val detailEntry = selectedMatchId?.let { id -> entries.firstOrNull { it.matchId == id } }
        if (detailEntry != null) {
            backFromDetailCallback.isEnabled = (activeTabIndex == 0)
            renderMatchDetailView(matchesTabContainer, detailEntry)
            return
        }
        selectedMatchId = null
        backFromDetailCallback.isEnabled = false

        // 1. Overall Statistics Card
        matchesTabContainer.addView(buildOverallStatisticsCard(entries))

        // 2. Match List Card
        val listCard = card().apply {
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        listCard.addView(TextView(this).apply {
            text = getString(R.string.match_history)
            textSize = 18f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })
        listCard.addView(TextView(this).apply {
            text = getString(R.string.match_history_summary)
            textSize = 13.5f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(2), 0, dp(10))
        })

        if (entries.isEmpty()) {
            listCard.addView(bodyText(getString(R.string.no_matches_yet)), spacedParams(dp(6)))
        } else {
            entries.forEach { entry ->
                listCard.addView(buildMatchListCard(entry), spacedParams(dp(10)))
            }
        }

        listCard.addView(secondaryButton(getString(R.string.refresh_match_history)) {
            PhoneMatchHistory.request(applicationContext)
        }, spacedParams(dp(14)))

        matchesTabContainer.addView(listCard, spacedParams(dp(14)))
    }

    private fun buildOverallStatisticsCard(entries: List<PhoneMatchHistoryEntry>): LinearLayout {
        val statsCard = card().apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        statsCard.addView(TextView(this).apply {
            text = getString(R.string.overall_statistics_title)
            textSize = 18f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })

        val totalMatches = entries.size
        val wins = entries.count { it.stoppedState == null && it.weWon }
        val completedCount = entries.count { it.stoppedState == null }
        val winRate = if (completedCount > 0) ((wins * 100.0) / completedCount).roundToInt() else 0
        val totalGames = entries.sumOf { it.totalGamesPlayed }
        val totalPoints = entries.sumOf { it.totalPointsPlayed }
        val totalSeconds = entries.sumOf {
            ((it.finishedAtEpochMillis - it.startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
        }
        val totalHours = totalSeconds / 3600L
        val totalMinutes = (totalSeconds % 3600L) / 60L
        val totalTimeText = if (totalHours > 0L) {
            getString(R.string.hours_format, totalHours, totalMinutes)
        } else {
            getString(R.string.minutes_format, totalMinutes)
        }
        val stepsEntries = entries.mapNotNull { it.steps }
        val totalStepsText = if (stepsEntries.isNotEmpty()) stepsEntries.sum().toString() else "--"

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row1.addView(
            statTile(getString(R.string.stat_matches), totalMatches.toString()),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        row1.addView(
            statTile(getString(R.string.stat_wins_rate, winRate), wins.toString()),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )
        statsCard.addView(row1, spacedParams(dp(12)))

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row2.addView(
            statTile(getString(R.string.stat_total_time), totalTimeText),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        row2.addView(
            statTile(getString(R.string.stat_total_steps), totalStepsText),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )
        statsCard.addView(row2, spacedParams(dp(8)))

        statsCard.addView(TextView(this).apply {
            text = getString(R.string.match_statistics, totalMatches, wins, totalGames, totalPoints)
            textSize = 13f
            setTextColor(Color.rgb(76, 86, 96))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        })

        return statsCard
    }

    private fun buildMatchListCard(entry: PhoneMatchHistoryEntry): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(240, 244, 248))
                setStroke(dp(1), Color.rgb(218, 226, 236))
            }
            setOnClickListener {
                selectedMatchId = entry.matchId
                selectedGameIndex = 0
                renderMatchesSection()
                rootScrollView.post { rootScrollView.smoothScrollTo(0, 0) }
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val statusLabel = matchStatusLabel(entry)
        val statusColor = matchStatusColor(entry)
        topRow.addView(
            TextView(this).apply {
                text = statusLabel
                textSize = 12f
                setTextColor(statusColor)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topRow.addView(TextView(this).apply {
            text = formatMatchDate(entry.finishedAtEpochMillis)
            textSize = 12f
            setTextColor(Color.rgb(100, 112, 124))
        })
        item.addView(topRow)

        item.addView(TextView(this).apply {
            text = formatResultLine(entry)
            textSize = 19f
            setTextColor(Color.rgb(18, 28, 38))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        })

        val statsLine = buildString {
            append(getString(R.string.match_details, entry.totalGamesPlayed, entry.totalPointsPlayed))
            append(" · ").append(formatDuration(entry))
            entry.averageHeartRate?.let { append(" · ").append(getString(R.string.match_heart_rate, it)) }
            entry.maximumHeartRate?.let { append(" · ").append(getString(R.string.match_max_heart_rate, it)) }
        }
        item.addView(TextView(this).apply {
            text = statsLine
            textSize = 13f
            setTextColor(Color.rgb(76, 86, 96))
        })

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        bottomRow.addView(
            TextView(this).apply {
                text = entry.steps?.let { getString(R.string.match_steps, it) }
                    ?: getString(R.string.steps_unavailable)
                textSize = 12.5f
                setTextColor(Color.rgb(100, 112, 124))
                setPadding(0, 0, dp(8), 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        // Weighted as well, so a long translation wraps instead of squeezing the steps text to 0 width.
        bottomRow.addView(
            TextView(this).apply {
                text = getString(R.string.tap_for_details)
                textSize = 12.5f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setTextColor(Color.rgb(1, 40, 83))
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        item.addView(bottomRow)

        return item
    }

    private fun renderMatchDetailView(container: LinearLayout, entry: PhoneMatchHistoryEntry) {
        container.addView(secondaryButton(getString(R.string.back_to_matches)) {
            selectedMatchId = null
            selectedGameIndex = 0
            backFromDetailCallback.isEnabled = false
            renderMatchesSection()
            rootScrollView.post { rootScrollView.smoothScrollTo(0, 0) }
        })

        // 1. Match Header Card
        val headerCard = card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val accent = matchStatusColor(entry)
        headerCard.addView(TextView(this).apply {
            text = matchStatusLabel(entry)
            textSize = 14f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        if (entry.stoppedState != null) {
            headerCard.addView(TextView(this).apply {
                text = getString(R.string.provisional_result, getString(R.string.ended_early))
                textSize = 12f
                setTextColor(Color.rgb(100, 112, 124))
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
        headerCard.addView(TextView(this).apply {
            text = formatMatchDate(entry.finishedAtEpochMillis)
            textSize = 12.5f
            setTextColor(Color.rgb(100, 112, 124))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        })

        val teamLabelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        teamLabelsRow.addView(
            TextView(this).apply {
                text = getString(R.string.our_team)
                textSize = 13f
                setTextColor(PhoneGameScoreChartView.COLOR_TEAM_US)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        teamLabelsRow.addView(
            TextView(this).apply {
                text = getString(R.string.sets_label)
                textSize = 12f
                setTextColor(Color.rgb(100, 112, 124))
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        teamLabelsRow.addView(
            TextView(this).apply {
                text = getString(R.string.opponent_short)
                textSize = 13f
                setTextColor(PhoneGameScoreChartView.COLOR_TEAM_OPP)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerCard.addView(teamLabelsRow)

        headerCard.addView(TextView(this).apply {
            text = getString(R.string.score_pair_int, entry.ourSets, entry.opponentSets)
            textSize = 36f
            setTextColor(Color.rgb(18, 28, 38))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        })

        val stopped = entry.stoppedState
        if (stopped != null) {
            val advAbbr = getString(R.string.adv_abbr)
            val pointScore = getString(
                R.string.score_pair_text,
                PhoneMatchHistoryParser.pointText(stopped, true, advAbbr),
                PhoneMatchHistoryParser.pointText(stopped, false, advAbbr)
            )
            headerCard.addView(TextView(this).apply {
                text = getString(R.string.points_at_stop)
                textSize = 11.5f
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            headerCard.addView(TextView(this).apply {
                text = pointScore
                textSize = 24f
                setTextColor(Color.rgb(18, 28, 38))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
        }
        container.addView(headerCard, spacedParams(dp(12)))

        // 2. Set & Tie-Break Breakdown Rows
        val scoreRows = buildScoreRows(entry)
        scoreRows.forEach { (title, ourVal, oppVal) ->
            val setCard = card().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            setCard.addView(TextView(this).apply {
                text = title
                textSize = 12f
                setTextColor(Color.rgb(91, 101, 111))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            val valsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, 0)
            }
            valsRow.addView(
                TextView(this).apply {
                    text = ourVal
                    textSize = 24f
                    setTextColor(PhoneGameScoreChartView.COLOR_TEAM_US)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            valsRow.addView(TextView(this).apply {
                text = ":"
                textSize = 22f
                setTextColor(Color.rgb(120, 132, 144))
                gravity = Gravity.CENTER
            })
            valsRow.addView(
                TextView(this).apply {
                    text = oppVal
                    textSize = 24f
                    setTextColor(PhoneGameScoreChartView.COLOR_TEAM_OPP)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            setCard.addView(valsRow)
            container.addView(setCard, spacedParams(dp(8)))
        }

        // 3. Game Score Chart & Rally-by-Rally Inspector
        val gameGroups = PhoneMatchHistoryParser.groupPointsByGame(entry.pointHistory)
        if (gameGroups.isNotEmpty()) {
            val chartIndex = selectedGameIndex.coerceIn(0, gameGroups.lastIndex)
            selectedGameIndex = chartIndex
            val selectedPoints = gameGroups[chartIndex]

            val chartCard = card().apply {
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            chartCard.addView(TextView(this).apply {
                text = formatGameTitle(selectedPoints.first())
                textSize = 15f
                setTextColor(Color.rgb(18, 28, 38))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            })

            val chartSurface = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(6), dp(8), dp(4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.rgb(240, 244, 248))
                    setStroke(dp(1), Color.rgb(220, 228, 236))
                }
            }
            val chartView = PhoneGameScoreChartView(this).apply {
                setPoints(selectedPoints)
            }
            chartSurface.addView(
                chartView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(190)
                )
            )
            chartCard.addView(chartSurface)

            // Legend row
            val legendRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }
            legendRow.addView(
                TextView(this).apply {
                    text = getString(R.string.team_legend_dot, getString(R.string.our_team))
                    textSize = 12.5f
                    setTextColor(PhoneGameScoreChartView.COLOR_TEAM_US)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            legendRow.addView(
                TextView(this).apply {
                    text = getString(R.string.team_legend_dot, getString(R.string.opponent_short))
                    textSize = 12.5f
                    setTextColor(PhoneGameScoreChartView.COLOR_TEAM_OPP)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            chartCard.addView(legendRow)

            // Prev / Position / Next navigation
            val navRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val prevBtn = Button(this).apply {
                text = getString(R.string.chart_prev)
                isAllCaps = false
                maxLines = 2
                setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                isEnabled = chartIndex > 0
                setTextColor(if (isEnabled) Color.WHITE else Color.rgb(130, 142, 154))
                backgroundTintList = ColorStateList.valueOf(
                    if (isEnabled) Color.rgb(1, 40, 83) else Color.rgb(225, 232, 240)
                )
                setOnClickListener {
                    selectedGameIndex = (chartIndex - 1).coerceAtLeast(0)
                    renderMatchesSection()
                }
            }
            val posLabel = TextView(this).apply {
                text = getString(R.string.chart_page_indicator, chartIndex + 1, gameGroups.size)
                textSize = 13.5f
                setTextColor(Color.rgb(64, 78, 92))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
            }
            val nextBtn = Button(this).apply {
                text = getString(R.string.chart_next)
                isAllCaps = false
                maxLines = 2
                setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                isEnabled = chartIndex < gameGroups.lastIndex
                setTextColor(if (isEnabled) Color.WHITE else Color.rgb(130, 142, 154))
                backgroundTintList = ColorStateList.valueOf(
                    if (isEnabled) Color.rgb(1, 40, 83) else Color.rgb(225, 232, 240)
                )
                setOnClickListener {
                    selectedGameIndex = (chartIndex + 1).coerceAtMost(gameGroups.lastIndex)
                    renderMatchesSection()
                }
            }
            navRow.addView(prevBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            navRow.addView(posLabel)
            navRow.addView(nextBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            chartCard.addView(navRow)

            container.addView(chartCard, spacedParams(dp(12)))

            // Collapsible rally-by-rally breakdown for the selected game
            container.addView(
                accordionSection(
                    title = getString(R.string.show_rally_list, selectedPoints.size),
                    summary = getString(
                        R.string.score_after_each_rally,
                        getString(R.string.our_team),
                        getString(R.string.opponent_short)
                    )
                ) { rallyContent ->
                    val usLabel = getString(R.string.our_team)
                    val oppLabel = getString(R.string.opponent_short)
                    selectedPoints.forEachIndexed { idx, pt ->
                        val pointCard = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                            setPadding(dp(12), dp(8), dp(12), dp(8))
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(12).toFloat()
                                setColor(Color.rgb(240, 244, 248))
                                setStroke(dp(1), Color.rgb(220, 228, 236))
                            }
                        }
                        val teamColor = if (pt.scoringTeamUs) {
                            PhoneGameScoreChartView.COLOR_TEAM_US
                        } else {
                            PhoneGameScoreChartView.COLOR_TEAM_OPP
                        }
                        pointCard.addView(TextView(this).apply {
                            text = getString(
                                R.string.point_number_team,
                                pt.number,
                                if (pt.scoringTeamUs) usLabel else oppLabel
                            )
                            textSize = 11.5f
                            setTextColor(teamColor)
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                        val usScore = PhoneMatchHistoryParser.recordedScore(selectedPoints, idx, true) ?: "?"
                        val oppScore = PhoneMatchHistoryParser.recordedScore(selectedPoints, idx, false) ?: "?"
                        pointCard.addView(TextView(this).apply {
                            text = getString(R.string.score_pair_text, usScore, oppScore)
                            textSize = 20f
                            setTextColor(Color.rgb(18, 28, 38))
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                        if (pt.gameEnded) {
                            pointCard.addView(TextView(this).apply {
                                text = getString(R.string.game_won_rallies, pt.ourRallies, pt.opponentRallies)
                                textSize = 11.5f
                                setTextColor(teamColor)
                                gravity = Gravity.CENTER
                            })
                        }
                        rallyContent.addView(pointCard, spacedParams(dp(6)))
                    }
                },
                spacedParams(dp(10))
            )
        } else if (stopped == null) {
            container.addView(
                bodyText(getString(R.string.older_matches_no_point_history)).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(4))
                },
                spacedParams(dp(8))
            )
        }

        // 4. Extended 6-Tile Match Metrics Grid (Duration, Points, Games, Steps, Avg HR, Max HR)
        val metricPairs = listOf(
            (getString(R.string.duration) to formatDuration(entry)) to
                (getString(R.string.points_played) to entry.totalPointsPlayed.toString()),
            (getString(R.string.games_played) to entry.totalGamesPlayed.toString()) to
                (getString(R.string.steps_label) to (entry.steps?.toString() ?: "--")),
            (getString(R.string.avg_heart_rate_label) to (entry.averageHeartRate?.let { "$it bpm" } ?: "--")) to
                (getString(R.string.max_heart_rate_label) to (entry.maximumHeartRate?.let { "$it bpm" } ?: "--"))
        )
        metricPairs.forEach { (leftMetric, rightMetric) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(
                statTile(leftMetric.first, leftMetric.second),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(5)
                }
            )
            row.addView(
                statTile(rightMetric.first, rightMetric.second),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(5)
                }
            )
            container.addView(row, spacedParams(dp(8)))
        }
    }

    private fun buildScoreRows(entry: PhoneMatchHistoryEntry): List<Triple<String, String, String>> {
        val stopped = entry.stoppedState
        return buildList {
            entry.completedSets.forEachIndexed { index, set ->
                add(
                    Triple(
                        getString(R.string.set_number, index + 1),
                        set.ourGames.toString(),
                        set.opponentGames.toString()
                    )
                )
                if (set.wasTieBreak) {
                    add(
                        Triple(
                            getString(R.string.tie_break),
                            set.ourTieBreakPoints.toString(),
                            set.opponentTieBreakPoints.toString()
                        )
                    )
                }
            }
            if (stopped != null) {
                if (stopped.isMatchTieBreak) {
                    add(
                        Triple(
                            getString(R.string.match_tie_break_full),
                            stopped.ourMatchTieBreakPoints.toString(),
                            stopped.opponentMatchTieBreakPoints.toString()
                        )
                    )
                } else {
                    add(
                        Triple(
                            getString(R.string.current_set),
                            stopped.ourGames.toString(),
                            stopped.opponentGames.toString()
                        )
                    )
                    if (stopped.isTieBreak) {
                        add(
                            Triple(
                                getString(R.string.tie_break_points),
                                stopped.ourTieBreakPoints.toString(),
                                stopped.opponentTieBreakPoints.toString()
                            )
                        )
                    }
                }
            } else if (entry.ourMatchTieBreakPoints > 0 || entry.opponentMatchTieBreakPoints > 0) {
                add(
                    Triple(
                        getString(R.string.match_tie_break_full),
                        entry.ourMatchTieBreakPoints.toString(),
                        entry.opponentMatchTieBreakPoints.toString()
                    )
                )
            }
        }
    }

    private fun formatGameTitle(point: PhoneGamePoint): String = when {
        point.matchTieBreak -> getString(R.string.match_tie_break_full)
        point.numeric -> getString(R.string.set_tie_break_title, point.setNumber)
        else -> getString(R.string.set_game_title, point.setNumber, point.gameNumber)
    }

    private fun matchStatusLabel(entry: PhoneMatchHistoryEntry): String {
        if (entry.stoppedState != null) {
            val balance = PhoneMatchHistoryParser.earlyStopBalance(entry)
            return when {
                balance > 0 -> getString(R.string.won_at_stop)
                balance < 0 -> getString(R.string.lost_at_stop)
                else -> getString(R.string.tied_at_stop)
            }
        }
        return if (entry.weWon) getString(R.string.match_won) else getString(R.string.match_lost)
    }

    private fun matchStatusColor(entry: PhoneMatchHistoryEntry): Int {
        if (entry.stoppedState != null) {
            val balance = PhoneMatchHistoryParser.earlyStopBalance(entry)
            return when {
                balance > 0 -> PhoneGameScoreChartView.COLOR_WON
                balance < 0 -> PhoneGameScoreChartView.COLOR_LOST
                else -> PhoneGameScoreChartView.COLOR_TIED
            }
        }
        return if (entry.weWon) PhoneGameScoreChartView.COLOR_WON else PhoneGameScoreChartView.COLOR_LOST
    }

    private fun formatResultLine(entry: PhoneMatchHistoryEntry): String {
        val stopped = entry.stoppedState
        if (stopped != null) {
            val completed = stopped.completedSets.joinToString("  ") { "${it.ourGames}:${it.opponentGames}" }
            val current = when {
                stopped.isMatchTieBreak -> getString(
                    R.string.match_tie_break_result,
                    stopped.ourMatchTieBreakPoints,
                    stopped.opponentMatchTieBreakPoints
                )
                stopped.isTieBreak -> {
                    val games = getString(R.string.games_result, stopped.ourGames, stopped.opponentGames)
                    val tb = getString(
                        R.string.tie_break_result,
                        stopped.ourTieBreakPoints,
                        stopped.opponentTieBreakPoints
                    )
                    "$games  $tb"
                }
                else -> {
                    val advAbbr = getString(R.string.adv_abbr)
                    val games = getString(R.string.games_result, stopped.ourGames, stopped.opponentGames)
                    val ourPts = PhoneMatchHistoryParser.pointText(stopped, true, advAbbr)
                    val oppPts = PhoneMatchHistoryParser.pointText(stopped, false, advAbbr)
                    "$games  $ourPts:$oppPts"
                }
            }
            return listOf(completed, current).filter { it.isNotBlank() }.joinToString("  ")
        }
        val sets = entry.completedSets.joinToString("  ") { "${it.ourGames}:${it.opponentGames}" }
        val mtb = if (entry.opponentMatchTieBreakPoints > 0 || entry.ourMatchTieBreakPoints > 0) {
            getString(
                R.string.match_tie_break_result,
                entry.ourMatchTieBreakPoints,
                entry.opponentMatchTieBreakPoints
            )
        } else ""
        return listOf(sets, mtb).filter { it.isNotBlank() }.joinToString("  ").ifBlank {
            getString(R.string.sets_result, entry.ourSets, entry.opponentSets)
        }
    }

    private fun formatDuration(entry: PhoneMatchHistoryEntry): String {
        val (hours, minutes) = PhoneMatchHistoryParser.durationHoursAndMinutes(
            entry.startedAtEpochMillis,
            entry.finishedAtEpochMillis
        )
        return if (hours > 0L) {
            getString(R.string.hours_format, hours, minutes)
        } else {
            getString(R.string.minutes_format, minutes)
        }
    }

    private fun formatMatchDate(epochMillis: Long): String {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return dateFormat.format(Date(epochMillis))
    }

    private fun statTile(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(240, 244, 248))
                setStroke(dp(1), Color.rgb(220, 228, 236))
            }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.rgb(91, 101, 111))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 18f
                setTextColor(Color.rgb(1, 40, 83))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }

    private fun accordionSection(
        title: String,
        summary: String,
        populate: (LinearLayout) -> Unit
    ): LinearLayout {
        val section = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(14), dp(16))
            isClickable = true
            isFocusable = true
            foreground = TypedValue().let { value ->
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                getDrawable(value.resourceId)
            }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.rgb(24, 32, 40))
            setTypeface(typeface, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = summary
            textSize = 13.5f
            setTextColor(Color.rgb(91, 101, 111))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val indicator = TextView(this).apply {
            text = "+"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(1, 40, 83))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(indicator, LinearLayout.LayoutParams(dp(40), dp(40)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(18))
            visibility = View.GONE
        }
        populate(content)

        fun updateAccessibility(expanded: Boolean) {
            val stateText = if (expanded) getString(R.string.expanded) else getString(R.string.collapsed)
            header.contentDescription = "$title, $stateText"
        }
        updateAccessibility(false)
        header.setOnClickListener {
            val expand = content.visibility != View.VISIBLE
            content.visibility = if (expand) View.VISIBLE else View.GONE
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
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(226, 230, 234))
        }
    }

    private fun primaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        minHeight = dp(52)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(
            Color.rgb(1, 40, 83)
        )
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        minHeight = dp(52)
        setTextColor(Color.rgb(1, 40, 83))
        backgroundTintList = ColorStateList.valueOf(
            Color.rgb(229, 237, 245)
        )
        setOnClickListener { action() }
    }

    private fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(76, 86, 96))
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
                    Tasks.await(
                        Wearable.getNodeClient(applicationContext)
                            .connectedNodes, 10, TimeUnit.SECONDS
                    ).sortedByDescending { it.isNearby }
                }
                when (nodes.size) {
                    0 -> watchInstallStatus.text = getString(R.string.status_no_watch)
                    1 -> watchInstallStatus.text = openWatchStore(nodes.single().id)
                    else -> {
                        watchInstallStatus.text = getString(R.string.status_choose_watch)
                        watchSelectionDialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.choose_watch_title))
                            .setItems(nodes.map { it.displayName }.toTypedArray()) { _, index ->
                                installOnSelectedWatch(nodes[index].id)
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .create()
                        watchSelectionDialog?.show()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                watchInstallStatus.text = getString(
                    R.string.status_watch_discovery_failed,
                    (e.cause ?: e).javaClass.simpleName
                )
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
        val helper = RemoteActivityHelper(
            applicationContext
        ) { it.run() }
        val links = listOf(
            "market://details?id=$packageName",
            "https://play.google.com/store/apps/details?id=$packageName"
        )
        var failure = "Timeout"
        for (link in links) {
            try {
                val sent = withTimeoutOrNull(15_000L.milliseconds) {
                    helper.startRemoteActivity(
                        Intent(Intent.ACTION_VIEW, link.toUri())
                            .addCategory(Intent.CATEGORY_BROWSABLE), nodeId
                    ).await()
                    true
                } ?: false
                if (sent) return getString(R.string.status_store_request_sent)
                failure = "Timeout"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = (e.cause ?: e).javaClass.simpleName
            }
        }
        return getString(R.string.status_store_request_failed, failure)
    }

    private fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
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
                status.text = if (granted.containsAll(syncPermissions)) {
                    getString(R.string.status_connected_ready)
                } else {
                    getString(R.string.status_hc_available_connect)
                }
            } catch (e: Exception) {
                status.text = getString(
                    R.string.status_connection_failed,
                    e.javaClass.simpleName,
                    e.message ?: ""
                )
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PadelScoreSync", getString(R.string.log_sync_failed), e)
            status.text = getString(
                R.string.status_sync_failed,
                e.javaClass.simpleName,
                e.message ?: ""
            )
        }
    }

    override fun onResume() {
        super.onResume()
        requestSync()
        PhoneMatchHistory.request(applicationContext)
        if (::matchesTabContainer.isInitialized) renderMatchesSection()
    }

    override fun onDestroy() {
        watchSelectionDialog?.dismiss()
        watchSelectionDialog = null
        scope.cancel()
        super.onDestroy()
    }
}
