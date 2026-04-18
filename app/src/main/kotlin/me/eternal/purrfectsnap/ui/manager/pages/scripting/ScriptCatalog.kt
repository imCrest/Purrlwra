@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cock.crest.purrfectsnap.lite.ui.manager.pages.scripting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.*
import cock.crest.purrfectsnap.lite.common.util.ktx.openLink
import cock.crest.purrfectsnap.lite.storage.getRepositories
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticEmptyState
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import okhttp3.OkHttpClient
import okhttp3.Request

data class ScriptRepoManifest(
    val scripts: List<ScriptRepoEntry>
)
data class ScriptRepoEntry(
    val name: String,
    val author: String? = null,
    val description: String? = null,
    val version: String? = null,
    val filepath: String
)

@Composable
fun ScriptCatalog(root: ScriptingRootSection) {
    val context = root.context
    val translation = remember { context.translation.getCategory("manager.scripting.catalog") }
    val coroutineScope = rememberCoroutineScope()
    val okHttpClient = remember { OkHttpClient() }
    val gson = remember { context.gson }

    var repositories by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoIndexes by remember { mutableStateOf<Map<String, ScriptRepoManifest>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshIndexes() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val repos = context.database.getRepositories("script")
            withContext(Dispatchers.Main) {
                repositories = repos
            }
            
            if (repos.isNotEmpty()) {
                val newIndexes = mutableMapOf<String, ScriptRepoManifest>()
                repos.forEach { repoRoot ->
                    val indexUrl = if (repoRoot.endsWith("/")) "${repoRoot}index.json" else "$repoRoot/index.json"
                    try {
                        val req = Request.Builder().url(indexUrl).build()
                        okHttpClient.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.charStream()?.let { reader ->
                                    val parsed = gson.fromJson(reader, ScriptRepoManifest::class.java)
                                    if (!parsed.scripts.isNullOrEmpty()) {
                                        newIndexes[repoRoot] = parsed
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    repoIndexes = newIndexes
                    isLoading = false
                }
            } else {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshIndexes() }

    val allScripts = repoIndexes.entries.flatMap { (repoUrl, manifest) ->
        manifest.scripts.map { repoUrl to it }
    }

    suspend fun isScriptInstalled(scriptName: String): Boolean {
        return try {
            val installedScripts = context.scriptManager.getSyncedModules()
            installedScripts.any { it.name.equals(scriptName, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    fun downloadScript(repoUrl: String, entry: ScriptRepoEntry) {
        coroutineScope.launch(Dispatchers.IO) {
            if (isScriptInstalled(entry.name)) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation["script_already_installed"])
                }
                return@launch
            }

            val rawUrl = if (repoUrl.endsWith("/")) repoUrl + entry.filepath else repoUrl + "/" + entry.filepath

            if (root.isScriptInstalledByUrl(rawUrl)) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation["script_already_installed"])
                }
                return@launch
            }

            try {
                val req = Request.Builder().url(rawUrl).build()
                okHttpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) { context.shortToast(translation.format("download_failed", "code" to response.code.toString())) }
                        return@use
                    }
                    val content = response.body?.bytes()
                    if (content != null) {
                        val folder = context.scriptManager.getScriptsFolder()
                        if (folder != null) {
                            val file = folder.createFile("application/javascript", "${entry.name}.js")
                            if (file != null) {
                                context.androidContext.contentResolver.openOutputStream(file.uri)?.use { output ->
                                    output.write(content)
                                }
                                withContext(Dispatchers.Main) {
                                    context.shortToast(translation["script_downloaded"])
                                    root.reloadDispatcher.dispatch()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    context.shortToast(translation["could_not_create_file"])
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                context.shortToast(translation["no_scripts_folder_selected"])
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation.format("error", "message" to (e.localizedMessage ?: context.translation["common.unknown"])))
                }
            }
        }
    }

    if (repositories.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AestheticEmptyState(
                icon = Icons.Default.Public,
                title = translation["no_repos_added"],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                actions = {
                    Button(
                        onClick = {
                            context.androidContext.openLink(
                                "https://github.com/sujxlsahu/PurrfectSnap/tree/dev/app/src/main/kotlin",
                                context.translation["toast_open_link_failed"]
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(translation["link_text"])
                    }
                }
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + root.routes.bottomPadding)
        ) {
            item {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                                        PurrfectPalette.glowSecondary.copy(alpha = 0.30f)
                                    )
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text(
                                    text = translation["loading"],
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (allScripts.isEmpty() && repositories.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AestheticEmptyState(
                            icon = Icons.Default.Code,
                            title = translation["no_scripts_available"],
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                        )
                    }
                }
            }
            items(allScripts) { (repoUrl, entry) ->
                var isDownloading by remember { mutableStateOf(false) }
                var isAlreadyInstalled by remember { mutableStateOf(false) }

                LaunchedEffect(entry) {
                    isAlreadyInstalled = isScriptInstalled(entry.name)
                }

                val shape = RoundedCornerShape(20.dp)
                val border = remember {
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                        )
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = shape,
                    color = PurrfectPalette.cardOverlayColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurrfectPalette.cardOverlay, shape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = entry.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            entry.author?.let {
                                Text(
                                    text = translation.format("by_author", "author" to it),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    color = PurrfectPalette.textSecondary
                                )
                            }
                            entry.description?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = PurrfectPalette.textSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.06f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Text(
                                    text = translation.format("version", "version" to (entry.version ?: context.translation["common.not_available"])),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                        Button(
                            enabled = !isDownloading && !isAlreadyInstalled,
                            onClick = {
                                isDownloading = true
                                downloadScript(repoUrl, entry)
                                coroutineScope.launch {
                                    delay(1000)
                                    isDownloading = false
                                    isAlreadyInstalled = isScriptInstalled(entry.name)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = PurrfectPalette.textSecondary
                            )
                        ) {
                            when {
                                isAlreadyInstalled -> Text(translation["installed_button"])
                                isDownloading -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                else -> Text(translation["download_button"])
                            }
                        }
                    }
                }
            }
        }
    }
}
