package cock.crest.purrfectsnap.lite.ui.manager.pages.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.bridge.location.LocationCoordinates
import cock.crest.purrfectsnap.lite.storage.addOrUpdateLocationCoordinate
import cock.crest.purrfectsnap.lite.storage.getLocationCoordinates
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class ConfigImportConfirmationScreen : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.features.config_import") }

    private data class ImportedFeature(
        val category: String,
        val name: String,
        val key: String,
        val value: Any,
        val indentation: Int
    )

    companion object {
        private const val COORDINATE_TOLERANCE = 0.0001 // ~11 meters tolerance for de-duplication
    }

    /**
     * Imports saved locations from JSON array into database with de-duplication.
     * Only adds locations that don't already exist (within coordinate tolerance).
     */
    private fun importSavedLocations(locationsArray: com.google.gson.JsonArray) {
        val existingLocations = context.database.getLocationCoordinates()
        
        for (i in 0 until locationsArray.size()) {
            val locationObj = locationsArray.get(i).asJsonObject
            val name = locationObj.get("name")?.asString ?: continue
            val latitude = locationObj.get("latitude")?.asDouble ?: continue
            val longitude = locationObj.get("longitude")?.asDouble ?: continue
            val radius = locationObj.get("radius")?.asDouble ?: 100.0
            
            // Check for existing location with similar coordinates (de-duplication)
            val existingMatch = existingLocations.find { existing ->
                abs(existing.latitude - latitude) < COORDINATE_TOLERANCE &&
                abs(existing.longitude - longitude) < COORDINATE_TOLERANCE
            }
            
            if (existingMatch == null) {
                // No duplicate found, add as new location
                val newLocation = LocationCoordinates().apply {
                    this.name = name
                    this.latitude = latitude
                    this.longitude = longitude
                    this.radius = radius
                }
                context.database.addOrUpdateLocationCoordinate(null, newLocation)
            }
            // If duplicate exists, skip (do not update or delete existing)
        }
    }

    private inner class ConfigParser {
        fun parse(configJson: String): Map<String, List<ImportedFeature>> {
            val featureList = mutableListOf<ImportedFeature>()
            val json = JSONObject(configJson)
            fun parseProperties(
                categoryKey: String,
                niceCategoryName: String,
                properties: JSONObject,
                prefix: String,
                indent: Int
            ) {
                for (key in properties.keys()) {
                    val value = properties.get(key)
                    val currentPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    if (value is JSONObject && value.has("state") && value.has("properties")) {
                        val featureNameKey =
                            "features.properties.$categoryKey.properties.${currentPrefix.split('.')
                                .joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                featureName,
                                key,
                                value.getBoolean("state"),
                                indent
                            )
                        )
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            currentPrefix,
                            indent + 1
                        )
                    } else if (value is JSONObject && value.has("properties")) {
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            currentPrefix,
                            indent
                        )
                    } else {
                        val featureNameKey =
                            "features.properties.$categoryKey.properties.${currentPrefix.split('.')
                                .joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                featureName,
                                key,
                                value,
                                indent
                            )
                        )
                    }
                }
            }
            for (categoryKey in json.keys()) {
                val value = json.get(categoryKey)
                if (value is JSONObject) {
                    val niceCategoryName =
                        context.translation["features.properties.$categoryKey.name"]
                            ?: categoryKey.replaceFirstChar { it.uppercase() }
                    if (value.has("state") && !value.has("properties")) {
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                translation["enable_feature"],
                                categoryKey,
                                value.getBoolean("state"),
                                0
                            )
                        )
                    } else if (value.has("properties")) {
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            "",
                            0
                        )
                    }
                }
            }
            return featureList.groupBy { it.category }
        }

        fun parseValue(featureKey: String, value: Any): Any {
            fun innerParse(v: Any): String {
                if (v is String) {
                    if (v.isBlank()) {
                        val emptyKey = "features.options.$featureKey.empty"
                        val translatedEmpty = context.translation[emptyKey]
                        val fallback = context.translation["features.options.empty"]?.takeUnless { it == "features.options.empty" }
                        return if (!translatedEmpty.isNullOrBlank() && translatedEmpty != emptyKey && !translatedEmpty.startsWith("features.")) translatedEmpty else (fallback ?: "Empty")
                    }
                    val translationKey = "features.options.$featureKey.$v"
                    val translated = context.translation[translationKey]
                    return if (!translated.isNullOrBlank() && translated != translationKey && !translated.startsWith("features.")) translated else v
                }
                return v.toString()
            }
            return when (value) {
                is Boolean -> if (value) translation["enabled"] else translation["disabled"]
                is JSONArray -> {
                    val list = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        list.add(innerParse(value.get(i)))
                    }
                    list
                }
                else -> innerParse(value)
            }
        }
    }

    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val parser = remember { ConfigParser() }
        val featuresByCategory = remember {
            routes.configJsonForImport?.let { parser.parse(it) } ?: emptyMap()
        }
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }
        val importLabel = translation["confirm_button"]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { routes.navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(context.translation["common.back"])
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translation["title"],
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                        Button(
                            onClick = {
                                routes.configJsonForImport?.let { json ->
                                    runCatching {
                                        val savedLocationsJson = context.config.loadFromString(json)
                                        
                                        // Import saved locations if present in the JSON
                                        savedLocationsJson?.let { locationsArray ->
                                            importSavedLocations(locationsArray)
                                        }
                                    }.onFailure { err ->
                                        context.longToast(
                                            context.translation.format(
                                                "config_import_failure_toast",
                                                "error" to (err.message ?: context.translation["common.unknown_error"])
                                            )
                                        )
                                    }
                                    context.shortToast(translation["config_imported_toast"])
                                    context.coroutineScope.launch(Dispatchers.Main) {
                                        routes.features.navigateReload()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.3f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(importLabel)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp + routes.bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                items(featuresByCategory.toList()) { (category, features) ->
                    val isExpanded = expandedState[category] ?: false
                    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedState[category] = !isExpanded },
                        shape = RoundedCornerShape(18.dp),
                        color = PurrfectPalette.cardOverlayColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 10.dp,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.32f)
                                )
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Color.White
                                    )
                                }
                                IconButton(onClick = { expandedState[category] = !isExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = translation["expand_button_description"],
                                        modifier = Modifier.graphicsLayer(rotationZ = rotationState),
                                        tint = Color.White
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    features.forEachIndexed { index, feature ->
                                        when (val parsedValue = parser.parseValue(feature.key, feature.value)) {
                                            is List<*> -> {
                                                Column(
                                                    modifier = Modifier.padding(start = (feature.indentation * 16).dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = feature.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                    Column(
                                                        modifier = Modifier.padding(start = 6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        parsedValue.forEachIndexed { itemIndex, item ->
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                NumberBubble(itemIndex + 1)
                                                                Text(
                                                                    text = item.toString(),
                                                                    color = PurrfectPalette.textSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            is String -> {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = (feature.indentation * 16).dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = feature.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = parsedValue,
                                                        color = PurrfectPalette.glowSecondary,
                                                        textAlign = TextAlign.Start,
                                                    )
                                                }
                                            }
                                        }
                                        if (index < features.size - 1) {
                                            Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
        }
    }

    @Composable
    private fun NumberBubble(number: Int) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.4f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
