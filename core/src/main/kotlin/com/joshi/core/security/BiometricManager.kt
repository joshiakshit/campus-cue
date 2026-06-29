package com.joshi.core.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricManager
    @Inject
    constructor(
        private val keystoreHelper: KeystoreHelper,
    ) {
        fun authenticate(
            activity: FragmentActivity,
            title: String = "Authenticate",
            subtitle: String = "Verify your identity to continue",
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
        ) {
            val cipher = keystoreHelper.getCipher(BIOMETRIC_KEY_ALIAS)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            onError(errString.toString())
                        }
                    },
                )

            val promptInfo =
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Cancel")
                    .build()

            prompt.authenticate(promptInfo, cryptoObject)
        }

        companion object {
            private const val BIOMETRIC_KEY_ALIAS = "campuscue_biometric_key"
        }
    }
