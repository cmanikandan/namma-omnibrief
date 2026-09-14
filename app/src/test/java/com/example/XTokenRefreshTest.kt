package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppPreferences
import com.example.data.remote.XApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the X OAuth 2.0 token lifecycle.
 *
 * ## Why this matters more than it looks
 *
 * X access tokens live about two hours and refresh tokens are **single-use**: every refresh issues
 * a new refresh token and kills the previous one. Two failure modes follow, and both have already
 * bitten this project in real use:
 *
 * 1. Not saving the rotated refresh token locks the user out permanently — the next refresh fails
 *    with "Value passed for the token was invalid" and the only fix is re-authorising by hand.
 * 2. Refreshing when there was no need burns a perfectly good refresh token for nothing.
 *
 * So the policy is tested directly rather than inferred from a posting test, which would require
 * publishing a real tweet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XTokenRefreshTest {

  private lateinit var prefs: AppPreferences

  /** Obviously fake values — never a prefix of a real credential. */
  private val fakeAccess = "FAKE_ACCESS_TOKEN_123"
  private val fakeRefresh = "FAKE_REFRESH_TOKEN_123"
  private val rotatedRefresh = "FAKE_ROTATED_REFRESH_456"

  @Before
  fun setUp() {
    prefs = AppPreferences(ApplicationProvider.getApplicationContext())
    prefs.xAccessToken = ""
    prefs.xRefreshToken = ""
    prefs.xClientId = ""
    prefs.xClientSecret = ""
  }

  // --- the renew-or-not policy -------------------------------------------------------------

  @Test
  fun `a missing access token always needs one minting`() {
    assertTrue(XApiService.needsRefresh("", expiresAtMillis = 0L, nowMillis = 1_000L))
  }

  @Test
  fun `an unknown expiry is not treated as expired`() {
    // 0 means the user pasted the token by hand, so its lifetime is genuinely unknown. Refreshing
    // on that assumption would burn the single-use refresh token every single time the app posts.
    assertFalse(XApiService.needsRefresh(fakeAccess, expiresAtMillis = 0L, nowMillis = 9_999_999L))
  }

  @Test
  fun `an expired token needs renewing`() {
    val now = 10_000_000L
    assertTrue(XApiService.needsRefresh(fakeAccess, expiresAtMillis = now - 1, nowMillis = now))
  }

  @Test
  fun `a token expiring inside the safety margin is renewed early`() {
    // Two minutes left: long enough to look fine, short enough that the request could easily land
    // after it dies. The user would see that as a failed post.
    val now = 10_000_000L
    val twoMinutesLeft = now + 2 * 60 * 1000L
    assertTrue(XApiService.needsRefresh(fakeAccess, twoMinutesLeft, now))
  }

  @Test
  fun `a token with plenty of life left is left alone`() {
    val now = 10_000_000L
    val anHourLeft = now + 60 * 60 * 1000L
    assertFalse(XApiService.needsRefresh(fakeAccess, anHourLeft, now))
  }

  @Test
  fun `the safety margin is the boundary, exactly`() {
    val now = 10_000_000L
    val atTheMargin = now + XApiService.ACCESS_TOKEN_EXPIRY_SKEW_MS
    assertTrue("at the margin should refresh", XApiService.needsRefresh(fakeAccess, atTheMargin, now))
    assertFalse(
      "one millisecond outside it should not",
      XApiService.needsRefresh(fakeAccess, atTheMargin + 1, now)
    )
  }

  // --- persistence -------------------------------------------------------------------------

  @Test
  fun `a refresh stores the rotated refresh token, not just the access token`() {
    prefs.xRefreshToken = fakeRefresh

    prefs.saveRefreshedTokens(fakeAccess, rotatedRefresh, expiresInSeconds = 7200)

    assertEquals(fakeAccess, prefs.xAccessToken)
    assertEquals(
      "the rotated refresh token must replace the old one or the app locks itself out",
      rotatedRefresh,
      prefs.xRefreshToken
    )
  }

  @Test
  fun `a refresh records when the new token dies`() {
    val before = System.currentTimeMillis()

    prefs.saveRefreshedTokens(fakeAccess, rotatedRefresh, expiresInSeconds = 7200)

    val expected = before + 7200 * 1000L
    // Generous window: the clock moves between the two reads.
    assertTrue(
      "expiry ${prefs.xTokenExpiresAt} should be about $expected",
      kotlin.math.abs(prefs.xTokenExpiresAt - expected) < 5_000
    )
    assertFalse(
      "a freshly minted token should not immediately want renewing",
      XApiService.needsRefresh(prefs.xAccessToken, prefs.xTokenExpiresAt, System.currentTimeMillis())
    )
  }

  @Test
  fun `a response without a rotated refresh token keeps the existing one`() {
    // The spec allows the server to omit refresh_token; the service falls back to the current
    // value, and blanking the stored one here would be unrecoverable.
    prefs.xRefreshToken = fakeRefresh

    prefs.saveRefreshedTokens(fakeAccess, "", expiresInSeconds = 7200)

    assertEquals(fakeRefresh, prefs.xRefreshToken)
  }

  @Test
  fun `pasting a token by hand clears any stale expiry`() {
    prefs.saveRefreshedTokens(fakeAccess, rotatedRefresh, expiresInSeconds = 7200)
    assertTrue(prefs.xTokenExpiresAt > 0)

    prefs.xAccessToken = "FAKE_HAND_PASTED_789"

    assertEquals(
      "a hand-pasted token has no known lifetime; keeping the old deadline would be a lie",
      0L,
      prefs.xTokenExpiresAt
    )
  }

  @Test
  fun `a client id and refresh token alone count as configured`() {
    // The app can mint an access token from these, so demanding a pasted access token as well
    // would reject a setup that works perfectly.
    prefs.xAuthMethod = "OAUTH2_USER"
    prefs.xClientId = "FAKE_CLIENT_ID_123"
    prefs.xRefreshToken = fakeRefresh
    prefs.xAccessToken = ""

    assertTrue(prefs.hasXCredentials)
  }
}
