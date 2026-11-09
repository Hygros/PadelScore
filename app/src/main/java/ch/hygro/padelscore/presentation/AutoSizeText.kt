package ch.hygro.padelscore.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.LocalTextStyle

val CompactButtonContentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/**
 * Text composable that automatically shrinks its font size down to [minFontSize] so the full label
 * fits within the available width and [maxLines] without clipping.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 12.sp,
    minFontSize: TextUnit = 7.5.sp,
    stepSize: TextUnit = 0.5.sp,
    fontWeight: FontWeight? = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    lineHeight: TextUnit = if (maxLines > 1) 1.15.em else TextUnit.Unspecified,
    softWrap: Boolean = true,
    overflow: TextOverflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current
) {
    val resolvedColor = color.takeOrElse {
        style.color.takeOrElse { LocalContentColor.current }
    }
    val mergedStyle = style.merge(
        TextStyle(
            color = resolvedColor,
            fontSize = maxFontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )
    val autoSize = remember(minFontSize, maxFontSize, stepSize) {
        TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = stepSize
        )
    }

    BasicText(
        text = text,
        modifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier,
        style = mergedStyle,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        autoSize = autoSize
    )
}
