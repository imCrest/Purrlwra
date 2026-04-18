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
            Color(0xFF261F58),
            Color(0xFF302A6D),
            Color(0xFF241F52)
        )
    )

    val panelGradient = Brush.linearGradient(
        listOf(
            Color(0xFF5C4B99),
            Color(0xFF322B5E),
            Color(0xFF1B1836)
        )
    )

    val glowPrimary = Color(0xFF8C7BFF)
    val glowSecondary = Color(0xFF5FD8FF)
    val iconTint = Color.White
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
