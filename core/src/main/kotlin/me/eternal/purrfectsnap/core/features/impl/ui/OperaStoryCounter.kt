package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.Layer
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.ParamMap
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper

class OperaStoryCounter : Feature("OperaStoryCounter") {
    private val counterState = mutableStateOf("")
    // "CAMERA" or "GALLERY" or ""
    private val sourceState = mutableStateOf("")

    override fun init() {
        val showCounter = this@OperaStoryCounter.context.config.userInterface.storyCounter.get()
        val showSourceIndicator = this@OperaStoryCounter.context.config.userInterface.storySourceIndicator.get()
        val storySnapJump = this@OperaStoryCounter.context.config.userInterface.storySnapJump.get()
        val storySnapListDownload = this@OperaStoryCounter.context.config.downloader.storySnapListDownload.get()
        val operaDownloadButton = this@OperaStoryCounter.context.config.downloader.operaDownloadButton.get()

        // OperaStoryOverlay handles counter/source/jump when any of these are enabled
        if (showCounter || showSourceIndicator || storySnapJump || storySnapListDownload || operaDownloadButton) return

        this@OperaStoryCounter.context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view is FrameLayout && event.parent.javaClass.superclass?.name?.endsWith("OpenLayout") == true) {
                val viewGroup = event.view as FrameLayout

                if (viewGroup.findViewWithTag<android.view.View>("story_counter") != null ||
                    event.parent.findViewWithTag<android.view.View>("story_counter") != null) return@subscribe

                if (event.parent.children().none { it.javaClass.name.endsWith("ScalableCircleMaskFrameLayout") }) return@subscribe

                val composeView = createComposeView(viewGroup.context) {
                    val counterText = counterState.value
                    val source = sourceState.value
                    val hasCounter = showCounter && counterText.isNotEmpty()
                    val hasSource = showSourceIndicator && source.isNotEmpty()

                    if (hasCounter || hasSource) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0x4C000000),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                // Counter section
                                if (hasCounter) {
                                    Text(
                                        text = counterText,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                // Divider between counter and source icon
                                if (hasCounter && hasSource) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(14.dp)
                                            .background(Color.White.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                // Source icon section
                                if (hasSource) {
                                    val icon = if (source == "CAMERA") Icons.Outlined.CameraAlt else Icons.Outlined.PhotoLibrary
                                    val description = if (source == "CAMERA") "Camera" else "Gallery"
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = description,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }.apply {
                    tag = "story_counter"
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val isOperaDownloadEnabled = this@OperaStoryCounter.context.config.downloader.operaDownloadButton.get()
                        gravity = Gravity.TOP or if (isOperaDownloadEnabled) Gravity.START else Gravity.END
                        topMargin = this@OperaStoryCounter.context.userInterface.dpToPx(50)
                        if (isOperaDownloadEnabled) {
                            marginStart = this@OperaStoryCounter.context.userInterface.dpToPx(10)
                        } else {
                            marginEnd = this@OperaStoryCounter.context.userInterface.dpToPx(10)
                        }
                    }
                }
                viewGroup.addView(composeView)
            }
        }

        onNextActivityCreate {
            this@OperaStoryCounter.context.mappings.useMapper(OperaPageViewControllerMapper::class) {
                arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                    classReference.get()?.hook(
                        methodName.get() ?: return@forEach,
                        HookStage.AFTER
                    ) { param ->
                        val viewState = (param.thisObject() as Any).getObjectField(viewStateField.get()!!).toString()

                        if (viewState != "FULLY_DISPLAYED") {
                            return@hook
                        }

                        val operaLayerList = (param.thisObject() as Any).getObjectField(layerListField.get()!!) as ArrayList<*>
                        val mediaParamMap: ParamMap = operaLayerList.map { Layer(it) }.first().paramMap
                        val snapSource = mediaParamMap["SNAP_SOURCE"]?.toString()

                        // Don't show counter in conversation chats or groups
                        if (mediaParamMap.containsKey("MESSAGE_ID")) {
                            this@OperaStoryCounter.context.runOnUiThread {
                                counterState.value = ""
                                sourceState.value = ""
                            }
                            return@hook
                        }

                        // Don't show counter on Spotlight (single snap, no story context)
                        if (snapSource == "SINGLE_SNAP_STORY") {
                            this@OperaStoryCounter.context.runOnUiThread {
                                counterState.value = ""
                                sourceState.value = ""
                            }
                            return@hook
                        }

                        // Extract counter info
                        val currentIndex = mediaParamMap["snap_index_in_story"]?.toString()?.toIntOrNull()
                            ?: mediaParamMap["SNAP_POSITION_IN_STORY"]?.toString()?.toIntOrNull()
                        val totalCount = mediaParamMap["snap_story_length"]?.toString()?.toIntOrNull()
                            ?: mediaParamMap["NUM_SNAPS_IN_STORY"]?.toString()?.toIntOrNull()

                        // Extract media origin from PLAYABLE_STORY_SNAP_RECORD
                        var mediaOrigin = ""
                        if (showSourceIndicator) {
                            val snapRecord = mediaParamMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString() ?: ""
                            mediaOrigin = if (snapRecord.contains("mediaOrigins=")) {
                                if (snapRecord.contains("mediaOrigins=[CAMERA]")) "CAMERA" else "GALLERY"
                            } else ""
                        }

                        this@OperaStoryCounter.context.runOnUiThread {
                            counterState.value = if (showCounter && currentIndex != null && totalCount != null && totalCount > 0) {
                                "${currentIndex + 1} / $totalCount"
                            } else ""
                            sourceState.value = mediaOrigin
                        }
                    }
                }
            }
        }
    }
}
