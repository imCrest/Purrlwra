package cock.crest.purrfectsnap.lite.core.data

import cock.crest.purrfectsnap.lite.core.util.ClassDetector

class SnapClassCache (
    private val classLoader: ClassLoader
) {
    val snapUUID by lazy { findClass("com.snapchat.client.messaging.UUID") }
    val snapShimsUUID by lazy { runCatching { classLoader.loadClass("com.snapchat.client.shims.UUID") }.getOrNull() }
    val snapManager by lazy { findClass("com.snapchat.client.messaging.SnapManager\$CppProxy") }
    val conversationManager by lazy { findClass("com.snapchat.client.messaging.ConversationManager\$CppProxy") }
    val presenceSession by lazy { findClass("com.snapchat.talkcorev3.PresenceSession\$CppProxy") }
    val message by lazy { findClass("com.snapchat.client.messaging.Message") }
    val messageUpdateEnum by lazy { findClass("com.snapchat.client.messaging.MessageUpdate") }
    val serverMessageIdentifier by lazy { findClass("com.snapchat.client.messaging.ServerMessageIdentifier") }
    val unifiedGrpcService by lazy { findClass("com.snapchat.client.grpc.UnifiedGrpcService\$CppProxy") }
    val networkApi by lazy { findClass("com.snapchat.client.network_api.NetworkApi\$CppProxy") }
    val messageDestinations by lazy { findClass("com.snapchat.client.messaging.MessageDestinations") }
    val localMessageContent by lazy { findClass("com.snapchat.client.messaging.LocalMessageContent") }
    val feedEntry by lazy { findClass("com.snapchat.client.messaging.FeedEntry") }
    val conversation by lazy { findClass("com.snapchat.client.messaging.Conversation") }
    val feedManager by lazy { findClass("com.snapchat.client.messaging.FeedManager\$CppProxy") }
    val nativeBridge by lazy { runCatching { findClass("com.snapchat.client.valdi.NativeBridge") }.getOrNull() ?: findClass("com.snapchat.client.composer.NativeBridge") }
    val valdiView by lazy { runCatching { findClass("com.snap.valdi.views.ValdiView") }.getOrNull() ?: runCatching { findClass("com.snap.composer.views.ComposerView") }.getOrNull() }
    val valdiFunction by lazy {
        ClassDetector.findClassBySignature(
            classLoader = classLoader,
            knownNames = listOf(
                "com.snap.valdi.callable.ValdiFunction",
                "com.snap.composer.callable.ComposerFunction"
            ),
            methodSignature = { clazz ->
                clazz.isInterface && clazz.methods.any {
                    it.name == "perform" && it.parameterTypes.size == 1 &&
                    it.returnType == Boolean::class.javaPrimitiveType
                }
            }
        )
    }
    
    val valdiMarshaller by lazy {
        ClassDetector.findClassBySignature(
            classLoader = classLoader,
            knownNames = listOf(
                "com.snap.valdi.utils.ValdiMarshaller",
                "com.snap.composer.utils.ComposerMarshaller"
            ),
            methodSignature = { clazz ->
                !clazz.isInterface && clazz.methods.any { it.name == "getUntyped" }
            }
        )
    }

    val valdiFunctionActionAdapter by lazy {
        ClassDetector.findClassBySignature(
            classLoader = classLoader,
            knownNames = listOf(
                "com.snap.valdi.callable.ValdiFunctionActionAdapter",
                "com.snap.composer.callable.ComposerFunctionActionAdapter"
            ),
            methodSignature = { clazz ->
                !clazz.isInterface && clazz.interfaces.isNotEmpty() &&
                clazz.methods.any { it.name == "perform" && it.parameterTypes.size == 1 }
            }
        )
    }

    private fun findClass(className: String): Class<*> {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Failed to find class $className", e)
        }
    }
}
