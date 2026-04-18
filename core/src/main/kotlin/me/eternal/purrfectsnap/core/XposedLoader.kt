package cock.crest.purrfectsnap.lite.core

import android.app.Application
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import cock.crest.purrfectsnap.lite.common.BuildConfig
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class XposedLoader : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != Constants.SNAPCHAT_PACKAGE_NAME) return
        // prevent loading in sub-processes
        if (param.processName.contains(":")) return
        XposedBridge.log(
            "Loading PurrfectSnap v${BuildConfig.VERSION_NAME}#${BuildConfig.GIT_HASH} (package: ${BuildConfig.APPLICATION_ID})"
        )
        Application::class.java.hook("attach", HookStage.BEFORE) { hookParam ->
            PurrfectSnap().init(hookParam.arg(0))
        }
    }
}
