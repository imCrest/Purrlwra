package cock.crest.purrfectsnap.lite.setup.patch

data class PatchConfig(
    val useManager: Boolean = false,
    val debuggable: Boolean = false,
    val overrideVersionCode: Boolean = false,
    val sigBypassLevel: Int = 0,
    val originalSignature: String? = null,
    val appComponentFactory: String? = null,
    val lspConfig: LSPConfig? = LSPConfig()
) {
    data class LSPConfig(
        var API_CODE: Int = 93,
        var VERSION_CODE: Int = 430,
        var VERSION_NAME: String = "0.7",
        var CORE_VERSION_CODE: Int = 7137,
        var CORE_VERSION_NAME: String = "1.10.1",
    )
}
