package cock.crest.purrfectsnap.lite.common.data

data class ExportedTrackerData(
    val type: ExportType,
    val rules: List<TrackerRule>
)