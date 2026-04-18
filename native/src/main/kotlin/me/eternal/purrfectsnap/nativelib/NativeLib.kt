package cock.crest.purrfectsnap.lite.nativelib

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.absoluteValue
import kotlin.random.Random

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}
    var signatureCache: String? = null

    companion object {
        var initialized = false
            private set
        private var libraryLoaded = false

        private fun findNativeLibraryDirs(): List<File> {
            val app = runCatching {
                val cls = Class.forName("android.app.ActivityThread")
                val method = cls.getMethod("currentApplication")
                method.invoke(null) as? android.app.Application
            }.getOrNull() ?: return emptyList()

            val dirs = mutableListOf<File>()
            val moduleDir = runCatching {
                app.createPackageContext(BuildConfig.MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
                    .applicationInfo.nativeLibraryDir
            }.getOrNull()
            moduleDir?.let { dirs.add(File(it)) }

            app.applicationInfo.nativeLibraryDir?.let { dirs.add(File(it)) }

            return dirs.distinctBy { it.absolutePath }
        }

        private fun tryLoadFromNativeDir(): Boolean {
            val dirs = findNativeLibraryDirs().filter { it.isDirectory }
            if (dirs.isEmpty()) return false

            for (dir in dirs) {
                val candidates = listOf(
                    "lib${BuildConfig.NATIVE_NAME}.so",
                    "libpurrfectsnap.so"
                ).map { File(dir, it) }

                val fallback = dir.listFiles()
                    ?.firstOrNull { it.name.startsWith("lib") && it.name.endsWith(".so") && it.name.contains("purrfectsnap") }

                val ordered = buildList<File> {
                    addAll(candidates)
                    fallback?.let { if (!contains(it)) add(it) }
                }

                for (file in ordered) {
                    if (!file.exists()) continue
                    val ok = runCatching {
                        System.load(file.absolutePath)
                        libraryLoaded = true
                        true
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            return false
        }

        fun tryEnsureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true

            val candidates = linkedSetOf(
                BuildConfig.NATIVE_NAME,
                // Some repackagers restore the original Cargo output name.
                "purrfectsnap",
            ).filter { it.isNotBlank() }

            var lastError: Throwable? = null
            for (name in candidates) {
                val ok = runCatching {
                    System.loadLibrary(name)
                    libraryLoaded = true
                    true
                }.onFailure { lastError = it }.getOrDefault(false)
                if (ok) return true
            }

            if (tryLoadFromNativeDir()) return true

            Log.e(
                "PurrfectSnap",
                "Failed to load native library (tried: ${candidates.joinToString()})",
                lastError
            )
            return false
        }

        fun ensureLibraryLoaded() {
            if (!tryEnsureLibraryLoaded()) {
                throw UnsatisfiedLinkError("Failed to load native library: ${BuildConfig.NATIVE_NAME}")
            }
        }
    }

    fun initOnce(callback: NativeLib.() -> Unit): () -> Unit {
        if (initialized) throw IllegalStateException("NativeLib already initialized")
        return runCatching {
            ensureLibraryLoaded()
            initialized = true
            callback(this)
            preInit()
            setChecksums(com.google.gson.Gson().toJson(Checksums.checksums))
            return@runCatching {
                signatureCache = init(signatureCache) ?: throw IllegalStateException("NativeLib init failed. Check logcat for more info")
            }
        }.onFailure {
            initialized = false
            Log.e("PurrfectSnap", "NativeLib init failed", it)
        }.getOrThrow()
    }

    @Suppress("unused")
    private fun onNativeUnaryCall(uri: String, buffer: ByteArray): NativeRequestData? {
        val nativeRequestData = NativeRequestData(uri, buffer)
        runCatching {
            nativeUnaryCallCallback(nativeRequestData)
        }.onFailure {
            Log.e("PurrfectSnap", "nativeUnaryCallCallback failed", it)
        }
        if (nativeRequestData.canceled || !nativeRequestData.buffer.contentEquals(buffer)) return nativeRequestData
        return null
    }

    fun loadNativeConfig(config: NativeConfig) {
        if (!initialized) return
        loadConfig(config)
    }

    fun lockNativeDatabase(name: String, callback: () -> Unit) {
        if (!initialized) return
        lockDatabase(name) {
            runCatching {
                callback()
            }.onFailure {
                Log.e("PurrfectSnap", "lockNativeDatabase callback failed", it)
            }
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadSharedLibrary(content: ByteArray) {
        if (!initialized) throw IllegalStateException("NativeLib not initialized")
        val generatedPath = "/data/app/${Random.nextLong().absoluteValue.toString(16)}.so"
        addLinkerSharedLibrary(generatedPath, content)
        System.load(generatedPath)
    }

    private external fun preInit()
    private external fun init(signatureCache: String?): String?
    private external fun loadConfig(config: NativeConfig)
    private external fun lockDatabase(name: String, callback: Runnable)
    external fun setValdiLoader(code: String)
    private external fun addLinkerSharedLibrary(path: String, content: ByteArray)
    private external fun evaluateEndpointNative(uri: String, arg0: String, hasAttestation: Boolean, outDecision: NativeDecision)
    private external fun evaluateNetworkRequestNative(url: String, outDecision: NativeDecision)
    external fun shouldBlockDuplexClient(path: String): Boolean
    private external fun evaluateAuthContextNative(requestPath: String, attestationRequired: Boolean, outDecision: NativeDecision)
    private external fun evaluateApiInvocationNative(methodId: String, annotations: String, outDecision: NativeDecision)
    private external fun runEndpointSelfTest(testMode: Boolean): Boolean
    private external fun setChecksums(checksums: String)
    external fun setTestMode(testMode: Boolean)
    external fun setInLoginSignup(inLoginSignup: Boolean)


    fun evaluateEndpoint(uri: String, arg0: String, hasAttestation: Boolean): NativeDecision {
        return NativeDecision().also { evaluateEndpointNative(uri, arg0, hasAttestation, it) }
    }

    fun evaluateNetworkRequest(url: String): NativeDecision {
        return NativeDecision().also { evaluateNetworkRequestNative(url, it) }
    }

    fun evaluateAuthContext(requestPath: String, attestationRequired: Boolean): NativeDecision {
        return NativeDecision().also { evaluateAuthContextNative(requestPath, attestationRequired, it) }
    }

    fun evaluateApiInvocation(methodId: String, annotations: String): NativeDecision {
        return NativeDecision().also { evaluateApiInvocationNative(methodId, annotations, it) }
    }

    fun isEndpointBlockerHealthy(testMode: Boolean = false): Boolean {
        if (!initialized) return false
        return runEndpointSelfTest(testMode)
    }
}
