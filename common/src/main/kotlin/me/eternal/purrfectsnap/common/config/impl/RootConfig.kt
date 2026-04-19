package cock.crest.purrfectsnap.lite.common.config.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.DataProcessors
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice
import cock.crest.purrfectsnap.lite.common.config.PropertyValue

class RootConfig : ConfigContainer() {
    companion object {
        private val LITE_ALLOWED_PATHS = setOf(
            "downloader.force_image_format",
            "downloader.download_profile_pictures",
            "downloader.opera_download_button",
            "downloader.story_snap_list_download",
            "downloader.download_context_menu",
            "user_interface.prevent_message_list_auto_scroll",
            "user_interface.hide_story_suggestions",
            "user_interface.story_source_indicator",
            "user_interface.message_indicators",
            "messaging.bypass_screenshot_detection",
            "messaging.hide_peek_a_peek",
            "messaging.prevent_story_rewatch_indicator",
            "messaging.hide_bitmoji_presence",
            "messaging.spoof_viewing_gallery_presence",
            "messaging.spoof_reply_camera_presence",
            "messaging.hide_typing_notifications",
            "messaging.unlimited_snap_view_time",
            "messaging.half_swipe_notifier",
            "messaging.disable_snap_mode_restrictions",
            "messaging.prevent_message_sending",
            "messaging.friend_mutation_notifier",
            "messaging.unsaveable_messages",
            "messaging.better_notifications",
            "messaging.message_logger",
            "messaging.gallery_media_send_override",
            "messaging.bypass_message_action_restrictions",
            "messaging.bypass_message_retention_policy",
            "global.disable_confirmation_dialogs",
            "global.disable_metrics",
            "global.block_ads",
            "global.disable_story_sections",
            "camera.hevc_recording",
            "camera.force_camera_source_encoding",
            "experimental.media_file_picker",
            "experimental.story_logger",
            "experimental.cof_experiments",
            "experimental.no_friend_score_delay",
            "experimental.snapscore_changes",
            "rules.stealth",
            "rules.chat_stealth",
            "rules.snap_stealth",
            "rules.hide_typing_indicator",
            "rules.auto_download",
            "rules.unsaveable_messages",
            "rules.message_logger",
        )

        private val INTERNAL_SUPPORT_PATHS = setOf(
            "global.update_settings",
            "global.ui_settings",
        )

    }

    val downloader = container("downloader", DownloaderConfig()) { icon = Icons.Default.Download }
    val userInterface = container("user_interface", UserInterfaceTweaks()) { icon = Icons.Default.RemoveRedEye }
    val messaging = container("messaging", MessagingTweaks()) { icon = Icons.AutoMirrored.Default.Send }
    val global = container("global", Global()) { icon = Icons.Default.MiscellaneousServices }
    val rules = container("rules", Rules()) { icon = Icons.AutoMirrored.Default.Rule }
    val camera = container("camera", Camera()) { icon = Icons.Default.Camera; requireRestart() }
    val streaksReminder = container("streaks_reminder", StreaksReminderConfig()) { icon = Icons.Default.Alarm }
    val experimental = container("experimental", Experimental()) { icon = Icons.Default.Science; addNotices(
        FeatureNotice.UNSTABLE) }
    val scripting = container("scripting", Scripting()) { icon = Icons.Default.DataObject }
    val friendTracker = container("friend_tracker", FriendTrackerConfig()) { icon = Icons.Default.PersonSearch }

    fun applyLiteProfile() {
        pruneContainer(pathPrefix = null)
        runCatching {
            downloader.forceImageFormat.set("jpg")
            userInterface.hideStorySuggestions.set(mutableListOf("hide_suggested_friend_stories"))
            userInterface.messageIndicators.set(
                mutableListOf(
                    "encryption_indicator",
                    "ovf_editor_indicator",
                    "director_mode_indicator"
                )
            )
            messaging.preventMessageSending.set(
                mutableListOf(
                    "chat_screenshot",
                    "chat_screen_record",
                    "camera_roll_save"
                )
            )
            messaging.betterNotifications.chatPreview.set(true)
            messaging.betterNotifications.mediaPreview.set(
                mutableListOf("SNAP", "EXTERNAL_MEDIA", "STICKER", "SHARE", "TINY_SNAP", "MAP_REACTION")
            )
            global.disableStorySections.set(mutableListOf("suggested_stories"))
            experimental.cofExperiments.set(Experimental.cofExperimentList.take(3).toMutableList())
        }
    }

    private fun ConfigContainer.pruneContainer(pathPrefix: String?) {
        if (pathPrefix != null && hasGlobalState && LITE_ALLOWED_PATHS.none { isPathOrDescendant(pathPrefix, it) }) {
            globalState = false
        }

        properties.toMap().forEach { (key, value) ->
            val fullPath = pathPrefix?.let { "$it.${key.name}" } ?: key.name

            if (key.dataType.type == DataProcessors.Type.CONTAINER) {
                val childContainer = value.get() as ConfigContainer
                childContainer.pruneContainer(fullPath)

                if (!shouldKeepContainer(fullPath)) {
                    childContainer.disableRecursively()
                    properties.remove(key)
                }

                return@forEach
            }

            if (!shouldKeepProperty(fullPath)) {
                disablePropertyValue(value, key.dataType.type)
                properties.remove(key)
            }
        }
    }

    private fun ConfigContainer.disableRecursively() {
        if (hasGlobalState) {
            globalState = false
        }

        properties.forEach { (key, value) ->
            if (key.dataType.type == DataProcessors.Type.CONTAINER) {
                (value.get() as ConfigContainer).disableRecursively()
            } else {
                disablePropertyValue(value, key.dataType.type)
            }
        }
    }

    private fun disablePropertyValue(value: PropertyValue<*>, type: DataProcessors.Type) {
        when (type) {
            DataProcessors.Type.BOOLEAN -> value.setAny(false)
            DataProcessors.Type.STRING -> value.setAny("")
            DataProcessors.Type.INTEGER -> value.setAny(0)
            DataProcessors.Type.FLOAT -> value.setAny(0f)
            DataProcessors.Type.STRING_MULTIPLE_SELECTION -> value.setAny(mutableListOf<String>())
            DataProcessors.Type.STRING_UNIQUE_SELECTION -> value.setAny("null")
            DataProcessors.Type.MAP_COORDINATES -> value.setAny(0.0 to 0.0)
            DataProcessors.Type.INT_COLOR -> value.setAny(null)
            DataProcessors.Type.CONTAINER -> Unit
        }
    }

    private fun shouldKeepProperty(fullPath: String): Boolean {
        return LITE_ALLOWED_PATHS.any { isPathOrDescendant(fullPath, it) } ||
            INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(fullPath, it) }
    }

    private fun shouldKeepContainer(fullPath: String): Boolean {
        return shouldKeepProperty(fullPath) ||
            LITE_ALLOWED_PATHS.any { isPathOrDescendant(it, fullPath) } ||
            INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(it, fullPath) }
    }

    private fun isPathOrDescendant(path: String, prefix: String): Boolean {
        return path == prefix || path.startsWith("$prefix.")
    }
}
