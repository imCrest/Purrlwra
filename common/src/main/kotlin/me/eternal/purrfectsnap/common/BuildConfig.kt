package cock.crest.purrfectsnap.lite.common

object BuildConfig {
    val VERSION_NAME get() = CommonBuildInfo.VERSION_NAME
    val VERSION_CODE get() = CommonBuildInfo.VERSION_CODE
    val APPLICATION_ID get() = CommonBuildInfo.APPLICATION_ID
    val BUILD_HASH get() = CommonBuildInfo.BUILD_HASH
    val GIT_HASH get() = CommonBuildInfo.GIT_HASH
    val SIF_ENDPOINT get() = CommonBuildInfo.SIF_ENDPOINT
    val DEBUG get() = false
}
