package cock.crest.purrfectsnap.lite.nativelib

/**
 * Configuration schema for the native layer.
 * 
 * CRITICAL: This class MUST maintain 1:1 field parity with 'native/rust/src/config.rs'.
 * Any modification to field names, types, or order without a corresponding change
 * in the Rust implementation will cause a JNI SIGABRT (crash on launch).
 */
data class NativeConfig(
    @JvmField
    val disableBitmoji: Boolean = false,
    @JvmField
    val disableMetrics: Boolean = false,
    @JvmField
    val valdiHooks: Boolean = false,
    @JvmField
    val customEmojiFontPath: String? = null,
    @JvmField
    val debugFontRedirect: Boolean = false,
)
