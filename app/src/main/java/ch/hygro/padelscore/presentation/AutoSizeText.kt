package ch.hygro.padelscore.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.LocalTextStyle

val CompactButtonContentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/** Smallest font size [AutoSizeText] will ever use. */
val AbsoluteMinFontSize: TextUnit = 6.sp

private data class TextFit(val fontSize: TextUnit, val maxLines: Int, val ellipsize: Boolean)

/**
 * Text composable that never cuts off a translated label while keeping a readable font size.
 *
 * Strategy for [maxLines] == 1 (labels, buttons):
 *  1. Shrink on a single line from [maxFontSize] down to [minFontSize] (preferred minimum).
 *  2. If that is not enough and [fallbackLines] > 1, wrap onto up to [fallbackLines] lines
 *     at a readable size (>= [minFontSize] first, then down to [AbsoluteMinFontSize] if a single
 *     word is wider than the container), never breaking inside a word.
 *  3. Shrink on a single line down to [AbsoluteMinFontSize].
 *  4. Last resort: let the text wrap on as many lines as needed at [minFontSize] (or the largest
 *     size where every word fits horizontally), or ellipsize if [ellipsize].
 *
 * Strategy for [maxLines] > 1 (descriptions, paragraphs):
 *  1. Shrink from [maxFontSize] down to [minFontSize] until the text fits into [maxLines].
 *  2. If the text needs more than [maxLines] lines at [minFontSize], keep the readable font size
 *     ([minFontSize], or the largest size where no single word overflows horizontally) and expand
 *     the line count instead of shrinking the paragraph to an unreadable size.
 *
 * Set [ellipsize] only for user generated content (e.g. team names).
 *
 * The text is measured and drawn by a plain [Layout] (no subcomposition), so it also works inside
 * components that query intrinsic sizes, such as Wear Material buttons.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 12.sp,
    minFontSize: TextUnit = 8.5.sp,
    stepSize: TextUnit = 0.5.sp,
    fontWeight: FontWeight? = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fallbackLines: Int = 2,
    ellipsize: Boolean = false,
    style: TextStyle = LocalTextStyle.current
) {
    val resolvedColor = color.takeOrElse {
        style.color.takeOrElse { LocalContentColor.current }
    }
    val effectiveLineHeight = if (lineHeight != TextUnit.Unspecified) {
        lineHeight
    } else if (maxLines > 1 || fallbackLines > 1) {
        1.18.em
    } else {
        TextUnit.Unspecified
    }
    val mergedStyle = style.merge(
        TextStyle(
            color = resolvedColor,
            fontSize = maxFontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = effectiveLineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val layouter = remember(
        text, mergedStyle, maxLines, fallbackLines, ellipsize,
        maxFontSize, minFontSize, stepSize, density, layoutDirection, fillMaxWidth
    ) {
        AutoSizeLayouter(
            measurer = textMeasurer,
            text = text,
            style = mergedStyle,
            density = density,
            layoutDirection = layoutDirection,
            maxFont = maxFontSize.value,
            preferredMin = minFontSize.value,
            step = stepSize.value.coerceAtLeast(0.1f),
            maxLines = maxLines,
            fallbackLines = fallbackLines,
            ellipsize = ellipsize,
            fillMaxWidth = fillMaxWidth
        )
    }

    Layout(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind {
                layouter.measured?.let { drawText(it, topLeft = Offset.Zero) }
            },
        measurePolicy = layouter
    )
}

/** Measures, sizes and caches the text layout for [AutoSizeText]. */
private class AutoSizeLayouter(
    private val measurer: TextMeasurer,
    private val text: String,
    private val style: TextStyle,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val maxFont: Float,
    private val preferredMin: Float,
    private val step: Float,
    private val maxLines: Int,
    private val fallbackLines: Int,
    private val ellipsize: Boolean,
    private val fillMaxWidth: Boolean
) : MeasurePolicy {

    /** Layout produced by the latest measure pass; drawn as is. */
    var measured: TextLayoutResult? = null
        private set

    private val cache = HashMap<Int, TextLayoutResult>()

    private fun layoutFor(maxWidthPx: Int): TextLayoutResult = cache.getOrPut(maxWidthPx) {
        val unbounded = maxWidthPx == Constraints.Infinity
        val fit = if (unbounded) {
            TextFit(maxFont.sp, maxLines, false)
        } else {
            computeFit(
                measurer = measurer,
                text = text,
                style = style,
                widthPx = maxWidthPx,
                density = density,
                layoutDirection = layoutDirection,
                maxFont = maxFont,
                preferredMin = preferredMin,
                step = step,
                maxLines = maxLines,
                fallbackLines = fallbackLines,
                ellipsize = ellipsize
            )
        }
        measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(fontSize = fit.fontSize),
            overflow = if (fit.ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip,
            softWrap = true,
            maxLines = fit.maxLines,
            // A min width equal to the max width makes TextAlign (center/end) work as in BasicText.
            constraints = if (!unbounded && fillMaxWidth) {
                Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx)
            } else {
                Constraints(maxWidth = maxWidthPx)
            },
            layoutDirection = layoutDirection,
            density = density
        )
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val result = layoutFor(maxWidthPx)
        measured = result
        val width = if (fillMaxWidth && constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            result.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        }
        val height = result.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {}
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int {
        val longestWord = text.split(Regex("\\s+|(?<=[-/])")).maxByOrNull { it.length } ?: text
        return measurer.measure(
            text = AnnotatedString(longestWord),
            style = style.copy(fontSize = AbsoluteMinFontSize),
            softWrap = false,
            maxLines = 1,
            layoutDirection = layoutDirection,
            density = this@AutoSizeLayouter.density
        ).size.width
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int = measurer.measure(
        text = AnnotatedString(text),
        style = style.copy(fontSize = maxFont.sp),
        softWrap = false,
        maxLines = 1,
        layoutDirection = layoutDirection,
        density = this@AutoSizeLayouter.density
    ).size.width

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int = layoutFor(width).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int = layoutFor(width).size.height
}

private fun computeFit(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
    density: Density,
    layoutDirection: LayoutDirection,
    maxFont: Float,
    preferredMin: Float,
    step: Float,
    maxLines: Int,
    fallbackLines: Int,
    ellipsize: Boolean
): TextFit {
    val constraints = Constraints(maxWidth = widthPx)
    val annotated = AnnotatedString(text)
    // Split on whitespace and after hyphens/slashes, which are valid line-break points.
    val words = text.split(Regex("\\s+|(?<=[-/])")).filter { it.isNotEmpty() }

    fun measure(size: Float, softWrap: Boolean, lines: Int) = measurer.measure(
        text = annotated,
        style = style.copy(fontSize = size.sp),
        overflow = TextOverflow.Clip,
        softWrap = softWrap,
        maxLines = lines,
        constraints = constraints,
        layoutDirection = layoutDirection,
        density = density
    )

    fun fitsSingleLine(size: Float) = !measure(size, softWrap = false, lines = 1).didOverflowWidth

    fun wordsFit(size: Float): Boolean = words.none { word ->
        measurer.measure(
            text = AnnotatedString(word),
            style = style.copy(fontSize = size.sp),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = constraints,
            layoutDirection = layoutDirection,
            density = density
        ).didOverflowWidth
    }

    fun fitsWrapped(size: Float, lines: Int): Boolean {
        // Never break inside a word: every word segment must fit on its own line.
        if (!wordsFit(size)) return false
        val result = measure(size, softWrap = true, lines = lines)
        return !result.didOverflowHeight && !result.didOverflowWidth
    }

    fun sizes(from: Float, to: Float): Sequence<Float> = generateSequence(from) { it - step }
        .takeWhile { it >= to - 0.001f }
        .map { it.coerceAtLeast(to) }

    val absMin = AbsoluteMinFontSize.value
    val maxFont = maxFont.coerceAtLeast(absMin)
    val preferredMin = preferredMin.coerceIn(absMin, maxFont)

    if (maxLines <= 1) {
        sizes(maxFont, preferredMin).firstOrNull { fitsSingleLine(it) }
            ?.let { return TextFit(it.sp, 1, false) }
        if (fallbackLines > 1) {
            sizes(maxFont, preferredMin).firstOrNull { fitsWrapped(it, fallbackLines) }
                ?.let { return TextFit(it.sp, fallbackLines, false) }
            sizes(preferredMin, absMin).firstOrNull { fitsWrapped(it, fallbackLines) }
                ?.let { return TextFit(it.sp, fallbackLines, false) }
        }
        sizes(preferredMin, absMin).firstOrNull { fitsSingleLine(it) }
            ?.let { return TextFit(it.sp, 1, false) }
        val wordFitSize = sizes(preferredMin, absMin).firstOrNull { wordsFit(it) } ?: absMin
        return if (ellipsize) {
            TextFit(wordFitSize.sp, 1, true)
        } else {
            TextFit(wordFitSize.sp, Int.MAX_VALUE, false)
        }
    }

    // Multi-line text: try fitting into maxLines down to preferredMin first.
    sizes(maxFont, preferredMin).firstOrNull { fitsWrapped(it, maxLines) }
        ?.let { return TextFit(it.sp, maxLines, false) }

    if (ellipsize) {
        sizes(preferredMin, absMin).firstOrNull { fitsWrapped(it, maxLines) }
            ?.let { return TextFit(it.sp, maxLines, false) }
        return TextFit(absMin.sp, maxLines, true)
    }

    // Keep at least preferredMin (shrinking only if a single word is wider than the container)
    // and allow as many lines as needed instead of shrinking paragraphs to 6sp.
    val readableSize = sizes(preferredMin, absMin).firstOrNull { wordsFit(it) } ?: absMin
    return TextFit(readableSize.sp, Int.MAX_VALUE, false)
}
