package cock.crest.purrfectsnap.lite.ui.util


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Outline
import android.os.Build
import android.provider.Settings
import android.view.*
import android.view.View.OnAttachStateChangeListener
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.addCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.R
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.UUID
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

class DialogProperties constructor(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    val usePlatformDefaultWidth: Boolean = true,
    val decorFitsSystemWindows: Boolean = true
) {

    constructor(
        dismissOnBackPress: Boolean = true,
        dismissOnClickOutside: Boolean = true,
        securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    ) : this(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        securePolicy = securePolicy,
        usePlatformDefaultWidth = true,
        decorFitsSystemWindows = true
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DialogProperties) return false

        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (securePolicy != other.securePolicy) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false
        if (decorFitsSystemWindows != other.decorFitsSystemWindows) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + securePolicy.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        result = 31 * result + decorFitsSystemWindows.hashCode()
        return result
    }
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var forceInline by remember(view) { mutableStateOf(false) }
    val hostActivity = remember(view) { view.context.findActivity() }
    val shouldUseInline = forceInline || hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed
    if (shouldUseInline) {
        InlineDialog(
            onDismissRequest = onDismissRequest,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            content = content
        )
        return
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dialogId = rememberSaveable { UUID.randomUUID() }
    val dialog = remember(view, density) {
        DialogWrapper(
            onDismissRequest,
            properties,
            view,
            layoutDirection,
            density,
            dialogId
        ).apply {
            setContent(composition) {
                // TODO(b/159900354): draw a scrim and add margins around the Compose Dialog, and
                //  consume clicks so they can't pass through to the underlying UI
                DialogLayout(
                    Modifier.semantics { dialog() },
                ) {
                    currentContent()
                }
            }
        }
    }

    DisposableEffect(dialog) {
        val showDialog = {
            try {
                dialog.prepareWindowForHost()
                if (!dialog.isShowing) {
                    dialog.show()
                }
            } catch (_: WindowManager.BadTokenException) {
                forceInline = true
            }
        }

        if (view.isAttachedToWindow || view.windowToken != null || view.rootView?.windowToken != null) {
            showDialog()
        } else {
            val listener = object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    showDialog()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            view.addOnAttachStateChangeListener(listener)
        }

        onDispose {
            dialog.dismiss()
            dialog.disposeComposition()
        }
    }

    SideEffect {
        dialog.updateParameters(
            onDismissRequest = onDismissRequest,
            properties = properties,
            layoutDirection = layoutDirection
        )
    }
}

@Composable
private fun InlineDialog(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val screenWidthDp = with(density) { displayMetrics.widthPixels.toDp() }
    val screenHeightDp = with(density) { displayMetrics.heightPixels.toDp() }
    val interactionSource = remember { MutableInteractionSource() }
    val fallbackBackDispatcherOwner = remember(onDismissRequest) {
        object : OnBackPressedDispatcherOwner {
            private val lifecycleRegistry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            private val dispatcher = OnBackPressedDispatcher(onDismissRequest)

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            override val onBackPressedDispatcher: OnBackPressedDispatcher
                get() = dispatcher
        }
    }
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current ?: fallbackBackDispatcherOwner
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = androidx.compose.ui.Alignment.Center,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside
        ),
        onDismissRequest = onDismissRequest
    ) {
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backDispatcherOwner) {
            Box(
                modifier = Modifier
                    .width(screenWidthDp)
                    .height(screenHeightDp)
                    .then(
                        if (dismissOnClickOutside) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onDismissRequest
                            )
                        } else {
                            Modifier
                        }
                    )
                    .semantics { dialog() },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(180)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)
                    ),
                    exit = fadeOut(animationSpec = tween(120)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(120)
                    )
                ) {
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {}
                        )
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

interface DialogWindowProvider {
    val window: Window
}

@Suppress("ViewConstructor")
private class DialogLayout(
    context: Context,
    override val window: Window
) : AbstractComposeView(context), DialogWindowProvider {

    private var content: @Composable () -> Unit by mutableStateOf({})

    var usePlatformDefaultWidth = false

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    fun setContent(parent: CompositionContext, content: @Composable () -> Unit) {
        setParentCompositionContext(parent)
        this.content = content
        shouldCreateCompositionOnAttachedToWindow = true
        createComposition()
    }

    override fun measureChild(
        child: View?,
        parentWidthMeasureSpec: Int,
        parentHeightMeasureSpec: Int
    ) {

        super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec)
    }

    private val displayWidth: Int
        get() {
            val density = context.resources.displayMetrics.density
            return (context.resources.configuration.screenWidthDp * density).roundToInt()
        }

    private val displayHeight: Int
        get() {
            val density = context.resources.displayMetrics.density
            return (context.resources.configuration.screenHeightDp * density).roundToInt()
        }

    @Composable
    override fun Content() {
        content()
    }
}

@SuppressLint("PrivateResource")
private class DialogWrapper(
    private var onDismissRequest: () -> Unit,
    private var properties: DialogProperties,
    private val composeView: View,
    layoutDirection: LayoutDirection,
    density: Density,
    dialogId: UUID
) : ComponentDialog(
    ContextThemeWrapper(
        composeView.context,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || properties.decorFitsSystemWindows) {
            R.style.DialogWindowTheme
        } else {
            R.style.FloatingDialogWindowTheme
        }
    )
),
    ViewRootForInspector {

    private val dialogLayout: DialogLayout

    // On systems older than Android S, there is a bug in the surface insets matrix math used by
    // elevation, so high values of maxSupportedElevation break accessibility services: b/232788477.
    private val maxSupportedElevation = 8.dp

    override val subCompositionView: AbstractComposeView get() = dialogLayout

    private val defaultSoftInputMode: Int

    init {
        val window = window ?: error("Dialog has no window")
        defaultSoftInputMode =
            window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        window.requestFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        @OptIn(ExperimentalComposeUiApi::class)
        WindowCompat.setDecorFitsSystemWindows(window, properties.decorFitsSystemWindows)
        dialogLayout = DialogLayout(context, window).apply {
            // Set unique id for AbstractComposeView. This allows state restoration for the state
            // defined inside the Dialog via rememberSaveable()
            setTag(R.id.compose_view_saveable_id_tag, "Dialog:$dialogId")
            // Enable children to draw their shadow by not clipping them
            clipChildren = false
            // Allocate space for elevation
            with(density) { elevation = maxSupportedElevation.toPx() }
            // Simple outline to force window manager to allocate space for shadow.
            // Note that the outline affects clickable area for the dismiss listener. In case of
            // shapes like circle the area for dismiss might be to small (rectangular outline
            // consuming clicks outside of the circle).
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, result: Outline) {
                    result.setRect(0, 0, view.width, view.height)
                    // We set alpha to 0 to hide the view's shadow and let the composable to draw
                    // its own shadow. This still enables us to get the extra space needed in the
                    // surface.
                    result.alpha = 0f
                }
            }
        }

        /**
         * Disables clipping for [this] and all its descendant [ViewGroup]s until we reach a
         * [DialogLayout] (the [ViewGroup] containing the Compose hierarchy).
         */
        fun ViewGroup.disableClipping() {
            clipChildren = false
            if (this is DialogLayout) return
            for (i in 0 until childCount) {
                (getChildAt(i) as? ViewGroup)?.disableClipping()
            }
        }

        // Turn of all clipping so shadows can be drawn outside the window
        (window.decorView as? ViewGroup)?.disableClipping()
        setContentView(dialogLayout)
        dialogLayout.setViewTreeLifecycleOwner(composeView.findViewTreeLifecycleOwner())
        dialogLayout.setViewTreeViewModelStoreOwner(composeView.findViewTreeViewModelStoreOwner())
        dialogLayout.setViewTreeSavedStateRegistryOwner(
            composeView.findViewTreeSavedStateRegistryOwner()
        )

        // Initial setup
        updateParameters(onDismissRequest, properties, layoutDirection)

        // Due to how the onDismissRequest callback works
        // (it enforces a just-in-time decision on whether to update the state to hide the dialog)
        // we need to unconditionally add a callback here that is always enabled,
        // meaning we'll never get a system UI controlled predictive back animation
        // for these dialogs
        onBackPressedDispatcher.addCallback(this) {
            if (properties.dismissOnBackPress) {
                onDismissRequest()
            }
        }
    }

    fun prepareWindowForHost() {
        val hostActivity = composeView.context.findActivity()
        val hostToken = composeView.applicationWindowToken
            ?: composeView.windowToken
            ?: composeView.rootView?.applicationWindowToken
            ?: composeView.rootView?.windowToken
        if (hostActivity == null) {
            when {
                hostToken != null -> window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                Settings.canDrawOverlays(composeView.context) -> window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
        }
        if (hostToken != null) {
            window?.attributes = window?.attributes?.apply {
                token = hostToken
            }
        }
        hostActivity?.let { setOwnerActivity(it) }
    }

    private fun setLayoutDirection(layoutDirection: LayoutDirection) {
        dialogLayout.layoutDirection = when (layoutDirection) {
            LayoutDirection.Ltr -> android.util.LayoutDirection.LTR
            LayoutDirection.Rtl -> android.util.LayoutDirection.RTL
        }
    }

    // TODO(b/159900354): Make the Android Dialog full screen and the scrim fully transparent

    fun setContent(parentComposition: CompositionContext, children: @Composable () -> Unit) {
        dialogLayout.setContent(parentComposition, children)
    }

    fun updateParameters(
        onDismissRequest: () -> Unit,
        properties: DialogProperties,
        layoutDirection: LayoutDirection
    ) {
        this.onDismissRequest = onDismissRequest
        this.properties = properties
        setLayoutDirection(layoutDirection)
        val dialogWindow = window
        val canUpdateWindowLayout = dialogWindow?.decorView?.let { decorView ->
            isShowing && decorView.isAttachedToWindow && decorView.windowToken != null
        } == true
        if (canUpdateWindowLayout && properties.usePlatformDefaultWidth && !dialogLayout.usePlatformDefaultWidth) {
            // Undo fixed size in internalOnLayout, which would suppress size changes when
            // usePlatformDefaultWidth is true.
            dialogWindow.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialogLayout.usePlatformDefaultWidth = properties.usePlatformDefaultWidth
        if (canUpdateWindowLayout && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @OptIn(ExperimentalComposeUiApi::class)
            if (properties.decorFitsSystemWindows) {
                dialogWindow?.setSoftInputMode(defaultSoftInputMode)
            } else {
                @Suppress("DEPRECATION")
                dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
    }

    fun disposeComposition() {
        dialogLayout.disposeComposition()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        if (result && properties.dismissOnClickOutside) {
            onDismissRequest()
        }

        return result
    }

    override fun cancel() {
        // Prevents the dialog from dismissing itself
        return
    }
}

@Composable
private fun DialogLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxBy { it.width }.width
        val height = placeables.maxBy { it.height }.height
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}
