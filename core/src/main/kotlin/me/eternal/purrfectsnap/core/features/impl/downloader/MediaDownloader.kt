package cock.crest.purrfectsnap.lite.core.features.impl.downloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import cock.crest.purrfectsnap.lite.bridge.DownloadCallback
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.FileType
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.download.*
import cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.common.util.ktx.longHashCode
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.common.util.snap.MediaDownloaderHelper
import cock.crest.purrfectsnap.lite.core.DownloadManagerClient
import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder.DecodedAttachment
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder.MessageDecoder
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.features.impl.ui.OperaStoryOverlay
import cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.ui.debugEditText
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.SNAPCHAT_13_80_VERSION
import cock.crest.purrfectsnap.lite.core.util.isSnapchatVersionAtLeast
import cock.crest.purrfectsnap.lite.core.util.media.PreviewUtils
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.MediaInfo
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.Layer
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.ParamMap
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.toKeyPair
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.absoluteValue

data class OperaViewerMessageContext(
    val conversationId: String,
    val clientMessageId: Long
)

class MediaDownloader : MessagingRuleFeature("MediaDownloader", MessagingRuleType.AUTO_DOWNLOAD) {
    private var lastSeenMediaInfoMap: MutableMap<SplitMediaAssetType, MediaInfo>? = null
    var lastSeenMapParams: ParamMap? = null
        private set
    @Volatile
    private var pendingBatchDownloadIndices: MutableList<Int>? = null
    @Volatile
    private var batchForceAllowDuplicate: Boolean = false
    private val translations by lazy {
        this@MediaDownloader.context.translation.getCategory("download_processor")
    }
    private val useModernOperaViewerContext by lazy {
        isSnapchatVersionAtLeast(
            this@MediaDownloader.context.mappings.getSnapchatPackageInfo()?.versionName,
            SNAPCHAT_13_80_VERSION
        )
    }

    private fun logInfo(msg: String) = this@MediaDownloader.context.log.info("[MediaDownloader] $msg")
    private fun logVerbose(msg: String) = this@MediaDownloader.context.log.verbose("[MediaDownloader] $msg")
    private fun logError(msg: String, e: Throwable? = null) = if (e != null) this@MediaDownloader.context.log.error("[MediaDownloader] $msg", e) else this@MediaDownloader.context.log.error("[MediaDownloader] $msg")

    @Volatile
    private var batchTotalCount: Int = 0
    @Volatile
    private var batchSuccessCount: Int = 0
    @Volatile
    private var batchFailureCount: Int = 0
    @Volatile
    private var initialBatchStoryIdentity: String? = null

    fun provideDownloadManagerClient(
        mediaIdentifier: String,
        mediaAuthor: String,
        creationTimestamp: Long? = null,
        downloadSource: MediaDownloadSource,
        friendInfo: FriendInfo? = null,
        forceAllowDuplicate: Boolean = false,
        isBatch: Boolean = false
    ): DownloadManagerClient {
        val modCtx = this@MediaDownloader.context
        val generatedHash = (
            if (!modCtx.config.downloader.allowDuplicate.get() && !forceAllowDuplicate) mediaIdentifier
            else UUID.randomUUID().toString()
        ).longHashCode().absoluteValue.toString(16)

        val iconUrl = BitmojiSelfie.getBitmojiSelfie(friendInfo?.bitmojiSelfieId, friendInfo?.bitmojiAvatarId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D)
        val downloadLogging = modCtx.config.downloader.logging.get()

        val outputPath = createNewFilePath(
            modCtx.config,
            generatedHash.substring(0, generatedHash.length.coerceAtMost(8)),
            downloadSource,
            mediaAuthor,
            creationTimestamp?.takeIf { it > 0L }
        )

        return DownloadManagerClient(
            context = modCtx,
            metadata = DownloadMetadata(
                mediaIdentifier = generatedHash,
                mediaAuthor = mediaAuthor,
                downloadSource = downloadSource.translate(modCtx.translation),
                iconUrl = iconUrl,
                outputPath = outputPath
            ),
            callback = object: DownloadCallback.Stub() {
                override fun onSuccess(outputFile: String) {
                    var finalOutputFile = outputFile
                    runCatching {
                        val file = java.io.File(outputFile)
                        if (file.exists()) {
                            val header = file.inputStream().use { input ->
                                val buffer = ByteArray(16)
                                input.read(buffer)
                                buffer
                            }

                            val fileType = FileType.fromByteArray(header)
                            if (fileType.isVideo && !outputFile.endsWith(".mp4", ignoreCase = true)) {
                                val newPath = outputFile.removeSuffix(".dat").removeSuffix(".tmp") + ".mp4"
                                val newFile = java.io.File(newPath)
                                if (file.renameTo(newFile)) {
                                    finalOutputFile = newPath
                                } else {
                                    file.copyTo(newFile, overwrite = true)
                                    file.delete()
                                    finalOutputFile = newPath
                                }
                            }
                        }
                    }.onFailure { logError("Post-Processing Logic Failed for $outputFile", it) }

                    if (isBatch) {
                        batchSuccessCount++
                        if (downloadLogging.contains("success")) {
                            modCtx.inAppOverlay.showStatusToast(
                                icon = Icons.Outlined.DownloadDone,
                                text = translations.format("batch_progress_toast", "current" to (batchSuccessCount + batchFailureCount).toString(), "total" to batchTotalCount.toString()),
                                durationMs = 1300
                            )
                        }
                        return
                    }

                    if (downloadLogging.contains("success")) {
                        val toastText = translations.format("content_saved_toast", "path" to java.io.File(finalOutputFile).name)
                        if (modCtx.isMainActivityPaused) modCtx.shortToast(toastText)
                        modCtx.inAppOverlay.showStatusToast(Icons.Outlined.DownloadDone, toastText, 1300)
                    }
                }

                override fun onProgress(message: String) {
                    if (isBatch || !downloadLogging.contains("progress")) return
                    val toastText = message.ifBlank { translations["download_started_toast"] ?: "Started" }
                    if (modCtx.isMainActivityPaused) modCtx.shortToast(toastText)
                    modCtx.inAppOverlay.showStatusToast(Icons.Outlined.Info, toastText, 1300)
                }

                override fun onFailure(message: String, throwable: String?) {
                    if (!downloadLogging.contains("failure")) return
                    val errorText = translations[if (message == "Failed to download") "failed_generic_toast" else message] ?: message
                    if (isBatch) { batchFailureCount++; return }
                    if (modCtx.isMainActivityPaused) modCtx.shortToast(errorText)
                    modCtx.inAppOverlay.showStatusToast(Icons.Outlined.ErrorOutline, errorText, 1300)
                }
            }
        )
    }

    private fun ParamMap.getStorySnapIndex(): Int? = this["snap_index_in_story"]?.toString()?.toIntOrNull() ?: this["SNAP_POSITION_IN_STORY"]?.toString()?.toIntOrNull()
    private fun ParamMap.getStorySnapTotal(): Int? = this["snap_story_length"]?.toString()?.toIntOrNull() ?: this["NUM_SNAPS_IN_STORY"]?.toString()?.toIntOrNull()

    private fun isMultiSnapStory(paramMap: ParamMap): Boolean {
        if (paramMap.containsKey("MESSAGE_ID") || paramMap["SNAP_SOURCE"]?.toString() == "SINGLE_SNAP_STORY") return false
        if (paramMap.containsKey("LONGFORM_VIDEO_PLAYLIST_ITEM")) return false
        val total = paramMap.getStorySnapTotal() ?: return false
        return total > 1
    }

    fun downloadLastOperaMediaAsync(allowDuplicate: Boolean) {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return
        val paramMap = lastSeenMapParams!!
        val mediaInfoMap = lastSeenMediaInfoMap!!
        val modCtx = this@MediaDownloader.context

        if (isMultiSnapStory(paramMap) && modCtx.config.downloader.storySnapListDownload.get()) {
            modCtx.runOnUiThread { showStorySnapSelectionDialog(paramMap, mediaInfoMap, allowDuplicate) }
            return
        }

        modCtx.coroutineScope.launch { handleOperaMedia(paramMap, mediaInfoMap, true, allowDuplicate) }
    }

    private fun showStorySnapSelectionDialog(paramMap: ParamMap, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>, allowDuplicate: Boolean) {
        val totalCount = paramMap.getStorySnapTotal() ?: return
        val currentIndex = paramMap.getStorySnapIndex() ?: 0
        val modCtx = this@MediaDownloader.context
        val tr = modCtx.translation.getCategory("download_processor.story_snap_dialog")
        val cancelStr = modCtx.translation["button.cancel"] ?: "Cancel"
        val downloadStr = modCtx.translation["button.download"] ?: "Download"

        modCtx.runOnUiThread {
            val mainActivity = modCtx.mainActivity ?: return@runOnUiThread
            createComposeAlertDialog(mainActivity) { alertDialog ->
                PurrfectOverlayTheme {
                    val selected = remember { mutableStateListOf<Int>().apply { add(currentIndex) } }
                    PurrfectGlassCard(title = tr["title"] ?: "Select", modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed((0 until totalCount).toList()) { index, _ ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = selected.contains(index), onCheckedChange = { if (it) selected.add(index) else selected.remove(index) }, colors = CheckboxDefaults.colors(checkedColor = PurrfectOverlayPalette.glowPrimary))
                                        Text(tr.format("snap_item", "index" to (index + 1).toString(), "total" to totalCount.toString()), style = MaterialTheme.typography.bodyMedium, color = PurrfectOverlayPalette.textPrimary)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selected.size == totalCount, onCheckedChange = { if (it) { selected.clear(); selected.addAll(0 until totalCount) } else selected.clear() }, colors = CheckboxDefaults.colors(checkedColor = PurrfectOverlayPalette.glowPrimary))
                                Text(tr["select_all"] ?: "Select All", style = MaterialTheme.typography.bodyMedium, color = PurrfectOverlayPalette.textPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { alertDialog.dismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(cancelStr) }
                                Button(onClick = { if (selected.isNotEmpty()) { startBatchDownload(selected.sorted().toMutableList(), allowDuplicate); alertDialog.dismiss() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PurrfectOverlayPalette.glowPrimary)) { Text(downloadStr) }
                            }
                        }
                    }
                }
            }.apply { window?.setBackgroundDrawableResource(android.R.color.transparent); show() }
        }
    }

    private fun startBatchDownload(indices: MutableList<Int>, allowDuplicate: Boolean) {
        if (indices.isEmpty()) return
        val paramMap = lastSeenMapParams ?: return
        val mediaInfoMap = lastSeenMediaInfoMap ?: return
        val modCtx = this@MediaDownloader.context

        batchTotalCount = indices.size
        batchSuccessCount = 0; batchFailureCount = 0
        pendingBatchDownloadIndices = indices
        batchForceAllowDuplicate = allowDuplicate
        initialBatchStoryIdentity = paramMap.getStoryIdentity()

        val currentIndex = paramMap.getStorySnapIndex() ?: 0
        val targetIndex = indices.first()
        val totalCount = paramMap.getStorySnapTotal()

        if (currentIndex == targetIndex) {
            modCtx.coroutineScope.launch { processNextBatchDownload(paramMap, mediaInfoMap) }
        } else {
            val jumped = modCtx.feature(OperaStoryOverlay::class).requestJumpToSnap(targetIndex, totalCount)
            if (!jumped) { pendingBatchDownloadIndices = null; modCtx.shortToast(translations["batch_download_jump_failed_toast"] ?: "Jump Failed") }
        }
    }

    private suspend fun downloadSingleSnap(paramMap: ParamMap, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>) {
        runCatching { 
            handleOperaMedia(paramMap, mediaInfoMap, forceDownload = true, forceAllowDuplicate = batchForceAllowDuplicate, isBatch = true) 
        }.onFailure {
            batchFailureCount++
            if (batchSuccessCount + batchFailureCount == batchTotalCount) flushPendingMergeAndComplete()
        }
    }

    private suspend fun processNextBatchDownload(paramMap: ParamMap, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>) {
        val queue = pendingBatchDownloadIndices ?: return
        if (queue.isEmpty()) return
        val modCtx = this@MediaDownloader.context

        val currentIdentity = paramMap.getStoryIdentity()
        if (initialBatchStoryIdentity != null && (currentIdentity == null || currentIdentity != initialBatchStoryIdentity)) {
            flushPendingMergeAndComplete(); return
        }

        val currentIndex = paramMap.getStorySnapIndex() ?: -1
        if (currentIndex != queue.first()) return

        queue.removeAt(0)
        downloadSingleSnap(paramMap, mediaInfoMap)

        if (queue.isNotEmpty()) {
            val totalCount = paramMap.getStorySnapTotal()
            modCtx.runOnUiThread {
                fun tryJump(retryCount: Int = 0) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val jumped = runCatching { modCtx.feature(OperaStoryOverlay::class).requestJumpToSnap(queue.first(), totalCount) }.getOrNull() == true
                        if (!jumped && retryCount < 1) tryJump(retryCount + 1)
                        else if (!jumped) { pendingBatchDownloadIndices = null; modCtx.shortToast(translations["batch_download_jump_failed_toast"] ?: "Jump Failed") }
                    }, if (retryCount == 0) 120L else 220L)
                }
                tryJump()
            }
        }
    }

    private fun flushPendingMergeAndComplete() {
        val modCtx = this@MediaDownloader.context
        pendingBatchDownloadIndices = null
        modCtx.shortToast(if (batchFailureCount == 0) translations["batch_download_complete_toast"] ?: "Batch Complete" else "Batch complete: $batchSuccessCount succeeded, $batchFailureCount failed")
    }

    fun showLastOperaDebugMediaInfo() {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return
        val modCtx = this@MediaDownloader.context
        modCtx.runOnUiThread {
            val mainActivity = modCtx.mainActivity ?: return@runOnUiThread
            val mediaInfoText = lastSeenMapParams?.concurrentHashMap?.map { (key, value) ->
                val transformedValue = if (value != null && value::class.java == PurrfectSnap.classCache.snapUUID) SnapUUID(value).toString() else value
                "- $key: $transformedValue"
            }?.joinToString("\n") ?: "No media info found"
            ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
                setTitle("Debug Media Info")
                setView(debugEditText(modCtx.androidContext, mediaInfoText))
                setNeutralButton("Copy") { _, _ -> modCtx.androidContext.copyToClipboard(mediaInfoText) }
                setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }.show()
        }
    }

    private fun isSnapContentType(contentTypeId: Int): Boolean = when (ContentType.fromId(contentTypeId)) {
        ContentType.SNAP, ContentType.TINY_SNAP, ContentType.EXTERNAL_MEDIA -> true
        else -> false
    }

    private fun validateViewerMessageContext(messageContext: OperaViewerMessageContext): OperaViewerMessageContext? {
        val modCtx = this@MediaDownloader.context
        val message = modCtx.database.getConversationMessageFromId(messageContext.clientMessageId) ?: return null
        if (message.clientConversationId != messageContext.conversationId || !isSnapContentType(message.contentType)) return null
        return messageContext
    }

    private fun resolveLegacyViewerMessageContext(paramMap: ParamMap? = lastSeenMapParams): OperaViewerMessageContext? {
        val parts = paramMap?.get("MESSAGE_ID")?.toString()?.split(':')?.takeIf { it.size == 3 } ?: return null
        return OperaViewerMessageContext(conversationId = parts[0], clientMessageId = parts[2].toLongOrNull() ?: return null)
    }

    private fun parseViewerMessageContext(rawValue: String): OperaViewerMessageContext? {
        val parts = rawValue.split(':')
        if (parts.size < 3) return null
        val conversationId = parts.firstOrNull()?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return null
        val clientMessageId = parts.lastOrNull()?.toLongOrNull() ?: return null
        return OperaViewerMessageContext(conversationId = conversationId, clientMessageId = clientMessageId)
    }

    fun resolveViewerMessageContextFromParamMap(paramMap: ParamMap? = lastSeenMapParams): OperaViewerMessageContext? {
        if (paramMap == null) return null
        if (!useModernOperaViewerContext) return resolveLegacyViewerMessageContext(paramMap)
        paramMap["MESSAGE_ID"]?.toString()?.let(::parseViewerMessageContext)?.let(::validateViewerMessageContext)?.let { return it }
        return paramMap.concurrentHashMap.values.asSequence().mapNotNull { it?.toString()?.let(::parseViewerMessageContext) }.mapNotNull(::validateViewerMessageContext).firstOrNull()
    }

    fun resolveCurrentSnapMessageContext(): OperaViewerMessageContext? {
        val modCtx = this@MediaDownloader.context
        if (!useModernOperaViewerContext) return resolveLegacyViewerMessageContext()
        val messaging = modCtx.feature(Messaging::class)
        val currentConversationId = messaging.openedConversationUUID?.toString()
        val currentMessageId = messaging.lastFocusedMessageId.takeIf { it > 0L }
        if (currentConversationId != null && currentMessageId != null) {
            validateViewerMessageContext(OperaViewerMessageContext(conversationId = currentConversationId, clientMessageId = currentMessageId))?.let { return it }
        }
        return resolveViewerMessageContextFromParamMap()
    }

    private suspend fun handleLocalReferences(path: String): String {
        val modCtx = this@MediaDownloader.context
        val uri = Uri.parse(path)
        if (uri.scheme == "http" || uri.scheme == "https") return path
        return suspendCoroutine { continuation ->
            modCtx.httpServer.ensureServerStarted()?.let { server ->
                runCatching {
                    val file = java.io.File(uri.path ?: path)
                    if (!file.exists()) { continuation.resume(path); return@runCatching }
                    val url = server.putDownloadableContent(file.inputStream(), file.length())
                    continuation.resume(url)
                }.onFailure { continuation.resume(path) }
            } ?: continuation.resume(path)
        }
    }

    private suspend fun downloadOperaMedia(downloadManagerClient: DownloadManagerClient, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>, paramMap: ParamMap) {
        val modCtx = this@MediaDownloader.context
        if (mediaInfoMap.isEmpty()) return
        paramMap["SNAP_ID"]?.toString()?.let { snapId ->
            modCtx.database.getStorySnapEntry(snapId)?.let { storySnapEntry ->
                downloadManagerClient.downloadSingleMedia(
                    storySnapEntry.mediaUrl ?: throw Exception("Media URL not found"),
                    DownloadMediaType.fromUri(Uri.parse(storySnapEntry.mediaUrl)),
                    (storySnapEntry.mediaKey to storySnapEntry.mediaIv).takeIf { it.first != null && it.second != null }?.let { (k, i) -> MediaEncryptionKeyPair(k!!, i!!, urlSafe = false) }
                ); return
            }
        }
        val originalMediaRef = handleLocalReferences(mediaInfoMap[SplitMediaAssetType.ORIGINAL]!!.uri)
        mediaInfoMap[SplitMediaAssetType.OVERLAY]?.let { overlay ->
            val overlayRef = handleLocalReferences(overlay.uri)
            downloadManagerClient.downloadMediaWithOverlay(
                InputMedia(originalMediaRef, DownloadMediaType.fromUri(Uri.parse(originalMediaRef)), mediaInfoMap[SplitMediaAssetType.ORIGINAL]!!.encryption?.toKeyPair()),
                InputMedia(overlayRef, DownloadMediaType.fromUri(Uri.parse(overlayRef)), overlay.encryption?.toKeyPair(), isOverlay = true)
            ); return
        }
        downloadManagerClient.downloadSingleMedia(originalMediaRef, DownloadMediaType.fromUri(Uri.parse(originalMediaRef)), mediaInfoMap[SplitMediaAssetType.ORIGINAL]!!.encryption?.toKeyPair())
    }

    fun canAutoDownloadMessage(databaseMessage: ConversationMessage): Boolean {
        val modCtx = this@MediaDownloader.context
        if (modCtx.config.downloader.preventSelfAutoDownload.get() && databaseMessage.senderId == modCtx.database.myUserId) return false
        return canUseRule(databaseMessage.clientConversationId!!)
    }

    private suspend fun handleOperaMedia(paramMap: ParamMap, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>, forceDownload: Boolean, forceAllowDuplicate: Boolean = false, isBatch: Boolean = false) {
        val modCtx = this@MediaDownloader.context
        resolveViewerMessageContextFromParamMap(paramMap)?.takeIf { forceDownload || shouldAutoDownload("friend_snaps") }?.let { messageContext ->
            val msg = modCtx.database.getConversationMessageFromId(messageContext.clientMessageId) ?: return@let
            if (!forceDownload && (!canUseRule(msg.clientConversationId!!) || (modCtx.config.downloader.preventSelfAutoDownload.get() && msg.senderId == modCtx.database.myUserId))) return@let
            val author = modCtx.database.getFriendInfo(msg.senderId!!) ?: return@let
            downloadOperaMedia(provideDownloadManagerClient("${msg.clientConversationId}${msg.senderId}${msg.serverMessageId}", author.usernameForSorting!!, msg.creationTimestamp, MediaDownloadSource.CHAT_MEDIA, author, forceAllowDuplicate, isBatch), mediaInfoMap, paramMap)
            return
        }
        paramMap["PLAYLIST_V2_GROUP"]?.takeIf { forceDownload || shouldAutoDownload("friend_stories") }?.let {
            val storyUserId = paramMap["TOPIC_SNAP_CREATOR_USER_ID"]?.toString() ?: paramMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()?.substringAfter("userId=")?.substringBefore(",")
            val author = modCtx.database.getFriendInfo(storyUserId ?: modCtx.database.myUserId) ?: return@let
            if (!forceDownload && ((modCtx.config.downloader.preventSelfAutoDownload.get() && author.userId == modCtx.database.myUserId) || !canUseRule(author.userId!!))) return@let
            downloadOperaMedia(provideDownloadManagerClient(paramMap["MEDIA_ID"].toString(), author.usernameForSorting!!, null, MediaDownloadSource.STORY, author, forceAllowDuplicate, isBatch), mediaInfoMap, paramMap)
            return
        }
        val snapSource = paramMap["SNAP_SOURCE"].toString()
        if (snapSource == "SINGLE_SNAP_STORY" && (forceDownload || shouldAutoDownload("spotlight"))) {
            downloadOperaMedia(provideDownloadManagerClient(paramMap["SNAP_ID"].toString(), paramMap["CREATOR_DISPLAY_NAME"].toString(), null, MediaDownloadSource.SPOTLIGHT, null, forceAllowDuplicate, isBatch), mediaInfoMap, paramMap); return
        }
        if (!forceDownload && !shouldAutoDownload("public_stories")) return
        val author = (paramMap["USER_ID"]?.let { modCtx.database.getFriendInfo(it.toString())?.mutableUsername } ?: paramMap["USERNAME"]?.toString()?.substringAfter("value=")?.substringBefore(")") ?: "unknown").sanitizeForPath()
        downloadOperaMedia(provideDownloadManagerClient(paramMap["SNAP_ID"].toString(), author, null, MediaDownloadSource.PUBLIC_STORY, null, forceAllowDuplicate, isBatch), mediaInfoMap, paramMap)
    }

    private fun shouldAutoDownload(keyFilter: String? = null): Boolean = this@MediaDownloader.context.config.downloader.autoDownloadSources.get().any { keyFilter == null || it.contains(keyFilter, true) }

    override fun init() {
        val modCtx = this@MediaDownloader.context
        if (getRuleState() == null) return
        onNextActivityCreate {
            modCtx.mappings.useMapper(OperaPageViewControllerMapper::class) {
                arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                    classReference.get()?.hook(methodName.get() ?: return@forEach, HookStage.AFTER) { param ->
                        val viewState = (param.thisObject() as Any).getObjectField(viewStateField.get()!!).toString()
                        if (viewState != "FULLY_DISPLAYED" && viewState != "DISPLAYED") return@hook
                        val operaLayerList = (param.thisObject() as Any).getObjectField(layerListField.get()!!) as ArrayList<*>
                        val layerParamMaps = operaLayerList.mapNotNull { l -> l?.let { runCatching { Layer(it).paramMap }.getOrNull() } }
                        val mediaParamMap = if (useModernOperaViewerContext) {
                            layerParamMaps.firstOrNull { it.containsKey("MESSAGE_ID") && (it.containsKey("image_media_info") || it.containsKey("video_media_info_list")) }
                                ?: layerParamMaps.firstOrNull { it.containsKey("image_media_info") || it.containsKey("video_media_info_list") }
                        } else layerParamMaps.firstOrNull { it.containsKey("image_media_info") || it.containsKey("video_media_info_list") } ?: return@hook
                        val mediaInfoMap = mutableMapOf<SplitMediaAssetType, MediaInfo>()
                        val isVideo = mediaParamMap!!.containsKey("video_media_info_list")
                        mediaInfoMap[SplitMediaAssetType.ORIGINAL] = MediaInfo(mediaParamMap[if (isVideo) "video_media_info_list" else "image_media_info"]!!)
                        if (modCtx.config.downloader.mergeOverlays.get() && mediaParamMap.containsKey("overlay_image_media_info")) mediaInfoMap[SplitMediaAssetType.OVERLAY] = MediaInfo(mediaParamMap["overlay_image_media_info"]!!)
                        if (shouldAutoDownload() && lastSeenMediaInfoMap?.get(SplitMediaAssetType.ORIGINAL)?.uri == mediaInfoMap[SplitMediaAssetType.ORIGINAL]?.uri) return@hook
                        lastSeenMapParams = mediaParamMap; lastSeenMediaInfoMap = mediaInfoMap
                        if (pendingBatchDownloadIndices != null) { modCtx.coroutineScope.launch { processNextBatchDownload(mediaParamMap, mediaInfoMap) }; return@hook }
                        if (!shouldAutoDownload()) return@hook
                        modCtx.coroutineScope.launch { runCatching { handleOperaMedia(mediaParamMap, mediaInfoMap, false) } }
                    }
                }
            }
        }
    }

    suspend fun downloadMessageId(messageId: Long, forceAllowDuplicate: Boolean = false, isPreview: Boolean = false, forceDownloadFirst: Boolean = false) {
        val modCtx = this@MediaDownloader.context
        val message = modCtx.database.getConversationMessageFromId(messageId) ?: throw Exception("Message not found")
        val friendInfo = modCtx.database.getFriendInfo(message.senderId!!) ?: throw Exception("Friend not found")
        val decodedAttachments = MessageDecoder.decode(ProtoReader(message.messageContent!!)).toMutableList()
        if (decodedAttachments.isEmpty()) { modCtx.shortToast(translations["no_attachments_toast"] ?: "No Attachments"); return }
        
        if (!isPreview) {
            if (forceDownloadFirst || decodedAttachments.size == 1 || modCtx.isMainActivityPaused) {
                downloadMessageAttachments(friendInfo, message, friendInfo.usernameForSorting!!, listOf(decodedAttachments.first()), forceAllowDuplicate)
            } else {
                withContext(Dispatchers.Main) { showAttachmentSelectionDialog(friendInfo, message, decodedAttachments, forceAllowDuplicate) }
            }
            return
        }
        
        if (decodedAttachments.size == 1) { previewAttachment(decodedAttachments.first()); return }
        
        withContext(Dispatchers.Main) {
            val mainActivity = modCtx.mainActivity ?: return@withContext
            ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
                var selected = 0
                setSingleChoiceItems(decodedAttachments.mapIndexed { i, a -> "${i + 1}: ${translations["attachment_type.${a.type.key}"] ?: a.type.key}" }.toTypedArray(), 0) { _, w -> selected = w }
                setPositiveButton(modCtx.translation["chat_action_menu.preview_button"] ?: "Preview") { _, _ -> previewAttachment(decodedAttachments[selected]) }
            }.show()
        }
    }

    private fun downloadMessageAttachments(f: FriendInfo, m: ConversationMessage, author: String, attachments: List<DecodedAttachment>, forceDup: Boolean) {
        attachments.forEach { a ->
            runCatching { provideDownloadManagerClient("${m.clientConversationId}${m.senderId}${m.serverMessageId}", author, m.creationTimestamp, MediaDownloadSource.CHAT_MEDIA, f, forceDup).downloadInputMedias(arrayOf(a.createInputMedia()!!)) }
        }
    }

    private fun showAttachmentSelectionDialog(friendInfo: FriendInfo, message: ConversationMessage, attachments: List<DecodedAttachment>, forceAllowDuplicate: Boolean) {
        val modCtx = this@MediaDownloader.context
        val mainActivity = modCtx.mainActivity ?: return
        ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
            val selected = mutableListOf<Int>().apply { addAll(attachments.indices) }
            setMultiChoiceItems(attachments.mapIndexed { i, a -> "${i + 1}: ${translations["attachment_type.${a.type.key}"] ?: a.type.key}" }.toTypedArray(), attachments.map { true }.toBooleanArray()) { _, which, isChecked -> if (isChecked) selected.add(which) else selected.remove(which) }
            setPositiveButton(modCtx.translation["button.download"] ?: "Download") { _, _ -> downloadMessageAttachments(friendInfo, message, friendInfo.usernameForSorting!!, selected.map { attachments[it] }, forceAllowDuplicate) }
        }.show()
    }

    @SuppressLint("SetTextI18n")
    private fun previewAttachment(attachment: DecodedAttachment) {
        val modCtx = this@MediaDownloader.context
        var previewBitmap: Bitmap? = null
        val previewCoroutine = modCtx.coroutineScope.launch {
            runCatching {
                attachment.openStream { attachmentStream, _ ->
                    val downloadedMediaList = mutableMapOf<SplitMediaAssetType, ByteArray>()
                    MediaDownloaderHelper.getSplitElements(attachmentStream!!) { type, inputStream -> downloadedMediaList[type] = inputStream.readBytes() }
                    val originalMedia = downloadedMediaList[SplitMediaAssetType.ORIGINAL] ?: return@openStream
                    val overlay = downloadedMediaList[SplitMediaAssetType.OVERLAY]
                    var bitmap = PreviewUtils.createPreview(originalMedia, isVideo = FileType.fromByteArray(originalMedia).isVideo) ?: throw Exception("preview is null")
                    overlay?.also { bitmap = PreviewUtils.mergeBitmapOverlay(bitmap, BitmapFactory.decodeByteArray(it, 0, it.size)) }
                    previewBitmap = bitmap
                }
            }
        }
        modCtx.runOnUiThread {
            val mainActivity = modCtx.mainActivity ?: return@runOnUiThread
            val viewGroup = LinearLayout(modCtx.androidContext).apply { gravity = Gravity.CENTER; addView(ProgressBar(modCtx.androidContext).apply { isIndeterminate = true }) }
            ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
                setOnDismissListener { previewCoroutine.cancel() }
                previewCoroutine.invokeOnCompletion { cause ->
                    modCtx.runOnUiThread {
                        viewGroup.removeAllViews()
                        if (cause != null) { viewGroup.addView(TextView(modCtx.androidContext).apply { text = "Failed to create preview"; setPadding(30, 30, 30, 30) }); return@runOnUiThread }
                        viewGroup.addView(ImageView(modCtx.androidContext).apply { setImageBitmap(previewBitmap); adjustViewBounds = true })
                    }
                }
                val dialog = show()
                dialog.setContentView(viewGroup)
                dialog.window?.setLayout(modCtx.androidContext.resources.displayMetrics.widthPixels, modCtx.androidContext.resources.displayMetrics.heightPixels)
            }
        }
    }

    fun downloadProfilePicture(url: String, author: String) {
        provideDownloadManagerClient(url.hashCode().toString(16).replaceFirst("-", ""), author, null, MediaDownloadSource.PROFILE_PICTURE).downloadSingleMedia(url, DownloadMediaType.REMOTE_MEDIA)
    }

    fun onMessageActionMenu(isPreviewMode: Boolean, forceAllowDuplicate: Boolean = false) {
        val modCtx = this@MediaDownloader.context
        val messaging = modCtx.feature(Messaging::class)
        if (messaging.openedConversationUUID == null) return
        modCtx.coroutineScope.launch { downloadMessageId(messaging.lastFocusedMessageId, forceAllowDuplicate, isPreviewMode) }
    }
}
