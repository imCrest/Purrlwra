package cock.crest.purrfectsnap.lite.common.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import cock.crest.purrfectsnap.lite.bridge.location.LocationCoordinates
import cock.crest.purrfectsnap.lite.bridge.ConfigStateListener
import cock.crest.purrfectsnap.lite.bridge.storage.FileHandleManager
import cock.crest.purrfectsnap.lite.common.bridge.InternalFileHandleType
import cock.crest.purrfectsnap.lite.common.bridge.InternalFileWrapper
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.common.config.impl.RootConfig
import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger
import cock.crest.purrfectsnap.lite.common.util.LazyBridgeValue
import kotlin.properties.Delegates

class ModConfig(
    private val context: Context,
    fileHandleManager: LazyBridgeValue<FileHandleManager>
) {
    private val fileWrapper = InternalFileWrapper(fileHandleManager, InternalFileHandleType.CONFIG, "{}")
    var locale: String = LocaleWrapper.DEFAULT_LOCALE

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    var wasPresent by Delegates.notNull<Boolean>()

    /* Used to notify the bridge client about config changes */
    var configStateListener: ConfigStateListener? = null
    lateinit var root: RootConfig
        private set

    fun isInitialized() = ::root.isInitialized

    private fun createRootConfig() = RootConfig().apply {
        lateInit(context)
        applyLiteProfile()
    }

    fun load() {
        wasPresent = fileWrapper.exists()
        val targetRoot = if (::root.isInitialized) {
            root
        } else {
            createRootConfig().also { root = it }
        }
        if (!wasPresent) {
            writeConfigObject(targetRoot)
            return
        }
        runCatching {
            loadConfig(targetRoot)
            targetRoot.applyLiteProfile()
            writeConfigObject(targetRoot)
        }.onFailure {
            writeConfigObject(targetRoot)
        }
    }

    private fun loadConfig(config: RootConfig) {
        val configFileContent = fileWrapper.readBytes()
        val configObject = gson.fromJson(configFileContent.toString(Charsets.UTF_8), JsonObject::class.java)
        locale = configObject.get("_locale")?.asString ?: LocaleWrapper.DEFAULT_LOCALE
        config.fromJson(configObject)
    }

    fun exportToString(
        exportSensitiveData: Boolean = true,
        includeSavedLocations: Boolean = true,
        savedLocations: List<LocationCoordinates>? = null,
        config: RootConfig = root,
    ): String {
        return gson.toJson(config.toJson(exportSensitiveData, includeSavedLocations).apply {
            addProperty("_locale", locale)
            if (includeSavedLocations && savedLocations != null) {
                add("_saved_locations", JsonArray().apply {
                    savedLocations.forEach { location ->
                        add(JsonObject().apply {
                            addProperty("id", location.id)
                            addProperty("name", location.name)
                            addProperty("latitude", location.latitude)
                            addProperty("longitude", location.longitude)
                            addProperty("radius", location.radius)
                        })
                    }
                })
            }
        })
    }

    fun reset() {
        root = createRootConfig().apply {
            writeConfigObject(this)
        }
    }

    fun writeConfig(dispatchConfigListener: Boolean = true) {
        writeConfigObject(root, dispatchConfigListener)
    }

    private fun writeConfigObject(config: RootConfig, dispatchConfigListener: Boolean = true) {
        var shouldRestart = false
        var shouldCleanCache = false
        var configChanged = false

        fun compareDiff(originalContainer: ConfigContainer, modifiedContainer: ConfigContainer) {
            val parentContainerFlags = modifiedContainer.parentContainerKey?.params?.flags ?: emptySet()

            parentContainerFlags.takeIf { originalContainer.hasGlobalState }?.apply {
                if (modifiedContainer.globalState != originalContainer.globalState) {
                    configChanged = true
                    if (contains(ConfigFlag.REQUIRE_RESTART)) shouldRestart = true
                    if (contains(ConfigFlag.REQUIRE_CLEAN_CACHE)) shouldCleanCache = true
                }
            }

            for (property in modifiedContainer.properties) {
                val modifiedValue = property.value.getNullable()
                val originalValue = originalContainer.properties.entries.firstOrNull {
                    it.key.name == property.key.name
                }?.value?.getNullable()

                if (originalValue is ConfigContainer && modifiedValue is ConfigContainer) {
                    compareDiff(originalValue, modifiedValue)
                    continue
                }

                if (modifiedValue != originalValue) {
                    val flags = property.key.params.flags + parentContainerFlags
                    configChanged = true
                    if (flags.contains(ConfigFlag.REQUIRE_RESTART)) shouldRestart = true
                    if (flags.contains(ConfigFlag.REQUIRE_CLEAN_CACHE)) shouldCleanCache = true
                }
            }
        }

        val oldConfig = runCatching { fileWrapper.readBytes().toString(Charsets.UTF_8) }.getOrNull()
        fileWrapper.writeBytes(exportToString(config = config).toByteArray(Charsets.UTF_8))

        configStateListener?.takeIf { dispatchConfigListener && it.asBinder().pingBinder() }?.also {
            runCatching {
                compareDiff(createRootConfig().apply {
                    fromJson(gson.fromJson(oldConfig ?: return@runCatching, JsonObject::class.java))
                }, config)

                if (configChanged) {
                    it.onConfigChanged()
                    if (shouldCleanCache) it.onCleanCacheRequired()
                    else if (shouldRestart) it.onRestartRequired()
                }
            }.onFailure {
                AbstractLogger.directError("Error while calling config state listener", it, "ConfigStateListener")
            }
        }
    }

    /**
     * Loads config from a JSON string.
     * @return JsonArray of saved locations if present in the JSON, null otherwise
     */
    fun loadFromString(string: String): JsonArray? {
        val configObject = gson.fromJson(string, JsonObject::class.java)
        locale = configObject.get("_locale")?.asString ?: LocaleWrapper.DEFAULT_LOCALE
        root.fromJson(configObject)
        writeConfig()
        
        // Return saved locations array if present (for caller to handle database import)
        return configObject.getAsJsonArray("_saved_locations")
    }
}
