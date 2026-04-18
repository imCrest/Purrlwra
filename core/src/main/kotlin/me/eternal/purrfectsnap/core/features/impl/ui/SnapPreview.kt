package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.view.ViewGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.addForegroundDrawable
import cock.crest.purrfectsnap.lite.core.ui.randomTag
import cock.crest.purrfectsnap.lite.core.ui.removeForegroundDrawable
import cock.crest.purrfectsnap.lite.core.util.EvictingMap
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.media.PreviewUtils
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import java.io.File

class SnapPreview : Feature("SnapPreview") {
    private val mediaFileCache = EvictingMap<String, File>(500) // mMediaId => mediaFile
    private val bitmapCache = EvictingMap<String, Bitmap>(50) // filePath => bitmap

    private val fetchJobTab = randomTag()
    private val previewHorizontalAdjustmentDp = 16
    private val previewVerticalAdjustmentDp = 10

    override fun init() {
        if (!context.config.userInterface.snapPreview.get()) return
        context.mappings.useMapper(CallbackMapper::class) {
            callbacks.getClass("ContentCallback")?.hook("handleContentResult", HookStage.BEFORE) { param ->
                val contentResult = param.arg<Any>(0)
                val classMethods = contentResult::class.java.methods

                val contentKey = classMethods.find { it.name == "getContentKey" }?.invoke(contentResult) ?: return@hook
                if (contentKey.getObjectField("mMediaContextType").toString() != "CHAT") return@hook

                val filePath = classMethods.find { it.name == "getFilePath" }?.invoke(contentResult) ?: return@hook
                val mediaId = contentKey.getObjectField("mMediaId").toString()

                mediaFileCache[mediaId.substringAfter("-")] = File(filePath.toString())
            }
        }

        onNextActivityCreate {
            val chatMediaCardHeight = context.userInterface.dpToPx(60)
            val chatMediaCardSnapMargin = context.userInterface.dpToPx(10)
            val chatMediaCardSnapMarginStartSdl = context.userInterface.dpToPx(15)
            val previewHorizontalAdjustment = context.userInterface.dpToPx(previewHorizontalAdjustmentDp)
            val previewVerticalAdjustment = context.userInterface.dpToPx(previewVerticalAdjustmentDp)

            fun decodeMedia(file: File) = runCatching {
                bitmapCache.getOrPut(file.absolutePath) {
                    PreviewUtils.resizeBitmap(
                        PreviewUtils.createPreviewFromFile(file) ?: return@runCatching null,
                        chatMediaCardHeight - chatMediaCardSnapMargin,
                        chatMediaCardHeight - chatMediaCardSnapMargin
                    )
                }
            }.getOrNull()

            context.event.subscribe(BindViewEvent::class) { event ->
                event.chatMessage { _, _ ->
                    val messageLinearLayout = (event.view as ViewGroup).getChildAt(0) as? ViewGroup ?: return@subscribe
                    messageLinearLayout.removeForegroundDrawable("snapPreview")

                    val message = event.databaseMessage ?: return@chatMessage
                    val messageReader = ProtoReader(message.messageContent ?: return@chatMessage)
                    val contentType = ContentType.fromMessageContainer(messageReader.followPath(4, 4))

                    if (contentType != ContentType.SNAP || message.isSaved == 1) return@chatMessage

                    val mediaIdKey = messageReader.getString(4, 5, 1, 3, 2, 2) ?: return@chatMessage

                    var mediaFile = mediaFileCache[mediaIdKey] ?: return@chatMessage
                    val mediaFilePath = mediaFile.absolutePath

                    (messageLinearLayout.getTag(fetchJobTab) as? Job)?.cancel()

                    if (bitmapCache[mediaFilePath] == null) {
                        messageLinearLayout.setTag(fetchJobTab, context.coroutineScope.launch {
                            bitmapCache[mediaFilePath] = decodeMedia(mediaFile) ?: return@launch
                            messageLinearLayout.postInvalidate()
                        })
                    }

                    messageLinearLayout.addForegroundDrawable("snapPreview", ShapeDrawable(object: Shape() {
                        override fun draw(canvas: Canvas, paint: Paint) {
                            val bitmap = bitmapCache[mediaFilePath] ?: return

                            canvas.drawBitmap(bitmap,
                                canvas.width.toFloat() - bitmap.width - chatMediaCardSnapMarginStartSdl.toFloat() - chatMediaCardSnapMargin.toFloat() + previewHorizontalAdjustment,
                                (canvas.height - bitmap.height) / 2f + previewVerticalAdjustment,
                                null
                            )
                        }
                    }))
                }
            }
        }
    }
}
