package cock.crest.purrfectsnap.lite.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress

enum class SetupHeroScene {
    INTRO,
    WARNING,
    PERMISSION,
    PATCH
}

@Composable
fun SetupGlassBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    paddingModifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.20f),
                    PurrfectPalette.glowPrimary.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.10f)
                )
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF171E27).copy(alpha = 0.95f),
                            Color(0xFF0A1016).copy(alpha = 0.96f)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
            Column(
                modifier = paddingModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
fun SetupBlobTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp
    ) {
        Text(
            text = title,
            color = Color(0xFFF4FAFF),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp)
        )
    }
}

@Composable
fun SetupSceneHero(
    scene: SetupHeroScene,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SetupBlobTitle(title = title)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = PurrfectPalette.textSecondary,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color(0xFF0A1015).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    RoundedCornerShape(28.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            SetupDecorCloud(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                width = 116.dp,
                height = 140.dp,
                color = Color(0xFF17252B),
                alpha = 0.75f
            )
            SetupDecorFlower(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
                size = 74.dp,
                color = Color(0xFF343B49),
                alpha = 0.92f
            )
            SetupDecorCloud(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                width = 110.dp,
                height = 126.dp,
                color = Color(0xFF293336),
                alpha = 0.8f
            )
            SetupDecorCloud(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp),
                width = 84.dp,
                height = 96.dp,
                color = Color(0xFF1A262B),
                alpha = 0.78f
            )
            SetupDecorFlower(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                size = 68.dp,
                color = Color(0xFF495064),
                alpha = 0.86f
            )
            when (scene) {
                SetupHeroScene.INTRO -> IntroPreview()
                SetupHeroScene.WARNING -> WarningPreview()
                SetupHeroScene.PERMISSION -> PermissionPreview()
                SetupHeroScene.PATCH -> PatchPreview()
            }
        }
    }
}

@Composable
fun SetupInfoSection(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info
) {
    SetupGlassBlock(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        paddingModifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(PurrfectPalette.glowSecondary)
                    )
                    Text(
                        text = line,
                        color = PurrfectPalette.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SetupGradientActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val gradient = Brush.horizontalGradient(
        listOf(
            Color(0xFFF7FBFF),
            Color(0xFFCFE8FF)
        )
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .scaleOnPress(interaction)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        tonalElevation = 0.dp,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) {
                        gradient
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    }
                )
                .padding(vertical = 15.dp, horizontal = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF0E1720)
                )
                Text(
                    text = label,
                    color = Color(0xFF0E1720),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun SetupLogsPanel(
    logs: List<String>,
    pulse: Float,
    title: String,
    copyLabel: String,
    linePrefix: (String) -> String,
    onCopy: () -> Unit
) {
    val animatedBrush = Brush.linearGradient(
        colors = listOf(
            PurrfectPalette.glowPrimary.copy(alpha = 0.14f + 0.08f * pulse),
            Color.Transparent,
            Color.White.copy(alpha = 0.06f + 0.04f * (1 - pulse))
        )
    )
    SetupGlassBlock(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        paddingModifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCopy)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = copyLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF091015),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .background(animatedBrush)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                logs.takeLast(12).forEach { line ->
                    Text(
                        text = linePrefix(line),
                        color = Color(0xFFE4ECEE),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPreview() {
    Box(
        modifier = Modifier.size(width = 250.dp, height = 210.dp),
        contentAlignment = Alignment.Center
    ) {
        SetupDecorCloud(
            modifier = Modifier.align(Alignment.BottomCenter),
            width = 200.dp,
            height = 112.dp,
            color = Color(0xFF162228),
            alpha = 0.72f
        )
        Surface(
            modifier = Modifier.size(width = 180.dp, height = 180.dp),
            shape = RoundedCornerShape(44.dp),
            color = Color.Transparent,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .size(width = 70.dp, height = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.8f))
                )
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 28.dp, top = (54 + index * 28).dp)
                            .size(width = if (index == 1) 116.dp else 96.dp, height = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == 1) PurrfectPalette.glowPrimary.copy(alpha = 0.32f)
                                else Color.White.copy(alpha = 0.82f)
                            )
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) { index ->
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = CircleShape,
                            color = if (index == 1) PurrfectPalette.glowSecondary else Color.White.copy(alpha = 0.75f)
                        ) {}
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 26.dp, bottom = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFB7D3E5)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF163344)
                )
            }
        }
    }
}

@Composable
private fun PermissionPreview() {
    Box(
        modifier = Modifier.size(width = 250.dp, height = 210.dp),
        contentAlignment = Alignment.Center
    ) {
        SetupDecorCloud(
            modifier = Modifier.align(Alignment.BottomCenter),
            width = 200.dp,
            height = 110.dp,
            color = Color(0xFF162228),
            alpha = 0.74f
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(78.dp),
                shape = RoundedCornerShape(24.dp),
                color = PurrfectPalette.glowPrimary.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            repeat(3) { index ->
                Surface(
                    modifier = Modifier.size(width = if (index == 1) 124.dp else 150.dp, height = 16.dp),
                    shape = RoundedCornerShape(50),
                    color = PurrfectPalette.glowPrimary.copy(alpha = if (index == 1) 0.42f else 0.28f)
                ) {}
            }
        }
    }
}

@Composable
private fun WarningPreview() {
    Box(
        modifier = Modifier.size(width = 250.dp, height = 210.dp),
        contentAlignment = Alignment.Center
    ) {
        SetupDecorCloud(
            modifier = Modifier.align(Alignment.BottomCenter),
            width = 210.dp,
            height = 110.dp,
            color = Color(0xFF162228),
            alpha = 0.72f
        )
        SetupGlassBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(24.dp),
            paddingModifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 2) 0.74f else 1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = if (index == 0) 0.82f else 0.2f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchPreview() {
    SetupGlassBlock(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(28.dp),
        paddingModifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF9EDAF4)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = Color(0xFF163344),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Step 3/6",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    text = "Preparing Lite build",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF081015)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "I: validating lightweight config",
                    "I: syncing patch payload",
                    "I: preparing install package",
                    "I: waiting for confirmation"
                ).forEach { line ->
                    Text(
                        text = line,
                        color = Color(0xFFE4ECEE),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SetupDecorCloud(
    modifier: Modifier,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    alpha: Float
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(34.dp))
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun BoxScope.SetupDecorFlower(
    modifier: Modifier,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    alpha: Float
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .size(width = size / 2.2f, height = size / 5f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = alpha))
                    .align(Alignment.Center)
                    .rotate(index * 22.5f)
            )
        }
    }
}
