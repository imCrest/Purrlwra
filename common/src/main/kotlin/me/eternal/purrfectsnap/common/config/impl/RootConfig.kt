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
        private val ALLOWED_FEATURE_PATHS = setOf(
            "camera.disable_cameras",
            "camera.immersive_camera_preview",
            "camera.camera_tweaks",
            "camera.custom_resolution",
            "camera.startup_default_camera",
            "camera.video_record_timer",
            "camera.unlock_zoom_limit",
            "downloader.save_folder",
            "downloader.auto_download_sources",
            "downloader.prevent_self_auto_download",
            "downloader.merge_overlays",
            "downloader.auto_download_voice_notes",
            "downloader.custom_path_format",
            "downloader.file_hash_check",
            "downloader.ffmpeg_options.custom_audio_codec",
            "downloader.call_recorder.call_recorder",
            "downloader.call_recorder.auto_start_recording",
            "downloader.chat_wallpaper_downloader",
            "experimental.account_switcher",
            "experimental.account_switcher.auto_backup_current_account",
            "experimental.add_friend_source_spoof",
            "experimental.better_transcript.force_transcription",
            "experimental.better_transcript.preferred_transcription_lang",
            "experimental.better_transcript.notification_transcript",
            "experimental.e2ee.force_message_encryption",
            "experimental.e2ee.hide_conversation_toolbox_ui",
            "experimental.hidden_snapchat_plus_features",
            "experimental.native_hooks.composer_hooks.show_first_created_username",
            "experimental.native_hooks.composer_hooks.bypass_camera_roll_limit",
            "experimental.native_hooks.composer_hooks.custom_self_destruct_snap_delay",
            "experimental.native_hooks.composer_hooks.composer_console",
            "experimental.native_hooks.composer_hooks.composer_logs",
            "experimental.native_hooks.disable_bitmoji",
            "experimental.native_hooks.debug_font_redirect",
            "experimental.native_hooks.custom_emoji_font",
            "experimental.native_hooks.custom_shared_library",
            "experimental.network_optimization",
            "experimental.spoof.device_model",
            "experimental.spoof.force_wifi_transport_flag",
            "experimental.spoof.remove_vpn_transport_flag",
            "experimental.spoof.spoof_device",
            "experimental.spoof.spoof_device_id.spoof_android_id",
            "experimental.spoof.spoof_device_id.custom_android_id",
            "experimental.spoof.randomize_device_profile.show_activation_overlay",
            "experimental.spoof.randomize_device_profile.randomize_ip_address",
            "experimental.spoof.randomize_device_profile.locale_options.time.time_zone_id",
            "experimental.spoof.randomize_device_profile.locale_options.time.time_zone_display_name",
            "experimental.spoof.randomize_device_profile.locale_options.time.auto_time",
            "experimental.spoof.randomize_device_profile.locale_options.time.auto_time_zone",
            "experimental.spoof.randomize_device_profile.persistent_app_language",
            "experimental.spoof.randomize_device_profile.generate_fresh_profile_action",
            "experimental.spoof.randomize_device_profile.view_current_profile_action",
            "experimental.spoof.randomize_device_profile.backup_profile_action",
            "experimental.spoof.randomize_device_profile.restore_profile_action",
            "experimental.spoof.randomize_device_profile.profile_generation_token",
            "experimental.spoof.randomize_device_profile.current_profile_snapshot",
            "experimental.voice_note_auto_play",
            "global.better_location.google_maps_api_key",
            "global.better_location.walk_radius",
            "global.better_location.always_update_location",
            "global.better_location.suspend_location_updates",
            "global.better_location.spoof_battery_level",
            "global.better_location.spoof_headphones",
            "global.block_ads",
            "global.disable_memories_snap_feed",
            "global.spotlight_creator_info",
            "global.bypass_video_length_restriction",
            "global.hide_active_music",
            "global.disable_snap_splitting",
            "global.update_settings.update_check_frequency",
            "global.update_settings.update_channel",
            "global.ui_settings.use_system_toasts",
            "messaging.hide_bitmoji_presence",
            "messaging.spoof_viewing_gallery_presence",
            "messaging.spoof_reply_camera_presence",
            "messaging.hide_typing_notifications",
            "messaging.auto_mark_as_read",
            "messaging.block_calls",
            "messaging.call_metadata_notifier",
            "messaging.unlimited_conversation_pinning",
            "messaging.auto_save_messages_in_conversations",
            "messaging.better_notifications.media_caption",
            "messaging.better_notifications.stacked_media_messages",
            "messaging.better_notifications.download_button",
            "messaging.better_notifications.mark_as_read_and_save_in_chat",
            "messaging.message_logger.message_filter",
            "messaging.auto_reply",
            "messaging.auto_reply.auto_trigger_config.friendSpecificGreeting",
            "messaging.auto_reply.auto_trigger_config.auto_reply_content_types",
            "messaging.auto_open_snaps.compact_notification",
            "messaging.auto_open_snaps.show_lifetime_stats",
            "messaging.auto_open_snaps.thermal_protection",
            "messaging.auto_open_snaps.only_on_wifi",
            "messaging.auto_open_snaps.only_when_idle",
            "messaging.pre_fetch_snaps",
            "messaging.strip_media_metadata",
            "messaging.instant_translation.enabled",
            "messaging.instant_translation.translate_on_tap",
            "rules.auto_reply",
            "scripting.module_folder",
            "scripting.auto_reload",
            "scripting.disable_log_anonymization",
            "scripting.disable_optimization",
            "streaks_reminder.group_notifications",
            "user_interface.auto_close_friend_feed_menu",
            "user_interface.friend_feed_message_preview",
            "user_interface.snap_preview",
            "user_interface.bootstrap_override.app_appearance",
            "user_interface.bootstrap_override.home_tab",
            "user_interface.force_amoled_theme",
            "user_interface.prevent_message_list_auto_scroll",
            "user_interface.streak_expiration_info",
            "user_interface.hide_friend_feed_entry",
            "user_interface.hide_streak_restore",
            "user_interface.hide_quick_add_suggestions",
            "user_interface.story_counter",
            "user_interface.story_source_indicator",
            "user_interface.story_snap_jump",
            "user_interface.stealth_mode_indicator",
            "user_interface.edit_text_override",
            "user_interface.vertical_story_viewer",
            "user_interface.spoof_snap_score.custom_snap_score",
        )

        private val INTERNAL_SUPPORT_PATHS = setOf(
            "global.update_settings",
            "global.ui_settings",
        )

        init {
            check(ALLOWED_FEATURE_PATHS.size == 122) {
                "Expected 122 allowed lite features, found ${ALLOWED_FEATURE_PATHS.size}"
            }
        }
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
        if (pathPrefix != null && hasGlobalState && pathPrefix !in ALLOWED_FEATURE_PATHS) {
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
        return fullPath in ALLOWED_FEATURE_PATHS || INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(fullPath, it) }
    }

    private fun shouldKeepContainer(fullPath: String): Boolean {
        return shouldKeepProperty(fullPath) ||
            ALLOWED_FEATURE_PATHS.any { isPathOrDescendant(it, fullPath) } ||
            INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(it, fullPath) }
    }

    private fun isPathOrDescendant(path: String, prefix: String): Boolean {
        return path == prefix || path.startsWith("$prefix.")
    }
}
