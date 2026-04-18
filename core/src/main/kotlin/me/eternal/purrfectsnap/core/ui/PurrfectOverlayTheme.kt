package cock.crest.purrfectsnap.lite.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle

@Immutable
object PurrfectOverlayPalette {
    val glowPrimary = Color(0xFF8C7BFF)
    val glowSecondary = Color(0xFF5FD8FF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFD9D3FF)

    val backgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF261F58),
            Color(0xFF302A6D),
            Color(0xFF241F52),
        )
    )

    val cardOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF2A2452).copy(alpha = 0.96f),
            Color(0xFF1A143A).copy(alpha = 0.92f),
        )
    )

    val cardOverlayColor = Color(0xFF2A2452).copy(alpha = 0.94f)
}

@Composable
fun PurrfectOverlayTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = PurrfectOverlayPalette.glowPrimary,
        secondary = PurrfectOverlayPalette.glowSecondary,
        background = Color.Transparent,
        surface = PurrfectOverlayPalette.cardOverlayColor,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides PurrfectOverlayPalette.textPrimary,
                LocalTextStyle provides LocalTextStyle.current.merge(
                    TextStyle(color = PurrfectOverlayPalette.textPrimary)
                )
            ) {
                content()
            }
        }
    )
}

@Composable
fun PurrfectGlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                spotColor = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.25f),
                ambientColor = PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.18f),
            )
            .clip(shape)
            .background(PurrfectOverlayPalette.cardOverlay, shape)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                            PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f),
                        )
                    )
                ),
                shape
            ),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(PurrfectOverlayPalette.cardOverlay, shape)
                .padding(16.dp)
        ) {
            Column {
                if (title != null || subtitle != null || icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (title != null) {
                                Text(
                                    text = title,
                                    color = PurrfectOverlayPalette.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    color = PurrfectOverlayPalette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Box(content = content)
            }
        }
    }
}

