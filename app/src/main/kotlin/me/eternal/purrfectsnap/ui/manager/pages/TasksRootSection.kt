package cock.crest.purrfectsnap.lite.ui.manager.pages

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.download.DownloadProcessor
import cock.crest.purrfectsnap.lite.bridge.DownloadCallback
import cock.crest.purrfectsnap.lite.common.data.download.MediaDownloadSource
import cock.crest.purrfectsnap.lite.common.data.download.DownloadMetadata
import cock.crest.purrfectsnap.lite.common.ui.TopBarActionButton
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.data.download.createNewFilePath
import cock.crest.purrfectsnap.lite.common.util.snap.RemoteMediaResolver
import cock.crest.purrfectsnap.lite.download.FFMpegProcessor
import cock.crest.purrfectsnap.lite.task.PendingTask
import cock.crest.purrfectsnap.lite.task.PendingTaskListener
import cock.crest.purrfectsnap.lite.task.Task
import cock.crest.purrfectsnap.lite.task.TaskStatus
import cock.crest.purrfectsnap.lite.task.TaskType
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.*
import java.io.File

class TasksRootSection : Routes.Route() {
    enum class TaskTab {
        ACTIVE, SCHEDULED
    }

    internal var selectedTab by mutableStateOf(TaskTab.ACTIVE)
    internal var activeTasks by mutableStateOf(listOf<PendingTask>())
    internal var recentTasks = mutableStateListOf<Task>()
    internal val taskSelection = mutableStateListOf<Pair<Task, DocumentFile?>>()
    internal var lastFetchedTaskId: Long? by mutableStateOf(null)

    internal fun fetchActiveTasks(scope: CoroutineScope) {
        activeTasks = context.taskManager.getActiveTasks().values.toList()
    }

    internal fun fetchNewRecentTasks() {
        val tasks = context.taskManager.fetchStoredTasks(lastFetchedTaskId ?: Long.MAX_VALUE, limit = 20)
        if (tasks.isEmpty()) return
        
        lastFetchedTaskId = tasks.keys.last()
        val activeTaskHashes = activeTasks.map { it.task.hash }
        val existingHashes = recentTasks.map { it.hash }
        
        val newTasks = tasks.values.filter { it.hash !in activeTaskHashes && it.hash !in existingHashes }
        recentTasks.addAll(newTasks)
    }

    internal fun refreshRecentTasks() {
        val tasks = context.taskManager.fetchStoredTasks(Long.MAX_VALUE, limit = 20)
        val activeTaskHashes = activeTasks.map { it.task.hash }
        val newTasks = tasks.values.filter { it.hash !in activeTaskHashes }
        
        recentTasks.clear()
        recentTasks.addAll(newTasks)
        lastFetchedTaskId = tasks.keys.lastOrNull()
    }

    internal fun isRecentTasksInitialized() = true

    override val init: () -> Unit = {
        recentTasks = mutableStateListOf()
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
                this@TasksRootSection.TasksScreen(nav)
            }
        }
    }

    @Composable
    internal fun TasksRootSection.TasksScreen(nav: NavBackStackEntry) {
        val listState = rememberLazyListState()
        var controlsHeight by remember { mutableStateOf(100.dp) }

        val computedScrollOffset by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt()
                else listState.firstVisibleItemScrollOffset
            }
        }

        LaunchedEffect(computedScrollOffset) {
            val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
            if (isAphelion) {
                routes.navigation?.globalScrollOffset = computedScrollOffset
            } else {
                routes.navigation?.globalScrollOffset = 0
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                routes.navigation?.globalScrollOffset = 0
            }
        }

        LaunchedEffect(Unit) {
            refreshRecentTasks()
            while (true) {
                fetchActiveTasks(this)
                delay(2000)
            }
        }

        val shouldFetchMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItemsNumber = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
                lastVisibleItemIndex > (totalItemsNumber - 5)
            }
        }

        LaunchedEffect(shouldFetchMore) {
            if (shouldFetchMore) {
                fetchNewRecentTasks()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(PurrfectPalette.backgroundGradient))

            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(controlsHeight))

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
                            top = 16.dp,
                            bottom = routes.bottomPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "auto_open_card") {
                            var queueItems by remember { mutableStateOf(listOf<Any>()) }
                            var processedCount by remember { mutableIntStateOf(0) }
                            
                            LaunchedEffect(Unit) {
                                while (true) {
                                    runCatching {
                                        val autoOpen = context.bridgeService?.messagingBridge?.getAutoOpenInterface()
                                        processedCount = autoOpen?.processedCount ?: 0
                                        val items = autoOpen?.queueItems ?: emptyList()
                                        queueItems = items.mapNotNull { 
                                            runCatching { context.gson.fromJson(it, Map::class.java) }.getOrNull()
                                        }
                                    }
                                    kotlinx.coroutines.delay(2000)
                                }
                            }
                            
                            if (queueItems.isNotEmpty() || processedCount > 0) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = MaterialTheme.shapes.large,
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(translation["auto_open_snaps.title"] ?: "Auto Open Snaps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { 
                                                runCatching { context.bridgeService?.messagingBridge?.getAutoOpenInterface()?.reset() }
                                            }) {
                                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Text(
                                            "${translation["auto_open_snaps.queue_size"] ?: "Queue"}: ${queueItems.size} \u00b7 ${translation["auto_open_snaps.processed_count"] ?: "Opened"}: $processedCount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }

                        if (activeTasks.isEmpty() && recentTasks.isEmpty()) {
                            item {
                                AphelionTasksEmptyState(translation["no_tasks"])
                            }
                        }

                        // CONSOLIDATED SESSION VIEW: Group Auto-Open tasks by their persistent session task.
                        // Non-AutoOpen tasks (Downloads, etc.) remain as individual cards.
                        val groupedActiveTasks = activeTasks.distinctBy { it.task.hash }

                        items(groupedActiveTasks, key = { it.task.hash }) { pendingTask ->
                            val isAutoOpen = pendingTask.task.isAutoOpen
                            val pulseAnimation = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by pulseAnimation.animateFloat(
                                initialValue = 0.15f,
                                targetValue = 0.45f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )

                            TaskCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { 
                                        if (isAutoOpen) {
                                            it.border(
                                                width = 1.5.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(
                                                        PurrfectPalette.glowPrimary.copy(alpha = pulseAlpha),
                                                        PurrfectPalette.glowSecondary.copy(alpha = pulseAlpha)
                                                    )
                                                ),
                                                shape = MaterialTheme.shapes.large
                                            )
                                        } else it
                                    },
                                task = pendingTask.task,
                                pendingTask = pendingTask
                            )
                        }

                        items(recentTasks.filter { task -> activeTasks.none { it.task.hash == task.hash } }, key = { it.hash }) { task ->
                            TaskCard(
                                modifier = Modifier.fillMaxWidth(),
                                task = task
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(modifier = Modifier.headerHeightTracker { controlsHeight = it }) {
                cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar(
                    title = translation["manager.routes.tasks"],
                    subtitle = translation["tasks_tagline"],
                    scrollOffset = computedScrollOffset,
                    enableMorph = true,
                    actions = {
                        topBarActions()
                    }
                )
            }
        }
    }

    internal fun mergeSelection(files: List<Pair<Task, DocumentFile>>) {
        val taskHash = System.nanoTime().toString(36)
        val firstTask = files.first().first
        val pendingTask = context.taskManager.createPendingTask(Task(
            type = TaskType.DOWNLOAD,
            title = firstTask.title,
            author = firstTask.author,
            hash = taskHash
        )).apply {
            status = TaskStatus.RUNNING
        }

        context.coroutineScope.launch(Dispatchers.IO) {
            val filesToMerge = files.mapNotNull { (_, documentFile) ->
                val tempFile = File.createTempFile("merge", ".tmp")
                context.androidContext.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }

            val mergedFile = File.createTempFile("merged", ".mp4")
            runCatching {
                context.shortToast(translation.format("merge_files_toast", "count" to filesToMerge.size.toString()))
                FFMpegProcessor.newFFMpegProcessor(context, pendingTask).execute(
                    FFMpegProcessor.Request(FFMpegProcessor.Action.MERGE_MEDIA, filesToMerge.map { it.absolutePath }, mergedFile)
                )
                DownloadProcessor(context, object: DownloadCallback.Default() {
                    override fun onSuccess(outputPath: String) {
                        context.log.verbose("Merged files to $outputPath")
                    }
                }).saveMediaToGallery(pendingTask, mergedFile, DownloadMetadata(
                    mediaIdentifier = taskHash,
                    outputPath = createNewFilePath(
                        context.config.root,
                        taskHash,
                        downloadSource = MediaDownloadSource.MERGED,
                        mediaAuthor = firstTask.author,
                        creationTimestamp = System.currentTimeMillis()
                    ),
                    mediaAuthor = firstTask.author,
                    downloadSource = MediaDownloadSource.MERGED.translate(context.translation),
                    iconUrl = null
                ))
            }.onFailure {
                context.log.error("Failed to merge files", it)
                pendingTask.fail(it.message ?: "Failed to merge files")
            }.onSuccess {
                pendingTask.success()
            }
            filesToMerge.forEach { it.delete() }
            mergedFile.delete()
        }.also {
            pendingTask.addListener(PendingTaskListener(onCancel = { it.cancel() }))
        }
    }

    internal fun clearTasks(alsoDeleteFiles: Boolean, scope: CoroutineScope) {
        if (taskSelection.isNotEmpty()) {
            taskSelection.forEach { (task, documentFile) ->
                scope.launch(Dispatchers.IO) {
                    context.taskManager.removeTask(task)
                    if (alsoDeleteFiles) documentFile?.delete()
                }
                recentTasks.remove(task)
            }
            activeTasks = activeTasks.filter { task -> !taskSelection.map { it.first }.contains(task.task) }
            taskSelection.clear()
        } else {
            scope.launch(Dispatchers.IO) { context.taskManager.clearAllTasks() }
            recentTasks.clear()
            activeTasks.forEach {
                runCatching { it.cancel() }.onFailure { throwable ->
                    context.log.error("Failed to cancel task $it", throwable)
                }
            }
            activeTasks = listOf()
        }
    }

    @Composable
    internal fun TaskDangerDialog(
        visible: Boolean,
        title: String,
        message: String,
        showDeleteFiles: Boolean,
        deleteFilesChecked: Boolean,
        onToggleDeleteFiles: (Boolean) -> Unit,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        if (!visible) return

        val dialogShape = RoundedCornerShape(24.dp)
        val haptic = LocalHapticFeedback.current
        val borderGradient = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.65f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.55f)
                )
            )
        }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = dialogShape,
                color = Color.White.copy(alpha = 0.06f),
                tonalElevation = 0.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, borderGradient)
            ) {
                Box(
                    modifier = Modifier
                        .background(PurrfectPalette.cardOverlay, dialogShape)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF6B9B),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }

                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PurrfectPalette.textSecondary
                        )

                        if (showDeleteFiles) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleDeleteFiles(!deleteFilesChecked) 
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = deleteFilesChecked,
                                        onCheckedChange = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleDeleteFiles(it) 
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = PurrfectPalette.glowPrimary,
                                            uncheckedColor = Color.White,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                    Column {
                                        Text(
                                            text = context.translation["delete_files_option"],
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = context.translation["delete_files_option_hint"] ?: "Also remove downloaded files",
                                            color = PurrfectPalette.textSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(context.translation["button.negative"])
                            }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(context.translation["button.positive"])
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun TasksEmptyState(text: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        .size(58.dp)
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
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = text,
                        tint = Color.White
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
        }
    }

    @Composable
    internal fun AphelionTasksEmptyState(text: String) {
        TasksEmptyState(text)
    }

    override val topBarActions: @Composable (RowScope.() -> Unit) = {
        var showConfirmDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        if (taskSelection.size > 1) {
            val canMergeSelection by rememberAsyncMutableState(defaultValue = false, keys = arrayOf(taskSelection.size)) {
                taskSelection.all { it.second?.type?.contains("video") == true }
            }

            if (canMergeSelection) {
                TopBarActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        mergeSelection(taskSelection.toList().also {
                            taskSelection.clear()
                        }.map { it.first to it.second!! })
                    },
                    icon = Icons.Filled.Merge,
                    text = translation["merge_button"]
                )
            }
        }

        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showConfirmDialog = true
        }) {
            Icon(Icons.Filled.Delete, contentDescription = translation["clear_button_description"])
        }

        if (showConfirmDialog) {
            var alsoDeleteFiles by remember { mutableStateOf(false) }
            val isSelection = taskSelection.isNotEmpty()
            val titleText = if (isSelection) {
                translation.format("remove_selected_tasks_confirm", "count" to taskSelection.size.toString())
            } else {
                translation["remove_all_tasks_confirm"]
            }
            val messageText = if (isSelection) translation["remove_selected_tasks_title"] else translation["remove_all_tasks_title"]

            TaskDangerDialog(
                visible = showConfirmDialog,
                title = titleText,
                message = messageText,
                showDeleteFiles = isSelection,
                deleteFilesChecked = alsoDeleteFiles,
                onToggleDeleteFiles = { alsoDeleteFiles = it },
                onConfirm = {
                    showConfirmDialog = false
                    clearTasks(alsoDeleteFiles, coroutineScope)
                },
                onDismiss = { showConfirmDialog = false }
            )
        }
    }

    @Composable
    internal fun TaskCard(modifier: Modifier, task: Task, pendingTask: PendingTask? = null) {
        var taskStatus by remember { mutableStateOf(task.status) }
        var taskProgressLabel by remember { mutableStateOf<String?>(null) }
        var taskProgress by remember { mutableIntStateOf(-1) }
        val isSelected by remember { derivedStateOf { taskSelection.any { it.first == task } } }
        val haptic = LocalHapticFeedback.current

        var documentFileMimeType by remember { mutableStateOf("") }
        var isDocumentFileReadable by remember { mutableStateOf(true) }
        
        val docVal = task.extra?.toUri()
        val documentFile by rememberAsyncMutableState(defaultValue = null as DocumentFile?, keys = arrayOf(taskStatus.name)) {
            if (docVal == null) null
            else DocumentFile.fromSingleUri(context.androidContext, docVal)?.apply {
                documentFileMimeType = type ?: ""
                isDocumentFileReadable = canRead()
            }
        }


        val listener = remember { PendingTaskListener(
            onStateChange = {
                taskStatus = it
            },
            onProgress = { label, progress ->
                taskProgressLabel = label
                taskProgress = progress
            }
        ) }

        LaunchedEffect(Unit) {
            pendingTask?.addListener(listener)
        }

        DisposableEffect(Unit) {
            onDispose {
                pendingTask?.removeListener(listener)
            }
        }

        fun toggleSelection() {
            if (isSelected) {
                taskSelection.removeIf { it.first == task }
                return
            }
            taskSelection.add(task to documentFile)
        }

        fun openFile() {
            if (!isDocumentFileReadable || documentFile == null) return
            runCatching {
                context.androidContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentFile!!.uri, documentFile!!.type)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.onFailure {
                context.log.error("Failed to open file ${documentFile?.uri}", it)
                context.shortToast(translation["failed_to_open_file"])
            }
        }

        val isActive = pendingTask != null && !taskStatus.isFinalStage()
        val cardModifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (taskSelection.isNotEmpty()) {
                            toggleSelection()
                            return@detectTapGestures
                        }
                        openFile()
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (taskSelection.isNotEmpty()) {
                            openFile()
                            return@detectTapGestures
                        }
                        toggleSelection()
                    }
                )
            }
            .let {
                if (isSelected) {
                    it
                        .border(2.dp, PurrfectPalette.glowSecondary, MaterialTheme.shapes.large)
                        .clip(MaterialTheme.shapes.large)
                } else it
            }

        val chipLabel = when {
            isActive -> translation.getOrNull("task_sending") ?: "Sending"
            taskStatus == TaskStatus.SUCCESS -> null
            taskStatus == TaskStatus.FAILURE -> translation.getOrNull("task_failed") ?: "Failed"
            taskStatus == TaskStatus.CANCELLED -> translation.getOrNull("task_cancelled") ?: "Cancelled"
            else -> taskStatus.name.lowercase().replaceFirstChar { it.titlecase() }
        }
        val chipIcon = when {
            isActive -> null
            taskStatus == TaskStatus.SUCCESS -> Icons.Filled.Check
            taskStatus == TaskStatus.FAILURE -> Icons.Filled.WarningAmber
            taskStatus == TaskStatus.CANCELLED -> Icons.Filled.Cancel
            else -> Icons.Filled.Info
        }
        val chipColors = when {
            isActive -> AssistChipDefaults.assistChipColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                labelColor = Color.White
            )
            taskStatus == TaskStatus.SUCCESS -> AssistChipDefaults.assistChipColors()
            taskStatus == TaskStatus.FAILURE -> AssistChipDefaults.assistChipColors(
                containerColor = Color(0xFFFF6B9B).copy(alpha = 0.18f),
                labelColor = Color.White
            )
            taskStatus == TaskStatus.CANCELLED -> AssistChipDefaults.assistChipColors(
                containerColor = Color.White.copy(alpha = 0.06f),
                labelColor = PurrfectPalette.textSecondary
            )
            else -> AssistChipDefaults.assistChipColors()
        }

        Surface(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            PurrfectPalette.glowPrimary.copy(alpha = 0.22f),
                                            PurrfectPalette.glowSecondary.copy(alpha = 0.18f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            documentFile?.let { doc ->
                                if (documentFileMimeType.contains("image")) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            ImageRequest.Builder(LocalContext.current)
                                                .data(doc.uri)
                                                .size(120)
                                                .crossfade(true)
                                                .build()
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = when {
                                            !isDocumentFileReadable -> Icons.Filled.DeleteOutline
                                            documentFileMimeType.contains("video") -> Icons.Filled.Videocam
                                            documentFileMimeType.contains("audio") -> Icons.Filled.MusicNote
                                            else -> Icons.Filled.FileCopy
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } ?: run {
                                Icon(
                                    imageVector = when (task.type) {
                                        TaskType.DOWNLOAD -> Icons.Filled.Download
                                        TaskType.CHAT_ACTION -> Icons.Filled.ChatBubble
                                        TaskType.SCHEDULED_SEND -> Icons.Filled.Schedule
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        task.author?.takeIf { it != "null" }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = PurrfectPalette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isActive) {
                            taskProgressLabel?.let {
                                val labelText = if (task.isAutoOpen) {
                                    // Live Metrics: Show Snaps/min and session total
                                    val sessionTimeMins = (System.currentTimeMillis() - 0L) / 60000.0 // Placeholder for session start
                                    val speed = if (sessionTimeMins > 0.1) String.format("%.1f", 0 / sessionTimeMins) else "0.0"
                                    "$it • $speed snaps/min"
                                } else it
                                Text(labelText, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            if (taskProgress != -1) {
                                LinearProgressIndicator(
                                    progress = { taskProgress.toFloat() / 100f },
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = PurrfectPalette.glowSecondary,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }
                        } else {
                            chipLabel?.let { label ->
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    leadingIcon = chipIcon?.let { { Icon(it, null, modifier = Modifier.size(14.dp)) } },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = chipColors,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    if (isActive) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pendingTask?.cancel()
                        }) {
                            Icon(Icons.Filled.Close, null, tint = Color(0xFFFF6B9B))
                        }
                    } else if (taskStatus == TaskStatus.SUCCESS) {
                        Icon(Icons.Filled.Check, null, tint = PurrfectPalette.glowSecondary)
                    }
                }
            }
        }
    }

    @Composable
    internal fun TaskTabSwitcher(
        selectedTab: TaskTab,
        onTabSelected: (TaskTab) -> Unit,
        activeCount: Int,
        scheduledCount: Int
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val count = if (tab == TaskTab.ACTIVE) activeCount else scheduledCount
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f),
                    border = if (selected) BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))) else BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tab == TaskTab.ACTIVE) Icons.Filled.Timer else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (tab == TaskTab.ACTIVE) (context.translation["tasks_tab_active"] ?: "Active") else (context.translation["tasks_tab_scheduled"] ?: "Scheduled"),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun TasksHeader(
        selectedTab: TaskTab,
        onTabSelected: (TaskTab) -> Unit,
        activeCount: Int,
        scheduledCount: Int,
        runningCount: Int,
        subtitle: String,
        onClear: () -> Unit,
        onMerge: () -> Unit,
        canMerge: Boolean
    ) {
        val haptic = LocalHapticFeedback.current
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = context.translation["manager.routes.tasks"],
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = subtitle,
                            color = PurrfectPalette.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canMerge) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMerge()
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = PurrfectPalette.glowPrimary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, PurrfectPalette.glowPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Merge, contentDescription = context.translation["tasks_merge_button"], tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(context.translation["tasks_merge_button"] ?: "Merge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                                Icon(
                                    imageVector = Icons.Filled.PlaylistAddCheckCircle,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = (context.translation["tasks_running_count"] ?: "{count} running")
                                        .replace("{count}", runningCount.toString()),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = context.translation["tasks_clear_button_description"],
                                tint = Color.White
                            )
                        }
                    }
                }
                
                TaskTabSwitcher(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    activeCount = activeCount,
                    scheduledCount = scheduledCount
                )
            }
        }
    }
}
