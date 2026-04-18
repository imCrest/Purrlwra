package cock.crest.purrfectsnap.lite.download.call

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.bridge.DownloadCallback
import cock.crest.purrfectsnap.lite.bridge.call.CallDownloadSession
import cock.crest.purrfectsnap.lite.common.data.download.AudioStreamFormat
import cock.crest.purrfectsnap.lite.common.data.download.DownloadMetadata
import cock.crest.purrfectsnap.lite.common.data.download.MediaDownloadSource
import cock.crest.purrfectsnap.lite.common.data.download.createNewFilePath
import cock.crest.purrfectsnap.lite.download.DownloadProcessor
import cock.crest.purrfectsnap.lite.download.FFMpegProcessor
import cock.crest.purrfectsnap.lite.task.PendingTaskListener
import cock.crest.purrfectsnap.lite.task.Task
import cock.crest.purrfectsnap.lite.task.TaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.absoluteValue

class CallDownloadSessionImpl(
    private val context: RemoteSideContext,
    private val callStartTimestamp: Long,
    private val author: String,
): CallDownloadSession.Stub() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var callEnded: Boolean = false

    private val streams = CopyOnWriteArrayList<CallStream>()
    private var mergeJob: Job? = null

    init {
        context.log.verbose("Starting call callStartTimestamp=$callStartTimestamp")
    }

    inner class CallStream(
        val startTimestampMillis: Long,
        private val audioStreamFormat: AudioStreamFormat
    ) {
        val job: Job
        val writePfd: ParcelFileDescriptor

        val outputFile = context.androidContext.cacheDir.resolve("call_${UUID.randomUUID()}.wav").apply {
            if (exists()) delete()
        }

        init {
            val pipe = ParcelFileDescriptor.createPipe()
            writePfd = pipe[1]

            job = coroutineScope.launch {
                runCatching {
                    FFMpegProcessor.newFFMpegProcessor(context).execute(
                        FFMpegProcessor.Request(
                            action = FFMpegProcessor.Action.DOWNLOAD_AUDIO_STREAM,
                            inputs = listOf("/proc/self/fd/${pipe[0].fd}"),
                            output = outputFile,
                            audioStreamFormat = audioStreamFormat
                        )
                    )
                }.onFailure {
                    context.log.error("Error converting call audio stream", it)
                    runCatching {
                        outputFile.delete()
                    }
                }

                pipe.forEach { runCatching { it.close() } }

                context.log.verbose("Call stream ended startTimestampMillis=$startTimestampMillis")
            }
        }
    }

    override fun createStream(
        startTimestampMillis: Long,
        channels: Int,
        sampleRate: Int,
        encoding: Int
    ): ParcelFileDescriptor? {
        if (callEnded) return null

        context.log.verbose("createFileDescriptor startTimestampMillis=$startTimestampMillis, channels=$channels, sampleRate=$sampleRate, encoding=$encoding")
        val callStream = CallStream(
            startTimestampMillis = startTimestampMillis,
            audioStreamFormat = AudioStreamFormat(
                channels = channels,
                sampleRate = sampleRate,
                encoding = encoding
            )
        )

        synchronized(streams) {
            streams.add(callStream)
        }

        return callStream.writePfd
    }

    override fun end() {
        if (callEnded) return
        callEnded = true

        if (streams.isEmpty()) {
            context.log.verbose("No call chunks to merge")
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val dateString = dateFormat.format(Date(callStartTimestamp))
        val finalFileName = "Call_${author}_$dateString"

        val outputFile = context.androidContext.cacheDir.resolve("${finalFileName}_final.mp3")
        val pendingTask = context.taskManager.createPendingTask(
            Task(
                type = TaskType.DOWNLOAD,
                title = context.translation.format("task_call_recording_title", "author" to author),
                author = author,
                hash = UUID.randomUUID().toString()
            )
        ).apply {
            addListener(PendingTaskListener(
                onCancel = {
                    mergeJob?.cancel()
                    outputFile.delete()
                    streams.forEach { stream ->
                        stream.outputFile.delete()
                    }
                }
            ))
        }

        mergeJob = coroutineScope.launch {
            try {
                streams.forEach { stream ->
                    stream.job.join()
                }

                val sortedStreams = streams.filter { it.outputFile.exists() }.sortedBy { it.startTimestampMillis }
                if (sortedStreams.isEmpty()) {
                    pendingTask.fail("No recorded audio data")
                    return@launch
                }

                FFMpegProcessor.newFFMpegProcessor(context, pendingTask).execute(
                    FFMpegProcessor.Request(
                        action = FFMpegProcessor.Action.MERGE_AUDIO_STREAMS,
                        inputs = sortedStreams.map { it.outputFile.absolutePath },
                        output = outputFile,
                        inputDelayOffsets = sortedStreams.associate { stream -> stream.outputFile.absolutePath to (stream.startTimestampMillis - callStartTimestamp).coerceAtLeast(0L) }
                    )
                )

                DownloadProcessor(context, object: DownloadCallback.Default() {
                    override fun onSuccess(outputPath: String) {
                        context.log.verbose("Downloaded call $outputPath")
                        context.shortToast(context.translation["features.properties.downloader.properties.call_recorder.properties.call_recording_saved_toast"])
                    }
                }).saveMediaToGallery(pendingTask, outputFile, DownloadMetadata(
                    mediaIdentifier = UUID.randomUUID().toString(),
                    outputPath = createNewFilePath(
                        context.config.root,
                        finalFileName,
                        downloadSource = MediaDownloadSource.VOICE_CALL,
                        mediaAuthor = author,
                        creationTimestamp = System.currentTimeMillis()
                    ),
                    mediaAuthor = author,
                    downloadSource = MediaDownloadSource.VOICE_CALL.translate(context.translation),
                    iconUrl = null
                ))
            } catch (e: Exception) {
                context.log.error("Failed to merge call recording", e)
                pendingTask.fail("Merge failed: ${e.message}")
            } finally {
                streams.forEach { stream ->
                    stream.outputFile.delete()
                }
                outputFile.delete()
            }

            context.log.verbose("ending call")
        }
    }
}
