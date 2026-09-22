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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.Team

@Composable
fun StartScreen(
    initialConfiguration: MatchConfiguration,
    onStartMatch: (MatchConfiguration) -> Unit,
    onOpenHistory: () -> Unit
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
                vertical = 7.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "NEUES MATCH",
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        ConfigurationRow(
            title = "ZÄHLWEISE",
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

        ConfigurationRow(
            title = "MATCH",
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

        ConfigurationRow(
            title = "SATZ",
            value = if (shortSet) {
                "KURZ BIS 4"
            } else {
                "NORMAL BIS 6"
            },
            onClick = {
                shortSet = !shortSet
            }
        )

        ConfigurationRow(
            title = "ERSTER AUFSCHLAG",
            value = if (firstServingTeam == Team.US) {
                "UNTEN"
            } else {
                "OBEN"
            },
            onClick = {
                firstServingTeam = firstServingTeam.other()
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StartActionButton(
                modifier = Modifier.weight(1f),
                text = "SPIELE",
                backgroundColor = Color(0xFF3A3A3A),
                onClick = onOpenHistory
            )
            StartActionButton(
                modifier = Modifier.weight(1f),
                text = "START",
                backgroundColor = Color(0xFF195F3B),
                onClick = {
                    val configuration = if (shortSet) {
                        MatchConfiguration.shortSet(scoringMode, matchFormat, firstServingTeam)
                    } else {
                        MatchConfiguration.normalSet(scoringMode, matchFormat, firstServingTeam)
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
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ConfigurationRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                color = Color(0xFF292929),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 10.dp,
                vertical = 2.dp
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
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun scoringModeLabel(mode: ScoringMode): String {
    return when (mode) {
        ScoringMode.CLASSIC_ADVANTAGE -> "VORTEIL"
        ScoringMode.GOLDEN_POINT -> "GOLDEN POINT"
        ScoringMode.STAR_POINT -> "STAR POINT"
    }
}

private fun matchFormatLabel(format: MatchFormat): String {
    return when (format) {
        MatchFormat.ONE_SET -> "1 SATZ"
        MatchFormat.BEST_OF_THREE -> "BEST OF 3"
        MatchFormat.TWO_SETS_AND_MATCH_TIE_BREAK -> "2 + MATCH-TB"
    }
}
