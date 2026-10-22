package ch.hygro.padelscore.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ch.hygro.padelscore.R
import ch.hygro.padelscore.logic.PadelScoreEngine
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorGoldenPoint
import ch.hygro.padelscore.presentation.theme.ColorMatchLost
import ch.hygro.padelscore.presentation.theme.ColorMatchWon
import ch.hygro.padelscore.presentation.theme.ColorStarPoint

@Composable
fun centerLabel(state: MatchState, showCourtChange: Boolean): String {
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
        state.advantageTeam == Team.OPPONENT -> stringResource(R.string.advantage_opponent)
        state.advantageTeam == Team.US -> stringResource(R.string.advantage_us)
        state.opponentPoints == 3 && state.ourPoints == 3 -> deuceLabel(state)
        else -> stringResource(R.string.games)
    }
}

@Composable
fun deuceLabel(state: MatchState): String {
    return when (state.configuration.scoringMode) {
        ScoringMode.STAR_POINT -> stringResource(R.string.deuce_with_count, state.deuceReturnCount + 1)
        ScoringMode.CLASSIC_ADVANTAGE,
        ScoringMode.GOLDEN_POINT -> stringResource(R.string.deuce)
    }
}

fun centerLabelColor(state: MatchState): Color {
    return when {
        state.phase == MatchPhase.MATCH_FINISHED &&
                state.ourSets > state.opponentSets -> ColorMatchWon
        state.phase == MatchPhase.MATCH_FINISHED &&
                state.opponentSets > state.ourSets -> ColorMatchLost
        state.isStarPointActive -> ColorStarPoint
        state.isGoldenPointActive -> ColorGoldenPoint
        state.advantageTeam != null -> Color.White
        state.opponentPoints == 3 && state.ourPoints == 3 -> Color.LightGray
        else -> Color.LightGray
    }
}

fun centerLabelFontSize(state: MatchState) =
    if (PadelScoreEngine.isMatchFinished(state)) 8.sp else 9.sp

@Composable
fun pointText(state: MatchState, team: Team): String {
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
fun scoringModeLabel(scoringMode: ScoringMode): String {
    return when (scoringMode) {
        ScoringMode.CLASSIC_ADVANTAGE -> stringResource(R.string.advantage_abbr)
        ScoringMode.GOLDEN_POINT -> stringResource(R.string.golden_abbr)
        ScoringMode.STAR_POINT -> stringResource(R.string.star_abbr)
    }
}

@Composable
fun scoringModeFullName(mode: ScoringMode): String {
    return when (mode) {
        ScoringMode.CLASSIC_ADVANTAGE -> stringResource(R.string.advantage)
        ScoringMode.GOLDEN_POINT -> stringResource(R.string.golden_point)
        ScoringMode.STAR_POINT -> stringResource(R.string.star_point)
    }
}

fun hasRunningSavedMatch(state: MatchState): Boolean {
    return state.phase != MatchPhase.MATCH_FINISHED &&
            (state.startedAtEpochMillis > 0L || matchHasStarted(state))
}

fun matchHasStarted(state: MatchState): Boolean {
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

const val MAX_HISTORY_SIZE = 100
const val RESET_HOLD_DURATION_MILLIS = 1500L
const val POINT_INPUT_LOCK_MILLIS = 300L
