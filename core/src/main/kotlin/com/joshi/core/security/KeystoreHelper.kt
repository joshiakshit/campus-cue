package com.joshi.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreHelper
    @Inject
    constructor() {
        private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        fun getOrCreateKey(
            alias: String,
            requireBiometric: Boolean = false,
        ): SecretKey {
            keyStore.getEntry(alias, null)?.let { entry ->
                return (entry as KeyStore.SecretKeyEntry).secretKey
            }

            val spec =
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(requireBiometric)
                    .build()

            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }

        fun getCipher(alias: String): Cipher {
            val key = getOrCreateKey(alias, requireBiometric = true)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        }
    }
