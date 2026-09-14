package com.example

import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the `[Image 1] ` marker stripping performed when an archived brief is reopened.
 *
 * This is a regression test for a defect that reached a published tweet: History stores a batch as
 * one string with per-part labels and `---` separators, and the composer used to load that string
 * verbatim, so the label was posted as the first characters of the post.
 *
 * Pure JVM — [MainViewModel.splitArchivedDraft] is in the companion object precisely so this can be
 * asserted without an Application, a Room database or the Today network fetch that constructing the
 * real ViewModel would trigger.
 */
class ArchivedDraftTest {

    @Test
    fun `strips the image label from a single part brief`() {
        val stored = "[Image 1] NSE has cleared its IPO backlog, the Economic Times reports."

        val parts = MainViewModel.splitArchivedDraft(stored)

        assertEquals(1, parts.size)
        assertEquals("NSE has cleared its IPO backlog, the Economic Times reports.", parts[0])
    }

    @Test
    fun `splits a batch and strips every label`() {
        val stored = listOf(
            "[Image 1] First story about markets.",
            "[Image 2] Second story about chips.",
            "[Pasted text] Third story from the clipboard."
        ).joinToString("\n\n---\n\n")

        val parts = MainViewModel.splitArchivedDraft(stored)

        assertEquals(
            listOf(
                "First story about markets.",
                "Second story about chips.",
                "Third story from the clipboard."
            ),
            parts
        )
    }

    @Test
    fun `leaves an unlabelled draft untouched`() {
        val stored = "A plain post with no marker at all."

        assertEquals(listOf(stored), MainViewModel.splitArchivedDraft(stored))
    }

    /**
     * The label regex is anchored, so a bracketed aside inside the post body must survive. Without
     * the anchor a post that merely *mentions* something in brackets would be silently truncated.
     */
    @Test
    fun `keeps brackets that are not a leading label`() {
        val stored = "The regulator [SEBI] approved the filing."

        assertEquals(listOf(stored), MainViewModel.splitArchivedDraft(stored))
    }

    /**
     * A leading bracket followed by a newline is body text, not a label — the marker written by
     * `saveDraftToRoom` is always on the same line as the text it prefixes.
     */
    @Test
    fun `does not strip a bracket that spans a line break`() {
        val stored = "[Not a label\nbecause it wraps] and then the post."

        assertEquals(listOf(stored), MainViewModel.splitArchivedDraft(stored))
    }

    @Test
    fun `drops empty parts`() {
        val stored = "[Image 1] Only real content.\n\n---\n\n[Image 2] "

        assertEquals(listOf("Only real content."), MainViewModel.splitArchivedDraft(stored))
    }
}
