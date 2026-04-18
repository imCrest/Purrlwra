package cock.crest.purrfectsnap.lite.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.SharedContextHolder
import cock.crest.purrfectsnap.lite.bridge.call.CallDownloadSession
import cock.crest.purrfectsnap.lite.bridge.snapclient.MessagingBridge
import cock.crest.purrfectsnap.lite.common.data.MessagingFriendInfo
import cock.crest.purrfectsnap.lite.common.data.MessagingGroupInfo
import cock.crest.purrfectsnap.lite.common.data.SocialScope
import cock.crest.purrfectsnap.lite.common.logger.LogLevel
import cock.crest.purrfectsnap.lite.common.ui.OverlayType
import cock.crest.purrfectsnap.lite.common.util.toParcelable
import cock.crest.purrfectsnap.lite.download.DownloadProcessor
import cock.crest.purrfectsnap.lite.download.FFMpegProcessor
import cock.crest.purrfectsnap.lite.download.call.CallDownloadSessionImpl
import cock.crest.purrfectsnap.lite.storage.*
import cock.crest.purrfectsnap.lite.task.Task
import cock.crest.purrfectsnap.lite.task.TaskType
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

class BridgeService : Service() {
    private lateinit var remoteSideContext: RemoteSideContext
    private var syncCallback: SyncCallback? = null
    var messagingBridge: MessagingBridge? = null
    @Volatile
    private var pendingSocialSnapshotCallback: ((List<MessagingFriendInfo>, List<MessagingGroupInfo>) -> Unit)? = null

    private fun clearSyncCallback() {
        syncCallback = null
    }

    fun requestEphemeralSocialSnapshot(callback: (List<MessagingFriendInfo>, List<MessagingGroupInfo>) -> Unit) {
        pendingSocialSnapshotCallback = callback
    }

    fun clearEphemeralSocialSnapshotRequest() {
        pendingSocialSnapshotCallback = null
    }

    override fun onDestroy() {
        clearSyncCallback()
        if (::remoteSideContext.isInitialized) {
            remoteSideContext.bridgeService = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        remoteSideContext = SharedContextHolder.remote(this).apply {
            if (checkForRequirements()) return null
        }
        remoteSideContext.apply {
            bridgeService = this@BridgeService
        }
        return BridgeBinder()
    }

    fun triggerScopeSync(scope: SocialScope, id: String, updateOnly: Boolean = false) {
        val callback = syncCallback ?: return
        runCatching {
            val database = remoteSideContext.database
            val syncedObject = when (scope) {
                SocialScope.FRIEND -> {
                    if (updateOnly && database.getFriendInfo(id) == null) return
                    callback.syncFriend(id)
                }
                SocialScope.GROUP -> {
                    if (updateOnly && database.getGroupInfo(id) == null) return
                    callback.syncGroup(id)
                }
            } ?: run {
                if (updateOnly) {
                    when (scope) {
                        SocialScope.FRIEND -> database.deleteFriend(id)
                        SocialScope.GROUP -> database.deleteGroup(id)
                    }
                    return
                }
                remoteSideContext.log.warn("Failed to sync $scope $id")
                return
            }

            when (scope) {
                SocialScope.FRIEND -> {
                    toParcelable<MessagingFriendInfo>(syncedObject)?.let { database.syncFriend(it) } ?: run {
                        if (updateOnly) {
                            database.deleteFriend(id)
                            return
                        }
                        remoteSideContext.log.warn("Failed to sync $scope $id")
                        return
                    }
                }
                SocialScope.GROUP -> {
                    toParcelable<MessagingGroupInfo>(syncedObject)?.let { database.syncGroupInfo(it) } ?: run {
                        if (updateOnly) {
                            database.deleteGroup(id)
                            return
                        }
                        remoteSideContext.log.warn("Failed to sync $scope $id")
                        return
                    }
                }
            }
        }.onFailure {
            if (it is RemoteException) {
                clearSyncCallback()
                remoteSideContext.log.warn("Failed to sync $scope $id: Callback is dead")
                return@onFailure
            }
            remoteSideContext.log.error("Failed to sync $scope $id", it)
        }
    }

    inner class BridgeBinder : BridgeInterface.Stub() {
        override fun getApplicationApkPath(): String = applicationInfo.publicSourceDir

        override fun broadcastLog(tag: String, level: String, message: String) {
            remoteSideContext.log.internalLog(tag, LogLevel.fromShortName(level) ?: LogLevel.INFO, message)
        }
        override fun enqueueDownload(intent: Intent, callback: DownloadCallback) {
            DownloadProcessor(
                remoteSideContext = remoteSideContext,
                callback = callback
            ).onReceive(intent)
        }

        override fun convertMedia(
            input: ParcelFileDescriptor?,
            inputExtension: String,
            outputExtension: String,
            audioCodec: String?,
            videoCodec: String?
        ): ParcelFileDescriptor? {
            return runBlocking {
                val taskId = UUID.randomUUID().toString()
                val inputFile = File.createTempFile(taskId, ".$inputExtension", remoteSideContext.androidContext.cacheDir)

                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(input).use { inputStream ->
                        inputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }.onFailure {
                    remoteSideContext.log.error("Failed to copy input file", it)
                    inputFile.delete()
                    return@runBlocking null
                }
                val cachedFile = File.createTempFile(taskId, ".$outputExtension", remoteSideContext.androidContext.cacheDir)

                val pendingTask = remoteSideContext.taskManager.createPendingTask(
                    Task(
                        type = TaskType.DOWNLOAD,
                        title = remoteSideContext.translation["task_media_conversion_title"],
                        author = null,
                        hash = taskId
                    )
                )
                runCatching {
                    FFMpegProcessor.newFFMpegProcessor(remoteSideContext, pendingTask).execute(
                        FFMpegProcessor.Request(
                            action = FFMpegProcessor.Action.CONVERSION,
                            inputs = listOf(inputFile.absolutePath),
                            output = cachedFile,
                            videoCodec = videoCodec,
                            audioCodec = audioCodec
                        )
                    )
                    pendingTask.success()
                    return@runBlocking ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }.onFailure {
                    pendingTask.fail(it.message ?: "Failed to convert video")
                    remoteSideContext.log.error("Failed to convert video", it)
                }

                inputFile.delete()
                cachedFile.delete()
                null
            }
        }

        override fun getRules(uuid: String): List<String> {
            return remoteSideContext.database.getRules(uuid).map { it.key }
        }

        override fun getRuleIds(type: String): MutableList<String> {
            return remoteSideContext.database.getRuleIds(type)
        }

        override fun setRule(uuid: String, rule: String, state: Boolean) {
            remoteSideContext.database.setRule(uuid, rule, state)
        }

        override fun sync(callback: SyncCallback) {
            clearSyncCallback()
            syncCallback = callback
            measureTimeMillis {
                remoteSideContext.database.getFriends().map { it.userId } .forEach { friendId ->
                    triggerScopeSync(SocialScope.FRIEND, friendId, true)
                }
                remoteSideContext.database.getGroups().map { it.conversationId }.forEach { groupId ->
                    triggerScopeSync(SocialScope.GROUP, groupId, true)
                }
            }.also {
                remoteSideContext.log.verbose("Syncing remote took $it ms")
            }
        }

        override fun triggerSync(scope: String, id: String) {
            remoteSideContext.log.verbose("trigger sync for $scope $id")
            triggerScopeSync(SocialScope.getByName(scope), id, true)
        }

        override fun passGroupsAndFriends(
            groups: List<String>,
            friends: List<String>
        ) {
            remoteSideContext.log.verbose("Received ${groups.size} groups and ${friends.size} friends")
            val parsedFriends = friends.mapNotNull { toParcelable<MessagingFriendInfo>(it) }
            val parsedGroups = groups.mapNotNull { toParcelable<MessagingGroupInfo>(it) }
            pendingSocialSnapshotCallback?.let { callback ->
                pendingSocialSnapshotCallback = null
                callback(parsedFriends, parsedGroups)
                return
            }
            remoteSideContext.database.replaceMessagingData(parsedFriends, parsedGroups)
            remoteSideContext.database.receiveMessagingDataCallback(parsedFriends, parsedGroups)
        }

        override fun getScopeNotes(id: String): String? {
            return remoteSideContext.database.getScopeNotes(id)
        }

        override fun setScopeNotes(id: String, content: String?) {
            remoteSideContext.database.setScopeNotes(id, content)
        }

        override fun getAllScopeNotes(): Map<String, String> {
            return remoteSideContext.database.getAllScopeNotes()
        }

        override fun setAllScopeNotes(notes: Map<String, String>) {
            remoteSideContext.database.setAllScopeNotes(notes)
        }

        override fun getScriptingInterface() = remoteSideContext.scriptManager

        override fun getE2eeInterface() = remoteSideContext.e2eeImplementation
        override fun getLogger() = remoteSideContext.messageLogger
        override fun getTracker() = remoteSideContext.tracker
        override fun getAccountStorage() = remoteSideContext.accountStorage
        override fun getFileHandleManager() = remoteSideContext.fileHandleManager
        override fun getLocationManager() = remoteSideContext.locationManager
        override fun getTaskInterface() = remoteSideContext.taskInterface

        override fun registerMessagingBridge(bridge: MessagingBridge) {
            messagingBridge = bridge
        }

        override fun openOverlay(type: String) {
            runCatching {
                val overlayType = OverlayType.fromKey(type) ?: throw IllegalArgumentException("Unknown overlay type: $type")
                remoteSideContext.remoteOverlay.show { routes ->
                    when (overlayType) {
                        OverlayType.SETTINGS -> routes.features
                        OverlayType.BETTER_LOCATION -> routes.betterLocation
                    }
                }
            }.onFailure {
                remoteSideContext.log.error("Failed to open $type overlay", it)
            }
        }

        override fun closeOverlay() {
            runCatching {
                remoteSideContext.remoteOverlay.close()
            }.onFailure {
                remoteSideContext.log.error("Failed to close overlay", it)
            }
        }

        override fun registerConfigStateListener(listener: ConfigStateListener) {
            remoteSideContext.config.configStateListener = listener
        }

        override fun getDebugProp(key: String, defaultValue: String?): String? {
            return remoteSideContext.sharedPreferences.all["debug_$key"]?.toString() ?: defaultValue
        }

        override fun startCallDownload(
            startTimestamp: Long,
            author: String
        ): CallDownloadSession {
            return CallDownloadSessionImpl(
                context = remoteSideContext,
                callStartTimestamp = startTimestamp,
                author = author
            )
        }
    }
}
