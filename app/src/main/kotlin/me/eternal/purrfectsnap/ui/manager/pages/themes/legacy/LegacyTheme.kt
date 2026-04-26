package me.eternal.purrfectsnap.ui.manager.pages.themes.legacy

import android.os.SystemClock
import android.content.SharedPreferences
import android.content.Intent
import com.google.gson.JsonParser
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
import me.eternal.purrfectsnap.ui.manager.theme.aphelion.AphelionHaptics
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
import me.eternal.purrfectsnap.LogLine
import me.eternal.purrfectsnap.ui.manager.ManagerAssistantEntry
import me.eternal.purrfectsnap.ui.manager.ManagerAssistantTriggerStyle
import me.eternal.purrfectsnap.LogReader
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.common.BuildConfig
import me.eternal.purrfectsnap.common.bridge.InternalFileHandleType
import me.eternal.purrfectsnap.common.bridge.wrapper.LoggerConversationExportTarget
import me.eternal.purrfectsnap.common.bridge.wrapper.LoggedMessage
import me.eternal.purrfectsnap.common.config.ConfigContainer
import me.eternal.purrfectsnap.common.config.PropertyPair
import me.eternal.purrfectsnap.common.data.ContentType
import me.eternal.purrfectsnap.common.data.SocialScope
import me.eternal.purrfectsnap.common.ui.TopBarActionButton
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfectsnap.common.util.ktx.copyToClipboard
import me.eternal.purrfectsnap.common.util.ktx.openLink
import me.eternal.purrfectsnap.common.util.protobuf.ProtoReader
import me.eternal.purrfectsnap.core.features.impl.downloader.decoder.DecodedAttachment
import me.eternal.purrfectsnap.core.features.impl.downloader.decoder.MessageDecoder
import me.eternal.purrfectsnap.core.wrapper.impl.getMessageText
import me.eternal.purrfectsnap.storage.findFriend
import me.eternal.purrfectsnap.storage.getAllScopeNotes
import me.eternal.purrfectsnap.storage.getFriendInfo
import me.eternal.purrfectsnap.storage.getGroupInfo
import me.eternal.purrfectsnap.storage.getQuickTiles
import me.eternal.purrfectsnap.storage.setAllScopeNotes
import me.eternal.purrfectsnap.storage.setQuickTiles
import me.eternal.purrfectsnap.ui.manager.ThemeContract
import me.eternal.purrfectsnap.ui.manager.components.AestheticDialog
import me.eternal.purrfectsnap.ui.manager.components.FloatingTopBar
import me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader
import me.eternal.purrfectsnap.ui.manager.data.Updater
import me.eternal.purrfectsnap.ui.manager.data.Updater.Channel
import me.eternal.purrfectsnap.ui.manager.pages.TasksRootSection
import me.eternal.purrfectsnap.ui.manager.pages.features.FeaturesRootSection
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeAbout
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeLogs
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection.Companion.QUICK_TILES_INITIALIZED_PREF
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection.Companion.cardMargin
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection.Companion.pageBackgroundGradient
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeSettings
import me.eternal.purrfectsnap.ui.manager.pages.home.QuickActionsDialog
import me.eternal.purrfectsnap.ui.manager.pages.scripting.ScriptingRootSection
import me.eternal.purrfectsnap.ui.manager.pages.social.SocialRootSection
import me.eternal.purrfectsnap.ui.manager.pages.social.sortSocialFriends
import me.eternal.purrfectsnap.ui.manager.pages.tracker.FriendTrackerManagerRoot
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.setup.Requirements
import me.eternal.purrfectsnap.ui.util.OnLifecycleEvent
import me.eternal.purrfectsnap.ui.util.PurrfectMarqueeText
import me.eternal.purrfectsnap.ui.util.openFile
import me.eternal.purrfectsnap.ui.util.purrfectSwitchColors
import me.eternal.purrfectsnap.ui.util.pullrefresh.PullRefreshIndicator
import me.eternal.purrfectsnap.ui.util.pullrefresh.pullRefresh
import me.eternal.purrfectsnap.ui.util.pullrefresh.rememberPullRefreshState
import me.eternal.purrfectsnap.ui.util.saveFile
import me.eternal.purrfectsnap.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

object LegacyTheme : ThemeContract {
    @OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
    @Composable override fun HomeRootSection.HomeScreen(nav: NavBackStackEntry) {

        @Composable
        fun LocalTopBarActionChip(
            icon: ImageVector,
            label: String? = null,
            contentDescription: String? = label,
            modifier: Modifier = Modifier,
            onClick: () -> Unit,
        ) {
            Surface(
                modifier = modifier.height(36.dp),
                shape = RoundedCornerShape(40),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(40))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
                    label?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        @Composable
        fun RowScope.LocalHomeActionChips() {
            ManagerAssistantEntry(
                context = context,
                routes = routes,
                style = ManagerAssistantTriggerStyle.DEFAULT,
                modifier = Modifier.weight(1f)
            )
            LocalTopBarActionChip(
                icon = Icons.Filled.BugReport,
                label = context.translation["manager.routes.home_logs"],
                modifier = Modifier.weight(1f)
            ) { routes.homeLogs.navigate() }
            LocalTopBarActionChip(
                icon = Icons.Filled.Info,
                label = translation["manager.routes.home_about"],
                modifier = Modifier.weight(1f)
            ) { routes.about.navigate() }
        }

        @Composable
        fun LocalHeroSection(
            versionName: String,
            latestUpdate: Updater.LatestRelease?,
            downloadState: UpdateDownloader.DownloadState,
            downloadProgress: Float,
            onUpdateAction: () -> Unit,
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
                        HeroBadge(translation.format("hero_version_label", "version" to versionName))
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
                    context.coroutineScope.launch(Dispatchers.IO) {
                        context.database.setQuickTiles(allQuickTileNames)
                        prefs.edit().putBoolean(QUICK_TILES_INITIALIZED_PREF, true).apply()
                    }
                    allQuickTileNames
                }
            }
        }
        val latestUpdate by rememberAsyncMutableState(defaultValue = null) {
            Updater.getLatestRelease(Channel.STABLE)
        }
        val changelogUrl = changelogStableUrl
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocalTopBarActionChip(
                        icon = Icons.Filled.Notifications,
                        label = null,
                        modifier = Modifier.width(56.dp),
                        contentDescription = translation["announcements_button_description"]
                    ) { showAnnouncementsDialog = true; loadAnnouncements() }
                    LocalTopBarActionChip(
                        icon = Icons.Filled.Description,
                        label = null,
                        modifier = Modifier.width(56.dp),
                        contentDescription = translation.getOrNull("changelog_button_description") ?: "Open full changelog"
                    ) { showFullChangelogDialog = true; loadFullChangelog(changelogUrl) }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        val intent = Intent(context.androidContext, me.eternal.purrfectsnap.ui.setup.SetupActivity::class.java)
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
                            RowAction(key = "regen_mappings") { context.checkForRequirements(Requirements.MAPPINGS) }
                            RowAction(key = "change_language") { context.checkForRequirements(Requirements.LANGUAGE) }
                        }

                        GlassCard {
                            RowTitle(title = translation["updates_title"])
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                var autoUpdateCheck by remember { mutableStateOf(context.config.root.global.updateSettings.autoUpdateCheck.getNullable() ?: true) }
                                ShiftedRow {
                                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = translation["auto_update_check"], fontSize = 14.sp)
                                        Switch(checked = autoUpdateCheck, onCheckedChange = { if (context.config.root.global.uiSettings.hapticFeedback.get()) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); autoUpdateCheck = it; context.config.root.global.updateSettings.autoUpdateCheck.set(it); context.config.writeConfig(); scheduleUpdateCheck() }, modifier = Modifier.padding(end = 26.dp), colors = purrfectSwitchColors())
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
                                var showExportOptionsDialog by remember { mutableStateOf(false) }
                                var showConversationExportDialog by remember { mutableStateOf(false) }
                                var showConversationFormatDialog by remember { mutableStateOf(false) }
                                var conversationSearchQuery by remember { mutableStateOf("") }
                                var selectedConversationForExport by remember { mutableStateOf<LoggerConversationExportTarget?>(null) }
                                var pendingConversationExportTarget by remember { mutableStateOf<LoggerConversationExportTarget?>(null) }
                                val loggerHistoryTranslation = remember { context.translation.getCategory("logger_history") }

                                data class ConversationSearchTarget(
                                    val target: LoggerConversationExportTarget,
                                    val friendDisplayName: String?,
                                    val friendUsername: String?,
                                    val chatDisplayName: String?,
                                    val groupDisplayName: String?,
                                    val readableUsernames: List<String>,
                                    val readableIdentifiers: List<String>,
                                    val isDirectChat: Boolean,
                                    val isGroupChat: Boolean,
                                    val sortOrder: Int
                                )

                                data class ConversationExportFormat(
                                    val extension: String,
                                    val mimeType: String,
                                    val label: String
                                )

                                data class ParsedConversationMessage(
                                    val senderId: String,
                                    val senderUsername: String,
                                    val timestamp: Long,
                                    val contentType: ContentType,
                                    val messageText: String?,
                                    val attachments: List<DecodedAttachment>
                                )

                                fun String.isUuidLike(): Boolean {
                                    val value = trim()
                                    if (value.length != 36) return false
                                    if (value[8] != '-' || value[13] != '-' || value[18] != '-' || value[23] != '-') return false
                                    return value.filterIndexed { index, _ ->
                                        index != 8 && index != 13 && index != 18 && index != 23
                                    }.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                                }

                                fun String.isLikelyInternalId(): Boolean {
                                    val value = trim()
                                    if (value.isUuidLike()) return true
                                    if (value.length >= 10 && value.all(Char::isDigit)) return true
                                    if (value.length >= 16 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == '-' }) {
                                        val digitCount = value.count(Char::isDigit)
                                        val alphaCount = value.count { it.lowercaseChar() in 'a'..'f' }
                                        if (digitCount >= 4 && alphaCount >= 4) return true
                                    }
                                    return false
                                }

                                fun String.toReadableIdentityOrNull(): String? {
                                    val value = trim()
                                    if (value.isEmpty()) return null
                                    if (value.isLikelyInternalId()) return null
                                    if (!value.any { it.isLetter() }) return null
                                    return value
                                }

                                fun String.toSearchIdentityOrNull(): String? {
                                    val value = trim()
                                    if (value.isEmpty()) return null
                                    if (value.equals("myai", ignoreCase = true)) return null
                                    return value
                                }

                                val exportTargets by rememberAsyncMutableState(defaultValue = emptyList<LoggerConversationExportTarget>()) {
                                    context.messageLogger.getConversationExportTargets()
                                }
                                val exportSearchTargets by rememberAsyncMutableState(
                                    defaultValue = emptyList<ConversationSearchTarget>(),
                                    keys = arrayOf(exportTargets)
                                ) {
                                    val friendIdentityCache = mutableMapOf<String, Pair<String?, String?>?>()
                                    exportTargets.mapIndexedNotNull { index, target ->
                                        val friend = context.database.findFriend(target.conversationId)
                                        val group = context.database.getGroupInfo(target.conversationId)
                                        val chatDisplayName = target.groupTitle
                                            ?.toReadableIdentityOrNull()
                                            ?.takeIf { !it.equals(target.conversationId, ignoreCase = true) }
                                        val friendDisplayName = friend?.displayName?.toReadableIdentityOrNull()
                                        val friendUsername = friend?.mutableUsername?.toReadableIdentityOrNull()
                                        val searchableUsernames = target.usernames
                                            .mapNotNull { it.toSearchIdentityOrNull() }
                                            .distinct()
                                        val readableUsernames = searchableUsernames
                                            .mapNotNull { it.toReadableIdentityOrNull() }
                                            .distinct()
                                        val hasManyParticipants = target.userIds.distinct().size > 2 || searchableUsernames.size > 2
                                        val fallbackFriendIdentities = if (friend == null && !hasManyParticipants) {
                                            target.userIds.mapNotNull { userId ->
                                                friendIdentityCache.getOrPut(userId) {
                                                    context.database.getFriendInfo(userId)?.let {
                                                        it.displayName?.toReadableIdentityOrNull() to
                                                            it.mutableUsername.toReadableIdentityOrNull()
                                                    }
                                                }?.takeIf { it.first != null || it.second != null }
                                            }
                                        } else {
                                            emptyList()
                                        }
                                        val fallbackFriendDisplayName = fallbackFriendIdentities.firstNotNullOfOrNull { it.first }
                                        val fallbackFriendUsername = fallbackFriendIdentities.firstNotNullOfOrNull { it.second }
                                        val resolvedFriendDisplayName = friendDisplayName ?: fallbackFriendDisplayName
                                        val resolvedFriendUsername = friendUsername ?: fallbackFriendUsername
                                        val groupDisplayName = group?.name?.toReadableIdentityOrNull()
                                            ?: chatDisplayName?.takeIf { hasManyParticipants }
                                        val isGroupChat = groupDisplayName != null || hasManyParticipants
                                        val isDirectChat = !isGroupChat
                                        val readableIdentifiers = buildList {
                                            add(target.conversationId)
                                            addAll(target.userIds)
                                            resolvedFriendDisplayName?.let { add(it) }
                                            resolvedFriendUsername?.let { add(it) }
                                            chatDisplayName?.let { add(it) }
                                            groupDisplayName?.let { add(it) }
                                            addAll(searchableUsernames)
                                            addAll(readableUsernames)
                                        }.distinct()
                                        ConversationSearchTarget(
                                            target = target,
                                            friendDisplayName = resolvedFriendDisplayName,
                                            friendUsername = resolvedFriendUsername,
                                            chatDisplayName = chatDisplayName,
                                            groupDisplayName = groupDisplayName,
                                            readableUsernames = readableUsernames,
                                            readableIdentifiers = readableIdentifiers,
                                            isDirectChat = isDirectChat,
                                            isGroupChat = isGroupChat,
                                            sortOrder = index
                                        )
                                    }.sortedWith(
                                        compareBy<ConversationSearchTarget> {
                                            when {
                                                it.isDirectChat -> 0
                                                it.isGroupChat -> 1
                                                else -> 2
                                            }
                                        }.thenBy { it.sortOrder }
                                    )
                                }
                                val filteredExportTargets = remember(exportSearchTargets, conversationSearchQuery) {
                                    val query = conversationSearchQuery.trim()
                                    if (query.isBlank()) {
                                        exportSearchTargets
                                    } else {
                                        exportSearchTargets.filter { searchTarget ->
                                            searchTarget.readableIdentifiers.any {
                                                it.contains(query, ignoreCase = true)
                                            }
                                        }
                                    }
                                }

                                val exportFormats = remember {
                                    listOf(
                                        ConversationExportFormat("db", "application/octet-stream", ".db"),
                                        ConversationExportFormat("html", "text/html", "HTML"),
                                        ConversationExportFormat("txt", "text/plain", "TXT")
                                    )
                                }

                                fun formatExportTarget(searchTarget: ConversationSearchTarget): String {
                                    searchTarget.friendDisplayName?.let { displayName ->
                                        val username = searchTarget.friendUsername
                                        val formattedName = if (username != null && !username.equals(displayName, ignoreCase = true)) {
                                            "$displayName • @$username"
                                        } else {
                                            displayName
                                        }
                                        return loggerHistoryTranslation.format("list_friend_format", "name" to formattedName)
                                    }

                                    searchTarget.friendUsername?.let { username ->
                                        return loggerHistoryTranslation.format("list_friend_format", "name" to "@$username")
                                    }

                                    searchTarget.chatDisplayName?.takeIf { searchTarget.isDirectChat }?.let {
                                        return loggerHistoryTranslation.format("list_friend_format", "name" to it)
                                    }

                                    if (searchTarget.isDirectChat && searchTarget.readableUsernames.isNotEmpty()) {
                                        val friendName = if (searchTarget.readableUsernames.size == 1) {
                                            searchTarget.readableUsernames.first()
                                        } else {
                                            searchTarget.readableUsernames.joinToString(", ")
                                        }
                                        return loggerHistoryTranslation.format("list_friend_format", "name" to friendName)
                                    }

                                    searchTarget.groupDisplayName?.let {
                                        return loggerHistoryTranslation.format("list_group_format", "name" to it)
                                    }

                                    if (searchTarget.readableUsernames.isNotEmpty()) {
                                        return loggerHistoryTranslation.format(
                                            "list_group_format",
                                            "name" to searchTarget.readableUsernames.joinToString(", ")
                                        )
                                    }

                                    return if (searchTarget.isGroupChat) {
                                        loggerHistoryTranslation.format("list_group_format", "name" to searchTarget.target.conversationId)
                                    } else {
                                        loggerHistoryTranslation.format("list_friend_format", "name" to searchTarget.target.conversationId)
                                    }
                                }

                                fun showExportError(throwable: Throwable) {
                                    context.log.error("Failed to export message logger", throwable)
                                    context.shortToast(
                                        translation.format(
                                            "message_logger_export_failed_toast",
                                            "message" to (throwable.message ?: "Unknown error")
                                        )
                                    )
                                }

                                fun showImportError(throwable: Throwable) {
                                    context.log.error("Failed to import message logger", throwable)
                                    context.shortToast(
                                        translation.format(
                                            "import_failed_toast",
                                            "message" to (throwable.message ?: "Unknown error")
                                        )
                                    )
                                }

                                fun parseConversationMessage(message: LoggedMessage): ParsedConversationMessage {
                                    val messageObject = runCatching {
                                        JsonParser.parseString(String(message.messageData, Charsets.UTF_8)).asJsonObject
                                    }.getOrNull()
                                    val messageContent = messageObject?.getAsJsonObject("mMessageContent")
                                    val contentBytes = runCatching {
                                        messageContent?.getAsJsonArray("mContent")?.map { it.asByte }?.toByteArray()
                                    }.getOrNull()
                                    val contentType = messageContent?.getAsJsonPrimitive("mContentType")?.asString?.let {
                                        runCatching { ContentType.valueOf(it) }.getOrNull()
                                    } ?: contentBytes?.let { ContentType.fromMessageContainer(ProtoReader(it)) } ?: ContentType.UNKNOWN
                                    val messageText = contentBytes?.getMessageText(contentType)
                                    val attachments = runCatching {
                                        messageContent?.let { MessageDecoder.decode(it) } ?: emptyList()
                                    }.getOrDefault(emptyList())

                                    return ParsedConversationMessage(
                                        senderId = message.userId,
                                        senderUsername = message.username,
                                        timestamp = message.sendTimestamp,
                                        contentType = contentType,
                                        messageText = messageText,
                                        attachments = attachments
                                    )
                                }

                                fun htmlEscape(input: String): String {
                                    val escaped = StringBuilder(input.length)
                                    input.forEach { char ->
                                        when (char) {
                                            '&' -> escaped.append("&amp;")
                                            '<' -> escaped.append("&lt;")
                                            '>' -> escaped.append("&gt;")
                                            '"' -> escaped.append("&quot;")
                                            '\'' -> escaped.append("&#39;")
                                            else -> escaped.append(char)
                                        }
                                    }
                                    return escaped.toString()
                                }

                                fun writeConversationExportFile(
                                    target: LoggerConversationExportTarget,
                                    format: ConversationExportFormat,
                                    outputFile: File
                                ): Int {
                                    val conversationId = target.conversationId.trim()
                                    if (conversationId.isEmpty()) {
                                        throw IllegalArgumentException("Conversation ID cannot be empty")
                                    }

                                    val searchTarget = exportSearchTargets.firstOrNull { it.target.conversationId == conversationId }
                                    val conversationTitle = searchTarget?.let { formatExportTarget(it) }
                                        ?: (translation["message_logger_export_individual_chat"] ?: "Exported Chat")
                                    val dateFormatter = DateFormat.getDateTimeInstance()
                                    val senderCache = mutableMapOf<String, String>()

                                    fun formatSenderLabel(senderId: String, senderUsername: String): String {
                                        val friendInfo = context.database.getFriendInfo(senderId)
                                        val senderDisplayName = friendInfo?.displayName?.toReadableIdentityOrNull()
                                        val senderReadableUsername = friendInfo?.mutableUsername?.toReadableIdentityOrNull()
                                            ?: senderUsername.toReadableIdentityOrNull()
                                        return when {
                                            senderDisplayName != null &&
                                                senderReadableUsername != null &&
                                                !senderDisplayName.equals(senderReadableUsername, ignoreCase = true) ->
                                                "$senderDisplayName (@$senderReadableUsername)"
                                            senderDisplayName != null -> senderDisplayName
                                            senderReadableUsername != null -> "@$senderReadableUsername"
                                            else -> translation["sender_unknown"] ?: "Unknown sender"
                                        }
                                    }

                                    outputFile.parentFile?.mkdirs()
                                    if (outputFile.exists() && !outputFile.delete()) {
                                        throw IllegalStateException("Failed to prepare export file")
                                    }

                                    return outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                                        val isHtmlFormat = format.extension == "html"
                                        if (isHtmlFormat) {
                                            writer.appendLine("<!DOCTYPE html>")
                                            writer.appendLine("<html><head><meta charset=\"UTF-8\" />")
                                            writer.appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />")
                                            writer.appendLine("<title>${htmlEscape(conversationTitle)}</title>")
                                            writer.appendLine(
                                                "<style>body{font-family:Arial,sans-serif;background:#121212;color:#f3f3f3;padding:16px;}h2{margin-top:0;}" +
                                                    ".meta{color:#a5a5a5;font-size:12px;margin-bottom:4px;}" +
                                                    ".message{border:1px solid #2d2d2d;border-radius:10px;padding:10px;margin:10px 0;background:#1b1b1b;}" +
                                                    ".content{white-space:pre-wrap;word-break:break-word;}" +
                                                    ".attachments{margin:8px 0 0 18px;padding:0;}a{color:#8db7ff;}</style>"
                                            )
                                            writer.appendLine("</head><body>")
                                            writer.appendLine("<h2>${htmlEscape(conversationTitle)}</h2>")
                                            writer.appendLine("<p>${htmlEscape(translation.format("message_logger_conversation_id", "id" to conversationId))}</p>")
                                        } else {
                                            writer.appendLine(conversationTitle)
                                            writer.appendLine("")
                                        }

                                        val exportedMessageCount = context.messageLogger.forEachConversationMessage(
                                            conversationId = conversationId,
                                            userIds = target.userIds,
                                            orderAscending = true
                                        ) { loggedMessage ->
                                            val parsed = parseConversationMessage(loggedMessage)
                                            val senderInfo = senderCache.getOrPut(parsed.senderId) {
                                                formatSenderLabel(parsed.senderId, parsed.senderUsername)
                                            }
                                            val senderLabel = senderInfo
                                            val content = parsed.messageText?.takeIf { it.isNotBlank() } ?: if (parsed.contentType == ContentType.CHAT) {
                                                loggerHistoryTranslation["empty_message"]
                                            } else {
                                                parsed.contentType.name.lowercase()
                                            }

                                            if (isHtmlFormat) {
                                                writer.appendLine("<div class=\"message\">")
                                                writer.appendLine(
                                                    "<div class=\"meta\">${
                                                        htmlEscape(
                                                            "${dateFormatter.format(Date(parsed.timestamp))} • $senderLabel • ${
                                                                parsed.contentType.name.lowercase()
                                                            }"
                                                        )
                                                    }</div>"
                                                )
                                                writer.appendLine("<div class=\"content\">${htmlEscape(content).replace("\n", "<br/>")}</div>")
                                                if (parsed.attachments.isNotEmpty()) {
                                                    writer.appendLine("<ul class=\"attachments\">")
                                                    parsed.attachments.forEachIndexed { index, attachment ->
                                                        val attachmentLabel = "${loggerHistoryTranslation.format("chat_attachment", "index" to (index + 1).toString())} [${attachment.type.name.lowercase()}]"
                                                        val directUrl = attachment.directUrl?.takeIf { it.isNotBlank() }
                                                        if (directUrl != null) {
                                                            writer.appendLine(
                                                                "<li><a href=\"${htmlEscape(directUrl)}\" target=\"_blank\" rel=\"noopener noreferrer\">${
                                                                    htmlEscape(attachmentLabel)
                                                                }</a></li>"
                                                            )
                                                        } else {
                                                            val placeholder = attachment.boltKey?.takeIf { it.isNotBlank() }
                                                                ?: attachment.mediaUniqueId?.takeIf { it.isNotBlank() }
                                                                ?: (translation["message_logger_missing_attachment_placeholder"] ?: "Attachment unavailable")
                                                            writer.appendLine("<li>${htmlEscape("$attachmentLabel: $placeholder")}</li>")
                                                        }
                                                    }
                                                    writer.appendLine("</ul>")
                                                }
                                                writer.appendLine("</div>")
                                            } else {
                                                writer.appendLine("[${dateFormatter.format(Date(parsed.timestamp))}] $senderLabel: $content")
                                                parsed.attachments.forEachIndexed { index, attachment ->
                                                    val attachmentLabel = "${loggerHistoryTranslation.format("chat_attachment", "index" to (index + 1).toString())} [${attachment.type.name.lowercase()}]"
                                                    val attachmentValue = attachment.directUrl?.takeIf { it.isNotBlank() }
                                                        ?: (translation["message_logger_missing_attachment_placeholder"] ?: "Attachment unavailable")
                                                    writer.appendLine("  - $attachmentLabel: $attachmentValue")
                                                }
                                                writer.appendLine("")
                                            }
                                        }

                                        if (exportedMessageCount == 0) {
                                            if (isHtmlFormat) {
                                                writer.appendLine("<p>${htmlEscape(translation["message_logger_no_messages_export_text"] ?: "No messages found in this chat.")}</p>")
                                            } else {
                                                writer.appendLine(translation["message_logger_no_messages_export_text"] ?: "No messages found in this chat.")
                                            }
                                        }

                                        if (isHtmlFormat) {
                                            writer.appendLine("</body></html>")
                                        }

                                        exportedMessageCount
                                    }
                                }

                                fun exportFullDatabase() {
                                    runCatching {
                                        activityLauncherHelper.saveFile("message_logger.db", "application/octet-stream") { uri ->
                                            context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { out ->
                                                context.messageLogger.databaseFile.inputStream().use { input -> input.copyTo(out) }
                                            } ?: throw IllegalStateException("Failed to open output stream")
                                        }
                                    }.onFailure { showExportError(it) }
                                }

                                fun exportConversation(target: LoggerConversationExportTarget, format: ConversationExportFormat) {
                                    val conversationId = target.conversationId.trim()
                                    if (conversationId.isEmpty()) {
                                        context.shortToast(translation["message_logger_missing_conversation_toast"])
                                        return
                                    }

                                    val fileNameSuffix = conversationId
                                        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                                        .take(24)
                                        .ifBlank { "chat" }

                                    runCatching {
                                        activityLauncherHelper.saveFile("message_logger_${fileNameSuffix}.${format.extension}", format.mimeType) { uri ->
                                            scope.launch {
                                                runCatching {
                                                    val exportedMessageCount = withContext(Dispatchers.IO) {
                                                        val tempFile = File(
                                                            context.androidContext.cacheDir,
                                                            "message_logger_export_${System.currentTimeMillis()}.${format.extension}"
                                                        )
                                                        try {
                                                            val messageCount = if (format.extension == "db") {
                                                                context.messageLogger.exportConversationDatabase(
                                                                    outputFile = tempFile,
                                                                    conversationId = conversationId,
                                                                    userIds = target.userIds
                                                                ).messageCount
                                                            } else {
                                                                writeConversationExportFile(target, format, tempFile)
                                                            }
                                                            context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { output ->
                                                                tempFile.inputStream().use { input -> input.copyTo(output) }
                                                            } ?: throw IllegalStateException("Failed to open output stream")
                                                            messageCount
                                                        } finally {
                                                            tempFile.delete()
                                                        }
                                                    }

                                                    if (exportedMessageCount == 0) {
                                                        context.shortToast(translation["message_logger_empty_chat_toast"])
                                                    } else {
                                                        context.shortToast(translation["success_toast"])
                                                    }
                                                }.onFailure { showExportError(it) }
                                            }
                                        }
                                    }.onFailure { showExportError(it) }
                                }

                                fun dismissConversationExportDialog() {
                                    showConversationExportDialog = false
                                    selectedConversationForExport = null
                                    conversationSearchQuery = ""
                                }

                                fun dismissConversationFormatDialog() {
                                    showConversationFormatDialog = false
                                    pendingConversationExportTarget = null
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val summary = translation.format("message_logger_summary", "messageCount" to storedMessagesCount.toString(), "storyCount" to storedStoriesCount.toString()).replace("\n", " | ")
                                    Text(summary, maxLines = 2, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp) ) {
                                        Button(onClick = { showExportOptionsDialog = true }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["export_button"]) }
                                        Button(onClick = { runCatching { activityLauncherHelper.openFile("application/octet-stream") { uri -> val tempFile = File(context.androidContext.cacheDir, "view_logger.db"); context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { it.copyTo(tempFile.outputStream()) }; routes.viewLoggerHistory.navigate { put("uri", URLEncoder.encode(tempFile.toUri().toString(), "UTF-8")) } } } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["view_button"]) }
                                        Button(onClick = { runCatching { context.messageLogger.purgeAll(); storedMessagesCount = 0; storedStoriesCount = 0 }.onSuccess { context.shortToast(translation["success_toast"]) } }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["clear_button"]) }
                                        Button(onClick = { showImportDialog = true }, colors = sharedButtonColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) { Text(text = translation["import_button"]) }
                                    }
                                }
                                OutlinedButton(modifier = Modifier.fillMaxWidth().padding(5.dp), onClick = { routes.loggerHistory.navigate() }, colors = sharedOutlinedColors, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) { Text(translation["view_logger_history_button"]) }
                                if (showImportDialog) {
                                    AestheticDialog(
                                        onDismissRequest = { showImportDialog = false },
                                        title = translation["message_logger_import_title"],
                                        text = translation["message_logger_import_text"],
                                        icon = Icons.Filled.Info,
                                        confirmButtonText = importLabel,
                                        dismissButtonText = context.translation["button.cancel"],
                                        onConfirm = {
                                            showImportDialog = false
                                            runCatching {
                                                activityLauncherHelper.openFile("application/octet-stream") { uri ->
                                                    scope.launch {
                                                        runCatching {
                                                            val importResult = withContext(Dispatchers.IO) {
                                                                context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { input ->
                                                                    context.messageLogger.importDatabase(input)
                                                                } ?: throw IllegalStateException("Failed to open selected backup file")
                                                            }
                                                            storedMessagesCount = importResult.messageCount
                                                            storedStoriesCount = importResult.storyCount
                                                            context.shortToast(translation["success_toast"])
                                                        }.onFailure { showImportError(it) }
                                                    }
                                                }
                                            }.onFailure { showImportError(it) }
                                        },
                                        onDismiss = { showImportDialog = false },
                                        showCloseButton = false
                                    )
                                }
                                if (showExportOptionsDialog) {
                                    AestheticDialog(
                                        onDismissRequest = { showExportOptionsDialog = false },
                                        title = translation["message_logger_export_title"] ?: "Export Message Logger",
                                        text = translation["message_logger_export_text"] ?: "Choose what to export.",
                                        icon = Icons.Filled.SaveAlt,
                                        confirmButtonText = context.translation["button.cancel"],
                                        onConfirm = { showExportOptionsDialog = false },
                                        showCloseButton = false,
                                        customContent = {
                                            Button(
                                                onClick = {
                                                    showExportOptionsDialog = false
                                                    pendingConversationExportTarget = null
                                                    showConversationExportDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = sharedButtonColors,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                            ) {
                                                Text(translation["message_logger_export_individual_chat"] ?: "Export Individual Chat")
                                            }
                                            Button(
                                                onClick = {
                                                    showExportOptionsDialog = false
                                                    exportFullDatabase()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = sharedButtonColors,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                            ) {
                                                Text(translation["message_logger_export_full_database"] ?: "Export Full Database")
                                            }
                                        }
                                    )
                                }
                                if (showConversationExportDialog) {
                                    AestheticDialog(
                                        onDismissRequest = { dismissConversationExportDialog() },
                                        title = translation["message_logger_select_chat_title"] ?: "Export Individual Chat",
                                        text = translation["message_logger_select_chat_text"] ?: "Search by username, display name, or chat name.",
                                        icon = Icons.Filled.Search,
                                        confirmButtonText = translation["message_logger_continue_button"] ?: "Continue",
                                        dismissButtonText = context.translation["button.cancel"],
                                        onConfirm = {
                                            val selectedTarget = selectedConversationForExport ?: return@AestheticDialog
                                            pendingConversationExportTarget = selectedTarget
                                            dismissConversationExportDialog()
                                            showConversationFormatDialog = true
                                        },
                                        onDismiss = { dismissConversationExportDialog() },
                                        showCloseButton = false,
                                        confirmEnabled = selectedConversationForExport != null,
                                        customContent = {
                                            OutlinedTextField(
                                                value = conversationSearchQuery,
                                                onValueChange = { conversationSearchQuery = it },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = {
                                                    Text(context.translation["manager.dialogs.add_friend.search_hint"] ?: "Search")
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Search, contentDescription = null)
                                                },
                                                trailingIcon = if (conversationSearchQuery.isNotBlank()) {
                                                    {
                                                        IconButton(onClick = { conversationSearchQuery = "" }) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Close,
                                                                contentDescription = null
                                                            )
                                                        }
                                                    }
                                                } else null,
                                                colors = TextFieldDefaults.colors(
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

                                            if (filteredExportTargets.isEmpty()) {
                                                Text(
                                                    text = translation["message_logger_no_chats_found"] ?: "No chats found",
                                                    color = PurrfectPalette.textSecondary,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                LazyColumn(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 280.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(filteredExportTargets.size) { index ->
                                                        val searchTarget = filteredExportTargets[index]
                                                        val target = searchTarget.target
                                                        val isSelected = selectedConversationForExport?.conversationId == searchTarget.target.conversationId
                                                        OutlinedButton(
                                                            onClick = { selectedConversationForExport = searchTarget.target },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isSelected) {
                                                                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f)
                                                                } else {
                                                                    Color.White.copy(alpha = 0.18f)
                                                                }
                                                            )
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                                            ) {
                                                                Text(
                                                                    text = formatExportTarget(searchTarget),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                val secondaryLabel = when {
                                                                    searchTarget.friendDisplayName != null && searchTarget.friendUsername != null -> "@${searchTarget.friendUsername}"
                                                                    searchTarget.friendDisplayName != null -> searchTarget.friendDisplayName
                                                                    searchTarget.chatDisplayName != null -> searchTarget.chatDisplayName
                                                                    searchTarget.groupDisplayName != null -> searchTarget.groupDisplayName
                                                                    searchTarget.readableUsernames.isNotEmpty() -> searchTarget.readableUsernames.joinToString(", ")
                                                                    else -> null
                                                                }
                                                                if (secondaryLabel != null) {
                                                                    Text(
                                                                        text = secondaryLabel,
                                                                        color = PurrfectPalette.textSecondary,
                                                                        fontSize = 12.sp,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                                Text(
                                                                    text = translation.format("message_logger_message_count", "count" to target.messageCount.toString()),
                                                                    color = PurrfectPalette.textSecondary,
                                                                    fontSize = 12.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                if (showConversationFormatDialog && pendingConversationExportTarget != null) {
                                    AestheticDialog(
                                        onDismissRequest = { dismissConversationFormatDialog() },
                                        title = translation["message_logger_select_export_format_title"] ?: "Select Export Format",
                                        text = translation["message_logger_select_export_format_text"] ?: "Choose how to export the selected chat.",
                                        icon = Icons.Filled.Description,
                                        confirmButtonText = context.translation["button.cancel"],
                                        onConfirm = { dismissConversationFormatDialog() },
                                        showCloseButton = false,
                                        customContent = {
                                            exportFormats.forEach { format ->
                                                val formatLabel = when (format.extension) {
                                                    "db" -> translation["message_logger_export_format_db"] ?: ".db"
                                                    "html" -> translation["message_logger_export_format_html"] ?: "HTML"
                                                    else -> translation["message_logger_export_format_txt"] ?: "TXT"
                                                }
                                                Button(
                                                    onClick = {
                                                        val exportTarget = pendingConversationExportTarget ?: return@Button
                                                        dismissConversationFormatDialog()
                                                        exportConversation(exportTarget, format)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = sharedButtonColors,
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                                ) {
                                                    Text(formatLabel)
                                                }
                                            }
                                        }
                                    )
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
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                DeveloperCard(name = "Eternal", subtitle = "", imageRes = R.drawable.pfp_external, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                                DeveloperCard(name = "Kaladin", subtitle = "", imageRes = R.drawable.pfp_kaladin, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                DeveloperCard(name = "schrodingerspet", subtitle = "", imageRes = R.drawable.pfp_schrodingerspet, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                                DeveloperCard(name = "RSR", subtitle = "", imageRes = R.drawable.pfp_rsr, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                            }
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
        subtitle: String? = null,
        modifier: Modifier = Modifier
    ) {
        val tapSource = remember { MutableInteractionSource() }
        Surface(
            modifier = modifier
                .height(150.dp)
                .scaleOnPress(tapSource),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = avenirNext,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = Color(0xFFD9D3FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                me.eternal.purrfectsnap.core.ui.PurrfectOverlayTheme {
                    me.eternal.purrfectsnap.core.ui.PurrfectGlassCard(
                        title = translation["filter_logs_title"] ?: "Log Filters",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HomeLogs.LogCategory.entries.forEach { category ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    enabledCategories[category] = !(enabledCategories[category] ?: true)
                                                    refreshLogs()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = enabledCategories[category] == true,
                                                onCheckedChange = { checked ->
                                                    enabledCategories[category] = checked
                                                    refreshLogs()
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = PurrfectPalette.glowPrimary,
                                                    uncheckedColor = Color.White.copy(alpha = 0.3f),
                                                    checkmarkColor = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = translation[category.translationKey] ?: category.name,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { showFilterDialog = false },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurrfectPalette.glowPrimary)
                            ) {
                                Text(translation["filter_logs_done_button"] ?: "Apply Filters", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
        // Controller handles data loading and synchronization
        SocialDataController()

        val titles = remember {
            listOf(translation["friends_tab"], translation["groups_tab"])
        }
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        var searchActive by rememberSaveable { mutableStateOf(false) }

        val normalizedQuery = remember(searchQuery) { searchQuery.trim() }
        
        // Filter logic based on the parent's synchronized data lists
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
