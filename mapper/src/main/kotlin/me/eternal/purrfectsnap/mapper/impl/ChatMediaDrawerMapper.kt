package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName
import cock.crest.purrfectsnap.lite.mapper.ext.getSuperClassName

class ChatMediaDrawerMapper : AbstractClassMapper("ChatMediaDrawer") {
    val chatMediaDrawerClass = classReference("chatMediaDrawerClass")
    val actionHandlerClass = classReference("actionHandlerClass")
    val sendItemsMethodName = string("sendItemsMethodName")
    /** List element type for sendItems(List, List) second parameter — used when generic type is erased. */
    val sendItemsListItemClass = classReference("sendItemsListItemClass")

    init {
        mapper {
            val drawerClass = classes.firstOrNull { clazz ->
                val name = clazz.getClassName()
                val superName = clazz.getSuperClassName() ?: return@firstOrNull false
                name.contains("ChatMediaDrawer") &&
                    !name.contains("ActionHandler") &&
                    superName.contains("ValdiGeneratedRootView")
            } ?: return@mapper

            val actionHandlerClazz = classes.firstOrNull { clazz ->
                clazz.getClassName().contains("ChatMediaDrawerActionHandler")
            } ?: return@mapper

            val sendItemsMethod = actionHandlerClazz.methods.firstOrNull { method ->
                method.name == "sendItems" && method.parameterTypes.size == 2
            } ?: actionHandlerClazz.methods.firstOrNull { method ->
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0].startsWith("Ljava/util/") &&
                    method.parameterTypes[1].startsWith("Ljava/util/")
            } ?: return@mapper

            chatMediaDrawerClass.set(drawerClass.getClassName().replace("/", "."))
            actionHandlerClass.set(actionHandlerClazz.getClassName().replace("/", "."))
            sendItemsMethodName.set(sendItemsMethod.name)

            // Find the list item type (e.g. Azc) by structure: has _order and _item (Valdi schema)
            val listItemClass = classes.firstOrNull { clazz ->
                val fieldNames = clazz.fields.map { it.name }.toSet()
                fieldNames.contains("_order") && fieldNames.contains("_item")
            }
            listItemClass?.let { sendItemsListItemClass.set(it.getClassName().replace("/", ".")) }
        }
    }
}
