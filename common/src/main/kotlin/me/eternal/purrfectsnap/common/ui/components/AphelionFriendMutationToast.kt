package cock.crest.purrfectsnap.lite.common.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AphelionFriendMutationToast(
    icon: ImageVector,
    text: String,
    bitmojiUrl: String?,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var bitmojiBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(bitmojiUrl) {
        if (bitmojiUrl != null) {
            runCatching {
                cock.crest.purrfectsnap.lite.common.util.snap.RemoteMediaResolver.downloadMedia(bitmojiUrl) { inputStream, _ ->
                    bitmojiBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        delay(5000)
        visible = false
        delay(500)
        onDismiss()
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = -100f * (1f - progress)
                alpha = progress
                scaleX = 0.9f + (0.1f * progress)
                scaleY = 0.9f + (0.1f * progress)
            }
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .shadow(20.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xE61B152E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmojiBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmojiBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
