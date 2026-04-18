package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.PropertyValue
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.RuleState


class Rules : ConfigContainer() {
    private val rules = mutableMapOf<MessagingRuleType, PropertyValue<String>>()

    fun getRuleState(ruleType: MessagingRuleType): RuleState? {
        return rules[ruleType]?.getNullable()?.let { RuleState.getByName(it) }
    }

    init {
        MessagingRuleType.entries.filter { it.listMode }.forEach { ruleType ->
            rules[ruleType] = unique(ruleType.key,"whitelist", "blacklist") {
                customTranslationPath = "rules.properties.${ruleType.key}"
                customOptionTranslationPath = "rules.modes"
                addNotices(*ruleType.configNotices)
                requireRestart()
            }.apply {
                set(ruleType.defaultValue)
            }
        }
    }
}
