package ch.hygro.padelscore.presentation

import androidx.activity.compose.BackHandler
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
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchHistoryEntry
import ch.hygro.padelscore.presentation.theme.ColorDestructive
import ch.hygro.padelscore.presentation.theme.ColorSecondary
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

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

@WearPreviewDevices
@Composable
private fun MatchHistoryScreenPreview() {
    PadelScoreTheme {
        MatchHistoryScreen(entries = emptyList(), onDeleteMatch = {}, onBack = {})
    }
}
