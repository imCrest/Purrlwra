package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessageUpdate
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.common.util.ktx.findFieldsToString
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.AutoMarkAsRead
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.features.impl.spying.StealthMode
import cock.crest.purrfectsnap.lite.core.ui.getValdiContext
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText
import cock.crest.purrfectsnap.lite.mapper.impl.ChatEventDispatcherMapper

class DoubleTapChatAction: Feature("Double Tap Chat Action") {
    private data class ChatDoubleTapTarget(
        val conversationId: String,
        val messageId: Long
    )

    private val messageIdPattern = Regex("([0-9a-fA-F-]{36}):[^,\\s:]+:(\\d+)")
    private var lastTapTarget: ChatDoubleTapTarget? = null
    private var lastTapAt = 0L
    private var lastTapDownTime = -1L
    private var lastHandledTarget: ChatDoubleTapTarget? = null
    private var lastHandledAt = 0L

    private fun resolveTarget(rawValue: String?): ChatDoubleTapTarget? {
        val match = rawValue?.let { messageIdPattern.find(it) } ?: return null
        val conversationId = match.groupValues[1]
        val messageId = match.groupValues[2].toLongOrNull() ?: return null
        val message = context.database.getConversationMessageFromId(messageId) ?: return null
        if (message.clientConversationId != conversationId) return null
        return ChatDoubleTapTarget(conversationId, messageId)
    }

    private fun resolveTargetFromDispatcherEvent(event: Any): ChatDoubleTapTarget? {
        resolveTarget(event.toString())?.let { return it }
        val field = event.javaClass.findFieldsToString(event, once = true) { _, value ->
            value.contains("ChatViewModel") || messageIdPattern.containsMatchIn(value)
        }.firstOrNull() ?: return null
        return resolveTarget(field.get(event)?.toString())
    }

    private fun resolveTargetFromView(view: View): ChatDoubleTapTarget? {
        val valdiContext = view.getValdiContext() ?: return null
        return sequenceOf(
            valdiContext.viewModel,
            valdiContext.viewModelLegacy,
            valdiContext.componentContext?.get()
        ).mapNotNull { candidate ->
            resolveTarget(candidate?.toString())
        }.firstOrNull()
    }

    private fun executeAction(action: String, target: ChatDoubleTapTarget) {
        if (
            lastHandledTarget == target &&
            SystemClock.uptimeMillis() - lastHandledAt <= ViewConfiguration.getDoubleTapTimeout().toLong()
        ) {
            return
        }

        lastHandledTarget = target
        lastHandledAt = SystemClock.uptimeMillis()

        if (action == "like_message") {
            context.feature(Messaging::class).conversationManager?.reactToMessage(
                target.conversationId,
                target.messageId,
                intentionType = 1L,
                onError = {},
                onSuccess = {}
            )
        }

        if (action == "copy_text") {
            val messageContent = context.database.getConversationMessageFromId(target.messageId)?.messageContent ?: return
            val proto = ProtoReader(messageContent).followPath(4, 4) ?: return
            context.androidContext.copyToClipboard(
                proto.getBuffer().getMessageText(ContentType.fromMessageContainer(proto) ?: ContentType.CHAT) ?: return,
                "Chat Message"
            )
        }

        if (action == "delete_message") {
            context.feature(Messaging::class).conversationManager?.updateMessage(
                target.conversationId,
                target.messageId,
                MessageUpdate.ERASE,
                onResult = {}
            )
        }

        if (action == "mark_as_read") {
            val message = context.database.getConversationMessageFromId(target.messageId) ?: return
            when (ContentType.fromId(message.contentType)) {
                ContentType.SNAP,
                ContentType.TINY_SNAP,
                ContentType.EXTERNAL_MEDIA -> {
                    context.coroutineScope.launch {
                        context.feature(AutoMarkAsRead::class).markSnapAsSeen(target.conversationId, target.messageId)
                    }
                }
                else -> {
                    context.feature(StealthMode::class).addDisplayedMessageException(target.messageId)
                    context.feature(Messaging::class).conversationManager?.displayedMessages(
                        target.conversationId,
                        target.messageId,
                        onResult = {
                            if (it != null) {
                                context.log.error("Failed to mark conversation as read: $it")
                                context.shortToast(context.translation["toast_mark_conversation_read_failed"])
                            }
                        }
                    )
                }
            }
        }

        if (action == "custom_emoji_reaction") {
            context.feature(Messaging::class).conversationManager?.reactToMessage(
                target.conversationId,
                target.messageId,
                emoji = context.config.messaging.doubleTapChatActionCustomEmoji.getNullable()?.takeIf { it.isNotEmpty() } ?: "\uD83D\uDC4D",
                onError = {},
                onSuccess = {}
            )
        }
    }

    override fun init() {
        var action = context.config.messaging.doubleTapChatAction.getNullable() ?: return

        context.mappings.useMapper(ChatEventDispatcherMapper::class) {
            classReference.getAsClass()?.hook("onChatItemDoubleClickEvent", HookStage.BEFORE) { param ->
                param.setResult(null)
                resolveTargetFromDispatcherEvent(param.arg(0))?.let { executeAction(action, it) }
            }
        }

        View::class.java.hook("dispatchTouchEvent", HookStage.BEFORE) { param ->
            val motionEvent = param.arg<MotionEvent>(0)
            if (motionEvent.actionMasked != MotionEvent.ACTION_UP) return@hook
            if (motionEvent.eventTime - motionEvent.downTime > ViewConfiguration.getTapTimeout()) return@hook
            if (lastTapDownTime == motionEvent.downTime) return@hook

            val target = resolveTargetFromView(param.thisObject()) ?: return@hook
            val now = SystemClock.uptimeMillis()
            val isSecondTap = lastTapTarget == target &&
                now - lastTapAt <= ViewConfiguration.getDoubleTapTimeout().toLong()

            lastTapDownTime = motionEvent.downTime
            if (isSecondTap) {
                executeAction(action, target)
                lastTapTarget = null
                lastTapAt = 0L
                return@hook
            }

            lastTapTarget = target
            lastTapAt = now
        }
    }
}
