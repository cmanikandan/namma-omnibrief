package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("omnibrief_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_X_BEARER_TOKEN = "x_bearer_token"
        private const val KEY_X_CLIENT_ID = "x_client_id"
        private const val KEY_X_CLIENT_SECRET = "x_client_secret"
        private const val KEY_X_ACCESS_TOKEN = "x_access_token"
        private const val KEY_X_REFRESH_TOKEN = "x_refresh_token"
        private const val KEY_X_AUTH_METHOD = "x_auth_method"
        private const val KEY_IS_X_BLUE = "is_x_blue"
        private const val KEY_DEFAULT_SOURCE = "default_source"
        private const val KEY_IS_LIGHT_THEME = "is_light_theme"
        private const val KEY_FONT_SCALE = "font_scale"

        const val DEFAULT_MODEL = "gemini-3.8-flash"

        /**
         * Default text scale. Deliberately above 1.0 — the stock sizing read too small, so the
         * app ships slightly larger and the user can adjust from Settings.
         */
        const val DEFAULT_FONT_SCALE = 1.15f

        /** Selectable text sizes, applied to the whole Material type scale. */
        val FONT_SCALE_OPTIONS: List<Pair<Float, String>> = listOf(
            0.90f to "Compact",
            1.00f to "Standard",
            1.15f to "Comfortable",
            1.30f to "Large",
            1.50f to "Extra Large"
        )

        /** Maximum number of article images that can be queued for a single batch. */
        const val MAX_ARTICLE_IMAGES = 10

        /** X hard character limits. Exceeding them makes the API reject the post outright. */
        const val X_STANDARD_CHAR_LIMIT = 280
        const val X_PREMIUM_CHAR_LIMIT = 25000

        /** Selectable Gemini models, newest first. All verified against the v1beta ListModels API. */
        val AVAILABLE_MODELS: List<Pair<String, String>> = listOf(
            "gemini-3.8-flash" to "Gemini Flash 3.8 (High Context)",
            "gemini-3.7-flash" to "Gemini Flash 3.7",
            "gemini-3.6-flash" to "Gemini Flash 3.6",
            "gemini-3.5-flash" to "Gemini Flash 3.5 (Standard)",
            "gemini-2.5-flash" to "Gemini Flash 2.5 (Fallback)"
        )
    }

    /**
     * Credentials are NEVER hardcoded in source. Resolution order:
     *  1. Value saved by the user in Settings (SharedPreferences, on-device only).
     *  2. BuildConfig value injected from the local, git-ignored `.env` file.
     *  3. Empty -> the UI prompts the user to configure the key.
     */
    private fun buildConfigValue(raw: String, placeholder: String): String {
        return if (raw.isNotBlank() && raw != placeholder) raw else ""
    }

    var geminiApiKey: String
        get() {
            val custom = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom
            return try {
                buildConfigValue(BuildConfig.GEMINI_API_KEY, "MY_GEMINI_API_KEY")
            } catch (e: Exception) {
                ""
            }
        }
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    /** Value bound to the Settings text field (never falls back to a baked-in key). */
    var customGeminiKeyInput: String
        get() = geminiApiKey
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var xClientId: String
        get() = prefs.getString(KEY_X_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_X_CLIENT_ID, value.trim()).apply()

    var xClientSecret: String
        get() = prefs.getString(KEY_X_CLIENT_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_X_CLIENT_SECRET, value.trim()).apply()

    var xAccessToken: String
        get() = prefs.getString(KEY_X_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_X_ACCESS_TOKEN, value.trim()).apply()

    var xRefreshToken: String
        get() = prefs.getString(KEY_X_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_X_REFRESH_TOKEN, value.trim()).apply()

    var xAuthMethod: String
        get() = prefs.getString(KEY_X_AUTH_METHOD, "OAUTH2_USER") ?: "OAUTH2_USER"
        set(value) = prefs.edit().putString(KEY_X_AUTH_METHOD, value).apply()

    var xBearerToken: String
        get() {
            val saved = prefs.getString(KEY_X_BEARER_TOKEN, "") ?: ""
            if (saved.isNotBlank()) return saved
            return try {
                buildConfigValue(BuildConfig.X_BEARER_TOKEN, "MY_X_BEARER_TOKEN")
            } catch (e: Exception) {
                ""
            }
        }
        set(value) = prefs.edit().putString(KEY_X_BEARER_TOKEN, value.trim()).apply()

    /** True when at least one usable X credential set is configured. */
    val hasXCredentials: Boolean
        get() = if (xAuthMethod == "OAUTH2_USER") {
            xAccessToken.isNotBlank() || (xClientId.isNotBlank() && xRefreshToken.isNotBlank())
        } else {
            xBearerToken.isNotBlank()
        }

    var isXBlue: Boolean
        get() = prefs.getBoolean(KEY_IS_X_BLUE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_X_BLUE, value).apply()

    var defaultSource: String
        get() = prefs.getString(KEY_DEFAULT_SOURCE, "Auto-Detect") ?: "Auto-Detect"
        set(value) = prefs.edit().putString(KEY_DEFAULT_SOURCE, value).apply()

    var isLightTheme: Boolean
        get() = prefs.getBoolean(KEY_IS_LIGHT_THEME, true) // Default to Light theme as requested
        set(value) = prefs.edit().putBoolean(KEY_IS_LIGHT_THEME, value).apply()

    /**
     * Whole-app text scale. Clamped to the range the type scale is designed for, so a stale or
     * out-of-range stored value can never render the UI unreadable.
     */
    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE).coerceIn(0.85f, 1.6f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.85f, 1.6f)).apply()
}
