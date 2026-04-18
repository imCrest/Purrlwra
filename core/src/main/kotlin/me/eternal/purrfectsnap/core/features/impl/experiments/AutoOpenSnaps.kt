package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import cock.crest.purrfectsnap.lite.bridge.AutoOpenInterface
import cock.crest.purrfectsnap.lite.common.config.PropertyValue
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessageState
import cock.crest.purrfectsnap.lite.common.data.MessageUpdate
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * AutoOpenSnaps: High-performance engine with real-time diagnostics.
 * Optimized for 20+ snaps/s with accurate stats and background resilience.
 */
class AutoOpenSnaps: MessagingRuleFeature("Auto Open Snaps", MessagingRuleType.AUTO_OPEN_SNAPS) {
    companion object {
        const val ACTION_PAUSE_RESUME = "cock.crest.purrfectsnap.lite.AUTO_OPEN_SNAPS_PAUSE_RESUME"
        const val ACTION_CLEAR_QUEUE = "cock.crest.purrfectsnap.lite.AUTO_OPEN_SNAPS_CLEAR_QUEUE"
        const val ACTION_STOP_ENGINE = "cock.crest.purrfectsnap.lite.AUTO_OPEN_SNAPS_STOP_ENGINE"
        
        private const val STATUS_NOTIFICATION_ID = 54321
        private const val NOTIFICATION_GROUP_KEY = "purrfectsnap.AUTO_OPEN"
        private const val PREF_TOTAL_OPENED = "auto_open_total_opened"
        private const val PREF_SESSION_START = "auto_open_session_start"
        
        private const val LAZY_SAVE_INTERVAL_MS = 600_000L 
    }

    private val gson = Gson()
    private val isPaused = AtomicBoolean(false)
    private val engineActive = AtomicBoolean(true)
    private val totalProcessed = AtomicInteger(0)
    private val sessionProcessed = AtomicInteger(0)
    private val sessionStartTime = AtomicLong(System.currentTimeMillis())
    private val averageProcessingTime = AtomicLong(800)
    private val lastSnapProcessedAt = AtomicLong(0)
    
    private val snapChannel = Channel<SnapQueueItem>(Channel.UNLIMITED)
    private val openedSnapsIds = ConcurrentHashMap.newKeySet<Long>()
    private val queuedSnaps = LinkedList<SnapQueueItem>()
    private var engineJob: Job? = null
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val autoOpenConfig by lazy { this@AutoOpenSnaps.context.config.messaging.autoOpenSnaps }
    private val notificationManager by lazy { this@AutoOpenSnaps.context.androidContext.getSystemService(NotificationManager::class.java) }
    private val prefs by lazy { this@AutoOpenSnaps.context.androidContext.getSharedPreferences("cock.crest.purrfectsnap.lite_preferences", Context.MODE_PRIVATE) }
    private val messaging by lazy { this@AutoOpenSnaps.context.feature(Messaging::class) }
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentStatusText = "Monitoring..."
    private var currentSpeedText = "Full Speed"
    private var lastNotificationUpdate = 0L
    private val notificationUpdateDelay = 1000L
    private val pendingNotificationUpdate = AtomicBoolean(false)
    private val snapTimestamps = LinkedList<Long>()
    private var lastConversationId: String? = null
    
    private val isSaving = AtomicBoolean(false)
    private val needsSaving = AtomicBoolean(false)
    private var isThermalThrottled = false
    private var lastThermalThrottleAt = 0L

    private fun logInfo(msg: String) = this@AutoOpenSnaps.context.log.info("[AutoOpenEngine] $msg")
    private fun logError(msg: String, e: Throwable? = null) = if (e != null) this@AutoOpenSnaps.context.log.error("[AutoOpenEngine] $msg", e) else this@AutoOpenSnaps.context.log.error("[AutoOpenEngine] $msg")

    private fun getSnapsPerSecond(): Double {
        val now = System.currentTimeMillis(); val window = 5000L
        synchronized(snapTimestamps) {
            snapTimestamps.removeIf { now - it > window }
            // Smoother calculation for high-frequency bursts
            return if (snapTimestamps.isEmpty()) 0.0 else (snapTimestamps.size.toDouble() / (window / 1000.0))
        }
    }

    private fun formatDuration(m: Long): String {
        val s = (m / 1000) % 60; val min = (m / 60000) % 60; val h = m / 3600000
        return when { h > 0 -> "${h}h ${min}m"; min > 0 -> "${min}m ${s}s"; else -> "${s}s" }
    }

    override fun init() {
        restorePersistence()
        createNotificationChannels()

        // NATIVE HOOKS: Ensuring Snapchat never sees the app as "In Background"
        if ((autoOpenConfig.allowRunningInBackground as PropertyValue<Boolean>).get()) {
            runCatching {
                findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").apply {
                    hook("appStateChanged", HookStage.BEFORE) { param ->
                        val state = param.arg<Any>(0).toString()
                        if (state == "INACTIVE" || state == "BACKGROUND") param.setResult(null)
                    }
                }
                findClass("com.snapchat.client.network_manager.NetworkManager\$CppProxy").apply {
                    hook("onAppForegrounded", HookStage.BEFORE) { param -> param.setResult(null) }
                    hook("onAppBackgrounded", HookStage.BEFORE) { param -> param.setResult(null) }
                }
            }
        }

        setupReceivers()
        startEngineWorker()
        setupDetector()
    }

    private fun startEngineWorker() {
        engineJob = this@AutoOpenSnaps.context.coroutineScope.launch(engineDispatcher) {
            while (engineActive.get()) {
                val item = try { snapChannel.receive() } catch (e: Exception) { break }
                
                while (isPaused.get() && engineActive.get()) {
                    currentStatusText = "Paused"; updateStatusNotification(); delay(500)
                }
                if (!engineActive.get()) break

                updateStatusNotification()
                if (!validateEnvironmentalConstraints()) { 
                    synchronized(queuedSnaps) { queuedSnaps.remove(item) }
                    continue 
                }

                // SPEED OPTIMIZATION: Instant switch (40ms) when stealth is off
                val isSafe = (autoOpenConfig.safeProcessing as PropertyValue<Boolean>).get()
                if (lastConversationId != null && lastConversationId != item.conversationId) {
                    delay(if (isSafe) (autoOpenConfig.delayBetweenConversations as PropertyValue<Int>).get().toLong() else 40L)
                }
                lastConversationId = item.conversationId
                
                processSnapItem(item)
                lastSnapProcessedAt.set(System.currentTimeMillis())
                
                // HIGH SPEED: 10ms floor for 20+ snaps/s
                val baseDelay = if (currentSpeedText == "Throttled") 3000L else (autoOpenConfig.delayBetweenSnaps as PropertyValue<Int>).get().toLong()
                if (isSafe) { 
                    delay(Random.nextLong(baseDelay, baseDelay + 200)) 
                } else {
                    delay(baseDelay.coerceAtMost(10)) 
                }
                
                if (synchronized(queuedSnaps) { queuedSnaps.isEmpty() }) {
                    currentStatusText = "Monitoring..."
                    updateStatusNotification()
                }
            }
        }
    }

    private suspend fun processSnapItem(item: SnapQueueItem) {
        currentStatusText = "Active"; updateStatusNotification()
        var success = false
        val startTime = System.currentTimeMillis()
        for (i in 0 until (autoOpenConfig.retryAttempts as PropertyValue<Int>).get()) {
            if (isPaused.get() || !engineActive.get() || autoOpenConfig.globalState == false) break
            
            if (messaging.conversationManager == null) { 
                runCatching { this@AutoOpenSnaps.context.messagingBridge.triggerSessionStart() }
                delay(1000) 
            }
            
            success = withContext(Dispatchers.IO) { performOpen(item) }
            if (success) {
                // IMPORTANT: Item only removed after successful processing to ensure Stats sync
                synchronized(queuedSnaps) { queuedSnaps.remove(item) }
                sessionProcessed.incrementAndGet(); totalProcessed.incrementAndGet(); recordSpeedTimestamp()
                val duration = System.currentTimeMillis() - startTime
                averageProcessingTime.set((averageProcessingTime.get() * 0.7 + duration * 0.3).toLong())
                triggerLazySave(); break
            }
            delay((autoOpenConfig.retryDelay as PropertyValue<Int>).get().toLong())
        }
        if (!success && !isPaused.get() && engineActive.get()) {
            logError("Engine failed to open Snap: ${item.messageId}")
            synchronized(queuedSnaps) { queuedSnaps.remove(item) }
            currentStatusText = "Failed: ${item.senderName}"; updateStatusNotification()
            openedSnapsIds.remove(item.messageId)
        }
    }

    private suspend fun performOpen(item: SnapQueueItem): Boolean {
        val manager = messaging.conversationManager ?: return false
        return suspendCancellableCoroutine { cont ->
            runCatching {
                manager.updateMessage(item.conversationId, item.messageId, MessageUpdate.READ) { result ->
                    if (result == null || result == "DUPLICATEREQUEST") { cont.resume(true) } 
                    else if (item.serverMessageId != 0L) {
                        manager.updateMessage(item.conversationId, item.serverMessageId, MessageUpdate.READ) { serverResult ->
                            cont.resume(serverResult == null || serverResult == "DUPLICATEREQUEST")
                        }
                    } else { cont.resume(false) }
                }
            }.onFailure { logError("Bridge Error", it); cont.resume(false) }
        }
    }

    private suspend fun validateEnvironmentalConstraints(): Boolean {
        while (engineActive.get()) {
            if (autoOpenConfig.globalState == false || isPaused.get()) return false
            val isWifi = isWifiConnected()
            val isIdle = isDeviceIdle()
            val onlyIdle = (autoOpenConfig.onlyWhenIdle as PropertyValue<Boolean>).get()
            val inSleepWindow = if (onlyIdle) isInsideSleepWindow() else false
            
            val wifiStop = (autoOpenConfig.onlyOnWifi as PropertyValue<Boolean>).get() && !isWifi
            val idleStop = onlyIdle && !isIdle && !inSleepWindow
            
            when {
                wifiStop -> { currentStatusText = "Waiting for WiFi..."; delay(5000) }
                idleStop -> { currentStatusText = "Waiting for Idle..."; delay(5000) }
                else -> { 
                    val thermalActive = (autoOpenConfig.thermalProtection as PropertyValue<Boolean>).get() && isThermalThrottled
                    currentSpeedText = if (inSleepWindow || thermalActive) "Throttled" else "Full Speed"
                    return true 
                }
            }
            updateStatusNotification()
        }
        return false
    }

    private fun setupDetector() {
        this@AutoOpenSnaps.context.event.subscribe(BuildMessageEvent::class, priority = 103) { event ->
            if (autoOpenConfig.globalState == false || !engineActive.get()) return@subscribe
            val message = event.message
            if (message.messageState != MessageState.COMMITTED || message.senderId?.toString() == this@AutoOpenSnaps.context.database.myUserId) return@subscribe
            val conversationId = message.messageDescriptor?.conversationId?.toString() ?: return@subscribe
            val clientMessageId = message.messageDescriptor?.messageId ?: return@subscribe
            val serverMessageId = message.orderKey ?: 0L

            val contentType = message.messageContent?.contentType
            if (contentType != ContentType.SNAP && contentType != ContentType.EXTERNAL_MEDIA) return@subscribe
            if (!canUseRule(conversationId)) return@subscribe
            if (openedSnapsIds.contains(clientMessageId)) return@subscribe
            openedSnapsIds.add(clientMessageId)
            
            val senderId = message.senderId?.toString() ?: "unknown"
            val item = SnapQueueItem(conversationId, clientMessageId, serverMessageId, senderId, getSenderDisplayName(senderId), getConversationType(conversationId, senderId), getSnapContentType(contentType))
            
            synchronized(queuedSnaps) { queuedSnaps.add(item) }
            snapChannel.trySend(item)
            
            acquireWakeLock(); updateStatusNotification(); triggerLazySave()
        }
    }

    private fun triggerLazySave() {
        needsSaving.set(true)
        if (isSaving.compareAndSet(false, true)) {
            this@AutoOpenSnaps.context.coroutineScope.launch(Dispatchers.IO) {
                while (needsSaving.get() && engineActive.get()) {
                    needsSaving.set(false); saveQueueToDisk(); delay(LAZY_SAVE_INTERVAL_MS)
                }
                isSaving.set(false)
            }
        }
    }

    private fun saveQueueToDisk() {
        prefs.edit { putInt(PREF_TOTAL_OPENED, totalProcessed.get()); putLong(PREF_SESSION_START, sessionStartTime.get()) }
    }

    private fun restorePersistence() {
        val savedStartTime = prefs.getLong(PREF_SESSION_START, 0)
        if (System.currentTimeMillis() - savedStartTime > 21600000) return
        totalProcessed.set(prefs.getInt(PREF_TOTAL_OPENED, 0)); sessionStartTime.set(savedStartTime)
    }

    private fun isWifiConnected(): Boolean {
        val cm = this@AutoOpenSnaps.context.androidContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun isDeviceIdle(): Boolean = (this@AutoOpenSnaps.context.androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager).isDeviceIdleMode
    private fun isInsideSleepWindow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour <= 6
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (this@AutoOpenSnaps.context.androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PurrfectSnap:AutoOpen").apply { acquire(8 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() { if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel("auto_open_status", "Auto-Open Status", NotificationManager.IMPORTANCE_LOW).apply { enableVibration(false); setSound(null, null) })
        }
    }

    private fun updateStatusNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastNotificationUpdate) < notificationUpdateDelay) {
            if (pendingNotificationUpdate.compareAndSet(false, true)) {
                this@AutoOpenSnaps.context.coroutineScope.launch { delay(notificationUpdateDelay - (now - lastNotificationUpdate)); updateStatusNotificationInternal() }
            }
            return
        }
        updateStatusNotificationInternal()
    }

    private fun updateStatusNotificationInternal() {
        if (!engineActive.get()) return
        val processed = sessionProcessed.get()
        val total = totalProcessed.get()
        val remaining = synchronized(queuedSnaps) { queuedSnaps.size }
        val isWorking = remaining > 0
        val speed = if (isWorking) getSnapsPerSecond() else 0.0
        
        lastNotificationUpdate = System.currentTimeMillis(); pendingNotificationUpdate.set(false)
        
        val sessionTotal = processed + remaining
        val progressPercent = if (sessionTotal > 0) (processed * 100) / sessionTotal else 0
        val eta = if (isWorking && !isPaused.get()) formatDuration(remaining * averageProcessingTime.get()) else "..."

        val builder = Notification.Builder(this@AutoOpenSnaps.context.androidContext, "auto_open_status")
            .setOngoing(isWorking).setOnlyAlertOnce(true).setGroup(NOTIFICATION_GROUP_KEY)
            
        // ICON LOGIC: Pause, Monitoring (Sync), or Active (Play)
        val iconRes = when {
            isPaused.get() -> android.R.drawable.ic_media_pause
            !isWorking -> android.R.drawable.ic_popup_sync
            else -> android.R.drawable.ic_media_play
        }
        builder.setSmallIcon(iconRes)
        builder.setContentTitle("Auto-Open: $currentStatusText")
        
        val isCompact = (autoOpenConfig.compactNotification as PropertyValue<Boolean>).get()
        if (isWorking) {
            builder.setContentText("Opened: $processed │ Queue: $remaining ($progressPercent%)")
            builder.setSubText("Speed: ${String.format(Locale.US, "%.1f", speed)}/s • Ends in: $eta")
            builder.setProgress(sessionTotal, processed, false)
        } else {
            builder.setContentText("$processed Opened Today │ $total Total")
            builder.setSubText(null)
            builder.setProgress(0, 0, false)
        }

        builder.addAction(Notification.Action.Builder(null, if (isPaused.get()) "Resume" else "Pause", createPendingIntent(ACTION_PAUSE_RESUME)).build())
        builder.addAction(Notification.Action.Builder(null, "Clear", createPendingIntent(ACTION_CLEAR_QUEUE)).build())
        builder.addAction(Notification.Action.Builder(null, "Stop", createPendingIntent(ACTION_STOP_ENGINE)).build())

        if (!isCompact) {
            val recentSnaps = synchronized(queuedSnaps) { queuedSnaps.takeLast(5) }
            val bigTextStyle = Notification.BigTextStyle().setSummaryText("")
            val detailText = buildString {
                append("QUEUE STATISTICS\n")
                append("├─ Opened: $processed snaps\n")
                append("├─ Queue: $remaining snaps • Ends in: $eta\n")
                if ((autoOpenConfig.showLifetimeStats as PropertyValue<Boolean>).get()) {
                    append("├─ Total Opened: $total snaps\n")
                }
                val speedNotion = if (isWorking) currentSpeedText else "Idle"
                val speedValue = "${String.format(Locale.US, "%.1f", speed)}/s"
                append("└─ Speed: $speedNotion ($speedValue)\n")

                if ((autoOpenConfig.showQueuePreview as PropertyValue<Boolean>).get()) {
                    append("\nQUEUE PREVIEW\n")
                    if (isWorking && remaining > 0) {
                        recentSnaps.reversed().forEach { item ->
                            append("• ${item.senderName} │ ${item.conversationType} (${item.contentType})\n")
                        }
                    } else {
                        append("Monitoring snaps in background...")
                    }
                }
            }
            bigTextStyle.bigText(detailText)
            builder.setStyle(bigTextStyle)
        }

        notificationManager.notify(STATUS_NOTIFICATION_ID, builder.build())
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(this@AutoOpenSnaps.context.androidContext.packageName)
        return PendingIntent.getBroadcast(this@AutoOpenSnaps.context.androidContext, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setupReceivers() {
        val actionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PAUSE_RESUME -> { isPaused.set(!isPaused.get()); updateStatusNotification(force = true) }
                    ACTION_CLEAR_QUEUE -> { sessionProcessed.set(0); synchronized(queuedSnaps) { queuedSnaps.clear() }; updateStatusNotification(force = true) }
                    ACTION_STOP_ENGINE -> shutdownFeature()
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val temp = intent.getIntExtra("temperature", 0) / 10f
                        if (temp >= 40f && !isThermalThrottled) { isThermalThrottled = true; lastThermalThrottleAt = System.currentTimeMillis() }
                        else if (isThermalThrottled && temp <= 36f && (System.currentTimeMillis() - lastThermalThrottleAt > 600000)) { isThermalThrottled = false }
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(ACTION_PAUSE_RESUME); addAction(ACTION_CLEAR_QUEUE); addAction(ACTION_STOP_ENGINE); addAction(Intent.ACTION_BATTERY_CHANGED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) this@AutoOpenSnaps.context.androidContext.registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED) 
        else this@AutoOpenSnaps.context.androidContext.registerReceiver(actionReceiver, filter)
    }

    private fun recordSpeedTimestamp() { synchronized(snapTimestamps) { snapTimestamps.addLast(System.currentTimeMillis()); if (snapTimestamps.size > 250) snapTimestamps.removeFirst() } }
    
    private fun shutdownFeature() { 
        engineActive.set(false)
        snapChannel.close()
        engineJob?.cancel()
        releaseWakeLock()
        cancelStatusNotification() 
    }
    
    private fun cancelStatusNotification() = notificationManager.cancel(STATUS_NOTIFICATION_ID)

    fun getInterface(): AutoOpenInterface {
        return object : AutoOpenInterface.Stub() {
            override fun getProcessedCount(): Int = totalProcessed.get()
            override fun getQueueItems(): List<String> = synchronized(queuedSnaps) { queuedSnaps.map { gson.toJson(it) } }
            override fun reset() { sessionProcessed.set(0); synchronized(queuedSnaps) { queuedSnaps.clear() }; updateStatusNotification(force = true) }
        }
    }

    private fun getSenderDisplayName(userId: String): String = this@AutoOpenSnaps.context.database.getFriendInfo(userId)?.displayName ?: "Unknown"
    private fun getConversationType(convId: String, senderId: String): String = if (this@AutoOpenSnaps.context.database.getDMOtherParticipant(convId) != null) "Friend DM" else this@AutoOpenSnaps.context.database.getFeedEntryByConversationId(convId)?.feedDisplayName ?: "Group Chat"
    private fun getSnapContentType(type: ContentType?): String = when (type) { ContentType.SNAP -> "Photo/Video"; ContentType.EXTERNAL_MEDIA -> "Media"; else -> "Message" }
}

data class SnapQueueItem(val conversationId: String, val messageId: Long, val serverMessageId: Long, val senderId: String, val senderName: String, val conversationType: String, val contentType: String, val timestamp: Long = System.currentTimeMillis())
