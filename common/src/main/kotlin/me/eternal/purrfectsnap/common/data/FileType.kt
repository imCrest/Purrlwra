package cock.crest.purrfectsnap.lite.common.data

import java.io.File
import java.io.InputStream

enum class FileType(
    val fileExtension: String? = null,
    val mimeType: String,
    val isVideo: Boolean = false,
    val isImage: Boolean = false,
    val isAudio: Boolean = false
) {
    GIF("gif", "image/gif", false, false, false),
    PNG("png", "image/png", false, true, false),
    MP4("mp4", "video/mp4", true, false, false),
    MKV("mkv", "video/mkv", true, false, false),
    AVI("avi", "video/avi", true, false, false),
    MP3("mp3", "audio/mp3",false, false, true),
    OPUS("opus", "audio/opus", false, false, true),
    AAC("aac", "audio/aac", false, false, true),
    JPG("jpg", "image/jpg",false, true, false),
    ZIP("zip", "application/zip", false, false, false),
    WEBP("webp", "image/webp", false, true, false),
    HEIC("heic", "image/heic", false, true, false),
    HEIF("heif", "image/heif", false, true, false),
    MPD("mpd", "text/xml", false, false, false),
    UNKNOWN("dat", "application/octet-stream", false, false, false);

    companion object {
        private val fileSignatures = mapOf(
            "52494646" to WEBP,
            "504b0304" to ZIP,
            "89504e47" to PNG,
            "00000020" to MP4,
            "00000018" to MP4,
            "0000001c" to MP4,
            "494433" to MP3,
            "4f676753" to OPUS,
            "fff15" to AAC,
            "ffd8ff" to JPG,
            "47494638" to GIF,
            "1a45dfa3" to MKV,
        )

        fun fromString(string: String?): FileType {
            return entries.firstOrNull { it.fileExtension.equals(string, ignoreCase = true) } ?: UNKNOWN
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val result = StringBuilder()
            for (b in bytes) {
                result.append(String.format("%02x", b))
            }
            return result.toString()
        }

        private fun looksLikeIsoBmffVideo(array: ByteArray): Boolean {
            if (array.size < 12) return false
            // ISO BMFF containers like MP4 expose an `ftyp` box at byte offset 4.
            if (array[4] != 'f'.code.toByte() ||
                array[5] != 't'.code.toByte() ||
                array[6] != 'y'.code.toByte() ||
                array[7] != 'p'.code.toByte()
            ) {
                return false
            }

            val majorBrand = String(array, 8, 4, Charsets.US_ASCII).trim('\u0000').lowercase()
            
            // Explicitly exclude known IMAGE-only brands to prevent false positives (HEIC/HEIF)
            val imageBrands = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
            if (majorBrand in imageBrands) return false

            return majorBrand in setOf(
                "mp41", "mp42", "isom", "iso2", "iso3", "iso4", "iso5", "iso6",
                "avc1", "dash", "cmfc", "msnv", "3gp4", "3gp5", "3gp6", "3g2a", "3g2b",
                "mp4v", "mp4a", "m4v ", "m4a ", "f4v ", "f4a "
            ) || majorBrand.isNotEmpty()
        }

        fun fromFile(file: File): FileType {
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(16)
                inputStream.read(buffer)
                return fromByteArray(buffer)
            }
        }

        fun fromByteArray(array: ByteArray): FileType {
            val headerBytes = ByteArray(16)
            System.arraycopy(array, 0, headerBytes, 0, 16)
            val hex = bytesToHex(headerBytes)
            
            // 1. Check strict signatures
            fileSignatures.entries.firstOrNull { hex.startsWith(it.key) }?.value?.let { return it }

            // 2. Check ISO BMFF container type
            val majorBrand = if (headerBytes.size >= 12 && 
                headerBytes[4] == 'f'.code.toByte() && headerBytes[5] == 't'.code.toByte() &&
                headerBytes[6] == 'y'.code.toByte() && headerBytes[7] == 'p'.code.toByte()) {
                String(headerBytes, 8, 4, Charsets.US_ASCII).trim('\u0000').lowercase()
            } else null

            if (majorBrand != null) {
                if (majorBrand in setOf("heic", "heix")) return HEIC
                if (majorBrand in setOf("mif1", "msf1")) return HEIF
                if (looksLikeIsoBmffVideo(headerBytes)) return MP4
            }

            return UNKNOWN
        }

        fun fromInputStream(inputStream: InputStream): FileType {
            val buffer = ByteArray(16)
            inputStream.read(buffer)
            return fromByteArray(buffer)
        }
    }
}
