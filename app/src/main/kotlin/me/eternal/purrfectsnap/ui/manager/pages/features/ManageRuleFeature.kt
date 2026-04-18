package cock.crest.purrfectsnap.lite.ui.manager.pages.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.ui.manager.rememberRouteScrollState
import cock.crest.purrfectsnap.lite.common.data.RuleState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncUpdateDispatcher
import cock.crest.purrfectsnap.lite.storage.clearRuleIds
import cock.crest.purrfectsnap.lite.storage.getRuleIds
import cock.crest.purrfectsnap.lite.storage.setRule
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.AddFriendDialog
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.AddFriendDialog.Actions

class ManageRuleFeature : Routes.Route()  {
    override val title: @Composable () -> Unit = {
        val navBackStackEntry by routes.navController.currentBackStackEntryAsState()
        val text = remember(navBackStackEntry) {
            navBackStackEntry?.arguments?.getString("rule_type")?.let { ruleType ->
                MessagingRuleType.getByName(ruleType)?.let {
                    context.config.root.rules.getPropertyPair(it.key).let {
                        context.translation[it.key.propertyName()]
                    }
                }
            }
        }
        text?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

    @Composable
    fun SelectRuleTypeRadio(
        checked: Boolean,
        text: String,
        onStateChanged: (Boolean) -> Unit,
        selectedBlock: @Composable () -> Unit = {},
    ) {
        val shape = RoundedCornerShape(22.dp)
        val border = if (checked) {
            Brush.linearGradient(listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.8f), PurrfectPalette.glowSecondary.copy(alpha = 0.7f)))
        } else {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f)))
        }
        Surface(
            shape = shape,
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, border),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStateChanged(!checked) }
        ) {
            Column(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = checked,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = PurrfectPalette.glowSecondary,
                            unselectedColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
                if (checked) {
                    Column(
                        modifier = Modifier
                            .padding(start = 44.dp, end = 6.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedBlock()
                    }
                }
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = content@{ navBackStackEntry ->
        val currentRuleType = navBackStackEntry.arguments?.getString("rule_type")?.let {
            MessagingRuleType.getByName(it)
        } ?: return@content

        var ruleState by remember {
            mutableStateOf(context.config.root.rules.getRuleState(currentRuleType))
        }

        val propertyKeyPair = remember {
            context.config.root.rules.getPropertyPair(currentRuleType.key)
        }

        val updateDispatcher = rememberAsyncUpdateDispatcher()
        val currentRuleIds by rememberAsyncMutableState(defaultValue = mutableListOf(), updateDispatcher = updateDispatcher) {
            context.database.getRuleIds(currentRuleType.key)
        }

        fun setRuleState(newState: RuleState?) {
            ruleState = newState
            propertyKeyPair.value.setAny(newState?.key ?: "null")
            context.coroutineScope.launch {
                context.config.writeConfig(dispatchConfigListener = false)
            }
        }

        var addFriendDialog by remember { mutableStateOf(null as AddFriendDialog?) }

        LaunchedEffect(addFriendDialog) {
            if (addFriendDialog == null) {
                updateDispatcher.dispatch()
            }
        }

        fun showAddFriendDialog() {
            addFriendDialog = AddFriendDialog(
                context = context,
                pinnedIds = currentRuleIds,
                actionHandler = Actions(
                    onFriendState = { friend, state ->
                        context.database.setRule(friend.userId, currentRuleType.key, state)
                        if (state) {
                            currentRuleIds.add(friend.userId)
                        } else {
                            currentRuleIds.remove(friend.userId)
                        }
                    },
                    onGroupState = { group, state ->
                        context.database.setRule(group.conversationId, currentRuleType.key, state)
                        if (state) {
                            currentRuleIds.add(group.conversationId)
                        } else {
                            currentRuleIds.remove(group.conversationId)
                        }
                    },
                    getFriendState = { friend ->
                        currentRuleIds.contains(friend.userId)
                    },
                    getGroupState = { group ->
                        currentRuleIds.contains(group.conversationId)
                    }
                )
            )
        }

        if (addFriendDialog != null) {
            addFriendDialog?.Content {
                addFriendDialog = null
            }
        }

        var confirmationDialog by remember { mutableStateOf(false) }
        if (confirmationDialog) {
            AestheticDialog(
                onDismissRequest = { confirmationDialog = false },
                title = translation["clear_list_button"],
                text = translation["dialog_clear_confirmation_text"],
                icon = Icons.Default.DeleteSweep,
                confirmButtonText = translation["dialog_clear_confirm_button"],
                dismissButtonText = translation["dialog_clear_cancel_button"],
                onDismiss = { confirmationDialog = false },
                onConfirm = {
                    context.database.clearRuleIds(currentRuleType.key)
                    context.coroutineScope.launch(context.database.executor.asCoroutineDispatcher()) {
                        updateDispatcher.dispatch()
                    }
                    confirmationDialog = false
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
            val density = LocalDensity.current
            var topBarHeight by remember { mutableStateOf(96.dp) }
            FloatingTopBar(
                title = remember { context.translation[propertyKeyPair.key.propertyName()] },
                onBack = { routes.navController.popBackStack() },
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
                    .padding(top = topBarHeight + 10.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .verticalScroll(rememberRouteScrollState(routeInfo.id)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val headerShape = RoundedCornerShape(22.dp)
                Surface(
                    shape = headerShape,
                    color = Color.White.copy(alpha = 0.04f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, headerShape)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = context.translation[propertyKeyPair.key.propertyDescription()],
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                }

                SelectRuleTypeRadio(
                    checked = ruleState == null,
                    text = translation["disable_state_option"],
                    onStateChanged = { setRuleState(null) }
                ) {
                    Text(text = translation["disable_state_subtext"], fontWeight = FontWeight.Normal, fontSize = 12.sp, color = PurrfectPalette.textSecondary)
                }

                val manageLabel = when (ruleState) {
                    RuleState.WHITELIST -> translation["whitelist_state_button"]
                    RuleState.BLACKLIST -> translation["blacklist_state_button"]
                    else -> null
                }

                SelectRuleTypeRadio(
                    checked = ruleState == RuleState.WHITELIST,
                    text = translation["whitelist_state_option"],
                    onStateChanged = { setRuleState(RuleState.WHITELIST) }
                ) {
                    Text(
                        text = translation.format("whitelist_state_subtext", "count" to currentRuleIds.size.toString()),
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = PurrfectPalette.textSecondary
                    )
                    Button(
                        onClick = { showAddFriendDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = translation["whitelist_state_button"])
                    }
                }

                SelectRuleTypeRadio(
                    checked = ruleState == RuleState.BLACKLIST,
                    text = translation["blacklist_state_option"],
                    onStateChanged = { setRuleState(RuleState.BLACKLIST) }
                ) {
                    Text(
                        text = translation.format("blacklist_state_subtext", "count" to currentRuleIds.size.toString()),
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = PurrfectPalette.textSecondary
                    )
                    Button(
                        onClick = { showAddFriendDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = translation["blacklist_state_button"])
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurrfectPalette.cardOverlay, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(PurrfectPalette.glowSecondary.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translation["clear_list_button"],
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!manageLabel.isNullOrBlank()) {
                                Text(
                                    text = manageLabel,
                                    color = PurrfectPalette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = { confirmationDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = translation["dialog_clear_confirm_button"])
                        }
                    }
                }

                Spacer(modifier = Modifier.height(routes.bottomPadding))
            }
        }
    }
}
