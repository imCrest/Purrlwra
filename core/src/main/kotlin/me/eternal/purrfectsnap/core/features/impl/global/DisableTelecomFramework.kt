package cock.crest.purrfectsnap.lite.core.features.impl.global

import android.content.ContextWrapper
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class DisableTelecomFramework: Feature("Disable Telecom Framework") {
    override fun init() {
        if (!context.config.global.disableTelecomFramework.get() && !context.config.messaging.blockCalls.get()) return

        ContextWrapper::class.java.hook("getSystemService", HookStage.BEFORE) { param ->
            if (param.arg<Any>(0).toString() == "telecom") param.setResult(null)
        }
    }
}
