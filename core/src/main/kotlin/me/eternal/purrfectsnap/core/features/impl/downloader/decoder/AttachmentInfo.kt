package cock.crest.purrfectsnap.lite.core.features.impl.downloader.decoder

import cock.crest.purrfectsnap.lite.common.data.download.MediaEncryptionKeyPair

data class BitmojiSticker(
    val reference: String,
) : AttachmentInfo()

open class AttachmentInfo(
    val encryption: MediaEncryptionKeyPair? = null,
    val resolution: Pair<Int, Int>? = null,
    val duration: Long? = null
) {
    override fun toString() = "AttachmentInfo(encryption=$encryption, resolution=$resolution, duration=$duration)"
}