package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.hideViewCompletely
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.findRestrictedMethod
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import java.nio.ByteBuffer

class BlockCalls : Feature("Block Calls") {
    private fun isBlockedCallNotificationType(type: String?): Boolean {
        return type?.lowercase() in setOf(
            "initiate_audio",
            "initiate_video",
            "abandon_audio",
            "abandon_video"
        )
    }

    private fun shouldBlockVolatilePayload(eventData: ProtoReader): Boolean {
        val dump = eventData.toString().lowercase()
        return listOf(
            "\"calluuid\"",
            "\"callaction\"",
            "\"messagetype\":\"caller_push\"",
            "\"messagetype\":\"streamer_data_v2\"",
            "\"messagetype\":\"callee_push\"",
            "\"messagetype\":\"caller_hangup\"",
            "\"messagetype\":\"call_end\""
        ).any { it in dump }
    }

    override fun init() {
        if (!context.config.messaging.blockCalls.get()) return

        runCatching {
            findClass("com.google.firebase.messaging.FirebaseMessagingService")
                .methods
                .first {
                    it.declaringClass.name == "com.google.firebase.messaging.FirebaseMessagingService" &&
                        it.returnType == Void::class.javaPrimitiveType &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == Intent::class.java
                }
                .hook(HookStage.BEFORE) { param ->
                    val intent = param.argNullable<Intent>(0) ?: return@hook
                    if (!isBlockedCallNotificationType(intent.getStringExtra("type"))) return@hook

                    context.log.verbose("Blocked Firebase call message ${intent.getStringExtra("type")}", "BlockCalls")
                    param.setResult(null)
                }
        }

        runCatching {
            NotificationManager::class.java.findRestrictedMethod { it.name == "notifyAsUser" }?.hook(HookStage.BEFORE) { param ->
                val notification = param.argNullable<Notification>(2) ?: return@hook
                val notificationType = notification.extras
                    ?.getBundle("system_notification_extras")
                    ?.getString("notification_type")

                if (!isBlockedCallNotificationType(notificationType)) return@hook

                context.log.verbose("Blocked call notification $notificationType", "BlockCalls")
                param.setResult(null)
            }
        }

        runCatching {
            findClass("com.snapchat.client.duplex.MessageHandler\$CppProxy").hook("onReceive", HookStage.BEFORE) { param ->
                val buffer = param.argNullable<ByteBuffer>(0) ?: return@hook
                val duplicate = buffer.duplicate().apply { position(0) }
                val bytes = ByteArray(duplicate.limit())
                duplicate.get(bytes)

                val reader = ProtoReader(bytes)
                val eventType = reader.getString(1, 1) ?: return@hook
                if (eventType != "volatile") return@hook

                val eventData = reader.followPath(1, 2) ?: return@hook
                if (!shouldBlockVolatilePayload(eventData)) return@hook

                context.log.verbose("Blocked volatile call payload", "BlockCalls")
                param.setResult(null)
            }
        }

        val talkCoreNames = listOf(
            "com.snapchat.talkcorev3.TalkCore\$CppProxy",
            "com.snapchat.talkcorev4.TalkCore\$CppProxy",
            "com.snapchat.talkcore.TalkCore\$CppProxy"
        )

        talkCoreNames.forEach { className ->
            runCatching {
                findClass(className).apply {
                    hook("updateTSCallingSession", HookStage.BEFORE) { param ->
                        val params = param.argNullable<Any>(0)
                        val conversationId = params?.getObjectFieldOrNull("mConversationId")?.toString()
                        val inCall = params?.getObjectFieldOrNull("mInCall") as? Boolean
                        context.log.verbose(
                            "Blocked talk session update inCall=$inCall convo=$conversationId",
                            "BlockCalls"
                        )
                        param.setResult(null)
                    }
                    hook("disposeTSCallingSession", HookStage.BEFORE) { param ->
                        context.log.verbose("Blocked talk session dispose", "BlockCalls")
                        param.setResult(null)
                    }
                }
            }
        }

        listOf(
            "com.snapchat.talkcorev3.TSCallingStateUpdateParams",
            "com.snapchat.talkcorev4.TSCallingStateUpdateParams",
            "com.snapchat.talkcore.TSCallingStateUpdateParams"
        ).forEach { className ->
            runCatching {
                findClass(className).hookConstructor(HookStage.AFTER) { param ->
                    val instance = param.thisObject<Any>()
                    val inCall = instance.getObjectFieldOrNull("mInCall") as? Boolean ?: return@hookConstructor
                    if (!inCall) return@hookConstructor

                    instance.setObjectField("mInCall", false)
                    context.log.verbose("Forced TSCallingStateUpdateParams.mInCall=false", "BlockCalls")
                }
            }
        }

        context.event.subscribe(AddViewEvent::class) { event ->
            val viewName = event.viewClassName.lowercase()
            val parentName = event.parent.javaClass.name.lowercase()
            val exactCallUi = setOf(
                "com.snap.talk.callviewwrapper",
                "com.snap.talk.core.callcontainer"
            )
            val callUiParents = setOf(
                "com.snap.talk.core.callcontainer"
            )

            if (viewName in exactCallUi || parentName in callUiParents) {
                context.log.verbose(
                    "Suppressed view ${event.viewClassName} parent=${event.parent.javaClass.name}",
                    "BlockCalls"
                )
                event.view.hideViewCompletely()
                return@subscribe
            }

            if (viewName.endsWith("callbuttonsview") ||
                (viewName.contains("call") && (
                    viewName.contains("overlay") ||
                    viewName.contains("incoming") ||
                    viewName.contains("ringing") ||
                    viewName.contains("ringer")
                ))
            ) {
                event.view.hideViewCompletely()
            }
        }
    }
}
