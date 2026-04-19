package cock.crest.purrfectsnap.lite.ui.manager.pages.home

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.action.EnumQuickActions
import cock.crest.purrfectsnap.lite.common.BuildConfig
import cock.crest.purrfectsnap.lite.common.action.EnumAction
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import cock.crest.purrfectsnap.lite.storage.getQuickTiles
import cock.crest.purrfectsnap.lite.storage.setQuickTiles
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.manager.data.UpdateDownloader
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater.Channel
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.AlertDialogs
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request

class HomeRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home") }

    companion object {
        internal const val QUICK_TILES_INITIALIZED_PREF = "quick_tiles_initialized"
        val cardMargin = 10.dp
        val pageBackgroundGradient = Brush.verticalGradient(
            listOf(
                Color(0xFF04070A),
                Color(0xFF0B1117),
                Color(0xFF030609)
            )
        )
    }

    internal val changelogClient by lazy { OkHttpClient() }
    internal val changelogStableUrl = "https://raw.githubusercontent.com/sujxlsahu/PurrfectSnap/dev/changelogs-stable.txt"
    internal val changelogPrereleaseUrl = "https://raw.githubusercontent.com/sujxlsahu/PurrfectSnap/dev/changelogs-prerelease.txt"
    internal val announcementsUrl = "https://raw.githubusercontent.com/sujxlsahu/PurrfectSnap/dev/announcements.txt"

    internal val heroGradientColors = listOf(
        Color(0xFF1B232C),
        Color(0xFF111821),
        Color(0xFF090F15)
    )
    internal val quickActionsGradientColors = listOf(
        Color(0xFF131A22),
        Color(0xFF0B1117)
    )
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    data class QaCard(val id: String, val name: String, val icon: ImageVector, val action: (Routes) -> Unit)
    internal val cardEntries by lazy {
        val list = mutableListOf<QaCard>()
        EnumQuickActions.entries.forEach { q ->
            val name = context.translation["actions.${q.key}.name"]
            list.add(QaCard(id = "quick.${q.key}", name = name, icon = q.icon, action = q.action))
        }
        EnumAction.entries.forEach { a ->
            val name = context.translation["actions.${a.key}.name"]
            list.add(QaCard(id = "action.${a.key}", name = name, icon = a.icon, action = { context.launchActionIntent(a) }))
        }
        list
    }
    internal val cards by lazy {
        EnumQuickActions.entries.map {
            (context.translation["actions.${it.key}.name"] to it.icon) to it.action
        }.associate {
            it.first to it.second
        }.toMutableMap().apply {
            EnumAction.entries.forEach { action ->
                this[context.translation["actions.${action.key}.name"] to action.icon] = {
                    context.launchActionIntent(action)
                }
            }
        }
    }

    @Composable
    internal fun rememberPreferenceBool(key: String, default: Boolean = false): State<Boolean> {
        val prefs = remember { context.sharedPreferences }
        val state = remember { mutableStateOf(prefs.getBoolean(key, default)) }
        DisposableEffect(prefs, key) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    state.value = prefs.getBoolean(key, default)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }

    @Composable
    fun ExternalLinkIcon(
        modifier: Modifier = Modifier,
        size: Dp = 44.dp,
        imageVector: ImageVector,
        onClick: (() -> Unit)? = null,
        tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        haptic: HapticFeedback? = null,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val clickModifier = if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { 
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick() 
            }
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .scaleOnPress(interactionSource)
                .then(clickModifier)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.55f)
            )
        }
    }

    @Composable
    internal fun HeroBadge(text: String) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }

    @Composable
    private fun TopBarActionChip(
        icon: ImageVector,
        label: String? = null,
        contentDescription: String? = label,
        onClick: () -> Unit,
    ) {
        Surface(
            shape = RoundedCornerShape(40),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(40))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = contentDescription, tint = Color.White)
                label?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
        OutlinedCard(
            modifier = Modifier
                .padding(start = cardMargin, end = cardMargin)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp),
                content = content
            )
        }
    }

    @Composable
    private fun RowScope.HomeActionChips() {
        TopBarActionChip(
            icon = Icons.Filled.BugReport,
            label = context.translation["manager.routes.home_logs"]
        ) { routes.homeLogs.navigate() }
        TopBarActionChip(
            icon = Icons.Filled.Info,
            label = translation["manager.routes.home_about"]
        ) { routes.about.navigate() }
    }


    @Composable
    private fun AuroraBackground() {
        val infiniteTransition = rememberInfiniteTransition(label = "aurora")
        val driftX by infiniteTransition.animateFloat(
            initialValue = -120f,
            targetValue = 220f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "driftX"
        )
        val driftY by infiniteTransition.animateFloat(
            initialValue = 80f,
            targetValue = -140f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "driftY"
        )
        val shimmer by infiniteTransition.animateFloat(
            initialValue = -120f,
            targetValue = 160f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer"
        )

        val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        val tertiaryGlow = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
        val trailGradient = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(
                        x = size.width * 0.25f + driftX,
                        y = size.height * 0.18f + driftY * 0.4f
                    ),
                    radius = size.minDimension * 0.9f
                ),
                alpha = 0.85f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryGlow, Color.Transparent),
                    center = Offset(
                        x = size.width * 0.78f - driftX * 0.45f,
                        y = size.height * 0.72f
                    ),
                    radius = size.minDimension * 0.95f
                ),
                alpha = 0.9f
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = trailGradient,
                    start = Offset(x = 0f, y = size.height * 0.15f + shimmer),
                    end = Offset(x = size.width, y = size.height * 0.9f + shimmer)
                ),
                size = this.size,
                alpha = 0.24f
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun HeroSection(
        versionName: String,
        latestUpdate: Updater.LatestRelease?,
        downloadState: UpdateDownloader.DownloadState,
        downloadProgress: Float,
        onUpdateAction: () -> Unit,
        channelLabel: String,
        isPurrAuraActive: Boolean,
        onWebsiteClick: () -> Unit,
        onTelegramClick: () -> Unit,
        onGithubClick: () -> Unit,
        authorName: String,
        onManageClick: () -> Unit,
        avenirNext: FontFamily
    ) {
        val heroShape = RoundedCornerShape(36.dp)
        val gitHashShort = remember { (context.installationSummary.modInfo?.gitHash ?: BuildConfig.GIT_HASH).take(7) }
        Box(
            modifier = Modifier
                .padding(horizontal = cardMargin, vertical = 6.dp)
                .clip(heroShape)
                .background(
                    Brush.linearGradient(
                        heroGradientColors
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), heroShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PurrfectSnap Lite",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = avenirNext
                    )
                    Text(
                        text = "Fucked By SUJ\u039bL",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        fontFamily = avenirNext
                    )
                    Text(
                        text = translation["hero_tagline"],
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
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
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = translation["update_title"],
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = translation.format(
                                        "update_content",
                                        "version" to (latestUpdate.versionName)
                                    ),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AnimatedContent(
                                targetState = downloadState,
                                label = "UpdateDownloadHero"
                            ) { state ->
                                when (state) {
                                    UpdateDownloader.DownloadState.IDLE,
                                    UpdateDownloader.DownloadState.FAILED -> {
                                        Button(
                                            onClick = onUpdateAction,
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color(0xFF1B152E)
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = translation["download_icon_description"],
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    UpdateDownloader.DownloadState.DOWNLOADING -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { downloadProgress },
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 3.dp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${(downloadProgress * 100).toInt()}%",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    UpdateDownloader.DownloadState.COMPLETED -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = translation["completed_icon_description"],
                                                tint = Color(0xFFA3F0C2)
                                            )
                                            Text(
                                                text = translation["update_ready_label"],
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isPurrAuraActive) PurrfectPalette.glowPrimary else Color(0xFF8C8CA3))
                                )
                                Text(
                                    text = if (isPurrAuraActive) translation["purr_aura_active_label"] else translation["purr_aura_inactive_label"],
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onManageClick,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Alignment.CenterHorizontally.let { Modifier.align(it) }
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(translation["open_settings_button"])
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onWebsiteClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1B152E)
                            )
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Site", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onGithubClick,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = translation["github_button"], maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ExternalLinkIcon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                            onClick = onTelegramClick,
                            tint = Color.White,
                            containerColor = Color.White.copy(alpha = 0.14f)
                        )
                    }
                }
            }
        }
    }
    internal fun resolveTileKey(name: String): String {
        val entry = cardEntries.firstOrNull { it.name == name }
        return entry?.id ?: name
    }
    private fun getTileSpan(name: String): Pair<Int, Int> {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        val raw = prefs.getString("quick_tile_size_$key", null) ?: "1x1"
        val parts = raw.split('x')
        val w = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        val h = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        return w to h
    }
    private fun setTileSpan(name: String, w: Int, h: Int) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().putString("quick_tile_size_$key", "${w.coerceIn(1,3)}x${h.coerceIn(1,3)}").apply()
    }
    internal fun clearTileSpan(name: String) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().remove("quick_tile_size_$key").apply()
    }

    internal fun clearTileOffset(name: String) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().remove("quick_tile_offset_$key").apply()
    }

    override val title: @Composable (() -> Unit)? = {}
    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }
    override val topBarActions: @Composable (RowScope.() -> Unit) = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            HomeActionChips()
        }
    }


    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(
            initialValue = context.config.root.global.uiSettings.managerTheme.get()
        ) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }
        key(themeId) {
            with(ManagerTheme.fromId(themeId).theme) {
                this@HomeRootSection.HomeScreen(nav)
            }
        }
    }

    internal fun extractChangelogForVersion(raw: String, version: String): String {
    val lines = raw.lines()
    val headerRegex = Regex("^\\s*#+\\s*v?${Regex.escape(version)}\\b", RegexOption.IGNORE_CASE)
    val collected = mutableListOf<String>()
    var collecting = false
    for (line in lines) {
        if (!collecting) {
            if (headerRegex.containsMatchIn(line)) {
                collecting = true
            }
            continue
        }
        if (line.trimStart().startsWith("#")) break
        collected.add(line)
    }
    return collected.joinToString("\n").trim()
}
}






