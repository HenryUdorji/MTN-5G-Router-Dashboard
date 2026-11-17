package com.routerdash.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RouterSettings(val host: String, val password: String?)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Router address and admin password. The password is encrypted with a key kept in the Android Keystore. */
class SettingsStore(private val context: Context) {
    private val hostKey = stringPreferencesKey("host")
    private val passwordKey = stringPreferencesKey("password_enc")

    val settings: Flow<RouterSettings> = context.dataStore.data
        .map { p -> RouterSettings(p[hostKey] ?: DEFAULT_HOST, p[passwordKey]?.let(PasswordCipher::decrypt)) }
        .distinctUntilChanged()

    suspend fun save(host: String, password: String) {
        val encrypted = PasswordCipher.encrypt(password)
        context.dataStore.edit {
            it[hostKey] = host
            it[passwordKey] = encrypted
        }
    }

    suspend fun clearPassword() {
        context.dataStore.edit { it.remove(passwordKey) }
    }

    companion object {
        const val DEFAULT_HOST = "192.168.0.1"
    }
}

private object PasswordCipher {
    private const val ALIAS = "routerdash_password"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = runCatching {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, IV_BYTES))
        String(c.doFinal(all, IV_BYTES, all.size - IV_BYTES))
    }.getOrNull()
}
