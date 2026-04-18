package cock.crest.purrfectsnap.lite.core.features.impl.spying

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.data.*
import cock.crest.purrfectsnap.lite.common.util.lazyBridge
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.toParcelable
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.core.wrapper.impl.toSnapUUID
import cock.crest.purrfectsnap.lite.nativelib.NativeLib
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.text.DateFormat
import java.util.Date

class FriendTracker : Feature("Friend Tracker") {
    companion object {
        private const val PRESENCE_PEEKING_BIT = 8
        private const val PRESENCE_REPLY_CAMERA_BIT = 9
        private const val PRESENCE_CHAT_MEDIA_BIT = 10
    }

    private val conversationPresenceState = mutableMapOf<String, MutableMap<String, FriendPresenceState?>>() // conversationId -> (userId -> state)
    private val tracker by lazyBridge { context.bridgeClient.getTracker() }
    private val translation by lazy { context.translation.getCategory("friend_tracker_notifications") }
    private val trackerTranslation by lazy { context.translation.getCategory("tracker") }
    private val notificationManager by lazy { context.androidContext.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(NotificationChannel(
            "friend_tracker",
            translation["notification_channel_name"],
            NotificationManager.IMPORTANCE_DEFAULT
        ))
    } }
    private val conversationEntries = mutableMapOf<Pair<String, String>, Long>()
    private val galleryEntries = mutableMapOf<Pair<String, String>, Long>()
    private val replyCameraEntries = mutableMapOf<Pair<String, String>, Long>()
    private val peekingStateListeners = mutableListOf<(String, String, Boolean) -> Unit>()

    fun addOnPeekingStateChangedListener(listener: (conversationId: String, userId: String, peeking: Boolean) -> Unit) {
        peekingStateListeners.add(listener)
    }

    private fun getTrackedEvents(eventType: TrackerEventType): TrackerEventsResult? {
        return runCatching {
            tracker.getTrackedEvents(eventType.key)?.let {
                toParcelable<TrackerEventsResult>(it)
            }
        }.onFailure {
            context.log.error("Failed to get tracked events for $eventType", it)
        }.getOrNull()
    }

    private fun isInConversation(conversationId: String?) = context.feature(Messaging::class).openedConversationUUID?.toString() == conversationId

    private fun sendInfoNotification(id: Int = System.nanoTime().toInt(), text: String) {
        notificationManager.notify(
            id,
            Notification.Builder(
                context.androidContext,
                "friend_tracker"
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(context.androidContext.packageManager.getLaunchIntentForPackage(
                    Constants.SNAPCHAT_PACKAGE_NAME
                )?.let {
                    PendingIntent.getActivity(
                        context.androidContext,
                        0, it, PendingIntent.FLAG_IMMUTABLE
                    )
                })
                .setContentText(text)
                .build()
        )
    }

    private fun handleVolatileEvent(protoReader: ProtoReader) {
        context.log.verbose("volatile event\n$protoReader")
    }

    private fun dispatchEvents(
        eventType: TrackerEventType,
        conversationId: String,
        userId: String,
        extras: String = ""
    ) {
        val feedEntry = context.database.getFeedEntryByConversationId(conversationId)
        val friendInfo = context.database.getFriendInfo(userId)
        val conversationName = feedEntry?.feedDisplayName ?: trackerTranslation["logs.log_entry.unknown_conversation"]
        val authorName = friendInfo?.displayName ?: friendInfo?.mutableUsername ?: trackerTranslation["logs.log_entry.unknown_user"]

        context.log.verbose("$authorName $eventType in $conversationName")

        getTrackedEvents(eventType)?.takeIf { it.canTrackOn(conversationId, userId) }?.getActions()?.forEach { (action, params) ->
            if ((params.onlyWhenAppActive || action == TrackerRuleAction.IN_APP_NOTIFICATION) && context.isMainActivityPaused) return@forEach
            if (params.onlyWhenAppInactive && !context.isMainActivityPaused) return@forEach
            if (params.onlyInsideConversation && !isInConversation(conversationId)) return@forEach
            if (params.onlyOutsideConversation && isInConversation(conversationId)) return@forEach

            context.log.verbose("dispatching $action for $eventType in $conversationName")

            val iCanSeeYouDetails = when (eventType) {
                TrackerEventType.I_CAN_SEE_YOU,
                TrackerEventType.I_CAN_SEE_YOU_2,
                TrackerEventType.I_CAN_SEE_YOU_3 -> buildICanSeeYouDetails(extras)
                else -> ""
            }
            val notificationText = translation[eventType.key]
                .replace("{friend}", authorName)
                .replace("{conversation}", conversationName)
                .replace("{details}", iCanSeeYouDetails)

            when (action) {
                TrackerRuleAction.PUSH_NOTIFICATION -> {
                    if (params.noPushNotificationWhenAppActive && !context.isMainActivityPaused) return@forEach
                    sendInfoNotification(text = notificationText)
                }
                TrackerRuleAction.IN_APP_NOTIFICATION -> context.inAppOverlay.showStatusToast(
                    icon = Icons.Default.Info,
                    text = notificationText
                )
                TrackerRuleAction.LOG -> context.bridgeClient.getMessageLogger().logTrackerEvent(
                    conversationId,
                    conversationName,
                    context.database.getConversationType(conversationId) == 1,
                    authorName,
                    userId,
                    eventType.key,
                    extras
                )
                else -> {}
            }
        }
    }

    private fun buildTimedActivityExtras(entry: Long?, exit: Long?, duration: Long?) = listOf(
        entry ?: -1,
        exit ?: -1,
        duration ?: -1
    ).joinToString("|")

    private fun buildICanSeeYouDetails(extras: String): String {
        val values = extras.split("|").map { it.toLongOrNull() ?: -1L }.let {
            if (it.size >= 3) it else it + List(3 - it.size) { -1L }
        }
        val entered = values[0].takeIf { it >= 0 }
        val exited = values[1].takeIf { it >= 0 }
        val duration = values[2].takeIf { it >= 0 }
        val enteredLabel = trackerTranslation["logs.log_entry.i_can_see_you_entered"]
        val leftLabel = trackerTranslation["logs.log_entry.i_can_see_you_left"]
        val durationLabel = trackerTranslation["logs.log_entry.i_can_see_you_duration"]
        val notAvailable = trackerTranslation["logs.log_entry.i_can_see_you_not_available"]
        val enteredText = formatICanSeeYouTime(entered, notAvailable)
        val leftText = formatICanSeeYouTime(exited, notAvailable)
        val durationText = formatICanSeeYouDuration(duration, notAvailable)
        return listOf(
            "$enteredLabel: $enteredText",
            "$durationLabel: $durationText",
            "$leftLabel: $leftText"
        ).joinToString(" · ")
    }

    private fun formatICanSeeYouTime(value: Long?, fallback: String): String {
        if (value == null || value < 0) return fallback
        return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(value))
    }

    private fun formatICanSeeYouDuration(value: Long?, fallback: String): String {
        if (value == null || value < 0) return fallback
        val totalSeconds = (value / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val hourUnit = trackerTranslation["logs.log_entry.i_can_see_you_unit_hour"]
        val minuteUnit = trackerTranslation["logs.log_entry.i_can_see_you_unit_minute"]
        val secondUnit = trackerTranslation["logs.log_entry.i_can_see_you_unit_second"]
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}${hourUnit}")
        if (minutes > 0 || hours > 0) parts.add("${minutes}${minuteUnit}")
        parts.add("${seconds}${secondUnit}")
        return parts.joinToString(" ")
    }

    private fun onConversationPresenceUpdate(conversationId: String, userId: String, oldState: FriendPresenceState?, currentState: FriendPresenceState?) {
        context.log.verbose("presence state for $userId in conversation $conversationId\n$currentState")

        val eventType = when {
            (oldState == null || currentState?.bitmojiPresent == false) && currentState?.bitmojiPresent == true -> TrackerEventType.CONVERSATION_ENTER
            (currentState == null || oldState?.bitmojiPresent == false) && oldState?.bitmojiPresent == true -> TrackerEventType.CONVERSATION_EXIT
            oldState?.typing == false && currentState?.typing == true -> if (currentState.speaking) TrackerEventType.STARTED_SPEAKING else TrackerEventType.STARTED_TYPING
            oldState?.typing == true && (currentState == null || !currentState.typing) -> if (oldState.speaking) TrackerEventType.STOPPED_SPEAKING else TrackerEventType.STOPPED_TYPING
            (oldState == null || !oldState.usingReplyCamera) && currentState?.usingReplyCamera == true -> TrackerEventType.STARTED_USING_REPLY_CAMERA
            oldState?.usingReplyCamera == true && (currentState == null || !currentState.usingReplyCamera) -> TrackerEventType.STOPPED_USING_REPLY_CAMERA
            (oldState == null || !oldState.viewingChatMedia) && currentState?.viewingChatMedia == true -> TrackerEventType.STARTED_VIEWING_CHAT_MEDIA
            oldState?.viewingChatMedia == true && (currentState == null || !currentState.viewingChatMedia) -> TrackerEventType.STOPPED_VIEWING_CHAT_MEDIA
            (oldState == null || !oldState.peeking) &&
                currentState?.peeking == true &&
                currentState.usingReplyCamera != true &&
                oldState?.usingReplyCamera != true -> TrackerEventType.STARTED_PEEKING
            oldState?.peeking == true &&
                (currentState == null || !currentState.peeking) &&
                currentState?.usingReplyCamera != true &&
                oldState.usingReplyCamera != true -> TrackerEventType.STOPPED_PEEKING
            else -> null
        }

        eventType ?: return

        when (eventType) {
            TrackerEventType.CONVERSATION_ENTER -> {
                conversationEntries[conversationId to userId] = System.currentTimeMillis()
            }
            TrackerEventType.CONVERSATION_EXIT -> {
                val key = conversationId to userId
                val exit = System.currentTimeMillis()
                val entry = conversationEntries.remove(key)
                dispatchEvents(
                    TrackerEventType.I_CAN_SEE_YOU,
                    conversationId,
                    userId,
                    buildTimedActivityExtras(entry, exit, entry?.let { exit - it })
                )
            }
            TrackerEventType.STARTED_VIEWING_CHAT_MEDIA -> {
                galleryEntries[conversationId to userId] = System.currentTimeMillis()
            }
            TrackerEventType.STOPPED_VIEWING_CHAT_MEDIA -> {
                val key = conversationId to userId
                val exit = System.currentTimeMillis()
                val entry = galleryEntries.remove(key)
                dispatchEvents(
                    TrackerEventType.I_CAN_SEE_YOU_2,
                    conversationId,
                    userId,
                    buildTimedActivityExtras(entry, exit, entry?.let { exit - it })
                )
            }
            TrackerEventType.STARTED_USING_REPLY_CAMERA -> {
                replyCameraEntries[conversationId to userId] = System.currentTimeMillis()
            }
            TrackerEventType.STOPPED_USING_REPLY_CAMERA -> {
                val key = conversationId to userId
                val exit = System.currentTimeMillis()
                val entry = replyCameraEntries.remove(key)
                dispatchEvents(
                    TrackerEventType.I_CAN_SEE_YOU_3,
                    conversationId,
                    userId,
                    buildTimedActivityExtras(entry, exit, entry?.let { exit - it })
                )
            }
            else -> {}
        }

        when (eventType) {
            TrackerEventType.STARTED_PEEKING -> peekingStateListeners.forEach { it(conversationId, userId, true) }
            TrackerEventType.STOPPED_PEEKING -> peekingStateListeners.forEach { it(conversationId, userId, false) }
            else -> {}
        }

        dispatchEvents(eventType, conversationId, userId)
    }

    private fun onConversationMessagingEvent(event: SessionEvent) {
        context.log.verbose("conversation messaging event\n${event.type} in ${event.conversationId} from ${event.authorUserId}")

        val eventType = when(event.type) {
            SessionEventType.MESSAGE_READ_RECEIPTS -> TrackerEventType.MESSAGE_READ
            SessionEventType.MESSAGE_DELETED -> TrackerEventType.MESSAGE_DELETED
            SessionEventType.MESSAGE_REACTION_ADD -> TrackerEventType.MESSAGE_REACTION_ADD
            SessionEventType.MESSAGE_REACTION_REMOVE -> TrackerEventType.MESSAGE_REACTION_REMOVE
            SessionEventType.MESSAGE_SAVED -> TrackerEventType.MESSAGE_SAVED
            SessionEventType.MESSAGE_UNSAVED -> TrackerEventType.MESSAGE_UNSAVED
            SessionEventType.MESSAGE_EDITED -> TrackerEventType.MESSAGE_EDITED
            SessionEventType.SNAP_OPENED -> TrackerEventType.SNAP_OPENED
            SessionEventType.SNAP_REPLAYED -> TrackerEventType.SNAP_REPLAYED
            SessionEventType.SNAP_REPLAYED_TWICE -> TrackerEventType.SNAP_REPLAYED_TWICE
            SessionEventType.SNAP_SCREENSHOT -> TrackerEventType.SNAP_SCREENSHOT
            SessionEventType.SNAP_SCREEN_RECORD -> TrackerEventType.SNAP_SCREEN_RECORD
            else -> return
        }

        val conversationMessage by lazy {
            (event as? SessionMessageEvent)?.serverMessageId?.let { context.database.getConversationServerMessage(event.conversationId, it) }
        }

        dispatchEvents(eventType, event.conversationId, event.authorUserId, extras = conversationMessage?.takeIf {
            eventType == TrackerEventType.MESSAGE_READ ||
            eventType == TrackerEventType.MESSAGE_REACTION_ADD ||
            eventType == TrackerEventType.MESSAGE_REACTION_REMOVE ||
            eventType == TrackerEventType.MESSAGE_DELETED ||
            eventType == TrackerEventType.MESSAGE_SAVED ||
            eventType == TrackerEventType.MESSAGE_UNSAVED ||
            eventType == TrackerEventType.MESSAGE_EDITED
        }?.contentType?.let { ContentType.fromId(it).name } ?: "")
    }

    private fun handlePresenceEvent(protoReader: ProtoReader) {
        val conversationId = protoReader.getString(6) ?: return

        val presenceMap = conversationPresenceState.getOrPut(conversationId) { mutableMapOf() }.toMutableMap()
        val userIds = mutableSetOf<String>()

        protoReader.eachBuffer(4) {
            val participantUserId = getString(1)?.takeIf { it.contains(":") }?.substringBefore(":") ?: return@eachBuffer
            userIds.add(participantUserId)
            if (participantUserId == context.database.myUserId) return@eachBuffer
            val stateMap = getVarInt(2, 1)?.toString(2)?.padStart(16, '0')?.reversed()?.map { it == '1' } ?: return@eachBuffer
            val usingReplyCamera = stateMap.getOrElse(PRESENCE_REPLY_CAMERA_BIT) { false }
            val viewingChatMedia = stateMap.getOrElse(PRESENCE_CHAT_MEDIA_BIT) { false }
            val peeking = stateMap.getOrElse(PRESENCE_PEEKING_BIT) { false }

            presenceMap[participantUserId] = FriendPresenceState(
                bitmojiPresent = stateMap[0],
                typing = stateMap[4],
                wasTyping = stateMap[5],
                speaking = stateMap[6] && stateMap[4],
                // Snapchat moved peeking by one bit on newer builds and added
                // dedicated chat-presence flags for reply camera and chat media viewing.
                peeking = peeking,
                usingReplyCamera = usingReplyCamera,
                viewingChatMedia = viewingChatMedia
            )
        }

        presenceMap.keys.filterNot { it in userIds }.forEach { presenceMap[it] = null }

        presenceMap.forEach { (userId, state) ->
            val oldState = conversationPresenceState[conversationId]?.get(userId)
            if (oldState != state) {
                onConversationPresenceUpdate(conversationId, userId, oldState, state)
            }
        }

        conversationPresenceState[conversationId] = presenceMap
    }

    private fun handleMessagingEvent(protoReader: ProtoReader) {
        // read receipts
        protoReader.followPath(12) {
            val conversationId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath

            followPath(7) readReceipts@{
                val senderId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@readReceipts
                val serverMessageId = getVarInt(2, 2) ?: return@readReceipts

                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_READ_RECEIPTS,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }
        }

        protoReader.followPath(13, 1, 4) {
            val serverMessageId = getVarInt(1) ?: return@followPath
            val senderId = getByteArray(2, 1) ?: return@followPath
            val conversationId = getByteArray(3, 1, 1, 1) ?: return@followPath

            onConversationMessagingEvent(
                SessionMessageEvent(
                    SessionEventType.MESSAGE_EDITED,
                    SnapUUID(conversationId).toString(),
                    SnapUUID(senderId).toString(),
                    serverMessageId
                )
            )
        }

        protoReader.followPath(6, 2) {
            val conversationId = getByteArray(3, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val senderId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val serverMessageId = getVarInt(2) ?: return@followPath

            if (contains(4)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.SNAP_OPENED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(13)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (getVarInt(13, 1) == 2L) SessionEventType.SNAP_REPLAYED_TWICE else SessionEventType.SNAP_REPLAYED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(6) || contains(7)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(6)) SessionEventType.MESSAGE_SAVED else SessionEventType.MESSAGE_UNSAVED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(11) || contains(12)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(11)) SessionEventType.SNAP_SCREENSHOT else SessionEventType.SNAP_SCREEN_RECORD,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }

            followPath(16) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_REACTION_ADD, conversationId, senderId, serverMessageId, reactionId = getVarInt(1, 1, 1)?.toInt() ?: -1
                    )
                )
            }

            if (contains(17)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_REACTION_REMOVE, conversationId, senderId, serverMessageId)
                )
            }

            followPath(8) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_DELETED, conversationId, senderId, serverMessageId, messageData = getByteArray(1))
                )
            }
        }
    }

    override fun init() {
        val sessionEventsConfig = context.config.friendTracker
        val shouldProcessSessionEvents = sessionEventsConfig.globalState == true || peekingStateListeners.isNotEmpty()
        if (!shouldProcessSessionEvents) return

        if (sessionEventsConfig.allowRunningInBackground.get()) {
            findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").apply {
                // prevent disabling events when the app is inactive
                hook("appStateChanged", HookStage.BEFORE) { param ->
                    if (param.arg<Any>(0).toString() == "INACTIVE") param.setResult(null)
                }
                // allow events when a notification is received
                hookConstructor(HookStage.AFTER) { param ->
                    methods.first { it.name == "appStateChanged" }.let { method ->
                        method.invoke(param.thisObject(), method.parameterTypes[0].enumConstants!!.first { it.toString() == "ACTIVE" })
                    }
                }
            }
        }

        if (sessionEventsConfig.recordMessagingEvents.get() || peekingStateListeners.isNotEmpty()) {
            val messageHandlerClass = findClass("com.snapchat.client.duplex.MessageHandler\$CppProxy").apply {
                hook("onReceive", HookStage.BEFORE) { param ->
                    param.setResult(null)

                    val byteBuffer = param.arg<ByteBuffer>(0)
                    val content = byteBuffer.let {
                        val bytes = ByteArray(it.limit())
                        it.get(bytes)
                        bytes
                    }
                    val reader = ProtoReader(content)
                    reader.getString(1, 1)?.let {
                        val eventData = reader.followPath(1, 2) ?: return@let
                        if (it == "volatile") {
                            handleVolatileEvent(eventData)
                            return@hook
                        }

                        if (it == "presence") {
                            handlePresenceEvent(eventData)
                            return@hook
                        }
                    }
                    handleMessagingEvent(reader)
                }
                hook("nativeDestroy", HookStage.BEFORE) { it.setResult(null) }
            }


            findClass("com.snapchat.client.messaging.Session").hook("create", HookStage.BEFORE) { param ->
                if (!NativeLib.initialized) {
                    context.log.warn("Can't register duplex message handler, native lib not initialized")
                    return@hook
                }

                val method = param.method() as Method
                val duplexClient = method.parameterTypes.indexOfFirst { it.name.endsWith("DuplexClient") }.let {
                    param.arg<Any>(it)
                }
                val dispatchQueue = method.parameterTypes.indexOfFirst { it.name.endsWith("DispatchQueue") }.let {
                    param.arg<Any>(it)
                }
                for (channel in arrayOf("pcs", "mcs")) {
                    duplexClient::class.java.methods.first {
                        it.name == "registerHandler"
                    }.invoke(
                        duplexClient,
                        channel,
                        messageHandlerClass.declaredConstructors.first().also { it.isAccessible = true }.newInstance(-1),
                        dispatchQueue
                    )
                }
            }
        }
    }
}
