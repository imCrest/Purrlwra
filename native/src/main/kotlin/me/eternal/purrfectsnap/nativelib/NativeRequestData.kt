package cock.crest.purrfectsnap.lite.nativelib

class NativeRequestData(
    val uri: String,
    var buffer: ByteArray,
    var canceled: Boolean = false,
)