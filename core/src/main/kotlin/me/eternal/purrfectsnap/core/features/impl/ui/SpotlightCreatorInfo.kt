package cock.crest.purrfectsnap.lite.core.features.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.data.FriendLinkType
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.common.util.ktx.copyToClipboard
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Snapchatter
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.Layer
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.ParamMap
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper
import java.text.SimpleDateFormat
import java.util.*

class SpotlightCreatorInfo : Feature("SpotlightCreatorInfo") {
    private data class CreatorInfo(
        val snapId: String,
        val creatorDisplayName: String,
        val creatorUserId: String?,
        val timestamp: Long?,
        val friendLinkType: FriendLinkType? = null,
    )

    @Volatile
    private var currentCreatorInfo: CreatorInfo? = null

    @Volatile
    private var isInSpotlight: Boolean = false

    @Volatile
    private var currentSnapId: String? = null

    @Volatile
    private var lastSpotlightUpdateTime: Long = 0

    private val userIdCache = mutableMapOf<String, String>()

    override fun init() {
        if (!context.config.global.spotlightCreatorInfo.get()) return

        onNextActivityCreate {
            context.event.subscribe(BindViewEvent::class) { event ->
                val modelStr = event.prevModel.toString()
                if (!modelStr.contains("Spotlight") && !modelStr.contains("SINGLE_SNAP_STORY")) return@subscribe

                val userId = modelStr.takeIf { it.contains("userId=") }
                    ?.substringAfter("userId=")
                    ?.substringBefore(",")
                    ?.substringBefore(")")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                val snapId = modelStr.takeIf { it.contains("snapId=") || modelStr.contains("SNAP_ID") }
                    ?.substringAfter("snapId=")
                    ?.substringBefore(",")
                    ?.substringBefore(")")
                    ?: modelStr.takeIf { it.contains("SNAP_ID") }
                        ?.substringAfter("SNAP_ID=")
                        ?.substringBefore(",")
                        ?.substringBefore(")")

                if (userId != null && snapId != null) {
                    userIdCache[snapId] = userId
                }
            }

            context.mappings.useMapper(OperaPageViewControllerMapper::class) {
                arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                    classReference.get()?.hook(
                        methodName.get() ?: return@forEach,
                        HookStage.AFTER
                    ) onCreatorInfoHook@{ param ->
                        val viewState = (param.thisObject<Any>()).getObjectField(viewStateField.get()!!).toString()
                        val operaLayerList = (param.thisObject<Any>()).getObjectField(layerListField.get()!!) as ArrayList<*>

                        if (operaLayerList.isEmpty()) {
                            if (isInSpotlight) {
                                isInSpotlight = false
                                currentCreatorInfo = null
                                currentSnapId = null
                                lastSpotlightUpdateTime = 0
                            }
                            return@onCreatorInfoHook
                        }

                        val mediaParamMap: ParamMap = operaLayerList.map { Layer(it) }.first().paramMap
                        val snapSource = mediaParamMap["SNAP_SOURCE"]?.toString()

                        if (snapSource != "SINGLE_SNAP_STORY") {
                            if (isInSpotlight) {
                                isInSpotlight = false
                                currentCreatorInfo = null
                                currentSnapId = null
                                lastSpotlightUpdateTime = 0
                            }
                            return@onCreatorInfoHook
                        }

                        if (viewState != "FULLY_DISPLAYED") return@onCreatorInfoHook

                        val snapId = mediaParamMap["SNAP_ID"]?.toString()
                        if (snapId == null || snapId == currentSnapId) return@onCreatorInfoHook

                        val creatorDisplayName = mediaParamMap["CREATOR_DISPLAY_NAME"]?.toString()
                        var creatorUserId = mediaParamMap["USER_ID"]?.toString()
                            ?: mediaParamMap["CREATOR_USER_ID"]?.toString()
                            ?: mediaParamMap["TOPIC_SNAP_CREATOR_USER_ID"]?.toString()

                        val playableStorySnapRecord = mediaParamMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
                        if (creatorUserId == null && playableStorySnapRecord != null) {
                            val recordStr = playableStorySnapRecord.toString()
                            creatorUserId = recordStr.substringAfter("userId=", "")
                                .substringBefore(",")
                                .substringBefore(")")
                                .takeIf { it.isNotBlank() && it != "null" }
                        }
                        if (creatorUserId == null) creatorUserId = userIdCache[snapId]

                        val creationTimestamp = playableStorySnapRecord?.substringAfter("creationTimestamp=")
                            ?.substringBefore(",")?.toLongOrNull()
                        val timestamp = creationTimestamp ?: mediaParamMap["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull()

                        if (creatorDisplayName != null || creatorUserId != null) {
                            val friendInfo: FriendInfo? = creatorUserId?.let { context.database.getFriendInfo(it) }
                            val linkType = friendInfo?.let { FriendLinkType.fromValue(it.friendLinkType) }

                            currentSnapId = snapId
                            isInSpotlight = true
                            lastSpotlightUpdateTime = System.currentTimeMillis()
                            currentCreatorInfo = CreatorInfo(
                                snapId = snapId,
                                creatorDisplayName = creatorDisplayName ?: context.translation.getOrNull("common.unknown") ?: "Unknown",
                                creatorUserId = creatorUserId,
                                timestamp = timestamp,
                                friendLinkType = linkType
                            )
                        } else {
                            if (isInSpotlight) {
                                isInSpotlight = false
                                currentCreatorInfo = null
                                currentSnapId = null
                                lastSpotlightUpdateTime = 0
                            }
                        }
                    }
                }
            }

            context.inAppOverlay.addCustomComposable {
                var info by remember { mutableStateOf<CreatorInfo?>(null) }
                var inSpotlight by remember { mutableStateOf(false) }
                var showDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    while (true) {
                        val wasInSpotlight = inSpotlight
                        info = currentCreatorInfo
                        inSpotlight = isInSpotlight
                        if (wasInSpotlight && !inSpotlight) showDialog = false
                        if (inSpotlight && info != null && lastSpotlightUpdateTime > 0 && !showDialog) {
                            if (System.currentTimeMillis() - lastSpotlightUpdateTime > 4000) {
                                inSpotlight = false
                                info = null
                                showDialog = false
                            }
                        }
                        kotlinx.coroutines.delay(100)
                    }
                }

                if (inSpotlight && info != null) {
                    InfoIconOverlay(
                        onClick = {
                            showDialog = true
                            lastSpotlightUpdateTime = System.currentTimeMillis()
                        }
                    )
                }

                if (showDialog && info != null) {
                    CreatorInfoDialog(
                        creatorInfo = info!!,
                        onDismiss = { showDialog = false }
                    )
                }
            }
        }
    }

    @Composable
    private fun InfoIconOverlay(onClick: () -> Unit) {
        val translation = remember { context.translation.getCategory("spotlight_creator_info") }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.4f),
                                    PurrfectOverlayPalette.cardOverlayColor
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = translation.getOrNull("creator_info") ?: "Creator Info",
                        tint = PurrfectOverlayPalette.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun CreatorInfoDialog(creatorInfo: CreatorInfo, onDismiss: () -> Unit) {
        var snapchatterInfo by remember { mutableStateOf<Snapchatter?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        val translation = remember { context.translation.getCategory("spotlight_creator_info") }

        LaunchedEffect(creatorInfo.snapId) {
            snapchatterInfo = null
            isLoading = false
            if (creatorInfo.creatorUserId != null) {
                isLoading = true
                withContext(Dispatchers.IO) {
                    val info = runCatching {
                        context.feature(Messaging::class).fetchSnapchatterInfos(listOf(creatorInfo.creatorUserId!!)).firstOrNull()
                    }.onFailure {
                        context.log.error("Failed to fetch creator info for ${creatorInfo.creatorUserId}", it)
                    }.getOrNull()
                    withContext(Dispatchers.Main) {
                        snapchatterInfo = info
                        isLoading = false
                    }
                }
            }
        }

        Dialog(onDismissRequest = onDismiss) {
            PurrfectOverlayTheme {
                val shape = RoundedCornerShape(20.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = shape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(PurrfectOverlayPalette.cardOverlay, shape)
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = translation.getOrNull("title") ?: "Creator Info",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurrfectOverlayPalette.textPrimary
                                )
                                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = translation.getOrNull("close") ?: "Close",
                                        modifier = Modifier.size(20.dp),
                                        tint = PurrfectOverlayPalette.textSecondary
                                    )
                                }
                            }

                            HorizontalDivider(color = PurrfectOverlayPalette.textSecondary.copy(alpha = 0.2f))

                            creatorInfo.timestamp?.let { ts ->
                                InfoRow(
                                    icon = Icons.Default.Schedule,
                                    label = translation.getOrNull("posted_on") ?: "Posted",
                                    value = formatDate(ts)
                                )
                            }

                            val displayName = snapchatterInfo?.displayName ?: creatorInfo.creatorDisplayName
                            InfoRow(
                                icon = Icons.Default.Person,
                                label = translation.getOrNull("display_name") ?: "Display name",
                                value = displayName
                            )

                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = PurrfectOverlayPalette.glowPrimary
                                    )
                                    Text(
                                        translation.getOrNull("loading_username") ?: "Loading…",
                                        fontSize = 13.sp,
                                        color = PurrfectOverlayPalette.textSecondary
                                    )
                                }
                            } else {
                                val username = snapchatterInfo?.username
                                InfoRowWithCopy(
                                    icon = Icons.Default.AccountCircle,
                                    label = translation.getOrNull("username") ?: "Username",
                                    value = username ?: (context.translation.getOrNull("common.unknown") ?: "—"),
                                    canCopy = username != null,
                                    onCopy = {
                                        username?.let {
                                            context.androidContext.copyToClipboard(it, translation.getOrNull("username") ?: "Username")
                                            context.inAppOverlay.showStatusToast(Icons.Default.Check, translation.getOrNull("username_copied") ?: "Copied")
                                        }
                                    }
                                )
                            }

                            InfoRowWithCopy(
                                icon = Icons.Default.Fingerprint,
                                label = translation.getOrNull("user_id") ?: "User ID",
                                value = creatorInfo.creatorUserId ?: (context.translation.getOrNull("common.unknown") ?: "—"),
                                canCopy = creatorInfo.creatorUserId != null,
                                onCopy = {
                                    creatorInfo.creatorUserId?.let {
                                        context.androidContext.copyToClipboard(it, translation.getOrNull("user_id") ?: "User ID")
                                        context.inAppOverlay.showStatusToast(Icons.Default.Check, translation.getOrNull("user_id_copied") ?: "Copied")
                                    }
                                }
                            )

                            creatorInfo.friendLinkType?.let { linkType ->
                                val statusText = when (linkType) {
                                    FriendLinkType.MUTUAL -> translation.getOrNull("mutual_friend") ?: "Mutual friend"
                                    FriendLinkType.FOLLOWING -> translation.getOrNull("following") ?: "Following"
                                    FriendLinkType.OUTGOING -> translation.getOrNull("friend_request_sent") ?: "Request sent"
                                    FriendLinkType.INCOMING -> translation.getOrNull("friend_request_received") ?: "Request received"
                                    FriendLinkType.BLOCKED -> translation.getOrNull("blocked") ?: "Blocked"
                                    FriendLinkType.DELETED -> translation.getOrNull("friend_removed") ?: "Removed"
                                    else -> null
                                }
                                if (statusText != null) {
                                    InfoRow(
                                        icon = Icons.Default.Person,
                                        label = translation.getOrNull("friend_status") ?: "Friend status",
                                        value = statusText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoRow(
        icon: ImageVector,
        label: String,
        value: String
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.9f)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = PurrfectOverlayPalette.textSecondary
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurrfectOverlayPalette.textPrimary
                )
            }
        }
    }

    @Composable
    private fun InfoRowWithCopy(
        icon: ImageVector,
        label: String,
        value: String,
        canCopy: Boolean,
        onCopy: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.9f)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = PurrfectOverlayPalette.textSecondary
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurrfectOverlayPalette.textPrimary
                )
            }
            if (canCopy) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = PurrfectOverlayPalette.glowSecondary
                    )
                }
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val ts = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}
