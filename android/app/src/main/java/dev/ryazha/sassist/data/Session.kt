package dev.ryazha.sassist.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.ryazha.sassist.model.AppLanguage

/**
 * Secure session store. Token, display name and per-room E2EE keys live in
 * EncryptedSharedPreferences backed by the Android Keystore (AES-256-GCM).
 * If the Keystore is unavailable we transparently fall back to plain prefs
 * so the app never crashes on exotic devices.
 */
class Session(context: Context) {
    companion object {
        // Public SAssist Labs free Render backend. Users can override it from
        // Server settings, but this verified URL works out of the box.
        const val DEFAULT_SERVER_URL = "wss://sassist-labs.onrender.com"
        private const val LEGACY_KOYEB_URL = "wss://sassist-dimasick-git.koyeb.app"

        // Local fallback for emulator development: run `docker compose up -d --build`.
        const val LOCAL_SERVER_URL = "ws://10.0.2.2:8080"
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

    /** Russian is the initial language even when the device itself is set to another locale. */
    var language: AppLanguage
        get() = AppLanguage.fromStored(prefs.getString("language", AppLanguage.Russian.storedValue))
        set(v) { prefs.edit().putString("language", v.storedValue).apply() }

    // Migrate the inactive historic Koyeb default while preserving every other
    // user-selected server address as an explicit override.
    var serverUrl: String
        get() = prefs.getString("serverUrl", DEFAULT_SERVER_URL)
            ?.takeUnless { it == LEGACY_KOYEB_URL }
            ?: DEFAULT_SERVER_URL
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
