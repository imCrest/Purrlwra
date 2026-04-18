package cock.crest.purrfectsnap.lite.common.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared palette for PurrfectSnap overlay UI (dialogs, story overlays) shown inside Snapchat.
 * Matches the PurrfectSnap manager app's premium look. Used by core module.
 */
object PurrfectOverlayPalette {
    val glowPrimary = Color(0xFF8C7BFF)
    val glowSecondary = Color(0xFF5FD8FF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFD9D3FF)
    val cardOverlayColor = Color(0xFF2A2452).copy(alpha = 0.95f)
    val cardOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF2A2452).copy(alpha = 0.95f),
            Color(0xFF1A143A).copy(alpha = 0.92f)
        )
    )
}
