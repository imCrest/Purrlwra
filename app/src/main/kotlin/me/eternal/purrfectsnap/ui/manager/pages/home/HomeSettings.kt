package cock.crest.purrfectsnap.lite.ui.manager.pages.home

import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.common.action.EnumAction
import cock.crest.purrfectsnap.lite.common.bridge.InternalFileHandleType
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.storage.getAllScopeNotes
import cock.crest.purrfectsnap.lite.storage.setAllScopeNotes
import cock.crest.purrfectsnap.lite.task.UpdateCheckWorker
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.Requirements
import cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
import cock.crest.purrfectsnap.lite.ui.util.AlertDialogs
import cock.crest.purrfectsnap.lite.ui.util.openFile
import cock.crest.purrfectsnap.lite.ui.util.saveFile
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HomeSettings : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home_settings") }
    internal lateinit var activityLauncherHelper: ActivityLauncherHelper
    private val dialogs by lazy { AlertDialogs(context.translation) }

    internal fun scheduleUpdateCheck() {
        val workManager = WorkManager.getInstance(context.androidContext)
        val updateSettings = context.config.root.global.updateSettings
        var configDirty = false
        val autoUpdateCheck = updateSettings.autoUpdateCheck.getNullable() ?: run {
            configDirty = true
            updateSettings.autoUpdateCheck.set(true)
            true
        }
        val frequency = updateSettings.updateCheckFrequency.getNullable() ?: run {
            configDirty = true
            updateSettings.updateCheckFrequency.set("daily")
            "daily"
        }
        val updateChannel = updateSettings.updateChannel.getNullable() ?: run {
            configDirty = true
            updateSettings.updateChannel.set("stable")
            "stable"
        }
        if (configDirty) {
            context.config.writeConfig()
        }

        if (autoUpdateCheck) {
            val repeatInterval = when (frequency) {
                "daily" -> 1L
                "weekly" -> 7L
                "monthly" -> 30L
                else -> 1L
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putString("channel_name", translation["update_notification_channel_name"])
                .putString("channel_description", translation["update_notification_channel_description"])
                .putString("notification_title", translation["update_notification_title"])
                .putString("notification_text", translation["update_notification_text"])
                .putString("update_channel", updateChannel)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(repeatInterval, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "purrfectsnap_update_check",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork("purrfectsnap_update_check")
        }
    }

    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(
            initialValue = context.config.root.global.uiSettings.managerTheme.get()
        ) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }

        key(themeId) {
            val currentTheme = ManagerTheme.fromId(themeId).theme
            with(currentTheme) {
                this@HomeSettings.SettingsScreen(nav)
            }
        }
    }

    @Composable
    internal fun RowTitle(title: String) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }

    @Composable
    internal fun PremiumPreferenceToggle(
        sharedPreferences: SharedPreferences,
        key: String,
        text: String,
        defaultValue: Boolean = false,
        confirmDisableTitle: String? = null,
        confirmDisableText: String? = null
    ) {
        val realKey = "debug_$key"
        var value by remember { mutableStateOf(sharedPreferences.getBoolean(realKey, defaultValue)) }
        var showDisableDialog by remember { mutableStateOf(false) }
        val hapticFeedback = LocalHapticFeedback.current
        val positiveLabel = context.translation["button.positive"]
        val negativeLabel = context.translation["button.negative"]

        LaunchedEffect(realKey) {
            if (!sharedPreferences.contains(realKey)) {
                sharedPreferences.edit().putBoolean(realKey, defaultValue).apply()
                value = defaultValue
            }
        }

        if (showDisableDialog) {
            AestheticDialog(
                onDismissRequest = { showDisableDialog = false },
                title = confirmDisableTitle ?: translation["reset_setup_dialog_title"],
                text = confirmDisableText.orEmpty(),
                icon = Icons.Filled.Warning,
                confirmButtonText = positiveLabel,
                dismissButtonText = negativeLabel,
                onConfirm = {
                    value = false
                    sharedPreferences.edit().putBoolean(realKey, false).apply()
                    showDisableDialog = false
                },
                onDismiss = { showDisableDialog = false },
                showCloseButton = false
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp)
                .clickable {
                    if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    val nextValue = !value
                    if (!nextValue && confirmDisableTitle != null) {
                        showDisableDialog = true
                    } else {
                        value = nextValue
                        sharedPreferences.edit() {
                            putBoolean(realKey, value)
                        }
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, modifier = Modifier.padding(start = 26.dp, end = 16.dp), fontSize = 14.sp)
            Switch(
                checked = value,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 26.dp),
                colors = purrfectSwitchColors()
            )
        }
    }

    @Composable
    internal fun PreferenceToggle(sharedPreferences: SharedPreferences, key: String, text: String) {
        val realKey = "debug_$key"
        var value by remember { mutableStateOf(sharedPreferences.getBoolean(realKey, false)) }
        val hapticFeedback = LocalHapticFeedback.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp)
                .clickable {
                    if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    value = !value
                    sharedPreferences
                        .edit() {
                            putBoolean(realKey, value)
                        }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, modifier = Modifier.padding(start = 26.dp, end = 16.dp), fontSize = 14.sp)
            Switch(
                checked = value,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 26.dp),
                colors = purrfectSwitchColors()
            )
        }
    }

    @Composable
    internal fun RowAction(key: String, requireConfirmation: Boolean = false, action: () -> Unit) {
        var confirmationDialog by remember {
            mutableStateOf(false)
        }
        fun takeAction() {
            if (requireConfirmation) {
                confirmationDialog = true
            } else {
                action()
            }
        }
        if (requireConfirmation && confirmationDialog) {
            Dialog(onDismissRequest = { confirmationDialog = false }) {
                dialogs.ConfirmDialog(title = context.translation["manager.dialogs.action_confirm.title"], onConfirm = {
                    action()
                    confirmationDialog = false
                }, onDismiss = {
                    confirmationDialog = false
                })
            }
        }
        ShiftedRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp)
                .clickable {
                    takeAction()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(text = context.translation["actions.$key.name"], fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                context.translation.getOrNull("actions.$key.description")?.let { Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 15.sp) }
            }
            IconButton(onClick = { takeAction() },
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = context.translation.getOrNull("actions.$key.name"),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    internal fun ShiftedRow(
        modifier: Modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        verticalAlignment: Alignment.Vertical = Alignment.Top,
        content: @Composable RowScope.() -> Unit
    ) {
        Row(
            modifier = modifier.padding(start = 26.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) { content(this) }
    }

    @Composable
    internal fun GlassCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun AestheticDropdownField(
        value: String,
        expanded: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = value, color = Color.White)
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
    }
}
