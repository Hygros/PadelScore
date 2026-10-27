package ch.hygro.padelscore.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorDestructive
import ch.hygro.padelscore.presentation.theme.ColorSecondary
import ch.hygro.padelscore.presentation.theme.ColorTeamOpponentText
import ch.hygro.padelscore.presentation.theme.ColorTeamUsText
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MatchHistoryScreen(
    entries: List<MatchHistoryEntry>,
    onDeleteMatch: (Long) -> Unit,
    onBack: () -> Unit
) {
    var pendingDeletion by remember { mutableStateOf<MatchHistoryEntry?>(null) }
    var selectedMatchId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Keep the list state alive while a detail page or confirmation is visible.
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val selectedEntry = entries.firstOrNull { it.matchId == selectedMatchId }

    if (selectedEntry != null) {
        BackHandler { selectedMatchId = null }
        MatchHistoryDetailScreen(entry = selectedEntry, onBack = { selectedMatchId = null })
        return
    }
    val deletionEntry = pendingDeletion
    if (deletionEntry != null) {
        BackHandler { pendingDeletion = null }
        DeleteMatchConfirmation(
            entry = deletionEntry,
            onConfirm = {
                pendingDeletion = null
                onDeleteMatch(deletionEntry.matchId)
            },
            onCancel = { pendingDeletion = null }
        )
        return
    }
    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp, start = 8.dp, end = 8.dp),
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    Text(stringResource(R.string.played_matches), color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            if (entries.isEmpty()) {
                item {
                    val itemScope = this
                    Box(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.85f else 1f)
                            .transformedHeight(itemScope, transformationSpec).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_matches_yet), color = Color.LightGray,
                            fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(entries, key = { it.matchId }) { entry ->
                    val itemScope = this
                    Box(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.90f else 1f)
                            .transformedHeight(itemScope, transformationSpec).padding(vertical = 3.dp)
                    ) {
                        HistoryEntryCard(entry, onClick = { selectedMatchId = entry.matchId },
                            onLongClick = { pendingDeletion = entry })
                    }
                }
            }
            item {
                val itemScope = this
                Box(
                    modifier = Modifier.fillMaxWidth().transformedHeight(itemScope, transformationSpec)
                        .padding(top = 8.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.82f else 0.88f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSecondary, contentColor = Color.White),
                        onClick = onBack
                    ) {
                        Text(stringResource(R.string.back), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteMatchConfirmation(entry: MatchHistoryEntry, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp, start = 8.dp, end = 8.dp),
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    Text(stringResource(R.string.delete_match_title), color = Color(0xFFFF7B72), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            item {
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.88f else 1f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(historyResultText(entry), color = Color.White, fontSize = 11.sp, lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                    Text(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray,
                        fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.delete_match_message), color = Color.White,
                        fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
                }
            }
            item {
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth().transformedHeight(itemScope, transformationSpec)
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.82f else 0.88f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDestructive, contentColor = Color.White),
                        onClick = onConfirm
                    ) {
                        Text(stringResource(R.string.delete), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.82f else 0.88f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSecondary, contentColor = Color.White),
                        onClick = onCancel
                    ) {
                        Text(stringResource(R.string.cancel), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

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
private fun earlyStopLabel(entry: MatchHistoryEntry): String = when {
    earlyStopBalance(entry) > 0 -> "WON AT STOP"
    earlyStopBalance(entry) < 0 -> "LOST AT STOP"
    else -> "TIED AT STOP"
}

@Composable
private fun earlyStopColor(entry: MatchHistoryEntry): Color = when {
    earlyStopBalance(entry) > 0 -> Color(0xFF66E59A)
    earlyStopBalance(entry) < 0 -> Color(0xFFFF7B72)
    else -> Color(0xFFFFD166)
}

@Composable
private fun HistoryEntryCard(entry: MatchHistoryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF292929), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    entry.stoppedState != null -> earlyStopLabel(entry)
                    entry.weWon -> stringResource(R.string.won)
                    else -> stringResource(R.string.lost)
                },
                color = when {
                    entry.stoppedState != null -> earlyStopColor(entry)
                    entry.weWon -> Color(0xFF66E59A)
                    else -> Color(0xFFFF7B72)
                },
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Text(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray, fontSize = 9.sp)
        }
        Text(historyResultText(entry), color = Color.White, fontSize = 14.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.Bold, maxLines = 2)
        val statsText = stringResource(R.string.history_stats_format, entry.totalGamesPlayed,
            entry.totalPointsPlayed, historyDurationText(entry))
        val hrText = entry.averageHeartRate?.takeIf { it > 0 }?.let { " · $it bpm" } ?: ""
        Text(statsText + hrText + (entry.maximumHeartRate?.let { " · Max $it bpm" } ?: ""),
            color = Color.LightGray, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2)
        Text(entry.steps?.let { "Steps: $it" } ?: "Steps unavailable", color = Color.LightGray,
            fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
    }
}

@Composable
private fun historyResultText(entry: MatchHistoryEntry): String {
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
private fun historyDateText(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", locale).withZone(ZoneId.of("Europe/Zurich"))
    }
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
private fun historyDurationText(entry: MatchHistoryEntry): String {
    val seconds = ((entry.finishedAtEpochMillis - entry.startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return if (hours > 0L) stringResource(R.string.hours_format, hours, minutes)
    else stringResource(R.string.minutes_format, minutes)
}

@Composable
private fun MatchHistoryDetailScreen(entry: MatchHistoryEntry, onBack: () -> Unit) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val accent = when {
        entry.stoppedState != null -> earlyStopColor(entry)
        entry.weWon -> Color(0xFF66E59A)
        else -> Color(0xFFFF7B72)
    }
    val status = when {
        entry.stoppedState != null -> earlyStopLabel(entry)
        entry.weWon -> stringResource(R.string.won)
        else -> stringResource(R.string.lost)
    }
    val stopped = entry.stoppedState
    val scoreRows = buildList {
        entry.completedSets.forEachIndexed { index, set ->
            add(Triple("SET ${index + 1}", set.ourGames.toString(), set.opponentGames.toString()))
            if (set.wasTieBreak) {
                add(Triple("TIE-BREAK", set.ourTieBreakPoints.toString(), set.opponentTieBreakPoints.toString()))
            }
        }
        if (stopped != null) {
            if (stopped.phase == MatchPhase.MATCH_TIE_BREAK) {
                add(Triple("MATCH TIE-BREAK", stopped.ourMatchTieBreakPoints.toString(), stopped.opponentMatchTieBreakPoints.toString()))
            } else {
                add(Triple("CURRENT SET", stopped.ourGames.toString(), stopped.opponentGames.toString()))
                if (stopped.isTieBreak) {
                    add(Triple("TIE-BREAK POINTS", stopped.ourTieBreakPoints.toString(), stopped.opponentTieBreakPoints.toString()))
                }
            }
        } else if (entry.ourMatchTieBreakPoints > 0 || entry.opponentMatchTieBreakPoints > 0) {
            add(Triple("MATCH TIE-BREAK", entry.ourMatchTieBreakPoints.toString(), entry.opponentMatchTieBreakPoints.toString()))
        }
    }
    val pointScore = stopped?.let { pointText(it, Team.US) + " : " + pointText(it, Team.OPPONENT) }
    var selectedGameNumber by rememberSaveable(entry.matchId) { mutableStateOf<Int?>(null) }
    val selectedGame = entry.pointHistory.filter { it.gameNumber == selectedGameNumber }
    if (selectedGame.isNotEmpty()) {
        BackHandler { selectedGameNumber = null }
        GamePointDetailScreen(selectedGame, onBack = { selectedGameNumber = null })
        return
    }
    val gameGroups = remember(entry.pointHistory) {
        entry.pointHistory.groupBy { it.setNumber to it.gameNumber }.values.toList()
    }
    var chartGameIndex by rememberSaveable(entry.matchId) { mutableIntStateOf(0) }
    val chartIndex = chartGameIndex.coerceIn(0, maxOf(0, gameGroups.lastIndex))
    val metricPairs = listOf(
        listOf("DURATION" to historyDurationText(entry), "POINTS PLAYED" to entry.totalPointsPlayed.toString()),
        listOf("GAMES PLAYED" to entry.totalGamesPlayed.toString(), "STEPS" to (entry.steps?.takeIf { it >= 0L }?.toString() ?: "--")),
        listOf("AVG HEART RATE" to (entry.averageHeartRate?.takeIf { it > 0 }?.let { "$it bpm" } ?: "--"),
            "MAX HEART RATE" to (entry.maximumHeartRate?.takeIf { it > 0 }?.let { "$it bpm" } ?: "--"))
    )
    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState, contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    Text("MATCH STATS", color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            item {
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.86f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 4.dp)
                        .background(Color(0xFF14232C), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(status, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (entry.stoppedState != null) {
                        Text("${stringResource(R.string.ended_early)} · provisional result", color = Color.LightGray,
                            fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                    Text(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("US", color = ColorTeamUsText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("SETS", color = Color.LightGray, fontSize = 9.sp)
                        Text("OPP", color = ColorTeamOpponentText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${entry.ourSets} : ${entry.opponentSets}", color = Color.White,
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (pointScore != null) {
                        Text("POINTS AT STOP", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(pointScore, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            items(scoreRows) { score ->
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.86f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 3.dp)
                        .background(Color(0xFF1B2028), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(score.first, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text(score.second, color = ColorTeamUsText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(":", color = Color.Gray, fontSize = 20.sp)
                        Text(score.third, color = ColorTeamOpponentText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth(0.90f).transformedHeight(itemScope, transformationSpec).padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (gameGroups.isNotEmpty()) {
                        Text(gameTitle(gameGroups[chartIndex].first()), color = Color.White,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            if (gameGroups.isNotEmpty()) {
                item {
                    val itemScope = this
                    Box(Modifier.fillMaxWidth(if (isRound) 0.94f else 0.98f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 3.dp)) {
                        GameScoreChart(
                            points = gameGroups[chartIndex],
                            gamePosition = "${chartIndex + 1}/${gameGroups.size}",
                            canPrevious = chartIndex > 0,
                            canNext = chartIndex < gameGroups.lastIndex,
                            onPrevious = { chartGameIndex = chartIndex - 1 },
                            onNext = { chartGameIndex = chartIndex + 1 }
                        )
                    }
                }
            }
            items(metricPairs) { pair ->
                val itemScope = this
                Row(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.86f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pair.forEach { metric ->
                        Column(
                            modifier = Modifier.weight(1f).background(Color(0xFF1B2028), RoundedCornerShape(16.dp))
                                .padding(horizontal = 4.dp, vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(metric.first, color = Color(0xFF9AAFC1), fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center)
                            Text(metric.second, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            item {
                val itemScope = this
                Column(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.86f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (stopped == null) {
                        Text("Older matches may have no point history.", color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                val itemScope = this
                Button(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.82f else 0.88f)
                        .transformedHeight(itemScope, transformationSpec).padding(top = 4.dp, bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSecondary, contentColor = Color.White),
                    onClick = onBack
                ) { Text(stringResource(R.string.back), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun gameTitle(point: GamePoint): String = when {
    point.matchTieBreak -> "MATCH TIE-BREAK"
    point.numeric -> "SET ${point.setNumber} · TIE-BREAK"
    else -> "SET ${point.setNumber} · GAME ${point.gameNumber}"
}

/** Recover the unchanged losing score only from a contiguous record of the same game. */
private fun recordedScore(points: List<GamePoint>, index: Int, us: Boolean): String? {
    val point = points.getOrNull(index) ?: return null
    val score = if (us) point.ourScore else point.opponentScore
    if (score != "-") return score
    val winnerIsUs = point.scoringTeam == Team.US
    if (!point.gameEnded || us == winnerIsUs || point.numeric) return null
    val previous = points.getOrNull(index - 1) ?: return null
    if (previous.number + 1 != point.number || previous.gameNumber != point.gameNumber ||
        previous.setNumber != point.setNumber) return null
    val previousScore = if (us) previous.ourScore else previous.opponentScore
    return previousScore.takeIf { it in listOf("0", "15", "30", "40", "AD") }
}

/** Score categories are equally spaced, not numeric distances between 15/30/40. */
private fun chartScoreLevel(text: String, numeric: Boolean): Int? = if (numeric) {
    text.toIntOrNull()?.takeIf { it >= 0 }
} else {
    when (text) {
        "0" -> 0
        "15" -> 1
        "30" -> 2
        "40" -> 3
        "AD" -> 4
        "GAME" -> 5
        else -> null // '-' on the losing team's final point is not a recorded score.
    }
}

/** Show every recorded point of the selected game without paging or downsampling. */
@Composable
private fun GameScoreChart(
    points: List<GamePoint>,
    gamePosition: String? = null,
    canPrevious: Boolean = false,
    canNext: Boolean = false,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {}
) {
    if (points.isEmpty()) return
    val firstPoint = points.first()
    val numeric = firstPoint.numeric
    val levels = listOf("0", "15", "30", "40", "AD", "GAME")
    val maximum = if (numeric) {
        maxOf(1, points.maxOf { maxOf(chartScoreLevel(it.ourScore, true) ?: 0,
            chartScoreLevel(it.opponentScore, true) ?: 0) })
    } else 5
    val yTicks = if (numeric) listOf(0, maximum / 2, maximum).distinct() else (0..5).toList()
    val density = LocalDensity.current
    val labelPixels = with(density) { 8.sp.toPx() }
    val paint = remember(labelPixels) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelPixels
            color = android.graphics.Color.rgb(225, 232, 240)
        }
    }
    val description = "Game score after recorded rallies. match point number. " +
            (if (numeric) "tie-break points." else "0, 15, 30, 40, AD, GAME. Equal score categories.") +
            " US solid circles; OPP solid circles. " +
            points.joinToString("; ") { "Point ${it.number}: ${it.ourScore} to ${it.opponentScore}" }
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF14232C), RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        Canvas(Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = description }) {
            val left = 43.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 5.dp.toPx()
            val bottom = size.height - 27.dp.toPx()
            if (right <= left || bottom <= top) return@Canvas
            val firstNumber = points.first().number
            val lastNumber = points.last().number
            val span = maxOf(1, lastNumber - firstNumber)
            fun x(number: Int): Float = if (firstNumber == lastNumber) (left + right) / 2f
            else left + (number - firstNumber).toFloat() / span * (right - left)
            fun y(value: Int) = bottom - value.toFloat() / maximum * (bottom - top)
            // Native canvas rotation keeps the vertical title outside the plot.
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saved = native.save()
                try {
                    val centerY = (top + bottom) / 2f
                    native.rotate(-90f, 6.dp.toPx(), centerY)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    native.drawText(
                        if (numeric) "Tie-break points" else "Game score",
                        6.dp.toPx(), centerY - (paint.ascent() + paint.descent()) / 2f, paint
                    )
                } finally {
                    native.restoreToCount(saved)
                }
                paint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.nativeCanvas.drawText(
                    "Match point number", (left + right) / 2f,
                    bottom + 24.dp.toPx(), paint
                )
            }
            yTicks.forEach { tick ->
                drawLine(Color(0xFF45596B), Offset(left, y(tick)), Offset(right, y(tick)), 0.7.dp.toPx())
                drawIntoCanvas { canvas ->
                    paint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas.nativeCanvas.drawText(if (numeric) tick.toString() else levels[tick],
                        left - 5.dp.toPx(), y(tick) - (paint.ascent() + paint.descent()) / 2f, paint)
                }
            }
            drawLine(Color.LightGray, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
            drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
            val tickIndices = listOf(0, (points.size - 1) / 2, points.lastIndex).distinct()
            tickIndices.forEach { index ->
                val number = points[index].number
                drawLine(Color.LightGray, Offset(x(number), bottom), Offset(x(number), bottom + 3.dp.toPx()), 1.dp.toPx())
                drawIntoCanvas { canvas ->
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    canvas.nativeCanvas.drawText(number.toString(), x(number), bottom + 12.dp.toPx(), paint)
                }
            }
            // Draw lines for both series
            points.forEachIndexed { index, point ->
                val valUs = recordedScore(points, index, true)?.let { chartScoreLevel(it, numeric) }
                val valOpp = recordedScore(points, index, false)?.let { chartScoreLevel(it, numeric) }

                if (index > 0 && point.number == points[index - 1].number + 1) {
                    val previous = points[index - 1]
                    val prevValUs = recordedScore(points, index - 1, true)?.let { chartScoreLevel(it, numeric) }
                    val prevValOpp = recordedScore(points, index - 1, false)?.let { chartScoreLevel(it, numeric) }

                    // Horizontal lines
                    if (prevValUs != null && prevValOpp != null && prevValUs == prevValOpp) {
                        val y = y(prevValUs)
                        val startX = x(previous.number)
                        val endX = x(point.number)
                        drawLine(ColorTeamUsText, Offset(startX, y - 0.5.dp.toPx()), Offset(endX, y - 0.5.dp.toPx()), 1.dp.toPx())
                        drawLine(ColorTeamOpponentText, Offset(startX, y + 0.5.dp.toPx()), Offset(endX, y + 0.5.dp.toPx()), 1.dp.toPx())
                    } else {
                        if (prevValUs != null) {
                            drawLine(ColorTeamUsText, Offset(x(previous.number), y(prevValUs)), Offset(x(point.number), y(prevValUs)), 2.dp.toPx())
                        }
                        if (prevValOpp != null) {
                            drawLine(ColorTeamOpponentText, Offset(x(previous.number), y(prevValOpp)), Offset(x(point.number), y(prevValOpp)), 2.dp.toPx())
                        }
                    }

                    // Vertical lines
                    if (valUs != null && prevValUs != null && valUs != prevValUs) {
                        drawLine(ColorTeamUsText, Offset(x(point.number), y(prevValUs)), Offset(x(point.number), y(valUs)), 2.dp.toPx())
                    }
                    if (valOpp != null && prevValOpp != null && valOpp != prevValOpp) {
                        drawLine(ColorTeamOpponentText, Offset(x(point.number), y(prevValOpp)), Offset(x(point.number), y(valOpp)), 2.dp.toPx())
                    }
                } else if (index == 0) {
                    if (valUs != null && valOpp != null && valUs == valOpp && valUs != 0) {
                        drawLine(ColorTeamUsText, Offset(x(point.number) - 0.5.dp.toPx(), y(0)), Offset(x(point.number) - 0.5.dp.toPx(), y(valUs)), 1.dp.toPx())
                        drawLine(ColorTeamOpponentText, Offset(x(point.number) + 0.5.dp.toPx(), y(0)), Offset(x(point.number) + 0.5.dp.toPx(), y(valOpp)), 1.dp.toPx())
                    } else {
                        if (valUs != null && valUs != 0) {
                            drawLine(ColorTeamUsText, Offset(x(point.number), y(0)), Offset(x(point.number), y(valUs)), 2.dp.toPx())
                        }
                        if (valOpp != null && valOpp != 0) {
                            drawLine(ColorTeamOpponentText, Offset(x(point.number), y(0)), Offset(x(point.number), y(valOpp)), 2.dp.toPx())
                        }
                    }
                }
            }

            // Draw points for both series. A marker is split whenever both coloured
            // line segments occupy its position, not only when both current scores are equal.
            points.forEachIndexed { index, point ->
                val valUs = recordedScore(points, index, true)?.let { chartScoreLevel(it, numeric) }
                val valOpp = recordedScore(points, index, false)?.let { chartScoreLevel(it, numeric) }
                val hasContiguousPrevious = index > 0 &&
                        point.number == points[index - 1].number + 1
                val prevValUs = if (hasContiguousPrevious) {
                    recordedScore(points, index - 1, true)?.let { chartScoreLevel(it, numeric) }
                } else null
                val prevValOpp = if (hasContiguousPrevious) {
                    recordedScore(points, index - 1, false)?.let { chartScoreLevel(it, numeric) }
                } else null
                val radius = 2.4.dp.toPx()

                fun liesOnSegment(value: Int, start: Int?, end: Int?): Boolean {
                    return !(start == null || end == null) && value in minOf(start, end)..maxOf(start, end)
                }

                fun drawSplitPoint(position: Offset) {
                    drawArc(
                        color = ColorTeamUsText,
                        startAngle = 135f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(position.x - radius, position.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    drawArc(
                        color = ColorTeamOpponentText,
                        startAngle = -45f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(position.x - radius, position.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }
                if (valUs != null && valOpp != null && valUs == valOpp) {
                    drawSplitPoint(Offset(x(point.number), y(valUs)))
                } else {
                    if (valUs != null) {
                        val position = Offset(x(point.number), y(valUs))
                        if (liesOnSegment(valUs, prevValOpp, valOpp)) {
                            drawSplitPoint(position)
                        } else {
                            drawCircle(ColorTeamUsText, radius, position)
                        }
                    }
                    if (valOpp != null) {
                        val position = Offset(x(point.number), y(valOpp))
                        if (liesOnSegment(valOpp, prevValUs, valUs)) {
                            drawSplitPoint(position)
                        } else {
                            drawCircle(ColorTeamOpponentText, radius, position)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("US ●", color = ColorTeamUsText, fontSize = 8.sp)
            Text("OPP ●", color = ColorTeamOpponentText, fontSize = 8.sp)
        }
        if (gamePosition != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Button(modifier = Modifier.weight(1f).height(34.dp), enabled = canPrevious,
                    onClick = onPrevious) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Prev", fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
                Text(gamePosition, color = Color.LightGray, fontSize = 8.sp, textAlign = TextAlign.Center)
                Button(modifier = Modifier.weight(1f).height(34.dp), enabled = canNext,
                    onClick = onNext) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Next", fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}


@Composable
private fun GamePointDetailScreen(points: List<GamePoint>, onBack: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(
            state = state, contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val scope = this
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(scope, spec),
                    transformation = with(scope) { SurfaceTransformation(spec) }
                ) {
                    Text(gameTitle(points.first()), color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            item {
                val scope = this
                Box(Modifier.fillMaxWidth(if (isRound) 0.94f else 0.98f)
                    .transformedHeight(scope, spec).padding(vertical = 5.dp)) { GameScoreChart(points) }
            }
            item {
                val scope = this
                Text("US : OPP · score after each rally", color = Color.LightGray, fontSize = 9.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.85f)
                        .transformedHeight(scope, spec).padding(vertical = 6.dp))
            }
            items(points, key = { it.number }) { point ->
                val scope = this
                val color = if (point.scoringTeam == Team.US) Color(0xFF66E59A) else Color(0xFF82B8FF)
                Column(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.86f else 0.94f)
                        .transformedHeight(scope, spec).padding(vertical = 3.dp)
                        .background(Color(0xFF1B2028), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("POINT #${point.number} · ${if (point.scoringTeam == Team.US) "US" else "OPP"}",
                        color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${recordedScore(points, points.indexOf(point), true) ?: "?"} : ${recordedScore(points, points.indexOf(point), false) ?: "?"}",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (point.gameEnded) {
                        Text("Game won · rallies ${point.ourRallies}:${point.opponentRallies}", color = color,
                            fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                val scope = this
                Button(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.82f else 0.88f)
                        .transformedHeight(scope, spec).padding(top = 6.dp, bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245A81), contentColor = Color.White),
                    onClick = onBack
                ) { Text(stringResource(R.string.back), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun MatchHistoryScreenPreview() {
    PadelScoreTheme {
        MatchHistoryScreen(entries = emptyList(), onDeleteMatch = {}, onBack = {})
    }
}
