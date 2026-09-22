package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.model.MatchState

@Composable
fun ResumeMatchScreen(
    matchState: MatchState,
    onContinueMatch: () -> Unit,
    onDiscardMatch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "LAUFENDES MATCH",
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = matchSummary(matchState),
            color = Color(0xFFFFD54F),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        ActionButton(
            text = "FORTSETZEN",
            backgroundColor = Color(0xFF195F3B),
            onClick = onContinueMatch
        )

        ActionButton(
            text = "VERWERFEN",
            backgroundColor = Color(0xFF8B2525),
            onClick = onDiscardMatch
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .height(39.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun matchSummary(state: MatchState): String {
    val setScore = "Sätze ${state.opponentSets}:${state.ourSets}"

    return when {
        state.isTieBreak -> {
            "$setScore  TB ${state.opponentTieBreakPoints}:${state.ourTieBreakPoints}"
        }

        state.phase == ch.hygro.padelscore.model.MatchPhase.MATCH_TIE_BREAK -> {
            "$setScore  MTB ${state.opponentMatchTieBreakPoints}:${state.ourMatchTieBreakPoints}"
        }

        else -> {
            "$setScore  Games ${state.opponentGames}:${state.ourGames}"
        }
    }
}
