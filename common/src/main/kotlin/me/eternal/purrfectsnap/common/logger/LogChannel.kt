package cock.crest.purrfectsnap.lite.common.logger

enum class LogChannel(
    val channel: String,
    val shortName: String
) {
    CORE("PurrfectSnapCore", "core"),
    COMMON("PurrfectSnapCommon", "common"),
    SCRIPTING("Scripting", "scripting"),
    NATIVE("PurrfectSnapNative", "native"),
    MANAGER("PurrfectSnapManager", "manager"),
    XPOSED("LSPosed-Bridge", "xposed");

    companion object {
        fun fromChannel(channel: String): LogChannel? {
            return entries.find { it.channel == channel }
        }
    }
}