package cock.crest.purrfectsnap.lite.ui.util

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A specialized modifier that tracks the height of the header (FloatingTopBar) 
 * and provides it to the caller. This eliminates repetitive onGloballyPositioned 
 * logic across all sub-pages.
 * 
 * @param onHeightChanged Callback triggered when the header height is measured.
 */
fun Modifier.headerHeightTracker(
    onHeightChanged: (Dp) -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    this.onGloballyPositioned { coordinates ->
        val heightDp = with(density) { coordinates.size.height.toDp() }
        onHeightChanged(heightDp)
    }
}
