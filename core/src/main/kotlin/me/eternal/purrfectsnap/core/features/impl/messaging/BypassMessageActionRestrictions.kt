package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.features.Feature

class BypassMessageActionRestrictions : Feature("Bypass Message Action Restrictions") {
    override fun init() {
        if (!context.config.messaging.bypassMessageActionRestrictions.get()) return
        onNextActivityCreate {
            context.event.subscribe(BuildMessageEvent::class, priority = 102) { event ->
                event.message.messageMetadata?.apply {
                    isSaveable = true
                    isReactable = true
                }
            }
        }
    }
}