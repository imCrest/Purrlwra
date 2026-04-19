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
            "messaging.bypass_screenshot_detection",
            "messaging.anonymous_story_viewing",
            "messaging.prevent_story_rewatch_indicator",
            "messaging.hide_peek_a_peek",
            "messaging.hide_bitmoji_presence",
            "messaging.spoof_viewing_gallery_presence",
            "messaging.spoof_reply_camera_presence",
            "messaging.hide_typing_notifications",
            "messaging.unlimited_snap_view_time",
            "messaging.auto_mark_as_read",
            "messaging.mark_snap_as_seen_button",
            "messaging.mark_snap_as_seen_processing_mode",
            "messaging.mark_snap_as_seen_limit",
            "messaging.skip_when_marking_as_seen",
            "messaging.loop_media_playback",
            "messaging.disable_replay_in_ff",
            "messaging.half_swipe_notifier",
            "messaging.call_start_confirmation",
            "messaging.block_calls",
            "messaging.call_metadata_notifier",
            "messaging.conversation_sound_effects_style",
            "messaging.unlimited_conversation_pinning",
            "messaging.disable_snap_mode_restrictions",
            "messaging.auto_save_messages_in_conversations",
            "messaging.unsaveable_messages",
            "messaging.prevent_message_sending",
            "messaging.friend_mutation_notifier",
            "messaging.better_notifications",
            "messaging.notification_blacklist",
            "messaging.message_logger",
            "messaging.gallery_media_send_override",
            "messaging.scheduled_send_allow_running_in_background",
            "messaging.strip_media_metadata",
            "messaging.bypass_message_retention_policy",
            "messaging.bypass_message_action_restrictions",
            "messaging.remove_groups_locked_status",
            "messaging.double_tap_chat_action",
            "messaging.double_tap_chat_action_custom_emoji",
            "messaging.auto_reply",
            "messaging.auto_delete_sent_messages",
            "messaging.auto_open_snaps",
            "messaging.pre_fetch_snaps",
            "messaging.instant_translation",
            "experimental.native_hooks",
            "experimental.spoof",
            "experimental.convert_message_locally",
            "experimental.media_file_picker",
            "experimental.story_logger",
            "experimental.account_switcher",
            "experimental.network_optimization",
            "experimental.better_transcript",
            "experimental.voice_note_auto_play",
            "experimental.friend_notes",
            "experimental.context_menu_fix",
            "experimental.cof_experiments",
            "experimental.app_lock",
            "experimental.infinite_story_boost",
            "experimental.meo_passcode_bypass",
            "experimental.no_friend_score_delay",
            "experimental.best_friend_pinning",
            "experimental.e2ee",
            "experimental.hidden_snapchat_plus_features",
            "experimental.custom_streaks_expiration_format",
            "experimental.add_friend_source_spoof",
            "experimental.prevent_forced_logout",
            "experimental.snapscore_changes",
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
