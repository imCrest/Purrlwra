package cock.crest.purrfectsnap.lite.ui.manager.pages.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncUpdateDispatcher
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.storage.*
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.coil.BitmojiImage
import cock.crest.purrfectsnap.lite.ui.util.openFile
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.pagerTabIndicatorOffset


@OptIn(ExperimentalFoundationApi::class)
class FriendTrackerManagerRoot : Routes.Route() {
    enum class FilterType {
        CONVERSATION, USERNAME, EVENT
    }

    override val translation by lazy { context.translation.getCategory("manager.friend_tracker") }
    internal val titles by lazy {
        listOf(
            translation["rules_tab"],
            translation["logs_tab"]
        )
    }
    internal var currentPage by mutableIntStateOf(0)
    internal lateinit var logDeleteAction : () -> Unit
    internal lateinit var exportAction : () -> Unit

    @Composable
    internal fun TrackerIconButton(
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(14.dp)
        val backgroundBrush = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.24f)
                )
            )
        }
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.White.copy(alpha = 0.06f),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = modifier.size(46.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(backgroundBrush, shape)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = contentDescription, tint = Color.White)
            }
        }
    }

    @Composable
    internal fun TrackerActionButton(
        label: String,
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val shape = RoundedCornerShape(22.dp)
        val backgroundBrush = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.3f)
                )
            )
        }
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .background(backgroundBrush, shape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, contentDescription = label, tint = Color.White)
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    internal fun TrackerPillButton(
        label: String,
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val shape = RoundedCornerShape(18.dp)
        val backgroundBrush = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.26f)
                )
            )
        }
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .background(backgroundBrush, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = label, tint = Color.White)
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    override val topBarActions: @Composable RowScope.() -> Unit = {
        var showExportDialog by remember { mutableStateOf(false) }
        var showSingleExportDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var showInvalidImportTypeDialog by remember { mutableStateOf(false) }

        if (showExportDialog) {
            AestheticDialog(
                onDismissRequest = { showExportDialog = false },
                title = translation["export_dialog_title"],
                text = translation["export_logs_dialog_confirm_text"],
                icon = Icons.Default.SaveAlt,
                confirmButtonText = translation["export_button"],
                onConfirm = {
                    showExportDialog = false
                    routes.friendTrackerConfigExport.navigate()
                },
                dismissButtonText = translation["button.cancel"],
                onDismiss = { showExportDialog = false },
                opaque = true,
                showCloseButton = false
            )
        }

        if (showSingleExportDialog) {
            val rules = rememberAsyncMutableStateList(defaultValue = emptyList()) {
                context.database.getTrackerRulesDesc()
            }
            SelectRuleDialog(
                onDismissRequest = { showSingleExportDialog = false },
                rules = rules,
                onRuleSelected = { rule ->
                    showSingleExportDialog = false
                    routes.friendTrackerConfigExport.navigate {
                        this["rule_id"] = rule.id.toString()
                    }
                },
                translation = translation
            )
        }

        fun handleImport(type: cock.crest.purrfectsnap.lite.common.data.ExportType) {
            routes.activityLauncher.openFile("application/json") { uri ->
                runCatching {
                    val content = context.androidContext.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@runCatching
                    val exportedData = context.gson.fromJson(content, cock.crest.purrfectsnap.lite.common.data.ExportedTrackerData::class.java)
                    if (exportedData.type != type) {
                        showInvalidImportTypeDialog = true
                        return@runCatching
                    }
                    routes.friendTrackerConfigJsonForImport = content
                    routes.friendTrackerConfigImport.navigate()
                }.onFailure {
                    context.longToast(
                        translation.format("read_file_failed_toast", "message" to (it.message ?: ""))
                    )
                }
            }
        }

        if (showInvalidImportTypeDialog) {
            AlertDialog(
                onDismissRequest = { showInvalidImportTypeDialog = false },
                title = { Text(translation["invalid_import_type_dialog_title"]) },
                text = { Text(translation["invalid_import_type_dialog_text"]) },
                confirmButton = {
                    Button(onClick = { showInvalidImportTypeDialog = false }) {
                        Text(translation["button.ok"])
                    }
                }
            )
        }

        if (showImportDialog) {
            AestheticDialog(
                onDismissRequest = { showImportDialog = false },
                title = translation["import_dialog_title"],
                text = translation["import_dialog_subtitle"] ?: translation["import_dialog_title"],
                icon = Icons.Default.FolderOpen,
                confirmButtonText = translation["bulk_import_button"],
                onConfirm = {
                    showImportDialog = false
                    handleImport(cock.crest.purrfectsnap.lite.common.data.ExportType.BULK)
                },
                dismissButtonText = translation["individual_import_button"],
                onDismiss = {
                    showImportDialog = false
                    handleImport(cock.crest.purrfectsnap.lite.common.data.ExportType.SINGLE)
                },
                opaque = true,
                showCloseButton = false
            )
        }

        if (currentPage == 0) {
            TrackerIconButton(
                icon = Icons.Default.FolderOpen,
                contentDescription = translation["import_button_description"],
                onClick = { showImportDialog = true }
            )
            Spacer(Modifier.width(8.dp))
            TrackerIconButton(
                icon = Icons.Default.SaveAlt,
                contentDescription = translation["export_button_description"],
                onClick = { showExportDialog = true }
            )
        }
    }

    internal lateinit var activityLauncherHelper: ActivityLauncherHelper

    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    override val floatingActionButton: @Composable () -> Unit = {
        when (currentPage) {
            1 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    TrackerActionButton(
                        label = translation["export_button"],
                        icon = Icons.Default.SaveAlt,
                        onClick = { context.coroutineScope.launch { exportAction() } }
                    )
                    TrackerActionButton(
                        label = translation["delete_button"],
                        icon = Icons.Default.DeleteOutline,
                        onClick = { context.coroutineScope.launch { logDeleteAction() } }
                    )
                }
            }
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    TrackerActionButton(
                        label = translation["catalog_button"],
                        icon = Icons.Default.Store,
                        onClick = { routes.friendTrackerCatalog.navigate() }
                    )
                    TrackerActionButton(
                        label = translation["add_rule_button"],
                        icon = Icons.Default.Add,
                        onClick = { routes.editRule.navigate() }
                    )
                }
            }
        }
    }

    @Composable
    internal fun ConfigRulesTab() {
        val updateRules = rememberAsyncUpdateDispatcher()
        val rules = rememberAsyncMutableStateList(defaultValue = listOf(), updateDispatcher = updateRules) {
            context.database.getTrackerRulesDesc()
        }
        @Composable
        fun EmptyState(text: String) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                        PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoGraph, contentDescription = text, tint = Color.White)
                    }
                }
                Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = routes.bottomPadding)
            ) {
                item {
                    if (rules.isEmpty()) {
                        EmptyState(translation["no_rules_found"])
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    val ruleName by rememberAsyncMutableState(defaultValue = rule.name) {
                        context.database.getTrackerRule(rule.id)?.name ?: translation["empty_rule_name"]
                    }
                    val eventCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.database.getTrackerEvents(rule.id).size
                    }
                    val scopeCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.database.getRuleTrackerScopes(rule.id).size
                    }
                    var enabled by rememberAsyncMutableState(defaultValue = rule.enabled) {
                        context.database.getTrackerRule(rule.id)?.enabled ?: false
                    }

                    val ruleShape = RoundedCornerShape(20.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                routes.editRule.navigate {
                                    this["rule_id"] = rule.id.toString()
                                }
                            }
                            .padding(8.dp),
                        shape = ruleShape,
                        color = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.4f)
                                )
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PurrfectPalette.cardOverlay, ruleShape)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.08f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                                                    PurrfectPalette.glowSecondary.copy(alpha = 0.3f)
                                                )
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = Color.White)
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(ruleName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(
                                    buildString {
                                        append(eventCount)
                                        append(" ")
                                        append(translation["events_suffix"])
                                        if (scopeCount > 0) {
                                            append(" • ")
                                            append(scopeCount)
                                            append(" ")
                                            append(translation["scopes_suffix"])
                                        }
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurrfectPalette.textSecondary
                                )
                                if (scopeCount > 0) {
                                    val scopesBitmoji = rememberAsyncMutableStateList(defaultValue = emptyList()) {
                                        context.database.getRuleTrackerScopes(rule.id, limit = 8).mapNotNull {
                                            context.database.getFriendInfo(it.key)?.let { friend ->
                                                friend.selfieId to friend.bitmojiId
                                            }
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy((-10).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        scopesBitmoji.take(4).forEach { friend ->
                                            BitmojiImage(
                                                size = 34,
                                                modifier = Modifier
                                                    .border(BorderStroke(1.dp, Color.White), CircleShape)
                                                    .background(Color.White, CircleShape)
                                                    .clip(CircleShape),
                                                context = context,
                                                url = BitmojiSelfie.getBitmojiSelfie(friend.first, friend.second, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D),
                                            )
                                        }
                                        if (scopeCount > scopesBitmoji.size) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.White.copy(alpha = 0.08f),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                            ) {
                                                Text(
                                                    text = "+${scopeCount - scopesBitmoji.size}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = translation[if (enabled) "enabled_label" else "disabled_label"],
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        context.database.setTrackerRuleState(rule.id, it)
                                    },
                                    colors = purrfectSwitchColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    internal fun TrackerScreenContent(nav: NavBackStackEntry) {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = 0) { titles.size }
        currentPage = pagerState.currentPage
        var showExportDialog by remember { mutableStateOf(false) }
        var showSingleExportDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var showInvalidImportTypeDialog by remember { mutableStateOf(false) }

        fun handleImport(type: cock.crest.purrfectsnap.lite.common.data.ExportType) {
            routes.activityLauncher.openFile("application/json") { uri ->
                runCatching {
                    val content = context.androidContext.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@runCatching
                    val exportedData = context.gson.fromJson(content, cock.crest.purrfectsnap.lite.common.data.ExportedTrackerData::class.java)
                    if (exportedData.type != type) {
                        showInvalidImportTypeDialog = true
                        return@runCatching
                    }
                    routes.friendTrackerConfigJsonForImport = content
                    routes.friendTrackerConfigImport.navigate()
                }.onFailure {
                    context.longToast(
                        translation.format("read_file_failed_toast", "message" to (it.message ?: ""))
                    )
                }
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
                    color = PurrfectPalette.cardOverlayColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 10.dp,
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
                                text = context.translation["manager.routes.friend_tracker"],
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = titles.getOrNull(pagerState.currentPage) ?: "",
                                color = PurrfectPalette.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                        if (pagerState.currentPage == 0) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                TrackerPillButton(
                                    label = translation["import_button"],
                                    icon = Icons.Default.FolderOpen,
                                    onClick = { showImportDialog = true }
                                )
                                TrackerPillButton(
                                    label = translation["export_button"],
                                    icon = Icons.Default.SaveAlt,
                                    onClick = { showExportDialog = true }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            titles.forEachIndexed { i, text ->
                                val selected = pagerState.currentPage == i
                                Surface(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable {
                                        coroutineScope.launch { pagerState.animateScrollToPage(i) }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
                                    border = if (selected) BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))) else BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        HorizontalPager(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            state = pagerState
                        ) { page ->
                            when (page) {
                                1 -> LogsTab(
                                    context = context,
                                    activityLauncherHelper = activityLauncherHelper,
                                    deleteAction = { logDeleteAction = it },
                                    exportAction = { exportAction = it },
                                    bottomPadding = routes.bottomPadding
                                )
                                0 -> ConfigRulesTab()
                            }
                        }
                    }
                }
            }
        }

        if (showExportDialog) {
            ChoiceDialog(
                onDismissRequest = { showExportDialog = false },
                title = translation["export_dialog_title"],
                choices = listOf(
                    translation["bulk_export_button"] to { Icon(Icons.Default.UploadFile, translation["bulk_export_button"]) },
                    translation["individual_export_button"] to { Icon(Icons.Default.FileOpen, translation["individual_export_button"]) }
                ),
                onChoiceSelected = { index ->
                    showExportDialog = false
                    when (index) {
                        0 -> routes.friendTrackerConfigExport.navigate()
                        1 -> showSingleExportDialog = true
                    }
                }
            )
        }

        if (showSingleExportDialog) {
            val rules = rememberAsyncMutableStateList(defaultValue = emptyList()) {
                context.database.getTrackerRulesDesc()
            }
            SelectRuleDialog(
                onDismissRequest = { showSingleExportDialog = false },
                rules = rules,
                onRuleSelected = { rule ->
                    showSingleExportDialog = false
                    routes.friendTrackerConfigExport.navigate {
                        this["rule_id"] = rule.id.toString()
                    }
                },
                translation = translation
            )
        }

        if (showInvalidImportTypeDialog) {
            AlertDialog(
                onDismissRequest = { showInvalidImportTypeDialog = false },
                title = { Text(translation["invalid_import_type_dialog_title"]) },
                text = { Text(translation["invalid_import_type_dialog_text"]) },
                confirmButton = {
                    Button(onClick = { showInvalidImportTypeDialog = false }) {
                        Text(translation["button.ok"])
                    }
                }
            )
        }

        if (showImportDialog) {
            ChoiceDialog(
                onDismissRequest = { showImportDialog = false },
                title = translation["import_dialog_title"],
                choices = listOf(
                    translation["bulk_import_button"] to { Icon(Icons.Default.UploadFile, translation["bulk_import_button"]) },
                    translation["individual_import_button"] to { Icon(Icons.Default.FileOpen, translation["individual_import_button"]) }
                ),
                onChoiceSelected = { index ->
                    showImportDialog = false
                    when (index) {
                        0 -> handleImport(cock.crest.purrfectsnap.lite.common.data.ExportType.BULK)
                        1 -> handleImport(cock.crest.purrfectsnap.lite.common.data.ExportType.SINGLE)
                    }
                }
            )
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
                this@FriendTrackerManagerRoot.FriendTrackerScreen(nav)
            }
        }
    }
}

@Composable
private fun SelectRuleDialog(
    onDismissRequest: () -> Unit,
    rules: List<cock.crest.purrfectsnap.lite.common.data.TrackerRule>,
    onRuleSelected: (cock.crest.purrfectsnap.lite.common.data.TrackerRule) -> Unit,
    translation: cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(translation["manager.friend_tracker.select_rule_to_export_title"], style = MaterialTheme.typography.headlineSmall)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules) { rule ->
                        ElevatedCard(
                            onClick = { onRuleSelected(rule) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rule.name,
                                modifier = Modifier.padding(16.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(translation["button.cancel"])
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(
    onDismissRequest: () -> Unit,
    title: String,
    choices: List<Pair<String, @Composable () -> Unit>>,
    onChoiceSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            shadowElevation = 20.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.5f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, shape)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                choices.forEachIndexed { index, (text, icon) ->
                    SelectButton(
                        onClick = { onChoiceSelected(index) },
                        text = text,
                        leadingIcon = icon
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectButton(
    onClick: () -> Unit,
    text: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(text = text, modifier = Modifier.weight(1f))
        }
    }
}
