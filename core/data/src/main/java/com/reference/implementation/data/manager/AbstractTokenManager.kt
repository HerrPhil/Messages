package com.reference.implementation.data.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.reference.implementation.data.audit.auditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

abstract class AbstractTokenManager(context: Context) {

    protected abstract val keyAlias: String
    protected abstract val encryptedTokenKey: String
    protected abstract val initializationVectorKey: String

    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    // --- Public API ---

    /**
     * Synchronous token persistence for OkHttp Interceptors, Authenticators, and Repositories.
     * Uses .commit() to guarantee synchronous disk flushing before subsequent HTTP retries execute.
     * Wrapping the method body in synchronized(this) guarantees that two threads attempting
     * to save tokens simultaneously won't corrupt key state.
     */
    fun saveToken(token: String): Boolean = synchronized(this) {
        return try {
            // Cipher / KeyStore operations are not guaranteed to be thread-safe
            // across concurrent calls on the same class instance.
            val (encryptedToken, tokenIV) = encrypt(token)

            // Pass 'commit = true' to force synchronous disk writing
            prefs.edit(commit = true) {
                putString(encryptedTokenKey, encryptedToken)
                putString(initializationVectorKey, tokenIV)
            }
        } catch (e: Exception) {
            auditLog("Failed to encrypt or save token: ${e.message}")
            false
        } as Boolean
    }

    /**
     * Wrapping the method body in synchronized(this) guarantees that two threads attempting
     * to get tokens simultaneously won't corrupt key state.
     */
    fun getToken(): String? = synchronized(this) {

        val encryptedToken = prefs.getString(encryptedTokenKey, null) ?: return null
        val iv = prefs.getString(initializationVectorKey, null) ?: return null

        return try {
            // Cipher / KeyStore operations are not guaranteed to be thread-safe
            // across concurrent calls on the same class instance.
            decrypt(encryptedToken, iv)
        } catch (e: Exception) {
            // Handle potential Keystore / Decryption errors gracefully
            auditLog("Decryption failed in getTokenSync: ${e.message}")
            null
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // 1. Wipe the "Island" (Hardware)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(keyAlias)

        // 2. Wipe the "Mess" (Persistence)
        prefs.edit {
            clear()
        }
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
        keyStore.getKey(keyAlias, null)?.let {
            return it as SecretKey
        }

        // Otherwise, generate a new one
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
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