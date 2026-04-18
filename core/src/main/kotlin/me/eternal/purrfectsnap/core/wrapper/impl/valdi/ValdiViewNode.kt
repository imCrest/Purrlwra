package cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi

import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class ValdiViewNode(obj: Long) : AbstractWrapper(obj) {
    companion object {
        fun fromNode(viewNode: Any?): ValdiViewNode? {
            return (viewNode?.javaClass?.methods?.firstOrNull {
                it.name == "getNativeHandle"
            }?.invoke(viewNode) as? Long)?.let { ValdiViewNode(it) } ?: return null
        }
    }

    fun getAttribute(name: String): Any? {
        return PurrfectSnap.classCache.nativeBridge?.methods?.firstOrNull {
            it.name == "getValueForAttribute"
        }?.invoke(null, instanceNonNull(), name)
    }

    fun setAttribute(name: String, value: Any) {
        PurrfectSnap.classCache.nativeBridge?.methods?.firstOrNull {
            it.name == "setValueForAttribute"
        }?.invoke(null, instanceNonNull(), name, value, false)
    }

    fun getChildren(): List<ValdiViewNode> {
        val children = PurrfectSnap.classCache.nativeBridge?.methods?.firstOrNull {
            it.name == "getRetainedViewNodeChildren"
        }?.invoke(null, instanceNonNull(), 1) as? LongArray ?: return emptyList()
        return children.map { ValdiViewNode(it) }
    }

    fun getClassName(): String {
        return PurrfectSnap.classCache.nativeBridge?.methods?.firstOrNull {
            it.name == "getViewClassName"
        }?.invoke(null, instanceNonNull())?.toString() ?: ""
    }

    override fun toString(): String {
        return PurrfectSnap.classCache.nativeBridge?.methods?.firstOrNull {
            it.name == "getViewNodeDebugDescription"
        }?.invoke(null, instanceNonNull())?.toString() ?: ""
    }
}
