package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice
import cock.crest.purrfectsnap.lite.common.config.RES_OBF_VERSION_CHECK
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType

class UserInterfaceTweaks : ConfigContainer() {
    class BootstrapOverride : ConfigContainer() {
        companion object {
            val tabs = arrayOf("map", "chat", "camera", "discover", "spotlight")
        }

        val appAppearance = unique("app_appearance", "always_light", "always_dark") { requireRestart() }
        val homeTab = unique("home_tab", *tabs) { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    }

    inner class FriendFeedMessagePreview : ConfigContainer(hasGlobalState = true) {
        val amount = integer("amount", defaultValue = 1)
    }


    val friendFeedMenuButtons = multiple(
        "friend_feed_menu_buttons","conversation_info", "mark_chat_as_read", "mark_snaps_as_seen", "mark_stories_as_seen_locally", *MessagingRuleType.entries.filter { it.showInFriendMenu }.map { it.key }.toTypedArray()
    ).apply {
        set(mutableListOf("conversation_info", MessagingRuleType.STEALTH.key))
    }
    val autoCloseFriendFeedMenu = boolean("auto_close_friend_feed_menu")
    val friendFeedMessagePreview = container("friend_feed_message_preview", FriendFeedMessagePreview()) { requireRestart() }
    val snapPreview = boolean("snap_preview") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val bootstrapOverride = container("bootstrap_override", BootstrapOverride()) { requireRestart() }
    val forceAmoledTheme = boolean("force_amoled_theme") { requireRestart() }
    val mapFriendNameTags = boolean("map_friend_nametags") { requireRestart() }
    val preventMessageListAutoScroll = boolean("prevent_message_list_auto_scroll") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val streakExpirationInfo = boolean("streak_expiration_info") { requireRestart() }
    val hideFriendFeedEntry = boolean("hide_friend_feed_entry") { requireRestart() }
    val hideStreakRestore = boolean("hide_streak_restore") { requireRestart() }
    val hideQuickAddSuggestions = boolean("hide_quick_add_suggestions") { requireRestart() }
    val hideStorySuggestions = multiple("hide_story_suggestions", "hide_suggested_friend_stories", "hide_my_stories") { requireRestart() }
    val hideUiComponents = multiple("hide_ui_components",
        "hide_voice_record_button",
        "hide_stickers_button",
        "hide_live_location_share_button",
        "hide_chat_call_buttons",
        "hide_profile_call_buttons",
        "hide_unread_chat_hint",
        "hide_post_to_story_buttons",
        "hide_billboard_prompt",
        "hide_snapchat_plus_gift_reminders",
        "hide_map_reactions",
    ) { requireRestart(); versionCheck = RES_OBF_VERSION_CHECK }
    val operaMediaQuickInfo = boolean("opera_media_quick_info") { requireRestart() }
    val storyCounter = boolean("story_counter") { requireRestart() }
    val storySourceIndicator = boolean("story_source_indicator") { requireRestart() }
    val storySnapJump = boolean("story_snap_jump") { requireRestart() }
    val oldBitmojiSelfie = unique("old_bitmoji_selfie", "2d", "3d") { requireCleanCache() }
    val disableSpotlight = boolean("disable_spotlight") { requireRestart() }
    val verticalStoryViewer = boolean("vertical_story_viewer") { requireRestart() }
    val messageIndicators = multiple("message_indicators", "encryption_indicator", "platform_indicator", "location_indicator", "ovf_editor_indicator", "director_mode_indicator") { requireRestart() }
    val stealthModeIndicator = boolean("stealth_mode_indicator") { requireRestart() }
    val editTextOverride = multiple("edit_text_override", "multi_line_chat_input", "bypass_text_input_limit") {
        requireRestart(); addNotices(FeatureNotice.BAN_RISK, FeatureNotice.INTERNAL_BEHAVIOR)
    }
    val preventForcedKeyboard = boolean("prevent_forced_keyboard") { requireRestart() }
    val settingsMenu = unique("settings_menu", "default", "legacy") { requireRestart() }.apply { set("default") }
    val chatHoldKillActions = multiple("chat_hold_kill_actions", "kill_snapchat", "kill_purrfectsnap") { requireRestart() }

    inner class SpoofSnapScore : ConfigContainer(hasGlobalState = true) {
        val customSnapScore = string("custom_snap_score") { 
            requireRestart()
            inputCheck = { input -> 
                if (input.isEmpty()) true
                else input.replace(Regex("[^0-9]"), "").isNotEmpty()
            }
        }
    }

    val spoofSnapScore = container("spoof_snap_score", SpoofSnapScore()) { requireRestart() }
}
