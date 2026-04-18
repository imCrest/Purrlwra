package cock.crest.purrfectsnap.lite.core.util

const val SNAPCHAT_13_80_VERSION = "13.80.0.0"

private fun parseSnapchatVersion(versionName: String?): List<Int>? {
    val normalizedVersion = versionName
        ?.substringBefore('-')
        ?.substringBefore(' ')
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return normalizedVersion.split('.')
        .mapNotNull { segment ->
            segment.filter(Char::isDigit).takeIf { it.isNotBlank() }?.toIntOrNull()
        }
        .takeIf { it.isNotEmpty() }
}

fun isSnapchatVersionAtLeast(versionName: String?, minimumVersion: String): Boolean {
    val current = parseSnapchatVersion(versionName) ?: return false
    val minimum = parseSnapchatVersion(minimumVersion) ?: return false
    val maxLength = maxOf(current.size, minimum.size)

    for (index in 0 until maxLength) {
        val currentPart = current.getOrElse(index) { 0 }
        val minimumPart = minimum.getOrElse(index) { 0 }
        if (currentPart != minimumPart) {
            return currentPart > minimumPart
        }
    }

    return true
}
