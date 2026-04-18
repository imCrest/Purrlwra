package cock.crest.purrfectsnap.lite.core.features.impl.downloader

import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.core.ui.InAppOverlay
import cock.crest.purrfectsnap.lite.bridge.call.CallDownloadSession
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

class CallRecorder : Feature("Call Recorder") {
    private var wasInCall = false
    private var callDownloadSession: CallDownloadSession? = null
    private val streams = ConcurrentHashMap<Int, CallStreamWrapper>()
    private val activeRemoteStreams = ConcurrentHashMap.newKeySet<Int>()
    private var fallbackMicRecord: AudioRecord? = null
    private var fallbackMicJob: Job? = null
    private var fallbackMicStartupJob: Job? = null
    private var pendingCallEndJob: Job? = null
    private var lastRemoteActivityTimestamp = 0L
    private var selfSideStreamOpened = false

    private val uiState get() = context.inAppOverlay.callRecorderState
    private val callRecorderConfig get() = context.config.downloader.callRecorder

    inner class CallStreamWrapper(
        private val audioFormat: AudioFormat,
        private val sourceLabel: String = "unknown",
        private val onStreamOpened: (() -> Unit)? = null,
        private val startTimestamp: Long = System.currentTimeMillis(),
    ) {
        private var stream: OutputStream? = null

        fun write(buffer: ByteArray) {
            if (!uiState.isRecording || callDownloadSession == null) return
            
            if (stream == null) {
                runCatching {
                    stream = ParcelFileDescriptor.AutoCloseOutputStream(
                        callDownloadSession?.createStream(
                            System.currentTimeMillis(),
                            audioFormat.channelCount,
                            audioFormat.sampleRate,
                            audioFormat.encoding
                        ) ?: return
                    )
                    context.log.verbose(
                        "Opened call stream source=$sourceLabel sampleRate=${audioFormat.sampleRate} channels=${audioFormat.channelCount} encoding=${audioFormat.encoding}",
                        "CallRecorder"
                    )
                    onStreamOpened?.invoke()
                }
            }
            runCatching { stream?.write(buffer) }
        }

        fun close() {
            runCatching { stream?.close() }
            stream = null
        }
    }

    private fun finalizeSession() {
        val session = callDownloadSession ?: return
        context.log.verbose("Finalizing call recording session")
        pendingCallEndJob?.cancel()
        pendingCallEndJob = null
        stopFallbackMicCapture("finalizeSession")
        runCatching { session.end() }
        callDownloadSession = null
        streams.values.forEach { it.close() }
    }

    private fun startManualRecording() {
        if (!uiState.isRecording) {
            uiState.isRecording = true
            uiState.recordingStartTime = System.currentTimeMillis()
            
            // Initialize call download session if not already started
            if (callDownloadSession == null) {
                context.log.verbose("Starting call recorder session: ${uiState.currentAuthor}")
                callDownloadSession = context.bridgeClient.startCallDownload(System.currentTimeMillis(), uiState.currentAuthor)
            }
            
            ensureSessionStarted()
            scheduleFallbackMicCapture()
        }
    }
    
    private fun stopRecording() {
        if (uiState.isRecording) {
            uiState.isRecording = false
            stopFallbackMicCapture("stopRecording")
            finalizeSession()
        }
    }

    private fun onCallStarted(conversationId: String) {
        if (wasInCall) return
        wasInCall = true
        activeRemoteStreams.clear()
        lastRemoteActivityTimestamp = 0L
        pendingCallEndJob?.cancel()
        pendingCallEndJob = null
        selfSideStreamOpened = false
        
        val author = (if (context.database.getConversationType(conversationId) == 1) {
            context.database.getFeedEntryByConversationId(conversationId)?.feedDisplayName
        } else {
            context.database.getDMOtherParticipant(conversationId)?.let { context.database.getFriendInfo(it)?.mutableUsername }
        }) ?: "unknown"
        
        context.log.verbose("Call started for: $author")
        
        uiState.currentAuthor = author
        
        if (callRecorderConfig.callRecorderUi.get()) {
            uiState.offsetX = 0f
            uiState.offsetY = 0f
            uiState.isMinimized = false
            uiState.lastInteractionTime = System.currentTimeMillis()
            uiState.showOverlay = true
        }

        if (callRecorderConfig.autoStartRecording.get()) {
            startManualRecording()
        }
    }

    private fun onCallEnded() {
        context.log.verbose("onCallEnded cleanup. wasInCall=$wasInCall, showOverlay=${uiState.showOverlay}")
        wasInCall = false
        activeRemoteStreams.clear()
        lastRemoteActivityTimestamp = 0L
        pendingCallEndJob?.cancel()
        pendingCallEndJob = null
        stopFallbackMicCapture("onCallEnded")
        finalizeSession()
        streams.clear()
        
        // Hide overlay UI (don't reset offsets here to avoid jumping during animation)
        uiState.isRecording = false
        uiState.showOverlay = false
    }

    private fun detectCallState() {
        // Primary hook: TalkCore native state updates
        val talkCoreNames = listOf(
            "com.snapchat.talkcorev3.TalkCore\$CppProxy",
            "com.snapchat.talkcorev4.TalkCore\$CppProxy",
            "com.snapchat.talkcore.TalkCore\$CppProxy"
        )
        
        talkCoreNames.forEach { className ->
            runCatching {
                findClass(className).apply {
                    hook("updateTSCallingSession", HookStage.BEFORE) { param ->
                        val params = param.arg<Any>(0)
                        val conversationId = params.getObjectFieldOrNull("mConversationId")?.toString() ?: return@hook
                        val inCall = params.getObjectFieldOrNull("mInCall") as? Boolean ?: false
                        context.log.verbose("updateTSCallingSession: inCall=$inCall convo=$conversationId", "CallRecorder")
                        if (inCall) onCallStarted(conversationId) else onCallEnded()
                    }
                    hook("disposeTSCallingSession", HookStage.BEFORE) { 
                        context.log.verbose("disposeTSCallingSession triggered", "CallRecorder")
                        onCallEnded() 
                    }
                }
            }
        }

        // Legacy/Generic hook: TSCallingStateUpdateParams constructor
        runCatching {
            findClass("com.snapchat.talkcorev3.TSCallingStateUpdateParams").hookConstructor(HookStage.AFTER) { param ->
                val instance = param.thisObject<Any>()
                val conversationId = instance.getObjectFieldOrNull("mConversationId")?.toString() ?: return@hookConstructor
                val inCall = instance.getObjectFieldOrNull("mInCall") as? Boolean ?: false
                if (inCall) onCallStarted(conversationId) else onCallEnded()
            }
        }
    }

    private fun checkStreamsAndCleanup() {
        // If all audio streams are released, the call is likely over
        if (streams.isEmpty() && wasInCall) {
            context.coroutineScope.launch {
                delay(200)
                if (streams.isEmpty() && wasInCall) {
                    context.log.verbose("Call end detected via stream release", "CallRecorder")
                    onCallEnded()
                }
            }
        }
    }

    private fun markRemoteStreamActive(streamId: Int, reason: String) {
        lastRemoteActivityTimestamp = System.currentTimeMillis()
        pendingCallEndJob?.cancel()
        pendingCallEndJob = null
        if (activeRemoteStreams.add(streamId)) {
            context.log.verbose("Remote stream active id=$streamId reason=$reason", "CallRecorder")
        }
    }

    private fun markRemoteStreamInactive(streamId: Int, reason: String) {
        if (activeRemoteStreams.remove(streamId)) {
            context.log.verbose("Remote stream inactive id=$streamId reason=$reason", "CallRecorder")
        }
        scheduleCallEndCheck(reason)
    }

    private fun scheduleCallEndCheck(reason: String, delayMs: Long = 1500L) {
        if (!wasInCall || lastRemoteActivityTimestamp == 0L || activeRemoteStreams.isNotEmpty()) return
        pendingCallEndJob?.cancel()
        pendingCallEndJob = context.coroutineScope.launch {
            delay(delayMs)
            if (!wasInCall) return@launch
            if (activeRemoteStreams.isNotEmpty()) return@launch
            val idleFor = System.currentTimeMillis() - lastRemoteActivityTimestamp
            if (idleFor < delayMs) return@launch
            context.log.verbose("Call end detected via remote inactivity reason=$reason idleFor=${idleFor}ms", "CallRecorder")
            onCallEnded()
        }
    }

    private fun ensureSessionStarted() {
        if (callDownloadSession != null) return
        val conversationId = context.feature(Messaging::class).openedConversationUUID?.toString()
            ?: context.feature(Messaging::class).lastFocusedConversationId
            ?: "unknown"
        onCallStarted(conversationId)
    }

    private fun isCallContextActive(): Boolean {
        return wasInCall || uiState.showOverlay || uiState.isRecording
    }

    private fun isDirectVoiceCaptureSource(audioSource: Int?): Boolean {
        return audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION ||
            audioSource == MediaRecorder.AudioSource.VOICE_CALL ||
            audioSource == MediaRecorder.AudioSource.VOICE_UPLINK
    }

    private fun isLikelyCallMicSource(audioSource: Int?): Boolean {
        return audioSource == MediaRecorder.AudioSource.DEFAULT ||
            audioSource == MediaRecorder.AudioSource.MIC ||
            audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION ||
            audioSource == MediaRecorder.AudioSource.UNPROCESSED ||
            audioSource == MediaRecorder.AudioSource.VOICE_PERFORMANCE
    }

    private fun registerAudioRecordStream(audioRecord: AudioRecord, reason: String): CallStreamWrapper? {
        val streamId = audioRecord.hashCode()
        streams[streamId]?.let { return it }

        val audioSource = runCatching { audioRecord.audioSource }.getOrNull()
        val shouldCapture = isDirectVoiceCaptureSource(audioSource) ||
            (isCallContextActive() && isLikelyCallMicSource(audioSource))
        if (!shouldCapture) return null

        val format = runCatching { audioRecord.format }.getOrNull() ?: return null
        if (format.sampleRate <= 0 || format.channelCount <= 0) return null

        return CallStreamWrapper(
            audioFormat = format,
            sourceLabel = "self-internal:$reason",
            onStreamOpened = {
                selfSideStreamOpened = true
                if (audioRecord !== fallbackMicRecord) {
                    stopFallbackMicCapture("internalSelfStreamOpened")
                }
            }
        ).also {
            streams[streamId] = it
            context.log.verbose(
                "Registered AudioRecord stream source=$audioSource reason=$reason sampleRate=${format.sampleRate} channels=${format.channelCount}",
                "CallRecorder"
            )
            if (isDirectVoiceCaptureSource(audioSource) || isCallContextActive()) {
                ensureSessionStarted()
            }
        }
    }

    private fun shouldCaptureSelfSide(): Boolean {
        return callRecorderConfig.callRecorder.get() != "only_record_others"
    }

    private fun scheduleFallbackMicCapture() {
        if (!shouldCaptureSelfSide() || selfSideStreamOpened || fallbackMicJob != null) return
        fallbackMicStartupJob?.cancel()
        fallbackMicStartupJob = context.coroutineScope.launch {
            delay(1200)
            if (!isActive || !uiState.isRecording || selfSideStreamOpened || fallbackMicJob != null) return@launch
            startFallbackMicCapture()
        }
    }

    private fun startFallbackMicCapture() {
        if (!shouldCaptureSelfSide() || selfSideStreamOpened || fallbackMicJob != null || !uiState.isRecording) return

        val sampleRate = 48_000
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBufferSize <= 0) {
            context.log.warn("Fallback mic capture unavailable: invalid min buffer size $minBufferSize", "CallRecorder")
            return
        }

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .setEncoding(encoding)
            .build()

        val audioRecord = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()
        }.getOrElse {
            context.log.error("Failed to create fallback mic recorder", it)
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            context.log.warn("Fallback mic recorder failed to initialize", "CallRecorder")
            runCatching { audioRecord.release() }
            return
        }

        fallbackMicRecord = audioRecord
        context.log.verbose("Starting fallback mic capture", "CallRecorder")

        fallbackMicJob = context.coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufferSize.coerceAtLeast(2048))
            val fallbackWrapper = CallStreamWrapper(
                audioFormat = audioFormat,
                sourceLabel = "self-fallback",
                onStreamOpened = {
                    selfSideStreamOpened = true
                }
            )
            val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply {
                enabled = true
            }
            val noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply {
                enabled = true
            }

            try {
                audioRecord.startRecording()
                while (isActive && uiState.isRecording && isCallContextActive() && fallbackMicRecord === audioRecord) {
                    val bytesRead = runCatching {
                        audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    }.getOrElse {
                        context.log.error("Fallback mic read failed", it)
                        break
                    }

                    if (bytesRead > 0) {
                        fallbackWrapper.write(buffer.copyOf(bytesRead))
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                context.log.error("Fallback mic capture crashed", e)
            } finally {
                fallbackWrapper.close()
                runCatching { audioRecord.stop() }
                echoCanceler?.release()
                noiseSuppressor?.release()
                runCatching { audioRecord.release() }
                if (fallbackMicRecord === audioRecord) {
                    fallbackMicRecord = null
                    fallbackMicJob = null
                }
                context.log.verbose("Stopped fallback mic capture", "CallRecorder")
            }
        }
    }

    private fun stopFallbackMicCapture(reason: String) {
        fallbackMicStartupJob?.cancel()
        fallbackMicStartupJob = null
        if (fallbackMicJob != null || fallbackMicRecord != null) {
            context.log.verbose("Stopping fallback mic capture reason=$reason", "CallRecorder")
        }
        fallbackMicJob?.cancel()
        fallbackMicJob = null
        fallbackMicRecord?.let { record ->
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        fallbackMicRecord = null
    }

    private fun isVoiceCommunicationTrack(attributes: AudioAttributes?, streamType: Int?): Boolean {
        return attributes?.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
            streamType == AudioManager.STREAM_VOICE_CALL ||
            streamType == 6
    }

    private fun registerAudioTrackStream(audioTrack: AudioTrack, reason: String): CallStreamWrapper? {
        val streamId = audioTrack.hashCode()
        streams[streamId]?.let { return it }

        val attributes = runCatching { audioTrack.audioAttributes }.getOrNull()
        val streamType = runCatching { audioTrack.streamType }.getOrNull()
        val isVoiceCommunication = isVoiceCommunicationTrack(attributes, streamType)
        val shouldCapture = isVoiceCommunication ||
            (isCallContextActive() && attributes?.usage == AudioAttributes.USAGE_UNKNOWN)
        if (!shouldCapture) return null

        val format = runCatching { audioTrack.format }.getOrNull() ?: return null
        if (format.sampleRate <= 0 || format.channelCount <= 0) return null

        return CallStreamWrapper(
            audioFormat = format,
            sourceLabel = "remote:$reason"
        ).also {
            streams[streamId] = it
            markRemoteStreamActive(streamId, "register:$reason")
            context.log.verbose(
                "Registered AudioTrack stream streamType=$streamType usage=${attributes?.usage} reason=$reason sampleRate=${format.sampleRate} channels=${format.channelCount}",
                "CallRecorder"
            )
            if (isVoiceCommunication || isCallContextActive()) {
                ensureSessionStarted()
            }
        }
    }

    private fun clampCopyRange(offset: Int, requestedLength: Int, maxLength: Int): Pair<Int, Int>? {
        if (requestedLength <= 0 || maxLength <= 0) return null
        val safeOffset = offset.coerceAtLeast(0).coerceAtMost(maxLength)
        val available = (maxLength - safeOffset).coerceAtLeast(0)
        val safeLength = requestedLength.coerceAtMost(available)
        if (safeLength <= 0) return null
        return safeOffset to safeLength
    }

    private fun copyAudioRecordByteBuffer(data: ByteBuffer, bytesRead: Int): ByteArray? {
        if (bytesRead <= 0) return null
        val currentPosition = data.position()
        val start = (currentPosition - bytesRead).coerceAtLeast(0)
        val end = currentPosition.coerceAtMost(data.limit())
        if (end <= start) return null
        return ByteArray(end - start).also { out ->
            val dup = data.duplicate()
            dup.position(start)
            dup.limit(end)
            dup.get(out)
        }
    }

    private fun copyFloatArrayToByteArray(data: FloatArray, offset: Int, sampleCount: Int): ByteArray? {
        val (safeOffset, safeLength) = clampCopyRange(offset, sampleCount, data.size) ?: return null
        return ByteArray(safeLength * Float.SIZE_BYTES).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(data, safeOffset, safeLength)
        }
    }

    override fun init() {
        if (callRecorderConfig.callRecorder.getNullable() == null) return
        
        // Listen for UI control events
        context.event.subscribe(InAppOverlay.CallRecorderControlEvent::class) { event ->
            if (event.start) startManualRecording() else stopRecording()
        }
        
        detectCallState()

        val recorderConfig = callRecorderConfig.callRecorder.get()

        AudioRecord::class.java.apply {
            if (recorderConfig == "only_record_others") return@apply
            hookConstructor(HookStage.AFTER) { param ->
                registerAudioRecordStream(param.thisObject<AudioRecord>(), "constructor")
            }

            hook("read", HookStage.AFTER) { param ->
                val result = param.getResult() as? Int ?: 0
                if (result <= 0) return@hook
                val audioRecord = param.thisObject<AudioRecord>()
                val wrapper = streams[param.thisObject<Any>().hashCode()]
                    ?: registerAudioRecordStream(audioRecord, "read")
                    ?: return@hook
                
                val buffer = when (val data = param.arg<Any>(0)) {
                    is ByteBuffer -> copyAudioRecordByteBuffer(data, result) ?: return@hook
                    is ByteArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        val (safeOffset, safeLength) = clampCopyRange(offset, result, data.size) ?: return@hook
                        data.copyOfRange(safeOffset, safeOffset + safeLength)
                    }
                    is ShortArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        val (safeOffset, safeLength) = clampCopyRange(offset, result, data.size) ?: return@hook
                        ByteArray(safeLength * 2).also {
                            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(data, safeOffset, safeLength)
                        }
                    }
                    is FloatArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        copyFloatArrayToByteArray(data, offset, result) ?: return@hook
                    }
                    else -> return@hook
                }
                wrapper.write(buffer)
            }

            hook("startRecording", HookStage.AFTER) {
                registerAudioRecordStream(it.thisObject<AudioRecord>(), "startRecording")
            }

            hook("stop", HookStage.BEFORE) { checkStreamsAndCleanup() }
            hook("release", HookStage.BEFORE) { 
                streams.remove(it.thisObject<Any>().hashCode())?.close()
                checkStreamsAndCleanup()
            }
        }

        AudioTrack::class.java.apply {
            if (recorderConfig == "only_record_self") return@apply
            hookConstructor(HookStage.AFTER) { param ->
                registerAudioTrackStream(param.thisObject<AudioTrack>(), "constructor")
            }

            hook("write", HookStage.BEFORE) { param ->
                val streamId = param.thisObject<Any>().hashCode()
                markRemoteStreamActive(streamId, "write")
                val wrapper = streams[streamId]
                    ?: registerAudioTrackStream(param.thisObject<AudioTrack>(), "write")
                    ?: return@hook
                val data = param.arg<Any>(0)

                val buffer = when (data) {
                    is ByteBuffer -> {
                        val requestedSize = param.argNullable<Int>(1) ?: data.remaining()
                        val safeSize = requestedSize.coerceAtMost(data.remaining()).coerceAtLeast(0)
                        if (safeSize <= 0) return@hook
                        ByteArray(safeSize).also {
                            val pos = data.position()
                            data.get(it, 0, safeSize)
                            data.position(pos)
                        }
                    }
                    is ByteArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        val requestedSize = param.argNullable<Int>(2) ?: data.size
                        val (safeOffset, safeLength) = clampCopyRange(offset, requestedSize, data.size) ?: return@hook
                        data.copyOfRange(safeOffset, safeOffset + safeLength)
                    }
                    is ShortArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        val requestedSize = param.argNullable<Int>(2) ?: data.size
                        val (safeOffset, safeLength) = clampCopyRange(offset, requestedSize, data.size) ?: return@hook
                        ByteArray(safeLength * 2).also {
                            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(data, safeOffset, safeLength)
                        }
                    }
                    is FloatArray -> {
                        val offset = param.argNullable<Int>(1) ?: 0
                        val requestedSize = param.argNullable<Int>(2) ?: data.size
                        copyFloatArrayToByteArray(data, offset, requestedSize) ?: return@hook
                    }
                    else -> return@hook
                }
                wrapper.write(buffer)
            }

            hook("play", HookStage.AFTER) {
                val audioTrack = it.thisObject<AudioTrack>()
                markRemoteStreamActive(audioTrack.hashCode(), "play")
                registerAudioTrackStream(audioTrack, "play")
            }

            hook("stop", HookStage.AFTER) {
                markRemoteStreamInactive(it.thisObject<Any>().hashCode(), "stop")
                checkStreamsAndCleanup()
            }
            hook("pause", HookStage.AFTER) {
                markRemoteStreamInactive(it.thisObject<Any>().hashCode(), "pause")
            }
            hook("flush", HookStage.AFTER) {
                markRemoteStreamInactive(it.thisObject<Any>().hashCode(), "flush")
            }
            hook("release", HookStage.BEFORE) { 
                markRemoteStreamInactive(it.thisObject<Any>().hashCode(), "release")
                streams.remove(it.thisObject<Any>().hashCode())?.close()
                checkStreamsAndCleanup()
            }
        }
    }
}
