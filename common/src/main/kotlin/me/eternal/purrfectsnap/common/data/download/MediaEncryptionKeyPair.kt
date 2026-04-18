
package cock.crest.purrfectsnap.lite.common.data.download

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class MediaEncryptionKeyPair(
    val key: String,
    val iv: String,
    val urlSafe: Boolean = true
) {
    @OptIn(ExperimentalEncodingApi::class)
    fun decryptInputStream(inputStream: InputStream): InputStream {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(if (urlSafe) Base64.UrlSafe.decode(key) else Base64.Default.decode(key), "AES"),
            IvParameterSpec(if (urlSafe) Base64.UrlSafe.decode(iv) else Base64.Default.decode(iv))
        )
        return CipherInputStream(inputStream, cipher)
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun Pair<ByteArray, ByteArray>.toKeyPair()
    = MediaEncryptionKeyPair(Base64.UrlSafe.encode(this.first), Base64.UrlSafe.encode(this.second))
