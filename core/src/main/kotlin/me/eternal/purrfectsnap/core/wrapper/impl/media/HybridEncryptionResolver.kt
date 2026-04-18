package cock.crest.purrfectsnap.lite.core.wrapper.impl.media

import android.util.Base64
import cock.crest.purrfectsnap.lite.common.data.download.MediaEncryptionKeyPair
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object HybridEncryptionResolver {

    fun resolve(
        mediaInfo: MediaInfo,
        storyKeyPair: MediaEncryptionKeyPair?
    ): MediaEncryptionKeyPair? {

        // ────OLD WRAPPER (images/snaps)───────────────────
        val wrapperPair = runCatching {
            mediaInfo.encryption?.toKeyPair()
        }.getOrNull()

        if (wrapperPair != null) {
            return wrapperPair
        }

        // ────DIRECT MEDIAINFO KEYPAIR────────────────
        val directPair = runCatching {
            mediaInfo.encryption?.toKeyPairUrlSafe()
        }.getOrNull()

        if (directPair != null) {
            return directPair
        }

        // ────STORY PARAMMAP AES─────────────────
        if (storyKeyPair != null) {
            return storyKeyPair
        }

        // ────NONE FOUND─────────────────
        return null
    }

    // Optional cipher builder if needed elsewhere
    fun buildCipher(pair: MediaEncryptionKeyPair): Cipher {
        val key = Base64.decode(pair.key, Base64.DEFAULT)
        val iv  = Base64.decode(pair.iv, Base64.DEFAULT)

        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv)
            )
        }
    }
}
