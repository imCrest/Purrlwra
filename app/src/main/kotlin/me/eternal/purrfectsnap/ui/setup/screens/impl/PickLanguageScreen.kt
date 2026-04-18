package cock.crest.purrfectsnap.lite.ui.setup.screens.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.SetupHeroScene
import cock.crest.purrfectsnap.lite.ui.setup.SetupSceneHero
import cock.crest.purrfectsnap.lite.ui.setup.screens.SetupScreen
import cock.crest.purrfectsnap.lite.ui.util.Motion
import cock.crest.purrfectsnap.lite.ui.util.ObservableMutableState
import cock.crest.purrfectsnap.lite.ui.util.scaleOnPress

class PickLanguageScreen : SetupScreen() {
    private val availableLocales by lazy {
        LocaleWrapper.fetchAvailableLocales(context.androidContext)
    }
    private lateinit var selectedLocale: ObservableMutableState<String>
    private fun getLocaleDisplayName(locale: String): String {
        val displayLocale = when (locale) {
            "zh-Hans" -> Locale.forLanguageTag("zh-Hans")
            "zh_TW" -> Locale.TRADITIONAL_CHINESE
            else -> try {
                Locale.forLanguageTag(locale.replace('_', '-'))
            } catch (e: Exception) {
                Locale.getDefault()
            }
        }
        return displayLocale.getDisplayName(Locale.getDefault())
    }
    private fun reloadTranslation(selectedLocale: String) {
        context.translation.reload(selectedLocale, isSetup = true)
    }
    private fun setLocale(locale: String) {
        with(context) {
            config.locale = locale
            config.writeConfig()
            reloadTranslation(locale)
        }
    }
    override fun onLeave() {
        context.config.locale = selectedLocale.value
        context.config.writeConfig()
    }
    override fun init() {
        val deviceLocale = Locale.getDefault().toString()
        selectedLocale =
            ObservableMutableState(
                defaultValue = availableLocales.firstOrNull { locale -> locale == deviceLocale }
                    ?: LocaleWrapper.DEFAULT_LOCALE
            ) { _, newValue ->
                setLocale(newValue)
            }.also { reloadTranslation(it.value) }
    }

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) { allowNext(true) }
        val deviceLocale = remember { Locale.getDefault().toString() }
        var isDialog by remember { mutableStateOf(false) }

        fun select(locale: String) {
            selectedLocale.value = locale
            isDialog = false
        }

        SetupCard {
            SetupSceneHero(
                scene = SetupHeroScene.INTRO,
                title = "PurrfectSnap Lite",
                subtitle = "Lean setup, calmer visuals, and a smaller Lite-first onboarding flow."
            )
            StepTitle(
                title = context.translation["setup.dialogs.select_language"],
                subtitle = context.translation["setup.pick_language.change_anytime_hint"],
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = PurrfectPalette.glowPrimary.copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = context.translation["setup.pick_language.current_selection"],
                            fontSize = 14.sp,
                            color = PurrfectPalette.textSecondary
                        )
                        Text(
                            text = remember(selectedLocale.value) { getLocaleDisplayName(selectedLocale.value) },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val browseSrc = remember { MutableInteractionSource() }
            Button(
                onClick = { isDialog = true },
                interactionSource = browseSrc,
                modifier = Modifier
                    .fillMaxWidth()
                    .scaleOnPress(browseSrc),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                    contentColor = Color.White
                )
            ) {
                Text(text = context.translation["setup.pick_language.browse_languages"])
            }
        }

        if (isDialog) {
            Dialog(onDismissRequest = { isDialog = false }) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = Motion.tweenFloatSpec(200)) + scaleIn(animationSpec = Motion.tweenFloatSpec(220)),
                    exit = fadeOut(animationSpec = Motion.tweenFloatSpec(150)) + scaleOut(animationSpec = Motion.tweenFloatSpec(180))
                ) {
                    SetupCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StepTitle(
                            title = context.translation["setup.pick_language.available_languages"],
                            subtitle = null,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableLocales) { locale ->
                                val label = remember(locale) { getLocaleDisplayName(locale) }
                                val isSelected = selectedLocale.value == locale
                                val rowSrc = remember { MutableInteractionSource() }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .scaleOnPress(rowSrc),
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) PurrfectPalette.glowPrimary.copy(alpha = 0.14f) else Color.White.copy(
                                        alpha = 0.05f
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) PurrfectPalette.glowPrimary.copy(alpha = 0.55f) else Color.White.copy(
                                            alpha = 0.1f
                                        )
                                    ),
                                    tonalElevation = if (isSelected) 8.dp else 0.dp,
                                    onClick = { select(locale) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = locale,
                                                fontSize = 11.sp,
                                                color = PurrfectPalette.textSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = PurrfectPalette.glowSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
