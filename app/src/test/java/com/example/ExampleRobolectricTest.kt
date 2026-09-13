package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppPreferences
import com.example.ui.screens.KEY_ALIAS_ACCESS_TOKEN
import com.example.ui.screens.KEY_ALIAS_BEARER
import com.example.ui.screens.KEY_ALIAS_CLIENT_ID
import com.example.ui.screens.KEY_ALIAS_CLIENT_SECRET
import com.example.ui.screens.KEY_ALIAS_GEMINI
import com.example.ui.screens.KEY_ALIAS_REFRESH_TOKEN
import com.example.ui.screens.parseKeyBlob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Namma Omnibrief", appName)
  }

  @Test
  fun `credentials are empty by default and never hardcoded`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)

    // No secret may ever be hardcoded in source. The only legitimate non-user source is the
    // local, git-ignored .env injected through BuildConfig, so assert against exactly that:
    // on a clean checkout (no .env) every credential must resolve to empty.
    val expectedGemini = BuildConfig.GEMINI_API_KEY
      .takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } ?: ""
    val expectedBearer = BuildConfig.X_BEARER_TOKEN
      .takeIf { it.isNotBlank() && it != "MY_X_BEARER_TOKEN" } ?: ""

    assertEquals("OAUTH2_USER", prefs.xAuthMethod)
    assertEquals(expectedGemini, prefs.geminiApiKey)
    assertEquals(expectedBearer, prefs.xBearerToken)

    // The OAuth 2.0 credentials have no build-time fallback at all: they are user-supplied only.
    assertTrue(prefs.xClientId.isBlank())
    assertTrue(prefs.xClientSecret.isBlank())
    assertTrue(prefs.xAccessToken.isBlank())
    assertTrue(prefs.xRefreshToken.isBlank())
    assertFalse(prefs.hasXCredentials)

    // Light theme is the default look and feel.
    assertTrue(prefs.isLightTheme)
  }

  @Test
  fun `credentials persist once saved from settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)

    prefs.xClientId = "test_custom_client_id"
    prefs.xClientSecret = "test_secret"
    prefs.xAccessToken = "test_access"
    prefs.xRefreshToken = "test_refresh"

    assertEquals("test_custom_client_id", prefs.xClientId)
    assertEquals("test_secret", prefs.xClientSecret)
    assertEquals("test_access", prefs.xAccessToken)
    assertEquals("test_refresh", prefs.xRefreshToken)
    assertTrue(prefs.hasXCredentials)
  }

  @Test
  fun `default gemini model is in the selectable list`() {
    assertTrue(AppPreferences.AVAILABLE_MODELS.any { it.first == AppPreferences.DEFAULT_MODEL })
    assertEquals(10, AppPreferences.MAX_ARTICLE_IMAGES)
  }

  @Test
  fun `bulk key blob parser handles env and colon formats`() {
    val blob = """
      # my keys
      export GEMINI_API_KEY=AIzaSyTest123
      X_CLIENT_ID: FAKE_CLIENT_ID_123
      "X_CLIENT_SECRET": "secret-value",
      X-Access-Token = access-value
      TWITTER_REFRESH_TOKEN=refresh-value
      X_BEARER_TOKEN=AAAAbearer
      not a pair
    """.trimIndent()

    val parsed = parseKeyBlob(blob)

    assertEquals("AIzaSyTest123", parsed[KEY_ALIAS_GEMINI])
    assertEquals("FAKE_CLIENT_ID_123", parsed[KEY_ALIAS_CLIENT_ID])
    assertEquals("secret-value", parsed[KEY_ALIAS_CLIENT_SECRET])
    assertEquals("access-value", parsed[KEY_ALIAS_ACCESS_TOKEN])
    assertEquals("refresh-value", parsed[KEY_ALIAS_REFRESH_TOKEN])
    assertEquals("AAAAbearer", parsed[KEY_ALIAS_BEARER])
  }

  @Test
  fun `bulk key blob parser ignores unrelated text`() {
    assertTrue(parseKeyBlob("just some pasted article text with no keys").isEmpty())
  }
}
