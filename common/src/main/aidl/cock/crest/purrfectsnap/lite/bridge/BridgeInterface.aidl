package cock.crest.purrfectsnap.lite.bridge;

import java.util.List;
import cock.crest.purrfectsnap.lite.bridge.DownloadCallback;
import cock.crest.purrfectsnap.lite.bridge.SyncCallback;
import cock.crest.purrfectsnap.lite.bridge.scripting.IScripting;
import cock.crest.purrfectsnap.lite.bridge.e2ee.E2eeInterface;
import cock.crest.purrfectsnap.lite.bridge.logger.LoggerInterface;
import cock.crest.purrfectsnap.lite.bridge.logger.TrackerInterface;
import cock.crest.purrfectsnap.lite.bridge.ConfigStateListener;
import cock.crest.purrfectsnap.lite.bridge.snapclient.MessagingBridge;
import cock.crest.purrfectsnap.lite.bridge.AccountStorage;
import cock.crest.purrfectsnap.lite.bridge.storage.FileHandleManager;
import cock.crest.purrfectsnap.lite.bridge.location.LocationManager;
import cock.crest.purrfectsnap.lite.bridge.call.CallDownloadSession;
import cock.crest.purrfectsnap.lite.bridge.task.TaskInterface;

interface BridgeInterface {
    /**
    * Get the PurrfectSnap APK path (used in LSPatch updater and for auto bridge restart)
    */
    String getApplicationApkPath();

    /**
    * broadcast a log message
    */
    oneway void broadcastLog(String tag, String level, String message);

    /**
     * Enqueue a download
     */
    oneway void enqueueDownload(in Intent intent, DownloadCallback callback);

    /**
     * File conversation
     */
    @nullable ParcelFileDescriptor convertMedia(in ParcelFileDescriptor input, String inputExtension, String outputExtension, @nullable String audioCodec, @nullable String videoCodec);

    /**
    * Get rules for a given user or conversation
    * @return list of rules (MessagingRuleType)
    */
    List<String> getRules(String uuid);

    /**
    * Get all ids for a specific rule
    * @param type rule type (MessagingRuleType)
    * @return list of ids
    */
    List<String> getRuleIds(String type);

    /**
    * Update rule for a giver user or conversation
    *
    * @param type rule type (MessagingRuleType)
    */
    oneway void setRule(String uuid, String type, boolean state);

    /**
    * Sync groups and friends
    */
    oneway void sync(SyncCallback callback);

    /**
    * Trigger sync for an id
    */
    oneway void triggerSync(String scope, String id);

    /**
    * Pass all groups and friends to be able to add them to the database
    * @param groups list of groups (MessagingGroupInfo as parcelable)
    * @param friends list of friends (MessagingFriendInfo as parcelable)
    */
    oneway void passGroupsAndFriends(in List<String> groups, in List<String> friends);

    @nullable String getScopeNotes(String id);

    oneway void setScopeNotes(String id, String content);

    Map<String, String> getAllScopeNotes();

    oneway void setAllScopeNotes(in Map<String, String> notes);

    IScripting getScriptingInterface();

    E2eeInterface getE2eeInterface();

    LoggerInterface getLogger();

    TrackerInterface getTracker();

    AccountStorage getAccountStorage();

    FileHandleManager getFileHandleManager();

    LocationManager getLocationManager();

    TaskInterface getTaskInterface();

    oneway void registerMessagingBridge(MessagingBridge bridge);

    oneway void openOverlay(String type);

    oneway void closeOverlay();

    oneway void registerConfigStateListener(in ConfigStateListener listener);

    @nullable String getDebugProp(String key, @nullable String defaultValue);

    CallDownloadSession startCallDownload(long startTimestamp, String author);
}
