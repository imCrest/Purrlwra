package me.eternal.purrfectsnap.common.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.ui.graphics.vector.ImageVector


enum class EnumAction(
    val key: String,
    val icon: ImageVector,
    val exitOnFinish: Boolean = false,
) {
    // Kept internal for config-driven clean cache flows; intentionally hidden from UI in Lite/Core.
    CLEAN_CACHE("clean_snapchat_cache", Icons.Default.CleaningServices, exitOnFinish = true);

    companion object {
        const val ACTION_PARAMETER = "se_action"
    }
}
