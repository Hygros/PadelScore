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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchPhase
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
            text = stringResource(R.string.ongoing_match),
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
            text = stringResource(R.string.resume),
            backgroundColor = Color(0xFF195F3B),
            onClick = onContinueMatch
        )

        ActionButton(
            text = stringResource(R.string.discard),
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

@Composable
private fun matchSummary(state: MatchState): String {
    val setScore = stringResource(
        R.string.sets_result,
        state.opponentSets,
        state.ourSets
    )

    return when {
        state.isTieBreak -> {
            "$setScore  " + stringResource(
                R.string.tie_break_result,
                state.opponentTieBreakPoints,
                state.ourTieBreakPoints
            )
        }

        state.phase == MatchPhase.MATCH_TIE_BREAK -> {
            "$setScore  " + stringResource(
                R.string.match_tie_break_result,
                state.opponentMatchTieBreakPoints,
                state.ourMatchTieBreakPoints
            )
        }

        else -> {
            "$setScore  " + stringResource(
                R.string.games_result,
                state.opponentGames,
                state.ourGames
            )
        }
    }
}
