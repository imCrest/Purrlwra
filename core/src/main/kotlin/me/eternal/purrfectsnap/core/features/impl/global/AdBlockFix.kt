package cock.crest.purrfectsnap.lite.core.features.impl.global

import android.os.SystemClock
import android.view.View
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.hideViewCompletely
import cock.crest.purrfectsnap.lite.core.ui.dispatchSyntheticTap
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.Layer
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.ParamMap
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class AdBlockFix : Feature("AdBlockFix") {
    private val adConversationIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    private var lastAutoSkippedOperaFingerprint: String? = null

    @Volatile
    private var lastAutoSkippedOperaAt = 0L

    override fun init() {
        if (!context.config.global.blockAds.get()) return

        hookFeedEntryTracking()
        hookMessagingFeedCallbacks()
        hookChatFeedRowSuppression()
        hookOperaAutoSkip()
    }

    private fun hookFeedEntryTracking() {
        findClass("com.snapchat.client.messaging.FeedEntry").hookConstructor(HookStage.AFTER) { param ->
            val feedEntry = param.thisObject<Any>()
            val conversationId = feedEntry.getObjectFieldOrNull("mConversationId")?.let(::SnapUUID)?.toString()
                ?: return@hookConstructor

            if (isCampaignFeedEntry(feedEntry) || isChatAdShareFeedEntry(feedEntry)) {
                adConversationIds.add(conversationId)
            }
        }
    }

    private fun hookMessagingFeedCallbacks() {
        context.mappings.useMapper(CallbackMapper::class) {
            classLoader = context.androidContext.classLoader
            val callbackMap = callbacks.getAsMap().orEmpty()
            val hookedCallbacks = mutableSetOf<String>()

            fun hookOnce(
                callbackClassName: String,
                methodName: String,
                block: (param: cock.crest.purrfectsnap.lite.core.util.hook.HookAdapter) -> Unit
            ) {
                val hookKey = "$callbackClassName#$methodName"
                if (!hookedCallbacks.add(hookKey)) return
                runCatching {
                    findClass(callbackClassName).hook(methodName, HookStage.BEFORE) { param ->
                        block(param)
                    }
                }.onFailure {
                    context.log.warn("Failed to hook $methodName on $callbackClassName")
                }
            }

            callbackMap.entries.forEach { (callbackName, callbackClassName) ->
                val className = callbackClassName ?: return@forEach
                when {
                    callbackName.startsWith("FetchAndSyncFeed") && callbackName.endsWith("Callback") -> {
                        hookOnce(className, "onFetchAndSyncFeedComplete") { param ->
                            val deletedEntries = param.argNullable<ArrayList<Any>>(2)
                            filterCampaignFeed(param.arg(0), deletedEntries)
                            if (deletedEntries?.isNotEmpty() == true) {
                                param.setArg(4, true)
                            }
                        }
                    }

                    callbackName.contains("SyncFeed") && callbackName.endsWith("Callback") -> {
                        hookOnce(className, "onSyncFeedComplete") { param ->
                            filterCampaignFeed(param.arg(0), param.argNullable(2))
                        }
                    }

                    callbackName == "FetchFeedCallback" || callbackName.contains("FetchFeedCallback") -> {
                        hookOnce(className, "onFetchFeedComplete") { param ->
                            filterCampaignFeed(param.arg(0))
                        }
                    }

                    callbackName == "FetchFeedEntriesCallback" || callbackName.contains("FetchFeedEntriesCallback") -> {
                        hookOnce(className, "onFetchFeedEntriesComplete") { param ->
                            filterCampaignFeed(param.arg(0))
                        }
                    }

                    callbackName == "QueryFeedCallback" || callbackName.contains("QueryFeedCallback") -> {
                        hookOnce(className, "onQueryFeedComplete") { param ->
                            filterCampaignFeed(param.arg(0))
                        }
                    }

                    callbackName == "FeedManagerDelegate" -> {
                        hookOnce(className, "onFeedEntriesUpdated") { param ->
                            filterCampaignFeed(param.arg(0))
                        }
                        hookOnce(className, "onInternalSyncFeed") { param ->
                            filterCampaignFeed(param.arg(0))
                        }
                    }
                }
            }
        }
    }

    private fun hookChatFeedRowSuppression() {
        context.event.subscribe(BindViewEvent::class) { event ->
            val modelDump = event.prevModel.toString()
            event.friendFeedItem { conversationId ->
                if (adConversationIds.contains(conversationId) || isChatAdShareModel(modelDump)) {
                    hideBoundChatFeedRow(event.view)
                }
            }
        }
    }

    private fun hideBoundChatFeedRow(view: View) {
        view.hideViewCompletely()
        (view.parent as? View)?.hideViewCompletely()
        (view.parent?.parent as? View)?.hideViewCompletely()
    }

    private fun hookOperaAutoSkip() {
        onNextActivityCreate {
            context.mappings.useMapper(OperaPageViewControllerMapper::class) {
                arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                    val resolvedMethod = methodName.get() ?: return@forEach
                    classReference.get()?.hook(resolvedMethod, HookStage.AFTER) { param ->
                        val viewState = runCatching {
                            param.thisObject<Any>().getObjectField(viewStateField.get()!!)?.toString()
                        }.getOrNull() ?: return@hook
                        if (viewState != "FULLY_DISPLAYED") return@hook

                        val layerList = runCatching {
                            param.thisObject<Any>().getObjectField(layerListField.get()!!) as? ArrayList<*>
                        }.getOrNull() ?: return@hook
                        val paramMap = runCatching {
                            layerList.map { Layer(it).paramMap }.firstOrNull()
                        }.getOrNull() ?: return@hook

                        if (!isSpotlightCommercialPage(paramMap)) return@hook

                        val fingerprint = buildOperaFingerprint(paramMap)
                        val now = SystemClock.elapsedRealtime()
                        if (fingerprint == lastAutoSkippedOperaFingerprint && now - lastAutoSkippedOperaAt < 1_500L) {
                            return@hook
                        }
                        lastAutoSkippedOperaFingerprint = fingerprint
                        lastAutoSkippedOperaAt = now

                        runOnUiThread {
                            context.mainActivity?.window?.decorView?.postDelayed({
                                context.mainActivity?.window?.decorView?.let { decorView ->
                                    val x = decorView.width * 0.88f
                                    val y = decorView.height * 0.5f
                                    decorView.dispatchSyntheticTap(x, y)
                                }
                            }, 70L)
                        }
                    }
                }
            }
        }
    }

    private fun filterCampaignFeed(entries: ArrayList<Any>, deletedEntries: ArrayList<Any>? = null) {
        entries.removeIf { feedEntry ->
            if (!isCampaignFeedEntry(feedEntry)) return@removeIf false
            val conversationIdInstance = feedEntry.getObjectFieldOrNull("mConversationId") ?: return@removeIf true
            deletedEntries?.add(createDeletedFeedEntry(conversationIdInstance))
            true
        }
    }

    private fun createDeletedFeedEntry(conversationIdInstance: Any) =
        findClass("com.snapchat.client.messaging.DeletedFeedEntry").dataBuilder {
            from("mFeedEntryIdentifier") {
                set("mConversationId", conversationIdInstance)
            }
            set("mReason", "AD_CAMPAIGN_COMPLETE")
        }!!

    private fun isCampaignFeedEntry(feedEntry: Any?): Boolean {
        if (feedEntry == null) return false
        if (feedEntry.getObjectFieldOrNull("mConversationSubType")?.toString() == "CAMPAIGN") {
            return true
        }
        return feedEntry.getObjectFieldOrNull("mConversationSubTypeMetadata")
            ?.getObjectFieldOrNull("mCampaignMetadata") != null
    }

    private fun isChatAdShareFeedEntry(feedEntry: Any): Boolean {
        val interactionDump = feedEntry.getObjectFieldOrNull("mInteractionInfo")?.toString().orEmpty()
        val displayDump = feedEntry.getObjectFieldOrNull("mDisplayInfo")?.toString().orEmpty()
        val combined = "$interactionDump $displayDump"
        return isChatAdShareModel(combined)
    }

    private fun isChatAdShareModel(modelDump: String): Boolean {
        if (modelDump.isBlank()) return false
        return modelDump.contains("CHAT_AD_SHARE") ||
            modelDump.contains("AD_SHARE") ||
            modelDump.contains("ChatAd") ||
            modelDump.contains("chat_ad_share") ||
            modelDump.contains("chat_sponsored_snap") ||
            modelDump.contains("CommonAttachmentViewModel") ||
            modelDump.contains("visibilityFeedbackURL") ||
            modelDump.contains("pageLoadPingURL")
    }

    private fun isSpotlightCommercialPage(paramMap: ParamMap): Boolean {
        val snapSource = paramMap["SNAP_SOURCE"]?.toString()
        if (snapSource != "SINGLE_SNAP_STORY" && snapSource != "SPOTLIGHT" && snapSource != "PUBLIC_STORY") {
            return false
        }

        val adProductType = paramMap["ad_product_type"]?.toString()
        if (!adProductType.isNullOrBlank() && adProductType != "UNKNOWN" && adProductType != "null") {
            return true
        }

        val pageDump = paramMap.toString().uppercase()
        return pageDump.contains("COMMERCIAL") || pageDump.contains("PROMOTED_STORY")
    }

    private fun buildOperaFingerprint(paramMap: ParamMap): String {
        val storyId = paramMap["STORY_ID"]?.toString()
        val snapId = paramMap["SNAP_ID"]?.toString()
            ?: paramMap["snap_id"]?.toString()
        val index = paramMap["snap_index_in_story"]?.toString()
            ?: paramMap["SNAP_POSITION_IN_STORY"]?.toString()
        return listOfNotNull(storyId, snapId, index, paramMap["ad_product_type"]?.toString()).joinToString("|")
    }
}
