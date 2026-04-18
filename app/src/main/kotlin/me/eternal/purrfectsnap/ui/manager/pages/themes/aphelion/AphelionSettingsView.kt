package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion

import android.content.SharedPreferences
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.common.action.EnumAction
import cock.crest.purrfectsnap.lite.common.bridge.InternalFileHandleType
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.storage.getAllScopeNotes
import cock.crest.purrfectsnap.lite.storage.setAllScopeNotes
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeSettings
import cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion.AphelionHaptics
import cock.crest.purrfectsnap.lite.ui.util.headerHeightTracker
import cock.crest.purrfectsnap.lite.ui.util.Motion
import cock.crest.purrfectsnap.lite.ui.setup.Requirements
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.saveFile
import cock.crest.purrfectsnap.lite.ui.util.openFile
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeSettings.AphelionSettingsScreen(nav: NavBackStackEntry) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    var switchCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var controlsHeight by remember { mutableStateOf(100.dp) }
    var showResetSetupDialog by remember { mutableStateOf(false) }

    val sharedButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.White.copy(alpha = 0.07f),
        contentColor = Color.White
    )
    val sharedOutlinedColors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)

    val computedScrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt()
            else listState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(computedScrollOffset) {
        routes.navigation?.globalScrollOffset = computedScrollOffset
    }

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
                confirmButtonText = context.translation["button.positive"],
                dismissButtonText = context.translation["button.negative"],
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = routes.bottomPadding + 24.dp)
        ) {
            item {
                Spacer(Modifier.height(controlsHeight))
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // THEME SWITCHER
                    GlassCard {
                        RowTitle(title = translation["ui_theme_title"] ?: "UI Theme")
                        ShiftedRow {
                            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = translation["settings_ui_theme"] ?: "Aphelion Theme", fontSize = 14.sp, color = Color.White)
                                val currentThemeId = context.config.root.global.uiSettings.managerTheme.get()
                                var localThemeId by remember { mutableStateOf(currentThemeId) }
                                
                                Switch(
                                    checked = localThemeId == "APHELION",
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
                                            writeJob.join() // Ensure it finishes its work before scope potentially closes
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

                    // ACTIONS
                    GlassCard {
                        RowTitle(title = translation["actions_title"])
                        EnumAction.entries.forEach { enumAction -> RowAction(key = enumAction.key) { context.launchActionIntent(enumAction) } }
                        RowAction(key = "regen_mappings") { context.checkForRequirements(Requirements.MAPPINGS) }
                        RowAction(key = "change_language") { context.checkForRequirements(Requirements.LANGUAGE) }
                    }

                    // UI SETTINGS
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

                    // UPDATES
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
                                        listOf("stable", "prerelease").forEach { channel -> DropdownMenuItem(text = { Text(text = translation.getOrNull("update_channel_${channel}") ?: channel) }, onClick = { selectedChannel = channel; channelMenuExpanded = false; context.config.root.global.updateSettings.updateChannel.set(channel); context.config.writeConfig(); scheduleUpdateCheck() }) }
                                    }
                                }
                            }
                        }
                    }

                    // RESET SETUP
                    GlassCard {
                        RowTitle(title = translation["reset_setup_title"])
                        ShiftedRow(modifier = Modifier.fillMaxWidth().heightIn(min = 55.dp).clickable { showResetSetupDialog = true }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = translation["reset_setup_action"], fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 14.dp))
                        }
                    }

                    // MESSAGE LOGGER
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
                                AestheticDialog(onDismissRequest = { showImportDialog = false }, title = translation["message_logger_import_title"], text = translation["message_logger_import_text"], icon = Icons.Filled.Info, confirmButtonText = context.translation["button.import"], dismissButtonText = context.translation["button.cancel"], onConfirm = { showImportDialog = false; runCatching { activityLauncherHelper.openFile("application/octet-stream") { uri -> context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { context.messageLogger.databaseFile.outputStream().use { out -> it.copyTo(out) } }; storedMessagesCount = context.messageLogger.getStoredMessageCount(); storedStoriesCount = context.messageLogger.getStoredStoriesCount(); context.shortToast(translation["success_toast"]) } } }, onDismiss = { showImportDialog = false }, showCloseButton = false)
                            }
                        }
                    }

                    // FRIEND NOTES
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

                    // DEBUG
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
                }
            }
        }

        FloatingTopBar(
            title = routeInfo.translatedKey?.value ?: translation["manager.routes.home_settings"] ?: "Settings",
            onBack = { routes.navController.popBackStack() },
            scrollOffset = computedScrollOffset,
            enableMorph = true,
            titleAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.headerHeightTracker { controlsHeight = it },
            actions = {
                IconButton(onClick = {
                    if (context.config.root.global.uiSettings.hapticFeedback.get()) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    routes.navigation?.openBottomBarCustomization = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        )
    }
}
