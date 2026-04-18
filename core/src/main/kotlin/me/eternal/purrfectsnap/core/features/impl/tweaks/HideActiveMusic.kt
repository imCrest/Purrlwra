package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import android.media.AudioManager
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class HideActiveMusic: Feature("Hide Active Music") {
    override fun init() {
        if (!context.config.global.hideActiveMusic.get()) return
        onNextActivityCreate {
            AudioManager::class.java.hook("isMusicActive", HookStage.BEFORE) {
                it.setResult(false)
            }
        }
    }
}