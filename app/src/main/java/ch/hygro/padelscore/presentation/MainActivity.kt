package ch.hygro.padelscore.presentation

import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.logic.PadelScoreEngine
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme
import ch.hygro.padelscore.storage.MatchHistoryRepository
import ch.hygro.padelscore.storage.MatchRepository
import ch.hygro.padelscore.vibration.HapticFeedbackManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PadelScoreTheme {
                PadelScoreApp()
            }
        }
    }
}

@Composable
private fun PadelScoreApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val hapticFeedbackManager = remember(view) {
        HapticFeedbackManager(view)
    }
    val ambientModeManager = rememberAmbientModeManager()
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
    var lastAcceptedPointAtMillis by remember { mutableStateOf(0L) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showMatchHistory by remember { mutableStateOf(false) }

    fun updateMatch(newState: MatchState) {
        matchState = newState
        matchRepository.saveSnapshot(
            currentState = newState,
            undoHistory = history
        )
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

    if (showMatchHistory) {
        BackHandler { showMatchHistory = false }
        MatchHistoryScreen(
            entries = matchHistoryRepository.loadEntries(),
            onBack = { showMatchHistory = false }
        )
        return
    }

    if (showConfiguration) {
        StartScreen(
            initialConfiguration = matchState.configuration,
            onStartMatch = { configuration ->
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
            onOpenHistory = { showMatchHistory = true }
        )
        return
    }

    if (
        isAmbientMode &&
        matchState.phase != MatchPhase.MATCH_FINISHED
    ) {
        AmbientMatchScreen(matchState = matchState)
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
            onCloseApp = {
                showExitConfirmation = false
                activity?.finish()
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

    val matchFinished = PadelScoreEngine.isMatchFinished(matchState)

    fun saveCurrentStateToHistory() {
        history.add(matchState)
        if (history.size > MAX_HISTORY_SIZE) history.removeAt(0)
    }

    fun addPoint(team: Team) {
        if (PadelScoreEngine.isMatchFinished(matchState)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastAcceptedPointAtMillis < POINT_INPUT_LOCK_MILLIS) return

        lastAcceptedPointAtMillis = now
        saveCurrentStateToHistory()

        val previousState = matchState
        val newState = PadelScoreEngine.awardPoint(previousState, team)

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
            manager = hapticFeedbackManager
        )
    }

    fun undoLastPoint() {
        if (history.isNotEmpty()) {
            lastAcceptedPointAtMillis = 0L
            updateMatch(history.removeAt(history.lastIndex))
            hapticFeedbackManager.undo()
        }
    }

    fun resetMatch() {
        val currentConfiguration = matchState.configuration
        lastAcceptedPointAtMillis = 0L
        history.clear()
        updateMatch(MatchState(configuration = currentConfiguration))
        showExitConfirmation = false
        showResumePrompt = false
        showConfiguration = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pointText = pointText(matchState, Team.OPPONENT),
            games = matchState.opponentGames,
            sets = matchState.opponentSets,
            backgroundColor = Color(0xFF8B2525),
            isServing = matchState.servingTeam == Team.OPPONENT,
            statsAtTop = true,
            enabled = !matchFinished,
            onClick = { addPoint(Team.OPPONENT) }
        )

        CenterScoreArea(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            matchState = matchState,
            canUndo = history.isNotEmpty(),
            canChangeScoringMode = false,
            onUndo = { undoLastPoint() },
            onReset = { resetMatch() },
            onChangeScoringMode = { }
        )

        ScoreArea(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pointText = pointText(matchState, Team.US),
            games = matchState.ourGames,
            sets = matchState.ourSets,
            backgroundColor = Color(0xFF195F3B),
            isServing = matchState.servingTeam == Team.US,
            statsAtTop = false,
            enabled = !matchFinished,
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
    statsAtTop: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val displayedBackgroundColor = if (enabled) {
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
                sets = sets
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
                fontSize = 45.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            if (isServing) {
                Text(
                    text = "●",
                    color = Color(0xFFFFD54F),
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(
                        if (statsAtTop) {
                            Alignment.BottomStart
                        } else {
                            Alignment.TopStart
                        }
                    )
                )
            }
        }

        if (!statsAtTop) {
            ScoreStatistics(
                games = games,
                sets = sets
            )
        }
    }
}

@Composable
private fun ScoreStatistics(
    games: Int,
    sets: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Games $games",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Sets $sets",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
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
    canUndo: Boolean,
    canChangeScoringMode: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onChangeScoringMode: () -> Unit
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
            delay(RESET_HOLD_DURATION_MILLIS)

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
            modifier = Modifier
                .width(82.dp)
                .clickable(
                    enabled = canChangeScoringMode,
                    onClick = onChangeScoringMode
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerLabel(matchState),
                color = centerLabelColor(matchState),
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
                color = if (canChangeScoringMode) {
                    Color(0xFFFFD54F)
                } else {
                    Color.White
                },
                fontSize = if (canChangeScoringMode) 8.sp else 15.sp,
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
                text = if (isResetHolding) "HALTEN" else "RESET",
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
    manager: HapticFeedbackManager
) {
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
            manager.point()
        }
    }
}

private fun centerLabel(state: MatchState): String {
    return when {
        state.phase == MatchPhase.MATCH_FINISHED &&
                state.ourSets > state.opponentSets -> "MATCH GEWONNEN"

        state.phase == MatchPhase.MATCH_FINISHED &&
                state.opponentSets > state.ourSets -> "MATCH VERLOREN"

        state.phase == MatchPhase.MATCH_TIE_BREAK -> "MATCH-TIE-BREAK"
        state.isTieBreak -> "TIE-BREAK"
        state.isStarPointActive -> "STAR POINT"
        state.isGoldenPointActive -> "GOLDEN POINT"
        state.advantageTeam == Team.OPPONENT -> "VORTEIL OBEN"
        state.advantageTeam == Team.US -> "VORTEIL UNTEN"
        state.opponentPoints == 3 && state.ourPoints == 3 -> deuceLabel(state)
        else -> "GAMES"
    }
}

private fun deuceLabel(state: MatchState): String {
    return when (state.configuration.scoringMode) {
        ScoringMode.STAR_POINT -> "DEUCE ${state.deuceReturnCount + 1}"
        ScoringMode.CLASSIC_ADVANTAGE,
        ScoringMode.GOLDEN_POINT -> "DEUCE"
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
        return "AD"
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

private fun scoringModeLabel(scoringMode: ScoringMode): String {
    return when (scoringMode) {
        ScoringMode.CLASSIC_ADVANTAGE -> "VORTEIL"
        ScoringMode.GOLDEN_POINT -> "GOLDEN"
        ScoringMode.STAR_POINT -> "STAR"
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
