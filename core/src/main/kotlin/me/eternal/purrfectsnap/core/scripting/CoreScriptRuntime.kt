package cock.crest.purrfectsnap.lite.core.scripting

import cock.crest.purrfectsnap.lite.common.scripting.JSModule
import cock.crest.purrfectsnap.lite.bridge.scripting.AutoReloadListener
import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger
import cock.crest.purrfectsnap.lite.common.scripting.ScriptRuntime
import cock.crest.purrfectsnap.lite.common.scripting.bindings.BindingSide
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.scripting.impl.*

/**
 * Core-side implementation of the [ScriptRuntime].
 * Manages script lifecycle synchronized with the JNI bridge connection state
 * to prevent race conditions during early-init hooks.
 */
class CoreScriptRuntime(
    private val modContext: ModContext,
    logger: AbstractLogger,
): ScriptRuntime(
    config = { modContext.config },
    androidContext = modContext.androidContext,
    logger = logger
) {
    // Indicates if the bridge has been reloaded at least once in this session
    private var isBridgeReloaded = false

    /**
     * Bridge connection status. Use [isBridgeConnected] to guard JNI-dependent operations.
     */
    @Volatile
    private var isBridgeConnected = false

    /**
     * Initializes the scripting environment and establishes bridge-aware lifecycle observers.
     */
    fun init() {
        buildModuleObject = { module ->
            putConst("currentSide", this, BindingSide.CORE.key)
            module.registerBindings(
                CoreScriptConfig(),
                CoreIPC(),
                CoreScriptHooker(),
                CoreMessaging(modContext),
                CoreEvents(modContext),
            )
        }

        modContext.bridgeClient.addOnConnectedCallback(initNow = true) {
            modContext.bridgeClient.getScriptingInterface()?.let { scriptingInterface ->
                logger.info("JNI Bridge established. Initializing scripts...")
                scripting = scriptingInterface
                isBridgeConnected = true

                if (!isBridgeReloaded) {
                    scriptingInterface.enabledScripts.forEach { path ->
                        runCatching {
                            logger.verbose("Loading script: $path")
                            load(path, scriptingInterface.getScriptContent(path))
                        }.onFailure {
                            logger.error("Failed to load script $path", it)
                        }
                    }
                }

                scriptingInterface.registerAutoReloadListener(object : AutoReloadListener.Stub() {
                    override fun restartApp() {
                        logger.info("Script change detected. Soft-restarting app...")
                        modContext.softRestartApp()
                    }
                })

                eachModule {
                    onBridgeConnected(reloaded = isBridgeReloaded)
                }

                if (!isBridgeReloaded) {
                    isBridgeReloaded = true
                }
            } ?: run {
                isBridgeConnected = false
                logger.error("JNI Bridge callback triggered but interface is null.")
            }
        }
    }

    /**
     * Safely iterates over loaded modules only when the JNI bridge is confirmed connected.
     */
    override fun eachModule(f: JSModule.() -> Unit) {
        if (!isBridgeConnected) return
        super.eachModule(f)
    }
}
