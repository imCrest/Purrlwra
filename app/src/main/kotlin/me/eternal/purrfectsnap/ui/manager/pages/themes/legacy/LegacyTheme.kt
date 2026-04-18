package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.legacy

import android.os.SystemClock
import android.content.SharedPreferences
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.drawToBitmap
import cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion.AphelionHaptics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.LogLine
import cock.crest.purrfectsnap.lite.LogReader
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.action.EnumQuickActions
import cock.crest.purrfectsnap.lite.common.BuildConfig
import cock.crest.purrfectsnap.lite.common.action.EnumAction
import cock.crest.purrfectsnap.lite.common.bridge.InternalFileHandleType
import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.PropertyPair
import cock.crest.purrfectsnap.lite.common.data.SocialScope
import cock.crest.purrfectsnap.lite.common.ui.TopBarActionButton
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import cock.crest.purrfectsnap.lite.storage.getAllScopeNotes
import cock.crest.purrfectsnap.lite.storage.getQuickTiles
import cock.crest.purrfectsnap.lite.storage.setAllScopeNotes
import cock.crest.purrfectsnap.lite.storage.setQuickTiles
import cock.crest.purrfectsnap.lite.ui.manager.ThemeContract
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.data.UpdateDownloader
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater
import cock.crest.purrfectsnap.lite.ui.manager.data.Updater.Channel
import cock.crest.purrfectsnap.lite.ui.manager.pages.TasksRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeAbout
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeLogs
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection.Companion.QUICK_TILES_INITIALIZED_PREF
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection.Companion.cardMargin
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection.Companion.pageBackgroundGradient
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeSettings
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.QuickActionsDialog
import cock.crest.purrfectsnap.lite.ui.manager.pages.scripting.ScriptingRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.SocialRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerManagerRoot
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.Requirements
import cock.crest.purrfectsnap.lite.ui.util.OnLifecycleEvent
import cock.crest.purrfectsnap.lite.ui.util.PurrfectMarqueeText
import cock.crest.purrfectsnap.lite.ui.util.openFile
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.PullRefreshIndicator
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.pullRefresh
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.rememberPullRefreshState
import cock.crest.purrfectsnap.lite.ui.util.saveFile
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

object LegacyTheme : ThemeContract {
    @OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
    @Composable override fun HomeRootSection.HomeScreen(nav: NavBackStackEntry) {

        @Composable
        fun LocalTopBarActionChip(
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
                        Text(text = it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        @Composable
        fun RowScope.LocalHomeActionChips() {
            LocalTopBarActionChip(icon = Icons.Filled.BugReport, label = context.translation["manager.routes.home_logs"]) { routes.homeLogs.navigate() }
            LocalTopBarActionChip(icon = Icons.Filled.Info, label = translation["manager.routes.home_about"]) { routes.about.navigate() }
        }

        @Composable
        fun LocalHeroSection(
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
                    .background(Brush.linearGradient(heroGradientColors))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), heroShape)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PurrfectSnap", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = avenirNext)
                        Text("By ΞTΞRNAL", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontFamily = avenirNext)
                        Text(text = translation["hero_tagline"] ?: "", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
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
                            tonalElevation = 0.dp, shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(translation["update_title"] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(translation.format("update_content", "version" to latestUpdate.versionName), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                AnimatedContent(targetState = downloadState, label = "UpdateDownloadHero") { state ->
                                    when (state) {
                                        UpdateDownloader.DownloadState.IDLE,
                                        UpdateDownloader.DownloadState.FAILED -> {
                                            Button(onClick = onUpdateAction, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), contentPadding = PaddingValues(12.dp)) {
                                                Icon(Icons.Default.Download, contentDescription = translation["download_icon_description"] ?: "", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        UpdateDownloader.DownloadState.DOWNLOADING -> {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 6.dp)) {
                                                CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
                                                Text("${(downloadProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        UpdateDownloader.DownloadState.COMPLETED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Check, contentDescription = translation["completed_icon_description"] ?: "", tint = Color(0xFFA3F0C2))
                                                Text(translation["update_ready_label"] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        tonalElevation = 0.dp, shadowElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (isPurrAuraActive) PurrfectPalette.glowPrimary else Color(0xFF8C8CA3)))
                                    Text(
                                        text = if (isPurrAuraActive) translation["purr_aura_active_label"] ?: "" else translation["purr_aura_inactive_label"] ?: "",
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { routes.settings.navigate() },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(translation["open_settings_button"] ?: "")
                            }
                        }
                    }

                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://purrfectsnap.vercel.app/", context.translation["toast_open_link_failed"]) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Site", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap", context.translation["toast_open_link_failed"]) }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = translation["github_button"] ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ExternalLinkIcon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                                onClick = { context.androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"]) },
                                tint = Color.White, containerColor = Color.White.copy(alpha = 0.14f)
                            )
                        }
                    }
                }
            }
        }

        val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }
        val prefs = remember { context.sharedPreferences }
        val allQuickTileNames = remember(cards) { cards.keys.map { it.first } }
        val selectedTiles = rememberAsyncMutableStateList(defaultValue = allQuickTileNames) {
            val storedTiles = context.database.getQuickTiles().filter { it.isNotBlank() }
            val hasInitializedQuickTiles = prefs.getBoolean(QUICK_TILES_INITIALIZED_PREF, false)
            when {
                storedTiles.isNotEmpty() -> {
                    if (!hasInitializedQuickTiles) prefs.edit().putBoolean(QUICK_TILES_INITIALIZED_PREF, true).apply()
                    storedTiles
                }
                hasInitializedQuickTiles -> storedTiles
                else -> {
                    context.database.setQuickTiles(allQuickTileNames)
                    prefs.edit().putBoolean(QUICK_TILES_INITIALIZED_PREF, true).apply()
                    allQuickTileNames
                }
            }
        }
        val updateChannel = context.config.root.global.updateSettings.updateChannel.getNullable() ?: "stable"
        val channelLabel = if (updateChannel == "prerelease") translation["channel_label_prerelease"] ?: "" else translation["channel_label_stable"] ?: ""
        val latestUpdate by rememberAsyncMutableState(defaultValue = null, keys = arrayOf(updateChannel)) {
            val channel = if (updateChannel == "prerelease") Channel.PRERELEASE else Channel.STABLE
            Updater.getLatestRelease(channel)
        }
        val changelogUrl = if (updateChannel == "prerelease") changelogPrereleaseUrl else changelogStableUrl
        val downloadState by UpdateDownloader.downloadState.collectAsState()
        val downloadProgress by UpdateDownloader.downloadProgress.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val isPurrAuraActive by rememberPreferenceBool("debug_test_mode", true)
        var showChangelogDialog by remember { mutableStateOf(false) }
        var changelogLoading by remember { mutableStateOf(false) }
        var changelogError by remember { mutableStateOf<String?>(null) }
        var changelogText by remember { mutableStateOf<String?>(null) }
        var changelogVersion by remember { mutableStateOf<String?>(null) }
        var showFullChangelogDialog by remember { mutableStateOf(false) }
        var fullChangelogLoading by remember { mutableStateOf(false) }
        var fullChangelogError by remember { mutableStateOf<String?>(null) }
        var fullChangelogText by remember { mutableStateOf<String?>(null) }
        var showAnnouncementsDialog by remember { mutableStateOf(false) }
        var announcementsLoading by remember { mutableStateOf(false) }
        var announcementsError by remember { mutableStateOf<String?>(null) }
        var announcementsText by remember { mutableStateOf<String?>(null) }

        val handleUpdateAction: () -> Unit = {
            latestUpdate?.let { latest ->
                val supportedAbis = android.os.Build.SUPPORTED_ABIS
                var abiName: String? = null
                for (abi in supportedAbis) {
                    when (abi) {
                        "arm64-v8a" -> { abiName = "arm64"; break }
                        "armeabi-v7a" -> { abiName = "armv7"; break }
                    }
                }
                if (latest.workflowId != null) {
                    if (abiName == null) {
                        android.widget.Toast.makeText(context.androidContext, translation["update_arch_not_supported_toast"], android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val artifactName = "purrfectsnap-${abiName}-debug"
                        val downloadUrl = "https://nightly.link/particle-box/PurrfectSnap/actions/runs/${latest.workflowId}/$artifactName.zip"
                        UpdateDownloader.downloadAndInstall(context, downloadUrl, "$artifactName.zip", coroutineScope)
                    }
                    return@let
                }
                val releaseDownload = abiName?.let { arch -> latest.assetDownloads[arch] }
                if (releaseDownload != null) {
                    val fileName = releaseDownload.substringAfterLast('/')
                    UpdateDownloader.downloadAndInstall(context, releaseDownload, fileName, coroutineScope)
                } else {
                    context.androidContext.openLink(latest.releaseUrl, context.translation["toast_open_link_failed"])
                }
            }
        }

        fun loadChangelog(targetVersion: String, url: String) {
            if (changelogVersion == targetVersion && changelogText != null) return
            changelogLoading = true; changelogError = null
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    changelogClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("Failed to fetch changelog (${response.code})")
                        val body = response.body?.string() ?: throw IllegalStateException("Empty changelog body")
                        extractChangelogForVersion(body, targetVersion).ifBlank { body.trim() }
                    }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { changelogText = text; changelogVersion = targetVersion; changelogLoading = false }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) { changelogError = error.message ?: "Failed to load changelog"; changelogLoading = false }
                }
            }
        }

        fun loadAnnouncements() {
            if (announcementsText != null) return
            announcementsLoading = true; announcementsError = null
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    changelogClient.newCall(Request.Builder().url(announcementsUrl).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("Failed to fetch announcements (${response.code})")
                        response.body?.string()?.trim() ?: throw IllegalStateException("Empty announcements body")
                    }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { announcementsText = text; announcementsLoading = false }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) { announcementsError = error.message ?: "Failed to load announcements"; announcementsLoading = false }
                }
            }
        }

        fun loadFullChangelog(url: String) {
            if (fullChangelogText != null) return
            fullChangelogLoading = true; fullChangelogError = null
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    changelogClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("Failed to fetch changelog (${response.code})")
                        response.body?.string()?.trim() ?: throw IllegalStateException("Empty changelog body")
                    }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { fullChangelogText = text; fullChangelogLoading = false }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) { fullChangelogError = error.message ?: "Failed to load changelog"; fullChangelogLoading = false }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (context.sharedPreferences.getBoolean("show_changelog_on_launch", false)) {
                val version = context.sharedPreferences.getString("changelog_version_on_launch", null)
                context.sharedPreferences.edit().putBoolean("show_changelog_on_launch", false).remove("changelog_version_on_launch").apply()
                version?.let { showChangelogDialog = true; loadChangelog(it, changelogUrl) }
            }
        }

        LaunchedEffect(Unit) {
            if (context.sharedPreferences.getBoolean("show_announcements_on_launch", false)) {
                context.sharedPreferences.edit().putBoolean("show_announcements_on_launch", false).apply()
                showAnnouncementsDialog = true; loadAnnouncements()
            }
        }

        val onUpdateButtonClick: () -> Unit = {
            latestUpdate?.let { showChangelogDialog = true; loadChangelog(it.versionName, changelogUrl) }
        }

        var showQuickActionsMenu by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentBottomPadding = routes.bottomPadding + navigationBarPadding + 96.dp

        Box(modifier = Modifier.fillMaxSize().background(pageBackgroundGradient)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = contentBottomPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues()).padding(horizontal = cardMargin, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalTopBarActionChip(icon = Icons.Filled.Notifications, label = null, contentDescription = translation["announcements_button_description"]) {
                            showAnnouncementsDialog = true; loadAnnouncements()
                        }
                        LocalTopBarActionChip(icon = Icons.Filled.Description, label = null, contentDescription = translation.getOrNull("changelog_button_description") ?: "Open full changelog") {
                            showFullChangelogDialog = true; loadFullChangelog(changelogUrl)
                        }
                    }
                    Row(modifier = Modifier.wrapContentWidth(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalHomeActionChips()
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LocalHeroSection(
                    versionName = BuildConfig.VERSION_NAME,
                    latestUpdate = latestUpdate,
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
                    onUpdateAction = onUpdateButtonClick,
                    channelLabel = channelLabel,
                    isPurrAuraActive = isPurrAuraActive,
                    onWebsiteClick = { context.androidContext.openLink("https://purrfectsnap.vercel.app/", context.translation["toast_open_link_failed"]) },
                    onTelegramClick = { context.androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"]) },
                    onGithubClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap", context.translation["toast_open_link_failed"]) },
                    authorName = "ETERNAL",
                    onManageClick = { routes.settings.navigate() },
                    avenirNext = avenirNext
                )
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedContent(targetState = selectedTiles.isNotEmpty(), label = "QuickActionsAnim") { hasQuickActions ->
                    val quickCardShape = RoundedCornerShape(34.dp)
                    Surface(
                        modifier = Modifier.padding(horizontal = cardMargin, vertical = 10.dp),
                        shape = quickCardShape, tonalElevation = 0.dp, shadowElevation = 24.dp,
                        color = Color.Transparent, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(quickActionsGradientColors)).padding(horizontal = 24.dp, vertical = 28.dp).padding(bottom = navigationBarPadding + 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!hasQuickActions) {
                                Text(translation["quick_actions_title"] ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(24.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Widgets, contentDescription = translation["quick_actions_icon_description"], modifier = Modifier.size(72.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(translation["quick_actions_empty_title"] ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(translation["quick_actions_empty_subtitle"] ?: "", fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(onClick = { showQuickActionsMenu = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                        Icon(Icons.Default.Add, contentDescription = translation["add_quick_action_description"], modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(translation["quick_actions_add_tile_button"] ?: "")
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(translation["quick_actions_title"] ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White, maxLines = 3, overflow = TextOverflow.Clip)
                                    Text(translation.format("quick_actions_count_label", "count" to selectedTiles.size.toString()), fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                                    Row(modifier = Modifier.wrapContentWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = { showQuickActionsMenu = true }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White) ) {
                                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_manage), contentDescription = translation["manage_quick_actions_description"], modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(translation["quick_actions_manage_button"] ?: "")
                                        }
                                    }
                                }
                                val spacing = 12.dp
                                val gridPadding = 8.dp
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val preferredTileWidth = 100.dp
                                    val columns = ((maxWidth + spacing) / (preferredTileWidth + spacing)).toInt().coerceAtLeast(2).coerceAtMost(4)
                                    val computedWidth = (maxWidth - gridPadding * 2 - spacing * (columns - 1)) / columns
                                    val tileWidth = if (computedWidth < preferredTileWidth) computedWidth else preferredTileWidth
                                    FlowRow(modifier = Modifier.fillMaxWidth().padding(all = gridPadding), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(spacing), maxItemsInEachRow = columns) {
                                        selectedTiles.forEach { tileName ->
                                            val cardEntry = cards.entries.find { entry -> entry.key.first == tileName } ?: return@forEach
                                            val (card, action) = cardEntry
                                            val interactionSource = remember { MutableInteractionSource() }
                                            Surface(
                                                modifier = Modifier.width(tileWidth).aspectRatio(1.05f).scaleOnPress(interactionSource).clickable { action(routes) },
                                                shape = RoundedCornerShape(18.dp),
                                                color = Color.White.copy(alpha = 0.06f), tonalElevation = 0.dp, shadowElevation = 0.dp,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                            ) {
                                                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.3f), PurrfectPalette.glowSecondary.copy(alpha = 0.22f)))).clipToBounds()) {
                                                    Column(modifier = Modifier.fillMaxSize().padding(all = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                        Icon(
                                                            imageVector = card.second, contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(44.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = card.first,
                                                            lineHeight = 16.sp,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center,
                                                            color = Color.White,
                                                            overflow = TextOverflow.Ellipsis,
                                                            maxLines = 2,
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
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showChangelogDialog) {
            AestheticDialog(
                onDismissRequest = { showChangelogDialog = false },
                title = translation["changelog_dialog_title"] ?: "Changelog",
                text = "", icon = Icons.Filled.Info,
                confirmButtonText = translation["changelog_dialog_update_button"] ?: "Update",
                onConfirm = { showChangelogDialog = false; handleUpdateAction() },
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
                        else if (announcementsError != null) Text(announcementsError!!, color = Color.Red, fontSize = 14.sp)
                        else Text(announcementsText ?: translation["announcements_dialog_empty"] ?: "", color = PurrfectPalette.textPrimary, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showFullChangelogDialog) {
            AestheticDialog(
                onDismissRequest = { showFullChangelogDialog = false },
                title = translation["changelog_dialog_title"] ?: "Changelog",
                text = "", icon = Icons.Filled.Description,
                confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
                onConfirm = { showFullChangelogDialog = false },
                showCloseButton = false,
                customContent = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable override fun HomeSettings.SettingsScreen(nav: NavBackStackEntry) {
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val hapticFeedback = LocalHapticFeedback.current
        val view = LocalView.current
        var switchCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val positiveLabel = context.translation["button.positive"]
        val negativeLabel = context.translation["button.negative"]
        val importLabel = context.translation["button.import"]
        val sharedButtonColors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White
        )
        val sharedOutlinedColors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        var showResetSetupDialog by remember { mutableStateOf(false) }

        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            if (showResetSetupDialog) {
                AestheticDialog(
                    onDismissRequest = { showResetSetupDialog = false },
                    title = translation["reset_setup_dialog_title"],
                    text = translation["reset_setup_dialog_text"],
                    icon = Icons.Filled.Warning,
                    confirmButtonText = positiveLabel,
                    dismissButtonText = negativeLabel,
                    onConfirm = {
                        showResetSetupDialog = false
                        context.sharedPreferences.edit()
                            .remove("setup_in_progress")
                            .remove("setup_current_route")
                            .remove("setup_skip_patch")
                            .remove("setup_install_mode")
                            .apply()

                        context.config.reset()
                        context.config.writeConfig()

                        val intent = Intent(context.androidContext, cock.crest.purrfectsnap.lite.ui.setup.SetupActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.androidContext.startActivity(intent)
                        routes.navController.popBackStack()
                    },
                    onDismiss = { showResetSetupDialog = false },
                    showCloseButton = false
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f)))),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { routes.navController.popBackStack() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                        Text(text = translation["manager.routes.home_settings"] ?: "Settings", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        IconButton(onClick = { routes.navigation?.openBottomBarCustomization = true }) {
                            Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassCard {
                            RowTitle(title = translation["ui_theme_title"] ?: "UI Theme")
                            ShiftedRow {
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = translation["settings_ui_theme"] ?: "Aphelion Theme", fontSize = 14.sp, color = Color.White)
                                    val currentThemeId = context.config.root.global.uiSettings.managerTheme.get()
                                    var localThemeId by remember { mutableStateOf(currentThemeId) }

                                    Switch(                                        checked = localThemeId == "APHELION",
                                        onCheckedChange = { isAphelion ->
                                            val newId = if (isAphelion) "APHELION" else "LEGACY"
                                            localThemeId = newId // Update UI instantly

                                            AphelionHaptics.themeRevealTick(context, hapticFeedback)

                                            // 1. Capture bitmap BEFORE theme change
                                            val bitmap = runCatching { view.drawToBitmap() }.getOrNull()

                                            // 2. Request Reveal
                                            routes.navigation?.themeRevealState?.requestReveal(
                                                newThemeId = newId,
                                                originCenter = switchCenter,
                                                bitmap = bitmap
                                            )

                                            // 3. Apply theme and persist
                                            scope.launch {
                                                kotlinx.coroutines.delay(50)
                                                context.config.root.global.uiSettings.managerTheme.set(newId)
                                                
                                                // Write to disk immediately on IO thread and finish
                                                val writeJob = launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    context.config.writeConfig()
                                                }
                                                writeJob.join() // Wait for write to finish
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(end = 26.dp)
                                            .onGloballyPositioned { coords ->
                                                val rootPos = coords.positionInRoot()
                                                switchCenter = androidx.compose.ui.geometry.Offset(
                                                    x = rootPos.x + coords.size.width / 2f,
                                                    y = rootPos.y + coords.size.height / 2f
                                                )
                                            },
                                        colors = purrfectSwitchColors()
                                    )
                                }
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["actions_title"])
                            EnumAction.entries.forEach { enumAction ->
                                RowAction(key = enumAction.key) { context.launchActionIntent(enumAction) }
                            }
                            RowAction(key = "regen_mappings") { context.checkForRequirements(Requirements.MAPPINGS) }
                            RowAction(key = "change_language") { context.checkForRequirements(Requirements.LANGUAGE) }
                        }

                        GlassCard {
                            RowTitle(title = translation["ui_settings_title"])
                            ShiftedRow {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = translation["haptic_feedback_label"], fontSize = 14.sp)
                                        var hapticEnabled by remember { mutableStateOf(context.config.root.global.uiSettings.hapticFeedback.getNullable() ?: true) }
                                        Switch(checked = hapticEnabled, onCheckedChange = { if (it) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); hapticEnabled = it; context.config.root.global.uiSettings.hapticFeedback.set(it); context.config.writeConfig() }, modifier = Modifier.padding(end = 26.dp), colors = purrfectSwitchColors())
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = translation["use_system_toasts_label"], fontSize = 14.sp)
                                        var useSystemToasts by remember { mutableStateOf(context.config.root.global.uiSettings.useSystemToasts.getNullable() ?: false) }
                                        Switch(checked = useSystemToasts, onCheckedChange = { if (context.config.root.global.uiSettings.hapticFeedback.get()) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); useSystemToasts = it; context.config.root.global.uiSettings.useSystemToasts.set(it); context.config.writeConfig() }, modifier = Modifier.padding(end = 26.dp), colors = purrfectSwitchColors())
                                    }
                                }
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["updates_title"])
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                var autoUpdateCheck by remember { mutableStateOf(context.config.root.global.updateSettings.autoUpdateCheck.getNullable() ?: true) }
                                var selectedChannel by remember { mutableStateOf(context.config.root.global.updateSettings.updateChannel.getNullable() ?: "stable") }
                                var channelMenuExpanded by remember { mutableStateOf(false) }
                                ShiftedRow {
                                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = translation["auto_update_check"], fontSize = 14.sp)
                                        Switch(checked = autoUpdateCheck, onCheckedChange = { if (context.config.root.global.uiSettings.hapticFeedback.get()) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); autoUpdateCheck = it; context.config.root.global.updateSettings.autoUpdateCheck.set(it); context.config.writeConfig(); scheduleUpdateCheck() }, modifier = Modifier.padding(end = 26.dp), colors = purrfectSwitchColors())
                                    }
                                }
                                AnimatedVisibility(visible = autoUpdateCheck) {
                                    ExposedDropdownMenuBox(expanded = channelMenuExpanded, onExpandedChange = { channelMenuExpanded = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                                        AestheticDropdownField(value = translation.getOrNull("update_channel_${selectedChannel}") ?: selectedChannel, expanded = channelMenuExpanded, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), onClick = { channelMenuExpanded = true })
                                        ExposedDropdownMenu(expanded = channelMenuExpanded, onDismissRequest = { channelMenuExpanded = false }) {
                                            listOf("stable", "prerelease").forEach { channel ->
                                                DropdownMenuItem(text = { Text(text = translation.getOrNull("update_channel_${channel}") ?: channel) }, onClick = { selectedChannel = channel; channelMenuExpanded = false; context.config.root.global.updateSettings.updateChannel.set(channel); context.config.writeConfig(); scheduleUpdateCheck() })
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["reset_setup_title"])
                            ShiftedRow(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp).clickable { showResetSetupDialog = true }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = translation["reset_setup_action"], fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 14.dp))
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["message_logger_title"])
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                var storedMessagesCount by rememberAsyncMutableState(defaultValue = 0) { context.messageLogger.getStoredMessageCount() }
                                var storedStoriesCount by rememberAsyncMutableState(defaultValue = 0) { context.messageLogger.getStoredStoriesCount() }
                                var showImportDialog by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val summary = translation.format("message_logger_summary", "messageCount" to storedMessagesCount.toString(), "storyCount" to storedStoriesCount.toString()).replace("\n", " | ")
                                    Text(summary, maxLines = 2, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp) ) {
                                        Button(onClick = { runCatching { activityLauncherHelper.saveFile("message_logger.db", "application/octet-stream") { uri -> context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { out -> context.messageLogger.databaseFile.inputStream().use { it.copyTo(out) } } } }.onFailure { context.log.error("Failed to export", it) } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["export_button"]) }
                                        Button(onClick = { runCatching { activityLauncherHelper.openFile("application/octet-stream") { uri -> val tempFile = File(context.androidContext.cacheDir, "view_logger.db"); context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { it.copyTo(tempFile.outputStream()) }; routes.viewLoggerHistory.navigate { put("uri", URLEncoder.encode(tempFile.toUri().toString(), "UTF-8")) } } } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["view_button"]) }
                                        Button(onClick = { runCatching { context.messageLogger.purgeAll(); storedMessagesCount = 0; storedStoriesCount = 0 }.onSuccess { context.shortToast(translation["success_toast"]) } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["clear_button"]) }
                                        Button(onClick = { showImportDialog = true }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["import_button"]) }
                                    }
                                }
                                OutlinedButton(modifier = Modifier.fillMaxWidth().padding(5.dp), onClick = { routes.loggerHistory.navigate() }, colors = sharedOutlinedColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) { Text(translation["view_logger_history_button"]) }
                                if (showImportDialog) {
                                    AestheticDialog(onDismissRequest = { showImportDialog = false }, title = translation["message_logger_import_title"], text = translation["message_logger_import_text"], icon = Icons.Filled.Info, confirmButtonText = importLabel, dismissButtonText = context.translation["button.cancel"], onConfirm = { showImportDialog = false; runCatching { activityLauncherHelper.openFile("application/octet-stream") { uri -> context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { context.messageLogger.databaseFile.outputStream().use { out -> it.copyTo(out) } }; storedMessagesCount = context.messageLogger.getStoredMessageCount(); storedStoriesCount = context.messageLogger.getStoredStoriesCount(); context.shortToast(translation["success_toast"]) } } }, onDismiss = { showImportDialog = false }, showCloseButton = false)
                                }
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["friend_notes_title"])
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = translation["friend_notes_description"], modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp), color = Color.White, textAlign = TextAlign.Center)
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = { runCatching { val notes = context.database.getAllScopeNotes(); if (notes.isEmpty()) return@runCatching; val json = context.gson.toJson(notes); activityLauncherHelper.saveFile("notes.json", "application/json") { uri -> context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { it.write(json.toByteArray()) }; context.shortToast(translation["friend_notes_backup_success"]) } } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["backup_button"]) }
                                        Button(onClick = { runCatching { activityLauncherHelper.openFile("application/json") { uri -> context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { val json = it.reader().readText(); val notes = context.gson.fromJson<Map<String, String>>(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type); context.database.setAllScopeNotes(notes); context.shortToast(translation["friend_notes_restore_success"]) } } } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["restore_button"]) }
                                    }
                                }
                            }
                        }

                        GlassCard {
                            RowTitle(title = translation["debug_title"])
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                                    var selectedFileType by remember { mutableStateOf(InternalFileHandleType.entries.first()) }
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1f)) {
                                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
                                            AestheticDropdownField(value = translation.getOrNull("debug_file_${selectedFileType.name.lowercase()}") ?: selectedFileType.fileName, expanded = expanded, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), onClick = { expanded = true })
                                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                InternalFileHandleType.entries.forEach { fileType -> DropdownMenuItem(onClick = { expanded = false; selectedFileType = fileType }, text = { Text(text = translation.getOrNull("debug_file_${fileType.name.lowercase()}") ?: fileType.fileName) }) }
                                            }
                                        }
                                    }
                                    Button(onClick = { runCatching { scope.launch { selectedFileType.resolve(context.androidContext).delete() } }.onSuccess { context.shortToast(translation["success_toast"]) } }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp)); Text(translation["clear_button"])
                                    }
                                }
                                ShiftedRow {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        PremiumPreferenceToggle(context.sharedPreferences, key = "test_mode", text = translation["test_mode_label"], defaultValue = true, confirmDisableTitle = translation["purr_aura_disable_title"], confirmDisableText = translation["purr_aura_disable_text"])
                                        PreferenceToggle(context.sharedPreferences, key = "disable_feature_loading", text = translation["disable_feature_loading_label"])
                                        PreferenceToggle(context.sharedPreferences, key = "disable_mapper", text = translation["disable_auto_mapper_label"])
                                        PreferenceToggle(context.sharedPreferences, key = "disable_bypass_indicator", text = translation["disable_bypass_indicator_label"])
                                        PreferenceToggle(context.sharedPreferences, key = "disable_cant_login_button", text = translation["disable_cant_login_button_label"] ?: "Disable Can't Login Button")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(routes.bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp))
                    }
                }
            }
        }
    }
    
    @Composable override fun HomeAbout.AboutScreen(nav: NavBackStackEntry) {
        val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }
        val scrollState = rememberScrollState()
        val aboutStory = remember { translation["about_story"] ?: "" }
        val pagePadding = 16.dp
        val bottomPadding = routes.bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        val tapSource = remember { MutableInteractionSource() }
        val tapCount = remember { mutableIntStateOf(0) }
        val lastTapTime = remember { mutableStateOf(0L) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = bottomPadding)
            ) {
                val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Spacer(modifier = Modifier.height(topPadding))

                Surface(
                    modifier = Modifier.padding(horizontal = pagePadding, vertical = 12.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f)))),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { routes.navController.popBackStack() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                        Text(text = routeInfo.translatedKey?.value ?: translation["manager.routes.home_about"] ?: "About", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.background(PurrfectPalette.panelGradient).padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = translation["about_title"] ?: "About",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = avenirNext,
                            modifier = Modifier.clickable(interactionSource = tapSource, indication = null) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastTapTime.value > 1500L) { tapCount.intValue = 0 }
                                tapCount.intValue += 1
                                lastTapTime.value = now
                                if (tapCount.intValue >= 5) { tapCount.intValue = 0; routes.retroGame.navigate() }
                            }
                        )
                        Text(text = translation["about_tagline"] ?: "", fontSize = 13.sp, color = Color(0xFFD9D3FF), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(text = translation["about_lead_developers_title"] ?: "Lead Developers", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            DeveloperCard(name = "ΞTΞRNAL", imageRes = R.drawable.pfp_external, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                            DeveloperCard(name = "<RSR/>", imageRes = R.drawable.pfp_rsr, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    tonalElevation = 0.dp, shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.background(PurrfectPalette.cardOverlay).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = translation["about_story_title"] ?: "Our Story", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = aboutStory, fontSize = 14.sp, color = Color(0xFFD9D3FF), lineHeight = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    tonalElevation = 0.dp, shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = translation["about_thanks_title"] ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap", context.translation["toast_open_link_failed"] ?: "") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = translation["github_button"] ?: "GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"] ?: "") }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = translation["telegram_button"] ?: "Telegram", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    @Composable
    internal fun HomeAbout.DeveloperCard(
        name: String,
        imageRes: Int,
        avenirNext: FontFamily,
        modifier: Modifier = Modifier
    ) {
        val tapSource = remember { MutableInteractionSource() }
        Surface(
            modifier = modifier.scaleOnPress(tapSource),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary)))
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                PurrfectMarqueeText(
                    text = name,
                    color = Color.White,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = avenirNext
                    )
                )
            }
        }
    }

    @Composable override fun HomeLogs.LogsScreen(nav: NavBackStackEntry) {
        val coroutineScope = rememberCoroutineScope()
        val composeContext = LocalContext.current
        var logReader by remember { mutableStateOf<LogReader?>(null) }
        val visibleLogs = remember { mutableStateListOf<LogLine>() }
        val mainExecutor = remember { context.androidContext.mainExecutor }
        var isRefreshing by remember { mutableStateOf(false) }
        var showFilterDialog by remember { mutableStateOf(false) }

        fun refreshLogs() {
            coroutineScope.launch {
                val readerResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.log.newReader { line ->
                            if (shouldHideLog(line)) return@newReader
                            mainExecutor.execute {
                                visibleLogs.add(line)
                            }
                        }
                    }
                }
                readerResult.onFailure {
                    context.longToast(translation["read_logs_failed_toast"])
                }
                readerResult.getOrNull()?.let { reader ->
                    logReader = reader
                    val filteredLogs = withContext(Dispatchers.IO) {
                        (0 until reader.lineCount).mapNotNull { index ->
                            reader.getLogLine(index)?.takeUnless(::shouldHideLog)
                        }
                    }
                    visibleLogs.clear()
                    visibleLogs.addAll(filteredLogs)
                }
                delay(220)
                if (visibleLogs.isNotEmpty()) {
                    val targetIndex = (visibleLogs.size - 1).coerceAtLeast(0)
                    logListState.scrollToItem(targetIndex)
                }
                isRefreshing = false
            }
        }

        @Composable
        fun LogFilterDialog() {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showFilterDialog = false }) {
                cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme {
                    cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard(title = translation["filter_logs_title"] ?: "Filter Log Categories", modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeLogs.LogCategory.entries.forEach { category ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            // Solo Focus Logic: Tap the name to filter only this category
                                            enabledCategories.keys.forEach { enabledCategories[it] = false }
                                            enabledCategories[category] = true
                                            isRefreshing = true
                                            refreshLogs()
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = enabledCategories[category] == true,
                                        onCheckedChange = { checked ->
                                            enabledCategories[category] = checked
                                            isRefreshing = true
                                            refreshLogs()
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = PurrfectPalette.glowPrimary,
                                            uncheckedColor = Color.White.copy(alpha = 0.4f),
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Text(
                                        text = translation[category.translationKey] ?: category.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = { showFilterDialog = false },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurrfectPalette.glowPrimary)
                                ) {
                                    Text(translation["filter_logs_done_button"] ?: "Done")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFilterDialog) {
            LogFilterDialog()
        }

        LaunchedEffect(externalRefreshTick.intValue) {
            if (externalRefreshTick.intValue > 0) {
                isRefreshing = true
                refreshLogs()
            }
        }
        val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = {
            isRefreshing = true
            refreshLogs()
        })
        LaunchedEffect(Unit) {
            isRefreshing = true
            refreshLogs()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                this@LogsScreen.LogsFloatingBar(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        refreshLogs()
                    },
                    onFilter = { showFilterDialog = true },
                    onExport = { exportLogs() },
                    onClear = { clearLogsAndReload() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    if (visibleLogs.isEmpty() && logReader != null) {
                        this@LogsScreen.EmptyLogsState()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize(),
                            state = logListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 12.dp,
                                bottom = routes.bottomPadding + 22.dp
                            )
                        ) {
                            items(visibleLogs, key = { it.hashCode() }) { line ->
                                this@LogsScreen.LogEntryCard(line = line, composeContext = composeContext)
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
            )
        }
    }

    @Composable override fun SocialRootSection.SocialScreen(nav: NavBackStackEntry) {
        val titles = remember {
            listOf(translation["friends_tab"], translation["groups_tab"])
        }
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        var searchActive by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            context.database.receiveMessagingDataCallback = { friends, groups ->
                friendList = friends
                groupList = groups
            }
            updateScopeLists()
        }
        DisposableEffect(Unit) {
            onDispose {
                context.database.receiveMessagingDataCallback = { _, _ -> }
            }
        }
        val normalizedQuery = remember(searchQuery) { searchQuery.trim() }
        val filteredFriends = remember(friendList, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                friendList
            } else {
                friendList.filter {
                    it.mutableUsername.contains(normalizedQuery, ignoreCase = true) ||
                        it.displayName?.contains(normalizedQuery, ignoreCase = true) == true
                }
            }
        }
        val filteredGroups = remember(groupList, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                groupList
            } else {
                groupList.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            SocialHeader(
                titles = titles,
                pagerState = pagerState,
                onTabSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
                friendCount = friendList.size,
                groupCount = groupList.size,
                searchActive = searchActive,
                onSearchToggle = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                }
            )
            if (searchActive) {
                val searchHint = context.translation["manager.dialogs.add_friend.search_hint"]
                val searchShape = RoundedCornerShape(18.dp)
                val searchBorder = Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                    )
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = searchShape,
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, searchBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurrfectPalette.cardOverlay, searchShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = searchHint,
                            tint = PurrfectPalette.textSecondary
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(PurrfectPalette.glowSecondary),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchHint,
                                    color = PurrfectPalette.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = translation["clear_search_button_description"],
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalPager(
                modifier = Modifier
                    .fillMaxSize(),
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> ScopeList(SocialScope.FRIEND, filteredFriends, filteredGroups)
                    1 -> ScopeList(SocialScope.GROUP, filteredFriends, filteredGroups)
                }
            }
        }
    }

    @Composable override fun TasksRootSection.TasksScreen(nav: NavBackStackEntry) {
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        var showConfirmDialog by remember { mutableStateOf(false) }
        var alsoDeleteFiles by remember { mutableStateOf(false) }
        val hapticFeedback = LocalHapticFeedback.current

        LaunchedEffect(Unit) {
            while (true) {
                fetchActiveTasks(this)
                fetchNewRecentTasks()
                delay(1000)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
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
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = context.translation["manager.routes.tasks"],
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            if (isRecentTasksInitialized()) {
                                Text(
                                    text = if (activeTasks.isNotEmpty()) {
                                        translation.format(
                                            "summary_active",
                                            "active" to activeTasks.size.toString(),
                                            "recent" to recentTasks.size.toString()
                                        )
                                    } else {
                                        translation.format(
                                            "summary_idle",
                                            "recent" to recentTasks.size.toString()
                                        )
                                    },
                                    color = PurrfectPalette.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (taskSelection.size > 1) {
                                val canMergeSelection by rememberAsyncMutableState(defaultValue = false, keys = arrayOf(taskSelection.size)) {
                                    taskSelection.all { it.second?.type?.contains("video") == true }
                                }
                                if (canMergeSelection) {
                                    Surface(
                                        onClick = {
                                            if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            mergeSelection(
                                                taskSelection.toList()
                                                    .also { taskSelection.clear() }
                                                    .map { it.first to it.second!! }
                                            )
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.Merge, contentDescription = translation["merge_button"], tint = Color.White, modifier = Modifier.size(16.dp))
                                            Text(translation["merge_button"] ?: "Merge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.PlaylistAddCheckCircle, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = translation.format("running_count", "count" to activeTasks.size.toString()),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(onClick = { showConfirmDialog = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = translation["clear_button_description"], tint = Color.White)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 0.dp,
                            bottom = routes.bottomPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            if (activeTasks.isEmpty() && (if (isRecentTasksInitialized()) recentTasks.isEmpty() else true)) {
                                TasksEmptyState(text = translation["no_tasks"] ?: "No tasks")
                            }
                        }

                        items(activeTasks, key = { it.taskId }) { pendingTask ->
                            TaskCard(modifier = Modifier.fillMaxWidth(), pendingTask.task, pendingTask = pendingTask)
                        }

                        if (isRecentTasksInitialized()) {
                            items(recentTasks, key = { it.hash }) { task ->
                                TaskCard(modifier = Modifier.fillMaxWidth(), task)
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmDialog) {
            val isSelection = taskSelection.isNotEmpty()
            val titleText = if (isSelection) {
                translation.format("remove_selected_tasks_confirm", "count" to taskSelection.size.toString())
            } else {
                translation["remove_all_tasks_confirm"]
            }
            val messageText = if (isSelection) translation["remove_selected_tasks_title"] else translation["remove_all_tasks_title"]

            TaskDangerDialog(
                visible = showConfirmDialog,
                title = titleText ?: "",
                message = messageText ?: "",
                showDeleteFiles = isSelection,
                deleteFilesChecked = alsoDeleteFiles,
                onToggleDeleteFiles = { alsoDeleteFiles = it },
                onConfirm = {
                    showConfirmDialog = false
                    clearTasks(alsoDeleteFiles, scope)
                },
                onDismiss = { showConfirmDialog = false }
            )
        }
    }

    @Composable override fun FeaturesRootSection.FeaturesScreen(nav: NavBackStackEntry) {
        Container(context.config.root, stateKey = "${routeInfo.id}:container:root")
    }
    @Composable override fun ScriptingRootSection.ScriptingScreen(nav: NavBackStackEntry) {
        val scriptingFolder by rememberAsyncMutableState(
            defaultValue = null,
            updateDispatcher = reloadDispatcher
        ) { context.scriptManager.getScriptsFolder() }
        val tabTitles = listOf(translation["installed_scripts_tab"], translation["catalog_tab"])
        var showImportDialog by remember { mutableStateOf(false) }
        var showToast by remember { mutableStateOf(false) }

        LaunchedEffect(scriptingFolder) {
            if (scriptingFolder == null && selectedTab != 0) {
                selectedTab = 0
            }
        }

        if (showImportDialog) {
            ImportRemoteScript { showImportDialog = false }
        }
        if (showToast) {
            LaunchedEffect(showToast) {
                context.shortToast(translation["select_scripts_folder_toast"])
                showToast = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            ScriptingHeader(
                titles = tabTitles,
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    if (index == 1 && scriptingFolder == null) {
                        showToast = true
                    } else {
                        selectedTab = index
                    }
                },
                onImport = {
                    if (scriptingFolder == null) showToast = true else showImportDialog = true
                },
                onOpenFolder = {
                    if (scriptingFolder == null) {
                        showToast = true
                    } else {
                        scriptingFolder?.let {
                            context.androidContext.openLink(
                                it.uri.toString(),
                                context.translation["toast_open_link_failed"]
                            )
                        }
                    }
                },
                onManageRepos = { routes.manageScriptRepos.navigate() },
                onDocs = {
                    context.androidContext.openLink(
                        "https://github.com/SnapEnhance/scripting-docs",
                        context.translation["toast_open_link_failed"]
                    )
                },
                folderSelected = scriptingFolder != null
            )
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> InstalledTabContent(
                    scriptingFolder = scriptingFolder
                )
                1 -> CatalogTabContent(
                    scriptingFolder = scriptingFolder
                )
            }
        }
    }
    @Composable
    override fun FriendTrackerManagerRoot.FriendTrackerScreen(nav: NavBackStackEntry) {
        TrackerScreenContent(nav)
    }
}
