package ch.hygro.padelscore.presentation

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.billing.SupporterBillingManager
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.logic.PadelScoreEngine
import ch.hygro.padelscore.logic.ServeCalculator
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme
import ch.hygro.padelscore.storage.MatchHistoryRepository
import ch.hygro.padelscore.storage.MatchRepository
import ch.hygro.padelscore.vibration.HapticFeedbackManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private lateinit var supporterBillingManager: SupporterBillingManager
    private val supporterBillingState =
        mutableStateOf(SupporterBillingState())

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
            this, "Allow notifications in app settings to keep the match available on your watch.",
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

@Composable
private fun PadelScoreContent(
    supporterBillingState: SupporterBillingState,
    resumeFromOngoing: Boolean,
    onResumeFromOngoingHandled: () -> Unit,
    onRefreshSupporterPurchases: () -> Unit,
    onLaunchSupporterPurchase: (Activity) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val hapticFeedbackManager = remember(view) {
        HapticFeedbackManager(view)
    }
    val ambientModeManager = checkNotNull(LocalAmbientModeManager.current)
    val isAmbientMode =
        ambientModeManager.currentAmbientMode is AmbientMode.Ambient

    val matchRepository = remember(context) {
        MatchRepository(context.applicationContext)
    }
    val matchHistoryRepository = remember(context) {
        MatchHistoryRepository(context.applicationContext)
    }
    val initialSnapshot = remember { matchRepository.loadSnapshot() }
    val initialState = initialSnapshot.currentState

    var matchState by remember { mutableStateOf(initialState) }
    var showResumePrompt by remember {
        mutableStateOf(hasRunningSavedMatch(initialState))
    }
    var showConfiguration by remember {
        mutableStateOf(
            !hasRunningSavedMatch(initialState) &&
                    !matchHasStarted(initialState)
        )
    }
    val history = remember {
        mutableStateListOf<MatchState>().apply {
            addAll(initialSnapshot.undoHistory)
        }
    }
    var lastAcceptedPointAtMillis by remember { mutableLongStateOf(0L) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showMatchHistory by remember { mutableStateOf(false) }
    var showCourtChange by remember { mutableStateOf(false) }
    var matchHistoryEntries by remember {
        mutableStateOf(matchHistoryRepository.loadEntries())
    }
    var showSupporter by remember { mutableStateOf(false) }

    fun updateMatch(newState: MatchState) {
        matchState = newState
        matchRepository.saveSnapshot(
            currentState = newState,
            undoHistory = history
        )
    }

    val matchFinished = PadelScoreEngine.isMatchFinished(matchState)

    fun saveCurrentStateToHistory() {
        history.add(matchState)
        if (history.size > MAX_HISTORY_SIZE) history.removeAt(0)
    }

    fun addPoint(team: Team) {
        if (PadelScoreEngine.isMatchFinished(matchState)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastAcceptedPointAtMillis < POINT_INPUT_LOCK_MILLIS) {
            Log.d("PadelInput", "point rejected: debounce team=$team")
            return
        }
        Log.d("PadelInput", "point accepted team=$team ambient=$isAmbientMode")

        lastAcceptedPointAtMillis = now
        saveCurrentStateToHistory()

        val previousState = matchState
        val newState = PadelScoreEngine.awardPoint(previousState, team)
        val courtChange = ServeCalculator.isCourtChange(previousState, newState)
        showCourtChange = courtChange

        if (
            previousState.phase != MatchPhase.MATCH_FINISHED &&
            newState.phase == MatchPhase.MATCH_FINISHED
        ) {
            matchHistoryRepository.saveCompletedMatch(newState)
        }
        updateMatch(newState)
        performScoreHapticFeedback(
            previousState = previousState,
            newState = newState,
            manager = hapticFeedbackManager,
            scoringTeam = team,
            isCourtChange = courtChange
        )
    }

    fun undoLastPoint() {
        if (history.isNotEmpty()) {
            lastAcceptedPointAtMillis = 0L
            showCourtChange = false
            updateMatch(history.removeAt(history.lastIndex))
            hapticFeedbackManager.undo()
        }
    }

    fun resetMatch() {
        val currentConfiguration = matchState.configuration
        lastAcceptedPointAtMillis = 0L
        showCourtChange = false
        history.clear()
        updateMatch(MatchState(configuration = currentConfiguration))
        showExitConfirmation = false
        showResumePrompt = false
        showConfiguration = true
    }

    val isActiveMatch = !showConfiguration && !showResumePrompt &&
        !showMatchHistory && !showSupporter && !matchFinished
    val isReducedDisplay = isAmbientMode ||
        (isActiveMatch && (activity as? MainActivity)?.wristLowered?.value == true)
    DisposableEffect(activity, isActiveMatch, isAmbientMode) {
        (activity as? MainActivity)?.setWristDetectionEnabled(isActiveMatch && !isAmbientMode)
        onDispose { (activity as? MainActivity)?.setWristDetectionEnabled(false) }
    }
    LaunchedEffect(resumeFromOngoing) {
        if (resumeFromOngoing) {
            if (hasRunningSavedMatch(matchState)) {
                showResumePrompt = false
                showConfiguration = false
                showMatchHistory = false
                showSupporter = false
            }
            onResumeFromOngoingHandled()
        }
    }
    LaunchedEffect(showConfiguration, showResumePrompt, showMatchHistory, showSupporter) {
        if (showConfiguration && !showResumePrompt && !showMatchHistory && !showSupporter) {
            (activity as? MainActivity)?.requestNotificationsOnFirstStartScreen()
        }
    }
    // A saved match remains ongoing even when the resume prompt is visible.
    val hasOngoingMatch = !showConfiguration && !matchFinished &&
        (isActiveMatch || hasRunningSavedMatch(matchState))
    LaunchedEffect(hasOngoingMatch, matchState) {
        (activity as? MainActivity)?.updateOngoingMatch(hasOngoingMatch, matchState)
    }
    // Do not cancel the ongoing notification in onPause or effect disposal.
    DisposableEffect(activity, isActiveMatch) {
        (activity as? MainActivity)?.setMatchQuietMode(isActiveMatch)
        onDispose { (activity as? MainActivity)?.setMatchQuietMode(false) }
    }
    var ambientClock by remember {
        mutableStateOf(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
    }
    ambientModeManager.AmbientTickEffect {
        ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }
    LaunchedEffect(isReducedDisplay, isAmbientMode) {
        while (isReducedDisplay && !isAmbientMode) {
            ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            delay(1.minutes)
        }
    }
    LaunchedEffect(isAmbientMode) {
        ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        Log.d("PadelInput", "ambient=$isAmbientMode activeMatch=$isActiveMatch")
    }

    if (showResumePrompt) {
        ResumeMatchScreen(
            matchState = matchState,
            onContinueMatch = {
                showResumePrompt = false
            },
            onDiscardMatch = {
                val previousConfiguration = matchState.configuration
                history.clear()
                lastAcceptedPointAtMillis = 0L
                updateMatch(
                    MatchState(configuration = previousConfiguration)
                )
                showResumePrompt = false
                showConfiguration = true
            }
        )
        return
    }

    if (showSupporter) {
        BackHandler { showSupporter = false }
        SupporterScreen(
            state = supporterBillingState,
            onPurchase = {
                activity?.let(onLaunchSupporterPurchase)
            },
            onRestorePurchases = onRefreshSupporterPurchases,
            onBack = { showSupporter = false }
        )
        return
    }

    if (showMatchHistory) {
        BackHandler { showMatchHistory = false }
        MatchHistoryScreen(
            entries = matchHistoryEntries,
            onDeleteMatch = { matchId ->
                matchHistoryRepository.removeByMatchId(matchId)
                matchHistoryEntries = matchHistoryRepository.loadEntries()
            },
            onBack = { showMatchHistory = false }
        )
        return
    }

    if (showConfiguration) {
        StartScreen(
            initialConfiguration = matchState.configuration,
            isSupporterActive = supporterBillingState.isSupporterActive,
            onStartMatch = { configuration ->
                lastAcceptedPointAtMillis = 0L
                history.clear()
                updateMatch(
                    MatchState(
                        configuration = configuration,
                        servingTeam = configuration.firstServingTeam,
                        startedAtEpochMillis = System.currentTimeMillis()
                    )
                )
                showConfiguration = false
            },
            onOpenHistory = {
                matchHistoryEntries = matchHistoryRepository.loadEntries()
                showMatchHistory = true
            },
            onOpenSupporter = {
                showSupporter = true
                onRefreshSupporterPurchases()
            }
        )
        return
    }

    BackHandler(enabled = !showExitConfirmation) {
        showExitConfirmation = true
    }

    BackHandler(enabled = showExitConfirmation) {
        showExitConfirmation = false
    }

    if (showExitConfirmation) {
        ExitMatchScreen(
            onContinueMatch = {
                showExitConfirmation = false
            },
            onBackToHome = { resetMatch() }
        )
        return
    }

    if (matchState.phase == MatchPhase.MATCH_FINISHED) {
        MatchFinishedScreen(
            matchState = matchState,
            canUndo = history.isNotEmpty(),
            onUndo = {
                if (history.isNotEmpty()) {
                    matchHistoryRepository.removeByMatchId(matchState.startedAtEpochMillis)
                    lastAcceptedPointAtMillis = 0L
                    updateMatch(history.removeAt(history.lastIndex))
                    hapticFeedbackManager.undo()
                }
            },
            onNewMatch = {
                val previousConfiguration = matchState.configuration
                history.clear()
                lastAcceptedPointAtMillis = 0L
                updateMatch(
                    MatchState(configuration = previousConfiguration)
                )
                showResumePrompt = false
                showConfiguration = true
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isDeuceCourt = ServeCalculator.isDeuceCourtServe(matchState.pointsInCurrentGame)

        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pointText = pointText(matchState, Team.OPPONENT),
            games = matchState.opponentGames,
            sets = matchState.opponentSets,
            backgroundColor = Color(0xFF8B2525),
            isServing = matchState.servingTeam == Team.OPPONENT,
            isDeuceCourt = isDeuceCourt,
            statsAtTop = true,
            enabled = !matchFinished,
            ambient = isReducedDisplay,
            onClick = { addPoint(Team.OPPONENT) }
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            CenterScoreArea(
                modifier = Modifier.fillMaxSize().alpha(if (isReducedDisplay) 0f else 1f),
                matchState = matchState,
                showCourtChange = showCourtChange,
                canUndo = !isReducedDisplay && history.isNotEmpty(),
                onUndo = { if (!isReducedDisplay) undoLastPoint() },
                onReset = { if (!isReducedDisplay) resetMatch() }
            )
            if (isReducedDisplay) {
                Text(text = ambientClock, color = Color.White, fontSize = 12.sp)
            }
        }

        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pointText = pointText(matchState, Team.US),
            games = matchState.ourGames,
            sets = matchState.ourSets,
            backgroundColor = Color(0xFF195F3B),
            isServing = matchState.servingTeam == Team.US,
            isDeuceCourt = isDeuceCourt,
            statsAtTop = false,
            enabled = !matchFinished,
            ambient = isReducedDisplay,
            onClick = { addPoint(Team.US) }
        )
    }
}

@Composable
private fun ScoreArea(
    modifier: Modifier,
    pointText: String,
    games: Int,
    sets: Int,
    backgroundColor: Color,
    isServing: Boolean,
    isDeuceCourt: Boolean,
    statsAtTop: Boolean,
    enabled: Boolean,
    ambient: Boolean,
    onClick: () -> Unit
) {
    val displayedBackgroundColor = if (ambient) {
        Color.Black
    } else if (enabled) {
        backgroundColor
    } else {
        backgroundColor.copy(alpha = 0.58f)
    }

    Column(
        modifier = modifier
            .background(
                color = displayedBackgroundColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (statsAtTop) {
            ScoreStatistics(
                games = games,
                sets = sets,
                ambient = ambient
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pointText,
                color = if (enabled) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.70f)
                },
                fontSize = if (ambient) 30.sp else 45.sp,
                lineHeight = if (ambient) 30.sp else 45.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            if (isServing) {
                val alignment = if (isDeuceCourt) {
                    if (statsAtTop) Alignment.BottomEnd else Alignment.TopEnd
                } else {
                    if (statsAtTop) Alignment.BottomStart else Alignment.TopStart
                }
                Text(
                    text = "●",
                    color = if (ambient) Color.White else Color(0xFFFFD54F),
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(alignment)
                )
            }
        }

        if (!statsAtTop) {
            ScoreStatistics(
                games = games,
                sets = sets,
                ambient = ambient
            )
        }
    }
}

@Composable
private fun ScoreStatistics(
    games: Int,
    sets: Int,
    ambient: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.games_with_count, games),
            color = if (ambient) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = if (ambient) 9.sp else 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(R.string.sets_with_count, sets),
            color = if (ambient) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = if (ambient) 9.sp else 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun CenterScoreArea(
    modifier: Modifier,
    matchState: MatchState,
    showCourtChange: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    var isResetHolding by remember {
        mutableStateOf(false)
    }

    var resetPerformed by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isResetHolding) {
        if (isResetHolding) {
            resetPerformed = false
            delay(RESET_HOLD_DURATION_MILLIS.milliseconds)

            if (isResetHolding) {
                resetPerformed = true
                onReset()
                hapticFeedback.performHapticFeedback(
                    HapticFeedbackType.LongPress
                )
                isResetHolding = false
            }
        }
    }

    Row(
        modifier = modifier.padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(40.dp)
                .background(
                    color = if (canUndo) Color(0xFF3A3A3A) else Color(0xFF1B1B1B),
                    shape = CircleShape
                )
                .clickable(enabled = canUndo, onClick = onUndo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶",
                color = if (canUndo) Color.White else Color.DarkGray,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.width(82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerLabel(matchState, showCourtChange),
                color = if (showCourtChange) Color(0xFFFF9800) else centerLabelColor(matchState),
                fontSize = centerLabelFontSize(matchState),
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Text(
                text = if (matchHasStarted(matchState)) {
                    "${matchState.opponentGames} : ${matchState.ourGames}"
                } else {
                    scoringModeLabel(matchState.configuration.scoringMode)
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .width(46.dp)
                .height(40.dp)
                .background(
                    color = if (isResetHolding) {
                        Color(0xFFB3261E)
                    } else {
                        Color(0xFF3A3A3A)
                    },
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            resetPerformed = false
                            isResetHolding = true
                            tryAwaitRelease()

                            if (!resetPerformed) {
                                isResetHolding = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isResetHolding) stringResource(R.string.hold_to_reset) else stringResource(R.string.reset),
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun performScoreHapticFeedback(
    previousState: MatchState,
    newState: MatchState,
    manager: HapticFeedbackManager,
    scoringTeam: Team,
    isCourtChange: Boolean
) {
    if (isCourtChange) {
        manager.courtChange()
        return
    }

    when {
        newState.phase == MatchPhase.MATCH_FINISHED &&
                previousState.phase != MatchPhase.MATCH_FINISHED -> {
            manager.matchFinished()
        }

        newState.opponentSets != previousState.opponentSets ||
                newState.ourSets != previousState.ourSets -> {
            manager.setWon()
        }

        newState.totalGamesPlayed > previousState.totalGamesPlayed -> {
            manager.gameWon()
        }

        newState.totalPointsPlayed > previousState.totalPointsPlayed -> {
            if (scoringTeam == Team.US) {
                manager.pointUs()
            } else {
                manager.pointOpponent()
            }
        }
    }
}

@Composable
private fun centerLabel(state: MatchState, showCourtChange: Boolean): String {
    if (showCourtChange) return stringResource(R.string.change_sides)
    return when {
        state.phase == MatchPhase.MATCH_FINISHED &&
                state.ourSets > state.opponentSets -> stringResource(R.string.match_won)

        state.phase == MatchPhase.MATCH_FINISHED &&
                state.opponentSets > state.ourSets -> stringResource(R.string.match_lost)

        state.phase == MatchPhase.MATCH_TIE_BREAK -> stringResource(R.string.match_tie_break)
        state.isTieBreak -> stringResource(R.string.tie_break)
        state.isStarPointActive -> stringResource(R.string.star_point)
        state.isGoldenPointActive -> stringResource(R.string.golden_point)
        state.advantageTeam == Team.OPPONENT -> stringResource(R.string.advantage_top)
        state.advantageTeam == Team.US -> stringResource(R.string.advantage_bottom)
        state.opponentPoints == 3 && state.ourPoints == 3 -> deuceLabel(state)
        else -> stringResource(R.string.games)
    }
}

@Composable
private fun deuceLabel(state: MatchState): String {
    return when (state.configuration.scoringMode) {
        ScoringMode.STAR_POINT -> stringResource(R.string.deuce_with_count, state.deuceReturnCount + 1)
        ScoringMode.CLASSIC_ADVANTAGE,
        ScoringMode.GOLDEN_POINT -> stringResource(R.string.deuce)
    }
}

private fun centerLabelColor(state: MatchState): Color {
    return when {
        state.phase == MatchPhase.MATCH_FINISHED &&
                state.ourSets > state.opponentSets -> Color(0xFF66E59A)

        state.phase == MatchPhase.MATCH_FINISHED &&
                state.opponentSets > state.ourSets -> Color(0xFFFF7B72)

        state.isStarPointActive -> Color(0xFFFF9800)
        state.isGoldenPointActive -> Color(0xFFFFD54F)
        state.advantageTeam != null -> Color.White
        state.opponentPoints == 3 && state.ourPoints == 3 -> Color.LightGray
        else -> Color.LightGray
    }
}

private fun centerLabelFontSize(state: MatchState) =
    if (PadelScoreEngine.isMatchFinished(state)) 8.sp else 9.sp

@Composable
private fun pointText(
    state: MatchState,
    team: Team
): String {
    if (state.phase == MatchPhase.MATCH_TIE_BREAK) {
        return when (team) {
            Team.OPPONENT -> state.opponentMatchTieBreakPoints.toString()
            Team.US -> state.ourMatchTieBreakPoints.toString()
        }
    }

    if (state.isTieBreak) {
        return when (team) {
            Team.OPPONENT -> state.opponentTieBreakPoints.toString()
            Team.US -> state.ourTieBreakPoints.toString()
        }
    }

    if (state.advantageTeam == team) {
        return stringResource(R.string.advantage_abbr)
    }

    val normalPoints = when (team) {
        Team.OPPONENT -> state.opponentPoints
        Team.US -> state.ourPoints
    }

    return when (normalPoints) {
        0 -> "0"
        1 -> "15"
        2 -> "30"
        else -> "40"
    }
}

@Composable
private fun scoringModeLabel(scoringMode: ScoringMode): String {
    return when (scoringMode) {
        ScoringMode.CLASSIC_ADVANTAGE -> stringResource(R.string.advantage)
        ScoringMode.GOLDEN_POINT -> stringResource(R.string.golden_abbr)
        ScoringMode.STAR_POINT -> stringResource(R.string.star_abbr)
    }
}

private fun hasRunningSavedMatch(state: MatchState): Boolean {
    return state.phase != MatchPhase.MATCH_FINISHED &&
            (
                    state.startedAtEpochMillis > 0L ||
                            matchHasStarted(state)
                    )
}

private fun matchHasStarted(state: MatchState): Boolean {
    return state.opponentPoints != 0 ||
            state.ourPoints != 0 ||
            state.opponentGames != 0 ||
            state.ourGames != 0 ||
            state.opponentSets != 0 ||
            state.ourSets != 0 ||
            state.isTieBreak ||
            state.opponentTieBreakPoints != 0 ||
            state.ourTieBreakPoints != 0 ||
            state.opponentMatchTieBreakPoints != 0 ||
            state.ourMatchTieBreakPoints != 0 ||
            state.phase == MatchPhase.MATCH_TIE_BREAK ||
            state.totalPointsPlayed != 0 ||
            state.totalGamesPlayed != 0 ||
            state.phase == MatchPhase.MATCH_FINISHED
}

private const val MAX_HISTORY_SIZE = 100
private const val RESET_HOLD_DURATION_MILLIS = 1500L
private const val POINT_INPUT_LOCK_MILLIS = 300L
