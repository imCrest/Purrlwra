package cock.crest.purrfectsnap.lite.core.features

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.features.impl.*
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.CallRecorder
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.ChatWallpaperDownloader
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.MediaDownloader
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.ProfilePictureDownloader
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.*
import cock.crest.purrfectsnap.lite.core.features.impl.global.*
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.*
import cock.crest.purrfectsnap.lite.core.features.impl.spying.FriendTracker
import cock.crest.purrfectsnap.lite.core.features.impl.spying.HalfSwipeNotifier
import cock.crest.purrfectsnap.lite.core.features.impl.spying.MessageLogger
import cock.crest.purrfectsnap.lite.core.features.impl.spying.StealthMode
import cock.crest.purrfectsnap.lite.core.features.impl.tweaks.*
import cock.crest.purrfectsnap.lite.core.features.impl.ui.*
import cock.crest.purrfectsnap.lite.core.logger.CoreLogger
import cock.crest.purrfectsnap.lite.core.ui.menu.MenuViewInjector
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

class FeatureManager(
    private val context: ModContext
) {
    private val features = mutableMapOf<KClass<out Feature>, Feature>()
    private val onActivityCreateListeners = mutableListOf<(Activity) -> Unit>()

    fun addActivityCreateListener(block: (Activity) -> Unit) {
        onActivityCreateListeners.add(block)
    }

    private fun register(vararg featureList: Feature) {
        if (context.bridgeClient.getDebugProp("disable_feature_loading") == "true") {
            context.log.warn("Feature loading is disabled")
            return
        }

        runBlocking {
            featureList.forEach { feature ->
                launch(Dispatchers.IO) {
                    runCatching {
                        feature.context = context
                        feature.registerNextActivityCallback = { block -> onActivityCreateListeners.add(block) }
                        synchronized(features) {
                            features[feature::class] = feature
                        }
                    }.onFailure {
                        CoreLogger.xposedLog("Failed to register feature ${feature.key}", it)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Feature> get(featureClass: KClass<T>): T? {
        return features[featureClass] as? T
    }

    fun getRuleFeatures() = features.values.filterIsInstance<MessagingRuleFeature>().sortedBy { it.ruleType.ordinal }

    fun init() {
        register(
            Debug(),
            EndToEndEncryption(),
            ScopeSync(),
            PreventMessageListAutoScroll(),
            Messaging(),
            FriendMutationObserver(),
            AutoMarkAsRead(),
            AutoRead(),
            MediaDownloader(),
            StealthMode(),
            MenuViewInjector(),
            MessageLogger(),
            ConvertMessageLocally(),
            SnapchatPlus(),
            AdBlockFix(),
            DisableMetrics(),
            EndpointsBlocker(),
            PreventMessageSending(),
            Notifications(),
            AutoSave(),
            AutoReply(),
            UITweaks(),
            OperaStoryCounter(),
            OperaStoryOverlay(),
            ConfigurationOverride(),
            COFOverride(),
            UnsaveableMessages(),
            SendOverride(),
            UnlimitedSnapViewTime(),
            BypassVideoLengthRestriction(),
            MediaUploadQualityOverride(),
            MeoPasscodeBypass(),
            AppLock(),
            CameraTweaks(),
            PerformanceMode(),
            InfiniteStoryBoost(),
            PinConversations(),
            DeviceSpooferHook(),
            ClientBootstrapOverride(),
            GooglePlayServicesDialogs(),
            NoFriendScoreDelay(),
            ProfilePictureDownloader(),
            AddFriendSourceSpoof(),
            DisableReplayInFF(),
            OldBitmojiSelfie(),
            FriendFeedMessagePreview(),
            HideStreakRestore(),
            HideFriendFeedEntry(),
            RequerySqlite(),
            RefreshFriendSuggestions(),
            LocalPinnedMessages(),
            BlockCalls(),
            CallMetadataNotifier(),
            ConversationSoundEffects(),
            CallButtonsOverride(),
            SnapPreview(),
            BypassScreenshotDetection(),
            HalfSwipeNotifier(),
            DisableConfirmationDialogs(),
            MixerStories(),
            MessageIndicators(),
            EditTextOverride(),
            PreventForcedLogout(),
            ConversationToolbox(),
            SpotlightCommentsUsername(),
			SpotlightCreatorInfo(),
            OperaStoryCounter(),
            OperaViewerParamsOverride(),
            StealthModeIndicator(),
            DisablePermissionRequests(),
            FriendTracker(),
            DefaultVolumeControls(),
            CallRecorder(),
            ChatWallpaperDownloader(),
            DisableMemoriesSnapFeed(),
            AccountSwitcher(),
            RemoveGroupsLockedStatus(),
            BypassMessageActionRestrictions(),
            BetterLocation(),
            MediaFilePicker(),
            HideActiveMusic(),
            AutoOpenSnaps(),
            CustomStreaksExpirationFormat(),
            ValdiHooks(),
            FirstCreatedUsername(),
            DisableCustomTabs(),
            BestFriendPinning(),
            ContextMenuFix(),
            DisableTelecomFramework(),
            BetterTranscript(),
            VoiceNoteOverride(),
            AutoDeleteSentMessages(),
            FriendNotes(),
            DoubleTapChatAction(),
            VideoRecordTimer(),
            SnapScoreChanges(),
            DisableSnapModeRestrictions(),
            MessageTranslator(),
            PreventForcedKeyboard(),
            CustomTheming(),
            HideTypingIndicator(),
            FakeSnapScore(),
        )

        features.values.toList().forEach { feature ->
            runCatching {
                measureTimeMillis {
                    feature.init()
                }
            }.onFailure {
                context.log.error("Failed to init feature ${feature.key}", it)
                context.longToast(
                    context.translation.format(
                        "toast_feature_init_failed",
                        "feature" to feature.key
                    )
                )
            }
        }
    }

    fun onActivityCreate(activity: Activity) {
        context.log.verbose("Activity created: ${activity.javaClass.simpleName}")
        onActivityCreateListeners.toList().also {
            onActivityCreateListeners.clear()
        }.forEach { activityListener ->
            measureTimeMillis {
                runCatching {
                    activityListener(activity)
                }.onFailure {
                    context.log.error("Failed to run activity listener ${activityListener::class.simpleName}", it)
                }
            }
        }
    }
}
