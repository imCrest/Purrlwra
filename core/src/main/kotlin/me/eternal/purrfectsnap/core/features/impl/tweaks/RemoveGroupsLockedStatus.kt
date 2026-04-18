package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor

class RemoveGroupsLockedStatus : Feature("Remove Groups Locked Status") {
    override fun init() {
        if (!context.config.messaging.removeGroupsLockedStatus.get()) return
        onNextActivityCreate(defer = true) {
            context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
                param.thisObject<Any>().dataBuilder {
                    set("mLockedState", "UNLOCKED")
                }
            }
        }
    }
}