@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cock.crest.purrfectsnap.lite.ui.manager.pages.home

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.LogLine
import cock.crest.purrfectsnap.lite.LogReader
import cock.crest.purrfectsnap.lite.common.logger.LogChannel
import cock.crest.purrfectsnap.lite.common.logger.LogLevel
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.PullRefreshIndicator
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.pullRefresh
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.rememberPullRefreshState
import cock.crest.purrfectsnap.lite.ui.util.saveFile
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard

class HomeLogs : Routes.Route() {
    internal val logListState = LazyListState()
    internal lateinit var activityLauncherHelper: ActivityLauncherHelper
    internal val externalRefreshTick = mutableIntStateOf(0)
    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    internal fun clearLogsAndReload() {
        context.coroutineScope.launch {
            context.log.clearLogs()
            withContext(Dispatchers.Main) {
                navigateReload()
            }
        }
    }

    internal fun exportLogs() {
        activityLauncherHelper.saveFile("purrfectsnap-logs-${System.currentTimeMillis()}.zip", "application/zip") { uri ->
            context.coroutineScope.launch {
                context.shortToast(translation["saving_logs_toast"])
                context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                    runCatching {
                        context.log.exportLogsToZip(it)
                        context.longToast(translation["saved_logs_success_toast"])
                    }.onFailure { error ->
                        context.longToast(translation["saved_logs_failure_toast"])
                        context.log.error("Failed to save logs to $uri!", error)
                    }
                }
            }
        }
    }

    override val topBarActions: @Composable (RowScope.() -> Unit) = {}
    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) { delay(300); value = context.config.root.global.uiSettings.managerTheme.get() }
        }
        key(themeId) { with(ManagerTheme.fromId(themeId).theme) { this@HomeLogs.LogsScreen(nav) } }
    }

    override val floatingActionButton: @Composable () -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val firstVisibleItem by remember { derivedStateOf { logListState.firstVisibleItemIndex } }
            val layoutInfo by remember { derivedStateOf { logListState.layoutInfo } }
            val floatingButtonColors = IconButtonDefaults.filledIconButtonColors(
                containerColor = PurrfectPalette.cardOverlayColor,
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(6.dp)
                ) {
                    FilledIconButton(
                        onClick = {
                            coroutineScope.launch {
                                logListState.scrollToItem(0)
                            }
                        },
                        enabled = firstVisibleItem != 0,
                        colors = floatingButtonColors
                    ) {
                        Icon(Icons.Filled.KeyboardDoubleArrowUp, contentDescription = null)
                    }
                    FilledIconButton(
                        onClick = {
                            coroutineScope.launch {
                                logListState.scrollToItem((logListState.layoutInfo.totalItemsCount - 1).takeIf { it >= 0 } ?: return@launch)
                            }
                        },
                        enabled = layoutInfo.visibleItemsInfo.lastOrNull()?.index != layoutInfo.totalItemsCount - 1,
                        colors = floatingButtonColors
                    ) {
                        Icon(Icons.Filled.KeyboardDoubleArrowDown, contentDescription = null)
                    }
                }
            }
        }
    }

    @Composable
    internal fun LogsFloatingBar(
        isRefreshing: Boolean,
        onRefresh: () -> Unit,
        onFilter: () -> Unit,
        onExport: () -> Unit,
        onClear: () -> Unit
    ) {
        var showMenu by remember { mutableStateOf(false) }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { routes.navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = translation["common.back"],
                            tint = Color.White
                        )
                    }
                    Text(
                        text = translation["manager.routes.home_logs"] ?: "Logs",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onFilter) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter Logs",
                            tint = PurrfectPalette.glowSecondary
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = translation["refresh_button_description"],
                            tint = Color.White
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(0.dp, 8.dp),
                            containerColor = Color(0xFF161821),
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = PurrfectPalette.glowPrimary
                                    )
                                },
                                text = { Text(text = translation["export_logs_button"] ?: "Export Logs", color = Color.White) },
                                onClick = {
                                    onExport()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9CAB)
                                    )
                                },
                                text = { Text(text = translation["clear_logs_button"] ?: "Clear Logs", color = Color.White) },
                                onClick = {
                                    onClear()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun EmptyLogsState() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.1f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = translation["no_logs_hint"],
                color = PurrfectPalette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = translation["refresh_hint"],
                color = PurrfectPalette.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    @Composable
    internal fun LogEntryCard(line: LogLine, composeContext: android.content.Context) {
        val normalizedMessage = remember(line.message) {
            val cleaned = line.message.replace("\r", "")
            val fragments = cleaned.lines()
            if (fragments.size > 3 && fragments.count { it.length <= 2 } > fragments.size / 2) {
                fragments.joinToString("") { it.trim() }
            } else {
                cleaned
            }
        }
        val levelColor = logLevelColor(line.logLevel)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .pointerInput(line.hashCode()) {
                    detectTapGestures(
                        onLongPress = {
                            composeContext.copyToClipboard(line.message)
                        }
                    )
                },
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.05f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, levelColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(levelColor, levelColor.copy(alpha = 0.35f))
                            ),
                            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                        )
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = levelColor.copy(alpha = 0.18f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, levelColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = logLevelIcon(line.logLevel),
                                    contentDescription = null,
                                    tint = levelColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = logLevelLabel(line.logLevel),
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = LogChannel.fromChannel(line.tag)?.shortName ?: line.tag,
                                fontWeight = FontWeight.SemiBold,
                                color = PurrfectPalette.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = line.dateTime,
                                color = PurrfectPalette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = normalizedMessage,
                        color = Color.White,
                        lineHeight = 16.sp,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    internal fun logLevelColor(logLevel: LogLevel): Color = when (logLevel) {
        LogLevel.DEBUG -> PurrfectPalette.glowSecondary
        LogLevel.INFO, LogLevel.VERBOSE -> Color(0xFFA3F0C2)
        LogLevel.WARN -> Color(0xFFFFD782)
        LogLevel.ERROR, LogLevel.ASSERT -> Color(0xFFFF9CAB)
    }

    internal fun logLevelLabel(logLevel: LogLevel): String = when (logLevel) {
        LogLevel.DEBUG -> "Debug"
        LogLevel.INFO -> "Info"
        LogLevel.VERBOSE -> "Verbose"
        LogLevel.WARN -> "Warning"
        LogLevel.ERROR -> "Error"
        LogLevel.ASSERT -> "Assert"
    }

    internal fun logLevelIcon(logLevel: LogLevel) = when (logLevel) {
        LogLevel.DEBUG -> Icons.Outlined.BugReport
        LogLevel.ERROR, LogLevel.ASSERT -> Icons.Outlined.Report
        LogLevel.INFO, LogLevel.VERBOSE -> Icons.Outlined.Info
        LogLevel.WARN -> Icons.Outlined.Warning
    }

    enum class LogCategory(val translationKey: String, val tags: List<String>) {
        CORE("log_category_core", listOf("core", "hook", "module", "mappings")),
        AUTO_OPEN("log_category_auto_open", listOf("autoopenengine", "autoopen")),
        MEDIA("log_category_media", listOf("downloader", "ffmpeg", "media", "video")),
        BRIDGE("log_category_bridge", listOf("messagingbridge", "bridge", "ipc")),
        SYSTEM("log_category_system", listOf("systemguard", "thermal", "battery", "wakelock")),
        TRACKER("log_category_tracker", listOf("tracker", "friendtracker"))
    }

    val enabledCategories = mutableStateMapOf<LogCategory, Boolean>().apply {
        LogCategory.entries.forEach { put(it, true) }
    }

    internal fun getCategoryForLog(line: LogLine): LogCategory? {
        val tag = line.tag.lowercase()
        val message = line.message.lowercase()
        return LogCategory.entries.find { category ->
            category.tags.any { tag.contains(it) || message.contains("[$it]") }
        }
    }

    internal fun shouldHideLog(line: LogLine): Boolean {
        val category = getCategoryForLog(line)
        if (category != null && enabledCategories[category] == false) return true

        val message = line.message.lowercase()
        val tag = line.tag.lowercase()
        return message.startsWith("blocked ep") ||
                message.startsWith("allowed ep") ||
                message.startsWith("blocked call") ||
                message.contains("detection keyword matched") ||
                message.startsWith("enc:v1:") ||
                message.contains("endpointsblocker") ||
                tag.contains("endpointsblocker") ||
                message.contains("securityfeatures") ||
                tag.contains("securityfeatures")
    }
}
