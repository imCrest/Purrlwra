package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setEnumField

class DisableReplayInFF : Feature("DisableReplayInFF") {
    override fun init() {
        val state by context.config.messaging.disableReplayInFF

        onNextActivityCreate(defer = true) {
            findClass("com.snapchat.client.messaging.InteractionInfo")
                .hookConstructor(HookStage.AFTER, { state }) { param ->
                    val instance = param.thisObject<Any>()
                    if (instance.getObjectField("mLongPressActionState").toString() == "REQUEST_SNAP_REPLAY") {
                        instance.setEnumField("mLongPressActionState", "SHOW_CONVERSATION_ACTION_MENU")
                    }
                }
        }
    }
}