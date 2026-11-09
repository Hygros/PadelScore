package ch.hygro.padelscore.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorSecondary
import ch.hygro.padelscore.presentation.theme.ColorTeamOpponentText
import ch.hygro.padelscore.presentation.theme.ColorTeamUsText

@Composable
internal fun MatchHistoryDetailScreen(entry: MatchHistoryEntry, onBack: () -> Unit) {
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
    val tieBreakLabel = stringResource(R.string.tie_break)
    val matchTieBreakLabel = stringResource(R.string.match_tie_break_full)
    val currentSetLabel = stringResource(R.string.current_set)
    val tieBreakPointsLabel = stringResource(R.string.tie_break_points)
    val setLabels = entry.completedSets.indices.map { index ->
        stringResource(R.string.set_number, index + 1)
    }
    val scoreRows = buildList {
        entry.completedSets.forEachIndexed { index, set ->
            add(Triple(setLabels[index], set.ourGames.toString(), set.opponentGames.toString()))
            if (set.wasTieBreak) {
                add(Triple(tieBreakLabel, set.ourTieBreakPoints.toString(), set.opponentTieBreakPoints.toString()))
            }
        }
        if (stopped != null) {
            if (stopped.phase == MatchPhase.MATCH_TIE_BREAK) {
                add(Triple(matchTieBreakLabel, stopped.ourMatchTieBreakPoints.toString(), stopped.opponentMatchTieBreakPoints.toString()))
            } else {
                add(Triple(currentSetLabel, stopped.ourGames.toString(), stopped.opponentGames.toString()))
                if (stopped.isTieBreak) {
                    add(Triple(tieBreakPointsLabel, stopped.ourTieBreakPoints.toString(), stopped.opponentTieBreakPoints.toString()))
                }
            }
        } else if (entry.ourMatchTieBreakPoints > 0 || entry.opponentMatchTieBreakPoints > 0) {
            add(Triple(matchTieBreakLabel, entry.ourMatchTieBreakPoints.toString(), entry.opponentMatchTieBreakPoints.toString()))
        }
    }
    val pointScore = stopped?.let { pointText(it, Team.US) + " : " + pointText(it, Team.OPPONENT) }
    val gameGroups = remember(entry.pointHistory) {
        entry.pointHistory.groupBy { it.setNumber to it.gameNumber }.values.toList()
    }
    var chartGameIndex by rememberSaveable(entry.matchId) { mutableIntStateOf(0) }
    val chartIndex = chartGameIndex.coerceIn(0, maxOf(0, gameGroups.lastIndex))
    val metricPairs = listOf(
        listOf(stringResource(R.string.duration) to historyDurationText(entry), stringResource(R.string.points_played) to entry.totalPointsPlayed.toString()),
        listOf(stringResource(R.string.games_played) to entry.totalGamesPlayed.toString(), stringResource(R.string.steps_label) to (entry.steps?.takeIf { it >= 0L }?.toString() ?: "--")),
        listOf(stringResource(R.string.avg_heart_rate_label) to (entry.averageHeartRate?.takeIf { it > 0 }?.let { "$it bpm" } ?: "--"),
            stringResource(R.string.max_heart_rate_label) to (entry.maximumHeartRate?.takeIf { it > 0 }?.let { "$it bpm" } ?: "--"))
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
                    AutoSizeText(
                        text = stringResource(R.string.match_stats),
                        color = Color.White,
                        maxFontSize = 13.sp,
                        minFontSize = 10.sp,
                        maxLines = 1
                    )
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
                    AutoSizeText(status, color = accent, maxFontSize = 11.sp, minFontSize = 8.sp, maxLines = 1)
                    if (entry.stoppedState != null) {
                        Text(stringResource(R.string.provisional_result, stringResource(R.string.ended_early)), color = Color.LightGray,
                            fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                    Text(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text(stringResource(R.string.our_team), color = ColorTeamUsText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.sets_label), color = Color.LightGray, fontSize = 9.sp)
                        Text(stringResource(R.string.opponent_short), color = ColorTeamOpponentText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${entry.ourSets} : ${entry.opponentSets}", color = Color.White,
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (pointScore != null) {
                        Text(stringResource(R.string.points_at_stop), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                            AutoSizeText(
                                text = metric.first,
                                color = Color(0xFF9AAFC1),
                                maxFontSize = 8.sp,
                                minFontSize = 6.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            AutoSizeText(
                                text = metric.second,
                                color = Color.White,
                                maxFontSize = 15.sp,
                                minFontSize = 10.sp,
                                maxLines = 1
                            )
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
                    if (gameGroups.isEmpty() && stopped == null) {
                        Text(stringResource(R.string.older_matches_no_point_history), color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                val itemScope = this
                Button(
                    modifier = Modifier.fillMaxWidth(if (isRound) 0.84f else 0.88f).height(48.dp)
                        .transformedHeight(itemScope, transformationSpec).padding(top = 4.dp, bottom = 12.dp),
                    contentPadding = CompactButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSecondary, contentColor = Color.White),
                    onClick = onBack
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.back),
                        maxFontSize = 12.sp,
                        minFontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun gameTitle(point: GamePoint): String = when {
    point.matchTieBreak -> stringResource(R.string.match_tie_break_full)
    point.numeric -> stringResource(R.string.set_tie_break_title, point.setNumber)
    else -> stringResource(R.string.set_game_title, point.setNumber, point.gameNumber)
}


