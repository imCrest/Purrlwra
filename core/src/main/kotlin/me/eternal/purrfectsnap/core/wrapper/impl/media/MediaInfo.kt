package cock.crest.purrfectsnap.lite.core.wrapper.impl.media

import android.os.Parcelable
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.lang.reflect.Field


class MediaInfo(obj: Any?) : AbstractWrapper(obj) {
    val uri: String
        get() {
            val firstStringUriField = instanceNonNull().javaClass.fields.first { f: Field -> f.type == String::class.java }
            return instanceNonNull().getObjectField(firstStringUriField.name) as String
        }

    init {
        instance?.let {
            if (it is List<*>) {
                if (it.isEmpty()) {
                    throw RuntimeException("MediaInfo is empty")
                }
                
                // Select highest quality media by comparing width * height
                // Use explicit field name search to avoid relying on field order
                instance = it.filterNotNull().maxByOrNull { mediaObj ->
                    runCatching {
                        val fields = mediaObj.javaClass.fields
                        
                        // Search for width and height fields by name (case-insensitive)
                        // Common patterns: "width", "mWidth", "height", "mHeight"
                        val widthField = fields.find { f -> 
                            f.name.equals("width", ignoreCase = true) ||
                            f.name.equals("mWidth", ignoreCase = true)
                        }
                        val heightField = fields.find { f -> 
                            f.name.equals("height", ignoreCase = true) ||
                            f.name.equals("mHeight", ignoreCase = true)
                        }
                        
                        // Validate fields exist and are integers before calculating resolution
                        if (widthField != null && heightField != null &&
                            (widthField.type == Int::class.javaPrimitiveType || widthField.type == Int::class.java) &&
                            (heightField.type == Int::class.javaPrimitiveType || heightField.type == Int::class.java)) {
                            widthField.isAccessible = true
                            heightField.isAccessible = true
                            widthField.getInt(mediaObj) * heightField.getInt(mediaObj)
                        } else {
                            0
                        }
                    }.getOrDefault(0)
                } ?: it.filterNotNull().firstOrNull() ?: it.firstOrNull()
            }
        }
    }

    val encryption: EncryptionWrapper?
        get() {
            val encryptionAlgorithmField = instanceNonNull().javaClass.fields.first { f: Field ->
                f.type.isInterface && Parcelable::class.java.isAssignableFrom(f.type)
            }
            return encryptionAlgorithmField[instance]?.let { EncryptionWrapper(it) }
        }
}
