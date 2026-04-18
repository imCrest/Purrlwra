package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoEditor
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.NativeUnaryCallEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField

class UnsaveableMessages : MessagingRuleFeature(
    "Unsaveable Messages",
    MessagingRuleType.UNSAVEABLE_MESSAGES
) {
    private var shouldModifySavePolicy = false

    private fun getEnabledFields(): List<Int> {
        val config = context.config.messaging.unsaveableMessages
        return buildList {
            if (config.chat.get()) add(2)
            if (config.externalMedia.get()) add(3)
            if (config.sticker.get()) add(4)
            if (config.share.get()) add(5)
            if (config.note.get()) add(6)
            if (config.storyReply.get()) add(7)
            if (config.snap.get()) add(11)
        }
    }

    override fun init() {
        if (context.config.rules.getRuleState(MessagingRuleType.UNSAVEABLE_MESSAGES) == null) return

        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            if (event.canceled) return@subscribe
            shouldModifySavePolicy = false
            
            if (event.destinations.stories?.isNotEmpty() == true && event.destinations.conversations?.isEmpty() == true) return@subscribe
            
            val localMessageContent = event.messageContent
            if (localMessageContent.contentType == ContentType.STATUS) return@subscribe

            val conversationIds = event.destinations.conversations?.map { it.toString() } ?: emptyList()
            val shouldApply = conversationIds.isEmpty() || conversationIds.all { canUseRule(it) }

            if (!shouldApply) return@subscribe

            val messageContentBytes = localMessageContent.content ?: return@subscribe
            val enabledFields = getEnabledFields()
            val protoReader = ProtoReader(messageContentBytes)

            val fieldPath = enabledFields.firstOrNull { protoReader.followPath(it) != null } ?: return@subscribe

            shouldModifySavePolicy = true
            
            // Set mSavePolicy on Java object
            try {
                val savePolicyEnumClass = Class.forName(
                    "com.snapchat.client.messaging.SavePolicy",
                    false,
                    localMessageContent.instanceNonNull().javaClass.classLoader
                )
                
                if (savePolicyEnumClass.isEnum) {
                    @Suppress("UNCHECKED_CAST")
                    val prohibitedEnum = java.lang.Enum.valueOf(
                        savePolicyEnumClass as Class<out Enum<*>>,
                        "PROHIBITED"
                    )
                    localMessageContent.instanceNonNull().setObjectField("mSavePolicy", prohibitedEnum)
                }
            } catch (e: Exception) {
                context.log.warn("UnsaveableMessages: Failed to set mSavePolicy: ${e.message}")
            }
            
            // Modify proto bytes
            try {
                localMessageContent.content = ProtoEditor(messageContentBytes).apply {
                    edit(fieldPath) {
                        remove(7)
                        addVarInt(7, 1)
                    }
                }.toByteArray()
            } catch (e: Exception) {
                context.log.error("UnsaveableMessages: Failed to modify savePolicy in proto: ${e.message}")
            }
        }

        // Fallback: Modify in NativeUnaryCallEvent
        context.event.subscribe(NativeUnaryCallEvent::class) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            if (!shouldModifySavePolicy) return@subscribe
            
            try {
                val enabledFields = getEnabledFields()
                val protoReader = ProtoReader(event.buffer)
                val fieldPath = enabledFields.firstOrNull { protoReader.followPath(it) != null } ?: return@subscribe

                event.buffer = ProtoEditor(event.buffer).apply {
                    edit(fieldPath) {
                        remove(7)
                        addVarInt(7, 1)
                    }
                }.toByteArray()
            } catch (e: Exception) {
                context.log.error("UnsaveableMessages: NativeUnaryCallEvent modification failed: ${e.message}")
            } finally {
                shouldModifySavePolicy = false
            }
        }
    }
}
