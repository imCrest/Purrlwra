package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.documentfile.provider.DocumentFile
import cock.crest.purrfectsnap.lite.common.ui.rememberAsyncMutableState
import cock.crest.purrfectsnap.lite.ui.manager.pages.scripting.ScriptingRootSection
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import kotlinx.coroutines.launch

@Composable
fun ScriptingRootSection.AphelionScriptingScreen(nav: NavBackStackEntry) {
    val scriptingFolder by rememberAsyncMutableState(
        defaultValue = null,
        updateDispatcher = reloadDispatcher
    ) { context.scriptManager.getScriptsFolder() }
    val tabTitles = listOf(translation["installed_scripts_tab"], translation["catalog_tab"])
    var showImportDialog by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scriptingFolder) {
        if (scriptingFolder == null && selectedTab != 0) {
            selectedTab = 0
        }
    }

    if (showImportDialog) {
        ImportRemoteScript { showImportDialog = false }
    }
    if (showToast) {
        LaunchedEffect(showToast) {
            context.shortToast(translation["select_scripts_folder_toast"])
            showToast = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PurrfectPalette.backgroundGradient)
    ) {
        ScriptingHeader(
            titles = tabTitles,
            selectedTab = selectedTab,
            onTabSelected = { index ->
                if (index == 1 && scriptingFolder == null) {
                    showToast = true
                } else {
                    selectedTab = index
                }
            },
            onImport = {
                if (scriptingFolder == null) showToast = true else showImportDialog = true
            },
            onOpenFolder = {
                if (scriptingFolder == null) {
                    showToast = true
                } else {
                    scriptingFolder?.let {
                        context.androidContext.openLink(
                            it.uri.toString(),
                            context.translation["toast_open_link_failed"]
                        )
                    }
                }
            },
            onManageRepos = { routes.manageScriptRepos.navigate() },
            onDocs = {
                context.androidContext.openLink(
                    "https://github.com/SnapEnhance/scripting-docs",
                    context.translation["toast_open_link_failed"]
                )
            },
            folderSelected = scriptingFolder != null
        )
        Spacer(Modifier.height(12.dp))
        when (selectedTab) {
            0 -> InstalledTabContent(
                scriptingFolder = scriptingFolder
            )
            1 -> CatalogTabContent(
                scriptingFolder = scriptingFolder
            )
        }
    }
}
