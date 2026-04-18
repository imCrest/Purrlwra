package cock.crest.purrfectsnap.lite.security

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

object SigningCertSha256 {
    fun get(context: Context): String {
        val signatures = runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.map { it.toByteArray() }
            }
        }.getOrNull().orEmpty()

        val first = signatures.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(first)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

