package me.eternal.purrfectsnap.core.features

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.eternal.purrfectsnap.core.ModContext
import me.eternal.purrfectsnap.core.features.impl.*
import me.eternal.purrfectsnap.core.features.impl.downloader.CallRecorder
import me.eternal.purrfectsnap.core.features.impl.downloader.ChatWallpaperDownloader
import me.eternal.purrfectsnap.core.features.impl.downloader.MediaDownloader
import me.eternal.purrfectsnap.core.features.impl.downloader.ProfilePictureDownloader
import me.eternal.purrfectsnap.core.features.impl.experiments.*
import me.eternal.purrfectsnap.core.features.impl.global.*
import me.eternal.purrfectsnap.core.features.impl.messaging.*
import me.eternal.purrfectsnap.core.features.impl.spying.FriendTracker
import me.eternal.purrfectsnap.core.features.impl.spying.HalfSwipeNotifier
import me.eternal.purrfectsnap.core.features.impl.spying.MessageLogger
import me.eternal.purrfectsnap.core.features.impl.spying.StealthMode
import me.eternal.purrfectsnap.core.features.impl.tweaks.*
import me.eternal.purrfectsnap.core.features.impl.ui.*
import me.eternal.purrfectsnap.core.logger.CoreLogger
import me.eternal.purrfectsnap.core.ui.menu.MenuViewInjector
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
            Messaging(),
            FriendMutationObserver(),
            MediaDownloader(),
            StealthMode(),
            MenuViewInjector(),
            ConvertMessageLocally(),
            SnapchatPlus(),
            AdBlockFix(),
            DisableMetrics(),
            EndpointsBlocker(),
            PreventMessageSending(),
            Notifications(),
            UITweaks(),
            OperaStoryCounter(),
            OperaStoryOverlay(),
            ConfigurationOverride(),
            UnsaveableMessages(),
            SendOverride(),
            UnlimitedSnapViewTime(),
            MediaUploadQualityOverride(),
            MeoPasscodeBypass(),
            CameraTweaks(),
            PerformanceMode(),
            InfiniteStoryBoost(),
            DeviceSpooferHook(),
            GooglePlayServicesDialogs(),
            NoFriendScoreDelay(),
            ProfilePictureDownloader(),
            DisableReplayInFF(),
            OldBitmojiSelfie(),
            RequerySqlite(),
            RefreshFriendSuggestions(),
            LocalPinnedMessages(),
            CallButtonsOverride(),
            BypassScreenshotDetection(),
            HalfSwipeNotifier(),
            DisableConfirmationDialogs(),
            MixerStories(),
            MessageIndicators(),
            ConversationToolbox(),
            OperaStoryCounter(),
            OperaViewerParamsOverride(),
            DefaultVolumeControls(),
            RemoveGroupsLockedStatus(),
            BypassMessageActionRestrictions(),
            BetterLocation(),
            MediaFilePicker(),
            CustomStreaksExpirationFormat(),
            ValdiHooks(),
            FirstCreatedUsername(),
            DisableCustomTabs(),
            BestFriendPinning(),
            ContextMenuFix(),
            DisableTelecomFramework(),
            BetterTranscript(),
            AutoDeleteSentMessages(),
            DoubleTapChatAction(),
            SnapScoreChanges(),
            DisableSnapModeRestrictions(),
            PreventForcedKeyboard(),
            CustomTheming(),
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
