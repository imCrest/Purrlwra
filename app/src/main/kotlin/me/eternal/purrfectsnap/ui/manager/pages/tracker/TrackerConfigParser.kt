package cock.crest.purrfectsnap.lite.ui.manager.pages.tracker

import cock.crest.purrfectsnap.lite.common.data.ExportedTrackerData
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import org.json.JSONArray

data class ImportedFeature(
    val category: String,
    val name: String,
    val key: String,
    val value: Any,
    val indentation: Int
)

class TrackerConfigParser(private val context: Routes.Route) {
    fun parse(configJson: String): Map<String, List<ImportedFeature>> {
        val featureMap = mutableMapOf<String, MutableList<ImportedFeature>>()
        val exportedData = context.context.gson.fromJson(configJson, ExportedTrackerData::class.java)
        val authorLabel = context.translation["tracker_author_label"]
        val enabledLabel = context.translation["tracker_enabled_label"]
        val enabledValue = context.translation["tracker_enabled_value"]
        val disabledValue = context.translation["tracker_disabled_value"]
        exportedData.rules.forEach { rule ->
            val features = mutableListOf<ImportedFeature>()
            features.add(ImportedFeature(rule.name, authorLabel, "author", rule.author ?: context.context.translation["common.unknown"], 0))
            features.add(ImportedFeature(rule.name, enabledLabel, "enabled", if (rule.enabled) enabledValue else disabledValue, 0))
            rule.events?.forEach { event ->
                features.add(ImportedFeature(rule.name, context.context.translation["tracker_events.${event.eventType}"], event.eventType, event.actions.joinToString(", ") { context.context.translation["tracker_actions.${it.key}"] }, 1))
            }
            featureMap[rule.name] = features
        }
        return featureMap
    }

    fun parseValue(featureKey: String, value: Any): Any {
        return when (value) {
            is Boolean -> if (value) context.translation["tracker_enabled_value"] else context.translation["tracker_disabled_value"]
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until value.length()) {
                    list.add(value.get(i).toString())
                }
                list
            }
            else -> value.toString()
        }
    }
}
