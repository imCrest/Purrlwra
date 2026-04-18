package cock.crest.purrfectsnap.lite.bridge.logger;

interface TrackerInterface {
    String getTrackedEvents(String eventType); // returns serialized TrackerEventsResult

    long updateFriendScore(String userId, long score); // returns old score (-1 if not found)
}