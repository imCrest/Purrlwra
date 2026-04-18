package cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi

import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.io.Closeable

class ValdiMarshaller(obj: Any): AbstractWrapper(obj), Closeable {
    companion object {
        fun create(): ValdiMarshaller? {
            return runCatching {
                ValdiMarshaller(PurrfectSnap.classCache.valdiMarshaller?.getMethod("create")?.invoke(null) ?: return null)
            }.getOrNull()
        }
    }

    private val getUntypedMethod by lazy { instanceNonNull().javaClass.methods.first { it.name == "getUntyped" } }
    private val getSizeMethod by lazy { instanceNonNull().javaClass.methods.first { it.name == "getSize" } }
    private val pushUntypedMethod by lazy { instanceNonNull().javaClass.methods.first { it.name == "pushUntyped" } }
    private val destroyMethod by lazy { instanceNonNull().javaClass.methods.firstOrNull { it.name == "destroy" } }

    fun getUntyped(index: Int): Any? = getUntypedMethod.invoke(instanceNonNull(), index)
    fun getSize() = getSizeMethod.invoke(instanceNonNull()) as Int
    fun pushUntyped(value: Any?): Any? = pushUntypedMethod.invoke(instanceNonNull(), value)

    fun destroy() {
        destroyMethod?.invoke(instanceNonNull())
    }

    override fun close() {
        runCatching { destroy() }
    }
}
