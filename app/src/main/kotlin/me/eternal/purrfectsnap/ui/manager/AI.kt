package me.eternal.purrfectsnap.ui.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.common.config.ConfigContainer
import me.eternal.purrfectsnap.common.config.ConfigFlag
import me.eternal.purrfectsnap.common.config.DataProcessors
import me.eternal.purrfectsnap.common.config.PropertyKey
import me.eternal.purrfectsnap.common.config.PropertyValue
import me.eternal.purrfectsnap.common.data.TrackerEventType
import me.eternal.purrfectsnap.common.data.TrackerRuleAction
import me.eternal.purrfectsnap.common.data.TrackerRuleActionParams
import me.eternal.purrfectsnap.common.data.TrackerScopeType
import me.eternal.purrfectsnap.storage.addOrUpdateTrackerRuleEvent
import me.eternal.purrfectsnap.storage.getAssistantRegistryEntries
import me.eternal.purrfectsnap.storage.getFriends
import me.eternal.purrfectsnap.storage.getGroups
import me.eternal.purrfectsnap.storage.getTrackerRuleByName
import me.eternal.purrfectsnap.storage.newTrackerRule
import me.eternal.purrfectsnap.storage.replaceAssistantRegistry
import me.eternal.purrfectsnap.storage.setRuleTrackerScopes
import me.eternal.purrfectsnap.storage.setTrackerRuleState
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.util.saveFile
import kotlin.math.max
import kotlin.math.min

private enum class AssistantRole {
    USER,
    ASSISTANT
}

private data class AssistantMessage(
    val role: AssistantRole,
    val text: String
)

private data class AssistantResult(
    val reply: String,
    val execute: (() -> Unit)? = null
)

private data class AssistantFeature(
    val id: String,
    val name: String,
    val description: String,
    val path: String,
    val searchPhrases: List<String>,
    val searchTokens: List<String>,
    val container: ConfigContainer? = null,
    val propertyKey: PropertyKey<*>? = null,
    val propertyValue: PropertyValue<*>? = null
) {
    val isContainerToggle get() = container != null && propertyKey != null && container.hasGlobalState
    val isProperty get() = propertyKey != null && propertyValue != null && propertyKey.dataType.type != DataProcessors.Type.CONTAINER
}

private data class AssistantRoute(
    val id: String,
    val name: String,
    val description: String,
    val searchPhrases: List<String>,
    val searchTokens: List<String>,
    val navigate: () -> Unit
)

private data class AssistantAction(
    val id: String,
    val name: String,
    val searchPhrases: List<String>,
    val searchTokens: List<String>,
    val execute: () -> Unit
)

private data class AssistantCandidate(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String,
    val execute: (() -> Unit)? = null
)

private data class ScopeTarget(
    val id: String,
    val displayName: String
)

private const val UNIQUE_OPTION_AMBIGUOUS = "__ambiguous__"
private const val TRAINING_LOG_URI_PREF = "assistant_training_log_uri"

enum class ManagerAssistantTriggerStyle {
    DEFAULT,
    APHELION
}

private class ManagerAssistantEngine(
    private val context: RemoteSideContext,
    private val routes: Routes
) {
    private val logTag = "PurrfectSnapAI"
    private val unsupportedPhrases = listOf(
        "snapchat plus",
        "snap plus",
        "snapchat premium"
    )
    private val stopWords = setOf(
        "a", "an", "the", "to", "for", "on", "off", "of", "in", "it", "me", "please",
        "set", "turn", "enable", "disable", "open", "go", "show", "what", "does", "do",
        "is", "my", "can", "you", "make", "create", "add", "called", "named", "and",
        "with", "into", "app", "assistant", "value", "feature"
    )
    private val featureCatalog by lazy { buildFeatureCatalog() }
    private val routeCatalog by lazy { buildRouteCatalog() }
    private val actionCatalog by lazy { buildActionCatalog() }
    @Volatile
    private var registryPrimed = false

    suspend fun handle(query: String): AssistantResult {
        val normalized = normalize(query)
        if (normalized.isBlank()) {
            return AssistantResult("Ask about a feature, tell me to change a setting, open a section, or create a friend tracker rule.")
        }
        context.log.info("AI handle query=\"$query\"", logTag)
        capabilityReply(normalized)?.let { return it }
        return directIntentReply(query, normalized)
            ?: strictFallbackReply(normalized)
    }

    private fun directIntentReply(rawQuery: String, normalized: String): AssistantResult? {
        supportFlowReply(normalized)?.let { return it }
        trackerRuleReply(rawQuery, normalized)?.let { return it }
        featureMutationReply(rawQuery, normalized)?.let { return it }
        actionReply(normalized)?.let { return it }
        navigationReply(normalized)?.let { return it }
        featureExplanationReply(normalized)?.let { return it }
        return unsupportedReply(normalized)
    }

    private fun supportFlowReply(normalized: String): AssistantResult? {
        basicsHelpReply(normalized)?.let { return it }
        messageLoggerHelpReply(normalized)?.let { return it }
        downloaderHelpReply(normalized)?.let { return it }
        galleryOverrideHelpReply(normalized)?.let { return it }
        performanceModeHelpReply(normalized)?.let { return it }
        customEmojiHelpReply(normalized)?.let { return it }
        bridgeHelpReply(normalized)?.let { return it }
        uiHelpReply(normalized)?.let { return it }
        privacyHelpReply(normalized)?.let { return it }
        autoOpenAutoSaveHelpReply(normalized)?.let { return it }
        rulesHelpReply(normalized)?.let { return it }
        friendTrackerHelpReply(normalized)?.let { return it }
        streaksHelpReply(normalized)?.let { return it }
        experimentalHelpReply(normalized)?.let { return it }
        convertMessageHelpReply(normalized)?.let { return it }
        scriptingHelpReply(normalized)?.let { return it }
        crashAndDebugHelpReply(normalized)?.let { return it }
        fakeSnapReply(normalized)?.let { return it }
        latestSnapReply(normalized)?.let { return it }
        installIssueReply(normalized)?.let { return it }
        installReply(normalized)?.let { return it }
        compatibilityReply(normalized)?.let { return it }
        latestUpdatesReply(normalized)?.let { return it }
        loginHelpReply(normalized)?.let { return it }
        updateReply(normalized)?.let { return it }
        deviceBanReply(normalized)?.let { return it }
        configImportExportReply(normalized)?.let { return it }
        currentThemeReply(normalized)?.let { return it }
        developerReply(normalized)?.let { return it }
        featureCountReply(normalized)?.let { return it }
        permissionsReply(normalized)?.let { return it }
        communityReply(normalized)?.let { return it }
        githubReply(normalized)?.let { return it }
        iosEmojiReply(normalized)?.let { return it }
        profitReply(normalized)?.let { return it }
        deletedContentReply(normalized)?.let { return it }
        snapscoreReply(normalized)?.let { return it }
        platformReply(normalized)?.let { return it }
        installIssueReply(normalized)?.let { return it }
        notOpeningAfterReinstallReply(normalized)?.let { return it }
        continuousSnapReply(normalized)?.let { return it }
        performanceReply(normalized)?.let { return it }
        updateCadenceReply(normalized)?.let { return it }
        crashLagReply(normalized)?.let { return it }
        mapperReply(normalized)?.let { return it }
        rootedLsposedReply(normalized)?.let { return it }
        trackerSupportReply(normalized)?.let { return it }
        snapchatMenuReply(normalized)?.let { return it }
        crashLogsReply(normalized)?.let { return it }
        safetyReply(normalized)?.let { return it }
        duplicateMessagesReply(normalized)?.let { return it }
        bugReportReply(normalized)?.let { return it }
        bestFeaturesReply(normalized)?.let { return it }
        downloadSnapsReply(normalized)?.let { return it }
        hideTypingIndicatorReply(normalized)?.let { return it }
        screenshotIndicatorReply(normalized)?.let { return it }
        return null
    }

    private fun basicsHelpReply(normalized: String): AssistantResult? {
        if (normalized == "hi" || normalized == "hello" || normalized == "hey") {
            return AssistantResult("Hi, I am PurrfectSnap AI, an AI assistant designed to help you with anything related to PurrfectSnap and also help our developers and contributors, who deserve a little rest :)")
        }
        if (normalized.contains("what is purrfectsnap used for")) {
            return AssistantResult("PurrfectSnap enhances Snapchat with privacy tools, downloader features, automation, UI tweaks, tracking tools, and quality-of-life features.")
        }
        if (normalized.contains("enable purrfectsnap for snapchat") || normalized.contains("how do i enable purrfectsnap")) {
            return AssistantResult("Install PurrfectSnap and follow the on-screen instructions. It will set everything up for you.")
        }
        if (normalized.contains("know if purrfectsnap is working")) {
            return AssistantResult("Open Snapchat and check whether your enabled features appear or work. If they do, PurrfectSnap is active.")
        }
        if (normalized.contains("where can i find the features screen")) {
            return AssistantResult("Open PurrfectSnap and tap the Features tab in the bottom bar.")
        }
        if (normalized.contains("reload purrfectsnap settings") || normalized.contains("restart snapchat after changing")) {
            return AssistantResult("After changing important settings, force stop and reopen Snapchat so the hooks reload cleanly.")
        }
        if ((normalized.contains("features") && normalized.contains("not working")) || normalized.contains("none of the features work")) {
            return AssistantResult("Try reinstalling everything.")
        }
        if (normalized.contains("check my purrfectsnap version")) {
            return AssistantResult("Open the homepage of PurrfectSnap. Your current version is shown there.")
        }
        if (normalized.contains("global features and per user")) {
            return AssistantResult("Global features affect Snapchat everywhere. Per-user features or rules affect only the selected friend or chat.")
        }
        if (normalized.contains("disable a broken feature safely")) {
            return AssistantResult("Disable the last feature you changed, force stop Snapchat, and reopen it. If needed, disable features one by one until the issue stops.")
        }
        if (normalized.contains("reset all purrfectsnap settings")) {
            return AssistantResult("Open Settings in PurrfectSnap and use the reset option there to restore defaults.")
        }
        if (normalized.contains("backup my purrfectsnap configuration")) {
            return AssistantResult("Go to the Features tab, tap the three dots in the top-right corner, then select Export Config.")
        }
        if (normalized.contains("restore my purrfectsnap settings")) {
            return AssistantResult("Go to the Features tab, tap the three dots in the top-right corner, then select Import Config.")
        }
        if (normalized.contains("safe to enable together")) {
            return AssistantResult("Most features are fine together, but test heavier features like Performance Mode, Friend Tracker, Custom Emoji, and experimental features one at a time.")
        }
        if (normalized.contains("one phone but not another") || normalized.contains("work differently on lspatch")) {
            return AssistantResult("Some features depend on device firmware, Android version, root environment, and how Snapchat is patched. LSPatch and LSPosed can behave differently.")
        }
        if (normalized.contains("need native hooks")) {
            return AssistantResult("Some features need native hooks because they patch Snapchat code paths that Java-only hooks cannot fully control.")
        }
        return null
    }

    private fun messageLoggerHelpReply(normalized: String): AssistantResult? {
        if (!(normalized.contains("message logger") || normalized.contains("logged messages") || normalized.contains("logged media"))) return null
        return when {
            normalized.contains("enable") -> AssistantResult("Open Features, search Message Logger, and enable it.")
            normalized.contains("where is") || normalized.contains("located") -> AssistantResult("Message Logger is in the Messaging section.")
            normalized.contains("what does") -> AssistantResult("Message Logger records chat activity so you can review messages, including deleted ones when available.")
            normalized.contains("view") || normalized.contains("see deleted") -> AssistantResult("Open Logs or the logger-related screens in PurrfectSnap to review recorded messages and deleted-message entries.")
            normalized.contains("blacklist") || normalized.contains("whitelist") -> AssistantResult("Blacklist mode excludes selected users, while whitelist mode logs only selected users.")
            normalized.contains("exclude one user") || normalized.contains("only from selected users") -> AssistantResult("Use the per-user controls or rules to include or exclude specific users from Message Logger.")
            normalized.contains("export") && normalized.contains("database") -> AssistantResult("Use database export for backups, HTML for browsing, and TXT for simple readable exports.")
            normalized.contains("html") -> AssistantResult("Use HTML export if you want a readable chat-style export.")
            normalized.contains("txt") -> AssistantResult("Use TXT export if you want a simple text export. Random IDs can appear depending on what metadata Snapchat exposes.")
            normalized.contains("import") -> AssistantResult("Import the Message Logger backup database through the relevant import option, then reopen Snapchat and verify the data appears.")
            normalized.contains("clear") -> AssistantResult("Clear Message Logger data from the relevant logger or logs screen in PurrfectSnap.")
            normalized.contains("download") && normalized.contains("deleted") -> AssistantResult("Deleted logged media may not always have an attachment available. If Snapchat no longer exposes the attachment, Download can show No attachment found.")
            else -> AssistantResult("Message Logger can record, filter, export, import, and review logged chat activity depending on the available Snapchat data.")
        }
    }

    private fun downloaderHelpReply(normalized: String): AssistantResult? {
        val downloaderRelated = normalized.contains("download") || normalized.contains("downloader") || normalized.contains("ffmpeg") || normalized.contains("overlay")
        if (!downloaderRelated) return null
        return when {
            normalized.contains("enable media downloader") || normalized.contains("download context menu") -> AssistantResult("Open Features, go to Downloader, and enable Download Context Menu or the downloader options you want.")
            normalized.contains("story") -> AssistantResult("Use the downloader features or context menus available on stories where PurrfectSnap exposes them.")
            normalized.contains("profile picture") -> AssistantResult("Enable Download Profile Pictures in Downloader.")
            normalized.contains("voice note") -> AssistantResult("Voice note downloading depends on the media attachment being available through Snapchat. Use the downloader/context menu where supported.")
            normalized.contains("group chat") || normalized.contains("saved chat media") || normalized.contains("unsaveable media") -> AssistantResult("Use the download context menu on the media when available. Support can vary depending on how Snapchat exposes that media.")
            normalized.contains("where are downloaded") || normalized.contains("folder path") -> AssistantResult("Downloaded Snapchat files are saved to your configured download folder, and you can change that folder path in the downloader settings.")
            normalized.contains("organize") && normalized.contains("username") -> AssistantResult("Use the downloader naming and folder options to organize files by username.")
            normalized.contains("organize") && normalized.contains("date") -> AssistantResult("Use the downloader naming and folder options if you want files organized by date.")
            normalized.contains("duplicate") -> AssistantResult("Allow Duplicate lets the same media be downloaded multiple times. Disable it if you want duplicate prevention.")
            normalized.contains("overlay") -> AssistantResult("Merge Overlays combines text and media overlays into the downloaded output.")
            normalized.contains("automatic media download") || normalized.contains("auto download") -> AssistantResult("Enable Auto Download in Downloader, then use per-user or rules-based controls if you want only selected users.")
            normalized.contains("download logging") || normalized.contains("download history") -> AssistantResult("Use the downloader logs/history screens if enabled to review past download activity.")
            normalized.contains("missing") || normalized.contains("not playing") || normalized.contains("fail") -> AssistantResult("Download failures usually come from unavailable attachments, unsupported media types, or post-processing issues such as FFmpeg processing.")
            normalized.contains("ffmpeg") -> AssistantResult("FFmpeg processing helps post-process downloaded media and can improve compatibility for certain outputs.")
            normalized.contains("quality") -> AssistantResult("Use the downloader and global quality settings to control output quality where supported.")
            else -> AssistantResult("The Downloader covers context-menu downloads, auto-download, profile pictures, overlays, FFmpeg processing, and folder organization.")
        }
    }

    private fun galleryOverrideHelpReply(normalized: String): AssistantResult? {
        if (!(normalized.contains("gallery media send override") || normalized.contains("override mode") || normalized.contains("gallery media") || normalized.contains("send override"))) return null
        return when {
            normalized.contains("where is") || normalized.contains("located") -> AssistantResult("Gallery Media Send Override is in the Messaging section.")
            normalized.contains("enable") -> AssistantResult("Open Features, go to Messaging, then enable Gallery Media Send Override.")
            normalized.contains("disabled mode") -> AssistantResult("Disabled mode keeps Snapchat's normal behavior.")
            normalized.contains("always ask") -> AssistantResult("Always Ask lets you choose the send type each time.")
            normalized.contains("snap mode") -> AssistantResult("Snap mode tries to send gallery media as a normal snap.")
            normalized.contains("always note") || normalized.contains("note mode") -> AssistantResult("Always Note sends the selected gallery media as a note.")
            normalized.contains("saveable snap") -> AssistantResult("Saveable Snap mode sends the media in a way that can be saved in chat.")
            normalized.contains("split") || normalized.contains("duration") || normalized.contains("video") -> AssistantResult("If Snapchat still splits long gallery videos or resets duration, force stop Snapchat and retry. Some resend/queue cases depend on Snapchat's own media handling.")
            normalized.contains("troubleshoot") || normalized.contains("not working") -> AssistantResult("If Gallery Media Send Override is not working, make sure Media File Picker is enabled, then force stop and reopen Snapchat.")
            else -> AssistantResult("Gallery Media Send Override controls how gallery media is sent: disabled, always ask, snap, note, or saveable snap.")
        }
    }

    private fun performanceModeHelpReply(normalized: String): AssistantResult? {
        if (!normalized.contains("performance mode")) return null
        return when {
            normalized.contains("where") -> AssistantResult("Performance Mode is in Global.")
            normalized.contains("difference") -> AssistantResult("Disabled keeps normal behavior, Smooth is lighter optimization, and Max is the most aggressive profile.")
            normalized.contains("enable smooth") -> AssistantResult("Set Performance Mode to Smooth in Global.")
            normalized.contains("enable max") -> AssistantResult("Set Performance Mode to Max in Global.")
            normalized.contains("disable") || normalized.contains("reset") -> AssistantResult("Set Performance Mode back to Disabled to restore the default profile.")
            normalized.contains("freeze") || normalized.contains("blank") || normalized.contains("glitch") || normalized.contains("missing ui") -> AssistantResult("If Performance Mode causes freezes, blank screens, or UI glitches, disable it or switch from Max to Smooth and reopen Snapchat.")
            normalized.contains("safest") -> AssistantResult("Smooth is the safer option. Max is more aggressive and may break on some devices.")
            normalized.contains("oneplus") -> AssistantResult("On some OnePlus devices, Max Performance Mode can be unstable. Smooth or Disabled is safer there.")
            normalized.contains("samsung") -> AssistantResult("Samsung devices can behave differently from OnePlus because firmware and graphics handling differ.")
            normalized.contains("cpu") -> AssistantResult("Performance Mode can increase CPU usage on some devices, especially Max.")
            else -> AssistantResult("Performance Mode changes Snapchat responsiveness and loading behavior. If it causes issues, lower it or disable it.")
        }
    }

    private fun customEmojiHelpReply(normalized: String): AssistantResult? {
        if (!(normalized.contains("custom emoji") || normalized.contains("emoji font") || normalized.contains("ios emoji"))) return null
        return when {
            normalized.contains("where") -> AssistantResult("Custom Emoji is in the Features tab. Search for Custom Emoji to configure it.")
            normalized.contains("enable") || normalized.contains("ios emoji") -> iosEmojiReply(normalized)
                ?: AssistantResult("Download the TTF file, import it through File Imports, then select it in Custom Emoji and reopen Snapchat.")
            normalized.contains("font path") || normalized.contains("custom_emoji_font_path") -> AssistantResult("That setting stores the selected emoji font file path.")
            normalized.contains("lag") || normalized.contains("stutter") || normalized.contains("crash") || normalized.contains("sigsegv") -> AssistantResult("If Custom Emoji causes lag or crashes, disable it first. Compatibility differs across devices and patch environments.")
            normalized.contains("lsposed") || normalized.contains("lspatch") -> AssistantResult("Custom Emoji can behave differently on LSPosed and LSPatch. If it is unstable, disable it.")
            normalized.contains("disable") || normalized.contains("recover") -> AssistantResult("If Snapchat keeps crashing after enabling Custom Emoji, disable it from PurrfectSnap and reopen Snapchat.")
            else -> AssistantResult("Custom Emoji lets you load a custom emoji font, but support varies across devices and environments.")
        }
    }

    private fun bridgeHelpReply(normalized: String): AssistantResult? {
        if (!(normalized.contains("bridge") || normalized.contains("deadobjectexception") || normalized.contains("binder death"))) return null
        return AssistantResult("The PurrfectSnap bridge is the helper connection used for manager and hook communication. If bridge-related crashes happen, collect logs and report them because they are runtime stability issues.")
    }

    private fun uiHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("amoled") || normalized.contains("theme") || normalized.contains("ui ") ||
            normalized.contains("spotlight") || normalized.contains("discover") || normalized.contains("friend feed entry") ||
            normalized.contains("settings gear") || normalized.contains("vertical story")
        if (!relevant) return null
        return when {
            normalized.contains("amoled") || normalized.contains("change the theme") -> AssistantResult("Use Manager Theme in Global to switch between Legacy and Aphelion.")
            normalized.contains("spotlight") || normalized.contains("discover") || normalized.contains("hide unwanted") -> AssistantResult("Use the relevant UI and global story/tab controls to hide unwanted Snapchat surfaces.")
            normalized.contains("friend feed entry") || normalized.contains("hide a specific user") -> AssistantResult("Hide Friend Feed Entry can hide selected users from parts of the feed, but Snapchat surfaces can behave differently, so test and refresh after changing it.")
            normalized.contains("settings gear") -> AssistantResult("If the settings gear is missing, ensure the relevant injector option is enabled and reopen Snapchat.")
            normalized.contains("blank") || normalized.contains("corruption") -> AssistantResult("If a UI tweak causes blank screens or corruption, disable the last UI-related feature you enabled and reopen Snapchat.")
            else -> AssistantResult("PurrfectSnap UI features let you change the manager theme, hide Snapchat surfaces, and tweak message and story presentation.")
        }
    }

    private fun privacyHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("stealth mode") || normalized.contains("typing indicator") || normalized.contains("auto read") ||
            normalized.contains("read receipt") || normalized.contains("anonymous story") || normalized.contains("unlimited conversation pinning") ||
            normalized.contains("e2e encryption") || normalized.contains("privacy features")
        if (!relevant) return null
        return when {
            normalized.contains("stealth mode") -> AssistantResult("Chat Stealth and related privacy tools help hide activity like typing or presence depending on the feature enabled.")
            normalized.contains("typing indicator") -> AssistantResult("Use Hide Typing Notifications to hide your typing indicator.")
            normalized.contains("auto read") || normalized.contains("read receipt") -> AssistantResult("Auto Read and related privacy options can affect read behavior per user or globally depending on your setup.")
            normalized.contains("anonymous story") -> AssistantResult("Anonymous story viewing helps reduce viewing indicators where supported.")
            normalized.contains("pin") -> AssistantResult("Unlimited Conversation Pinning lets you pin more conversations locally than Snapchat normally allows.")
            normalized.contains("e2e encryption") -> AssistantResult("Use E2E Encryption where you want encrypted chat behavior supported by Snapchat and PurrfectSnap.")
            else -> AssistantResult("PurrfectSnap privacy features include stealth tools, typing hiding, screenshot bypass, anonymous story viewing, and local conversation controls.")
        }
    }

    private fun autoOpenAutoSaveHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("auto open") || normalized.contains("auto save") || normalized.contains("snap pre fetch") || normalized.contains("prefetch")
        if (!relevant) return null
        return when {
            normalized.contains("auto open") && normalized.contains("specific user") -> AssistantResult("Use per-user options or rules if you want Auto Open for only selected users.")
            normalized.contains("exclude") -> AssistantResult("Use the per-user or rules-based exclude options for Auto Open or Auto Save.")
            normalized.contains("drain battery") || normalized.contains("overheat") -> AssistantResult("Auto Open and prefetch can increase battery use and heat. Use thermal protection and disable aggressive settings if needed.")
            normalized.contains("not work") || normalized.contains("stuck") || normalized.contains("loading forever") -> AssistantResult("If Auto Open or Auto Save gets stuck, reduce aggressive settings, reopen Snapchat, and test whether prefetch or heavy background work is causing it.")
            normalized.contains("what does") -> AssistantResult("Snap Pre-Fetch caches incoming snap media early, while Auto Open and Auto Save automate opening or saving where supported.")
            else -> AssistantResult("Auto Open, Auto Save, and Snap Pre-Fetch can be configured globally, per user, or through rules.")
        }
    }

    private fun rulesHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("rules engine") || normalized.contains("message logger rule") || normalized.contains("auto download rule") ||
            normalized.contains("auto save rule") || normalized.contains("auto open rule") || normalized.contains("whitelist mode") ||
            normalized.contains("blacklist mode") || normalized.contains("which takes priority")
        if (!relevant) return null
        return AssistantResult("Rules let you apply Message Logger, Auto Download, Auto Save, and Auto Open behavior to selected users or groups. Blacklist excludes selected targets, whitelist limits the feature to selected targets, and per-user settings can interact with global defaults.")
    }

    private fun friendTrackerHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("friend tracker") || normalized.contains("track snapchat profile changes") || normalized.contains("display name changes") || normalized.contains("bitmoji changes")
        if (!relevant) return null
        return when {
            normalized.contains("how to use") || normalized.contains("how do i use") || normalized.contains("set up") ->
                AssistantResult("Open the Friend Tracker tab, enable Friend Tracker, then create rules for events like typing, screenshot, or message read.")
            normalized.contains("enable") -> AssistantResult("Enable Friend Tracker, then create rules or review its logs for the events you want.")
            normalized.contains("export") || normalized.contains("purge") || normalized.contains("storage") -> AssistantResult("Friend Tracker stores activity data until you export or purge it from its management screens.")
            normalized.contains("background") -> AssistantResult("Disable the background-related tracking options if you do not want Friend Tracker running in the background.")
            normalized.contains("display name") || normalized.contains("bitmoji") || normalized.contains("profile changes") -> AssistantResult("Friend Tracker can help monitor profile-related changes, depending on the available event coverage.")
            normalized.contains("disable tracking for one friend") -> AssistantResult("Use Friend Tracker scope or per-user controls to exclude that friend.")
            else -> AssistantResult("Friend Tracker records supported Snapchat activity and profile-related events so you can review them or build rules around them.")
        }
    }

    private fun streaksHelpReply(normalized: String): AssistantResult? {
        if (!normalized.contains("streak")) return null
        return when {
            normalized.contains("enable") -> AssistantResult("Open Streaks Reminder and enable it there.")
            normalized.contains("interval") || normalized.contains("remaining time") -> AssistantResult("Use the Interval and Remaining Time options inside Streaks Reminder.")
            normalized.contains("group streak") -> AssistantResult("Enable the relevant streak reminder options if you want group-related reminders where supported.")
            normalized.contains("not getting") || normalized.contains("duplicate") -> AssistantResult("Check your Streaks Reminder settings and notification behavior if reminders are missing or duplicated.")
            normalized.contains("customize") || normalized.contains("stop") -> AssistantResult("Use the Streaks Reminder settings to tune or disable the reminders.")
            else -> AssistantResult("Streaks Reminder periodically reminds you about streak activity based on the configured timing options.")
        }
    }

    private fun experimentalHelpReply(normalized: String): AssistantResult? {
        if (!(normalized.contains("experimental") || normalized.contains("native hooks") || normalized.contains("spoofing") || normalized.contains("account switcher") || normalized.contains("app lock") || normalized.contains("better transcript") || normalized.contains("cof"))) return null
        return AssistantResult("Experimental features are advanced options that can be powerful but less stable. Test them one at a time, and disable the last one you changed if Snapchat starts crashing.")
    }

    private fun convertMessageHelpReply(normalized: String): AssistantResult? {
        if (!normalized.contains("convert message locally") && !normalized.contains("convert a sent snap")) return null
        return AssistantResult("Convert Message Locally turns snaps into chat-style external media locally where supported. If the converted media disappears or won’t open, Snapchat likely refreshed or no longer exposes it the same way.")
    }

    private fun scriptingHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("script") || normalized.contains("scripting") || normalized.contains("developer mode") || normalized.contains("module folder")
        if (!relevant) return null
        return when {
            normalized.contains("open the scripting ui") -> AssistantResult("Open the Scripts tab to access the scripting UI.")
            normalized.contains("developer mode") -> AssistantResult("Enable Developer Mode from the scripting-related settings if you need advanced script behavior.")
            normalized.contains("module folder") -> AssistantResult("The module folder is where your scripts are stored.")
            normalized.contains("auto reload") -> AssistantResult("Enable Auto Reload in Scripting if you want scripts to reload when they change.")
            normalized.contains("disable a broken script") || normalized.contains("view script logs") -> AssistantResult("Open the Scripts tab to disable the broken script and review any related script logs.")
            normalized.contains("reset the scripting environment") -> AssistantResult("Use the scripting UI and related settings to reset or reload the scripting environment.")
            else -> AssistantResult("Scripting lets you extend PurrfectSnap with custom scripts and developer tools.")
        }
    }

    private fun crashAndDebugHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("crash") || normalized.contains("freeze") || normalized.contains("debug") || normalized.contains("sigsegv") || normalized.contains("logs should i provide")
        if (!relevant) return null
        return when {
            normalized.contains("collect purrfectsnap logs") || normalized.contains("what logs should i provide") || normalized.contains("sigsegv") || normalized.contains("native or java") -> crashLogsReply(normalized)
            normalized.contains("share device info") -> AssistantResult("Share your device model, Android version, patch environment, and the relevant crash logs when reporting debugging issues.")
            normalized.contains("which feature caused a crash") -> AssistantResult("Disable the last changed feature first, then test again. If needed, re-enable features one by one to find the culprit.")
            normalized.contains("oneplus") || normalized.contains("lspatch") || normalized.contains("background") -> AssistantResult("Some crashes are device- or patch-environment-specific. Capture logs and note whether the issue happens only on OnePlus, LSPatch, or after background resume.")
            else -> AssistantResult("If Snapchat crashes or freezes, collect logs and report the issue to the group with the relevant device and setup details.")
        }
    }

    private fun latestSnapReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("latest snap") || normalized.contains("latest snapchat") ||
            normalized.contains("latest update") || normalized.contains("newest update") || normalized.contains("which snapchat version")) &&
            (normalized.contains("work") || normalized.contains("support") || normalized.contains("compatible"))
        if (!relevant) return null
        return AssistantResult("PurrfectSnap automatically downloads the latest Snapchat version for you. You do not need to do anything manually.")
    }

    private fun installReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("install") || normalized.contains("setup")) &&
            (normalized.contains("safe") || normalized.contains("safely") || normalized.contains("how do i"))
        if (!relevant) return null
        return AssistantResult("Just install PurrfectSnap and follow the on-screen instructions. It'll do the job for you.")
    }

    private fun rootedLsposedReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("lsposed") || normalized.contains("xposed")
        if (!relevant) return null
        return AssistantResult("LSPosed is needed only for rooted devices.")
    }

    private fun compatibilityReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("compatib") || normalized.contains("android version") || normalized.contains("supported android")
        if (!relevant) return null
        return AssistantResult("PurrfectSnap supports Android 11 and above.")
    }

    private fun latestUpdatesReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("latest updates") || normalized.contains("get updates") || normalized.contains("new updates")
        if (!relevant) return null
        return AssistantResult("Yes, PurrfectSnap automatically downloads a latest version from a set of versions.")
    }

    private fun loginHelpReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("login") || normalized.contains("log in") || normalized.contains("temporarily disabled")
        if (!relevant) return null
        if (normalized.contains("issue") || normalized.contains("can't") || normalized.contains("cannot") || normalized.contains("temporarily disabled") || normalized.contains("failed attempts")) {
            return AssistantResult(
                "Go to Snapchat and log in. If you face issues, tap the Can't Login button on the login screen and follow the temporarily disabled fix shown there."
            )
        }
        return AssistantResult("Go to Snapchat and log in. If you face issues, tap the Can't Login button on the login screen and follow the temporarily disabled fix shown there.")
    }

    private fun updateReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("how do i update") || normalized.contains("how to update") || normalized.contains("update purrfectsnap") || normalized.contains("update snapchat")
        if (!relevant) return null
        return AssistantResult(
            "To update Snapchat, use Reset and Restart PurrfectSnap in Settings and redo the process. You may get a newer randomized Snap version for security. To update PurrfectSnap itself, use the update button on the homepage when one is available."
        )
    }

    private fun configImportExportReply(normalized: String): AssistantResult? {
        if (normalized.contains("import config") || normalized.contains("import settings")) {
            return AssistantResult("Go to the Features tab, tap the three dots in the top-right corner, then select Import Config.")
        }
        if (normalized.contains("export config") || normalized.contains("export settings")) {
            return AssistantResult("Go to the Features tab, tap the three dots in the top-right corner, then select Export Config.")
        }
        return null
    }

    private fun currentThemeReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("what is this theme") || normalized.contains("which theme") || normalized.contains("current theme")
        if (!relevant) return null
        val currentTheme = context.config.root.global.uiSettings.managerTheme.get()
        return AssistantResult(if (currentTheme == "APHELION") "You are currently using the Aphelion theme." else "You are currently using the Legacy theme.")
    }

    private fun developerReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("who develops") || normalized.contains("who made") || normalized.contains("who created purrfectsnap")
        if (!relevant) return null
        return AssistantResult("Eternal and his team founded the mod back in October 2025. Now, it's maintained by Kaladin, schrodingerspet, and their team.")
    }

    private fun featureCountReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("how many") || normalized.contains("count")) && normalized.contains("feature")
        if (!relevant) return null
        return AssistantResult("PurrfectSnap currently has ${featureCatalog.size} assistant-indexed features.")
    }

    private fun permissionsReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("permission") || normalized.contains("what does it require")
        if (!relevant) return null
        return AssistantResult("PurrfectSnap mainly needs internet, notifications, install packages, and display over other apps for the in-Snapchat menu. Biometric is optional for App Lock, and Shizuku is only needed on the non-root setup path.")
    }

    private fun communityReply(normalized: String): AssistantResult? {
        if (normalized.contains("human help") || normalized.contains("need help") || normalized.contains("telegram link")) {
            return AssistantResult("Click on the Telegram button on the homepage for the channel link, then tap the pinned message in our channel where you will find the link to our group.")
        }
        if (normalized.contains("where is the telegram group") || normalized.contains("where is telegram group")) {
            return AssistantResult("Click on the Telegram button on the homepage for the channel link, then tap the pinned message in our channel where you will find the link to our group.")
        }
        if (normalized.contains("active community") || normalized.contains("join the community")) {
            return AssistantResult("Yes, this mod is actively maintained. To join the community, click on the Telegram button on the homepage.")
        }
        if (normalized.contains("where to report issue") || normalized.contains("where do i report") || normalized.contains("report issue")) {
            return AssistantResult("Refer to our Telegram group if you need to report a bug or any question that I couldn't answer. Click on the Telegram button in the homepage and then tap on the pinned msg in our channel where you will find the link to our group.")
        }
        return null
    }

    private fun githubReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("github link") || normalized.contains("where is github")
        if (!relevant) return null
        return AssistantResult("Go to the homepage of PurrfectSnap and tap on the GitHub button.")
    }

    private fun iosEmojiReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("ios emoji") || normalized.contains("custom emoji") || normalized.contains("how to use emoji") || normalized.contains("change font")
        if (!relevant) return null
        return AssistantResult("Download the TTF file and import it through File Imports on the homepage of PurrfectSnap. Then go to the Features tab, search Custom Emoji, and select that file. Force stop and reopen Snapchat. If it still doesn't work, then it is not supported for your device.")
    }

    private fun profitReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("earn profit") || normalized.contains("non profit") || normalized.contains("profit mod")
        if (!relevant) return null
        return AssistantResult("PurrfectSnap is completely free and is a non-profit mod.")
    }

    private fun performanceReply(normalized: String): AssistantResult? {
        if (normalized.contains("battery") && (normalized.contains("drain") || normalized.contains("more"))) {
            return AssistantResult("It can drain battery slightly, especially if you use high-intensity features like Friend Tracker.")
        }
        if (normalized.contains("camera quality") || normalized.contains("camera performance")) {
            return AssistantResult("No. In fact, it can improve it a lot through our Performance Mode feature, which is set to Max by default.")
        }
        return null
    }

    private fun deletedContentReply(normalized: String): AssistantResult? {
        if (normalized.contains("logs of deleted") || normalized.contains("deleted messages or snap")) {
            return AssistantResult("Logger History in the homepage quick actions.")
        }
        if (normalized.contains("see deleted snaps") || normalized.contains("see deleted chats")) {
            return AssistantResult("Yes, I have turned on the Message Logger feature for you, which will help you see deleted messages and chats.", execute = {
                featureCatalog.firstOrNull { it.id == "message_logger" }?.container?.globalState = true
                featureCatalog.firstOrNull { it.id == "auto_purge" }?.propertyValue?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as? PropertyValue<String>)?.set("never")
                }
                context.config.writeConfig()
            })
        }
        return null
    }

    private fun snapscoreReply(normalized: String): AssistantResult? {
        if (normalized.contains("increase my snapscore") || normalized.contains("boost snapscore")) {
            return AssistantResult("Join Snap Spam groups and use the Auto Open Snaps feature along with them. You can also use the continuous snap sender feature to take it up a notch.")
        }
        if (normalized.contains("spam groups")) {
            return AssistantResult("Ask in the Telegram group.")
        }
        if (normalized.contains("spoof snapscore") || normalized.contains("fake snapscore")) {
            return AssistantResult("Yes. Use the Snapscore spoof-related features in the spoofing section where available.")
        }
        return null
    }

    private fun platformReply(normalized: String): AssistantResult? {
        if (normalized.contains("ios version")) return AssistantResult("No.")
        if ((normalized.contains("creating an account") || normalized.contains("create account")) && normalized.contains("error")) {
            return AssistantResult("You can't create accounts while using PurrfectSnap. You have to create them on stock Snapchat, then log in using the mod.")
        }
        return null
    }

    private fun installIssueReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("app not installed") || normalized.contains("package appears to be invalid")
        if (!relevant) return null
        return AssistantResult("You must have an existing Snapchat installed either in a work profile or as a cloned app. Uninstall it. If you already did that and still see the same issue, make sure you uninstalled it with the option to keep data unchecked. If not done, install Snapchat from the Play Store and then uninstall it like that. If it still does not work, you need a PC to uninstall Snapchat using ADB. Search on Google how to do that.")
    }

    private fun notOpeningAfterReinstallReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("snapchat is not opening") || normalized.contains("snapchat not opening") || normalized.contains("still not opening")) &&
            (normalized.contains("reset") || normalized.contains("clean install") || normalized.contains("reinstall"))
        if (!relevant) return null
        return AssistantResult("Do not HMAL. Remove Snapchat and PurrfectSnap from HMAL. Also remove them from Denylist.")
    }

    private fun continuousSnapReply(normalized: String): AssistantResult? {
        if (!normalized.contains("continuous snap sender")) return null
        return AssistantResult("Go to Snapchat, open a friend's chat, tap the gallery icon, choose a picture, hit send, then tap Send as Continuous Snap, enter the number, and hit send.", execute = {
            featureCatalog.firstOrNull { it.id == "media_file_picker" }?.propertyValue?.let {
                @Suppress("UNCHECKED_CAST")
                (it as? PropertyValue<Boolean>)?.set(true)
            }
            featureCatalog.firstOrNull { it.id == "mode" && it.path.contains("Gallery", ignoreCase = true) }?.propertyValue?.let {
                @Suppress("UNCHECKED_CAST")
                (it as? PropertyValue<String>)?.set("always_ask")
            }
            context.config.writeConfig()
        })
    }

    private fun updateCadenceReply(normalized: String): AssistantResult? {
        if (normalized.contains("actively maintained")) {
            return AssistantResult("Yes, with weekly or monthly updates. Join the Telegram channel for up-to-date announcements or keep an eye on the Announcements tab.")
        }
        if (normalized.contains("next update")) {
            return AssistantResult("Soon.")
        }
        val relevant = normalized.contains("how often is it updated") || normalized.contains("weekly or monthly")
        if (!relevant) return null
        return AssistantResult("PurrfectSnap is updated weekly or monthly, depending on what needs to be shipped.")
    }

    private fun crashLagReply(normalized: String): AssistantResult? {
        if ((normalized.contains("crash") || normalized.contains("crashes")) && !normalized.contains("log")) {
            return AssistantResult("Sometimes, yes. It is an under-development project. If you face issues, report them to our group.")
        }
        if (normalized.contains("lag") || normalized.contains("freeze")) {
            return AssistantResult("Sometimes, yes. It is an under-development project. If you face issues, report them to our group.")
        }
        return null
    }

    private fun mapperReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("break when snap updates") || normalized.contains("stop working after update") ||
            normalized.contains("do features stop working") || normalized.contains("latest snap update")) &&
            (normalized.contains("update") || normalized.contains("snap"))
        if (!relevant) return null
        return AssistantResult("We have implemented a mapper which should cover changes to Snapchat's code in new updates.")
    }

    private fun trackerSupportReply(normalized: String): AssistantResult? {
        if (normalized.contains("sent only to you")) {
            return AssistantResult("Yes, I have turned on the feature for you. It's located in Message Indicators.", execute = {
                setBooleanFeatureEnabled(
                    ids = listOf("message_indicators"),
                    phraseHints = listOf("Message Indicators", "sent only to you"),
                    enabled = true
                )
            })
        }
        if (normalized.contains("i can see you") && (normalized.contains("disable") || normalized.contains("turn off"))) {
            return AssistantResult("Open Friend Tracker and disable the I Can See You rules there.")
        }
        if (normalized.contains("stories") && normalized.contains("typing")) {
            return AssistantResult("Use Hide Typing Notifications for typing privacy, and use the downloader or story-related tools separately for stories.")
        }
        return null
    }

    private fun fakeSnapReply(normalized: String): AssistantResult? {
        val asksPossibility = listOf("is it possible", "can i", "can you", "possible").any { normalized.contains(it) }
        val relevant = listOf(
            "media upload tag", "fake snap", "fake snaps", "send from gallery", "gallery snap",
            "gallery media", "remove upload tag", "upload tag", "without tag", "without label"
        ).any { normalized.contains(it) }
        if (!relevant) return null
        if (asksPossibility && !listOf("how", "guide", "enable", "setup", "set up").any { normalized.contains(it) }) {
            return AssistantResult("Yes, it's possible. Do you want me to guide you?")
        }
        return AssistantResult(
            reply = "I have enabled the necessary settings for this feature. Now force stop and reopen Snapchat, send a picture from your gallery, select your friend, and tap Send. A dialog will appear; tap Make Snap Saveable in Chat and then tap Send!",
            execute = {
                val mediaFilePicker = featureCatalog.firstOrNull { it.id == "media_file_picker" }
                val overrideMode = featureCatalog.firstOrNull { it.id == "mode" && it.path.contains("Gallery", ignoreCase = true) }
                mediaFilePicker?.propertyValue?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as PropertyValue<Boolean>).set(true)
                }
                overrideMode?.propertyValue?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as PropertyValue<String>).set("always_ask")
                }
                context.config.writeConfig()
            }
        )
    }

    private fun snapchatMenuReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("purrfectsnap menu") ||
            (normalized.contains("menu") && normalized.contains("snapchat")) ||
            normalized.contains("settings in snapchat")
        if (!relevant) return null
        return AssistantResult(
            "To be able to access PurrfectSnap settings in Snapchat directly for faster access, follow this:\n\n" +
                "Open Snapchat, go to the chat tab and click on the chat text at the top, a menu will pop up. If it doesn't, ensure that you have given display over other apps permission to PurrfectSnap and set Settings Menu to default in PurrfectSnap."
        )
    }

    private fun crashLogsReply(normalized: String): AssistantResult? {
        val relevant = listOf("crash log", "crash logs", "logfox", "report crash", "grab logs").any { normalized.contains(it) }
        if (!relevant) return null
        return AssistantResult(
            "To grab crash logs, please follow these steps:\n\n" +
                "Non root:\n\n" +
                "Download Logfox from GitHub: https://github.com/F0x1d/LogFox/releases\n" +
                "Install it, open it, and set it up with Shizuku. You need Shizuku, so make sure it is set up first.\n" +
                "Keep Logfox running in the background. You will see a \"running\" notification.\n" +
                "Open Snapchat and let it crash. You will receive a notification from Logfox; tap it, copy the crash log, and send it in the Issues topic.\n\n" +
                "Root:\n\n" +
                "Download Logfox from GitHub: https://github.com/F0x1d/LogFox/releases\n" +
                "Open it and grant root permissions.\n" +
                "Keep Logfox running in the background. You will see a \"running\" notification.\n" +
                "Open Snapchat and let it crash. You will receive a notification from Logfox; tap it, copy the crash log, and send it in the Issues topic."
        )
    }

    private fun safetyReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("safe") || normalized.contains("ban") || normalized.contains("banned") ||
            normalized.contains("main account") || normalized.contains("my account")
        if (!relevant) return null
        return AssistantResult("It's 100% safe and ban-proof. Only new accounts get banned easily, so it is always recommended to use accounts that are at least 6 months old. PurrfectSnap is open source and completely free, so rest assured, it is completely safe.")
    }

    private fun deviceBanReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("device banned") || normalized.contains("ss06")
        if (!relevant) return null
        return AssistantResult("I have applied the necessary settings to fix it. Force stop and reopen Snapchat and log in.", execute = {
            featureCatalog.firstOrNull { it.id == "spoof" }?.container?.globalState = true
            featureCatalog.firstOrNull { it.path.contains("Randomized Device Profile", ignoreCase = true) && it.isContainerToggle }?.container?.globalState = true
            context.config.writeConfig()
        })
    }

    private fun duplicateMessagesReply(normalized: String): AssistantResult? {
        val describesProblem = listOf("issue", "problem", "facing", "seeing", "getting", "having", "i have", "i see").any {
            normalized.contains(it)
        }
        val relevant = (normalized.contains("duplicate") || normalized.contains("duplicating") ||
            normalized.contains("same message") || normalized.contains("repeated message") ||
            normalized.contains("disappearing message") || normalized.contains("dissapearing message")) &&
            (normalized.contains("message") || normalized.contains("messages")) &&
            describesProblem
        if (!relevant) return null
        return AssistantResult("The Message Translator feature was causing it, so I turned it off.", execute = {
            featureCatalog.firstOrNull { it.id == "instant_translation" }?.container?.globalState = false
            featureCatalog.firstOrNull { it.id == "enabled" && it.path.contains("Translation", ignoreCase = true) }?.propertyValue?.let {
                @Suppress("UNCHECKED_CAST")
                (it as PropertyValue<Boolean>).set(false)
            }
            context.config.writeConfig()
        })
    }

    private fun bugReportReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("report bug") || normalized.contains("report bugs") ||
            normalized.contains("facing an issue") || normalized.contains("how do i report")
        if (!relevant) return null
        return AssistantResult("First, tell me what issue you are facing here. If I can help, great. If it needs developer attention, please report it in the Telegram group with proper crash logs if required.")
    }

    private fun bestFeaturesReply(normalized: String): AssistantResult? {
        if (normalized.contains("privacy") && (normalized.contains("best") || normalized.contains("feature") || normalized.contains("recommend"))) {
            return AssistantResult("For privacy, use Bypass Screenshot Detection, Hide Typing Notifications, Stealth Mode, and Message Logger controls.")
        }
        val relevant = normalized.contains("best features") || normalized.contains("recommend features") || normalized.contains("surprise me")
        if (!relevant || normalized.contains("tracker rule")) return null
        return AssistantResult("Some of the best PurrfectSnap features are Friend Tracker, Media Downloader, Bypass Screenshot Detection, Hide Typing Indicator, Stealth Mode, Snapchat Plus controls, and Auto Save tools.")
    }

    private fun downloadSnapsReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("download") || normalized.contains("save")) &&
            (normalized.contains("snap") || normalized.contains("snaps") || normalized.contains("media"))
        if (!relevant) return null
        return AssistantResult("Yes, you can download snaps using PurrfectSnap's downloader features. Enable the download button or auto-download sources in the Downloader settings.")
    }

    private fun hideTypingIndicatorReply(normalized: String): AssistantResult? {
        val relevant = normalized.contains("typing indicator") || normalized.contains("hide typing") || normalized.contains("typing notification")
        if (!relevant) return null
        val asksExplanation = listOf("what does", "what is", "what can", "explain", "tell me about", "how does", "meaning", "purpose", "details", "info").any {
            normalized.contains(it)
        }
        if (asksExplanation) return null
        val wantsDisable = listOf("disable", "turn off", "deactivate", "switch off").any { normalized.contains(it) } ||
            Regex("\\bswitch\\b.*\\boff\\b").containsMatchIn(normalized)
        val wantsEnable = listOf("enable", "turn on", "activate", "switch on").any { normalized.contains(it) } ||
            Regex("\\bswitch\\b.*\\bon\\b").containsMatchIn(normalized)
        if (wantsEnable || wantsDisable) {
            return AssistantResult(
                if (wantsDisable) "Disabled Hide Typing Notifications." else "Enabled Hide Typing Notifications.",
                execute = {
                    setBooleanFeatureEnabled(
                        ids = listOf("hide_typing_notifications"),
                        phraseHints = listOf("Hide Typing Notifications", "typing indicator", "typing notification"),
                        enabled = !wantsDisable
                    )
                }
            )
        }
        return AssistantResult("Yes, enable Hide Typing Notifications to hide your typing indicator.")
    }

    private fun screenshotIndicatorReply(normalized: String): AssistantResult? {
        val relevant = (normalized.contains("screenshot") || normalized.contains("screen record") || normalized.contains("record notification")) &&
            (normalized.contains("indicator") || normalized.contains("without") || normalized.contains("detection") ||
                normalized.contains("notification") || normalized.contains("from chat") || normalized.contains("hide"))
        if (!relevant) return null
        val asksExplanation = listOf("what does", "what is", "what can", "explain", "tell me about", "how does", "meaning", "purpose", "details", "info").any {
            normalized.contains(it)
        }
        if (asksExplanation) return null
        val wantsDisable = listOf("disable", "turn off", "deactivate", "switch off").any { normalized.contains(it) } ||
            Regex("\\bswitch\\b.*\\boff\\b").containsMatchIn(normalized)
        val wantsEnable = listOf("enable", "turn on", "activate", "switch on").any { normalized.contains(it) } ||
            Regex("\\bswitch\\b.*\\bon\\b").containsMatchIn(normalized) ||
            normalized == "yes" || normalized == "yeah" || normalized == "yep" || normalized == "sure" ||
            normalized == "ok" || normalized == "okay" || normalized == "enable it" || normalized == "do it"
        if (!wantsEnable && !wantsDisable && listOf("is there", "is it possible", "can i", "can you", "option").any { normalized.contains(it) }) {
            return AssistantResult("Yes, Bypass Screenshot Detection can hide screenshot and screen-record notifications from chat. Do you want me to enable it?")
        }
        return AssistantResult(
            if (wantsDisable) "Disabled Bypass Screenshot Detection." else "Enabled Bypass Screenshot Detection.",
            execute = {
                setBooleanFeatureEnabled(
                    ids = listOf("bypass_screenshot_detection"),
                    phraseHints = listOf("Bypass Screenshot Detection", "screenshot detection", "screen record"),
                    enabled = !wantsDisable
                )
            }
        )
    }

    private fun setBooleanFeatureEnabled(ids: List<String>, phraseHints: List<String>, enabled: Boolean) {
        val feature = featureCatalog.firstOrNull { feature ->
            feature.id in ids || phraseHints.any { hint ->
                feature.name.contains(hint, ignoreCase = true) ||
                    feature.path.contains(hint, ignoreCase = true) ||
                    feature.searchPhrases.any { it.contains(hint, ignoreCase = true) }
            }
        } ?: return
        feature.container?.globalState = enabled
        feature.propertyValue?.let {
            @Suppress("UNCHECKED_CAST")
            (it as? PropertyValue<Boolean>)?.set(enabled)
        }
        context.config.writeConfig()
    }

    private fun capabilityReply(normalized: String): AssistantResult? {
        val capabilityTerms = listOf("what can you do", "help", "capabilities", "how can you help")
        if (capabilityTerms.none { normalized.contains(it) }) return null
        return AssistantResult(
            "I can explain app features, open sections like logs, settings, features, social, scripts, and friend tracker, change supported settings, launch quick actions, and create basic friend tracker rules from natural language."
        )
    }

    private fun unsupportedReply(normalized: String): AssistantResult? {
        val phrase = unsupportedPhrases.firstOrNull { normalized.contains(it) } ?: return null
        if (featureCatalog.any { feature -> feature.searchPhrases.any { normalize(it) == phrase } }) return null
        return AssistantResult("I can only control PurrfectSnap features. \"$phrase\" is not a PurrfectSnap setting or tool, so I won't guess a replacement.")
    }

    private fun trackerRuleReply(rawQuery: String, normalized: String): AssistantResult? {
        val mentionsTrackerEvent = bestTrackerEventMatch(normalized) != null
        val wantsTrackerRule = normalized.contains("tracker rule") ||
            normalized.contains("friend tracker") ||
            normalized.contains("tracking rule") ||
            (normalized.contains("rule") && mentionsTrackerEvent)
        val createIntent = listOf("create", "make", "add", "new").any { normalized.contains(it) }
        if (!wantsTrackerRule || !createIntent) return null

        val event = bestTrackerEventMatch(normalized)
            ?: return AssistantResult("I can create the rule, but I need the trigger event. Try something like \"create a friend tracker rule for typing\" or \"...for screenshot\".")
        val actions = bestTrackerActions(normalized).ifEmpty { listOf(TrackerRuleAction.LOG) }
        val scopeTargets = resolveScopeTargets(normalized)
        val scopeType = if (scopeTargets.isEmpty()) null else TrackerScopeType.WHITELIST
        val requestedName = Regex("(named|called)\\s+['\\\"]?([^'\\\"]+)['\\\"]?", RegexOption.IGNORE_CASE)
            .find(rawQuery)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val ruleName = requestedName ?: buildDefaultRuleName(event, scopeTargets)

        if (context.database.getTrackerRuleByName(ruleName) != null) {
            return AssistantResult("A friend tracker rule named \"$ruleName\" already exists. Use a different name or edit the existing rule.")
        }

        val actionLabels = actions.joinToString(", ") { translateTrackerAction(it) }
        val scopeSummary = scopeTargets.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.displayName } ?: "all tracked conversations"

        return AssistantResult(
            reply = "Created \"$ruleName\" for ${translateTrackerEvent(event)} with $actionLabels on $scopeSummary.",
            execute = {
                val ruleId = context.database.newTrackerRule(ruleName, "Purrfect Assistant")
                context.database.setTrackerRuleState(ruleId, true)
                context.database.addOrUpdateTrackerRuleEvent(
                    ruleId = ruleId,
                    eventType = event.key,
                    params = TrackerRuleActionParams(),
                    actions = actions
                )
                if (scopeType != null) {
                    context.database.setRuleTrackerScopes(ruleId, scopeType, scopeTargets.map { it.id })
                }
                routes.editRule.navigate {
                    put("rule_id", ruleId.toString())
                }
            }
        )
    }

    private fun featureMutationReply(rawQuery: String, normalized: String): AssistantResult? {
        val mutationFeature = bestDirectFeatureMatch(normalized)
            ?: bestFeatureMatch(normalized)
            ?: bestLenientFeatureMatch(normalized)
            ?: return null
        if (!mutationFeature.isProperty && !mutationFeature.isContainerToggle) return null

        val desiredBoolean = parseDesiredBoolean(normalized)
        val valueAfterTo = rawQuery.substringAfter(" to ", "").trim().takeIf { rawQuery.contains(" to ", ignoreCase = true) && it.isNotBlank() }
        val valueAfterAs = rawQuery.substringAfter(" as ", "").trim().takeIf { rawQuery.contains(" as ", ignoreCase = true) && it.isNotBlank() }
        val valueCandidate = valueAfterTo ?: valueAfterAs

        if (mutationFeature.isContainerToggle) {
            val desiredState = desiredBoolean ?: return null
            return AssistantResult(
                reply = "${if (desiredState) "Enabled" else "Disabled"} ${mutationFeature.name}.",
                execute = {
                    mutationFeature.container?.globalState = desiredState
                    context.config.writeConfig()
                }
            )
        }

        val propertyKey = mutationFeature.propertyKey ?: return null
        val propertyValue = mutationFeature.propertyValue ?: return null

        return when (propertyKey.dataType.type) {
            DataProcessors.Type.BOOLEAN -> {
                val desiredState = desiredBoolean ?: return null
                AssistantResult(
                    reply = "${if (desiredState) "Enabled" else "Disabled"} ${mutationFeature.name}.",
                    execute = {
                        @Suppress("UNCHECKED_CAST")
                        (propertyValue as PropertyValue<Boolean>).set(desiredState)
                        context.config.writeConfig()
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                val explicitValue = valueCandidate ?: extractExplicitSetValue(rawQuery)
                val option = explicitValue?.let { matchUniqueOption(propertyKey, it) }
                    ?: desiredBoolean?.let { desired ->
                        resolveBooleanUniqueOption(propertyKey, desired)
                    }
                    ?: matchUniqueOption(propertyKey, normalized)
                    ?: return null
                if (option == UNIQUE_OPTION_AMBIGUOUS) {
                    return AssistantResult(buildUniqueOptionFollowUp(mutationFeature, propertyKey))
                }
                AssistantResult(
                    reply = "Set ${mutationFeature.name} to ${translateOption(propertyKey, option)}.",
                    execute = {
                        @Suppress("UNCHECKED_CAST")
                        (propertyValue as PropertyValue<String>).set(option)
                        context.config.writeConfig()
                    }
                )
            }

            DataProcessors.Type.INTEGER -> {
                val number = valueCandidate?.toIntOrNull() ?: normalized.filter { it.isDigit() }.toIntOrNull() ?: return null
                AssistantResult(
                    reply = "Set ${mutationFeature.name} to $number.",
                    execute = {
                        @Suppress("UNCHECKED_CAST")
                        (propertyValue as PropertyValue<Int>).set(number)
                        context.config.writeConfig()
                    }
                )
            }

            DataProcessors.Type.FLOAT -> {
                val number = valueCandidate?.toFloatOrNull() ?: Regex("(\\d+(?:\\.\\d+)?)").find(normalized)?.value?.toFloatOrNull() ?: return null
                AssistantResult(
                    reply = "Set ${mutationFeature.name} to $number.",
                    execute = {
                        @Suppress("UNCHECKED_CAST")
                        (propertyValue as PropertyValue<Float>).set(number)
                        context.config.writeConfig()
                    }
                )
            }

            DataProcessors.Type.STRING -> {
                val text = valueCandidate?.takeIf { it.isNotBlank() } ?: return null
                AssistantResult(
                    reply = "Set ${mutationFeature.name} to \"$text\".",
                    execute = {
                        @Suppress("UNCHECKED_CAST")
                        (propertyValue as PropertyValue<String>).set(text)
                        context.config.writeConfig()
                    }
                )
            }

            else -> null
        }
    }

    private fun extractExplicitSetValue(rawQuery: String): String? {
        val patterns = listOf(
            Regex("\\bset\\b.+?\\bto\\b\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("\\bmake\\b.+?\\bto\\b\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("\\bchange\\b.+?\\bto\\b\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("\\bas\\b\\s+(.+)$", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(rawQuery)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun actionReply(normalized: String): AssistantResult? {
        val wantsAction = listOf("run", "launch", "start", "open", "clean", "export").any { normalized.contains(it) }
        if (!wantsAction) return null
        val action = bestDirectActionMatch(normalized) ?: return null
        return AssistantResult(reply = "Launching ${action.name}.", execute = action.execute)
    }

    private fun navigationReply(normalized: String): AssistantResult? {
        val wantsNavigation = listOf("open", "go to", "take me", "show", "navigate").any { normalized.contains(it) }
        if (!wantsNavigation) return null
        val route = bestDirectRouteMatch(normalized) ?: return null
        return AssistantResult(reply = "Opened ${route.name}.", execute = route.navigate)
    }

    private fun featureExplanationReply(normalized: String): AssistantResult? {
        val feature = bestDirectFeatureMatch(normalized) ?: return null
        val currentValue = if (feature.propertyKey?.dataType?.type == DataProcessors.Type.CONTAINER) {
            null
        } else {
            feature.propertyKey?.let { key ->
                feature.propertyValue?.let { value -> describeCurrentValue(key, value) }
            } ?: if (feature.isContainerToggle) {
                null
            } else {
                feature.container?.globalState?.let { if (it) "Enabled" else "Disabled" }
            }
        }
        val reply = buildString {
            append(feature.name)
            append(": ")
            append(featureSummary(feature))
            append(" Find it in ")
            append(feature.path)
            append(".")
            currentValue?.takeIf { it.isNotBlank() }?.let {
                append(" Current value: ")
                append(it)
                append(".")
            }
        }
        return AssistantResult(reply)
    }

    private fun buildCandidates(rawQuery: String, normalized: String): List<AssistantCandidate> {
        val wantsSetting = parseDesiredBoolean(normalized) != null || normalized.contains("set ")
        val wantsExplanation = listOf("what", "explain", "how", "feature").any { normalized.contains(it) }
        val wantsNavigation = listOf("open", "go", "show", "navigate").any { normalized.contains(it) }
        val desiredBoolean = parseDesiredBoolean(normalized)

        val featureCandidates = featureCatalog
            .map { it to scoreFeature(normalized, it) }
            .filter { it.second >= 0.42f }
            .sortedByDescending { it.second }
            .take(4)
            .mapNotNull { (feature, _) ->
                if (wantsSetting && !feature.isProperty && !feature.isContainerToggle) return@mapNotNull null
                AssistantCandidate(
                    id = feature.id,
                    kind = if (feature.isProperty || feature.isContainerToggle) "feature" else "info",
                    title = feature.name,
                    summary = "${feature.path}. ${featureSummary(feature)}",
                    execute = when {
                        wantsSetting && feature.isContainerToggle && desiredBoolean != null -> ({
                            feature.container?.globalState = desiredBoolean
                            context.config.writeConfig()
                        })
                        wantsSetting && feature.isProperty -> buildFeatureSetter(feature, rawQuery, normalized)
                        else -> null
                    }
                )
            }

        val routeCandidates = if (wantsNavigation) {
            routeCatalog
                .map { it to scoreCandidate(normalized, it.searchPhrases, it.searchTokens) }
                .filter { it.second >= 0.45f }
                .sortedByDescending { it.second }
                .take(3)
                .map { (route, _) ->
                    AssistantCandidate(
                        id = route.id,
                        kind = "route",
                        title = route.name,
                        summary = route.description,
                        execute = route.navigate
                    )
                }
        } else emptyList()

        val actionCandidates = if (wantsNavigation) {
            actionCatalog
                .map { it to scoreCandidate(normalized, it.searchPhrases, it.searchTokens) }
                .filter { it.second >= 0.5f }
                .sortedByDescending { it.second }
                .take(2)
                .map { (action, _) ->
                    AssistantCandidate(
                        id = action.id,
                        kind = "action",
                        title = action.name,
                        summary = action.name,
                        execute = action.execute
                    )
                }
        } else emptyList()

        val all = when {
            wantsNavigation -> routeCandidates + actionCandidates + featureCandidates.take(1)
            wantsSetting || wantsExplanation -> featureCandidates + routeCandidates.take(1)
            else -> featureCandidates + routeCandidates + actionCandidates
        }
        return all.distinctBy { it.id }.take(5)
    }

    private fun buildFeatureSetter(feature: AssistantFeature, rawQuery: String, normalized: String): (() -> Unit)? {
        val propertyKey = feature.propertyKey ?: return null
        val propertyValue = feature.propertyValue ?: return null
        val desiredBoolean = parseDesiredBoolean(normalized)
        val valueAfterTo = rawQuery.substringAfter(" to ", "").trim().takeIf { rawQuery.contains(" to ", ignoreCase = true) && it.isNotBlank() }
        val valueAfterAs = rawQuery.substringAfter(" as ", "").trim().takeIf { rawQuery.contains(" as ", ignoreCase = true) && it.isNotBlank() }
        val valueCandidate = valueAfterTo ?: valueAfterAs ?: normalized
        return when (propertyKey.dataType.type) {
            DataProcessors.Type.BOOLEAN -> desiredBoolean?.let { target ->
                {
                    @Suppress("UNCHECKED_CAST")
                    (propertyValue as PropertyValue<Boolean>).set(target)
                    context.config.writeConfig()
                }
            }
            DataProcessors.Type.STRING_UNIQUE_SELECTION -> matchUniqueOption(propertyKey, valueCandidate)?.let { matched ->
                {
                    @Suppress("UNCHECKED_CAST")
                    (propertyValue as PropertyValue<String>).set(matched)
                    context.config.writeConfig()
                }
            }
            DataProcessors.Type.INTEGER -> valueCandidate.toIntOrNull()?.let { target ->
                {
                    @Suppress("UNCHECKED_CAST")
                    (propertyValue as PropertyValue<Int>).set(target)
                    context.config.writeConfig()
                }
            }
            DataProcessors.Type.FLOAT -> valueCandidate.toFloatOrNull()?.let { target ->
                {
                    @Suppress("UNCHECKED_CAST")
                    (propertyValue as PropertyValue<Float>).set(target)
                    context.config.writeConfig()
                }
            }
            DataProcessors.Type.STRING -> {
                @Suppress("UNCHECKED_CAST")
                {
                    (propertyValue as PropertyValue<String>).set(valueCandidate)
                    context.config.writeConfig()
                }
            }
            else -> null
        }
    }

    private fun buildSelectionPrompt(rawQuery: String, candidates: List<AssistantCandidate>): String {
        return buildString {
            appendLine("Choose the best candidate id for the user's request.")
            appendLine("Return only one id or NONE.")
            appendLine("User: $rawQuery")
            appendLine("Candidates:")
            candidates.forEach { candidate ->
                appendLine("${candidate.id} | ${candidate.kind} | ${candidate.title} | ${candidate.summary}")
            }
        }
    }

    private fun parseSelectedCandidate(rawResponse: String, candidates: List<AssistantCandidate>): AssistantCandidate? {
        val normalized = normalize(rawResponse)
        if (normalized.contains("none")) return null
        return candidates.firstOrNull { candidate ->
            normalized.contains(normalize(candidate.id)) || normalized.contains(normalize(candidate.title))
        }
    }

    private fun formatCandidateReply(rawQuery: String, normalized: String, candidate: AssistantCandidate): String {
        val wantsExplanation = listOf("what", "explain", "how", "feature").any { normalized.contains(it) }
        val wantsNavigation = listOf("open", "go", "show", "navigate").any { normalized.contains(it) }
        val wantsSetting = parseDesiredBoolean(normalized) != null || normalized.contains("set ")
        return when {
            candidate.kind == "route" || candidate.kind == "action" || wantsNavigation -> "Opened ${candidate.title}."
            candidate.kind == "feature" && wantsSetting -> {
                val desired = parseDesiredBoolean(normalized)
                desired?.let { if (it) "Enabled ${candidate.title}." else "Disabled ${candidate.title}." }
                    ?: "Updated ${candidate.title}."
            }
            wantsExplanation || candidate.kind == "info" || candidate.kind == "feature" -> "${candidate.title}: ${candidate.summary}"
            else -> rawQuery
        }
    }

    private fun strictFallbackReply(normalized: String): AssistantResult {
        val feature = bestDirectFeatureMatch(normalized) ?: bestFeatureMatch(normalized)
        val route = bestDirectRouteMatch(normalized) ?: bestRouteMatch(normalized)
        val suggestions = buildList {
            feature?.let { add("feature: ${it.name}") }
            route?.let { add("section: ${it.name}") }
        }.joinToString(", ")
        return AssistantResult(
            if (suggestions.isNotBlank()) {
                "I couldn't confidently execute that yet. Closest matches: $suggestions. Try asking to enable a setting, open a section, or create a tracker rule with a clear event."
            } else {
                "I couldn't map that to an app feature yet. Try naming the feature, section, or tracker event more directly."
            }
        )
    }

    private fun ensureRegistrySynced() {
        if (registryPrimed) return
        val entries = featureCatalog.map { feature ->
            val valueType = feature.propertyKey?.dataType?.type?.name ?: if (feature.isContainerToggle) "BOOLEAN" else "INFO"
            val allowedValues = when {
                feature.isContainerToggle -> listOf("true", "false")
                feature.propertyKey?.dataType?.type == DataProcessors.Type.BOOLEAN -> listOf("true", "false")
                else -> feature.propertyValue?.defaultValues?.map { it.toString() }.orEmpty()
            }
            val allowedActions = when {
                feature.isContainerToggle || feature.propertyKey?.dataType?.type == DataProcessors.Type.BOOLEAN ->
                    listOf("toggle_setting", "search_feature")
                feature.isProperty -> listOf("set_option", "search_feature")
                else -> listOf("search_feature")
            }
            me.eternal.purrfectsnap.storage.AssistantRegistryEntry(
                id = feature.id,
                kind = "feature",
                title = feature.name,
                category = feature.path.substringBefore(" > ", "Features"),
                path = feature.path,
                description = featureSummary(feature),
                settingKey = feature.propertyKey?.name,
                screenRoute = routes.features.routeInfo.id,
                allowedActions = allowedActions,
                allowedValues = allowedValues,
                aliases = feature.searchPhrases,
                commonTypos = emptyList(),
                examples = listOf(
                    "What does ${feature.name} do?",
                    "Open ${feature.name}",
                    "Enable ${feature.name}"
                ),
                searchTokens = feature.searchTokens
            )
        } + routeCatalog.map { route ->
            me.eternal.purrfectsnap.storage.AssistantRegistryEntry(
                id = route.id,
                kind = "route",
                title = route.name,
                category = "Navigation",
                path = route.name,
                description = route.description,
                screenRoute = route.id,
                allowedActions = listOf("open_screen", "search_feature"),
                aliases = route.searchPhrases,
                examples = listOf("Open ${route.name}", "Go to ${route.name}"),
                searchTokens = route.searchTokens
            )
        } + actionCatalog.map { action ->
            me.eternal.purrfectsnap.storage.AssistantRegistryEntry(
                id = action.id,
                kind = "action",
                title = action.name,
                category = "Action",
                path = action.name,
                description = action.name,
                allowedActions = listOf("open_screen", "search_feature"),
                aliases = action.searchPhrases,
                examples = listOf("Run ${action.name}", "Launch ${action.name}"),
                searchTokens = action.searchTokens
            )
        }
        context.database.replaceAssistantRegistry(entries)
        registryPrimed = true
    }

    private fun buildRetrievedDocuments(normalized: String): List<String> {
        val query = normalize(normalized)
        val registryEntries = context.database.getAssistantRegistryEntries()
        val directIds = buildSet {
            bestDirectFeatureMatch(query)?.id?.let(::add)
            bestDirectRouteMatch(query)?.id?.let(::add)
            bestDirectActionMatch(query)?.id?.let(::add)
        }

        fun toRegistryDoc(entry: me.eternal.purrfectsnap.storage.AssistantRegistryEntry): String {
            return buildString {
                append(entry.kind)
                append("|id=")
                append(entry.id)
                append("|title=")
                append(escapeForJson(entry.title))
                append("|path=")
                append(escapeForJson(entry.path))
                append("|desc=")
                append(escapeForJson(entry.description))
                append("|route=")
                append(escapeForJson(entry.screenRoute.orEmpty()))
                append("|actions=")
                append(escapeForJson(entry.allowedActions.joinToString(",")))
                append("|values=")
                append(escapeForJson(entry.allowedValues.joinToString(",")))
                append("|aliases=")
                append(escapeForJson(entry.aliases.take(6).joinToString(",")))
            }
        }

        val forcedDocs = registryEntries
            .filter { it.id in directIds }
            .take(2)
            .map(::toRegistryDoc)

        val scored = registryEntries.map { entry ->
            val phrases = buildList {
                add(entry.title)
                add(entry.description)
                add(entry.path)
                addAll(entry.aliases)
                addAll(entry.commonTypos)
                addAll(entry.examples)
            }.filter { it.isNotBlank() }
            val exactBoost = if (phrases.any { normalize(it) == query }) 3f else 0f
            val fuzzyScore = scoreCandidate(query, phrases, entry.searchTokens + phrases.flatMap { tokenize(it) })
            val semanticScore = scoreTokens(query, entry.searchTokens + tokenize(entry.description) + entry.examples.flatMap { tokenize(it) })
            entry to (exactBoost + max(fuzzyScore, semanticScore))
        }
            .filter { it.second >= 0.42f }
            .sortedByDescending { it.second }
            .map { (entry, _) -> entry }
            .filterNot { it.id in directIds }
            .take(maxOf(0, 2 - forcedDocs.size))
            .map(::toRegistryDoc)

        val trackerDoc = buildString {
            append("tracker|id=friend_tracker_rule|title=Friend Tracker Rule|path=Friend Tracker|desc=Create friend tracker rules.")
            append("|actions=create_tracker_rule")
            append("|values=")
            append(
                escapeForJson(
                    (TrackerEventType.entries.map { it.key } + TrackerRuleAction.entries.map { it.key })
                        .joinToString(",")
                )
            )
            append("|aliases=friend tracker,tracker rule")
        }

        return forcedDocs + scored + trackerDoc
    }

    private fun escapeForJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }

    private fun featureAliasesForKey(keyName: String): List<String> {
        return when (keyName) {
            "friend_tracker" -> listOf("friend tracker", "tracker", "tracker feature", "friend tracking")
            "snapchat_plus" -> listOf("snapchat plus", "snap plus", "snapchat premium", "plus", "snapchat pluys", "snapchta plus", "snap plus feature")
            "merge_overlays" -> listOf("merge overlay", "merge overlays", "overlay merge", "merge overlay feature", "merge overlays feature")
            "hidden_snapchat_plus_features" -> listOf("hidden snap features", "hidden snapchat features", "hidden snap plus features", "hidden snapchat plus features")
            "manager_theme" -> listOf("manager theme", "theme", "aphelion theme", "legacy theme")
            "haptic_feedback" -> listOf("haptic", "haptic feedback", "vibration", "vibrate")
            "record_messaging_events" -> listOf("record tracker events", "tracker event logging")
            "allow_running_in_background" -> listOf("background tracking", "run in background")
            "custom_video_codec" -> listOf("video codec", "custom video codec")
            "custom_audio_codec" -> listOf("audio codec")
            "auto_purge" -> listOf("auto purge", "purge")
            "bypass_screenshot_detection" -> listOf(
                "bypass screenshot detection",
                "hide screenshot notification",
                "hide screenshot indicator",
                "hide screen record notification",
                "screenshot without notification",
                "record without notification"
            )
            "hide_typing_notifications" -> listOf("hide typing indicator", "hide typing notification", "typing indicator")
            "download_button" -> listOf("download snaps", "save snaps", "download button", "media downloader")
            "instant_translation" -> listOf("message translator", "message translation", "translator causing duplicate messages")
            else -> emptyList()
        }
    }

    private fun featureSummary(feature: AssistantFeature): String {
        feature.description.takeIf { it.isNotBlank() }?.let { return it }
        return when (feature.id) {
            "friend_tracker" -> "Tracks Snapchat presence and messaging events so you can create rules for typing, speaking, screenshots, opens, and other activity."
            "snapchat_plus" -> "Controls the Snapchat Plus subscription state PurrfectSnap reports to Snapchat."
            "merge_overlays" -> "Merges downloaded media overlays into the saved media output."
            "hidden_snapchat_plus_features" -> "Unlocks hidden Snapchat Plus feature switches exposed by PurrfectSnap."
            "haptic_feedback" -> "Controls vibration feedback for manager interactions."
            "manager_theme" -> "Changes the manager UI theme."
            "record_messaging_events" -> "Stores messaging events for friend tracker rules."
            "allow_running_in_background" -> "Lets the friend tracker keep running while the app is not foregrounded."
            "bypass_screenshot_detection" -> "Hides screenshot and screen-record detection notifications from Snapchat chats."
            "hide_typing_notifications" -> "Hides your typing indicator from chats."
            "download_button" -> "Adds downloader controls for saving snaps and media."
            else -> "This is part of the app configuration."
        }
    }

    private fun buildFeatureCatalog(): List<AssistantFeature> {
        val features = mutableListOf<AssistantFeature>()
        fun walk(container: ConfigContainer, pathSegments: List<String>) {
            container.properties.forEach { (key, value) ->
                val isHidden = key.params.flags.contains(ConfigFlag.HIDDEN)
                val translatedName = translated(key.propertyName(), humanize(key.name))
                val translatedDescription = translated(key.propertyDescription(), "")
                val path = (pathSegments + translatedName).joinToString(" > ")
                val phrases = buildList {
                    add(translatedName)
                    add(translatedDescription)
                    add(path)
                    add(key.name)
                    addAll(featureAliasesForKey(key.name))
                    key.params.disabledKey?.let { add(it) }
                    value.defaultValues?.forEach { add(it.toString()) }
                }.filter { it.isNotBlank() }.distinct()
                val tokens = phrases.flatMap { tokenize(it) }.distinct()

                if (key.dataType.type == DataProcessors.Type.CONTAINER) {
                    val child = value.get() as? ConfigContainer ?: return@forEach
                    if (!isHidden) {
                        features += AssistantFeature(
                            id = key.name,
                            name = translatedName,
                            description = translatedDescription,
                            path = path,
                            searchPhrases = phrases,
                            searchTokens = tokens,
                            container = child,
                            propertyKey = key,
                            propertyValue = value
                        )
                    }
                    walk(child, if (isHidden) pathSegments else pathSegments + translatedName)
                } else {
                    features += AssistantFeature(
                        id = key.name,
                        name = translatedName,
                        description = translatedDescription,
                        path = path,
                        searchPhrases = phrases,
                        searchTokens = tokens,
                        propertyKey = key,
                        propertyValue = value
                    )
                }
            }
        }
        walk(context.config.root, emptyList())
        return features
    }

    private fun buildRouteCatalog(): List<AssistantRoute> {
        fun route(
            routeId: String,
            name: String,
            description: String,
            vararg aliases: String,
            navigate: () -> Unit
        ) = AssistantRoute(
            id = routeId,
            name = name,
            description = description,
            searchPhrases = buildList {
                add(name)
                add(description)
                aliases.forEach { add(it) }
            }.distinct(),
            searchTokens = buildList {
                add(name)
                add(description)
                aliases.forEach { add(it) }
            }.flatMap { tokenize(it) }.distinct(),
            navigate = navigate
        )

        return listOf(
            route(routes.home.routeInfo.id, "Home", "Main dashboard", "home", "dashboard") { routes.home.navigateReset() },
            route(routes.homeLogs.routeInfo.id, "Logs", "App log overview", "logs", "home logs", "logger") { routes.homeLogs.navigateReset() },
            route(routes.about.routeInfo.id, "About", "App overview and about", "about", "info") { routes.about.navigateReset() },
            route(routes.settings.routeInfo.id, "Settings", "Home settings", "settings", "preferences") { routes.settings.navigateReset() },
            route(routes.features.routeInfo.id, "Features", "Feature configuration", "features", "feature settings") { routes.features.navigateReset() }
        )
    }

    private fun buildActionCatalog(): List<AssistantAction> {
        // Intentionally empty for Lite/Core builds: quick actions and manager-triggered actions are removed.
        return emptyList()
    }

    private fun bestDirectFeatureMatch(normalized: String): AssistantFeature? =
        clearWinner(directFeatureCandidates(normalized))

    private fun bestDirectRouteMatch(normalized: String): AssistantRoute? =
        clearWinner(directRouteCandidates(normalized))

    private fun bestDirectActionMatch(normalized: String): AssistantAction? =
        clearWinner(directActionCandidates(normalized))

    private fun directFeatureCandidates(normalized: String): List<Pair<AssistantFeature, Float>> {
        return featureCatalog.mapNotNull { feature ->
            val score = directPhraseScore(normalized, feature.searchPhrases)
            if (score <= 0f) null else feature to score
        }.sortedByDescending { it.second }
    }

    private fun directRouteCandidates(normalized: String): List<Pair<AssistantRoute, Float>> {
        return routeCatalog.mapNotNull { route ->
            val score = directPhraseScore(normalized, route.searchPhrases)
            if (score <= 0f) null else route to score
        }.sortedByDescending { it.second }
    }

    private fun directActionCandidates(normalized: String): List<Pair<AssistantAction, Float>> {
        return actionCatalog.mapNotNull { action ->
            val score = directPhraseScore(normalized, action.searchPhrases)
            if (score <= 0f) null else action to score
        }.sortedByDescending { it.second }
    }

    private fun <T> clearWinner(candidates: List<Pair<T, Float>>): T? {
        val top = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.getOrNull(1)
        if (top.second < 8f) return null
        if (runnerUp != null && top.second < runnerUp.second + 2f) return null
        return top.first
    }

    private fun bestFeatureMatch(normalized: String): AssistantFeature? {
        return featureCatalog.maxByOrNull { feature -> scoreFeature(normalized, feature) }
            ?.takeIf { scoreFeature(normalized, it) >= 0.72f }
    }

    private fun bestLenientFeatureMatch(normalized: String): AssistantFeature? {
        val candidates = featureCatalog
            .map { feature -> feature to scoreFeature(normalized, feature) }
            .filter { it.second >= 0.32f }
            .sortedByDescending { it.second }
        val top = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        if (second != null && top.second < second.second + 0.08f) return null
        return top.first
    }

    private fun bestRouteMatch(normalized: String): AssistantRoute? {
        return routeCatalog.maxByOrNull { route -> scoreCandidate(normalized, route.searchPhrases, route.searchTokens) }
            ?.takeIf { scoreCandidate(normalized, it.searchPhrases, it.searchTokens) >= 0.74f }
    }

    private fun bestActionMatch(normalized: String): AssistantAction? {
        return actionCatalog.maxByOrNull { action -> scoreCandidate(normalized, action.searchPhrases, action.searchTokens) }
            ?.takeIf { scoreCandidate(normalized, it.searchPhrases, it.searchTokens) >= 0.76f }
    }

    private fun directPhraseScore(normalized: String, phrases: List<String>): Float {
        val query = normalize(normalized)
        if (query.isBlank()) return 0f
        return phrases.maxOfOrNull { phrase ->
            val normalizedPhrase = normalize(phrase)
            when {
                normalizedPhrase.isBlank() -> 0f
                query == normalizedPhrase -> 10f + normalizedPhrase.length
                query.contains(normalizedPhrase) -> 5f + normalizedPhrase.length
                meaningfulTokens(query).containsAll(meaningfulTokens(normalizedPhrase)) && meaningfulTokens(normalizedPhrase).isNotEmpty() -> 3f + normalizedPhrase.length
                else -> 0f
            }
        } ?: 0f
    }

    private fun bestTrackerEventMatch(normalized: String): TrackerEventType? {
        return TrackerEventType.entries.maxByOrNull { event ->
            maxOf(
                directPhraseScore(normalized, listOf(event.key, translateTrackerEvent(event)) + trackerEventAliases(event)),
                scoreTokens(
                    normalized,
                    tokenize(event.key) +
                        tokenize(translateTrackerEvent(event)) +
                        trackerEventAliases(event).flatMap { tokenize(it) }
                )
            )
        }?.takeIf {
            maxOf(
                directPhraseScore(normalized, listOf(it.key, translateTrackerEvent(it)) + trackerEventAliases(it)),
                scoreTokens(
                    normalized,
                    tokenize(it.key) +
                        tokenize(translateTrackerEvent(it)) +
                        trackerEventAliases(it).flatMap { tokenize(it) }
                )
            ) >= 0.45f
        }
    }

    private fun bestTrackerActions(normalized: String): List<TrackerRuleAction> {
        return TrackerRuleAction.entries.filter { action ->
            scoreTokens(normalized, tokenize(action.key) + tokenize(translateTrackerAction(action))) >= 0.58f
        }
    }

    private fun trackerEventAliases(event: TrackerEventType): List<String> {
        return when (event.key) {
            "started_typing" -> listOf("typing", "started typing", "is typing")
            "stopped_typing" -> listOf("stopped typing", "typing stopped")
            "started_speaking" -> listOf("speaking", "started speaking", "someone speaks", "they speak")
            "stopped_speaking" -> listOf("stopped speaking", "speaking stopped")
            "message_read" -> listOf("opened chat", "read chat", "chat opened", "read messages", "showed chat")
            "snap_opened" -> listOf("opened snap", "viewed snap", "snap opened", "showed snap")
            "snap_screenshot" -> listOf("screenshot", "screenshots", "took screenshot", "screen shot")
            "snap_screen_record" -> listOf("screen record", "screen recording", "recorded screen")
            "message_saved" -> listOf("saved message", "saved in chat")
            "message_unsaved" -> listOf("unsaved message", "removed save")
            else -> emptyList()
        }
    }

    private fun resolveScopeTargets(normalized: String): List<ScopeTarget> {
        if (normalized.contains("for all")) return emptyList()
        val targets = mutableListOf<ScopeTarget>()
        context.database.getFriends().forEach { friend ->
            val label = friend.displayName?.takeIf { it.isNotBlank() } ?: friend.mutableUsername
            val tokens = tokenize(label) + tokenize(friend.mutableUsername)
            if (scoreTokens(normalized, tokens) >= 0.78f) {
                targets += ScopeTarget(friend.userId, label)
            }
        }
        context.database.getGroups().forEach { group ->
            if (scoreTokens(normalized, tokenize(group.name)) >= 0.8f) {
                targets += ScopeTarget(group.conversationId, group.name)
            }
        }
        return targets.distinctBy { it.id }.take(3)
    }

    private fun translateTrackerEvent(event: TrackerEventType): String {
        return translated("tracker_events.${event.key}", humanize(event.key))
    }

    private fun translateTrackerAction(action: TrackerRuleAction): String {
        return translated("tracker_actions.${action.key}", humanize(action.key))
    }

    private fun translated(path: String, fallback: String): String {
        return runCatching { context.translation[path] }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != path }
            ?: fallback
    }

    private fun buildDefaultRuleName(event: TrackerEventType, targets: List<ScopeTarget>): String {
        val base = translateTrackerEvent(event)
        val targetSuffix = targets.firstOrNull()?.displayName?.let { " - $it" }.orEmpty()
        return "$base Rule$targetSuffix"
    }

    private fun parseDesiredBoolean(normalized: String): Boolean? {
        return when {
            listOf("turn off", "disable", "disabled", "deactivate", "hide", "switch off").any { normalized.contains(it) } -> false
            Regex("\\b(turn|switch)\\b.*\\boff\\b").containsMatchIn(normalized) -> false
            listOf("turn on", "enable", "enabled", "activate", "show", "switch on").any { normalized.contains(it) } -> true
            Regex("\\b(turn|switch)\\b.*\\bon\\b").containsMatchIn(normalized) -> true
            else -> null
        }
    }

    private fun matchUniqueOption(propertyKey: PropertyKey<*>, candidate: String): String? {
        val values = uniqueOptionValues(propertyKey)
        val normalizedCandidate = normalize(candidate)
        val compactCandidate = compactNormalize(candidate)
        return values.maxByOrNull { option ->
            val terms = uniqueOptionTerms(propertyKey, option)
            val tokenScore = scoreTokens(normalizedCandidate, terms.flatMap { tokenize(it) })
            val compactScore = if (terms.any { compactNormalize(it) == compactCandidate }) 1f else 0f
            max(tokenScore, compactScore)
        }?.takeIf { option ->
            val terms = uniqueOptionTerms(propertyKey, option)
            val tokenScore = scoreTokens(normalizedCandidate, terms.flatMap { tokenize(it) })
            val compactScore = if (terms.any { compactNormalize(it) == compactCandidate }) 1f else 0f
            max(tokenScore, compactScore) >= 0.52f
        }
    }

    private fun uniqueOptionValues(propertyKey: PropertyKey<*>): List<String> {
        return buildList {
            propertyKey.params.disabledKey?.let { add(it) }
            featureCatalog.firstOrNull { it.propertyKey == propertyKey }
                ?.propertyValue
                ?.defaultValues
                ?.forEach { add(it.toString()) }
        }.distinct()
    }

    private fun uniqueOptionTerms(propertyKey: PropertyKey<*>, option: String): List<String> {
        return buildList {
            add(option)
            add(translateOption(propertyKey, option))
            add(option.replace('_', ' '))
            add(option.replace("-", " "))
            if (option == "ad_free") {
                add("adfree")
                add("ad free")
                add("without ads")
                add("no ads")
            }
            if (option == "not_subscribed") {
                add("disabled")
                add("off")
                add("not subscribed")
                add("no plus")
            }
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun resolveBooleanUniqueOption(propertyKey: PropertyKey<*>, enabled: Boolean): String? {
        val values = uniqueOptionValues(propertyKey)
        if (!enabled) {
            return values.firstOrNull { isDisabledUniqueOption(propertyKey, it) }
                ?: propertyKey.params.disabledKey
                ?: "null"
        }
        val enabledOptions = values.filterNot { isDisabledUniqueOption(propertyKey, it) }
        return when (enabledOptions.size) {
            0 -> null
            1 -> enabledOptions.first()
            else -> UNIQUE_OPTION_AMBIGUOUS
        }
    }

    private fun isDisabledUniqueOption(propertyKey: PropertyKey<*>, option: String): Boolean {
        val normalized = normalize(option)
        return option == propertyKey.params.disabledKey ||
            option == "null" ||
            normalized in setOf("disabled", "disable", "off", "none", "not subscribed", "not subscribed")
    }

    private fun buildUniqueOptionFollowUp(feature: AssistantFeature, propertyKey: PropertyKey<*>): String {
        val options = uniqueOptionValues(propertyKey)
            .filterNot { isDisabledUniqueOption(propertyKey, it) }
            .joinToString(" or ") { translateOption(propertyKey, it) }
        return "Which ${feature.name} option: $options?"
    }

    private fun translateOption(propertyKey: PropertyKey<*>, option: String): String {
        return runCatching { propertyKey.propertyOption(context.translation, option) }.getOrElse { humanize(option) }
    }

    private fun describeCurrentValue(propertyKey: PropertyKey<*>, propertyValue: PropertyValue<*>): String {
        return when (propertyKey.dataType.type) {
            DataProcessors.Type.BOOLEAN -> {
                val current = propertyValue.getNullable() as? Boolean ?: false
                if (current) "Enabled" else "Disabled"
            }

            DataProcessors.Type.CONTAINER -> "Section"

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                val current = propertyValue.getNullable()?.toString() ?: propertyKey.params.disabledKey ?: "disabled"
                translateOption(propertyKey, current)
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                val current = propertyValue.getNullable() as? List<*>
                current?.joinToString(", ") ?: "No items selected"
            }

            else -> propertyValue.getNullable()?.toString() ?: "Not set"
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun compactNormalize(value: String): String {
        return normalize(value).replace(" ", "")
    }

    private fun tokenize(value: String): List<String> {
        return normalize(value).split(' ').filter { it.isNotBlank() }
    }

    private fun meaningfulTokens(value: String): List<String> {
        return tokenize(value).filter { it !in stopWords && it.length > 1 }
    }

    private fun scoreFeature(query: String, feature: AssistantFeature): Float {
        return scoreCandidate(query, feature.searchPhrases, feature.searchTokens)
    }

    private fun scoreCandidate(query: String, phrases: List<String>, tokens: List<String>): Float {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return 0f
        if (phrases.any { normalize(it) == normalizedQuery }) return 1.2f
        if (phrases.any { phrase ->
                val normalizedPhrase = normalize(phrase)
                normalizedPhrase.isNotBlank() && normalizedQuery.contains(normalizedPhrase)
            }) {
            return 1.05f
        }

        val queryTokens = meaningfulTokens(normalizedQuery)
        if (queryTokens.isEmpty()) return 0f
        val overlap = queryTokens.count { queryToken ->
            tokens.any { token -> token == queryToken || token.contains(queryToken) || queryToken.contains(token) }
        }
        val requiredOverlap = when {
            queryTokens.size >= 4 -> 2
            else -> 1
        }
        if (overlap < requiredOverlap) return 0f

        val coverage = overlap.toFloat() / queryTokens.size.toFloat()
        val fuzzy = tokens.maxOfOrNull { similarity(normalizedQuery, it) } ?: 0f
        return max(coverage, fuzzy)
    }

    private fun scoreTokens(query: String, tokens: List<String>): Float {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return 0f
        val fullText = tokens.joinToString(" ")
        if (fullText.contains(normalizedQuery)) return 1f
        val queryTokens = tokenize(normalizedQuery)
        val coverage = if (queryTokens.isEmpty()) 0f else {
            queryTokens.count { queryToken ->
                tokens.any { token -> token.contains(queryToken) || queryToken.contains(token) }
            }.toFloat() / queryTokens.size.toFloat()
        }
        val fuzzy = tokens.maxOfOrNull { similarity(normalizedQuery, it) } ?: 0f
        return max(coverage, fuzzy)
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        val distance = levenshtein(a, b)
        val maxLength = max(a.length, b.length)
        return 1f - distance.toFloat() / maxLength.toFloat()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), previous[j] + cost)
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }
        return previous[b.length]
    }

    private fun humanize(value: String): String {
        return value.replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }
}

@Composable
fun ManagerAssistantEntry(
    context: RemoteSideContext,
    routes: Routes,
    style: ManagerAssistantTriggerStyle,
    shrinkFactor: Float = 1f,
    modifier: Modifier = Modifier,
    initialUserMessage: String? = null,
    showImprovementLogging: Boolean = true
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    ManagerAssistantTrigger(style = style, shrinkFactor = shrinkFactor, modifier = modifier) {
        isOpen = true
    }
    if (isOpen) {
        ManagerAssistantDialog(
            context = context,
            routes = routes,
            initialUserMessage = initialUserMessage,
            showImprovementLogging = showImprovementLogging,
            onDismiss = { isOpen = false }
        )
    }
}

@Composable
private fun ManagerAssistantTrigger(
    style: ManagerAssistantTriggerStyle,
    shrinkFactor: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val border = when (style) {
        ManagerAssistantTriggerStyle.DEFAULT -> BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ManagerAssistantTriggerStyle.APHELION -> BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        )
    }
    Surface(
        modifier = modifier.height(36.dp).defaultMinSize(minWidth = 36.dp),
        shape = RoundedCornerShape(40.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = border
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = (10 * shrinkFactor.coerceIn(0.55f, 1f)).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = "Open assistant", tint = Color.White, modifier = Modifier.size(20.dp))
            val labelAlpha = if (style == ManagerAssistantTriggerStyle.APHELION) (shrinkFactor - 0.1f).coerceIn(0f, 1f) else 1f
            if (labelAlpha > 0.02f) {
                Spacer(modifier = Modifier.width((8 * shrinkFactor).dp))
                Text(
                    text = "AI",
                    color = Color.White.copy(alpha = labelAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ManagerAssistantDialog(
    context: RemoteSideContext,
    routes: Routes,
    initialUserMessage: String? = null,
    showImprovementLogging: Boolean = true,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val engine = remember(context, routes) { ManagerAssistantEngine(context, routes) }
    val messages = remember {
        mutableStateListOf(
            AssistantMessage(
                AssistantRole.ASSISTANT,
                "Ask me to explain a feature, open a section, change a setting, or create a friend tracker rule."
            )
        )
    }
    val suggestions = remember {
        mutableStateListOf(
            "Open logs",
            "What does Friend Tracker do?",
            "Enable haptic feedback",
            "Create a friend tracker rule for typing"
        )
    }
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var pendingAssistantFollowUp by rememberSaveable { mutableStateOf<String?>(null) }
    var trainingLogUri by rememberSaveable {
        mutableStateOf(context.sharedPreferences.getString(TRAINING_LOG_URI_PREF, "").orEmpty())
    }
    fun appendTrainingLog(userText: String, assistantText: String) {
        val uri = trainingLogUri.takeIf { it.isNotBlank() } ?: return
        runCatching {
            context.androidContext.contentResolver.openOutputStream(Uri.parse(uri), "wa")?.bufferedWriter()?.use { writer ->
                writer.appendLine("USER: $userText")
                writer.appendLine("ASSISTANT: $assistantText")
                writer.appendLine("---")
            }
        }.onFailure {
            context.log.error("Failed to append assistant training log", it)
        }
    }

    fun chooseTrainingLogFile() {
        routes.activityLauncher.saveFile("purrfectsnap-ai-training-log.txt", "text/plain") { uri ->
            if (uri.isNotBlank()) {
                trainingLogUri = uri
                context.sharedPreferences.edit().putString(TRAINING_LOG_URI_PREF, uri).apply()
                context.shortToast("Assistant training log enabled")
            }
        }
    }

    fun submitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isWorking) return
        messages += AssistantMessage(AssistantRole.USER, trimmed)
        input = ""
        isWorking = true
        scope.launch {
            val normalizedReply = trimmed.lowercase().trim()
            val resolvedQuery = when {
                pendingAssistantFollowUp == "fake_snap_guide" &&
                    normalizedReply in setOf("yes", "yeah", "yep", "sure", "ok", "okay", "guide me", "do it") ->
                    "how to remove the media upload tag"
                pendingAssistantFollowUp == "screenshot_detection_enable" &&
                    normalizedReply in setOf("yes", "yeah", "yep", "sure", "ok", "okay", "enable it", "do it") ->
                    "enable bypass screenshot detection"
                else -> trimmed
            }
            pendingAssistantFollowUp = null
            val result = runCatching {
                withContext(Dispatchers.Default) { engine.handle(resolvedQuery) }
            }.getOrElse {
                context.log.error("Assistant query failed", it)
                AssistantResult("Assistant failed to process that request. Please try a clearer feature, setting, or section name.")
            }
            result.execute?.invoke()
            messages += AssistantMessage(AssistantRole.ASSISTANT, result.reply)
            appendTrainingLog(trimmed, result.reply)
            pendingAssistantFollowUp = when {
                result.reply.contains("Do you want me to guide you?", ignoreCase = true) -> "fake_snap_guide"
                result.reply.contains("Do you want me to enable it?", ignoreCase = true) -> "screenshot_detection_enable"
                else -> null
            }
            isWorking = false
        }
    }

    LaunchedEffect(messages.size, isWorking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(initialUserMessage) {
        initialUserMessage?.takeIf { it.isNotBlank() }?.let {
            if (messages.none { message -> message.role == AssistantRole.USER }) {
                submitQuery(it)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.42f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, RoundedCornerShape(26.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 680.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White)
                                }
                            }
                            Column {
                                Text(
                                    text = "PurrfectSnap AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Text("x", color = Color.White, fontSize = 20.sp)
                        }
                    }

                    if (showImprovementLogging) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { chooseTrainingLogFile() },
                                label = {
                                    Text(
                                        if (trainingLogUri.isBlank()) "Contribute assistant improvement data?"
                                        else "Assistant improvement logging enabled",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    labelColor = Color.White,
                                    leadingIconContentColor = Color.White
                                ),
                                leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message ->
                            AssistantBubble(message = message)
                        }
                        if (isWorking) {
                            item {
                                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.06f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                        Text("Working on that...", color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    if (messages.size <= 1) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = { submitQuery(suggestion) },
                                    label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White,
                                        leadingIconContentColor = Color.White
                                    ),
                                    leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask or command the app...", color = PurrfectPalette.textSecondary) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitQuery(input) }),
                            maxLines = 4
                        )
                        Button(
                            onClick = { submitQuery(input) },
                            enabled = input.isNotBlank() && !isWorking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(56.dp).wrapContentWidth()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage) {
    val isUser = message.role == AssistantRole.USER
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp
    )
    val background = if (isUser) {
        Brush.linearGradient(
            listOf(
                PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f))))
        ) {
            Box(
                modifier = Modifier.background(background, shape).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(text = message.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
