package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.ui.getValdiContext
import cock.crest.purrfectsnap.lite.core.ui.hideViewCompletely
import cock.crest.purrfectsnap.lite.core.ui.onLayoutChange
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getIdentifier
import android.widget.TextView
import me.eternal.purrfectsnap.core.event.events.impl.AddViewEvent
import me.eternal.purrfectsnap.core.event.events.impl.BindViewEvent
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.ui.children
import me.eternal.purrfectsnap.core.ui.getValdiContext
import me.eternal.purrfectsnap.core.ui.hideViewCompletely
import me.eternal.purrfectsnap.core.ui.onLayoutChange
import me.eternal.purrfectsnap.core.util.dataBuilder
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.Hooker
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.ktx.getIdentifier

fun getChatInputBar(event: AddViewEvent): Lazy<ViewGroup?>? {
    if (!event.parent.javaClass.name.endsWith("ChatInputLayout")) return null
    val isViewSwitcher = event.viewClassName.endsWith("ViewSwitcher")

    return lazy {
        // get the first linear layout in the view switcher
        val firstLinearLayout = if (isViewSwitcher) {
            (event.view as ViewGroup).children()
                .firstOrNull { it is LinearLayout } as? ViewGroup ?: return@lazy null
        } else {
            event.view as? ViewGroup ?: return@lazy null
        }
        // get the first linear layout with at least 3 children
        firstLinearLayout.children()
            .firstOrNull { v -> v is LinearLayout && v.childCount > 2 } as? LinearLayout
            ?: return@lazy null
    }
}

class UITweaks : Feature("UITweaks") {
    private val identifierCache = mutableMapOf<String, Int>()

    fun getId(name: String, defType: String): Int {
        return identifierCache.getOrPut("$name:$defType") {
            context.resources.getIdentifier(name, defType)
        }
    }

    private fun hideStorySection(event: AddViewEvent) {
        val parent = event.parent
        parent.visibility = View.GONE
        val marginLayoutParams = parent.layoutParams as MarginLayoutParams
        marginLayoutParams.setMargins(-99999, -99999, -99999, -99999)
        event.canceled = true
    }

    private fun hideView(view: View) {
        view.apply {
            visibility = View.GONE
            post {
                isEnabled = false
                visibility = View.GONE
                setWillNotDraw(true)
            }
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.post { view.visibility = View.GONE }
            }
        }
    }

    private fun findSpotlightNavTarget(
        event: AddViewEvent,
        spotlightNavIds: Set<Int>,
        spotlightNavNames: Set<String>
    ): View? {
        data class ViewMetadata(
            val view: View,
            val resourceEntryName: String?,
            val contentDescription: String?,
            val text: String?,
            val className: String
        )

        fun resourceEntryNameOrNull(view: View): String? {
            val id = view.id
            if (id == View.NO_ID || id == 0) return null
            return runCatching { context.resources.getResourceEntryName(id) }.getOrNull()
        }

        val markerKeywords = setOf("spotlight", "following", "discover")

        val viewChain = buildList {
            var current: View? = event.view
            repeat(5) {
                current ?: return@repeat
                add(
                    ViewMetadata(
                        view = current!!,
                        resourceEntryName = resourceEntryNameOrNull(current!!),
                        contentDescription = current!!.contentDescription?.toString(),
                        text = (current as? TextView)?.text?.toString(),
                        className = current!!.javaClass.name
                    )
                )
                current = current?.parent as? View
            }
        }

        fun isExactMatch(metadata: ViewMetadata): Boolean {
            return metadata.view.id in spotlightNavIds ||
                metadata.resourceEntryName in spotlightNavNames
        }

        fun hasMarker(metadata: ViewMetadata): Boolean {
            return listOfNotNull(
                metadata.resourceEntryName,
                metadata.contentDescription,
                metadata.text
            ).any { value ->
                markerKeywords.any { keyword ->
                    value.contains(keyword, ignoreCase = true)
                }
            }
        }

        fun isNavigationLike(metadata: ViewMetadata): Boolean {
            val resourceEntryName = metadata.resourceEntryName.orEmpty()
            val className = metadata.className
            return resourceEntryName.contains("hova_nav", ignoreCase = true) ||
                resourceEntryName.contains("bottom_nav", ignoreCase = true) ||
                resourceEntryName.contains("nav", ignoreCase = true) ||
                resourceEntryName.contains("tab", ignoreCase = true) ||
                className.contains("navigation", ignoreCase = true) ||
                className.contains("bottom", ignoreCase = true) ||
                className.contains("tab", ignoreCase = true) ||
                className.contains("hova", ignoreCase = true)
        }

        if (viewChain.none(::isExactMatch) && viewChain.none(::hasMarker)) {
            return null
        }

        var sawSpotlightMarker = false
        viewChain.forEach { metadata ->
            if (isExactMatch(metadata) || hasMarker(metadata)) {
                sawSpotlightMarker = true
            }

            if (sawSpotlightMarker && isNavigationLike(metadata)) {
                return metadata.view
            }
        }

        return viewChain.firstOrNull(::isExactMatch)?.view
    }

    private fun findSpotlightHeaderTabsTarget(view: View): View? {
        fun collectTextLabels(current: View, depth: Int = 0, maxDepth: Int = 2): List<String> {
            if (depth > maxDepth) return emptyList()

            val ownText = listOfNotNull(
                current.contentDescription?.toString(),
                (current as? TextView)?.text?.toString()
            ).filter { it.isNotBlank() }

            if (current !is ViewGroup) return ownText

            return ownText + current.children().flatMap { child ->
                collectTextLabels(child, depth + 1, maxDepth)
            }
        }

        fun isHeaderMarkerText(value: String): Boolean {
            return value.contains("spotlight", ignoreCase = true) ||
                value.contains("discover", ignoreCase = true) ||
                value.contains("following", ignoreCase = true)
        }

        val candidateChain = buildList {
            var current: View? = view
            repeat(6) {
                current ?: return@repeat
                add(current!!)
                current = current?.parent as? View
            }
        }

        candidateChain.forEach { candidate ->
            val group = candidate as? ViewGroup ?: return@forEach
            if (group.childCount !in 2..4) return@forEach

            val directMarkedChildren = group.children().count { child ->
                collectTextLabels(child).any(::isHeaderMarkerText)
            }

            if (directMarkedChildren < 2) return@forEach

            val texts = collectTextLabels(group)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val hasSpotlightOrDiscover = texts.any {
                it.contains("spotlight", ignoreCase = true) ||
                    it.contains("discover", ignoreCase = true)
            }
            val hasFollowing = texts.any { it.contains("following", ignoreCase = true) }

            if (hasSpotlightOrDiscover && hasFollowing) {
                return group
            }
        }

        return null
    }

    private fun onActivityCreate() {
        val blockAds by context.config.global.blockAds
        val hiddenElements by context.config.userInterface.hideUiComponents
        val hideStorySuggestions by context.config.userInterface.hideStorySuggestions
        val disableSpotlight by context.config.userInterface.disableSpotlight
        val isImmersiveCamera by context.config.camera.immersiveCameraPreview

        val displayMetrics = context.resources.displayMetrics
        val deviceAspectRatio = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()

        val chatNoteRecordButton = getId("chat_note_record_button", "id")
        val unreadHintButton = getId("unread_hint_button", "id")
        val spotlightNavIds = listOf(
            getId("hova_nav_spotlight", "id"),
            getId("ngs_hova_nav_spotlight", "id"),
            getId("hova_nav_spotlight_tab", "id"),
            getId("hova_nav_spotlight_button", "id"),
            getId("hova_nav_discover", "id"),
            getId("ngs_hova_nav_discover", "id"),
            getId("hova_nav_discover_tab", "id"),
            getId("hova_nav_discover_button", "id")
        ).filter { it != 0 }.toSet()
        val spotlightNavNames = setOf(
            "hova_nav_spotlight",
            "ngs_hova_nav_spotlight",
            "hova_nav_spotlight_tab",
            "hova_nav_spotlight_button",
            "hova_nav_discover",
            "ngs_hova_nav_discover",
            "hova_nav_discover_tab",
            "hova_nav_discover_button"
        )

        Resources::class.java.methods.first { it.name == "getDimensionPixelSize" }.hook(
            HookStage.AFTER,
            { isImmersiveCamera }
        ) { param ->
            val id = param.arg<Int>(0)
            if (
                id == getId("capri_viewfinder_default_corner_radius", "dimen") ||
                id == getId("ngs_hova_nav_larger_camera_button_size", "dimen")
            ) {
                param.setResult(0)
            }
        }

        context.event.subscribe(BindViewEvent::class, { hideStorySuggestions.isNotEmpty() }) { event ->
            if (event.view is FrameLayout) {
                fun removeView() {
                    event.view.layoutParams = event.view.layoutParams?.apply {
                        width = 0
                        height = 0
                    } ?: return
                }

                val viewModelString = event.prevModel.toString()
                val isMyStory by lazy {
                    viewModelString.let {
                        it.startsWith("StoryCarouselItemViewModel") && it.contains("storyId=")
                    }
                }

                if (hideStorySuggestions.contains("hide_my_stories") && isMyStory) {
                    removeView()
                    return@subscribe
                }
            }
        }

        context.event.subscribe(BindViewEvent::class, { disableSpotlight }) { event ->
            findSpotlightHeaderTabsTarget(event.view)?.hideViewCompletely()
        }

        context.event.subscribe(AddViewEvent::class) { event ->
            val viewId = event.view.id
            val view = event.view

            if (blockAds && viewId == getId("df_promoted_story", "id")) {
                hideStorySection(event)
            }

            findSpotlightNavTarget(event, spotlightNavIds, spotlightNavNames)?.takeIf { disableSpotlight }?.let { targetView ->
                targetView.hideViewCompletely()
                if (targetView !== view) {
                    view.hideViewCompletely()
                }
                event.canceled = true
                return@subscribe
            }

            if (isImmersiveCamera) {
                if (view.id == getId("edits_container", "id")) {
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        val width = it.arg(2) as Int
                        val realHeight = (width / deviceAspectRatio).toInt()
                        it.setArg(3, realHeight)
                    }
                }
                if (view.id == getId("full_screen_surface_view", "id")) {
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        it.setArg(1, 1)
                        it.setArg(3, displayMetrics.heightPixels)
                    }
                }
            }

            if (
                hiddenElements.contains("hide_billboard_prompt") &&
                event.parent.javaClass.name.endsWith("BillboardFeedHeaderPromptComponent")
            ) {
                hideView(event.parent)
                view.getValdiContext()?.componentContext?.get()?.dataBuilder {
                    val dismissFunction = get<Any>("_onDismiss") ?: return@subscribe
                    dismissFunction.javaClass.getMethod("invoke").invoke(dismissFunction)
                }
            }

            if (
                event.parent.javaClass.name.endsWith("ConstraintLayout") &&
                event.view is LinearLayout &&
                hiddenElements.contains("hide_map_reactions")
            ) {
                val viewGroup = event.view as ViewGroup
                val children = viewGroup.children()

                // hide image views in the reaction bar
                if (children.takeIf { it.count() == 5 }?.all { it.javaClass.name.endsWith("SnapImageView") } == true) {
                    children.forEach { imageView ->
                        imageView.hideViewCompletely()
                    }
                }
            }

            if (
                event.parent.javaClass.name.endsWith("PreviewBottomToolbarView") &&
                hiddenElements.contains("hide_post_to_story_buttons")
            ) {
                if (event.parent.childCount == 1) {
                    event.view.hideViewCompletely()
                }
            }

            if (viewId == getId("send_btn", "id") && hiddenElements.contains("hide_post_to_story_buttons")) {
                // hide previous view
                if (event.parent.childCount > 0) {
                    val lastChild = event.parent.getChildAt(event.parent.childCount - 1)
                        ?.takeIf { it is LinearLayout } ?: return@subscribe
                    context.log.verbose("Hiding post to story button")
                    lastChild.hideViewCompletely()
                }
            }

            getChatInputBar(event)?.let { lazyChatInputBar ->
                val chatInputBar by lazyChatInputBar

                if (hiddenElements.contains("hide_live_location_share_button")) {
                    chatInputBar?.onLayoutChange {
                        chatInputBar!!.children()
                            .lastOrNull {
                                it.javaClass.name.endsWith("AppCompatImageButton") &&
                                    runCatching { it.resources.getResourceName(it.id) }.getOrNull() == null
                            }
                            ?.hideViewCompletely()
                    }
                }

                if (hiddenElements.contains("hide_stickers_button")) {
                    chatInputBar
                        ?.children()
                        ?.lastOrNull { layout ->
                            layout is FrameLayout && layout.children().all {
                                it.javaClass.name.endsWith("SnapImageView")
                            }
                        }
                        ?.hideViewCompletely()
                }
            }

            if (viewId == chatNoteRecordButton && hiddenElements.contains("hide_voice_record_button")) {
                view.hideViewCompletely()
            }

            if (viewId == unreadHintButton && hiddenElements.contains("hide_unread_chat_hint")) {
                event.canceled = true
            }
        }
    }

    override fun init() {
        onNextActivityCreate {
            onActivityCreate()
        }
    }
}
