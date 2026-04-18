package cock.crest.purrfectsnap.lite.ui.manager.pages.tracker

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.PopupProperties
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.TrackerLog
import cock.crest.purrfectsnap.lite.common.data.MessagingFriendInfo
import cock.crest.purrfectsnap.lite.common.data.TrackerEventType
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.storage.getFriendInfo
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.coil.BitmojiImage
import cock.crest.purrfectsnap.lite.ui.util.saveFile
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import java.text.DateFormat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    context: RemoteSideContext,
    activityLauncherHelper: ActivityLauncherHelper,
    deleteAction: (() -> Unit) -> Unit,
    exportAction: (() -> Unit) -> Unit,
    bottomPadding: Dp,
) {
    val translation = remember { context.translation.getCategory("manager.friend_tracker") }
    val trackerTranslation = remember { context.translation.getCategory("tracker") }
    val coroutineScope = rememberCoroutineScope()

    val logs = remember { mutableStateListOf<TrackerLog>() }
    var isLoading by remember { mutableStateOf(false) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var filterType by remember { mutableStateOf(FriendTrackerManagerRoot.FilterType.USERNAME) }
    var reverseSortOrder by remember { mutableStateOf(true) }
    val sinceDatePickerState = rememberDatePickerState(
        initialDisplayMode = DisplayMode.Picker
    )

    var filter by remember { mutableStateOf("") }
    var searchTimeoutJob by remember { mutableStateOf<Job?>(null) }

    fun getPaginatedLogs(pageIndex: Int) = context.messageLogger.getLogs(
        pageIndex = pageIndex,
        pageSize = 30,
        timestamp = sinceDatePickerState.selectedDateMillis,
        reverseOrder = reverseSortOrder,
        filter = {
        when (filterType) {
            FriendTrackerManagerRoot.FilterType.USERNAME -> it.username.contains(filter, ignoreCase = true)
            FriendTrackerManagerRoot.FilterType.CONVERSATION -> it.conversationTitle?.contains(filter, ignoreCase = true) == true || (it.username == filter && !it.isGroup)
            FriendTrackerManagerRoot.FilterType.EVENT -> it.eventType.contains(filter, ignoreCase = true)
        }
    })

    fun formatICanSeeYouDuration(value: Long?): String {
        if (value == null || value < 0) return trackerTranslation.get("logs.log_entry.i_can_see_you_not_available")
        val totalSeconds = (value / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val hourUnit = trackerTranslation.get("logs.log_entry.i_can_see_you_unit_hour")
        val minuteUnit = trackerTranslation.get("logs.log_entry.i_can_see_you_unit_minute")
        val secondUnit = trackerTranslation.get("logs.log_entry.i_can_see_you_unit_second")
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}${hourUnit}")
        if (minutes > 0 || hours > 0) parts.add("${minutes}${minuteUnit}")
        parts.add("${seconds}${secondUnit}")
        return parts.joinToString(" ")
    }

    fun formatICanSeeYouTime(value: Long?): String {
        if (value == null || value < 0) return trackerTranslation.get("logs.log_entry.i_can_see_you_not_available")
        return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(value))
    }

    fun buildICanSeeYouDetails(data: String): String {
        val values = data.split("|").map { it.toLongOrNull() ?: -1L }.let {
            if (it.size >= 3) it else it + List(3 - it.size) { -1L }
        }
        val entered = values[0].takeIf { it >= 0 }
        val exited = values[1].takeIf { it >= 0 }
        val duration = values[2].takeIf { it >= 0 }
        val enteredLabel = trackerTranslation.get("logs.log_entry.i_can_see_you_entered")
        val leftLabel = trackerTranslation.get("logs.log_entry.i_can_see_you_left")
        val durationLabel = trackerTranslation.get("logs.log_entry.i_can_see_you_duration")
        return listOf(
            "$enteredLabel: ${formatICanSeeYouTime(entered)}",
            "$durationLabel: ${formatICanSeeYouDuration(duration)}",
            "$leftLabel: ${formatICanSeeYouTime(exited)}"
        ).joinToString(" • ")
    }

    suspend fun loadNewLogs() {
        withContext(Dispatchers.IO) {
            getPaginatedLogs(pageIndex).let {
                withContext(Dispatchers.Main) {
                    logs.addAll(it)
                    pageIndex += 1
                }
            }
        }
    }

    suspend fun resetAndLoadLogs() {
        isLoading = true
        logs.clear()
        pageIndex = 0
        loadNewLogs()
        isLoading = false
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportSelectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        deleteAction { showDeleteDialog = true }
        exportAction { showExportSelectionDialog = true }
    }

    if (showDeleteDialog) {
        val deleteCoroutineScope = rememberCoroutineScope { Dispatchers.IO }
        var deleteLogsTask by remember { mutableStateOf<Job?>(null) }
        var deletedLogsCount by remember { mutableIntStateOf(0) }

        fun deleteLogs() {
            deleteLogsTask = deleteCoroutineScope.launch {
                var index = 0
                while (true) {
                    val newLogs = getPaginatedLogs(index++)
                    if (newLogs.isEmpty()) {
                        break
                    }
                    newLogs.forEach {
                        context.messageLogger.deleteTrackerLog(it.id)
                        deletedLogsCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    delay(500)
                    resetAndLoadLogs()
                    context.shortToast(translation.format("deleted_logs_toast", "count" to deletedLogsCount.toString()))
                    showDeleteDialog = false
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                deleteLogsTask?.cancel()
            }
        }

        AestheticDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = translation["delete_logs_dialog_title"],
            text = if (deleteLogsTask != null) translation.format("deleting_logs_dialog_text", "count" to deletedLogsCount.toString()) else translation["delete_logs_dialog_confirm_text"],
            icon = Icons.Default.DeleteOutline,
            confirmButtonText = translation["delete_button"],
            onConfirm = { if (deleteLogsTask == null) deleteLogs() },
            dismissButtonText = context.translation["button.cancel"],
            onDismiss = { showDeleteDialog = false },
            loading = deleteLogsTask != null,
            opaque = true,
            showCloseButton = false
        )
    }

    if (showExportSelectionDialog) {
        val exportCoroutineScope = rememberCoroutineScope { Dispatchers.IO }
        var exportTask by remember { mutableStateOf<Job?>(null) }
        var exportType by remember { mutableStateOf("json") }

        fun exportLogs() {
            activityLauncherHelper.saveFile("tracker_logs_${System.currentTimeMillis()}.$exportType") { uri ->
                exportTask = exportCoroutineScope.launch {
                    context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                        val writer = it.writer()
                        val jsonWriter by lazy {
                            JsonWriter(writer).apply {
                                setIndent("  ")
                                beginArray()
                            }
                        }

                        var index = 0
                        while (true) {
                            val newLogs = getPaginatedLogs(index++)
                            if (newLogs.isEmpty()) {
                                break
                            }
                            newLogs.forEach { log ->
                                when (exportType) {
                                    "json" -> {
                                        jsonWriter.jsonValue(log.toJson().toString())
                                    }
                                    "csv" -> {
                                        writer.write(log.toCsv())
                                        writer.write("\n")
                                    }
                                }
                                writer.flush()
                            }
                        }
                        when (exportType) {
                            "json" -> {
                                jsonWriter.endArray()
                                jsonWriter.close()
                            }
                            "csv" -> writer.close()
                        }
                    }
                }.apply {
                    invokeOnCompletion {
                        exportTask = null
                        showExportSelectionDialog = false
                        if (it == null) {
                            context.shortToast(translation["exported_logs_toast"])
                        } else {
                            context.log.error("Failed to export logs", it)
                            context.shortToast(translation["export_logs_failed_toast"])
                        }
                    }
                }
            }
        }

        AestheticDialog(
            onDismissRequest = { showExportSelectionDialog = false },
            title = translation["export_logs_dialog_title"],
            text = translation["export_logs_dialog_confirm_text"],
            icon = Icons.Default.SaveAlt,
            customContent = {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    Surface(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(translation.format("export_as_button", "type" to exportType.uppercase()), color = Color.White)
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("json", "csv").forEach { type ->
                            DropdownMenuItem(onClick = {
                                exportType = type
                                expanded = false
                            }, text = {
                                Text(type.uppercase(), color = Color.White)
                            })
                        }
                    }
                }
            },
            confirmButtonText = translation["export_button"],
            onConfirm = { if (exportTask == null) exportLogs() },
            dismissButtonText = context.translation["button.cancel"],
            onDismiss = {
                exportTask?.cancel()
                exportTask = null
                showExportSelectionDialog = false
            },
            loading = exportTask != null,
            opaque = true,
            showCloseButton = false
        )
    }


    @Composable
    fun FilterSelection(
        selectionExpanded: MutableState<Boolean>
    ) {
        var dropDownExpanded by remember { mutableStateOf(false) }
        var showDatePicker by remember { mutableStateOf(false) }

        LaunchedEffect(selectionExpanded.value) {
            if (!selectionExpanded.value) {
                dropDownExpanded = false
            }
        }

        LaunchedEffect(showDatePicker) {
            if (showDatePicker) {
                sinceDatePickerState.displayMode = DisplayMode.Picker
            }
        }

        if (showDatePicker) {
            AestheticDialog(
                onDismissRequest = { showDatePicker = false },
                title = "",
                text = "",
                icon = Icons.Default.DateRange,
                confirmButtonText = context.translation["button.ok"],
                onConfirm = { showDatePicker = false },
                dismissButtonText = context.translation["button.cancel"],
                onDismiss = {
                    showDatePicker = false
                    sinceDatePickerState.selectedDateMillis = null
                },
                customContent = {
                    Surface(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = PurrfectPalette.cardOverlayColor,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        DatePicker(
                            state = sinceDatePickerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 360.dp)
                                .padding(6.dp)
                                .background(PurrfectPalette.cardOverlayColor, RoundedCornerShape(14.dp)),
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = PurrfectPalette.cardOverlayColor,
                                titleContentColor = Color.White,
                                headlineContentColor = Color.White,
                                weekdayContentColor = Color.White.copy(alpha = 0.9f),
                                subheadContentColor = PurrfectPalette.textSecondary,
                                selectedDayContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                selectedDayContentColor = Color.Black,
                                todayContentColor = Color.White,
                                todayDateBorderColor = PurrfectPalette.glowSecondary,
                                dayContentColor = Color.White.copy(alpha = 0.85f),
                                disabledDayContentColor = Color.White.copy(alpha = 0.35f),
                                yearContentColor = Color.White,
                                currentYearContentColor = PurrfectPalette.glowSecondary,
                                selectedYearContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                selectedYearContentColor = Color.Black,
                                dividerColor = Color.White.copy(alpha = 0.14f),
                                navigationContentColor = Color.White,
                                dateTextFieldColors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.White.copy(alpha = 0.6f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = PurrfectPalette.glowSecondary,
                                    focusedLabelColor = PurrfectPalette.textSecondary,
                                    unfocusedLabelColor = PurrfectPalette.textSecondary
                                )
                            )
                        )
                    }
                },
                opaque = true,
                showCloseButton = false,
                showIcon = false,
                showTitle = false
            )
        }

        DropdownMenu(
            expanded = selectionExpanded.value,
            onDismissRequest = { selectionExpanded.value = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(PurrfectPalette.cardOverlay, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val rowHSpacing = 10.dp

                    Text(
                        translation["filters_title"],
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rowHSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(translation["search_by_label"], color = Color.White)
                        ExposedDropdownMenuBox(
                            expanded = dropDownExpanded,
                            onExpandedChange = { dropDownExpanded = it },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Surface(
                                onClick = { dropDownExpanded = true },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(filterType.name, color = Color.White)
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropDownExpanded)
                                }
                            }
                            ExposedDropdownMenu(
                                expanded = dropDownExpanded,
                                onDismissRequest = { dropDownExpanded = false },
                                containerColor = Color(0xFF101220),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                FriendTrackerManagerRoot.FilterType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        onClick = {
                                            filter = ""
                                            filterType = type
                                            dropDownExpanded = false
                                            coroutineScope.launch { resetAndLoadLogs() }
                                        },
                                        text = { Text(type.name, color = Color.White) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rowHSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(translation["reverse_order_checkbox"], color = Color.White)
                        Switch(
                            checked = reverseSortOrder,
                            onCheckedChange = {
                                reverseSortOrder = it
                            },
                            colors = purrfectSwitchColors()
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rowHSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(translation[if (reverseSortOrder) "since_label" else "until_label"], color = Color.White)
                        val dateLabel = remember(showDatePicker) {
                            sinceDatePickerState.selectedDateMillis?.let {
                                DateFormat.getDateInstance().format(it)
                            } ?: translation["pick_a_date_button"]
                        }
                        Surface(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                PurrfectPalette.glowPrimary.copy(alpha = 0.3f),
                                                PurrfectPalette.glowSecondary.copy(alpha = 0.26f)
                                            )
                                        ),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
                                Text(dateLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showAutoComplete by remember { mutableStateOf(false) }
            val showFilterSelection = remember { mutableStateOf(false) }
            val inputShape = RoundedCornerShape(18.dp)

            ExposedDropdownMenuBox(
                expanded = showAutoComplete,
                onExpandedChange = { showAutoComplete = it },
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = inputShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, inputShape)
                    ) {
                        IconButton(
                            onClick = { showFilterSelection.value = !showFilterSelection.value },
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(46.dp)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = translation["filter_button_description"], tint = Color.White)
                        }
                        FilterSelection(showFilterSelection)
                        if (showFilterSelection.value) {
                            DisposableEffect(Unit) {
                                onDispose {
                                    coroutineScope.launch { resetAndLoadLogs() }
                                }
                            }
                        }
                        TextField(
                            value = filter,
                            onValueChange = {
                                filter = it
                                coroutineScope.launch {
                                    searchTimeoutJob?.cancel()
                                    searchTimeoutJob = coroutineScope.launch {
                                        delay(200)
                                        showAutoComplete = true
                                        resetAndLoadLogs()
                                    }
                                }
                            },
                            placeholder = { Text(translation["search_placeholder"], color = PurrfectPalette.textSecondary) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            trailingIcon = {
                                if (filter.isNotEmpty()) {
                                    IconButton(onClick = {
                                        filter = ""
                                        coroutineScope.launch { resetAndLoadLogs() }
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = translation["clear_button_description"], tint = Color.White)
                                    }
                                }
                            }
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAutoComplete,
                    onDismissRequest = { showAutoComplete = false },
                    properties = PopupProperties(focusable = false),
                    containerColor = Color(0xFF161821),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 12.dp
                ) {
                    val suggestedEntries = remember(filter) { mutableStateListOf<String>() }

                    LaunchedEffect(filter) {
                        suggestedEntries.clear()
                        launch(Dispatchers.IO) {
                            suggestedEntries.addAll(
                                when (filterType) {
                                    FriendTrackerManagerRoot.FilterType.USERNAME -> context.messageLogger.findUsername(filter)
                                    FriendTrackerManagerRoot.FilterType.CONVERSATION -> context.messageLogger.findConversation(filter) + context.messageLogger.findUsername(filter)
                                    FriendTrackerManagerRoot.FilterType.EVENT -> TrackerEventType.entries.filter { it.name.contains(filter, ignoreCase = true) }.map { it.key }
                                }.take(5)
                            )
                        }
                    }

                    suggestedEntries.forEach { entry ->
                        DropdownMenuItem(
                            onClick = {
                                filter = entry
                                coroutineScope.launch { resetAndLoadLogs() }
                                showAutoComplete = false
                            },
                            text = { Text(entry, color = Color.White) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (logs.isEmpty() && !isLoading) {
                        Column(
                            modifier = Modifier.padding(top = 40.dp),
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
                                    Icon(Icons.Filled.History, contentDescription = translation["no_logs_found"], tint = Color.White)
                                }
                            }
                            Text(
                                translation["no_logs_found"],
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            items(logs, key = { it.userId + it.id }) { log ->
                var databaseFriend by remember { mutableStateOf<MessagingFriendInfo?>(null) }
                LaunchedEffect(Unit) {
                    launch(Dispatchers.IO) {
                        databaseFriend = context.database.getFriendInfo(log.userId)
                    }
                }
                val cardShape = RoundedCornerShape(18.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    shape = cardShape,
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, cardShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        BitmojiImage(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(58.dp),
                            size = 58,
                            context = context,
                            url = databaseFriend?.takeIf { it.bitmojiId != null }?.let {
                                BitmojiSelfie.getBitmojiSelfie(it.selfieId, it.bitmojiId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D)
                            },
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val eventLabel = trackerTranslation["logs.log_entry.events.${log.eventType}"]
                            val conversationLabel = log.conversationTitle ?: trackerTranslation["logs.log_entry.unknown_conversation"]
                            val eventTextTemplate = trackerTranslation["logs.log_entry.event_text"]
                            val eventText = eventTextTemplate
                                .replace("{friend}", databaseFriend?.displayName ?: log.username)
                                .replace("{event}", eventLabel)
                                .replace("{conversation}", conversationLabel)

                            Text(databaseFriend?.displayName?.let {
                                "$it (${log.username})"
                            } ?: log.username, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = Color.White)
                            Text(eventText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = PurrfectPalette.textSecondary)
                            if (log.eventType == TrackerEventType.I_CAN_SEE_YOU.key) {
                                Text(
                                    buildICanSeeYouDetails(log.data),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                            }
                            Text(
                                DateFormat.getDateTimeInstance().format(log.timestamp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 15.sp,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }

                        IconButton(
                            onClick = {
                                context.messageLogger.deleteTrackerLog(log.id)
                                logs.remove(log)
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"], tint = Color.White)
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))

                LaunchedEffect(pageIndex) {
                    loadNewLogs()
                }
            }

        }
    }
}
