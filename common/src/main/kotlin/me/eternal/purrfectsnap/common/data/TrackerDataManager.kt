package cock.crest.purrfectsnap.lite.common.data

interface TrackerDataManager {
    fun getExportedTrackerData(): ExportedTrackerData
    fun getExportedTrackerData(ruleId: Int): ExportedTrackerData?
    fun importTrackerData(data: ExportedTrackerData)
}