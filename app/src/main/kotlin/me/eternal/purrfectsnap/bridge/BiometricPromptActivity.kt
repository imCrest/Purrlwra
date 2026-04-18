@file:Suppress("DEPRECATION")

package cock.crest.purrfectsnap.lite.bridge

import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cock.crest.purrfectsnap.lite.SharedContextHolder
import java.util.concurrent.Executors

class BiometricPromptActivity: ComponentActivity() {
    private val deviceCredentialRequestCode = 2201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun cancel() {
            setResult(RESULT_CANCELED, Intent())
            finish()
        }

        val remoteSideContext = SharedContextHolder.remote(this)
        val executor = Executors.newSingleThreadExecutor()
        val negativeText = remoteSideContext.translation.getOrNull("biometric_auth.cancel")
            ?.takeIf { it.isNotBlank() }
            ?: "Cancel"
        val titleText = remoteSideContext.translation.getOrNull("biometric_auth.title")
            ?.takeIf { it.isNotBlank() }
            ?: "Unlock"
        val subtitleText = remoteSideContext.translation.getOrNull("biometric_auth.subtitle")
            ?.takeIf { it.isNotBlank() }
            ?: "Confirm your screen lock"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val fingerprintManager = getSystemService(FingerprintManager::class.java)
            val hasFingerprintSupport = runCatching {
                fingerprintManager?.isHardwareDetected == true && fingerprintManager.hasEnrolledFingerprints()
            }.getOrDefault(false)
            if (!hasFingerprintSupport) {
                val keyguardManager = getSystemService(KeyguardManager::class.java)
                val canUseDeviceCredential = keyguardManager?.isKeyguardSecure == true
                if (canUseDeviceCredential) {
                    val intent = keyguardManager.createConfirmDeviceCredentialIntent(titleText, subtitleText)
                    if (intent != null) {
                        startActivityForResult(intent, deviceCredentialRequestCode)
                        setContent {}
                        return
                    }
                }
                cancel()
                setContent {}
                return
            }
        }

        BiometricPrompt.Builder(this@BiometricPromptActivity)
            .setTitle(titleText)
            .setSubtitle(subtitleText)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                } else {
                    setNegativeButton(negativeText, mainExecutor) { _, _ -> cancel() }
                }
            }
            .build().authenticate(
                CancellationSignal().apply {
                    setOnCancelListener {
                        cancel()
                    }
                },
                executor,
                object: BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        cancel()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        setResult(RESULT_OK, Intent())
                        finish()
                    }
                }
            )

        setContent {}
    }

    @Deprecated("Overrides deprecated API onActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != deviceCredentialRequestCode) return
        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, Intent())
        } else {
            setResult(RESULT_CANCELED, Intent())
        }
        finish()
    }
}
