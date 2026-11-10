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
                    AutoSizeText(
                        text = stringResource(R.string.played_matches),
                        color = Color.White,
                        maxFontSize = 13.sp,
                        minFontSize = 10.sp,
                        maxLines = 1
                    )
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
                        AutoSizeText(stringResource(R.string.no_matches_yet), color = Color.LightGray,
                            maxFontSize = 10.5.sp, minFontSize = 9.sp, fontWeight = FontWeight.Normal, maxLines = 5)
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
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.84f else 0.88f).height(48.dp),
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
                    AutoSizeText(
                        text = stringResource(R.string.delete_match_title),
                        color = Color(0xFFFF7B72),
                        maxFontSize = 13.sp,
                        minFontSize = 10.sp,
                        maxLines = 1
                    )
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
                    AutoSizeText(historyResultText(entry), color = Color.White, maxFontSize = 11.sp,
                        minFontSize = 9.sp, maxLines = 2)
                    AutoSizeText(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray,
                        maxFontSize = 9.5.sp, minFontSize = 8.5.sp, fontWeight = FontWeight.Normal)
                    AutoSizeText(stringResource(R.string.delete_match_message), color = Color.White,
                        maxFontSize = 10.sp, minFontSize = 9.sp, fontWeight = FontWeight.Normal, maxLines = 6)
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
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.84f else 0.88f).height(48.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDestructive, contentColor = Color.White),
                        onClick = onConfirm
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.delete),
                            maxFontSize = 12.sp,
                            minFontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(if (isRound) 0.84f else 0.88f).height(48.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSecondary, contentColor = Color.White),
                        onClick = onCancel
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.cancel).uppercase(),
                            maxFontSize = 12.sp,
                            minFontSize = 8.sp,
                            maxLines = 1
                        )
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
            AutoSizeText(
                text = when {
                    entry.stoppedState != null -> earlyStopLabel(entry)
                    entry.weWon -> stringResource(R.string.won)
                    else -> stringResource(R.string.lost)
                },
                modifier = Modifier.weight(1f),
                color = when {
                    entry.stoppedState != null -> earlyStopColor(entry)
                    entry.weWon -> Color(0xFF66E59A)
                    else -> Color(0xFFFF7B72)
                },
                maxFontSize = 11.sp,
                minFontSize = 7.5.sp,
                textAlign = TextAlign.Start,
                maxLines = 1
            )
            AutoSizeText(historyDateText(entry.finishedAtEpochMillis), color = Color.LightGray,
                maxFontSize = 9.sp, fontWeight = FontWeight.Normal, fillMaxWidth = false,
                modifier = Modifier.padding(start = 4.dp), fallbackLines = 1)
        }
        AutoSizeText(historyResultText(entry), color = Color.White, maxFontSize = 14.sp,
            textAlign = TextAlign.Start, maxLines = 2)
        val statsText = stringResource(R.string.history_stats_format, entry.totalGamesPlayed,
            entry.totalPointsPlayed, historyDurationText(entry))
        val hrText = entry.averageHeartRate?.takeIf { it > 0 }?.let { " · $it bpm" } ?: ""
        AutoSizeText(statsText + hrText + (entry.maximumHeartRate?.takeIf { it > 0 }?.let { " · Max $it bpm" } ?: ""),
            color = Color.LightGray, maxFontSize = 9.5.sp, minFontSize = 8.5.sp, fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start, maxLines = 4)
        AutoSizeText(
            text = entry.steps?.let { stringResource(R.string.steps_count, it) }
                ?: stringResource(R.string.steps_unavailable),
            color = Color.LightGray,
            maxFontSize = 9.5.sp,
            minFontSize = 8.5.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            maxLines = 3
        )
    }
}

@WearPreviewDevices
@Composable
private fun MatchHistoryScreenPreview() {
    PadelScoreTheme {
        MatchHistoryScreen(entries = emptyList(), onDeleteMatch = {}, onBack = {})
    }
}
