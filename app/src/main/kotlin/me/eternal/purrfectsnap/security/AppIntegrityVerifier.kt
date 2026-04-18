package cock.crest.purrfectsnap.lite.security

import android.content.Context

object AppIntegrityVerifier {
    fun enforce(context: Context) {
        // Intentionally disabled: do not perform APK signature/hash enforcement.
        return
    }
}
