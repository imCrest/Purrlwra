package me.eternal.purrfectsnap.core.action

import android.content.Intent
import me.eternal.purrfectsnap.common.action.EnumAction
import me.eternal.purrfectsnap.core.ModContext
import me.eternal.purrfectsnap.core.action.impl.CleanCache

class ActionManager(
    private val modContext: ModContext,
) {

    private val actions by lazy {
        mapOf(
            EnumAction.CLEAN_CACHE to CleanCache(),
        ).map {
            it.key to it.value.apply {
                this.context = modContext
            }
        }.toMap().toMutableMap()
    }

    fun onNewIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EnumAction.ACTION_PARAMETER) ?: return
        intent.removeExtra(EnumAction.ACTION_PARAMETER)
        execute(EnumAction.entries.find { it.key == action } ?: return)
    }

    fun onActivityCreate() {
        actions.values.forEach { it.onActivityCreate() }
    }

    fun execute(enumAction: EnumAction) {
        val action = actions[enumAction] ?: return
        action.run()
        if (enumAction.exitOnFinish) {
            modContext.forceCloseApp()
        }
    }
}
