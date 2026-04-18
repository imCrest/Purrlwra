package cock.crest.purrfectsnap.lite.core.action.impl

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import cock.crest.purrfectsnap.lite.common.data.FriendLinkType
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.core.action.AbstractAction
import cock.crest.purrfectsnap.lite.core.event.events.impl.ActivityResultEvent
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.AddFriendSourceSpoof
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.ktx.findStaticObjectFieldByType
import cock.crest.purrfectsnap.lite.core.util.EvictingMap
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Snapchatter
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.common.util.snap.RemoteMediaResolver
import cock.crest.purrfectsnap.lite.mapper.impl.FriendRelationshipChangerMapper
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.*

class ManageFriendList : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("friend_list") }
    private val dialogBackground = Brush.verticalGradient(
        listOf(
            Color(0xFF1D1538),
            Color(0xFF130F2A)
        )
    )
    private val panelOverlay = Brush.linearGradient(
        listOf(
            Color(0xFF2C2551),
            Color(0xFF1B1636)
        )
    )
    private val accentGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF8C7BFF),
            Color(0xFF5FD8FF)
        )
    )
    private var pendingPickerAction: Pair<Int, (data: Uri) -> Unit>? = null
    private val uuidRegex = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    
    private fun getUserIdBlacklist() = arrayOf(
        context.database.myUserId,
        "b42f1f70-5a8b-4c53-8c25-34e7ec9e6781",
        "84ee8839-3911-492d-8b94-72dd80f3713a",
    )

    private fun addFriend(userId: String) {
        val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance
            ?: run {
                context.longToast(context.translation["toast_friend_add_unavailable"])
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

                // Helper function to find static field by trying fallback
                fun findStaticField(clazz: Class<*>): Any? {
                    return clazz.findStaticObjectFieldByType(clazz)
                }

                // Get enum constant for USERNAME
                val enumClass = method.parameterTypes[2]
                val enumConstants = enumClass.enumConstants ?: enumClass.getMethod("values").invoke(null) as? Array<*>
                    ?: return@runCatching context.log.error("Could not get enum constants")
                val addedByUsername = enumConstants.firstOrNull { it.toString().contains("USERNAME", ignoreCase = true) }
                    ?: return@runCatching context.log.error("Could not find ADDED_BY_USERNAME enum")

                // Get static field instances
                val sourceTypeDefault = findStaticField(sourceTypeClass)
                    ?: return@runCatching context.log.error("Could not find source type static field")

                val pageTypeDefault = findStaticField(pageTypeClass)
                    ?: return@runCatching context.log.error("Could not find page type static field")

                // Invoke method with 14 parameters
                method.isAccessible = true
                val result = method.invoke(
                    null,
                    friendRelationshipChangerInstance,
                    userId,
                    addedByUsername,
                    sourceTypeDefault,
                    pageTypeDefault,
                    null, null, null, null, null, // String params 6-10
                    null, // InteractionPlacementInfo
                    null, // String str7
                    null, // Integer num
                    4064 // int flags
                )

                // Subscribe to the Completable result
                result?.javaClass?.methods?.firstOrNull {
                    it.name == "subscribe" && it.parameterCount == 0
                }?.let { subscribeMethod ->
                    subscribeMethod.isAccessible = true
                    subscribeMethod.invoke(result)
                }
            }.onFailure {
                context.log.error("Failed to add friend $userId", it)
                context.longToast(
                    context.translation.format("toast_friend_add_failed", "message" to (it.message ?: ""))
                )
            }
        }
    }

    override fun onActivityCreate() {
        context.event.subscribe(ActivityResultEvent::class) { event ->
            pendingPickerAction?.takeIf { it.first == event.requestCode }?.let {
                pendingPickerAction = null
                event.canceled = true
                it.second(event.intent.data!!)
            }
        }
    }

    private fun exportFriends(userIds: List<String>) {
        pendingPickerAction = Random.nextInt(0, 65535) to { data ->
            context.androidContext.contentResolver.openOutputStream(data)?.bufferedWriter()?.use { writer ->
                userIds.forEach { writer.write(it); writer.newLine() }
            }
            context.longToast(
                context.translation.format("toast_friends_exported", "count" to userIds.size.toString())
            )
        }
        context.mainActivity?.startActivityForResult(
            Intent.createChooser(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "my_friends.txt")
            }, "Select a location to save the file"),
            pendingPickerAction!!.first
        )
    }

    private val userIdToSnapchatter = mutableMapOf<String, Snapchatter>()

    @Composable
    private fun ManagerDialog() {
        val pendingFriendRequests = remember { mutableStateMapOf<String, Job>() }
        var fetchedFriends by remember { mutableStateOf<List<String>?>(null) }
        val coroutineScope = rememberCoroutineScope()
        val bitmojiCache = remember { EvictingMap<String, Bitmap>(50) }
        val noBitmojiBitmap = remember { BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_report_image).asImageBitmap() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(dialogBackground)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 44.dp, y = (-36).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8C7BFF).copy(alpha = 0.32f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-60).dp, y = 28.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF5FD8FF).copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .heightIn(min = 260.dp)
                    .border(1.2.dp, accentGradient, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
                color = Color.White.copy(alpha = 0.04f)
            ) {
                if (fetchedFriends == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panelOverlay)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    translation.get("manage_title"),
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = translation.get("export_description"),
                                    color = Color(0xFFD9D3FF),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrimaryButton(
                                text = translation.get("export_friends"),
                                modifier = Modifier.weight(1f)
                            ) {
                                exportFriends(context.database.getAllFriends().filter { it.friendLinkType == FriendLinkType.MUTUAL.value && it.addedTimestamp > 0L }.mapNotNull { it.userId })
                            }
                            SecondaryButton(
                                text = translation.get("import_from_file"),
                                modifier = Modifier.weight(1f)
                            ) {
                                pendingPickerAction = Random.nextInt(0, 65535) to { data ->
                                    runCatching {
                                        fetchedFriends = context.androidContext.contentResolver.openInputStream(data)?.bufferedReader()?.readLines()?.filter { it.matches(uuidRegex) }?.map { it.trim() }?.toMutableList() ?: mutableListOf()
                                    }.onFailure {
                                        context.log.error("Failed to import friends", it)
                                        context.longToast(
                                            context.translation.format(
                                                "toast_friends_import_failed",
                                                "message" to (it.message ?: "")
                                            )
                                        )
                                    }
                                }
                                context.mainActivity?.startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }, "Select a file"), pendingPickerAction!!.first)
                            }
                        }
                        PrimaryButton(
                            text = translation.get("load_suggested_friends"),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            coroutineScope.launch(Dispatchers.IO) {
                                val blacklist = getUserIdBlacklist()
                                val suggestedFriends = context.database.getAllFriends().filter { it.userId !in blacklist && it.friendLinkType == FriendLinkType.SUGGESTED.value }.sortedByDescending { it.addedTimestamp }.mapNotNull { it.userId }
                                withContext(Dispatchers.Main) { fetchedFriends = suggestedFriends }
                            }
                        }
                    }
                } else {
                    var searchQuery by remember { mutableStateOf("") }

                    val filteredFriends = remember(fetchedFriends, searchQuery) {
                        val friends = fetchedFriends ?: emptyList()
                        val sorted = { list: List<String> -> list.sortedByDescending { context.database.getFriendInfo(it)?.addedTimestamp ?: 0L } }
                        if (searchQuery.isBlank()) sorted(friends) else sorted(friends.filter { userId ->
                            val info = context.database.getFriendInfo(userId)
                            info?.mutableUsername?.contains(searchQuery, ignoreCase = true) == true || info?.displayName?.contains(searchQuery, ignoreCase = true) == true || userId.contains(searchQuery, ignoreCase = true)
                        })
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp)
                            .background(panelOverlay)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                    .clickable { fetchedFriends = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = context.translation["common.back"],
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = translation.get("manage_title"),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.size(46.dp))
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF8EF0F3)),
                            decorationBox = { innerTextField ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFFB1B4D7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Box(Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            BasicText(
                                                "Search...",
                                                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFB1B4D7), fontSize = 14.sp)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 0.dp,
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                item {
                                    if (filteredFriends.isEmpty()) {
                                        BasicText(
                                            context.translation["common.no_friends_found"],
                                            style = androidx.compose.ui.text.TextStyle(color = Color(0xFFA8B5D1), fontSize = 13.sp),
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                items(filteredFriends) { userId ->
                                    var friendInfo by remember(userId) { mutableStateOf(context.database.getFriendInfo(userId)) }
                                    var friendLinkType by remember(userId) { mutableStateOf(friendInfo?.let { FriendLinkType.fromValue(it.friendLinkType) }) }

                                    val isActuallyAdded = friendInfo?.let { info ->
                                        friendLinkType != null && info.addedTimestamp > 0 &&
                                            (friendLinkType == FriendLinkType.MUTUAL || friendLinkType == FriendLinkType.OUTGOING)
                                    } ?: false

                                    var friendSnapchatter by remember(userId) { mutableStateOf<Snapchatter?>(null) }

                                    LaunchedEffect(userId) {
                                        friendSnapchatter = userIdToSnapchatter[userId] ?: run {
                                            withContext(Dispatchers.IO) {
                                                context.feature(Messaging::class).fetchSnapchatterInfos(listOf(userId)).firstOrNull()?.also {
                                                    userIdToSnapchatter[userId] = it
                                                }
                                            }
                                        }
                                    }

                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            delay(2000)
                                            context.database.getFriendInfo(userId)?.let {
                                                friendInfo = it
                                                FriendLinkType.fromValue(it.friendLinkType)?.let { newType ->
                                                    if (newType != friendLinkType) friendLinkType = newType
                                                }
                                            }
                                        }
                                    }

                                    var bitmojiBitmap by remember(userId, friendInfo?.bitmojiAvatarId) {
                                        mutableStateOf(friendInfo?.bitmojiAvatarId?.let { bitmojiCache[it] })
                                    }

                                    LaunchedEffect(userId, friendInfo?.bitmojiAvatarId, friendInfo?.bitmojiSelfieId) {
                                        val info = friendInfo ?: return@LaunchedEffect
                                        val avatarId = info.bitmojiAvatarId ?: return@LaunchedEffect
                                        val selfieId = info.bitmojiSelfieId ?: return@LaunchedEffect
                                        if (bitmojiBitmap != null) return@LaunchedEffect
                                        BitmojiSelfie.getBitmojiSelfie(selfieId, avatarId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D)?.let { url ->
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    RemoteMediaResolver.downloadMedia(url) { inputStream, _ ->
                                                        bitmojiCache[avatarId] = BitmapFactory.decodeStream(inputStream).also { bitmojiBitmap = it }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 6.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(1.dp, accentGradient, RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Image(
                                            bitmap = remember(bitmojiBitmap) { bitmojiBitmap?.asImageBitmap() ?: noBitmojiBitmap },
                                            contentDescription = null,
                                            modifier = Modifier.size(35.dp)
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = friendSnapchatter?.let { snapchatter ->
                                                    snapchatter.displayName?.let { "$it (${snapchatter.username})" }
                                                        ?: snapchatter.username
                                                        ?: context.translation["common.unknown"]
                                                } ?: context.translation["common.unknown"],
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                friendLinkType?.let { type ->
                                                    StatusPill(
                                                        text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                                        color = if (type == FriendLinkType.MUTUAL) Color(0xFF8EF0F3) else Color(0xFFD9D3FF)
                                                    )
                                                }
                                                friendSnapchatter?.let {
                                                    val isFollowing = friendLinkType == FriendLinkType.FOLLOWING
                                                    val isPending = pendingFriendRequests[userId]?.isActive == true
                                                    val canAdd = !isActuallyAdded && !isPending && !isFollowing

                                                    PrimaryButton(
                                                        text = when {
                                                            isFollowing -> "Following"
                                                            isActuallyAdded -> context.translation["common.added"]
                                                            isPending -> translation.get("adding")
                                                            else -> translation.get("add")
                                                        },
                                                        modifier = Modifier.widthIn(min = 110.dp),
                                                        enabled = canAdd
                                                    ) {
                                                        if (!canAdd) return@PrimaryButton
                                                        val prevLinkType = friendLinkType
                                                        addFriend(userId)
                                                        pendingFriendRequests[userId] = coroutineScope.launch(Dispatchers.IO) {
                                                            withTimeout(10000) {
                                                                while (true) {
                                                                    context.database.getFriendInfo(userId)?.let { updated ->
                                                                        FriendLinkType.fromValue(updated.friendLinkType)?.takeIf { it != prevLinkType }?.let {
                                                                            friendInfo = updated
                                                                            friendLinkType = it
                                                                            return@withTimeout
                                                                        }
                                                                    }
                                                                    delay(500)
                                                                }
                                                            }
                                                        }.apply { invokeOnCompletion { pendingFriendRequests.remove(userId) } }
                                                    }
                                                    if (isPending) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color(0xFF8EF0F3)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PrimaryButton(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .alpha(if (enabled) 1f else 0.6f)
                .background(accentGradient)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(color = Color(0xFF0A0F1D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }

    @Composable
    private fun SecondaryButton(
        text: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFE6ECFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }

    @Composable
    private fun StatusPill(text: String, color: Color) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            createComposeAlertDialog(context.mainActivity!!) {
                ManagerDialog()
            }.apply {
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }
}
