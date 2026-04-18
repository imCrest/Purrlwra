package cock.crest.purrfectsnap.lite.core.util.hook

import com.highcapable.yukihookapi.hook.param.PackageParam

object HookRuntime {
    @Volatile
    private var currentPackageParam: PackageParam? = null

    fun attach(packageParam: PackageParam) {
        currentPackageParam = packageParam
    }

    fun packageParam(): PackageParam {
        return currentPackageParam ?: error("Yuki PackageParam is not initialized")
    }
}
