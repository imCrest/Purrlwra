package cock.crest.purrfectsnap.lite.ui.manager.pages.scripting

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.*
import cock.crest.purrfectsnap.lite.common.scripting.type.ModuleInfo
import cock.crest.purrfectsnap.lite.common.scripting.ui.EnumScriptInterface
import cock.crest.purrfectsnap.lite.common.scripting.ui.InterfaceManager
import cock.crest.purrfectsnap.lite.common.scripting.ui.ScriptInterface
import cock.crest.purrfectsnap.lite.common.ui.AsyncUpdateDispatcher
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncUpdateDispatcher
import cock.crest.purrfectsnap.lite.common.util.ktx.getUrlFromClipboard
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import cock.crest.purrfectsnap.lite.storage.isScriptEnabled
import cock.crest.purrfectsnap.lite.storage.setScriptEnabled
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticEmptyState
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.chooseFolder
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.PullRefreshIndicator
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.pullRefresh
import cock.crest.purrfectsnap.lite.ui.util.pullrefresh.rememberPullRefreshState

class ScriptingRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.scripting") }
    internal lateinit var activityLauncherHelper: ActivityLauncherHelper
    internal val reloadDispatcher = AsyncUpdateDispatcher(updateOnFirstComposition = false)
    internal var selectedTab by mutableStateOf(0)

    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    internal suspend fun isScriptInstalledByUrl(scriptUrl: String): Boolean {
        return try {
            val installedScripts = context.scriptManager.getSyncedModules()
            installedScripts.any { module ->
                module.updateUrl?.equals(scriptUrl, ignoreCase = true) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    internal fun downloadScript(scriptUrl: String, onComplete: () -> Unit) {
        context.coroutineScope.launch {
            if (isScriptInstalledByUrl(scriptUrl)) {
                context.shortToast(translation["script_already_installed"])
                return@launch
            }

            runCatching {
                context.shortToast(translation["downloading_script"])
                val moduleInfo = context.scriptManager.importFromUrl(scriptUrl)
                context.shortToast(translation.format("script_downloaded", "name" to moduleInfo.name))
                reloadDispatcher.dispatch()
                onComplete()
            }.onFailure {
                context.log.error("Failed to download script", it)
                context.shortToast(translation["download_script_failed"])
            }
        }
    }

    @Composable
    internal fun ImportRemoteScript(
        dismiss: () -> Unit
    ) {
        var url by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            context.androidContext.getUrlFromClipboard()?.let { url = it }
        }

        AestheticDialog(
            onDismissRequest = dismiss,
            title = translation["import_script_from_url_title"],
            text = translation["import_script_warning"],
            icon = Icons.Default.Link,
            confirmButtonText = translation["import_button"],
            dismissButtonText = translation["button.cancel"],
            onDismiss = dismiss,
            loading = isLoading,
            confirmEnabled = url.isNotBlank(),
            showCloseButton = false,
            onConfirm = {
                isLoading = true
                context.coroutineScope.launch {
                    runCatching {
                        if (isScriptInstalledByUrl(url)) {
                            context.shortToast(translation["script_already_installed"])
                            withContext(Dispatchers.Main) {
                                dismiss()
                            }
                            return@launch
                        }

                        val moduleInfo = context.scriptManager.importFromUrl(url)
                        context.shortToast(translation.format("script_imported", "name" to moduleInfo.name))
                        reloadDispatcher.dispatch()
                        withContext(Dispatchers.Main) {
                            dismiss()
                        }
                        return@launch
                    }.onFailure {
                        context.log.error("Failed to import script", it)
                        context.shortToast(translation.format("import_failed", "message" to (it.message ?: context.translation["common.unknown"])))
                    }
                    isLoading = false
                }
            },
            customContent = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(text = translation["enter_url_label"]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { focusRequester.requestFocus() },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.06f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = PurrfectPalette.glowSecondary.copy(alpha = 0.5f),
                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = PurrfectPalette.glowSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = PurrfectPalette.textSecondary
                    )
                )
            }
        )
    }

    @Composable
    internal fun ModuleActions(
        script: ModuleInfo,
        canUpdate: Boolean,
        dismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = dismiss) {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                val actions = remember {
                    mutableMapOf<Pair<String, ImageVector>, suspend () -> Unit>().apply {
                        if (canUpdate) {
                            put(translation["update_module_button"] to Icons.Default.Download) {
                                dismiss()
                                context.shortToast(translation.format("updating_script", "name" to script.name))
                                runCatching {
                                    val modulePath = context.scriptManager.getModulePath(script.name) ?: throw Exception(translation["module_not_found"])
                                    context.scriptManager.unloadScript(modulePath)
                                    val moduleInfo = context.scriptManager.importFromUrl(script.updateUrl!!, filepath = modulePath)
                                    context.shortToast(translation.format("updated_script", "name" to script.name, "version" to moduleInfo.version))
                                    context.database.setScriptEnabled(script.name, false)
                                    withContext(context.database.executor.asCoroutineDispatcher()) {
                                        reloadDispatcher.dispatch()
                                    }
                                }.onFailure {
                                    context.log.error("Failed to update module", it)
                                    context.shortToast(translation["update_module_failed"])
                                }
                            }
                        }
                        put(translation["edit_module_button"] to Icons.Default.Edit) {
                            runCatching {
                                val modulePath = context.scriptManager.getModulePath(script.name)!!
                                context.androidContext.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        data = context.scriptManager.getScriptsFolder()!!.findFile(modulePath)!!.uri
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    }
                                )
                                dismiss()
                            }.onFailure {
                                context.log.error("Failed to open module file", it)
                                context.shortToast(translation["open_module_failed"])
                            }
                        }
                        put(translation["clear_module_data_button"] to Icons.Default.Save) {
                            runCatching {
                                context.scriptManager.getModuleDataFolder(script.name).deleteRecursively()
                                context.shortToast(translation["module_data_cleared"])
                                dismiss()
                            }.onFailure {
                                context.log.error("Failed to clear module data", it)
                                context.shortToast(translation["clear_module_data_failed"])
                            }
                        }
                        put(translation["delete_module_button"] to Icons.Default.DeleteOutline) {
                            context.scriptManager.apply {
                                runCatching {
                                    val modulePath = getModulePath(script.name)!!
                                    unloadScript(modulePath)
                                    getScriptsFolder()?.findFile(modulePath)?.delete()
                                    reloadDispatcher.dispatch()
                                    context.shortToast(translation.format("deleted_script", "name" to script.name))
                                    dismiss()
                                }.onFailure {
                                    context.log.error("Failed to delete module", it)
                                    context.shortToast(translation["delete_module_failed"])
                                }
                            }
                        }
                    }.toMap()
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = translation["actions_title"],
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    items(actions.size) { index ->
                        val action = actions.entries.elementAt(index)
                        ListItem(
                            modifier = Modifier
                                .clickable { context.coroutineScope.launch { action.value(); dismiss() } }
                                .fillMaxWidth(),
                            leadingContent = {
                                Icon(action.key.second, action.key.first)
                            },
                            headlineContent = { Text(action.key.first) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun ModuleItem(script: ModuleInfo) {
        var enabled by rememberAsyncMutableState(defaultValue = false, keys = arrayOf(script)) {
            context.database.isScriptEnabled(script.name)
        }
        var openSettings by remember(script) { mutableStateOf(false) }
        var openActions by remember { mutableStateOf(false) }

        val dispatcher = rememberAsyncUpdateDispatcher()
        val reloadCallback = remember { suspend { dispatcher.dispatch() } }
        val latestUpdate by rememberAsyncMutableState(defaultValue = null, updateDispatcher = dispatcher, keys = arrayOf(script)) {
            context.scriptManager.checkForUpdate(script)
        }

        LaunchedEffect(Unit) {
            reloadDispatcher.addCallback(reloadCallback)
        }
        DisposableEffect(Unit) {
            onDispose { reloadDispatcher.removeCallback(reloadCallback) }
        }

        val cardShape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                if (enabled) Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))
                else SolidColor(Color.White.copy(alpha = 0.08f))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { if (enabled) openSettings = !openSettings }
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = script.displayName ?: script.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = script.description ?: translation["no_description"],
                            fontSize = 13.sp,
                            color = PurrfectPalette.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            latestUpdate?.let {
                                AssistChip(
                                    onClick = { },
                                    enabled = false,
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = PurrfectPalette.glowSecondary) },
                                    label = { Text(translation.format("update_available", "version" to it.version)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    )
                                )
                            }
                            if (openSettings && enabled) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(translation["actions_button"]) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.06f),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    IconButton(onClick = { openActions = !openActions }) {
                        Icon(Icons.Default.Build, translation["actions_button"], tint = Color.White)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { isChecked ->
                            openSettings = false
                            context.coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    val modulePath = context.scriptManager.getModulePath(script.name)!!
                                    context.scriptManager.unloadScript(modulePath)
                                    if (isChecked) {
                                        context.scriptManager.loadScript(modulePath)
                                        context.scriptManager.runtime.getModuleByName(script.name)
                                            ?.callFunction("module.onPurrfectSnapLoad")
                                        context.shortToast(translation.format("loaded_script", "name" to script.name))
                                    } else {
                                        context.shortToast(translation.format("unloaded_script", "name" to script.name))
                                    }
                                    context.database.setScriptEnabled(script.name, isChecked)
                                    withContext(Dispatchers.Main) { enabled = isChecked }
                                }.onFailure { throwable ->
                                    withContext(Dispatchers.Main) { enabled = !isChecked }
                                    context.log.error("Failed to ${if (isChecked) "enable" else "disable"} script", throwable)
                                    context.shortToast(translation.format(if (isChecked) "enable_script_failed" else "disable_script_failed"))
                                }
                            }
                        },
                        colors = purrfectSwitchColors()
                    )
                }
                if (openSettings) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    ScriptSettings(script)
                }
            }
        }
        if (openActions) {
            ModuleActions(script = script, canUpdate = latestUpdate != null) { openActions = false }
        }
    }

    override val floatingActionButton: @Composable () -> Unit = {}

    @Composable
    internal fun SelectFolderButton(onClick: () -> Unit) {
        val label = translation["select_folder_button"]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(78.dp),
                shape = CircleShape,
                color = PurrfectPalette.cardOverlayColor.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.7f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.65f)
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.42f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.34f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }

    @Composable
    internal fun ScriptSettings(script: ModuleInfo) {
        val settingsInterface = remember {
            val module =
                context.scriptManager.runtime.getModuleByName(script.name) ?: return@remember null
            (module.getBinding(InterfaceManager::class))?.buildInterface(EnumScriptInterface.SETTINGS)
        }
        if (settingsInterface == null) {
            Text(
                text = translation["no_settings_for_module"],
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            ScriptInterface(interfaceBuilder = settingsInterface)
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }

        key(themeId) {
            with(ManagerTheme.fromId(themeId).theme) {
                this@ScriptingRootSection.ScriptingScreen(nav)
            }
        }
    }

    @Composable
    internal fun InstalledTabContent(
        scriptingFolder: DocumentFile?
    ) {
        val scriptModules by rememberAsyncMutableState(
            defaultValue = emptyList(),
            updateDispatcher = reloadDispatcher
        ) { context.scriptManager.sync(); context.scriptManager.getSyncedModules() }
        val coroutineScope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            refreshing = true
            withContext(Dispatchers.IO) {
                reloadDispatcher.dispatch()
                refreshing = false
            }
        }
        val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
            refreshing = true
            coroutineScope.launch(Dispatchers.IO) {
                reloadDispatcher.dispatch()
                refreshing = false
            }
        })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.04f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState),
                        contentPadding = PaddingValues(bottom = routes.bottomPadding + 28.dp, start = 8.dp, end = 8.dp, top = 12.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            if (scriptingFolder == null && !refreshing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 260.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = BorderStroke(
                                            1.dp,
                                            Brush.linearGradient(
                                                listOf(
                                                    PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                                                    PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                                                )
                                            )
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(58.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(Color.White.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOpen,
                                                    contentDescription = null,
                                                    tint = PurrfectPalette.glowSecondary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Text(
                                                text = translation["no_scripts_folder_selected_title"],
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                color = Color.White
                                            )
                                            Text(
                                                text = translation["select_scripts_folder_toast"],
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                color = PurrfectPalette.textSecondary,
                                                lineHeight = 18.sp
                                            )
                                            SelectFolderButton(
                                                onClick = {
                                                    activityLauncherHelper.chooseFolder {
                                                        context.config.root.scripting.moduleFolder.set(it)
                                                        context.config.writeConfig()
                                                        coroutineScope.launch { reloadDispatcher.dispatch() }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (scriptModules.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AestheticEmptyState(
                                        icon = Icons.Default.DataObject,
                                        title = translation["no_scripts_found_title"],
                                        subtitle = translation["use_catalog_to_add_scripts"],
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 22.dp)
                                    )
                                }
                            }
                        }
                        items(scriptModules.size, key = { scriptModules[it].hashCode() }) { index ->
                            ModuleItem(scriptModules[index])
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = refreshing,
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
        var scriptingWarning by remember {
            mutableStateOf(context.sharedPreferences.run {
                getBoolean("scripting_warning", true).also {
                    edit().putBoolean("scripting_warning", false).apply()
                }
            })
        }
        if (scriptingWarning) {
            var timeout by remember { mutableIntStateOf(10) }
            LaunchedEffect(Unit) {
                while (timeout > 0) {
                    delay(1000)
                    timeout--
                }
            }
            AestheticDialog(
                onDismissRequest = { if (timeout == 0) scriptingWarning = false },
                title = context.translation["manager.dialogs.scripting_warning.title"],
                text = context.translation["manager.dialogs.scripting_warning.content"],
                icon = Icons.Default.Warning,
                confirmButtonText = translation["button.ok"],
                onConfirm = { if (timeout == 0) scriptingWarning = false },
                loading = timeout > 0,
                showCloseButton = false,
                customContent = {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PurrfectPalette.cardOverlayColor,
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                PurrfectPalette.glowPrimary.copy(alpha = 0.25f),
                                                PurrfectPalette.glowSecondary.copy(alpha = 0.18f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = timeout.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                        }
                    }
                }
            )
        }
    }

    @Composable
    internal fun CatalogTabContent(
        scriptingFolder: DocumentFile?
    ) {
        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.04f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                if (scriptingFolder == null) {
                    AestheticEmptyState(
                        icon = Icons.Default.FolderOpen,
                        title = translation["no_scripts_folder_selected_title"],
                        subtitle = translation["select_scripts_folder_toast"],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                    )
                } else {
                    ScriptCatalog(this@ScriptingRootSection)
                }
            }
        }
    }

    @Composable
    internal fun ScriptingHeader(
        titles: List<String>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        onImport: () -> Unit,
        onOpenFolder: () -> Unit,
        onManageRepos: () -> Unit,
        onDocs: () -> Unit,
        folderSelected: Boolean
    ) {
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
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = translation["manager.routes.scripts"],
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDocs) {
                            Icon(Icons.Default.CollectionsBookmark, contentDescription = translation["documentation_button"], tint = Color.White)
                        }
                        IconButton(onClick = onManageRepos) {
                            Icon(Icons.Default.Public, contentDescription = translation["manage_repos_button"], tint = Color.White)
                        }
                        IconButton(onClick = onOpenFolder) {
                            Icon(Icons.Default.FolderOpen, contentDescription = translation["open_scripts_folder_button"], tint = Color.White)
                        }
                        IconButton(onClick = onImport, enabled = folderSelected) {
                            Icon(Icons.Default.Link, contentDescription = translation["import_from_url_button"], tint = if (folderSelected) Color.White else Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
                ScriptingTabSwitcher(
                    titles = titles,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            }
        }
    }

    @Composable
    internal fun ScriptingTabSwitcher(
        titles: List<String>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            titles.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f),
                    border = if (isSelected) BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))
                    ) else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onTabSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (index == 0) Icons.Default.Extension else Icons.Default.Public,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    override val topBarActions: @Composable() (RowScope.() -> Unit) = {}
}




