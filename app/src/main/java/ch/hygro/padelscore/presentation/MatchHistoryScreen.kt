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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.model.MatchHistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MatchHistoryScreen(entries: List<MatchHistoryEntry>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            "GESPIELTE MATCHES",
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (entries.isEmpty()) {
            Text(
                "NOCH KEINE MATCHES",
                color = Color.LightGray,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        } else {
            entries.forEach { HistoryEntryCard(it) }
        }
        Box(
            modifier = Modifier.fillMaxWidth(0.72f).height(31.dp)
                .background(Color(0xFF3A3A3A), RoundedCornerShape(16.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "ZURÜCK",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: MatchHistoryEntry) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF292929), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (entry.weWon) "GEWONNEN" else "VERLOREN",
                color = if (entry.weWon) Color(0xFF66E59A) else Color(0xFFFF7B72),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                historyDateText(entry.finishedAtEpochMillis),
                color = Color.LightGray,
                fontSize = 7.sp
            )
        }
        Text(
            historyResultText(entry),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
        Text(
            "${entry.totalGamesPlayed} Games · ${entry.totalPointsPlayed} Punkte · ${historyDurationText(entry)}",
            color = Color.LightGray,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            maxLines = 1
        )
    }
}

private fun historyResultText(entry: MatchHistoryEntry): String {
    val sets = entry.completedSets.joinToString("  ") {
        "${it.opponentGames}:${it.ourGames}"
    }
    val matchTieBreak = if (
        entry.opponentMatchTieBreakPoints > 0 ||
        entry.ourMatchTieBreakPoints > 0
    ) {
        "MTB ${entry.opponentMatchTieBreakPoints}:${entry.ourMatchTieBreakPoints}"
    } else {
        ""
    }
    return listOf(sets, matchTieBreak)
        .filter { it.isNotBlank() }
        .joinToString("  ")
        .ifBlank { "Sätze ${entry.opponentSets}:${entry.ourSets}" }
}

private val historyDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.getDefault())
        .withZone(ZoneId.of("Europe/Zurich"))

private fun historyDateText(epochMillis: Long): String =
    historyDateFormatter.format(Instant.ofEpochMilli(epochMillis))

private fun historyDurationText(entry: MatchHistoryEntry): String {
    val seconds = (
            (entry.finishedAtEpochMillis - entry.startedAtEpochMillis) / 1000L
            ).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return if (hours > 0L) {
        "%d:%02d h".format(hours, minutes)
    } else {
        "%d min".format(minutes)
    }
}
