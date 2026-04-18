package cock.crest.purrfectsnap.lite.core.action.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.FriendLinkType
import cock.crest.purrfectsnap.lite.common.database.impl.ConversationMessage
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.database.impl.FriendFeedEntry
import cock.crest.purrfectsnap.lite.common.messaging.MessagingConstraints
import cock.crest.purrfectsnap.lite.common.messaging.MessagingTask
import cock.crest.purrfectsnap.lite.common.messaging.MessagingTaskType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.common.util.snap.RemoteMediaResolver
import cock.crest.purrfectsnap.lite.core.action.AbstractAction
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.AddFriendSourceSpoof
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.BetterLocation
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.util.EvictingMap
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.ktx.findStaticObjectFieldByType
import cock.crest.purrfectsnap.lite.mapper.impl.FriendRelationshipChangerMapper
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume
import kotlin.random.Random

class BulkMessagingAction : AbstractAction() {
    private var followingLogCount = 0

    enum class SortBy {
        NONE,
        USERNAME,
        ADDED_TIMESTAMP,
        SNAP_SCORE,
        STREAK_LENGTH,
        MOST_MESSAGES_SENT,
        MOST_RECENT_MESSAGE,
        NEAREST_LOCATION
    }

    enum class Filter {
        ALL,
        MY_FRIENDS,
        BLOCKED,
        REMOVED_ME,
        DELETED,
        SUGGESTED,
        BUSINESS_ACCOUNTS,
        STREAKS,
        NON_STREAKS,
        FOLLOWING,
        INCOMING,
        INCOMING_FOLLOWER,
        LOCATION_ON_MAP
    }

    enum class ConversationType {
        FRIENDS_ONLY,
        GROUPS_ONLY,
        BOTH
    }

    private val translation by lazy { context.translation.getCategory("bulk_messaging_action") }
    private val betterLocation by lazy { context.feature(BetterLocation::class) }

    private fun hasReliableStreak(friend: FriendInfo, streakFeedUserIds: Set<String>): Boolean {
        val userId = friend.userId ?: return false
        if (userId in streakFeedUserIds) return true
        if (friend.streakExpirationTimestamp > 0L) return true
        if (friend.streakLength > 0) return true
        val categories = friend.friendmojiCategories?.split(",") ?: return false
        return categories.any { category ->
            category.contains("streak", ignoreCase = true) ||
                category.contains("hourglass", ignoreCase = true)
        }
    }

    private object BulkMessagingPalette {
        val background = Brush.verticalGradient(
            listOf(
                Color(0xFF261F58),
                Color(0xFF302A6D),
                Color(0xFF241F52)
            )
        )
        val cardOverlay = Brush.linearGradient(
            listOf(
                Color(0xFF2A2452).copy(alpha = 0.95f),
                Color(0xFF1A143A).copy(alpha = 0.92f)
            )
        )
        val glowStroke = Brush.linearGradient(
            listOf(
                Color(0xFF8C7BFF),
                Color(0xFF5FD8FF)
            )
        )
        val glowPrimary = Color(0xFF8C7BFF)
        val glowSecondary = Color(0xFF5FD8FF)
        val textPrimary = Color.White
        val textSecondary = Color(0xFFD9D3FF)
        val surface = Color(0xFF1E1A44)
        val faintSurface = Color.White.copy(alpha = 0.06f)
        val outline = Color.White.copy(alpha = 0.12f)
    }

    private fun removeAction(
        ctx: Context,
        ids: List<String>,
        delay: Pair<Long, Long>,
        action: suspend (id: String, setDialogMessage: (String) -> Unit) -> Unit = { _, _ -> }
    ) = context.coroutineScope.launch {
        val statusTextView = TextView(ctx)
        val progressBar = ProgressBar(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val dialog = withContext(Dispatchers.Main) {
            val d = ViewAppearanceHelper.newAlertDialogBuilder(ctx)
                .setTitle("...")
                .setView(LinearLayout(ctx).apply {
                    val padding = (16 * ctx.resources.displayMetrics.density).toInt()
                    val spacing = (8 * ctx.resources.displayMetrics.density).toInt()
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(padding, padding, padding, padding)
                    addView(statusTextView.apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setSingleLine(false)
                        setPadding(0, 0, 0, spacing)
                    })
                    addView(progressBar)
                })
                .setCancelable(false)
                .show()
            // Style dialog to match app UI (gradient, app colors)
            val density = ctx.resources.displayMetrics.density
            d.window?.setBackgroundDrawable(GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    AndroidColor.parseColor("#2A2452"),
                    AndroidColor.parseColor("#1A143A")
                )
            ).apply {
                cornerRadius = (20 * density).toFloat()
            })
            val titleId = ctx.resources.getIdentifier("alertTitle", "id", "android")
            if (titleId != 0) {
                (d.window?.decorView?.findViewById<View>(titleId) as? TextView)?.setTextColor(AndroidColor.WHITE)
            }
            statusTextView.setTextColor(AndroidColor.parseColor("#E0E0E0"))
            progressBar.indeterminateTintList = ColorStateList.valueOf(AndroidColor.parseColor("#8C7BFF"))
            d
        }

        ids.forEachIndexed { index, id ->
            launch(Dispatchers.Main) {
                dialog.setTitle(
                    translation.format("progress_status", "index" to (index + 1).toString(), "total" to ids.size.toString())
                )
            }
            runCatching {
                action(id) {
                    launch(Dispatchers.Main) {
                        statusTextView.text = it
                    }
                }
            }.onFailure {
                context.log.error("Failed to process $it", it)
                context.shortToast(translation.format("failed_to_process", "id" to id))
            }
            delay(Random.nextLong(delay.first, delay.second))
        }
        withContext(Dispatchers.Main) {
            dialog.dismiss()
        }
    }

    @Composable
    private fun ConfirmationDialog(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Dialog(onDismissRequest = onCancel) {
            val shape = RoundedCornerShape(22.dp)
            Surface(
                shape = shape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, BulkMessagingPalette.glowStroke)
            ) {
                Column(
                    modifier = Modifier
                        .background(BulkMessagingPalette.cardOverlay, shape)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BulkMessagingPalette.faintSurface
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = BulkMessagingPalette.textPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = translation["confirmation_dialog.title"],
                        style = MaterialTheme.typography.titleLarge,
                        color = BulkMessagingPalette.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = translation["confirmation_dialog.message"],
                        style = MaterialTheme.typography.bodyMedium,
                        color = BulkMessagingPalette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = BulkMessagingPalette.textPrimary
                            )
                        ) {
                            Text(text = context.translation["button.negative"])
                        }
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BulkMessagingPalette.glowPrimary.copy(alpha = 0.34f),
                                contentColor = BulkMessagingPalette.textPrimary
                            )
                        ) {
                            Text(text = context.translation["button.positive"])
                        }
                    }
                }
            }
        }
    }

    private fun filterFriends(
        friends: List<FriendInfo>,
        filter: Filter,
        nameFilter: String,
        streakFeedUserIds: Set<String> = emptySet()
    ): List<FriendInfo> {
        val userIdBlacklist = arrayOf(
            context.database.myUserId,
            "b42f1f70-5a8b-4c53-8c25-34e7ec9e6781", // myai
            "84ee8839-3911-492d-8b94-72dd80f3713a", // teamsnapchat
        )

        if (filter == Filter.FOLLOWING) {
            val uniqueLinkTypes = friends.map { it.friendLinkType }.distinct().sorted()
            context.log.verbose("BulkMessaging: All unique friendLinkType values in database: $uniqueLinkTypes")
            context.log.verbose("BulkMessaging: FOLLOWING enum value: ${FriendLinkType.FOLLOWING.value}")
        }

        if (filter == Filter.FOLLOWING) followingLogCount = 0

        val result = friends.filter { friend ->
            friend.userId !in userIdBlacklist && when (filter) {
                Filter.ALL -> true
                Filter.MY_FRIENDS -> friend.friendLinkType == FriendLinkType.MUTUAL.value && friend.addedTimestamp > 0
                Filter.BLOCKED -> friend.friendLinkType == FriendLinkType.BLOCKED.value
                Filter.REMOVED_ME -> friend.friendLinkType == FriendLinkType.OUTGOING.value && friend.addedTimestamp > 0 && friend.businessCategory == 0
                Filter.SUGGESTED -> friend.friendLinkType == FriendLinkType.SUGGESTED.value
                Filter.DELETED -> friend.friendLinkType == FriendLinkType.DELETED.value
                Filter.BUSINESS_ACCOUNTS -> friend.businessCategory > 0
                Filter.STREAKS -> friend.friendLinkType == FriendLinkType.MUTUAL.value &&
                    friend.addedTimestamp > 0 &&
                    hasReliableStreak(friend, streakFeedUserIds)
                Filter.NON_STREAKS -> friend.friendLinkType == FriendLinkType.MUTUAL.value &&
                    friend.addedTimestamp > 0 &&
                    !hasReliableStreak(friend, streakFeedUserIds)
                Filter.FOLLOWING -> {
                    val isFollowing = friend.friendLinkType == FriendLinkType.FOLLOWING.value ||
                        (friend.friendLinkType == FriendLinkType.OUTGOING.value &&
                            (friend.businessCategory > 0 || friend.addedTimestamp <= 0))
                    if (isFollowing) {
                        when {
                            followingLogCount < 5 -> context.log.verbose("BulkMessaging: Friend ${friend.mutableUsername} (${friend.userId}) - LinkType=${friend.friendLinkType}, BusinessCategory=${friend.businessCategory}, Match=true")
                            followingLogCount == 5 -> context.log.verbose("BulkMessaging: Additional following entries truncated")
                        }
                        followingLogCount++
                    }
                    isFollowing
                }
                Filter.INCOMING -> friend.friendLinkType == FriendLinkType.INCOMING.value
                Filter.INCOMING_FOLLOWER -> friend.friendLinkType == FriendLinkType.INCOMING_FOLLOWER.value
                Filter.LOCATION_ON_MAP -> betterLocation.locationHistory.contains(friend.userId)
            } && nameFilter.takeIf { it.isNotBlank() }?.let { name ->
                friend.mutableUsername?.contains(
                    name,
                    ignoreCase = true
                ) == true || friend.displayName?.contains(name, ignoreCase = true) == true
            } ?: true
        }
        
        if (filter == Filter.FOLLOWING) {
            context.log.verbose("BulkMessaging: FOLLOWING filter returned ${result.size} friends")
        }
        
        return result
    }

    private fun getDMLastMessage(userId: String?): ConversationMessage? {
        return context.database.getDMConversationId(userId ?: return null)?.let {
            context.database.getMessagesFromConversationId(it, 1)
        }?.firstOrNull()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    private fun BulkMessagingDialog() {
        val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
        var sortBy by remember { mutableStateOf(SortBy.USERNAME) }
        var filter by remember { mutableStateOf(Filter.REMOVED_ME) }
        var conversationType by remember { mutableStateOf(ConversationType.FRIENDS_ONLY) }
        var sortReverseOrder by remember { mutableStateOf(false) }
        val selectedFriends = remember { mutableStateListOf<String>() }
        val selectedGroups = remember { mutableStateListOf<String>() }
        val friends = remember { mutableStateListOf<FriendInfo>() }
        val groups = remember { mutableStateListOf<FriendFeedEntry>() }
        val hiddenFriendIds = remember { mutableStateListOf<String>() }
        val hiddenConversationIds = remember { mutableStateListOf<String>() }
        val bitmojiCache = remember { EvictingMap<String, Bitmap>(50) }
        val noBitmojiBitmap = remember { BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_report_image).asImageBitmap() }

        val focusManager = LocalFocusManager.current
        var nameFilter by remember { mutableStateOf("") }

        suspend fun refreshList(clearSelected: Boolean = true) {
            val myLocation = betterLocation.locationHistory[context.database.myUserId]

            withContext(Dispatchers.IO) {
                // Sync with Snapchat feed: only show friends whose DM conversation still exists in feed
                // (FriendsFeedView excludes cleared via "clearedTimestamp < lastInteractionTimestamp")
                // BUT: only apply this filtering to certain filters that specifically need it
                val friendIdsStillInFeed = if (filter in setOf(Filter.MY_FRIENDS)) {
                    runCatching {
                        context.database.getFeedEntries(Int.MAX_VALUE)
                            .filter { it.conversationType == 0 && it.participantsSize == 2 }
                            .mapNotNull { it.participants?.firstOrNull { id -> id != context.database.myUserId } }
                            .toSet()
                    }.getOrElse { emptySet() }
                } else {
                    emptySet()
                }

                val incomingRequestUserIds = if (filter == Filter.INCOMING || filter == Filter.INCOMING_FOLLOWER) {
                    runCatching { context.database.getIncomingRequestUserIds() }.getOrElse { emptySet() }
                } else emptySet()
                val streakFeedUserIds = if (filter == Filter.STREAKS || filter == Filter.NON_STREAKS) {
                    runCatching {
                        context.database.getFeedEntries(Int.MAX_VALUE)
                            .filter { it.conversationType == 0 && it.participantsSize == 2 }
                            .filter { (it.streakCount ?: 0) > 0 || (it.streakExpirationTimestampMs ?: 0L) > 0L }
                            .mapNotNull { entry ->
                                entry.friendUserId ?: entry.participants?.firstOrNull { id -> id != context.database.myUserId }
                            }
                            .toSet()
                    }.getOrElse { emptySet() }
                } else emptySet()

                val newFriends = if (conversationType == ConversationType.FRIENDS_ONLY || conversationType == ConversationType.BOTH) {
                    context.database.getAllFriends().let { friends ->
                        filterFriends(friends, filter, nameFilter, streakFeedUserIds)
                    }
                        .filter { it.userId?.let { id -> !hiddenFriendIds.contains(id) } == true }
                        .filter { friend ->
                            when {
                                // Only show incoming/follower requests that exist in FriendWhoAddedMe (real pending requests)
                                filter == Filter.INCOMING || filter == Filter.INCOMING_FOLLOWER ->
                                    friend.userId != null && friend.userId in incomingRequestUserIds
                                // Only apply feed filtering to MY_FRIENDS filter
                                filter == Filter.MY_FRIENDS ->
                                    friendIdsStillInFeed.isEmpty() || friend.userId in friendIdsStillInFeed
                                // All other filters: don't restrict by feed presence
                                else -> true
                            }
                        }
                        .toMutableList()
                } else mutableListOf()
                
                val newGroups = if (conversationType == ConversationType.GROUPS_ONLY || conversationType == ConversationType.BOTH) {
                    runCatching {
                        context.database.getFeedEntries(Int.MAX_VALUE).filter {
                            it.conversationType == 1 && (nameFilter.isBlank() || it.feedDisplayName?.contains(nameFilter, ignoreCase = true) == true) && it.key?.let { key -> !hiddenConversationIds.contains(key) } == true
                        }.toMutableList()
                    }.getOrElse {
                        context.log.warn("Failed to load group feed entries: ${it.message}")
                        mutableListOf()
                    }
                } else mutableListOf()
                
                when (sortBy) {
                    SortBy.NONE -> {}
                    SortBy.USERNAME -> {
                        newFriends.sortBy { it.mutableUsername }
                        newGroups.sortBy { it.feedDisplayName }
                    }
                    SortBy.ADDED_TIMESTAMP -> newFriends.sortBy { it.addedTimestamp }
                    SortBy.SNAP_SCORE -> newFriends.sortBy { it.snapScore }
                    SortBy.STREAK_LENGTH -> newFriends.sortBy { it.streakLength }
                    SortBy.MOST_MESSAGES_SENT -> newFriends.sortByDescending {
                        getDMLastMessage(it.userId)?.serverMessageId ?: 0
                    }
                    SortBy.MOST_RECENT_MESSAGE -> {
                        newFriends.sortByDescending {
                            getDMLastMessage(it.userId)?.creationTimestamp
                        }
                        newGroups.sortByDescending { it.lastInteractionTimestamp }
                    }
                    SortBy.NEAREST_LOCATION -> {
                        if (myLocation != null) {
                            newFriends.sortBy {
                                betterLocation.locationHistory[it.userId]?.distanceTo(myLocation)
                                    ?: Double.MAX_VALUE
                            }
                        }
                    }
                }
                if (sortReverseOrder) {
                    newFriends.reverse()
                    newGroups.reverse()
                }
                withContext(Dispatchers.Main) {
                    if (clearSelected) {
                        selectedFriends.clear()
                        selectedGroups.clear()
                    }
                    friends.clear()
                    friends.addAll(newFriends)
                    groups.clear()
                    groups.addAll(newGroups)
                }
            }
        }

        fun markRemoval(friendId: String? = null, conversationId: String? = null) {
            coroutineScope.launch(Dispatchers.Main) {
                conversationId?.let {
                    if (!hiddenConversationIds.contains(it)) hiddenConversationIds.add(it)
                    selectedGroups.remove(it)
                    groups.removeAll { entry -> entry.key == it }
                }
                friendId?.let {
                    if (!hiddenFriendIds.contains(it)) hiddenFriendIds.add(it)
                    selectedFriends.remove(it)
                    friends.removeAll { info -> info.userId == it }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BulkMessagingPalette.background)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, BulkMessagingPalette.glowStroke)
            ) {
                Column(
                    modifier = Modifier
                        .background(BulkMessagingPalette.cardOverlay)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Bulk messaging",
                            style = MaterialTheme.typography.titleLarge,
                            color = BulkMessagingPalette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = translation["conversation_types.${conversationType.name.lowercase()}"],
                            style = MaterialTheme.typography.bodyMedium,
                            color = BulkMessagingPalette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
            // Top tabs: Friends Only | Groups Only | Friends & Groups
            val tabs = remember {
                listOf(
                    ConversationType.FRIENDS_ONLY to translation["conversation_types.friends_only"],
                    ConversationType.GROUPS_ONLY to translation["conversation_types.groups_only"],
                    ConversationType.BOTH to translation["conversation_types.both"],
                )
            }
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BulkMessagingPalette.faintSurface, RoundedCornerShape(18.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { (type, title) ->
                        val selected = conversationType == type
                        val label = when (type) {
                            ConversationType.FRIENDS_ONLY -> translation["conversation_types.friends_only"]
                            ConversationType.GROUPS_ONLY -> translation["conversation_types.groups_only"].replace(" ", "\n")
                            ConversationType.BOTH -> translation["conversation_types.both"].replace(" & ", "\n")
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    BorderStroke(1.dp, if (selected) BulkMessagingPalette.glowPrimary else BulkMessagingPalette.outline),
                                    RoundedCornerShape(14.dp)
                                )
                                .background(
                                    if (selected) BulkMessagingPalette.glowPrimary.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (!selected) {
                                        conversationType = type
                                        coroutineScope.launch { refreshList() }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) BulkMessagingPalette.textPrimary else BulkMessagingPalette.textSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val searchShape = RoundedCornerShape(14.dp)
                    BasicTextField(
                        value = nameFilter,
                        onValueChange = {
                            nameFilter = it
                            coroutineScope.launch { refreshList(clearSelected = false) }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = BulkMessagingPalette.textPrimary
                        ),
                        cursorBrush = SolidColor(BulkMessagingPalette.glowPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BulkMessagingPalette.outline, searchShape)
                                .background(Color.Transparent, searchShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (nameFilter.isEmpty()) {
                                Text(
                                    text = translation["search_by_name"],
                                    color = BulkMessagingPalette.textSecondary
                                )
                            }
                            innerTextField()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var filterMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = filterMenuExpanded,
                            onExpandedChange = { filterMenuExpanded = it },
                        ) {
                            ElevatedCard(
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
                            ) {
                                Text(
                                    text = translation["filters.${filter.name.lowercase()}"],
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = BulkMessagingPalette.textPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false },
                                containerColor = BulkMessagingPalette.surface
                            ) {
                                Filter.entries.forEach { entry ->
                                    DropdownMenuItem(
                                        onClick = {
                                            filter = entry
                                            filterMenuExpanded = false
                                        },
                                        text = {
                                            Text(
                                                text = translation["filters.${entry.name.lowercase()}"],
                                                fontWeight = if (entry == filter) FontWeight.Bold else FontWeight.Normal,
                                                color = if (entry == filter) BulkMessagingPalette.glowPrimary else BulkMessagingPalette.textPrimary
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = sortMenuExpanded,
                            onExpandedChange = { sortMenuExpanded = it },
                        ) {
                            ElevatedCard(
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
                            ) {
                                Text(
                                    text = translation["sort_by"],
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = BulkMessagingPalette.textPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                containerColor = BulkMessagingPalette.surface
                            ) {
                                SortBy.entries.forEach { entry ->
                                    DropdownMenuItem(
                                        onClick = {
                                            sortBy = entry
                                            sortMenuExpanded = false
                                        },
                                        text = {
                                            Text(
                                                text = translation["sort_options.${entry.name.lowercase()}"],
                                                fontWeight = if (entry == sortBy) FontWeight.Bold else FontWeight.Normal,
                                                color = if (entry == sortBy) BulkMessagingPalette.glowPrimary else BulkMessagingPalette.textPrimary
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = context.translation["manager.dialogs.messaging_action.select_all_button"],
                            fontSize = 14.sp,
                            color = BulkMessagingPalette.textSecondary
                        )

                        Checkbox(
                            checked = {
                                val totalItems = when (conversationType) {
                                    ConversationType.FRIENDS_ONLY -> friends.size
                                    ConversationType.GROUPS_ONLY -> groups.size
                                    ConversationType.BOTH -> friends.size + groups.size
                                }
                                val selectedItems = when (conversationType) {
                                    ConversationType.FRIENDS_ONLY -> selectedFriends.size
                                    ConversationType.GROUPS_ONLY -> selectedGroups.size
                                    ConversationType.BOTH -> selectedFriends.size + selectedGroups.size
                                }
                                totalItems > 0 && selectedItems == totalItems
                            }(),
                            onCheckedChange = { state ->
                                if (state) {
                                    when (conversationType) {
                                        ConversationType.FRIENDS_ONLY -> {
                                            friends.mapNotNull { it.userId }.forEach { userId ->
                                                if (!selectedFriends.contains(userId)) {
                                                    selectedFriends.add(userId)
                                                }
                                            }
                                        }
                                        ConversationType.GROUPS_ONLY -> {
                                            groups.mapNotNull { it.key }.forEach { conversationId ->
                                                if (!selectedGroups.contains(conversationId)) {
                                                    selectedGroups.add(conversationId)
                                                }
                                            }
                                        }
                                        ConversationType.BOTH -> {
                                            friends.mapNotNull { it.userId }.forEach { userId ->
                                                if (!selectedFriends.contains(userId)) {
                                                    selectedFriends.add(userId)
                                                }
                                            }
                                            groups.mapNotNull { it.key }.forEach { conversationId ->
                                                if (!selectedGroups.contains(conversationId)) {
                                                    selectedGroups.add(conversationId)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    when (conversationType) {
                                        ConversationType.FRIENDS_ONLY -> {
                                            if (nameFilter.isNotBlank()) {
                                                filterFriends(friends, filter, nameFilter).mapNotNull { it.userId }.forEach { userId ->
                                                    selectedFriends.remove(userId)
                                                }
                                            } else {
                                                selectedFriends.clear()
                                            }
                                        }
                                        ConversationType.GROUPS_ONLY -> {
                                            if (nameFilter.isNotBlank()) {
                                                groups.filter { it.feedDisplayName?.contains(nameFilter, ignoreCase = true) == true }
                                                    .mapNotNull { it.key }.forEach { conversationId ->
                                                    selectedGroups.remove(conversationId)
                                                }
                                            } else {
                                                selectedGroups.clear()
                                            }
                                        }
                                        ConversationType.BOTH -> {
                                            if (nameFilter.isNotBlank()) {
                                                filterFriends(friends, filter, nameFilter).mapNotNull { it.userId }.forEach { userId ->
                                                    selectedFriends.remove(userId)
                                                }
                                                groups.filter { it.feedDisplayName?.contains(nameFilter, ignoreCase = true) == true }
                                                    .mapNotNull { it.key }.forEach { conversationId ->
                                                    selectedGroups.remove(conversationId)
                                                }
                                            } else {
                                                selectedFriends.clear()
                                                selectedGroups.clear()
                                            }
                                        }
                                    }
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BulkMessagingPalette.glowPrimary,
                                uncheckedColor = BulkMessagingPalette.outline,
                                checkmarkColor = BulkMessagingPalette.textPrimary
                            )
                        )
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 200.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                item {
                    val isEmpty = when (conversationType) {
                        ConversationType.FRIENDS_ONLY -> friends.isEmpty()
                        ConversationType.GROUPS_ONLY -> groups.isEmpty()
                        ConversationType.BOTH -> friends.isEmpty() && groups.isEmpty()
                    }
                    if (isEmpty) {
                        Text(text = when (conversationType) {
                            ConversationType.FRIENDS_ONLY -> translation["no_friends_found"]
                            ConversationType.GROUPS_ONLY -> translation["no_groups_found"]
                            ConversationType.BOTH -> translation["no_friends_or_groups_found"]
                        }, fontSize = 12.sp, fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = BulkMessagingPalette.textPrimary)
                    }
                }
                items(friends, key = { it.userId!! }) { friendInfo ->
                    var bitmojiBitmap by remember(friendInfo) { mutableStateOf(bitmojiCache[friendInfo.bitmojiAvatarId]) }

                    fun selectFriend(state: Boolean) {
                        friendInfo.userId?.let {
                            if (state) {
                                selectedFriends.add(it)
                            } else {
                                selectedFriends.remove(it)
                            }
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.elevatedCardColors(containerColor = BulkMessagingPalette.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .clickable {
                                    selectFriend(!selectedFriends.contains(friendInfo.userId))
                                }.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { context.androidContext.copyToClipboard(friendInfo.mutableUsername.toString()) }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                        LaunchedEffect(friendInfo) {
                            withContext(Dispatchers.IO) {
                                if (bitmojiBitmap != null || friendInfo.bitmojiAvatarId == null || friendInfo.bitmojiSelfieId == null) return@withContext

                                val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(friendInfo.bitmojiSelfieId, friendInfo.bitmojiAvatarId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D) ?: return@withContext

                                runCatching {
                                    RemoteMediaResolver.downloadMedia(bitmojiUrl) { inputStream, length ->
                                        bitmojiCache[friendInfo.bitmojiAvatarId ?: return@withContext] = BitmapFactory.decodeStream(inputStream).also {
                                            bitmojiBitmap = it
                                        }
                                    }
                                }
                            }
                        }

                        Image(
                            bitmap = remember (bitmojiBitmap) { bitmojiBitmap?.asImageBitmap() ?: noBitmojiBitmap },
                            contentDescription = null,
                            modifier = Modifier.size(35.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ){
                                Text(text = (friendInfo.displayName ?: friendInfo.mutableUsername).toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis, maxLines = 1, lineHeight = 10.sp, color = BulkMessagingPalette.textPrimary)
                                Text(text = friendInfo.mutableUsername.toString(), fontSize = 10.sp, fontWeight = FontWeight.Light, overflow = TextOverflow.Ellipsis, maxLines = 1, lineHeight = 10.sp, color = BulkMessagingPalette.textSecondary)
                            }
                            val lastMessage by rememberAsyncMutableState(defaultValue = null) {
                                getDMLastMessage(friendInfo.userId)
                            }

                            val userInfo = remember(friendInfo, lastMessage) {
                                buildString {
                                    append(translation["relationship"])
                                    append(context.translation["friendship_link_type.${FriendLinkType.fromValue(friendInfo.friendLinkType).shortName}"])
                                    friendInfo.addedTimestamp.takeIf { it > 0L }?.let {
                                        append("\nAdded ${DateFormat.getDateTimeInstance().format(Date(it))}")
                                    }
                                    friendInfo.snapScore.takeIf { it > 0 }?.let {
                                        append("\nSnap Score: $it")
                                    }
                                    friendInfo.streakLength.takeIf { it > 0 }?.let {
                                        append("\nStreaks length: $it")
                                    }
                                    lastMessage?.let {
                                        append("\nSent messages: ${it.serverMessageId}")
                                        append("\nLast message: ${DateFormat.getDateTimeInstance().format(Date(it.creationTimestamp))}")
                                    }
                                    betterLocation.locationHistory[context.database.myUserId]?.let { myLocation ->
                                        betterLocation.locationHistory[friendInfo.userId]?.let {
                                            append("\n${myLocation.distanceTo(it).let { distance ->
                                                if (distance < 1) "${(distance * 1000).toInt()} m" else "${distance.toInt()} km"
                                            } } away")
                                        }
                                    }
                                }
                            }
                            Text(text = userInfo, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis, color = BulkMessagingPalette.textSecondary)
                        }

                        Checkbox(
                            checked = selectedFriends.contains(friendInfo.userId),
                            onCheckedChange = { selectFriend(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BulkMessagingPalette.glowPrimary,
                                uncheckedColor = BulkMessagingPalette.outline,
                                checkmarkColor = BulkMessagingPalette.textPrimary
                            )
                        )
                    }
                    }
                }
                
                // Group items
                items(groups, key = { it.key!! }) { groupInfo ->
                    fun selectGroup(state: Boolean) {
                        groupInfo.key?.let {
                            if (state) {
                                selectedGroups.add(it)
                            } else {
                                selectedGroups.remove(it)
                            }
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.elevatedCardColors(containerColor = BulkMessagingPalette.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .clickable {
                                    selectGroup(!selectedGroups.contains(groupInfo.key))
                                }.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { context.androidContext.copyToClipboard(groupInfo.feedDisplayName.toString()) }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Group icon placeholder
                            Image(
                                bitmap = noBitmojiBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(35.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = groupInfo.feedDisplayName?.toString() ?: translation["unknown_group"], 
                                    fontSize = 16.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    overflow = TextOverflow.Ellipsis, 
                                    maxLines = 1, 
                                    lineHeight = 10.sp,
                                    color = BulkMessagingPalette.textPrimary
                                )
                                
                                val groupInfo = remember(groupInfo) {
                                    buildString {
                                        append(translation["type_group_chat"])
                                        groupInfo.lastInteractionTimestamp.takeIf { it > 0L }?.let {
                                            append("\nLast interaction: ${DateFormat.getDateTimeInstance().format(Date(it))}")
                                        }
                                    }
                                }
                                Text(text = groupInfo, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis, color = BulkMessagingPalette.textSecondary)
                            }

                            Checkbox(
                                checked = selectedGroups.contains(groupInfo.key),
                                onCheckedChange = { selectGroup(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BulkMessagingPalette.glowPrimary,
                                    uncheckedColor = BulkMessagingPalette.outline,
                                    checkmarkColor = BulkMessagingPalette.textPrimary
                                )
                            )
                        }
                    }
                }
            }

            var showConfirmationDialog by remember { mutableStateOf(false) }
            var action by remember { mutableStateOf({}) }

            if (showConfirmationDialog) {
                ConfirmationDialog(
                    onConfirm = {
                        action()
                        action = {}
                        showConfirmationDialog = false
                    },
                    onCancel = {
                        action = {}
                        showConfirmationDialog = false
                    }
                )
            }

            val ctx = LocalContext.current

            val actions = remember(selectedFriends.size, selectedGroups.size, conversationType, filter) {
                val following = filter == Filter.FOLLOWING
                buildMap<() -> String, () -> Unit> {
                    val messagingFeature = context.feature(Messaging::class)
                    fun fetchConversations(ids: List<String>, errorKey: String, block: (Map<String, String>) -> Unit) {
                        if (ids.isEmpty()) {
                            block(emptyMap())
                            return
                        }
                        fun run(map: Map<String, String>) = block(map)
                        messagingFeature.conversationManager?.getOneOnOneConversationIds(ids, onError = { error ->
                            context.shortToast(translation.format(errorKey, "error" to error))
                            run(emptyMap())
                        }, onSuccess = { conversations ->
                            run(conversations.toMap())
                        }) ?: run(emptyMap())
                    }
                    fun MutableMap<() -> String, () -> Unit>.addUnfollowActions(errorKey: String) {
                        if (selectedFriends.isEmpty()) return
                        put({ "${translation["unfollow"]} (${selectedFriends.size})" }) {
                            val ids = selectedFriends.toList()
                            selectedFriends.clear()
                            fetchConversations(ids, errorKey) { conversations ->
                                removeAction(ctx, ids, 500L to 1200L) { userId, setDialogMessage ->
                                    unfollowUser(userId)
                                    conversations[userId]?.let { conversationId ->
                                        clearConversationFeed(conversationId, setDialogMessage, onFailure = {
                                            setDialogMessage(translation["actions.unfollow"])
                                        }, onSuccess = {
                                            markRemoval(userId, conversationId)
                                        })
                                    } ?: setDialogMessage(translation["actions.unfollow"])
                                }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                            }
                        }
                        put({ "${translation["unfollow"]} & ${translation.format("clean_conversations", "count" to selectedFriends.size.toString())}" }) {
                            val ids = selectedFriends.toList()
                            selectedFriends.clear()
                            fetchConversations(ids, errorKey) { conversations ->
                                val reverse = conversations.entries.associate { it.value to it.key }
                                removeAction(ctx, conversations.values.distinct(), 500L to 1200L) { conversationId, setDialogMessage ->
                                    cleanConversation(conversationId, setDialogMessage)
                                    reverse[conversationId]?.let { unfollowUser(it) }
                                    clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                        reverse[conversationId]?.let { markRemoval(it, conversationId) }
                                    })
                                }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                            }
                        }
                    }
                    val incomingRequests = filter == Filter.INCOMING || filter == Filter.INCOMING_FOLLOWER
                    when (conversationType) {
                        ConversationType.FRIENDS_ONLY -> when {
                            following -> addUnfollowActions("failed_to_fetch_conversations")
                            incomingRequests -> {
                                put({ "${translation["accept_requests"]} (${selectedFriends.size})" }) {
                                    val ids = selectedFriends.toList()
                                    selectedFriends.clear()
                                    removeAction(ctx, ids, 500L to 1200L) { userId, setDialogMessage ->
                                        acceptFriendRequest(userId)
                                        setDialogMessage(translation["actions.accept"])
                                        markRemoval(userId, null)
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                }
                                put({ "${translation["ignore_requests"]} (${selectedFriends.size})" }) {
                                    val ids = selectedFriends.toList()
                                    selectedFriends.clear()
                                    removeAction(ctx, ids, 500L to 1200L) { userId, setDialogMessage ->
                                        ignoreFriendRequest(userId)
                                        setDialogMessage(translation["actions.ignore"])
                                        markRemoval(userId, null)
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                }
                            }
                            else -> {
                            put({ translation.format("clean_conversations", "count" to selectedFriends.size.toString()) }) {
                                context.feature(Messaging::class).conversationManager?.getOneOnOneConversationIds(selectedFriends.toList().also { selectedFriends.clear() }, onError = { error ->
                                    context.shortToast(translation.format("failed_to_fetch_conversations", "error" to error))
                                }, onSuccess = { conversations ->
                                    removeAction(ctx, conversations.map { it.second }.distinct(), 10L to 40L) { conversationId, setDialogMessage ->
                                        cleanConversation(conversationId, setDialogMessage)
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                })
                            }
                            put({ translation.format("clear_friend_feed", "count" to selectedFriends.size.toString()) }) {
                                val ids = selectedFriends.toList()
                                fetchConversations(ids, "failed_to_fetch_conversations") { conversations ->
                                    val conversationIds = conversations.values.distinct()
                                    if (conversationIds.isEmpty()) {
                                        selectedFriends.clear()
                                        coroutineScope.launch { refreshList() }
                                        return@fetchConversations
                                    }
                                    removeAction(ctx, conversationIds, 10L to 40L) { conversationId, setDialogMessage ->
                                        clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                            markRemoval(conversationId = conversationId)
                                            coroutineScope.launch { refreshList(clearSelected = false) }
                                        })
                                    }.invokeOnCompletion {
                                        selectedFriends.clear()
                                        coroutineScope.launch { refreshList() }
                                    }
                                }
                            }
                            put({ translation.format("remove_friends", "count" to selectedFriends.size.toString()) }) {
                                val ids = selectedFriends.toList()
                                selectedFriends.clear()
                                fetchConversations(ids, "failed_to_fetch_conversations") { conversations ->
                                    removeAction(ctx, ids, 500L to 1200L) { userId, setDialogMessage ->
                                    removeFriend(userId)
                                    conversations[userId]?.let { conversationId ->
                                        clearConversationFeed(conversationId, setDialogMessage, onFailure = {
                                            setDialogMessage(translation["actions.remove"])
                                        }, onSuccess = {
                                            markRemoval(userId, conversationId)
                                        })
                                        } ?: setDialogMessage(translation["actions.remove"])
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                }
                            }
                            put({ translation.format("clean_conversations_and_remove_friends", "count" to selectedFriends.size.toString()) }) {
                                context.feature(Messaging::class).conversationManager?.getOneOnOneConversationIds(selectedFriends.toList().also { selectedFriends.clear() }, onError = { error ->
                                    context.shortToast(translation.format("failed_to_fetch_conversations", "error" to error))
                                }, onSuccess = { conversations ->
                                    removeAction(ctx, conversations.map { it.second }.distinct(), 500L to 1200L) { conversationId, setDialogMessage ->
                                        cleanConversation(conversationId, setDialogMessage)
                                        conversations.firstOrNull { it.second == conversationId }?.first?.let { friendId ->
                                            removeFriend(friendId)
                                            clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                                markRemoval(friendId, conversationId)
                                            })
                                        }
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                })
                            }
                            }
                        }
                        ConversationType.GROUPS_ONLY -> if (!following) {
                            put({ translation.format("clean_group_conversations", "count" to selectedGroups.size.toString()) }) {
                                removeAction(ctx, selectedGroups.toList().also { selectedGroups.clear() }, 10L to 40L) { conversationId, setDialogMessage ->
                                    cleanConversation(conversationId, setDialogMessage)
                                }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                            }
                            put({ translation.format("clear_group_feed", "count" to selectedGroups.size.toString()) }) {
                                val ids = selectedGroups.toList()
                                if (ids.isEmpty()) {
                                    selectedGroups.clear()
                                    coroutineScope.launch { refreshList() }
                                } else {
                                    removeAction(ctx, ids, 10L to 40L) { conversationId, setDialogMessage ->
                                        clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                            markRemoval(conversationId = conversationId)
                                            coroutineScope.launch { refreshList(clearSelected = false) }
                                        })
                                    }.invokeOnCompletion {
                                        selectedGroups.clear()
                                        coroutineScope.launch { refreshList() }
                                    }
                                }
                            }
                        }
                        ConversationType.BOTH -> if (following) {
                            addUnfollowActions("failed_to_fetch_friend_conversations")
                        } else {
                            put({ translation.format("clean_all_conversations", "count" to (selectedFriends.size + selectedGroups.size).toString()) }) {
                                if (selectedFriends.isNotEmpty()) {
                                    context.feature(Messaging::class).conversationManager?.getOneOnOneConversationIds(selectedFriends.toList(), onError = { error ->
                                        context.shortToast(translation.format("failed_to_fetch_friend_conversations", "error" to error))
                                    }, onSuccess = { conversations ->
                                        removeAction(ctx, conversations.map { it.second }.distinct(), 10L to 40L) { conversationId, setDialogMessage ->
                                            cleanConversation(conversationId, setDialogMessage)
                                        }.invokeOnCompletion {
                                            if (selectedGroups.isNotEmpty()) {
                                                removeAction(ctx, selectedGroups.toList(), 10L to 40L) { conversationId, setDialogMessage ->
                                                    cleanConversation(conversationId, setDialogMessage)
                                                }.invokeOnCompletion {
                                                    selectedFriends.clear()
                                                    selectedGroups.clear()
                                                    coroutineScope.launch { refreshList() }
                                                }
                                            } else {
                                                selectedFriends.clear()
                                                coroutineScope.launch { refreshList() }
                                            }
                                        }
                                    })
                                } else if (selectedGroups.isNotEmpty()) {
                                    removeAction(ctx, selectedGroups.toList().also { selectedGroups.clear() }, 10L to 40L) { conversationId, setDialogMessage ->
                                        cleanConversation(conversationId, setDialogMessage)
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                }
                            }
                            put({ translation.format("clear_all_feed", "count" to (selectedFriends.size + selectedGroups.size).toString()) }) {
                                val friendIds = selectedFriends.toList()
                                val groupIds = selectedGroups.toList()
                                fun finish() {
                                    selectedFriends.clear()
                                    selectedGroups.clear()
                                    coroutineScope.launch { refreshList() }
                                }
                                fetchConversations(friendIds, "failed_to_fetch_friend_conversations") { conversations ->
                                    val conversationIds = conversations.values.distinct()
                                    when {
                                        conversationIds.isNotEmpty() -> removeAction(ctx, conversationIds, 10L to 40L) { conversationId, setDialogMessage ->
                                            clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                                markRemoval(conversationId = conversationId)
                                                coroutineScope.launch { refreshList(clearSelected = false) }
                                            })
                                        }.invokeOnCompletion {
                                            if (groupIds.isNotEmpty()) {
                                                removeAction(ctx, groupIds, 10L to 40L) { conversationId, setDialogMessage ->
                                                    clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                                        markRemoval(conversationId = conversationId)
                                                        coroutineScope.launch { refreshList(clearSelected = false) }
                                                    })
                                                }.invokeOnCompletion { finish() }
                                            } else finish()
                                        }
                                        groupIds.isNotEmpty() -> removeAction(ctx, groupIds, 10L to 40L) { conversationId, setDialogMessage ->
                                            clearConversationFeed(conversationId, setDialogMessage, onSuccess = {
                                                markRemoval(conversationId = conversationId)
                                                coroutineScope.launch { refreshList(clearSelected = false) }
                                            })
                                        }.invokeOnCompletion { finish() }
                                        else -> finish()
                                    }
                                }
                            }
                            put({ translation.format("remove_friends", "count" to selectedFriends.size.toString()) }) {
                                val ids = selectedFriends.toList()
                                selectedFriends.clear()
                                fetchConversations(ids, "failed_to_fetch_friend_conversations") { conversations ->
                                    removeAction(ctx, ids, 500L to 1200L) { userId, setDialogMessage ->
                                        removeFriend(userId)
                                        conversations[userId]?.let { conversationId ->
                                        clearConversationFeed(conversationId, setDialogMessage, onFailure = {
                                            setDialogMessage(translation["actions.remove"])
                                        }, onSuccess = {
                                            markRemoval(userId, conversationId)
                                        })
                                        } ?: setDialogMessage(translation["actions.remove"])
                                    }.invokeOnCompletion { coroutineScope.launch { refreshList() } }
                                }
                            }
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BulkMessagingPalette.outline), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Choose an action",
                        style = MaterialTheme.typography.titleMedium,
                        color = BulkMessagingPalette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    var actionsMenuExpanded by remember { mutableStateOf(false) }
                    val actionsList = actions.toList()
                    Button(
                        onClick = { actionsMenuExpanded = true },
                        enabled = when (conversationType) {
                            ConversationType.FRIENDS_ONLY -> selectedFriends.isNotEmpty()
                            ConversationType.GROUPS_ONLY -> selectedGroups.isNotEmpty()
                            ConversationType.BOTH -> selectedFriends.isNotEmpty() || selectedGroups.isNotEmpty()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BulkMessagingPalette.glowPrimary,
                            contentColor = BulkMessagingPalette.textPrimary
                        )
                    ) {
                        Text(text = translation["actions.title"])
                    }
                    DropdownMenu(
                        expanded = actionsMenuExpanded,
                        onDismissRequest = { actionsMenuExpanded = false },
                        containerColor = BulkMessagingPalette.surface
                    ) {
                        actionsList.forEach { (textBuilder, actionFunction) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = remember(selectedFriends.size, selectedGroups.size) { textBuilder() },
                                        color = BulkMessagingPalette.textPrimary
                                    )
                                },
                                onClick = {
                                    actionsMenuExpanded = false
                                    showConfirmationDialog = true
                                    action = actionFunction
                                }
                            )
                        }
                    }
                }
            }

        }
        }
        }

        LaunchedEffect(sortBy, sortReverseOrder) {
            coroutineScope.launch {
                refreshList(clearSelected = false)
            }
            focusManager.clearFocus()
        }

        LaunchedEffect(filter) {
            coroutineScope.launch {
                if (filter == Filter.FOLLOWING) {
                    kotlinx.coroutines.delay(50)
                }
                refreshList()
            }
            focusManager.clearFocus()
        }

        LaunchedEffect(conversationType) {
            coroutineScope.launch {
                refreshList()
            }
            focusManager.clearFocus()
        }
    }

    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            createComposeAlertDialog(context.mainActivity!!) {
                BulkMessagingDialog()
            }.apply {
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }

    private fun removeFriend(userId: String) {
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance!!
            val runFriendDurableJobMethod = classReference.getAsClass()?.methods?.first {
                it.name == runFriendDurableJob.getAsString()
            } ?: throw Exception("Failed to find runFriendDurableJobMethod method")

            val removeFriendDurableJob = context.androidContext.classLoader.loadClass("com.snap.identity.job.snapchatter.RemoveFriendDurableJob")
                .constructors.firstOrNull {
                it.parameterTypes.size == 1
            }?.run {
                newInstance(
                    parameterTypes[0].dataBuilder {
                        set("a", userId)
                        set("b", "DELETED_BY_MY_FRIENDS")
                        set("f", "")
                    }
                )
            } ?: throw Exception("Failed to create RemoveFriendDurableJob instance")

            val completable = runFriendDurableJobMethod.invoke(null,
                friendRelationshipChangerInstance,
                userId,
                removeFriendDurableJob,
                0x5,
                "DELETED_BY_MY_FRIENDS",
            )!!
            completable::class.java.methods.first {
                it.name == "subscribe" && it.parameterTypes.isEmpty()
            }.invoke(completable)
        }
    }

    private fun unfollowUser(userId: String) {
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance!!
            val runFriendDurableJobMethod = classReference.getAsClass()?.methods?.first {
                it.name == runFriendDurableJob.getAsString()
            } ?: throw Exception("Failed to find runFriendDurableJobMethod method")

            val unfollowJobClass = runCatching {
                context.androidContext.classLoader.loadClass("com.snap.identity.job.snapchatter.UnfollowFriendDurableJob")
            }.getOrNull() ?: run {
                context.log.warn("Unfollow job unavailable, removing friend instead")
                removeFriend(userId)
                return@useMapper
            }

            val unfollowFriendDurableJob = unfollowJobClass.constructors.firstOrNull {
                it.parameterTypes.size == 1
            }?.run {
                newInstance(
                    parameterTypes[0].dataBuilder {
                        set("a", userId)
                    }
                )
            } ?: run {
                context.log.warn("Failed to create unfollow job instance, removing friend instead")
                removeFriend(userId)
                return@useMapper
            }

            val completable = runFriendDurableJobMethod.invoke(null,
                friendRelationshipChangerInstance,
                userId,
                unfollowFriendDurableJob,
                0x6,
                null,
            )!!
            completable::class.java.methods.first {
                it.name == "subscribe" && it.parameterTypes.isEmpty()
            }.invoke(completable)
        }
    }

    private fun acceptFriendRequest(userId: String) {
        val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance
            ?: run {
                context.log.error("Accept friend: FriendRelationshipChanger instance not available")
                return
            }
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            runCatching {
                val f9lClass = helperClass.getAsClass() ?: return@runCatching context.log.error("Could not find FriendRelationshipChanger helper class")
                val addFriendMethodName = addFriend14Method.get() ?: return@runCatching context.log.error("Could not find add friend method name")
                val sourceTypeClass = sourceType.getAsClass() ?: return@runCatching context.log.error("Could not find source type class")
                val pageTypeClass = pageType.getAsClass() ?: return@runCatching context.log.error("Could not find page type class")
                val method = f9lClass.methods.firstOrNull { it.name == addFriendMethodName }
                    ?: f9lClass.declaredMethods.firstOrNull { it.name == addFriendMethodName }
                    ?: return@runCatching context.log.error("Could not find $addFriendMethodName method")
                fun findStaticField(clazz: Class<*>): Any? = clazz.findStaticObjectFieldByType(clazz)
                val enumClass = method.parameterTypes[2]
                val enumConstants = enumClass.enumConstants ?: enumClass.getMethod("values").invoke(null) as? Array<*>
                    ?: return@runCatching context.log.error("Could not get enum constants")
                val addedByUsername = enumConstants.firstOrNull { it.toString().contains("USERNAME", ignoreCase = true) }
                    ?: return@runCatching context.log.error("Could not find ADDED_BY_USERNAME enum")
                val sourceTypeDefault = findStaticField(sourceTypeClass) ?: return@runCatching context.log.error("Could not find source type static field")
                val pageTypeDefault = findStaticField(pageTypeClass) ?: return@runCatching context.log.error("Could not find page type static field")
                method.isAccessible = true
                val paramCount = method.parameterTypes.size
                val args = arrayOfNulls<Any?>(paramCount).apply {
                    if (paramCount >= 1) set(0, friendRelationshipChangerInstance)
                    if (paramCount >= 2) set(1, userId)
                    if (paramCount >= 3) set(2, addedByUsername)
                    if (paramCount >= 4) set(3, sourceTypeDefault)
                    if (paramCount >= 5) set(4, pageTypeDefault)
                    for (i in 5 until paramCount - 1) set(i, null)
                    if (paramCount >= 14) set(13, 4064)
                    else if (paramCount >= 13) set(paramCount - 1, 4064)
                }
                val result = method.invoke(null, *args)
                result?.javaClass?.methods?.firstOrNull { it.name == "subscribe" && it.parameterCount == 0 }?.let { it.isAccessible = true; it.invoke(result) }
            }.onFailure {
                context.log.error("Failed to accept friend request $userId", it)
            }
        }
    }

    private fun ignoreFriendRequest(userId: String) {
        context.database.setIncomingRequestIgnored(userId)
    }

    private suspend fun cleanConversation(
        conversationId: String,
        setDialogMessage: (String) -> Unit
    ) {
        val messageCount = mutableIntStateOf(0)
        MessagingTask(
            context.messagingBridge,
            conversationId,
            taskType = MessagingTaskType.DELETE,
            constraints = listOf(MessagingConstraints.MY_USER_ID(context.messagingBridge), {
                contentType != ContentType.STATUS.id
            }),
            processedMessageCount = messageCount,
            onSuccess = {
                setDialogMessage(translation.format("deleted_messages", "count" to messageCount.intValue.toString()))
            },
        ).run()
    }

    private suspend fun clearConversationFeed(
        conversationId: String,
        setDialogMessage: (String) -> Unit,
        onFailure: (() -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) = suspendCancellableCoroutine { continuation ->
        fun resume() {
            if (!continuation.isCompleted) continuation.resume(Unit)
        }
        runCatching {
            context.feature(Messaging::class).clearConversationFromFeed(conversationId, onError = {
                context.log.error("Failed to clear feed $conversationId: $it")
                onFailure?.invoke()
                resume()
            }, onSuccess = {
                setDialogMessage(translation["cleared_from_feed"])
                onSuccess?.invoke()
                resume()
            })
        }.onFailure {
            context.log.error("Failed to clear feed $conversationId", it)
            onFailure?.invoke()
            resume()
        }
    }
}

