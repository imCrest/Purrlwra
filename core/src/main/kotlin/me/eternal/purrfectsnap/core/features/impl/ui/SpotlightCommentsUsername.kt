package cock.crest.purrfectsnap.lite.core.features.impl.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.common.ui.createComposeAlertDialog
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.ui.children
import cock.crest.purrfectsnap.lite.core.util.EvictingMap
import java.util.Locale

class SpotlightCommentsUsername : Feature("SpotlightCommentsUsername") {
    private val usernameCache = EvictingMap<String, String>(150)
    private val dialogTranslation by lazy { context.translation.getCategory("spotlight_comments_username_dialog") }

    @SuppressLint("SetTextI18n")
    override fun init() {
        if (!context.config.global.spotlightCommentsUsername.get()) return

        onNextActivityCreate(defer = true) {
            val messaging = context.feature(Messaging::class)
            context.event.subscribe(BindViewEvent::class) { event ->
                val posterUserId = event.prevModel.toString().takeIf { it.startsWith("Comment") }
                    ?.substringAfter("posterUserId=")?.substringBefore(",")?.substringBefore(")") ?: return@subscribe

                if (posterUserId == "null") return@subscribe

                fun setUserInfo(username: String) {
                    usernameCache[posterUserId] = username
                    val commentsCreatorBadgeTimestamp = (event.view as ViewGroup).children().filterIsInstance<TextView>()
                        .getOrNull(1) ?: return
                    
                    val customIcon = context.config.global.spotlightCommentsUsernameIcon.get().takeIf { it.isNotBlank() } ?: "[👤]"
                    val exclamationIcon = "  $customIcon"
                    val spannableString = SpannableString(exclamationIcon + commentsCreatorBadgeTimestamp.text.toString())
                    
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            showUserInfoDialog(posterUserId, username)
                        }
                        
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                        }
                    }
                    
                    spannableString.setSpan(clickableSpan, 0, exclamationIcon.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                    
                    commentsCreatorBadgeTimestamp.text = spannableString
                    commentsCreatorBadgeTimestamp.movementMethod = LinkMovementMethod.getInstance()
                    commentsCreatorBadgeTimestamp.setTextColor(Color.WHITE)
                }

                event.view.post {
                    usernameCache[posterUserId]?.let {
                        setUserInfo(it)
                        return@post
                    }

                    context.coroutineScope.launch {
                        val username = runCatching {
                            messaging.fetchSnapchatterInfos(listOf(posterUserId)).firstOrNull()
                        }.onFailure {
                            context.log.error("Failed to fetch snapchatter info for user $posterUserId", it)
                        }.getOrNull()?.username ?: return@launch

                        withContext(Dispatchers.Main) {
                            setUserInfo(username)
                        }
                    }
                }
            }
        }
    }
    
    private fun showUserInfoDialog(userId: String, username: String) {
        context.coroutineScope.launch {
            val messaging = context.feature(Messaging::class)
            val userInfo = runCatching {
                messaging.fetchSnapchatterInfos(listOf(userId)).firstOrNull()
            }.onFailure {
                context.log.error("Failed to fetch detailed user info for $userId", it)
            }.getOrNull()
            
            withContext(Dispatchers.Main) {
                createComposeAlertDialog(
                    context.mainActivity!!,
                    content = { alertDialog ->
                        UserInfoDialog(
                            username = username,
                            userId = userId,
                            displayName = userInfo?.displayName,
                            title = dialogTranslation["title"],
                            subtitle = dialogTranslation["subtitle"],
                            usernameLabel = dialogTranslation["username_label"],
                            displayNameLabel = dialogTranslation["display_name_label"],
                            userIdLabel = dialogTranslation["user_id_label"],
                            unavailableLabel = dialogTranslation["not_available"],
                            closeLabel = context.translation["common.close"],
                            onDismiss = { alertDialog.dismiss() }
                        )
                    }
                ).show()
            }
        }
    }

    @Composable
    private fun UserInfoDialog(
        username: String,
        userId: String,
        displayName: String?,
        title: String,
        subtitle: String,
        usernameLabel: String,
        displayNameLabel: String,
        userIdLabel: String,
        unavailableLabel: String,
        closeLabel: String,
        onDismiss: () -> Unit
    ) {
        val shape = remember { RoundedCornerShape(24.dp) }
        val overlayBrush = remember {
            Brush.linearGradient(
                listOf(
                    ComposeColor(0xFF2A2452).copy(alpha = 0.95f),
                    ComposeColor(0xFF1A143A).copy(alpha = 0.92f)
                )
            )
        }
        val accentBrush = remember {
            Brush.linearGradient(
                listOf(
                    ComposeColor(0xFF8C7BFF).copy(alpha = 0.42f),
                    ComposeColor(0xFF5FD8FF).copy(alpha = 0.34f)
                )
            )
        }
        val rows = remember(username, userId, displayName) {
            listOf(
                usernameLabel to username,
                displayNameLabel to (displayName?.takeIf { it.isNotBlank() } ?: unavailableLabel),
                userIdLabel to userId
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = shape,
            border = BorderStroke(1.dp, ComposeColor.White.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFF2A2452).copy(alpha = 0.95f))
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
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ComposeColor.White,
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
                            color = ComposeColor.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ComposeColor(0xFFD9D3FF),
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                ComposeColor.White.copy(alpha = 0.06f),
                                RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rows.forEachIndexed { index, (label, value) ->
                            UserInfoRow(label = label, value = value)
                            if (index != rows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = ComposeColor.White.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFF8C7BFF).copy(alpha = 0.34f),
                            contentColor = ComposeColor.White
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
    private fun UserInfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                color = ComposeColor(0xFFD9D3FF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(96.dp)
            )
            SelectionContainer(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = value,
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
