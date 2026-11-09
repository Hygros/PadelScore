package ch.hygro.padelscore.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorTeamOpponentText
import ch.hygro.padelscore.presentation.theme.ColorTeamUsText

/** Recover the unchanged losing score only from a contiguous record of the same game. */
internal fun recordedScore(points: List<GamePoint>, index: Int, us: Boolean): String? {
    val point = points.getOrNull(index) ?: return null
    val score = if (us) point.ourScore else point.opponentScore
    if (score != "-") return score
    val winnerIsUs = point.scoringTeam == Team.US
    if (!point.gameEnded || us == winnerIsUs || point.numeric) return null
    val previous = points.getOrNull(index - 1) ?: return null
    if (previous.number + 1 != point.number || previous.gameNumber != point.gameNumber ||
        previous.setNumber != point.setNumber) return null
    val previousScore = if (us) previous.ourScore else previous.opponentScore
    return previousScore.takeIf { it in listOf("0", "15", "30", "40", "AD") }
}

/** Score categories are equally spaced, not numeric distances between 15/30/40. */
private fun chartScoreLevel(text: String, numeric: Boolean): Int? = if (numeric) {
    text.toIntOrNull()?.takeIf { it >= 0 }
} else {
    when (text) {
        "0" -> 0
        "15" -> 1
        "30" -> 2
        "40" -> 3
        "AD" -> 4
        "GAME" -> 5
        else -> null // '-' on the losing team's final point is not a recorded score.
    }
}

/** Show every recorded point of the selected game without paging or downsampling. */
@Composable
internal fun GameScoreChart(
    points: List<GamePoint>,
    gamePosition: String? = null,
    canPrevious: Boolean = false,
    canNext: Boolean = false,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {}
) {
    if (points.isEmpty()) return
    val firstPoint = points.first()
    val numeric = firstPoint.numeric
    val levels = listOf("0", "15", "30", "40", "AD", "GAME")
    val maximum = if (numeric) {
        maxOf(1, points.maxOf { maxOf(chartScoreLevel(it.ourScore, true) ?: 0,
            chartScoreLevel(it.opponentScore, true) ?: 0) })
    } else 5
    val yTicks = if (numeric) listOf(0, maximum / 2, maximum).distinct() else (0..5).toList()
    val density = LocalDensity.current
    val labelPixels = with(density) { 8.sp.toPx() }
    val paint = remember(labelPixels) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelPixels
            color = android.graphics.Color.rgb(225, 232, 240)
        }
    }
    val yAxisLabel = if (numeric) {
        stringResource(R.string.chart_tie_break_points)
    } else {
        stringResource(R.string.chart_game_score)
    }
    val xAxisLabel = stringResource(R.string.chart_match_point_number)
    val usShort = stringResource(R.string.our_team)
    val oppShort = stringResource(R.string.opponent_short)
    val description = "Game score after recorded rallies. match point number. " +
            (if (numeric) "tie-break points." else "0, 15, 30, 40, AD, GAME. Equal score categories.") +
            " US solid circles; OPP solid circles. " +
            points.joinToString("; ") { "Point ${it.number}: ${it.ourScore} to ${it.opponentScore}" }
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF14232C), RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        Canvas(Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = description }) {
            val left = 43.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 5.dp.toPx()
            val bottom = size.height - 27.dp.toPx()
            if (right <= left || bottom <= top) return@Canvas
            val firstNumber = points.first().number
            val lastNumber = points.last().number
            val span = maxOf(1, lastNumber - firstNumber)
            fun x(number: Int): Float = if (firstNumber == lastNumber) (left + right) / 2f
            else left + (number - firstNumber).toFloat() / span * (right - left)
            fun y(value: Int) = bottom - value.toFloat() / maximum * (bottom - top)
            // Native canvas rotation keeps the vertical title outside the plot.
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saved = native.save()
                try {
                    val centerY = (top + bottom) / 2f
                    native.rotate(-90f, 6.dp.toPx(), centerY)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    native.drawText(
                        yAxisLabel,
                        6.dp.toPx(), centerY - (paint.ascent() + paint.descent()) / 2f, paint
                    )
                } finally {
                    native.restoreToCount(saved)
                }
                paint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.nativeCanvas.drawText(
                    xAxisLabel, (left + right) / 2f,
                    bottom + 24.dp.toPx(), paint
                )
            }
            yTicks.forEach { tick ->
                drawLine(Color(0xFF45596B), Offset(left, y(tick)), Offset(right, y(tick)), 0.7.dp.toPx())
                drawIntoCanvas { canvas ->
                    paint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas.nativeCanvas.drawText(if (numeric) tick.toString() else levels[tick],
                        left - 5.dp.toPx(), y(tick) - (paint.ascent() + paint.descent()) / 2f, paint)
                }
            }
            drawLine(Color.LightGray, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
            drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
            val tickIndices = listOf(0, (points.size - 1) / 2, points.lastIndex).distinct()
            tickIndices.forEach { index ->
                val number = points[index].number
                drawLine(Color.LightGray, Offset(x(number), bottom), Offset(x(number), bottom + 3.dp.toPx()), 1.dp.toPx())
                drawIntoCanvas { canvas ->
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    canvas.nativeCanvas.drawText(number.toString(), x(number), bottom + 12.dp.toPx(), paint)
                }
            }
            // Draw lines for both series
            points.forEachIndexed { index, point ->
                val valUs = recordedScore(points, index, true)?.let { chartScoreLevel(it, numeric) }
                val valOpp = recordedScore(points, index, false)?.let { chartScoreLevel(it, numeric) }

                if (index > 0 && point.number == points[index - 1].number + 1) {
                    val previous = points[index - 1]
                    val prevValUs = recordedScore(points, index - 1, true)?.let { chartScoreLevel(it, numeric) }
                    val prevValOpp = recordedScore(points, index - 1, false)?.let { chartScoreLevel(it, numeric) }

                    // Horizontal lines
                    if (prevValUs != null && prevValOpp != null && prevValUs == prevValOpp) {
                        val y = y(prevValUs)
                        val startX = x(previous.number)
                        val endX = x(point.number)
                        drawLine(ColorTeamUsText, Offset(startX, y - 0.5.dp.toPx()), Offset(endX, y - 0.5.dp.toPx()), 1.dp.toPx())
                        drawLine(ColorTeamOpponentText, Offset(startX, y + 0.5.dp.toPx()), Offset(endX, y + 0.5.dp.toPx()), 1.dp.toPx())
                    } else {
                        if (prevValUs != null) {
                            drawLine(ColorTeamUsText, Offset(x(previous.number), y(prevValUs)), Offset(x(point.number), y(prevValUs)), 2.dp.toPx())
                        }
                        if (prevValOpp != null) {
                            drawLine(ColorTeamOpponentText, Offset(x(previous.number), y(prevValOpp)), Offset(x(point.number), y(prevValOpp)), 2.dp.toPx())
                        }
                    }

                    // Vertical lines
                    if (valUs != null && prevValUs != null && valUs != prevValUs) {
                        drawLine(ColorTeamUsText, Offset(x(point.number), y(prevValUs)), Offset(x(point.number), y(valUs)), 2.dp.toPx())
                    }
                    if (valOpp != null && prevValOpp != null && valOpp != prevValOpp) {
                        drawLine(ColorTeamOpponentText, Offset(x(point.number), y(prevValOpp)), Offset(x(point.number), y(valOpp)), 2.dp.toPx())
                    }
                } else if (index == 0) {
                    if (valUs != null && valOpp != null && valUs == valOpp && valUs != 0) {
                        drawLine(ColorTeamUsText, Offset(x(point.number) - 0.5.dp.toPx(), y(0)), Offset(x(point.number) - 0.5.dp.toPx(), y(valUs)), 1.dp.toPx())
                        drawLine(ColorTeamOpponentText, Offset(x(point.number) + 0.5.dp.toPx(), y(0)), Offset(x(point.number) + 0.5.dp.toPx(), y(valOpp)), 1.dp.toPx())
                    } else {
                        if (valUs != null && valUs != 0) {
                            drawLine(ColorTeamUsText, Offset(x(point.number), y(0)), Offset(x(point.number), y(valUs)), 2.dp.toPx())
                        }
                        if (valOpp != null && valOpp != 0) {
                            drawLine(ColorTeamOpponentText, Offset(x(point.number), y(0)), Offset(x(point.number), y(valOpp)), 2.dp.toPx())
                        }
                    }
                }
            }

            // Draw points for both series. A marker is split whenever both coloured
            // line segments occupy its position, not only when both current scores are equal.
            points.forEachIndexed { index, point ->
                val valUs = recordedScore(points, index, true)?.let { chartScoreLevel(it, numeric) }
                val valOpp = recordedScore(points, index, false)?.let { chartScoreLevel(it, numeric) }
                val hasContiguousPrevious = index > 0 &&
                        point.number == points[index - 1].number + 1
                val prevValUs = if (hasContiguousPrevious) {
                    recordedScore(points, index - 1, true)?.let { chartScoreLevel(it, numeric) }
                } else null
                val prevValOpp = if (hasContiguousPrevious) {
                    recordedScore(points, index - 1, false)?.let { chartScoreLevel(it, numeric) }
                } else null
                val radius = 2.4.dp.toPx()

                fun liesOnSegment(value: Int, start: Int?, end: Int?): Boolean {
                    return !(start == null || end == null) && value in minOf(start, end)..maxOf(start, end)
                }

                fun drawSplitPoint(position: Offset) {
                    drawArc(
                        color = ColorTeamUsText,
                        startAngle = 135f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(position.x - radius, position.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    drawArc(
                        color = ColorTeamOpponentText,
                        startAngle = -45f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(position.x - radius, position.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }
                if (valUs != null && valOpp != null && valUs == valOpp) {
                    drawSplitPoint(Offset(x(point.number), y(valUs)))
                } else {
                    if (valUs != null) {
                        val position = Offset(x(point.number), y(valUs))
                        if (liesOnSegment(valUs, prevValOpp, valOpp)) {
                            drawSplitPoint(position)
                        } else {
                            drawCircle(ColorTeamUsText, radius, position)
                        }
                    }
                    if (valOpp != null) {
                        val position = Offset(x(point.number), y(valOpp))
                        if (liesOnSegment(valOpp, prevValUs, valUs)) {
                            drawSplitPoint(position)
                        } else {
                            drawCircle(ColorTeamOpponentText, radius, position)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("$usShort ●", color = ColorTeamUsText, fontSize = 8.sp)
            Text("$oppShort ●", color = ColorTeamOpponentText, fontSize = 8.sp)
        }
        if (gamePosition != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Button(
                    modifier = Modifier.weight(1f).height(34.dp),
                    enabled = canPrevious,
                    contentPadding = CompactButtonContentPadding,
                    onClick = onPrevious
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.chart_prev),
                        maxFontSize = 10.sp,
                        minFontSize = 7.5.sp,
                        maxLines = 1
                    )
                }
                Text(gamePosition, color = Color.LightGray, fontSize = 8.sp, textAlign = TextAlign.Center)
                Button(
                    modifier = Modifier.weight(1f).height(34.dp),
                    enabled = canNext,
                    contentPadding = CompactButtonContentPadding,
                    onClick = onNext
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.chart_next),
                        maxFontSize = 10.sp,
                        minFontSize = 7.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
