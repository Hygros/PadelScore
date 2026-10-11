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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                horizontal = 22.dp,
                vertical = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.new_match),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ConfigurationField(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.scoring_mode),
                value = scoringModeLabel(scoringMode),
                onClick = {
                    scoringMode = when (scoringMode) {
                        ScoringMode.CLASSIC_ADVANTAGE -> {
                            ScoringMode.GOLDEN_POINT
                        }

                        ScoringMode.GOLDEN_POINT -> {
                            ScoringMode.STAR_POINT
                        }

                        ScoringMode.STAR_POINT -> {
                            ScoringMode.CLASSIC_ADVANTAGE
                        }
                    }
                }
            )

            ConfigurationField(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.match_format),
                value = matchFormatLabel(matchFormat),
                onClick = {
                    matchFormat = when (matchFormat) {
                        MatchFormat.ONE_SET -> {
                            MatchFormat.BEST_OF_THREE
                        }

                        MatchFormat.BEST_OF_THREE -> {
                            MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK
                        }

                        MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK -> {
                            MatchFormat.ONE_SET
                        }
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
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

        Row(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            StartActionButton(
                modifier = Modifier.weight(1f),
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
            .height(31.dp)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 8.sp,
            lineHeight = 11.sp,
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
            .height(52.dp)
            .background(
                color = Color(0xFF292929),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 5.dp,
                vertical = 3.dp
            ),
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
                lineHeight = 7.sp,
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
