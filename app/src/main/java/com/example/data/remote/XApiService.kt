package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed class XPostResult {
    data class Success(val tweetId: String, val text: String) : XPostResult()
    data class Error(val message: String) : XPostResult()
}

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

    suspend fun postTweet(
        accessToken: String,
        text: String,
        clientId: String = "",
        clientSecret: String = "",
        refreshToken: String = "",
        onTokenRefreshed: (suspend (newAccess: String, newRefresh: String) -> Unit)? = null
    ): XPostResult = withContext(Dispatchers.IO) {
        var currentToken = accessToken.trim()
        if (currentToken.isBlank() && refreshToken.isNotBlank() && clientId.isNotBlank()) {
            val refresh = refreshOAuth2Token(clientId, clientSecret, refreshToken)
            if (refresh.success) {
                currentToken = refresh.accessToken
                onTokenRefreshed?.invoke(refresh.accessToken, refresh.refreshToken)
            } else {
                return@withContext XPostResult.Error("Could not obtain access token: ${refresh.error}")
            }
        }

        if (currentToken.isBlank()) {
            return@withContext XPostResult.Error("No X Access Token or OAuth 2.0 credentials configured. Please check Settings.")
        }

        var postRes = executePostRequest(currentToken, text)

        // If 401 Unauthorized (expired token) and we have refresh credentials, auto-refresh and retry once!
        if (postRes is XPostResult.Error && postRes.message.contains("Unauthorized", ignoreCase = true) && refreshToken.isNotBlank() && clientId.isNotBlank()) {
            Log.d("XApiService", "Access token expired. Refreshing OAuth 2.0 token...")
            val refresh = refreshOAuth2Token(clientId, clientSecret, refreshToken)
            if (refresh.success) {
                currentToken = refresh.accessToken
                onTokenRefreshed?.invoke(refresh.accessToken, refresh.refreshToken)
                postRes = executePostRequest(currentToken, text)
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

    private fun executePostRequest(token: String, text: String): XPostResult {
        try {
            val url = "https://api.twitter.com/2/tweets"
            val payload = JSONObject().apply {
                put("text", text)
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
