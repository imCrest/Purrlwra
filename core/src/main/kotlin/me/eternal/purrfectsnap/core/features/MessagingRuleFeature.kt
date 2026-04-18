package cock.crest.purrfectsnap.lite.core.features

import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.RuleState
import java.util.concurrent.ConcurrentHashMap

abstract class MessagingRuleFeature(name: String, val ruleType: MessagingRuleType) : Feature(name) {
    private val listeners = mutableListOf<(String, Boolean) -> Unit>()
    private val ruleCache = ConcurrentHashMap<String, Boolean>()

    fun addStateListener(listener: (conversationId: String, newState: Boolean) -> Unit) {
        listeners.add(listener)
    }

    open fun getRuleState() = context.config.rules.getRuleState(ruleType)

    fun setState(conversationId: String, state: Boolean) {
        val targetId = context.database.getDMOtherParticipant(conversationId) ?: conversationId
        context.bridgeClient.setRule(
            targetId,
            ruleType,
            state
        )
        ruleCache[targetId] = state
        listeners.forEach { it(conversationId, state) }
    }

    fun getState(conversationId: String): Boolean {
        val targetId = context.database.getDMOtherParticipant(conversationId) ?: conversationId
        return ruleCache.getOrPut(targetId) {
            context.bridgeClient.getRules(targetId).contains(ruleType)
        } && getRuleState() != null
    }

    fun canUseRule(conversationId: String): Boolean {
        if (ruleType.key == "translation" && context.config.messaging.instantTranslation.globalState != true) {
            return false
        }
        val state = getState(conversationId)
        if (context.config.rules.getRuleState(ruleType) == RuleState.BLACKLIST) {
            return !state
        }
        return state
    }

    override fun onBridgeAction(action: String, extras: Map<String, Any>?, callback: (Any?) -> Unit) {
        if (action == "get_state") {
            val conversationId = extras?.get("conversationId") as? String ?: return
            callback(getState(conversationId))
            return
        }
        if (action == "set_state") {
            val conversationId = extras?.get("conversationId") as? String ?: return
            val state = extras["state"] as? Boolean ?: return
            setState(conversationId, state)
            callback(true)
            return
        }
        super.onBridgeAction(action, extras, callback)
    }
}
