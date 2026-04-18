package cock.crest.purrfectsnap.lite.mapper.impl

import cock.crest.purrfectsnap.lite.mapper.AbstractClassMapper
import cock.crest.purrfectsnap.lite.mapper.ext.findConstString
import cock.crest.purrfectsnap.lite.mapper.ext.getClassName
import cock.crest.purrfectsnap.lite.mapper.ext.isAbstract
import cock.crest.purrfectsnap.lite.mapper.ext.isEnum
import java.lang.reflect.Modifier

class FriendRelationshipChangerMapper : AbstractClassMapper("FriendRelationshipChanger") {
    val classReference = classReference("class")

    val friendshipRelationshipChangerKtx = classReference("removeFriendClass")
    val addFriendMethod = string("addFriendMethod")
    val runFriendDurableJob = string("runFriendDurableJob")

    val helperClass = classReference("helper")
    val addFriend14Method = string("addFriend14Method")
    val sourceType = classReference("sourceType")
    val pageType = classReference("pageType")

    init {
        mapper {
            var frcClassDef: com.android.tools.smali.dexlib2.iface.ClassDef? = null
            for (classDef in classes) {
                if (classDef.methods.any { it.name == "<init>" && it.implementation?.findConstString("FriendRelationshipChangerImpl") == true }) {
                    frcClassDef = classDef
                    classReference.set(classDef.getClassName())

                    runFriendDurableJob.set(classDef.methods.firstOrNull {
                        Modifier.isStatic(it.accessFlags) &&
                                it.returnType.contains("CompletableAndThenCompletable") &&
                                it.parameterTypes.size == 5 &&
                                it.parameterTypes[0] == classDef.type &&
                                it.parameterTypes[1] == "Ljava/lang/String;" &&
                                it.parameterTypes[3] == "I" &&
                                it.parameterTypes[4] == "Ljava/lang/String;"
                    }?.name)
                    break
                }
            }

            val frcType = frcClassDef?.type ?: return@mapper
            val frcInterfaces = frcClassDef.interfaces

            for (classDef in classes) {
                if (helperClass.get() == null) {
                    val method = classDef.methods.firstOrNull {
                        Modifier.isStatic(it.accessFlags) &&
                        it.parameterTypes.size == 14 &&
                        (it.parameterTypes[0] == frcType || frcInterfaces.contains(it.parameterTypes[0])) &&
                        it.parameterTypes[1] == "Ljava/lang/String;" &&
                        getClass(it.parameterTypes[2])?.isEnum() == true
                    }
                    if (method != null) {
                        helperClass.set(classDef.getClassName())
                        addFriend14Method.set(method.name)
                        sourceType.set(getClass(method.parameterTypes[3])?.getClassName())
                        pageType.set(getClass(method.parameterTypes[4])?.getClassName())
                    }
                }

                if (friendshipRelationshipChangerKtx.get() == null && classDef.isAbstract()) {
                    val addFriendDexMethod = classDef.methods.firstOrNull {
                        Modifier.isStatic(it.accessFlags) &&
                        it.parameterTypes.size == 6 &&
                        it.parameterTypes[1] == "Ljava/lang/String;" &&
                        getClass(it.parameterTypes[2])?.isEnum() == true &&
                        getClass(it.parameterTypes[4])?.isEnum() == true &&
                        it.parameterTypes[5] == "I"
                    }
                    if (addFriendDexMethod != null) {
                        friendshipRelationshipChangerKtx.set(classDef.getClassName())
                        addFriendMethod.set(addFriendDexMethod.name)
                    }
                }
            }
        }
    }
}
