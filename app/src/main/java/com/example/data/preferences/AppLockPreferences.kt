package com.example.data.preferences

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores app-lock settings and never persists the PIN itself. */
class AppLockPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Method { BIOMETRIC, PIN }
    enum class Policy { ON_APP_CLOSE, ON_SCREEN_OFF, IMMEDIATELY, AFTER_TIMEOUT }

    companion object {
        private const val PREFS_NAME = "clipnest_preferences"
        private const val KEY_ENABLED = "app_lock_enabled"
        private const val KEY_METHOD = "app_lock_method"
        private const val KEY_POLICY = "app_lock_policy"
        private const val KEY_TIMEOUT_MINUTES = "app_lock_timeout_minutes"
        private const val KEY_PIN_HASH = "app_lock_pin_hash"
        private const val KEY_PIN_SALT = "app_lock_pin_salt"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var method: Method
        get() = runCatching { Method.valueOf(prefs.getString(KEY_METHOD, Method.BIOMETRIC.name)!!) }
            .getOrDefault(Method.BIOMETRIC)
        set(value) = prefs.edit().putString(KEY_METHOD, value.name).apply()

    var policy: Policy
        get() = runCatching { Policy.valueOf(prefs.getString(KEY_POLICY, Policy.ON_APP_CLOSE.name)!!) }
            .getOrDefault(Policy.ON_APP_CLOSE)
        set(value) = prefs.edit().putString(KEY_POLICY, value.name).apply()

    var timeoutMinutes: Int
        get() = prefs.getInt(KEY_TIMEOUT_MINUTES, 5).coerceIn(1, 120)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT_MINUTES, value.coerceIn(1, 120)).apply()

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)

    fun setPin(pin: String) {
        require(pin.length >= 4) { "PIN must contain at least 4 digits" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash.toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.fromHex() ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.fromHex() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }
            .getOrElse { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") }
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
