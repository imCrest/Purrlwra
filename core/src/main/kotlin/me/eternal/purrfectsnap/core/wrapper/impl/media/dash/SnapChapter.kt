package cock.crest.purrfectsnap.lite.core.wrapper.impl.media.dash

import cock.crest.purrfectsnap.lite.core.util.ktx.findFieldNamesByType
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class SnapChapter (obj: Any?) : AbstractWrapper(obj) {
    private val longFields by lazy {
        instanceNonNull().findFieldNamesByType(Long::class.javaPrimitiveType ?: Long::class.java)
    }
    val snapId by lazy {
        instanceNonNull().getObjectField(longFields.first()) as Long
    }
    val startTimeMs by lazy {
        instanceNonNull().getObjectField(longFields[1]) as Long
    }
}
