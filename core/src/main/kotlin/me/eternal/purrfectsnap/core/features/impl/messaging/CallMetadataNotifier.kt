package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import java.nio.ByteBuffer
import java.text.DateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

class CallMetadataNotifier : Feature("Call Metadata Notifier") {
    private data class CapturedCallMetadata(
        var callUuid: String? = null,
        var attemptId: String? = null,
        var media: String? = null,
        var isGroup: String? = null,
        var scopeId: String? = null,
        var ipv4Address: String? = null,
        var startTimestamp: Long? = null,
        var endedTimestamp: Long? = null,
        val messageTypes: LinkedHashSet<String> = linkedSetOf(),
        val rawPayloads: LinkedHashSet<String> = linkedSetOf()
    ) {
        fun reset() {
            callUuid = null
            attemptId = null
            media = null
            isGroup = null
            scopeId = null
            ipv4Address = null
            startTimestamp = null
            endedTimestamp = null
            messageTypes.clear()
            rawPayloads.clear()
        }

        fun hasData(): Boolean {
            return callUuid != null ||
                attemptId != null ||
                media != null ||
                isGroup != null ||
                scopeId != null ||
                ipv4Address != null ||
                messageTypes.isNotEmpty() ||
                rawPayloads.isNotEmpty()
        }
    }

    private val notificationChannelId = "call_metadata_notifier"
    private val metadata = CapturedCallMetadata()
    private var wasInCall = false
    private val translation by lazy { context.translation.getCategory("call_metadata_notifier") }
    private val notificationManager by lazy {
        context.androidContext.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(
                NotificationChannel(
                    notificationChannelId,
                    translation["notification_channel_name"],
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun isAscii(bytes: ByteArray): Boolean {
        return bytes.all { it in 0x20..0x7E || it == 0x0A.toByte() || it == 0x0D.toByte() }
    }

    private fun collectStrings(reader: ProtoReader, depth: Int = 0, output: MutableList<String>) {
        if (depth > 5) return
        reader.eachBuffer { _, buffer ->
            if (buffer.isEmpty()) return@eachBuffer
            if (isAscii(buffer)) {
                output.add(buffer.toString(Charsets.UTF_8))
            }
            runCatching { ProtoReader(buffer) }.getOrNull()?.let {
                collectStrings(it, depth + 1, output)
            }
        }
    }

    private fun parseObjectPayload(payload: String): Map<String, String> {
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|true|false|-?\\d+(?:\\.\\d+)?)")
        return regex.findAll(payload).associate { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2]
            key to rawValue.trim('"')
        }
    }

    @Synchronized
    private fun updateFromPayload(payload: String) {
        if (!payload.startsWith("{") || !payload.endsWith("}")) return

        val parsed = parseObjectPayload(payload)
        if (parsed.isEmpty()) return

        parsed["callUuid"]?.let { newCallUuid ->
            if (metadata.callUuid != null && metadata.callUuid != newCallUuid && metadata.hasData()) {
                notifyCallEnded("new_call_boundary")
            }
            metadata.callUuid = newCallUuid
        }

        parsed["attemptId"]?.let { metadata.attemptId = it }
        parsed["media"]?.let { metadata.media = it }
        parsed["isGroup"]?.let { metadata.isGroup = it }
        parsed["scopeId"]?.let { metadata.scopeId = it }
        parsed["ipv4Address"]?.let { metadata.ipv4Address = it }
        parsed["messageType"]?.let { metadata.messageTypes.add(it) }
        parsed["callAction"]?.let {
            metadata.messageTypes.add("callAction:$it")
            if (it.equals("START", ignoreCase = true) && metadata.startTimestamp == null) {
                metadata.startTimestamp = System.currentTimeMillis()
                wasInCall = true
            }
            if (it.equals("END", ignoreCase = true) || it.equals("HANGUP", ignoreCase = true)) {
                handleCallEnded("call_action:$it")
            }
        }
        metadata.rawPayloads.add(payload)
    }

    @Synchronized
    private fun handleVolatileEvent(reader: ProtoReader) {
        val payloads = mutableListOf<String>()
        collectStrings(reader, output = payloads)

        payloads.forEach { payload ->
            updateFromPayload(payload)
            val normalized = payload.lowercase(Locale.ROOT)
            if ("caller_hangup" in normalized || "\"messagetype\":\"call_end\"" in normalized) {
                handleCallEnded("volatile_end")
            }
        }
    }

    @Synchronized
    private fun handleCallStarted(reason: String) {
        if (!wasInCall) {
            wasInCall = true
            if (metadata.startTimestamp == null) {
                metadata.startTimestamp = System.currentTimeMillis()
            }
        }
        metadata.messageTypes.add("state:$reason")
    }

    @Synchronized
    private fun handleCallEnded(reason: String) {
        val shouldNotify = wasInCall || metadata.hasData()
        wasInCall = false
        if (!shouldNotify) return
        notifyCallEnded(reason)
    }

    @Synchronized
    private fun notifyCallEnded(reason: String) {
        if (!metadata.hasData()) return

        metadata.endedTimestamp = System.currentTimeMillis()
        val lines = buildList {
            metadata.ipv4Address?.let { add("IP: $it") }
            metadata.scopeId?.let { add("Scope ID: $it") }
            metadata.callUuid?.let { add("Call UUID: $it") }
            metadata.attemptId?.let { add("Attempt ID: $it") }
            metadata.media?.let { add("Media: $it") }
            metadata.isGroup?.let { add("Group Call: $it") }
            if (metadata.startTimestamp != null) {
                add("Started: ${DateFormat.getDateTimeInstance().format(Date(metadata.startTimestamp!!))}")
            }
            if (metadata.endedTimestamp != null) {
                add("Ended: ${DateFormat.getDateTimeInstance().format(Date(metadata.endedTimestamp!!))}")
            }
            if (metadata.messageTypes.isNotEmpty()) {
                add("Events: ${metadata.messageTypes.joinToString(", ")}")
            }
            add("Reason: $reason")
        }

        val notification = Notification.Builder(context.androidContext, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(translation["notification_title"])
            .setContentText(lines.firstOrNull() ?: translation["notification_empty"])
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()

        notificationManager.notify((metadata.callUuid ?: metadata.attemptId ?: reason).hashCode(), notification)
        metadata.reset()
        wasInCall = false
    }

    override fun init() {
        if (!context.config.messaging.callMetadataNotifier.get()) return

        runCatching {
            findClass("com.snapchat.client.duplex.MessageHandler\$CppProxy").hook("onReceive", HookStage.BEFORE) { param ->
                val buffer = param.argNullable<ByteBuffer>(0) ?: return@hook
                val duplicate = buffer.duplicate().apply { position(0) }
                val bytes = ByteArray(duplicate.limit())
                duplicate.get(bytes)

                val reader = ProtoReader(bytes)
                if (reader.getString(1, 1) != "volatile") return@hook
                val eventData = reader.followPath(1, 2) ?: return@hook
                handleVolatileEvent(eventData)
            }
        }

        runCatching {
            findClass("com.google.firebase.messaging.FirebaseMessagingService")
                .methods
                .first {
                    it.declaringClass.name == "com.google.firebase.messaging.FirebaseMessagingService" &&
                        it.returnType == Void::class.javaPrimitiveType &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == Intent::class.java
                }
                .hook(HookStage.BEFORE) { param ->
                    val intent = param.argNullable<Intent>(0) ?: return@hook
                    when (intent.getStringExtra("type")?.lowercase(Locale.ROOT)) {
                        "abandon_audio", "abandon_video" -> handleCallEnded("firebase:${intent.getStringExtra("type")}")
                    }
                }
        }

        listOf(
            "com.snapchat.talkcorev3.TalkCore\$CppProxy",
            "com.snapchat.talkcorev4.TalkCore\$CppProxy",
            "com.snapchat.talkcore.TalkCore\$CppProxy"
        ).forEach { className ->
            runCatching {
                findClass(className).apply {
                    hook("updateTSCallingSession", HookStage.BEFORE) { param ->
                        val params = param.argNullable<Any>(0) ?: return@hook
                        val inCall = params.getObjectFieldOrNull("mInCall") as? Boolean ?: return@hook
                        if (inCall) handleCallStarted("talkcore:update_true")
                        else handleCallEnded("talkcore:update_false")
                    }
                    hook("disposeTSCallingSession", HookStage.BEFORE) {
                        handleCallEnded("talkcore:dispose")
                    }
                }
            }
        }

        listOf(
            "com.snapchat.talkcorev3.TSCallingStateUpdateParams",
            "com.snapchat.talkcorev4.TSCallingStateUpdateParams",
            "com.snapchat.talkcore.TSCallingStateUpdateParams"
        ).forEach { className ->
            runCatching {
                findClass(className).hookConstructor(HookStage.AFTER) { param ->
                    val instance = param.thisObject<Any>()
                    val inCall = instance.getObjectFieldOrNull("mInCall") as? Boolean ?: return@hookConstructor
                    if (inCall) handleCallStarted("params_ctor:true")
                    else handleCallEnded("params_ctor:false")
                }
            }
        }
    }
}
