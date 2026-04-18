package cock.crest.purrfectsnap.lite.common.bridge

import cock.crest.purrfectsnap.lite.bridge.storage.FileHandleManager
import cock.crest.purrfectsnap.lite.common.util.LazyBridgeValue
import cock.crest.purrfectsnap.lite.common.util.lazyBridge

open class InternalFileWrapper(
    fileHandleManager: LazyBridgeValue<FileHandleManager>,
    private val fileType: InternalFileHandleType,
    val defaultValue: String? = null
): FileHandleWrapper(lazyBridge { fileHandleManager.value.getFileHandle(FileHandleScope.INTERNAL.key, fileType.key)!! }) {
    override fun readBytes(): ByteArray {
        if (!exists()) {
            defaultValue?.toByteArray(Charsets.UTF_8)?.let {
                writeBytes(it)
                return it
            }
        }
        return super.readBytes()
    }
}