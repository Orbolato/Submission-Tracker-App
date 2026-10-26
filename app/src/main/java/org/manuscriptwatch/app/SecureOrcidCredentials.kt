package org.manuscriptwatch.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Optional app-local vault. AndroidKeyStore key never leaves the device. */
object SecureOrcidCredentials {
    private const val KEY_ALIAS = "manuscript_watch_orcid_v1"
    private const val PREFS = "manuscript_watch_secure_orcid"
    private const val KEY_BLOB = "encrypted_login"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false) // Allows usage without a foreground biometric prompt.
            .build())
        return generator.generateKey()
    }

    /** Explicit opt-in required before calling save. No cloud backup and no plaintext preferences. */
    fun save(context: Context, username: String, password: String) {
        require(username.isNotBlank() && password.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = JSONObject().put("username", username).put("password", password)
            .toString().toByteArray(Charsets.UTF_8)
        try {
            val blob = JSONObject()
                .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("value", Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP))
            check(prefs(context).edit().putString(KEY_BLOB, blob.toString()).commit())
        } finally { bytes.fill(0) }
    }

    fun isSaved(context: Context): Boolean = prefs(context).contains(KEY_BLOB)

    /** Caller must constrain use to HTTPS on the exact ORCID host, never arbitrary URLs. */
    fun read(context: Context): Pair<String, String>? {
        val raw = prefs(context).getString(KEY_BLOB, null) ?: return null
        return try {
            val data = JSONObject(raw)
            val iv = Base64.decode(data.getString("iv"), Base64.NO_WRAP)
            val ct = Base64.decode(data.getString("value"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ct)
            try {
                val decoded = JSONObject(String(plain, Charsets.UTF_8))
                decoded.getString("username") to decoded.getString("password")
            } finally { plain.fill(0) }
        } catch (_: Exception) { null }
    }

    fun delete(context: Context) { prefs(context).edit().remove(KEY_BLOB).commit() }
}
