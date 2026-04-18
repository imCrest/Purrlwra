package cock.crest.purrfectsnap.lite.ui.manager

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import cock.crest.purrfectsnap.lite.ui.manager.pages.TasksRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeAbout
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeSettings
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeLogs
import cock.crest.purrfectsnap.lite.ui.manager.pages.scripting.ScriptingRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.SocialRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerManagerRoot

/**
 * ThemeContract defines the visual layout contract every theme must fulfill.
 */
interface ThemeContract {
    @Composable fun HomeRootSection.HomeScreen(nav: NavBackStackEntry)
    @Composable fun HomeSettings.SettingsScreen(nav: NavBackStackEntry)
    @Composable fun HomeAbout.AboutScreen(nav: NavBackStackEntry)
    @Composable fun HomeLogs.LogsScreen(nav: NavBackStackEntry)
    @Composable fun SocialRootSection.SocialScreen(nav: NavBackStackEntry)
    @Composable fun TasksRootSection.TasksScreen(nav: NavBackStackEntry)
    @Composable fun FeaturesRootSection.FeaturesScreen(nav: NavBackStackEntry)
    @Composable fun ScriptingRootSection.ScriptingScreen(nav: NavBackStackEntry)
    @Composable fun FriendTrackerManagerRoot.FriendTrackerScreen(nav: NavBackStackEntry)
}
