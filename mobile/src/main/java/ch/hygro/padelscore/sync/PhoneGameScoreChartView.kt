package ch.hygro.padelscore.sync

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min

class PhoneGameScoreChartView(context: Context) : View(context) {

    private var points: List<PhoneGamePoint> = emptyList()

    private val density = resources.displayMetrics.density

    private val baseLabelTextSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        11f,
        resources.displayMetrics
    )

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = baseLabelTextSize
        color = Color.rgb(64, 78, 92)
    }

    /** Draws [text] at the given anchor, shrinking the font so it never exceeds [maxWidth]. */
    private fun drawFittedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float) {
        labelPaint.textSize = baseLabelTextSize
        val textWidth = labelPaint.measureText(text)
        if (textWidth > maxWidth && maxWidth > 0f) {
            val minSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 6f, resources.displayMetrics)
            labelPaint.textSize = max(baseLabelTextSize * maxWidth / textWidth, minSize)
        }
        canvas.drawText(text, x, y, labelPaint)
        labelPaint.textSize = baseLabelTextSize
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(212, 221, 230)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(148, 163, 180)
        strokeWidth = 1.4f * density
        style = Paint.Style.STROKE
    }

    private val usLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_US
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val oppLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_OPP
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val usThinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_US
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }

    private val oppThinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_OPP
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }

    private val usFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_US
        style = Paint.Style.FILL
    }

    private val oppFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEAM_OPP
        style = Paint.Style.FILL
    }

    private val arcRect = RectF()

    fun setPoints(newPoints: List<PhoneGamePoint>) {
        points = newPoints
        updateAccessibilityDescription()
        invalidate()
    }

    private fun updateAccessibilityDescription() {
        if (points.isEmpty()) {
            contentDescription = null
            return
        }
        val numeric = points.first().numeric
        val pointsDescription = points.joinToString("; ") { point ->
            context.getString(R.string.chart_a11y_point, point.number, point.ourScore, point.opponentScore)
        }
        contentDescription = if (numeric) {
            context.getString(
                R.string.chart_a11y_header_numeric,
                context.getString(R.string.chart_tie_break_points),
                pointsDescription
            )
        } else {
            context.getString(
                R.string.chart_a11y_header_regular,
                pointsDescription
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (190 * density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPoints = points
        if (currentPoints.isEmpty()) return

        val numeric = currentPoints.first().numeric
        val maximum = if (numeric) {
            max(
                1,
                currentPoints.maxOf {
                    max(
                        PhoneMatchHistoryParser.chartScoreLevel(it.ourScore, true) ?: 0,
                        PhoneMatchHistoryParser.chartScoreLevel(it.opponentScore, true) ?: 0
                    )
                }
            )
        } else {
            5
        }
        val yTicks = if (numeric) listOf(0, maximum / 2, maximum).distinct() else (0..5).toList()
        val levelsList = listOf("0", "15", "30", "40", context.getString(R.string.adv_abbr), context.getString(R.string.game_label))
        fun tickText(tick: Int) = if (numeric) tick.toString() else levelsList[tick]

        // Make room for the widest y tick label (e.g. translated "game") instead of cutting it off.
        labelPaint.textSize = baseLabelTextSize
        val widestTick = yTicks.maxOf { labelPaint.measureText(tickText(it)) }
        val left = max(58f * density, 32f * density + widestTick)
        val right = width - 18f * density
        val top = 12f * density
        val bottom = height - 40f * density
        if (right <= left || bottom <= top) return

        val firstNumber = currentPoints.first().number
        val lastNumber = currentPoints.last().number
        val span = max(1, lastNumber - firstNumber)

        fun x(number: Int): Float = if (firstNumber == lastNumber) {
            (left + right) / 2f
        } else {
            left + (number - firstNumber).toFloat() / span * (right - left)
        }

        fun y(value: Int): Float = bottom - value.toFloat() / maximum * (bottom - top)

        val yAxisLabel = if (numeric) {
            context.getString(R.string.chart_tie_break_points)
        } else {
            context.getString(R.string.chart_game_score)
        }
        val xAxisLabel = context.getString(R.string.chart_match_point_number)

        val saved = canvas.save()
        try {
            val centerY = (top + bottom) / 2f
            canvas.rotate(-90f, 12f * density, centerY)
            labelPaint.textAlign = Paint.Align.CENTER
            drawFittedText(
                canvas,
                yAxisLabel,
                12f * density,
                centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                bottom - top
            )
        } finally {
            canvas.restoreToCount(saved)
        }

        labelPaint.textAlign = Paint.Align.CENTER
        drawFittedText(
            canvas,
            xAxisLabel,
            (left + right) / 2f,
            bottom + 34f * density,
            width - 16f * density
        )

        yTicks.forEach { tick ->
            val yPos = y(tick)
            canvas.drawLine(left, yPos, right, yPos, gridPaint)
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                tickText(tick),
                left - 8f * density,
                yPos - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint
            )
        }

        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val tickIndices = listOf(0, (currentPoints.size - 1) / 2, currentPoints.lastIndex).distinct()
        tickIndices.forEach { index ->
            val number = currentPoints[index].number
            val xPos = x(number)
            canvas.drawLine(xPos, bottom, xPos, bottom + 5f * density, axisPaint)
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(number.toString(), xPos, bottom + 18f * density, labelPaint)
        }

        val splitOffset = 1f * density
        currentPoints.forEachIndexed { index, point ->
            val valUs = PhoneMatchHistoryParser.recordedScore(currentPoints, index, true)
                ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
            val valOpp = PhoneMatchHistoryParser.recordedScore(currentPoints, index, false)
                ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }

            if (index > 0 && point.number == currentPoints[index - 1].number + 1) {
                val previous = currentPoints[index - 1]
                val prevValUs = PhoneMatchHistoryParser.recordedScore(currentPoints, index - 1, true)
                    ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
                val prevValOpp = PhoneMatchHistoryParser.recordedScore(currentPoints, index - 1, false)
                    ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }

                if (prevValUs != null && prevValOpp != null && prevValUs == prevValOpp) {
                    val yPos = y(prevValUs)
                    val startX = x(previous.number)
                    val endX = x(point.number)
                    canvas.drawLine(startX, yPos - splitOffset, endX, yPos - splitOffset, usThinPaint)
                    canvas.drawLine(startX, yPos + splitOffset, endX, yPos + splitOffset, oppThinPaint)
                } else {
                    if (prevValUs != null) {
                        canvas.drawLine(x(previous.number), y(prevValUs), x(point.number), y(prevValUs), usLinePaint)
                    }
                    if (prevValOpp != null) {
                        canvas.drawLine(x(previous.number), y(prevValOpp), x(point.number), y(prevValOpp), oppLinePaint)
                    }
                }

                if (valUs != null && prevValUs != null && valUs != prevValUs) {
                    canvas.drawLine(x(point.number), y(prevValUs), x(point.number), y(valUs), usLinePaint)
                }
                if (valOpp != null && prevValOpp != null && valOpp != prevValOpp) {
                    canvas.drawLine(x(point.number), y(prevValOpp), x(point.number), y(valOpp), oppLinePaint)
                }
            } else if (index == 0) {
                if (valUs != null && valOpp != null && valUs == valOpp && valUs != 0) {
                    val xPos = x(point.number)
                    canvas.drawLine(xPos - splitOffset, y(0), xPos - splitOffset, y(valUs), usThinPaint)
                    canvas.drawLine(xPos + splitOffset, y(0), xPos + splitOffset, y(valOpp), oppThinPaint)
                } else {
                    if (valUs != null && valUs != 0) {
                        canvas.drawLine(x(point.number), y(0), x(point.number), y(valUs), usLinePaint)
                    }
                    if (valOpp != null && valOpp != 0) {
                        canvas.drawLine(x(point.number), y(0), x(point.number), y(valOpp), oppLinePaint)
                    }
                }
            }
        }

        val radius = 4f * density
        currentPoints.forEachIndexed { index, point ->
            val valUs = PhoneMatchHistoryParser.recordedScore(currentPoints, index, true)
                ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
            val valOpp = PhoneMatchHistoryParser.recordedScore(currentPoints, index, false)
                ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
            val hasContiguousPrevious = index > 0 && point.number == currentPoints[index - 1].number + 1
            val prevValUs = if (hasContiguousPrevious) {
                PhoneMatchHistoryParser.recordedScore(currentPoints, index - 1, true)
                    ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
            } else null
            val prevValOpp = if (hasContiguousPrevious) {
                PhoneMatchHistoryParser.recordedScore(currentPoints, index - 1, false)
                    ?.let { PhoneMatchHistoryParser.chartScoreLevel(it, numeric) }
            } else null

            fun liesOnSegment(value: Int, start: Int?, end: Int?): Boolean {
                return !(start == null || end == null) && value in min(start, end)..max(start, end)
            }

            fun drawSplitPoint(px: Float, py: Float) {
                arcRect.set(px - radius, py - radius, px + radius, py + radius)
                canvas.drawArc(arcRect, 135f, 180f, true, usFillPaint)
                canvas.drawArc(arcRect, -45f, 180f, true, oppFillPaint)
            }

            val px = x(point.number)
            if (valUs != null && valOpp != null && valUs == valOpp) {
                drawSplitPoint(px, y(valUs))
            } else {
                if (valUs != null) {
                    val py = y(valUs)
                    if (liesOnSegment(valUs, prevValOpp, valOpp)) {
                        drawSplitPoint(px, py)
                    } else {
                        canvas.drawCircle(px, py, radius, usFillPaint)
                    }
                }
                if (valOpp != null) {
                    val py = y(valOpp)
                    if (liesOnSegment(valOpp, prevValUs, valUs)) {
                        drawSplitPoint(px, py)
                    } else {
                        canvas.drawCircle(px, py, radius, oppFillPaint)
                    }
                }
            }
        }
    }

    companion object {
        val COLOR_TEAM_US = Color.rgb(21, 94, 179)
        val COLOR_TEAM_OPP = Color.rgb(209, 59, 48)
        val COLOR_WON = Color.rgb(22, 128, 74)
        val COLOR_LOST = Color.rgb(201, 52, 43)
        val COLOR_TIED = Color.rgb(176, 104, 14)
    }
}
