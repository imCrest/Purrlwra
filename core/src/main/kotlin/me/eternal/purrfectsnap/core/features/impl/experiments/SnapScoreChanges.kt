package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.view.ViewGroup
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.getValdiContext
import cock.crest.purrfectsnap.lite.core.ui.getValdiViewNode
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID

class SnapScoreChanges: Feature("Snap Score Changes") {
    private val scores = mutableMapOf<String, Long>()
    private var lastViewedUserId: String? = null

    override fun init() {
        if (!context.config.experimental.snapScoreChanges.get()) return

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri != "/com.snapchat.atlas.gw.AtlasGw/GetFriendsUserScore") return@subscribe

            event.addResponseCallback {
                synchronized(scores) {
                    ProtoReader(buffer).eachBuffer(1) {
                        val friendUUID = getByteArray(1) ?: return@eachBuffer
                        val score = getVarInt(2) ?: return@eachBuffer

                        scores[SnapUUID(friendUUID).toString()] = score
                    }
                }
            }
        }

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.viewClassName.endsWith("UnifiedProfileFlatlandProfileViewTopViewFrameLayout")) {
                val composerView = (event.view as ViewGroup).getChildAt(0) ?: return@subscribe
                val composerContext = composerView.getValdiContext() ?: return@subscribe

                lastViewedUserId = composerContext.viewModel?.getObjectField("_userId")?.toString()
            }

            if (event.viewClassName.endsWith("ProfileFlatlandFriendSnapScoreIdentityPillDialogView")) {
                event.view.post {
                    event.view.getValdiContext()!!.enqueueNextRenderCallback {
                        val composerViewNode = event.view.getValdiViewNode() ?: return@enqueueNextRenderCallback
                        val surface = composerViewNode.getChildren().getOrNull(1) ?: return@enqueueNextRenderCallback

                        val snapTextView = surface.getChildren().lastOrNull {
                            it.getClassName().endsWith("SnapTextView")
                        } ?: return@enqueueNextRenderCallback


                        val currentFriendScore = scores[lastViewedUserId] ?: (event.view.getValdiContext()?.viewModel?.getObjectField("_friendSnapScore") as? Double)?.toLong() ?: return@enqueueNextRenderCallback

                        val oldSnapScore = context.bridgeClient.getTracker().updateFriendScore(
                            lastViewedUserId ?: return@enqueueNextRenderCallback,
                            currentFriendScore
                        )

                        val diff = currentFriendScore - oldSnapScore

                        snapTextView.setAttribute("value", "${if (oldSnapScore != -1L && diff > 0) "\uD83D\uDCC8 +$diff !\n\n" else ""}Last Checked Score: ${oldSnapScore.takeIf { it != -1L } ?: "N/A"}")
                        event.view.postInvalidate()
                    }
                    event.view.postInvalidate()
                }
            }
        }
    }
}
