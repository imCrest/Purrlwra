package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.widget.TextView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.getValdiContext
import cock.crest.purrfectsnap.lite.core.ui.getValdiViewNode
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi.ValdiViewNode

class FakeSnapScore : Feature("Fake Snap Score") {

    private fun findAllSnapTextViewsRecursive(node: ValdiViewNode, depth: Int = 0, result: MutableList<ValdiViewNode> = mutableListOf()): List<ValdiViewNode> {
        if (depth > 15) return result
        if (node.getClassName().endsWith("SnapTextView")) result.add(node)
        for (child in node.getChildren()) {
            findAllSnapTextViewsRecursive(child, depth + 1, result)
        }
        return result
    }

    override fun init() {
        if (context.config.userInterface.spoofSnapScore.globalState != true) return

        val customScoreRaw = context.config.userInterface.spoofSnapScore.customSnapScore.getNullable()?.trim()?.takeIf { it.isNotBlank() }
            ?: return

        val customScore = try {
            val digitsOnly = customScoreRaw.replace(Regex("[^0-9]"), "")
            if (digitsOnly.isNotEmpty()) {
                val clampedVal = digitsOnly.toLong().coerceAtMost(9999999L)
                val formatted = StringBuilder()
                val reversed = clampedVal.toString().reversed()
                for (i in reversed.indices) {
                    formatted.append(reversed[i])
                    if ((i + 1) % 3 == 0 && i != reversed.lastIndex) {
                        formatted.append(",")
                    }
                }
                formatted.reverse().toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } ?: return

        // Approach 1: AddViewEvent + Valdi setAttribute (score dialog when tapping pill)
        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.viewClassName.endsWith("ProfileFlatlandmySnapScoreIdentityPillDialogView")) {
                event.view.post {
                    event.view.getValdiContext()?.enqueueNextRenderCallback {
                        val rootNode = event.view.getValdiViewNode() ?: return@enqueueNextRenderCallback
                        val snapTextViews = findAllSnapTextViewsRecursive(rootNode)
                        // Only spoof the blue pill (2nd), white shows original score
                        snapTextViews.getOrNull(1)?.setAttribute("value", customScore)
                        event.view.postInvalidate()
                    }
                    event.view.postInvalidate()
                }
            }
        }

        // Approach 2: TextView.setText hook as fallback - text change only, no layout modifications
        onNextActivityCreate {
            TextView::class.java.hook("setText", HookStage.BEFORE) { param ->
                val text = param.argNullable<CharSequence>(0)?.toString() ?: return@hook
                if (!text.matches(Regex("^[0-9\\s,.]+$"))) return@hook

                val digits = text.replace(Regex("[^0-9]"), "")
                if (digits.length < 4 && !text.contains(",")) return@hook

                val textView = param.thisObject() as TextView
                var parent = textView.parent
                var isMyProfile = false
                var isFriendContext = false
                var isInScoreDialog = false

                while (parent != null) {
                    val fullName = parent.javaClass.name.lowercase()
                    if (fullName.contains("friendsnapscore") || fullName.contains("friendprofile")) {
                        isFriendContext = true
                        break
                    }
                    if (fullName.contains("mysnapscore") || fullName.contains("myprofile")) {
                        isMyProfile = true
                    }
                    if (fullName.contains("mysnapscoreidentitypilldialog")) isInScoreDialog = true
                    parent = parent.parent
                }

                // Only spoof blue pill in profile (not in dialog - AddViewEvent handles that). White always shows original.
                if (isMyProfile && !isFriendContext && !isInScoreDialog) {
                    param.setArg(0, customScore)
                    // Prevent ellipsis (...) when score is large - fix TextView and parent TextViews (white + blue pill)
                    textView.post {
                        val minW = textView.paint.measureText(customScore).toInt() + 80
                        var current: android.view.View? = textView
                        for (i in 0..4) {
                            if (current == null) break
                            if (current is TextView) {
                                current.ellipsize = null
                                current.maxWidth = Int.MAX_VALUE
                                current.minWidth = minW
                                current.minimumWidth = minW
                            }
                            current = current.parent as? android.view.View
                            current?.requestLayout()
                        }
                    }
                }
            }
        }
    }
}
