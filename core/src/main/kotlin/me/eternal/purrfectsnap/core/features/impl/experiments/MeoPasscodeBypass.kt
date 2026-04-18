package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.mapper.impl.BCryptClassMapper

class MeoPasscodeBypass : Feature("Meo Passcode Bypass") {
    override fun init() {
        if (!context.config.experimental.meoPasscodeBypass.get()) return

        onNextActivityCreate(defer = true) {
            context.mappings.useMapper(BCryptClassMapper::class) {
                classReference.get()?.hook(
                    hashMethod.get()!!,
                    HookStage.BEFORE,
                ) { param ->
                    //set the hash to the result of the method
                    param.setResult(param.arg(1))
                }
            }
        }
    }
}