package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.findConstString
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName
import cock.crest.purrfectsnap.lite.mapper.ext.isAbstract
import cock.crest.purrfectsnap.lite.mapper.ext.searchNextFieldReference

class DefaultMediaItemMapper : AbstractClassMapper("DefaultMediaItem") {
    val cameraRollMediaId = classReference("cameraRollMediaIdClass")
    val durationMsField = string("durationMsField")
    val defaultMediaItemClass = classReference("defaultMediaItemClass")
    val defaultMediaItemDurationMsField = string("defaultMediaItemDurationMsField")

    init {
        mapper {
            for (clazz in classes) {
                if (clazz.methods.find { it.name == "toString" }?.implementation?.findConstString("CameraRollMediaId", contains = true) != true) {
                    continue
                }
                val durationMsDexField = clazz.fields.firstOrNull { it.type == "J" } ?: continue

                cameraRollMediaId.set(clazz.getClassName())
                durationMsField.set(durationMsDexField.name)
                return@mapper
            }
        }

        mapper {
            for (clazz in classes) {
                val superClass = getClass(clazz.superclass) ?: continue

                if (!superClass.isAbstract() || superClass.interfaces.isEmpty() || superClass.interfaces[0] != "Ljava/lang/Comparable;") continue
                if (clazz.methods.none { it.returnType == "Landroid/net/Uri;" }) continue

                val durationInMillisDexField = clazz.methods.firstOrNull { it.name == "toString" }?.implementation?.takeIf {
                    it.findConstString("metadata", contains = true)
                }?.searchNextFieldReference("durationInMillis", contains = true) ?: continue
                defaultMediaItemClass.set(clazz.getClassName())
                defaultMediaItemDurationMsField.set(durationInMillisDexField.name)
                return@mapper
            }
        }
    }
}