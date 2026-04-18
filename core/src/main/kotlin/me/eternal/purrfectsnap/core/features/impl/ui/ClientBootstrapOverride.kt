package cock.crest.purrfectsnap.lite.core.features.impl.ui

import cock.crest.purrfectsnap.lite.common.config.impl.UserInterfaceTweaks
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoEditor
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoWriter
import cock.crest.purrfectsnap.lite.core.features.Feature
import java.io.File


class ClientBootstrapOverride: Feature("ClientBootstrapOverride") {

    private val clientBootstrapFolder by lazy { File(context.androidContext.filesDir, "client-bootstrap") }

    private val appearanceStartupConfigFile by lazy { File(clientBootstrapFolder, "appearancestartupconfig") }
    private val plusFile by lazy { File(clientBootstrapFolder, "plus") }

    override fun init() {
        val bootstrapOverrideConfig = context.config.userInterface.bootstrapOverride

        if (!clientBootstrapFolder.exists() && (bootstrapOverrideConfig.appAppearance.getNullable() != null || bootstrapOverrideConfig.homeTab.getNullable() != null)) {
            clientBootstrapFolder.mkdirs()
        }

        bootstrapOverrideConfig.appAppearance.getNullable()?.also { appearance ->
            val state = when (appearance) {
                "always_light" -> 0
                "always_dark" -> 1
                else -> return@also
            }.toByte()
            appearanceStartupConfigFile.writeBytes(byteArrayOf(0, 0, 0, state))
        }

        val homeTab = bootstrapOverrideConfig.homeTab.getNullable()

        if (homeTab != null) {
            val plusFileBytes = plusFile.exists().let { if (it) plusFile.readBytes() else ProtoWriter().toByteArray() }

            plusFile.writeBytes(
                ProtoEditor(plusFileBytes).apply {
                    edit {
                        homeTab.let { currentTab ->
                            remove(1)
                            addVarInt(1, UserInterfaceTweaks.BootstrapOverride.tabs.indexOf(currentTab) + 1)
                        }
                    }
                }.toByteArray()
            )
        }
    }
}