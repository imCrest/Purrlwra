package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.app.ActivityManager
import android.os.Process
import android.view.View
import android.widget.FrameLayout
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.ui.OverlayType
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getId
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress

class SettingsMenu : AbstractMenu() {
    private val hovaHeaderSearchIconId by lazy {
        context.resources.getId("hova_header_search_icon")
    }

    override fun init() {
        if (context.config.userInterface.settingsMenu.get() != "default") return
        context.androidContext.classLoader.loadClass("com.snap.ui.view.SnapFontTextView").hook("setText", HookStage.BEFORE) { param ->
            val view = param.thisObject<View>()
            if ((view.parent as? FrameLayout)?.findViewById<View>(hovaHeaderSearchIconId) != null) {
                view.post {
                    view.setOnClickListener {
                        context.bridgeClient.openOverlay(OverlayType.SETTINGS)
                    }
                    view.setOnLongClickListener {
                        handleChatHoldKillAction()
                    }
                }
            }
        }
    }

    private fun handleChatHoldKillAction(): Boolean {
        val selectedActions = context.config.userInterface.chatHoldKillActions.get()
        if (selectedActions.isEmpty()) return false

        context.androidContext.vibrateLongPress()
        context.mainActivity?.vibrateLongPress()

        if (selectedActions.contains("kill_purrfectsnap")) {
            runCatching {
                val activityManager = context.androidContext.getSystemService(ActivityManager::class.java)
                activityManager?.killBackgroundProcesses(Constants.MODULE_PACKAGE_NAME)
            }
        }

        if (selectedActions.contains("kill_snapchat")) {
            Process.killProcess(Process.myPid())
        }

        return true
    }
}
