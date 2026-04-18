package cock.crest.purrfectsnap.lite.common.config.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.DataProcessors
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice

class RootConfig : ConfigContainer() {
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

    fun applyLiteDefaults() {
        downloader.disableRecursively()
        userInterface.disableRecursively()
        rules.disableRecursively()
        camera.disableRecursively()
        streaksReminder.disableRecursively()
        scripting.disableRecursively()
        friendTracker.disableRecursively()

        global.disableRecursively(excludedPropertyNames = setOf("update_settings", "ui_settings"))
    }

    private fun ConfigContainer.disableRecursively(excludedPropertyNames: Set<String> = emptySet()) {
        if (hasGlobalState) {
            globalState = false
        }

        properties.forEach { (key, value) ->
            if (key.name in excludedPropertyNames) return@forEach

            when (key.dataType.type) {
                DataProcessors.Type.BOOLEAN -> value.setAny(false)
                DataProcessors.Type.STRING -> value.setAny("")
                DataProcessors.Type.INTEGER -> value.setAny(0)
                DataProcessors.Type.FLOAT -> value.setAny(0f)
                DataProcessors.Type.STRING_MULTIPLE_SELECTION -> value.setAny(mutableListOf<String>())
                DataProcessors.Type.STRING_UNIQUE_SELECTION -> value.setAny("null")
                DataProcessors.Type.MAP_COORDINATES -> value.setAny(0.0 to 0.0)
                DataProcessors.Type.INT_COLOR -> value.setAny(null)
                DataProcessors.Type.CONTAINER -> (value.get() as ConfigContainer).disableRecursively()
            }
        }
    }
}
