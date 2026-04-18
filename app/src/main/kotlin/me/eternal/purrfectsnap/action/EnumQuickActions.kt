package cock.crest.purrfectsnap.lite.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector
import cock.crest.purrfectsnap.lite.ui.manager.Routes

enum class EnumQuickActions(
    val key: String,
    val icon: ImageVector,
    val action: Routes.() -> Unit
) {
    FILE_IMPORTS("file_imports", Icons.Default.FolderOpen, {
        fileImports.navigateReset()
    }),
    LOGGER_HISTORY("logger_history", Icons.Default.History, {
        loggerHistory.navigateReset()
    }),
}
