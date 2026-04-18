package cock.crest.purrfectsnap.lite.ui.manager.pages.home

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class RetroGameScreen : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.retro_flight") }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val gridWidth = 120
        val gridHeight = 160
        val shipSpeed = 1.7f
        val shipRadius = 4.5f
        val maxSpeedMultiplier = 2.2f
        val speedRampPerScore = 0.035f
        val rng = remember { Random(System.currentTimeMillis()) }
        val pixelFont = remember { FontFamily.Monospace }

        data class Star(var x: Float, var y: Float, var speed: Float)
        data class Planet(var x: Float, var y: Float, var radius: Float, var speed: Float)

        var score by remember { mutableIntStateOf(0) }
        var isGameOver by remember { mutableStateOf(false) }
        var leftPressed by remember { mutableStateOf(false) }
        var rightPressed by remember { mutableStateOf(false) }
        var shipX by remember { mutableFloatStateOf(gridWidth / 2f) }
        val shipY = gridHeight - 20f

        val stars = remember { mutableListOf<Star>() }
        val planets = remember { mutableListOf<Planet>() }

        val frameBitmap = remember { ImageBitmap(gridWidth, gridHeight) }
        val frameCanvas = remember { Canvas(frameBitmap) }
        val paint = remember { Paint() }
        var frameTick by remember { mutableIntStateOf(0) }

        fun spawnPlanet(): Planet {
            val radius = rng.nextInt(6, 14).toFloat()
            val x = rng.nextInt(radius.roundToInt(), gridWidth - radius.roundToInt()).toFloat()
            val y = rng.nextInt(-gridHeight, -20).toFloat()
            val speed = rng.nextInt(10, 20) / 10f
            return Planet(x = x, y = y, radius = radius, speed = speed)
        }

        fun resetGame() {
            score = 0
            isGameOver = false
            shipX = gridWidth / 2f
            stars.clear()
            planets.clear()
            repeat(28) {
                stars.add(
                    Star(
                        x = rng.nextInt(0, gridWidth).toFloat(),
                        y = rng.nextInt(0, gridHeight).toFloat(),
                        speed = rng.nextInt(6, 12) / 10f
                    )
                )
            }
            repeat(4) { planets.add(spawnPlanet()) }
        }

        fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
            paint.color = color
            paint.alpha = 1f
            frameCanvas.drawRect(Rect(left, top, right, bottom), paint)
        }

        fun drawCircle(centerX: Float, centerY: Float, radius: Float, color: Color) {
            paint.color = color
            frameCanvas.drawCircle(androidx.compose.ui.geometry.Offset(centerX, centerY), radius, paint)
        }

        fun drawShip() {
            val sx = shipX.roundToInt().toFloat()
            drawRect(sx - 2f, shipY, sx + 3f, shipY + 1f, Color(0xFFE7F6FF))
            drawRect(sx - 3f, shipY + 1f, sx + 4f, shipY + 3f, Color(0xFFE7F6FF))
            drawRect(sx - 4f, shipY + 3f, sx + 5f, shipY + 6f, Color(0xFFE7F6FF))
            drawRect(sx - 2f, shipY + 6f, sx + 3f, shipY + 8f, Color(0xFFE7F6FF))
            drawRect(sx - 1f, shipY + 2f, sx + 2f, shipY + 4f, Color(0xFF8AF4FF))
            if (!isGameOver) {
                drawRect(sx - 1f, shipY + 8f, sx + 2f, shipY + 10f, Color(0xFFFFB24D))
                drawRect(sx, shipY + 10f, sx + 1f, shipY + 12f, Color(0xFFFF6B6B))
            }
        }

        fun renderFrame() {
            drawRect(0f, 0f, gridWidth.toFloat(), gridHeight.toFloat(), Color(0xFF1B1440))
            drawRect(0f, 0f, gridWidth.toFloat(), 14f, Color(0xFF5236B8))
            stars.forEach { star ->
                drawRect(star.x, star.y, star.x + 1f, star.y + 1f, Color(0xFFEEE9FF))
            }
            planets.forEach { planet ->
                drawCircle(planet.x, planet.y, planet.radius, Color(0xFF4FD4FF))
                drawCircle(planet.x - planet.radius * 0.3f, planet.y - planet.radius * 0.2f, planet.radius * 0.35f, Color(0xFFB0F0FF))
            }
            drawShip()
            if (isGameOver) {
                drawRect(0f, gridHeight / 2f - 10f, gridWidth.toFloat(), gridHeight / 2f + 8f, Color(0xAA000000))
            }
        }

        LaunchedEffect(Unit) {
            resetGame()
            while (true) {
                delay(16)
                if (!isGameOver) {
                    val speedMultiplier = min(
                        maxSpeedMultiplier,
                        1f + (score * speedRampPerScore)
                    )
                    val direction = (if (leftPressed) -1f else 0f) + (if (rightPressed) 1f else 0f)
                    shipX = (shipX + direction * shipSpeed).coerceIn(8f, gridWidth - 8f)
                    stars.forEach { star ->
                        star.y += star.speed * speedMultiplier
                        if (star.y > gridHeight) {
                            star.y = 0f
                            star.x = rng.nextInt(0, gridWidth).toFloat()
                        }
                    }
                    planets.forEachIndexed { index, planet ->
                        planet.y += planet.speed * speedMultiplier
                        if (planet.y - planet.radius > gridHeight) {
                            planets[index] = spawnPlanet()
                            score += 1
                        }
                    }
                    planets.forEach { planet ->
                        val dx = abs(planet.x - shipX)
                        val dy = abs(planet.y - shipY)
                        if (dx * dx + dy * dy < (planet.radius + shipRadius) * (planet.radius + shipRadius)) {
                            isGameOver = true
                        }
                    }
                }
                renderFrame()
                frameTick++
            }
        }

        val bottomPadding = routes.bottomPadding +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            24.dp

        val isAphelion = remember { context.config.root.global.uiSettings.managerTheme.get() == "APHELION" }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAphelion) {
                    FloatingTopBar(
                        title = translation["title"] ?: "Retro Flight",
                        onBack = { routes.navController.popBackStack() }
                    )
                } else {
                    val shape = RoundedCornerShape(26.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                        shape = shape,
                        color = Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.55f), PurrfectPalette.glowSecondary.copy(alpha = 0.35f)))),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(onClick = { routes.navController.popBackStack() }, modifier = Modifier.size(42.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = translation["title"] ?: "",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .background(Color(0xFF21195A), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    key(frameTick) {
                        Image(
                            bitmap = frameBitmap,
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Color.White.copy(alpha = 0.1f))
                        )
                    }
                    Text(
                        text = score.toString(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = pixelFont,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                    )
                    if (isGameOver) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = translation["game_over_label"],
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = pixelFont
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { resetGame() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5A43D6),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(translation["restart_button"], fontFamily = pixelFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pointerInteropFilter {
                                when (it.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        leftPressed = true
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        leftPressed = false
                                        true
                                    }
                                    else -> false
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E2568),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = translation["left_button"],
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = pixelFont,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pointerInteropFilter {
                                when (it.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        rightPressed = true
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        rightPressed = false
                                        true
                                    }
                                    else -> false
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E2568),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = translation["right_button"],
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = pixelFont,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
