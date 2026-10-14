package com.vansh.quicknotes.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

/**
 * API key at rest (spec §3): AES-GCM under a non-exportable Android Keystore key; the
 * ciphertext lives in app-private prefs. The plaintext key never touches disk, logs or
 * backups (backup is fully excluded in our rules anyway).
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("qn_secrets", Context.MODE_PRIVATE)

    fun hasApiKey(): Boolean = prefs.getString(KEY_CIPHERTEXT, null) != null

    fun getApiKey(): String? {
        val blob = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return runCatching { decrypt(blob) }.getOrNull()
    }

    fun setApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(KEY_CIPHERTEXT).apply()
            return
        }
        prefs.edit().putString(KEY_CIPHERTEXT, encrypt(value)).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val ct = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS * 8, iv))
        }
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "quicknotes-ai-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CIPHERTEXT = "ai_api_key_ct"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 16
    }
}
