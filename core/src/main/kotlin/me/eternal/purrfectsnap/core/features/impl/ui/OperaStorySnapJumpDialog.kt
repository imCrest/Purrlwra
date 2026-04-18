package cock.crest.purrfectsnap.lite.core.features.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cock.crest.purrfectsnap.lite.common.ui.PurrfectOverlayPalette
import kotlin.math.roundToInt

/**
 * Composable dialog for jumping to a specific snap in the story (Auto Skip).
 * Matches SnapEnhance dialog size (75% width), transparency (0.88), and layout.
 * Styled with PurrfectSnap colors.
 */
@Composable
fun OperaStorySnapJumpDialog(
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf((currentIndex + 1).toFloat()) }
    val selectedSnap = sliderValue.roundToInt()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(
                    color = PurrfectOverlayPalette.cardOverlayColor.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$selectedSnap",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurrfectOverlayPalette.textPrimary
                    )
                    Text(
                        text = " / $totalCount",
                        fontSize = 14.sp,
                        color = PurrfectOverlayPalette.textSecondary,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..totalCount.toFloat(),
                    steps = if (totalCount > 2) totalCount - 2 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = PurrfectOverlayPalette.glowPrimary,
                        activeTrackColor = PurrfectOverlayPalette.glowPrimary,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = PurrfectOverlayPalette.textPrimary.copy(alpha = 0.12f),
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            color = PurrfectOverlayPalette.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onDismiss()
                                onJump(selectedSnap - 1)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Go",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
