package cock.crest.purrfectsnap.lite.core.features.impl.global

import android.graphics.Bitmap
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.mapper.impl.MediaQualityLevelProviderMapper
import java.lang.reflect.Method

class MediaUploadQualityOverride : Feature("Media Upload Quality Override") {
    override fun init() {
        if (context.config.global.mediaUploadQualityConfig.forceVideoUploadSourceQuality.get()) {
            context.mappings.useMapper(MediaQualityLevelProviderMapper::class) {
                val providerClass = mediaQualityLevelProvider.getAsClass()
                val providerMethodName = mediaQualityLevelProviderMethod.getAsString()

                if (providerClass == null || providerMethodName == null) {
                    context.log.warn("MediaQualityLevelProvider mapping failed - provider class or method not found")
                    return@useMapper
                }

                providerClass.hook(
                    providerMethodName,
                    HookStage.BEFORE
                ) { param ->
                    try {
                        val method = param.method() as Method
                        val returnType = method.returnType

                        val levelMax = returnType.enumConstants?.firstOrNull {
                            it.toString() == "LEVEL_MAX"
                        }

                        if (levelMax != null) {
                            param.setArg(1, levelMax)
                        } else {
                            context.log.warn("LEVEL_MAX enum constant not found. Available: ${returnType.enumConstants?.joinToString()}")
                        }
                    } catch (e: Exception) {
                        context.log.error("Failed to override video quality", e)
                    }
                }
            }
        }

        val disableImageCompression by context.config.global.mediaUploadQualityConfig.disableImageCompression
        val imageUploadFormat = context.config.global.mediaUploadQualityConfig.customUploadImageFormat.getNullable()

        if (imageUploadFormat != null || disableImageCompression) {
            Bitmap::class.java.hook("compress", HookStage.BEFORE) { param ->
                try {
                    val quality = param.arg<Int>(1)
                    val currentFormat = param.arg<Any>(0)

                    if (quality == 0) return@hook

                    if (currentFormat == Bitmap.CompressFormat.JPEG ||
                        currentFormat == Bitmap.CompressFormat.PNG ||
                        currentFormat == Bitmap.CompressFormat.WEBP) {

                        @Suppress("DEPRECATION")
                        val newFormat = when (imageUploadFormat) {
                            "png" -> Bitmap.CompressFormat.PNG
                            "webp" -> Bitmap.CompressFormat.WEBP
                            "jpeg" -> Bitmap.CompressFormat.JPEG
                            else -> currentFormat as Bitmap.CompressFormat
                        }

                        param.setArg(0, newFormat)

                        if (disableImageCompression) {
                            param.setArg(1, 100)
                        }
                    }
                } catch (e: Exception) {
                    context.log.error("Failed to override image compression", e)
                }
            }

            try {
                findClass("com.snap.camera.jni.SnapImageTranscoder").hook("nativeEncodeBitmapToJpeg", HookStage.BEFORE) {
                    it.setResult(ByteArray(0))
                }
            } catch (e: Exception) {
                context.log.warn("Failed to hook SnapImageTranscoder - this is expected on some Snapchat versions")
            }
        }
    }
}
