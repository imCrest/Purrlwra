package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.core.util.ktx.findFieldNamesByType
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class ScSize(
    obj: Any?
) : AbstractWrapper(obj) {
    private val intFields by lazy {
        instanceNonNull().findFieldNamesByType(Int::class.javaPrimitiveType ?: Int::class.java)
    }
    private val firstFieldName by lazy { intFields.first() }
    private val secondFieldName by lazy { intFields.last() }

    var first: Int get() = instanceNonNull().getObjectField(firstFieldName) as Int
        set(value) {
            instanceNonNull().setObjectField(firstFieldName, value)
        }

    var second: Int get() = instanceNonNull().getObjectField(secondFieldName) as Int
        set(value) {
            instanceNonNull().setObjectField(secondFieldName, value)
        }
}
