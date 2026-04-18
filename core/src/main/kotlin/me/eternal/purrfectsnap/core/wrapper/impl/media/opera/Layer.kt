package cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera

import cock.crest.purrfectsnap.lite.common.util.ktx.findFieldsToString
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class Layer(obj: Any?) : AbstractWrapper(obj) {
    val paramMap: ParamMap
        get() {
            val layerControllerField = instanceNonNull()::class.java.findFieldsToString(instance, once = true) { _, value ->
                value.contains("OperaPageModel")
            }.firstOrNull() ?: throw RuntimeException("Could not find layerController field")

            val paramsMapHashMap = layerControllerField.type.findFieldsToString(layerControllerField[instance], once = true) { _, value ->
                value.contains("OperaPageModel")
            }.firstOrNull() ?: throw RuntimeException("Could not find paramsMap field")

            return ParamMap(paramsMapHashMap[layerControllerField[instance]]!!)
        }
}
