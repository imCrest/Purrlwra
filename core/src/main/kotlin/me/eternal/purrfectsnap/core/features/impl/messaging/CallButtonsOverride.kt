package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.PurrfectGlassCard
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayPalette
import cock.crest.purrfectsnap.lite.core.ui.PurrfectOverlayTheme
import cock.crest.purrfectsnap.lite.core.ui.ViewAppearanceHelper
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.ui.hideViewCompletely
import cock.crest.purrfectsnap.lite.core.util.hook.HookAdapter
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class CallButtonsOverride : Feature("CallButtonsOverride") {
    private fun hookTouchEvent(param: HookAdapter, motionEvent: MotionEvent, onConfirm: () -> Unit) {
        if (motionEvent.action != MotionEvent.ACTION_UP) return
        param.setResult(true)
        createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
            PurrfectOverlayTheme {
                PurrfectGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = context.translation["call_start_confirmation.dialog_title"],
                    subtitle = context.translation["call_start_confirmation.dialog_message"],
                    icon = Icons.Default.Call
                ) {
                    val actionShape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally)
                    ) {
                        Button(
                            modifier = Modifier.width(120.dp),
                            onClick = { alertDialog.dismiss() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(context.translation["button.negative"])
                        }
                        Button(
                            modifier = Modifier.width(120.dp),
                            onClick = {
                                alertDialog.dismiss()
                                onConfirm()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.26f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(context.translation["button.positive"])
                        }
                    }
                }
            }
        }.show()
    }

    override fun init() {
        val hideUiComponents by context.config.userInterface.hideUiComponents
        val blockCalls = context.config.messaging.blockCalls.get()

        val hideProfileCallButtons = blockCalls || hideUiComponents.contains("hide_profile_call_buttons")
        val hideChatCallButtons = blockCalls || hideUiComponents.contains("hide_chat_call_buttons")
        val callStartConfirmation = context.config.messaging.callStartConfirmation.get()

        if (!hideProfileCallButtons && !hideChatCallButtons && !callStartConfirmation && !blockCalls) return

        var actionSheetVideoCallButtonId = -1
        var actionSheetAudioCallButtonId = -1

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.viewClassName.endsWith("ConstraintLayout")) {
                val layout = event.view as? ViewGroup ?: return@subscribe
                val children = layout.children()
                if (children.any { !it.javaClass.name.endsWith("FriendActionButton") } || children.size != 4) return@subscribe

                actionSheetVideoCallButtonId = children.getOrNull(2)?.id ?: throw IllegalStateException("Video call button not found")
                actionSheetAudioCallButtonId = children.getOrNull(3)?.id ?: throw IllegalStateException("Audio call button not found")

                if (hideProfileCallButtons) {
                    children.getOrNull(2)?.hideViewCompletely()
                    children.getOrNull(3)?.hideViewCompletely()
                }
            }

            if (event.viewClassName.endsWith("CallButtonsView") && hideChatCallButtons) {
                event.view.hideViewCompletely()
            }
        }

        onNextActivityCreate {
            if (callStartConfirmation || blockCalls) {
                (runCatching { findClass("com.snap.valdi.views.ValdiRootView") }.getOrNull()
                    ?: findClass("com.snap.composer.views.ComposerRootView"))
                    .hook("dispatchTouchEvent", HookStage.BEFORE) { param ->
                    val view = param.thisObject() as? ViewGroup ?: return@hook
                    if (!view.javaClass.name.endsWith("CallButtonsView")) return@hook
                    val childComposerView = view.getChildAt(0) as? ViewGroup ?: return@hook
                    // check if the child composer view contains 2 call buttons
                    if (childComposerView.children().count {
                            it::class.java == childComposerView::class.java
                        } != 2) return@hook
                    if (blockCalls) {
                        param.setResult(true)
                        return@hook
                    }
                    hookTouchEvent(param, param.arg(0)) {
                        param.invokeOriginal()
                    }
                }

                findClass("com.snap.ui.view.stackdraw.StackDrawLayout").hook("onTouchEvent", HookStage.BEFORE) { param ->
                    val view = param.thisObject<View>().takeIf { it.id != -1 } ?: return@hook
                    if (view.id != actionSheetAudioCallButtonId && view.id != actionSheetVideoCallButtonId) return@hook

                    if (blockCalls) {
                        param.setResult(true)
                        return@hook
                    }
                    hookTouchEvent(param, param.arg(0)) {
                        arrayOf(
                            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0),
                            MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
                        ).forEach {
                            param.invokeOriginal(arrayOf(it))
                        }
                    }
                }
            }
        }
    }
}
