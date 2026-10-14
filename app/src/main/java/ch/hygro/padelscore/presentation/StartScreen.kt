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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun StartScreen(
    initialConfiguration: MatchConfiguration,
    isSupporterActive: Boolean,
    onStartMatch: (MatchConfiguration) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSupporter: () -> Unit
) {
    var scoringMode by remember {
        mutableStateOf(initialConfiguration.scoringMode)
    }
    var matchFormat by remember {
        mutableStateOf(initialConfiguration.matchFormat)
    }
    var shortSet by remember {
        mutableStateOf(initialConfiguration.gamesRequiredToWinSet == 4)
    }
    var firstServingTeam by remember {
        mutableStateOf(initialConfiguration.firstServingTeam)
    }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 1.dp, bottom = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.new_match),
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                val itemScope = this
                Row(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.90f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ConfigurationField(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.scoring_mode),
                        value = scoringModeLabel(scoringMode),
                        onClick = {
                            scoringMode = when (scoringMode) {
                                ScoringMode.CLASSIC_ADVANTAGE -> ScoringMode.GOLDEN_POINT
                                ScoringMode.GOLDEN_POINT -> ScoringMode.STAR_POINT
                                ScoringMode.STAR_POINT -> ScoringMode.CLASSIC_ADVANTAGE
                            }
                        }
                    )

                    ConfigurationField(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.match_format),
                        value = matchFormatLabel(matchFormat),
                        onClick = {
                            matchFormat = when (matchFormat) {
                                MatchFormat.ONE_SET -> MatchFormat.BEST_OF_THREE
                                MatchFormat.BEST_OF_THREE -> MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK
                                MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK -> MatchFormat.ONE_SET
                            }
                        }
                    )
                }
            }

            item {
                val itemScope = this
                Row(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.90f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ConfigurationField(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.set_format),
                        value = if (shortSet) {
                            stringResource(R.string.short_set_label)
                        } else {
                            stringResource(R.string.normal_set_label)
                        },
                        onClick = {
                            shortSet = !shortSet
                        }
                    )

                    ConfigurationField(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.first_serve),
                        value = if (firstServingTeam == Team.US) {
                            stringResource(R.string.bottom)
                        } else {
                            stringResource(R.string.top)
                        },
                        onClick = {
                            firstServingTeam = firstServingTeam.other()
                        }
                    )
                }
            }

            item {
                val itemScope = this
                Row(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.90f else 0.92f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 1.dp, bottom = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StartActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.matches_history),
                        backgroundColor = Color(0xFF3A3A3A),
                        onClick = onOpenHistory
                    )

                    StartActionButton(
                        modifier = Modifier.weight(1f),
                        text = if (isSupporterActive) {
                            stringResource(R.string.supporter_active)
                        } else {
                            stringResource(R.string.supporter_inactive)
                        },
                        backgroundColor = if (isSupporterActive) {
                            Color(0xFF163D29)
                        } else {
                            Color(0xFF3A3A3A)
                        },
                        contentColor = if (isSupporterActive) {
                            Color(0xFF66E59A)
                        } else {
                            Color.White
                        },
                        onClick = onOpenSupporter
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.90f else 0.92f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 1.dp, bottom = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StartActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.start),
                        backgroundColor = Color(0xFF195F3B),
                        onClick = {
                            val configuration = if (shortSet) {
                                MatchConfiguration.shortSet(
                                    scoringMode,
                                    matchFormat,
                                    firstServingTeam
                                )
                            } else {
                                MatchConfiguration.normalSet(
                                    scoringMode,
                                    matchFormat,
                                    firstServingTeam
                                )
                            }
                            onStartMatch(configuration)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartActionButton(
    modifier: Modifier,
    text: String,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(25.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ConfigurationField(
    modifier: Modifier,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(
                color = Color(0xFF292929),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.LightGray,
                fontSize = 6.sp,
                lineHeight = 6.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = value,
                color = Color(0xFFFFD54F),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun scoringModeLabel(mode: ScoringMode): String {
    return when (mode) {
        ScoringMode.CLASSIC_ADVANTAGE -> {
            stringResource(R.string.advantage)
        }

        ScoringMode.GOLDEN_POINT -> {
            stringResource(R.string.golden_point)
        }

        ScoringMode.STAR_POINT -> {
            stringResource(R.string.star_point)
        }
    }
}

@Composable
private fun matchFormatLabel(format: MatchFormat): String {
    return when (format) {
        MatchFormat.ONE_SET -> {
            stringResource(R.string.one_set)
        }

        MatchFormat.BEST_OF_THREE -> {
            stringResource(R.string.best_of_three)
        }

        MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK -> {
            stringResource(R.string.two_sets_match_tb)
        }
    }
}

@WearPreviewDevices
@Composable
private fun StartScreenPreview() {
    PadelScoreTheme {
        StartScreen(
            initialConfiguration = MatchConfiguration.normalSet(),
            isSupporterActive = false,
            onStartMatch = {},
            onOpenHistory = {},
            onOpenSupporter = {}
        )
    }
}
