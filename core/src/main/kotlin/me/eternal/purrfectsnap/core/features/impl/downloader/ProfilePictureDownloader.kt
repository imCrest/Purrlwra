package cock.crest.purrfectsnap.lite.core.features.impl.downloader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.AddViewEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.ktx.isDarkTheme

class ProfilePictureDownloader : Feature("ProfilePictureDownloader") {
    private companion object {
        const val DOWNLOAD_BUTTON_TAG = "profile_picture_download_button"
    }

    @SuppressLint("SetTextI18n")
    override fun init() {
        if (!context.config.downloader.downloadProfilePictures.get()) return

        var friendUsername: String? = null
        var backgroundUrl: String? = null
        var avatarUrl: String? = null

        onNextActivityCreate(defer = true) {
            val profileViewClasses = setOf(
                "com.snap.unifiedpublicprofile.UnifiedPublicProfileView",
                "com.snap.modules.profile3.UserProfileV2RootComponent",
                "com.snap.profile.ui.flatland.UnifiedProfileFlatlandProfileView"
            )

            context.event.subscribe(AddViewEvent::class) { event ->
                if (event.view::class.java.name !in profileViewClasses) return@subscribe

                val parent = event.parent
                if (parent.findViewWithTag<View>(DOWNLOAD_BUTTON_TAG) != null) return@subscribe

                val activity = parent.context.findActivity() ?: context.mainActivity ?: return@subscribe
                val buttonText = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.button"]

                val button = ImageButton(activity).apply {
                    val density = resources.displayMetrics.density
                    val buttonSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
                    val iconPadding = (8 * density).toInt()
                    val darkTheme = context.isDarkTheme()

                    tag = DOWNLOAD_BUTTON_TAG
                    contentDescription = buttonText
                    scaleType = ImageView.ScaleType.CENTER
                    setImageResource(android.R.drawable.stat_sys_download)
                    imageTintList = ColorStateList.valueOf(
                        if (darkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#151A1A")
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (darkTheme) android.graphics.Color.parseColor("#1D1D1D")
                            else android.graphics.Color.WHITE
                        )
                    }
                    setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                    minimumWidth = 0
                    minimumHeight = 0
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        stateListAnimator = null
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        buttonSize,
                        buttonSize,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        leftMargin = (8 * density).toInt()
                        topMargin = 236
                    }
                    setOnClickListener {
                        val choices = buildList {
                            backgroundUrl?.let {
                                add(
                                    ProfilePictureChoice(
                                        key = "background_option",
                                        url = it,
                                        iconType = ProfilePictureChoiceIcon.BACKGROUND
                                    )
                                )
                            }
                            avatarUrl?.let {
                                add(
                                    ProfilePictureChoice(
                                        key = "avatar_option",
                                        url = it,
                                        iconType = ProfilePictureChoiceIcon.AVATAR
                                    )
                                )
                            }
                        }

                        createComposeAlertDialog(
                            activity,
                            content = { alertDialog ->
                                ProfilePictureDialog(
                                    title = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.title"],
                                    subtitle = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.subtitle"],
                                    emptyText = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.empty_state"],
                                    downloadHint = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.download_hint"],
                                    closeLabel = this@ProfilePictureDownloader.context.translation["common.close"],
                                    choices = choices,
                                    optionLabel = { key ->
                                        this@ProfilePictureDownloader.context.translation["profile_picture_downloader.$key"]
                                    },
                                    onDownload = { selectedUrl ->
                                        runCatching {
                                            this@ProfilePictureDownloader.context.feature(MediaDownloader::class).downloadProfilePicture(
                                                selectedUrl,
                                                friendUsername ?: "unknown"
                                            )
                                        }.onFailure {
                                            this@ProfilePictureDownloader.context.log.error("Failed to download profile picture", it)
                                        }
                                        alertDialog.dismiss()
                                    },
                                    onDismiss = { alertDialog.dismiss() }
                                )
                            }
                        ).show()
                    }
                }

                val overlayWrapper = object : FrameLayout(parent.context) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if (childCount == 0) return false
                        val child = getChildAt(0)
                        val hitRect = Rect()
                        child.getHitRect(hitRect)
                        return if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
                            super.onTouchEvent(event)
                        } else {
                            false
                        }
                    }
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                parent.post {
                    if (parent.findViewWithTag<View>(DOWNLOAD_BUTTON_TAG) != null) return@post
                    overlayWrapper.addView(button)
                    parent.addView(overlayWrapper)
                    overlayWrapper.bringToFront()
                }
            }


            fun parseProfileData(buffer: ByteArray) {
                runCatching {
                    ProtoReader(buffer).followPath(1, 1, 2) {
                        friendUsername = getString(2) ?: return@followPath
                        followPath(4) {
                            backgroundUrl = getString(2)
                            avatarUrl = getString(100)
                        }
                    }
                }.onFailure {
                    context.log.error("Failed to parse profile picture data", it)
                }
            }

            context.event.subscribe(NetworkApiRequestEvent::class) { event ->
                if (!event.url.contains("getPublicProfile")) return@subscribe
                event.onSuccess { buffer -> buffer?.let { parseProfileData(it) } }
            }

            context.event.subscribe(UnaryCallEvent::class) { event ->
                if (!event.uri.contains("getPublicProfile")) return@subscribe
                event.addResponseCallback {
                    parseProfileData(buffer)
                }
            }
        }
    }

    @Composable
    private fun ProfilePictureDialog(
        title: String,
        subtitle: String,
        emptyText: String,
        downloadHint: String,
        closeLabel: String,
        choices: List<ProfilePictureChoice>,
        optionLabel: (String) -> String,
        onDownload: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        val shape = remember { RoundedCornerShape(24.dp) }
        val overlayBrush = remember {
            Brush.linearGradient(
                listOf(
                    Color(0xFF2A2452).copy(alpha = 0.95f),
                    Color(0xFF1A143A).copy(alpha = 0.92f)
                )
            )
        }
        val accentBrush = remember {
            Brush.linearGradient(
                listOf(
                    Color(0xFF8C7BFF).copy(alpha = 0.42f),
                    Color(0xFF5FD8FF).copy(alpha = 0.34f)
                )
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2452).copy(alpha = 0.95f))
        ) {
            Box(
                modifier = Modifier
                    .background(overlayBrush, shape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(accentBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD9D3FF),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (choices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 18.dp, vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD9D3FF),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            choices.forEach { choice ->
                                ProfilePictureOptionCard(
                                    title = optionLabel(choice.key),
                                    iconType = choice.iconType,
                                    downloadHint = downloadHint,
                                    onClick = { onDownload(choice.url) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8C7BFF).copy(alpha = 0.34f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = closeLabel,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ProfilePictureOptionCard(
        title: String,
        iconType: ProfilePictureChoiceIcon,
        downloadHint: String,
        onClick: () -> Unit
    ) {
        val icon = when (iconType) {
            ProfilePictureChoiceIcon.AVATAR -> Icons.Default.Person
            ProfilePictureChoiceIcon.BACKGROUND -> Icons.Default.Image
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF8C7BFF).copy(alpha = 0.18f),
                            Color(0xFF5FD8FF).copy(alpha = 0.1f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = downloadHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD9D3FF)
                )
            }
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = Color(0xFF5FD8FF)
            )
        }
    }

    private data class ProfilePictureChoice(
        val key: String,
        val url: String,
        val iconType: ProfilePictureChoiceIcon
    )

    private enum class ProfilePictureChoiceIcon {
        AVATAR,
        BACKGROUND
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
