package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed class XPostResult {
    data class Success(val tweetId: String, val text: String) : XPostResult()
    data class Error(val message: String) : XPostResult()
}

/**
 * Outcome of an X media upload.
 *
 * [mediaId] is the opaque string X returns for a successfully uploaded image; it is attached to
 * the subsequent tweet. It is a *string* and not a number on purpose — the ids overflow a 64-bit
 * signed integer and JSON parsers silently mangle them.
 */
data class MediaUploadResult(
    val success: Boolean,
    val mediaId: String = "",
    val error: String? = null
)

data class TokenRefreshResult(
    val success: Boolean,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Long = 0,
    val error: String? = null
)

class XApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun refreshOAuth2Token(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): TokenRefreshResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || refreshToken.isBlank()) {
            return@withContext TokenRefreshResult(false, error = "Client ID and Refresh Token are required.")
        }

        try {
            val credentials = Base64.encodeToString(
                "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build()

            val request = Request.Builder()
                .url("https://api.twitter.com/2/oauth2/token")
                .addHeader("Authorization", "Basic $credentials")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val newAccess = json.optString("access_token", "")
                    val newRefresh = json.optString("refresh_token", refreshToken)
                    val expiresIn = json.optLong("expires_in", 7200)
                    TokenRefreshResult(true, newAccess, newRefresh, expiresIn, null)
                } else {
                    val errDetail = try {
                        val json = JSONObject(body)
                        json.optString("error_description", json.optString("error", "HTTP ${response.code}"))
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    TokenRefreshResult(false, error = errDetail)
                }
            }
        } catch (e: Exception) {
            Log.e("XApiService", "Failed to refresh X token", e)
            TokenRefreshResult(false, error = e.localizedMessage ?: e.message)
        }
    }

    /**
     * Publishes [text], optionally with [imageUri] attached as a photo.
     *
     * The access token is renewed from [refreshToken] **before** the call when it is missing or
     * known to be expiring, rather than only after X rejects it. An X access token lives about two
     * hours, so for anything but heavy daily use the stored one is almost always stale by the time
     * the user actually wants to post — reacting to the 401 turns every such post into two round
     * trips, and the first one fails visibly if the retry then goes wrong.
     *
     * [accessTokenExpiresAt] is epoch millis, or 0 when unknown (a hand-pasted token). Unknown
     * means "try it and see": the 401 retry below is still there as the backstop.
     *
     * [onTokenRefreshed] must persist all three values. X rotates the refresh token on every
     * refresh and invalidates the old one, so failing to save the new one locks the user out.
     *
     * The media upload deliberately happens *after* the token has been freshened and *before* the
     * tweet, so it always runs with a valid token. If it fails the whole post fails: silently
     * publishing text-only is precisely the bug this attachment support exists to fix, and a tweet
     * cannot be edited to add the picture afterwards.
     */
    suspend fun postTweet(
        accessToken: String,
        text: String,
        clientId: String = "",
        clientSecret: String = "",
        refreshToken: String = "",
        accessTokenExpiresAt: Long = 0L,
        imageUri: Uri? = null,
        appContext: Context? = null,
        onTokenRefreshed: (suspend (newAccess: String, newRefresh: String, expiresInSeconds: Long) -> Unit)? = null
    ): XPostResult = withContext(Dispatchers.IO) {
        var currentToken = accessToken.trim()
        val canRefresh = refreshToken.isNotBlank() && clientId.isNotBlank()

        if (canRefresh && needsRefresh(currentToken, accessTokenExpiresAt, System.currentTimeMillis())) {
            val refresh = refreshOAuth2Token(clientId, clientSecret, refreshToken)
            if (refresh.success) {
                currentToken = refresh.accessToken
                onTokenRefreshed?.invoke(refresh.accessToken, refresh.refreshToken, refresh.expiresIn)
            } else if (currentToken.isBlank()) {
                return@withContext XPostResult.Error("Could not obtain access token: ${refresh.error}")
            }
            // A failed refresh while we still hold a token is not fatal: the token may yet work,
            // and if it does not the 401 path below produces a far more useful message.
        }

        if (currentToken.isBlank()) {
            return@withContext XPostResult.Error("No X Access Token or OAuth 2.0 credentials configured. Please check Settings.")
        }

        val mediaIds = mutableListOf<String>()
        if (imageUri != null && appContext != null) {
            val upload = uploadImage(currentToken, imageUri, appContext)
            if (!upload.success) {
                return@withContext XPostResult.Error(
                    upload.error ?: "Could not upload the photo to X."
                )
            }
            mediaIds.add(upload.mediaId)
        }

        var postRes = executePostRequest(currentToken, text, mediaIds)

        // If 401 Unauthorized (expired token) and we have refresh credentials, auto-refresh and retry once!
        if (postRes is XPostResult.Error && postRes.message.contains("Unauthorized", ignoreCase = true) && canRefresh) {
            Log.d("XApiService", "Access token expired. Refreshing OAuth 2.0 token...")
            val refresh = refreshOAuth2Token(clientId, clientSecret, refreshToken)
            if (refresh.success) {
                currentToken = refresh.accessToken
                onTokenRefreshed?.invoke(refresh.accessToken, refresh.refreshToken, refresh.expiresIn)
                postRes = executePostRequest(currentToken, text, mediaIds)
            } else {
                // X refresh tokens are single-use: each refresh issues a new one and invalidates
                // the old. A stale copy in Settings is by far the most common cause here, and the
                // raw API message ("Value passed for the token was invalid") does not say so.
                val hint = if (refresh.error?.contains("invalid", ignoreCase = true) == true) {
                    "Your refresh token is no longer valid. X refresh tokens are single-use and " +
                        "rotate on every refresh, so a saved copy goes stale once it has been used " +
                        "elsewhere. Re-authorise the app in the X Developer Portal and paste the new " +
                        "Access and Refresh tokens into Settings."
                } else {
                    refresh.error ?: "Unknown error"
                }
                return@withContext XPostResult.Error("Could not renew your X session. $hint")
            }
        }

        postRes
    }

    /**
     * Uploads a single image to X and returns its media id.
     *
     * Uses the v2 `POST /2/media/upload` multipart form, which handles images in one shot — the
     * chunked INIT/APPEND/FINALIZE dance is only needed for video and animated GIF.
     *
     * The image is re-encoded to a JPEG capped at [MAX_IMAGE_DIMENSION_PX] before upload, matching
     * what is sent to Gemini. A modern phone photo is 4000 px and 8 MB; X's limit is 5 MB, and
     * nothing is gained by pushing full resolution through a mobile connection for a timeline
     * image that renders at around 1200 px.
     */
    suspend fun uploadImage(
        accessToken: String,
        imageUri: Uri,
        context: Context
    ): MediaUploadResult = withContext(Dispatchers.IO) {
        val bytes = readUriAsJpegBytes(context, imageUri)
            ?: return@withContext MediaUploadResult(false, error = "Could not read the selected image.")

        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media_category", "tweet_image")
                .addFormDataPart(
                    "media",
                    "image.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.x.com/2/media/upload")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    // v2 nests the id under `data`; the v1.1-compatible shape returns it flat.
                    val id = json.optJSONObject("data")?.optString("id", "").orEmpty()
                        .ifBlank { json.optString("media_id_string", "") }
                    if (id.isBlank()) {
                        MediaUploadResult(false, error = "X accepted the image but returned no media id.")
                    } else {
                        MediaUploadResult(true, id)
                    }
                } else if (response.code == 403) {
                    // X answers a missing scope with a bare {"title":"Forbidden","status":403} and
                    // never mentions scopes, so the explanation has to come from here. This is the
                    // single most likely cause: media.write is a separate scope from tweet.write
                    // and is easy to leave unticked when authorising.
                    MediaUploadResult(
                        false,
                        error = "X refused the image upload (403). Your X authorisation is most " +
                            "likely missing the 'media.write' scope — posting text works without " +
                            "it, attaching a photo does not. Re-authorise the app in the X " +
                            "Developer Portal with media.write enabled, then paste the new tokens " +
                            "into Settings. To post without the photo in the meantime, turn off " +
                            "'Attach photo to post' in Settings."
                    )
                } else {
                    val detail = try {
                        val err = JSONObject(responseBody)
                        err.optString("detail", err.optString("title", "HTTP ${response.code}"))
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }
                    MediaUploadResult(false, error = "Image upload failed: $detail")
                }
            }
        } catch (e: Exception) {
            Log.e("XApiService", "Error uploading media to X", e)
            MediaUploadResult(false, error = "Network error during image upload: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Decodes [uri] and re-encodes it as a JPEG small enough to upload.
     *
     * Delegates to [ImageEncoder] so the bytes X receives are identical to the ones Gemini analysed
     * — including the EXIF rotation, which this used to drop and which put a sideways newspaper on
     * the timeline.
     */
    private fun readUriAsJpegBytes(context: Context, uri: Uri): ByteArray? =
        ImageEncoder.readUriAsJpegBytes(
            context = context,
            uri = uri,
            maxDimension = MAX_IMAGE_DIMENSION_PX,
            quality = JPEG_QUALITY,
            logTag = "XApiService"
        )

    private fun executePostRequest(
        token: String,
        text: String,
        mediaIds: List<String> = emptyList()
    ): XPostResult {
        try {
            val url = "https://api.twitter.com/2/tweets"
            val payload = JSONObject().apply {
                put("text", text)
                if (mediaIds.isNotEmpty()) {
                    put("media", JSONObject().apply {
                        put("media_ids", JSONArray(mediaIds))
                    })
                }
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                return if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val data = json.optJSONObject("data")
                    val tweetId = data?.optString("id", "") ?: "success"
                    XPostResult.Success(tweetId, text)
                } else {
                    val errorDetail = try {
                        val errJson = JSONObject(responseBody)
                        val detail = errJson.optString("detail", "")
                        val title = errJson.optString("title", "")
                        if (response.code == 403 && detail.contains("Application-Only", ignoreCase = true)) {
                            "X API Notice: Provided token is an App-Only Bearer Token. Please configure OAuth 2.0 User Context in Settings or use 'X App' button."
                        } else if (response.code == 401) {
                            "Unauthorized: Token may be expired or invalid."
                        } else if (detail.isNotBlank()) {
                            detail
                        } else if (title.isNotBlank()) {
                            title
                        } else {
                            "HTTP ${response.code}: $responseBody"
                        }
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }
                    XPostResult.Error(errorDetail)
                }
            }
        } catch (e: Exception) {
            Log.e("XApiService", "Error posting to X", e)
            return XPostResult.Error("Network error: ${e.localizedMessage ?: e.message}")
        }
    }

    companion object {
        /**
         * How far ahead of the deadline a token counts as expired.
         *
         * A token with thirty seconds left will very likely be dead by the time the request lands,
         * and the user pays for that with a failed post. Five minutes is comfortably longer than
         * any plausible round trip or device clock drift, and costs nothing: renewing early is
         * free, whereas renewing late is a visible error.
         */
        const val ACCESS_TOKEN_EXPIRY_SKEW_MS = 5 * 60 * 1000L

        /**
         * Longest edge, in pixels, of an image uploaded to X.
         *
         * Matches the cap used for Gemini so both services see the same picture. X allows 5 MB per
         * image and renders timeline photos at roughly 1200 px wide, so a full-resolution 4000 px
         * phone capture buys nothing and risks the size limit on a slow connection.
         */
        const val MAX_IMAGE_DIMENSION_PX = 1600

        /** JPEG quality for uploaded images. 85 is visually lossless for photographed text. */
        const val JPEG_QUALITY = 85

        /**
         * Whether an access token should be renewed before being used.
         *
         * Pure and side-effect free so the policy can be tested without a network or a clock.
         *
         * [expiresAtMillis] of 0 means the lifetime is unknown — a token the user pasted by hand.
         * That is deliberately **not** treated as expired: the token is probably fine, and
         * refreshing it unnecessarily would burn the single-use refresh token for nothing.
         */
        fun needsRefresh(accessToken: String, expiresAtMillis: Long, nowMillis: Long): Boolean {
            if (accessToken.isBlank()) return true
            if (expiresAtMillis <= 0L) return false
            return nowMillis >= expiresAtMillis - ACCESS_TOKEN_EXPIRY_SKEW_MS
        }

        fun launchDirectXComposer(context: Context, text: String) {
            try {
                val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
                val tweetIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/intent/tweet?text=$encoded"))
                tweetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(tweetIntent)
            } catch (e: Exception) {
                // Fallback to generic share
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Post to X"))
            }
        }
    }
}
