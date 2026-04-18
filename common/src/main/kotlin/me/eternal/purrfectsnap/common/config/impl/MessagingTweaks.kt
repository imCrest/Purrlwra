package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.ConfigFlag
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice
import cock.crest.purrfectsnap.lite.common.config.PropertyValue
import cock.crest.purrfectsnap.lite.common.data.NotificationType
import cock.crest.purrfectsnap.lite.common.util.PURGE_DISABLED_KEY
import cock.crest.purrfectsnap.lite.common.util.PURGE_TRANSLATION_KEY
import cock.crest.purrfectsnap.lite.common.util.PURGE_VALUES

class MessagingTweaks : ConfigContainer() {
    companion object {
        const val DELETED_MESSAGE_COLOR = 0x6Eb71c1c;
    }

    inner class HalfSwipeNotifierConfig : ConfigContainer(hasGlobalState = true) {
        val minDuration: PropertyValue<Int> = integer("min_duration", defaultValue = 0) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null && maxDuration.get() >= it.toInt() }
        }
        val maxDuration: PropertyValue<Int> = integer("max_duration", defaultValue = 20) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null && minDuration.get() <= it.toInt() }
        }
    }

    inner class MessageLoggerConfig : ConfigContainer(hasGlobalState = true) {
        val keepMyOwnMessages = boolean("keep_my_own_messages")
        val autoPurge = unique("auto_purge", *PURGE_VALUES) {
            customOptionTranslationPath = PURGE_TRANSLATION_KEY
            disabledKey = PURGE_DISABLED_KEY
        }.apply { set("3_days") }
        val messageFilter = multiple("message_filter", "CHAT",
            "SNAP",
            "NOTE",
            "EXTERNAL_MEDIA",
            "STICKER"
        ) {
            customOptionTranslationPath = "content_type"
        }
        val deletedMessageColor = color("deleted_message_color", DELETED_MESSAGE_COLOR)
    }

    class BetterNotifications: ConfigContainer() {
        val groupNotifications = boolean("group_notifications")
        val chatPreview = boolean("chat_preview")
        val mediaPreview = multiple("media_preview", "SNAP", "EXTERNAL_MEDIA", "STICKER", "SHARE", "TINY_SNAP", "MAP_REACTION") {
            customOptionTranslationPath = "content_type"
        }
        val mediaCaption = boolean("media_caption")
        val stackedMediaMessages = boolean("stacked_media_messages")
        val friendAddSource = boolean("friend_add_source")
        val replyButton = boolean("reply_button") { addNotices(FeatureNotice.UNSTABLE) }
        val smartReplies = boolean("smart_replies")
        val downloadButton = boolean("download_button")
        val markAsReadButton = boolean("mark_as_read_button") { addNotices(FeatureNotice.UNSTABLE) }
        val markAsReadAndSaveInChat = boolean("mark_as_read_and_save_in_chat") { addNotices(FeatureNotice.UNSTABLE) }
    }

    class AutoReplyConfig : ConfigContainer(hasGlobalState = true) {
        val allowRunningInBackground = boolean("allow_running_in_background", false)
        val cooldownSeconds = integer("cooldown_seconds", defaultValue = 30) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null }
        }
        val messageAgeThreshold = integer("message_age_threshold", defaultValue = 15) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(1) != null }
        }
        
        inner class AiConfig : ConfigContainer(hasGlobalState = false) {
            val enableAiReplies = boolean("enable_ai_replies", false)
            val aiProvider = unique("ai_provider", "gemini", "deepseek", "openai", "openrouter") {
                customOptionTranslationPath = "ai_provider"
            }.apply { set("gemini") }
            val aiModel = string("ai_model", defaultValue = "gemini-2.5-flash") {
                inputCheck = { it.isNotBlank() }
            }
            val aiApiKey = string("ai_api_key", defaultValue = "") {
                inputCheck = { true }
            }
            val aiSystemPrompt = string("ai_system_prompt", defaultValue = "You are a helpful and friendly assistant responding to messages on Snapchat. Keep responses natural, casual, and conversational. Avoid being overly formal or robotic. Respond as if you're a real person having a normal conversation.") {
                inputCheck = { it.isNotBlank() }
            }
            val aiMaxTokens = integer("ai_max_tokens", defaultValue = 150) {
                inputCheck = { it.toIntOrNull()?.coerceIn(10, 1000) != null }
            }
            val aiTemperature = string("ai_temperature", defaultValue = "0.7") {
                inputCheck = { it.toDoubleOrNull()?.coerceIn(0.0, 2.0) != null }
            }
            val aiContextLength = integer("ai_context_length", defaultValue = 5) {
                inputCheck = { it.toIntOrNull()?.coerceIn(1, 20) != null }
            }
            val aiPersonalityTraits = string("ai_personality_traits", defaultValue = "friendly, casual, helpful, empathetic") {
                inputCheck = { it.isNotBlank() }
            }
            val aiResponseStyle = unique("ai_response_style", "casual", "formal", "friendly", "humorous", "empathetic", "toxic", "busy") {
                customOptionTranslationPath = "ai_response_style"
            }.apply { set("casual") }
            val aiUseConversationHistory = boolean("ai_use_conversation_history", true)
            val aiIncludeFriendInfo = boolean("ai_include_friend_info", true)
            val aiFallbackToTemplate = boolean("ai_fallback_to_template", true)
            val aiRequestTimeout = integer("ai_request_timeout", defaultValue = 10) {
                inputCheck = { it.toIntOrNull()?.coerceIn(5, 60) != null }
            }
            val aiRetryAttempts = integer("ai_retry_attempts", defaultValue = 2) {
                inputCheck = { it.toIntOrNull()?.coerceIn(0, 5) != null }
            }
            val aiResponseLanguage = unique("ai_response_language", "auto", "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi", "tr", "pl", "nl", "sv", "da", "no", "fi") {
                customOptionTranslationPath = "ai_response_language"
            }.apply { set("auto") }
        }
        
        inner class AutoTriggerConfig : ConfigContainer(hasGlobalState = false) {
            val friendSpecificGreeting = boolean("friendSpecificGreeting")
            val friendGreeting = string("friendGreeting", defaultValue = "Hey") {
                inputCheck = { it.isNotBlank() }
            }
            
            val autoReplyContentTypes = multiple("auto_reply_content_types",
                "chat_messages",
                "snap_messages", 
                "story_share_messages",
                "story_reply_messages",
                "external_media_messages",
                "voice_note_messages",
                "sticker_messages",
                "tiny_snap_messages",
                "map_reaction_messages",
                "half_swipes"
            ) { 
                customOptionTranslationPath = "content_type" 
            }.apply {
                set(mutableListOf(
                    "chat_messages",
                    "snap_messages", 
                    "story_share_messages",
                    "story_reply_messages",
                    "external_media_messages",
                    "voice_note_messages",
                    "sticker_messages",
                    "tiny_snap_messages",
                    "map_reaction_messages",
                    "half_swipes"
                ))
            }
            
            val chatMessages = string("chat_messages", defaultValue = "Hello! How are you?")
            val snapMessages = string("snap_messages", defaultValue = "Thanks for the snap!")
            val storyShareMessages = string("story_share_messages", defaultValue = "Thanks for sharing!")
            val storyReplyMessages = string("story_reply_messages", defaultValue = "Thanks for the story reply!")
            val externalMediaMessages = string("external_media_messages", defaultValue = "Nice media!")
            val voiceNoteMessages = string("voice_note_messages", defaultValue = "Thanks for the voice note!")
            val stickerMessages = string("sticker_messages", defaultValue = "Cool sticker!")
            val tinySnapMessages = string("tiny_snap_messages", defaultValue = "Thanks for the tiny snap!")
            val mapReactionMessages = string("map_reaction_messages", defaultValue = "Thanks for the map reaction!")
            val halfSwipeMessages = string("half_swipe_messages", defaultValue = "[\"I noticed you half-swiped! I'll respond soon.\"]")
        }
        
        val aiConfig = container("ai_config", AiConfig())
        val autoTriggerConfig = container("auto_trigger_config", AutoTriggerConfig())
    }

    class AutoOpenSnapsConfig : ConfigContainer(hasGlobalState = true) {
        val allowRunningInBackground = boolean("allow_running_in_background", false)
        val minDelay = integer("min_delay", defaultValue = 50) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null }
        }
        val maxDelayMs = integer("max_delay_ms", defaultValue = 100) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null && it.toInt() > minDelay.get() }
        }
        val queueSize = integer("queue_size", defaultValue = 700) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(1) != null }
        }
        val retryAttempts = integer("retry_attempts", defaultValue = 5) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(1) != null }
        }
        val delayBetweenSnaps = integer("delay_between_snaps", defaultValue = 100) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null }
        }
        val delayBetweenConversations = integer("delay_between_conversations", defaultValue = 500) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null }
        }
        val retryDelay = integer("retry_delay", defaultValue = 3000) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(1000) != null }
        }
        val compactNotification = boolean("compact_notification", false)
        val showLifetimeStats = boolean("show_lifetime_stats", false)
        val showQueuePreview = boolean("show_queue_preview", true)
        val thermalProtection = boolean("thermal_protection", false)
        
        val onlyOnWifi = boolean("only_on_wifi", false)
        val safeProcessing = boolean("safe_processing", true)
        val onlyWhenIdle = boolean("only_when_idle", false)
        val sleepWindow = string("sleep_window", defaultValue = "23:00-07:00") {
            addFlags(ConfigFlag.NO_DISABLE_KEY)
            inputCheck = { it.matches(Regex("^([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)$")) }
        }
    }

    class AutoDeleteSentMessagesConfig : ConfigContainer(hasGlobalState = true) {
        val allowRunningInBackground = boolean("allow_running_in_background", false)
        val deleteAfterValue = integer("delete_after_value", defaultValue = 10) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(1) != null }
        }
        val deleteAfterUnit = unique("delete_after_unit", "seconds", "minutes", "hours") {
            customOptionTranslationPath = "features.options.delete_after_unit"
            addFlags(ConfigFlag.NO_DISABLE_KEY)
        }.apply { set("seconds") }
        val messageTypes = multiple("message_types", "CHAT", "SNAP", "NOTE", "EXTERNAL_MEDIA", "STICKER") {
            customOptionTranslationPath = "content_type"
        }.apply {
            set(mutableListOf("CHAT"))
        }
        val showCountdown = boolean("show_countdown", defaultValue = true)
        val showNotification = boolean("show_notification", defaultValue = true)
    }

    class InstantTranslationConfig : ConfigContainer(hasGlobalState = true) {
        val enabled = boolean("enabled", false)
        val sourceLanguage = string("source_language", defaultValue = "auto") {
            inputCheck = { it.isNotBlank() }
        }
        val targetLanguage = string("target_language", defaultValue = "en") {
            inputCheck = { it.isNotBlank() }
        }
        val showOriginal = boolean("show_original", defaultValue = true)
        val showTranslation = boolean("show_translation", defaultValue = true)
        val translationPosition = unique("translation_position", "above", "below", "inline") {
            customOptionTranslationPath = "translation_position"
        }.apply { set("below") }
        val autoTranslate = boolean("auto_translate", defaultValue = true)
        val translateOnTap = boolean("translate_on_tap", defaultValue = false)
        val pauseOnError = boolean("pause_on_error", defaultValue = true)
        val maxRetries = integer("max_retries", defaultValue = 3) {
            inputCheck = { it.toIntOrNull()?.coerceIn(1, 10) != null }
        }
        val retryDelay = integer("retry_delay", defaultValue = 1000) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(500) != null }
        }

        val supportedLanguages = multiple("supported_languages",
            "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi", "tr", "nl", "pl", "sv", "da", "no", "fi", "cs", "hu", "ro", "bg", "hr", "sk", "sl", "et", "lv", "lt", "mt", "ga", "cy"
        ) {
            customOptionTranslationPath = "language_codes"
        }.apply {
            set(mutableListOf("en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi", "tr"))
        }
    }

    val bypassScreenshotDetection = boolean("bypass_screenshot_detection") { requireRestart() }
    val anonymousStoryViewing = boolean("anonymous_story_viewing")
    val preventStoryRewatchIndicator = boolean("prevent_story_rewatch_indicator") { requireRestart() }
    val hidePeekAPeek = boolean("hide_peek_a_peek")
    val hideBitmojiPresence = boolean("hide_bitmoji_presence")
    val spoofViewingGalleryPresence = boolean("spoof_viewing_gallery_presence")
    val spoofReplyCameraPresence = boolean("spoof_reply_camera_presence")
    val hideTypingNotifications = boolean("hide_typing_notifications")
    val unlimitedSnapViewTime = boolean("unlimited_snap_view_time")
    val autoMarkAsRead = multiple("auto_mark_as_read", "snap_reply", "conversation_read", "save_snap_in_chat") { requireRestart() }
    val markSnapAsSeenButton = boolean("mark_snap_as_seen_button") { requireRestart() }
    val markSnapAsSeenProcessingMode = unique("mark_snap_as_seen_processing_mode", "limit", "complete").apply {
        set("limit")
    }
    val markSnapAsSeenLimit = integer("mark_snap_as_seen_limit", defaultValue = 50) {
        inputCheck = { it.toIntOrNull()?.coerceAtLeast(1) != null }
    }
    val skipWhenMarkingAsSeen = boolean("skip_when_marking_as_seen") { requireRestart() }
    val loopMediaPlayback = boolean("loop_media_playback") { requireRestart() }
    val disableReplayInFF = boolean("disable_replay_in_ff")
    val halfSwipeNotifier = container("half_swipe_notifier", HalfSwipeNotifierConfig()) { requireRestart()}
    val callStartConfirmation = boolean("call_start_confirmation") { requireRestart() }
    val blockCalls = boolean("block_calls") { requireRestart() }
    val callMetadataNotifier = boolean("call_metadata_notifier") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val conversationSoundEffectsStyle = unique("conversation_sound_effects_style", "disabled", "imessage", "telegram", "whatsapp", "subtle") {
        requireRestart()
        customOptionTranslationPath = "conversation_sound_effects_style"
        addFlags(ConfigFlag.NO_TRANSLATE)
        addFlags(ConfigFlag.NO_DISABLE_KEY)
    }.apply { set("disabled") }
    val unlimitedConversationPinning = boolean("unlimited_conversation_pinning") { requireRestart() }
    val disableSnapModeRestrictions = boolean("disable_snap_mode_restrictions") { requireRestart() }
    val autoSaveMessagesInConversations = multiple("auto_save_messages_in_conversations",
        "CHAT",
        "SNAP",
        "NOTE",
        "EXTERNAL_MEDIA",
        "STICKER"
    ) { requireRestart(); customOptionTranslationPath = "content_type" }
    
    class UnsaveableMessagesConfig : ConfigContainer() {
        val chat = boolean("chat", defaultValue = true)
        val snap = boolean("snap")
        val externalMedia = boolean("external_media")
        val sticker = boolean("sticker")
        val share = boolean("share")
        val note = boolean("note")
        val storyReply = boolean("story_reply")
    }

    val unsaveableMessages = container("unsaveable_messages", UnsaveableMessagesConfig()) { requireRestart() }

    val preventMessageSending = multiple("prevent_message_sending", *NotificationType.getOutgoingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
    }
    val friendMutationNotifier = multiple("friend_mutation_notifier",
        "remove_friend",
        "birthday_changes",
        "bitmoji_selfie_changes",
        "bitmoji_avatar_changes",
        "bitmoji_background_changes",
        "bitmoji_scene_changes",
    ) { requireRestart() }
    val betterNotifications = container("better_notifications", BetterNotifications()) { requireRestart() }
    val notificationBlacklist = multiple("notification_blacklist", *NotificationType.getIncomingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
    }
    val messageLogger = container("message_logger", MessageLoggerConfig()) { requireRestart() }

    class GalleryMediaSendOverrideConfig : ConfigContainer() {
        val mode = unique("mode", "always_ask", "SNAP", "NOTE", "SAVEABLE_SNAP") {
            requireRestart()
            customOptionTranslationPath = "gallery_media_send_override"
        }
        val includeCameraSnaps = boolean("include_camera_snaps", false) { requireRestart() }
    }

    val galleryMediaSendOverride = container("gallery_media_send_override", GalleryMediaSendOverrideConfig()) { requireRestart() }
    val scheduledSendAllowRunningInBackground = boolean("scheduled_send_allow_running_in_background", false)
    val stripMediaMetadata = multiple("strip_media_metadata", "hide_caption_text", "hide_snap_filters", "hide_extras", "remove_audio_note_duration", "remove_audio_note_transcript_capability") { requireRestart() }
    val bypassMessageRetentionPolicy = boolean("bypass_message_retention_policy") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val bypassMessageActionRestrictions = boolean("bypass_message_action_restrictions") { requireRestart() }
    val removeGroupsLockedStatus = boolean("remove_groups_locked_status") { requireRestart() }
    val doubleTapChatAction = unique("double_tap_chat_action", "like_message", "copy_text", "delete_message", "mark_as_read", "custom_emoji_reaction") { requireRestart() }
    val doubleTapChatActionCustomEmoji = string("double_tap_chat_action_custom_emoji") {
        inputCheck = { it.length == 2 && it.toByteArray(Charsets.UTF_8).size >= 4 } }
    val autoReply = container("auto_reply", AutoReplyConfig()) { requireRestart() }
    val autoDeleteSentMessages = container("auto_delete_sent_messages", AutoDeleteSentMessagesConfig()) { requireRestart() }
    
    val autoOpenSnaps = container("auto_open_snaps", AutoOpenSnapsConfig()) { requireRestart(); addNotices(FeatureNotice.BAN_RISK, FeatureNotice.UNSTABLE) }
    val preFetchSnaps = boolean("pre_fetch_snaps", false)
    val instantTranslation = container("instant_translation", InstantTranslationConfig()) { requireRestart() }
}
