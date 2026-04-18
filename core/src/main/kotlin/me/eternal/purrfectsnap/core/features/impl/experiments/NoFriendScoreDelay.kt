package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.mapper.impl.ScoreUpdateMapper
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class NoFriendScoreDelay : Feature("NoFriendScoreDelay") {
    override fun init() {
        if (!context.config.experimental.noFriendScoreDelay.get()) return

        onNextActivityCreate {
            context.mappings.useMapper(ScoreUpdateMapper::class) {
                classReference.get()?.hookConstructor(HookStage.BEFORE) { param ->
                    param.args().indexOfFirst {
                        val longValue = it.toString().toLongOrNull() ?: return@indexOfFirst false
                        longValue > 30.minutes.inWholeMilliseconds && longValue < 10.days.inWholeMilliseconds
                    }.takeIf { it != -1 }?.let { index ->
                        param.setArg(index, 0)
                    }
                }
            }
        }
    }
}