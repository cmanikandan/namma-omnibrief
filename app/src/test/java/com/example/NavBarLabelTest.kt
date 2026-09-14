package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.ui.components.AppDestination
import com.example.ui.components.OmniNavBar
import com.example.ui.theme.MAX_FONT_SCALE
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test for the bottom navigation bar at large Text Size settings.
 *
 * ## Why this exists
 *
 * Five tabs share the screen width, so each label has roughly a fifth of it. Once the Text Size
 * setting started actually working, "Conference" no longer fitted and Material wrapped it onto a
 * second line — breaking the word in half and making the whole bar taller. The user reported it.
 *
 * The label now shrinks to fit instead of wrapping, so the invariant worth pinning down is that a
 * label's laid-out height barely moves between the baseline scale and the largest one. A wrapped
 * label is twice as tall as a single line at the same size, and here the size is 75% larger too.
 *
 * ## Three things these tests get wrong if you are not careful
 *
 * - **The tree must be unmerged.** `NavigationBarItem` is selectable, so it merges its children's
 *   semantics. Querying the merged tree returns the whole 80dp tab and every assertion below
 *   compares the tab against itself, passing whatever the label does.
 * - **Graphics must be [GraphicsMode.Mode.NATIVE].** In legacy graphics mode Robolectric does not
 *   measure text with real fonts, nothing ever overflows its constraints, and the test cannot see
 *   wrapping even when it is happening.
 * - **The device qualifier matters.** Robolectric's default screen is 320dp wide, narrower than
 *   any phone this app will run on. Pixel 8 matches the hardware the bug was reported on.
 *
 * All three were wrong in the first draft of this file, and it passed against the unfixed code.
 * If you change these tests, re-check them by reverting `NavLabel` to a plain `Text` and confirming
 * they fail.
 *
 * Both scales are rendered inside a single `setContent`, because the Compose test rule refuses a
 * second call.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NavBarLabelTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Anything at or above this ratio means the label wrapped rather than shrank. */
  private val wrapRatio = 2.0f

  private fun renderBothScales(historyCount: Int = 0) {
    composeTestRule.setContent {
      Column {
        MyApplicationTheme(fontScale = 1.0f) {
          OmniNavBar(
            selectedDestination = AppDestination.HEADLINES,
            onDestinationSelected = {},
            historyCount = historyCount
          )
        }
        MyApplicationTheme(fontScale = MAX_FONT_SCALE) {
          OmniNavBar(
            selectedDestination = AppDestination.HEADLINES,
            onDestinationSelected = {},
            historyCount = historyCount
          )
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  /** Heights of the two renderings of the label [text], baseline scale first. */
  private fun heightsOf(text: String): Pair<Int, Int> {
    val nodes =
      composeTestRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals("expected the label to be rendered once per nav bar", 2, nodes.size)
    return nodes[0].size.height to nodes[1].size.height
  }

  @Test
  fun `the longest label does not wrap at the largest text size`() {
    renderBothScales()
    val (baseline, large) = heightsOf(AppDestination.CONFERENCE.label)

    assertTrue("the label should have a real height", baseline > 0)
    assertTrue(
      "\"${AppDestination.CONFERENCE.label}\" wrapped: $baseline px at scale 1.0 but $large px at " +
        "$MAX_FONT_SCALE",
      large < baseline * wrapRatio
    )
  }

  @Test
  fun `every tab label stays on one line at the largest text size`() {
    renderBothScales()
    AppDestination.entries.forEach { destination ->
      val (baseline, large) = heightsOf(destination.label)
      assertTrue(
        "\"${destination.label}\" wrapped: $baseline px at scale 1.0 but $large px at " +
          "$MAX_FONT_SCALE",
        large < baseline * wrapRatio
      )
    }
  }

  @Test
  fun `the archive count does not push its label onto a second line`() {
    // "Archive (10)" is the longest string the bar ever shows, and it only appears once Room has
    // filled up — which is exactly when it is least likely to be noticed in review.
    renderBothScales(historyCount = 10)
    val (baseline, large) = heightsOf("${AppDestination.HISTORY.label} (10)")

    assertTrue("the label should have a real height", baseline > 0)
    assertTrue(
      "\"Archive (10)\" wrapped: $baseline px at scale 1.0 but $large px at $MAX_FONT_SCALE",
      large < baseline * wrapRatio
    )
  }

  @Test
  fun `labels still grow when there is room for them`() {
    // The fix must not turn into "pin the nav bar to 11sp and ignore the setting". "Today" is
    // short enough to fit at any offered scale, so it should come out visibly bigger.
    renderBothScales()
    val (baseline, large) = heightsOf(AppDestination.HEADLINES.label)

    assertTrue(
      "a short label should still honour the Text Size setting: $baseline px vs $large px",
      large > baseline
    )
  }
}
