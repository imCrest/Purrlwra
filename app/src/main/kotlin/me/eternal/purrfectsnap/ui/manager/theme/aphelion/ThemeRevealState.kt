package cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Carries all data needed to execute one theme reveal transition.
 */
data class ThemeRevealRequest(
    val newThemeId: String,
    val originCenter: Offset,
    val oldThemeBitmap: android.graphics.Bitmap?,
    val id: Long = System.currentTimeMillis()
)

/**
 * Observable state that lives on the [Navigation] instance.
 * Optimized for stability during rapid toggle events.
 */
class ThemeRevealState {

    var pendingReveal by mutableStateOf<ThemeRevealRequest?>(null)
        private set

    /**
     * Requests a new theme reveal animation.
     * Always starts a new reveal immediately, even if one is already in progress.
     */
    fun requestReveal(
        newThemeId: String,
        originCenter: Offset,
        bitmap: android.graphics.Bitmap?
    ) {
        // Clean up the old one first to prevent memory leaks and "dead periods"
        val oldBitmap = pendingReveal?.oldThemeBitmap
        if (oldBitmap?.isRecycled == false) {
            oldBitmap.recycle()
        }

        // Immediately update with the new request ID to force a fresh animation
        pendingReveal = ThemeRevealRequest(
            newThemeId = newThemeId,
            originCenter = originCenter,
            oldThemeBitmap = bitmap,
            id = System.currentTimeMillis() // Unique ID ensures fresh start
        )
    }

    /** Called by the overlay composable once the animation has fully completed. */
    fun clearReveal() {
        val oldBitmap = pendingReveal?.oldThemeBitmap
        pendingReveal = null
        
        // Manual memory management for the heavy screenshot bitmap
        if (oldBitmap?.isRecycled == false) {
            oldBitmap.recycle()
        }
    }
}
