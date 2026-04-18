package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.view.View
import android.view.ViewGroup
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.getValdiContext
import cock.crest.purrfectsnap.lite.core.ui.getValdiViewNode
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.valdi.ValdiViewNode

class FirstCreatedUsername : Feature("FirstCreatedUsername") {
    private val profileViewSuffixes = setOf(
        "UnifiedPublicProfileView",
        "UserProfileV2RootComponent",
        "UnifiedProfileFlatlandProfileView",
        "UnifiedProfileFlatlandProfileViewTopViewFrameLayout",
        "ProfileFlatlandFriendSnapScoreIdentityPillDialogView"
    )

    private val usernameFieldNames = listOf(
        "_username",
        "username",
        "_mutableUsername",
        "mutableUsername",
        "_publicUsername",
        "publicUsername",
        "usernameForSorting"
    )

    private val nestedUsernameContainers = listOf(
        "_user",
        "user",
        "_userInfo",
        "userInfo",
        "_friend",
        "friend",
        "_identity",
        "identity",
        "_profile",
        "profile"
    )

    override fun init() {
        if (!context.config.experimental.nativeHooks.valdiHooks.showFirstCreatedUsername.get()) return

        context.event.subscribe(AddViewEvent::class) { event ->
            if (profileViewSuffixes.none { event.viewClassName.endsWith(it) }) return@subscribe
            event.view.post {
                patchProfileView(event.view)
            }
        }
    }

    private fun patchProfileView(view: View) {
        val valdiHost = sequenceOf(view, (view as? ViewGroup)?.getChildAt(0))
            .filterNotNull()
            .firstOrNull { it.getValdiContext() != null } ?: return

        val valdiContext = valdiHost.getValdiContext() ?: return
        val primaryViewModel = valdiContext.viewModel ?: valdiContext.viewModelLegacy ?: return
        val legacyViewModel = valdiContext.viewModelLegacy

        val userId = primaryViewModel.findUserId()
            ?: legacyViewModel?.findUserId()

        val currentUsername = primaryViewModel.findUsername()
            ?: legacyViewModel?.findUsername()
            ?: userId?.let { context.database.getFriendInfo(it)?.mutableUsername }
            ?: return

        val normalizedCurrentUsername = normalizeUsername(currentUsername) ?: return
        val firstCreatedUsername = resolveFirstCreatedUsername(userId, normalizedCurrentUsername) ?: return
        if (firstCreatedUsername == normalizedCurrentUsername) return

        val decoratedUsername = "$normalizedCurrentUsername ($firstCreatedUsername)"

        var updated = primaryViewModel.applyUsernameOverride(normalizedCurrentUsername, decoratedUsername)
        if (legacyViewModel != null && legacyViewModel !== primaryViewModel) {
            updated = legacyViewModel.applyUsernameOverride(normalizedCurrentUsername, decoratedUsername) || updated
        }

        valdiContext.enqueueNextRenderCallback {
            val rootNode = valdiHost.getValdiViewNode() ?: return@enqueueNextRenderCallback
            if (rootNode.applyRenderedUsernameOverride(normalizedCurrentUsername, decoratedUsername) || updated) {
                view.postInvalidate()
            }
        }
    }

    private fun resolveFirstCreatedUsername(userId: String?, currentUsername: String): String? {
        context.database.getFriendOriginalUsername(currentUsername)
            ?.takeIf { it.isNotBlank() && it != currentUsername }
            ?.let { return it }

        val friendInfo = userId?.let(context.database::getFriendInfo)
            ?: context.database.getFriendInfoByUsername(currentUsername)

        return friendInfo
            ?.firstCreatedUsername
            ?.takeIf { it.isNotBlank() && it != currentUsername }
    }

    private fun Any.findUserId(): String? {
        return getObjectFieldOrNull("_userId").asSafeString()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: getObjectFieldOrNull("userId").asSafeString()
                ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun Any.findUsername(): String? {
        findUsernameInObject(this)?.let { return it }
        nestedUsernameContainers.forEach { fieldName ->
            val nestedObject = getObjectFieldOrNull(fieldName) ?: return@forEach
            findUsernameInObject(nestedObject)?.let { return it }
        }
        return null
    }

    private fun findUsernameInObject(target: Any): String? {
        usernameFieldNames.forEach { fieldName ->
            val value = target.getObjectFieldOrNull(fieldName).asSafeString() ?: return@forEach
            normalizeUsername(value)?.let { return it }
        }
        return null
    }

    private fun Any.applyUsernameOverride(currentUsername: String, decoratedUsername: String): Boolean {
        var changed = applyUsernameOverrideToObject(this, currentUsername, decoratedUsername)
        nestedUsernameContainers.forEach { fieldName ->
            val nestedObject = getObjectFieldOrNull(fieldName) ?: return@forEach
            changed = applyUsernameOverrideToObject(nestedObject, currentUsername, decoratedUsername) || changed
        }
        return changed
    }

    private fun applyUsernameOverrideToObject(target: Any, currentUsername: String, decoratedUsername: String): Boolean {
        var changed = false
        val firstCreatedUsername = decoratedUsername
            .substringAfter("(", "")
            .substringBeforeLast(")")
            .takeIf { it.isNotBlank() }
        usernameFieldNames.forEach { fieldName ->
            val rawValue = target.getObjectFieldOrNull(fieldName).asSafeString() ?: return@forEach
            if (normalizeUsername(rawValue) != currentUsername) return@forEach

            val updatedValue = appendOriginalUsername(rawValue, currentUsername, firstCreatedUsername, decoratedUsername)
            if (updatedValue == rawValue) return@forEach

            runCatching {
                target.setObjectField(fieldName, updatedValue)
                changed = true
            }
        }
        return changed
    }

    private fun ValdiViewNode.applyRenderedUsernameOverride(currentUsername: String, decoratedUsername: String): Boolean {
        var changed = false
        val firstCreatedUsername = decoratedUsername
            .substringAfter("(", "")
            .substringBeforeLast(")")
            .takeIf { it.isNotBlank() }
        walk().forEach { node ->
            val className = node.getClassName()
            if (!className.endsWith("SnapTextView") && !className.endsWith("TextView")) return@forEach

            arrayOf("value", "text", "title").forEach { attributeName ->
                val rawValue = node.getAttribute(attributeName).asSafeString() ?: return@forEach
                if (normalizeUsername(rawValue) != currentUsername) return@forEach

                val updatedValue = appendOriginalUsername(rawValue, currentUsername, firstCreatedUsername, decoratedUsername)
                if (updatedValue == rawValue) return@forEach

                runCatching {
                    node.setAttribute(attributeName, updatedValue)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun ValdiViewNode.walk(): Sequence<ValdiViewNode> = sequence {
        yield(this@walk)
        getChildren().forEach { child ->
            yieldAll(child.walk())
        }
    }

    private fun normalizeUsername(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return trimmed.removePrefix("@").substringBefore(" (").trim().takeIf { it.isNotBlank() }
    }

    private fun Any?.asSafeString(): String? {
        return when (this) {
            is String -> this
            is CharSequence -> this.toString()
            is Char -> this.toString()
            else -> null
        }
    }

    private fun appendOriginalUsername(
        rawValue: String,
        currentUsername: String,
        firstCreatedUsername: String?,
        decoratedUsername: String
    ): String {
        if (firstCreatedUsername != null && rawValue.contains("($firstCreatedUsername)")) return rawValue
        if (rawValue.contains("($currentUsername)") || rawValue.contains("($decoratedUsername)")) return rawValue

        val prefixedCurrentUsername = "@$currentUsername"
        return when {
            rawValue == prefixedCurrentUsername -> "@$decoratedUsername"
            rawValue.startsWith(prefixedCurrentUsername) -> rawValue.replaceFirst(prefixedCurrentUsername, "@$decoratedUsername")
            rawValue == currentUsername -> decoratedUsername
            rawValue.startsWith(currentUsername) -> rawValue.replaceFirst(currentUsername, decoratedUsername)
            else -> rawValue
        }
    }
}
