package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.Event
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message

class BuildMessageEvent(
    val message: Message
): Event()