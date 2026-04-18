package cock.crest.purrfectsnap.lite.core.features.impl

import cock.crest.purrfectsnap.lite.core.features.Feature

import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.mapper.impl.CompositeConfigurationProviderMapper

data class ConfigKeyInfo(
    val category: String?,
    val name: String?,
    val defaultValue: Any?
)

data class ConfigFilter(
    val filter: (ConfigKeyInfo) -> Boolean,
    val defaultValue: (ConfigKeyInfo) -> Any?,
    val isAppExperiment: Boolean?
)

class ConfigurationOverride : Feature("Configuration Override") {
    override fun init() {
        context.mappings.useMapper(CompositeConfigurationProviderMapper::class) {
            fun getConfigKeyInfo(key: Any?) = runCatching {
                if (key == null) return@runCatching null
                val keyClassMethods = key::class.java.methods
                val keyName = keyClassMethods.firstOrNull { it.name == "getName" }?.invoke(key)?.toString() ?: key.toString()
                val category = keyClassMethods.firstOrNull { it.name == configEnumMapping["getCategory"]?.get().toString() }?.invoke(key)?.toString() ?: return null
                val valueHolder = keyClassMethods.firstOrNull { it.name == configEnumMapping["getValue"]?.get().toString() }?.invoke(key) ?: return null
                val defaultValue = valueHolder.getObjectField(configEnumMapping["defaultValueField"]?.get().toString()) ?: return null
                ConfigKeyInfo(category, keyName, defaultValue)
            }.onFailure {
                context.log.error("Failed to get config key info", it)
            }.getOrNull()

            val propertyOverrides = mutableMapOf<String, ConfigFilter>()
            val loggedOverrides = mutableSetOf<String>()

            fun overrideProperty(key: String, filter: (ConfigKeyInfo) -> Boolean, value: (ConfigKeyInfo) -> Any?, isAppExperiment: Boolean = false) {
                propertyOverrides[key] = ConfigFilter(filter, value, isAppExperiment)
            }

            fun logPerformanceOverride(key: String, value: Any?) {
                if (!key.contains("PRELOAD") &&
                    !key.contains("PERFORMANCE") &&
                    !key.contains("WARM") &&
                    !key.contains("PREFETCH") &&
                    !key.contains("LATENCY") &&
                    !key.contains("ANALYTICS") &&
                    !key.contains("THREAD_PRIORITY") &&
                    !key.contains("HD_MODE") &&
                    !key.contains("LENS") &&
                    !key.contains("THUMBNAIL")
                ) {
                    return
                }
                synchronized(loggedOverrides) {
                    if (!loggedOverrides.add(key)) return
                }
                context.log.info("Performance override applied: $key=$value", "PerformanceMode")
            }

            overrideProperty("STREAK_EXPIRATION_INFO", { context.config.userInterface.streakExpirationInfo.get() },
                { true })
            overrideProperty("TRANSCODING_MAX_QUALITY", { context.config.global.mediaUploadQualityConfig.forceVideoUploadSourceQuality.get() || context.config.messaging.galleryMediaSendOverride.mode.getNullable() != null },
                { true }, isAppExperiment = true)

            run {
                val isForceQuality = { _: ConfigKeyInfo -> context.config.global.mediaUploadQualityConfig.forceVideoUploadSourceQuality.get() || context.config.messaging.galleryMediaSendOverride.mode.getNullable() != null }
                val level7Value = { _: ConfigKeyInfo -> 700 }
                arrayOf(
                    "MY_STORY_UPLOAD_QUALITY_LEVEL",
                    "PUBLIC_STORY_UPLOAD_QUALITY_LEVEL",
                    "GROUP_STORY_UPLOAD_QUALITY_LEVEL",
                    "SPOTLIGHT_UPLOAD_QUALITY_LEVEL",
                    "MEDIA_UPLOAD_QUALITY_LEVEL",
                    "MESSAGING_MEDIA_UPLOAD_QUALITY_LEVEL",
                    "IMAGE_UPLOAD_QUALITY_LEVEL",
                    "IMAGE_QUALITY_LEVEL_FOR_PRE_UPLOAD",
                    "IMAGE_QUALITY_LEVEL_FOR_PUBLIC_POSTING",
                    "IMAGE_QUALITY_LEVEL_FOR_PRIVATE_POSTING",
                    "RETRANSCODE_UPLOAD_QUALITY_LEVEL",
                    "MEMORIES_BACKUP_MEDIA_LEVEL",
                    "MEMORIES_BACKUP_MEDIA_LEVEL_HIGH_QUALITY",
                    "MEDIA_EXPORT_QUALITY_LEVEL"
                ).forEach { key ->
                    overrideProperty(key, isForceQuality, level7Value)
                }
                overrideProperty("ENABLE_HIGH_QUALITY_MEMORIES_BACKUP",
                    isForceQuality, { true })
                overrideProperty("MEDIA_QUALITY_LEVEL_DOWNGRADING_PERCENTAGE",
                    isForceQuality, { 0.0f })
                overrideProperty("CHAT_MEDIA_IMPORT_TRANSCODED_QUALITY",
                    isForceQuality, { 4 })
                overrideProperty("POSTED_STORY_IMPORT_TRANSCODED_QUALITY",
                    isForceQuality, { 4 })
            }

            run {
                val isDisableCompression = { _: ConfigKeyInfo -> context.config.global.mediaUploadQualityConfig.disableImageCompression.get() || context.config.messaging.galleryMediaSendOverride.mode.getNullable() != null }
                overrideProperty("LIBJPEG_IMAGE_ENCODING_QUALITY", isDisableCompression, { 100 })
                overrideProperty("LIBJPEG_IMAGE_ENCODING_QUALITY_V2", isDisableCompression, { 100 })
            }

            overrideProperty("CAMERA_ME_ENABLE_HEVC_RECORDING", { context.config.camera.hevcRecording.get() },
                { true })
            overrideProperty("MEDIA_RECORDER_MAX_QUALITY_LEVEL", { context.config.camera.forceCameraSourceEncoding.get() },
                { true })
            overrideProperty("ENABLE_MESSAGE_WINDOW_MANAGER", { context.config.global.performanceMode.profile.getNullable() != null },
                { true })
            overrideProperty("ENABLE_SIMPLE_CONVERSATION_RESET", { context.config.global.performanceMode.profile.getNullable() == "max" },
                { false })
            overrideProperty("PREVIEW_PRELOAD_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() != null },
                { true })
            overrideProperty("BUFFERED_VIDEO_RECORDING_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() != null },
                { true })
            overrideProperty("CAMERA_THREAD_PRIORITY", { context.config.global.performanceMode.profile.getNullable() != null },
                { true })
            overrideProperty("HD_MODE_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() == "max" },
                { true })

            arrayOf(
                "FEATURE_PRELOADER",
                "SERVER_PREFETCH",
                "SERVER_PREFETCH_WITH_COF",
                "DISCOVER_FEED_PERFORMANCE",
                "LOGIN_PRELOAD",
                "PREFETCH_REPO_SUBSCRIBE_ON_CPU",
                "COMPUTE_FEED_CACHE_WITH_TTL",
                "COMPUTE_FEED_NETWORK_WITH_CACHE",
                "OPERA_WARMUP",
                "SHOW_PREFETCH",
            ).forEach { key ->
                overrideProperty(key, { context.config.global.performanceMode.profile.getNullable() != null }, { true })
            }

            arrayOf(
                "USER_STORY_PRELOAD",
                "STARTUP_LENS_ACTIVATOR",
                "LENSES_PREVIEW_ACTIVATOR",
                "THUMBNAIL_PRESENTER_ACTIVATOR",
                "SINGLE_SEGMENT_THUMBNAIL_ACTIVATOR",
                "DISCOVER_FEED_STORY_PREFETCH",
                "DISCOVER_FEED_THUMBNAILS",
                "REFACTORED_WITH_WARMUP_LENS",
            ).forEach { key ->
                overrideProperty(key, { context.config.global.performanceMode.profile.getNullable() == "max" }, { false })
            }

            overrideProperty("LOAD_LATENCY_TRACKER_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() != null },
                { false })
            overrideProperty("ANALYTICS_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() != null },
                { false })
            overrideProperty("LOCK_SCREEN_ANALYTICS_ACTIVATOR", { context.config.global.performanceMode.profile.getNullable() != null },
                { false })
            overrideProperty("REDUCE_MY_PROFILE_UI_COMPLEXITY", { context.config.userInterface.mapFriendNameTags.get() },
                { true })

            arrayOf("DISABLE_SPLIT_RENDER_PASS_CONTROLLER", "ENABLE_LONG_SNAP_SENDING").forEach {
                overrideProperty(it, { context.config.global.disableSnapSplitting.get() }, { true })
            }

            overrideProperty("DF_VOPERA_FOR_STORIES", { context.config.userInterface.verticalStoryViewer.get() },
                { true }, isAppExperiment = true)

            overrideProperty("BYPASS_AD_FEATURE_GATE", { context.config.global.blockAds.get() },
                { true })

            overrideProperty("SPONSORED_SNAPS_ENABLED", { context.config.global.blockAds.get() }, { false })
            overrideProperty("SPONSORED_SNAP_UPDATE_SPONSORED_FEED_ITEM", { context.config.global.blockAds.get() }, { false })

            arrayOf("CUSTOM_AD_TRACKER_URL", "CUSTOM_AD_INIT_SERVER_URL", "CUSTOM_AD_SERVER_URL", "INIT_PRIMARY_URL", "INIT_SHADOW_URL", "GRAPHENE_HOST").forEach {
                overrideProperty(it, { context.config.global.blockAds.get() }, { "http://127.0.0.1" })
            }
            overrideProperty("GIFTING_CHAT_BIRTHDAY_UPSELL_ENABLED", { context.config.userInterface.hideUiComponents.get().contains("hide_snapchat_plus_gift_reminders") }, { false })

            classReference.getAsClass()?.hook(
                getProperty.getAsString()!!,
                HookStage.AFTER
            ) { param ->
                val propertyKey = getConfigKeyInfo(param.argNullable<Any>(0)) ?: return@hook

                propertyOverrides[propertyKey.name]?.let { (filter, value) ->
                    if (!filter(propertyKey)) return@let
                    value(propertyKey).also {
                        logPerformanceOverride(propertyKey.name ?: return@also, it)
                        param.setResult(it)
                    }
                }
            }

            classReference.get()?.hook(
                observeProperty.getAsString()!!,
                HookStage.BEFORE
            ) { param ->
                val enumData = param.arg<Any>(0)
                val key = enumData.toString()
                val setValue: (Any?) -> Unit = setValue@{ value ->
                    val valueHolder = enumData::class.java.methods.firstOrNull {
                        it.name == configEnumMapping["getValue"]?.getAsString() && it.parameterCount == 0
                    }?.invoke(enumData) ?: return@setValue
                    valueHolder.setObjectField(configEnumMapping["defaultValueField"]?.getAsString()!!, value)
                }

                propertyOverrides[key]?.let { (filter, value) ->
                    val keyInfo = getConfigKeyInfo(enumData) ?: return@let
                    if (!filter(keyInfo)) return@let
                    value(keyInfo).also {
                        logPerformanceOverride(key, it)
                        setValue(it)
                    }
                }
            }

            runCatching {
                val customBooleanPropertyRules = mutableListOf<(ConfigKeyInfo) -> Boolean>()

                appExperimentProvider["getBooleanAppExperimentClass"]?.getAsClass()
                    ?.hook("invoke", HookStage.BEFORE) { param ->
                        val keyInfo = getConfigKeyInfo(param.arg(1)) ?: return@hook
                        if (customBooleanPropertyRules.any { it(keyInfo) }) {
                            param.setResult(true)
                            return@hook
                        }
                        propertyOverrides[keyInfo.name]?.let { (filter, value, isAppExperiment) ->
                            if (isAppExperiment != true || !filter(keyInfo)) return@let
                            value(keyInfo).also {
                                logPerformanceOverride(keyInfo.name ?: return@also, it)
                                param.setResult(it)
                            }
                        }
                    }

                Hooker.ephemeralHookConstructor(
                    classReference.get()!!,
                    HookStage.AFTER
                ) { constructorParam ->
                    val instance = constructorParam.thisObject<Any>()
                    val appExperimentProviderInstance = instance::class.java.fields.firstOrNull {
                        appExperimentProvider["class"]?.getAsClass()?.isAssignableFrom(it.type) == true
                    }?.get(instance) ?: return@ephemeralHookConstructor

                    appExperimentProviderInstance::class.java.methods.first {
                        it.name == appExperimentProvider["hasExperimentMethod"]?.getAsString().toString()
                    }.hook(HookStage.BEFORE) { param ->
                        val keyInfo = getConfigKeyInfo(param.arg(0)) ?: return@hook
                        if (customBooleanPropertyRules.any { it(keyInfo) }) {
                            param.setResult(true)
                            return@hook
                        }

                        val propertyOverride = propertyOverrides[keyInfo.name] ?: return@hook
                        propertyOverride.isAppExperiment.takeIf { propertyOverride.filter(keyInfo) }?.let {
                            logPerformanceOverride(keyInfo.name ?: return@let, it)
                            param.setResult(it)
                        }
                    }
                }
            }.onFailure {
                context.log.error("Failed to hook appExperimentProvider", it)
            }
        }
    }
}
