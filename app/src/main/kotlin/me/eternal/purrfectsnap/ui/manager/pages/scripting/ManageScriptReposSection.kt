package cock.crest.purrfectsnap.lite.ui.manager.pages.scripting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.compose.ui.window.Dialog
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.common.util.ktx.getUrlFromClipboard
import cock.crest.purrfectsnap.lite.storage.addRepo
import cock.crest.purrfectsnap.lite.storage.getRepositories
import cock.crest.purrfectsnap.lite.storage.removeRepo
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticDialog
import cock.crest.purrfectsnap.lite.ui.manager.components.AestheticEmptyState
import cock.crest.purrfectsnap.lite.ui.manager.components.FloatingTopBar
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import okhttp3.OkHttpClient

class ManageScriptReposSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.scripting.repos") }
    private val refreshTrigger = mutableStateOf(0)
    private val okHttpClient by lazy { OkHttpClient() }

    private fun extractRepoInfo(url: String): Pair<String, String> {
        if (url.contains("raw.githubusercontent.com")) {
            val parts = url.removePrefix("https://raw.githubusercontent.com/").split("/")
            if (parts.size >= 2) {
                return parts[1] to parts[0]
            }
        }
        return url.substringAfterLast("/").substringBeforeLast(".") to url.substringAfter("://").substringBefore("/")
    }

    override val floatingActionButton: @Composable () -> Unit = {
        var showAddDialog by remember { mutableStateOf(false) }
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorDialogMessage by remember { mutableStateOf("") }

        if (showErrorDialog) {
            AestheticDialog(
                onDismissRequest = { showErrorDialog = false },
                title = translation["invalid_repo_title"],
                text = errorDialogMessage,
                icon = Icons.Default.Error,
                confirmButtonText = translation["button.ok"],
                onConfirm = { showErrorDialog = false },
                showCloseButton = false
            )
        }

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            )
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(translation["add_repo_button"])
        }

        if (showAddDialog) {
            val coroutineScope = rememberCoroutineScope { Dispatchers.IO }

            var url by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { showAddDialog = false }) {
                val focusRequester = remember { FocusRequester() }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
                            ) {
                                Icon(
                                    Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = translation["add_repo_dialog_title"],
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = translation["manager.dialogs.scripting.repo_hint"]
                                        ?: translation["repo_url_label"]
                                        ?: "",
                                    color = PurrfectPalette.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onGloballyPositioned { focusRequester.requestFocus() },
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(translation["repo_url_label"], color = PurrfectPalette.textSecondary) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                cursorColor = PurrfectPalette.glowSecondary,
                                focusedLabelColor = PurrfectPalette.textSecondary,
                                unfocusedLabelColor = PurrfectPalette.textSecondary
                            )
                        )
                        LaunchedEffect(Unit) {
                            context.androidContext.getUrlFromClipboard()?.let { url = it }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text(translation["button.cancel"], color = PurrfectPalette.textSecondary)
                            }
                            Button(
                                enabled = !loading && url.isNotBlank(),
                                onClick = {
                                    loading = true
                                    coroutineScope.launch {
                                        runCatching {
                                            var modifiedUrl = url
                                            if (url.startsWith("https://github.com/")) {
                                                val splitUrl = modifiedUrl.removePrefix("https://github.com/").split("/")
                                                val repoName = splitUrl[0] + "/" + splitUrl[1]
                                                okHttpClient.newCall(
                                                    okhttp3.Request.Builder().url("https://api.github.com/repos/$repoName").build()
                                                ).execute().use { response ->
                                                    if (!response.isSuccessful) {
                                                        throw Exception("Failed to fetch default branch: ${response.code}")
                                                    }
                                                    val json = response.body?.string() ?: throw Exception("Empty response")
                                                    val defaultBranch = Regex("\"default_branch\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                                                        ?: throw Exception("No default_branch field")
                                                    modifiedUrl = "https://raw.githubusercontent.com/$repoName/$defaultBranch/"
                                                }
                                            }

                                            val indexUrl = modifiedUrl.toUri().buildUpon().appendPath("index.json").build().toString()
                                            val request = okhttp3.Request.Builder().url(indexUrl).build()
                                            val isValid = okHttpClient.newCall(request).execute().use { response ->
                                                if (!response.isSuccessful) throw Exception("Failed to fetch index.json: ${response.code}")
                                                val indexJson = response.body?.string() ?: throw Exception("Empty index.json")
                                                JsonParser.parseString(indexJson).asJsonObject.has("scripts")
                                            }

                                            if (isValid) {
                                                context.database.addRepo("script", modifiedUrl)
                                                context.shortToast(translation["repo_added_toast"])
                                                showAddDialog = false
                                                refreshTrigger.value++
                                            } else {
                                                errorDialogMessage = translation["invalid_repo_error"]
                                                showErrorDialog = true
                                            }
                                        }.onFailure {
                                            context.log.error("Failed to add repository", it)
                                            context.shortToast(translation.format("add_repo_failed_toast", "message" to (it.message ?: context.translation["common.unknown"])))
                                        }
                                        loading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.3f),
                                    contentColor = Color.White
                                )
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text(translation["add_button"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val repositories by remember(refreshTrigger.value) {
            mutableStateOf<List<String>>(runBlocking { context.database.getRepositories("script") })
        }
        val density = LocalDensity.current
        val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        var topBarHeight by remember { mutableStateOf(statusBarTopPadding + 96.dp) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            FloatingTopBar(
                title = routeInfo.translatedKey?.value ?: translation["title"],
                onBack = { routes.navController.popBackStack() },
                modifier = Modifier
                    .zIndex(2f)
                    .onGloballyPositioned {
                        val newHeight = with(density) { it.size.height.toDp() }
                        if (newHeight != topBarHeight) topBarHeight = newHeight
                    }
            )
            if (repositories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AestheticEmptyState(
                        icon = Icons.Default.Public,
                        title = translation["no_repos_added"],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = topBarHeight + 12.dp,
                        end = 12.dp,
                        bottom = 18.dp + routes.bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repositories) { url ->
                        val (repoName, author) = remember(url) { extractRepoInfo(url) }
                        var showRemoveDialog by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = PurrfectPalette.cardOverlayColor,
                            tonalElevation = 0.dp,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                                        PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                                    )
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
                                ) {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp),
                                        tint = Color.White
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = repoName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = author,
                                        fontSize = 13.sp,
                                        color = PurrfectPalette.textSecondary
                                    )
                                }

                                Button(
                                    onClick = { showRemoveDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.22f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(translation["remove_button"])
                                }

                                AnimatedVisibility(visible = showRemoveDialog) {
                                    AestheticDialog(
                                        onDismissRequest = { showRemoveDialog = false },
                                        title = translation["remove_repo_dialog_title"],
                                        text = translation["remove_repo_dialog_text"],
                                        icon = Icons.Default.Error,
                                        confirmButtonText = translation["remove_button"],
                                        dismissButtonText = translation["button.cancel"],
                                        onDismiss = { showRemoveDialog = false },
                                        onConfirm = {
                                            context.database.removeRepo("script", url)
                                            showRemoveDialog = false
                                            refreshTrigger.value++
                                        },
                                        showCloseButton = false
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
