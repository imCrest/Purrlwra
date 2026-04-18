package cock.crest.purrfectsnap.lite.ui.setup.screens.impl

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.setup.patch.AutoPatchServer
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.screens.SetupScreen
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request

class RootInstallSnapchatScreen : SetupScreen() {
    private val autoPatchServer = AutoPatchServer()
    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.HOURS)
        .connectTimeout(1, TimeUnit.HOURS)
        .readTimeout(1, TimeUnit.HOURS)
        .writeTimeout(1, TimeUnit.HOURS)
        .build()
    private val targetPackage = "com.snapchat.android"

    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val translation = context.translation
        val logs = remember { mutableStateListOf(translation["setup.root_install.ready_log"]) }
        @Suppress("DEPRECATION")
        val clipboard = LocalClipboardManager.current
        var progress by remember { mutableFloatStateOf(-1f) }
        var downloadedApkPath by rememberSaveable { mutableStateOf<String?>(null) }
        val downloadedApk = remember(downloadedApkPath) { downloadedApkPath?.let(::File) }
        var isRunning by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var downloadStartedAt by rememberSaveable { mutableStateOf(0L) }
        var installVerified by rememberSaveable { mutableStateOf(false) }
        var installRequested by rememberSaveable { mutableStateOf(false) }
        var installWatcher by remember { mutableStateOf<Job?>(null) }
        var downloadFinished by rememberSaveable { mutableStateOf(false) }
        val logPulse by rememberInfiniteTransition(label = "rootInstallLogPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "rootInstallLogPulseValue"
        )

        LaunchedEffect(Unit) { allowNext(false) }
        LaunchedEffect(installVerified) { allowNext(installVerified) }

        fun pushLog(message: String) {
            if (logs.size > 120) logs.removeAt(0)
            logs.add(message)
        }

        fun isSnapchatInstalledAfter(timestamp: Long): Boolean {
            if (timestamp == 0L) return false
            val info = runCatching {
                context.androidContext.packageManager.getPackageInfo(targetPackage, 0)
            }.getOrNull() ?: return false
            return info.lastUpdateTime >= timestamp
        }

        fun isSnapchatInstalled(): Boolean {
            return runCatching { context.androidContext.packageManager.getPackageInfo(targetPackage, 0) }.isSuccess
        }

        fun startInstallWatcher() {
            if (downloadStartedAt == 0L) return
            installWatcher?.cancel()
            installWatcher = coroutineScope.launch {
                repeat(80) {
                    if (isSnapchatInstalledAfter(downloadStartedAt)) {
                        installVerified = true
                        pushLog(translation["setup.root_install.install_confirmed_log"])
                        return@launch
                    }
                    delay(1200)
                }
            }
        }

        LaunchedEffect(installRequested, downloadStartedAt) {
            if (installRequested && downloadStartedAt > 0) {
                startInstallWatcher()
            }
        }

        LaunchedEffect(downloadStartedAt) {
            if (downloadStartedAt > 0 && isSnapchatInstalledAfter(downloadStartedAt)) {
                installVerified = true
            }
        }

        LaunchedEffect(installVerified) {
            if (installVerified) {
                downloadedApk?.let { runCatching { it.delete() } }
                downloadedApkPath = null
            }
        }

        suspend fun pushStatus(message: String) = withContext(Dispatchers.Main) { pushLog(message) }
        suspend fun setProgress(value: Float) = withContext(Dispatchers.Main) { progress = value }

        suspend fun downloadSnapchatFromAutoPatchServer(): File? = withContext(Dispatchers.IO) {
            val latestApk = autoPatchServer.fetchLatestSnapchatApk() ?: return@withContext null
            pushStatus(
                translation.format(
                    "setup.root_install.download_recommended_status",
                    "version" to latestApk.tagName
                )
            )

            okHttpClient.newCall(Request.Builder().url(latestApk.downloadUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val cacheDir = listOfNotNull(
                    context.activity?.externalCacheDir,
                    context.androidContext.externalCacheDir,
                    context.androidContext.cacheDir
                ).firstOrNull { it.exists() || it.mkdirs() } ?: return@withContext null
                val outputFile = File(cacheDir, latestApk.apkName)
                if (outputFile.parentFile?.exists() == false && outputFile.parentFile?.mkdirs() == false) {
                    return@withContext null
                }
                outputFile.outputStream().use { output ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var totalRead = 0L
                        val totalSize = response.body?.contentLength() ?: -1L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (totalSize > 0) setProgress(totalRead.toFloat() / totalSize.toFloat())
                        }
                    } ?: return@withContext null
                }
                setProgress(-1f)
                outputFile
            }
        }

        fun installDownloadedApk() {
            val apk = downloadedApk ?: return
            installRequested = true
            startInstallWatcher()
            val uri = FileProvider.getUriForFile(
                context.androidContext,
                "${context.androidContext.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.androidContext.startActivity(intent)
        }

        fun markAlreadyInstalled() {
            installRequested = false
            installVerified = true
            pushLog(translation["setup.root_install.mark_installed_log"])
        }

        fun startDownloadAndInstall() {
            coroutineScope.launch(Dispatchers.Main) {
                isRunning = true
                allowNext(false)
                error = null
                progress = -1f
                downloadedApkPath = null
                installVerified = false
                installRequested = false
                downloadFinished = false
                downloadStartedAt = System.currentTimeMillis()
                logs.clear()
                pushLog(translation["setup.root_install.start_download_log"])
                runCatching {
                    if (isSnapchatInstalled()) {
                        pushStatus(translation["setup.root_install.uninstall_prompt_status"])
                        throw IllegalStateException(translation["setup.root_install.uninstall_prompt_error"])
                    }
                    pushStatus(translation["setup.root_install.fetching_apk_status"])
                    val downloaded = downloadSnapchatFromAutoPatchServer()
                        ?: throw IllegalStateException(translation["setup.root_install.download_failed_error"])
                    downloadedApkPath = downloaded.absolutePath
                    pushStatus(
                        translation.format(
                            "setup.root_install.download_completed_status",
                            "fileName" to downloaded.name
                        )
                    )
                    downloadFinished = true
                    pushStatus(translation["setup.root_install.launching_installer_status"])
                    installDownloadedApk()
                }.onFailure {
                    val message = it.message ?: it.toString()
                    error = it.message ?: it.toString()
                    it.stackTraceToString()
                        .lineSequence()
                        .filter { line -> line.isNotBlank() }
                        .forEach { line -> pushLog(line) }
                    pushStatus(
                        translation.format(
                            "setup.root_install.failed_status",
                            "message" to message
                        )
                    )
                }
                isRunning = false
                progress = -1f
            }
        }

        val accent = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowSecondary,
                    PurrfectPalette.glowPrimary
                )
            )
        }

        SetupCard {
            StepTitle(
                title = translation["setup.root_install.title"],
                subtitle = null,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.03f),
                tonalElevation = 0.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(PurrfectPalette.cardOverlay)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(visible = isRunning || progress >= 0f) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isDownloading = progress >= 0f
                            Text(
                                text = if (isDownloading) {
                                    translation.format(
                                        "setup.root_install.status_downloading",
                                        "percent" to (progress * 100).toInt().toString()
                                    )
                                } else {
                                    translation["setup.root_install.status_preparing"]
                                },
                                color = PurrfectPalette.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            if (isDownloading) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    color = PurrfectPalette.glowPrimary,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = PurrfectPalette.glowPrimary,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }

                    LogsPanel(
                        logs = logs,
                        pulse = logPulse,
                        accent = accent,
                        translation = translation,
                        onCopy = {
                            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            pushLog(translation["setup.root_install.logs_copied"])
                        }
                    )

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (installVerified) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Text(
                                        text = translation["setup.root_install.install_success"],
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            if (downloadedApk == null) {
                                GradientActionButton(
                                    label = translation["setup.root_install.download_button"],
                                    icon = Icons.Filled.Download,
                                    onClick = { startDownloadAndInstall() },
                                    enabled = !isRunning
                                )
                            }
                            if (downloadedApk != null) {
                                GradientActionButton(
                                    label = translation["setup.root_install.install_button"],
                                    icon = Icons.Filled.Verified,
                                    onClick = { installDownloadedApk() },
                                    enabled = true
                                )
                                val manualInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scaleOnPress(manualInteraction)
                                        .clickable(
                                            interactionSource = manualInteraction,
                                            indication = null
                                        ) { markAlreadyInstalled() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.9f)
                                        )
                                        Text(
                                            text = translation["setup.root_install.already_installed_button"],
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val gradient = Brush.horizontalGradient(listOf(PurrfectPalette.glowSecondary, PurrfectPalette.glowPrimary))
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent)
            .scaleOnPress(interaction)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        tonalElevation = 0.dp,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .background(if (enabled) gradient else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                .padding(vertical = 14.dp, horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LogsPanel(
    logs: List<String>,
    pulse: Float,
    accent: Brush,
    translation: LocaleWrapper,
    onCopy: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val animatedBrush = Brush.linearGradient(
        colors = listOf(
            PurrfectPalette.glowPrimary.copy(alpha = 0.18f + 0.1f * pulse),
            Color.Transparent,
            PurrfectPalette.glowSecondary.copy(alpha = 0.12f + 0.1f * (1 - pulse))
        ),
        start = Offset.Zero,
        end = Offset(400f * (0.6f + pulse), 260f * (0.4f + (1 - pulse)))
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .background(animatedBrush)
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = translation["setup.root_install.logs_title"],
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCopy() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = translation["setup.root_install.copy_button"],
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logs.forEach { line ->
                        Text(
                            text = translation.format(
                                "setup.root_install.log_line_prefix",
                                "line" to line
                            ),
                            color = PurrfectPalette.textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
