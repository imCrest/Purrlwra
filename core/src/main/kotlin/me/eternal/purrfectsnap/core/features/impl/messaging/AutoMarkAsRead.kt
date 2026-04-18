package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessageUpdate
import cock.crest.purrfectsnap.lite.core.event.events.impl.OnSnapInteractionEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.spying.StealthMode
import cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.util.CallbackBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class AutoMarkAsRead : Feature("Auto Mark As Read") {
    val canMarkConversationAsRead by lazy { context.config.messaging.autoMarkAsRead.get().contains("conversation_read") }
    private val markAsSeenBatchSize = 50
    private val markAsSeenBatchCooldownMs = 4000L

    private data class PendingSnapMessage(
        val clientMessageId: Long,
        val creationTimestamp: Long
    )

    private fun String?.isRateLimited(): Boolean {
        val value = this ?: return false
        return value.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            value.contains("Rate limited", ignoreCase = true)
    }

    private fun showRateLimitedDialog(processed: Int, total: Int) {
        val activity = context.mainActivity ?: run {
            context.inAppOverlay.showStatusToast(
                Icons.Default.WarningAmber,
                "Rate limited after $processed/$total snaps. Try again later."
            )
            return
        }

        createComposeAlertDialog(activity) {
            PurrfectOverlayTheme {
                PurrfectGlassCard(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    title = "Rate Limited",
                    subtitle = "Snapchat stopped the mark-as-seen run to protect your account",
                    icon = Icons.Default.WarningAmber
                ) {
                    Column(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Processed $processed of $total snaps before the request was rate limited.",
                            color = PurrfectOverlayPalette.textSecondary
                        )
                        Text(
                            text = "No bypass was attempted. Wait a bit and run it again, or lower the per-run limit in settings.",
                            color = PurrfectOverlayPalette.textSecondary
                        )
                    }
                }
            }
        }.show()
    }

    fun markConversationsAsRead(conversationIds: List<String>) {
        conversationIds.forEach { conversationId ->
            val lastClientMessageId = context.database.getMessagesFromConversationId(conversationId, 1)?.firstOrNull()?.clientMessageId?.toLong() ?: Long.MAX_VALUE
            context.feature(StealthMode::class).addDisplayedMessageException(lastClientMessageId)
            context.feature(Messaging::class).conversationManager?.displayedMessages(conversationId, lastClientMessageId) {
                if (it != null) {
                    context.log.warn("Failed to mark message $lastClientMessageId as read in conversation $conversationId")
                }
            }
        }
    }

    suspend fun markSnapAsSeen(conversationId: String, clientMessageId: Long): String? {
        return suspendCoroutine { continuation ->
            context.feature(Messaging::class).conversationManager?.updateMessage(conversationId, clientMessageId, MessageUpdate.READ) {
                continuation.resume(it)
                if (it != null && it != "DUPLICATEREQUEST") {
                    context.log.error("Error marking message as read $it")
                }
            }
        }
    }

    private fun getPendingSnapMessageIds(conversationId: String, requestedLimit: Int?): List<Long> {
        val collected = mutableListOf<PendingSnapMessage>()
        val pageSize = 200
        var page = 0

        while (true) {
            val messages = context.database.getMessagesFromConversationId(conversationId, pageSize, page) ?: break
            messages.forEach { message ->
                if (message.contentType != ContentType.SNAP.id && message.contentType != ContentType.EXTERNAL_MEDIA.id) return@forEach
                if (message.isViewedByUser == 1 || message.readTimestamp > 0L) return@forEach
                collected += PendingSnapMessage(
                    clientMessageId = message.clientMessageId.toLong(),
                    creationTimestamp = message.creationTimestamp
                )
            }
            if (messages.size < pageSize) break
            if (requestedLimit != null && collected.size >= requestedLimit) break
            page++
        }

        return collected
            .distinctBy { it.clientMessageId }
            .sortedBy { it.creationTimestamp }
            .let { pending ->
                if (requestedLimit != null) pending.take(requestedLimit) else pending
            }
            .map { it.clientMessageId }
    }

    fun markSnapsAsSeen(conversationId: String) {
        val messaging = context.feature(Messaging::class)
        val processingMode = context.config.messaging.markSnapAsSeenProcessingMode.get()
        val configuredLimit = context.config.messaging.markSnapAsSeenLimit.get()
            .coerceAtLeast(1)
        val requestedLimit = if (processingMode == "complete") null else configuredLimit
        val messageIds = getPendingSnapMessageIds(conversationId, requestedLimit)
            .ifEmpty {
                messaging.getFeedCachedMessageIds(conversationId)
                    ?.map { it.toLong() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { cached ->
                        if (requestedLimit != null) cached.take(requestedLimit) else cached
                    }
                    ?: run {
                        context.inAppOverlay.showStatusToast(
                            Icons.Default.WarningAmber,
                            context.translation["mark_as_seen.no_unseen_snaps_toast"]
                        )
                        return
                    }
            }
        val targetMessageIds = if (requestedLimit == null) {
            messageIds
        } else {
            messageIds.take(requestedLimit)
        }

        var job: Job? = null
        val processedCount = mutableIntStateOf(0)
        var rateLimitedAt: Int? = null
        val dialog = createComposeAlertDialog(context.mainActivity!!, builder = {
            setOnDismissListener { job?.cancel() }
        }) {
            PurrfectOverlayTheme {
                PurrfectGlassCard(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    title = "Marking Snaps as Seen",
                    subtitle = "Updating read state for queued snaps",
                    icon = Icons.Default.Visibility
                ) {
                    Column(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = PurrfectOverlayPalette.glowSecondary,
                            trackColor = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.18f)
                        )
                        Text(
                            text = "${processedCount.intValue}/${targetMessageIds.size}",
                            color = PurrfectOverlayPalette.textSecondary
                        )
                    }
                }
            }
        }.apply { show() }

        context.coroutineScope.launch(Dispatchers.IO) {
            targetMessageIds.forEachIndexed { index, messageId ->
                val result = markSnapAsSeen(conversationId, messageId)
                if (result.isRateLimited()) {
                    rateLimitedAt = processedCount.intValue
                    return@launch
                }
                delay(Random.nextLong(20, 60))
                context.runOnUiThread {
                    processedCount.intValue = index + 1
                }
                val processed = index + 1
                if (processed < targetMessageIds.size && processed % markAsSeenBatchSize == 0) {
                    delay(markAsSeenBatchCooldownMs)
                }
            }
        }.also { job = it }.invokeOnCompletion {
            context.runOnUiThread {
                dialog.dismiss()
                if (rateLimitedAt != null) {
                    val processedIndex = rateLimitedAt!!
                    processedCount.intValue = processedIndex
                    showRateLimitedDialog(processedIndex, targetMessageIds.size)
                } else if (requestedLimit != null && targetMessageIds.size < messageIds.size) {
                    context.inAppOverlay.showStatusToast(
                        Icons.Default.Info,
                        "Processed ${targetMessageIds.size} of ${messageIds.size} unseen snaps."
                    )
                }
            }
        }
    }

    override fun init() {
        val config by context.config.messaging.autoMarkAsRead
        if (config.isEmpty()) return

        if (config.contains("save_snap_in_chat")) {
            var lastInteractedSnapClientMessageId = -1L

            context.event.subscribe(OnSnapInteractionEvent::class) { event ->
                lastInteractedSnapClientMessageId = event.messageId
            }

            context.classCache.conversationManager.hook("updateMessage", HookStage.BEFORE) { param ->
                if (param.arg<Any>(2).toString() != "SAVE") return@hook

                val clientMessageId = param.arg<Long>(1)
                if (lastInteractedSnapClientMessageId != clientMessageId) return@hook

                val conversationId = SnapUUID(param.arg(0))

                param.setResult(null)

                val snapManager = context.feature(Messaging::class).snapManager ?: return@hook
                val stealthMode = context.feature(StealthMode::class)

                // ignore conversations without snap stealth enabled
                if (!stealthMode.canUseSnapStealth(conversationId.toString())) return@hook

                stealthMode.addSnapInteractionException(clientMessageId)

                val onSnapInteraction = snapManager.javaClass.methods.firstOrNull { it.name == "onSnapInteraction" } ?: return@hook

                context.mappings.useMapper(CallbackMapper::class) {
                    onSnapInteraction.invoke(snapManager,
                        findClass("com.snapchat.client.messaging.SnapInteractionType").enumConstants!!.first { it.toString() == "VIEWING_INITIATED" },
                        conversationId.instanceNonNull(),
                        clientMessageId,
                        CallbackBuilder(callbacks.getClass("SnapInteractionCallback")!!)
                            .override("onSuccess") {
                                param.invokeOriginal()
                                stealthMode.addSnapInteractionException(clientMessageId)
                            }
                            .override("onError") {
                                context.log.verbose("error ${it.arg<Any>(0)}")
                            }
                            .build()
                    )
                }
            }
        }

        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            event.addCallbackResult("onSuccess") {
                if (canMarkConversationAsRead) {
                    markConversationsAsRead(event.destinations.conversations?.map { it.toString() } ?: return@addCallbackResult)
                }

                if (config.contains("snap_reply")) {
                    val quotedMessageId = event.messageContent.instanceNonNull().getObjectFieldOrNull("mQuotedMessageId") as? Long ?: return@addCallbackResult
                    val message = context.database.getConversationMessageFromId(quotedMessageId) ?: return@addCallbackResult

                    if (message.contentType == ContentType.SNAP.id) {
                        context.coroutineScope.launch {
                            markSnapAsSeen(event.destinations.conversations?.firstOrNull()?.toString() ?: return@launch, quotedMessageId)
                        }
                    }
                }
            }
        }
    }
}
