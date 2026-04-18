package cock.crest.purrfectsnap.lite.ui.manager.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.Motion

@Composable
fun AestheticDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    icon: ImageVector,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    customContent: (@Composable ColumnScope.() -> Unit)? = null,
    loading: Boolean = false,
    opaque: Boolean = false,
    showCloseButton: Boolean = true,
    confirmEnabled: Boolean = true,
    showIcon: Boolean = true,
    showTitle: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val surfaceColor = if (opaque) {
        PurrfectPalette.cardOverlayColor.copy(alpha = 1f)
    } else {
        PurrfectPalette.cardOverlayColor
    }

    Dialog(onDismissRequest = onDismissRequest) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = Motion.tweenFloatSpec(180)) + scaleIn(animationSpec = Motion.tweenFloatSpec(220)),
            exit = fadeOut(animationSpec = Motion.tweenFloatSpec(150)) + scaleOut(animationSpec = Motion.tweenFloatSpec(180))
        ) {
            val shape = RoundedCornerShape(22.dp)
            Card(
                shape = shape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                colors = CardDefaults.cardColors(containerColor = surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .background(PurrfectPalette.cardOverlay, shape)
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (showIcon) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                PurrfectPalette.glowPrimary.copy(alpha = 0.3f),
                                                PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        if (showTitle && title.isNotBlank()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (text.isNotBlank()) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PurrfectPalette.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        customContent?.invoke(this)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                        ) {
                            if (dismissButtonText != null && onDismiss != null) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) { Text(dismissButtonText) }
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = confirmEnabled && !loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.34f),
                                contentColor = Color.White,
                                disabledContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.22f),
                                disabledContentColor = Color.White.copy(alpha = 0.75f)
                            )
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(confirmButtonText)
                                }
                            }
                        }
                    }
                    if (showCloseButton) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
