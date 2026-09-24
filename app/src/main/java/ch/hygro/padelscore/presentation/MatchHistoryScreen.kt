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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchHistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = stringResource(R.string.played_matches),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matches_yet),
                color = Color.LightGray,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        } else {
            entries.forEach { entry ->
                HistoryEntryCard(
                    entry = entry,
                    onLongClick = {
                        pendingDeletion = entry
                    }
                )
            }
        }

        HistoryActionButton(
            text = stringResource(R.string.back),
            backgroundColor = Color(0xFF3A3A3A),
            onClick = onBack
        )
    }
}

@Composable
private fun DeleteMatchConfirmation(
    entry: MatchHistoryEntry,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = stringResource(R.string.delete_match_title),
            color = Color(0xFFFF7B72),
            fontSize = 13.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

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
            .fillMaxWidth(0.72f)
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

private val historyDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.getDefault())
        .withZone(ZoneId.of("Europe/Zurich"))

private fun historyDateText(epochMillis: Long): String =
    historyDateFormatter.format(Instant.ofEpochMilli(epochMillis))

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
