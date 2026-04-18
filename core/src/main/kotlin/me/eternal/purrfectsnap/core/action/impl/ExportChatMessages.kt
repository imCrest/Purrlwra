package cock.crest.purrfectsnap.lite.core.action.impl

import android.app.AlertDialog
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import kotlinx.coroutines.*
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.database.impl.FriendFeedEntry
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LoggedMessage
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LoggerWrapper
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.core.action.AbstractAction
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.logger.CoreLogger
import cock.crest.purrfectsnap.lite.core.messaging.ConversationExporter
import cock.crest.purrfectsnap.lite.core.messaging.ExportFormat
import cock.crest.purrfectsnap.lite.core.messaging.ExportParams
import cock.crest.purrfectsnap.lite.core.messaging.ExportSortOrder
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import java.io.File
import kotlin.math.absoluteValue

private data class ExportColorParticipant(
    val userId: String,
    val displayName: String,
    val username: String
)

class ExportChatMessages : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("chat_export") }
    private val dialogLogs = mutableListOf<String>()
    private var dialogTitle by mutableStateOf("")
    private var dialogText by mutableStateOf("")
    private var currentActionDialog: AlertDialog? = null
    private val dialogBackground = Brush.verticalGradient(
        listOf(
            Color(0xFF1D1538),
            Color(0xFF130F2A)
        )
    )
    private val panelOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF2C2551),
            Color(0xFF1B1636)
        )
    )
    private val accentGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF8C7BFF),
            Color(0xFF5FD8FF)
        )
    )

    private data class ExportTarget(
        val outputFile: File,
        val finalize: (File) -> String
    )

    private fun resolveExportTarget(fileName: String, mimeType: String): ExportTarget {
        val configuredFolder = context.config.downloader.saveFolder.get()?.trim().orEmpty()
        val defaultTarget = {
            val publicFolder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PurrfectSnap"
            ).also { if (!it.exists()) it.mkdirs() }
            val outputFile = publicFolder.resolve(fileName).also { if (it.exists()) it.delete() }
            ExportTarget(outputFile) { file -> file.absolutePath }
        }

        if (configuredFolder.isBlank()) {
            return defaultTarget()
        }

        val outputFolder = runCatching {
            DocumentFile.fromTreeUri(context.androidContext, Uri.parse(configuredFolder))
        }.getOrNull()

        if (outputFolder == null || !outputFolder.canWrite()) {
            return defaultTarget()
        }

        val tempFile = File(context.androidContext.cacheDir, fileName).also {
            if (it.exists()) it.delete()
        }
        return ExportTarget(tempFile) { file ->
            val outputFile = outputFolder.createFile(mimeType, fileName)
                ?: throw IllegalStateException("Failed to create export file")
            context.androidContext.contentResolver.openOutputStream(outputFile.uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Failed to write export file")
            outputFile.uri.toString()
        }
    }

    private fun logDialog(message: String) {
        context.runOnUiThread {
            if (dialogLogs.size > 10) dialogLogs.removeAt(0)
            dialogLogs.add(message)
            context.log.debug("dialog: $message", "ExportChatMessages")
            dialogText = dialogLogs.joinToString("\n")
        }
    }

    private fun setStatus(message: String) {
        context.runOnUiThread {
            dialogTitle = message
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun ExporterDialog(
        getDialog: () -> AlertDialog? = { null }
    ) {
        fun t(key: String) = translation["exporter_dialog.$key"]

        var feedEntries by remember { mutableStateOf(emptyList<FriendFeedEntry>()) }
        var exportType by remember { mutableStateOf(ExportFormat.HTML) }
        val selectedFeedEntries = remember { mutableStateListOf<FriendFeedEntry>() }
        val messageTypeFilter = remember { mutableStateListOf<ContentType>() }
        var amountOfMessages by remember { mutableIntStateOf(-1) }
        var downloadMedias by remember { mutableStateOf(false) }
        var showConversationPicker by remember { mutableStateOf(false) }
        var showFormatPicker by remember { mutableStateOf(false) }
        var showOrderPicker by remember { mutableStateOf(false) }
        var showMessageTypePicker by remember { mutableStateOf(false) }
        var exportSortOrder by remember { mutableStateOf(ExportSortOrder.NEWEST_TO_OLDEST) }
        val colorOverrides = remember { mutableStateMapOf<String, String>() }
        var colorPickerTarget by remember { mutableStateOf<ExportColorParticipant?>(null) }
        var colorPickerValue by remember { mutableStateOf<Color?>(null) }
        var participants by remember { mutableStateOf<List<ExportColorParticipant>>(emptyList()) }
        val allFriends by rememberAsyncMutableState(null) { context.database.getAllFriends().associateBy { it.userId!! } }
        val myUserId = context.database.myUserId
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val accent = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(dialogBackground)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-40).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8C7BFF).copy(alpha = 0.36f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-60).dp, y = 30.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF5FD8FF).copy(alpha = 0.32f), Color.Transparent)
                            )
                        )
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .border(1.2.dp, accentGradient, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
                color = Color.White.copy(alpha = 0.04f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panelOverlay)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(28.dp),
                                contentDescription = null
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Export conversations",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                text = t("select_conversations_title"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFD9D3FF),
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Text(
                            text = exportType.extension.uppercase(),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    SectionLabel(t("select_conversations_title"))
                    GlassField(
                        value = selectedFeedEntries
                            .takeIf { it.isNotEmpty() }
                            ?.let { translation.format("exporter_dialog.text_field_selection", "amount" to it.size.toString()) }
                            ?: t("text_field_selection_all"),
                        onClick = { showConversationPicker = true },
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )

                    SectionLabel(t("export_file_format_title"))
                    GlassField(
                        value = exportType.extension.uppercase(),
                        onClick = { showFormatPicker = true },
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )

                    SectionLabel(t("sort_order_title"))
                    GlassField(
                        value = t(
                            if (exportSortOrder == ExportSortOrder.OLDEST_TO_NEWEST) {
                                "sort_order_oldest_to_newest"
                            } else {
                                "sort_order_newest_to_oldest"
                            }
                        ),
                        onClick = { showOrderPicker = true },
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )

                    SectionLabel(t("message_type_filter_title"))
                    GlassField(
                        value = messageTypeFilter.takeIf { it.isNotEmpty() }?.let {
                            translation.format("exporter_dialog.text_field_selection", "amount" to it.size.toString())
                        } ?: t("text_field_selection_all"),
                        onClick = { showMessageTypePicker = true },
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )

                    SectionLabel(t("amount_of_messages_title"))
                    GlassTextField(
                        value = amountOfMessages.takeIf { it != -1 }?.toString() ?: "",
                        onValueChange = { amountOfMessages = it.toIntOrNull()?.absoluteValue ?: -1 },
                        placeholder = "Unlimited",
                        onImeDone = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )

                    SectionLabel(t("download_medias_title"))
                    NeonToggle(
                        checked = downloadMedias,
                        onCheckedChange = { downloadMedias = it },
                        label = if (downloadMedias) translation["exporter_dialog.download_medias_title"] else translation["exporter_dialog.download_medias_title"],
                        accent = accent
                    )

                    SectionLabel("Participant colors")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 260.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (participants.isEmpty()) {
                                BasicText(
                                    text = "Select conversations to customize participant colors.",
                                    style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 12.sp)
                                )
                            } else {
                                participants.forEach { participant ->
                                    val colorHex = colorOverrides[participant.userId]
                                    val color = colorHex?.let { Color(AndroidColor.parseColor(it)) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                colorPickerTarget = participant
                                                colorPickerValue = color
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ColorSwatch(color = color)
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = participant.displayName,
                                                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            BasicText(
                                                text = participant.username,
                                                style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 12.sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicText(
                                            text = colorHex ?: "Auto",
                                            style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 11.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    SecondaryButton(
                        text = translation["dialog_negative_button"],
                        modifier = Modifier.weight(1f),
                        onClick = { getDialog()?.dismiss() }
                    )
                    PrimaryButton(
                        text = translation["dialog_positive_button"],
                        modifier = Modifier.weight(1f),
                        enabled = selectedFeedEntries.isNotEmpty() || feedEntries.isNotEmpty(),
                        onClick = {
                            val selection = if (selectedFeedEntries.isEmpty()) feedEntries else selectedFeedEntries
                            exportChatForConversations(
                                selection,
                                ExportParams(
                                    exportFormat = exportType,
                                    sortOrder = exportSortOrder,
                                    messageTypeFilter = messageTypeFilter.takeIf { it.isNotEmpty() },
                                    amountOfMessages = amountOfMessages.takeIf { it != -1 },
                                    downloadMedias = downloadMedias,
                                    colorOverrides = colorOverrides.takeIf { it.isNotEmpty() }?.toMap()
                                )
                            )
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        feedEntries = context.database.getFeedEntries(500)
                        }
                    }
                }
            }

            LaunchedEffect(selectedFeedEntries.toList(), allFriends) {
                withContext(Dispatchers.IO) {
                    val selection = selectedFeedEntries.toList()
                    if (selection.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            participants = emptyList()
                        }
                        return@withContext
                    }
                    val participantsMap = linkedMapOf<String, ExportColorParticipant>()
                    selection.forEach { entry ->
                        val userIds = context.database.getConversationParticipants(entry.key!!, useCache = false) ?: emptyList()
                        userIds.forEach { userId ->
                            if (participantsMap.containsKey(userId)) return@forEach
                            val friend = allFriends?.get(userId) ?: context.database.getFriendInfo(userId)
                            val displayName = friend?.displayName ?: friend?.mutableUsername ?: userId
                            val username = friend?.mutableUsername ?: userId
                            participantsMap[userId] = ExportColorParticipant(userId, displayName, username)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        participants = participantsMap.values.toList()
                    }
                }
            }

            colorPickerTarget?.let { target ->
                Dialog(
                    onDismissRequest = { colorPickerTarget = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    ExportColorPickerDialog(
                        participant = target,
                        initialColor = colorPickerValue,
                        onSave = { color ->
                            if (color == null) {
                                colorOverrides.remove(target.userId)
                            } else {
                                colorOverrides[target.userId] = colorToHex(color)
                            }
                            colorPickerTarget = null
                        },
                        onClear = {
                            colorOverrides.remove(target.userId)
                            colorPickerTarget = null
                        }
                    )
                }
            }

            if (showConversationPicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showConversationPicker = false }
                )
                SelectorPopup(
                    title = t("select_conversations_title"),
                    onDismiss = { showConversationPicker = false }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp * 0.65f))
                    ) {
                        items(feedEntries, key = { it.key!! }) { feedEntry ->
                            val isSelected = selectedFeedEntries.contains(feedEntry)
                            SelectorRow(
                                title = remember(feedEntry) {
                                    (if (feedEntry.conversationType == 1) feedEntry.feedDisplayName else feedEntry.participants?.filter { it != myUserId }?.firstOrNull()?.let { userId ->
                                        allFriends?.get(userId)?.let { friend -> friend.displayName?.let { "$it (${friend.mutableUsername})" } ?: friend.mutableUsername }
                                    }) ?: "Unknown"
                                },
                                subtitle = feedEntry.takeIf { it.conversationType == 1 }?.let {
                                    "${feedEntry.participantsSize} participants"
                                },
                                checked = isSelected,
                                onToggle = {
                                    if (isSelected) selectedFeedEntries -= feedEntry else selectedFeedEntries += feedEntry
                                }
                            )
                        }

                        if (feedEntries.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SecondaryButton(
                                        text = t("text_field_selection_all"),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            selectedFeedEntries.clear()
                                            selectedFeedEntries.addAll(feedEntries)
                                        }
                                    )
                                    SecondaryButton(
                                        text = translation["dialog_negative_button"],
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedFeedEntries.clear() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showFormatPicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showFormatPicker = false }
                )
                SelectorPopup(
                    title = t("export_file_format_title"),
                    onDismiss = { showFormatPicker = false }
                ) {
                    ExportFormat.entries.forEach { exportFormat ->
                        SelectorRow(
                            title = exportFormat.name,
                            subtitle = exportFormat.extension.uppercase(),
                            checked = exportType == exportFormat,
                            onToggle = {
                                exportType = exportFormat
                                showFormatPicker = false
                            }
                        )
                    }
                }
            }

            if (showMessageTypePicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showMessageTypePicker = false }
                )
                SelectorPopup(
                    title = t("message_type_filter_title"),
                    onDismiss = { showMessageTypePicker = false }
                ) {
                    arrayOf(
                        ContentType.CHAT,
                        ContentType.SNAP,
                        ContentType.EXTERNAL_MEDIA,
                        ContentType.NOTE,
                        ContentType.STICKER
                    ).forEach { contentType ->
                        val isSelected = messageTypeFilter.contains(contentType)
                        SelectorRow(
                            title = contentType.name.lowercase().replaceFirstChar { it.uppercase() },
                            subtitle = null,
                            checked = isSelected,
                            onToggle = {
                                if (isSelected) messageTypeFilter -= contentType else messageTypeFilter += contentType
                            }
                        )
                    }
                    SecondaryButton(
                        text = t("text_field_selection_all"),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            messageTypeFilter.clear()
                            showMessageTypePicker = false
                        }
                    )
                }
            }

            if (showOrderPicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showOrderPicker = false }
                )
                SelectorPopup(
                    title = t("sort_order_title"),
                    onDismiss = { showOrderPicker = false }
                ) {
                    SelectorRow(
                        title = t("sort_order_newest_to_oldest"),
                        subtitle = null,
                        checked = exportSortOrder == ExportSortOrder.NEWEST_TO_OLDEST,
                        onToggle = {
                            exportSortOrder = ExportSortOrder.NEWEST_TO_OLDEST
                            showOrderPicker = false
                        }
                    )
                    SelectorRow(
                        title = t("sort_order_oldest_to_newest"),
                        subtitle = null,
                        checked = exportSortOrder == ExportSortOrder.OLDEST_TO_NEWEST,
                        onToggle = {
                            exportSortOrder = ExportSortOrder.OLDEST_TO_NEWEST
                            showOrderPicker = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        BasicText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontSize = 13.sp,
                color = Color(0xFFD9D3FF),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Start
            )
        )
    }

    @Composable
    private fun GlassField(
        value: String,
        onClick: () -> Unit,
        tint: Color
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(tint.copy(alpha = 0.18f))
                .border(1.dp, accentGradient, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            BasicText(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Start, fontWeight = FontWeight.Medium)
            )
        }
    }

    @Composable
    private fun GlassTextField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        onImeDone: () -> Unit,
        tint: Color
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color(0xFF8EF0F3)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(tint.copy(alpha = 0.16f))
                        .border(1.dp, accentGradient, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    if (value.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 14.sp)
                        )
                    }
                    inner()
                }
            }
        )
    }

    @Composable
    private fun NeonToggle(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String,
        accent: Color
    ) {
        val knobOffset by animateFloatAsState(targetValue = if (checked) 20f else 0f, animationSpec = tween(220), label = "toggle")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, accentGradient, RoundedCornerShape(14.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 26.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color(0xFF10192B))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(40.dp))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = knobOffset.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(accent, Color(0xFF6C7CFF))))
                )
            }
            Column {
                BasicText(
                    text = label,
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                )
                BasicText(
                    text = if (checked) "Media files included" else "Only text content",
                    style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 12.sp)
                )
            }
        }
    }

    @Composable
    private fun PrimaryButton(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .alpha(if (enabled) 1f else 0.6f)
                .background(accentGradient)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = TextStyle(color = Color(0xFF0A0F1D), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
        }
    }

    @Composable
    private fun SecondaryButton(
        text: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }

    @Composable
    private fun SelectorPopup(
        title: String,
        onDismiss: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(focusable = true, dismissOnClickOutside = false, dismissOnBackPress = true),
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(panelOverlay)
                    .border(1.2.dp, accentGradient, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicText(
                        text = title,
                        style = TextStyle(color = Color(0xFFE6ECFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    androidx.compose.foundation.Image(
                        painter = rememberVectorPainter(Icons.Outlined.Close),
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onDismiss)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    )
                }
                content()
            }
        }
    }

    @Composable
    private fun SelectorRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onToggle: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, accentGradient, RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCheckbox(checked = checked, accent = Color(0xFF8EF0F3))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BasicText(
                    text = title,
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    BasicText(
                        text = it,
                        style = TextStyle(color = Color(0xFFB1B4D7), fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun GlassCheckbox(checked: Boolean, accent: Color) {
        val animatedAlpha by animateFloatAsState(targetValue = if (checked) 1f else 0f, animationSpec = tween(200), label = "checkbox")
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(visible = checked) {
                androidx.compose.foundation.Image(
                    painter = rememberVectorPainter(Icons.Rounded.Check),
                    contentDescription = "Checked",
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.copy(alpha = animatedAlpha))
                        .padding(2.dp)
                )
            }
        }
    }

    @Composable
    private fun ExportProgressDialog(
        onCancel: () -> Unit
    ) {
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(dialogBackground)
                .padding(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.2.dp, accentGradient, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 0.dp,
                color = Color.White.copy(alpha = 0.04f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panelOverlay)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                            .verticalScroll(scrollState)
                            .padding(12.dp)
                    ) {
                        BasicText(
                            text = dialogText,
                            style = TextStyle(color = Color(0xFFD9D3FF), fontSize = 12.sp)
                        )
                    }
                    SecondaryButton(
                        text = translation["dialog_negative_button"],
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCancel
                    )
                }
            }
        }
    }

    @Composable
    private fun ColorSwatch(color: Color?) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color ?: Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (color == null) {
                BasicText(
                    text = "A",
                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }

    private fun colorToHex(color: Color): String {
        return String.format("#%06X", 0xFFFFFF and color.toArgb())
    }

    @Composable
    private fun ExportColorPickerDialog(
        participant: ExportColorParticipant,
        initialColor: Color?,
        onSave: (Color?) -> Unit,
        onClear: () -> Unit
    ) {
        var currentColor by remember { mutableStateOf(initialColor ?: Color.White) }
        val controller = remember { ColorPickerController().apply { selectByColor(currentColor, false) } }
        var colorHexValue by remember { mutableStateOf(colorToHex(currentColor).removePrefix("#")) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF8C7BFF).copy(alpha = 0.6f),
                        Color(0xFF5FD8FF).copy(alpha = 0.5f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1636))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Color for ${participant.displayName}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                TextField(
                    value = colorHexValue,
                    onValueChange = { value ->
                        colorHexValue = value
                        runCatching {
                            val parsed = Color(AndroidColor.parseColor("#$value"))
                            currentColor = parsed
                            controller.selectByColor(parsed, true)
                        }
                    },
                    label = { Text(text = "Hex Color") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF8EF0F3),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    controller = controller,
                    onColorChanged = {
                        if (!it.fromUser) return@HsvColorPicker
                        currentColor = it.color
                        colorHexValue = colorToHex(it.color).removePrefix("#")
                    }
                )
                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    controller = controller
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Auto",
                        modifier = Modifier.weight(1f),
                        onClick = onClear
                    )
                    PrimaryButton(
                        text = "Save",
                        modifier = Modifier.weight(1f),
                        onClick = { onSave(currentColor) }
                    )
                }
            }
        }
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
                ExporterDialog { alertDialog }
            }.apply {
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }

    private suspend fun fetchMessagesPaginated(conversationId: String, lastMessageId: Long, amount: Int): List<Message> = runBlocking {
        for (i in 0..5) {
            val messages: List<Message>? = suspendCancellableCoroutine { continuation ->
                context.feature(Messaging::class).conversationManager?.fetchConversationWithMessagesPaginated(conversationId,
                    lastMessageId,
                    amount, onSuccess = { messages ->
                        continuation.resumeWith(Result.success(messages))
                    }, onError = {
                        continuation.resumeWith(Result.success(null))
                    }) ?: continuation.resumeWith(Result.success(null))
            }
            if (messages != null) return@runBlocking messages
            logDialog("Retrying in 1 second...")
            delay(1000)
        }
        logDialog("Failed to fetch messages")
        emptyList()
    }

    private fun exportChatForConversations(
        conversations: List<FriendFeedEntry>,
        exportParams: ExportParams,
    ) {
        dialogLogs.clear()
        val jobs = mutableListOf<Job>()
        dialogTitle = translation["exporting_chats"]
        dialogText = ""

        val conversationSize = translation.format("processing_chats", "amount" to conversations.size.toString())
        
        logDialog(conversationSize)

        val exportJob = context.coroutineScope.launch {
            conversations.forEach { conversation ->
                launch {
                    runCatching {
                        exportFullConversation(conversation, exportParams)
                    }.onFailure {
                        logDialog(translation.format("export_fail", "conversation" to conversation.key.toString()))
                        logDialog(it.stackTraceToString())
                        CoreLogger.xposedLog(it)
                    }
                }.also { jobs.add(it) }
            }
            jobs.joinAll()
            logDialog(translation["finished"])
        }

        currentActionDialog = createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
            ExportProgressDialog {
                exportJob.cancel()
                jobs.forEach { it.cancel() }
                alertDialog.dismiss()
            }
        }.apply {
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    private fun fetchLoggerMessages(conversationId: String): List<LoggedMessage> {
        return runCatching {
            val loggerWrapper = LoggerWrapper(context.androidContext)
            val messages = mutableListOf<LoggedMessage>()
            var fromTimestamp = Long.MAX_VALUE
            while (true) {
                val batch = loggerWrapper.fetchMessages(conversationId, fromTimestamp, 500, reverseOrder = true)
                if (batch.isEmpty()) break
                messages.addAll(batch)
                fromTimestamp = batch.last().sendTimestamp
            }
            messages
        }.getOrDefault(emptyList())
    }

    private fun messageSortKey(message: Message): Long {
        return message.orderKey
            ?: message.messageMetadata?.createdAt
            ?: message.messageDescriptor?.messageId
            ?: Long.MIN_VALUE
    }

    private suspend fun exportFullConversation(
        feedEntry: FriendFeedEntry,
        exportParams: ExportParams,
    ) {
        //first fetch the first message
        val conversationId = feedEntry.key!!
        val conversationParticipants = context.database.getConversationParticipants(feedEntry.key!!, useCache = false)
            ?.mapNotNull {
                context.database.getFriendInfo(it)
            }?.associateBy { it.userId!! } ?: emptyMap()

        val loggerMessages = fetchLoggerMessages(conversationId)
        val participantMap = conversationParticipants.toMutableMap().apply {
            loggerMessages.forEach { message ->
                if (containsKey(message.userId)) return@forEach
                this[message.userId] = FriendInfo(
                    userId = message.userId,
                    displayName = message.username,
                    username = message.username,
                    usernameForSorting = message.username
                )
            }
        }

        val conversationName = feedEntry.feedDisplayName ?: conversationParticipants.values.take(3).joinToString("_") { it.mutableUsername ?: "" }

        val outputName = "conversation_${conversationName}_${System.currentTimeMillis()}.${exportParams.exportFormat.extension}"
        val mimeType = when (exportParams.exportFormat) {
            ExportFormat.JSON -> "application/json"
            ExportFormat.TEXT -> "text/plain"
            ExportFormat.HTML -> "text/html"
        }
        val outputTarget = resolveExportTarget(outputName, mimeType)
        val outputFile = outputTarget.outputFile

        logDialog(translation.format("exporting_message", "conversation" to conversationName))

        val conversationExporter = ConversationExporter(
            context = context,
            friendFeedEntry = feedEntry,
            conversationParticipants = participantMap,
            exportParams = exportParams,
            cacheFolder = context.androidContext.cacheDir.resolve("chat_export").also { if (!it.exists()) it.mkdirs() },
            outputFile = outputFile,
        ).apply { init(); printLog = {
            logDialog(it.toString())
        } }

        var foundMessageCount = 0
        val exportedOrderKeys = mutableSetOf<Long>()
        val fetchedMessages = mutableListOf<Message>()
        val seenMessageKeys = mutableSetOf<String>()

        var lastMessageId: Long? = null
        fetchMessagesPaginated(conversationId, Long.MAX_VALUE, amount = 1).firstOrNull()?.also { message ->
            val messageKey = message.orderKey?.toString() ?: message.messageDescriptor?.messageId?.toString()
            if (messageKey != null && seenMessageKeys.add(messageKey)) {
                fetchedMessages.add(message)
            }
            lastMessageId = message.messageDescriptor?.messageId
        }

        if (lastMessageId == null && fetchedMessages.isEmpty()) {
            logDialog(translation["no_messages_found"])
        }

        while (lastMessageId != null) {
            val pagedMessages = fetchMessagesPaginated(conversationId, lastMessageId, amount = 500)
            if (pagedMessages.isEmpty()) break

            pagedMessages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor!!.messageId!!
            }

            pagedMessages.forEach { message ->
                val messageKey = message.orderKey?.toString() ?: message.messageDescriptor?.messageId?.toString()
                if (messageKey != null && seenMessageKeys.add(messageKey)) {
                    fetchedMessages.add(message)
                }
            }
        }

        val filteredMessages = exportParams.messageTypeFilter?.let { filter ->
            fetchedMessages.filter { message ->
                val contentType = message.messageContent?.contentType ?: return@filter false
                filter.contains(contentType)
            }
        } ?: fetchedMessages

        val sortedMessages = when (exportParams.sortOrder) {
            ExportSortOrder.OLDEST_TO_NEWEST -> filteredMessages.sortedBy { messageSortKey(it) }
            ExportSortOrder.NEWEST_TO_OLDEST -> filteredMessages.sortedByDescending { messageSortKey(it) }
        }

        val messagesToWrite = exportParams.amountOfMessages?.let { limit ->
            sortedMessages.take(limit)
        } ?: sortedMessages

        messagesToWrite.forEach { message ->
            conversationExporter.readMessage(message)
            foundMessageCount++
            message.orderKey?.let { exportedOrderKeys.add(it) }
            setStatus("Exporting (found ${foundMessageCount})")
        }

        if (loggerMessages.isNotEmpty() && (exportParams.amountOfMessages == null || foundMessageCount < exportParams.amountOfMessages)) {
            val parsedLoggerMessages = loggerMessages.mapNotNull { conversationExporter.parseLoggedMessage(it) }
            val sortedLoggerMessages = when (exportParams.sortOrder) {
                ExportSortOrder.OLDEST_TO_NEWEST -> parsedLoggerMessages.sortedBy { it.orderKey }
                ExportSortOrder.NEWEST_TO_OLDEST -> parsedLoggerMessages.sortedByDescending { it.orderKey }
            }
            for (loggedMessage in sortedLoggerMessages) {
                if (exportedOrderKeys.contains(loggedMessage.orderKey)) continue
                val filter = exportParams.messageTypeFilter
                if (filter != null && !filter.contains(loggedMessage.contentType)) continue
                if (exportParams.amountOfMessages != null && foundMessageCount >= exportParams.amountOfMessages) break
                conversationExporter.readLoggedMessage(loggedMessage)
                foundMessageCount++
                setStatus("Exporting (found ${foundMessageCount})")
            }
        }

        if (exportParams.exportFormat == ExportFormat.HTML) conversationExporter.awaitDownload()
        conversationExporter.close()
        logDialog(translation["writing_output"])
        dialogLogs.clear()
        val exportedPath = runCatching { outputTarget.finalize(outputFile) }.getOrElse { error ->
            logDialog("Failed to write export output")
            logDialog(error.toString())
            context.log.error("Failed to finalize chat export", error)
            return
        }
        if (outputFile.parentFile == context.androidContext.cacheDir) {
            outputFile.delete()
        }
        logDialog("\n" + translation.format("exported_to",
            "path" to exportedPath
        ) + "\n")
    }
}
