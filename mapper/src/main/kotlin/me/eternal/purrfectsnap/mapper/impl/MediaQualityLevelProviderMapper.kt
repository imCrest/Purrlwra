package cock.crest.purrfectsnap.lite.mapper.impl

import com.android.tools.smali.dexlib2.AccessFlags
import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.findConstString
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName
import cock.crest.purrfectsnap.lite.mapper.ext.hasStaticConstructorString
import cock.crest.purrfectsnap.lite.mapper.ext.isAbstract
import cock.crest.purrfectsnap.lite.mapper.ext.isEnum

class MediaQualityLevelProviderMapper : AbstractClassMapper("MediaQualityLevelProvider") {
    val mediaQualityLevelProvider = classReference("mediaQualityLevelProvider")
    val mediaQualityLevelProviderMethod = string("mediaQualityLevelProviderMethod")

    init {
        var enumQualityLevel : String? = null

        mapper {
            for (enumClass in classes) {
                if (!enumClass.isEnum()) continue

                if (enumClass.hasStaticConstructorString("LEVEL_MAX")) {
                    enumQualityLevel = enumClass.getClassName()
                    break;
                }
            }
        }

        mapper {
            if (enumQualityLevel == null) return@mapper

            for (clazz in classes) {
                if (!clazz.isAbstract()) continue
                if (clazz.fields.none { it.accessFlags and AccessFlags.TRANSIENT.value != 0 }) continue

                clazz.methods.firstOrNull { method ->
                    method.returnType == "L$enumQualityLevel;"
                        && method.parameters.size == 2
                        && method.parameters.last().type == "L$enumQualityLevel;"
                }?.let {
                    mediaQualityLevelProvider.set(clazz.getClassName())
                    mediaQualityLevelProviderMethod.set(it.name)
                    return@mapper
                }
            }
        }

        mapper {
            if (enumQualityLevel == null || mediaQualityLevelProvider.get() != null) return@mapper

            for (clazz in classes) {
                if (clazz.methods.none { it.implementation?.findConstString("video-transcoding-level-", contains = true) == true }) continue

                clazz.methods.firstOrNull { method ->
                    method.returnType == "L$enumQualityLevel;"
                        && method.parameters.size == 2
                        && method.parameters.last().type == "L$enumQualityLevel;"
                }?.let {
                    mediaQualityLevelProvider.set(clazz.getClassName())
                    mediaQualityLevelProviderMethod.set(it.name)
                    return@mapper
                }
            }
        }

        mapper {
            if (enumQualityLevel == null || mediaQualityLevelProvider.get() != null) return@mapper

            for (clazz in classes) {
                val hasTranscodingConfig = clazz.methods.any { method ->
                    method.implementation?.findConstString("video-transcoding-level-1", contains = true) == true
                }
                if (!hasTranscodingConfig) continue

                val hasImageTranscodingConfig = clazz.methods.any { method ->
                    method.implementation?.findConstString("image_transcoding_level_", contains = true) == true
                }
                if (!hasImageTranscodingConfig) continue

                clazz.methods.firstOrNull { method ->
                    method.returnType == "L$enumQualityLevel;"
                        && method.parameters.size == 2
                        && method.parameters.last().type == "L$enumQualityLevel;"
                }?.let { qualityMethod ->
                    mediaQualityLevelProvider.set(clazz.getClassName())
                    mediaQualityLevelProviderMethod.set(qualityMethod.name)
                    return@mapper
                }
            }
        }
    }
}
