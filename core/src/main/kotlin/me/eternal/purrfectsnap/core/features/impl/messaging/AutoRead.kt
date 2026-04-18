package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.core.event.events.impl.ConversationUpdateEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import java.util.concurrent.ConcurrentHashMap

class AutoRead : MessagingRuleFeature("Auto Read", MessagingRuleType.AUTO_READ) {
    private val lastMarked = ConcurrentHashMap<String, Long>()

    override fun init() {
        context.event.subscribe(ConversationUpdateEvent::class) { event ->
            if (!canUseRule(event.conversationId)) return@subscribe
            if (!shouldMarkConversation(event)) return@subscribe
            val now = System.currentTimeMillis()
            val last = lastMarked[event.conversationId] ?: 0L
            if (now - last < 1000L) return@subscribe
            lastMarked[event.conversationId] = now
            context.coroutineScope.launch(Dispatchers.IO) {
                markUnreadMessages(event.conversationId)
            }
        }
    }

    private suspend fun markUnreadMessages(conversationId: String) {
        runCatching {
            val messaging = context.feature(Messaging::class)
            val conversationManager = messaging.conversationManager ?: return
            val autoMark = context.feature(AutoMarkAsRead::class)
            val myId = context.database.myUserId
            val unread = collectUnreadMessages(conversationId, myId)
            if (unread.isEmpty()) return
            val chatIds = mutableSetOf<Long>()
            val snapIds = mutableSetOf<Long>()
            unread.forEach { message ->
                val messageId = message.clientMessageId.toLong()
                when (ContentType.fromId(message.contentType)) {
                    ContentType.SNAP,
                    ContentType.TINY_SNAP,
                    ContentType.EXTERNAL_MEDIA -> snapIds.add(messageId)
                    else -> chatIds.add(messageId)
                }
            }
            chatIds.sorted().forEach { id ->
                conversationManager.displayedMessages(conversationId, id) {
                    if (it != null) {
                        context.log.warn("AutoRead failed to mark chat message $id in $conversationId: $it")
                    }
                }
            }
            snapIds.sorted().forEach { id ->
                val error = autoMark.markSnapAsSeen(conversationId, id)
                if (error != null && error != "DUPLICATEREQUEST") {
                    context.log.warn("AutoRead failed to mark snap $id in $conversationId: $error")
                }
            }
        }.onFailure {
            context.log.error("AutoRead failed to mark conversation $conversationId", it)
        }
    }

    private fun collectUnreadMessages(conversationId: String, myId: String): List<cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage> {
        val unread = LinkedHashSet<cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage>()
        val pageSize = 200
        val maxPages = 5
        var page = 0
        while (page < maxPages) {
            val messages = context.database.getMessagesFromConversationId(conversationId, pageSize, page) ?: break
            if (messages.isEmpty()) break
            val filtered = messages.filter { message ->
                message.senderId != myId && message.isViewedByUser == 0
            }
            unread.addAll(filtered)
            if (messages.size < pageSize) break
            page++
        }
        return unread.toList()
    }

    private fun shouldMarkConversation(event: ConversationUpdateEvent): Boolean {
        val myId = context.database.myUserId
        return event.messages.any {
            val senderId = it.senderId?.toString() ?: return@any false
            if (senderId == myId) return@any false
            it.messageMetadata?.openedBy?.none { uuid -> uuid.toString() == myId } == true
        }
    }
}

