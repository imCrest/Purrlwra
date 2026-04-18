package cock.crest.purrfectsnap.lite.core.util.ktx

fun Any.getObjectField(fieldName: String): Any? {
    return runCatching {
        KavaRefFieldBridge.getField(this, fieldName)
    }.getOrElse {
        findFieldRecursive(this::class.java, fieldName).also { it.isAccessible = true }.get(this)
    }
}

fun Any.findFieldNamesByType(type: Class<*>): List<String> {
    return KavaRefFieldBridge.findFieldNamesByType(this, type).toList()
}

fun Any.allFieldNames(): List<String> {
    return KavaRefFieldBridge.getAllFieldNames(this).toList()
}

fun Class<*>.getStaticObjectField(fieldName: String): Any? {
    return runCatching {
        KavaRefFieldBridge.getStaticField(this, fieldName)
    }.getOrElse {
        findFieldRecursive(this, fieldName).also { it.isAccessible = true }.get(null)
    }
}

fun Class<*>.findStaticObjectFieldByType(type: Class<*>): Any? {
    return KavaRefFieldBridge.findStaticFieldByType(this, type)
}

fun Any.setEnumField(fieldName: String, value: String) {
    val enumType = runCatching {
        KavaRefFieldBridge.getFieldType(this, fieldName)
    }.getOrElse {
        findFieldRecursive(this::class.java, fieldName).type
    }
    enumType.enumConstants?.firstOrNull { it.toString() == value }?.let { enum ->
        setObjectField(fieldName, enum)
    }
}

fun Any.setObjectField(fieldName: String, value: Any?) {
    runCatching {
        KavaRefFieldBridge.setField(this, fieldName, value)
    }.getOrElse {
        findFieldRecursive(this::class.java, fieldName).also { f -> f.isAccessible = true }.set(this, value)
    }
}

fun Any.getObjectFieldOrNull(fieldName: String): Any? {
    return try {
        getObjectField(fieldName)
    } catch (t: Throwable) {
        null
    }
}

private fun findFieldRecursive(clazz: Class<*>, fieldName: String): java.lang.reflect.Field {
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java && current != Object::class.java) {
        runCatching { return current.getDeclaredField(fieldName) }
        current = current.superclass
    }
    throw NoSuchFieldException("${clazz.name}#$fieldName")
}

