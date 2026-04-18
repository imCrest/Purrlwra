package cock.crest.purrfectsnap.lite.core.features.impl.ui

import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.RuleState

import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper

class HideFriendFeedEntry : MessagingRuleFeature("HideFriendFeedEntry", ruleType = MessagingRuleType.HIDE_FRIEND_FEED) {
    private fun createDeletedFeedEntry(conversationIdInstance: Any) = findClass("com.snapchat.client.messaging.DeletedFeedEntry").dataBuilder {
        from("mFeedEntryIdentifier") {
            set("mConversationId", conversationIdInstance)
        }
        set("mReason", "CLEAR_CONVERSATION")
    }

    private fun filterFriendFeed(entries: ArrayList<Any>, deletedEntries: ArrayList<Any>? = null) {
        entries.removeIf { feedEntry ->
            val conversationIdInstance = feedEntry.getObjectField("mConversationId") ?: return@removeIf false
            if (canUseRule(SnapUUID(conversationIdInstance).toString())) {
                deletedEntries?.add(createDeletedFeedEntry(conversationIdInstance)!!)
                true
            } else {
                false
            }
        }
    }

    private fun hookCallbackMethod(
        hookedCallbacks: MutableSet<String>,
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

    override fun init() {
        if (!context.config.userInterface.hideFriendFeedEntry.get()) return

        context.mappings.useMapper(CallbackMapper::class) {
            classLoader = context.androidContext.classLoader
            val hasFetchAndSyncCallback = callbacks.getAsMap()?.entries?.any {
                it.key.startsWith("FetchAndSyncFeed") && it.key.endsWith("Callback")
            } == true
            if (callbacks.getClass("SyncFeedCallback") == null || !hasFetchAndSyncCallback) {
                runCatching { context.mappings.refresh() }.onFailure {
                    context.log.error("Failed to refresh mappings for HideFriendFeedEntry callbacks", it)
                }
                classLoader = context.androidContext.classLoader
            }
            val callbackMap = callbacks.getAsMap().orEmpty()
            val hookedCallbacks = mutableSetOf<String>()

            callbackMap.entries.forEach { (callbackName, callbackClassName) ->
                when {
                    callbackName.startsWith("FetchAndSyncFeed") && callbackName.endsWith("Callback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchAndSyncFeedComplete") { param ->
                            val deletedConversations: ArrayList<Any> = param.arg(2)
                            filterFriendFeed(param.arg(0), deletedConversations)

                            if (deletedConversations.any {
                                    val uuid = SnapUUID(it.getObjectField("mFeedEntryIdentifier")?.getObjectField("mConversationId")).toString()
                                    context.database.getFeedEntryByConversationId(uuid) != null
                                }) {
                                param.setArg(4, true)
                            }
                        }
                    }

                    callbackName.contains("SyncFeed") && callbackName.endsWith("Callback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onSyncFeedComplete") { param ->
                            filterFriendFeed(param.arg(0), param.argNullable(2))
                        }
                    }

                    callbackName == "FetchFeedCallback" || callbackName.contains("FetchFeedCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchFeedComplete") { param ->
                            filterFriendFeed(param.arg(0))
                        }
                    }

                    callbackName == "FetchFeedEntriesCallback" || callbackName.contains("FetchFeedEntriesCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchFeedEntriesComplete") { param ->
                            filterFriendFeed(param.arg(0))
                        }
                    }

                    callbackName == "QueryFeedCallback" || callbackName.contains("QueryFeedCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onQueryFeedComplete") { param ->
                            filterFriendFeed(param.arg(0))
                        }
                    }

                    callbackName == "FeedManagerDelegate" -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFeedEntriesUpdated") { param ->
                            filterFriendFeed(param.arg(0))
                        }
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onInternalSyncFeed") { param ->
                            filterFriendFeed(param.arg(0))
                        }
                    }
                }
            }

            if (callbackMap.entries.none { it.key.startsWith("FetchAndSyncFeed") && it.key.endsWith("Callback") }) {
                context.log.warn("Failed to hook FetchAndSyncFeedCallback")
            }
            if (callbackMap.entries.none { it.key.contains("SyncFeed") && it.key.endsWith("Callback") }) {
                context.log.warn("Failed to hook SyncFeedCallback")
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}
