package cock.crest.purrfectsnap.lite.core.features.impl

import android.widget.TextView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature

class Debug : Feature("Debug") {
    override fun init() {
        if (!context.isDeveloper) return
        context.event.subscribe(AddViewEvent::class) { event ->
            event.view.post {
                val viewText = event.view.takeIf { it is TextView }?.let { (it as TextView).text } ?: ""
                event.view.contentDescription = "0x" + (event.view.id.takeIf { it > 0 }?.toString(16) ?: "") + " " + viewText
            }
        }
    }
}