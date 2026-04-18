package cock.crest.purrfectsnap.lite.core.features.impl.ui

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID

class HideStreakRestore : Feature("HideStreakRestore") {
    override fun init() {
        if (!context.config.userInterface.hideStreakRestore.get()) return

        findClass("com.snapchat.client.messaging.FeedEntry").hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            if (instance.getObjectFieldOrNull("mDisplayInfo")
                    ?.getObjectFieldOrNull("mFeedItem")
                    ?.getObjectFieldOrNull("mConversation")
                    ?.getObjectFieldOrNull("mState")
                    ?.toString() == "STREAK_RESTORE") {
                instance.getObjectFieldOrNull("mDisplayInfo")
                    ?.getObjectFieldOrNull("mFeedItem")
                    ?.setObjectField("mConversation", null)
                val conversationId = SnapUUID(instance.getObjectField("mConversationId")).toString()
                context.feature(Messaging::class).conversationManager?.dismissStreakRestore(
                    conversationId,
                    onError = {
                        context.log.error("Failed to dismiss streak restore: $it")
                    }, onSuccess = {
                        context.log.info("Dismissed streak restore for conversation $conversationId")
                    }
                )
            }
        }

        findClass("com.snapchat.client.messaging.StreakMetadata").hookConstructor(HookStage.AFTER) { param ->
            param.thisObject<Any>().dataBuilder {
                set("mExpiredStreak", null)
            }
        }
    }
}