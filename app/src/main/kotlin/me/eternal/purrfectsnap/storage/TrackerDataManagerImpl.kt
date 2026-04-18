package cock.crest.purrfectsnap.lite.storage

import cock.crest.purrfectsnap.lite.common.data.ExportedTrackerData
import cock.crest.purrfectsnap.lite.common.data.TrackerDataManager
import cock.crest.purrfectsnap.lite.storage.AppDatabase

class TrackerDataManagerImpl(private val db: AppDatabase) : TrackerDataManager {
    override fun getExportedTrackerData(): ExportedTrackerData {
        return ExportedTrackerData(
            type = cock.crest.purrfectsnap.lite.common.data.ExportType.BULK,
            rules = db.getTrackerRulesDesc().map { rule ->
                rule.copy(
                    events = db.getTrackerEvents(rule.id),
                    scopes = db.getRuleTrackerScopes(rule.id)
                )
            }
        )
    }

    override fun getExportedTrackerData(ruleId: Int): ExportedTrackerData? {
        return db.getTrackerRule(ruleId)?.let {
            ExportedTrackerData(
                type = cock.crest.purrfectsnap.lite.common.data.ExportType.SINGLE,
                rules = listOf(it.copy(
                    events = db.getTrackerEvents(it.id),
                    scopes = db.getRuleTrackerScopes(it.id)
                ))
            )
        }
    }

    override fun importTrackerData(data: ExportedTrackerData) {
        if (data.type == cock.crest.purrfectsnap.lite.common.data.ExportType.BULK) {
            db.clearTrackerRules()
        }
        data.rules.forEach { rule ->
            if (db.getTrackerRuleByName(rule.name) != null) {
                return@forEach
            }
            val ruleId = db.newTrackerRule(rule.name, rule.author)
            db.setTrackerRuleState(ruleId, rule.enabled)
            rule.events?.forEach { event ->
                db.addOrUpdateTrackerRuleEvent(
                    ruleId = ruleId,
                    eventType = event.eventType,
                    params = event.params,
                    actions = event.actions
                )
            }
            rule.scopes?.let { scopes ->
                if (scopes.isNotEmpty()) {
                    val scopeType = scopes.values.first()
                    val scopeIds = scopes.keys.toList()
                    db.setRuleTrackerScopes(ruleId, scopeType, scopeIds)
                }
            }
        }
    }
}
