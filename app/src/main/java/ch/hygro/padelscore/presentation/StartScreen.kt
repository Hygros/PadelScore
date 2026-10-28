package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
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
import ch.hygro.padelscore.presentation.theme.ColorPrimary
import ch.hygro.padelscore.presentation.theme.ColorSecondary
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun StartScreen(
    initialConfiguration: MatchConfiguration,
    isSupporterActive: Boolean,
    onStartMatch: (MatchConfiguration) -> Unit,
    onConfigurationChanged: (MatchConfiguration) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSupporter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPadelPong: () -> Unit = {}
) {
    var scoringMode by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.scoringMode)
    }
    var matchFormat by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.matchFormat)
    }
    var shortSet by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.gamesRequiredToWinSet == 4)
    }
    var firstServingTeam by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.firstServingTeam)
    }

    LaunchedEffect(scoringMode, matchFormat, shortSet, firstServingTeam) {
        val draft = if (shortSet) {
            MatchConfiguration.shortSet(scoringMode, matchFormat, firstServingTeam)
        } else {
            MatchConfiguration.normalSet(scoringMode, matchFormat, firstServingTeam)
        }
        if (draft != initialConfiguration) onConfigurationChanged(draft)
    }
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val haptic = LocalHapticFeedback.current
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastSecretTapMillis by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    val now = android.os.SystemClock.elapsedRealtime()

                                    // Easter-Egg-Einstellungen
                                    val requiredTaps = 7
                                    val maxTapInterval = 300L

                                    // Aufeinanderfolgende Tipps zählen
                                    secretTapCount = if (
                                        now - lastSecretTapMillis <= maxTapInterval
                                    ) {
                                        secretTapCount + 1
                                    } else {
                                        1
                                    }

                                    lastSecretTapMillis = now

                                    // Minispiel nach sieben Tipps öffnen
                                    if (secretTapCount >= requiredTaps) {
                                        secretTapCount = 0
                                        onOpenPadelPong()
                                    }
                                }
                            )
                        },
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
                        value = scoringModeFullName(scoringMode),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shortSet = !shortSet
                        }
                    )

                    ConfigurationField(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.first_serve),
                        value = if (firstServingTeam == Team.US) {
                            stringResource(R.string.our_team)
                        } else {
                            stringResource(R.string.opponent_team)
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            firstServingTeam = firstServingTeam.other()
                        }
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
                    Button(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = Color.White
                        ),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                    ) {
                        Text(
                            text = stringResource(R.string.start),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                    Button(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSecondary,
                            contentColor = Color.White
                        ),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.settings),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSecondary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenHistory()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.matches_history),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSupporterActive) ColorPrimary else ColorSecondary,
                            contentColor = if (isSupporterActive) Color(0xFF66E59A) else Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSupporter()
                        }
                    ) {
                        Text(
                            text = if (isSupporterActive) {
                                stringResource(R.string.supporter_active)
                            } else {
                                stringResource(R.string.supporter_inactive)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
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
            .height(48.dp)
            .background(
                color = Color(0xFF292929),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.LightGray,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = value,
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
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
            onConfigurationChanged = {},
            onOpenHistory = {},
            onOpenSupporter = {},
            onOpenSettings = {}
        )
    }
}
