package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.graphics.Typeface
import cock.crest.purrfectsnap.lite.common.bridge.FileHandleScope
import cock.crest.purrfectsnap.lite.core.ModContext
import java.io.File
import java.io.FileOutputStream

private var cachedFontPath: String? = null
private var cachedFontName: String? = null

fun getCustomEmojiFontPath(
    context: ModContext
): String? {
    val customFileName = context.config.experimental.nativeHooks.customEmojiFont.getNullable()
        ?.trim()
        ?.takeIf { it.isNotBlank() } ?: return null
    val safeFileName = customFileName.substringAfterLast("/").substringAfterLast("\\")
    if (safeFileName.isBlank()) return null

    fun clearCachedPath(cacheFile: File? = null): String? {
        runCatching { cacheFile?.delete() }
        cachedFontPath = null
        cachedFontName = safeFileName
        return null
    }

    fun validateFontFile(file: File): Boolean {
        return runCatching {
            Typeface.createFromFile(file)
            true
        }.onFailure {
            context.log.warn("Custom emoji font validation failed for ${file.absolutePath}: ${it.message}")
        }.getOrDefault(false)
    }

    return runCatching {
        val handle = context.fileHandlerManager.getFileHandle(
            FileHandleScope.USER_IMPORT.key,
            customFileName
        ) ?: return@runCatching clearCachedPath()
        if (!handle.exists()) {
            val oldCacheFile = File(context.androidContext.cacheDir, "emoji_fonts").resolve(safeFileName)
            return@runCatching clearCachedPath(oldCacheFile)
        }

        val persistentDir = File(context.androidContext.cacheDir, "emoji_fonts").apply {
            mkdirs()
        }
        val persistentFile = File(persistentDir, safeFileName)

        val needsCopy = handle.open(android.os.ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
            val sourceSize = pfd.statSize
            !persistentFile.exists() || (sourceSize > 0 && sourceSize != persistentFile.length())
        } ?: true

        if (needsCopy) {
            val tempFile = File(persistentDir, "$safeFileName.tmp")
            handle.open(android.os.ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                FileOutputStream(tempFile).use { output ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                        input.copyTo(output)
                    }
                }
            }
            if (!tempFile.renameTo(persistentFile)) {
                tempFile.copyTo(persistentFile, overwrite = true)
                tempFile.delete()
            }
        }

        if (!validateFontFile(persistentFile)) {
            return@runCatching clearCachedPath(persistentFile)
        }

        cachedFontName = safeFileName
        cachedFontPath = persistentFile.absolutePath
        cachedFontPath?.takeIf { it.isNotEmpty() }
    }.onFailure {
        context.log.error("Failed to get custom emoji font", it)
        clearCachedPath()
    }.getOrNull()
}


