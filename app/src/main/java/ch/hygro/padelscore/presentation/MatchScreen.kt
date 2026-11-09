package ch.hygro.padelscore.presentation

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.health.FitnessTrackingService
import ch.hygro.padelscore.logic.PadelScoreEngine
import ch.hygro.padelscore.logic.ServeCalculator
import ch.hygro.padelscore.minigame.PadelPongScreen
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorTeamOpponent
import ch.hygro.padelscore.presentation.theme.ColorTeamUs
import ch.hygro.padelscore.storage.MatchHistoryRepository
import ch.hygro.padelscore.storage.MatchRepository
import ch.hygro.padelscore.storage.SettingsRepository
import ch.hygro.padelscore.vibration.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

/**
 * Root composable of the watch UI. It owns the match state and the undo history and decides
 * which screen is shown.
 *
 * Screens are selected by the `show*` flags through early returns further down. The order of
 * those checks matters because several flags can be true at once (for example
 * `showConfiguration` together with `showPadelPong`).
 */
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
        SettingsRepository(context.applicationContext)
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
        mutableStateOf<List<MatchHistoryEntry>>(emptyList())
    }
    var showSupporter by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPadelPong by remember { mutableStateOf(false) }
    var draftConfiguration by remember { mutableStateOf(initialState.configuration) }
    val bodySensorPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions: Map<String, Boolean> ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                FitnessTrackingService.start(context, matchState.startedAtEpochMillis)
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

    val saveMatchFailedMessage = stringResource(R.string.save_match_failed)
    val deleteMatchFailedMessage = stringResource(R.string.delete_match_failed)

    fun addPoint(team: Team) {
        if (PadelScoreEngine.isMatchFinished(matchState)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastAcceptedPointAtMillis < POINT_INPUT_LOCK_MILLIS) {
            Log.d("PadelInput", "point rejected: debounce team=$team")
            return
        }
        Log.d("PadelInput", "point accepted team=$team ambient=$isAmbientMode")

        val previousState = matchState
        val newState = PadelScoreEngine.awardPoint(previousState, team)
        val courtChange = ServeCalculator.isCourtChange(previousState, newState)

        val matchJustFinished = previousState.phase != MatchPhase.MATCH_FINISHED &&
            newState.phase == MatchPhase.MATCH_FINISHED
        if (matchJustFinished) {
            // The average heart rate is read from the service again for the stored match state
            // below; the two reads are deliberately kept separate.
            val completedState = newState.copy(averageHeartRate = FitnessTrackingService.averageHeartRate)
            try {
                matchHistoryRepository.saveCompletedMatch(completedState, completedState.averageHeartRate)
            } catch (exception: Exception) {
                Log.e("PadelScore", "Could not save completed match", exception)
                Toast.makeText(
                    context,
                    saveMatchFailedMessage,
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        lastAcceptedPointAtMillis = now
        saveCurrentStateToHistory()
        showCourtChange = courtChange
        updateMatch(
            if (matchJustFinished) {
                newState.copy(averageHeartRate = FitnessTrackingService.averageHeartRate)
            } else {
                newState
            }
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

    fun endAndSaveMatch() {
        if (PadelScoreEngine.isMatchFinished(matchState) ||
            matchState.startedAtEpochMillis <= 0L
        ) return

        val stoppedState = matchState.copy(
            finishedAtEpochMillis = System.currentTimeMillis(),
            averageHeartRate = FitnessTrackingService.averageHeartRate
        )

        try {
            matchHistoryRepository.saveCompletedMatch(
                state = stoppedState,
                averageHeartRate = stoppedState.averageHeartRate,
                endedEarly = true
            )
        } catch (exception: Exception) {
            Log.e("PadelScore", "Could not save ended match", exception)
            Toast.makeText(
                context,
                saveMatchFailedMessage,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        FitnessTrackingService.stop(context)
        history.clear()
        lastAcceptedPointAtMillis = 0L
        showCourtChange = false
        updateMatch(MatchState(configuration = stoppedState.configuration))
        showExitConfirmation = false
        showResumePrompt = false
        showConfiguration = true
    }

    fun resetMatch() {
        FitnessTrackingService.discard(context)
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
            !showMatchHistory && !showSupporter && !showSettings && !showPadelPong && !matchFinished
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
    LaunchedEffect(matchHistoryRepository, showMatchHistory) {
        matchHistoryEntries = withContext(Dispatchers.IO) {
            matchHistoryRepository.loadEntries()
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
            val missingPermissions = missingFitnessPermissions(context)
            if (missingPermissions.isEmpty()) {
                FitnessTrackingService.start(context, matchState.startedAtEpochMillis)
            } else {
                bodySensorPermissionsLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            FitnessTrackingService.stop(context)
        }
    }

    // Do not cancel the ongoing notification in onPause or effect disposal.
    DisposableEffect(activity, isActiveMatch) {
        (activity as? MainActivity)?.setMatchQuietMode(isActiveMatch)
        onDispose { (activity as? MainActivity)?.setMatchQuietMode(false) }
    }
    var ambientClock by remember { mutableStateOf(formatClock(context)) }
    ambientModeManager.AmbientTickEffect {
        ambientClock = formatClock(context)
    }
    // Keep the clock current in interactive mode, aligned to full minutes.
    LaunchedEffect(isAmbientMode) {
        Log.d("PadelInput", "ambient=$isAmbientMode activeMatch=$isActiveMatch")
        ambientClock = formatClock(context)
        while (!isAmbientMode) {
            delay((60_000L - System.currentTimeMillis() % 60_000L).milliseconds)
            ambientClock = formatClock(context)
        }
    }

    if (showResumePrompt) {
        ResumeMatchScreen(
            matchState = matchState,
            onContinueMatch = { showResumePrompt = false },
            onDiscardMatch = {
                FitnessTrackingService.discard(context)
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
                try {
                    matchHistoryRepository.removeByMatchId(matchId)
                } catch (exception: Exception) {
                    Log.e("PadelScore", "Could not delete match", exception)
                    Toast.makeText(
                        context,
                        deleteMatchFailedMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
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

    if (showPadelPong && showConfiguration) {
        BackHandler { showPadelPong = false }
        PadelPongScreen(onExit = { showPadelPong = false })
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
                showMatchHistory = true
            },
            onOpenSupporter = {
                showSupporter = true
                onRefreshSupporterPurchases()
            },
            onOpenSettings = {
                showSettings = true
            },
            onOpenPadelPong = {
                if (!hasRunningSavedMatch(matchState)) showPadelPong = true
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
            onBackToHome = { resetMatch() },
            onEndAndSave = if (
                !matchFinished && matchState.startedAtEpochMillis > 0L
            ) {
                { endAndSaveMatch() }
            } else {
                null
            }
        )
        return
    }

    if (matchState.phase == MatchPhase.MATCH_FINISHED) {
        MatchFinishedScreen(
            matchState = matchState,
            canUndo = history.isNotEmpty(),
            onUndo = {
                if (history.isNotEmpty()) {
                    try {
                        matchHistoryRepository.removeByMatchId(
                            matchState.startedAtEpochMillis,
                            deleteHealthData = false
                        )
                    } catch (exception: Exception) {
                        Log.e("PadelScore", "Could not undo completed match", exception)
                        Toast.makeText(
                            context,
                            deleteMatchFailedMessage,
                            Toast.LENGTH_LONG
                        ).show()
                        return@MatchFinishedScreen
                    }
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
            teamName = stringResource(R.string.opponent_team),
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
                clockText = ambientClock,
                canUndo = !isReducedDisplay && history.isNotEmpty(),
                onUndo = { if (!isReducedDisplay) undoLastPoint() },
                onReset = { if (!isReducedDisplay) showExitConfirmation = true }
            )
            if (isReducedDisplay) {
                Text(text = ambientClock, color = Color.White, fontSize = 12.sp)
            }
        }

        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            teamName = stringResource(R.string.our_team),
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

private fun formatClock(context: Context): String =
    DateFormat.getTimeFormat(context).format(Date())

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
