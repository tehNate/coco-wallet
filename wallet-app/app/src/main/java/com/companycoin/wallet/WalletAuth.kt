package com.companycoin.wallet

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * One device-auth gate for send / seed export / voting.
 *
 * The old wallet code used a strong-biometric prompt with a Cancel button.
 * That fails on PIN-only devices and on phones without enrolled biometrics.
 * This helper allows BIOMETRIC_WEAK or DEVICE_CREDENTIAL on Android 11+, and
 * falls back to a clear "set a screen lock" message instead of a dead prompt.
 */
object WalletAuth {
    private const val AUTH_TAG = "CoCoWalletAuth"

    fun status(context: Context): String {
        val bm = BiometricManager.from(context)
        fun label(code: Int): String = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> "available"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "no hardware"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "not enrolled"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "security update required"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "unsupported"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "unknown"
            else -> "code $code"
        }
        val bio = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val cred = bm.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        return "Biometric: ${label(bio)}; device credential: ${label(cred)}"
    }

    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val manager = BiometricManager.from(context)
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(fragment, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    Toast.makeText(context, errString, Toast.LENGTH_LONG).show()
                }
            }
        })

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        // Android 11+: biometric or the device PIN/pattern/password, whichever
        // the user has. Do not set a negative button in this mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS) {
                builder.setAllowedAuthenticators(allowed)
                prompt.authenticate(builder.build())
                return
            }
        }

        // Android 7.1–10: biometric prompt; if none is enrolled, the caller gets
        // a clear message instead of a silent failure.
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
            prompt.authenticate(builder.build())
            return
        }

        Toast.makeText(
            context,
            "Set a screen lock (PIN/pattern) or enroll a fingerprint/face in Android Settings, then retry.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
