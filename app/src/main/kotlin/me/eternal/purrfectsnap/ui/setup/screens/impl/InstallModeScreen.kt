package cock.crest.purrfectsnap.lite.ui.setup.screens.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.SetupHeroScene
import cock.crest.purrfectsnap.lite.ui.setup.SetupInfoSection
import cock.crest.purrfectsnap.lite.ui.setup.SetupSceneHero
import cock.crest.purrfectsnap.lite.ui.setup.screens.SetupScreen
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress

enum class InstallMode { ROOT, NON_ROOT }

class InstallModeScreen(
    private val onModeChosen: (InstallMode) -> Unit,
    private val onSkipAutoSetup: () -> Unit
) : SetupScreen() {
    private var selectedMode: InstallMode? = null
    private var skipAutoSetup = false

    override fun init() {
        selectedMode = null
        skipAutoSetup = false
    }

    override fun onLeave() {
        if (skipAutoSetup) return
    }

    @Composable
    override fun Content() {
        var choice by remember { mutableStateOf(selectedMode) }
        var skipSelected by remember { mutableStateOf(skipAutoSetup) }

        LaunchedEffect(choice, skipSelected) {
            selectedMode = choice
            skipAutoSetup = skipSelected
            allowNext(choice != null || skipSelected)
            if (!skipSelected) {
                choice?.let(onModeChosen)
            }
        }

        SetupCard {
            SetupSceneHero(
                scene = SetupHeroScene.WARNING,
                title = context.translation["setup.install_mode.notice_title"],
                subtitle = context.translation["setup.install_mode.notice_intro"]
            )
            StepTitle(
                title = context.translation["setup.install_mode.step_title"],
                subtitle = context.translation["setup.install_mode.step_subtitle"],
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            SetupInfoSection(
                title = context.translation["setup.install_mode.notice_root_title"],
                lines = listOf(
                    context.translation["setup.install_mode.notice_root_body"]
                ),
                icon = Icons.Filled.VerifiedUser
            )
            SetupInfoSection(
                title = context.translation["setup.install_mode.notice_non_root_title"],
                lines = listOf(
                    context.translation["setup.install_mode.notice_non_root_body"]
                ),
                icon = Icons.Filled.Shield
            )
            SetupInfoSection(
                title = context.translation["setup.install_mode.notice_issues_hint"],
                lines = listOf(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                            append(context.translation["setup.install_mode.notice_note_prefix"])
                        }
                        append(context.translation["setup.install_mode.notice_note_body"])
                    }.toString()
                ),
                icon = Icons.Filled.Warning
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeOption(
                    title = context.translation["setup.install_mode.root_option_title"],
                    subtitle = context.translation["setup.install_mode.root_option_subtitle"],
                    icon = Icons.Filled.VerifiedUser,
                    accent = Brush.horizontalGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.55f)
                        )
                    ),
                    selected = choice == InstallMode.ROOT,
                    onClick = {
                        choice = InstallMode.ROOT
                        skipSelected = false
                    }
                )
                ModeOption(
                    title = context.translation["setup.install_mode.non_root_option_title"],
                    subtitle = context.translation["setup.install_mode.non_root_option_subtitle"],
                    icon = Icons.Filled.Shield,
                    accent = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF7DD3FC),
                            Color(0xFF6366F1)
                        )
                    ),
                    selected = choice == InstallMode.NON_ROOT,
                    onClick = {
                        choice = InstallMode.NON_ROOT
                        skipSelected = false
                    }
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .scaleOnPress(interactionSource)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        skipSelected = true
                        choice = null
                        onSkipAutoSetup()
                        goNext()
                    },
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = context.translation["setup.install_mode.skip_auto_setup"],
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = PurrfectPalette.glowSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun ModeOption(
        title: String,
        subtitle: String,
        icon: ImageVector,
        accent: Brush,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val background = if (selected) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.04f)
        }
        val borderColor = if (selected) {
            Color.White.copy(alpha = 0.4f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scaleOnPress(interactionSource)
                .clip(RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() },
            color = background,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .background(accent)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = PurrfectPalette.textSecondary,
                        lineHeight = 18.sp
                    )
                }
                if (selected) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accent)
                                .clip(CircleShape)
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {}
                }
            }
        }
    }
}
