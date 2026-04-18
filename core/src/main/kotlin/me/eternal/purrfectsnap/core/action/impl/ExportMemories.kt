package cock.crest.purrfectsnap.lite.core.action.impl

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OpenParams
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.*
import cock.crest.purrfectsnap.lite.common.data.FileType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.ktx.getLongOrNull
import cock.crest.purrfectsnap.lite.common.util.ktx.getStringOrNull
import cock.crest.purrfectsnap.lite.core.action.AbstractAction
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue

class ExportMemories : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("memories") }
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
    private val rangeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    data class TimeRange(
        val start: Long?,
        val end: Long?,
    )

    data class MemoriesEntry(
        val storyTitle: String,
        val createTime: Long,
        val mediaKey: String?,
        val mediaIv: String?,
        val downloadUrl: String
    ) {
        val folderName: String
            get() = storyTitle.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim().replace(Regex("\\s+"), "_")
    }

    private data class ExportTarget(
        val outputFile: File,
        val finalize: (File) -> String
    )

    private fun resolveExportTarget(fileName: String, mimeType: String): ExportTarget {
        val configuredFolder = context.config.downloader.saveFolder.get()?.trim().orEmpty()
        val defaultTarget = {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val outputDir = documentsDir.takeIf { it.exists() || it.mkdirs() }
                ?: context.androidContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.androidContext.filesDir
            val outputFile = File(outputDir, fileName).also {
                if (it.exists()) it.delete()
            }
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

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
    private suspend fun exportMemories(
        scope: CoroutineScope = context.coroutineScope,
        database: SQLiteDatabase,
        timeRange: TimeRange?,
        includeMEO: Boolean,
        folders: Boolean,
        progress: (Int, Int) -> Unit
    ) {
        val downloadContext = Dispatchers.IO.limitedParallelism(10)
        val writeToZipContext = Dispatchers.IO.limitedParallelism(1)
        val outputTarget = resolveExportTarget(
            "memories_${System.currentTimeMillis()}.zip",
            "application/zip"
        )
        val outputZip = outputTarget.outputFile
        val okHttpClient = OkHttpClient.Builder().build()
        val outputZipFile = withContext(Dispatchers.IO) {
            ZipOutputStream(FileOutputStream(outputZip)).apply {
                setComment("Exported from PurrfectSnap")
                setMethod(ZipOutputStream.DEFLATED)
            }
        }
        var totalCount = 0
        var currentCount = 0
        var failed = 0

        fun updateProgress() {
            progress((currentCount.toFloat() / totalCount.toFloat() * 100f).toInt(), failed)
        }

        val jobs = mutableListOf<Job>()

        val meoMasterKeyPair = if (includeMEO) {
            runCatching {
                database.rawQuery("SELECT * FROM memories_meo_confidential", null).use { cursor ->
                    if (cursor.moveToNext()) {
                        cursor.getStringOrNull("master_key")!!.trim() to cursor.getStringOrNull("master_key_iv")!!.trim()
                    } else null
                }
            }.getOrNull()
        } else null

        database.rawQuery("SELECT memories_entry.title as story_title, memories_snap.create_time, " +
                "memories_snap.media_key, memories_snap.media_iv, memories_snap.encrypted_media_key, memories_snap.encrypted_media_iv, " +
                "memories_media.download_url FROM memories_snap " +
            "INNER JOIN memories_entry ON memories_snap.memories_entry_id = memories_entry._id " +
            "INNER JOIN memories_media ON memories_snap.media_id = memories_media._id " +
            "WHERE memories_snap.create_time >= ? AND memories_snap.create_time <= ? " +
            "ORDER BY memories_snap.create_time ASC", arrayOf(timeRange?.start?.toString() ?: "-1", timeRange?.end?.toString() ?: Long.MAX_VALUE.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val encryptedMediaKey = cursor.getStringOrNull("encrypted_media_key")?.trim()
                val encryptedMediaIv = cursor.getStringOrNull("encrypted_media_iv")?.trim()
                var mediaKey = cursor.getStringOrNull("media_key")?.trim()
                var mediaIv = cursor.getStringOrNull("media_iv")?.trim()

                if (!includeMEO && encryptedMediaKey != null && encryptedMediaIv != null) continue

                meoMasterKeyPair.takeIf { encryptedMediaKey != null && encryptedMediaIv != null }?.let { keyPair ->
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    runCatching {
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.decode(keyPair.first), "AES"), IvParameterSpec(Base64.decode(keyPair.second)))
                        mediaKey = Base64.encode(cipher.doFinal(Base64.decode(encryptedMediaKey ?: return@let)))
                        mediaIv = Base64.encode(cipher.doFinal(Base64.decode(encryptedMediaIv ?: return@let)))
                        context.log.verbose("decrypted meo $mediaKey/$mediaIv")
                    }.onFailure {
                        context.log.error("failed to decrypt meo", it)
                    }
                }

                if (mediaKey == null || mediaIv == null) {
                    context.log.error("missing media key or iv for ${cursor.getStringOrNull("download_url")}")
                    failed++
                    updateProgress()
                    continue
                }

                val entry = MemoriesEntry(
                    storyTitle = cursor.getStringOrNull("story_title") ?: "unknown",
                    createTime = cursor.getLongOrNull("create_time") ?: -1L,
                    mediaKey = mediaKey,
                    mediaIv = mediaIv,
                    downloadUrl = cursor.getStringOrNull("download_url") ?: continue
                )

                totalCount++

                scope.launch(downloadContext) {
                    var downloadedFile = File.createTempFile("memories", ".tmp", context.androidContext.cacheDir)

                    runCatching {
                        okHttpClient.newCall(
                            okhttp3.Request.Builder()
                                .url(entry.downloadUrl)
                                .build()
                        ).execute().use { response ->
                            val inputStream  = response.body.byteStream().let {
                                if (entry.mediaKey != null && entry.mediaIv != null) {
                                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.decode(entry.mediaKey), "AES"), IvParameterSpec(Base64.decode(entry.mediaIv)))
                                    CipherInputStream(it, cipher)
                                } else it
                            }

                            downloadedFile.outputStream().use { outputStream ->
                                inputStream.use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            val fileType = FileType.fromFile(downloadedFile)

                            downloadedFile = File(
                                downloadedFile.parentFile,
                                "${entry.createTime}-${entry.downloadUrl.hashCode().absoluteValue.toString(16)}.${fileType.fileExtension}"
                            ).also {
                                downloadedFile.renameTo(it)
                            }

                            withContext(writeToZipContext) {
                                val zipEntry = ZipEntry("${if (folders) entry.folderName + "/" else entry.folderName}${downloadedFile.name}")
                                FileTime.fromMillis(entry.createTime).let {
                                    zipEntry.lastModifiedTime = it
                                    zipEntry.lastAccessTime = it
                                    zipEntry.creationTime = it
                                }
                                outputZipFile.apply {
                                    putNextEntry(zipEntry)
                                    downloadedFile.inputStream().use { it.copyTo(outputZipFile) }
                                    closeEntry()
                                    flush()
                                }
                                currentCount++
                                updateProgress()
                            }
                        }
                    }.onFailure {
                        context.log.error("failed to download ${entry.downloadUrl}", it)
                        failed++
                        updateProgress()
                    }
                    downloadedFile.delete()
                }.also { jobs.add(it) }
            }
        }

        jobs.joinAll()
        withContext(Dispatchers.IO) {
            outputZipFile.close()
        }
        val exportedPath = runCatching { outputTarget.finalize(outputZip) }
            .getOrElse { error ->
                context.log.error("Failed to finalize memories export", error)
                context.longToast(context.translation["toast_export_memories_failed"])
                return
            }
        if (outputZip.parentFile == context.androidContext.cacheDir) {
            outputZip.delete()
        }
        context.longToast(
            context.translation.format("toast_exported_to_path", "path" to exportedPath)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ExporterDialog(database: SQLiteDatabase, onDismiss: () -> Unit) {
        var exportJob by remember { mutableStateOf(null as Job?) }
        var exportFinished by remember { mutableStateOf(false) }
        var exportProgress by remember { mutableStateOf(Pair(0, 0)) } // progress, failed

        var dateRangeFilter by remember { mutableStateOf(false) }
        var sortByFolder by remember { mutableStateOf(false) }
        var includeMEO by remember { mutableStateOf(false) }
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = OffsetDateTime.now().minusDays(8).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = Instant.now().toEpochMilli(),
            initialDisplayMode = DisplayMode.Picker
        )

        val totalCount = remember(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis, dateRangeFilter) {
            val timeRange = dateRangePickerState.takeIf { dateRangeFilter }?.let {
                TimeRange(it.selectedStartDateMillis, it.selectedEndDateMillis)
            }

            database.rawQuery("SELECT COUNT(*) FROM memories_snap WHERE create_time >= ? AND create_time <= ? ", arrayOf(timeRange?.start?.toString() ?: "-1", timeRange?.end?.toString() ?: Long.MAX_VALUE.toString())).use {
                it.moveToFirst()
                it.getInt(0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(dialogBackground)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 46.dp, y = (-38).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8C7BFF).copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-60).dp, y = 32.dp)
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Filled.Download,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(28.dp),
                                contentDescription = null
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translation.get("export_title"),
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = translation.get("total_memories").replace("{count}", totalCount.toString()),
                                color = Color(0xFFD9D3FF),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "ZIP",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    if (exportJob != null) {
                        BasicText(
                            text = translation.get("exporting_memories").replace("{failed}", exportProgress.second.toString()),
                            modifier = Modifier.fillMaxWidth(),
                            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Start)
                        )
                        ProgressBar(progress = exportProgress.first / 100f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecondaryButton(text = translation.get("quit"), modifier = Modifier.weight(1f)) {
                                exportJob?.cancel()
                                exportJob = null
                                onDismiss()
                            }
                            if (exportFinished) {
                                PrimaryButton(text = translation.get("done"), modifier = Modifier.weight(1f)) {
                                    exportJob = null
                                    onDismiss()
                                }
                            }
                        }
                    } else {
                        var dateRangeDialog by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = translation.get("date_range"),
                                    color = Color(0xFFD9D3FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                BareToggle(
                                    checked = dateRangeFilter,
                                    onCheckedChange = { dateRangeFilter = it },
                                    accent = Color(0xFF8EF0F3)
                                )
                            }
                            val formattedRange = remember(
                                dateRangePickerState.selectedStartDateMillis,
                                dateRangePickerState.selectedEndDateMillis
                            ) {
                                formatRange(
                                    dateRangePickerState.selectedStartDateMillis,
                                    dateRangePickerState.selectedEndDateMillis
                                )
                            }
                            Text(
                                text = formattedRange,
                                color = Color(0xFFD9D3FF),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, accentGradient, RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            PrimaryButton(
                                text = translation.get("select"),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { dateRangeDialog = true }
                            )
                        }

                        LaunchedEffect(dateRangeFilter) {
                            if (dateRangeFilter) dateRangeDialog = true
                        }

                        if (dateRangeDialog) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { dateRangeDialog = false }
                            )
                            GlassPopup(onDismiss = { dateRangeDialog = false }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = translation.get("date_range"),
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable { dateRangeDialog = false }
                                    )
                                }
                                    CompositionLocalProvider(
                                        LocalTextSelectionColors provides TextSelectionColors(
                                            handleColor = Color(0xFF8EF0F3),
                                            backgroundColor = Color(0x338EF0F3)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    Brush.radialGradient(
                                                    listOf(
                                                        Color(0xFF8C7BFF).copy(alpha = 0.22f),
                                                        Color.Transparent
                                                    ),
                                                    radius = 520f
                                                )
                                            )
                                            .border(1.dp, accentGradient, RoundedCornerShape(20.dp))
                                            .padding(10.dp)
                                        ) {
                                            DateRangePicker(
                                                state = dateRangePickerState,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp)),
                                                showModeToggle = true,
                                                colors = DatePickerDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    titleContentColor = Color.White,
                                                    headlineContentColor = Color.White,
                                                    weekdayContentColor = Color.White.copy(alpha = 0.9f),
                                                    subheadContentColor = Color(0xFFD9D3FF),
                                                    selectedDayContainerColor = Color(0xFF8C7BFF).copy(alpha = 0.6f),
                                                    selectedDayContentColor = Color(0xFF0A0F1D),
                                                    todayContentColor = Color.White,
                                                    todayDateBorderColor = Color(0xFF5FD8FF),
                                                    dayContentColor = Color.White.copy(alpha = 0.95f),
                                                    disabledDayContentColor = Color.White.copy(alpha = 0.35f),
                                                    dividerColor = Color.White.copy(alpha = 0.16f),
                                                    navigationContentColor = Color.White,
                                                    dateTextFieldColors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color(0xFF0E1221),
                                                        unfocusedContainerColor = Color(0xFF0E1221),
                                                        disabledContainerColor = Color(0x330E1221),
                                                        cursorColor = Color(0xFF8EF0F3),
                                                        focusedLabelColor = Color(0xFF8EF0F3),
                                                        unfocusedLabelColor = Color(0xFFD9D3FF),
                                                        focusedBorderColor = Color.Transparent,
                                                        unfocusedBorderColor = Color.Transparent,
                                                        disabledBorderColor = Color.Transparent,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White
                                                    )
                                                )
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        SecondaryButton(
                                            text = translation.get("cancel"),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            dateRangePickerState.setSelection(
                                                startDateMillis = null,
                                                endDateMillis = null
                                            )
                                            dateRangeDialog = false
                                        }
                                        PrimaryButton(
                                            text = translation.get("ok"),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            dateRangeDialog = false
                                        }
                                    }
                                }
                            }
                        }

                        SectionLabel(translation.get("sort_by_folder"))
                        NeonToggle(
                            checked = sortByFolder,
                            onCheckedChange = { sortByFolder = it },
                            label = translation.get("sort_by_folder"),
                            accent = Color(0xFF9EF88F)
                        )

                        SectionLabel(translation.get("include_my_eyes_only"))
                        NeonToggle(
                            checked = includeMEO,
                            onCheckedChange = { includeMEO = it },
                            label = translation.get("include_my_eyes_only"),
                            accent = Color(0xFFE6B8FF)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecondaryButton(text = translation.get("cancel"), modifier = Modifier.weight(1f), onClick = onDismiss)
                            PrimaryButton(text = translation.get("export"), modifier = Modifier.weight(1f)) {
                                context.coroutineScope.launch {
                                    exportMemories(
                                        scope = this,
                                        database = database,
                                        timeRange = dateRangePickerState.takeIf { dateRangeFilter }?.let {
                                            TimeRange(it.selectedStartDateMillis, it.selectedEndDateMillis)
                                        },
                                        folders = sortByFolder,
                                        includeMEO = includeMEO,
                                    ) { progress, failed ->
                                        exportProgress = Pair(progress, failed)
                                    }
                                }.also { exportJob = it }.invokeOnCompletion {
                                    exportFinished = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ProgressBar(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = clamped)
                    .fillMaxHeight()
                    .background(accentGradient)
            )
        }
    }

    @Composable
    private fun GlassPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelOverlay)
                    .border(1.2.dp, accentGradient, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        BasicText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = androidx.compose.ui.text.TextStyle(
                color = Color(0xFFD9D3FF),
                fontSize = 13.sp,
                textAlign = TextAlign.Start
            )
        )
    }

    @Composable
    private fun BareToggle(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        accent: Color
    ) {
        val knobOffset by animateFloatAsState(targetValue = if (checked) 18f else 0f, animationSpec = tween(180), label = "bareToggle")
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.04f))
                .clickable { onCheckedChange(!checked) }
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(18.dp)
                    .offset(x = knobOffset.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(accent, Color(0xFF6C7CFF))))
            )
        }
    }

    @Composable
    private fun NeonToggle(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String,
        accent: Color
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, accentGradient, RoundedCornerShape(14.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0F1727))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                        .offset(x = if (checked) 18.dp else 0.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(accent, Color(0xFF6C7CFF))))
                )
            }
            Column {
                BasicText(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp)
                )
                BasicText(
                    text = if (checked) "On" else "Off",
                    style = androidx.compose.ui.text.TextStyle(color = Color(0xFFB1B4D7), fontSize = 11.sp)
                )
            }
        }
    }

    @Composable
    private fun PrimaryButton(
        text: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(accentGradient)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF0A0F1D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }

    private fun formatRange(start: Long?, end: Long?): String {
        fun fmt(millis: Long?): String = millis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(rangeFormatter)
        } ?: "Not set"
        return "${fmt(start)} - ${fmt(end)}"
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            val database = runCatching {
                SQLiteDatabase.openDatabase(
                    context.androidContext.getDatabasePath("memories.db"),
                    OpenParams.Builder().setOpenFlags(SQLiteDatabase.OPEN_READONLY).build()
                )
            }.getOrNull()

            if (database == null) {
                context.longToast(context.translation["toast_open_memories_db_failed"])
                return@launch
            }

            createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
                ExporterDialog(database) { alertDialog.dismiss() }
            }.apply {
                setOnDismissListener { database.close() }
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }
}
