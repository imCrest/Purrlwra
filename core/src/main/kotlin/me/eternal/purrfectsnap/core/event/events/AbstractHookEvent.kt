package cock.crest.purrfectsnap.lite.core.event.events

import cock.crest.purrfectsnap.lite.core.event.Event
import cock.crest.purrfectsnap.lite.core.util.hook.HookAdapter

abstract class AbstractHookEvent : Event() {
    lateinit var adapter: HookAdapter
    private val invokeLaterCallbacks = mutableListOf<() -> Unit>()

    fun addInvokeLater(callback: () -> Unit) {
        invokeLaterCallbacks.add(callback)
    }

    private fun invokeLater() {
        invokeLaterCallbacks.forEach { it() }
    }

    fun postHookEvent(block: AbstractHookEvent.() -> Unit = {}) {
        block().apply {
            invokeLater()
            if (canceled) adapter.setResult(null)
        }
    }

    fun invokeOriginal() {
        invokeLater()
        adapter.invokeOriginal()
    }
}