package cock.crest.purrfectsnap.lite.ui.manager.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Centralized palette for the premium PurrfectSnap look.
 * Avoids relying on MaterialTheme for tinting so we can keep a consistent brand glow everywhere.
 */
object PurrfectPalette {
    val backgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF09141C),
            Color(0xFF102430),
            Color(0xFF081017)
        )
    )

    val panelGradient = Brush.linearGradient(
        listOf(
            Color(0xFF163344),
            Color(0xFF0E1E2A),
            Color(0xFF0A131A)
        )
    )

    val glowPrimary = Color(0xFF43D5C5)
    val glowSecondary = Color(0xFFFFC96C)
    val iconTint = Color.White
    val textPrimary = Color.White
    val textSecondary = Color(0xFFD2E8E4)
    val cardOverlayColor = Color(0xFF11212C).copy(alpha = 0.95f)
    val cardOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF11212C).copy(alpha = 0.95f),
            Color(0xFF09141C).copy(alpha = 0.92f)
        )
    )
}
