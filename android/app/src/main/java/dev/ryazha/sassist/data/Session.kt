package dev.ryazha.sassist.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure session store. Token, display name and per-room E2EE keys live in
 * EncryptedSharedPreferences backed by the Android Keystore (AES-256-GCM).
 * If the Keystore is unavailable we transparently fall back to plain prefs
 * so the app never crashes on exotic devices.
 */
class Session(context: Context) {
    companion object {
        // Free local backend: run `docker compose up -d --build` on this computer.
        // Android emulator reaches the host machine through 10.0.2.2.
        const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8080"
    }

    private val prefs: SharedPreferences = run {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "sassist_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("sassist", Context.MODE_PRIVATE)
        }
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().putString("token", v).apply() }

    var username: String?
        get() = prefs.getString("username", null)
        set(v) { prefs.edit().putString("username", v).apply() }

    // Defaults to the free local backend on the developer machine. Users can
    // still replace it with a LAN/cloud URL in Server settings.
    var serverUrl: String
        get() = prefs.getString("serverUrl", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(v) { prefs.edit().putString("serverUrl", v).apply() }

    fun roomKey(channel: String): String =
        prefs.getString("roomkey_" + channel, null) ?: ("sa-default-" + channel)

    /** True once the user has set their own E2EE passphrase for this room. */
    fun hasCustomRoomKey(channel: String): Boolean =
        prefs.getString("roomkey_" + channel, null) != null

    fun setRoomKey(channel: String, key: String) {
        prefs.edit().putString("roomkey_" + channel, key).apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
