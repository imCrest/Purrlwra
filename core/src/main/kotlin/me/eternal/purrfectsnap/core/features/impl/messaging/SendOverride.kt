package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.*
import cock.crest.purrfectsnap.lite.bridge.task.TaskListener
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoEditor
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoWriter
import cock.crest.purrfectsnap.lite.core.event.events.impl.MediaUploadEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.NativeUnaryCallEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.MediaFilePicker
import cock.crest.purrfectsnap.lite.core.messaging.MessageSender
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageContent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageDestinations
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.util.CallbackBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@OptIn(ExperimentalMaterial3Api::class)
class SendOverride : Feature("Send Override") {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "scheduled_send"
        private val internalMultipartSend = ThreadLocal.withInitial { false }
        private var queuedOriginalItemRepeatCount = 0
        private var queuedOriginalItemRepeatOverrideType: String? = null

        private fun queueOriginalItemRepeats(repeatCount: Int, overrideType: String) {
            queuedOriginalItemRepeatCount = repeatCount
            queuedOriginalItemRepeatOverrideType = overrideType
            MediaFilePicker.setQueuedOverrideType(overrideType)
        }

        private fun clearQueuedOriginalItemRepeats() {
            queuedOriginalItemRepeatCount = 0
            queuedOriginalItemRepeatOverrideType = null
        }

        private fun handleQueuedOriginalItemRepeatSuccess(): Boolean {
            if (queuedOriginalItemRepeatCount <= 0) {
                clearQueuedOriginalItemRepeats()
                return false
            }

            val overrideType = queuedOriginalItemRepeatOverrideType ?: run {
                clearQueuedOriginalItemRepeats()
                return false
            }

            queuedOriginalItemRepeatCount--
            MediaFilePicker.setQueuedOverrideType(overrideType)
            val result = MediaFilePicker.sendReusableOriginalItem()
            if (!result) {
                queuedOriginalItemRepeatCount++
                clearQueuedOriginalItemRepeats()
            }
            return result
        }
    }
    
    private var selectedType by mutableStateOf("SNAP")
    private var disableSplitForCurrentSend by mutableStateOf(false)
    private var customDuration by mutableFloatStateOf(10f)
    private var scheduledTime by mutableStateOf<Long?>(null)
    private var showClockPicker by mutableStateOf(false)
    private var clockPickerHour by mutableIntStateOf(12)
    private var clockPickerMinute by mutableIntStateOf(0)
    private var notificationIdCounter = 1000
    private val backgroundHookLock = Any()
    private var backgroundHookRefs = 0
    private var backgroundHooks: List<Hooker.HookHandle>? = null
    private fun acquireScheduledSendBackground(): () -> Unit {
        if (!context.config.messaging.scheduledSendAllowRunningInBackground.get()) return {}
        var enableFailed = false
        synchronized(backgroundHookLock) {
            backgroundHookRefs++
            if (backgroundHookRefs == 1) {
                if (!enableScheduledSendBackgroundLocked()) {
                    backgroundHookRefs--
                    enableFailed = true
                }
            }
        }
        if (enableFailed) return {}
        var released = false
        return {
            synchronized(backgroundHookLock) {
                if (released) return@synchronized
                released = true
                if (backgroundHookRefs > 0) backgroundHookRefs--
                if (backgroundHookRefs == 0) {
                    backgroundHooks?.forEach { it.unhook() }
                    backgroundHooks = null
                }
            }
        }
    }

    private fun enableScheduledSendBackgroundLocked(): Boolean {
        return runCatching {
            val duplexClass = findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy")
            val appStateMethod = duplexClass.methods.firstOrNull { it.name == "appStateChanged" } ?: return false
            val hooks = mutableListOf<Hooker.HookHandle>()
            hooks.addAll(
                duplexClass.hook("appStateChanged", HookStage.BEFORE) { param ->
                    if (param.arg<Any>(0).toString() == "INACTIVE") param.setResult(null)
                }
            )
            hooks.addAll(
                duplexClass.hookConstructor(HookStage.AFTER) { param ->
                    val activeValue = appStateMethod.parameterTypes[0].enumConstants?.firstOrNull { it.toString() == "ACTIVE" }
                        ?: return@hookConstructor
                    appStateMethod.invoke(param.thisObject(), activeValue)
                }
            )
            backgroundHooks = hooks
            true
        }.getOrElse {
            context.log.error("Failed to enable scheduled send background mode", it)
            false
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.androidContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scheduled Send",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Notifications for scheduled snap sends"
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, content: String) {
        val notificationManager = context.androidContext.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(context.androidContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(notificationIdCounter++, builder.build())
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun init() {
        createNotificationChannel()
        
        val stripMediaMetadata = context.config.messaging.stripMediaMetadata.get()
        var postSavePolicy: Int? = null

        val configOverrideType = context.config.messaging.galleryMediaSendOverride.mode.getNullable()?.toString()
        if (configOverrideType == null && stripMediaMetadata.isEmpty()) return

        context.event.subscribe(MediaUploadEvent::class) { event ->
            // Handle audio notes separately since they don't have path 11, 5
            if (stripMediaMetadata.isNotEmpty() && 
                (event.localMessageContent.contentType == ContentType.NOTE || 
                 stripMediaMetadata.contains("remove_audio_note_duration") || 
                 stripMediaMetadata.contains("remove_audio_note_transcript_capability"))) {
                event.onMediaUploaded { result ->
                    if (result.messageContent.contentType == ContentType.NOTE) {
                        val contentReader = ProtoReader(result.messageContent.content!!)
                        result.messageContent.content = ProtoEditor(result.messageContent.content!!).apply {
                            // Check which path structure exists - try both to be safe
                            val hasFullPath = contentReader.followPath(4, 4, 6, 1, 1) != null
                            val hasDirectPath = contentReader.followPath(6, 1, 1) != null
                            
                            if (stripMediaMetadata.contains("remove_audio_note_duration")) {
                                // Audio note duration is at field 13 (confirmed from MessageDecoder line 94 and MessageSender line 27)
                                if (hasFullPath) {
                                    edit(4, 4, 6, 1, 1) {
                                        remove(13)
                                    }
                                }
                                if (hasDirectPath || !hasFullPath) {
                                    edit(6, 1, 1) {
                                        remove(13)
                                    }
                                }
                            }
                            if (stripMediaMetadata.contains("remove_audio_note_transcript_capability")) {
                                if (hasFullPath) {
                                    edit(4, 4, 6, 1) {
                                        remove(3) // locale string
                                    }
                                }
                                if (hasDirectPath || !hasFullPath) {
                                    edit(6, 1) {
                                        remove(3) // locale string
                                    }
                                }
                                runCatching {
                                    result.messageContent.instanceNonNull().setObjectField("mAllowsTranscription", false)
                                }
                            }
                        }.toByteArray()
                    }
                }
            }

            ProtoReader(event.localMessageContent.content!!).followPath(11, 5)?.let { snapDocPlayback ->
                event.onMediaUploaded { result ->
                    result.messageContent.content = ProtoEditor(result.messageContent.content!!).apply {
                        edit(11, 5) {
                            edit(1) {
                                edit(1) {
                                    snapDocPlayback.getVarInt(2, 99)?.let { customDuration ->
                                        remove(15)
                                        addVarInt(15, customDuration)
                                    }
                                    remove(27)
                                    remove(26)
                                    addBuffer(26, byteArrayOf())
                                }
                            }

                            // set back the original snap duration
                            snapDocPlayback.getByteArray(2)?.let {
                                val originalHasSound = firstOrNull(2)?.toReader()?.getVarInt(5)
                                remove(2)
                                addBuffer(2, it)

                                originalHasSound?.let { hasSound ->
                                    edit(2) {
                                        remove(5)
                                        addVarInt(5, hasSound)
                                    }
                                }
                            }
                        }

                        if (stripMediaMetadata.isNotEmpty()) {
                            when (result.messageContent.contentType) {
                                ContentType.SNAP, ContentType.EXTERNAL_MEDIA -> {
                                    edit(*(if (result.messageContent.contentType == ContentType.SNAP) intArrayOf(11) else intArrayOf(3, 3))) {
                                        if (stripMediaMetadata.contains("hide_caption_text")) {
                                            edit(5) {
                                                editEach(1) {
                                                    remove(2)
                                                }
                                            }
                                        }
                                        if (stripMediaMetadata.contains("hide_snap_filters")) {
                                            remove(9)
                                            remove(11)
                                        }
                                        if (stripMediaMetadata.contains("hide_extras")) {
                                            remove(13)
                                            edit(5, 1) {
                                                remove(2)
                                            }
                                        }
                                    }
                                }
                                ContentType.NOTE -> {
                                    if (stripMediaMetadata.contains("remove_audio_note_duration")) {
                                        edit(6, 1, 1) {
                                            remove(13)
                                        }
                                    }
                                    if (stripMediaMetadata.contains("remove_audio_note_transcript_capability")) {
                                        edit(6, 1) {
                                            remove(3)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }

                        edit(11, 5, 2) {
                            remove(99)
                        }
                    }.toByteArray()
                }
            }
        }

        if (configOverrideType == null) return

        context.event.subscribe(NativeUnaryCallEvent::class, priority = 100) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            postSavePolicy?.let { savePolicy ->
                context.log.verbose("postSavePolicy=$savePolicy")
                val protoReader = ProtoReader(event.buffer)
                event.buffer = ProtoEditor(event.buffer).apply {
                    // Handle chat messages (field 4)
                    if (protoReader.followPath(4) != null) {
                        edit(4) {
                            remove(7)
                            addVarInt(7, savePolicy)
                        }

                        // remove Keep Snaps in Chat ability
                        if (savePolicy == 1/* PROHIBITED */) {
                            edit(6, 9) {
                                remove(1)
                            }
                        }
                    }
                    
                    // Handle NOTE messages (field 6) - audio notes
                    // Check both root level (6) and nested under media (4, 4, 6)
                    val noteAtRoot = protoReader.followPath(6) != null
                    val noteNested = protoReader.followPath(4, 4, 6) != null
                    
                    if (noteAtRoot || noteNested) {
                        // Set save policy in the NOTE path
                        val hasNestedPath = if (noteNested) {
                            protoReader.followPath(4, 4, 6, 1, 1) != null
                        } else {
                            protoReader.followPath(6, 1, 1) != null
                        }
                        
                        if (noteNested) {
                            if (hasNestedPath) {
                                edit(4, 4, 6, 1, 1) {
                                    remove(7)
                                    addVarInt(7, savePolicy)
                                }
                            } else {
                                edit(4, 4, 6, 1) {
                                    remove(7)
                                    addVarInt(7, savePolicy)
                                }
                            }
                        } else {
                            if (hasNestedPath) {
                                edit(6, 1, 1) {
                                    remove(7)
                                    addVarInt(7, savePolicy)
                                }
                            } else {
                                edit(6, 1) {
                                    remove(7)
                                    addVarInt(7, savePolicy)
                                }
                            }
                        }
                    }

                    // Handle SNAP messages (field 11)
                    val snapAtRoot = protoReader.followPath(11) != null
                    val snapNested = protoReader.followPath(4, 4, 11) != null
                    if (snapAtRoot || snapNested) {
                        if (snapNested) {
                            edit(4, 4, 11) {
                                remove(7)
                                addVarInt(7, savePolicy)
                            }
                        } else {
                            edit(11) {
                                remove(7)
                                addVarInt(7, savePolicy)
                            }
                        }
                    }
                }.toByteArray()
            }
        }

        context.event.subscribe(UnaryCallEvent::class, priority = 100) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
        }

        context.event.subscribe(SendMessageWithContentEvent::class, priority = -100) { event ->
            if (internalMultipartSend.get() == true) return@subscribe
            postSavePolicy = null
            if (event.destinations.stories?.isNotEmpty() == true && event.destinations.conversations?.isEmpty() == true) return@subscribe
            val localMessageContent = event.messageContent
            // Allow both EXTERNAL_MEDIA (gallery) and SNAP (camera)
            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA && 
                localMessageContent.contentType != ContentType.SNAP &&
                localMessageContent.instanceNonNull().getObjectFieldOrNull("mExternalContentMetadata") == null) return@subscribe
            val includeCameraSnaps = context.config.messaging.galleryMediaSendOverride.includeCameraSnaps.get()
            if (localMessageContent.contentType == ContentType.SNAP && !includeCameraSnaps) return@subscribe

            //prevent story replies
            val messageProtoReader = ProtoReader(localMessageContent.content ?: return@subscribe)
            if (messageProtoReader.contains(7)) return@subscribe

            val conversationIds = event.destinations.conversations?.map { it.toString() } ?: return@subscribe
            if (conversationIds.isEmpty()) return@subscribe
            
            val recipientNames = conversationIds.mapNotNull { convId ->
                runCatching {
                    val dmParticipant = context.database.getDMOtherParticipant(convId)
                    if (dmParticipant != null) {
                        context.database.getFriendInfo(dmParticipant)?.displayName ?: context.database.getFriendInfo(dmParticipant)?.mutableUsername
                    } else {
                        context.database.getFeedEntryByConversationId(convId)?.feedDisplayName
                    }
                }.getOrNull()
            }.ifEmpty { listOf("Unknown") }
            
            val recipientName = recipientNames.joinToString(", ")

            event.canceled = true
            event.adapter.setResult(null)

            fun invokeOriginalAndRestoreResult(ev: SendMessageWithContentEvent) {
                val result = ev.adapter.invokeOriginal()
                ev.adapter.setResult(result)
                ev.canceled = false
            }

            val sendMessageCallbackClass by lazy {
                lateinit var result: Class<*>
                context.mappings.useMapper(CallbackMapper::class) {
                    result = callbacks.getClass("SendMessageCallback") ?: error("Failed to resolve SendMessageCallback")
                }
                result
            }

            fun cloneDestinations(source: MessageDestinations): Any {
                return context.gson.fromJson(
                    context.gson.toJson(source.instanceNonNull()),
                    context.classCache.messageDestinations
                )
            }

            val sendMessageWithContentMethod by lazy {
                sequence {
                    var current: Class<*>? = context.classCache.conversationManager
                    while (current != null && current != Any::class.java && current != Object::class.java) {
                        yield(current)
                        current = current.superclass
                    }
                }.flatMap { it.declaredMethods.asSequence() }
                    .first { it.name == "sendMessageWithContent" }
            }

            val originalMessageJson = context.gson.toJson(localMessageContent.instanceNonNull())
            val originalCallback = event.adapter.args().getOrNull(2)
            val conversationManagerInstance by lazy {
                context.feature(Messaging::class).conversationManager?.instanceNonNull()
            }

            fun invokeCallbackError(callback: Any?, error: Any?) {
                runCatching {
                    callback?.javaClass?.methods?.firstOrNull { method ->
                        method.name == "onError" && method.parameterCount == 1
                    }?.invoke(callback, error)
                }
            }

            fun applyOverride(
                targetMessageContent: MessageContent,
                targetReader: ProtoReader,
                overrideType: String,
                snapDurationMs: Int?
            ): Boolean {
                val bypassLimit = context.config.experimental.nativeHooks.valdiHooks.bypassCameraRollLimit.get()
                if (overrideType != "ORIGINAL" && !bypassLimit && (targetReader.followPath(3)?.getCount(3) ?: 0) > 1) {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Default.WarningAmber,
                        context.translation["gallery_media_send_override.multiple_media_toast"]
                    )
                    return false
                }

                when (overrideType) {
                    "SNAP", "SAVEABLE_SNAP" -> {
                        val savePolicyValue = if (overrideType == "SAVEABLE_SNAP") 2 else 1
                        postSavePolicy = savePolicyValue

                        val extras = targetReader.followPath(3, 3, 13)?.getBuffer()

                        if (targetMessageContent.contentType != ContentType.SNAP) {
                            targetMessageContent.content = ProtoWriter().apply {
                                from(11) {
                                    from(5) {
                                        from(1) {
                                            from(1) {
                                                addVarInt(2, 0)
                                                addVarInt(12, 0)
                                                addVarInt(15, 0)
                                            }
                                            addVarInt(6, 1)
                                        }
                                        from(2) {}
                                    }
                                    extras?.let {
                                        addBuffer(13, it)
                                    }
                                    from(22) {}
                                }
                            }.toByteArray()
                        }

                        targetMessageContent.contentType = ContentType.SNAP
                        targetMessageContent.content = ProtoEditor(targetMessageContent.content!!).apply {
                            edit(11, 5, 2) {
                                arrayOf(6, 7, 8).forEach { remove(it) }
                                addVarInt(5, targetReader.getVarInt(3, 3, 5, 2, 5) ?: targetReader.getVarInt(11, 5, 2, 5) ?: 1)
                                if (snapDurationMs != null && overrideType != "SAVEABLE_SNAP") {
                                    addVarInt(8, snapDurationMs / 1000)
                                    if (snapDurationMs / 1000 <= 0) {
                                        addVarInt(99, snapDurationMs)
                                    }
                                } else {
                                    addBuffer(6, byteArrayOf())
                                }
                            }

                            // set app source (same as SnapEnhance - no save policy in proto for story+chat)
                            edit(11, 22) {
                                remove(4)
                                addVarInt(4, 5) // APP_SOURCE_CAMERA
                            }

                            // Enforce save policy directly on SNAP message body.
                            edit(11) {
                                remove(7)
                                addVarInt(7, savePolicyValue)
                            }
                        }.toByteArray()
                    }
                    "NOTE" -> {
                        // Check if "prevent audio" is enabled in UnsaveableMessages
                        val shouldPreventSave = context.config.messaging.unsaveableMessages.note.get()
                        if (shouldPreventSave) {
                            postSavePolicy = 1 // PROHIBITED
                        }
                        targetMessageContent.contentType = ContentType.NOTE
                        val stripMeta = context.config.messaging.stripMediaMetadata.get()
                        val omitTranscript = stripMeta.contains("remove_audio_note_transcript_capability")
                        val rawDurationMs = targetReader.getVarInt(3, 3, 5, 1, 1, 15)?.toLong()
                            ?: targetReader.getVarInt(3, 3, 5, 2, 8)?.toLong()?.times(1000)
                            ?: (context.feature(MediaFilePicker::class).lastMediaDuration ?: 0).toLong()
                        val durationForProto = minOf(rawDurationMs, MessageSender.VOICE_NOTE_MAX_DURATION_MS)
                        val audioNoteProto = MessageSender.audioNoteProto(
                            durationForProto,
                            if (omitTranscript) null else Locale.getDefault().toLanguageTag()
                        )
                        
                        // Set save policy in the proto if prevent audio is enabled
                        targetMessageContent.content = if (shouldPreventSave) {
                            // Check which path structure exists in the audio note proto
                            val protoReader = ProtoReader(audioNoteProto)
                            val hasNestedPath = protoReader.followPath(6, 1, 1) != null
                            
                            ProtoEditor(audioNoteProto).apply {
                                // Set save policy to PROHIBITED (1) in the NOTE path
                                if (hasNestedPath) {
                                    edit(6, 1, 1) {
                                        remove(7)
                                        addVarInt(7, 1)
                                    }
                                } else {
                                    edit(6, 1) {
                                        remove(7)
                                        addVarInt(7, 1)
                                    }
                                }
                            }.toByteArray()
                        } else {
                            audioNoteProto
                        }
                        
                    }
                }

                if (postSavePolicy != null) {
                    try {
                        val savePolicyEnumClass = runCatching {
                            Class.forName(
                                "com.snapchat.client.messaging.SavePolicy",
                                false,
                                targetMessageContent.instanceNonNull().javaClass.classLoader
                            )
                        }.getOrNull()

                        if (savePolicyEnumClass != null && savePolicyEnumClass.isEnum) {
                            @Suppress("UNCHECKED_CAST")
                            val enumClass = savePolicyEnumClass as Class<out Enum<*>>
                            val policyName = when (postSavePolicy) {
                                1 -> "PROHIBITED"
                                2 -> "VIEWER_SAVABLE"
                                else -> null
                            }
                            if (policyName != null) {
                                val policyEnum = runCatching {
                                    java.lang.Enum.valueOf(enumClass, policyName)
                                }.getOrNull()

                                if (policyEnum != null) {
                                    targetMessageContent.instanceNonNull().setObjectField("mSavePolicy", policyEnum)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        context.log.warn("SendOverride: Failed to set mSavePolicy: ${e.message}")
                    }
                }

                return true
            }

            fun createMessageContentFromOriginal(): MessageContent {
                return MessageContent(
                    context.gson.fromJson(originalMessageJson, context.classCache.localMessageContent)
                ).also { messageContent ->
                    val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

                    fun shouldScrubField(fieldName: String): Boolean {
                        if (fieldName == "mId") return false
                        return fieldName in setOf("mMessageId", "mQuotedMessageId") ||
                            fieldName.contains("AttemptId", ignoreCase = true) ||
                            fieldName.contains("ClientMessageId", ignoreCase = true) ||
                            fieldName.contains("ClientId", ignoreCase = true) ||
                            fieldName.contains("MessageUuid", ignoreCase = true) ||
                            fieldName.contains("UUID", ignoreCase = true)
                    }

                    fun scrubValue(value: Any?) {
                        if (value == null) return
                        if (!visited.add(value)) return

                        when (value) {
                            is String, is Number, is Boolean, is ByteArray, is Enum<*> -> return
                            is Iterable<*> -> {
                                value.forEach { scrubValue(it) }
                                return
                            }
                            is Map<*, *> -> {
                                value.values.forEach { scrubValue(it) }
                                return
                            }
                        }

                        sequence<Class<*>> {
                            var current: Class<*>? = value.javaClass
                            while (current != null && current != Any::class.java && current != Object::class.java) {
                                yield(current)
                                current = current.superclass
                            }
                        }.flatMap { it.declaredFields.asSequence() }
                            .forEach { field ->
                                runCatching {
                                    field.isAccessible = true
                                    if (shouldScrubField(field.name)) {
                                        when (field.type) {
                                            java.lang.Long.TYPE -> field.setLong(value, 0L)
                                            java.lang.Integer.TYPE -> field.setInt(value, 0)
                                            java.lang.Boolean.TYPE -> field.setBoolean(value, false)
                                            else -> field.set(value, null)
                                        }
                                    } else {
                                        scrubValue(field.get(value))
                                    }
                                }
                            }
                    }

                    scrubValue(messageContent.instanceNonNull())
                }
            }

            fun invokeSendManually(messageContent: MessageContent, callback: Any?) {
                val conversationManager = conversationManagerInstance ?: error("ConversationManager is null")
                internalMultipartSend.set(true)
                try {
                    sendMessageWithContentMethod.invoke(
                        conversationManager,
                        cloneDestinations(event.destinations),
                        messageContent.instanceNonNull(),
                        callback
                    )
                } finally {
                    internalMultipartSend.set(false)
                }
            }

            fun sendMediaManual(
                sourceMessageContent: MessageContent,
                overrideType: String,
                snapDurationMs: Int?,
                completionCallback: Any?
            ): Boolean {
                val sourceReader = ProtoReader(sourceMessageContent.content ?: return false)
                val mediaCount = sourceReader.followPath(3)?.getCount(3) ?: 0
                if (overrideType != "ORIGINAL" && mediaCount > 1) {
                    val mediaBuffers = mutableListOf<ByteArray>()
                    sourceReader.followPath(3)?.eachBuffer { id, buffer ->
                        if (id == 3) mediaBuffers.add(buffer)
                    }
                    if (mediaBuffers.isEmpty()) return false

                    fun buildPartMessageContent(partIndex: Int): MessageContent {
                        val partContent = createMessageContentFromOriginal()
                        val metadata = partContent.instanceNonNull().getObjectFieldOrNull("mExternalContentMetadata")
                        val refs = ArrayList(partContent.localMediaReferences ?: arrayListOf())
                        val contentRefs = (metadata?.getObjectFieldOrNull("mContentReferences") as? ArrayList<*>)?.toCollection(ArrayList())
                        val encryptionRefs = (metadata?.getObjectFieldOrNull("mRemoteMediaEncryption") as? ArrayList<*>)?.toCollection(ArrayList())
                        partContent.content = ProtoEditor(partContent.content!!).apply {
                            edit(3) {
                                remove(3)
                                addBuffer(3, mediaBuffers[partIndex])
                            }
                        }.toByteArray()
                        if (partIndex < refs.size) {
                            partContent.localMediaReferences = arrayListOf(refs[partIndex])
                        }
                        metadata?.let {
                            if (contentRefs != null && partIndex < contentRefs.size) {
                                it.setObjectField("mContentReferences", arrayListOf(contentRefs[partIndex]))
                            }
                            if (encryptionRefs != null && partIndex < encryptionRefs.size) {
                                it.setObjectField("mRemoteMediaEncryption", arrayListOf(encryptionRefs[partIndex]))
                            }
                        }
                        return partContent
                    }

                    fun sendPart(partIndex: Int) {
                        postSavePolicy = null
                        val partContent = buildPartMessageContent(partIndex)
                        val partReader = ProtoReader(partContent.content ?: return)
                        if (!applyOverride(partContent, partReader, overrideType, snapDurationMs)) return

                        val callback = if (partIndex == mediaCount - 1) {
                            completionCallback
                        } else {
                            CallbackBuilder(sendMessageCallbackClass)
                                .override("onSuccess") {
                                    sendPart(partIndex + 1)
                                }
                                .override("onError", shouldUnhook = false) {
                                    invokeCallbackError(completionCallback, it.argNullable<Any>(0))
                                }
                                .build()
                        }

                        invokeSendManually(partContent, callback)
                    }

                    sendPart(0)
                    return true
                }

                postSavePolicy = null
                val targetReader = ProtoReader(sourceMessageContent.content ?: return false)
                if (!applyOverride(sourceMessageContent, targetReader, overrideType, snapDurationMs)) return false
                invokeSendManually(sourceMessageContent, completionCallback)
                return true
            }

            fun sendRepeatedMediaManual(
                repeatCount: Int,
                overrideType: String,
                snapDurationMs: Int?
            ): Boolean {
                if (repeatCount <= 0) return false

                fun sendIteration(index: Int) {
                    val callback = if (index == repeatCount - 1) {
                        originalCallback
                    } else {
                        CallbackBuilder(sendMessageCallbackClass)
                            .override("onSuccess") {
                                sendIteration(index + 1)
                            }
                            .override("onError", shouldUnhook = false) {
                                invokeCallbackError(originalCallback, it.argNullable<Any>(0))
                            }
                            .build()
                    }

                    val preparedContent = createMessageContentFromOriginal()
                    if (!sendMediaManual(preparedContent, overrideType, snapDurationMs, callback)) {
                        invokeCallbackError(originalCallback, "Failed to send")
                    }
                }

                sendIteration(0)
                return true
            }

            fun sendMedia(overrideType: String, snapDurationMs: Int?): Boolean {
                postSavePolicy = null
                return applyOverride(localMessageContent, messageProtoReader, overrideType, snapDurationMs)
            }

            val resolvedOverrideType = MediaFilePicker.getQueuedOverrideType()
                ?: configOverrideType?.takeIf { it != "always_ask" }

            fun attachQueuedRepeatCallbacks(sendEvent: SendMessageWithContentEvent) {
                sendEvent.addCallbackResult("onSuccess") {
                    context.runOnUiThread {
                        val handledSplit = MediaFilePicker.handleCurrentQueuedItemSuccess()
                        val handledRepeat = if (!handledSplit) {
                            handleQueuedOriginalItemRepeatSuccess()
                        } else {
                            false
                        }
                        if (!handledSplit && !handledRepeat) {
                            MediaFilePicker.clearQueuedSplitItems()
                            clearQueuedOriginalItemRepeats()
                        }
                    }
                }
                sendEvent.addCallbackResult("onError") {
                    MediaFilePicker.clearQueuedSplitItems()
                    clearQueuedOriginalItemRepeats()
                }
            }

            if (resolvedOverrideType != null) {
                if (MediaFilePicker.hasPendingSplitCleanup() || MediaFilePicker.getQueuedOverrideType() != null || queuedOriginalItemRepeatCount > 0) {
                    attachQueuedRepeatCallbacks(event)
                }
                if (sendMedia(resolvedOverrideType, 10000)) {
                    if (event.canceled) invokeOriginalAndRestoreResult(event)
                }
                return@subscribe
            }

            context.runOnUiThread {
                val recipientNameForTask = recipientName
                                val mediaCount = messageProtoReader.followPath(3)?.getCount(3) ?: 0
                
                createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
                    PurrfectOverlayTheme {
                        val mainTranslation = remember {
                            context.translation.getCategory("send_override_dialog")
                        }
                        val dialogShape = RoundedCornerShape(24.dp)
                        val dialogSurfaceColor = Color(0xFF2A2452)
                        val border = remember {
                            Brush.linearGradient(
                                listOf(
                                    PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                                    PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f)
                                )
                            )
                        }
                        val dialogBackground = remember {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF2A2452),
                                    Color(0xFF1A143A)
                                )
                            )
                        }

                        @Composable
                        fun ActionTile(
                            modifier: Modifier = Modifier,
                            selected: Boolean = false,
                            icon: ImageVector,
                            title: String,
                            onClick: () -> Unit
                        ) {
                            Card(
                                modifier = modifier,
                                onClick = onClick,
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Color(0xFF3E3478) else Color(0xFF2F2A5B),
                                    contentColor = Color.White
                                ),
                                border = if (selected) BorderStroke(1.dp, PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.6f)) else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = title,
                                        modifier = Modifier.size(28.dp),
                                        tint = if (selected) PurrfectOverlayPalette.glowSecondary else Color.White.copy(alpha = 0.9f)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        title,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        softWrap = true,
                                        lineHeight = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = dialogShape,
                            color = dialogSurfaceColor,
                            tonalElevation = 0.dp,
                            shadowElevation = 18.dp,
                            border = BorderStroke(1.dp, border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(dialogBackground, dialogShape)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val translation = remember {
                                    context.translation.getCategory("features.options.gallery_media_send_override")
                                }
                                var scheduleEnabled by remember { mutableStateOf(false) }
                                var continuousSendEnabled by remember { mutableStateOf(false) }
                                var continuousSendCount by remember { mutableStateOf("2") }

                                Text(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    text = "Send as ${translation[selectedType]}",
                                    modifier = Modifier.padding(5.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActionTile(
                                        modifier = Modifier.weight(1f).height(92.dp),
                                        selected = selectedType == "ORIGINAL",
                                        icon = Icons.Filled.Photo,
                                        title = translation["ORIGINAL"]
                                    ) {
                                        selectedType = "ORIGINAL"
                                    }
                                    ActionTile(
                                        modifier = Modifier.weight(1f).height(92.dp),
                                        selected = selectedType == "SNAP" || selectedType == "SAVEABLE_SNAP",
                                        icon = Icons.Filled.PhotoCamera,
                                        title = translation["SNAP"]
                                    ) {
                                        selectedType = "SNAP"
                                    }
                                    ActionTile(
                                        modifier = Modifier.weight(1f).height(92.dp),
                                        selected = selectedType == "NOTE",
                                        icon = Icons.Filled.MusicNote,
                                        title = translation["NOTE"]
                                    ) {
                                        selectedType = "NOTE"
                                    }
                                }

                                fun convertDuration(duration: Float) = when {
                                    duration in -2f..-1f -> 100
                                    duration in -1f..-0f -> 250
                                    duration in -0f..1f -> 500
                                    duration >= 11f -> null
                                    else -> ((duration * 1000).toInt() / 1000) * 1000
                                }
                        
                        fun formatTimeText(ms: Long): String {
                            val days = (ms / (24 * 60 * 60 * 1000)).toInt()
                            val hours = ((ms / (60 * 60 * 1000)) % 24).toInt()
                            val minutes = ((ms / (60 * 1000)) % 60).toInt()
                            val seconds = ((ms / 1000) % 60).toInt()
                            return buildString {
                                if (days > 0) append("${days}d ")
                                if (hours > 0 || days > 0) append("${hours}h ")
                                if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
                                append("${seconds}s")
                            }
                        }

                        when (selectedType) {
                            "SNAP", "SAVEABLE_SNAP" -> {
                                fun toggleSaveable() {
                                    selectedType = if (selectedType == "SAVEABLE_SNAP") "SNAP" else "SAVEABLE_SNAP"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        disableSplitForCurrentSend = !disableSplitForCurrentSend
                                    },
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = disableSplitForCurrentSend,
                                        onCheckedChange = {
                                            disableSplitForCurrentSend = it
                                        }
                                    )
                                    Text(text = mainTranslation["single_send_hint"], lineHeight = 15.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        toggleSaveable()
                                    },
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ){
                                    Checkbox(
                                        checked = selectedType == "SAVEABLE_SNAP",
                                        onCheckedChange = {
                                            toggleSaveable()
                                        }
                                    )
                                    Text(text = mainTranslation["saveable_snap_hint"], lineHeight = 15.sp)
                                }
                                Column(
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = mainTranslation.format("duration",
                                            "duration" to (convertDuration(customDuration)?.toDuration(DurationUnit.MILLISECONDS)?.toString(DurationUnit.SECONDS, 2) ?: mainTranslation["unlimited_duration"])
                                        )
                                    )
                                    Slider(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = selectedType != "SAVEABLE_SNAP",
                                        value = customDuration,
                                        onValueChange = {
                                            customDuration = it
                                        },
                                        valueRange = -2f..11f,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                continuousSendEnabled = !continuousSendEnabled
                            },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = continuousSendEnabled,
                                onCheckedChange = {
                                    continuousSendEnabled = it
                                }
                            )
                            Text(text = mainTranslation["continuous_send_toggle"], lineHeight = 15.sp)
                        }

                        if (continuousSendEnabled) {
                            OutlinedTextField(
                                value = continuousSendCount,
                                onValueChange = { value ->
                                    continuousSendCount = value.filter(Char::isDigit).take(3)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(mainTranslation["continuous_send_count_label"]) },
                                placeholder = { Text(mainTranslation["continuous_send_count_placeholder"]) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                keyboardActions = KeyboardActions.Default
                            )
                            Text(
                                text = mainTranslation["continuous_send_hint"],
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = scheduleEnabled,
                                onCheckedChange = {
                                    scheduleEnabled = it
                                    if (!it) scheduledTime = null
                                }
                            )
                            Text(text = mainTranslation["schedule"], modifier = Modifier.weight(1f))
                            if (scheduleEnabled) {
                                Button(onClick = { showClockPicker = true }) {
                                    scheduledTime?.let { time ->
                                        Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(time))
                                    } ?: Text(context.translation["select"])
                                }
                            }
                        }

                        if (scheduleEnabled && showClockPicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = scheduledTime ?: System.currentTimeMillis()
                            )
                            val timePickerState = rememberTimePickerState(
                                initialHour = clockPickerHour,
                                initialMinute = clockPickerMinute
                            )
                            
                            var showDatePickerDialog by remember { mutableStateOf(false) }
                            var showTimePickerDialog by remember { mutableStateOf(false) }
                            
                            if (showDatePickerDialog) {
                                DatePickerDialog(
                                    onDismissRequest = { showDatePickerDialog = false },
                                    confirmButton = {
                                        TextButton(onClick = { showDatePickerDialog = false }) {
                                            Text(context.translation["button.ok"])
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePickerDialog = false }) {
                                            Text(context.translation["button.cancel"])
                                        }
                                    }
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                            
                            if (showTimePickerDialog) {
                                AlertDialog(
                                    onDismissRequest = { showTimePickerDialog = false },
                                    confirmButton = {
                                        TextButton(onClick = { 
                                            clockPickerHour = timePickerState.hour
                                            clockPickerMinute = timePickerState.minute
                                            showTimePickerDialog = false 
                                        }) {
                                            Text(context.translation["button.ok"])
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showTimePickerDialog = false }) {
                                            Text(context.translation["button.cancel"])
                                        }
                                    },
                                    text = {
                                        TimePicker(state = timePickerState)
                                    }
                                )
                            }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        mainTranslation["select_time"], 
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    
                                    OutlinedButton(
                                        onClick = { showDatePickerDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            datePickerState.selectedDateMillis?.let {
                                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                                            } ?: context.translation.getOrNull("select_date") ?: "Select Date"
                                        )
                                    }
                                    
                                    OutlinedButton(
                                        onClick = { showTimePickerDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(String.format("%02d:%02d", clockPickerHour, clockPickerMinute))
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        OutlinedButton(onClick = { showClockPicker = false }) {
                                            Text(context.translation["button.cancel"])
                                        }
                                        Button(onClick = {
                                            val selectedDateMillis = datePickerState.selectedDateMillis
                                            if (selectedDateMillis == null) {
                                                context.inAppOverlay.showStatusToast(
                                                    icon = Icons.Default.WarningAmber,
                                                    text = mainTranslation.getOrNull("select_date_first") ?: "Please select a date"
                                                )
                                                return@Button
                                            }
                                            
                                            val calendar = Calendar.getInstance()
                                            calendar.timeInMillis = selectedDateMillis
                                            calendar.set(Calendar.HOUR_OF_DAY, clockPickerHour)
                                            calendar.set(Calendar.MINUTE, clockPickerMinute)
                                            calendar.set(Calendar.SECOND, 0)
                                            calendar.set(Calendar.MILLISECOND, 0)
                                            
                                            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                                                context.inAppOverlay.showStatusToast(
                                                    icon = Icons.Default.WarningAmber,
                                                    text = mainTranslation.getOrNull("invalid_time") ?: "Please select a future time"
                                                )
                                                return@Button
                                            }
                                            
                                            scheduledTime = calendar.timeInMillis
                                            showClockPicker = false
                                        }) {
                                            Text(context.translation["button.ok"])
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = {
                                alertDialog.dismiss()
                            }) {
                                Text(context.translation["button.cancel"])
                            }
                            Button(onClick = {
                                val finalSelectedType = selectedType
                                val repeatCount = if (continuousSendEnabled) {
                                    continuousSendCount.toIntOrNull()?.takeIf { it > 0 }
                                } else {
                                    1
                                }
                                if (repeatCount == null) {
                                    context.inAppOverlay.showStatusToast(
                                        icon = Icons.Default.WarningAmber,
                                        text = mainTranslation["continuous_send_invalid_count"]
                                    )
                                    return@Button
                                }
                                if (repeatCount > 1 && disableSplitForCurrentSend && MediaFilePicker.hasOriginalUnsplitItem()) {
                                    context.inAppOverlay.showStatusToast(
                                        icon = Icons.Default.WarningAmber,
                                        text = mainTranslation["continuous_send_single_send_conflict"]
                                    )
                                    return@Button
                                }
                                alertDialog.dismiss()
                                if (disableSplitForCurrentSend && MediaFilePicker.hasOriginalUnsplitItem()) {
                                    MediaFilePicker.setQueuedOverrideType(finalSelectedType)
                                    if (!MediaFilePicker.sendOriginalUnsplitItem()) {
                                        MediaFilePicker.setQueuedOverrideType(null)
                                    }
                                    return@Button
                                } else if (MediaFilePicker.hasPendingSplitCleanup()) {
                                    MediaFilePicker.setQueuedOverrideType(finalSelectedType)
                                    event.addCallbackResult("onSuccess") {
                                        context.runOnUiThread {
                                            if (!MediaFilePicker.handleCurrentQueuedItemSuccess()) {
                                                MediaFilePicker.clearQueuedSplitItems()
                                            }
                                        }
                                    }
                                    event.addCallbackResult("onError") {
                                        MediaFilePicker.clearQueuedSplitItems()
                                    }
                                }
                                val delayMs = scheduledTime?.let { it - System.currentTimeMillis() }
                                if (delayMs != null && delayMs > 0) {
                                    val taskHash = java.util.UUID.randomUUID().toString()
                                    // Format the scheduled time for display
                                    val scheduledDateTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(scheduledTime)
                                    context.bridgeClient.getTaskInterface().createTask(
                                        "scheduled_send",
                                        scheduledDateTime,
                                        recipientNameForTask,
                                        taskHash
                                    )
                                    val scheduledTimeText = formatTimeText(delayMs)
                                    context.inAppOverlay.showStatusToast(
                                        icon = Icons.Filled.Schedule,
                                        text = context.translation.format("schedule_scheduled_for", "name" to recipientNameForTask, "time" to scheduledTimeText) ?: "Scheduled for $recipientNameForTask in $scheduledTimeText"
                                    )
                                    val releaseBackground = acquireScheduledSendBackground()
                                    
                                    event.addCallbackResult("onSuccess") {
                                        context.bridgeClient.getTaskInterface().successTask(taskHash)
                                    }
                                    event.addCallbackResult("onError") { 
                                        context.bridgeClient.getTaskInterface().failTask(taskHash, it.getOrNull(0)?.toString() ?: "Unknown error")
                                    }

                                    val job = context.coroutineScope.launch {
                                        val startTime = System.currentTimeMillis()
                                        while (true) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            val remaining = delayMs - elapsed
                                            if (remaining <= 0) break
                                            
                                            val timeText = formatTimeText(remaining)
                                            val progress = (elapsed * 100 / delayMs).toInt().coerceIn(0, 99)
                                            
                                            context.bridgeClient.getTaskInterface().updateTaskProgress(
                                                taskHash,
                                                context.translation.getOrNull("schedule_sending_in")?.replace("{time}", timeText) ?: "Sending in $timeText",
                                                progress
                                            )
                                            delay(1000)
                                        }

                                        context.bridgeClient.getTaskInterface().updateTaskProgress(taskHash, "Sending...", 100)

                                        if (sendRepeatedMediaManual(
                                                repeatCount,
                                                finalSelectedType,
                                                if (finalSelectedType != "SAVEABLE_SNAP") convertDuration(customDuration) else null
                                            )) {
                                            val successText = context.translation.format("schedule_sent_to", "name" to recipientNameForTask) ?: "Sent to $recipientNameForTask"
                                            context.inAppOverlay.showStatusToast(
                                                icon = Icons.Filled.CheckCircle,
                                                text = successText
                                            )
                                            val notificationTitle = context.translation.getOrNull("schedule_sent") ?: "Scheduled snap sent"
                                            val notificationContent = "$scheduledDateTime\n$recipientNameForTask"
                                            showNotification(
                                                notificationTitle,
                                                notificationContent
                                            )
                                        } else {
                                            context.bridgeClient.getTaskInterface().failTask(taskHash, "Failed to send")
                                            val failureText = context.translation.format("schedule_failed_to", "name" to recipientNameForTask) ?: "Failed to send to $recipientNameForTask"
                                            context.inAppOverlay.showStatusToast(
                                                icon = Icons.Filled.WarningAmber,
                                                text = failureText
                                            )
                                            val failNotificationTitle = context.translation.getOrNull("schedule_failed") ?: "Scheduled snap failed"
                                            val failNotificationContent = "$scheduledDateTime\n$recipientNameForTask"
                                            showNotification(
                                                failNotificationTitle,
                                                failNotificationContent
                                            )
                                        }
                                    }
                                    val listener = object : TaskListener.Stub() {
                                        override fun onCancel() {
                                            job.cancel()
                                        }
                                        override fun onProgress(label: String, progress: Int) {}
                                        override fun onStateChange(status: String) {}
                                        override fun onSuccess() {}
                                    }
                                    context.bridgeClient.getTaskInterface().registerTaskListener(taskHash, listener)
                                    job.invokeOnCompletion { throwable ->
                                        releaseBackground()
                                        context.bridgeClient.getTaskInterface().unregisterTaskListener(taskHash, listener)
                                        if (throwable is CancellationException) {
                                            context.inAppOverlay.showStatusToast(
                                                icon = Icons.Filled.Cancel,
                                                text = context.translation.format("schedule_cancelled_for", "name" to recipientNameForTask) ?: "Cancelled for $recipientNameForTask"
                                            )
                                        }
                                    }
                                } else {
                                    if (repeatCount == 1) {
                                        if (sendMedia(finalSelectedType, if (finalSelectedType != "SAVEABLE_SNAP") convertDuration(customDuration) else null)) {
                                            invokeOriginalAndRestoreResult(event)
                                        }
                                    } else if (MediaFilePicker.hasReusableOriginalItem()) {
                                        queueOriginalItemRepeats(repeatCount - 1, finalSelectedType)
                                        attachQueuedRepeatCallbacks(event)
                                        if (sendMedia(finalSelectedType, if (finalSelectedType != "SAVEABLE_SNAP") convertDuration(customDuration) else null)) {
                                            invokeOriginalAndRestoreResult(event)
                                        } else {
                                            clearQueuedOriginalItemRepeats()
                                        }
                                    } else {
                                        sendRepeatedMediaManual(
                                            repeatCount,
                                            finalSelectedType,
                                            if (finalSelectedType != "SAVEABLE_SNAP") convertDuration(customDuration) else null
                                        )
                                    }
                                }
                            }) {
                                Text(if (scheduledTime != null) mainTranslation["schedule"] else context.translation["button.send"])
                            }
                        }
                    }
                }
                }
                }.show()
            }
        }
    }
}
