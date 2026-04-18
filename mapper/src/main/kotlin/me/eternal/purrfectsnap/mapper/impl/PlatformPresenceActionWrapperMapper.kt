package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName

class PlatformPresenceActionWrapperMapper : AbstractClassMapper("PlatformPresenceActionWrapper") {
    val classReference = classReference("class")

    init {
        mapper {
            classes.firstOrNull { classDef ->
                classDef.fields.any { field ->
                    field.type == "Lcom/snap/presence/PlatformChatVisibleAction;"
                }
            }?.let { classReference.set(it.getClassName()) }
        }
    }
}
