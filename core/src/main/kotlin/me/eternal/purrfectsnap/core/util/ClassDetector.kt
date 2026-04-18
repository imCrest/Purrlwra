package cock.crest.purrfectsnap.lite.core.util

object ClassDetector {
    fun findClassBySignature(
        classLoader: ClassLoader,
        knownNames: List<String>,
        methodSignature: (Class<*>) -> Boolean
    ): Class<*>? {
        for (className in knownNames) {
            runCatching {
                val clazz = classLoader.loadClass(className)
                if (methodSignature(clazz)) return clazz
            }.onFailure { }
        }
        
        for (name in knownNames) {
            val alternative = when {
                name.contains("composer", ignoreCase = true) -> name.replace("composer", "valdi", ignoreCase = true)
                name.contains("valdi", ignoreCase = true) -> name.replace("valdi", "composer", ignoreCase = true)
                else -> null
            }
            alternative?.let {
                runCatching {
                    val clazz = classLoader.loadClass(it)
                    if (methodSignature(clazz)) return clazz
                }.onFailure { }
            }
        }
        
        return null
    }
}


