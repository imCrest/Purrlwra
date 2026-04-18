package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.CursorWrapper
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.FileType
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.common.util.ktx.getLongOrNull
import cock.crest.purrfectsnap.lite.common.util.ktx.getTypeArguments
import cock.crest.purrfectsnap.lite.core.event.events.impl.ActivityResultEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.mapper.impl.ChatMediaDrawerMapper
import java.io.File
import java.io.InputStream
import java.lang.reflect.Method
import java.nio.ByteBuffer
import kotlin.random.Random

class MediaFilePicker : Feature("Media File Picker") {
    companion object {
        private const val SNAP_CHUNK_DURATION_MS = 10_000L
        private val queuedSplitItems = ArrayDeque<Any>()
        private val queuedSplitItemIds = ArrayDeque<String>()
        private val queuedSplitCleanupUris = mutableMapOf<String, String>()
        private var originalUnsplitItem: Any? = null
        private var reusableOriginalItem: Any? = null
        private var queuedOverrideType: String? = null
        private var bypassSplitOnce = false
        private var sendSingleItemHandler: ((Any) -> Boolean)? = null
        private var cleanupItemHandler: ((String) -> Unit)? = null
        fun hasQueuedSplitItems(): Boolean = queuedSplitItems.isNotEmpty()
        fun hasPendingSplitCleanup(): Boolean = queuedSplitItemIds.isNotEmpty()
        fun hasOriginalUnsplitItem(): Boolean = originalUnsplitItem != null
        fun hasReusableOriginalItem(): Boolean = reusableOriginalItem != null
        fun setQueuedOverrideType(value: String?) {
            queuedOverrideType = value
        }
        fun getQueuedOverrideType(): String? = queuedOverrideType
        fun clearQueuedSplitItems(deleteTempItems: Boolean = true) {
            if (deleteTempItems) {
                val cleanup = cleanupItemHandler
                queuedSplitCleanupUris.values.toList().forEach { uri ->
                    cleanup?.invoke(uri)
                }
            }
            queuedSplitItems.clear()
            queuedSplitItemIds.clear()
            queuedSplitCleanupUris.clear()
            originalUnsplitItem = null
            queuedOverrideType = null
        }
        fun sendReusableOriginalItem(): Boolean {
            val item = reusableOriginalItem ?: return false
            bypassSplitOnce = true
            val sender = sendSingleItemHandler ?: return false
            return sender(item)
        }
        private fun queueSplitItems(items: List<Any>, preparedItems: List<PreparedMediaItem>, originalItem: Any?) {
            clearQueuedSplitItems(deleteTempItems = false)
            originalUnsplitItem = originalItem
            items.drop(1).forEach { queuedSplitItems.addLast(it) }
            preparedItems.forEach {
                queuedSplitItemIds.addLast(it.itemId)
                queuedSplitCleanupUris[it.itemId] = it.uri
            }
        }
        fun sendOriginalUnsplitItem(): Boolean {
            val item = originalUnsplitItem ?: return false
            val overrideType = queuedOverrideType
            clearQueuedSplitItems(deleteTempItems = true)
            queuedOverrideType = overrideType
            bypassSplitOnce = true
            val sender = sendSingleItemHandler ?: return false
            return sender(item)
        }
        fun handleCurrentQueuedItemSuccess(): Boolean {
            queuedSplitItemIds.removeFirstOrNull()?.let { itemId ->
                queuedSplitCleanupUris.remove(itemId)?.let { uri ->
                    cleanupItemHandler?.invoke(uri)
                }
            }
            if (queuedSplitItems.isEmpty()) {
                queuedOverrideType = null
                return false
            }
            val next = queuedSplitItems.removeFirstOrNull() ?: run {
                queuedOverrideType = null
                return false
            }
            val sender = sendSingleItemHandler ?: return false
            val result = sender(next)
            if (!result) {
                queuedSplitItems.addFirst(next)
            }
            return result
        }
    }

    var lastMediaDuration: Long? = null
        private set

    private data class PreparedMediaItem(
        val itemId: String,
        val durationMs: Long,
        val uri: String
    )

    private fun splitVideoIntoChunks(
        inputFile: File,
        chunkDurationMs: Long = SNAP_CHUNK_DURATION_MS
    ): List<File> {
        val durationMs = extractMediaDuration(Uri.fromFile(inputFile)) ?: return emptyList()
        if (durationMs <= chunkDurationMs) return listOf(inputFile)

        val retriever = MediaMetadataRetriever()
        val rotation = runCatching {
            retriever.setDataSource(inputFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        }.getOrDefault(0).also {
            runCatching { retriever.release() }
        }

        val outputFiles = mutableListOf<File>()
        var chunkStartMs = 0L
        var chunkIndex = 0

        while (chunkStartMs < durationMs) {
            val chunkEndMs = minOf(chunkStartMs + chunkDurationMs, durationMs)
            val outputFile = File.createTempFile("purrfectsnap_chunk_${chunkIndex}_", ".mp4", context.androidContext.cacheDir)
            val extractor = MediaExtractor()
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            val chunkStartUs = chunkStartMs * 1000
            val chunkEndUs = chunkEndMs * 1000
            var muxerStarted = false
            var wroteAnySample = false

            try {
                extractor.setDataSource(inputFile.absolutePath)

                repeat(extractor.trackCount) { trackIndex ->
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@repeat
                    if (!mime.startsWith("video/") && !mime.startsWith("audio/")) return@repeat
                    extractor.selectTrack(trackIndex)
                    trackMap[trackIndex] = muxer.addTrack(format)
                }

                if (rotation != 0) {
                    muxer.setOrientationHint(rotation)
                }

                val maxBufferSize = (0 until extractor.trackCount).maxOfOrNull { trackIndex ->
                    extractor.getTrackFormat(trackIndex).let { format ->
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        } else {
                            1024 * 1024
                        }
                    }
                } ?: (1024 * 1024)

                val buffer = ByteBuffer.allocateDirect(maxBufferSize)
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                muxer.start()
                muxerStarted = true

                extractor.seekTo(chunkStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0) break
                    if (sampleTimeUs < chunkStartUs) {
                        extractor.advance()
                        continue
                    }
                    if (sampleTimeUs >= chunkEndUs) break

                    val sampleTrackIndex = extractor.sampleTrackIndex
                    val muxerTrackIndex = trackMap[sampleTrackIndex]
                    if (muxerTrackIndex != null) {
                        bufferInfo.presentationTimeUs = sampleTimeUs - chunkStartUs
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                        wroteAnySample = true
                    }
                    extractor.advance()
                }

                if (wroteAnySample) {
                    outputFiles += outputFile
                } else {
                    outputFile.delete()
                }
            } catch (throwable: Throwable) {
                outputFile.delete()
                outputFiles.forEach { it.delete() }
                throw throwable
            } finally {
                if (muxerStarted) {
                    runCatching { muxer.stop() }
                }
                runCatching { muxer.release() }
                runCatching { extractor.release() }
            }

            chunkStartMs += chunkDurationMs
            chunkIndex++
        }

        return outputFiles
    }

    private fun registerTemporaryVideo(file: File, displayName: String): PreparedMediaItem {
        val resolver = context.androidContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/.PurrfectSnap")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create MediaStore entry")

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Failed to open MediaStore output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }

        val durationMs = extractMediaDuration(uri) ?: 0L
        val itemId = uri.lastPathSegment ?: error("Failed to resolve MediaStore item id")

        context.coroutineScope.launch {
            delay(120_000)
            runCatching { resolver.delete(uri, null, null) }
        }

        return PreparedMediaItem(itemId = itemId, durationMs = durationMs, uri = uri.toString())
    }

    private fun buildDrawerItems(itemClass: Any, mediaItems: List<PreparedMediaItem>): List<Any> {
        return mediaItems.mapIndexedNotNull { index, mediaItem ->
            itemClass.dataBuilder {
                from("_item") {
                    set("_cameraRollSource", "Snapchat")
                    set("_contentUri", "")
                    set("_durationMs", mediaItem.durationMs.toDouble())
                    set("_disabled", false)
                    set("_imageRotation", 0.0)
                    set("_width", 1080.0)
                    set("_height", 1920.0)
                    set("_timestampMs", (System.currentTimeMillis() + index).toDouble())
                    from("_itemId") {
                        set("_itemId", mediaItem.itemId)
                        set("_type", "VIDEO")
                    }
                }
                set("_order", index.toDouble())
            }
        }
    }

    private fun prepareChunkedItemsFromMediaStoreId(itemId: String, durationMs: Long): List<PreparedMediaItem>? {
        val numericId = itemId.toLongOrNull() ?: return null
        val effectiveDurationMs = durationMs.takeIf { it > 0 } ?: extractMediaDuration(
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, numericId)
        ) ?: return null
        if (effectiveDurationMs <= SNAP_CHUNK_DURATION_MS) return null

        val sourceUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, numericId)
        val sourceFile = File.createTempFile("purrfectsnap_gallery_source_", ".mp4", context.androidContext.cacheDir)

        return runCatching {
            context.androidContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to open source gallery video")

            val chunkFiles = splitVideoIntoChunks(sourceFile, SNAP_CHUNK_DURATION_MS)
            val preparedItems = chunkFiles.mapIndexed { index, file ->
                registerTemporaryVideo(file, "purrfectsnap_gallery_chunk_${System.currentTimeMillis()}_$index.mp4")
            }
            chunkFiles.forEach { if (it != sourceFile) it.delete() }
            preparedItems
        }.also {
            sourceFile.delete()
        }.getOrElse {
            context.log.error("Failed to prepare split gallery items", it)
            null
        }
    }

    private fun extractMediaDuration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context.androidContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun resolveInputExtension(uri: Uri, mimeType: String?): String {
        val extensionFromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.lowercase()
        val extensionFromUri = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).takeIf { !it.isNullOrBlank() }?.lowercase()

        return when (extensionFromMime ?: extensionFromUri ?: mimeType) {
            "video/mp4", "audio/mp4", "application/mp4", "mp4", "m4v" -> "mp4"
            "video/quicktime", "mov", "qt" -> "mov"
            "video/webm", "webm" -> "webm"
            "video/x-matroska", "video/mkv", "mkv" -> "mkv"
            "video/avi", "video/x-msvideo", "avi" -> "avi"
            "audio/mpeg", "audio/mp3", "mp3" -> "mp3"
            "audio/aac", "aac" -> "aac"
            "audio/ogg", "audio/opus", "opus", "ogg" -> "opus"
            "audio/wav", "audio/x-wav", "wav" -> "wav"
            "audio/mp4a-latm", "audio/x-m4a", "m4a" -> "m4a"
            else -> FileType.fromString(extensionFromMime ?: extensionFromUri).fileExtension ?: "mp4"
        }
    }

    @SuppressLint("Recycle")
    override fun init() {
        if (!context.config.experimental.mediaFilePicker.get()) return

        onNextActivityCreate(defer = true) {
            lateinit var chatMediaDrawerActionHandler: Any
            var sendItemsMethod: Method? = null
            var drawerViewClass: Class<*>? = null
            var sendItemsListItemClassFallback: Class<*>? = null
            var sendItemsHookedHandler: Any? = null

            context.mappings.useMapper(ChatMediaDrawerMapper::class) {
                val drawerCls = chatMediaDrawerClass.getAsClass() ?: return@useMapper
                val actionHandlerCls = actionHandlerClass.getAsClass() ?: return@useMapper
                val sendItemsName = sendItemsMethodName.getAsString() ?: "sendItems"
                drawerViewClass = drawerCls
                sendItemsListItemClassFallback = sendItemsListItemClass.getAsClass()

                val contextType = drawerCls.genericSuperclass?.getTypeArguments()?.getOrNull(1) ?: return@useMapper
                val handlerParamMethod = contextType.methods.firstOrNull { method ->
                    method.parameterTypes.size == 1 && (
                        method.parameterTypes[0].name.endsWith("ChatMediaDrawerActionHandler") ||
                            actionHandlerCls.isAssignableFrom(method.parameterTypes[0])
                    )
                } ?: return@useMapper
                val sendItems = handlerParamMethod.parameterTypes[0].methods.firstOrNull { it.name == sendItemsName } ?: return@useMapper
                sendItemsMethod = sendItems
                handlerParamMethod.hook(HookStage.AFTER) {
                    chatMediaDrawerActionHandler = it.arg(0)
                    val handlerInstance = chatMediaDrawerActionHandler
                    sendSingleItemHandler = sendSingleItem@{ item ->
                        runCatching {
                            sendItemsMethod?.invoke(chatMediaDrawerActionHandler, listOf<Any>(), listOf(item))
                            true
                        }.getOrElse { throwable ->
                            context.log.error("MediaFilePicker: Failed to send queued split item", throwable)
                            false
                        }
                    }
                    cleanupItemHandler = { uriString ->
                        runCatching {
                            context.androidContext.contentResolver.delete(Uri.parse(uriString), null, null)
                        }.onFailure {
                            context.log.warn("MediaFilePicker: Failed to delete temp split media: ${it.message}")
                        }
                    }
                    if (sendItemsHookedHandler === handlerInstance) return@hook
                    sendItemsHookedHandler = handlerInstance

                    Hooker.hookObjectMethod(
                        handlerInstance::class.java,
                        handlerInstance,
                        sendItemsName,
                        HookStage.BEFORE
                    ) { param ->
                        if (bypassSplitOnce) {
                            bypassSplitOnce = false
                            return@hookObjectMethod
                        }
                        val currentItems = (param.argNullable<Any>(1) as? List<*>)?.filterNotNull() ?: return@hookObjectMethod
                        if (currentItems.isEmpty()) return@hookObjectMethod
                        reusableOriginalItem = currentItems.firstOrNull()

                        val itemClass = sendItems.genericParameterTypes.getOrNull(1)?.getTypeArguments()?.firstOrNull()
                            ?: sendItemsListItemClassFallback
                            ?: currentItems.firstOrNull()?.javaClass
                            ?: return@hookObjectMethod

                        val preparedExpandedItems = mutableListOf<PreparedMediaItem>()
                        var didExpand = false
                        val expandedItems = currentItems.flatMap { item ->
                            val baseItem = item.getObjectFieldOrNull("_item") ?: return@flatMap listOf(item)
                            val durationMs = ((baseItem.getObjectFieldOrNull("_durationMs") as? Double)?.toLong())
                                ?: ((baseItem.getObjectFieldOrNull("_durationMs") as? Long))
                                ?: 0L
                            val itemId = baseItem.getObjectFieldOrNull("_itemId")
                                ?.getObjectFieldOrNull("_itemId")
                                ?.toString()
                                ?: return@flatMap listOf(item)

                            val splitItems = prepareChunkedItemsFromMediaStoreId(itemId, durationMs)
                            if (splitItems.isNullOrEmpty()) {
                                listOf(item)
                            } else {
                                didExpand = true
                                preparedExpandedItems.addAll(splitItems)
                                buildDrawerItems(itemClass, splitItems)
                            }
                        }

                        if (didExpand && expandedItems.isNotEmpty()) {
                            queueSplitItems(expandedItems, preparedExpandedItems, currentItems.firstOrNull())
                            param.setArg(1, listOf(expandedItems.first()))
                        }
                    }
                }
            }

            var requestCode: Int? = null
            var firstVideoId: Long? = null
            var mediaInputStream: InputStream? = null

            ContentResolver::class.java.apply {
                hook("query", HookStage.AFTER) { param ->
                    val uri = param.arg<Uri>(0)
                    if (!uri.toString().endsWith(firstVideoId.toString())) return@hook

                    param.setResult(object : CursorWrapper(param.getResult() as Cursor) {
                        override fun getLong(columnIndex: Int): Long {
                            if (getColumnName(columnIndex) == "duration") {
                                return lastMediaDuration ?: -1
                            }
                            return super.getLong(columnIndex)
                        }
                    })
                }
                hook("openInputStream", HookStage.BEFORE) { param ->
                    val uri = param.arg<Uri>(0)
                    if (uri.toString().endsWith(firstVideoId.toString())) {
                        param.setResult(mediaInputStream)
                        mediaInputStream = null
                    }
                }
            }

            context.event.subscribe(ActivityResultEvent::class) { event ->
                if (sendItemsMethod == null || event.requestCode != requestCode || event.resultCode != Activity.RESULT_OK) return@subscribe
                requestCode = null

                firstVideoId = context.androidContext.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_TAKEN} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLongOrNull("_id")
                    } else {
                        null
                    }
                }

                if (firstVideoId == null) {
                    context.inAppOverlay.showStatusToast(
                        Icons.Default.Upload,
                        "Must have a video in gallery to upload."
                    )
                    return@subscribe
                }

                fun sendMedia(items: List<PreparedMediaItem>? = null) {
                    val method = sendItemsMethod ?: return
                    val itemClass = method.genericParameterTypes.getOrNull(1)?.getTypeArguments()?.firstOrNull()
                        ?: sendItemsListItemClassFallback
                    if (itemClass == null) {
                        context.log.warn("MediaFilePicker: sendItems second parameter type has no generic info (type erasure). genericParameterTypes[1]=${method.genericParameterTypes.getOrNull(1)}")
                        context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to send media (incompatible version).")
                        return
                    }
                    val mediaItems = items ?: listOf(PreparedMediaItem(firstVideoId.toString(), lastMediaDuration ?: 0L, ""))
                    val builtItems = buildDrawerItems(itemClass, mediaItems)
                    if (builtItems.size != mediaItems.size) {
                        context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to build media item.")
                        return
                    }
                    method.invoke(chatMediaDrawerActionHandler, listOf<Any>(), builtItems)
                }

                fun startConversion(audioOnly: Boolean) {
                    context.coroutineScope.launch {
                        val pickedUri = event.intent?.data ?: run {
                            context.inAppOverlay.showStatusToast(Icons.Default.Error, "No media was selected.")
                            return@launch
                        }
                        val mimeType = context.androidContext.contentResolver.getType(pickedUri)
                        val inputExtension = resolveInputExtension(pickedUri, mimeType)
                        val outputExtension = if (audioOnly || mimeType?.startsWith("audio/") == true) "m4a" else "mp4"

                        lastMediaDuration = extractMediaDuration(pickedUri)

                        context.inAppOverlay.showStatusToast(Icons.Default.Crop, "Converting media...", durationMs = 3000)
                        val pickedFileDescriptor = context.androidContext.contentResolver.openFileDescriptor(pickedUri, "r")
                        if (pickedFileDescriptor == null) {
                            context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to open selected media.")
                            return@launch
                        }

                        val pfd = context.bridgeClient.convertMedia(
                            pickedFileDescriptor,
                            inputExtension,
                            outputExtension,
                            "aac",
                            if (!audioOnly) "libx264" else null
                        )

                        if (pfd == null) {
                            context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to convert media.")
                            return@launch
                        }

                        context.inAppOverlay.showStatusToast(Icons.Default.CheckCircleOutline, "Media converted successfully.")

                        runCatching {
                            if (!audioOnly && (lastMediaDuration ?: 0L) > 10_000L) {
                                val convertedFile = File.createTempFile("purrfectsnap_source_", ".$outputExtension", context.androidContext.cacheDir)
                                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                                    convertedFile.outputStream().use { output -> input.copyTo(output) }
                                }

                                val chunkFiles = splitVideoIntoChunks(convertedFile)
                                val preparedItems = chunkFiles.mapIndexed { index, file ->
                                    registerTemporaryVideo(file, "purrfectsnap_chunk_${System.currentTimeMillis()}_$index.mp4")
                                }

                                chunkFiles.forEach { if (it != convertedFile) it.delete() }
                                convertedFile.delete()

                                sendMedia(preparedItems)
                            } else {
                                mediaInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                                sendMedia()
                            }
                        }.onFailure {
                            mediaInputStream = null
                            context.log.error(it)
                            context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to send media.")
                        }
                    }
                }

                val pickedUri = event.intent?.data ?: return@subscribe
                val isAudio = context.androidContext.contentResolver.getType(pickedUri)?.startsWith("audio/") == true

                if (isAudio || context.config.messaging.galleryMediaSendOverride.mode.getNullable() == null) {
                    startConversion(isAudio)
                    return@subscribe
                }

                android.app.AlertDialog.Builder(context.mainActivity!!)
                    .setTitle("Convert video file")
                    .setItems(arrayOf("Send as video/audio", "Send as audio only")) { _, which ->
                        startConversion(which == 1)
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.show()
            }

            val buttonTag = Random.nextInt(0, 65535)

            context.event.subscribe(AddViewEvent::class) { event ->
                if (event.parent !is FrameLayout || drawerViewClass?.isInstance(event.view) != true) return@subscribe

                event.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        if (event.parent.findViewWithTag<View>(buttonTag)?.run {
                                visibility = View.VISIBLE
                                bringToFront()
                            } != null) return
                        event.parent.addView(
                            createComposeView(context.mainActivity!!) {
                                PurrfectOverlayTheme {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 10.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        val shape = RoundedCornerShape(18.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .shadow(
                                                    elevation = 16.dp,
                                                    shape = shape,
                                                    spotColor = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.28f),
                                                    ambientColor = PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.20f)
                                                )
                                                .clip(shape)
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        listOf(
                                                            PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.35f),
                                                            PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.18f)
                                                        )
                                                    ),
                                                    shape = shape
                                                )
                                                .border(
                                                    1.dp,
                                                    Brush.linearGradient(
                                                        listOf(
                                                            PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.7f),
                                                            PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.55f)
                                                        )
                                                    ),
                                                    shape = shape
                                                )
                                                .clickable {
                                                    requestCode = Random.nextInt(0, 65535)
                                                    this@MediaFilePicker.context.mainActivity!!.startActivityForResult(
                                                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                            addCategory(Intent.CATEGORY_OPENABLE)
                                                            type = "video/*"
                                                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
                                                        },
                                                        requestCode!!
                                                    )
                                                },
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Upload,
                                                contentDescription = "Upload media",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }.apply {
                                tag = buttonTag
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            }
                        )
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        event.parent.findViewWithTag<View>(buttonTag)?.visibility = View.GONE
                    }
                })
            }
        }
    }
}
