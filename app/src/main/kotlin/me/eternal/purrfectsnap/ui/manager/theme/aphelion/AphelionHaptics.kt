package cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import cock.crest.purrfectsnap.lite.RemoteSideContext

/**
 * Specialized haptic engine for the Aphelion "Liquid Glass" experience.
 * Provides more nuanced feedback than standard Compose haptics.
 */
object AphelionHaptics {

    /**
     * Triggers a subtle, sharp "tick" intended for the start of a theme reveal.
     */
    fun themeRevealTick(remoteSideContext: RemoteSideContext, haptic: HapticFeedback) {
        runCatching {
            if (!shouldPerformHaptics(remoteSideContext)) return

            val androidContext = remoteSideContext.androidContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = androidContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = androidContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                // Fallback for older APIs
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }.onFailure { it.printStackTrace() }
    }

    /**
     * Triggers a "soft" impact feel, good for glass interactions.
     */
    fun softImpact(remoteSideContext: RemoteSideContext, haptic: HapticFeedback) {
        runCatching {
            if (!shouldPerformHaptics(remoteSideContext)) return

            val androidContext = remoteSideContext.androidContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val vibrator = androidContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun shouldPerformHaptics(remoteSideContext: RemoteSideContext): Boolean {
        return remoteSideContext.config.root.global.uiSettings.hapticFeedback.get()
    }
}
