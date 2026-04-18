package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.MediaDownloader
import cock.crest.purrfectsnap.lite.core.ui.children

import java.lang.ref.WeakReference

/**
 * Main overlay feature for story counter, source indicator, and Auto Skip (snap jump).
 * Also provides snap jump logic for Story Snap List Download batch downloads.
 */
class OperaStoryOverlay : Feature("OperaStoryOverlay") {
    private val overlayState = OperaStoryOverlayState()
    private var storyFrameLayout = WeakReference<ViewGroup>(null)
    private lateinit var snapJump: OperaStorySnapJump

    override fun init() {
        val showCounter = context.config.userInterface.storyCounter.get()
        val showSourceIndicator = context.config.userInterface.storySourceIndicator.get()
        val enableSnapJump = context.config.userInterface.storySnapJump.get()
        val storySnapListDownload = context.config.downloader.storySnapListDownload.get()
        val showDownloadButton = context.config.downloader.operaDownloadButton.get()

        if (!showCounter && !showSourceIndicator && !enableSnapJump && !storySnapListDownload && !showDownloadButton) return

        snapJump = OperaStorySnapJump(context, overlayState) { storyFrameLayout.get() }

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent.javaClass.superclass?.name?.endsWith("OpenLayout") == true) {
                val viewGroup = event.view as? ViewGroup ?: return@subscribe

                val isWrapped = viewGroup is FrameLayout && viewGroup.childCount == 1 && viewGroup.getChildAt(0) is ViewGroup
                val actualLayer = if (isWrapped) viewGroup.getChildAt(0) as ViewGroup else viewGroup

                if (viewGroup.findViewWithTag<View>("story_counter") != null ||
                    event.parent.findViewWithTag<View>("story_counter") != null ||
                    actualLayer.javaClass.name.endsWith("ScalableCircleMaskFrameLayout")
                ) return@subscribe

                if (actualLayer.childCount > 0 && !actualLayer.javaClass.name.contains("OperaShapeView")) {
                    storyFrameLayout = WeakReference(viewGroup as FrameLayout)
                    viewGroup.tag = "story_counter"

                    val composeView = createComposeView(viewGroup.context) {
                        val counterText = overlayState.counterState.value
                        val source = overlayState.sourceState.value
                        val hasCounter = showCounter && counterText.isNotEmpty()
                        val hasSource = showSourceIndicator && source.isNotEmpty()
                        val currentIdx = overlayState.currentIndexState.intValue
                        val totalCount = overlayState.totalCountState.intValue
                        var showJumpDialog by remember { mutableStateOf(false) }

                        val isDownloadButtonEnabled = context.config.downloader.operaDownloadButton.get()

                        if (hasCounter || hasSource || enableSnapJump || isDownloadButtonEnabled) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                if (hasCounter || hasSource || (enableSnapJump && totalCount > 1)) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = Color(0x4C000000),
                                                shape = CircleShape
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            if (hasCounter) {
                                                OperaStoryCounterDisplay(
                                                    counterText = counterText,
                                                    enableSnapJump = enableSnapJump,
                                                    totalCount = totalCount,
                                                    onCounterClick = { showJumpDialog = true }
                                                )
                                            }

                                            if (enableSnapJump && (hasCounter || totalCount > 1) && totalCount > 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(10.dp)
                                                        .background(Color.White.copy(alpha = 0.4f))
                                                )
                                                Icon(
                                                    imageVector = Icons.Outlined.SkipNext,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { showJumpDialog = true }
                                                )
                                            }

                                            if (hasSource) {
                                                if (hasCounter || (enableSnapJump && totalCount > 1)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(1.dp)
                                                            .height(10.dp)
                                                            .background(Color.White.copy(alpha = 0.4f))
                                                    )
                                                }
                                                OperaStorySourceIndicatorDisplay(source = source)
                                            }
                                        }
                                    }
                                }

                                if (isDownloadButtonEnabled) {
                                    val mediaDownloader = remember { context.feature(MediaDownloader::class) }
                                    val snapSource = overlayState.snapSourceState.value
                                    val isInConversation = overlayState.isInConversationState.value
                                    if (snapSource != "SINGLE_SNAP_STORY" && snapSource != "SPOTLIGHT" && snapSource != "PUBLIC_STORY" && !isInConversation) {
                                        if (hasCounter || hasSource || (enableSnapJump && totalCount > 1)) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0x4C000000),
                                                    shape = CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(18.dp)
                                                    .clickable {
                                                        mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = false)
                                                    }
                                            )
                                        }
                                    }
                                }

                                if (enableSnapJump && showJumpDialog && totalCount > 1) {
                                    OperaStorySnapJumpDialog(
                                        currentIndex = currentIdx,
                                        totalCount = totalCount,
                                        onDismiss = { showJumpDialog = false },
                                        onJump = { targetIndex ->
                                            showJumpDialog = false
                                            snapJump.jumpToSnap(targetIndex)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    composeView.tag = "story_counter"
                    composeView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.END
                        topMargin = this@OperaStoryOverlay.context.userInterface.dpToPx(50)
                        marginEnd = this@OperaStoryOverlay.context.userInterface.dpToPx(10)
                    }
                    viewGroup.addView(composeView)
                }
            }
        }

        onNextActivityCreate {
            overlayState.setupDisplayStateHook(
                context = context,
                showCounter = showCounter,
                showSourceIndicator = showSourceIndicator,
                onSnapFullyDisplayed = if (enableSnapJump) {
                    { currentIndex ->
                        if (snapJump.isJumping()) {
                            snapJump.onSnapFullyDisplayed(currentIndex)
                        }
                    }
                } else null,
                onClearState = if (enableSnapJump) { { snapJump.removeJumpOverlay() } } else null
            )
        }
    }

    fun requestJumpToSnap(targetIndex: Int, totalCountOverride: Int? = null): Boolean =
        snapJump.requestJumpToSnap(targetIndex, totalCountOverride)
}

@Composable
private fun OperaStoryCounterDisplay(
    counterText: String,
    enableSnapJump: Boolean,
    totalCount: Int,
    onCounterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (counterText.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(
            if (enableSnapJump && totalCount > 1)
                Modifier.clickable { onCounterClick() }
            else Modifier
        )
    ) {
        Text(
            text = counterText,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

@Composable
private fun OperaStorySourceIndicatorDisplay(
    source: String,
    modifier: Modifier = Modifier
) {
    if (source.isEmpty()) return

    val icon = if (source == "CAMERA") Icons.Outlined.CameraAlt else Icons.Outlined.PhotoLibrary
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier.size(11.dp)
    )
}
