package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor

class DisableSnapModeRestrictions: Feature("Disable Snap Mode Restrictions") {
    override fun init() {
        if (!context.config.messaging.disableSnapModeRestrictions.get()) return

        findClass("com.snapchat.client.messaging.SnapModeInfo").hookConstructor(HookStage.AFTER) { param ->
            param.thisObject<Any>().dataBuilder {
                set("mSelfDestructSnapDurationMs", null)
            }
        }
    }
}