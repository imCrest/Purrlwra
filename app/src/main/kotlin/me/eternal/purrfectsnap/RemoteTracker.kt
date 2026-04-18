package cock.crest.purrfectsnap.lite

import cock.crest.purrfectsnap.lite.bridge.logger.TrackerInterface
import cock.crest.purrfectsnap.lite.common.data.ScopedTrackerRule
import cock.crest.purrfectsnap.lite.common.data.TrackerEventsResult
import cock.crest.purrfectsnap.lite.common.data.TrackerRule
import cock.crest.purrfectsnap.lite.common.data.TrackerRuleEvent
import cock.crest.purrfectsnap.lite.common.util.toSerialized
import cock.crest.purrfectsnap.lite.storage.getRuleTrackerScopes
import cock.crest.purrfectsnap.lite.storage.getTrackerEvents
import cock.crest.purrfectsnap.lite.storage.updateFriendScore


class RemoteTracker(
    private val context: RemoteSideContext
): TrackerInterface.Stub() {
    fun init() {}

    override fun getTrackedEvents(eventType: String): String? {
        val events = mutableMapOf<TrackerRule, MutableList<TrackerRuleEvent>>()

        context.database.getTrackerEvents(eventType).forEach { (event, rule) ->
            events.getOrPut(rule) { mutableListOf() }.add(event)
        }

        return TrackerEventsResult(events.mapKeys {
            ScopedTrackerRule(it.key, context.database.getRuleTrackerScopes(it.key.id))
        }).toSerialized()
    }

    override fun updateFriendScore(userId: String, score: Long): Long {
        return context.database.updateFriendScore(userId, score)
    }
}