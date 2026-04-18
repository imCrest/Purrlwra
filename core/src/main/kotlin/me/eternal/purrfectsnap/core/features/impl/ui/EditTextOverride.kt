package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor

class EditTextOverride : Feature("Edit Text Override") {
    override fun init() {
        val editTextOverride by context.config.userInterface.editTextOverride
        if (editTextOverride.isEmpty()) return

        onNextActivityCreate {
            if (editTextOverride.contains("bypass_text_input_limit")) {
                TextView::class.java.getMethod("setFilters", Array<InputFilter>::class.java)
                    .hook(HookStage.BEFORE) { param ->
                        param.setArg(0, param.arg<Array<InputFilter>>(0).filter {
                            it !is InputFilter.LengthFilter
                        }.toTypedArray())
                    }
            }

            if (editTextOverride.contains("multi_line_chat_input")) {
                findClass("com.snap.messaging.chat.features.input.InputBarEditText").apply {
                    hookConstructor(HookStage.AFTER) { param ->
                        val editText = param.thisObject<EditText>()
                        editText.inputType = editText.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    }
                }
            }
        }
    }
}