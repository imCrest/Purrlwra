package cock.crest.purrfectsnap.lite.core.bridge


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import cock.crest.purrfectsnap.lite.bridge.*
import cock.crest.purrfectsnap.lite.bridge.call.CallDownloadSession
import cock.crest.purrfectsnap.lite.bridge.e2ee.E2eeInterface
import cock.crest.purrfectsnap.lite.bridge.location.LocationManager
import cock.crest.purrfectsnap.lite.bridge.logger.LoggerInterface
import cock.crest.purrfectsnap.lite.bridge.logger.TrackerInterface
import cock.crest.purrfectsnap.lite.bridge.task.TaskInterface
import cock.crest.purrfectsnap.lite.bridge.scripting.IScripting
import cock.crest.purrfectsnap.lite.bridge.snapclient.MessagingBridge
import cock.crest.purrfectsnap.lite.bridge.storage.FileHandleManager
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.data.MessagingFriendInfo
import cock.crest.purrfectsnap.lite.common.data.MessagingGroupInfo
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.SocialScope
import cock.crest.purrfectsnap.lite.common.ui.OverlayType
import cock.crest.purrfectsnap.lite.common.util.toSerialized
import cock.crest.purrfectsnap.lite.core.ModContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class BridgeClient(
    private val context: ModContext
):  ServiceConnection {
    private var continuation: Continuation<Boolean>? = null
    private val connectSemaphore = Semaphore(permits = 1)
    private val reconnectSemaphore = Semaphore(permits = 1)
    private lateinit var service: BridgeInterface

    private val onConnectedCallbacks = mutableListOf<suspend () -> Unit>()
    private var cachePurrfectSnapApkPath: String? = null

    fun addOnConnectedCallback(initNow: Boolean = false, callback: suspend () -> Unit) {
        synchronized(onConnectedCallbacks) {
            onConnectedCallbacks.add(callback)
        }
        initNow.takeIf { it && this::service.isInitialized }?.let {
            runBlocking {
                callback()
            }
        }
    }

    private fun resumeContinuation(state: Boolean) {
        runBlocking {
            connectSemaphore.withPermit {
                runCatching { continuation?.resume(state) }
                continuation = null
            }
        }
    }

    suspend fun connect(onFailure: (Throwable) -> Unit): Boolean? {
        if (this::service.isInitialized && service.asBinder().pingBinder()) {
            return true
        }

        val connectionTimeout = 15000L
        val retryDelay = 3000L

        return withTimeoutOrNull(connectionTimeout) {
            var result: Boolean? = null

            for (retry in 0.. (connectionTimeout / retryDelay).toInt()) {
                result = withTimeoutOrNull(retryDelay) {
                    suspendCancellableCoroutine { cancellableContinuation ->
                        continuation = cancellableContinuation
                        with(context.androidContext) {
                            //ensure the remote process is running
                            runCatching {
                                startActivity(Intent()
                                    .setClassName(Constants.MODULE_PACKAGE_NAME, "cock.crest.purrfectsnap.lite.bridge.ForceStartActivity")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                )
                            }

                            runCatching {
                                val intent = Intent()
                                    .setClassName(Constants.MODULE_PACKAGE_NAME, "cock.crest.purrfectsnap.lite.bridge.BridgeService")
                                runCatching {
                                    if (this@BridgeClient::service.isInitialized) {
                                        unbindService(this@BridgeClient)
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    bindService(
                                        intent,
                                        Context.BIND_AUTO_CREATE,
                                        Executors.newSingleThreadExecutor(),
                                        this@BridgeClient
                                    )
                                } else {
                                    val handler = Handler(HandlerThread("BridgeClient").apply { start() }.looper)
                                    this::class.java.methods.firstOrNull {
                                        it.name == "bindServiceAsUser" && it.parameterTypes.size == 5
                                    }?.invoke(
                                        this,
                                        intent,
                                        this@BridgeClient,
                                        Context.BIND_AUTO_CREATE,
                                        handler,
                                        Process.myUserHandle()
                                    ) ?: throw NoSuchMethodException("bindServiceAsUser")
                                }
                            }.onFailure {
                                onFailure(it)
                                resumeContinuation(false)
                            }
                        }
                    }
                }
                if (result != null) break
            }

            result
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        this.service = BridgeInterface.Stub.asInterface(service)
        runBlocking {
            onConnectedCallbacks.forEach {
                runCatching {
                    it()
                }.onFailure {
                    context.log.error("Failed to run onConnectedCallback", it)
                }
            }
        }
        cachePurrfectSnapApkPath = this.service.applicationApkPath.also {
            if (cachePurrfectSnapApkPath != null && cachePurrfectSnapApkPath != it) {
                context.log.verbose("Restarting Snapchat due to PurrfectSnap update")
                context.softRestartApp()
                return
            }
        }
        resumeContinuation(true)
    }

    override fun onNullBinding(name: ComponentName) {
        resumeContinuation(false)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        continuation = null
    }

    private fun tryReconnect() {
        runBlocking {
            reconnectSemaphore.withPermit {
                if (service.asBinder().pingBinder()) return@runBlocking
                Log.d("BridgeClient", "service is dead, restarting")
                val canLoad = connect {
                    Log.e("BridgeClient", "connection failed", it)
                }
                if (canLoad != true) {
                    Log.e("BridgeClient", "failed to reconnect to service, result=$canLoad")
                    return@runBlocking
                }
            }
        }
    }

    private fun <T> safeServiceCall(block: () -> T): T {
        return runCatching {
            block()
        }.getOrElse { throwable ->
            if (throwable is DeadObjectException) {
                tryReconnect()
                return@getOrElse runCatching {
                    block()
                }.getOrElse {
                    Log.e("BridgeClient", "service call failed", it)
                    throw it
                }
            }
            throw throwable
        }
    }

    fun broadcastLog(tag: String, level: String, message: String) {
        message.chunked(1024 * 256).forEach {
            runCatching {
                service.broadcastLog(tag, level, it)
            }
        }
    }

    fun getApplicationApkPath(): String = safeServiceCall { service.applicationApkPath }

    fun enqueueDownload(intent: Intent, callback: DownloadCallback) = safeServiceCall {
        service.enqueueDownload(intent, callback)
    }

    fun convertMedia(
        input: ParcelFileDescriptor,
        inputExtension: String,
        outputExtension: String,
        audioCodec: String?,
        videoCodec: String?
    ): ParcelFileDescriptor? = safeServiceCall {
        service.convertMedia(input, inputExtension, outputExtension, audioCodec, videoCodec)
    }

    fun sync(callback: SyncCallback) {
        if (!context.database.hasMain()) return
        safeServiceCall {
            service.sync(callback)
        }
    }

    fun triggerSync(scope: SocialScope, id: String) = safeServiceCall {
        service.triggerSync(scope.key, id)
    }

    fun passGroupsAndFriends(groups: List<MessagingGroupInfo>, friends: List<MessagingFriendInfo>) =
        safeServiceCall {
            val serializedGroups = groups.mapNotNull { it.toSerialized() }
            val serializedFriends = friends.mapNotNull { it.toSerialized() }
            val maxChunkBytes = 128 * 1024

            fun chunkSerialized(values: List<String>): List<List<String>> {
                if (values.isEmpty()) return listOf(emptyList())
                val result = mutableListOf<List<String>>()
                val currentChunk = mutableListOf<String>()
                var currentSize = 0

                values.forEach { value ->
                    val valueSize = value.toByteArray(StandardCharsets.UTF_8).size + 32
                    if (currentChunk.isNotEmpty() && currentSize + valueSize > maxChunkBytes) {
                        result += currentChunk.toList()
                        currentChunk.clear()
                        currentSize = 0
                    }
                    currentChunk += value
                    currentSize += valueSize
                }

                if (currentChunk.isNotEmpty()) {
                    result += currentChunk.toList()
                }
                return result
            }

            val groupChunks = chunkSerialized(serializedGroups)
            val friendChunks = chunkSerialized(serializedFriends)
            val chunkCount = maxOf(groupChunks.size, friendChunks.size)

            context.log.info(
                "Sending social snapshot in $chunkCount chunk(s): " +
                    "${serializedGroups.size} groups, ${serializedFriends.size} friends"
            )

            repeat(chunkCount) { index ->
                service.passGroupsAndFriends(
                    groupChunks.getOrElse(index) { emptyList() },
                    friendChunks.getOrElse(index) { emptyList() }
                )
            }
        }

    fun getRules(targetUuid: String): List<MessagingRuleType> = safeServiceCall {
        service.getRules(targetUuid).mapNotNull { MessagingRuleType.getByName(it) }
    }

    fun getRuleIds(ruleType: MessagingRuleType): List<String> = safeServiceCall {
        service.getRuleIds(ruleType.key)
    }

    fun setRule(targetUuid: String, type: MessagingRuleType, state: Boolean) = safeServiceCall {
        service.setRule(targetUuid, type.key, state)
    }

    fun getScopeNotes(id: String): String? = safeServiceCall { service.getScopeNotes(id) }

    fun setScopeNotes(id: String, content: String?) = safeServiceCall { service.setScopeNotes(id, content) }

    fun getAllScopeNotes(): Map<String, String> = safeServiceCall { service.getAllScopeNotes() }

    fun setAllScopeNotes(notes: Map<String, String>) = safeServiceCall { service.setAllScopeNotes(notes) }

    fun getScriptingInterface(): IScripting? = safeServiceCall<IScripting?> { service.scriptingInterface }

    fun getE2eeInterface(): E2eeInterface = safeServiceCall { service.e2eeInterface }

    fun getMessageLogger(): LoggerInterface = safeServiceCall { service.logger }

    fun getTracker(): TrackerInterface = safeServiceCall { service.tracker }

    fun getAccountStorage(): AccountStorage = safeServiceCall { service.accountStorage }

    fun getFileHandlerManager(): FileHandleManager = safeServiceCall { service.fileHandleManager }

    fun getLocationManager(): LocationManager = safeServiceCall { service.locationManager }

    fun getTaskInterface(): TaskInterface = safeServiceCall { service.taskInterface }

    fun registerMessagingBridge(bridge: MessagingBridge) = safeServiceCall { service.registerMessagingBridge(bridge) }

    fun openOverlay(type: OverlayType) = safeServiceCall { service.openOverlay(type.key) }
    fun closeOverlay() = safeServiceCall { service.closeOverlay() }

    fun registerConfigStateListener(listener: ConfigStateListener) = safeServiceCall { service.registerConfigStateListener(listener) }

    fun getDebugProp(name: String, defaultValue: String? = null): String? = safeServiceCall { service.getDebugProp(name, defaultValue) }

    fun startCallDownload(
        startTimestamp: Long,
        author: String,
    ): CallDownloadSession {
        return safeServiceCall { service.startCallDownload(startTimestamp, author) }
    }
}

