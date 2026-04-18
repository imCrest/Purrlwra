package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message

class ConversationUpdateEvent(
    val conversationId: String,
    val conversation: Any?,
    val messages: List<Message>
) : AbstractHookEvent()