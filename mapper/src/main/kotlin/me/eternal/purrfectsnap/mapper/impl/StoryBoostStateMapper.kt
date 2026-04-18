package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.findConstString
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName

class StoryBoostStateMapper : AbstractClassMapper("StoryBoostState") {
    val classReference = classReference("class")

    init {
        mapper {
            for (clazz in classes) {
                val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
                if (firstConstructor.parameters.size != 3) continue
                if (firstConstructor.parameterTypes[1] != "J" || firstConstructor.parameterTypes[2] != "J") continue

                if (clazz.methods.firstOrNull { it.name == "toString" }?.implementation?.findConstString("StoryBoostState", contains = true) != true) continue

                classReference.set(clazz.getClassName())
                return@mapper
            }
        }
    }
}