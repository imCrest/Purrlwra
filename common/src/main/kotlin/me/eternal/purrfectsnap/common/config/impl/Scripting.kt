package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.ConfigFlag

class Scripting : ConfigContainer() {
    val developerMode = boolean("developer_mode", false) { requireRestart() }
    val moduleFolder = string("module_folder", "modules") { addFlags(ConfigFlag.FOLDER, ConfigFlag.SENSITIVE); requireRestart()  }
    val autoReload = unique("auto_reload", "snapchat_only", "all")
    val integratedUI = boolean("integrated_ui", false) { requireRestart() }
    val disableLogAnonymization = boolean("disable_log_anonymization", false) { requireRestart() }
    val disableOptimization = boolean("disable_optimization", false) { requireRestart() }
}