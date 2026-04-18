package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.NotificationType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoEditor
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.NativeUnaryCallEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class PreventMessageSending : Feature("Prevent message sending") {
    override fun init() {
        val preventMessageSending by context.config.messaging.preventMessageSending

        fun handleUpdateContentMessage(uri: String, buffer: ByteArray): ByteArray? {
            if (uri != "/messagingcoreservice.MessagingCoreService/UpdateContentMessage") return null
            return ProtoEditor(buffer).apply {
                edit(3) {
                    // replace replayed to read receipt
                    if (firstOrNull(13) != null) {
                        remove(13)
                        addBuffer(4, byteArrayOf())
                    }
                }
            }.toByteArray()
        }

        fun handleNativeUnaryCall(uri: String, buffer: ByteArray): ByteArray? {
            if (uri == "/messagingcoreservice.MessagingCoreService/UpdateContentMessage") {
                return handleUpdateContentMessage(uri, buffer)
            }
            return null
        }

        context.event.subscribe(NativeUnaryCallEvent::class) { event ->
            if (event.uri == "/messagingcoreservice.MessagingCoreService/CreateContentMessage") {
                val contentTypeId = ProtoReader(event.buffer).getVarInt(4, 2)?.toInt() ?: return@subscribe
                val associatedType = NotificationType.fromContentType(ContentType.fromId(contentTypeId)) ?: return@subscribe
                if (preventMessageSending.contains(associatedType.key) && associatedType.key != "snap_replay") {
                    context.log.verbose("Preventing native CreateContentMessage for $associatedType")
                    event.canceled = true
                }
            }

            if (preventMessageSending.contains("snap_replay")) {
                handleNativeUnaryCall(event.uri, event.buffer)?.let { event.buffer = it }
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri == "/messagingcoreservice.MessagingCoreService/CreateContentMessage") {
                val contentTypeId = ProtoReader(event.buffer).getVarInt(4, 2)?.toInt() ?: return@subscribe
                val associatedType = NotificationType.fromContentType(ContentType.fromId(contentTypeId)) ?: return@subscribe
                if (preventMessageSending.contains(associatedType.key) && associatedType.key != "snap_replay") {
                    context.log.verbose("Preventing CreateContentMessage for $associatedType")
                    event.canceled = true
                }
            }

            if (preventMessageSending.contains("snap_replay")) {
                handleNativeUnaryCall(event.uri, event.buffer)?.let { event.buffer = it }
            }
        }

        context.classCache.conversationManager.hook("updateMessage", HookStage.BEFORE) { param ->
            val messageUpdate = param.arg<Any>(2).toString()
            if (messageUpdate == "SCREENSHOT" && preventMessageSending.contains("chat_screenshot")) {
                param.setResult(null)
            }

            if (messageUpdate == "SCREEN_RECORD" && preventMessageSending.contains("chat_screen_record")) {
                param.setResult(null)
            }

            if (messageUpdate == "REPLAY" && preventMessageSending.contains("snap_replay")) {
                param.setResult(null)
            }
        }

        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            val contentType = event.messageContent.contentType
            val associatedType = NotificationType.fromContentType(contentType ?: return@subscribe) ?: return@subscribe

            if (preventMessageSending.contains(associatedType.key)) {
                context.log.verbose("Preventing message sending for $associatedType")
                event.canceled = true
            }
        }
    }
}
