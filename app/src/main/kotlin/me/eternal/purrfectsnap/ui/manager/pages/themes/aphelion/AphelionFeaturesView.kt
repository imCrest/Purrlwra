package cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion

import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection.Companion.FEATURE_CONTAINER_ROUTE
import cock.crest.purrfectsnap.lite.ui.manager.pages.features.FeaturesRootSection.Companion.SEARCH_FEATURE_ROUTE
import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.PropertyPair
import cock.crest.purrfectsnap.lite.common.config.toPropertyPair

@Composable
fun FeaturesRootSection.AphelionFeaturesScreen(nav: NavBackStackEntry) {
    val navBackStackEntry by routes.navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    when (currentDestination?.route) {
        FEATURE_CONTAINER_ROUTE -> {
            val containerName = navBackStackEntry?.arguments?.getString("name")!!
            val propertyPair = allContainers[containerName]!!
            Container(
                configContainer = propertyPair.value.get() as ConfigContainer,
                stateKey = "${routeInfo.id}:container:$containerName",
                sectionTitle = context.translation[propertyPair.key.propertyName()],
                sectionSubtitle = context.translation[propertyPair.key.propertyDescription()],
                onBack = { routes.navController.popBackStack() }
            )
        }
        SEARCH_FEATURE_ROUTE -> {
            val keyword = navBackStackEntry?.arguments?.getString("keyword").orEmpty()
            val properties = allProperties.filter {
                isSearchVisibleProperty(it.key) && (
                    it.key.name.contains(keyword, ignoreCase = true) ||
                        context.translation[it.key.propertyName()].contains(keyword, ignoreCase = true) ||
                        context.translation[it.key.propertyDescription()].contains(keyword, ignoreCase = true)
                )
            }.map { (it.key to it.value).toPropertyPair() }

            PropertiesView(
                properties = properties,
                stateKey = "${routeInfo.id}:search:$keyword",
                isSearchResults = true,
                searchKeyword = keyword,
                enableGlobalSearch = true,
                onBack = { navigateToMainRoot() }
            )
        }
        else -> {
            Container(
                configContainer = context.config.root,
                stateKey = "${routeInfo.id}:container:root"
            )
        }
    }
}
