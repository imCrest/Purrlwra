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
        private val LITE_FEATURE_PREFIXES = setOf(
            "messaging",
            "experimental",
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
        if (pathPrefix != null && hasGlobalState && LITE_FEATURE_PREFIXES.none { isPathOrDescendant(pathPrefix, it) }) {
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
        return LITE_FEATURE_PREFIXES.any { isPathOrDescendant(fullPath, it) } ||
            INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(fullPath, it) }
    }

    private fun shouldKeepContainer(fullPath: String): Boolean {
        return shouldKeepProperty(fullPath) ||
            LITE_FEATURE_PREFIXES.any { isPathOrDescendant(it, fullPath) } ||
            INTERNAL_SUPPORT_PATHS.any { isPathOrDescendant(it, fullPath) }
    }

    private fun isPathOrDescendant(path: String, prefix: String): Boolean {
        return path == prefix || path.startsWith("$prefix.")
    }
}
