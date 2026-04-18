package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.data.FileType
import cock.crest.purrfectsnap.lite.common.ui.AppMaterialTheme
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.ktx.toParcelFileDescriptor
import cock.crest.purrfectsnap.lite.common.util.snap.MediaDownloaderHelper
import cock.crest.purrfectsnap.lite.core.event.events.impl.ActivityResultEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.CustomComposable
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getId
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class AccountSwitcher: Feature("Account Switcher") {
    private val translation by lazy { context.translation.getCategory("account_switcher_ui") }
    private var exportCallback: Pair<Int, String>? = null // requestCode -> userId
    private var importRequestCode: Int? = null

    private val accounts = mutableStateListOf<Pair<String, String>>()
    private val isLoginActivity get() = context.mainActivity?.javaClass?.name?.endsWith("LoginSignupActivity") == true

    private fun updateUsers() {
        accounts.clear()
        runCatching {
            accounts.addAll(context.bridgeClient.getAccountStorage().accounts.map { it.key to it.value })
        }.onFailure {
            context.log.error("Failed to update users", it)
        }
    }

    @Composable
    private fun ManagementPopup() {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                updateUsers()
            }
        }


        Column(
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Account Switcher", modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 25.sp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    if (accounts.isEmpty()) {
                        Text("No accounts found! To start, backup your current account.", modifier = Modifier
                            .padding(16.dp)
                            .padding(16.dp)
                            .fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

                items(accounts) { user ->
                    var removeAccountPopup by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isLoginActivity && context.database.myUserId == user.first) MaterialTheme.colorScheme.surfaceBright
                            else MaterialTheme.colorScheme.surfaceDim
                        ) ,
                        onClick = {
                            runCatching {
                                if (!isLoginActivity && context.database.myUserId == user.first) {
                                    context.shortToast(
                                        translation.format("already_logged_in", "username" to user.second)
                                    )
                                    return@runCatching
                                }

                                if (!isLoginActivity && context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                                    backupCurrentAccount()
                                }

                                login(userId = user.first, username = user.second)
                            }.onFailure {
                                context.shortToast(translation["login_failed_toast"])
                                context.log.error("Failed to login", it)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.second, modifier = Modifier
                                .padding(10.dp)
                                .weight(1f))
                            Row(
                                modifier = Modifier
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                FilledIconButton(onClick = {
                                    val requestCode = Random.nextInt(100, 65535)
                                    exportCallback = requestCode to user.first

                                    context.mainActivity?.startActivityForResult(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_TITLE, "account_${user.second}.zip")
                                            },
                                            "Export account"
                                        ),
                                        requestCode
                                    )
                                }) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = "Export account")
                                }
                                FilledIconButton(onClick = {
                                    removeAccountPopup = true
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove account")
                                }
                            }
                        }
                    }

                    if (removeAccountPopup) {
                        AlertDialog(
                            onDismissRequest = { removeAccountPopup = false },
                            confirmButton = {
                                Button(onClick = {
                                    context.bridgeClient.getAccountStorage().removeAccount(user.first)
                                    removeAccountPopup = false
                                    updateUsers()
                                }) {
                                    Text("Remove")
                                }
                            },
                            title = { Text("Remove account") },
                            text = { Text("Are you sure you want to remove ${user.second}?") },
                            dismissButton = {
                                Button(onClick = {
                                    removeAccountPopup = false
                                }) {
                                    Text("Cancel")
                                }
                            },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.mainActivity?.startActivityForResult(
                            Intent.createChooser(
                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/zip"
                                },
                                "Import account"
                            ),
                            Random.nextInt(100, 65535).also {
                                importRequestCode = it
                            }
                        )
                    }
                ) {
                    Text("Import account")
                }

                if (!isLoginActivity) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            backupCurrentAccount()
                            updateUsers()
                        }
                    ) {
                        Text("Backup current account")
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            if (context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                                backupCurrentAccount()
                            }
                            logout()
                        }
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
    }


    private fun showManagementPopup() {
        context.runOnUiThread {
            createComposeAlertDialog(context.mainActivity!!) {
                AppMaterialTheme(isDarkTheme = true) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        ManagementPopup()
                    }
                }
            }.show()
        }
    }

    private fun logout() {
        context.androidContext.dataDir.resolve( "shared_prefs/user_session_shared_pref.xml").takeIf { it.exists() }?.delete()
        context.shortToast(translation["logged_out_toast"])
        context.softRestartApp()
    }

    private fun login(userId: String, username: String) {
        val accountData = context.bridgeClient.getAccountStorage().getAccountData(userId)?.let { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
        if (accountData == null) {
            context.shortToast(translation["data_not_found_toast"])
            return
        }

        val dataDir = context.androidContext.dataDir
        arrayOf(
            context.androidContext.filesDir,
            context.androidContext.cacheDir,
            dataDir.resolve("databases"),
            dataDir.resolve("shared_prefs"),
        ).forEach { dir -> 
            runCatching {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { it.deleteRecursively() }
                }
            }.onFailure {
                context.log.verbose("Failed to clean directory $dir: ${it.message}")
            }
        }

        try {
            val zipInputStream = ZipInputStream(accountData.inputStream())
            var entry: ZipEntry?
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                runCatching {
                    val entryName = entry!!.name
                    val file = dataDir.resolve(entryName)
                    
                    if (file.exists()) {
                        file.delete()
                    } else {
                        file.parentFile?.mkdirs()
                    }
                    context.log.debug("Extracting ${file.absolutePath}")
                    file.outputStream().buffered().use {
                        zipInputStream.copyTo(it, bufferSize = 8192)
                    }
                }.onFailure {
                    context.log.verbose("Failed to extract ${entry!!.name}: ${it.message}")
                }
            }
            zipInputStream.close()
        } catch (e: Exception) {
            context.log.error("Failed to restore account data", e)
            context.shortToast(translation["restore_failed_toast"])
            return
        }

        context.log.debug("Account data restored")
        context.shortToast(translation.format("logged_in_as_toast", "username" to username))
        context.softRestartApp()
    }

    private fun getCurrentAccountData(): ParcelFileDescriptor {
        val pfd = ParcelFileDescriptor.createPipe()

        context.coroutineScope.launch(Dispatchers.IO) {
            var zipOutputStream: ZipOutputStream? = null
            try {
                zipOutputStream = ZipOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pfd[1]))
                val dataDir = context.androidContext.dataDir
                val maxFileSize = 5 * 1024 * 1024

                fun addFile(path: String, file: File): Boolean {
                    return try {
                        if (!file.exists() || !file.isFile || !file.canRead()) return false
                        val fileSize = file.length()
                        if (fileSize > maxFileSize || fileSize == 0L) return false
                        file.inputStream().buffered().use { input ->
                            zipOutputStream!!.putNextEntry(ZipEntry(path))
                            input.copyTo(zipOutputStream!!, bufferSize = 8192)
                            zipOutputStream!!.closeEntry()
                        }
                        true
                    } catch (e: Exception) {
                        context.log.verbose("Failed to add file $file: ${e.message}")
                        false
                    }
                }

                val databasesDir = dataDir.resolve("databases")
                if (databasesDir.exists() && databasesDir.isDirectory) {
                    databasesDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".db") && !file.name.endsWith(".db-wal") && !file.name.endsWith(".db-shm")) {
                            addFile("databases/${file.name}", file)
                        }
                    }
                }

                val sharedPrefsDir = dataDir.resolve("shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".xml") && file.canRead()) {
                            addFile("shared_prefs/${file.name}", file)
                        }
                    }
                }

                zipOutputStream.flush()
                zipOutputStream.close()
                zipOutputStream = null
            } catch (e: Exception) {
                context.log.error("Backup failed", e)
                try {
                    zipOutputStream?.close()
                } catch (_: Exception) {}
                try {
                    pfd[1].close()
                } catch (_: Exception) {}
            }
        }

        return pfd[0]
    }

    private fun backupCurrentAccount() {
        runCatching {
            context.bridgeClient.getAccountStorage().addAccount(
                context.database.myUserId,
                context.database.getFriendInfo(context.database.myUserId)?.mutableUsername ?: "Unknown username",
                getCurrentAccountData()
            )
            context.shortToast(translation["backup_success_toast"])
        }.onFailure {
            context.shortToast(translation["backup_failure_toast"])
            context.log.error("Failed to backup account", it)
        }
    }

    private fun importAccount(fileUri: Uri) {
        var tempZip: File? = null
        var mainDbFile: File? = null
        var mainDbWalFile: File? = null
        var mainDbShmFile: File? = null

        runCatching {
            // copy zip file
            context.mainActivity!!.contentResolver.openInputStream(fileUri)?.use { input ->
                val bufferedInputStream = input.buffered()
                val fileType = MediaDownloaderHelper.getFileType(bufferedInputStream)

                if (fileType != FileType.ZIP) {
                    throw Exception("Invalid file type")
                }

                context.androidContext.cacheDir.resolve(System.currentTimeMillis().toString()).also {
                    tempZip = it
                }.outputStream().use { output ->
                    bufferedInputStream.copyTo(output)
                }
            }

            context.log.verbose("Extracting account data")

            // extract main.db in cache
            tempZip?.inputStream().use { fileInputStream ->
                val zipInputStream = ZipInputStream(fileInputStream)
                var entry: ZipEntry?
                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    val fileName = entry?.name?.substringAfterLast('/') ?: continue
                    if (!fileName.startsWith("main.db")) continue

                    val file = context.androidContext.cacheDir.resolve(fileName)
                    context.log.verbose("Found ${entry!!.name} in zip file")

                    when (fileName) {
                        "main.db" -> mainDbFile = file
                        "main.db-wal" -> mainDbWalFile = file
                        "main.db-shm" -> mainDbShmFile = file
                    }

                    file.outputStream().use {
                        zipInputStream.copyTo(it)
                    }
                }
            }

            assert(mainDbFile != null) { "main.db not found in zip file" }

            SQLiteDatabase.openDatabase(mainDbFile!!.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {  sqliteDatabase ->
                val userId = sqliteDatabase.rawQuery("SELECT userId FROM SnapToken", null).use {
                    if (!it.moveToFirst()) throw Exception("userId not found in main.db")
                    it.getString(0)
                }
                context.log.verbose("Found userId $userId")
                val username = sqliteDatabase.rawQuery("SELECT username FROM Friend WHERE userId = ?", arrayOf(userId)).use {
                    if (!it.moveToFirst()) throw Exception("username not found in main.db")
                    it.getString(0)
                }
                context.log.verbose("Found username $username")
                tempZip?.inputStream()?.use {
                    context.bridgeClient.getAccountStorage().addAccount(
                        userId,
                        username,
                        it.toParcelFileDescriptor(context.coroutineScope)
                    )
                }
                context.shortToast(translation.format("import_success_toast", "username" to username))
                updateUsers()
            }
        }.onFailure {
            context.shortToast(
                translation.format("import_failure_toast", "message" to (it.message ?: ""))
            )
            context.log.error("Failed to import account", it)
        }

        tempZip?.delete()
        mainDbFile?.delete()
        mainDbWalFile?.delete()
        mainDbShmFile?.delete()
    }

    @SuppressLint("SetTextI18n")
    override fun init() {
        if (context.config.experimental.accountSwitcher.globalState != true) return

        onNextActivityCreate {
            val hovaHeaderSearchIcon = context.resources.getId("hova_header_search_icon")

            context.event.subscribe(AddViewEvent::class) { event ->
                if (event.view.id != hovaHeaderSearchIcon) return@subscribe

                event.view.setOnLongClickListener {
                    context.mainActivity!!.vibrateLongPress()
                    showManagementPopup()
                    false
                }
            }
        }

        context.event.subscribe(ActivityResultEvent::class) { event ->
            if (importRequestCode == event.requestCode) {
                importRequestCode = null
                if (event.resultCode != Activity.RESULT_OK) return@subscribe
                event.canceled = true
                val uri = event.intent.data ?: return@subscribe

                context.coroutineScope.launch { importAccount(uri) }
            }

            if (exportCallback?.first == event.requestCode) {
                val userId = exportCallback?.second
                exportCallback = null
                event.canceled = true
                if (event.resultCode != Activity.RESULT_OK) return@subscribe

                context.coroutineScope.launch {
                    runCatching {
                        event.intent.data?.let { uri ->
                            val accountDataPfd = context.bridgeClient.getAccountStorage().getAccountData(userId) ?: throw Exception("Account data not found")
                            context.androidContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                ParcelFileDescriptor.AutoCloseInputStream(accountDataPfd).use {
                                    it.copyTo(outputStream)
                                }
                            }
                            context.shortToast(translation["export_success_toast"])
                        }
                    }.onFailure {
                        context.shortToast(translation["export_failed_toast"])
                        context.log.error("Failed to export account", it)
                    }
                }
            }
        }

        findClass("com.snap.identity.service.ForcedLogoutBroadcastReceiver").hook("onReceive", HookStage.BEFORE) { param ->
            val intent = param.arg<Intent>(1)
            if (isLoginActivity) return@hook
            if (intent.getBooleanExtra("forced", false) && !context.config.experimental.preventForcedLogout.get()) {
                runCatching {
                    val accountStorage = context.bridgeClient.getAccountStorage()

                        if (accountStorage.isAccountExists(context.database.myUserId)) {
                            accountStorage.removeAccount(context.database.myUserId)
                            context.shortToast(translation["forced_logout_toast"])
                        }
                }
                return@hook
            }

            if (context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                backupCurrentAccount()
            }
        }

        val switchButtonComposable: CustomComposable = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { showManagementPopup() }
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Switch Account",
                        modifier = Modifier.size(45.dp)
                    )
                }
            }
        }

        onNextActivityCreate { activity ->
            if (!activity.componentName.className.endsWith("LoginSignupActivity")) return@onNextActivityCreate
            context.inAppOverlay.addCustomComposable(switchButtonComposable)
            onNextActivityCreate {
                context.inAppOverlay.removeCustomComposable(switchButtonComposable)
            }
        }
    }
}
