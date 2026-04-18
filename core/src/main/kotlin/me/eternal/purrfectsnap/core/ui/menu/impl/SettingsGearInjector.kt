package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.app.ActivityManager
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.ui.OverlayType
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.util.ktx.getDrawable
import cock.crest.purrfectsnap.lite.core.util.ktx.getStyledAttributes
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress

class SettingsGearInjector : AbstractMenu() {
    private val hovaHeaderAddFriendIconId by lazy {
        this@SettingsGearInjector.context.resources.getIdentifier("hova_header_add_friend_icon", "id", "com.snapchat.android")
    }

    private val gearIconId = View.generateViewId()
    private val logTag = "SettingsGearInjector"

    override fun init() {
        this@SettingsGearInjector.context.log.info("Initializing", logTag)
        if (this@SettingsGearInjector.context.config.userInterface.settingsMenu.get() != "legacy") {
            this@SettingsGearInjector.context.log.info("Settings menu is not legacy, aborting init.", logTag)
            return
        }

        this@SettingsGearInjector.context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view.id == hovaHeaderAddFriendIconId) {
                this@SettingsGearInjector.context.log.info("AddViewEvent triggered for hova_header_add_friend_icon", logTag)
                val parent = event.parent as? FrameLayout ?: return@subscribe
                if (parent.findViewById<View>(gearIconId) != null) {
                    this@SettingsGearInjector.context.log.info("Gear icon already exists, skipping.", logTag)
                    return@subscribe
                }

                this@SettingsGearInjector.context.log.info("Creating and adding gear icon.", logTag)
                val gearIcon = ImageView(parent.context).apply {
                    id = gearIconId
                    val resources = this@SettingsGearInjector.context.resources
                    val theme = this@SettingsGearInjector.context.androidContext.theme
                    setImageDrawable(resources.getDrawable("svg_settings_32x32", theme))
                    resources.getStyledAttributes("headerButtonOpaqueIconTint", theme).getColorStateList(0)?.let {
                        imageTintList = it
                    }
                    setOnClickListener {
                        this@SettingsGearInjector.context.log.info("Gear icon clicked.", logTag)
                        this@SettingsGearInjector.context.bridgeClient.openOverlay(OverlayType.SETTINGS)
                    }
                    setOnLongClickListener {
                        this@SettingsGearInjector.handleChatHoldKillAction()
                    }
                }

                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                }

                parent.addView(gearIcon, layoutParams)
                this@SettingsGearInjector.context.log.info("Gear icon added to parent. Posting position and size update.", logTag)

                event.view.post {
                    try {
                        this@SettingsGearInjector.context.log.info("Running position and size update.", logTag)
                        val addFriendIcon = event.view
                        val friendIconParams = addFriendIcon.layoutParams as ViewGroup.MarginLayoutParams
                        val friendIconMarginEnd = friendIconParams.marginEnd
                        val friendIconWidth = addFriendIcon.width
                        val friendIconHeight = addFriendIcon.height

                        this@SettingsGearInjector.context.log.info("Friend icon details: width=$friendIconWidth, height=$friendIconHeight, marginEnd=$friendIconMarginEnd", logTag)

                        val newMargin = friendIconWidth + friendIconMarginEnd + this@SettingsGearInjector.context.userInterface.dpToPx(4)
                        this@SettingsGearInjector.context.log.info("Calculated new marginEnd for gear icon: $newMargin", logTag)

                        (gearIcon.layoutParams as FrameLayout.LayoutParams).apply {
                            height = friendIconHeight
                            width = friendIconHeight // Make it a square
                            marginEnd = newMargin
                        }.also {
                            gearIcon.layoutParams = it
                        }
                        this@SettingsGearInjector.context.log.info("Successfully updated gear icon position and size.", logTag)
                    } catch (t: Throwable) {
                        this@SettingsGearInjector.context.log.error("Failed to position or size gear icon", t, logTag)
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
