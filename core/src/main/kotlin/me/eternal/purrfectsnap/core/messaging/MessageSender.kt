package cock.crest.purrfectsnap.lite.core.messaging

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoWriter
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.CallbackBuilder
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageDestinations
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper

class MessageSender(
    private val context: ModContext,
) {
    companion object {
        const val VOICE_NOTE_MAX_DURATION_MS = 100_000L

        val audioNoteProto: (Long, String?) -> ByteArray = { duration, userLocale ->
            ProtoWriter().apply {
                from(6, 1) {
                    from(1) {
                        addVarInt(2, 4)
                        from(5) {
                            addVarInt(1, 0)
                            addVarInt(2, 0)
                        }
                        addVarInt(7, 0)
                        addVarInt(13, duration)
                    }
                    if (userLocale != null) {
                        addString(3, userLocale)
                    }
                }
            }.toByteArray()
        }

    }

    private val sendMessageCallback by lazy {
        lateinit var result: Class<*>
        context.mappings.useMapper(CallbackMapper::class) {
            result = callbacks.getClass("SendMessageCallback") ?: return@useMapper
        }
        result
    }

    private fun createLocalMessageContentTemplate(
        contentType: ContentType,
        messageContent: ByteArray,
        localMediaReference: ByteArray? = null,
        savePolicy: String = "PROHIBITED",
    ): String {
        return """
        {
            "mAllowsTranscription": false,
            "mBotMention": false,
            "mBundleMetadata": null,
            "mContent": [${messageContent.joinToString(",")}],
            "mContentType": "${contentType.name}",
            "mExternalContentMetadata": null,
            "mFeedDisplayInfo": null,
            "mIncidentalAttachments": [],
            "mLocalMediaReferences": [${
                if (localMediaReference != null) {
                    "{\"mId\": [${localMediaReference.joinToString(",")}]}"
                } else {
                    ""
                }
            }],
            "mLocalPlatformData": null,
            "mMessageBehaviorHint": null,
            "mMessageTypeMetadata": null,
            "mPlatformAnalytics": {
                "mAttemptId": null,
                "mContent": null,
                "mMetricsMessageMediaType": "NO_MEDIA",
                "mMetricsMessageType": "TEXT",
                "mReactionSendSource": "NONE",
                "mReactionSource": "NONE",
                "mSendMessageAnalytics": null
            },
            "mQuotedMessageId": null,
            "mRemoteMediaReferences": [],
            "mSavePolicy": "$savePolicy",
            "mSnapModeInfo": null
        }
        """.trimIndent()
    }

    private fun internalSendMessage(conversations: List<SnapUUID>, localMessageContentTemplate: String, callback: Any) {
        val sendMessageWithContentMethod = sequence {
            var current: Class<*>? = context.classCache.conversationManager
            while (current != null && current != Any::class.java && current != Object::class.java) {
                yield(current)
                current = current.superclass
            }
        }.flatMap { clazz -> clazz.declaredMethods.asSequence() }
            .firstOrNull { it.name == "sendMessageWithContent" }
            ?: throw NoSuchMethodException("sendMessageWithContent")

        val localMessageContent = context.gson.fromJson(localMessageContentTemplate, context.classCache.localMessageContent)
        val messageDestinations = MessageDestinations(AbstractWrapper.newEmptyInstance(context.classCache.messageDestinations)).also {
            it.conversations = conversations.toCollection(ArrayList())
            it.mPhoneNumbers = arrayListOf<Any>()
            it.stories = arrayListOf<Any>()
            it.massSnaps = arrayListOf<Any>()
        }

        sendMessageWithContentMethod.invoke(context.feature(Messaging::class).conversationManager?.instanceNonNull(), messageDestinations.instanceNonNull(), localMessageContent, callback)
    }

    fun sendChatMessage(conversations: List<SnapUUID>, message: String, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        internalSendMessage(conversations, createLocalMessageContentTemplate(ContentType.CHAT, ProtoWriter().apply {
            from(2) {
                addString(1, message)
            }
        }.toByteArray(), savePolicy = "LIFETIME"), CallbackBuilder(sendMessageCallback)
            .override("onSuccess", callback = { onSuccess() })
            .override("onError", callback = { onError(it.arg(0)) })
            .build())
    }

    fun sendCustomChatMessage(conversations: List<SnapUUID>, contentType: ContentType, message: ProtoWriter.() -> Unit, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        internalSendMessage(conversations, createLocalMessageContentTemplate(contentType, ProtoWriter().apply {
            message()
        }.toByteArray(), savePolicy = "LIFETIME"), CallbackBuilder(sendMessageCallback)
            .override("onSuccess", callback = { onSuccess() })
            .override("onError", callback = { onError(it.arg(0)) })
            .build())
    }
}
