package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.media.MediaRecorder
import android.os.HandlerThread
import android.os.Process
import android.util.Base64
import android.view.View
import android.widget.OverScroller
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.io.File
import java.lang.Thread
import java.lang.reflect.Method
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ThreadPoolExecutor
import com.google.gson.reflect.TypeToken
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.findRestrictedMethod
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import okhttp3.Dispatcher

class PerformanceMode : Feature("Performance Mode") {
    companion object {
        private const val CHAT_FEED_CACHE_MAX_ROWS = 400
        private const val CHAT_FEED_CACHE_MAX_BLOB_BYTES = 512
        private const val CHAT_FEED_CACHE_MIN_REFRESH_INTERVAL_MS = 15_000L
        private const val CHAT_FEED_CACHE_MAX_AGE_MS = 5L * 60L * 1000L
        private const val CHAT_FEED_CACHE_SCHEMA_VERSION = 2
        private const val MESSAGE_WINDOW_STATE_MAX_CONVERSATIONS = 64
        private const val MESSAGE_WINDOW_STATE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
        private const val SNAP_PREFETCH_GROUP_MESSAGES = 48
        private const val SNAP_PREFETCH_DM_MESSAGES = 24
        private const val REOPEN_WARMUP_GROUP_MESSAGES = 160
        private const val REOPEN_WARMUP_DM_MESSAGES = 96
    }

    private data class SnapshotCell(
        val type: Int,
        val stringValue: String? = null,
        val longValue: Long? = null,
        val doubleValue: Double? = null,
        val blobValue: String? = null,
    )

    private data class CursorSnapshot(
        val columns: List<String>,
        val rows: List<List<SnapshotCell>>,
    )

    private data class ChatFeedSnapshotCache(
        val schemaVersion: Int,
        val queryKey: String,
        val createdAt: Long,
        val snapshot: CursorSnapshot,
    )

    private data class MessageWindowState(
        val conversationId: String,
        val currentSize: Int,
        val oldestOrderKey: Long?,
        val newestOrderKey: Long?,
        val updatedAt: Long,
        val isGroup: Boolean,
    )

    override fun init() {
        val profile = context.config.global.performanceMode.profile.getNullable() ?: return
        val isMaxProfile = profile == "max"
        val threadPriority = if (isMaxProfile) {
            Process.THREAD_PRIORITY_DISPLAY
        } else {
            Process.THREAD_PRIORITY_MORE_FAVORABLE
        }
        val minimumFrameRate = if (isMaxProfile) 60 else 45
        val minimumRecordingFrameRate = if (isMaxProfile) 30 else 24
        val durationScale = if (isMaxProfile) 0.35f else 0.55f
        val recyclerViewCacheSize = if (isMaxProfile) 64 else 32
        val maxRequests = if (isMaxProfile) 192 else 96
        val maxRequestsPerHost = if (isMaxProfile) 32 else 16
        val minimumCoreThreads = if (isMaxProfile) 16 else 8
        val prefetchItemCount = if (isMaxProfile) 24 else 12
        val maxAnimationDurationMs = if (isMaxProfile) 90L else 140L
        val maxScrollDurationMs = if (isMaxProfile) 72 else 180
        val preferredRefreshRate = if (isMaxProfile) 120f else 90f
        val snapMapTransitionDurationMs = if (isMaxProfile) 0L else 24L
        val snapMapCameraDurationMs = if (isMaxProfile) 16L else 64L
        val snapMapMoveDurationMs = if (isMaxProfile) 8L else 40L
        val snapMapPrefetchZoomDelta = if (isMaxProfile) 6 else 3
        val preferredJavaThreadPriority = if (isMaxProfile) Thread.NORM_PRIORITY + 2 else Thread.NORM_PRIORITY + 1

        context.log.info(
            "Performance mode enabled: profile=$profile, threadPriority=$threadPriority, minFps=$minimumFrameRate, minRecordingFps=$minimumRecordingFrameRate, durationScale=$durationScale, rvCache=$recyclerViewCacheSize, maxRequests=$maxRequests/$maxRequestsPerHost, minCoreThreads=$minimumCoreThreads, prefetch=$prefetchItemCount, maxAnimMs=$maxAnimationDurationMs, maxScrollMs=$maxScrollDurationMs, preferredRefreshRate=$preferredRefreshRate, snapMapTransitionMs=$snapMapTransitionDurationMs, snapMapCameraMs=$snapMapCameraDurationMs, snapMapMoveMs=$snapMapMoveDurationMs, snapMapPrefetchZoomDelta=$snapMapPrefetchZoomDelta, javaThreadPriority=$preferredJavaThreadPriority",
            "PerformanceMode"
        )

        runCatching {
            ValueAnimator.setFrameDelay(0L)
            context.log.info("Applied ValueAnimator frame delay override: 0ms", "PerformanceMode")
        }

        fun firstHitLogger(name: String): (String) -> Unit {
            val didLog = AtomicBoolean(false)
            return { details ->
                if (didLog.compareAndSet(false, true)) {
                    context.log.info("First hit: $name | $details", "PerformanceMode")
                }
            }
        }

        val handlerThreadConstructorLog = firstHitLogger("HandlerThread.constructor")
        val handlerThreadStartLog = firstHitLogger("HandlerThread.start")
        val threadStartLog = firstHitLogger("Thread.start")
        val executorLog = firstHitLogger("ThreadPoolExecutor.constructor")
        val dispatcherLog = firstHitLogger("OkHttp.Dispatcher.constructor")
        val animatorLog = firstHitLogger("ValueAnimator.getDurationScale")
        val recyclerCtorLog = firstHitLogger("RecyclerView.constructor")
        val recyclerAdapterLog = firstHitLogger("RecyclerView.setAdapter")
        val recyclerLayoutManagerLog = firstHitLogger("RecyclerView.setLayoutManager")
        val sqliteOpenLog = firstHitLogger("SQLiteDatabase.openDatabase")
        val sqliteCreateLog = firstHitLogger("SQLiteDatabase.openOrCreateDatabase")
        val mediaRecorderLog = firstHitLogger("MediaRecorder.setVideoFrameRate")
        val overScrollerLog = firstHitLogger("OverScroller.startScroll")
        val mapDialogLog = firstHitLogger("Dialog.show")
        val mapViewLog = firstHitLogger("MapView.constructor")
        val mapboxNetworkBlockLog = firstHitLogger("SnapMap.telemetryBlock")
        val mapCameraAnimLog = firstHitLogger("SnapMap.mapAnimatorDuration")
        val mapThreadLog = firstHitLogger("SnapMap.mapThread")
        val mapRendererFpsLog = firstHitLogger("SnapMap.mapRendererFps")
        val mapTransitionLog = firstHitLogger("SnapMap.transitionOptions")
        val mapMoveLog = firstHitLogger("SnapMap.moveDuration")
        fun isPerformanceSensitiveThread(name: String?): Boolean {
            val normalizedName = name?.lowercase() ?: return false
            return listOf("codec", "transcod", "feed", "story", "opera", "messag", "network", "db", "disk", "map", "mapbox", "snapmap", "viewport").any {
                normalizedName.contains(it)
            }
        }

        fun clampPositiveDuration(durationMs: Long, maxDurationMs: Long): Long {
            if (durationMs <= 0L) return durationMs
            return durationMs.coerceAtMost(maxDurationMs)
        }

        val performanceCacheDir = File(context.androidContext.filesDir, "performance_mode_cache").apply { mkdirs() }
        val chatFeedSnapshotFile = File(performanceCacheDir, "chat_feed_snapshot.json")
        val lastChatFeedSnapshotWrite = AtomicLong(0L)
        val chatFeedSnapshotServedThisProcess = AtomicBoolean(false)

        fun invalidateChatFeedSnapshot(reason: String) {
            val deleted = runCatching {
                if (!chatFeedSnapshotFile.exists()) return@runCatching false
                chatFeedSnapshotFile.delete()
            }.getOrDefault(false)
            chatFeedSnapshotServedThisProcess.set(false)
            if (deleted) {
                context.log.info("Invalidated chat feed snapshot ($reason)", "PerformanceMode")
            }
        }

        Activity::class.java.hook("onResume", HookStage.AFTER) {
            if (!isMaxProfile) return@hook
            chatFeedSnapshotServedThisProcess.set(false)
        }

        val windowStatePrefs = context.androidContext.getSharedPreferences("purrfectsnap_perf_message_windows", Context.MODE_PRIVATE)
        val messageWindowStates = runCatching {
            val raw = windowStatePrefs.getString("states", null).orEmpty()
            if (raw.isBlank()) {
                LinkedHashMap<String, MessageWindowState>()
            } else {
                context.gson.fromJson<LinkedHashMap<String, MessageWindowState>>(
                    raw,
                    object : TypeToken<LinkedHashMap<String, MessageWindowState>>() {}.type
                ) ?: LinkedHashMap()
            }
        }.getOrElse { LinkedHashMap() }

        fun persistMessageWindowStates() {
            runCatching {
                windowStatePrefs.edit().putString("states", context.gson.toJson(messageWindowStates)).apply()
            }.onFailure {
                context.log.error("Failed to persist message window states", it, "PerformanceMode")
            }
        }

        val snapshotQueryWhitespaceRegex = Regex("\\s+")
        fun buildChatFeedSnapshotQueryKey(sql: String): String {
            return sql.lowercase()
                .replace(snapshotQueryWhitespaceRegex, " ")
                .trim()
        }

        fun isChatFeedQuery(sql: String): Boolean {
            val normalized = buildChatFeedSnapshotQueryKey(sql)
            if (!normalized.startsWith("select ")) return false

            val isFriendsFeedViewQuery =
                normalized.startsWith("select * from friendsfeedview ") &&
                    normalized.contains(" order by _id ") &&
                    normalized.contains(" limit ")

            val isFeedEntryQuery =
                normalized.startsWith("select * from feed_entry ") &&
                    normalized.contains(" order by last_updated_timestamp desc ") &&
                    normalized.contains(" limit ")

            return (isFriendsFeedViewQuery || isFeedEntryQuery) &&
                !normalized.contains("count(") &&
                !normalized.contains("select 0") &&
                !normalized.contains("where key = ?") &&
                !normalized.contains("where client_conversation_id = ?")
        }

        fun cursorCell(cursor: Cursor, index: Int): SnapshotCell {
            return when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> SnapshotCell(Cursor.FIELD_TYPE_NULL)
                Cursor.FIELD_TYPE_INTEGER -> SnapshotCell(Cursor.FIELD_TYPE_INTEGER, longValue = cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> SnapshotCell(Cursor.FIELD_TYPE_FLOAT, doubleValue = cursor.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> SnapshotCell(Cursor.FIELD_TYPE_STRING, stringValue = cursor.getString(index))
                Cursor.FIELD_TYPE_BLOB -> SnapshotCell(
                    Cursor.FIELD_TYPE_BLOB,
                    blobValue = cursor.getBlob(index)
                        ?.takeIf { it.size <= CHAT_FEED_CACHE_MAX_BLOB_BYTES }
                        ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                )
                else -> SnapshotCell(Cursor.FIELD_TYPE_STRING, stringValue = cursor.getString(index))
            }
        }

        fun snapshotFromCursor(cursor: Cursor): CursorSnapshot? {
            val originalPosition = cursor.position
            val snapshot = runCatching {
                val columns = cursor.columnNames.toList()
                val rows = mutableListOf<List<SnapshotCell>>()
                if (cursor.moveToFirst()) {
                    var rowCount = 0
                    do {
                        rows += columns.indices.map { index -> cursorCell(cursor, index) }
                        rowCount++
                    } while (rowCount < CHAT_FEED_CACHE_MAX_ROWS && cursor.moveToNext())
                }
                CursorSnapshot(columns, rows)
            }.onFailure {
                context.log.error("Failed to snapshot chat feed cursor", it, "PerformanceMode")
            }.getOrNull()

            runCatching { cursor.moveToPosition(originalPosition) }
            val restoredPosition = runCatching { cursor.position }.getOrNull()
            if (restoredPosition != originalPosition) {
                context.log.warn(
                    "Skipping chat feed snapshot write due non-restorable cursor position (from=$originalPosition to=${restoredPosition ?: "unknown"})",
                    "PerformanceMode"
                )
                return null
            }
            return snapshot
        }

        fun snapshotToMatrixCursor(snapshot: CursorSnapshot): MatrixCursor {
            return MatrixCursor(snapshot.columns.toTypedArray(), snapshot.rows.size).also { matrixCursor ->
                snapshot.rows.forEach { row ->
                    matrixCursor.addRow(row.map { cell ->
                        when (cell.type) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cell.longValue
                            Cursor.FIELD_TYPE_FLOAT -> cell.doubleValue
                            Cursor.FIELD_TYPE_BLOB -> cell.blobValue?.let { Base64.decode(it, Base64.NO_WRAP) }
                            else -> cell.stringValue
                        }
                    })
                }
            }
        }

        fun readSnapshot(file: File, expectedQueryKey: String): CursorSnapshot? {
            return runCatching {
                if (!file.exists()) return null
                val cache = context.gson.fromJson(file.readText(Charsets.UTF_8), ChatFeedSnapshotCache::class.java) ?: return null
                if (cache.schemaVersion != CHAT_FEED_CACHE_SCHEMA_VERSION) {
                    runCatching { file.delete() }
                    return null
                }
                if (cache.queryKey != expectedQueryKey) {
                    runCatching { file.delete() }
                    return null
                }
                if (System.currentTimeMillis() - cache.createdAt > CHAT_FEED_CACHE_MAX_AGE_MS) {
                    runCatching { file.delete() }
                    return null
                }
                cache.snapshot
            }.getOrElse {
                runCatching { file.delete() }
                null
            }
        }

        fun writeSnapshot(file: File, queryKey: String, snapshot: CursorSnapshot) {
            runCatching {
                file.writeText(
                    context.gson.toJson(
                        ChatFeedSnapshotCache(
                            schemaVersion = CHAT_FEED_CACHE_SCHEMA_VERSION,
                            queryKey = queryKey,
                            createdAt = System.currentTimeMillis(),
                            snapshot = snapshot,
                        )
                    ),
                    Charsets.UTF_8
                )
            }.onFailure {
                context.log.error("Failed to persist friend list snapshot", it, "PerformanceMode")
            }
        }

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (!isMaxProfile) return@subscribe
            val url = event.url
            if (url.contains("ami/friends")) {
                invalidateChatFeedSnapshot("friends-mutation-sync")
            }
            if (url.contains("mapbox") && (url.contains("events.") || url.contains("telemetry"))) {
                event.canceled = true
                mapboxNetworkBlockLog("url=$url")
            }
        }

        HandlerThread::class.java.hookConstructor(HookStage.BEFORE) { param ->
            if (param.args().size < 2) return@hookConstructor
            val threadName = param.argNullable<String>(0)
            if (!isPerformanceSensitiveThread(threadName)) return@hookConstructor
            param.setArg(1, threadPriority)
            handlerThreadConstructorLog("name=$threadName priority=$threadPriority")
        }

        HandlerThread::class.java.hook("start", HookStage.AFTER) { param ->
            val thread = param.nullableThisObject<Any>() as? HandlerThread ?: return@hook
            if (!isPerformanceSensitiveThread(thread.name)) return@hook
            runCatching {
                val tid = thread.threadId
                if (tid > 0) {
                    Process.setThreadPriority(tid, threadPriority)
                }
            }
            handlerThreadStartLog("name=${thread.name} tid=${thread.threadId} priority=$threadPriority")
        }

        Thread::class.java.hook("start", HookStage.AFTER) { param ->
            val thread = param.thisObject<Thread>()
            if (!isPerformanceSensitiveThread(thread.name)) return@hook
            runCatching {
                thread.priority = preferredJavaThreadPriority
            }
            threadStartLog("name=${thread.name} priority=${thread.priority}")
            if ((thread.name ?: "").contains("map", ignoreCase = true) || (thread.name ?: "").contains("mapbox", ignoreCase = true)) {
                mapThreadLog("name=${thread.name} priority=${thread.priority}")
            }
        }

        ThreadPoolExecutor::class.java.hookConstructor(HookStage.AFTER) { param ->
            val executor = param.thisObject<ThreadPoolExecutor>()
            runCatching {
                val targetCorePoolSize = executor.maximumPoolSize.coerceAtLeast(1).coerceAtMost(minimumCoreThreads.coerceAtLeast(executor.corePoolSize))
                if (executor.corePoolSize < targetCorePoolSize) {
                    executor.corePoolSize = targetCorePoolSize
                }
                executor.allowCoreThreadTimeOut(false)
                executor.prestartAllCoreThreads()
                executorLog("core=${executor.corePoolSize} max=${executor.maximumPoolSize} active=${executor.activeCount}")
            }
        }

        Dispatcher::class.java.hookConstructor(HookStage.AFTER) { param ->
            val dispatcher = param.thisObject<Dispatcher>()
            runCatching {
                dispatcher.maxRequests = maxRequests
                dispatcher.maxRequestsPerHost = maxRequestsPerHost
                dispatcherLog("maxRequests=${dispatcher.maxRequests} maxRequestsPerHost=${dispatcher.maxRequestsPerHost}")
            }
        }

        ValueAnimator::class.java.hook("getDurationScale", HookStage.AFTER) { param ->
            param.setResult(durationScale)
            animatorLog("durationScale=$durationScale")
        }

        RecyclerView::class.java.hookConstructor(HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            recyclerView.setItemViewCacheSize(recyclerViewCacheSize)
            recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
            if (isMaxProfile) {
                recyclerView.itemAnimator = null
            }
            recyclerCtorLog("cache=$recyclerViewCacheSize max=$isMaxProfile class=${recyclerView::class.java.name}")
        }

        RecyclerView::class.java.hook("setAdapter", HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            recyclerView.setItemViewCacheSize(recyclerViewCacheSize)
            if (isMaxProfile) {
                recyclerView.itemAnimator = null
            }
            recyclerAdapterLog("cache=$recyclerViewCacheSize adapter=${param.argNullable<Any>(0)?.javaClass?.name}")
        }

        RecyclerView::class.java.hook("setLayoutManager", HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            val layoutManager = param.argNullable<Any>(0)
            when (layoutManager) {
                is LinearLayoutManager -> {
                    layoutManager.isItemPrefetchEnabled = true
                    layoutManager.initialPrefetchItemCount = prefetchItemCount
                }
                is StaggeredGridLayoutManager -> {
                    layoutManager.isItemPrefetchEnabled = true
                    layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            }
            recyclerLayoutManagerLog("layoutManager=${layoutManager?.javaClass?.name} prefetch=$prefetchItemCount")
        }

        fun SQLiteDatabase.applyPerformancePragmas() {
            runCatching { execSQL("PRAGMA synchronous = NORMAL") }
            runCatching { execSQL("PRAGMA temp_store = MEMORY") }
            runCatching { execSQL("PRAGMA cache_size = -32768") }
            runCatching { execSQL("PRAGMA mmap_size = 268435456") }
            runCatching { execSQL("PRAGMA journal_size_limit = 1048576") }
            runCatching { execSQL("PRAGMA optimize") }
        }

        SQLiteDatabase::class.java.hook("openDatabase", HookStage.AFTER) { param ->
            (param.getResult() as? SQLiteDatabase)?.also {
                it.applyPerformancePragmas()
                sqliteOpenLog("path=${param.argNullable<Any>(0)}")
            }
        }

        SQLiteDatabase::class.java.hook("openOrCreateDatabase", HookStage.AFTER) { param ->
            (param.getResult() as? SQLiteDatabase)?.also {
                it.applyPerformancePragmas()
                sqliteCreateLog("path=${param.argNullable<Any>(0)}")
            }
        }

        MediaRecorder::class.java.hook("setVideoFrameRate", HookStage.BEFORE) { param ->
            val currentRate = param.arg<Int>(0)
            val applied = currentRate
                .coerceAtLeast(minimumRecordingFrameRate)
                .coerceAtMost(if (isMaxProfile) 60 else 45)
            if (applied != currentRate) {
                param.setArg(0, applied)
            }
            mediaRecorderLog("requested=$currentRate applied=${param.arg<Int>(0)}")
        }

        OverScroller::class.java.hook("startScroll", HookStage.BEFORE) { param ->
            if (param.args().size >= 5) {
                val original = param.arg<Int>(4)
                val updated = original.coerceAtMost(maxScrollDurationMs)
                if (updated != original) {
                    param.setArg(4, updated)
                }
                overScrollerLog("requested=$original applied=${param.arg<Int>(4)}")
            }
        }

        OverScroller::class.java.hook("fling", HookStage.BEFORE) { param ->
            if (param.args().size >= 10) {
                val overX = param.arg<Int>(8)
                val overY = param.arg<Int>(9)
                if (overX != 0) param.setArg(8, 0)
                if (overY != 0) param.setArg(9, 0)
            }
        }

        fun applyActivityPerformanceTuning(activity: Activity) {
            runCatching {
                activity.window.setWindowAnimations(0)
            }
        }

        onNextActivityCreate {
            applyActivityPerformanceTuning(it)
        }

        Dialog::class.java.hook("show", HookStage.AFTER) { param ->
            val dialog = param.nullableThisObject<Any>() as? Dialog ?: return@hook
            val window = dialog.window ?: return@hook
            runCatching {
                window.setWindowAnimations(0)
                if (dialog::class.java.name.contains("map", ignoreCase = true) || dialog::class.java.name.contains("snap", ignoreCase = true)) {
                    mapDialogLog("class=${dialog::class.java.name}")
                }
            }
        }

        runCatching {
            findClass("com.mapbox.mapboxsdk.maps.MapView").hookConstructor(HookStage.AFTER) { param ->
                val mapView = param.nullableThisObject<Any>() as? View ?: return@hookConstructor
                mapView.overScrollMode = View.OVER_SCROLL_NEVER
                mapViewLog("class=${mapView::class.java.name}")
            }
        }

        runCatching {
            findClass("com.mapbox.mapboxsdk.maps.renderer.MapRenderer").hook("setMaximumFps", HookStage.BEFORE) { param ->
                val requested = param.arg<Int>(0)
                val applied = requested.coerceAtLeast(120)
                if (applied != requested) {
                    param.setArg(0, applied)
                }
                mapRendererFpsLog("requested=$requested applied=${param.arg<Int>(0)}")
            }
        }

        runCatching {
            val nativeMapViewClass = findClass("com.mapbox.mapboxsdk.maps.NativeMapView")
            val transitionOptionsClass = findClass("com.mapbox.mapboxsdk.style.layers.TransitionOptions")
            val transitionOptionsCtor = transitionOptionsClass.getDeclaredConstructor(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply {
                isAccessible = true
            }

            fun findNativeMapMethod(name: String, predicate: (Method) -> Boolean): Method? {
                return nativeMapViewClass.findRestrictedMethod { method ->
                    method.name == name && predicate(method)
                }?.apply {
                    isAccessible = true
                }
            }

            val nativeCancelTransitions = findNativeMapMethod("nativeCancelTransitions") { it.parameterCount == 0 }
            val nativeSetPrefetchTiles = findNativeMapMethod("nativeSetPrefetchTiles") { it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
            val nativeSetPrefetchZoomDelta = findNativeMapMethod("nativeSetPrefetchZoomDelta") { it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }
            val nativeSetTransitionDelay = findNativeMapMethod("nativeSetTransitionDelay") { it.parameterCount == 1 && it.parameterTypes[0] == Long::class.javaPrimitiveType }
            val nativeSetTransitionDuration = findNativeMapMethod("nativeSetTransitionDuration") { it.parameterCount == 1 && it.parameterTypes[0] == Long::class.javaPrimitiveType }
            val nativeSetTransitionOptions = findNativeMapMethod("nativeSetTransitionOptions") { it.parameterCount == 1 && it.parameterTypes[0].name == transitionOptionsClass.name }

            nativeMapViewClass.hookConstructor(HookStage.AFTER) { param ->
                val nativeMapView = param.thisObject<Any>()
                runCatching {
                    nativeSetPrefetchTiles?.invoke(nativeMapView, true)
                    nativeSetPrefetchZoomDelta?.invoke(nativeMapView, snapMapPrefetchZoomDelta)
                    nativeSetTransitionDelay?.invoke(nativeMapView, 0L)
                    nativeSetTransitionDuration?.invoke(nativeMapView, snapMapTransitionDurationMs)
                    nativeSetTransitionOptions?.invoke(
                        nativeMapView,
                        transitionOptionsCtor.newInstance(snapMapTransitionDurationMs, 0L, false)
                    )
                    nativeCancelTransitions?.invoke(nativeMapView)
                    mapTransitionLog("transitionMs=$snapMapTransitionDurationMs prefetchZoomDelta=$snapMapPrefetchZoomDelta placementTransitions=false")
                }
            }

            nativeMapViewClass.findRestrictedMethod { method ->
                method.name == "g" &&
                    method.parameterCount == 6 &&
                    method.parameterTypes.last() == Long::class.javaPrimitiveType
            }?.hook(HookStage.BEFORE) { param ->
                val original = param.arg<Long>(5)
                val applied = clampPositiveDuration(original, snapMapCameraDurationMs)
                if (applied != original) {
                    param.setArg(5, applied)
                }
                runCatching { nativeCancelTransitions?.invoke(param.thisObject<Any>()) }
                mapCameraAnimLog("requested=$original applied=${param.arg<Long>(5)}")
            }

            nativeMapViewClass.findRestrictedMethod { method ->
                method.name == "v" &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == Double::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Double::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Long::class.javaPrimitiveType
            }?.hook(HookStage.BEFORE) { param ->
                val original = param.arg<Long>(2)
                val applied = clampPositiveDuration(original, snapMapMoveDurationMs)
                if (applied != original) {
                    param.setArg(2, applied)
                }
                runCatching { nativeCancelTransitions?.invoke(param.thisObject<Any>()) }
                mapMoveLog("requested=$original applied=${param.arg<Long>(2)}")
            }
        }.onFailure {
            context.log.error("Failed to install Snap Map transition hooks", it, "PerformanceMode")
        }

        runCatching {
            findClass("com.snapchat.client.messaging.MessageWindowManager\$CppProxy").hook("initWindow", HookStage.BEFORE) { param ->
                if (!isMaxProfile) return@hook

                val conversationId = runCatching {
                    SnapUUID(param.arg(0)).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@hook

                val initParams = param.arg<Any>(1)
                val conversationType = context.database.getConversationType(conversationId) ?: return@hook
                val isGroup = conversationType == 1
                val savedState = synchronized(messageWindowStates) {
                    messageWindowStates[conversationId]
                        ?.takeIf { System.currentTimeMillis() - it.updatedAt <= MESSAGE_WINDOW_STATE_MAX_AGE_MS }
                }

                val enumConstants = initParams.getObjectField("mStartingType")?.javaClass?.enumConstants ?: return@hook
                if (savedState != null) {
                    val restoredMaxSize = if (savedState.isGroup) {
                        savedState.currentSize.coerceAtLeast(220).coerceAtMost(520)
                    } else {
                        savedState.currentSize.coerceAtLeast(140).coerceAtMost(320)
                    }
                    val restoredForward = (savedState.currentSize + if (savedState.isGroup) 24 else 16).coerceAtMost(restoredMaxSize)
                    val restoredBack = if (savedState.isGroup) 180 else 120
                    initParams.setObjectField("mStartingType", enumConstants.firstOrNull { it.toString() == "MESSAGE" } ?: return@hook)
                    initParams.setObjectField("mStartingOrderKey", savedState.oldestOrderKey ?: savedState.newestOrderKey)
                    initParams.setObjectField("mMaxSize", restoredMaxSize)
                    initParams.setObjectField("mNumMessagesForward", restoredForward)
                    initParams.setObjectField("mNumMessagesBack", restoredBack)

                    val warmupAmount = if (savedState.isGroup) REOPEN_WARMUP_GROUP_MESSAGES else REOPEN_WARMUP_DM_MESSAGES
                    val oldestKey = savedState.oldestOrderKey
                    if (oldestKey != null) {
                        context.feature(Messaging::class).conversationManager?.fetchConversationWithMessagesPaginated(
                            conversationId = conversationId,
                            lastMessageId = oldestKey,
                            amount = warmupAmount,
                            onSuccess = {},
                            onError = {}
                        )
                    }
                }
            }
        }.onFailure {
            context.log.error("Failed to install saved message window restore hooks", it, "PerformanceMode")
        }

        context.mappings.useMapper(CallbackMapper::class) {
            callbacks.getClass("MessageWindowManagerDelegate")?.hook("onWindowUpdated", HookStage.AFTER) { param ->
                if (!isMaxProfile) return@hook

                val conversationId = runCatching { SnapUUID(param.arg(0)).toString() }.getOrNull() ?: return@hook
                val update = param.arg<Any>(2)
                val pagination = update.getObjectField("mPagination") ?: return@hook
                val currentSize = pagination.getObjectField("mCurrentSize") as? Int ?: return@hook
                val oldestOrderKey = pagination.getObjectField("mOldestOrderKey") as? Long
                val newestOrderKey = pagination.getObjectField("mNewestOrderKey") as? Long
                val conversationType = context.database.getConversationType(conversationId) ?: 0
                val isGroup = conversationType == 1

                synchronized(messageWindowStates) {
                    messageWindowStates[conversationId] = MessageWindowState(
                        conversationId = conversationId,
                        currentSize = currentSize.coerceAtMost(if (isGroup) 420 else 260),
                        oldestOrderKey = oldestOrderKey,
                        newestOrderKey = newestOrderKey,
                        updatedAt = System.currentTimeMillis(),
                        isGroup = isGroup
                    )
                    while (messageWindowStates.size > MESSAGE_WINDOW_STATE_MAX_CONVERSATIONS) {
                        val eldestKey = messageWindowStates.entries.minByOrNull { it.value.updatedAt }?.key ?: break
                        messageWindowStates.remove(eldestKey)
                    }
                    persistMessageWindowStates()
                }
            }
        }

        runCatching {
            findClass("io.requery.android.database.sqlite.SQLiteDatabase").hook("rawQueryWithFactory", HookStage.BEFORE) { param ->
                if (!isMaxProfile) return@hook
                val sql = param.argNullable<String>(1) ?: return@hook
                if (!isChatFeedQuery(sql)) return@hook
                if (chatFeedSnapshotServedThisProcess.get()) return@hook
                val queryKey = buildChatFeedSnapshotQueryKey(sql)
                readSnapshot(chatFeedSnapshotFile, queryKey)?.let { snapshot ->
                    param.setResult(snapshotToMatrixCursor(snapshot))
                    chatFeedSnapshotServedThisProcess.set(true)
                }
            }

            findClass("io.requery.android.database.sqlite.SQLiteDatabase").hook("rawQueryWithFactory", HookStage.AFTER) { param ->
                if (!isMaxProfile) return@hook
                val sql = param.argNullable<String>(1) ?: return@hook
                if (!isChatFeedQuery(sql)) return@hook
                val cursor = param.getResult() as? Cursor ?: return@hook
                val now = System.currentTimeMillis()
                if (now - lastChatFeedSnapshotWrite.get() < CHAT_FEED_CACHE_MIN_REFRESH_INTERVAL_MS) return@hook
                val queryKey = buildChatFeedSnapshotQueryKey(sql)
                val snapshot = snapshotFromCursor(cursor) ?: return@hook
                if (snapshot.rows.isEmpty()) return@hook
                writeSnapshot(chatFeedSnapshotFile, queryKey, snapshot)
                lastChatFeedSnapshotWrite.set(now)
            }
        }.onFailure {
            context.log.error("Failed to install chat feed cache hooks", it, "PerformanceMode")
        }

    }
}
