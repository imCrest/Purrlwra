package cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi

import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class ValdiFunction(obj: Any): AbstractWrapper(obj) {
    private val performMethod by lazy {
        instanceNonNull().javaClass.getMethod(
            "perform",
            PurrfectSnap.classCache.valdiMarshaller
        )
    }

    fun perform(valdiMarshaller: ValdiMarshaller): Boolean {
        return performMethod.invoke(instanceNonNull(), valdiMarshaller.instanceNonNull()) as Boolean
    }
}
