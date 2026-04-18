package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.reflect.TypeToken
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.ui.CustomComposable
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText
import cock.crest.purrfectsnap.lite.core.wrapper.impl.sanitizeForLayout
import kotlinx.coroutines.delay

class LocalPinnedMessages : Feature("Local Pinned Messages") {
    data class PinnedMessageSnapshot(
        val messageId: Long,
        val senderName: String,
        val preview: String,
        val timestamp: Long
    )

    private val prefs by lazy {
        context.androidContext.getSharedPreferences("purrfectsnap_local_pinned_messages", 0)
    }

    private var revision by mutableLongStateOf(0L)
    private var trackedChatLayout by mutableStateOf<View?>(null)
    private var trackedChatVisibility by mutableStateOf(false)

    private fun readPins(): MutableMap<String, PinnedMessageSnapshot> {
        val json = prefs.getString("pins", null) ?: return mutableMapOf()
        return runCatching {
            context.gson.fromJson<MutableMap<String, PinnedMessageSnapshot>>(
                json,
                object : TypeToken<MutableMap<String, PinnedMessageSnapshot>>() {}.type
            ) ?: mutableMapOf()
        }.getOrElse { mutableMapOf() }
    }

    private fun writePins(pins: Map<String, PinnedMessageSnapshot>) {
        prefs.edit().putString("pins", context.gson.toJson(pins)).apply()
        revision++
    }

    private fun resolveMessagePreview(message: ConversationMessage): String {
        val messageContainer = message.messageContent?.let { ProtoReader(it) }?.followPath(4, 4)
        val contentType = ContentType.fromMessageContainer(messageContainer) ?: ContentType.fromId(message.contentType)
        return messageContainer?.getBuffer()?.getMessageText(contentType)
            ?: "[${context.translation.getCategory("content_type")[contentType.name]}]"
    }

    private fun resolveSenderName(message: ConversationMessage): String {
        return message.senderId?.let { senderId ->
            context.database.getFriendInfo(senderId)?.let { it.displayName ?: it.mutableUsername }
        } ?: context.translation.getCategory("logger_history")["unknown_sender"]
    }

    fun pinFocusedMessage() {
        val messaging = context.feature(Messaging::class)
        val conversationId = messaging.openedConversationUUID?.toString() ?: return
        val messageId = messaging.lastFocusedMessageId.takeIf { it > 0 } ?: return
        val message = context.database.getConversationMessageFromId(messageId) ?: return

        val pins = readPins()
        pins[conversationId] = PinnedMessageSnapshot(
            messageId = messageId,
            senderName = resolveSenderName(message),
            preview = resolveMessagePreview(message).sanitizeForLayout(),
            timestamp = message.creationTimestamp
        )
        writePins(pins)
        context.shortToast(context.translation["local_pinned_messages.pinned_toast"])
    }

    fun unpinFocusedConversation() {
        val conversationId = context.feature(Messaging::class).openedConversationUUID?.toString() ?: return
        val pins = readPins()
        if (pins.remove(conversationId) != null) {
            writePins(pins)
            context.shortToast(context.translation["local_pinned_messages.unpinned_toast"])
        }
    }

    fun hasPinnedMessageForOpenedConversation(): Boolean {
        val conversationId = context.feature(Messaging::class).openedConversationUUID?.toString() ?: return false
        return readPins().containsKey(conversationId)
    }

    private fun isActuallyVisible(view: View): Boolean {
        if (!view.isShown || view.visibility != View.VISIBLE || !view.isAttachedToWindow) return false
        if (view.width <= 0 || view.height <= 0 || view.alpha <= 0f) return false
        return view.getGlobalVisibleRect(Rect())
    }

    private fun trackChatLayoutCandidate(view: View?) {
        view ?: return
        trackedChatLayout = view
        trackedChatVisibility = isActuallyVisible(view)
    }

    override fun init() {
        context.event.subscribe(AddViewEvent::class) { event ->
            when {
                event.parent.javaClass.name.endsWith("ChatInputLayout") -> {
                    trackChatLayoutCandidate(event.parent)
                }
                event.viewClassName.endsWith("ChatInputLayout") -> {
                    trackChatLayoutCandidate(event.view)
                }
            }
        }

        onNextActivityCreate {
            trackedChatLayout = null
            trackedChatVisibility = false
        }

        lateinit var pinnedComposable: CustomComposable
        pinnedComposable = {
            revision
            val trackedLayout = trackedChatLayout
            var currentConversationId by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(revision, trackedLayout) {
                while (true) {
                    currentConversationId = context.feature(Messaging::class).openedConversationUUID?.toString()
                    trackedChatVisibility = trackedLayout?.let { isActuallyVisible(it) } == true
                    delay(16)
                }
            }

            val conversationId = currentConversationId
            if (conversationId != null && trackedChatVisibility) {
                val pinned = remember(revision, conversationId) { readPins()[conversationId] }
                if (pinned != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 62.dp, start = 14.dp, end = 14.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        PurrfectOverlayTheme {
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PurrfectOverlayPalette.cardOverlayColor.copy(alpha = 0.95f), shape)
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                                    .clickable { }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = PurrfectOverlayPalette.glowPrimary
                                )
                                Text(
                                    text = pinned.preview,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable {
                                        unpinFocusedConversation()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        context.inAppOverlay.addCustomComposable(pinnedComposable)
    }
}
