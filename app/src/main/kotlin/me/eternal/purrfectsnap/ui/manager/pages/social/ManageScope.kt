package cock.crest.purrfectsnap.lite.ui.manager.pages.social

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.FriendStreaks
import cock.crest.purrfectsnap.lite.common.data.MessagingFriendInfo
import cock.crest.purrfectsnap.lite.common.data.MessagingGroupInfo
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.SocialScope
import cock.crest.purrfectsnap.lite.common.data.normalizeStealthRules
import cock.crest.purrfectsnap.lite.common.data.withNormalizedRuleToggle
import cock.crest.purrfectsnap.lite.common.ui.AutoClearKeyboardFocus
import cock.crest.purrfectsnap.lite.common.ui.EditNoteTextField
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie
import cock.crest.purrfectsnap.lite.storage.*
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.AlertDialogs
import cock.crest.purrfectsnap.lite.ui.util.Dialog
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.coil.BitmojiImage
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ManageScope: Routes.Route() {
    private val dialogs by lazy { AlertDialogs(context.translation) }

    private fun deleteScope(scope: SocialScope, id: String, coroutineScope: CoroutineScope) {
        when (scope) {
            SocialScope.FRIEND -> context.database.deleteFriend(id)
            SocialScope.GROUP -> context.database.deleteGroup(id)
        }
        context.database.executeAsync {
            coroutineScope.launch {
                routes.navController.popBackStack()
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = content@{ navBackStackEntry ->
        val scope = SocialScope.getByName(navBackStackEntry.arguments?.getString("scope")!!)
        val id = navBackStackEntry.arguments?.getString("id")!!
        val density = LocalDensity.current
        var topBarHeight by remember { mutableStateOf(96.dp) }
        var deleteConfirmDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        val titleText by rememberAsyncMutableState<String?>(null, keys = arrayOf<Any>(id, scope)) {
            when (scope) {
                SocialScope.FRIEND -> context.database.getFriendInfo(id)?.displayName
                SocialScope.GROUP -> context.database.getGroupInfo(id)?.name
            }
        }

        if (deleteConfirmDialog) {
            AestheticDialog(
                onDismissRequest = { deleteConfirmDialog = false },
                title = translation.format("delete_scope_confirm_dialog_title", "scope" to context.translation["scopes.${scope.key}"]),
                text = "",
                icon = Icons.Rounded.DeleteForever,
                confirmButtonText = translation["delete_button"],
                dismissButtonText = context.translation["button.cancel"],
                onDismiss = { deleteConfirmDialog = false },
                onConfirm = {
                    deleteScope(scope, id, coroutineScope)
                    deleteConfirmDialog = false
                },
                opaque = true,
                showCloseButton = false
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            FloatingTopBar(
                title = titleText ?: translation["manage_scope_title"],
                onBack = { routes.navController.popBackStack() },
                actions = {
                    IconButton(onClick = { deleteConfirmDialog = true }) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color.White)
                    }
                },
                modifier = Modifier
                    .zIndex(2f)
                    .onGloballyPositioned {
                        val newHeight = with(density) { it.size.height.toDp() }
                        if (newHeight != topBarHeight) topBarHeight = newHeight
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight + 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
            var bottomComposable by remember {
                mutableStateOf(null as (@Composable () -> Unit)?)
            }
            var hasScope by remember {
                mutableStateOf(null as Boolean?)
            }
            when (scope) {
                SocialScope.FRIEND -> {
                    var streaks by remember { mutableStateOf(null as FriendStreaks?) }
                    val friend by rememberAsyncMutableState(null) {
                        context.database.getFriendInfo(id)?.also {
                            streaks = context.database.getFriendStreaks(id)
                        }.also {
                            hasScope = it != null
                        }
                    }
                    friend?.let {
                        Friend(id, it, streaks) { bottomComposable = it }
                    }
                }
                SocialScope.GROUP -> {
                    val group by rememberAsyncMutableState(null) {
                        context.database.getGroupInfo(id).also {
                            hasScope = it != null
                        }
                    }
                    group?.let {
                        Group(it) { bottomComposable = it }
                    }
                }
            }
            if (hasScope == true) {
                if (context.config.root.experimental.friendNotes.get()) {
                    NotesCard(id)
                }
                RulesCard(id)
            }
            bottomComposable?.invoke()
            if (hasScope == false) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = translation["not_found"],
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(routes.bottomPadding))
            }
        }
    }

    @Composable
    private fun NotesCard(
        id: String
    ) {
        val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
        var scopeNotes by rememberAsyncMutableState(null) {
            context.database.getScopeNotes(id)
        }

        AutoClearKeyboardFocus()

        EditNoteTextField(
            modifier = Modifier.padding(8.dp),
            primaryColor = Color.White,
            placeholder = context.translation["manager.sections.manage_scope.notes_placeholder"],
            content = scopeNotes,
            setContent = { scopeNotes = it }
        )

        DisposableEffect(Unit) {
            onDispose {
                coroutineScope.launch {
                    context.database.setScopeNotes(id, scopeNotes)
                }
            }
        }
    }

    @Composable
    private fun RulesCard(
        id: String
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        val rules = rememberAsyncMutableStateList(listOf()) {
            context.database.getRules(id).normalizeStealthRules().toList()
        }

        fun updateRules(ruleType: MessagingRuleType, enabled: Boolean) {
            val previousRules = rules.toSet()
            val updatedRules = previousRules.withNormalizedRuleToggle(ruleType, enabled)
            val changedRules = (previousRules + updatedRules).filter { rule ->
                (rule in previousRules) != (rule in updatedRules)
            }

            rules.clear()
            rules.addAll(updatedRules)

            changedRules.forEach { changedRule ->
                context.database.setRule(id, changedRule.key, changedRule in updatedRules)
            }
        }

        SectionTitle(translation["rules_title"])

        ContentCard {
            MessagingRuleType.entries.forEach { ruleType ->
                val ruleState = context.config.root.rules.getRuleState(ruleType)
                val ruleEnabled = rules.any { it.key == ruleType.key }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(all = 4.dp)
                ) {
                    Text(
                        text = if (ruleType.listMode && ruleState != null) {
                            context.translation["rules.properties.${ruleType.key}.options.${ruleState.key}"]
                        } else context.translation["rules.properties.${ruleType.key}.name"],
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 5.dp, end = 5.dp),
                        color = Color.White
                    )
                    Switch(
                        checked = ruleEnabled,
                        enabled = if (ruleType.listMode) ruleState != null else true,
                        onCheckedChange = {
                            updateRules(ruleType, it)
                        },
                        colors = purrfectSwitchColors()
                    )
                }
            }
        }
    }

    @Composable
    private fun ContentCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .then(modifier),
            shape = shape,
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.3f)
                    )
                )
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(
                    modifier = Modifier
                        .background(PurrfectPalette.cardOverlay, shape)
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            maxLines = 1,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 18.dp, top = 10.dp, bottom = 6.dp),
            color = Color.White
        )
    }

    @Composable
    private fun RowScope.E2eeActionButton(
        label: String,
        icon: ImageVector,
        accent: Brush,
        onClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier
                .weight(1f)
                .scaleOnPress(interactionSource)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    private fun computeStreakETA(timestamp: Long): String? {
        val now = System.currentTimeMillis()
        val stringBuilder = StringBuilder()
        val diff = timestamp - now
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        if (days > 0) {
            stringBuilder.append("$days day ")
            return stringBuilder.toString()
        }
        if (hours > 0) {
            stringBuilder.append("$hours hours ")
            return stringBuilder.toString()
        }
        if (minutes > 0) {
            stringBuilder.append("$minutes minutes ")
            return stringBuilder.toString()
        }
        if (seconds > 0) {
            stringBuilder.append("$seconds seconds ")
            return stringBuilder.toString()
        }
        return null
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Composable
    private fun Friend(
        id: String,
        friend: MessagingFriendInfo,
        streaks: FriendStreaks?,
        setBottomComposable: ((@Composable () -> Unit)?) -> Unit = {}
    ) {
        LaunchedEffect(Unit) {
            setBottomComposable {
                Spacer(modifier = Modifier.height(16.dp))

                if (context.config.root.experimental.e2eEncryption.globalState == true) {
                    var hasSecretKey by rememberAsyncMutableState(defaultValue = false) {
                        context.e2eeImplementation.friendKeyExists(friend.userId)
                    }
                    var importDialog by remember { mutableStateOf(false) }

                    if (importDialog) {
                        Dialog(
                            onDismissRequest = { importDialog = false }
                        ) {
                            dialogs.RawInputDialog(onDismiss = { importDialog = false  }, onConfirm = { newKey ->
                                importDialog = false
                                runCatching {
                                    val key = Base64.decode(newKey)
                                    if (key.size != 32) {
                                        context.longToast(translation["invalid_key_size_32_bytes"])
                                        return@runCatching
                                    }

                                    context.coroutineScope.launch {
                                        context.e2eeImplementation.storeSharedSecretKey(friend.userId, key)
                                        context.longToast(translation["successfully_imported_key"])
                                    }

                                    hasSecretKey = true
                                }.onFailure {
                                    context.longToast(translation.format("failed_to_import_key", "message" to (it.message ?: "")))
                                    context.log.error("Failed to import key", it)
                                }
                            })
                        }
                    }

                    ContentCard {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                                                        PurrfectPalette.glowSecondary.copy(alpha = 0.4f)
                                                    )
                                                ),
                                                RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = translation["e2ee_title"],
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = translation["e2ee_subtitle"],
                                        color = PurrfectPalette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (hasSecretKey) {
                                    E2eeActionButton(
                                        label = translation["export_base64_button"],
                                        icon = Icons.Filled.Lock,
                                        accent = Brush.horizontalGradient(
                                            listOf(
                                                PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                                                PurrfectPalette.glowSecondary.copy(alpha = 0.55f)
                                            )
                                        ),
                                        onClick = {
                                            context.coroutineScope.launch {
                                                val secretKey = Base64.encode(context.e2eeImplementation.getSharedSecretKey(friend.userId) ?: return@launch)
                                                //TODO: fingerprint auth
                                                context.activity!!.startActivity(Intent.createChooser(Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, secretKey)
                                                    type = "text/plain"
                                                }, "").apply {
                                                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(
                                                        Intent().apply {
                                                            putExtra(Intent.EXTRA_TEXT, secretKey)
                                                            putExtra(Intent.EXTRA_SUBJECT, secretKey)
                                                        })
                                                    )
                                                })
                                            }
                                        }
                                    )
                                }

                                E2eeActionButton(
                                    label = translation["import_base64_button"],
                                    icon = Icons.Filled.Lock,
                                    accent = Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF7DD3FC),
                                            Color(0xFF818CF8)
                                        )
                                    ),
                                    onClick = { importDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(
                friend.selfieId, friend.bitmojiId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
            )
            BitmojiImage(context = context, url = bitmojiUrl, size = 120)
            Text(
                text = friend.displayName ?: friend.mutableUsername,
                maxLines = 1,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = friend.mutableUsername,
                maxLines = 1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PurrfectPalette.textSecondary
            )
        }

        if (context.config.root.experimental.storyLogger.get()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = {
                    routes.loggedStories.navigate {
                        put("id", id)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f), contentColor = Color.White)) {
                    Text(translation["logged_stories_button"])
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Column {
            //streaks
            streaks?.let {
                var shouldNotify by remember { mutableStateOf(it.notify) }
                SectionTitle(translation["streaks_title"])
                ContentCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = translation.format(
                                    "streaks_length_text", "length" to streaks.length.toString()
                                ), maxLines = 1
                            )
                            Text(
                                text = computeStreakETA(streaks.expirationTimestamp)?.let { translation.format(
                                    "streaks_expiration_text",
                                    "eta" to it
                                ) } ?: translation["streaks_expiration_text_expired"],
                                maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translation["reminder_button"],
                                maxLines = 1,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Switch(
                                checked = shouldNotify,
                                onCheckedChange = {
                                    context.database.setFriendStreaksNotify(id, it)
                                    shouldNotify = it
                                },
                                colors = purrfectSwitchColors()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Group(
        group: MessagingGroupInfo,
        setBottomComposable: ((@Composable () -> Unit)?) -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = group.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = translation.format(
                    "participants_text", "count" to group.participantsCount.toString()
                ),
                maxLines = 1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PurrfectPalette.textSecondary
            )
        }
    }
}
