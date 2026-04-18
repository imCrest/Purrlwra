package cock.crest.purrfectsnap.lite.core.messaging

import android.util.Base64InputStream
import android.util.Base64OutputStream
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.common.BuildConfig
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LoggedMessage
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.database.impl.FriendFeedEntry
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.snap.MediaDownloaderHelper
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.features.impl.spying.MessageLogger
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder.DecodedAttachment
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder.MessageDecoder
import cock.crest.purrfectsnap.lite.core.util.hook.findRestrictedConstructor
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipFile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class ConversationExporter(
    private val context: ModContext,
    private val friendFeedEntry: FriendFeedEntry,
    private val conversationParticipants: Map<String, FriendInfo>,
    private val exportParams: ExportParams,
    private val cacheFolder: File,
    private val outputFile: File
) {
    lateinit var printLog: (Any?) -> Unit

    private val downloadThreadExecutor = Executors.newFixedThreadPool(4)
    private val writeThreadExecutor = Executors.newSingleThreadExecutor()

    private val conversationJsonDataFile by lazy { cacheFolder.resolve("messages_${friendFeedEntry.key}.json") }
    private val jsonDataWriter by lazy { JsonWriter(conversationJsonDataFile.writer()) }
    private val outputFileStream by lazy { outputFile.outputStream() }
    private val participants = mutableMapOf<String, Int>()

    private val newBase64OutputStream by lazy {
        Base64OutputStream::class.java.findRestrictedConstructor {
            it.parameterTypes.size == 3 &&
            it.parameterTypes[0] == OutputStream::class.java &&
            it.parameterTypes[1] == Int::class.javaPrimitiveType &&
            it.parameterTypes[2] == Boolean::class.javaPrimitiveType
        } ?: throw Throwable("Failed to find Base64OutputStream constructor")
    }

    private val newBase64InputStream by lazy {
        Base64InputStream::class.java.findRestrictedConstructor {
            it.parameterTypes.size == 3 &&
            it.parameterTypes[0] == InputStream::class.java &&
            it.parameterTypes[1] == Int::class.javaPrimitiveType &&
            it.parameterTypes[2] == Boolean::class.javaPrimitiveType
        } ?: throw Throwable("Failed to find Base64InputStream constructor")
    }

    fun init() {
        when (exportParams.exportFormat) {
            ExportFormat.TEXT -> {
                outputFileStream.write("Conversation id: ${friendFeedEntry.key}\n".toByteArray())
                outputFileStream.write("Conversation name: ${friendFeedEntry.feedDisplayName}\n".toByteArray())
                outputFileStream.write("Participants:\n".toByteArray())
                conversationParticipants.forEach { (userId, friendInfo) ->
                    outputFileStream.write("  $userId: ${friendInfo.displayName}\n".toByteArray())
                }
                outputFileStream.write("\n\n".toByteArray())
            }
            else -> {
                jsonDataWriter.isHtmlSafe = true
                jsonDataWriter.serializeNulls = true

                jsonDataWriter.beginObject()
                jsonDataWriter.name("conversationId").value(friendFeedEntry.key)
                jsonDataWriter.name("conversationName").value(friendFeedEntry.feedDisplayName)
                exportParams.colorSeedHex?.let { colorSeed ->
                    jsonDataWriter.name("colorSeed").value(colorSeed)
                }

                var index = 0

                jsonDataWriter.name("participants").apply {
                    beginObject()
                    conversationParticipants.forEach { (userId, friendInfo) ->
                        jsonDataWriter.name(userId).beginObject()
                        jsonDataWriter.name("id").value(index)
                        jsonDataWriter.name("userId").value(userId)
                        jsonDataWriter.name("displayName").value(friendInfo.displayName)
                        jsonDataWriter.name("username").value(friendInfo.usernameForSorting)
                        jsonDataWriter.name("bitmojiSelfieId").value(friendInfo.bitmojiSelfieId)
                        jsonDataWriter.endObject()
                        participants[userId] = index++
                    }
                    endObject()
                }

                exportParams.colorOverrides?.takeIf { it.isNotEmpty() }?.let { overrides ->
                    jsonDataWriter.name("userColors").beginObject()
                    overrides.forEach { (userId, color) ->
                        jsonDataWriter.name(userId).value(color)
                    }
                    jsonDataWriter.endObject()
                }

                jsonDataWriter.name("messages").beginArray()

                if (exportParams.exportFormat != ExportFormat.HTML) return
                outputFileStream.write("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta http-equiv="X-UA-Compatible" content="IE=edge">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title></title>
                    </head>
                """.trimIndent().toByteArray())

                outputFileStream.write("<!-- This file was generated by PurrfectSnap ${BuildConfig.VERSION_NAME} -->\n</head>".toByteArray())

                outputFileStream.flush()
            }
        }
    }

    private val downloadedMediaIdCache = CopyOnWriteArraySet<String>()
    private val pendingDownloadMediaIdCache = CopyOnWriteArraySet<String>()

    data class LoggedMessageExportData(
        val orderKey: Long,
        val senderId: String,
        val senderUsername: String?,
        val contentType: ContentType,
        val contentBytes: ByteArray?,
        val createdTimestamp: Long,
        val readTimestamp: Long?,
        val attachments: List<DecodedAttachment>,
        val isDeleted: Boolean
    )

    private fun downloadMedia(attachments: List<DecodedAttachment>) {
        downloadThreadExecutor.execute {
            attachments.forEach decode@{ attachment ->
                if (attachment.mediaUniqueId in downloadedMediaIdCache || attachment.mediaUniqueId in pendingDownloadMediaIdCache) return@decode
                pendingDownloadMediaIdCache.add(attachment.mediaUniqueId!!)
                for (i in 0..5) {
                    printLog("downloading ${attachment.boltKey ?: attachment.directUrl}... (attempt ${i + 1}/5)")
                    runCatching {
                        runBlocking {
                            attachment.openStream { downloadedInputStream, _ ->
                                MediaDownloaderHelper.getSplitElements(downloadedInputStream!!) { type, splitInputStream ->
                                    val mediaKey = "${type}_${attachment.mediaUniqueId}"
                                    val bufferedInputStream = BufferedInputStream(splitInputStream)
                                    val fileType = MediaDownloaderHelper.getFileType(bufferedInputStream)
                                    val mediaFile = cacheFolder.resolve("$mediaKey.${fileType.fileExtension}")

                                    mediaFile.outputStream().use { fos ->
                                        bufferedInputStream.copyTo(fos)
                                    }

                                    writeThreadExecutor.execute {
                                        outputFileStream.write("<div class=\"media-$mediaKey\"><!-- ".toByteArray())
                                        mediaFile.inputStream().use {
                                            val deflateInputStream = DeflaterInputStream(it, Deflater(Deflater.BEST_SPEED, true))
                                            (newBase64InputStream.newInstance(
                                                deflateInputStream,
                                                android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP,
                                                true
                                            ) as InputStream).copyTo(outputFileStream)
                                            outputFileStream.write(" --></div>\n".toByteArray())
                                            outputFileStream.flush()
                                        }
                                    }
                                }
                                writeThreadExecutor.execute {
                                    downloadedMediaIdCache.add(attachment.mediaUniqueId!!)
                                }
                            }
                        }
                        return@decode
                    }.onFailure {
                        downloadedMediaIdCache.remove(attachment.mediaUniqueId!!)
                        printLog("failed to download media ${attachment.boltKey}. retrying...")
                        it.printStackTrace()
                    }
                }
                pendingDownloadMediaIdCache.remove(attachment.mediaUniqueId!!)
            }
        }
    }

    private fun writeJsonMessage(
        orderKey: Long?,
        senderId: String?,
        contentType: ContentType,
        savedBy: List<String>,
        seenBy: List<String>,
        openedBy: List<String>,
        reactions: Map<String, Long?>,
        createdTimestamp: Long?,
        readTimestamp: Long?,
        serializedContent: String?,
        rawContent: ByteArray?,
        attachments: List<DecodedAttachment>,
        isDeleted: Boolean
    ) {
        jsonDataWriter.apply {
            beginObject()
            name("orderKey").value(orderKey)
            name("senderId").value(participants.getOrDefault(senderId ?: "", -1))
            name("type").value(contentType.toString())

            fun addUserList(name: String, list: List<String>) {
                name(name).beginArray()
                list.map { participants.getOrDefault(it, -1) }.forEach { value(it) }
                endArray()
            }

            addUserList("savedBy", savedBy)
            addUserList("seenBy", seenBy)
            addUserList("openedBy", openedBy)

            name("reactions").beginObject()
            reactions.forEach { (userId, reactionId) ->
                name(participants.getOrDefault(userId, -1).toString()).value(reactionId)
            }
            endObject()

            name("createdTimestamp").value(createdTimestamp)
            name("readTimestamp").value(readTimestamp)
            name("isDeleted").value(isDeleted)
            if (serializedContent != null) {
                name("serializedContent").value(serializedContent)
            } else {
                name("serializedContent").nullValue()
            }
            if (rawContent != null) {
                name("rawContent").value(Base64.UrlSafe.encode(rawContent))
            } else {
                name("rawContent").nullValue()
            }
            name("attachments").beginArray()
            attachments.forEach attachments@{ attachment ->
                beginObject()
                name("url").value(attachment.boltKey ?: attachment.directUrl)
                name("key").value(attachment.mediaUniqueId)
                name("type").value(attachment.type.toString())
                name("encryption").apply {
                    attachment.attachmentInfo?.encryption?.let { encryption ->
                        beginObject()
                        name("key").value(encryption.key)
                        name("iv").value(encryption.iv)
                        endObject()
                    } ?: nullValue()
                }
                endObject()
            }
            endArray()
            endObject()
            flush()
        }
    }

    fun readMessage(message: Message) {
        if (exportParams.exportFormat == ExportFormat.TEXT) {
            val (displayName, senderUsername) = conversationParticipants[message.senderId.toString()]?.let {
                it.displayName to it.mutableUsername
            } ?: ("" to message.senderId.toString())

            val date = DateFormat.getDateTimeInstance().format(Date(message.messageMetadata!!.createdAt ?: -1))
            outputFileStream.write("[$date] - $displayName ($senderUsername): ${message.serialize() ?: message.messageContent?.contentType?.name}\n".toByteArray(Charsets.UTF_8))
            return
        }
        val contentType = message.messageContent?.contentType ?: return

        val attachments = MessageDecoder.decode(message.messageContent!!)
        if (exportParams.downloadMedias && (contentType == ContentType.NOTE ||
                    contentType == ContentType.SNAP ||
                    contentType == ContentType.EXTERNAL_MEDIA ||
                    contentType == ContentType.STICKER ||
                    contentType == ContentType.SHARE ||
                    contentType == ContentType.MAP_REACTION)
            ) {
            downloadMedia(attachments)
        }

        writeJsonMessage(
            orderKey = message.orderKey,
            senderId = message.senderId.toString(),
            contentType = contentType,
            savedBy = message.messageMetadata!!.savedBy!!.map { it.toString() },
            seenBy = message.messageMetadata!!.seenBy!!.map { it.toString() },
            openedBy = message.messageMetadata!!.openedBy!!.map { it.toString() },
            reactions = message.messageMetadata!!.reactions!!.associate { it.userId.toString() to it.reactionId },
            createdTimestamp = message.messageMetadata!!.createdAt,
            readTimestamp = message.messageMetadata!!.readAt,
            serializedContent = message.serialize(),
            rawContent = message.messageContent!!.content,
            attachments = attachments,
            isDeleted = false
        )
    }

    fun parseLoggedMessage(loggedMessage: LoggedMessage): LoggedMessageExportData? {
        val messageObject = runCatching {
            JsonParser.parseString(String(loggedMessage.messageData, Charsets.UTF_8)).asJsonObject
        }.getOrNull() ?: return null

        val messageContent = messageObject.getAsJsonObject("mMessageContent") ?: return null
        val contentBytes = messageContent.getAsJsonArray("mContent")?.map { it.asByte }?.toByteArray()
        val contentType = messageContent.getAsJsonPrimitive("mContentType")?.asString?.let {
            runCatching { ContentType.valueOf(it) }.getOrNull()
        } ?: contentBytes?.let { ContentType.fromMessageContainer(ProtoReader(it)) } ?: ContentType.UNKNOWN

        val metadata = messageObject.getAsJsonObject("mMetadata")
        val createdTimestamp = metadata?.getAsJsonPrimitive("mCreatedAt")?.asLong ?: loggedMessage.sendTimestamp
        val readTimestamp = metadata?.getAsJsonPrimitive("mReadAt")?.asLong
        val orderKey = messageObject.getAsJsonPrimitive("mOrderKey")?.asLong ?: loggedMessage.messageId
        val attachments = runCatching { MessageDecoder.decode(messageContent) }.getOrDefault(emptyList())
        val isDeleted = runCatching {
            val messageLogger = context.feature(MessageLogger::class)
            messageLogger.isEnabled && messageLogger.isLoggedMessageDeleted(loggedMessage.messageId)
        }.getOrDefault(false)

        return LoggedMessageExportData(
            orderKey = orderKey,
            senderId = loggedMessage.userId,
            senderUsername = loggedMessage.username,
            contentType = contentType,
            contentBytes = contentBytes,
            createdTimestamp = createdTimestamp,
            readTimestamp = readTimestamp,
            attachments = attachments,
            isDeleted = isDeleted
        )
    }

    fun readLoggedMessage(data: LoggedMessageExportData) {
        val serializedContent = data.contentBytes?.getMessageText(data.contentType)

        if (exportParams.exportFormat == ExportFormat.TEXT) {
            val (displayName, senderUsername) = conversationParticipants[data.senderId]?.let {
                it.displayName to it.mutableUsername
            } ?: (data.senderUsername ?: data.senderId) to (data.senderUsername ?: data.senderId)

            val date = DateFormat.getDateTimeInstance().format(Date(data.createdTimestamp))
            outputFileStream.write("[$date] - $displayName ($senderUsername): ${serializedContent ?: data.contentType.name}\n".toByteArray(Charsets.UTF_8))
            return
        }

        if (exportParams.downloadMedias && (data.contentType == ContentType.NOTE ||
                    data.contentType == ContentType.SNAP ||
                    data.contentType == ContentType.EXTERNAL_MEDIA ||
                    data.contentType == ContentType.STICKER ||
                    data.contentType == ContentType.SHARE ||
                    data.contentType == ContentType.MAP_REACTION)
            ) {
            downloadMedia(data.attachments)
        }

        writeJsonMessage(
            orderKey = data.orderKey,
            senderId = data.senderId,
            contentType = data.contentType,
            savedBy = emptyList(),
            seenBy = emptyList(),
            openedBy = emptyList(),
            reactions = emptyMap(),
            createdTimestamp = data.createdTimestamp,
            readTimestamp = data.readTimestamp,
            serializedContent = serializedContent,
            rawContent = data.contentBytes,
            attachments = data.attachments,
            isDeleted = data.isDeleted
        )
    }

    fun awaitDownload() {
        downloadThreadExecutor.shutdown()
        downloadThreadExecutor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS)
        writeThreadExecutor.shutdown()
        writeThreadExecutor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS)
    }

    fun close() {
        if (exportParams.exportFormat != ExportFormat.TEXT) {
            jsonDataWriter.endArray()
            jsonDataWriter.endObject()
            jsonDataWriter.flush()
            jsonDataWriter.close()
        }

        if (exportParams.exportFormat == ExportFormat.JSON) {
            conversationJsonDataFile.inputStream().use {
                it.copyTo(outputFileStream)
            }
        }

        if (exportParams.exportFormat == ExportFormat.HTML) {
            //write the json file
            outputFileStream.write("<script type=\"application/json\" class=\"exported_content\">".toByteArray())

            (newBase64OutputStream.newInstance(
                outputFileStream,
                android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP,
                true
            ) as OutputStream).let { outputStream ->
                val deflateOutputStream = DeflaterOutputStream(outputStream, Deflater(Deflater.BEST_SPEED, true), true)
                conversationJsonDataFile.inputStream().use { fileInputStream ->
                    val buffer = ByteArray(4096)
                    var length: Int
                    while (fileInputStream.read(buffer).also { length = it } > 0) {
                        deflateOutputStream.write(buffer, 0, length)
                        deflateOutputStream.flush()
                    }
                }
                deflateOutputStream.finish()
                outputStream.flush()
            }

            outputFileStream.write("</script>\n".toByteArray())
            printLog("writing template...")

            runCatching {
                ZipFile(context.bridgeClient.getApplicationApkPath()).use { apkFile ->
                    //export rawinflate.js
                    apkFile.getEntry("assets/web/rawinflate.js")?.let { entry ->
                        outputFileStream.write("<script>".toByteArray())
                        apkFile.getInputStream(entry).copyTo(outputFileStream)
                        outputFileStream.write("</script>\n".toByteArray())
                    }

                    //export avenir next font
                    apkFile.getEntry("assets/web/avenir_next_medium.ttf")?.let { entry ->
                        val encodedFontData = Base64.Default.encode(apkFile.getInputStream(entry).readBytes())
                        outputFileStream.write("""
                            <style>
                                @font-face {
                                    font-family: 'Avenir Next';
                                    src: url('data:font/truetype;charset=utf-8;base64, $encodedFontData');
                                    font-weight: normal;
                                    font-style: normal;
                                }
                            </style>
                        """.trimIndent().toByteArray())
                    }

                    apkFile.getEntry("assets/web/export_template.html")?.let { entry ->
                        apkFile.getInputStream(entry).copyTo(outputFileStream)
                    }

                    apkFile.close()
                }
            }.onFailure {
                throw Throwable("Failed to read template from apk", it)
            }

            outputFileStream.write("</html>".toByteArray())
        }

        outputFileStream.flush()
        outputFileStream.close()
    }
}
