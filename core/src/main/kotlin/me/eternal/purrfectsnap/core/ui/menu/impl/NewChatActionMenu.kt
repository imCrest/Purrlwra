package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.content.Context
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cock.crest.purrfectsnap.lite.bridge.logger.LoggedChatEdit
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.snap.RemoteMediaResolver
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.MediaDownloader
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder.MessageDecoder
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.ConvertMessageLocally
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.features.impl.spying.MessageLogger
import cock.crest.purrfectsnap.lite.core.features.impl.ui.LocalPinnedMessages
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.ui.debugEditText
import cock.crest.purrfectsnap.lite.core.ui.iterateParent
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.ui.triggerCloseTouchEvent
import cock.crest.purrfectsnap.lite.core.util.ktx.isDarkTheme
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class NewChatActionMenu : AbstractMenu() {
    private fun debugAlertDialog(context: Context, title: String, text: String) {
        this@NewChatActionMenu.context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context).apply {
                setTitle(title)
                setView(debugEditText(context, text))
                setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                setNegativeButton("Copy") { _, _ ->
                    context.copyToClipboard(text, title)
                }
            }.show()
        }
    }

    fun showChatEditHistory(
        edits: List<LoggedChatEdit>,
    ) {
        createComposeAlertDialog(context.mainActivity!!) {
            cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme {
                cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    title = this@NewChatActionMenu.context.translation["chat_action_menu.show_chat_edit_history"]
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        itemsIndexed(edits) { index, edit ->
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                context.androidContext.copyToClipboard(edit.message)
                                            }
                                        )
                                    },
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(edit.message)
                                Text(
                                    text = DateFormat.getDateTimeInstance()
                                        .format(edit.timestamp) + " (${index + 1})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    color = cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }.show()
    }

    private val lastFocusedMessage
        get() = context.database.getConversationMessageFromId(context.feature(Messaging::class).lastFocusedMessageId)

    @OptIn(ExperimentalLayoutApi::class, ExperimentalEncodingApi::class)
    fun createDebugInfoView(context: Context): ComposeView {
        val messageLogger = this@NewChatActionMenu.context.feature(MessageLogger::class)
        val messaging = this@NewChatActionMenu.context.feature(Messaging::class)

        return createComposeView(context) {
            Card(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        debugAlertDialog(context,
                            "Message Info",
                            StringBuilder().apply {
                                runCatching {
                                    append("conversation_id: ${arroyoMessage.clientConversationId}\n")
                                    append("sender_id: ${arroyoMessage.senderId}\n")
                                    append("client_id: ${arroyoMessage.clientMessageId}, server_id: ${arroyoMessage.serverMessageId}\n")
                                    append("content_type: ${ContentType.fromId(arroyoMessage.contentType)} (${arroyoMessage.contentType})\n")
                                    append("parsed_content_type: ${
                                        ContentType.fromMessageContainer(
                                            ProtoReader(arroyoMessage.messageContent!!).followPath(4, 4)
                                        ).let { "$it (${it?.id})" }}\n")
                                    append("creation_timestamp: ${
                                        SimpleDateFormat.getDateTimeInstance().format(
                                            Date(arroyoMessage.creationTimestamp)
                                        )} (${arroyoMessage.creationTimestamp})\n")
                                    append("read_timestamp: ${
                                        SimpleDateFormat.getDateTimeInstance().format(
                                            Date(arroyoMessage.readTimestamp)
                                        )} (${arroyoMessage.readTimestamp})\n")
                                    append("ml_deleted: ${messageLogger.isMessageDeleted(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong())}, ")
                                    append("ml_stored: ${messageLogger.getMessageObject(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong()) != null}\n")
                                }
                            }.toString()
                        )
                    }) {
                        Text(this@NewChatActionMenu.context.translation["debug_dialogs.info"])
                    }
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        messaging.conversationManager?.fetchMessage(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong(), onSuccess = { message ->
                            val decodedAttachments = MessageDecoder.decode(message.messageContent!!)
                            debugAlertDialog(
                                context,
                                this@NewChatActionMenu.context.translation["debug_dialogs.media_references"],
                                decodedAttachments.mapIndexed { index, attachment ->
                                    StringBuilder().apply {
                                        append("---- media $index ----\n")
                                        append("resolveProto: ${attachment.boltKey}\n")
                                        append("type: ${attachment.type}\n")
                                        attachment.attachmentInfo?.apply {
                                            encryption?.let {
                                                append("encryption:\n  - key: ${it.key}\n  - iv: ${it.iv}\n")
                                            }
                                            resolution?.let {
                                                append("resolution: ${it.first}x${it.second}\n")
                                            }
                                            duration?.let {
                                                append("duration: $it\n")
                                            }
                                        }
                                        runCatching {
                                            attachment.boltKey?.let {
                                                val mediaHeaders = RemoteMediaResolver.getMediaHeaders(
                                                    Base64.UrlSafe.decode(it))
                                                append("content-type: ${mediaHeaders["content-type"]}\n")
                                                append("content-length: ${Formatter.formatShortFileSize(context, mediaHeaders["content-length"]?.toLongOrNull() ?: 0)}\n")
                                                append("creation-date: ${mediaHeaders["last-modified"]}\n")
                                            }
                                            attachment.directUrl?.let {
                                                append("url: $it\n")
                                            }
                                        }
                                    }.toString()
                                }.joinToString("\n\n")
                            )
                        })
                    }) {
                        Text(this@NewChatActionMenu.context.translation["debug_dialogs.refs"])
                    }
                    Button(onClick = {
                        val message = lastFocusedMessage ?: return@Button
                        debugAlertDialog(
                            context,
                            this@NewChatActionMenu.context.translation["debug_dialogs.arroyo_proto"],
                            message.messageContent?.let { ProtoReader(it) }?.toString() ?: "empty"
                        )
                    }) {
                        Text(this@NewChatActionMenu.context.translation["debug_dialogs.arroyo"])
                    }
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        messaging.conversationManager?.fetchMessage(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong(), onSuccess = { message ->
                                                    debugAlertDialog(
                            context,
                            this@NewChatActionMenu.context.translation["debug_dialogs.message_proto"],
                                message.messageContent?.content?.let { ProtoReader(it) }?.toString() ?: "empty"
                            )
                        }, onError = {
                            this@NewChatActionMenu.context.shortToast(this@NewChatActionMenu.context.translation["error_messages.failed_to_fetch_message"].replace("{error}", it.toString()))
                        })
                    }) {
                        Text(this@NewChatActionMenu.context.translation["debug_dialogs.message"])
                    }
                }
            }
        }
    }

    fun handle(event: AddViewEvent) {
        if (event.parent is LinearLayout) return
        val closeActionMenu = { event.parent.iterateParent {
            it.triggerCloseTouchEvent()
            false
        } }

        val mediaDownloader = context.feature(MediaDownloader::class)
        val messageLogger = context.feature(MessageLogger::class)
        val messaging = context.feature(Messaging::class)

        val composeView = createComposeView(event.view.context) {
            val primaryColor = remember { if (event.view.context.isDarkTheme()) Color.White else Color.Black }
            val avenirNextMediumFont = remember {
                FontFamily.SansSerif
            }

            @Composable
            fun ListButton(
                modifier: Modifier = Modifier,
                icon: ImageVector,
                text: String,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(modifier)
                        .padding(top = 11.dp, bottom = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 16.dp),
                        imageVector = icon,
                        tint = primaryColor,
                        contentDescription = text
                    )
                    Text(text, color = primaryColor, fontFamily = avenirNextMediumFont, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (context.config.downloader.downloadContextMenu.get()) {
                    ListButton(icon = Icons.Outlined.RemoveRedEye, text = context.translation["chat_action_menu.preview_button"], modifier = Modifier.clickable {
                        closeActionMenu()
                        mediaDownloader.onMessageActionMenu(true)
                    })
                    ListButton(icon = Icons.Outlined.Download, text = context.translation["chat_action_menu.download_button"], modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                closeActionMenu()
                                mediaDownloader.onMessageActionMenu(false)
                            },
                            onLongPress = {
                                context.androidContext.vibrateLongPress()
                                mediaDownloader.onMessageActionMenu(isPreviewMode = false, forceAllowDuplicate = true)
                            }
                        )
                    })
                }

                if (context.config.messaging.messageLogger.globalState == true) {
                    val chatEdits by rememberAsyncMutableState(defaultValue = null) {
                        context.feature(MessageLogger::class).getChatEdits(
                            messaging.openedConversationUUID.toString(),
                            messaging.lastFocusedMessageId
                        )
                    }

                    if (chatEdits != null && chatEdits?.isNotEmpty() == true) {
                        ListButton(icon = Icons.Outlined.History, text = context.translation["chat_action_menu.show_chat_edit_history"], modifier = Modifier.clickable {
                            closeActionMenu()
                            showChatEditHistory(chatEdits!!)
                        })
                    }

                    ListButton(icon = Icons.Outlined.BookmarkRemove, text = context.translation["chat_action_menu.delete_logged_message_button"], modifier = Modifier.clickable {
                        closeActionMenu()
                        context.executeAsync {
                            messageLogger.deleteMessage(messaging.openedConversationUUID.toString(), messaging.lastFocusedMessageId)
                        }
                    })
                }

                if (context.config.experimental.convertMessageLocally.get()) {
                    ListButton(icon = Icons.Outlined.Image, text = context.translation["chat_action_menu.convert_message"], modifier = Modifier.clickable {
                        closeActionMenu()
                        messaging.conversationManager?.fetchMessage(
                            messaging.openedConversationUUID.toString(),
                            messaging.lastFocusedMessageId,
                            onSuccess = {
                                context.runOnUiThread {
                                    runCatching {
                                        context.feature(ConvertMessageLocally::class)
                                            .convertMessageInterface(it)
                                    }.onFailure {
                                        context.log.verbose("Failed to convert message: $it")
                                        context.shortToast(context.translation["error_messages.failed_to_edit_message"].replace("{error}", it.toString()))
                                    }
                                }
                            },
                            onError = {
                                context.shortToast(context.translation["error_messages.failed_to_fetch_message"].replace("{error}", it.toString()))
                            }
                        )
                    })
                }

                val pinnedMessages = context.feature(LocalPinnedMessages::class)
                ListButton(
                    icon = Icons.Outlined.PushPin,
                    text = if (pinnedMessages.hasPinnedMessageForOpenedConversation()) context.translation["chat_action_menu.unpin_local_message"] else context.translation["chat_action_menu.pin_local_message"],
                    modifier = Modifier.clickable {
                        closeActionMenu()
                        if (pinnedMessages.hasPinnedMessageForOpenedConversation()) {
                            pinnedMessages.unpinFocusedConversation()
                        } else {
                            pinnedMessages.pinFocusedMessage()
                        }
                    }
                )
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        event.view = ScrollView(event.view.context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(composeView)
                composeView.post {
                    (event.parent.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        if (height < composeView.measuredHeight) {
                            height += composeView.measuredHeight
                        } else {
                            setObjectField("a", null) // remove drag callback
                        }
                    }
                    event.parent.requestLayout()
                }
                addView(event.view)
            })
        }
    }
}
