package cock.crest.purrfectsnap.lite.core.features.impl.global

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.ktx.findFieldNamesByType
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.mapper.impl.PlusSubscriptionMapper
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SnapchatPlus: Feature("SnapchatPlus") {
    private val expirationTimeMillis = (System.currentTimeMillis() + 15552000000L)

    override fun init() {
        val snapchatPlusTier = context.config.global.snapchatPlus.getNullable()

        if (snapchatPlusTier != null) {
            context.mappings.useMapper(PlusSubscriptionMapper::class) {
                classReference.get()?.hookConstructor(HookStage.AFTER) { param ->
                    param.thisObject<Any>().dataBuilder {
                        //subscription tier
                        if (get<Any>(tierField.getAsString()!!)?.javaClass?.isEnum == true) {
                            set(tierField.getAsString()!!, when (snapchatPlusTier) {
                                "not_subscribed" -> "NO_ACCESS"
                                "basic" -> "SNAPCHAT_PLUS"
                                "ad_free" -> "SNAPCHAT_PLUS_AD_FREE"
                                else -> "SNAPCHAT_PLUS"
                            })
                        } else {
                            set(tierField.getAsString()!!, when (snapchatPlusTier) {
                                "not_subscribed" -> 1
                                "basic" -> 2
                                "ad_free" -> 3
                                else -> 2
                            })
                        }

                        //subscription status
                        set(statusField.getAsString()!!, 2)

                        val fallbackOriginalSubscriptionTime = System.currentTimeMillis() - 7776000000L
                        val customPurchaseDate = context.config.global.snapchatPlusPurchaseDate.get().trim()
                        val customPurchaseDateMillis = if (customPurchaseDate.isNotEmpty()) {
                            runCatching {
                                LocalDate
                                    .parse(customPurchaseDate, DateTimeFormatter.ISO_LOCAL_DATE)
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            }.getOrNull()
                        } else {
                            null
                        }

                        set(
                            originalSubscriptionTimeMillisField.getAsString()!!,
                            customPurchaseDateMillis ?: fallbackOriginalSubscriptionTime
                        )
                        set(expirationTimeMillisField.getAsString()!!, expirationTimeMillis)
                    }
                }
            }
        }

        if (context.config.experimental.hiddenSnapchatPlusFeatures.get()) {
            findClass("com.snap.plus.FeatureCatalog").methods.last {
                !it.name.contains("init") &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].name != "java.lang.Boolean"
            }.hook(HookStage.BEFORE) { param ->
                val instance = param.thisObject<Any>()
                val firstArg = param.argNullable<Any>(0) ?: return@hook

                instance.findFieldNamesByType(firstArg::class.java).forEach { fieldName ->
                    instance.setObjectField(fieldName, firstArg)
                }
            }
        }
    }
}
