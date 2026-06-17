package com.reference.implementation.messages.data.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "refresh_manager_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

class RefreshManager(context: Context) {

    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    // --- Public API ---
    suspend fun saveRefresh(refresh: String) = withContext(Dispatchers.IO) {

        val (encryptedRefresh, refreshIV) = encrypt(refresh)

        // Store both the encrypted data and the IV
        // The "apply()" is embedded in the edit {} body
        prefs.edit {
            putString("encrypted_refresh", encryptedRefresh)
            putString("refresh_iv", refreshIV)
        }
    }

    suspend fun getRefresh(): String? = withContext(Dispatchers.IO) {

        val encryptedRefresh = prefs.getString("encrypted_refresh", null) ?: return@withContext null
        val iv = prefs.getString("refresh_iv", null) ?: return@withContext null

        decrypt(encryptedRefresh, iv)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // 1. Wipe the "Island" (Hardware)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(KEY_ALIAS)

        // 2. Wipe the "Mess" (Persistence)
        prefs.edit { clear() }
    }

    // --- Encryption Logic ---
    private fun encrypt(data: String): Pair<String, String> {

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encryptedBytes = cipher.doFinal(data.toByteArray())
        val iv = cipher.iv // Initialization Vector is needed for decryption

        val encodedBytesToString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        val encodedIvToString = Base64.encodeToString(iv, Base64.DEFAULT)

        return Pair(encodedBytesToString, encodedIvToString)
    }

    private fun decrypt(encryptedData: String, iv: String): String {

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))

        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedBytes)

        return String(decryptedBytes)
    }

    // --- Hardware KeyStore (The "Island" bridge) ---
    // Idempotent: get or create a key
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        // If the key already exists, just return it. Voila!
        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as SecretKey
        }

        // Otherwise, generate a new one
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
