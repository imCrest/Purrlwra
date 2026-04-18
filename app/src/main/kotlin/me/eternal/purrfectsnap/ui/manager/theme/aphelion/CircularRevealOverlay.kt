package cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion

import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import androidx.compose.ui.platform.LocalHapticFeedback
import cock.crest.purrfectsnap.lite.RemoteSideContext

private const val REVEAL_DURATION_MS = 3200
private const val WAVE_BAND_WIDTH_PX = 300f

// "Explosive Dissipation" Easing: Instant high velocity at start, rapid energy loss, ending in a slow crawl.
private val AphelionEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun CircularRevealOverlay(
    context: RemoteSideContext,
    request: ThemeRevealRequest,
    onComplete: () -> Unit
) {
    // Safety check: if bitmap was recycled or is null, skip.
    val bitmap = request.oldThemeBitmap ?: run {
        LaunchedEffect(request.id) { onComplete() }
        return
    }
    
    if (bitmap.isRecycled) {
        LaunchedEffect(request.id) { onComplete() }
        return
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    // Ensure the reveal is cleared even if navigation happens mid-animation
    DisposableEffect(request.id) {
        onDispose { onComplete() }
    }
    
    val maxRadius = remember(configuration) {
        with(density) {
            val w = configuration.screenWidthDp.dp.toPx()
            val h = configuration.screenHeightDp.dp.toPx()
            sqrt(w * w + h * h)
        }
    }

    val animatedRadius = remember(request.id) { Animatable(0f) }

    LaunchedEffect(request.id) {
        AphelionHaptics.themeRevealTick(context, hapticFeedback)

        animatedRadius.animateTo(
            targetValue = maxRadius + WAVE_BAND_WIDTH_PX,
            animationSpec = tween(durationMillis = REVEAL_DURATION_MS, easing = AphelionEasing)
        )
        onComplete()
    }

    val progress = (animatedRadius.value / (maxRadius + WAVE_BAND_WIDTH_PX)).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "wave_time")
    val timeValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 5_000, easing = LinearEasing)),
        label = "wave_time_value"
    )

    // --- AGSL SHADER LOGIC (Android 13+) ---
    
    val runtimeShader = remember(bitmap) {
        android.graphics.RuntimeShader(WaveEdgeShader.AGSL).apply {
            setInputShader("content", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }
    }

    val shaderPaint = remember(runtimeShader) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = runtimeShader
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = animatedRadius.value
        val center = request.originCenter

        drawIntoCanvas { canvas ->
            runtimeShader.setFloatUniform("revealRadius", radius)
            runtimeShader.setFloatUniform("revealCenter", center.x, center.y)
            runtimeShader.setFloatUniform("bandWidth", WAVE_BAND_WIDTH_PX)
            runtimeShader.setFloatUniform("time", timeValue)
            runtimeShader.setFloatUniform("uProgress", progress)
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, shaderPaint)
        }
    }
}
