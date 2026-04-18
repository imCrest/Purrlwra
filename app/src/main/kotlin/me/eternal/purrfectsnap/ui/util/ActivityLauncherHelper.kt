package cock.crest.purrfectsnap.lite.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger

typealias ActivityLauncherCallback = (resultCode: Int, intent: Intent?) -> Unit

class ActivityLauncherHelper(
    val activity: ComponentActivity,
) {
    private var callback: ActivityLauncherCallback? = null
    private var permissionResultLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            runCatching {
                callback?.let { it(if (result) Activity.RESULT_OK else Activity.RESULT_CANCELED, null) }
            }.onFailure {
                AbstractLogger.directError("Failed to process activity result", it)
            }
            callback = null
        }

    private var activityResultLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            runCatching {
                callback?.let { it(result.resultCode, result.data) }
            }.onFailure {
                AbstractLogger.directError("Failed to process activity result", it)
            }
            callback = null
        }

    fun launch(intent: Intent, callback: ActivityLauncherCallback) {
        launch(intent, callback, null)
    }

    fun launch(intent: Intent, callback: ActivityLauncherCallback, onFailure: ((Throwable) -> Unit)?) {
        if (this.callback != null) {
            val error = IllegalStateException("Already launching an activity")
            AbstractLogger.directError("Ignored concurrent activity launch", error)
            onFailure?.invoke(error)
            return
        }
        this.callback = callback

        runCatching {
            activityResultLauncher.launch(intent)
        }.onFailure { throwable ->
            AbstractLogger.directError("Failed to launch intent", throwable)
            onFailure?.invoke(throwable)
            runCatching { callback(Activity.RESULT_CANCELED, null) }
            this.callback = null
        }
    }

    fun requestPermission(permission: String, callback: ActivityLauncherCallback) {
        if (this.callback != null) {
            AbstractLogger.directError(
                "Ignored concurrent permission request",
                IllegalStateException("Already launching an activity")
            )
            return
        }
        this.callback = callback
        permissionResultLauncher.launch(permission)
    }

    fun canLaunch(intent: Intent): Boolean = intent.resolveActivity(activity.packageManager) != null
}

fun ActivityLauncherHelper.chooseFolder(onUnavailable: (() -> Unit)? = null, callback: (uri: String) -> Unit) {
    launch(
        intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),
        callback = { resultCode, intent ->
            if (resultCode != Activity.RESULT_OK) {
                callback("")
                return@launch
            }
            val uri = intent?.data ?: return@launch
            val value = uri.toString()
            runCatching {
                this.activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                callback(value)
            }.onFailure {
                AbstractLogger.directError("Failed to persist folder permission", it)
                callback("")
            }
        },
        onFailure = { throwable ->
            if (throwable is ActivityNotFoundException) {
                onUnavailable?.invoke()
            }
            callback("")
        }
    )
}

fun ActivityLauncherHelper.saveFile(name: String, type: String = "*/*", callback: (uri: String) -> Unit) {
    launch(
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    ) { resultCode, intent ->
        if (resultCode != Activity.RESULT_OK) {
            return@launch
        }
        val uri = intent?.data ?: return@launch
        val value = uri.toString()
        this.activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        callback(value)
    }
}
fun ActivityLauncherHelper.openFile(type: String = "*/*", callback: (uri: String) -> Unit) {
    launch(
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    ) { resultCode, intent ->
        if (resultCode != Activity.RESULT_OK) {
            return@launch
        }
        val uri = intent?.data ?: return@launch
        val value = uri.toString()
        this.activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        callback(value)
    }
}
