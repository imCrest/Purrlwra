package cock.crest.purrfectsnap.lite.core.wrapper.impl.media

import android.util.Base64
import android.util.Log
import cock.crest.purrfectsnap.lite.common.data.download.MediaEncryptionKeyPair
import cock.crest.purrfectsnap.lite.core.util.ktx.findFieldNamesByType
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Cipher mode enum

enum class SnapCipherMode {
    CBC,
    CTR
}

// Encryption Wrapper

class EncryptionWrapper(
    instance: Any? = null,
    private val mode: SnapCipherMode = SnapCipherMode.CBC
) : AbstractWrapper(instance) {

    // Manual key injection (story fallback)

    private var manualKey: ByteArray? = null
    private var manualIv: ByteArray? = null

    constructor(
        key: ByteArray,
        iv: ByteArray,
        mode: SnapCipherMode = SnapCipherMode.CBC
    ) : this(null, mode) {
        manualKey = key
        manualIv = iv
    }

    // Key + IV extraction

    val keySpec: ByteArray by lazy {
        manualKey ?: searchByteArrayField(32)?.let { instanceNonNull().getObjectField(it) as? ByteArray }
        ?: throw NoSuchFieldException("Failed to find 32-byte key field")
    }

    val ivKeyParameterSpec: ByteArray by lazy {
        manualIv ?: searchByteArrayField(16)?.let { instanceNonNull().getObjectField(it) as? ByteArray }
        ?: throw NoSuchFieldException("Failed to find 16-byte IV field")
    }

    private fun searchByteArrayField(length: Int): String? {
        val fields = instanceNonNull().findFieldNamesByType(ByteArray::class.java)
        return fields.firstOrNull { fieldName ->
            (instanceNonNull().getObjectField(fieldName) as? ByteArray)?.size == length
        }
    }

    // Cipher builders

    /** Chat media */
    private fun buildCBCCipherPKCS5(mode: Int): Cipher {
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(
                mode,
                SecretKeySpec(keySpec, "AES"),
                IvParameterSpec(ivKeyParameterSpec)
            )
        }
    }

    /** Story images */
    private fun buildCBCCipherNoPadding(mode: Int): Cipher {
        return Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(
                mode,
                SecretKeySpec(keySpec, "AES"),
                IvParameterSpec(ivKeyParameterSpec)
            )
        }
    }

    /** Videos + DASH */
    private fun buildCTRCipher(mode: Int): Cipher {
        return Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                mode,
                SecretKeySpec(keySpec, "AES"),
                IvParameterSpec(ivKeyParameterSpec)
            )
        }
    }

    // Generic decrypt (auto detect)

    fun decrypt(data: ByteArray): ByteArray {

        Log.d(
            "PurrfectSnap",
            "Decrypt → keySize=${keySpec.size} ivSize=${ivKeyParameterSpec.size}"
        )

        return try {
            buildCBCCipherPKCS5(Cipher.DECRYPT_MODE).doFinal(data)

        } catch (cbcError: Exception) {

            Log.d("PurrfectSnap", "PKCS5 failed → trying CTR")

            buildCTRCipher(Cipher.DECRYPT_MODE).doFinal(data)
        }
    }

    // Image decrypt (story + chat safe)

    fun decryptImage(data: ByteArray): ByteArray {

        Log.d(
            "PurrfectSnap",
            "Image Decrypt → keySize=${keySpec.size} ivSize=${ivKeyParameterSpec.size}"
        )

        return try {
            // Story images
            buildCBCCipherNoPadding(Cipher.DECRYPT_MODE).doFinal(data)

        } catch (noPadError: Exception) {

            Log.d("PurrfectSnap", "CBC NoPadding failed → trying PKCS5")

            try {
                // Chat images
                buildCBCCipherPKCS5(Cipher.DECRYPT_MODE).doFinal(data)

            } catch (pkcsError: Exception) {

                Log.d("PurrfectSnap", "PKCS5 failed → trying CTR")

                // Edge fallback
                buildCTRCipher(Cipher.DECRYPT_MODE).doFinal(data)
            }
        }
    }

    // Stream decrypt

    fun decrypt(input: InputStream): InputStream {
        val cipher = try {
            buildCBCCipherPKCS5(Cipher.DECRYPT_MODE)
        } catch (_: Exception) {
            buildCTRCipher(Cipher.DECRYPT_MODE)
        }

        return CipherInputStream(input, cipher)
    }

    fun decryptImage(input: InputStream): InputStream {

        val cipher = try {
            buildCBCCipherNoPadding(Cipher.DECRYPT_MODE)
        } catch (_: Exception) {
            buildCBCCipherPKCS5(Cipher.DECRYPT_MODE)
        }

        return CipherInputStream(input, cipher)
    }

    fun decrypt(output: OutputStream): OutputStream {

        val cipher = try {
            buildCBCCipherPKCS5(Cipher.DECRYPT_MODE)
        } catch (_: Exception) {
            buildCTRCipher(Cipher.DECRYPT_MODE)
        }

        return CipherOutputStream(output, cipher)
    }
}

// KeyPair Extensions

/** Standard Base64 */
fun EncryptionWrapper.toKeyPair(): MediaEncryptionKeyPair {
    return MediaEncryptionKeyPair(
        key = Base64.encodeToString(this.keySpec, Base64.NO_WRAP),
        iv = Base64.encodeToString(this.ivKeyParameterSpec, Base64.NO_WRAP),
        urlSafe = false
    )
}

/** URL Safe (story media) */
fun EncryptionWrapper.toKeyPairUrlSafe(): MediaEncryptionKeyPair {
    return MediaEncryptionKeyPair(
        key = Base64.encodeToString(
            this.keySpec,
            Base64.URL_SAFE or Base64.NO_WRAP
        ),
        iv = Base64.encodeToString(
            this.ivKeyParameterSpec,
            Base64.URL_SAFE or Base64.NO_WRAP
        ),
        urlSafe = true
    )
}
