package cock.crest.purrfectsnap.lite.common.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---------- Light color palette ----------
val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE9DDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF22005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1E192B)
val md_theme_light_tertiary = Color(0xFF3C5BA9)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFDBE1FF)
val md_theme_light_onTertiaryContainer = Color(0xFF001849)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFFFBFF)
val md_theme_light_onBackground = Color(0xFF1C1B1E)
val md_theme_light_surface = Color(0xFFFFFBFF)
val md_theme_light_onSurface = Color(0xFF1C1B1E)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EB)
val md_theme_light_onSurfaceVariant = Color(0xFF49454E)
val md_theme_light_outline = Color(0xFF7A757F)
val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
val md_theme_light_inverseSurface = Color(0xFF313033)
val md_theme_light_inversePrimary = Color(0xFFCFBCFF)
val md_theme_light_surfaceTint = Color(0xFF6750A4)
val md_theme_light_outlineVariant = Color(0xFFCAC4CF)
val md_theme_light_scrim = Color(0xFF000000)

// ---------- Dark color palette ----------
val md_theme_dark_primary = Color(0xFFCFBCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378A)
val md_theme_dark_onPrimaryContainer = Color(0xFFE9DDFF)
val md_theme_dark_secondary = Color(0xFFCBC2DB)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
val md_theme_dark_tertiary = Color(0xFFB3C5FF)
val md_theme_dark_onTertiary = Color(0xFF002B75)
val md_theme_dark_tertiaryContainer = Color(0xFF21428F)
val md_theme_dark_onTertiaryContainer = Color(0xFFDBE1FF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1C1B1E)
val md_theme_dark_onBackground = Color(0xFFE6E1E6)
val md_theme_dark_surface = Color(0xFF1C1B1E)
val md_theme_dark_onSurface = Color(0xFFE6E1E6)
val md_theme_dark_surfaceVariant = Color(0xFF49454E)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4CF)
val md_theme_dark_outline = Color(0xFF948F99)
val md_theme_dark_inverseOnSurface = Color(0xFF1C1B1E)
val md_theme_dark_inverseSurface = Color(0xFFE6E1E6)
val md_theme_dark_inversePrimary = Color(0xFF6750A4)
val md_theme_dark_surfaceTint = Color(0xFFCFBCFF)
val md_theme_dark_outlineVariant = Color(0xFF49454E)
val md_theme_dark_scrim = Color(0xFF000000)

// ---------- AMOLED (true black dark) palette (only crucial overrides) ----------
val md_theme_amoled_background = Color(0xFF000000)
val md_theme_amoled_surface = Color(0xFF000000)
val md_theme_amoled_onBackground = Color(0xFFE6E1E6)
val md_theme_amoled_onSurface = Color(0xFFE6E1E6)

// Material3 ColorScheme for light theme
private val LightThemeColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim
)

// Material3 ColorScheme for dark theme
private val DarkThemeColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim
)

@Composable
fun AppMaterialTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effectiveThemeMode = themeMode ?: if (isDarkTheme) ThemeMode.DARK else ThemeMode.LIGHT
    val colorScheme = when (effectiveThemeMode) {
        ThemeMode.AMOLED -> {
            val baseScheme = if (dynamicColor) dynamicDarkColorScheme(context) else DarkThemeColors
            baseScheme.copy(
                background = md_theme_amoled_background,
                onBackground = md_theme_amoled_onBackground,
                surface = md_theme_amoled_surface,
                onSurface = md_theme_amoled_onSurface
            )
        }
        ThemeMode.DARK -> if (dynamicColor) dynamicDarkColorScheme(context) else DarkThemeColors
        ThemeMode.LIGHT -> if (dynamicColor) dynamicLightColorScheme(context) else LightThemeColors
        ThemeMode.SYSTEM -> when {
            isSystemInDarkTheme() && dynamicColor -> dynamicDarkColorScheme(context)
            !isSystemInDarkTheme() && dynamicColor -> dynamicLightColorScheme(context)
            isSystemInDarkTheme() -> DarkThemeColors
            else -> LightThemeColors
        }
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(24.dp),
        small = RoundedCornerShape(24.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        content = content
    )
}
