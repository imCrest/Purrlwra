package cock.crest.purrfectsnap.lite.nativelib

import androidx.annotation.Keep

@Keep
class NativeDecision {
    @JvmField var blocked: Boolean = false
    @JvmField var reason: String = "allowed"
    @JvmField var keyword: String? = null
    @JvmField var keywordContext: String? = null
    @JvmField var matchType: String? = null
    @JvmField var matchValue: String? = null
}
