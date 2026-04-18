package cock.crest.purrfectsnap.lite.core.features.impl.ui

import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.RuleState
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID

class PinConversations : MessagingRuleFeature("PinConversations", MessagingRuleType.PIN_CONVERSATION) {
    override fun init() {
        if (!context.config.messaging.unlimitedConversationPinning.get()) return

        context.classCache.feedManager.hook("setPinnedConversationStatus", HookStage.BEFORE) { param ->
            val conversationUUID = SnapUUID(param.arg(0))
            val isPinned = param.arg<Any>(1).toString() == "PINNED"
            setState(conversationUUID.toString(), isPinned)
            val callback = param.arg<Any>(2)
            mutableSetOf<() -> Unit>().apply {
                addAll(Hooker.ephemeralHookObjectMethod(callback::class.java, callback,"onSuccess", HookStage.BEFORE) {
                    forEach { it() }
                })
                addAll(Hooker.ephemeralHookObjectMethod(callback::class.java, callback,"onError", HookStage.BEFORE) { methodParam ->
                    methodParam.setResult(null)
                    callback::class.java.getDeclaredMethod("onSuccess").invoke(callback)
                })
            }
        }

        context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationUUID = SnapUUID(instance.getObjectField("mConversationId"))
            if (getState(conversationUUID.toString())) {
                instance.setObjectField("mPinnedTimestampMs", 1L)
            }
        }

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationUUID = SnapUUID(instance.getObjectField("mConversationId") ?: return@hookConstructor)
            val isPinned = getState(conversationUUID.toString())
            if (isPinned) {
                instance.setObjectField("mPinnedTimestampMs", 1L)
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}