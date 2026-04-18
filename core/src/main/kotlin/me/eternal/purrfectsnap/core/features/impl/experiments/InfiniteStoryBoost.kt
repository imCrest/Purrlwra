package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.mapper.impl.StoryBoostStateMapper

class InfiniteStoryBoost : Feature("InfiniteStoryBoost") {
    override fun init() {
        if (!context.config.experimental.infiniteStoryBoost.get()) return

        onNextActivityCreate(defer = true) {
            context.mappings.useMapper(StoryBoostStateMapper::class) {
                classReference.get()?.hookConstructor(HookStage.BEFORE) { param ->
                    val startTimeMillis = param.arg<Long>(1)
                    //reset timestamp if it's more than 24 hours
                    if (System.currentTimeMillis() - startTimeMillis > 86400000) {
                        param.setArg(1, 0)
                        param.setArg(2, 0)
                    }
                }
            }
        }
    }
}