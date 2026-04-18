package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName

class ChatEventDispatcherMapper : AbstractClassMapper("ChatEventDispatcher")  {
    val classReference = classReference("class")

    init {
        mapper {
            for (clazz in classes) {
                if (clazz.methods.firstOrNull { it.name == "onChatItemDoubleClickEvent" } == null) continue
                classReference.set(clazz.getClassName())
                return@mapper
            }
        }
    }
}