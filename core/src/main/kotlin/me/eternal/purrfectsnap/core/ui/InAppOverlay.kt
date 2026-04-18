package cock.crest.purrfectsnap.lite.core.ui

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import cock.crest.purrfectsnap.lite.common.ui.AppMaterialTheme
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.core.event.Event
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.isDarkTheme
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess

typealias CustomComposable = @Composable BoxScope.() -> Unit

class CallRecorderUIState {
    var isRecording by mutableStateOf(false)
    var showOverlay by mutableStateOf(false)
    var currentAuthor by mutableStateOf("")
    var recordingStartTime by mutableStateOf(0L)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var isMinimized by mutableStateOf(false)
    var lastInteractionTime by mutableStateOf(0L)
}

class VideoRecordTimerState {
    var isRecording by mutableStateOf(false)
    var recordingStartTime by mutableStateOf(0L)
}

class InAppOverlay(
    private val context: ModContext
) {
    val callRecorderState = CallRecorderUIState()
    val videoRecordTimerState = VideoRecordTimerState()
    companion object {
        fun showCrashOverlay(content: String, throwable: Throwable? = null) {
            // deny network requests
            PurrfectSnap.classCache.apply {
                unifiedGrpcService.hook("unaryCall", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
                networkApi.hook("submit", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
            }

            Hooker.ephemeralHook(Activity::class.java, "onPostCreate", HookStage.AFTER) { param ->
                val contentView = param.thisObject<Activity>().findViewById<FrameLayout>(android.R.id.content)
                contentView.children().forEach { it.visibility = View.GONE }
                val screenView = createComposeView(param.thisObject()) {
                    AppMaterialTheme(isDarkTheme = true) {
                        val auroraGradient = Brush.verticalGradient(
                            listOf(Color(0xFF2E2E69), Color(0xFF1E1E45))
                        )
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(auroraGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "PurrfectSnap",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(40.dp))
                                    Text(
                                        text = content,
                                        fontSize = 18.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Spacer(modifier = Modifier.height(60.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                                    ) {
                                        throwable?.let {
                                            Button(
                                                onClick = {
                                                    contentView.context.copyToClipboard(it.stackTraceToString())
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(alpha = 0.1f),
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Text("Copy error")
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                exitProcess(1)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("Exit App")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                contentView.addView(screenView)
            }
        }
    }

    inner class Toast(
        val composable: @Composable Toast.() -> Unit,
        val durationMs: Int
    ) {
        var shown by mutableStateOf(false)
        var visible by mutableStateOf(false)
    }

    private val toasts = mutableStateListOf<Toast>()
    private val customComposables = mutableStateListOf<CustomComposable>()

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun OverlayContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            toasts.forEach { toast ->
                val animation by animateFloatAsState(
                    targetValue = if (toast.visible) 1f else 0f,
                    animationSpec = if (toast.visible) tween(durationMillis = 150) else tween(durationMillis = 300),
                    label = "toast"
                )

                LaunchedEffect(toast) {
                    toast.visible = true
                    if (toast.durationMs < 0) return@LaunchedEffect
                    delay(toast.durationMs.toLong())
                    toast.visible = false
                    delay(1000)
                    toast.shown = true
                    synchronized(toasts) {
                        if (toasts.isNotEmpty() && toasts.all { it.shown }) toasts.clear()
                    }
                }

                val deviceWidth = LocalContext.current.resources.displayMetrics.widthPixels
                val delayAnimationSpec =  rememberSplineBasedDecay<Float>()
                val draggableState = remember {
                    AnchoredDraggableState(
                        initialValue = 0,
                        anchors = DraggableAnchors {
                            -1 at -deviceWidth.toFloat()
                            0 at 0f
                            1 at deviceWidth.toFloat()
                        }
                    )
                }
                val flingBehavior = AnchoredDraggableDefaults.flingBehavior(draggableState)
                LaunchedEffect(draggableState) {
                    snapshotFlow { draggableState.currentValue }
                        .collect { value ->
                            if (value != 0) {
                                toast.visible = false
                            }
                        }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .anchoredDraggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            flingBehavior = flingBehavior
                        )
                        .offset { IntOffset(draggableState.offset.roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = animation
                            translationY = -100.dp.toPx() * (1 - animation)
                        }
                ) {
                    if (animation > 0.01f) {
                        toast.composable(toast)
                    }
                }
            }

            customComposables.forEach {
                it()
            }

            CallRecorderOverlay()
            VideoRecordTimerOverlay()
        }
    }

    @Composable
    private fun VideoRecordTimerOverlay() {
        var elapsedTime by remember { mutableStateOf(0L) }

        LaunchedEffect(videoRecordTimerState.isRecording) {
            if (videoRecordTimerState.isRecording) {
                while (videoRecordTimerState.isRecording) {
                    delay(100)
                    elapsedTime = System.currentTimeMillis() - videoRecordTimerState.recordingStartTime
                }
            } else {
                elapsedTime = 0L
            }
        }

        AnimatedVisibility(
            visible = videoRecordTimerState.isRecording,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(200)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 45.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val seconds = (elapsedTime / 1000) % 60
                val minutes = (elapsedTime / 1000) / 60

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseRatio by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseRatio"
                )

                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Red.copy(alpha = pulseRatio), CircleShape)
                    )
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun CallRecorderOverlay() {
        var elapsedTime by remember { mutableStateOf(0L) }
        val density = LocalDensity.current
        val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels.toFloat()
        
        // Update timer every second
        LaunchedEffect(callRecorderState.isRecording) {
            if (callRecorderState.isRecording) {
                while (true) {
                    delay(1000)
                    elapsedTime = System.currentTimeMillis() - callRecorderState.recordingStartTime
                }
            } else {
                elapsedTime = 0L
            }
        }

        // Auto-minimize logic
        LaunchedEffect(callRecorderState.showOverlay, callRecorderState.lastInteractionTime) {
            if (callRecorderState.showOverlay && !callRecorderState.isMinimized) {
                delay(10000)
                callRecorderState.isMinimized = true
            }
        }
        
        // Reset interaction time when shown
        LaunchedEffect(callRecorderState.showOverlay) {
            if (callRecorderState.showOverlay) {
                callRecorderState.lastInteractionTime = System.currentTimeMillis()
                callRecorderState.isMinimized = false
            }
        }

        val displayOffsetX by animateFloatAsState(
            targetValue = if (callRecorderState.isMinimized) {
                -screenWidth / 2f + with(density) { 24.dp.toPx() }
            } else {
                callRecorderState.offsetX
            },
            label = "offsetX"
        )
        
        val displayOffsetY by animateFloatAsState(
            targetValue = if (callRecorderState.isMinimized) 0f else callRecorderState.offsetY,
            label = "offsetY"
        )
        
        // Pulsing animation for recording indicator
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha"
        )
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = callRecorderState.showOverlay,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                val uiDesign = context.config.downloader.callRecorder.callRecorderUiDesign.get()
                val isDark = context.mainActivity?.isDarkTheme() != false
                
                val containerColor = when (uiDesign) {
                    "default" -> if (isDark) Color(0xFF2D2D30) else Color(0xFFEFEFF0)
                    "cyber" -> Color(0xFF000000)
                    "frost" -> Color(0xB3FFFFFF)
                    "snapchat" -> Color(0xFFFFFC00)
                    else -> if (isDark) Color(0xFF2D2D30) else Color(0xFFEFEFF0)
                }

                val contentColor = when (uiDesign) {
                    "default" -> if (isDark) Color(0xFFE4E4E4) else Color(0xFF1F1F1F)
                    "frost" -> Color(0xFF333333)
                    "snapchat" -> Color.Black
                    else -> Color.White
                }

                val buttonBackground = when (uiDesign) {
                    "default" -> if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    "cyber" -> Color(0xFF00E5FF).copy(alpha = 0.1f)
                    "frost" -> Color.Black.copy(alpha = 0.1f)
                    "snapchat" -> Color.Black
                    else -> if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                }

                val buttonIconColor = when (uiDesign) {
                    "default" -> if (isDark) Color(0xFFE4E4E4) else Color(0xFF1F1F1F)
                    "snapchat" -> Color.White
                    "frost" -> Color(0xFF333333)
                    "cyber" -> Color(0xFF00E5FF)
                    else -> Color.White
                }

                val auroraGradient = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6F28A8),
                        Color(0xFF0059B7)
                    )
                )

                Card(
                    modifier = Modifier
                        .offset { IntOffset(displayOffsetX.roundToInt(), displayOffsetY.roundToInt()) }
                        .shadow(
                            elevation = 16.dp, 
                            shape = CircleShape,
                            ambientColor = if (uiDesign == "cyber") Color(0xFF00E5FF) else Color.Black,
                            spotColor = if (uiDesign == "cyber") Color(0xFF00E5FF) else Color.Black
                        )
                        .then(when (uiDesign) {
                            "cyber" -> Modifier.background(
                                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                shape = CircleShape
                            ).padding(1.dp)
                            "frost" -> Modifier.background(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            ).padding(0.5.dp)
                            else -> Modifier
                        })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    callRecorderState.isMinimized = false
                                    callRecorderState.lastInteractionTime = System.currentTimeMillis()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    callRecorderState.offsetX += dragAmount.x
                                    callRecorderState.offsetY += dragAmount.y
                                    callRecorderState.lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (callRecorderState.isMinimized) {
                                    callRecorderState.isMinimized = false
                                }
                                callRecorderState.lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                        .wrapContentSize(),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiDesign == "default") Color.Transparent else containerColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Box(modifier = Modifier.then(
                        if (uiDesign == "default") Modifier.background(auroraGradient) else Modifier
                    )) {
                        AnimatedContent(
                            targetState = callRecorderState.isMinimized,
                            label = "minimized"
                        ) { minimized ->
                            if (minimized) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (callRecorderState.isRecording) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .scale(pulseScale)
                                                    .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(Color.Red, CircleShape)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(contentColor.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Timer and Status
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val seconds = (elapsedTime / 1000) % 60
                                        val minutes = (elapsedTime / 1000) / 60
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (callRecorderState.isRecording) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    // Pulsing Outer Ring
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .scale(pulseScale)
                                                            .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
                                                    )
                                                    // Solid Inner Core
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(Color.Red, CircleShape)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = String.format("%02d:%02d", minutes, seconds),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = contentColor
                                            )
                                        }
                                    }

                                    // Vertical Separator
                                    Box(
                                        modifier = Modifier
                                            .width(1.5.dp)
                                            .height(20.dp)
                                            .background(contentColor.copy(alpha = 0.2f))
                                    )

                                    // Control Button
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(buttonBackground, CircleShape)
                                            .pointerInput(callRecorderState.isRecording) {
                                                detectTapGestures {
                                                    callRecorderState.lastInteractionTime = System.currentTimeMillis()
                                                    context.event.post(CallRecorderControlEvent(!callRecorderState.isRecording))
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (callRecorderState.isRecording) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(buttonIconColor, RoundedCornerShape(1.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(buttonIconColor, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    class CallRecorderControlEvent(val start: Boolean) : Event()

    private val overlayTag = Random.nextLong()

    private fun injectOverlay(activity: Activity) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        activity.runOnUiThread {
            if (root.findViewWithTag<View>(overlayTag) != null) return@runOnUiThread
            root.addView(createComposeView(activity) {
                AppMaterialTheme(isDarkTheme = remember { activity.isDarkTheme() }) {
                    OverlayContent()
                }
            }.apply {
                tag = overlayTag
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })
        }
    }

    fun onActivityCreate(activity: Activity) {
        injectOverlay(activity)
    }

    fun addCustomComposable(composable: CustomComposable) {
        customComposables.add(composable)
    }

    fun removeCustomComposable(composable: CustomComposable) {
        customComposables.remove(composable)
    }

    @Composable
    private fun DurationProgress(
        duration: Int,
        modifier: Modifier = Modifier
    ) {
        val progress = remember { Animatable(1f) }
        val progressGradient = Brush.horizontalGradient(
            listOf(
                Color(0xFF6F28A8).copy(alpha = 0.7f),
                Color(0xFF0059B7).copy(alpha = 0.7f)
            )
        )

        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
        }

        Box(
            modifier = modifier
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF6F28A8).copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value)
                    .fillMaxHeight()
                    .background(brush = progressGradient, shape = RoundedCornerShape(2.dp))
            )
        }
    }

    fun showStatusToast(
        icon: ImageVector,
        text: String,
        durationMs: Int = 2000,
        showDuration: Boolean = true,
        maxLines: Int = 3
    ) {
        showToast(
            icon = { Icon(icon, contentDescription = "icon", modifier = Modifier.size(32.dp)) },
            text = {
                Text(text, modifier = Modifier.fillMaxWidth(), maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, fontSize = 13.sp)
            },
            durationMs = durationMs,
            showDuration = showDuration
        )
    }

    private fun showToast(
        icon: @Composable () -> Unit = {
            Icon(Icons.Outlined.Warning, contentDescription = "icon", modifier = Modifier.size(32.dp))
        },
        text: @Composable () -> Unit = {},
        durationMs: Int = 3000,
        showDuration: Boolean = true,
    ) {
        injectOverlay(context.mainActivity!!)
        toasts.add(Toast(
            composable = {
                val isDark = LocalContext.current.isDarkTheme()
                val auroraGradient = Brush.verticalGradient(
                    listOf(
                        if (isDark) Color(0xFF2E2E69) else Color(0xFFE9DDFF),
                        if (isDark) Color(0xFF1B1B4D) else Color(0xFFF9F8FF)
                    )
                )

                ElevatedCard(
                    modifier = Modifier
                        .padding(12.dp)
                        .shadow(12.dp, MaterialTheme.shapes.large)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Box(modifier = Modifier.background(auroraGradient)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            icon()
                            text()
                        }
                    }
                    if (showDuration && durationMs > 0) {
                        DurationProgress(duration = durationMs, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            durationMs = durationMs
        ))
    }

    private enum class BypassAnimation { SLIDE_DOWN, ZOOM_IN }

    fun showBypassStatusIndicator(isWorking: Boolean) {
        if (context.bridgeClient.getDebugProp("disable_bypass_indicator", "false") == "true") {
            return
        }

        val animationType = BypassAnimation.values().random()
        
        lateinit var composable: CustomComposable
        composable = {
            var visible by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                visible = true
                delay(3000)
                visible = false
                delay(400)
                context.inAppOverlay.removeCustomComposable(composable)
            }

            val progress by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 70f
                ),
                label = "progress"
            )

            val (offsetX, offsetY, scale) = when (animationType) {
                BypassAnimation.SLIDE_DOWN -> Triple(0f, -80f * (1f - progress), 1f)
                BypassAnimation.ZOOM_IN -> Triple(0f, 0f, progress)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        scaleX = scale
                        scaleY = scale
                        alpha = progress
                    }
            ) {
                val bypassGradient = if (isWorking) Brush.horizontalGradient(
                    listOf(
                        Color(0xFF6F28A8).copy(alpha = 0.7f),
                        Color(0xFF0059B7).copy(alpha = 0.7f)
                    )
                ) else Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8B1538).copy(alpha = 0.7f),
                        Color(0xFFB71C1C).copy(alpha = 0.7f)
                    )
                )

                Row(
                    modifier = Modifier
                        .background(
                            brush = bypassGradient,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isWorking) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Text(
                        text = if (isWorking) 
                            context.translation["manager.sections.bypass_status.active"]
                        else 
                            context.translation["manager.sections.bypass_status.inactive"],
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        context.mainActivity?.let { injectOverlay(it) }
        customComposables.add(composable)
    }
}
