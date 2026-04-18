package cock.crest.purrfectsnap.lite.ui.util

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Returns true if the user has disabled animator duration scale at the system level.
 */
fun prefersReducedMotion(context: Context): Boolean {
    return runCatching {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }.getOrDefault(false)
}

@Composable
fun rememberPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    val state = remember { mutableStateOf(prefersReducedMotion(context)) }
    LaunchedEffect(Unit) {
        state.value = prefersReducedMotion(context)
    }
    return state.value
}

object Motion {
    /**
     * Standard scroll distance (in pixels) for the header to complete its morphing animation.
     */
    const val HEADER_MORPH_THRESHOLD = 300f
    
    /**
     * Milliseconds per pixel for marquee scrolling speed.
     */
    const val MARQUEE_CADENCE = 20

    // 1. High-end Spring Physics for "Alive" UI
    val springDynamic = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    @Composable
    fun tweenSpec(durationMillis: Int, easing: Easing = FastOutSlowInEasing): FiniteAnimationSpec<Int> {
        val reduced = rememberPrefersReducedMotion()
        val d = if (reduced) 0 else durationMillis
        return tween(durationMillis = d, easing = easing)
    }

    @Composable
    fun tweenFloatSpec(durationMillis: Int, easing: Easing = FastOutSlowInEasing): FiniteAnimationSpec<Float> {
        val reduced = rememberPrefersReducedMotion()
        val d = if (reduced) 0 else durationMillis
        return tween(durationMillis = d, easing = easing)
    }

    @Composable
    fun springDefault(): FiniteAnimationSpec<Float> {
        return spring(stiffness = Spring.StiffnessMedium)
    }
}

/**
 * Kinetic scale-down on press using spring physics.
 */
@Composable
fun Modifier.scaleOnPress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    scaleDown: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed) scaleDown else 1f
    
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.springDynamic,
        label = "pressScale"
    )
    
    return this.then(Modifier.graphicsLayer {
        scaleX = animated
        scaleY = animated
    })
}
