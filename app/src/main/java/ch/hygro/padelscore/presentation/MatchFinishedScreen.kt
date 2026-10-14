package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun MatchFinishedScreen(
    matchState: MatchState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit
) {
    val weWon = matchState.ourSets > matchState.opponentSets
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    Text(
                        text = if (weWon) stringResource(R.string.match_won) else stringResource(R.string.match_lost),
                        color = if (weWon) Color(0xFF66E59A) else Color(0xFFFF7B72),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = completedSetsText(matchState),
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Statistic(
                            label = stringResource(R.string.duration),
                            value = matchDurationText(matchState)
                        )
                        Statistic(
                            label = stringResource(R.string.points),
                            value = matchState.totalPointsPlayed.toString()
                        )
                        Statistic(
                            label = stringResource(R.string.games),
                            value = matchState.totalGamesPlayed.toString()
                        )
                    }
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    FinishedActionButton(
                        text = stringResource(R.string.undo),
                        backgroundColor = if (canUndo) Color(0xFF3A3A3A) else Color(0xFF1B1B1B),
                        enabled = canUndo,
                        onClick = onUndo
                    )

                    FinishedActionButton(
                        text = stringResource(R.string.new_match),
                        backgroundColor = Color(0xFF195F3B),
                        enabled = true,
                        onClick = onNewMatch
                    )
                }
            }
        }
    }
}

@Composable
private fun Statistic(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 6.sp,
            lineHeight = 7.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun FinishedActionButton(
    text: String,
    backgroundColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .height(31.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.DarkGray,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun completedSetsText(state: MatchState): String {
    val normalSets = state.completedSets.joinToString(separator = "  ") { result ->
        "${result.opponentGames}:${result.ourGames}"
    }

    val matchTieBreak = if (
        state.opponentMatchTieBreakPoints > 0 ||
        state.ourMatchTieBreakPoints > 0
    ) {
        stringResource(
            R.string.match_tie_break_result,
            state.opponentMatchTieBreakPoints,
            state.ourMatchTieBreakPoints
        )
    } else {
        ""
    }

    return listOf(normalSets, matchTieBreak)
        .filter { it.isNotBlank() }
        .joinToString(separator = "  ")
        .ifBlank {
            stringResource(
                R.string.sets_result,
                state.opponentSets,
                state.ourSets
            )
        }
}

private fun matchDurationText(state: MatchState): String {
    if (state.startedAtEpochMillis <= 0L) return "--:--"

    val endTime = state.finishedAtEpochMillis ?: System.currentTimeMillis()
    val durationSeconds = ((endTime - state.startedAtEpochMillis) / 1000L)
        .coerceAtLeast(0L)
    val hours = durationSeconds / 3600L
    val minutes = (durationSeconds % 3600L) / 60L
    val secs = durationSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d".format(hours, minutes)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}

@WearPreviewDevices
@Composable
private fun MatchFinishedScreenPreview() {
    PadelScoreTheme {
        MatchFinishedScreen(
            matchState = MatchState(configuration = MatchConfiguration.normalSet()),
            canUndo = true,
            onUndo = {},
            onNewMatch = {}
        )
    }
}
