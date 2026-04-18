package cock.crest.purrfectsnap.lite.core.features.impl

import cock.crest.purrfectsnap.lite.core.features.Feature

import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.mapper.impl.COFObservableMapper
import java.lang.reflect.Method

class COFOverride : Feature("COF Override") {
    var hasActionMenuV2 = false

    override fun init() {
        val cofExperiments by context.config.experimental.cofExperiments

        context.mappings.useMapper(COFObservableMapper::class) {
            classReference.getAsClass()?.hook(getBooleanObservable.get() ?: return@useMapper, HookStage.AFTER) { param ->
                val configId = param.arg<String>(0)
                val result by lazy { param.getResult()?.getObjectField("b") }

                fun setBooleanResult(state: Boolean) {
                    param.setResult((param.method() as Method).returnType.dataBuilder {
                        set("a", 4)
                        set("b", state)
                    })
                }

                if (cofExperiments.contains(configId.lowercase())) {
                    setBooleanResult(true)
                }

                if ((configId == "ANDROID_ACTION_MENU_V2" || configId == "ANDROID_ACTION_MENU_ADJUST_MESSAGE_POSITION") && result == true) {
                    hasActionMenuV2 = true
                }
            }
        }
    }
}