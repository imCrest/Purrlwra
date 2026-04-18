package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.OnSnapInteractionEvent
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.MediaDownloader
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.OperaViewerMessageContext
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.AutoMarkAsRead
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.ui.iterateParent
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.ui.randomTag
import cock.crest.purrfectsnap.lite.core.ui.triggerCloseTouchEvent
import cock.crest.purrfectsnap.lite.core.ui.triggerCloseTouchEventAtFraction
import cock.crest.purrfectsnap.lite.core.util.SNAPCHAT_13_80_VERSION
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.isSnapchatVersionAtLeast
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress
import cock.crest.purrfectsnap.lite.mapper.impl.OperaPageViewControllerMapper
import java.util.concurrent.atomic.AtomicInteger

class OperaViewerIcons : AbstractMenu() {
    private val actionMenuIconSize by lazy { context.userInterface.dpToPx(32) }
    private val actionMenuIconMargin by lazy { context.userInterface.dpToPx(5) }
    private val actionMenuIconMarginTop by lazy { context.userInterface.dpToPx(10) }
    private val viewerVisibleState = mutableStateOf(false)
    private val viewerMessageContextState = mutableStateOf<OperaViewerMessageContext?>(null)
    private val inlineDownloadButtonVisibleState = mutableStateOf(false)
    private val inlineMarkButtonVisibleState = mutableStateOf(false)
    private var overlayRegistered = false
    private var hooksInitialized = false
    private var hasSeenVisibleModernViewerContainer = false
    private val modernViewerHideToken = AtomicInteger(0)
    private val useModernViewerBehavior by lazy {
        isSnapchatVersionAtLeast(
            context.mappings.getSnapchatPackageInfo()?.versionName,
            SNAPCHAT_13_80_VERSION
        )
    }

    override fun init() {
        if (!useModernViewerBehavior) return
        if (hooksInitialized) return
        hooksInitialized = true

        registerOverlayFallback()
        val mediaDownloader = context.feature(MediaDownloader::class)

        context.event.subscribe(OnSnapInteractionEvent::class) {
            refreshViewerMessageContext(mediaDownloader)
        }

        context.mappings.useMapper(OperaPageViewControllerMapper::class) {
            arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                classReference.get()?.hook(
                    methodName.get() ?: return@forEach,
                    HookStage.AFTER
                ) { param ->
                    val viewState = param.thisObject<Any>().getObjectField(viewStateField.get()!!).toString()
                    val isVisible = viewState == "FULLY_DISPLAYED"

                    if (!isVisible) {
                        scheduleHideIfViewerActuallyClosed()
                        return@hook
                    }

                    modernViewerHideToken.incrementAndGet()
                    viewerVisibleState.value = true
                    refreshViewerMessageContext(mediaDownloader)
                }
            }
        }
    }

    private fun refreshViewerMessageContext(
        mediaDownloader: MediaDownloader,
        retryCount: Int = 4
    ) {
        context.coroutineScope.launch(Dispatchers.Main) {
            repeat(retryCount) { attempt ->
                mediaDownloader.resolveViewerMessageContextFromParamMap()?.let {
                    viewerMessageContextState.value = it
                    return@launch
                }
                if (attempt < retryCount - 1) {
                    delay(120L * (attempt + 1))
                }
            }
            viewerMessageContextState.value = null
        }
    }

    @Composable
    private fun OverlayActionButton(
        icon: ImageVector,
        onTap: () -> Unit,
        onLongPress: (() -> Unit)? = null
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .pointerInput(onLongPress) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = {
                            onLongPress?.invoke()
                        }
                    )
                },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    tint = Color.White,
                    contentDescription = null
                )
            }
        }
    }

    private fun registerOverlayFallback() {
        if (overlayRegistered) return
        overlayRegistered = true

        context.inAppOverlay.addCustomComposable {
            val mediaDownloader = context.feature(MediaDownloader::class)
            val messageContext = viewerMessageContextState.value
            if (
                !viewerVisibleState.value ||
                messageContext == null
            ) return@addCustomComposable

            LaunchedEffect(messageContext) {
                var hiddenChecks = 0
                while (viewerVisibleState.value && viewerMessageContextState.value == messageContext) {
                    delay(160)
                    if (hasVisibleModernViewerContainer()) {
                        hasSeenVisibleModernViewerContainer = true
                        hiddenChecks = 0
                        continue
                    }

                    if (!hasSeenVisibleModernViewerContainer) continue

                    hiddenChecks++
                    if (hiddenChecks >= 2) {
                        clearModernViewerState()
                        break
                    }
                }
            }

            val showDownloadFallback = context.config.downloader.operaDownloadButton.get() &&
                !inlineDownloadButtonVisibleState.value
            val showMarkFallback = context.config.messaging.markSnapAsSeenButton.get() &&
                !inlineMarkButtonVisibleState.value

            if (!showDownloadFallback && !showMarkFallback) return@addCustomComposable

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 18.dp, bottom = 118.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (showDownloadFallback) {
                        OverlayActionButton(
                            icon = Icons.Outlined.Download,
                            onTap = {
                                mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = false)
                            },
                            onLongPress = {
                                context.androidContext.vibrateLongPress()
                                mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = true)
                            }
                        )
                    }

                    if (showMarkFallback) {
                        OverlayActionButton(
                            icon = Icons.Default.RemoveRedEye,
                            onTap = {
                                context.coroutineScope.launch {
                                    markCurrentSnapAsSeen(parent = null)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun clearModernViewerState() {
        modernViewerHideToken.incrementAndGet()
        viewerVisibleState.value = false
        viewerMessageContextState.value = null
        inlineDownloadButtonVisibleState.value = false
        inlineMarkButtonVisibleState.value = false
        hasSeenVisibleModernViewerContainer = false
    }

    private fun scheduleHideIfViewerActuallyClosed() {
        val token = modernViewerHideToken.incrementAndGet()
        context.coroutineScope.launch(Dispatchers.Main) {
            delay(240)
            if (modernViewerHideToken.get() != token) return@launch
            if (hasVisibleModernViewerContainer()) return@launch
            clearModernViewerState()
        }
    }

    private fun isActuallyVisible(view: View): Boolean {
        val visibleRect = Rect()
        return view.isShown &&
            view.getGlobalVisibleRect(visibleRect) &&
            visibleRect.height() > 0 &&
            visibleRect.width() > 0
    }

    private fun findVisibleOpenLayout(view: View): View? {
        if (view.javaClass.hasNameSuffixInHierarchy("OpenLayout") && isActuallyVisible(view)) {
            return view
        }

        val viewGroup = view as? ViewGroup ?: return null
        for (index in 0 until viewGroup.childCount) {
            findVisibleOpenLayout(viewGroup.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun findVisibleModernViewerContainer(): View? {
        val contentView = context.mainActivity?.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findVisibleOpenLayout(contentView)
    }

    private fun hasVisibleModernViewerContainer(): Boolean {
        return findVisibleModernViewerContainer() != null
    }

    private fun currentViewerMessageContext(mediaDownloader: MediaDownloader): OperaViewerMessageContext? {
        return mediaDownloader.resolveViewerMessageContextFromParamMap()?.also {
            viewerMessageContextState.value = it
        } ?: viewerMessageContextState.value
    }

    private fun hasViewerAdvanced(
        mediaDownloader: MediaDownloader,
        originalMessageContext: OperaViewerMessageContext
    ): Boolean {
        if (!hasVisibleModernViewerContainer()) {
            return true
        }

        return currentViewerMessageContext(mediaDownloader)?.let { it != originalMessageContext } == true
    }

    private suspend fun waitForViewerAdvance(
        mediaDownloader: MediaDownloader,
        originalMessageContext: OperaViewerMessageContext,
        timeoutMs: Long
    ): Boolean {
        var elapsedMs = 0L
        while (elapsedMs < timeoutMs) {
            delay(40)
            elapsedMs += 40
            if (hasViewerAdvanced(mediaDownloader, originalMessageContext)) {
                return true
            }
        }

        return hasViewerAdvanced(mediaDownloader, originalMessageContext)
    }

    private fun dispatchLegacySkipGesture(parent: ViewGroup?) {
        if (parent != null) {
            var touchedParent = false
            parent.iterateParent {
                touchedParent = true
                it.triggerCloseTouchEvent()
                false
            }
            if (touchedParent) return
        }

        context.mainActivity
            ?.findViewById<View>(android.R.id.content)
            ?.triggerCloseTouchEvent()
    }

    private fun dispatchForwardHotZoneTap(target: View?, xFraction: Float) {
        target?.triggerCloseTouchEventAtFraction(xFraction = xFraction, yFraction = 0.5f)
    }

    private suspend fun skipMarkedSnap(
        parent: ViewGroup?,
        mediaDownloader: MediaDownloader,
        originalMessageContext: OperaViewerMessageContext
    ) {
        val contentView = context.mainActivity?.findViewById<ViewGroup>(android.R.id.content)
        val skipAttempts = listOf<suspend () -> Unit>(
            {
                dispatchLegacySkipGesture(parent)
            },
            {
                dispatchForwardHotZoneTap(findVisibleModernViewerContainer() ?: contentView, 0.88f)
            },
            {
                dispatchForwardHotZoneTap(contentView ?: findVisibleModernViewerContainer(), 0.88f)
            },
            {
                val target = findVisibleModernViewerContainer() ?: contentView
                dispatchForwardHotZoneTap(target, 0.88f)
                delay(55)
                if (!hasViewerAdvanced(mediaDownloader, originalMessageContext)) {
                    dispatchForwardHotZoneTap(target, 0.94f)
                }
            }
        )

        for ((index, attempt) in skipAttempts.withIndex()) {
            attempt()
            if (waitForViewerAdvance(mediaDownloader, originalMessageContext, if (index == 0) 120L else 180L)) {
                return
            }
        }
    }

    private fun Class<*>?.hasNameSuffixInHierarchy(suffix: String): Boolean {
        var current = this
        while (current != null) {
            if (current.name.endsWith(suffix)) return true
            current = current.superclass
        }
        return false
    }

    private fun shouldInjectIntoViewer(event: AddViewEvent): Boolean {
        if (event.view !is FrameLayout) return false
        if (!event.parent.javaClass.hasNameSuffixInHierarchy("OpenLayout")) return false

        val viewGroup = event.view as? ViewGroup ?: return false

        val hasOnlyImageChildren = viewGroup.childCount > 0 && viewGroup.children().all { it is ImageView }
        val hasMaskFrameSibling = (event.parent as? ViewGroup)?.children()?.any {
            it.javaClass.hasNameSuffixInHierarchy("ScalableCircleMaskFrameLayout")
        } == true

        return hasOnlyImageChildren || hasMaskFrameSibling
    }

    private fun resolveCurrentMessageContext(mediaDownloader: MediaDownloader): OperaViewerMessageContext? {
        return mediaDownloader.resolveViewerMessageContextFromParamMap()?.also {
            viewerMessageContextState.value = it
        }
    }

    private fun hasPreviewToolbar(parent: ViewGroup): Boolean {
        return (parent.parent as? ViewGroup)?.children()?.any { child ->
            child is ViewGroup && child.children().any { it::class.java.name.endsWith("PreviewToolbar") }
        } == true
    }

    private fun syncInlineDownloadButtonVisibility(view: View, mediaDownloader: MediaDownloader, parent: ViewGroup) {
        val isVisible = resolveCurrentMessageContext(mediaDownloader) != null && !hasPreviewToolbar(parent)
        view.visibility = if (isVisible) View.VISIBLE else View.GONE
        inlineDownloadButtonVisibleState.value = isVisible
    }

    private fun syncInlineMarkButtonVisibility(view: View, mediaDownloader: MediaDownloader) {
        val isVisible = resolveCurrentMessageContext(mediaDownloader) != null
        view.visibility = if (isVisible) View.VISIBLE else View.GONE
        inlineMarkButtonVisibleState.value = isVisible
    }

    private suspend fun markCurrentSnapAsSeen(parent: ViewGroup?) {
        val mediaDownloader = context.feature(MediaDownloader::class)
        val messageContext = resolveCurrentMessageContext(mediaDownloader) ?: return
        val result = context.feature(AutoMarkAsRead::class).markSnapAsSeen(
            messageContext.conversationId,
            messageContext.clientMessageId
        )

        if (result == "DUPLICATEREQUEST" || result == null) {
            if (context.config.messaging.skipWhenMarkingAsSeen.get()) {
                withContext(Dispatchers.Main) {
                    skipMarkedSnap(parent, mediaDownloader, messageContext)
                }
            }
        }

        if (result == "DUPLICATEREQUEST") return
        if (result == null) {
            context.inAppOverlay.showStatusToast(
                Icons.Default.Info,
                context.translation["mark_as_seen.seen_toast"],
                durationMs = 800
            )
        } else {
            context.inAppOverlay.showStatusToast(
                Icons.Default.Info,
                "Failed to mark as seen: $result",
            )
        }
    }

    override fun onViewAdded(event: AddViewEvent) {
        if (useModernViewerBehavior) {
            if (shouldInjectIntoViewer(event)) {
                modernViewerHideToken.incrementAndGet()
                viewerVisibleState.value = true
                refreshViewerMessageContext(context.feature(MediaDownloader::class))
            }
            return
        }
        if (event.view is FrameLayout && event.parent.javaClass.superclass?.name?.endsWith("OpenLayout") == true) {
            val viewGroup = event.view as? ViewGroup ?: return
            if (
                viewGroup.childCount == 0 ||
                viewGroup.children().any { it !is ImageView } ||
                event.parent.children().none { it.javaClass.name.endsWith("ScalableCircleMaskFrameLayout") }
            ) return
            inject(viewGroup)
        }
    }

    private fun inject(parent: ViewGroup) {
        val mediaDownloader = context.feature(MediaDownloader::class)

        if (context.config.downloader.operaDownloadButton.get()) {
            parent.addView(LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, actionMenuIconMarginTop * 2 + actionMenuIconSize, 0, 0)
                    marginEnd = actionMenuIconMargin
                    gravity = Gravity.TOP or Gravity.END
                }
                addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        inlineDownloadButtonVisibleState.value = false
                        this@OperaViewerIcons.context.coroutineScope.launch(Dispatchers.Main) {
                            delay(250)
                            syncInlineDownloadButtonVisibility(v, mediaDownloader, parent)
                        }
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        inlineDownloadButtonVisibleState.value = false
                    }
                })

                addView(createComposeView(parent.context) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        tint = Color.White,
                        contentDescription = null
                    )
                }.apply {
                    setOnClickListener {
                        mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = false)
                    }
                    setOnLongClickListener {
                        context.vibrateLongPress()
                        mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = true)
                        true
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        actionMenuIconSize,
                        actionMenuIconSize
                    ).apply {
                        setMargins(0, 0, 0, actionMenuIconMargin * 2)
                    }
                })
            }, 0)
        }

        if (context.config.messaging.markSnapAsSeenButton.get()) {
            if (!useModernViewerBehavior) {
                parent.addView(createComposeView(parent.context)  {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        tint = Color.White,
                        contentDescription = null
                    )
                }.apply {
                    setOnClickListener {
                        this@OperaViewerIcons.context.coroutineScope.launch {
                            markCurrentSnapAsSeen(parent)
                        }
                    }

                    addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            v.visibility = View.GONE
                            this@OperaViewerIcons.context.coroutineScope.launch(Dispatchers.Main) {
                                delay(250)
                                v.visibility = if (resolveCurrentMessageContext(mediaDownloader) != null) View.VISIBLE else View.GONE
                            }
                        }

                        override fun onViewDetachedFromWindow(v: View) {}
                    })

                    layoutParams = FrameLayout.LayoutParams(
                        (actionMenuIconSize * 1.5).toInt(),
                        (actionMenuIconSize * 1.5).toInt()
                    ).apply {
                        setMargins(0, 0, 0, actionMenuIconMarginTop * 2 + this@OperaViewerIcons.context.userInterface.dpToPx(80))
                        marginEnd = actionMenuIconMarginTop * 2
                        marginStart = actionMenuIconMarginTop * 2
                        gravity = Gravity.BOTTOM or Gravity.END
                    }
                })
                return
            }

            parent.addView(createComposeView(parent.context)  {
                Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    tint = Color.White,
                    contentDescription = null
                )
            }.apply {
                setOnClickListener {
                    this@OperaViewerIcons.context.coroutineScope.launch {
                        markCurrentSnapAsSeen(parent)
                    }
                }

                addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.visibility = View.GONE
                        inlineMarkButtonVisibleState.value = false
                        this@OperaViewerIcons.context.coroutineScope.launch(Dispatchers.Main) {
                            delay(250)
                            syncInlineMarkButtonVisibility(v, mediaDownloader)
                        }
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        inlineMarkButtonVisibleState.value = false
                    }
                })

                layoutParams = FrameLayout.LayoutParams(
                    (actionMenuIconSize * 1.5).toInt(),
                    (actionMenuIconSize * 1.5).toInt()
                ).apply {
                    setMargins(0, 0, 0, actionMenuIconMarginTop * 2 + this@OperaViewerIcons.context.userInterface.dpToPx(80))
                    marginEnd = actionMenuIconMarginTop * 2
                    marginStart = actionMenuIconMarginTop * 2
                    gravity = Gravity.BOTTOM or Gravity.END
                }
            })
        }
    }
}
