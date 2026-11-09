package ch.hygro.padelscore.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.Team
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Display-only outcome: sets, current games, then current points. No official winner is stored. */
@Composable
private fun earlyStopBalance(entry: MatchHistoryEntry): Int {
    val state = entry.stoppedState ?: return 0
    val sets = state.ourSets.compareTo(state.opponentSets)
    if (sets != 0) return sets
    if (state.phase == MatchPhase.MATCH_TIE_BREAK) {
        return state.ourMatchTieBreakPoints.compareTo(state.opponentMatchTieBreakPoints)
    }
    val games = state.ourGames.compareTo(state.opponentGames)
    if (games != 0) return games
    if (state.isTieBreak) {
        return state.ourTieBreakPoints.compareTo(state.opponentTieBreakPoints)
    }
    fun rank(score: String): Int = when (score) {
        "0" -> 0
        "15" -> 1
        "30" -> 2
        "40" -> 3
        "AD" -> 4
        else -> -1
    }
    val us = rank(pointText(state, Team.US))
    val opponent = rank(pointText(state, Team.OPPONENT))
    return if ((us >= 0) && (opponent >= 0)) us.compareTo(opponent) else 0
}

@Composable
internal fun earlyStopLabel(entry: MatchHistoryEntry): String = when {
    earlyStopBalance(entry) > 0 -> stringResource(R.string.won_at_stop)
    earlyStopBalance(entry) < 0 -> stringResource(R.string.lost_at_stop)
    else -> stringResource(R.string.tied_at_stop)
}

@Composable
internal fun earlyStopColor(entry: MatchHistoryEntry): Color = when {
    earlyStopBalance(entry) > 0 -> Color(0xFF66E59A)
    earlyStopBalance(entry) < 0 -> Color(0xFFFF7B72)
    else -> Color(0xFFFFD166)
}

@Composable
internal fun historyResultText(entry: MatchHistoryEntry): String {
    val stopped = entry.stoppedState
    if (stopped != null) {
        val completed = stopped.completedSets.joinToString("  ") { "${it.ourGames}:${it.opponentGames}" }
        val current: String = when {
            stopped.phase == MatchPhase.MATCH_TIE_BREAK -> {
                stringResource(R.string.match_tie_break_result, stopped.ourMatchTieBreakPoints, stopped.opponentMatchTieBreakPoints)
            }
            stopped.isTieBreak -> {
                val games = stringResource(R.string.games_result, stopped.ourGames, stopped.opponentGames)
                val tieBreak = stringResource(R.string.tie_break_result, stopped.ourTieBreakPoints, stopped.opponentTieBreakPoints)
                "$games  $tieBreak"
            }
            else -> {
                val games = stringResource(R.string.games_result, stopped.ourGames, stopped.opponentGames)
                val ourPoints = pointText(stopped, Team.US)
                val opponentPoints = pointText(stopped, Team.OPPONENT)
                "$games  $ourPoints:$opponentPoints"
            }
        }
        return listOf(completed, current).filter { it.isNotBlank() }.joinToString("  ")
    }
    val sets = entry.completedSets.joinToString("  ") { "${it.ourGames}:${it.opponentGames}" }
    val matchTieBreak = if (entry.opponentMatchTieBreakPoints > 0 || entry.ourMatchTieBreakPoints > 0) {
        stringResource(R.string.match_tie_break_result, entry.ourMatchTieBreakPoints, entry.opponentMatchTieBreakPoints)
    } else ""
    return listOf(sets, matchTieBreak).filter { it.isNotBlank() }.joinToString("  ").ifBlank {
        stringResource(R.string.sets_result, entry.ourSets, entry.opponentSets)
    }
}

@Composable
internal fun historyDateText(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", locale).withZone(ZoneId.systemDefault())
    }
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
internal fun historyDurationText(entry: MatchHistoryEntry): String {
    val seconds = ((entry.finishedAtEpochMillis - entry.startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return if (hours > 0L) stringResource(R.string.hours_format, hours, minutes)
    else stringResource(R.string.minutes_format, minutes)
}
