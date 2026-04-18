package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent

class NativeUnaryCallEvent(
    val uri: String,
    var buffer: ByteArray
) : AbstractHookEvent()