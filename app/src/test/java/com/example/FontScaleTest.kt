package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppPreferences
import com.example.ui.theme.MAX_FONT_SCALE
import com.example.ui.theme.MIN_FONT_SCALE
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PreviewAtFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the Settings -> Appearance -> Text Size control.
 *
 * ## Why these exist
 *
 * The first implementation scaled only [androidx.compose.material3.Typography]. That looked correct
 * in review and was almost entirely ineffective in practice, because these screens use roughly 150
 * literal `fontSize = 12.sp` values rather than `MaterialTheme.typography.*`. The setting moved a
 * handful of strings and left the rest of the app alone.
 *
 * The fix was to scale at the density level instead, so the thing worth asserting is specifically
 * that **a hardcoded `sp` literal responds to the setting** — not merely that the type ramp does.
 * A test that only checked the typography would have passed against the broken implementation.
 *
 * ## Structure
 *
 * `createComposeRule().setContent` may only be called **once per test**, so each test does a single
 * `setContent` and renders every scale it needs as sibling subtrees inside that one composition,
 * recording resolved pixel sizes as it goes. Measuring in pixels (rather than asserting on the
 * `TextStyle`) is deliberate: pixels are what the density override actually changes, and what the
 * user ends up looking at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FontScaleTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Renders a themed subtree per entry in [scales] and returns the pixel size that a literal
   * `sizeSp.sp` resolves to under each.
   *
   * Everything happens inside one composition, which is what the Compose test rule requires.
   */
  private fun literalSpPixelsAt(vararg scales: Float, sizeSp: Float = 12f): List<Float> {
    val out = MutableList(scales.size) { 0f }
    composeTestRule.setContent {
      scales.forEachIndexed { i, scale ->
        MyApplicationTheme(fontScale = scale) {
          with(LocalDensity.current) { out[i] = sizeSp.sp.toPx() }
        }
      }
    }
    return out
  }

  @Test
  fun `hardcoded sp literals scale with the setting`() {
    val (atOne, atOneAndAHalf) = literalSpPixelsAt(1.0f, 1.5f)

    assertTrue("baseline should resolve to a real size", atOne > 0f)
    // The whole point: a literal 12.sp must get 50% bigger, not stay put.
    assertEquals(atOne * 1.5f, atOneAndAHalf, 0.01f)
  }

  @Test
  fun `smaller scale shrinks hardcoded sp literals`() {
    val (atOne, atCompact) = literalSpPixelsAt(1.0f, 0.9f)
    assertEquals(atOne * 0.9f, atCompact, 0.01f)
  }

  @Test
  fun `typography is not scaled twice`() {
    // The theme applies the scale via LocalDensity, so the Typography it installs must stay at
    // baseline. If someone also passes appTypography(fontScale) here, bodyLarge would come out at
    // scale^2 and this catches it.
    var bodyLargePx = 0f
    var literalPx = 0f
    composeTestRule.setContent {
      MyApplicationTheme(fontScale = 1.5f) {
        with(LocalDensity.current) {
          bodyLargePx = MaterialTheme.typography.bodyLarge.fontSize.toPx()
          // bodyLarge is defined as 17sp at baseline.
          literalPx = 17f.sp.toPx()
        }
      }
    }
    assertEquals(literalPx, bodyLargePx, 0.01f)
  }

  @Test
  fun `scale is clamped to the supported range`() {
    val (absurd, maxed, tiny, minned) =
      literalSpPixelsAt(99f, MAX_FONT_SCALE, 0.01f, MIN_FONT_SCALE)

    assertEquals("above-range scale should clamp to the maximum", maxed, absurd, 0.01f)
    assertEquals("below-range scale should clamp to the minimum", minned, tiny, 0.01f)
    assertTrue("clamped max must still exceed clamped min", maxed > minned)
  }

  @Test
  fun `settings preview renders at the option's own scale not the active one`() {
    // Each row in the Text Size picker must show its own size regardless of what is selected, so
    // the same option must measure identically whether the app is currently at 0.9 or at 1.5.
    var whileCompact = 0f
    var whileLarge = 0f
    composeTestRule.setContent {
      MyApplicationTheme(fontScale = 0.9f) {
        PreviewAtFontScale(optionScale = 1.3f, currentScale = 0.9f) {
          with(LocalDensity.current) { whileCompact = 14f.sp.toPx() }
        }
      }
      MyApplicationTheme(fontScale = 1.5f) {
        PreviewAtFontScale(optionScale = 1.3f, currentScale = 1.5f) {
          with(LocalDensity.current) { whileLarge = 14f.sp.toPx() }
        }
      }
    }

    assertTrue("preview should resolve to a real size", whileCompact > 0f)
    assertEquals(whileLarge, whileCompact, 0.01f)
  }

  @Test
  fun `default font scale is larger than stock and within the offered options`() {
    // The user explicitly asked for a slightly bigger default.
    assertTrue(
      "default should be above the 1.0 baseline",
      AppPreferences.DEFAULT_FONT_SCALE > 1.0f
    )
    assertTrue(
      "default must be one of the selectable options, or Settings shows nothing as selected",
      AppPreferences.FONT_SCALE_OPTIONS.any {
        kotlin.math.abs(it.first - AppPreferences.DEFAULT_FONT_SCALE) < 0.001f
      }
    )
  }

  @Test
  fun `every offered option is inside the clamp range`() {
    // An option outside the clamp would be selectable but have no effect, which reads as a bug.
    AppPreferences.FONT_SCALE_OPTIONS.forEach { (scale, label) ->
      assertTrue(
        "$label ($scale) is outside [$MIN_FONT_SCALE, $MAX_FONT_SCALE]",
        scale in MIN_FONT_SCALE..MAX_FONT_SCALE
      )
    }
  }
}
