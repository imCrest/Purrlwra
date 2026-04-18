package cock.crest.purrfectsnap.lite.ui.manager.pages.home

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.ManagerTheme
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.PurrfectMarqueeText
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress

class HomeAbout : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home_about") }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(
            initialValue = context.config.root.global.uiSettings.managerTheme.get()
        ) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }

        LaunchedEffect(Unit) {
            context.shortToast(translation["about_magic_toast"] ?: "Tap 5 times in this screen to see some magic 😉!")
        }

        key(themeId) {
            val currentTheme = ManagerTheme.fromId(themeId).theme
            with(currentTheme) {
                this@HomeAbout.AboutScreen(nav)
            }
        }
    }

    @Composable
    internal fun DeveloperCard(
        name: String,
        imageRes: Int,
        avenirNext: FontFamily,
        modifier: Modifier = Modifier
    ) {
        val tapSource = remember { MutableInteractionSource() }
        val tapTimeoutMs = 1500L
        val tapCount = remember { mutableIntStateOf(0) }
        val lastTapTime = remember { mutableLongStateOf(0L) }

        Surface(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTapTime.longValue > tapTimeoutMs) {
                    tapCount.intValue = 0
                }
                tapCount.intValue += 1
                lastTapTime.longValue = now
                if (tapCount.intValue >= 5) {
                    tapCount.intValue = 0
                    routes.retroGame.navigate()
                }
            },
            modifier = modifier.scaleOnPress(tapSource),
            interactionSource = tapSource,
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary)))
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                PurrfectMarqueeText(
                    text = name,
                    color = Color.White,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = avenirNext
                    )
                )
            }
        }
    }
}
