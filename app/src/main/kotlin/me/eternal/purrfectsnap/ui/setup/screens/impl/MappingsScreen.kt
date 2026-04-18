package cock.crest.purrfectsnap.lite.ui.setup.screens.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.setup.screens.SetupScreen
import cock.crest.purrfectsnap.lite.ui.util.AlertDialogs
import cock.crest.purrfectsnap.lite.ui.util.Motion

class MappingsScreen : SetupScreen() {
    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val translation = context.translation
        var infoText by remember { mutableStateOf(null as String?) }
        var isGenerating by remember { mutableStateOf(false) }
        fun finishMappings() = goNext()

        if (infoText != null) {
            fun dismiss() {
                infoText = null
                finishMappings()
            }

            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            Dialog(onDismissRequest = { dismiss() }) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = Motion.tweenFloatSpec(180)) + scaleIn(animationSpec = Motion.tweenFloatSpec(200)),
                    exit = fadeOut(animationSpec = Motion.tweenFloatSpec(150)) + scaleOut(animationSpec = Motion.tweenFloatSpec(180))
                ) {
                    remember { AlertDialogs(context.translation) }.InfoDialog(title = infoText!!) {
                        dismiss()
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                if (isGenerating) return@launch
                isGenerating = true
                runCatching {
                    context.mappings.init(context.androidContext)
                    if (context.mappings.getSnapchatPackageInfo() == null) {
                        throw Exception(context.translation["setup.mappings.generate_failure_no_snapchat"])
                    }
                    val warnings = context.mappings.refresh()

                    if (warnings.isNotEmpty()) {
                        isGenerating = false
                        infoText = translation.format(
                            "setup.mappings.warnings_info",
                            "count" to warnings.size.toString(),
                            "warnings" to warnings.joinToString("\n")
                        ).also {
                            context.log.warn(it)
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        finishMappings()
                    }
                }.onFailure {
                    isGenerating = false
                    infoText = context.translation["setup.mappings.generate_failure"] + "\n\n" + it.message
                    context.log.error("Failed to generate mappings", it)
                }
            }
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.mappings.dialog"],
                subtitle = null
            )
            DialogText(
                text = translation["setup.mappings.progress_hint"]
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.48f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.36f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = PurrfectPalette.glowPrimary,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }
}
