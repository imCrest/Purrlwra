package cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi

import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

class ValdiContext(obj: Any): AbstractWrapper(obj) {
    val componentPath by field<String>("componentPath")
    val viewModel by field<Any?>("innerViewModel")
    val moduleName by field<String>("moduleName")
    val componentContext by field<WeakReference<Any?>>("componentContext")

    val viewModelLegacy: Any?
        get() = runCatching { instanceNonNull().getObjectField("viewModel") }.getOrNull()
            ?: instanceNonNull()::class.java.methods.firstOrNull { it.name == "getViewModel" && it.parameterTypes.isEmpty() }?.invoke(instanceNonNull())

    fun enqueueNextRenderCallback(callback: () -> Unit) {
        val method = instanceNonNull()::class.java.methods.firstOrNull {
            it.name == "onNextLayout"
        }
        method?.invoke(instanceNonNull(), Proxy.newProxyInstance(
            instanceNonNull()::class.java.classLoader,
            arrayOf(method.parameterTypes[0])
        ) { _, _, _ ->
            callback()
        })
    }
}
