package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import android.content.ContextWrapper
import android.content.pm.PackageManager
import cock.crest.purrfectsnap.lite.common.config.impl.Global
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class DisablePermissionRequests : Feature("Disable Permission Requests") {
    override fun init() {
        val deniedPermissions by context.config.global.disablePermissionRequests
        if (deniedPermissions.isEmpty()) return

        ContextWrapper::class.java.hook("checkPermission", HookStage.BEFORE) { param ->
            val permission = param.arg<String>(0)
            val permissionKey = Global.permissionMap[permission] ?: return@hook
            if (deniedPermissions.contains(permissionKey)) {
                param.setResult(PackageManager.PERMISSION_GRANTED)
            }
        }
    }
}