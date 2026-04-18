package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageContent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageDestinations

class MediaUploadEvent(
    val localMessageContent: MessageContent,
    val destinations: MessageDestinations,
    val callback: Any,
): AbstractHookEvent() {
    class MediaUploadResult(
        val messageContent: MessageContent
    )

    val mediaUploadCallbacks = mutableListOf<(MediaUploadResult) -> Unit>()

    fun onMediaUploaded(callback: (MediaUploadResult) -> Unit) {
        mediaUploadCallbacks.add(callback)
    }
}