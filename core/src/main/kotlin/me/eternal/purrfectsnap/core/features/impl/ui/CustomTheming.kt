package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField

class CustomTheming : Feature("Custom Theming") {
    private val amoledBlack = 0xFF000000.toInt()

    private val legacyKnownAttrIds = setOf(
        0x7f0404b8 // older Snapchat builds
    )

    private val patchedAttrIds = hashSetOf<Int>()

    private val colorTypes = setOf(
        TypedValue.TYPE_INT_COLOR_ARGB8,
        TypedValue.TYPE_INT_COLOR_RGB8,
        TypedValue.TYPE_INT_COLOR_ARGB4,
        TypedValue.TYPE_INT_COLOR_RGB4
    )

    private fun isNearBlackOpaque(color: Int): Boolean {
        val alpha = (color ushr 24) and 0xFF
        if (alpha != 0xFF) return false
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        val avg = (red + green + blue) / 3
        return avg <= 0x20
    }

    private fun shouldPatch(attrId: Int, attrName: String?, originalColor: Int): Boolean {
        if (originalColor == amoledBlack) return false
        if (attrId in legacyKnownAttrIds) return true
        if (!isNearBlackOpaque(originalColor)) return false

        val name = (attrName ?: return true).lowercase()
        return listOf(
            "background",
            "surface",
            "container",
            "scrim",
            "overlay",
            "sheet",
            "panel"
        ).any { it in name }
    }

    private fun patchTypedArray(result: TypedArray, attrIds: IntArray?) {
        val requestedAttrs = attrIds?.takeIf { it.isNotEmpty() } ?: return
        val typedArrayData = runCatching { result.getObjectField("mData") as IntArray }.getOrNull() ?: return
        val stride = (typedArrayData.size / requestedAttrs.size).takeIf { it >= 2 } ?: return

        requestedAttrs.forEachIndexed { index, attrId ->
            val offset = index * stride
            if (offset + 1 >= typedArrayData.size) return@forEachIndexed

            val type = typedArrayData[offset]
            if (type !in colorTypes) return@forEachIndexed

            val originalColor = runCatching { result.getColor(index, Int.MIN_VALUE) }.getOrNull()
                ?.takeIf { it != Int.MIN_VALUE }
                ?: return@forEachIndexed

            val attrName = runCatching { context.androidContext.resources.getResourceEntryName(attrId) }.getOrNull()
            val shouldPatch = attrId in patchedAttrIds || shouldPatch(attrId, attrName, originalColor)
            if (!shouldPatch) return@forEachIndexed

            typedArrayData[offset + 1] = amoledBlack
            if (patchedAttrIds.add(attrId)) {
                context.log.verbose(
                    "[AMOLED PATCH] Patched attrId 0x${attrId.toString(16)} (${attrName ?: "unknown"}) from 0x${originalColor.toUInt().toString(16)} to AMOLED black"
                )
            }
        }
    }

    private fun patchProgrammaticColor(color: Int): Int {
        return if (isNearBlackOpaque(color) && color != amoledBlack) amoledBlack else color
    }

    override fun init() {
        if (!context.config.userInterface.forceAmoledTheme.get()) return

        onNextActivityCreate {
            context.androidContext.theme.javaClass
                .hook("obtainStyledAttributes", HookStage.AFTER) { param ->
                    val requestedAttrs = param.args().firstOrNull { it is IntArray } as? IntArray
                    val result = param.getResult() as? TypedArray ?: return@hook
                    patchTypedArray(result, requestedAttrs)
                }

            context.androidContext.javaClass
                .hook("obtainStyledAttributes", HookStage.AFTER) { param ->
                    val requestedAttrs = param.args().firstOrNull { it is IntArray } as? IntArray
                    val result = param.getResult() as? TypedArray ?: return@hook
                    patchTypedArray(result, requestedAttrs)
                }

            View::class.java.hook("setBackgroundColor", HookStage.BEFORE) { param ->
                val color = param.argNullable<Int>(0) ?: return@hook
                val patched = patchProgrammaticColor(color)
                if (patched != color) {
                    param.setArg(0, patched)
                }
            }

            ColorDrawable::class.java.hookConstructor(HookStage.BEFORE) { param ->
                val color = param.argNullable<Int>(0) ?: return@hookConstructor
                val patched = patchProgrammaticColor(color)
                if (patched != color) {
                    param.setArg(0, patched)
                }
            }

            ColorDrawable::class.java.hook("setColor", HookStage.BEFORE) { param ->
                val color = param.argNullable<Int>(0) ?: return@hook
                val patched = patchProgrammaticColor(color)
                if (patched != color) {
                    param.setArg(0, patched)
                }
            }
        }
    }
}
