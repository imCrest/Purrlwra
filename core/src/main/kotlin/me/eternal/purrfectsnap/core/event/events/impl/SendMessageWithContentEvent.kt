package cock.crest.purrfectsnap.lite.core.event.events.impl

import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageContent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageDestinations

class SendMessageWithContentEvent(
    val destinations: MessageDestinations,
    val messageContent: MessageContent,
    private val callback: Any
) : AbstractHookEvent() {

    fun addCallbackResult(methodName: String, block: (args: Array<Any?>) -> Unit) {
        Hooker.ephemeralHookObjectMethod(
            callback::class.java,
            callback,
            methodName,
            HookStage.BEFORE
        ) { block(it.args()) }
    }
}