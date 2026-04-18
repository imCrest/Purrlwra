package cock.crest.purrfectsnap.lite.ui.manager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.ui.manager.pages.FileImportsRoot
import cock.crest.purrfectsnap.lite.ui.manager.pages.LoggerHistoryRoot
import cock.crest.purrfectsnap.lite.ui.manager.pages.ManageReposSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.TasksRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.ManageRuleFeature
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeLogs
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeAbout
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.HomeSettings
import cock.crest.purrfectsnap.lite.ui.manager.pages.home.RetroGameScreen
import cock.crest.purrfectsnap.lite.ui.manager.pages.location.BetterLocationRoot
import cock.crest.purrfectsnap.lite.ui.manager.pages.scripting.ScriptingRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.LoggedStories
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.ManageScope
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.MessagingPreview
import cock.crest.purrfectsnap.lite.ui.manager.pages.social.SocialRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.EditRule
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerManagerRoot
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerCatalog
import cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.ManageFriendTrackerReposSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.scripting.ManageScriptReposSection


data class RouteInfo(
    val id: String,
    val key: String = id,
    val icon: ImageVector = Icons.Default.Home,
    val primary: Boolean = false,
    val showInNavBar: Boolean = primary,
    val hasOwnTopBar: Boolean = false,
) {
    var translatedKey: Lazy<String>? = null
    val childIds = mutableListOf<String>()
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Routes(
    private val context: RemoteSideContext,
) {
    companion object {
        const val CONFIG_IMPORT_CONFIRMATION_ROUTE = "config_import_confirmation"
        const val CONFIG_EXPORT_SUMMARY_ROUTE = "config_export_summary/?exportSensitiveData={exportSensitiveData}&includeSavedLocations={includeSavedLocations}"
        const val FRIEND_TRACKER_CONFIG_EXPORT_ROUTE = "friend_tracker_config_export/?rule_id={rule_id}"
        const val FRIEND_TRACKER_CONFIG_IMPORT_ROUTE = "friend_tracker_config_import"
        const val VIEW_LOGGER_HISTORY_ROUTE = "view_logger_history/{uri}"
    }

    lateinit var navController: NavController
    lateinit var activityLauncher: cock.crest.purrfectsnap.lite.ui.util.ActivityLauncherHelper
    var navigation: cock.crest.purrfectsnap.lite.ui.manager.Navigation? = null
    private val routes = mutableListOf<Route>()
    var bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
    var configJsonForImport: String? = null
    var friendTrackerConfigJsonForImport: String? = null
    var onRuleImported: (() -> Unit)? = null

    val configImportConfirmation = route(RouteInfo(CONFIG_IMPORT_CONFIRMATION_ROUTE, hasOwnTopBar = true), cock.crest.purrfectsnap.lite.ui.manager.pages.features.ConfigImportConfirmationScreen())
    val configExportSummary = route(RouteInfo(CONFIG_EXPORT_SUMMARY_ROUTE, hasOwnTopBar = true), cock.crest.purrfectsnap.lite.ui.manager.pages.features.ConfigExportSummaryScreen())

    val tasks = route(RouteInfo("tasks", icon = Icons.Default.TaskAlt, primary = true, hasOwnTopBar = true), TasksRootSection())

    val features = route(RouteInfo("features", icon = Icons.Default.Stars, primary = true, hasOwnTopBar = true), FeaturesRootSection())
    val manageRuleFeature = route(RouteInfo("manage_rule_feature/?rule_type={rule_type}", hasOwnTopBar = true), ManageRuleFeature()).parent(features)

    val home = route(RouteInfo("home", icon = Icons.Default.Home, primary = true, hasOwnTopBar = true), HomeRootSection())
    val about = route(RouteInfo("home_about", hasOwnTopBar = true), HomeAbout()).parent(home)
    val retroGame = route(RouteInfo("retro_game", hasOwnTopBar = true), RetroGameScreen()).parent(home)
    val settings = route(RouteInfo("home_settings", hasOwnTopBar = true), HomeSettings()).parent(home)
    val homeLogs = route(RouteInfo("home_logs", hasOwnTopBar = true), HomeLogs()).parent(home)
    val loggerHistory = route(RouteInfo("logger_history", hasOwnTopBar = true), LoggerHistoryRoot()).parent(home)
    val viewLoggerHistory = route(RouteInfo(VIEW_LOGGER_HISTORY_ROUTE, hasOwnTopBar = true), LoggerHistoryRoot()).parent(home)
    val friendTracker = route(RouteInfo("friend_tracker", icon = Icons.Default.PersonSearch, hasOwnTopBar = true), FriendTrackerManagerRoot()).parent(home)
    val editRule = route(RouteInfo("edit_rule/?rule_id={rule_id}", hasOwnTopBar = true), EditRule())
    val friendTrackerConfigExport = route(RouteInfo(FRIEND_TRACKER_CONFIG_EXPORT_ROUTE), cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerConfigExportScreen())
    val friendTrackerConfigImport = route(RouteInfo(FRIEND_TRACKER_CONFIG_IMPORT_ROUTE), cock.crest.purrfectsnap.lite.ui.manager.pages.tracker.FriendTrackerConfigImportScreen())
    val friendTrackerCatalog = route(RouteInfo("friend_tracker_catalog", hasOwnTopBar = true), FriendTrackerCatalog())
    val manageFriendTrackerRepos = route(RouteInfo("manage_friend_tracker_repos", hasOwnTopBar = true), ManageFriendTrackerReposSection())

    val fileImports = route(RouteInfo("file_imports", hasOwnTopBar = true), FileImportsRoot()).parent(home)
    val manageRepos = route(RouteInfo("manage_repos/?type={type}"), ManageReposSection())

    val social = route(RouteInfo("social", icon = Icons.Default.Group, primary = true, hasOwnTopBar = true), SocialRootSection())
    val manageScope = route(RouteInfo("manage_scope/?scope={scope}&id={id}", hasOwnTopBar = true), ManageScope()).parent(social)
    val messagingPreview = route(RouteInfo("messaging_preview/?scope={scope}&id={id}", hasOwnTopBar = true), MessagingPreview()).parent(social)
    val loggedStories = route(RouteInfo("logged_stories/?id={id}"), LoggedStories()).parent(social)

    val scripting = route(RouteInfo("scripts", icon = Icons.Filled.DataObject, primary = true, hasOwnTopBar = true), ScriptingRootSection())
    val manageScriptRepos = route(RouteInfo("manage_script_repos", hasOwnTopBar = true), ManageScriptReposSection())

    val betterLocation = route(RouteInfo("better_location", showInNavBar = false, primary = true), BetterLocationRoot())

    open class Route {
        open val init: () -> Unit = { }
        open val title: @Composable (() -> Unit)? = null
        open val topBarActions: @Composable RowScope.() -> Unit = {}
        open val floatingActionButton: @Composable () -> Unit = {}
        open val content: @Composable (NavBackStackEntry) -> Unit = {}
        open val customComposables: NavGraphBuilder.() -> Unit = {}

        var parentRoute: Route? = null
            private set

        lateinit var context: RemoteSideContext
        lateinit var routeInfo: RouteInfo
        lateinit var routes: Routes

        open val translation by lazy { context.translation.getCategory("manager.sections.${routeInfo.key.substringBefore("/")}")}

        private fun replaceArguments(id: String, args: Map<String, String>) = args.takeIf { it.isNotEmpty() }?.let {
            args.entries.fold(id) { acc, (key, value) ->
                acc.replace("{$key}", value)
            }
        } ?: id

        fun navigate(args: MutableMap<String, String>.() -> Unit = {}) {
            routes.navController.navigate(replaceArguments(routeInfo.id, HashMap<String, String>().apply { args() }))
        }

        fun navigateReload() {
            routes.navController.navigate(routeInfo.id) {
                popUpTo(routeInfo.id) { inclusive = true }
            }
        }

        fun navigateReset(args: MutableMap<String, String>.() -> Unit = {}) {
            routes.navController.navigate(replaceArguments(routeInfo.id, HashMap<String, String>().apply { args() })) {
                popUpTo(routes.navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun parent(route: Route): Route {
            assert(route.routeInfo.primary) { "Parent route must be a primary route" }
            parentRoute = route
            return this
        }
    }

    val currentRoute: Route?
        get() = routes.firstOrNull { route ->
            navController.currentBackStackEntry?.destination?.hierarchy?.any { it.route == route.routeInfo.id } ?: false
        }

    val currentDestination: String?
        get() = navController.currentBackStackEntry?.destination?.route

    fun getCurrentRoute(navBackStackEntry: NavBackStackEntry?): Route? {
        if (navBackStackEntry == null) return null

        return navBackStackEntry.destination.hierarchy.firstNotNullOfOrNull { destination ->
            routes.firstOrNull { route ->
                route.routeInfo.id == destination.route || route.routeInfo.childIds.contains(destination.route)
            }
        }
    }

    fun getRoutes(): List<Route> = routes

    private fun route(routeInfo: RouteInfo, route: Route): Route {
        route.apply {
            this.routeInfo = routeInfo
            routes = this@Routes
            context = this@Routes.context
            this.routeInfo.translatedKey = lazy { context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"] }
        }
        routes.add(route)
        return route
    }
}
