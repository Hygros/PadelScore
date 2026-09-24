package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import ch.hygro.padelscore.model.Team

@Composable
fun AmbientMatchScreen(
    matchState: MatchState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        AmbientTeamScore(
            points = ambientPointText(matchState, Team.OPPONENT),
            games = matchState.opponentGames,
            sets = matchState.opponentSets,
            isServing = matchState.servingTeam == Team.OPPONENT
        )

        Text(
            text = ambientStatusText(matchState),
            color = Color(0xFFBDBDBD),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        AmbientTeamScore(
            points = ambientPointText(matchState, Team.US),
            games = matchState.ourGames,
            sets = matchState.ourSets,
            isServing = matchState.servingTeam == Team.US
        )
    }
}

@Composable
private fun AmbientTeamScore(
    points: String,
    games: Int,
    sets: Int,
    isServing: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isServing) "•  $points" else points,
            color = Color(0xFFBDBDBD),
            fontSize = 35.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.games_abbr, games),
                color = Color(0xFF8A8A8A),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.sets_abbr, sets),
                color = Color(0xFF8A8A8A),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ambientPointText(
    state: MatchState,
    team: Team
): String {
    if (state.phase == MatchPhase.MATCH_TIE_BREAK) {
        return when (team) {
            Team.OPPONENT -> state.opponentMatchTieBreakPoints.toString()
            Team.US -> state.ourMatchTieBreakPoints.toString()
        }
    }

    if (state.isTieBreak) {
        return when (team) {
            Team.OPPONENT -> state.opponentTieBreakPoints.toString()
            Team.US -> state.ourTieBreakPoints.toString()
        }
    }

    if (state.advantageTeam == team) return stringResource(R.string.advantage_abbr)

    val points = when (team) {
        Team.OPPONENT -> state.opponentPoints
        Team.US -> state.ourPoints
    }

    return when (points) {
        0 -> "0"
        1 -> "15"
        2 -> "30"
        else -> "40"
    }
}

@Composable
private fun ambientStatusText(state: MatchState): String {
    return when {
        state.phase == MatchPhase.MATCH_TIE_BREAK -> stringResource(R.string.match_tie_break)
        state.isTieBreak -> stringResource(R.string.tie_break)
        state.isStarPointActive -> stringResource(R.string.star_point)
        state.isGoldenPointActive -> stringResource(R.string.golden_point)
        state.advantageTeam == Team.OPPONENT -> stringResource(R.string.advantage_top)
        state.advantageTeam == Team.US -> stringResource(R.string.advantage_bottom)
        else -> "${state.opponentGames} : ${state.ourGames}"
    }
}
