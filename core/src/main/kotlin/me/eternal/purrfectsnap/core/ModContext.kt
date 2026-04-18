package cock.crest.purrfectsnap.lite.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.MappingsWrapper
import cock.crest.purrfectsnap.lite.common.config.ModConfig
import cock.crest.purrfectsnap.lite.common.util.lazyBridge
import cock.crest.purrfectsnap.lite.core.action.ActionManager
import cock.crest.purrfectsnap.lite.core.bridge.BridgeClient
import cock.crest.purrfectsnap.lite.core.database.DatabaseAccess
import cock.crest.purrfectsnap.lite.core.event.EventBus
import cock.crest.purrfectsnap.lite.core.event.EventDispatcher
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.FeatureManager
import cock.crest.purrfectsnap.lite.core.features.impl.experiments.getCustomEmojiFontPath
import cock.crest.purrfectsnap.lite.core.logger.CoreLogger
import cock.crest.purrfectsnap.lite.core.messaging.CoreMessagingBridge
import cock.crest.purrfectsnap.lite.core.messaging.MessageSender
import cock.crest.purrfectsnap.lite.core.scripting.CoreScriptRuntime
import cock.crest.purrfectsnap.lite.core.ui.InAppOverlay
import cock.crest.purrfectsnap.lite.core.ui.UserInterface
import cock.crest.purrfectsnap.lite.core.util.media.HttpServer
import cock.crest.purrfectsnap.lite.nativelib.NativeConfig
import cock.crest.purrfectsnap.lite.nativelib.NativeLib
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext(
    val androidContext: Context,
    val purrfectSnap: PurrfectSnap
) {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var bridgeClient: BridgeClient
    var mainActivity: Activity? = null

    val classCache get() = PurrfectSnap.classCache
    val resources: Resources get() = androidContext.resources
    val gson: Gson = GsonBuilder().create()

    private val lazyFileHandlerManager = lazyBridge { bridgeClient.getFileHandlerManager() }
    val fileHandlerManager by lazyFileHandlerManager

    private val _config by lazy { ModConfig(androidContext, lazyFileHandlerManager) }
    val config get() = _config.root
    val log by lazy { CoreLogger(this.bridgeClient) }
    val translation by lazy { LocaleWrapper(androidContext, lazyFileHandlerManager) }
    val httpServer = HttpServer()
    val messageSender = MessageSender(this)

    val features = FeatureManager(this)
    val mappings by lazy { MappingsWrapper(lazyFileHandlerManager).apply { init(androidContext) } }
    val actionManager = ActionManager(this)
    val database = DatabaseAccess(this)
    val event = EventBus(this)
    val eventDispatcher = EventDispatcher(this)
    val native = NativeLib()
    val scriptRuntime by lazy { CoreScriptRuntime(this, log) }
    val messagingBridge = CoreMessagingBridge(this)
    val inAppOverlay = InAppOverlay(this)
    val userInterface = UserInterface(this)

    val isDeveloper by lazy { config.scripting.developerMode.get() }

    var isMainActivityPaused = true
    var disablePlugin = false

    fun <T : Feature> feature(featureClass: KClass<T>): T {
        return features.get(featureClass)!!
    }

    fun runOnUiThread(runnable: () -> Unit) {
        if (Looper.getMainLooper().isCurrentThread) {
            runnable()
            return
        }
        Handler(Looper.getMainLooper()).post {
            runCatching(runnable).onFailure {
                CoreLogger.xposedLog("UI thread runnable failed", it)
            }
        }
    }

    fun executeAsync(runnable: suspend ModContext.() -> Unit) {
        coroutineScope.launch {
            runCatching {
                runnable()
            }.onFailure {
                longToast(translation.format("toast_async_task_failed", "message" to (it.message ?: "")))
                log.error("Async task failed", it)
            }
        }
    }

    fun shortToast(message: Any?) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun longToast(message: Any?) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
    }

    fun softRestartApp(saveSettings: Boolean = false) {
        if (saveSettings) {
            _config.writeConfig()
        }
        val intent: Intent? = androidContext.packageManager.getLaunchIntentForPackage(
            Constants.SNAPCHAT_PACKAGE_NAME
        )
        intent?.let {
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            androidContext.startActivity(mainIntent)
        }
        exitProcess(1)
    }

    fun crash(message: String, throwable: Throwable? = null) {
        logCritical(message, throwable ?: Throwable())
        delayForceCloseApp(100)
    }

    fun logCritical(message: Any?, throwable: Throwable = Throwable()) {
        log.error(message ?: "Snapchat crash", throwable)
        longToast(message ?: translation["toast_snapchat_crashed"])
    }

    private fun delayForceCloseApp(delay: Long) = Handler(Looper.getMainLooper()).postDelayed({
        forceCloseApp()
    }, delay)

    fun forceCloseApp() {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    fun reloadConfig() {
        log.verbose("reloading config")
        _config.load()
        reloadNativeConfig()
    }

    fun reloadNativeConfig() {
        native.loadNativeConfig(
            NativeConfig(
                disableBitmoji = config.experimental.nativeHooks.disableBitmoji.get(),
                disableMetrics = config.global.disableMetrics.get(),
                valdiHooks = config.experimental.nativeHooks.valdiHooks.globalState == true &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                customEmojiFontPath = getCustomEmojiFontPath(this),
                debugFontRedirect = config.experimental.nativeHooks.debugFontRedirect.get()
                )
                )    }

    fun getConfigLocale(): String {
        return _config.locale
    }

    fun isLoggedIn() = androidContext.getSharedPreferences("user_session_shared_pref", 0).getString("key_user_id", null) != null
}
