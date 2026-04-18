package cock.crest.purrfectsnap.lite.ui.util

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A premium marquee text component that supports continuous looping.
 * Optimized using TextMeasurer for robust width calculation.
 */
@Composable
fun PurrfectMarqueeText(
    text: String,
    style: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    textAlign: TextAlign = TextAlign.Center,
    contentAlignment: Alignment = Alignment.Center,
    delayMillis: Int = 1500,
    velocity: Dp = 30.dp,
    maxLines: Int = 1,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = contentAlignment
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = if (enabled) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = delayMillis,
                    initialDelayMillis = delayMillis,
                    velocity = velocity
                )
            } else Modifier
        )
    }
}
