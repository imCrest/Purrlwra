package cock.crest.purrfectsnap.lite.common.scripting.ui

import android.app.Activity
import android.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cock.crest.purrfectsnap.lite.common.scripting.bindings.AbstractBinding
import cock.crest.purrfectsnap.lite.common.scripting.bindings.BindingSide
import cock.crest.purrfectsnap.lite.common.scripting.ktx.contextScope
import cock.crest.purrfectsnap.lite.common.scripting.ktx.scriptableObject
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.Node
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.NodeType
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.impl.ActionNode
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.impl.ActionType
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.impl.RowColumnNode
import cock.crest.purrfectsnap.lite.common.scripting.ui.components.impl.TextInputNode
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction


class InterfaceBuilder {
    val nodes = mutableListOf<Node>()
    var onDisposeCallback: (() -> Unit)? = null

    private fun createNode(type: NodeType, block: Node.() -> Unit): Node {
        return Node(type).apply(block).also { nodes.add(it) }
    }

    fun onDispose(block: () -> Unit) {
        nodes.add(ActionNode(ActionType.DISPOSE, callback = block))
    }

    fun onLaunched(block: () -> Unit) {
        onLaunched(Unit, block)
    }

    fun onLaunched(key: Any, block: () -> Unit) {
        nodes.add(ActionNode(ActionType.LAUNCHED, key, block))
    }

    fun row(block: (InterfaceBuilder) -> Unit) = RowColumnNode(NodeType.ROW).apply {
        children.addAll(InterfaceBuilder().apply(block).nodes)
    }.also { nodes.add(it) }

    fun column(block: (InterfaceBuilder) -> Unit) = RowColumnNode(NodeType.COLUMN).apply {
        children.addAll(InterfaceBuilder().apply(block).nodes)
    }.also { nodes.add(it) }

    fun text(text: String) = createNode(NodeType.TEXT) {
        label(text)
    }

    fun switch(state: Boolean?, callback: (Boolean) -> Unit) = createNode(NodeType.SWITCH) {
        attributes["state"] = state
        attributes["callback"] = callback
    }

    fun button(label: String, callback: () -> Unit) = createNode(NodeType.BUTTON) {
        label(label)
        attributes["callback"] = callback
    }

    fun slider(min: Int, max: Int, step: Int, value: Int, callback: (Int) -> Unit) = createNode(
        NodeType.SLIDER
    ) {
        attributes["value"] = value
        attributes["min"] = min
        attributes["max"] = max
        attributes["step"] = step
        attributes["callback"] = callback
    }

    fun list(label: String, items: List<String>, callback: (String) -> Unit) = createNode(NodeType.LIST) {
        label(label)
        attributes["items"] = items
        attributes["callback"] = callback
    }

    fun textInput(placeholder: String, value: String, callback: (String) -> Unit) = TextInputNode().apply {
        placeholder(placeholder)
        value(value)
        callback(callback)
    }.also { nodes.add(it) }
}



@Suppress("unused")
class InterfaceManager : AbstractBinding("interface-manager", BindingSide.COMMON) {
    private val interfaces = mutableMapOf<String, (args: Map<String, Any?>) -> InterfaceBuilder?>()

    fun buildInterface(scriptInterface: EnumScriptInterface, args: Map<String, Any?> = emptyMap()): InterfaceBuilder? {
        return runCatching {
            interfaces[scriptInterface.key]?.invoke(args)
        }.onFailure {
            context.runtime.logger.error("Failed to build interface ${scriptInterface.key} for ${context.moduleInfo.name}", it)
        }.getOrNull()
    }

    override fun onDispose() {
        interfaces.clear()
    }

    fun hasInterface(scriptInterfaces: EnumScriptInterface): Boolean {
        return interfaces.containsKey(scriptInterfaces.key)
    }

    @JSFunction fun create(name: String, callback: Function) {
        interfaces[name] = { args ->
            val interfaceBuilder = InterfaceBuilder()
            runCatching {
                contextScope {
                    callback.call(this, callback, callback, arrayOf(interfaceBuilder, scriptableObject {
                        args.forEach { (key, value) ->
                            putConst(key,this, value)
                        }
                    }))
                }
                interfaceBuilder
            }.onFailure {
                context.runtime.logger.error("Failed to create interface $name for ${context.moduleInfo.name}", it)
            }.getOrNull()
        }
    }

    @JSFunction fun createAlertDialog(activity: Activity, builder: (AlertDialog.Builder) -> Unit, callback: (interfaceBuilder: InterfaceBuilder, alertDialog: AlertDialog) -> Unit): AlertDialog {
        return createComposeAlertDialog(activity, builder = builder) { alertDialog ->
            val interfaceBuilder = remember {
                InterfaceBuilder().also {
                    contextScope {
                        callback(it, alertDialog)
                    }
                }
            }
            val shape = RoundedCornerShape(22.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF8C7BFF).copy(alpha = 0.52f),
                                Color(0xFF5FD8FF).copy(alpha = 0.30f)
                            )
                        ),
                        shape = shape
                    ),
                color = Color.Transparent,
                shape = shape
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF2A2452).copy(alpha = 0.97f),
                                    Color(0xFF1A143A).copy(alpha = 0.94f),
                                )
                            ),
                            shape = shape
                        )
                        .padding(10.dp)
                ) {
                    ScriptInterface(interfaceBuilder = interfaceBuilder)
                }
            }
        }
    }

    @JSFunction fun createAlertDialog(activity: Activity, callback: (interfaceBuilder: InterfaceBuilder, alertDialog: AlertDialog) -> Unit): AlertDialog {
        return createAlertDialog(activity, {}, callback)
    }

    override fun getObject() = this
}
