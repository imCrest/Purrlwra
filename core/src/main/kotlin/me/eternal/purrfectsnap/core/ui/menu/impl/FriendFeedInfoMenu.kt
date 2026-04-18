package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.FriendLinkType
import cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.scripting.JSModule
import cock.crest.purrfectsnap.lite.common.scripting.ui.EnumScriptInterface
import cock.crest.purrfectsnap.lite.common.scripting.ui.InterfaceManager
import cock.crest.purrfectsnap.lite.common.scripting.ui.ScriptInterface
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.EndToEndEncryption
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.AutoMarkAsRead
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.features.impl.spying.MessageLogger
import cock.crest.purrfectsnap.lite.core.features.impl.spying.StealthMode
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.ui.triggerRootCloseTouchEvent
import cock.crest.purrfectsnap.lite.core.util.ktx.isDarkTheme
import cock.crest.purrfectsnap.lite.core.wrapper.impl.sanitizeForLayout
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FriendFeedInfoMenu : AbstractMenu() {
    private fun formatDate(timestamp: Long): String? {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
    }

    private fun showProfileInfo(profile: FriendInfo) {
        val translation = context.translation.getCategory("profile_info")
        val firstCreatedUsername = context.database.getFriendOriginalUsername(profile.mutableUsername.toString()) ?: profile.firstCreatedUsername

        context.runOnUiThread {
            val addedTimestamp: Long = profile.addedTimestamp.coerceAtLeast(profile.reverseAddedTimestamp)
            val birthday = Calendar.getInstance()
            birthday[Calendar.MONTH] = (profile.birthday shr 32).toInt() - 1
            val birthdayLine = birthday.getDisplayName(
                Calendar.MONTH,
                Calendar.LONG,
                context.translation.loadedLocale
            )?.let {
                if (profile.birthday == 0L) context.translation["profile_info.hidden_birthday"]
                else context.translation.format(
                    "profile_info.birthday",
                    "month" to it,
                    "day" to profile.birthday.toInt().toString()
                )
            }
            val friendshipLine = run {
                context.translation["friendship_link_type.${FriendLinkType.fromValue(profile.friendLinkType).shortName}"]
            }.takeIf {
                if (profile.friendLinkType == FriendLinkType.MUTUAL.value) addedTimestamp.toInt() > 0 else true
            }
            val snapchatPlusState = translation.getCategory("snapchat_plus_state")[
                if (profile.postViewEmoji != null) "subscribed" else "not_subscribed"
            ]

            val lines = buildList {
                translation["first_created_username"]?.let { add("$it: $firstCreatedUsername") }
                translation["mutable_username"]?.let { add("$it: ${profile.mutableUsername}") }
                profile.displayName?.let { name ->
                    translation["display_name"]?.let { add("$it: $name") }
                }
                formatDate(addedTimestamp)?.takeIf { addedTimestamp > 0 }?.let { date ->
                    translation["added_date"]?.let { add("$it: $date") }
                }
                birthdayLine?.let { add(it) }
                friendshipLine?.let { value ->
                    translation["friendship"]?.let { add("$it: $value") }
                }
                context.database.getAddSource(profile.userId!!)?.takeIf { it.isNotEmpty() }?.let { value ->
                    translation["add_source"]?.let { add("$it: $value") }
                }
                translation["snapchat_plus"]?.let { add("$it: $snapchatPlusState") }
            }

            val avatarUrl = runCatching {
                if (profile.bitmojiSelfieId != null && profile.bitmojiAvatarId != null) {
                    BitmojiSelfie.getBitmojiSelfie(
                        profile.bitmojiSelfieId.toString(),
                        profile.bitmojiAvatarId.toString(),
                        BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                    )
                } else null
            }.getOrNull()

            createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
                PurrfectOverlayTheme {
                    val border = remember {
                        Brush.linearGradient(
                            listOf(
                                PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    }
                    val shape = RoundedCornerShape(26.dp)
                    val avatarBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, avatarUrl) {
                        value = runCatching {
                            val url = avatarUrl ?: return@runCatching null
                            val bytes = withContext(Dispatchers.IO) { URL(url).readBytes() }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                        shape = shape,
                        color = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 18.dp,
                        border = BorderStroke(1.dp, border)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(PurrfectOverlayPalette.cardOverlay, shape)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.08f)
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.padding(8.dp),
                                        tint = Color.White
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = translation["title"] ?: "Profile Info",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    val subtitleText = listOfNotNull(
                                        profile.displayName,
                                        profile.username,
                                        profile.mutableUsername
                                    ).firstOrNull()?.toString().orEmpty()
                                    Text(
                                        text = subtitleText,
                                        color = PurrfectOverlayPalette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarBitmap != null) {
                                        Image(
                                            bitmap = avatarBitmap!!,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    lines.forEach { line ->
                                        Text(
                                            text = line,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = context.translation["button.ok"] ?: "OK",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { alertDialog.dismiss() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }.show()
        }
    }

    private suspend fun showConversationPreview(
        targetUser: String?,
        conversationId: String
    ) {
        val friendInfo = targetUser?.let { context.database.getFriendInfo(it) }
        val conversationInfo = conversationId.takeIf { targetUser == null }?.let { context.database.getFeedEntryByConversationId(it) }
        val participants by lazy {
            context.database.getConversationParticipants(conversationId)!!
                .map { context.database.getFriendInfo(it)!! }
                .associateBy { it.userId!! }
        }

        withContext(Dispatchers.Main) {
            createComposeAlertDialog(
                context.mainActivity!!,
            ) {
                var pageIndex by remember { mutableIntStateOf(0) }
                val messages = remember { mutableStateListOf<@Composable () -> Unit>() }
                var totalMessages by remember { mutableIntStateOf(-1) }
                val coroutineScope = rememberCoroutineScope()

                suspend fun loadMore() {
                    val conversationMessages = context.database.getMessagesFromConversationId(
                        conversationId,
                        50,
                        page = pageIndex++
                    ) ?: emptyList()

                    if (totalMessages == -1) {
                        totalMessages = conversationMessages.firstOrNull()?.serverMessageId ?: 0
                    }

                    val messageLogger = context.feature(MessageLogger::class)
                    val endToEndEncryption = context.feature(EndToEndEncryption::class)

                    val parsedMessages = conversationMessages.mapNotNull<ConversationMessage, @Composable () -> Unit> { message ->
                        val sender = participants[message.senderId]
                        val messageProtoReader =
                            (messageLogger.takeIf { it.isEnabled && message.contentType == ContentType.STATUS.id }?.getMessageProto(conversationId, message.clientMessageId.toLong()) // process deleted messages if message logger is enabled
                                ?: ProtoReader(message.messageContent!!).followPath(4, 4) // database message
                            )?.let {
                            if (endToEndEncryption.isEnabled) endToEndEncryption.decryptDatabaseMessage(message) else it // try to decrypt message if e2ee is enabled
                        } ?: return@mapNotNull null

                        val contentType = ContentType.fromMessageContainer(messageProtoReader) ?: ContentType.fromId(message.contentType)
                        var messageString = if (contentType == ContentType.CHAT) {
                            messageProtoReader.getString(2, 1) ?: return@mapNotNull null
                        } else "[${context.translation.getOrNull("content_type.${contentType.name}") ?: contentType.name}]"

                        if (contentType == ContentType.SNAP) {
                            messageString = "\uD83D\uDFE5" //red square
                            if (message.readTimestamp > 0) {
                                messageString += " \uD83D\uDC40 " //eyes
                                messageString += DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                ).format(Date(message.readTimestamp))
                            }
                        }

                        messageString = messageString.sanitizeForLayout()

                        var displayUsername = sender?.displayName ?: sender?.usernameForSorting ?: context.translation["conversation_preview.unknown_user"]
                        displayUsername = displayUsername.sanitizeForLayout()

                        if (displayUsername.length > 12) {
                            displayUsername = displayUsername.substring(0, 13) + "... "
                        }

                        {
                            Text(
                                text = "$displayUsername: $messageString",
                                modifier = Modifier.padding(4.dp),
                                color = Color.White
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        messages.addAll(parsedMessages)
                    }
                }

                PurrfectOverlayTheme {
                    PurrfectGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                        title = context.translation.getOrNull("conversation_preview.title") ?: "Preview",
                        subtitle = (friendInfo?.displayName ?: conversationInfo?.feedDisplayName ?: conversationId).sanitizeForLayout(),
                        icon = Icons.Outlined.Visibility
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            @Composable
                            fun Entry(icon: ImageVector, text: String?, title: Boolean) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
                                    Text(
                                        text = text ?: "",
                                        fontWeight = if (title) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = if (title) 14.sp else 12.sp,
                                        color = Color.White.copy(alpha = if (title) 0.95f else 0.80f),
                                        maxLines = if (title) 1 else 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    friendInfo?.let { info ->
                                        Entry(
                                            Icons.Outlined.Person,
                                            info.displayName?.let { "$it (${info.usernameForSorting})" } ?: info.usernameForSorting,
                                            true
                                        )
                                    }
                                    conversationInfo?.let {
                                        Entry(Icons.Outlined.Group, (it.feedDisplayName ?: it.key).toString(), true)
                                    }
                                    Entry(
                                        Icons.AutoMirrored.Outlined.Message,
                                        context.translation.format("conversation_preview.total_messages", "count" to totalMessages.toString()),
                                        false
                                    )
                                }
                                friendInfo?.let {
                                    IconButton(
                                        onClick = { coroutineScope.launch(Dispatchers.IO) { showProfileInfo(it) } }
                                    ) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }

                            Spacer(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.10f))
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(8.dp),
                                reverseLayout = true
                            ) {
                                items(messages) { message ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        CompositionLocalProvider(
                                            LocalContentColor provides Color.White
                                        ) {
                                            message()
                                        }
                                    }
                                }
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { loadMore() } }
                                    if (messages.isEmpty()) {
                                        Text(
                                            text = context.translation["conversation_preview.no_messages"],
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }.show()
        }
    }

    @Composable
    private fun MenuElement(
        index: Int,
        icon: ImageVector,
        text: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        content: @Composable RowScope.() -> Unit = {}
    ) {
        val shape = RoundedCornerShape(18.dp)
        val border = Brush.linearGradient(
            listOf(
                PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.45f),
                PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.28f)
            )
        )
        if (index > 0) Spacer(Modifier.height(10.dp))
        Surface(
            color = Color.Transparent,
            contentColor = Color.White,
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .background(PurrfectOverlayPalette.cardOverlay, shape)
                .border(1.dp, border, shape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                onLongClick?.invoke()
                            },
                            onTap = {
                                onClick()
                            }
                        )
                    }
                    .heightIn(min = 55.dp)
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp))
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    lineHeight = 18.sp,
                    fontSize = 16.sp,
                )
                content()
            }
        }
    }

    private val messaging by lazy { context.feature(Messaging::class)}

    override fun onViewAdded(event: AddViewEvent) {
        fun hasAvatarHeader(viewGroup: ViewGroup): Boolean {
            val constraintLayout = viewGroup.getChildAt(0)?.takeIf { it.javaClass.name.endsWith("ConstraintLayout") } as? ViewGroup ?: return false
            return constraintLayout.children().firstOrNull { it.javaClass.name.endsWith("AvatarView") } != null
        }

        if (messaging.lastFocusedConversationType == 1 &&
            event.viewClassName.endsWith("ConstraintLayout") &&
            event.parent.javaClass.name.endsWith("RecyclerView")
        ) {
            val actionSheetItemsContainerLayout = LinearLayout(event.view.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            injectIntoActionSheetItems(actionSheetItemsContainerLayout) {
                actionSheetItemsContainerLayout.addView(it, 0)
            }

            (event.view as? ViewGroup)?.addView(actionSheetItemsContainerLayout, 0)
            actionSheetItemsContainerLayout.post {
                val parentViewGroup = actionSheetItemsContainerLayout.parent as? ViewGroup ?: return@post
                val topOffset = parentViewGroup.children()
                    .filter { it !== actionSheetItemsContainerLayout && it.visibility != View.GONE }
                    .maxOfOrNull { child ->
                        child.bottom.takeIf { it > 0 } ?: child.measuredHeight
                    } ?: 0

                val desiredPadding = topOffset + this@FriendFeedInfoMenu.context.userInterface.dpToPx(10)
                if (actionSheetItemsContainerLayout.paddingTop != desiredPadding) {
                    actionSheetItemsContainerLayout.setPadding(
                        actionSheetItemsContainerLayout.paddingLeft,
                        desiredPadding,
                        actionSheetItemsContainerLayout.paddingRight,
                        actionSheetItemsContainerLayout.paddingBottom
                    )
                    actionSheetItemsContainerLayout.requestLayout()
                }
            }
        }

        if (event.parent is LinearLayout && event.viewClassName.endsWith("SnapCardView") && hasAvatarHeader(event.parent)) {
            val actionSheetItemsContainerLayout = (event.view as ViewGroup).getChildAt(0) as? ViewGroup ?: throw IllegalStateException("ActionSheetItemsContainerLayout not found")
            injectIntoActionSheetItems(actionSheetItemsContainerLayout) {
                actionSheetItemsContainerLayout.addView(it, 0)
            }
        }
    }

    private fun injectIntoActionSheetItems(actionSheetItemsContainer: View, viewConsumer: ((View) -> Unit)) {
        val friendFeedMenuOptions by context.config.userInterface.friendFeedMenuButtons
        if (friendFeedMenuOptions.isEmpty()) return

        val messaging = context.feature(Messaging::class)
        val conversationId = messaging.lastFocusedConversationId ?: return
        val targetUser by lazy { context.database.getDMOtherParticipant(conversationId) }
        messaging.resetLastFocusedConversation()

        val translation = context.translation.getCategory("friend_menu_option")

        fun closeMenu() {
            if (!context.config.userInterface.autoCloseFriendFeedMenu.get()) return
            context.mainActivity?.triggerRootCloseTouchEvent()
        }

        @Composable
        fun ComposeFriendFeedMenu() {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                var elementIndex by remember { mutableIntStateOf(0) }

                if (friendFeedMenuOptions.contains("conversation_info")) {
                    MenuElement(
                        remember { elementIndex++ },
                        Icons.Outlined.RemoveRedEye,
                        translation["preview"],
                        onClick = {
                            context.coroutineScope.launch {
                                showConversationPreview(targetUser, conversationId)
                            }
                        }
                    )
                }

                context.features.getRuleFeatures().forEach { ruleFeature ->
                    if (!friendFeedMenuOptions.contains(ruleFeature.ruleType.key)) return@forEach

                    val ruleState = ruleFeature.getRuleState() ?: return@forEach
                    var state by remember { mutableStateOf(ruleFeature.getState(conversationId)) }

                    fun toggle() {
                        state = !ruleFeature.getState(conversationId)
                        ruleFeature.setState(conversationId, state)
                        context.inAppOverlay.showStatusToast(
                            if (state) Icons.Default.CheckCircleOutline else Icons.Default.NotInterested,
                            context.translation.format("rules.toasts.${if (state) "enabled" else "disabled"}", "ruleName" to context.translation[ruleFeature.ruleType.translateOptionKey(ruleState.key)]),
                            durationMs = 1500
                        )
                        closeMenu()
                    }

                    MenuElement(
                        remember { elementIndex++ },
                        icon = ruleFeature.ruleType.icon,
                        text = context.translation[ruleFeature.ruleType.translateOptionKey(ruleState.key)],
                        onClick = {
                            toggle()
                        }
                    ) {
                        Switch(
                            checked = state,
                            onCheckedChange = {
                                state = it
                                toggle()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
                            )
                        )
                    }
                }

                if (friendFeedMenuOptions.contains("mark_snaps_as_seen")) {
                    MenuElement(
                        remember { elementIndex++ },
                        Icons.Outlined.EditNote,
                        translation["mark_snaps_as_seen"],
                        onClick = {
                            context.apply {
                                closeMenu()
                                feature(AutoMarkAsRead::class).markSnapsAsSeen(conversationId)
                            }
                        }
                    )
                }

                if (friendFeedMenuOptions.contains("mark_chat_as_read")) {
                    MenuElement(
                        remember { elementIndex++ },
                        Icons.Outlined.MarkChatRead,
                        translation["mark_chat_as_read"],
                        onClick = {
                            context.apply {
                                closeMenu()
                                val latestMessageId = database.getMessagesFromConversationId(conversationId, 1)
                                    ?.firstOrNull()
                                    ?.clientMessageId
                                    ?.toLong()
                                    ?: return@apply

                                feature(StealthMode::class).addDisplayedMessageException(latestMessageId)
                                feature(Messaging::class).conversationManager?.displayedMessages(
                                    conversationId,
                                    latestMessageId
                                ) { error ->
                                    if (error != null) {
                                        log.error("Failed to mark chat as read: $error")
                                        shortToast(this.translation["toast_mark_conversation_read_failed"])
                                    } else {
                                        inAppOverlay.showStatusToast(
                                            Icons.Default.Info,
                                            translation["mark_chat_as_read_toast"],
                                            durationMs = 1800
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                if (targetUser != null && friendFeedMenuOptions.contains("mark_stories_as_seen_locally")) {
                    val markAsSeenTranslation = remember { context.translation.getCategory("mark_as_seen") }

                    MenuElement(
                        remember { elementIndex++ },
                        Icons.Outlined.RemoveRedEye,
                        translation["mark_stories_as_seen_locally"],
                        onClick = {
                            context.apply {
                                closeMenu()
                                inAppOverlay.showStatusToast(
                                    Icons.Default.Info,
                                    if (database.setStoriesViewedState(targetUser!!, true)) markAsSeenTranslation["seen_toast"]
                                    else markAsSeenTranslation["already_seen_toast"],
                                    durationMs = 2500
                                )
                            }
                        },
                        onLongClick = {
                            actionSheetItemsContainer.post {
                                context.apply {
                                    closeMenu()
                                    inAppOverlay.showStatusToast(
                                        Icons.Default.Info,
                                        if (database.setStoriesViewedState(targetUser!!, false)) markAsSeenTranslation["unseen_toast"]
                                        else markAsSeenTranslation["already_unseen_toast"],
                                        durationMs = 2500
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        viewConsumer(
            createComposeView(actionSheetItemsContainer.context) {
                PurrfectOverlayTheme {
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.merge(
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            ComposeFriendFeedMenu()
                        }
                    }
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )

        if (context.config.scripting.integratedUI.get()) {
            context.scriptRuntime.eachModule {
                val interfaceManager = getBinding(InterfaceManager::class)
                    ?.takeIf {
                        it.hasInterface(EnumScriptInterface.FRIEND_FEED_CONTEXT_MENU)
                    } ?: return@eachModule

                viewConsumer(LinearLayout(actionSheetItemsContainer.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    orientation = LinearLayout.VERTICAL
                    addView(createComposeView(actionSheetItemsContainer.context) {
                        PurrfectOverlayTheme {
                            val shape = RoundedCornerShape(18.dp)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.06f), shape)
                                    .border(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.4f),
                                                PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.28f)
                                            )
                                        ),
                                        shape
                                    )
                                    .padding(10.dp),
                                color = Color.Transparent,
                                shape = shape,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                ScriptInterface(interfaceBuilder = remember {
                                    interfaceManager.buildInterface(
                                        EnumScriptInterface.FRIEND_FEED_CONTEXT_MENU,
                                        mapOf(
                                            "conversationId" to conversationId,
                                            "userId" to targetUser
                                        )
                                    )
                                } ?: return@Surface)
                            }
                        }
                    })
                })
            }
        }
    }
}
