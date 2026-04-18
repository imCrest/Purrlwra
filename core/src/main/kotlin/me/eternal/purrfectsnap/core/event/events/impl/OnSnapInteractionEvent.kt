package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID

class OnSnapInteractionEvent(
    val interactionType: String,
    val conversationId: SnapUUID,
    val messageId: Long
) : AbstractHookEvent()