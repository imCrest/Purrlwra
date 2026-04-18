package cock.crest.purrfectsnap.lite.core.features.impl.ui

import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.Layer
import cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera.ParamMap
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import java.util.ArrayList
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Shared state for Opera Story overlay (snap jump for batch download).
 * Provides story index, total count, and hook setup for display state changes.
 */
class OperaStoryOverlayState {
    val counterState = mutableStateOf("")
    val sourceState = mutableStateOf("")
    val currentIndexState = mutableIntStateOf(-1)
    val totalCountState = mutableIntStateOf(0)
    val snapSourceState = mutableStateOf<String?>(null)
    val isInConversationState = mutableStateOf(false)
    val storyIdentityState = mutableStateOf<String?>(null)

    fun setupDisplayStateHook(
        context: ModContext,
        showCounter: Boolean,
        showSourceIndicator: Boolean,
        onSnapFullyDisplayed: ((Int) -> Unit)?,
        onClearState: (() -> Unit)? = null
    ) {
        context.mappings.useMapper(OperaPageViewControllerMapper::class) {
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

                    if (mediaParamMap.containsKey("MESSAGE_ID") || snapSource == "SINGLE_SNAP_STORY") {
                        context.runOnUiThread {
                            counterState.value = ""
                            sourceState.value = ""
                            currentIndexState.intValue = -1
                            totalCountState.intValue = 0
                            snapSourceState.value = snapSource
                            isInConversationState.value = mediaParamMap.containsKey("MESSAGE_ID")
                            onClearState?.invoke()
                        }
                        return@hook
                    }

                    val currentIndex = mediaParamMap["snap_index_in_story"]?.toString()?.toIntOrNull()
                        ?: mediaParamMap["SNAP_POSITION_IN_STORY"]?.toString()?.toIntOrNull()
                    val totalCount = mediaParamMap["snap_story_length"]?.toString()?.toIntOrNull()
                        ?: mediaParamMap["NUM_SNAPS_IN_STORY"]?.toString()?.toIntOrNull()
                    val storyIdentity = mediaParamMap["STORY_ID"]?.toString()
                        ?.takeIf { it.isNotBlank() && it != "null" }
                        ?: mediaParamMap["TOPIC_SNAP_CREATOR_USER_ID"]?.toString()
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        ?: mediaParamMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
                            ?.substringAfter("storyUserId=", "")
                            ?.substringBefore(",")
                            ?.takeIf { it.isNotBlank() && it != "null" }

                    var mediaOrigin = ""
                    if (showSourceIndicator) {
                        val snapRecord = mediaParamMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString() ?: ""
                        mediaOrigin = if (snapRecord.contains("mediaOrigins=")) {
                            if (snapRecord.contains("mediaOrigins=[CAMERA]")) "CAMERA" else "GALLERY"
                        } else ""
                    }

                    context.runOnUiThread {
                        counterState.value = if (showCounter && currentIndex != null && totalCount != null && totalCount > 0) {
                            "${currentIndex + 1} / $totalCount"
                        } else ""
                        sourceState.value = mediaOrigin
                        currentIndexState.intValue = currentIndex ?: -1
                        totalCountState.intValue = totalCount ?: 0
                        snapSourceState.value = snapSource
                        isInConversationState.value = false
                        storyIdentityState.value = storyIdentity

                        onSnapFullyDisplayed?.let { callback ->
                            if (currentIndex != null) callback(currentIndex)
                        }
                    }
                }
            }
        }
    }
}
