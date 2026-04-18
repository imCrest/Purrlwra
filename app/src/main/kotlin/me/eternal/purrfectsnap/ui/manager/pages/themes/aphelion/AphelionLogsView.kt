package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavBackStackEntry
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeLogs
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.ui.util.headerHeightTracker
import cock.crest.purrfectsnap.lite.ui.util.Motion
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeLogs.AphelionLogsScreen(nav: NavBackStackEntry) {
    val coroutineScope = rememberCoroutineScope()
    var controlsHeight by remember { mutableStateOf(100.dp) }
    val composeContext = LocalContext.current
    var logReader by remember { mutableStateOf<cock.crest.purrfectsnap.lite.LogReader?>(null) }
    val visibleLogs = remember { mutableStateListOf<cock.crest.purrfectsnap.lite.LogLine>() }
    var isRefreshing by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    fun refreshLogs() {
        isRefreshing = true
        coroutineScope.launch(Dispatchers.IO) {
            val readerResult = runCatching {
                context.log.newReader { line ->
                    if (shouldHideLog(line)) return@newReader
                    coroutineScope.launch(Dispatchers.Main) {
                        visibleLogs.add(line)
                    }
                }
            }
            readerResult.onFailure {
                context.longToast(translation["read_logs_failed_toast"] ?: "Failed to read logs")
            }
            readerResult.getOrNull()?.let { reader ->
                logReader = reader
                val filteredLogs = (0 until reader.lineCount).mapNotNull { index ->
                    reader.getLogLine(index)?.takeUnless(::shouldHideLog)
                }
                withContext(Dispatchers.Main) {
                    visibleLogs.clear()
                    visibleLogs.addAll(filteredLogs)
                    if (visibleLogs.isNotEmpty()) {
                        logListState.scrollToItem((visibleLogs.size - 1).coerceAtLeast(0))
                    }
                    isRefreshing = false
                }
            }
        }
    }

    @Composable
    fun LogFilterDialog() {
        Dialog(onDismissRequest = { showFilterDialog = false }) {
            PurrfectOverlayTheme {
                PurrfectGlassCard(title = translation["filter_logs_title"] ?: "Filter Log Categories", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeLogs.LogCategory.entries.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        enabledCategories.keys.forEach { enabledCategories[it] = false }
                                        enabledCategories[category] = true
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

    LaunchedEffect(externalRefreshTick.value) {
        if (externalRefreshTick.value > 0) {
            refreshLogs()
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurrfectPalette.backgroundGradient)
    ) {
        var showDropDown by remember { mutableStateOf(false) }
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            if (visibleLogs.isEmpty() && logReader != null) {
                EmptyLogsState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = logListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = controlsHeight,
                        bottom = routes.bottomPadding + 12.dp
                    )
                ) {
                    items(visibleLogs, key = { it.hashCode() }) { line ->
                        LogEntryCard(line = line, composeContext = composeContext)
                    }
                }
            }
        }

        FloatingTopBar(
            title = context.translation["manager.routes.home_logs"] ?: "Logs",
            onBack = { routes.navController.popBackStack() },
            scrollOffset = if (logListState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt() else logListState.firstVisibleItemScrollOffset,
            enableMorph = true,
            modifier = Modifier.headerHeightTracker { controlsHeight = it },
            actions = {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter Logs", tint = PurrfectPalette.glowSecondary)
                }
                IconButton(onClick = { refreshLogs() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showDropDown = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showDropDown,
                        onDismissRequest = { showDropDown = false },
                        offset = DpOffset(0.dp, 8.dp),
                        containerColor = Color(0xFF161821),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                clearLogsAndReload()
                                showDropDown = false
                            },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = PurrfectPalette.glowPrimary) },
                            text = { Text(translation["clear_logs_button"] ?: "Clear", color = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = PurrfectPalette.glowPrimary
                            )
                        )
                        DropdownMenuItem(
                            onClick = {
                                exportLogs()
                                showDropDown = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = PurrfectPalette.glowSecondary) },
                            text = { Text(translation["export_logs_button"] ?: "Export", color = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = PurrfectPalette.glowSecondary
                            )
                        )
                    }
                }
            }
        )
    }
}
