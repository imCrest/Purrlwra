package cock.crest.purrfectsnap.lite.common.scripting.bindings

import cock.crest.purrfectsnap.lite.common.scripting.JSModule
import cock.crest.purrfectsnap.lite.common.scripting.ScriptRuntime
import cock.crest.purrfectsnap.lite.common.scripting.type.ModuleInfo

class BindingsContext(
    val moduleInfo: ModuleInfo,
    val runtime: ScriptRuntime,
    val module: JSModule
)