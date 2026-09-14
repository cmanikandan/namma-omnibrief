package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Namma Omnibrief type scale - editorial and punchy, one notch larger than the
// Material 3 defaults because the user asked for a bigger comfortable default
// (bodyLarge is 17sp, not 16sp).
//
// Everything is built from the platform sans-serif (Roboto on device). No font
// resources and no extra Gradle dependencies - downloadable fonts are out of scope.

/** Shared builder so every style scales consistently. */
private fun omniStyle(
  fontSize: Float,
  lineHeight: Float,
  fontWeight: FontWeight,
  letterSpacing: Float,
  scale: Float,
  fontFamily: FontFamily = FontFamily.SansSerif,
): TextStyle =
  TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = (fontSize * scale).sp,
    lineHeight = (lineHeight * scale).sp,
    letterSpacing = letterSpacing.sp,
  )

/**
 * Builds the full Material 3 type scale, with every `fontSize` and `lineHeight` multiplied by
 * [scale].
 *
 * [scale] is the user's font-size preference from Settings. Values from roughly 0.85f (compact) to
 * 1.6f (large) are supported; anything outside a sane range is clamped so a corrupted preference
 * can never produce unreadable or absurd text.
 *
 * Letter spacing is deliberately *not* scaled: fixed tracking keeps large headlines looking tight
 * and editorial instead of loose and airy as they grow.
 */
fun appTypography(scale: Float = 1.0f): Typography {
  val s = scale.coerceIn(0.75f, 1.75f)
  return Typography(
    // Display - hero moments only.
    displayLarge = omniStyle(58f, 64f, FontWeight.Bold, -1.0f, s),
    displayMedium = omniStyle(46f, 54f, FontWeight.Bold, -0.5f, s),
    displaySmall = omniStyle(38f, 46f, FontWeight.Bold, -0.25f, s),

    // Headline - screen titles and section heroes.
    headlineLarge = omniStyle(34f, 42f, FontWeight.ExtraBold, -0.5f, s),
    headlineMedium = omniStyle(29f, 37f, FontWeight.Bold, -0.4f, s),
    headlineSmall = omniStyle(25f, 33f, FontWeight.Bold, -0.25f, s),

    // Title - card headers, dialog titles, list leads.
    titleLarge = omniStyle(23f, 30f, FontWeight.Bold, -0.2f, s),
    titleMedium = omniStyle(18f, 25f, FontWeight.SemiBold, 0.0f, s),
    titleSmall = omniStyle(15f, 21f, FontWeight.SemiBold, 0.1f, s),

    // Body - the reading sizes. bodyLarge is the app default.
    bodyLarge = omniStyle(17f, 26f, FontWeight.Normal, 0.15f, s),
    bodyMedium = omniStyle(15f, 23f, FontWeight.Normal, 0.2f, s),
    bodySmall = omniStyle(13f, 19f, FontWeight.Normal, 0.25f, s),

    // Label - buttons, chips, nav items, metadata.
    labelLarge = omniStyle(15f, 20f, FontWeight.SemiBold, 0.3f, s),
    labelMedium = omniStyle(13f, 18f, FontWeight.Medium, 0.4f, s),
    labelSmall = omniStyle(12f, 16f, FontWeight.Medium, 0.5f, s),
  )
}

/** Default, unscaled scale. Kept as a top-level val so existing references still compile. */
val Typography = appTypography()
