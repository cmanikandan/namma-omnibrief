package com.example.data.preferences

import android.content.Context
import com.example.data.remote.HeadlineSort
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
        private const val KEY_X_TOKEN_EXPIRES_AT = "x_token_expires_at"
        private const val KEY_X_AUTH_METHOD = "x_auth_method"
        private const val KEY_IS_X_BLUE = "is_x_blue"
        private const val KEY_DEFAULT_SOURCE = "default_source"
        private const val KEY_IS_LIGHT_THEME = "is_light_theme"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HEADLINE_SORT = "headline_sort"
        private const val KEY_ATTACH_IMAGE = "attach_image_to_post"

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

    /**
     * The X access token.
     *
     * Setting this clears [xTokenExpiresAt], because a hand-pasted token carries no expiry
     * information and keeping the previous one would have the app believe a brand-new token is
     * already stale (or worse, that an old one is still good). Use [saveRefreshedTokens] when the
     * value came from a refresh and the lifetime *is* known.
     */
    var xAccessToken: String
        get() = prefs.getString(KEY_X_ACCESS_TOKEN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_X_ACCESS_TOKEN, value.trim()).apply()
            xTokenExpiresAt = 0L
        }

    var xRefreshToken: String
        get() = prefs.getString(KEY_X_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_X_REFRESH_TOKEN, value.trim()).apply()

    /**
     * Epoch millis at which [xAccessToken] stops being valid, or **0 when unknown**.
     *
     * X access tokens last about two hours. Storing the deadline lets the app renew one *before*
     * using it rather than discovering the problem as a failed post. 0 means "no idea" — the app
     * then just tries the token and falls back to refreshing on a 401.
     */
    var xTokenExpiresAt: Long
        get() = prefs.getLong(KEY_X_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_X_TOKEN_EXPIRES_AT, value).apply()

    /**
     * Persists the result of a successful token refresh.
     *
     * Writes all three values in a fixed order so the expiry is not wiped by [xAccessToken]'s
     * setter. **The refresh token must be stored too**: X rotates it on every refresh and
     * invalidates the previous one, so dropping the new value locks the app out until the user
     * re-authorises by hand.
     */
    fun saveRefreshedTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        xAccessToken = accessToken
        if (refreshToken.isNotBlank()) xRefreshToken = refreshToken
        xTokenExpiresAt =
            if (expiresInSeconds > 0) System.currentTimeMillis() + expiresInSeconds * 1000 else 0L
    }

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

    /**
     * True when at least one usable X credential set is configured.
     *
     * A client ID plus a refresh token is enough on its own: the app can mint an access token from
     * those, so requiring a pasted access token as well would reject a perfectly workable setup.
     */
    val hasXCredentials: Boolean
        get() = if (xAuthMethod == "OAUTH2_USER") {
            xAccessToken.isNotBlank() || (xClientId.isNotBlank() && xRefreshToken.isNotBlank())
        } else {
            xBearerToken.isNotBlank()
        }

    var isXBlue: Boolean
        get() = prefs.getBoolean(KEY_IS_X_BLUE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_X_BLUE, value).apply()

    /**
     * Whether the photographed article is uploaded and attached to the post.
     *
     * Defaults to on — a post about a newspaper page reads far better with the page attached, and
     * that is what the user expects to happen.
     *
     * It is a setting rather than always-on because attaching requires the `media.write` OAuth
     * scope, which `tweet.write` does not imply. An authorisation granted without it can publish
     * text perfectly well but gets a bare 403 on upload, and re-authorising is a trip to the X
     * Developer Portal. Turning this off is the escape hatch that keeps the app usable in the
     * meantime, instead of the app quietly dropping the image and leaving the user to notice.
     */
    var attachImageToPost: Boolean
        get() = prefs.getBoolean(KEY_ATTACH_IMAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_ATTACH_IMAGE, value).apply()

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

    /**
     * How the Today feed is ordered. Persisted so the choice survives a restart.
     *
     * Stored by the enum's stable [com.example.data.remote.HeadlineSort.id] rather than by
     * `name` or ordinal, so renaming or reordering the enum cannot silently reset a user's choice.
     */
    var headlineSort: HeadlineSort
        get() = HeadlineSort.fromId(prefs.getString(KEY_HEADLINE_SORT, null))
        set(value) = prefs.edit().putString(KEY_HEADLINE_SORT, value.id).apply()
}
