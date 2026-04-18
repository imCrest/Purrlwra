package cock.crest.purrfectsnap.lite.core.features.impl.spying

import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.RuleState
import cock.crest.purrfectsnap.lite.core.event.events.impl.OnSnapInteractionEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap

class StealthMode : MessagingRuleFeature("StealthMode", MessagingRuleType.STEALTH) {
    private val displayedMessageQueue = CopyOnWriteArraySet<Long>()
    private val snapInteractionQueue = CopyOnWriteArraySet<Long>()
    private val ruleSnapshotCache = ConcurrentHashMap<String, Pair<Long, Set<MessagingRuleType>>>()

    private fun getTargetId(conversationId: String): String {
        return context.database.getDMOtherParticipant(conversationId) ?: conversationId
    }

    private fun getRuleSnapshot(conversationId: String): Set<MessagingRuleType> {
        val targetId = getTargetId(conversationId)
        val now = System.currentTimeMillis()
        ruleSnapshotCache[targetId]?.takeIf { now - it.first < 1_000L }?.let { return it.second }
        return context.bridgeClient.getRules(targetId).toSet().also { rules ->
            ruleSnapshotCache[targetId] = now to rules
        }
    }

    private fun isRuleActive(
        conversationId: String,
        ruleType: MessagingRuleType
    ): Boolean {
        val ruleState = context.config.rules.getRuleState(ruleType) ?: return false
        val enabled = ruleType in getRuleSnapshot(conversationId)
        return if (ruleState == RuleState.BLACKLIST) !enabled else enabled
    }

    fun canUseChatStealth(conversationId: String): Boolean {
        return isRuleActive(conversationId, MessagingRuleType.STEALTH) ||
            isRuleActive(conversationId, MessagingRuleType.CHAT_STEALTH)
    }

    fun canUseSnapStealth(conversationId: String): Boolean {
        return isRuleActive(conversationId, MessagingRuleType.STEALTH) ||
            isRuleActive(conversationId, MessagingRuleType.SNAP_STEALTH)
    }

    fun isAnyStealthEnabled(conversationId: String): Boolean {
        return canUseChatStealth(conversationId) || canUseSnapStealth(conversationId)
    }

    fun addDisplayedMessageException(clientMessageId: Long) {
        displayedMessageQueue.add(clientMessageId)
    }

    fun addSnapInteractionException(messageId: Long) {
        snapInteractionQueue.add(messageId)
    }


    override fun init() {
        arrayOf("mediaMessagesDisplayed", "displayedMessages").forEach { methodName: String ->
            context.classCache.conversationManager.hook(methodName, HookStage.BEFORE) { param ->
                if (displayedMessageQueue.removeIf { param.arg<Long>(1) == it }) return@hook
                if (canUseChatStealth(SnapUUID(param.arg(0)).toString())) {
                    param.setResult(null)
                }
            }
        }

        context.event.subscribe(OnSnapInteractionEvent::class) { event ->
            if (snapInteractionQueue.removeIf { event.messageId == it }) return@subscribe
            if (canUseSnapStealth(event.conversationId.toString())) {
                event.canceled = true
            }
        }
    }
}
