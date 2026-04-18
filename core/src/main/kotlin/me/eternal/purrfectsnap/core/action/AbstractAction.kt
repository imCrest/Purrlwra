package cock.crest.purrfectsnap.lite.core.action

import cock.crest.purrfectsnap.lite.core.ModContext
import java.io.File

abstract class AbstractAction{
    lateinit var context: ModContext

    /**
     * called when the action is triggered
     */
    open fun run() {}

    open fun onActivityCreate() {}

    protected open fun deleteRecursively(parent: File?) {
        if (parent == null) return
        if (parent.isDirectory) for (child in parent.listFiles()!!) deleteRecursively(
            child
        )
        if (parent.exists() && (parent.isFile || parent.isDirectory)) {
            parent.delete()
        }
    }
}