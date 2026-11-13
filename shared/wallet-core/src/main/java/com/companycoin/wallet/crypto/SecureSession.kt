package com.companycoin.wallet.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Account session for the optional CoCo account (wallet backup/restore).
 * The JWT is stored in EncryptedSharedPreferences (Android Keystore).
 */
object SecureSession {
    private const val FILE = "coco_account_secure"
    private const val FALLBACK_FILE = "coco_account_fallback"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Rare devices without a usable keystore: keep the feature working.
            context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
        }
    }

    fun save(context: Context, token: String, email: String?, name: String?) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun email(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)
    fun name(context: Context): String? = prefs(context).getString(KEY_NAME, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
