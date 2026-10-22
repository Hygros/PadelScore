package ch.hygro.padelscore.presentation

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.logic.PadelScoreEngine
import ch.hygro.padelscore.logic.ServeCalculator
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.storage.MatchHistoryRepository
import ch.hygro.padelscore.storage.MatchRepository
import ch.hygro.padelscore.vibration.HapticFeedbackManager
import ch.hygro.padelscore.presentation.theme.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PadelScoreContent(
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
    val isAmbientMode = ambientModeManager.currentAmbientMode is AmbientMode.Ambient

    val matchRepository = remember(context) {
        MatchRepository(context.applicationContext)
    }
    val matchHistoryRepository = remember(context) {
        MatchHistoryRepository(context.applicationContext)
    }
    val settingsRepository = remember(context) {
        ch.hygro.padelscore.storage.SettingsRepository(context.applicationContext)
    }
    val initialSnapshot = remember { matchRepository.loadSnapshot() }
    val initialState = initialSnapshot.currentState

    var matchState by remember { mutableStateOf(initialState) }
    var showResumePrompt by remember {
        mutableStateOf(hasRunningSavedMatch(initialState))
    }
    var showConfiguration by remember {
        mutableStateOf(
            !hasRunningSavedMatch(initialState) && !matchHasStarted(initialState)
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
    var showSettings by remember { mutableStateOf(false) }
    var draftConfiguration by remember { mutableStateOf(initialState.configuration) }

    val scope = rememberCoroutineScope()
    val bodySensorPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                ch.hygro.padelscore.health.FitnessTrackingService.start(context, matchState.startedAtEpochMillis)
            } else {
                Log.d("FitnessTracker", "Body sensor permissions not granted")
            }
        }
    )

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

        if (previousState.phase != MatchPhase.MATCH_FINISHED &&
            newState.phase == MatchPhase.MATCH_FINISHED
        ) {
            val completedState = newState.copy(averageHeartRate = ch.hygro.padelscore.health.FitnessTrackingService.averageHeartRate)
            matchHistoryRepository.saveCompletedMatch(completedState, completedState.averageHeartRate)
        }
        updateMatch(
            if (newState.phase == MatchPhase.MATCH_FINISHED &&
                previousState.phase != MatchPhase.MATCH_FINISHED
            ) newState.copy(averageHeartRate = ch.hygro.padelscore.health.FitnessTrackingService.averageHeartRate) else newState
        )
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
        ch.hygro.padelscore.health.FitnessTrackingService.discard(context)
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
            !showMatchHistory && !showSupporter && !showSettings && !matchFinished
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
                showSettings = false
            }
            onResumeFromOngoingHandled()
        }
    }
    LaunchedEffect(showConfiguration, showResumePrompt, showMatchHistory, showSupporter, showSettings) {
        if (showConfiguration && !showResumePrompt && !showMatchHistory && !showSupporter && !showSettings) {
            (activity as? MainActivity)?.requestNotificationsOnFirstStartScreen()
        }
    }
    // A saved match remains ongoing even when the resume prompt is visible.
    val hasOngoingMatch = !showConfiguration && !matchFinished &&
            (isActiveMatch || hasRunningSavedMatch(matchState))
    LaunchedEffect(hasOngoingMatch, matchState) {
        (activity as? MainActivity)?.updateOngoingMatch(hasOngoingMatch, matchState)
    }
    
    val swipeGesturesEnabled by settingsRepository.swipeGesturesEnabled.collectAsState()
    val healthTrackingEnabled by settingsRepository.healthTrackingEnabled.collectAsState()
    val isMatchRunning = !showConfiguration && !showResumePrompt && !matchFinished && matchState.startedAtEpochMillis > 0L
    LaunchedEffect(isMatchRunning, healthTrackingEnabled) {
        if (isMatchRunning && healthTrackingEnabled) {
            val requiredPermissions = listOf(
                if (android.os.Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else android.Manifest.permission.BODY_SENSORS,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            )
            val missingPermissions = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isEmpty()) {
                ch.hygro.padelscore.health.FitnessTrackingService.start(context, matchState.startedAtEpochMillis)
            } else {
                bodySensorPermissionsLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            ch.hygro.padelscore.health.FitnessTrackingService.stop(context)
        }
    }

    // Do not cancel the ongoing notification in onPause or effect disposal.
    DisposableEffect(activity, isActiveMatch) {
        (activity as? MainActivity)?.setMatchQuietMode(isActiveMatch)
        onDispose { (activity as? MainActivity)?.setMatchQuietMode(false) }
    }
    var ambientClock by remember {
        mutableStateOf(
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
        )
    }
    ambientModeManager.AmbientTickEffect {
        ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    LaunchedEffect(isReducedDisplay, isAmbientMode) {
        while (isReducedDisplay && !isAmbientMode) {
            ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            delay(1.minutes)
        }
    }
    LaunchedEffect(isAmbientMode) {
        ambientClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        Log.d("PadelInput", "ambient=$isAmbientMode activeMatch=$isActiveMatch")
    }

    if (showResumePrompt) {
        ResumeMatchScreen(
            matchState = matchState,
            onContinueMatch = { showResumePrompt = false },
            onDiscardMatch = {
                ch.hygro.padelscore.health.FitnessTrackingService.discard(context)
                val previousConfiguration = matchState.configuration
                history.clear()
                lastAcceptedPointAtMillis = 0L
                updateMatch(MatchState(configuration = previousConfiguration))
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
            onPurchase = { activity?.let(onLaunchSupporterPurchase) },
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

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            repository = settingsRepository,
            onBack = { showSettings = false }
        )
        return
    }

    if (showConfiguration) {
        StartScreen(
            initialConfiguration = draftConfiguration,
            onConfigurationChanged = { draftConfiguration = it },
            isSupporterActive = supporterBillingState.isSupporterActive,
            onStartMatch = { configuration ->
                draftConfiguration = configuration
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
            },
            onOpenSettings = {
                showSettings = true
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
            onContinueMatch = { showExitConfirmation = false },
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
                    matchHistoryRepository.removeByMatchId(matchState.startedAtEpochMillis, deleteHealthData = false)
                    lastAcceptedPointAtMillis = 0L
                    updateMatch(history.removeAt(history.lastIndex))
                    hapticFeedbackManager.undo()
                }
            },
            onNewMatch = {
                val previousConfiguration = matchState.configuration
                history.clear()
                lastAcceptedPointAtMillis = 0L
                updateMatch(MatchState(configuration = previousConfiguration))
                showResumePrompt = false
                showConfiguration = true
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).let {
            if (swipeGesturesEnabled && !matchFinished) {
                it.pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag < -50f) {
                                addPoint(Team.OPPONENT)
                            } else if (totalDrag > 50f) {
                                addPoint(Team.US)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    )
                }
            } else {
                it
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isDeuceCourt = ServeCalculator.isDeuceCourtServe(matchState.pointsInCurrentGame)

        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pointText = pointText(matchState, Team.OPPONENT),
            games = matchState.opponentGames,
            sets = matchState.opponentSets,
            backgroundColor = ColorTeamOpponent,
            isServing = matchState.servingTeam == Team.OPPONENT,
            isDeuceCourt = isDeuceCourt,
            statsAtTop = true,
            enabled = !matchFinished,
            swipeGesturesEnabled = swipeGesturesEnabled,
            ambient = isReducedDisplay,
            onClick = { addPoint(Team.OPPONENT) }
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
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
            backgroundColor = ColorTeamUs,
            isServing = matchState.servingTeam == Team.US,
            isDeuceCourt = isDeuceCourt,
            statsAtTop = false,
            enabled = !matchFinished,
            swipeGesturesEnabled = swipeGesturesEnabled,
            ambient = isReducedDisplay,
            onClick = { addPoint(Team.US) }
        )
    }
}

@Composable
fun ScoreArea(
    modifier: Modifier,
    pointText: String,
    games: Int,
    sets: Int,
    backgroundColor: Color,
    isServing: Boolean,
    isDeuceCourt: Boolean,
    statsAtTop: Boolean,
    enabled: Boolean,
    swipeGesturesEnabled: Boolean = false,
    ambient: Boolean,
    onClick: () -> Unit
) {
    val isRound = LocalConfiguration.current.isScreenRound
    val displayedBackgroundColor = when {
        ambient -> Color.Black
        enabled -> backgroundColor
        else -> backgroundColor.copy(alpha = 0.58f)
    }
    val shape = if (statsAtTop) {
        RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    }
    val scoreFontSize = if (ambient) 35.sp else 55.sp

    Box(
        modifier = modifier
            .background(color = displayedBackgroundColor, shape = shape)
            .clickable(enabled = enabled && !swipeGesturesEnabled, onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top button: statistics above the score.
            if (statsAtTop) {
                Spacer(modifier = Modifier.height(8.dp))
                ScoreStatistics(
                    games = games,
                    sets = sets,
                    ambient = ambient,
                    isRound = isRound
                )
            }

            // Center the score in the remaining space between the
            // statistics row and the inner edge of the button.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pointText,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.70f),
                    style = TextStyle(
                        fontSize = scoreFontSize,
                        lineHeight = scoreFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Bottom button: statistics below the score.
            if (!statsAtTop) {
                ScoreStatistics(
                    games = games,
                    sets = sets,
                    ambient = ambient,
                    isRound = isRound
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Keep the serve indicator independent of the score layout.
        if (isServing) {
            val alignment = if (isDeuceCourt) {
                if (statsAtTop) Alignment.BottomEnd else Alignment.TopEnd
            } else {
                if (statsAtTop) Alignment.BottomStart else Alignment.TopStart
            }
            Box(
                modifier = Modifier.align(alignment)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .width(12.dp)
                    .height(12.dp)
                    .background(
                        color = if (ambient) Color.White else ColorServeIndicator, 
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun ScoreStatistics(
    games: Int,
    sets: Int,
    ambient: Boolean = false,
    isRound: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.games_with_count, games),
            color = if (ambient) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = if (ambient) 9.sp else if (isRound) 10.sp else 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.sets_with_count, sets),
            color = if (ambient) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = if (ambient) 9.sp else if (isRound) 10.sp else 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun CenterScoreArea(
    modifier: Modifier,
    matchState: MatchState,
    showCourtChange: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isResetHolding by remember { mutableStateOf(false) }
    var resetPerformed by remember { mutableStateOf(false) }
    
    // Track hold duration to animate the progress indicator
    var holdDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isResetHolding) {
        if (isResetHolding) {
            resetPerformed = false
            holdDuration = 0L
            val startTime = SystemClock.elapsedRealtime()
            while (holdDuration < RESET_HOLD_DURATION_MILLIS && isResetHolding) {
                delay(16.milliseconds) // ~60fps update rate
                holdDuration = SystemClock.elapsedRealtime() - startTime
            }
            if (isResetHolding && holdDuration >= RESET_HOLD_DURATION_MILLIS) {
                resetPerformed = true
                onReset()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                isResetHolding = false
            }
        } else {
            holdDuration = 0L
        }
    }

    Row(
        modifier = modifier.padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(46.dp).height(40.dp)
                .background(
                    color = if (canUndo) ColorUndoButtonBgEnabled else ColorUndoButtonBgDisabled,
                    shape = CircleShape
                )
                .clickable(enabled = canUndo, onClick = onUndo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = stringResource(R.string.undo),
                tint = if (canUndo) Color.White else Color.DarkGray,
                modifier = Modifier.padding(10.dp)
            )
        }

        Column(
            modifier = Modifier.width(82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerLabel(matchState, showCourtChange),
                color = if (showCourtChange) ColorCourtChange else centerLabelColor(matchState),
                fontSize = centerLabelFontSize(matchState),
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = if (matchHasStarted(matchState)) {
                    "${matchState.ourGames} : ${matchState.opponentGames}"
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
            modifier = Modifier.width(46.dp).height(40.dp)
                .background(
                    color = ColorResetButtonBg,
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
            if (isResetHolding) {
                CircularProgressIndicator(
                    progress = { (holdDuration.toFloat() / RESET_HOLD_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = Color.White,
                        trackColor = ColorResetButtonBgHolding
                    ),
                    strokeWidth = 3.dp
                )
            }
            Text(
                text = stringResource(R.string.reset),
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
            if (scoringTeam == Team.US) manager.pointUs() else manager.pointOpponent()
        }
    }
}