package ch.hygro.padelscore.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
