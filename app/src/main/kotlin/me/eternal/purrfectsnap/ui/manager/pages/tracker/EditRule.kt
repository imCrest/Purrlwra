@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package cock.crest.purrfectsnap.lite.ui.manager.pages.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.*
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableStateList
import cock.crest.purrfectsnap.lite.storage.*
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.AddFriendDialog

class EditRule : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.friend_tracker") }

    private data class RuleSnapshot(
        val name: String,
        val author: String,
        val scopes: List<String>,
        val events: List<TrackerRuleEvent>,
        val scopeType: TrackerScopeType
    )

    @Composable
    private fun RuleCard(
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = PaddingValues(16.dp),
        content: @Composable ColumnScope.() -> Unit
    ) {
        val shape = RoundedCornerShape(22.dp)
        val border = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.42f)
                )
            )
        }
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, border)
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, shape)
                    .padding(contentPadding)
            ) {
                Column(content = content)
            }
        }
    }

    @Composable
    fun ActionCheckbox(
        text: String,
        checked: MutableState<Boolean>,
        onChanged: (Boolean) -> Unit = {}
    ) {
        Row(
            modifier = Modifier.clickable {
                checked.value = !checked.value
                onChanged(checked.value)
            },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                modifier = Modifier.height(30.dp),
                checked = checked.value,
                onCheckedChange = { checked.value = it; onChanged(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = PurrfectPalette.glowPrimary,
                    uncheckedColor = Color.White.copy(alpha = 0.8f),
                    checkmarkColor = Color.Black
                )
            )
            Text(text, fontSize = 12.sp, color = Color.White)
        }
    }
    @Composable
    fun ConditionCheckboxes(
        params: TrackerRuleActionParams
    ) {
        ActionCheckbox(
            text = translation["condition_only_inside_conversation"],
            checked = remember { mutableStateOf(params.onlyInsideConversation) },
            onChanged = { params.onlyInsideConversation = it }
        )
        ActionCheckbox(
            text = translation["condition_only_outside_conversation"],
            checked = remember { mutableStateOf(params.onlyOutsideConversation) },
            onChanged = { params.onlyOutsideConversation = it }
        )
        ActionCheckbox(
            text = translation["condition_only_when_app_active"],
            checked = remember { mutableStateOf(params.onlyWhenAppActive) },
            onChanged = { params.onlyWhenAppActive = it }
        )
        ActionCheckbox(
            text = translation["condition_only_when_app_inactive"],
            checked = remember { mutableStateOf(params.onlyWhenAppInactive) },
            onChanged = { params.onlyWhenAppInactive = it }
        )
        ActionCheckbox(
            text = translation["condition_no_push_notification_when_app_active"],
            checked = remember { mutableStateOf(params.noPushNotificationWhenAppActive) },
            onChanged = { params.noPushNotificationWhenAppActive = it }
        )
    }
    @Composable
    fun AddEventDialog(
        onDismissRequest: () -> Unit,
        onEventAdd: (TrackerRuleEvent) -> Unit
    ) {
        val expanded = remember { mutableStateOf(false) }
        val currentEventType = remember { mutableStateOf(TrackerEventType.CONVERSATION_ENTER.key) }
        val addEventActions = remember { mutableStateOf(emptySet<TrackerRuleAction>()) }
        val addEventActionParams = remember { TrackerRuleActionParams() }
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(dismissOnClickOutside = true, usePlatformDefaultWidth = false)
        ) {
            RuleCard(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .widthIn(max = 420.dp),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        translation["add_event_dialog_title"],
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                        ExposedDropdownMenuBox(
                            expanded = expanded.value,
                            onExpandedChange = { expanded.value = !expanded.value },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        val eventLabel = context.translation["tracker_events.${currentEventType.value}"]
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .wrapContentWidth(),
                                value = eventLabel,
                                onValueChange = {},
                                readOnly = true,
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.White),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = PurrfectPalette.cardOverlayColor,
                                    unfocusedContainerColor = PurrfectPalette.cardOverlayColor,
                                    focusedIndicatorColor = Color.White.copy(alpha = 0.18f),
                                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.14f),
                                    cursorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = expanded.value,
                            onDismissRequest = { expanded.value = false },
                            modifier = Modifier.wrapContentWidth(),
                            containerColor = Color(0xFF121528),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            TrackerEventType.entries.forEach { eventType ->
                                DropdownMenuItem(
                                    onClick = {
                                        currentEventType.value = eventType.key
                                        expanded.value = false
                                    },
                                    text = { Text(context.translation["tracker_events.${eventType.key}"], color = Color.White) }
                                )
                            }
                        }
                    }
                    Text(
                        translation["triggers_title"],
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrackerRuleAction.entries.forEach { action ->
                            ActionCheckbox(
                                context.translation["tracker_actions.${action.key}"],
                                checked = remember { mutableStateOf(addEventActions.value.contains(action)) }
                            ) {
                                if (it) addEventActions.value += action else addEventActions.value -= action
                            }
                        }
                    }
                    Text(
                        translation["conditions_title"],
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ConditionCheckboxes(addEventActionParams)
                    Button(
                        onClick = {
                            onEventAdd(
                                TrackerRuleEvent(
                                    id = -1,
                                    enabled = true,
                                    eventType = currentEventType.value,
                                    params = addEventActionParams.copy(),
                                    actions = addEventActions.value.toList()
                                )
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                            contentColor = Color.White
                        )
                    ) { Text(translation["add_button"]) }
                }
            }
    }
    }
    override val title: @Composable () -> Unit = {}
    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val coroutineScope = rememberCoroutineScope()
        val currentRuleId = navBackStackEntry.arguments?.getString("rule_id")?.toIntOrNull()
        val events = rememberAsyncMutableStateList<TrackerRuleEvent>(defaultValue = emptyList()) {
            currentRuleId?.let { ruleId -> context.database.getTrackerEvents(ruleId) } ?: emptyList()
        }
        val eventsToDelete = remember { mutableStateListOf<TrackerRuleEvent>() }
        var currentScopeType by remember { mutableStateOf(TrackerScopeType.BLACKLIST) }
        val scopes = rememberAsyncMutableStateList<String>(defaultValue = emptyList()) {
            currentRuleId?.let { ruleId ->
                context.database.getRuleTrackerScopes(ruleId).also { map ->
                    currentScopeType = if (map.isEmpty()) TrackerScopeType.WHITELIST else map.values.first()
                }.map { entry -> entry.key }
            } ?: emptyList()
        }
        val ruleName = rememberAsyncMutableState<String>(defaultValue = "", keys = arrayOf(currentRuleId)) {
            currentRuleId?.let { ruleId -> context.database.getTrackerRule(ruleId)?.name ?: translation["default_rule_name"] } ?: translation["default_rule_name"]
        }
        val authorName = rememberAsyncMutableState<String>(defaultValue = "", keys = arrayOf(currentRuleId)) {
            currentRuleId?.let { ruleId -> context.database.getTrackerRule(ruleId)?.author ?: "" } ?: ""
        }
        fun snapshotEvents() = events.map { event ->
            event.copy(params = event.params.copy(), actions = event.actions.toList())
        }
        fun buildSnapshot() = RuleSnapshot(
            name = ruleName.value,
            author = authorName.value,
            scopes = scopes.toList(),
            events = snapshotEvents(),
            scopeType = currentScopeType
        )
        val initialSnapshot = remember(currentRuleId) {
            mutableStateOf<RuleSnapshot?>(if (currentRuleId == null) buildSnapshot() else null)
        }
        LaunchedEffect(
            currentRuleId,
            ruleName.value,
            authorName.value,
            events.size,
            scopes.size,
            currentScopeType
        ) {
            if (initialSnapshot.value == null) {
                val hasLoaded = ruleName.value.isNotBlank() ||
                    authorName.value.isNotBlank() ||
                    events.isNotEmpty() ||
                    scopes.isNotEmpty()
                if (hasLoaded) {
                    initialSnapshot.value = buildSnapshot()
                }
            }
        }
        val isDirty by remember {
            derivedStateOf {
                val snapshot = initialSnapshot.value ?: return@derivedStateOf false
                snapshot.name != ruleName.value ||
                snapshot.author != authorName.value ||
                snapshot.scopes != scopes.toList() ||
                snapshot.events != snapshotEvents() ||
                snapshot.scopeType != currentScopeType
            }
        }
        var deleteConfirmation by remember { mutableStateOf(false) }
        var showDuplicateNameDialog by remember { mutableStateOf(false) }
        var showEventsEmptyDialog by remember { mutableStateOf(false) }
        var showDiscardDialog by remember { mutableStateOf(false) }
        var addFriendDialog by remember { mutableStateOf<AddFriendDialog?>(null) }
        var addEventDialogVisible by remember { mutableStateOf(false) }
        if (showDiscardDialog) {
            AestheticDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = translation["discard_changes_dialog_title"],
                text = translation["discard_changes_dialog_text"],
                icon = Icons.Default.Warning,
                confirmButtonText = translation["discard_button"],
                onConfirm = {
                    showDiscardDialog = false
                    routes.navController.popBackStack()
                },
                dismissButtonText = translation["button.cancel"],
                onDismiss = { showDiscardDialog = false },
                opaque = true,
                showCloseButton = false
            )
        }
        BackHandler(enabled = isDirty) {
            showDiscardDialog = true
        }
        if (showEventsEmptyDialog) {
            AestheticDialog(
                onDismissRequest = { showEventsEmptyDialog = false },
                title = translation["cannot_save_rule_dialog_title"],
                text = translation["cannot_save_rule_dialog_text"],
                icon = Icons.Default.Info,
                confirmButtonText = translation["button.ok"],
                onConfirm = { showEventsEmptyDialog = false },
                opaque = true,
                showCloseButton = false
            )
        }
        if (showDuplicateNameDialog) {
            AestheticDialog(
                onDismissRequest = { showDuplicateNameDialog = false },
                title = translation["duplicate_rule_name_dialog_title"],
                text = translation["duplicate_rule_name_dialog_text"],
                icon = Icons.Default.Warning,
                confirmButtonText = translation["button.ok"],
                onConfirm = { showDuplicateNameDialog = false }
            )
        }
        if (deleteConfirmation) {
            AestheticDialog(
                onDismissRequest = { deleteConfirmation = false },
                title = translation["delete_rule_dialog_title"],
                text = translation["delete_rule_dialog_text"],
                icon = Icons.Default.DeleteOutline,
                confirmButtonText = translation["delete_button"],
                onConfirm = {
                    if (currentRuleId != null) context.database.deleteTrackerRule(currentRuleId)
                    routes.navController.popBackStack()
                },
                dismissButtonText = translation["button.cancel"],
                onDismiss = { deleteConfirmation = false },
                opaque = true,
                showCloseButton = false
            )
        }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding(),
            containerColor = Color.Transparent,
            topBar = {
                val topShape = RoundedCornerShape(26.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    shape = topShape,
                    color = PurrfectPalette.cardOverlayColor,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = {
                            if (isDirty) {
                                showDiscardDialog = true
                            } else {
                                routes.navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = translation["back_button_description"], tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                translation[if (currentRuleId == null) "new_rule_title" else "edit_rule_title"],
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                translation["rule_subtitle"] ?: "",
                                color = PurrfectPalette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (currentRuleId != null) {
                            IconButton(onClick = { deleteConfirmation = true }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"], tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(onClick = {
                            if (events.isEmpty()) {
                                showEventsEmptyDialog = true
                                return@IconButton
                            }
                            if (currentRuleId == null && context.database.getTrackerRuleByName(ruleName.value.trim()) != null) {
                                showDuplicateNameDialog = true
                                return@IconButton
                            }
                            val ruleId = currentRuleId ?: context.database.newTrackerRule()
                            eventsToDelete.forEach { event -> context.database.deleteTrackerRuleEvent(event.id.takeIf { it > -1 } ?: return@forEach) }
                            events.forEach { event ->
                                context.database.addOrUpdateTrackerRuleEvent(
                                    event.id.takeIf { it > -1 },
                                    ruleId,
                                    event.eventType,
                                    event.params,
                                    event.actions
                                )
                            }
                            context.database.setTrackerRuleName(ruleId, ruleName.value.trim())
                            context.database.setTrackerRuleAuthor(ruleId, authorName.value.trim())
                            context.database.setRuleTrackerScopes(ruleId, currentScopeType, scopes)
                            routes.navController.popBackStack()
                        }) { Icon(Icons.Filled.Save, contentDescription = translation["save_button_description"], tint = Color.White) }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PurrfectPalette.backgroundGradient)
                    .padding(padding)
            ) {
                val contentBottomPadding = routes.bottomPadding + 12.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentBottomPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RuleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val textFieldShape = RoundedCornerShape(16.dp)
                        Text(
                            translation["general_section_title"],
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = ruleName.value,
                            onValueChange = { ruleName.value = it },
                            label = { Text(translation["rule_name_label"], color = PurrfectPalette.textSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            singleLine = true,
                            shape = textFieldShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PurrfectPalette.glowPrimary,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = PurrfectPalette.textSecondary
                            ),
                            textStyle = LocalTextStyle.current.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = authorName.value,
                            onValueChange = { authorName.value = it },
                            label = { Text(translation["author_name_label"], color = PurrfectPalette.textSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            singleLine = true,
                            shape = textFieldShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PurrfectPalette.glowPrimary,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = PurrfectPalette.textSecondary
                            ),
                            textStyle = LocalTextStyle.current.copy(color = Color.White)
                        )
                    }
                    RuleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            translation["scope_section_title"],
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val friendDialogActions = remember {
                            AddFriendDialog.Actions(
                                onFriendState = { friend, state ->
                                    if (state) scopes.add(friend.userId) else scopes.remove(friend.userId)
                                },
                                onGroupState = { group, state ->
                                    if (state) scopes.add(group.conversationId) else scopes.remove(group.conversationId)
                                },
                                getFriendState = { friend -> friend.userId in scopes },
                                getGroupState = { group -> group.conversationId in scopes }
                            )
                        }
                        val scopeOptions = listOf(
                            0 to translation["scope_all"],
                            1 to translation["scope_whitelist"],
                            2 to translation["scope_blacklist"]
                        )
                        val selectedScopeIndex = when {
                            scopes.isEmpty() -> 0
                            currentScopeType == TrackerScopeType.WHITELIST -> 1
                            else -> 2
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            scopeOptions.forEach { (index, label) ->
                                val selected = selectedScopeIndex == index
                                val optionShape = RoundedCornerShape(16.dp)
                                val optionBrush = remember {
                                    Brush.linearGradient(
                                        listOf(
                                            PurrfectPalette.glowPrimary.copy(alpha = if (selected) 0.22f else 0.12f),
                                            PurrfectPalette.glowSecondary.copy(alpha = if (selected) 0.18f else 0.1f)
                                        )
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 46.dp),
                                    shape = optionShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) PurrfectPalette.glowPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
                                    ),
                                    tonalElevation = 0.dp,
                                    shadowElevation = if (selected) 10.dp else 0.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(optionBrush, optionShape)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                when (index) {
                                                    0 -> scopes.clear()
                                                    1 -> {
                                                        currentScopeType = TrackerScopeType.WHITELIST
                                                        if (scopes.isEmpty()) {
                                                            addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                                        }
                                                    }
                                                    2 -> {
                                                        currentScopeType = TrackerScopeType.BLACKLIST
                                                        if (scopes.isEmpty()) {
                                                            addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        if (scopes.isNotEmpty()) {
                            val selectorShape = RoundedCornerShape(18.dp)
                        val selectorBrush = remember {
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
                                    )
                                )
                            }
                            Surface(
                                onClick = {
                                    addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = selectorShape,
                                color = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                                            PurrfectPalette.glowSecondary.copy(alpha = 0.38f)
                                        )
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(selectorShape)
                                        .background(selectorBrush)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        translation.format("select_friends_groups_button", "count" to scopes.size.toString()),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        addFriendDialog?.Content { addFriendDialog = null }
                    }
                    RuleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(Modifier.animateContentSize()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    translation["events_section_title"],
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { addEventDialogVisible = true },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = translation["add_event_button"], modifier = Modifier.size(28.dp), tint = Color.White)
                                }
                            }
                            if (addEventDialogVisible) {
                                AddEventDialog(
                                    onDismissRequest = { addEventDialogVisible = false },
                                    onEventAdd = { event ->
                                        events.add(0, event)
                                        addEventDialogVisible = false
                                    }
                                )
                            }
                            if (events.isEmpty()) {
                                Text(
                                    translation["no_events_text"],
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    color = PurrfectPalette.textSecondary
                                )
                            }
                            events.forEach { event ->
                                var expanded by remember { mutableStateOf(false) }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .padding(vertical = 4.dp)
                                        .clickable { expanded = !expanded },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f, fill = false),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                                Column {
                                                    Text(
                                                        context.translation["tracker_events.${event.eventType}"],
                                                        lineHeight = 20.sp,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = event.actions.joinToString(", ") { context.translation["tracker_actions.${it.key}"] },
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Light,
                                                        overflow = TextOverflow.Ellipsis,
                                                        maxLines = 1,
                                                        lineHeight = 14.sp,
                                                        color = PurrfectPalette.textSecondary
                                                    )
                                                }
                                            }
                                            OutlinedIconButton(
                                                onClick = {
                                                    if (event.id > -1) {
                                                        eventsToDelete.add(event)
                                                    }
                                                    events.remove(event)
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"], tint = Color.White)
                                            }
                                        }
                                        if (expanded) {
                                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                                ConditionCheckboxes(event.params)
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
