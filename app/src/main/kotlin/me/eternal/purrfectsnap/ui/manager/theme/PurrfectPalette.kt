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
            Color(0xFF04070A),
            Color(0xFF0A1016),
            Color(0xFF030609)
        )
    )

    val panelGradient = Brush.linearGradient(
        listOf(
            Color(0xFF1B232D),
            Color(0xFF111923),
            Color(0xFF0B1117)
        )
    )

    val glowPrimary = Color(0xFFAEDFFF)
    val glowSecondary = Color(0xFFF6FBFF)
    val iconTint = Color.White
    val textPrimary = Color.White
    val textSecondary = Color(0xFFD9E5EF)
    val cardOverlayColor = Color(0xFF121922).copy(alpha = 0.94f)
    val cardOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF1A222C).copy(alpha = 0.94f),
            Color(0xFF0C1219).copy(alpha = 0.90f)
        )
    )
}
