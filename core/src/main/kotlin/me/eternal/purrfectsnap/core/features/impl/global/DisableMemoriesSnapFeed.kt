package cock.crest.purrfectsnap.lite.core.features.impl.global

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.mapper.impl.MemoriesPresenterMapper

class DisableMemoriesSnapFeed : Feature("Disable Memories Snap Feed") {
    override fun init() {
        if (!context.config.global.disableMemoriesSnapFeed.get()) return
        onNextActivityCreate {
            context.mappings.useMapper(MemoriesPresenterMapper::class) {
                classReference.get()?.apply {
                    val getNameMethod = getMethod("getName") ?: return@apply

                    hook(onNavigationEventMethod.get()!!, HookStage.BEFORE) { param ->
                        val instance = param.thisObject<Any>()

                        if (getNameMethod.invoke(instance) == "MemoriesAsyncPresenterFragmentSubscriber") {
                            param.setResult(null)
                        }
                    }
                }
            }
        }
    }
}