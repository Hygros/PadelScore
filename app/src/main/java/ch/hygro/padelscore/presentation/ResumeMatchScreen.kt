package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchPhase
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun ResumeMatchScreen(
    matchState: MatchState,
    onContinueMatch: () -> Unit,
    onDiscardMatch: () -> Unit
) {
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
                        text = stringResource(R.string.ongoing_match),
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
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
                        text = matchSummary(matchState),
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 2.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.82f else 0.88f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF195F3B),
                            contentColor = Color.White
                        ),
                        onClick = onContinueMatch
                    ) {
                        Text(
                            text = stringResource(R.string.resume),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.82f else 0.88f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B2525),
                            contentColor = Color.White
                        ),
                        onClick = onDiscardMatch
                    ) {
                        Text(
                            text = stringResource(R.string.discard),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
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

@WearPreviewDevices
@Composable
private fun ResumeMatchScreenPreview() {
    PadelScoreTheme {
        ResumeMatchScreen(
            matchState = MatchState(configuration = MatchConfiguration.normalSet()),
            onContinueMatch = {},
            onDiscardMatch = {}
        )
    }
}
