package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchHistoryEntry
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
    var pendingDeletion by remember {
        mutableStateOf<MatchHistoryEntry?>(null)
    }

    if (pendingDeletion != null) {
        DeleteMatchConfirmation(
            entry = pendingDeletion!!,
            onConfirm = {
                val matchId = pendingDeletion?.matchId ?: return@DeleteMatchConfirmation
                pendingDeletion = null
                onDeleteMatch(matchId)
            },
            onCancel = {
                pendingDeletion = null
            }
        )
        return
    }

    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 10.dp,
                bottom = 14.dp,
                start = 8.dp,
                end = 8.dp
            ),
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
                        text = stringResource(R.string.played_matches),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (entries.isEmpty()) {
                item {
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.85f else 1f)
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_matches_yet),
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(entries, key = { it.matchId }) { entry ->
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.90f else 1f)
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 3.dp)
                    ) {
                        HistoryEntryCard(
                            entry = entry,
                            onLongClick = {
                                pendingDeletion = entry
                            }
                        )
                    }
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 8.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HistoryActionButton(
                        text = stringResource(R.string.back),
                        backgroundColor = Color(0xFF3A3A3A),
                        onClick = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteMatchConfirmation(
    entry: MatchHistoryEntry,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 10.dp,
                bottom = 14.dp,
                start = 8.dp,
                end = 8.dp
            ),
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
                        text = stringResource(R.string.delete_match_title),
                        color = Color(0xFFFF7B72),
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
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = historyResultText(entry),
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Text(
                        text = historyDateText(entry.finishedAtEpochMillis),
                        color = Color.LightGray,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.delete_match_message),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center
                    )
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
                    HistoryActionButton(
                        text = stringResource(R.string.delete),
                        backgroundColor = Color(0xFF8B2525),
                        onClick = onConfirm
                    )

                    HistoryActionButton(
                        text = stringResource(R.string.cancel),
                        backgroundColor = Color(0xFF3A3A3A),
                        onClick = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: MatchHistoryEntry,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF292929), RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entry.weWon) {
                    stringResource(R.string.won)
                } else {
                    stringResource(R.string.lost)
                },
                color = if (entry.weWon) {
                    Color(0xFF66E59A)
                } else {
                    Color(0xFFFF7B72)
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = historyDateText(entry.finishedAtEpochMillis),
                color = Color.LightGray,
                fontSize = 7.sp
            )
        }

        Text(
            text = historyResultText(entry),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )

        Text(
            text = stringResource(
                R.string.history_stats_format,
                entry.totalGamesPlayed,
                entry.totalPointsPlayed,
                historyDurationText(entry)
            ),
            color = Color.LightGray,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun HistoryActionButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.76f)
            .height(31.dp)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun historyResultText(entry: MatchHistoryEntry): String {
    val sets = entry.completedSets.joinToString("  ") {
        "${it.opponentGames}:${it.ourGames}"
    }
    val matchTieBreak = if (
        entry.opponentMatchTieBreakPoints > 0 ||
        entry.ourMatchTieBreakPoints > 0
    ) {
        stringResource(
            R.string.match_tie_break_result,
            entry.opponentMatchTieBreakPoints,
            entry.ourMatchTieBreakPoints
        )
    } else {
        ""
    }

    return listOf(sets, matchTieBreak)
        .filter { it.isNotBlank() }
        .joinToString("  ")
        .ifBlank {
            stringResource(
                R.string.sets_result,
                entry.opponentSets,
                entry.ourSets
            )
        }
}

@Composable
private fun historyDateText(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter
            .ofPattern("dd.MM.yy HH:mm", locale)
            .withZone(ZoneId.of("Europe/Zurich"))
    }
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
private fun historyDurationText(entry: MatchHistoryEntry): String {
    val seconds = (
        (entry.finishedAtEpochMillis - entry.startedAtEpochMillis) / 1000L
    ).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L

    return if (hours > 0L) {
        stringResource(R.string.hours_format, hours, minutes)
    } else {
        stringResource(R.string.minutes_format, minutes)
    }
}

@WearPreviewDevices
@Composable
private fun MatchHistoryScreenPreview() {
    PadelScoreTheme {
        MatchHistoryScreen(
            entries = emptyList(),
            onDeleteMatch = {},
            onBack = {}
        )
    }
}
