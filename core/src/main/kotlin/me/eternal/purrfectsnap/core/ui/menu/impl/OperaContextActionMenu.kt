package cock.crest.purrfectsnap.lite.core.ui.menu.impl

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.use
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.features.impl.OperaViewerParamsOverride
import cock.crest.purrfectsnap.lite.core.features.impl.downloader.MediaDownloader
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.ui.menu.AbstractMenu
import cock.crest.purrfectsnap.lite.core.ui.triggerCloseTouchEvent
import cock.crest.purrfectsnap.lite.core.util.ktx.getIdentifier
import cock.crest.purrfectsnap.lite.core.util.ktx.vibrateLongPress
import cock.crest.purrfectsnap.lite.core.wrapper.impl.ScSize
import java.text.DateFormat
import java.util.Date

@SuppressLint("DiscouragedApi")
class OperaContextActionMenu : AbstractMenu() {
    /*
    LinearLayout :
        - LinearLayout:
            - SnapFontTextView
            - ImageView
        - LinearLayout:
            - SnapFontTextView
            - ImageView
        - LinearLayout:
            - SnapFontTextView
            - ImageView
     */
    private fun isViewGroupButtonMenuContainer(viewGroup: ViewGroup): Boolean {
        if (viewGroup !is LinearLayout) return false
        val children = viewGroup.children()
        return if (children.any { view: View? -> view !is LinearLayout })
            false
        else children.map { view: View -> view as LinearLayout }
            .any { linearLayout: LinearLayout ->
                linearLayout.children().any { viewChild: View ->
                    viewChild.javaClass.name.endsWith("SnapFontTextView")
                }
            }
    }

    override fun onViewAdded(event: AddViewEvent) {
        val parentView = event.parent.parent as? ScrollView ?: return
        val view = event.view
        if (view !is LinearLayout) return
        if (!isViewGroupButtonMenuContainer(view as ViewGroup)) return

        val linearLayout = LinearLayout(view.context)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.gravity = Gravity.CENTER
        linearLayout.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        val translation = context.translation.getCategory("opera_context_menu")
        val mediaDownloader = context.feature(MediaDownloader::class)
        val paramMap = mediaDownloader.lastSeenMapParams

        if (paramMap != null && context.config.userInterface.operaMediaQuickInfo.get()) {
            val playableStorySnapRecord = paramMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
            val sentTimestamp = playableStorySnapRecord?.substringAfter("timestamp=")
                ?.substringBefore(",")?.toLongOrNull()
                ?: mediaDownloader.resolveCurrentSnapMessageContext()?.clientMessageId?.let { messageId ->
                    context.database.getConversationMessageFromId(
                        messageId
                    )?.creationTimestamp
                }
                ?: paramMap["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull()
            val dateFormat = DateFormat.getDateTimeInstance()
            val creationTimestamp = playableStorySnapRecord?.substringAfter("creationTimestamp=")
                ?.substringBefore(",")?.toLongOrNull()
            val expirationTimestamp = playableStorySnapRecord?.substringAfter("expirationTimestamp=")
                ?.substringBefore(",")?.toLongOrNull()
                ?: paramMap["SNAP_EXPIRATION_TIMESTAMP_MILLIS"]?.toString()?.toLongOrNull()

            val mediaSize = paramMap["snap_size"]?.let { ScSize(it) }
            val durationMs = paramMap["media_duration_ms"]?.toString()

            val stringBuilder = StringBuilder().apply {
                if (sentTimestamp != null) {
                    append(translation.format("sent_at", "date" to dateFormat.format(Date(sentTimestamp))))
                    append("\n")
                }
                if (creationTimestamp != null) {
                    append(translation.format("created_at", "date" to dateFormat.format(Date(creationTimestamp))))
                    append("\n")
                }
                if (expirationTimestamp != null) {
                    append(translation.format("expires_at", "date" to dateFormat.format(Date(expirationTimestamp))))
                    append("\n")
                }
                if (mediaSize != null) {
                    append(translation.format("media_size", "size" to "${mediaSize.first}x${mediaSize.second}"))
                    append("\n")
                }
                if (durationMs != null) {
                    append(translation.format("media_duration", "duration" to durationMs))
                    append("\n")
                }
                if (last() == '\n') deleteCharAt(length - 1)
            }

            if (stringBuilder.isNotEmpty()) {
                linearLayout.addView(TextView(view.context).apply {
                    text = stringBuilder.toString()
                    setPadding(40, 10, 0, 0)
                })
            }
        }

        if (context.config.global.videoPlaybackRateSlider.get()) {
            val operaViewerParamsOverride = context.feature(OperaViewerParamsOverride::class)

            linearLayout.addView(createComposeView(view.context) {
                val glowPrimary = Color(0xFF8C7BFF)
                val glowSecondary = Color(0xFF5FD8FF)
                val cardShape = RoundedCornerShape(22.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    var value by remember { mutableFloatStateOf(operaViewerParamsOverride.currentPlaybackRate) }
                    Card(
                        shape = cardShape,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    glowPrimary.copy(alpha = 0.45f),
                                    glowSecondary.copy(alpha = 0.35f)
                                )
                            )
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2A2452).copy(alpha = 0.94f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF2A2452).copy(alpha = 0.95f),
                                            Color(0xFF1A143A).copy(alpha = 0.92f)
                                        )
                                    ),
                                    cardShape
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    glowPrimary.copy(alpha = 0.35f),
                                                    glowSecondary.copy(alpha = 0.28f)
                                                )
                                            ),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SlowMotionVideo,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(androidx.compose.ui.Alignment.Center)
                                            .size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Playback Rate",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "x" + String.format("%.2f", value),
                                        color = Color(0xFFD9D3FF),
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { androidContext ->
                                    SeekBar(androidContext).apply {
                                        max = 390
                                        progress = ((value - 0.1f) * 100).toInt().coerceIn(0, max)
                                        thumbTintList = ColorStateList.valueOf(Color.White.toArgb())
                                        progressTintList = ColorStateList.valueOf(glowSecondary.toArgb())
                                        progressBackgroundTintList = ColorStateList.valueOf(Color.White.copy(alpha = 0.16f).toArgb())
                                        splitTrack = false

                                        setOnTouchListener { seekBar, motionEvent ->
                                            when (motionEvent.actionMasked) {
                                                MotionEvent.ACTION_DOWN,
                                                MotionEvent.ACTION_MOVE -> seekBar.parent?.requestDisallowInterceptTouchEvent(true)
                                                MotionEvent.ACTION_UP,
                                                MotionEvent.ACTION_CANCEL -> seekBar.parent?.requestDisallowInterceptTouchEvent(false)
                                            }
                                            false
                                        }

                                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                                val playbackRate = (0.1f + (progress / 100f)).coerceIn(0.1f, 4.0f)
                                                value = playbackRate
                                                operaViewerParamsOverride.currentPlaybackRate = playbackRate
                                            }

                                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                                                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                                            }

                                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                                            }
                                        })
                                    }
                                },
                                update = { seekBar ->
                                    val targetProgress = ((value - 0.1f) * 100).toInt().coerceIn(0, seekBar.max)
                                    if (seekBar.progress != targetProgress) {
                                        seekBar.progress = targetProgress
                                    }
                                }
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "0.1x",
                                    color = Color(0xFFD9D3FF),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "4.0x",
                                    color = Color(0xFFD9D3FF),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }

        if (context.config.downloader.downloadContextMenu.get()) {
            linearLayout.addView(Button(view.context).apply {
                text = translation["download"]
                setOnClickListener {
                    mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = false)
                    parentView.triggerCloseTouchEvent()
                }
                setOnLongClickListener {
                    context.vibrateLongPress()
                    mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = true)
                    parentView.triggerCloseTouchEvent()
                    true
                }
                this@OperaContextActionMenu.context.userInterface.applyActionButtonTheme(this)
            })
        }

        if (context.isDeveloper) {
            linearLayout.addView(Button(view.context).apply {
                text = translation["show_debug_info"]
                setOnClickListener { mediaDownloader.showLastOperaDebugMediaInfo() }
                this@OperaContextActionMenu.context.userInterface.applyActionButtonTheme(this)
            })
        }

        (view as? ViewGroup)?.addView(linearLayout, 0)
    }
}
