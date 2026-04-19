package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.common.BuildConfig
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import cock.crest.purrfectsnap.lite.storage.getQuickTiles
import cock.crest.purrfectsnap.lite.storage.setQuickTiles
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.data.UpdateDownloader
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater.Channel
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.QuickActionsDialog
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.Motion
import cock.crest.purrfectsnap.lite.ui.util.PurrfectMarqueeText
import cock.crest.purrfectsnap.lite.ui.util.headerHeightTracker
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeRootSection.AphelionHomeScreen(nav: NavBackStackEntry) {

    @Composable
    fun LivingPurrAura(isActive: Boolean, haptic: HapticFeedback) {
        val infiniteTransition = rememberInfiniteTransition(label = "aura")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.88f, targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulse"
        )
        val glow1 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "g1"
        )
        val glow2 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, delayMillis = 1100, easing = LinearEasing), RepeatMode.Restart),
            label = "g2"
        )
        val glow3 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, delayMillis = 2200, easing = LinearEasing), RepeatMode.Restart),
            label = "g3"
        )
        val coreColor by animateColorAsState(
            targetValue = if (isActive) PurrfectPalette.glowPrimary else Color(0xFF8C8CA3),
            animationSpec = tween(800), label = "coreColor"
        )
        val secondaryColor by animateColorAsState(
            targetValue = if (isActive) PurrfectPalette.glowSecondary else Color(0xFF6B6B7A),
            animationSpec = tween(800), label = "secondaryColor"
        )
        Canvas(modifier = Modifier.size(44.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = 6.dp.toPx()
            fun drawAuroraGlow(progress: Float, alphaMultiplier: Float) {
                if (!isActive || progress <= 0f) return
                val auroraRadius = baseRadius * (1.2f + 4.5f * progress)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to coreColor.copy(alpha = 0.25f * (1f - progress) * alphaMultiplier),
                        0.6f to secondaryColor.copy(alpha = 0.12f * (1f - progress) * alphaMultiplier),
                        1.0f to Color.Transparent,
                        center = center, radius = auroraRadius
                    ),
                    radius = auroraRadius, center = center
                )
            }
            drawAuroraGlow(glow1, 0.8f)
            drawAuroraGlow(glow2, 0.5f)
            drawAuroraGlow(glow3, 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, secondaryColor),
                    center = center, radius = baseRadius * pulseScale
                ),
                radius = baseRadius * pulseScale, center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = (baseRadius * pulseScale) * 0.25f,
                center = Offset(center.x - (baseRadius * pulseScale) * 0.3f, center.y - (baseRadius * pulseScale) * 0.3f)
            )
        }
    }

    @Composable
    fun AphelionTopBarActionChip(
        icon: ImageVector,
        label: String? = null,
        contentDescription: String? = label,
        shrinkFactor: Float = 1f,
        haptic: HapticFeedback,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
            shape = RoundedCornerShape(40),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(40))
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
                    .padding(vertical = 6.dp, horizontal = lerp(10.dp, 12.dp, shrinkFactor)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        val s = 0.82f + (0.18f * shrinkFactor); scaleX = s; scaleY = s
                    }
                )
                if (label != null) {
                    val labelAlpha = (shrinkFactor - 0.1f).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.width((8 * shrinkFactor).dp))
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = labelAlpha),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .graphicsLayer { alpha = labelAlpha; translationX = (-4 * (1f - shrinkFactor)).dp.toPx() }
                            .widthIn(max = (75 * shrinkFactor).dp)
                    )
                }
            }
        }
    }

    @Composable
    fun RowScope.AphelionHomeActionChips(
        scrollState: androidx.compose.foundation.ScrollState,
        haptic: HapticFeedback
    ) {
        val shrinkFactor by remember(scrollState.value) {
            derivedStateOf { (1f - (scrollState.value.toFloat() / Motion.HEADER_MORPH_THRESHOLD)).coerceIn(0f, 1f) }
        }
        AphelionTopBarActionChip(
            icon = Icons.Filled.BugReport,
            label = context.translation["manager.routes.home_logs"],
            shrinkFactor = shrinkFactor, haptic = haptic
        ) { routes.homeLogs.navigate() }
        AphelionTopBarActionChip(
            icon = Icons.Filled.Settings,
            label = context.translation["manager.routes.home_settings"],
            shrinkFactor = shrinkFactor, haptic = haptic
        ) { routes.settings.navigate() }
    }

    @Composable
    fun ActionCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(128.dp)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF151E27),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.BottomEnd).size(56.dp))
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun AphelionHeroSection(
        versionName: String,
        latestUpdate: Updater.LatestRelease?,
        downloadState: UpdateDownloader.DownloadState,
        downloadProgress: Float,
        onUpdateAction: () -> Unit,
        channelLabel: String,
        isPurrAuraActive: Boolean,
        onAboutClick: () -> Unit,
        avenirNext: FontFamily,
        scrollOffset: () -> Int,
        haptic: HapticFeedback
    ) {
        val heroShape = RoundedCornerShape(36.dp)
        val gitHashShort = remember { (context.installationSummary.modInfo?.gitHash ?: BuildConfig.GIT_HASH).take(7) }
        Box(
            modifier = Modifier
                .padding(horizontal = HomeRootSection.cardMargin, vertical = 6.dp)
                .clip(heroShape)
                .background(Brush.linearGradient(heroGradientColors))
                .border(1.dp, Color.White.copy(alpha = 0.1f), heroShape)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PurrfectSnap",
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = avenirNext,
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - ((scrollOffset() - 250f) / 120f)).coerceIn(0f, 1f)
                            translationY = (-scrollOffset() * 0.06f)
                        }
                    )
                    Text(
                        text = "By \u039eT\u039eRNAL",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontFamily = avenirNext,
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - ((scrollOffset() - 300f) / 120f)).coerceIn(0f, 1f)
                            translationY = (-scrollOffset() * 0.04f)
                        }
                    )
                    Text(
                        text = translation["hero_tagline"] ?: "",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - ((scrollOffset() - 350f) / 120f)).coerceIn(0f, 1f)
                            translationY = (-scrollOffset() * 0.02f)
                        }
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        alpha = (1f - ((scrollOffset() - 400f) / 120f)).coerceIn(0f, 1f)
                    },
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroBadge(translation.format("hero_version_label", "version" to versionName, "channel" to channelLabel))
                    gitHashShort.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }?.let {
                        HeroBadge(translation.format("hero_build_label", "build" to it))
                    }
                }

                if (latestUpdate != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(translation["update_title"] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(translation.format("update_content", "version" to latestUpdate.versionName), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                            }
                            AnimatedContent(targetState = downloadState, label = "UpdateDownloadHero") { state ->
                                when (state) {
                                    UpdateDownloader.DownloadState.IDLE,
                                    UpdateDownloader.DownloadState.FAILED -> {
                                        Button(
                                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdateAction() },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))
                                        ) { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    }
                                    UpdateDownloader.DownloadState.DOWNLOADING -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(28.dp), color = Color.White)
                                            Text("${(downloadProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    UpdateDownloader.DownloadState.COMPLETED -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA3F0C2))
                                            Text(translation["update_ready_label"] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    tonalElevation = 0.dp, shadowElevation = 0.dp
                ) {
                    val unifiedButtonWidth = 180.dp
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.width(unifiedButtonWidth).height(46.dp),
                            tonalElevation = 0.dp, shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                    LivingPurrAura(isActive = isPurrAuraActive, haptic = haptic)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPurrAuraActive) translation["purr_aura_active_label"] ?: "" else translation["purr_aura_inactive_label"] ?: "",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAboutClick() },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.06f), contentColor = Color.White),
                            modifier = Modifier.width(unifiedButtonWidth).height(46.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation.getOrNull("about_meet_team_button") ?: "About Us", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val androidContext = context.androidContext
                        Button(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://purrfectsnap.me", context.translation["toast_open_link_failed"]) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E)),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                PurrfectMarqueeText(text = "Site", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Color(0xFF1B152E))
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://github.com/sujxlsahu/PurrfectSnap", context.translation["toast_open_link_failed"]) },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                PurrfectMarqueeText(text = translation["github_button"] ?: "", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }
                        ExternalLinkIcon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"]) },
                            tint = Color.White, containerColor = Color.White.copy(alpha = 0.14f),
                            haptic = haptic
                        )
                    }
                }
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }
    val prefs = remember { context.sharedPreferences }
    val allQuickTileNames = remember(cardEntries) { cardEntries.map { it.name } }
    val selectedTiles = rememberAsyncMutableStateList(defaultValue = allQuickTileNames) {
        val storedTiles = context.database.getQuickTiles().filter { it.isNotBlank() }
        val hasInitialized = prefs.getBoolean(HomeRootSection.QUICK_TILES_INITIALIZED_PREF, false)
        when {
            storedTiles.isNotEmpty() -> {
                if (!hasInitialized) prefs.edit().putBoolean(HomeRootSection.QUICK_TILES_INITIALIZED_PREF, true).apply()
                storedTiles
            }
            hasInitialized -> storedTiles
            else -> {
                context.database.setQuickTiles(allQuickTileNames)
                prefs.edit().putBoolean(HomeRootSection.QUICK_TILES_INITIALIZED_PREF, true).apply()
                allQuickTileNames
            }
        }
    }

    val updateChannel = context.config.root.global.updateSettings.updateChannel.getNullable() ?: "stable"
    val channelLabel = if (updateChannel == "prerelease") translation["channel_label_prerelease"] ?: "" else translation["channel_label_stable"] ?: ""
    val latestUpdate by rememberAsyncMutableState(defaultValue = null, keys = arrayOf(updateChannel)) {
        Updater.getLatestRelease(if (updateChannel == "prerelease") Channel.PRERELEASE else Channel.STABLE)
    }
    val downloadState by UpdateDownloader.downloadState.collectAsState()
    val downloadProgress by UpdateDownloader.downloadProgress.collectAsState()
    val isPurrAuraActive by rememberPreferenceBool("debug_test_mode", true)
    val scrollState = rememberScrollState()
    var showQuickActionsMenu by rememberSaveable { mutableStateOf(false) }
    var showChangelogDialog by rememberSaveable { mutableStateOf(false) }
    var changelogText by rememberSaveable { mutableStateOf<String?>(null) }
    var changelogLoading by remember { mutableStateOf(false) }
    var changelogError by remember { mutableStateOf<String?>(null) }
    var changelogVersion by remember { mutableStateOf<String?>(null) }
    var showFullChangelogDialog by rememberSaveable { mutableStateOf(false) }
    var fullChangelogText by rememberSaveable { mutableStateOf<String?>(null) }
    var fullChangelogLoading by remember { mutableStateOf(false) }
    var fullChangelogError by remember { mutableStateOf<String?>(null) }
    var showAnnouncementsDialog by rememberSaveable { mutableStateOf(false) }
    var announcementsText by rememberSaveable { mutableStateOf<String?>(null) }
    var announcementsLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var controlsHeight by remember { mutableStateOf(100.dp) }

    LaunchedEffect(scrollState.value) { routes.navigation?.globalScrollOffset = scrollState.value }

    val handleUpdateAction: () -> Unit = {
        latestUpdate?.let { latest ->
            val abiName = android.os.Build.SUPPORTED_ABIS.firstNotNullOfOrNull {
                when (it) { "arm64-v8a" -> "arm64"; "armeabi-v7a" -> "armv7"; else -> null }
            }
            if (latest.workflowId != null) {
                if (abiName != null) {
                    val artifactName = "purrfectsnap-${if (abiName == "arm64") "armv8" else "armv7"}-debug"
                    UpdateDownloader.downloadAndInstall(context, "https://nightly.link/sujxlsahu/PurrfectSnap/actions/runs/${latest.workflowId}/$artifactName.zip", "$artifactName.zip", coroutineScope)
                }
            } else {
                abiName?.let { arch -> latest.assetDownloads[arch] }?.let { url ->
                    UpdateDownloader.downloadAndInstall(context, url, url.substringAfterLast('/'), coroutineScope)
                }
            }
        }
    }

    fun loadChangelog() {
        val targetVersion = latestUpdate?.versionName ?: BuildConfig.VERSION_NAME
        if (changelogVersion == targetVersion && changelogText != null) return
        changelogLoading = true
        changelogError = null
        coroutineScope.launch(Dispatchers.IO) {
            val url = if (updateChannel == "prerelease") changelogPrereleaseUrl else changelogStableUrl
            runCatching {
                OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                    extractChangelogForVersion(body, targetVersion).ifBlank { body.trim() }
                }
            }.onSuccess { text ->
                withContext(Dispatchers.Main) { 
                    changelogText = text
                    changelogVersion = targetVersion
                    changelogLoading = false 
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { 
                    changelogError = e.message ?: "Failed to fetch"
                    changelogLoading = false 
                }
            }
        }
    }

    fun loadAnnouncements() {
        if (announcementsText != null) return
        announcementsLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                OkHttpClient().newCall(Request.Builder().url(announcementsUrl).build()).execute().use { it.body?.string() ?: "" }
            }
                .onSuccess { withContext(Dispatchers.Main) { announcementsText = it; announcementsLoading = false } }
                .onFailure { withContext(Dispatchers.Main) { announcementsLoading = false } }
        }
    }

    fun loadFullChangelog() {
        if (fullChangelogText != null) return
        fullChangelogLoading = true
        fullChangelogError = null
        coroutineScope.launch(Dispatchers.IO) {
            val url = if (updateChannel == "prerelease") changelogPrereleaseUrl else changelogStableUrl
            runCatching {
                OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                    body.trim()
                }
            }.onSuccess { text ->
                withContext(Dispatchers.Main) {
                    fullChangelogText = text
                    fullChangelogLoading = false
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    fullChangelogError = e.message ?: "Failed to fetch"
                    fullChangelogLoading = false
                }
            }
        }
    }

    val borderPath = remember { Path() }
    val uPath = remember { Path() }

    Box(modifier = Modifier.fillMaxSize().background(HomeRootSection.pageBackgroundGradient)) {

        val focusFactor by remember(scrollState.value) {
            derivedStateOf { (scrollState.value.toFloat() / Motion.HEADER_MORPH_THRESHOLD).coerceIn(0f, 1f) }
        }
        val stickyBrandingAlpha by remember(scrollState.value) {
            derivedStateOf { ((scrollState.value.toFloat() - 50f) / 100f).coerceIn(0f, 1f) }
        }
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val headerHeight = lerp(54.dp, 56.dp, focusFactor)
        val containerTopPadding = lerp(statusBarHeight + 2.dp, 0.dp, focusFactor)
        val internalTopPadding = lerp(0.dp, statusBarHeight, focusFactor)
        val topCorners = lerp(26.dp, 0.dp, focusFactor)
        val bottomCorners = lerp(26.dp, 28.dp, focusFactor)

        Box(modifier = Modifier.fillMaxWidth().zIndex(10f)) {
            val refractiveColor = remember { Color(0xFF241F52) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = containerTopPadding)
                    .height(internalTopPadding + headerHeight + 32.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to refractiveColor.copy(alpha = 0.95f * focusFactor),
                            0.6f to refractiveColor.copy(alpha = 0.85f * focusFactor),
                            1.0f to Color.Transparent
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = containerTopPadding)
                    .headerHeightTracker { controlsHeight = it }
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val brush = Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = focusFactor * 0.6f),
                                PurrfectPalette.glowSecondary.copy(alpha = focusFactor * 0.4f)
                            )
                        )
                        val tr = topCorners.toPx()
                        val br = bottomCorners.toPx()
                        if (focusFactor > 0.9f) {
                            uPath.reset()
                            uPath.apply {
                                moveTo(0f, 0f)
                                lineTo(0f, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2 * br, 2 * br, size.height), 180f, -90f, false)
                                lineTo(size.width - br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * br, size.height - 2 * br, size.width, size.height), 90f, -90f, false)
                                lineTo(size.width, 0f)
                            }
                            drawPath(uPath, brush, style = Stroke(strokeWidth))
                        } else if (focusFactor > 0.01f) {
                            borderPath.reset()
                            borderPath.apply {
                                moveTo(tr, 0f)
                                lineTo(size.width - tr, 0f)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * tr, 0f, size.width, 2 * tr), 270f, 90f, false)
                                lineTo(size.width, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * br, size.height - 2 * br, size.width, size.height), 0f, 90f, false)
                                lineTo(br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2 * br, 2 * br, size.height), 90f, 90f, false)
                                lineTo(0f, tr)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * tr, 2 * tr), 180f, 90f, false)
                            }
                            drawPath(borderPath, brush, style = Stroke(strokeWidth))
                        }
                    },
                shape = RoundedCornerShape(topStart = topCorners, topEnd = topCorners, bottomStart = bottomCorners, bottomEnd = bottomCorners),
                color = Color(0xFF1B152E).copy(alpha = focusFactor * 0.95f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = internalTopPadding)
                        .padding(horizontal = 16.dp)
                        .height(headerHeight)
                ) {
                    Text(
                        text = "PurrfectSnap",
                        color = Color.White.copy(alpha = stickyBrandingAlpha),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = avenirNext,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    val announcementShift by remember(focusFactor) { derivedStateOf { (-6 * focusFactor).dp } }
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = announcementShift.toPx() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AphelionTopBarActionChip(
                            icon = Icons.Filled.Notifications, label = null,
                            shrinkFactor = (1f - focusFactor).coerceIn(0f, 1f),
                            contentDescription = translation["announcements_button_description"],
                            haptic = haptic
                        ) { showAnnouncementsDialog = true; loadAnnouncements() }
                        AphelionTopBarActionChip(
                            icon = Icons.Filled.Description, label = null,
                            shrinkFactor = (1f - focusFactor).coerceIn(0f, 1f),
                            contentDescription = translation.getOrNull("changelog_button_description") ?: "Open full changelog",
                            haptic = haptic
                        ) { showFullChangelogDialog = true; loadFullChangelog() }
                    }
                    val settingsShift by remember(focusFactor) { derivedStateOf { (6 * focusFactor).dp } }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { translationX = settingsShift.toPx() },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AphelionHomeActionChips(scrollState = scrollState, haptic = haptic)
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = routes.bottomPadding + 4.dp)) {
            Spacer(Modifier.height(controlsHeight + containerTopPadding))

            AphelionHeroSection(
                versionName = BuildConfig.VERSION_NAME,
                latestUpdate = latestUpdate,
                downloadState = downloadState,
                downloadProgress = downloadProgress,
                onUpdateAction = { latestUpdate?.let { showChangelogDialog = true; loadChangelog() } },
                channelLabel = channelLabel,
                isPurrAuraActive = isPurrAuraActive,
                onAboutClick = { routes.about.navigate() },
                avenirNext = avenirNext,
                scrollOffset = { scrollState.value },
                haptic = haptic
            )

            Spacer(Modifier.height(12.dp))

            AnimatedContent(targetState = selectedTiles.isNotEmpty(), label = "QuickActions") { hasQuickActions ->
                Surface(
                    modifier = Modifier.padding(horizontal = HomeRootSection.cardMargin, vertical = 10.dp),
                    shape = RoundedCornerShape(34.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(quickActionsGradientColors)).padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!hasQuickActions) {
                            Text(translation["quick_actions_title"] ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.height(24.dp))
                            Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Text(translation["quick_actions_empty_title"] ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showQuickActionsMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(translation["quick_actions_add_tile_button"] ?: "")
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(translation["quick_actions_title"] ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(translation.format("quick_actions_count_label", "count" to selectedTiles.size.toString()), fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showQuickActionsMenu = true },
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_manage), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(translation["quick_actions_manage_button"] ?: "")
                                }
                            }

                            fun actionCardIcon(id: String) = when (id) {
                                "quick.file_imports" -> Icons.Outlined.FolderOpen
                                "quick.logger_history" -> Icons.Outlined.History
                                "action.export_chat_messages" -> Icons.AutoMirrored.Outlined.Chat
                                "action.export_memories" -> Icons.Outlined.Image
                                "action.bulk_messaging_action" -> Icons.Outlined.Message
                                "action.clean_snapchat_cache" -> Icons.Outlined.CleaningServices
                                "action.manage_friend_list" -> Icons.Outlined.PersonOutline
                                else -> Icons.Outlined.Widgets
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                selectedTiles.forEachIndexed { index, name ->
                                    val entry = cardEntries.find { it.name == name } ?: return@forEachIndexed
                                    val subtitle = context.translation.getOrNull("actions.${entry.id.substringAfter('.')}.description")
                                        ?: "${translation["quick_actions_manage_button"] ?: "Manage"} ${entry.name}"
                                    val icon = actionCardIcon(entry.id)
                                    val startPadding = if (index % 2 == 0) 0.dp else 18.dp
                                    val endPadding = if (index % 2 == 0) 18.dp else 0.dp
                                    ActionCard(
                                        title = name,
                                        subtitle = subtitle,
                                        icon = icon,
                                        modifier = Modifier.padding(start = startPadding, end = endPadding)
                                    ) { entry.action(routes) }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showAnnouncementsDialog) {
        AestheticDialog(
            onDismissRequest = { showAnnouncementsDialog = false },
            title = translation["announcements_dialog_title"] ?: "Announcements",
            text = "", icon = Icons.Filled.Notifications,
            confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
            onConfirm = { showAnnouncementsDialog = false },
            showCloseButton = false,
            customContent = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (announcementsLoading) CircularProgressIndicator(color = Color.White)
                    else Text(announcementsText ?: translation["announcements_dialog_empty"] ?: "", color = PurrfectPalette.textPrimary, fontSize = 14.sp)
                }
            }
        )
    }

    if (showChangelogDialog) {
        AestheticDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = translation["changelog_dialog_title"] ?: "Changelog",
            text = "", icon = Icons.Filled.Info,
            confirmButtonText = translation["changelog_dialog_update_button"] ?: "Update",
            onConfirm = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showChangelogDialog = false; handleUpdateAction() },
            dismissButtonText = translation["changelog_dialog_cancel_button"] ?: "Cancel",
            onDismiss = { showChangelogDialog = false },
            showCloseButton = false,
            customContent = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (changelogLoading) CircularProgressIndicator(color = Color.White)
                    else if (changelogError != null) Text(changelogError!!, color = Color.Red, fontSize = 14.sp)
                    else Text(changelogText ?: translation["changelog_dialog_empty"] ?: "", color = PurrfectPalette.textPrimary, fontSize = 14.sp)
                }
            }
        )
    }

    if (showFullChangelogDialog) {
        AestheticDialog(
            onDismissRequest = { showFullChangelogDialog = false },
            title = translation["changelog_dialog_title"] ?: "Changelog",
            text = "",
            icon = Icons.Filled.Description,
            confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
            onConfirm = { showFullChangelogDialog = false },
            showCloseButton = false,
            customContent = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (fullChangelogLoading) CircularProgressIndicator(color = Color.White)
                    else if (fullChangelogError != null) Text(fullChangelogError!!, color = Color.Red, fontSize = 14.sp)
                    else Text(fullChangelogText ?: translation["changelog_dialog_empty"] ?: "", color = PurrfectPalette.textPrimary, fontSize = 14.sp)
                }
            }
        )
    }

    if (showQuickActionsMenu) {
        QuickActionsDialog(
            quickActions = cards,
            selectedQuickActions = selectedTiles,
            onDismiss = { showQuickActionsMenu = false },
            onSave = { newList ->
                val removed = selectedTiles.filter { it !in newList }
                removed.forEach { clearTileSpan(it); clearTileOffset(it) }
                selectedTiles.clear(); selectedTiles.addAll(newList)
                context.coroutineScope.launch { context.database.setQuickTiles(selectedTiles) }
                showQuickActionsMenu = false
            },
            translation = translation
        )
    }
}
