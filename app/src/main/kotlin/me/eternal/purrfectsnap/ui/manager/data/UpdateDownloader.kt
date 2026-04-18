package cock.crest.purrfectsnap.lite.ui.manager.data

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tonyodev.fetch2.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.RemoteSideContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateDownloader {
    private const val TAG = "UpdateDownloader"
    private var fetch: Fetch? = null
    private var listener: FetchListener? = null
    private val fallbackHttpClient by lazy { OkHttpClient() }

    private fun getInstance(context: RemoteSideContext): Fetch {
        fetch?.let { return it }
        fetch = run {
            val fetchConfiguration = FetchConfiguration.Builder(context.androidContext)
                .setDownloadConcurrentLimit(3)
                .build()
            Fetch.getInstance(fetchConfiguration)
        }
        return fetch!!
    }
    enum class DownloadState {
        IDLE,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }

    val downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadProgress = MutableStateFlow(0f)


    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(targetDirectory, zipEntry.name)
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zipEntry = zis.nextEntry
            }
        }
    }

    private fun resolveDownloadedApk(
        remoteContext: RemoteSideContext,
        downloadedFile: File
    ): File {
        val context = remoteContext.androidContext
        if (downloadedFile.extension.equals("zip", ignoreCase = true)) {
            val unzipDir = File(context.externalCacheDir, "update")
            if (unzipDir.exists()) unzipDir.deleteRecursively()
            unzipDir.mkdirs()
            remoteContext.log.info(
                "Extracting update archive ${downloadedFile.absolutePath} -> ${unzipDir.absolutePath}",
                TAG
            )
            unzip(downloadedFile, unzipDir)
            return unzipDir.walk().firstOrNull { it.isFile && it.extension.equals("apk", true) }
                ?: throw IllegalStateException("No APK found in the downloaded archive")
        }

        if (!downloadedFile.extension.equals("apk", ignoreCase = true)) {
            remoteContext.log.warn(
                "Downloaded file is not an APK or ZIP (${downloadedFile.name}); attempting installation anyway.",
                TAG
            )
        }
        return downloadedFile
    }

    private fun scheduleReset(scope: CoroutineScope) {
        scope.launch {
            delay(2000)
            downloadState.value = DownloadState.IDLE
            downloadProgress.value = 0f
        }
    }

    private fun installDownloadedFile(
        remoteContext: RemoteSideContext,
        downloadedFile: File,
        scope: CoroutineScope
    ) {
        val context = remoteContext.androidContext
        val translation = remoteContext.translation.getCategory("manager.sections.home")
        downloadState.value = DownloadState.COMPLETED
        runCatching {
            remoteContext.log.info(
                "Download completed -> ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)",
                TAG
            )
            Toast.makeText(context, translation["update_download_completed_toast"], Toast.LENGTH_SHORT).show()
            val apkFile = resolveDownloadedApk(remoteContext, downloadedFile)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            remoteContext.log.info("Launching installer for ${apkFile.absolutePath}", TAG)
            context.startActivity(installIntent)
            scope.launch(Dispatchers.IO) {
                delay(30_000)
                runCatching { downloadedFile.delete() }
                apkFile.parentFile
                    ?.takeIf { it.name == "update" }
                    ?.let { dir -> runCatching { dir.deleteRecursively() } }
                remoteContext.log.info("Cleaned downloaded update files", TAG)
            }
        }.onFailure {
            Toast.makeText(context, translation["update_install_failed_toast"], Toast.LENGTH_SHORT).show()
            remoteContext.log.error("Failed to install downloaded update", it, TAG)
            downloadState.value = DownloadState.FAILED
        }
        scheduleReset(scope)
    }

    private fun failDownload(
        remoteContext: RemoteSideContext,
        scope: CoroutineScope,
        errorMessage: String,
        throwable: Throwable? = null
    ) {
        val context = remoteContext.androidContext
        val translation = remoteContext.translation.getCategory("manager.sections.home")
        downloadState.value = DownloadState.FAILED
        Toast.makeText(
            context,
            translation.format("update_download_failed_toast", "error" to errorMessage),
            Toast.LENGTH_SHORT
        ).show()
        throwable?.let { remoteContext.log.error("Update download failed: $errorMessage", it, TAG) }
            ?: remoteContext.log.error("Update download failed: $errorMessage", TAG)
        scheduleReset(scope)
    }

    private fun startHttpFallbackDownload(
        remoteContext: RemoteSideContext,
        downloadUrl: String,
        filePath: String,
        scope: CoroutineScope
    ) {
        val partialFile = File("$filePath.part")
        val outputFile = File(filePath)
        scope.launch(Dispatchers.IO) {
            runCatching {
                remoteContext.log.warn("Fetch download failed, retrying update download via OkHttp fallback", TAG)
                partialFile.parentFile?.mkdirs()
                if (partialFile.exists()) partialFile.delete()
                if (outputFile.exists()) outputFile.delete()

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "PurrfectSnap-Updater")
                    .build()

                fallbackHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP_${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("EMPTY_RESPONSE_BODY")
                    val contentLength = body.contentLength()
                    var downloadedBytes = 0L

                    body.byteStream().use { input ->
                        partialFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (contentLength > 0) {
                                    downloadProgress.value = downloadedBytes.toFloat() / contentLength.toFloat()
                                }
                            }
                        }
                    }
                }

                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
                installDownloadedFile(remoteContext, outputFile, scope)
            }.onFailure {
                runCatching { partialFile.delete() }
                failDownload(remoteContext, scope, "FALLBACK_${it.message ?: "UNKNOWN"}", it)
            }
        }
    }

    fun downloadAndInstall(
        remoteContext: RemoteSideContext,
        downloadUrl: String,
        fileName: String,
        scope: CoroutineScope
    ) {
        val context = remoteContext.androidContext
        val translation = remoteContext.translation.getCategory("manager.sections.home")
        val fetch = getInstance(remoteContext)
        val filePath = File(context.externalCacheDir, fileName).path
        remoteContext.log.info("Starting update download from $downloadUrl -> $filePath", TAG)
        val request = Request(downloadUrl, filePath).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
        }
        listener?.let { fetch.removeListener(it) }
        var fallbackAttempted = false
        listener = object : AbstractFetchListener() {
            override fun onAdded(download: Download) {
                downloadState.value = DownloadState.DOWNLOADING
                remoteContext.log.info("Queued update download: ${download.file}", TAG)
            }

            override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
                downloadState.value = DownloadState.DOWNLOADING
                Toast.makeText(context, translation["update_download_started_toast"], Toast.LENGTH_SHORT).show()
            }

            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                downloadProgress.value = download.progress / 100f
            }

            override fun onCompleted(download: Download) {
                installDownloadedFile(remoteContext, File(download.file), scope)
                fetch.removeListener(this)
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                fetch.removeListener(this)
                if (!fallbackAttempted && error == Error.REQUEST_NOT_SUCCESSFUL) {
                    fallbackAttempted = true
                    downloadState.value = DownloadState.DOWNLOADING
                    downloadProgress.value = 0f
                    remoteContext.log.warn("Fetch returned REQUEST_NOT_SUCCESSFUL, starting fallback downloader", TAG)
                    startHttpFallbackDownload(remoteContext, downloadUrl, filePath, scope)
                    return
                }
                failDownload(remoteContext, scope, error.toString(), throwable)
            }
        }
        fetch.addListener(listener!!)
        fetch.enqueue(request, { }, { })
    }
}
