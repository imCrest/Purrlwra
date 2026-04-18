package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoWriter
import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageContent

class ConvertMessageLocally : Feature("Convert Message Edit") {
    private val messageCache = mutableMapOf<Long, MessageContent>()

    private fun dispatchMessageEdit(message: Message, restore: Boolean = false) {
        val messageId = message.messageDescriptor!!.messageId!!
        if (!restore) messageCache[messageId] = message.messageContent!!

        context.runOnUiThread {
            context.feature(Messaging::class).localUpdateMessage(
                message.messageDescriptor!!.conversationId!!.toString(),
                message
            )
        }
    }

    fun convertMessageInterface(messageInstance: Message) {
        val actions = mutableListOf<Pair<String, (Message) -> Unit>>()
        actions += context.translation["button.restore_original"] to actions@{ message ->
            val descriptor = message.messageDescriptor ?: return@actions
            messageCache.remove(descriptor.messageId!!)
            context.feature(Messaging::class).conversationManager?.fetchMessage(
                descriptor.conversationId!!.toString(),
                descriptor.messageId!!,
                onSuccess = { msg ->
                    dispatchMessageEdit(msg, true)
                }
            )
        }

        val contentType = messageInstance.messageContent?.contentType
        if (contentType == ContentType.SNAP) {
            actions += context.translation["button.convert_external_media"] to convert@{ message ->
                val snapMessageContent = ProtoReader(message.messageContent!!.content!!).followPath(11)
                    ?.getBuffer() ?: return@convert
                message.messageContent!!.content = ProtoWriter().apply {
                    from(3) {
                        addBuffer(3, snapMessageContent)
                    }
                }.toByteArray()
                dispatchMessageEdit(message)
            }
        }

        createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
            ConvertMessageDialog(
                title = context.translation["chat_action_menu.convert_message"],
                subtitle = context.translation["convert_message_dialog.subtitle"],
                closeLabel = context.translation["button.cancel"],
                actions = actions.map { (label, _) ->
                    ConvertMessageAction(
                        label = label,
                        icon = when (label) {
                            context.translation["button.restore_original"] -> Icons.Default.Restore
                            else -> Icons.Default.Cached
                        }
                    )
                },
                onSelect = { index ->
                    actions.getOrNull(index)?.second?.invoke(messageInstance)
                    alertDialog.dismiss()
                },
                onDismiss = { alertDialog.dismiss() }
            )
        }.show()
    }

    override fun init() {
        onNextActivityCreate {
            context.event.subscribe(BuildMessageEvent::class, priority = 2) {
                val clientMessageId = it.message.messageDescriptor?.messageId ?: return@subscribe
                if (!messageCache.containsKey(clientMessageId)) return@subscribe
                it.message.messageContent = messageCache[clientMessageId]
            }
        }
    }

    @Composable
    private fun ConvertMessageDialog(
        title: String,
        subtitle: String,
        closeLabel: String,
        actions: List<ConvertMessageAction>,
        onSelect: (Int) -> Unit,
        onDismiss: () -> Unit
    ) {
        val shape = remember { RoundedCornerShape(24.dp) }
        val overlayBrush = remember {
            Brush.linearGradient(
                listOf(
                    Color(0xFF2A2452).copy(alpha = 0.95f),
                    Color(0xFF1A143A).copy(alpha = 0.92f)
                )
            )
        }
        val accentBrush = remember {
            Brush.linearGradient(
                listOf(
                    Color(0xFF8C7BFF).copy(alpha = 0.42f),
                    Color(0xFF5FD8FF).copy(alpha = 0.34f)
                )
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2452).copy(alpha = 0.95f))
        ) {
            Box(
                modifier = Modifier
                    .background(overlayBrush, shape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(accentBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD9D3FF),
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        actions.forEachIndexed { index, action ->
                            ConvertMessageOptionCard(
                                label = action.label,
                                icon = action.icon,
                                onClick = { onSelect(index) }
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8C7BFF).copy(alpha = 0.34f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = closeLabel,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ConvertMessageOptionCard(
        label: String,
        icon: ImageVector,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF8C7BFF).copy(alpha = 0.18f),
                            Color(0xFF5FD8FF).copy(alpha = 0.1f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    private data class ConvertMessageAction(
        val label: String,
        val icon: ImageVector
    )
}
