package ch.hygro.padelscore.presentation

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.presentation.theme.ColorCourtChange
import ch.hygro.padelscore.presentation.theme.ColorResetButtonBg
import ch.hygro.padelscore.presentation.theme.ColorResetButtonBgHolding
import ch.hygro.padelscore.presentation.theme.ColorServeIndicator
import ch.hygro.padelscore.presentation.theme.ColorUndoButtonBgDisabled
import ch.hygro.padelscore.presentation.theme.ColorUndoButtonBgEnabled
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ScoreArea(
    modifier: Modifier,
    teamName: String,
    pointText: String,
    games: Int,
    sets: Int,
    backgroundColor: Color,
    isServing: Boolean,
    isDeuceCourt: Boolean,
    statsAtTop: Boolean,
    enabled: Boolean,
    swipeGesturesEnabled: Boolean = false,
    ambient: Boolean,
    onClick: () -> Unit
) {
    val isRound = LocalConfiguration.current.isScreenRound
    val displayedBackgroundColor = when {
        ambient -> Color.Black
        enabled -> backgroundColor
        else -> backgroundColor.copy(alpha = 0.58f)
    }
    val shape = RectangleShape
    val scoreFontSize = if (ambient) 35.sp else 55.sp

    Box(
        modifier = modifier
            .background(color = displayedBackgroundColor, shape = shape)
            .clickable(enabled = enabled && !swipeGesturesEnabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top button: statistics above the score.
            if (statsAtTop) {
                Spacer(modifier = Modifier.height(8.dp))
                ScoreStatistics(
                    games = games,
                    sets = sets,
                    ambient = ambient,
                    isRound = isRound
                )
            }

            // Center the score in the remaining space between the
            // statistics row and the inner edge of the button.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pointText,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.70f),
                    style = TextStyle(
                        fontSize = scoreFontSize,
                        lineHeight = scoreFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Bottom button: statistics below the score.
            if (!statsAtTop) {
                ScoreStatistics(
                    games = games,
                    sets = sets,
                    ambient = ambient,
                    isRound = isRound
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Serve indicator: full screen width matching CenterScoreArea horizontal coordinate system, offset by 21dp for ScoreStatistics
        if (isServing) {
            // The opponent (top) faces us, so their deuce court (their right) is on our left.
            val dotOnRight = if (statsAtTop) !isDeuceCourt else isDeuceCourt
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (statsAtTop) 21.dp else 0.dp,
                        bottom = if (!statsAtTop) 21.dp else 0.dp
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!dotOnRight) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (ambient) Color.White else ColorServeIndicator,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(82.dp))

                Box(
                    modifier = Modifier.width(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dotOnRight) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (ambient) Color.White else ColorServeIndicator,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Team name label horizontally centered with the Reset button
        if (teamName.isNotEmpty()) {
            val teamAlignment = if (statsAtTop) Alignment.BottomCenter else Alignment.TopCenter
            val teamPadding = if (statsAtTop) Modifier.padding(bottom = 0.dp) else Modifier.padding(top = 0.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(teamAlignment)
                    .then(teamPadding),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(46.dp))
                Spacer(modifier = Modifier.width(82.dp))
                Box(
                    modifier = Modifier.width(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AutoSizeText(
                        text = teamName,
                        modifier = Modifier.padding(horizontal = 3.dp),
                        color = if (ambient) Color.White else Color.White.copy(alpha = 0.55f),
                        maxFontSize = 8.sp,
                        minFontSize = AbsoluteMinFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        fallbackLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreStatistics(
    games: Int,
    sets: Int,
    ambient: Boolean = false,
    isRound: Boolean = false
) {
    Row(
        // On round screens the top/bottom edge is narrow, so keep the row inside the circle.
        modifier = Modifier.fillMaxWidth(if (isRound) 0.5f else 1f).height(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statsFontSize = if (ambient) 9.sp else if (isRound) 10.sp else 12.sp
        val statsColor = if (ambient) Color.White else Color.White.copy(alpha = 0.9f)
        AutoSizeText(
            text = stringResource(R.string.games_with_count, games),
            modifier = Modifier.weight(1f),
            color = statsColor,
            maxFontSize = statsFontSize,
            minFontSize = AbsoluteMinFontSize,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            fallbackLines = 1
        )
        Spacer(modifier = Modifier.width(10.dp))
        AutoSizeText(
            text = stringResource(R.string.sets_with_count, sets),
            modifier = Modifier.weight(1f),
            color = statsColor,
            maxFontSize = statsFontSize,
            minFontSize = AbsoluteMinFontSize,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            maxLines = 1,
            fallbackLines = 1
        )
    }
}

@Composable
fun CenterScoreArea(
    modifier: Modifier,
    matchState: MatchState,
    showCourtChange: Boolean,
    clockText: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isResetHolding by remember { mutableStateOf(false) }
    var resetPerformed by remember { mutableStateOf(false) }

    // Track hold duration to animate the progress indicator
    var holdDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isResetHolding) {
        if (isResetHolding) {
            resetPerformed = false
            holdDuration = 0L
            val startTime = SystemClock.elapsedRealtime()
            while (holdDuration < RESET_HOLD_DURATION_MILLIS && isResetHolding) {
                delay(16.milliseconds) // ~60fps update rate
                holdDuration = SystemClock.elapsedRealtime() - startTime
            }
            if (isResetHolding) {
                resetPerformed = true
                onReset()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                isResetHolding = false
            }
        } else {
            holdDuration = 0L
        }
    }

    Row(
        modifier = modifier.padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(46.dp).height(40.dp)
                .background(
                    color = if (canUndo) ColorUndoButtonBgEnabled else ColorUndoButtonBgDisabled,
                    shape = CircleShape
                )
                .clickable(enabled = canUndo, onClick = onUndo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = stringResource(R.string.undo),
                tint = if (canUndo) Color.White else Color.DarkGray,
                modifier = Modifier.padding(10.dp)
            )
        }

        Column(
            modifier = Modifier.width(82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val labelText = centerLabel(matchState, showCourtChange)
            val baseFontSize = centerLabelFontSize(matchState)
            val started = matchHasStarted(matchState)

            AutoSizeText(
                text = clockText,
                color = Color.White.copy(alpha = 0.7f),
                maxFontSize = 10.sp,
                minFontSize = AbsoluteMinFontSize,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fallbackLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.requiredWidth(94.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label ends at the center line, games start at the center line.
                Box(
                    modifier = Modifier.weight(1f).padding(end = 3.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Shrinks, then wraps (never inside a word) instead of being cut off.
                    AutoSizeText(
                        text = labelText,
                        color = if (showCourtChange) ColorCourtChange else centerLabelColor(matchState),
                        maxFontSize = baseFontSize,
                        minFontSize = 7.sp,
                        lineHeight = 1.1.em,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        fallbackLines = 2
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).padding(start = 3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AutoSizeText(
                        text = if (started) {
                            "${matchState.ourGames} : ${matchState.opponentGames}"
                        } else {
                            scoringModeLabel(matchState.configuration.scoringMode)
                        },
                        color = Color.White,
                        maxFontSize = if (started) 15.sp else 11.sp,
                        minFontSize = AbsoluteMinFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        fallbackLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier.width(46.dp).height(40.dp)
                .background(
                    color = ColorResetButtonBg,
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            resetPerformed = false
                            isResetHolding = true
                            tryAwaitRelease()
                            if (!resetPerformed) {
                                isResetHolding = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isResetHolding) {
                CircularProgressIndicator(
                    progress = { (holdDuration.toFloat() / RESET_HOLD_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = Color.White,
                        trackColor = ColorResetButtonBgHolding
                    ),
                    strokeWidth = 3.dp
                )
            }
            AutoSizeText(
                text = stringResource(R.string.reset),
                modifier = Modifier.padding(horizontal = 4.dp),
                color = Color.White,
                maxFontSize = 8.sp,
                minFontSize = AbsoluteMinFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fallbackLines = 1
            )
        }
    }
}
